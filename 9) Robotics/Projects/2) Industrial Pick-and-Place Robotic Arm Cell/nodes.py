"""ROS2-style nodes wiring the conveyor ground truth (`conveyor.py`) and the
servo HAL (`servo_hal.py`) to the arm-cell pick-place logic purely through
named topics, via the `ros2_lite` shim -- exactly the node/topic split a
real industrial cell controller uses: a sensor-driver node publishes raw
station events, a motion-executor node is the only thing that ever touches
actuators, and a cell/task-sequencer node in between does nothing but plan
moves and publish commands. No node ever calls another node's method
directly.
"""

import numpy as np
import ros2_lite as rclpy
import kinematics
import trajectory


class ConveyorNode(rclpy.Node):
    """Publishes `/part_detected` the instant the photoelectric sensor at
    the fixed pick station trips -- the only node that talks to the
    conveyor/sensor ground truth.
    """

    def __init__(self, feeder, clock, period_s=0.05):
        super().__init__("conveyor_node")
        self._feeder = feeder
        self._clock = clock
        self._pub = self.create_publisher(dict, "/part_detected", 10)
        self.create_timer(period_s, self._tick)

    def _tick(self):
        event = self._feeder.step(self._clock.dt, self._clock.t)
        if event is not None:
            self.get_logger().info(
                f"Photoelectric sensor tripped -- part #{event['part_id']} "
                f"(type {event['part_type']}) at t={event['t_arrival']:.2f}s")
            self._pub.publish(event)


class ServoDriverNode(rclpy.Node):
    """Subscribes to `/joint_cmd` and is the ONLY node allowed to call the
    servo HAL -- mirrors a real motor-driver node sitting between a task
    sequencer and the physical PCA9685 board. Publishes the HAL's actual
    (slew-limited) angles on `/joint_states` every tick, exactly like a real
    driver node republishing joint feedback.
    """

    def __init__(self, hal, clock, period_s=0.02):
        super().__init__("servo_driver_node")
        self._hal = hal
        self._clock = clock
        self._pub = self.create_publisher(dict, "/joint_states", 10)
        self.create_subscription(dict, "/joint_cmd", self._on_cmd, 10)
        self.create_timer(period_s, self._tick)

    def _on_cmd(self, msg):
        self._hal.set_angles(msg["angles_deg"])
        gripper = msg.get("gripper")
        if gripper == "open":
            self._hal.open_gripper()
        elif gripper == "close":
            self._hal.close_gripper()

    def _tick(self):
        self._hal.step(self._clock.dt)
        self._pub.publish({"t": self._clock.t, "angles_deg": self._hal.get_angles()})


