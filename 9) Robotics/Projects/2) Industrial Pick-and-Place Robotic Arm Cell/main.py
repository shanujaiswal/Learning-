"""Industrial pick-and-place arm cell -- ROS2-style node graph, end to end.

A 3-DOF servo-driven benchtop arm (base yaw + shoulder + elbow) sits next to
a conveyor belt. Parts arrive at a fixed photoelectric-sensor pick station
strictly every `TAKT_TIME_S` seconds (the upstream fill/cap/label process
sets that rhythm, not the arm). Every time the sensor trips, the arm must
run a full pick -> place cycle -- approach, descend, grasp, lift, move to
the part-type's bin, descend, release, retreat, return home -- and finish
before the NEXT part arrives, or it falls behind (backlog).

Run:
    pip install numpy matplotlib
    python main.py
"""

import numpy as np
import matplotlib.pyplot as plt

import ros2_lite as rclpy
from conveyor import ConveyorFeeder
from servo_hal import ServoDriverHAL
from nodes import ConveyorNode, ServoDriverNode, ArmCellNode
import kinematics

N_PARTS = 9
TAKT_TIME_S = 8.0            # fixed interval between part arrivals on the belt
TICK_PERIOD_S = 0.05         # simulated control-loop period (20 Hz)
MOVE_TIME_S = 1.2            # quintic duration for a horizontal approach/bin move
VERTICAL_TIME_S = 0.6        # quintic duration for a descend/lift move
DWELL_TIME_S = 0.4           # grasp/release pause (must exceed gripper slew time)
MAX_SIM_TIME_S = 300.0       # safety cap so a bug can't hang the run forever


def main():
    print("=" * 78)
    print("INDUSTRIAL PICK-AND-PLACE ROBOTIC ARM CELL")
    print("=" * 78)
    print(f"Takt time: {TAKT_TIME_S:.1f}s/part  |  Parts to run: {N_PARTS}\n")

    rclpy.reset_bus()
    clock = rclpy.SimClock(dt_s=TICK_PERIOD_S)

    feeder = ConveyorFeeder(takt_time_s=TAKT_TIME_S, n_parts=N_PARTS, rng_seed=7)
    home_deg = kinematics.joints_rad_to_servo_deg(
        kinematics.inverse_kinematics(*ArmCellNode.HOME_XYZ))
    hal = ServoDriverHAL(num_channels=4, slew_rate_deg_s=300.0, initial_deg=90.0)
    hal.set_angles(home_deg)   # start the arm already parked at HOME, not mid-swing
    hal.step(999.0)            # let the slew model snap to HOME before t=0

    conveyor_node = ConveyorNode(feeder, clock)
    servo_node = ServoDriverNode(hal, clock)
    cell_node = ArmCellNode(clock, takt_time_s=TAKT_TIME_S, tick_period_s=TICK_PERIOD_S,
                             move_time_s=MOVE_TIME_S, vertical_time_s=VERTICAL_TIME_S,
                             dwell_time_s=DWELL_TIME_S)

    nodes = [conveyor_node, servo_node, cell_node]

    print("Running the cell...\n")
    while clock.t < MAX_SIM_TIME_S:
        clock.t += clock.dt
        for node in nodes:
            for timer in node._timers:
                period_s, callback, elapsed = timer
                elapsed += clock.dt
                if elapsed >= period_s:
                    elapsed = 0.0
                    callback()
                timer[2] = elapsed
        if feeder.done and cell_node.is_idle_and_done:
            break

    if not (feeder.done and cell_node.is_idle_and_done):
        print("\nWARNING: hit the simulation time cap before the cell finished all parts.")

    # -- summary ------------------------------------------------------------
    cycles = cell_node.completed_cycles
    n_done = len(cycles)
    cycle_times = np.array([c["cycle_time_s"] for c in cycles])
    n_misses = sum(1 for c in cycles if not c["beat_takt"])
    max_backlog = max((c["backlog_after"] for c in cycles), default=0)

    print("\n" + "=" * 78)
    print("SUMMARY")
    print("=" * 78)
    print(f"Parts completed:        {n_done}/{N_PARTS}")
    print(f"Average cycle time:     {cycle_times.mean():.2f}s  (takt = {TAKT_TIME_S:.2f}s)")
    print(f"Min / max cycle time:   {cycle_times.min():.2f}s / {cycle_times.max():.2f}s")
    print(f"Takt-time misses:       {n_misses}/{n_done}")
    print(f"Max backlog reached:    {max_backlog} part(s) waiting")
    print(f"Total sim time:         {clock.t:.1f}s")
    for c in cycles:
        status = "OK  " if c["beat_takt"] else "MISS"
        print(f"  part #{c['part_id']} (type {c['part_type']}): "
              f"{c['cycle_time_s']:.2f}s [{status}] backlog_after={c['backlog_after']}")

    # -- plot -----------------------------------------------------------
    joint_log = np.array(cell_node.joint_log)
    ee_path = np.array(cell_node.ee_path)

    fig, axes = plt.subplots(1, 2, figsize=(15, 6.5))
    fig.suptitle("Industrial Pick-and-Place Arm Cell")

    ax = axes[0]
    labels = ["base yaw", "shoulder", "elbow", "gripper"]
    for i, label in enumerate(labels):
        ax.plot(joint_log[:, 0], joint_log[:, i + 1], linewidth=1.3, label=label)
    ax.set_xlabel("time (s)")
    ax.set_ylabel("servo angle (deg)")
    ax.set_title("Joint angle trajectories (actual, slew-limited)")
    ax.legend(fontsize=8, loc="upper right")
    ax.grid(alpha=0.3)

    ax = axes[1]
    sc = ax.scatter(ee_path[:, 1], ee_path[:, 2], c=ee_path[:, 0], cmap="viridis", s=4)
    pick_x, pick_y, _ = ArmCellNode.PICK_XYZ
    ax.plot(pick_x, pick_y, "s", color="red", markersize=10, label="pick station")
    for name, (bx, by, _) in ArmCellNode.BIN_XYZ.items():
        ax.plot(bx, by, "^", markersize=10, label=f"bin {name}")
    home_x, home_y, _ = ArmCellNode.HOME_XYZ
    ax.plot(home_x, home_y, "o", color="black", markersize=8, label="home")
    ax.set_xlabel("x (m)")
    ax.set_ylabel("y (m)")
    ax.set_title("End-effector path (top-down), colored by time")
    ax.set_aspect("equal")
    ax.legend(fontsize=8, loc="upper left")
    ax.grid(alpha=0.3)
    fig.colorbar(sc, ax=ax, label="t (s)", shrink=0.8)

    plt.tight_layout()
    out_path = "arm_cell_result.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")


if __name__ == "__main__":
    main()
