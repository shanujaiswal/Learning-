"""ROS2-style nodes for the DEPLOYMENT (inference) side of the delivery
robot -- this is what actually runs on the robot after training is done.

Contrast with Project 1's `NavigationNode`: that node computes a FRESH A*
plan against a live-built occupancy map every time it runs, re-searching
the graph from scratch whenever the situation changes. `PolicyNode` below
does none of that -- it holds a Q-table that was already learned offline
by `train.py`, and at runtime it does nothing more than a table lookup
(`argmax_a Q(s, a)`) per tick. All the "planning" effort already happened
during training; deployment is cheap and constant-time, which is exactly
the classical-planner-vs-learned-policy trade-off in real fleets: nav2-
style planners re-verify a plan against the world every cycle (more
compute per tick, but always reasoning over the actual current map), while
a learned policy is near-free per tick but is only as good as the
situations it saw during training (see the README's honest trade-off
discussion).

Node graph:
    SensorNode   -- publishes `/proximity` (danger bitmask) and `/cell_odom`
                    (current grid cell), the only node touching the HAL's
                    sensor-facing calls.
    PolicyNode   -- subscribes to `/proximity` + `/cell_odom`, looks up the
                    trained Q-table greedily (no exploration at deployment
                    time), publishes `/cmd_action` (this project's
                    `/cmd_vel`-equivalent: a discrete N/S/E/W action).
    MotionExecutorNode -- subscribes to `/cmd_action`, is the only node
                    allowed to call `drive_cell` on the HAL -- mirrors
                    Project 1's `MotorDriverNode` sitting between the
                    control stack and the physical actuator.
"""

import numpy as np
import ros2_lite as rclpy

from q_learning_agent import state_to_index, index_to_action


class SensorNode(rclpy.Node):
    """Publishes `/cell_odom` (current grid cell) and `/proximity` (N/S/E/W
    danger bitmask) -- the only node that reads off the HAL's sensor-facing
    calls, exactly like Project 1's `LidarNode`/`OdometryNode` split, here
    combined into one node since both readings come off the same cheap
    grid-world HAL.
    """

    def __init__(self, hal_drive, hal_proximity, clock, period_s=0.1):
        super().__init__("sensor_node")
        self._hal_drive = hal_drive
        self._hal_proximity = hal_proximity
        self._clock = clock
        self._odom_pub = self.create_publisher(dict, "/cell_odom", 10)
        self._prox_pub = self.create_publisher(dict, "/proximity", 10)
        self.create_timer(period_s, self._tick)

    def _tick(self):
        row, col = self._hal_drive.read_odometry_cell()
        self._odom_pub.publish({"t": self._clock.t, "row": row, "col": col})
        bitmask = self._hal_proximity.get_danger_bitmask()
        self._prox_pub.publish({"t": self._clock.t, "danger_bitmask": bitmask})


class PolicyNode(rclpy.Node):
    """Subscribes to `/cell_odom` + `/proximity`, looks up the TRAINED
    Q-table greedily, publishes `/cmd_action` -- the deployed policy. No
    learning happens here (epsilon=0, table is read-only) and no search
    happens here either -- this is pure inference, the deployment
    counterpart to Project 1's `NavigationNode` (which instead re-plans
    with A* live).
    """

    def __init__(self, q_table, clock, cmd_period_s=0.1):
        super().__init__("policy_node")
        self._q_table = q_table
        self._clock = clock
        self._latest_odom = None
        self._latest_proximity = None
        self.last_action = None
        self.create_subscription(dict, "/cell_odom", self._on_odom, 10)
        self.create_subscription(dict, "/proximity", self._on_proximity, 10)
        self._pub = self.create_publisher(dict, "/cmd_action", 10)
        self.create_timer(cmd_period_s, self._tick)

    def _on_odom(self, msg):
        self._latest_odom = msg

    def _on_proximity(self, msg):
        self._latest_proximity = msg

    def _tick(self):
        if self._latest_odom is None or self._latest_proximity is None:
            return
        state = (self._latest_odom["row"], self._latest_odom["col"],
                  self._latest_proximity["danger_bitmask"])
        state_idx = state_to_index(state)
        row = self._q_table[state_idx]
        best = np.flatnonzero(row == row.max())
        action_idx = int(best[0]) if len(best) == 1 else int(np.random.default_rng().choice(best))
        action = index_to_action(action_idx)
        self.last_action = action
        self._pub.publish({"t": self._clock.t, "action": action})


class MotionExecutorNode(rclpy.Node):
    """Subscribes to `/cmd_action` and is the ONLY node allowed to call
    `drive_cell` on the drive HAL -- the deployment-side equivalent of
    Project 1's `MotorDriverNode`.
    """

    def __init__(self, hal_drive, clock):
        super().__init__("motion_executor_node")
        self._hal_drive = hal_drive
        self._clock = clock
        self.reached_goal = False
        self.collided_with_pedestrian = False
        self.episode_done = False
        self.trail = []
        self.total_reward = 0.0
        self.steps = 0
        self.create_subscription(dict, "/cmd_action", self._on_cmd_action, 10)

    def _on_cmd_action(self, msg):
        if self.episode_done:
            return
        self._hal_drive.drive_cell(msg["action"])
        reward, info, done = self._hal_drive.last_step_result()
        self.total_reward += reward
        self.steps += 1
        self.trail.append(self._hal_drive.read_odometry_cell())
        if info.get("collision") == "pedestrian":
            self.collided_with_pedestrian = True
        if done:
            self.episode_done = True
            if self._hal_drive.read_odometry_cell() == self._hal_drive.env.goal:
                self.reached_goal = True
