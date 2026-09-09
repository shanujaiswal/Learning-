"""ROS2-style nodes wiring the HAL (`hardware.py`) to the SLAM/planning
logic (`mapping_and_planning.py`) purely through named topics, via the
`ros2_lite` shim -- exactly the node/topic architecture a real ROS2
navigation stack (`nav2`) uses: sensor driver nodes publish raw data,
a mapping node fuses it into a map, a navigation node plans/reacts and
publishes velocity commands, and a motor-driver node is the only thing
that ever touches the actuators.
"""

import numpy as np
import ros2_lite as rclpy
from mapping_and_planning import OccupancyGridMap, astar, potential_field_step, simplify_path


def _normalize_angle(angle):
    return (angle + np.pi) % (2 * np.pi) - np.pi


class LidarNode(rclpy.Node):
    """Publishes raw `/scan` readings -- the only node that talks to the
    LIDAR HAL.
    """

    def __init__(self, hal_lidar, clock, period_s=0.1):
        super().__init__("lidar_node")
        self._hal_lidar = hal_lidar
        self._clock = clock
        self._pub = self.create_publisher(dict, "/scan", 10)
        self.create_timer(period_s, self._tick)

    def _tick(self):
        angles, ranges = self._hal_lidar.get_scan(self._clock.t)
        self._pub.publish({"t": self._clock.t, "angles": angles, "ranges": ranges})


class OdometryNode(rclpy.Node):
    """Publishes `/odom` (wheel-encoder dead-reckoning pose) -- the only
    node that reads odometry off the drive HAL.
    """

    def __init__(self, hal_drive, clock, period_s=0.1):
        super().__init__("odometry_node")
        self._hal_drive = hal_drive
        self._clock = clock
        self._pub = self.create_publisher(dict, "/odom", 10)
        self.create_timer(period_s, self._tick)

    def _tick(self):
        x, y, heading = self._hal_drive.read_wheel_odometry()
        self._pub.publish({"t": self._clock.t, "x": x, "y": y, "heading": heading})


class MappingNode(rclpy.Node):
    """Fuses `/scan` + `/odom` into a log-odds occupancy grid (SLAM-lite)
    and republishes the current map on `/map` periodically -- exactly the
    real `slam_toolbox`-style role: consume raw sensor topics, publish a
    `nav_msgs/OccupancyGrid`-equivalent for the navigation stack to consume.
    """

    def __init__(self, clock, map_publish_period_s=1.0, max_range_m=6.0):
        super().__init__("mapping_node")
        self.grid_map = OccupancyGridMap()
        self.trail = []
        self._max_range_m = max_range_m
        self._latest_odom = None
        self._map_pub = self.create_publisher(OccupancyGridMap, "/map", 1)
        self.create_subscription(dict, "/odom", self._on_odom, 10)
        self.create_subscription(dict, "/scan", self._on_scan, 10)
        self.create_timer(map_publish_period_s, self._publish_map)

    def _on_odom(self, msg):
        self._latest_odom = msg
        self.trail.append((msg["x"], msg["y"]))

    def _on_scan(self, msg):
        if self._latest_odom is None:
            return
        self.grid_map.integrate_scan(
            self._latest_odom["x"], self._latest_odom["y"],
            msg["angles"], msg["ranges"], self._max_range_m)

    def _publish_map(self):
        self._map_pub.publish(self.grid_map)


class MotorDriverNode(rclpy.Node):
    """Subscribes to `/cmd_vel` and is the ONLY node allowed to call
    `set_wheel_speeds` -- mirrors a real `diff_drive_controller` node
    sitting between the navigation stack and the physical motor driver.
    """

    def __init__(self, hal_drive, clock):
        super().__init__("motor_driver_node")
        self._hal_drive = hal_drive
        self._clock = clock
        self.create_subscription(dict, "/cmd_vel", self._on_cmd_vel, 10)

    def _on_cmd_vel(self, msg):
        wheel_base = self._hal_drive.wheel_base
        v, omega = msg["v"], msg["omega"]
        left = v - omega * wheel_base / 2.0
        right = v + omega * wheel_base / 2.0
        self._hal_drive.set_wheel_speeds(left, right, self._clock.dt)


