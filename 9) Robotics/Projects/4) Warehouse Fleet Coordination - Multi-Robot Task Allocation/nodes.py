"""ROS2-style node graph for the fleet, namespaced per robot exactly the
way a real multi-robot ROS2 deployment is namespaced: every robot gets its
own topic namespace (`robot_0/odom`, `robot_0/cmd_vel`, ...), and ONE
central `FleetManagerNode` coordinates across all of them -- the same
architecture real warehouse fleets (Amazon Robotics, Fetch, OTTO Motors)
run: per-robot hardware/control nodes that only know about themselves, plus
a fleet-management layer that has visibility across the whole roster.

Per-robot topics (namespace `robot_i`):
    robot_i/odom     -- wheel-encoder pose, published by OdometryNode
    robot_i/cmd_vel  -- (v, omega) command, consumed by MotorDriverNode
    robot_i/status   -- {"status": "idle"|"busy"} from RobotControllerNode
    robot_i/target   -- {"task_id", "x", "y"} pushed BY the fleet manager
    robot_i/task_done -- {"task_id", "ns", "t"} emitted on arrival

Fleet-wide: the `FleetManagerNode` itself has no public topics other than
what it subscribes to above -- in a real system it would also publish a
`/fleet/status` summary topic, omitted here since nothing else needs to
consume it.

Inter-robot collision avoidance is deliberately NOT a special topic: each
`RobotControllerNode` simply subscribes to every OTHER robot's `odom`
topic too, the same way a real multi-robot costmap layer subscribes to
neighboring robots' localization topics to treat them as moving obstacles
-- no new abstraction needed, just more subscriptions on the same topic
type every node already understands.
"""

import numpy as np
import ros2_lite as rclpy
import world
from task_allocation import auction_allocate


def _normalize_angle(angle):
    return (angle + np.pi) % (2 * np.pi) - np.pi


class OdometryNode(rclpy.Node):
    """Publishes `robot_i/odom` -- the only node that reads odometry off
    this robot's drive HAL.
    """

    def __init__(self, ns, hal_drive, clock, period_s=0.1):
        super().__init__(f"{ns}_odometry_node")
        self.ns = ns
        self._hal = hal_drive
        self._clock = clock
        self._pub = self.create_publisher(dict, f"{ns}/odom", 10)
        self.create_timer(period_s, self._tick)

    def _tick(self):
        x, y, heading = self._hal.read_wheel_odometry()
        self._pub.publish({"t": self._clock.t, "x": x, "y": y, "heading": heading})


class MotorDriverNode(rclpy.Node):
    """Subscribes to `robot_i/cmd_vel` and is the ONLY node allowed to call
    `set_wheel_speeds` on this robot's HAL.
    """

    def __init__(self, ns, hal_drive, clock):
        super().__init__(f"{ns}_motor_driver_node")
        self._hal = hal_drive
        self._clock = clock
        self.create_subscription(dict, f"{ns}/cmd_vel", self._on_cmd_vel, 10)

    def _on_cmd_vel(self, msg):
        wheel_base = self._hal.wheel_base
        v, omega = msg["v"], msg["omega"]
        left = v - omega * wheel_base / 2.0
        right = v + omega * wheel_base / 2.0
        self._hal.set_wheel_speeds(left, right, self._clock.dt)


