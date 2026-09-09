# Project 2 — Industrial Pick-and-Place Robotic Arm Cell

## Real-world scenario

A small-parts packaging line runs a benchtop 3-DOF robotic arm (base yaw +
shoulder + elbow, servo-driven — the same class of low-cost automation arm
as a uArm or AL5D) at a fixed pick station. Parts already come down the
conveyor at a strict, metronomic rate set by the upstream fill/cap/label
process — a fixed **photoelectric sensor** at one exact spot on the belt
trips every time a part passes it. The arm's only job: every time that
sensor trips, pick the part off the belt and sort it into the correct
per-type bin, and finish the *entire* cycle — approach, descend, grasp,
lift, move, descend, release, retreat, return home — before the **next**
part arrives.

That fixed arrival rate is the line's **takt time**, and it is the one
constraint that actually decides whether this cell design works in
production: if the arm's pick-place cycle takes longer than takt time, it
does not "run a bit behind" — parts start queuing up behind the sensor (or
falling off the end of a real conveyor), and the backlog only grows from
there. This is exactly the constraint real packaging/assembly-line
integrators size a cell's motion profile against, not an abstract "can the
arm reach point B" demo.

## Why this is built the way it is, not just "the theory demo again"

Three things make this a real engineering project rather than a rerun of
the Theory/Topic-Practicals kinematics math demos:

1. **A hardware abstraction layer (`servo_hal.py`)** — every other module
   talks only to `ServoDriverHAL`, never to a "true" arm position directly.
   It exposes the exact call surface a real PCA9685 driver board's Python
   library (`adafruit_servokit.ServoKit`) gives you — `set_angle(channel, deg)`,
   `set_angles({...})` — and models the one thing a naive simulation always
   skips: a real servo horn can't snap to a new angle, it slews toward it at
   a bounded max rate, only caught up to by calling `step(dt_s)` every tick.
   Swap `ServoDriverHAL` for a thin wrapper around a real `ServoKit`
   instance and every node above it keeps working completely unmodified —
   the actual pattern real robotics codebases use to keep simulation and
   hardware interchangeable.
2. **A real ROS2-style node/topic architecture (`ros2_lite.py`, `nodes.py`)**
   — `ConveyorNode`, `ServoDriverNode`, and `ArmCellNode` never call each
   other's methods directly; they only publish/subscribe on named topics
   (`/part_detected`, `/joint_cmd`, `/joint_states`), exactly like real
   `rclpy` nodes. `ros2_lite.py` is the same generic pub/sub shim from
   Project 1 (copied verbatim — it's architecture-agnostic, not AMR- or
   arm-specific), so migrating any node here onto a real ROS2 install is a
   couple of import lines, not a rewrite.
3. **Quintic-polynomial motion profiles, not linear/instant joint jumps
   (`trajectory.py`)** — every commanded move blends smoothly from zero
   velocity/acceleration at the start to zero velocity/acceleration at the
   end, exactly the technique real industrial arm controllers, CNC/3D-printer
   motion planners, and `ros2_control` joint-trajectory controllers use to
   avoid mechanical jerk. Combined with the takt-time bookkeeping in
   `ArmCellNode` (which queues, rather than drops, a part that arrives while
   the arm is still mid-cycle), the project shows the real, non-negotiable
   trade-off every cell designer faces: faster motion profiles buy takt-time
   margin, but only up to the point where the arm's own mechanics (and the
   HAL's slew-rate limit) can't move any faster.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `kinematics.py` | Closed-form forward/inverse kinematics for the 2-link planar shoulder/elbow + base yaw, plus joint-rad ↔ servo-degree calibration | The arm's kinematic model + servo calibration table |
| `trajectory.py` | Quintic time-scaling + joint-space trajectory sampling | An industrial controller's motion-profile generator (`ros2_control` joint trajectory controller) |
| `servo_hal.py` | HAL: `ServoDriverHAL` — `set_angle`/`set_angles`/`step(dt_s)` slew-rate model, gripper open/close presets | PCA9685 driver board + `adafruit_servokit.ServoKit` |
| `conveyor.py` | Ground truth: `ConveyorFeeder` — fixed-takt-time part arrivals tripping a fixed photoelectric sensor | The belt + photoelectric/through-beam sensor at the pick station |
| `ros2_lite.py` | Minimal pub/sub + timer/clock shim, written against the real `rclpy` API (identical to Project 1's copy) | `rclpy` |
| `nodes.py` | `ConveyorNode` (publishes `/part_detected`), `ServoDriverNode` (owns the HAL, subscribes `/joint_cmd`, publishes `/joint_states`), `ArmCellNode` (pick-place state machine + takt-time/backlog stats) | A real cell controller's node graph |
| `main.py` | Wires the nodes, runs N parts through the cell, prints a takt-time/backlog summary, plots joint trajectories + end-effector path | A launch file + `rclpy.spin()` |

## Run it

```bash
cd "2) Industrial Pick-and-Place Robotic Arm Cell"
pip install numpy matplotlib
python main.py
```

Takes a few seconds and saves `arm_cell_result.png`: the actual (slew-limited)
joint-angle trajectories over time for all four servo channels (base, shoulder,
elbow, gripper), and the top-down end-effector path showing every pick/place
motion between home, the pick station, and the two bins.

Console output reports, per part: cycle time, whether it beat takt time, and
the backlog (parts still waiting) right after that cycle finished — plus an
overall summary (parts completed, average/min/max cycle time, total
takt-time misses, max backlog reached).

## Things to try changing

- Lower `TAKT_TIME_S` in `main.py` from `8.0` down to `5.0` and watch every
  cycle flip to `MISS` and `backlog_after` climb cycle over cycle — the
  arm's cycle time doesn't change (its motion profile is fixed), only
  whether the *line* can still keep up with it. That's the real lesson: a
  cell design's throughput is a hard number, not something that "mostly
  works" under a faster line.
- Shorten `MOVE_TIME_S`/`VERTICAL_TIME_S` in `main.py` to make the arm move
  faster and reclaim takt-time margin — then push `ServoDriverHAL`'s
  `slew_rate_deg_s` down in `main.py` to well below what the faster profile
  demands, and watch the actual (slew-limited) joint trajectory in the plot
  visibly lag behind the commanded quintic profile — a real illustration of
  why a faster motion program alone doesn't help once you've outrun the
  servo's physical top speed.
- Change `ArmCellNode.BIN_XYZ`'s coordinates, or add a third bin/part type,
  and re-run `kinematics.inverse_kinematics` on the new coordinates first
  (see the round-trip check pattern in this project's development) to
  confirm the new bin position is inside both the arm's physical reach
  *and* `JOINT_LIMITS_RAD` before wiring it into `ArmCellNode`.
