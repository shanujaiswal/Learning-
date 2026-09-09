"""ROS2-style nodes wiring the camera/vision pipeline, the AI classifier,
the grasp planner, and the servo HAL (`servo_hal.py`) purely through named
topics via the `ros2_lite` shim -- exactly the node/topic architecture a
real vision-guided sorting cell uses: a camera driver node publishes raw
frames, a perception node runs detection+classification, a planning node
turns detections into motion targets, and a motor-driver node is the only
thing that ever touches the actual servos.
"""

import numpy as np
import ros2_lite as rclpy
import kinematics
import trajectory
import vision
import grasp_planner

HOME_JOINT_RAD = np.array([0.0, np.pi / 2, np.pi / 2])  # arm folded up, straight above base


class CameraNode(rclpy.Node):
    """Publishes `/camera_frame` on a timer -- the only node that renders
    (in a real cell: captures) the overhead tray image. Owns the tray's
    ground-truth object list purely as a bookkeeping convenience for this
    simulation (a real camera driver has no such list -- it just returns
    pixels); it is never read by any other node, only rendered into pixels.
    """

    def __init__(self, tray_objects, clock, period_s=2.0):
        super().__init__("camera_node")
        self._tray = list(tray_objects)
        self._clock = clock
        self._pub = self.create_publisher(dict, "/camera_frame", 1)
        self.create_subscription(dict, "/object_picked", self._on_object_picked, 10)
        self.create_timer(period_s, self._tick)
        self.frame_count = 0
        self.last_frame = None

    def _tick(self):
        frame = vision.render_scene(self._tray)
        self.last_frame = frame
        self.frame_count += 1
        self._pub.publish({"t": self._clock.t, "frame": frame, "n_tray_objects": len(self._tray)})

    def _on_object_picked(self, msg):
        px, py = msg["pixel_xy"]
        if not self._tray:
            return
        dists = [np.hypot(o["pixel_xy"][0] - px, o["pixel_xy"][1] - py) for o in self._tray]
        nearest = int(np.argmin(dists))
        self._tray.pop(nearest)


class PerceptionNode(rclpy.Node):
    """Subscribes `/camera_frame`, runs the real cv2 segmentation +
    feature-extraction + trained-sklearn-model pipeline (`vision.
    detect_objects`), and publishes `/detections` -- the AI perception step
    of the cell.
    """

    def __init__(self, model, clock):
        super().__init__("perception_node")
        self._model = model
        self._clock = clock
        self._pub = self.create_publisher(dict, "/detections", 1)
        self.create_subscription(dict, "/camera_frame", self._on_frame, 1)
        self.last_detections = []

    def _on_frame(self, msg):
        detections = vision.detect_objects(msg["frame"], self._model)
        self.last_detections = detections
        self._pub.publish({"t": msg["t"], "detections": detections})


class GraspPlannerNode(rclpy.Node):
    """Subscribes `/detections`, turns each into a world-frame grasp plan
    via `grasp_planner.plan_grasp`, and publishes `/pick_targets` sorted
    nearest-first -- the same simple travel-minimizing heuristic a real
    pick sequencer uses to cut cycle time between successive picks.
    """

    def __init__(self, clock):
        super().__init__("grasp_planner_node")
        self._clock = clock
        self._pub = self.create_publisher(dict, "/pick_targets", 1)
        self.create_subscription(dict, "/detections", self._on_detections, 1)

    def _on_detections(self, msg):
        targets = [grasp_planner.plan_grasp(d) for d in msg["detections"]]
        targets.sort(key=lambda t: np.hypot(*t["world_xy"]))
        self._pub.publish({"t": msg["t"], "pick_targets": targets})


class ServoDriverNode(rclpy.Node):
    """Subscribes `/joint_cmd` and is the ONLY node allowed to call the
    servo HAL -- mirrors a real motor-driver node sitting between the
    motion-control stack and the physical PCA9685/servo board. Republishes
    the HAL's current joint/gripper state on `/joint_states`.
    """

    def __init__(self, hal, clock):
        super().__init__("servo_driver_node")
        self._hal = hal
        self._clock = clock
        self._state_pub = self.create_publisher(dict, "/joint_states", 10)
        self.create_subscription(dict, "/joint_cmd", self._on_cmd, 10)
        self.create_timer(clock.dt, self._tick)

    def _on_cmd(self, msg):
        if msg["type"] == "joint":
            self._hal.set_joint_angles_rad(msg["joint_rad"])
        elif msg["type"] == "gripper":
            if msg["action"] == "close":
                self._hal.close_gripper()
            else:
                self._hal.open_gripper()

    def _tick(self):
        self._state_pub.publish({
            "t": self._clock.t,
            "joint_rad": self._hal.read_joint_angles_rad(),
            "gripper": self._hal.gripper_state(),
        })