class ExplorationDriverNode(rclpy.Node):
    """Phase 1 only: a reactive "bump-and-turn" wander controller -- drive
    forward, and steer away whenever the LIDAR sees an obstacle closing in
    ahead -- so `MappingNode` gets enough LIDAR coverage to build a usable
    map before any goal-directed navigation is attempted. This is the same
    "explore first, navigate second" split a real newly-deployed AMR runs on
    its first lap of a warehouse, and the same simple wandering strategy
    real low-cost robot vacuums use when they have no prior map at all.
    """

    def __init__(self, clock, forward_speed_mps=0.5, turn_speed_rad_s=1.0,
                 front_cone_rad=np.deg2rad(40), close_range_m=0.6, cmd_period_s=0.1,
                 bias_flip_period_s=20.0):
        super().__init__("exploration_driver_node")
        self._clock = clock
        self._forward_speed = forward_speed_mps
        self._turn_speed = turn_speed_rad_s
        self._front_cone_rad = front_cone_rad
        self._close_range_m = close_range_m
        self._bias_flip_period_s = bias_flip_period_s
        self._bias = 1.0
        self._bias_elapsed_s = 0.0
        self._latest_odom = None
        self._latest_scan = None
        self.create_subscription(dict, "/odom", self._on_odom, 10)
        self.create_subscription(dict, "/scan", self._on_scan, 10)
        self._pub = self.create_publisher(dict, "/cmd_vel", 10)
        self.create_timer(cmd_period_s, self._tick)

    def _on_odom(self, msg):
        self._latest_odom = msg

    def _on_scan(self, msg):
        self._latest_scan = msg

    def _tick(self):
        self._bias_elapsed_s += self._clock.dt
        if self._bias_elapsed_s >= self._bias_flip_period_s:
            self._bias_elapsed_s = 0.0
            self._bias *= -1.0  # periodically flip turn bias so both sides of the floor get swept

        if self._latest_odom is None or self._latest_scan is None:
            self._pub.publish({"v": 0.0, "omega": 0.0})
            return

        heading = self._latest_odom["heading"]
        angles, ranges = self._latest_scan["angles"], self._latest_scan["ranges"]
        relative = _normalize_angle(angles - heading)
        in_front_cone = np.abs(relative) < self._front_cone_rad
        min_front_range = np.min(ranges[in_front_cone]) if np.any(in_front_cone) else np.inf

        if min_front_range < self._close_range_m:
            # Obstacle ahead: pivot away (slow crawl, not a full stop, so the
            # robot never permanently deadlocks nose-in against a wall).
            self._pub.publish({"v": 0.15, "omega": self._turn_speed * self._bias})
        else:
            self._pub.publish({"v": self._forward_speed, "omega": 0.18 * self._bias})


