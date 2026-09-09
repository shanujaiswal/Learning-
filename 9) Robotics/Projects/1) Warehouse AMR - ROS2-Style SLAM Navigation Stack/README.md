# Project 1 — Warehouse AMR: ROS2-Style SLAM Navigation Stack

## Real-world scenario

A warehouse deploys an Autonomous Mobile Robot (AMR) onto a new floor it has
never seen. On its first shift it must:

1. **Explore** the floor, building its own map from noisy sensors alone —
   no one hands it a blueprint.
2. **Plan** a route from wherever it ends up to a pack station, using only
   the map it just built itself.
3. **Execute** that route while a forklift crosses the aisle, and **recover
   on the fly** when the route runs into something its own map didn't know
   about — exactly what happens on a real warehouse floor.

This mirrors how real AMR fleets (e.g. those built on ROS2's `nav2` stack)
are actually architected and actually fail/recover in production, not a
cleaned-up textbook version of navigation.

## Why this is built the way it is, not just "the theory demo again"

Three things make this a real engineering project rather than a rerun of
the Theory/Topic-Practicals math demos:

1. **A hardware abstraction layer (`hardware.py`)** — every other module
   talks only to `SimulatedDiffDriveHAL`/`SimulatedLidar2DHAL`, never to
   the ground-truth world directly. Swap those two classes for real ones
   wrapping motor-driver PWM calls and an RPLidar SDK, and every node
   above the HAL keeps working unmodified — the actual pattern real
   robotics codebases use to keep simulation and hardware interchangeable.
2. **A real ROS2-style node/topic architecture (`ros2_lite.py`, `nodes.py`)**
   — `LidarNode`, `OdometryNode`, `MappingNode`, `NavigationNode`, and
   `MotorDriverNode` never call each other's methods directly; they only
   publish/subscribe on named topics (`/scan`, `/odom`, `/map`, `/cmd_vel`),
   exactly like real `rclpy` nodes. `ros2_lite.py` is a from-scratch
   pub/sub shim (no `rclpy` install required) written against the *same*
   API real ROS2 uses, so migrating any node here onto an actual ROS2
   install is a couple of import lines, not a rewrite.
3. **A real recovery behavior, not a scripted success path** — the robot's
   very first route plan runs straight into a shelf its own map hadn't
   fully seen yet (a genuinely common real-world failure: the map is only
   as good as what got explored). `NavigationNode` detects the stall and
   **replans against the corrected live map**, the same recovery pattern
   `nav2` itself uses. This isn't a bug to hide — it's the single most
   important real lesson about autonomous navigation: a robot can only
   ever plan against what it has actually perceived, and a good stack
   notices when that assumption breaks and recovers instead of getting
   stuck.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `world.py` | Ground truth: shelf racks + a patrolling forklift. **Nothing but `hardware.py` may read it.** | The physical warehouse floor |
| `hardware.py` | HAL: `SimulatedDiffDriveHAL` (wheel-speed commands, drifting encoder odometry, wall/shelf collision), `SimulatedLidar2DHAL` (noisy scans) | Motor-driver board + wheel encoders + 2D LIDAR (RPLidar/Hokuyo) |
| `mapping_and_planning.py` | Log-odds occupancy-grid SLAM, A* global planner, potential-field local avoidance, path simplification | `slam_toolbox`, `nav2_planner`, `nav2_controller` |
| `ros2_lite.py` | Minimal pub/sub + timer/clock shim, written against the real `rclpy` API | `rclpy` |
| `nodes.py` | `LidarNode`, `OdometryNode`, `MappingNode`, `MotorDriverNode`, `ExplorationDriverNode` (phase 1 wander controller), `NavigationNode` (phase 3 planner+controller+recovery) | A real `nav2` node graph |
| `main.py` | Wires the nodes together, runs explore → plan → execute, plots the result | A launch file + `rclpy.spin()` |

## Run it

```bash
cd "1) Warehouse AMR - ROS2-Style SLAM Navigation Stack"
pip install numpy matplotlib
python main.py
```

Takes a few seconds and saves `warehouse_amr_result.png`: the self-built
occupancy map with the exploration path, and the A* plan vs. the actually
executed path — including the loop where the robot stalls against an
unmapped shelf corner and replans around it.

## Things to try changing

- Move `START_XY`/`GOAL_XY` in `main.py`, or add another shelf rack in
  `world.py`, and watch how much the exploration coverage (and therefore
  the number of stall-triggered replans) changes.
- Lower `ExplorationDriverNode`'s `close_range_m` and see the map end up
  patchier — then watch `NavigationNode` replan more often as a result.
- Make the forklift faster (`world.FORKLIFT`'s `speed_mps`) and watch the
  potential-field repulsion in `mapping_and_planning.potential_field_step`
  fail to react in time — a real illustration of why local planners have
  a maximum obstacle speed they can safely handle.