class RobotControllerNode(rclpy.Node):
    """One per robot: plans one A* route across the known facility map the
    moment `FleetManagerNode` assigns it a task, then follows the route via
    potential-field local control -- attraction to the current waypoint,
    repulsion from nearby shelf racks, AND repulsion from every OTHER
    robot's current position (read straight off their `odom` topics).
    Reports `idle`/`busy` status and `task_done` events so the fleet
    manager knows when to auction this robot a new task.
    """

    def __init__(self, ns, other_namespaces, clock, cmd_period_s=0.1,
                 max_speed_mps=0.55, waypoint_tolerance_m=0.35,
                 stall_timeout_s=3.0, stall_nudge_mps=0.35):
        super().__init__(f"{ns}_controller_node")
        self.ns = ns
        self._clock = clock
        self._max_speed = max_speed_mps
        self._tolerance = waypoint_tolerance_m
        self._stall_timeout_s = stall_timeout_s
        self._stall_nudge = stall_nudge_mps

        self.x = self.y = self.heading = None
        self.status = "idle"
        self.current_task_id = None
        self.path = None
        self._waypoint_idx = 0
        self._best_dist_to_waypoint = np.inf
        self._stall_elapsed_s = 0.0
        self._nudge_dir = 1.0

        self.busy_time_s = 0.0
        self.executed_trail = []   # (t, x, y) every tick, whether idle or busy
        self.neighbor_positions = {}  # other_ns -> (x, y)

        self._pub_cmd = self.create_publisher(dict, f"{ns}/cmd_vel", 10)
        self._pub_status = self.create_publisher(dict, f"{ns}/status", 10)
        self._pub_done = self.create_publisher(dict, f"{ns}/task_done", 10)

        self.create_subscription(dict, f"{ns}/odom", self._on_own_odom, 10)
        self.create_subscription(dict, f"{ns}/target", self._on_target, 10)
        for other_ns in other_namespaces:
            self.create_subscription(dict, f"{other_ns}/odom", self._make_neighbor_cb(other_ns), 10)

        self.create_timer(cmd_period_s, self._tick)

    def _make_neighbor_cb(self, other_ns):
        def _cb(msg):
            self.neighbor_positions[other_ns] = (msg["x"], msg["y"])
        return _cb

    def _on_own_odom(self, msg):
        self.x, self.y, self.heading = msg["x"], msg["y"], msg["heading"]
        self.executed_trail.append((self._clock.t, self.x, self.y))

    def _on_target(self, msg):
        self.current_task_id = msg["task_id"]
        raw_path = world.astar((self.x, self.y), (msg["x"], msg["y"]))
        self.path = world.simplify_path(raw_path) if raw_path is not None else [(msg["x"], msg["y"])]
        self._waypoint_idx = 0
        self._best_dist_to_waypoint = np.inf
        self._stall_elapsed_s = 0.0
        self.status = "busy"
        self._pub_status.publish({"ns": self.ns, "status": "busy"})

    def _tick(self):
        if self.x is None:
            return

        if self.status == "idle":
            self._pub_cmd.publish({"v": 0.0, "omega": 0.0})
            return

        self.busy_time_s += self._clock.dt
        waypoint = self.path[self._waypoint_idx]
        dist_to_waypoint = np.hypot(waypoint[0] - self.x, waypoint[1] - self.y)

        # Stall recovery: if a knot of neighbor robots has us boxed in near
        # a waypoint (a real, common failure mode for purely-reactive
        # multi-robot separation -- two/three robots' repulsion fields can
        # cancel out into a local deadlock), nudge sideways briefly instead
        # of pushing straight at a waypoint the potential field can't reach.
        if dist_to_waypoint < self._best_dist_to_waypoint - 0.05:
            self._best_dist_to_waypoint = dist_to_waypoint
            self._stall_elapsed_s = 0.0
        else:
            self._stall_elapsed_s += self._clock.dt

        if dist_to_waypoint < self._tolerance:
            if self._waypoint_idx < len(self.path) - 1:
                self._waypoint_idx += 1
                self._best_dist_to_waypoint = np.inf
                self._stall_elapsed_s = 0.0
            else:
                self.status = "idle"
                self.path = None
                self._pub_status.publish({"ns": self.ns, "status": "idle"})
                self._pub_done.publish({"ns": self.ns, "task_id": self.current_task_id, "t": self._clock.t})
                self.current_task_id = None
                self._pub_cmd.publish({"v": 0.0, "omega": 0.0})
                return

        desired_vec = world.potential_field_step(
            self.x, self.y, waypoint, list(self.neighbor_positions.values()))

        if self._stall_elapsed_s > self._stall_timeout_s:
            # Deadlock-breaking nudge: a perpendicular sidestep, same idea
            # as a real reactive-nav "recovery behavior" when purely local
            # avoidance forces cancel out into a standstill.
            perp = np.array([-desired_vec[1], desired_vec[0]])
            norm = np.linalg.norm(perp)
            if norm > 1e-6:
                desired_vec = desired_vec + self._nudge_dir * self._stall_nudge * perp / norm
            if self._stall_elapsed_s > self._stall_timeout_s * 2:
                self._nudge_dir *= -1.0
                self._stall_elapsed_s = 0.0

        desired_heading = np.arctan2(desired_vec[1], desired_vec[0])
        heading_error = _normalize_angle(desired_heading - self.heading)
        speed = np.clip(np.linalg.norm(desired_vec), 0.0, self._max_speed) * max(0.0, np.cos(heading_error))
        omega = np.clip(1.8 * heading_error, -1.2, 1.2)
        self._pub_cmd.publish({"v": speed, "omega": omega})