class ArmSortingNode(rclpy.Node):
    """Subscribes `/pick_targets` + `/joint_states`, runs the perceive ->
    plan -> control pick-sort-place cycle: for each pick target, streams a
    minimum-jerk joint trajectory (`trajectory.py`) computed via
    `kinematics.inverse_kinematics` through approach -> descend -> grasp ->
    lift -> transit to bin -> place -> retract, publishing `/joint_cmd`
    each tick -- the same perceive/plan/execute loop a real vision-guided
    sorting cell's motion sequencer runs once per detected object.
    """

    def __init__(self, clock, move_duration_s=1.2):
        super().__init__("arm_sorting_node")
        self._clock = clock
        self._dt = clock.dt
        self._move_duration = move_duration_s
        self._cmd_pub = self.create_publisher(dict, "/joint_cmd", 10)
        self._picked_pub = self.create_publisher(dict, "/object_picked", 10)
        self.create_subscription(dict, "/joint_states", self._on_joint_states, 10)
        self.create_subscription(dict, "/pick_targets", self._on_pick_targets, 1)
        self.create_timer(clock.dt, self._tick)

        self._latest_joint_rad = HOME_JOINT_RAD.copy()
        self._queue = []
        self._busy = False
        self._phases = []
        self._phase_idx = 0
        self._row_idx = 0
        self._current_target = None

        self.ee_history = []       # [(t, x, y, z)] executed end-effector path
        self.completed_picks = []  # [{class, world_xy, bin_xy, confidence}]

    def _on_joint_states(self, msg):
        self._latest_joint_rad = msg["joint_rad"]

    def _on_pick_targets(self, msg):
        if self._busy or self._queue:
            return  # a sort cycle is already in progress -- don't re-queue
        if msg["pick_targets"]:
            self._queue = list(msg["pick_targets"])

    def _build_phases(self, target, start_joint_rad):
        """Builds the ordered list of (kind, payload) phases for one full
        pick-sort-place cycle of `target`.
        """
        wx, wy = target["world_xy"]
        bx, by = target["bin_xy"]
        waypoints_xyz = [
            (wx, wy, target["approach_z"]),   # 1. hover above the object
            (wx, wy, target["grasp_z"]),      # 2. descend onto it
        ]
        phases = []
        prev_joint = start_joint_rad
        for xyz in waypoints_xyz:
            goal_joint = kinematics.inverse_kinematics(*xyz)
            phases.append(("move", trajectory.generate_joint_trajectory(
                prev_joint, goal_joint, self._move_duration, self._dt)))
            prev_joint = goal_joint

        phases.append(("gripper", "close"))

        remaining_xyz = [
            (wx, wy, target["approach_z"]),   # 3. lift clear of the tray
            (bx, by, target["approach_z"]),   # 4. transit above the bin
            (bx, by, target["place_z"]),      # 5. lower into the bin
        ]
        for xyz in remaining_xyz:
            goal_joint = kinematics.inverse_kinematics(*xyz)
            phases.append(("move", trajectory.generate_joint_trajectory(
                prev_joint, goal_joint, self._move_duration, self._dt)))
            prev_joint = goal_joint

        phases.append(("gripper", "open"))

        goal_joint = kinematics.inverse_kinematics(bx, by, target["approach_z"])
        phases.append(("move", trajectory.generate_joint_trajectory(
            prev_joint, goal_joint, self._move_duration, self._dt)))  # 6. retract

        return phases

    def _start_next_target(self):
        self._current_target = self._queue.pop(0)
        self._phases = self._build_phases(self._current_target, self._latest_joint_rad)
        self._phase_idx = 0
        self._row_idx = 0
        self._busy = True

    def _tick(self):
        if not self._busy:
            if self._queue:
                self._start_next_target()
            else:
                return

        kind, payload = self._phases[self._phase_idx]

        if kind == "gripper":
            self._cmd_pub.publish({"type": "gripper", "action": payload})
            if payload == "close":
                t = self._current_target
                self._picked_pub.publish({"pixel_xy": t["pixel_xy"]})
                self.completed_picks.append({
                    "class": t["class"], "confidence": t["confidence"],
                    "world_xy": t["world_xy"], "bin_xy": t["bin_xy"],
                })
            self._phase_idx += 1
            self._advance_or_finish()
            return

        traj_rows = payload
        joint_rad = traj_rows[self._row_idx]
        self._cmd_pub.publish({"type": "joint", "joint_rad": joint_rad})
        ee_xyz = kinematics.forward_kinematics(*joint_rad)
        self.ee_history.append((self._clock.t, *ee_xyz))

        self._row_idx += 1
        if self._row_idx >= len(traj_rows):
            self._row_idx = 0
            self._phase_idx += 1
            self._advance_or_finish()

    def _advance_or_finish(self):
        if self._phase_idx >= len(self._phases):
            self._busy = False
            self._current_target = None

    @property
    def idle(self):
        return not self._busy and not self._queue
