"""Warehouse Fleet Coordination -- multi-robot order fulfillment, end to end.

A batch of pick orders (shelf locations that need visiting) arrives at
once. `FleetManagerNode` auctions them out to whichever robots are idle
(Bertsekas auction algorithm, `task_allocation.py`), and every robot drives
to its assigned pick location via an A* route across the known facility
map, using potential-field local control to react to shelf racks AND to
its fleet-mates in real time. As soon as a robot finishes a pick, it goes
idle and gets auctioned the next-cheapest open task -- exactly the
"tasks arrive in a batch, robots free up one at a time" loop a real
warehouse fleet-management system runs all shift long.

Run:
    pip install numpy matplotlib
    python main.py
"""

import numpy as np
import matplotlib.pyplot as plt

import world
import hardware
import ros2_lite as rclpy
from nodes import OdometryNode, MotorDriverNode, RobotControllerNode, FleetManagerNode

N_ROBOTS = 5
N_TASKS = 18                      # > N_ROBOTS so every robot gets re-assigned multiple times
SIM_SECONDS_BUDGET = 260.0
NEAR_MISS_THRESHOLD_M = 0.8        # inside this, robots are "close enough to notice each other"
BODY_DIAMETER_M = 0.44             # 2 x body_radius_m -- an actual physical overlap
RNG_SEED = 3

# Depot slots at the pack station -- a real fleet parks/charges its idle
# robots in a small staging area near the pack station between batches,
# spaced far enough apart that they don't start the run already touching.
DEPOT_SLOTS = [
    (12.4, 0.6), (13.0, 0.6), (13.6, 0.6),
    (12.4, 1.2), (13.0, 1.2), (13.6, 1.2),
]