class FleetManagerNode(rclpy.Node):
    """The central coordinator: holds the pick-order queue, tracks which
    robots are idle, and runs one round of auction-based task allocation
    (`task_allocation.auction_allocate`) every `assignment_period_s`
    whenever there's at least one idle robot and at least one open task --
    exactly the role a real warehouse fleet-management system plays
    (matching Kiva/Amazon-Robotics-style architectures: one fleet
    controller, many dumb-by-comparison mobile robots).
    """

    def __init__(self, robot_namespaces, task_queue, clock, assignment_period_s=0.5):
        super().__init__("fleet_manager_node")
        self.clock = clock
        self.total_tasks = len(task_queue)
        self.pending_tasks = dict(task_queue)          # task_id -> (x, y), not yet assigned
        self.robot_status = {ns: "idle" for ns in robot_namespaces}
        self.robot_position = {ns: None for ns in robot_namespaces}
        self.assignment_log = []                        # (t, ns, task_id)
        self.completed_tasks = {}                        # task_id -> (ns, t)

        self._target_pubs = {ns: self.create_publisher(dict, f"{ns}/target", 10) for ns in robot_namespaces}
        for ns in robot_namespaces:
            self.create_subscription(dict, f"{ns}/odom", self._make_odom_cb(ns), 10)
            self.create_subscription(dict, f"{ns}/status", self._on_status, 10)
            self.create_subscription(dict, f"{ns}/task_done", self._on_task_done, 10)
        self.create_timer(assignment_period_s, self._tick)

    def _make_odom_cb(self, ns):
        def _cb(msg):
            self.robot_position[ns] = (msg["x"], msg["y"])
        return _cb

    def _on_status(self, msg):
        self.robot_status[msg["ns"]] = msg["status"]

    def _on_task_done(self, msg):
        self.completed_tasks[msg["task_id"]] = (msg["ns"], msg["t"])

    def all_tasks_done(self):
        return len(self.completed_tasks) >= self.total_tasks

    def _tick(self):
        if not self.pending_tasks:
            return
        idle_positions = {
            ns: self.robot_position[ns]
            for ns, status in self.robot_status.items()
            if status == "idle" and self.robot_position[ns] is not None
        }
        if not idle_positions:
            return

        assignment = auction_allocate(idle_positions, self.pending_tasks)
        for ns, task_id in assignment.items():
            tx, ty = self.pending_tasks.pop(task_id)
            self.robot_status[ns] = "assigned"  # optimistic, until the robot's own /status confirms "busy"
            self._target_pubs[ns].publish({"task_id": task_id, "x": tx, "y": ty})
            self.assignment_log.append((self.clock.t, ns, task_id))
            self.get_logger().info(
                f"auctioned task '{task_id}' to {ns} at t={self.clock.t:.1f}s "
                f"({len(self.pending_tasks)} task(s) still queued)")