class NavigationNode(rclpy.Node):
    """Phase 3: consumes `/map` + `/odom` + `/scan`, plans one A* route
    across the self-built map to `goal_xy`, then publishes `/cmd_vel`
    commands that follow it via potential-field local avoidance -- this is
    the real-world equivalent of `nav2`'s planner + controller servers
    combined into one node for simplicity.
    """

    def __init__(self, goal_xy, clock, waypoint_tolerance_m=0.45,
                 max_speed_mps=0.6, cmd_period_s=0.1,
                 stall_timeout_s=4.0, max_replans=8):
        super().__init__("navigation_node")
        self.goal_xy = goal_xy
        self._clock = clock
        self._tolerance = waypoint_tolerance_m
        self._max_speed = max_speed_mps
        self._latest_map = None
        self._latest_odom = None
        self._latest_scan = None
        self.path = None
        self._waypoint_idx = 0
        self.reached_goal = False
        self.executed_trail = []
        self.replan_count = 0

        # Stall/recovery bookkeeping -- the map used for the ORIGINAL plan
        # may have had unexplored (optimistically "free") cells that turn
        # out to be a real shelf once the robot gets close enough to see
        # it. A real nav stack (nav2) detects exactly this -- no progress
        # toward the next waypoint for too long -- and triggers a recovery:
        # replan against the now-corrected live map instead of continuing
        # to push against something it physically can't get through.
        self._stall_timeout_s = stall_timeout_s
        self._max_replans = max_replans
        self._stall_elapsed_s = 0.0
        self._best_dist_to_waypoint = np.inf

        self._pub = self.create_publisher(dict, "/cmd_vel", 10)
        self.create_subscription(OccupancyGridMap, "/map", self._on_map, 1)
        self.create_subscription(dict, "/odom", self._on_odom, 10)
        self.create_subscription(dict, "/scan", self._on_scan, 10)
        self.create_timer(cmd_period_s, self._tick)

    def _on_map(self, msg):
        self._latest_map = msg

    def _on_odom(self, msg):
        self._latest_odom = msg

    def _on_scan(self, msg):
        self._latest_scan = msg

    def _tick(self):
        if self._latest_odom is None:
            return
        x, y, heading = self._latest_odom["x"], self._latest_odom["y"], self._latest_odom["heading"]
        self.executed_trail.append((x, y))

        if self.reached_goal:
            self._pub.publish({"v": 0.0, "omega": 0.0})
            return

        if self.path is None:
            if self._latest_map is None:
                return
            if self.replan_count > self._max_replans:
                self.get_logger().info("Exceeded max replan attempts -- holding position.")
                self._pub.publish({"v": 0.0, "omega": 0.0})
                return
            raw_path = astar(self._latest_map, (x, y), self.goal_xy)
            if raw_path is None:
                self.get_logger().info("No path found across the current map -- holding position.")
                self._pub.publish({"v": 0.0, "omega": 0.0})
                return
            self.path = simplify_path(raw_path)
            self._waypoint_idx = 0
            self._best_dist_to_waypoint = np.inf
            self._stall_elapsed_s = 0.0
            self.get_logger().info(
                f"A* found a {len(raw_path)}-cell route, simplified to "
                f"{len(self.path)} waypoints for the controller to follow.")

        waypoint = self.path[self._waypoint_idx]
        dist_to_waypoint = np.hypot(waypoint[0] - x, waypoint[1] - y)

        # Stall/recovery check: if we haven't gotten meaningfully closer to
        # the current waypoint in `_stall_timeout_s`, the map we planned
        # against was almost certainly wrong about what's physically there
        # -- trigger a replan against the live (by-now-corrected) map.
        if dist_to_waypoint < self._best_dist_to_waypoint - 0.05:
            self._best_dist_to_waypoint = dist_to_waypoint
            self._stall_elapsed_s = 0.0
        else:
            self._stall_elapsed_s += self._clock.dt
            if self._stall_elapsed_s > self._stall_timeout_s:
                self.get_logger().info(
                    f"Stalled near waypoint {self._waypoint_idx} {waypoint} -- "
                    f"replanning against the updated live map (replan #{self.replan_count + 1}).")
                self.path = None
                self.replan_count += 1
                self._pub.publish({"v": 0.0, "omega": 0.0})
                return

        if dist_to_waypoint < self._tolerance:
            if self._waypoint_idx < len(self.path) - 1:
                self._waypoint_idx += 1
                waypoint = self.path[self._waypoint_idx]
                self._best_dist_to_waypoint = np.inf
                self._stall_elapsed_s = 0.0
            else:
                self.reached_goal = True
                self.get_logger().info(f"Goal reached at t={self._clock.t:.1f}s.")
                self._pub.publish({"v": 0.0, "omega": 0.0})
                return

        obstacle_readings = []
        if self._latest_scan is not None:
            obstacle_readings = list(zip(self._latest_scan["angles"], self._latest_scan["ranges"]))

        desired_vec = potential_field_step(x, y, waypoint, obstacle_readings)
        desired_heading = np.arctan2(desired_vec[1], desired_vec[0])
        heading_error = _normalize_angle(desired_heading - heading)

        speed = np.clip(np.linalg.norm(desired_vec), 0.0, self._max_speed) * max(0.0, np.cos(heading_error))
        omega = np.clip(1.8 * heading_error, -1.2, 1.2)
        self._pub.publish({"v": speed, "omega": omega})