def main():
    print("=" * 78)
    print("WAREHOUSE FLEET COORDINATION -- AUCTION-BASED MULTI-ROBOT TASK ALLOCATION")
    print("=" * 78)

    rng = np.random.default_rng(RNG_SEED)
    rclpy.reset_bus()
    clock = rclpy.SimClock(dt_s=0.1)

    namespaces = [f"robot_{i}" for i in range(N_ROBOTS)]
    hals = {
        ns: hardware.SimulatedDiffDriveHAL(*DEPOT_SLOTS[i], heading0=np.pi, seed=1000 + i)
        for i, ns in enumerate(namespaces)
    }

    all_nodes = []
    odom_nodes, controller_nodes = {}, {}
    for ns in namespaces:
        odom_node = OdometryNode(ns, hals[ns], clock)
        motor_node = MotorDriverNode(ns, hals[ns], clock)
        odom_nodes[ns] = odom_node
        all_nodes += [odom_node, motor_node]
    for ns in namespaces:
        others = [o for o in namespaces if o != ns]
        ctrl_node = RobotControllerNode(ns, others, clock)
        controller_nodes[ns] = ctrl_node
        all_nodes.append(ctrl_node)

    task_ids = rng.choice(list(world.PICK_LOCATIONS.keys()), size=N_TASKS, replace=False)
    task_queue = {tid: world.PICK_LOCATIONS[tid] for tid in task_ids}
    fleet = FleetManagerNode(namespaces, task_queue, clock, assignment_period_s=0.5)
    all_nodes.append(fleet)

    print(f"\nFleet: {N_ROBOTS} robots, {N_TASKS} pick orders queued at t=0.")
    print("Running auction-based allocation + reactive collision avoidance...\n")
    rclpy.spin(all_nodes, SIM_SECONDS_BUDGET, clock)

    # --- Fleet metrics -----------------------------------------------------
    n_done = len(fleet.completed_tasks)
    if n_done < fleet.total_tasks:
        print(f"WARNING: only {n_done}/{fleet.total_tasks} tasks completed "
              f"within the {SIM_SECONDS_BUDGET:.0f}s simulation budget.")
    completion_times = [t for (_, t) in fleet.completed_tasks.values()]
    makespan = max(completion_times) if completion_times else 0.0

    print("\n" + "-" * 78)
    print("FLEET METRICS")
    print("-" * 78)
    print(f"Tasks completed:      {n_done} / {fleet.total_tasks}")
    print(f"Makespan (last pick):  {makespan:.1f} s")
    print(f"Total auction assignments made: {len(fleet.assignment_log)}")

    print("\nPer-robot utilization (busy time / makespan):")
    utilizations = {}
    tasks_per_robot = {ns: 0 for ns in namespaces}
    for tid, (ns, t) in fleet.completed_tasks.items():
        tasks_per_robot[ns] += 1
    for ns in namespaces:
        ctrl = controller_nodes[ns]
        util = ctrl.busy_time_s / makespan if makespan > 0 else 0.0
        utilizations[ns] = util
        print(f"  {ns}: {util*100:5.1f}% busy, {tasks_per_robot[ns]} task(s) completed, "
              f"{ctrl.busy_time_s:.1f}s busy time")
    avg_util = np.mean(list(utilizations.values()))
    print(f"  Fleet-average utilization: {avg_util*100:.1f}%")

    # --- Near-collision events, computed from the recorded trails ----------
    trails = {ns: np.array(controller_nodes[ns].executed_trail) for ns in namespaces}
    min_len = min(len(t) for t in trails.values())
    positions = np.stack([trails[ns][:min_len, 1:3] for ns in namespaces], axis=1)  # (T, N, 2)
    times = trails[namespaces[0]][:min_len, 0]

    diffs = positions[:, :, None, :] - positions[:, None, :, :]                     # (T, N, N, 2)
    dist = np.linalg.norm(diffs, axis=-1)                                          # (T, N, N)
    n = len(namespaces)
    iu = np.triu_indices(n, 1)
    pair_dist = dist[:, iu[0], iu[1]]                                              # (T, n_pairs)

    close = pair_dist < NEAR_MISS_THRESHOLD_M
    # Count RISING edges per pair (transition into "close") -- so a robot
    # pair lingering close for many consecutive ticks counts as ONE event,
    # not one event per tick, the same way an ops dashboard would log
    # "N near-miss incidents" rather than "N ticks spent near a neighbor".
    rising = close[1:] & ~close[:-1]
    n_near_miss_events = int(np.sum(rising)) + int(np.sum(close[0]))  # include any starting already-close
    min_dist_ever = float(pair_dist[1:].min()) if len(pair_dist) > 1 else float(pair_dist.min())
    n_actual_collisions = int(np.sum(pair_dist[1:] < BODY_DIAMETER_M))

    print(f"\nNear-collision events (< {NEAR_MISS_THRESHOLD_M} m separation) detected & avoided: "
          f"{n_near_miss_events}")
    print(f"Closest any two robots ever got:                        {min_dist_ever:.2f} m "
          f"(body diameter is {BODY_DIAMETER_M} m)")
    if n_actual_collisions > 0:
        print(f"WARNING: {n_actual_collisions} tick(s) with actual body overlap between robots!")
    else:
        print("No actual body-to-body collisions occurred -- reactive separation held.")

    # --- Plot ----------------------------------------------------------------
    colors = plt.cm.tab10(np.linspace(0, 1, N_ROBOTS))
    fig, axes = plt.subplots(1, 2, figsize=(16, 7.5))
    fig.suptitle("Warehouse Fleet Coordination: Auction-Based Task Allocation")

    ax = axes[0]
    for (xmin, ymin, xmax, ymax) in world.SHELF_RACKS:
        ax.add_patch(plt.Rectangle((xmin, ymin), xmax - xmin, ymax - ymin,
                                    facecolor="lightgray", edgecolor="black", linewidth=1.0))
    for ns, color in zip(namespaces, colors):
        trail = trails[ns]
        ax.plot(trail[:, 1], trail[:, 2], color=color, linewidth=1.3, alpha=0.85, label=f"{ns} trajectory")
        ax.plot(trail[0, 1], trail[0, 2], "s", color=color, markersize=8, markeredgecolor="black")
    for tid, (ns, _) in fleet.completed_tasks.items():
        tx, ty = task_queue[tid]
        color = colors[namespaces.index(ns)]
        ax.plot(tx, ty, "*", color=color, markersize=14, markeredgecolor="black", markeredgewidth=0.6)
    ax.set_title("Executed trajectories (■ = depot start, ★ = pick task, color = servicing robot)")
    ax.set_xlabel("x (m)"); ax.set_ylabel("y (m)")
    ax.set_xlim(0, world.FLOOR_SIZE); ax.set_ylim(0, world.FLOOR_SIZE)
    ax.set_aspect("equal")
    ax.legend(fontsize=7, loc="upper left", ncol=1)

    ax = axes[1]
    for ns, color in zip(namespaces, colors):
        ctrl = controller_nodes[ns]
        ax.barh(ns, utilizations[ns] * 100, color=color)
    ax.set_xlabel("Utilization (% of makespan spent busy)")
    ax.set_title(f"Per-robot utilization -- makespan {makespan:.0f}s, "
                 f"{n_done}/{fleet.total_tasks} tasks, "
                 f"{n_near_miss_events} near-miss event(s) avoided")
    ax.set_xlim(0, 100)
    ax.grid(axis="x", alpha=0.3)

    plt.tight_layout()
    out_path = "fleet_coordination_result.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()
