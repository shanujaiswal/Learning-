# Project 4 — Warehouse Fleet Coordination: Multi-Robot Task Allocation

## Real-world scenario

A warehouse operator has scaled past a single AMR (Project 1) to a small
fleet. A wave of pick orders — shelf slots that need visiting for an
outbound shipment — arrives all at once. The fleet-management system must:

1. **Auction the batch out to whichever robots are idle**, so no robot
   sits parked while work is queued and no two robots get sent to fight
   over the same nearby slot.
2. **Keep re-assigning work as robots free up** — with more orders than
   robots, every robot in the fleet gets handed several tasks over the
   course of the batch, not just one.
3. **Never collide with a fleet-mate while en route**, using only local,
   reactive awareness of nearby robots — re-planning a global route around
   every other moving robot every control tick is not something a real
   onboard controller can afford to do.

This is exactly how real multi-robot warehouse fleets (Amazon Robotics,
Fetch, OTTO Motors) are actually architected: one central fleet-management
node doing task assignment, many independent robots doing their own local
navigation and local collision avoidance.

## Why this is a real fleet-coordination project, not the theory demo again

1. **A real auction algorithm, not round-robin (`task_allocation.py`)** —
   `auction_allocate` implements the actual Bertsekas auction algorithm
   (Bertsekas, 1979/1988) for the assignment problem: every idle robot bids
   its own Euclidean travel cost against every open task, bids are
   resolved through the classic bid/price-rise auction process (prices
   only ever rise, which is what guarantees termination instead of two
   robots endlessly re-bidding on the same task), and `FleetManagerNode`
   just re-runs the auction on whatever idle-robot/open-task roster exists
   whenever it changes — the same reason real MRTA (multi-robot task
   allocation) systems favor auction methods over solving one big static
   assignment problem: they handle robots/tasks becoming available over
   time naturally, without re-deriving the whole assignment from scratch.
2. **A per-robot HAL, reused fleet-wide (`hardware.py`)** — every robot
   gets its own `SimulatedDiffDriveHAL` instance exposing the same
   `set_wheel_speeds` / `read_wheel_odometry` call surface as Project 1's
   HAL. A HAL instance deliberately knows nothing about any other robot —
   exactly like a real chassis's motor-driver board, which has no idea any
   other robot exists. Fleet-awareness lives one layer up, in the node
   graph, not in the hardware driver.
3. **A real ROS2-style multi-robot, multi-namespace architecture
   (`ros2_lite.py`, `nodes.py`)** — every robot gets its own topic
   namespace (`robot_0/odom`, `robot_0/cmd_vel`, `robot_0/status`,
   `robot_0/target`, ...), precisely how a real ROS2 multi-robot fleet is
   namespaced, plus one central `FleetManagerNode` with visibility across
   the whole roster. Inter-robot collision avoidance needs no new
   abstraction at all: `RobotControllerNode` just subscribes to every
   OTHER robot's `odom` topic too, and treats their positions as moving
   repulsive obstacles in the same potential field it already uses for
   shelf racks — the same way a real multi-robot costmap layer treats
   neighboring robots' localization topics as dynamic obstacles.
4. **Real fleet metrics, not "it finished"** — `main.py` reports makespan
   (time of the last completed pick), per-robot utilization (busy time /
   makespan), and the number of near-collision events detected and
   avoided (robot pairs that came within 0.8 m of each other, counted as
   discrete incidents via rising-edge detection, not one count per
   simulation tick) — the same categories a real fleet-ops dashboard
   reports after a shift.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `world.py` | Shelf racks + named pick locations, a static A* planner, and the potential-field local controller (attraction + shelf repulsion + inter-robot repulsion). Unlike Project 1, this is a *known* facility site map, not something built via SLAM — realistic for an *operating* fleet vs. a brand-new floor. | The warehouse's surveyed site map + `nav2_planner`/`nav2_controller` |
| `hardware.py` | `SimulatedDiffDriveHAL` — one instance per robot: wheel-speed commands, drifting encoder odometry, wall/shelf collision. Has zero knowledge of other robots. | Each robot's motor-driver board + wheel encoders |
| `task_allocation.py` | `auction_allocate(robot_positions, task_positions)` — the Bertsekas auction algorithm over a Euclidean bid/cost matrix | A fleet-management system's task dispatcher |
| `ros2_lite.py` | Minimal pub/sub + timer/clock shim (identical to Project 1) | `rclpy` |
| `nodes.py` | Per-robot `OdometryNode`, `MotorDriverNode`, `RobotControllerNode` (namespaced `robot_i/...`), plus one fleet-wide `FleetManagerNode` | A real multi-robot `nav2` + fleet-manager node graph |
| `main.py` | Spins up N robots + M pick tasks (M > N), runs the fleet to completion, prints makespan/utilization/near-miss metrics, plots trajectories | A launch file for a fleet + an ops dashboard |

## Run it

```bash
cd "4) Warehouse Fleet Coordination - Multi-Robot Task Allocation"
pip install numpy matplotlib
python main.py
```

Takes well under a minute and saves `fleet_coordination_result.png`: every
robot's executed trajectory over the shelf layout (color-coded by which
robot serviced which pick location), plus a per-robot utilization chart.
Console output shows every auction assignment as it happens, then the full
fleet metrics: tasks completed, makespan, per-robot utilization, and
near-collision events detected and avoided.

A representative run (5 robots, 18 pick orders): **18/18 tasks completed**,
makespan **~53 s**, fleet-average utilization **~86%**, **11 near-collision
events** detected and avoided with the closest any two robots ever got
being ~0.57 m (their combined body diameter is 0.44 m) and zero actual
body-to-body collisions.

## Things to try changing

- Raise `N_TASKS` well above `N_ROBOTS` in `main.py` and watch the makespan
  grow roughly linearly while utilization stays high — the queueing
  behavior a real fleet-ops team sizes fleets around.
- Drop `N_ROBOTS` while keeping `N_TASKS` fixed and watch utilization climb
  toward 100% (everyone's always busy) at the cost of a longer makespan —
  the real fleet-size/throughput trade-off.
- Shrink `world.potential_field_step`'s `robot_repel_radius_m` in
  `world.py` and watch the near-collision count climb, then shrink it far
  enough and watch an actual body-to-body overlap appear in the metrics —
  a direct illustration of why real fleets tune collision-avoidance
  parameters conservatively rather than for minimum travel time.
- Swap the bid cost in `task_allocation.auction_allocate` from Euclidean
  distance to true `world.astar` path length (expensive — one A* solve per
  robot/task pair per auction round) and see how much the assignment
  quality changes on this floorplan vs. how much slower each auction round
  gets — the real speed/accuracy trade-off that pushes real systems toward
  a fast admissible bid proxy instead.
- Cluster all of `DEPOT_SLOTS` in `main.py` into a single point and watch
  the very first few seconds turn into a genuine near-miss cluster before
  the fleet fans out — a realistic illustration of why real depots space
  charging/staging slots apart rather than packing them together.