class ArmCellNode(rclpy.Node):
    """The pick-place task sequencer -- the real-world equivalent of an
    industrial cell controller's PLC/robot-program logic. Consumes
    `/part_detected` and `/joint_states`, and for every part builds a queued
    sequence of IK + quintic-trajectory waypoints for one full cycle:

        home -> approach pick -> descend -> GRASP -> lift ->
        approach part-type's bin -> descend -> RELEASE -> retreat -> home

    and publishes exactly one `/joint_cmd` waypoint per tick (open-loop,
    like a real industrial arm controller that trusts its servos to track
    commands rather than closing a position loop against `/joint_states`
    itself -- `/joint_states` here is consumed only for logging/plotting).

    If a new part's sensor trigger arrives while the arm is still mid-cycle
    on a previous part, it is queued as backlog rather than dropped -- the
    real failure mode a pick-and-place cell has when its cycle time doesn't
    beat the line's takt time: parts don't wait for the arm, they queue up
    (or overflow off the end of the conveyor in a real line).
    """

    HOME_XYZ = (0.10, 0.00, 0.28)
    PICK_XYZ = (0.22, 0.00, 0.00 + 0.06)
    PICK_APPROACH_DZ = 0.14
    BIN_XYZ = {
        "A": (0.15, 0.20, 0.06),
        "B": (0.15, -0.20, 0.06),
    }
    BIN_APPROACH_DZ = 0.14

    def __init__(self, clock, takt_time_s=8.0, tick_period_s=0.05,
                 move_time_s=1.2, vertical_time_s=0.6, dwell_time_s=0.4):
        super().__init__("arm_cell_node")
        self._clock = clock
        self._takt_time_s = takt_time_s
        self._tick_period_s = tick_period_s
        self._move_time_s = move_time_s
        self._vertical_time_s = vertical_time_s
        self._dwell_time_s = dwell_time_s

        self._motion_queue = []       # list of {"angles_rad":..., "gripper":...}
        self._pending_parts = []      # backlog of /part_detected events not yet started
        self._active_event = None
        self._cycle_start_t = None
        self._last_commanded_rad = kinematics.inverse_kinematics(*self.HOME_XYZ)

        self.completed_cycles = []    # stats, one dict per finished part
        self.ee_path = []             # (t, x, y, z) sampled every tick for plotting
        self.joint_log = []           # (t, base_deg, shoulder_deg, elbow_deg, gripper_deg)

        self._cmd_pub = self.create_publisher(dict, "/joint_cmd", 10)
        self.create_subscription(dict, "/part_detected", self._on_part_detected, 10)
        self.create_subscription(dict, "/joint_states", self._on_joint_states, 10)
        self.create_timer(tick_period_s, self._tick)

    # -- subscriptions ----------------------------------------------------
    def _on_part_detected(self, event):
        self._pending_parts.append(event)

    def _on_joint_states(self, msg):
        deg = msg["angles_deg"]
        self.joint_log.append((msg["t"], deg[0], deg[1], deg[2], deg[3]))

    # -- trajectory building ------------------------------------------------
    def _queue_move(self, target_xyz, duration_s, gripper=None):
        """Appends a quintic-blended joint move from the last commanded pose
        to `target_xyz`'s IK solution onto the motion queue.
        """
        q_end = kinematics.inverse_kinematics(*target_xyz)
        _, positions, _, _ = trajectory.joint_space_quintic_trajectory(
            self._last_commanded_rad, q_end, duration_s, self._tick_period_s)
        for i, q in enumerate(positions):
            # only the LAST sample of a move segment carries a gripper
            # command -- exactly like a real program only actuating the
            # gripper once the arm has actually arrived at the pose.
            g = gripper if (i == len(positions) - 1) else None
            self._motion_queue.append({"angles_rad": q, "gripper": g})
        self._last_commanded_rad = q_end

    def _queue_dwell(self, hold_xyz, duration_s, gripper):
        """Holds the current commanded pose in place for `duration_s` while
        issuing a gripper command -- used for the grasp/release pauses,
        which are a position-hold, not a Cartesian move.
        """
        q_hold = kinematics.inverse_kinematics(*hold_xyz)
        n_samples = max(1, int(round(duration_s / self._tick_period_s)))
        for i in range(n_samples):
            g = gripper if i == 0 else None
            self._motion_queue.append({"angles_rad": q_hold, "gripper": g})
        self._last_commanded_rad = q_hold

    def _build_cycle_queue(self, event):
        part_type = event["part_type"]
        bin_x, bin_y, bin_z = self.BIN_XYZ[part_type]
        pick_x, pick_y, pick_z = self.PICK_XYZ
        pick_approach = (pick_x, pick_y, pick_z + self.PICK_APPROACH_DZ)
        bin_approach = (bin_x, bin_y, bin_z + self.BIN_APPROACH_DZ)

        self._queue_move(pick_approach, self._move_time_s)                 # 1. approach pick
        self._queue_move(self.PICK_XYZ, self._vertical_time_s)             # 2. descend
        self._queue_dwell(self.PICK_XYZ, self._dwell_time_s, "close")      # 3. grasp
        self._queue_move(pick_approach, self._vertical_time_s)             # 4. lift
        self._queue_move(bin_approach, self._move_time_s)                  # 5. move to bin
        self._queue_move((bin_x, bin_y, bin_z), self._vertical_time_s)     # 6. descend
        self._queue_dwell((bin_x, bin_y, bin_z), self._dwell_time_s, "open")  # 7. release
        self._queue_move(bin_approach, self._vertical_time_s)              # 8. retreat
        self._queue_move(self.HOME_XYZ, self._move_time_s)                 # 9. return home

    # -- main loop ----------------------------------------------------------
    def _tick(self):
        if self._motion_queue:
            item = self._motion_queue.pop(0)
            angles_deg = kinematics.joints_rad_to_servo_deg(item["angles_rad"])
            self._cmd_pub.publish({"angles_deg": angles_deg, "gripper": item["gripper"]})
            xyz = kinematics.forward_kinematics(*item["angles_rad"])
            self.ee_path.append((self._clock.t, xyz[0], xyz[1], xyz[2]))
            if not self._motion_queue and self._active_event is not None:
                self._finish_cycle()
            return

        if self._active_event is None and self._pending_parts:
            event = self._pending_parts.pop(0)
            self._active_event = event
            self._cycle_start_t = self._clock.t
            self._build_cycle_queue(event)
            return
        # else: idle at home, nothing queued -- arm is caught up with the line.

    def _finish_cycle(self):
        cycle_time_s = self._clock.t - self._cycle_start_t
        beat_takt = cycle_time_s <= self._takt_time_s
        backlog_after = len(self._pending_parts)
        record = {
            "part_id": self._active_event["part_id"],
            "part_type": self._active_event["part_type"],
            "cycle_time_s": cycle_time_s,
            "beat_takt": beat_takt,
            "backlog_after": backlog_after,
        }
        self.completed_cycles.append(record)
        status = "OK" if beat_takt else "MISS"
        self.get_logger().info(
            f"Cycle complete -- part #{record['part_id']} (type {record['part_type']}): "
            f"{cycle_time_s:.2f}s [{status} vs takt {self._takt_time_s:.2f}s], "
            f"backlog={backlog_after}")
        self._active_event = None

    @property
    def is_idle_and_done(self):
        return (self._active_event is None and not self._motion_queue
                and not self._pending_parts)
