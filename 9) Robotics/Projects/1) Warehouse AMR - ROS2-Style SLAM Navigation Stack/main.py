"""Warehouse AMR -- ROS2-style SLAM navigation stack, end to end.

Phase 1 (Explore):  drive a sweeping pattern, build the occupancy-grid map.
Phase 2 (Plan):     A* runs once `NavigationNode` sees a map on `/map`.
Phase 3 (Execute):  follow the plan, swerving live around the forklift via
                    potential-field local avoidance, without ever
                    re-running A*.

Run:
    pip install numpy matplotlib
    python main.py
"""

import numpy as np
import matplotlib.pyplot as plt

import world
import hardware
import ros2_lite as rclpy
from nodes import (LidarNode, OdometryNode, MappingNode, MotorDriverNode,
                    ExplorationDriverNode, NavigationNode)

START_XY = (1.0, 4.5)
START_HEADING = 0.4
GOAL_XY = (12.5, 12.0)


def main():
    print("=" * 78)
    print("WAREHOUSE AMR -- ROS2-STYLE SLAM NAVIGATION STACK")
    print("=" * 78)

    clock = rclpy.SimClock(dt_s=0.1)
    hal_drive = hardware.SimulatedDiffDriveHAL(*START_XY, START_HEADING)
    hal_lidar = hardware.SimulatedLidar2DHAL(hal_drive)

    lidar_node = LidarNode(hal_lidar, clock)
    odom_node = OdometryNode(hal_drive, clock)
    mapping_node = MappingNode(clock)
    motor_node = MotorDriverNode(hal_drive, clock)

    # --- Phase 1: Explore ---------------------------------------------
    print("\nPhase 1/3 -- Exploring the floor to build the occupancy map...")
    explorer = ExplorationDriverNode(clock)
    rclpy.spin([lidar_node, odom_node, mapping_node, motor_node, explorer], 90.0, clock)
    explore_trail = np.array(mapping_node.trail)
    true_x, true_y, _ = hal_drive.true_pose()
    odom_x, odom_y, _ = hal_drive.read_wheel_odometry()
    drift_m = np.hypot(true_x - odom_x, true_y - odom_y)
    print(f"Exploration done at t={clock.t:.1f}s. Odometry drift from ground truth: {drift_m:.2f} m")
    print(f"Occupancy grid: {np.sum(mapping_node.grid_map.probability_map() > 0.7)} confidently-occupied "
          f"cells, {np.sum(mapping_node.grid_map.probability_map() < 0.3)} confidently-free cells.")

    # --- Phase 2 + 3: Plan (inside NavigationNode) then Execute --------
    print(f"\nPhase 2/3 -- Planning a route to goal {GOAL_XY} across the self-built map...")
    print("Phase 3/3 -- Executing the route, reacting live to the forklift...")
    navigator = NavigationNode(GOAL_XY, clock)
    rclpy.spin([lidar_node, odom_node, mapping_node, motor_node, navigator], clock.t + 150.0, clock)
    print(f"Navigation finished with {navigator.replan_count} live replan(s) "
          f"triggered by obstacles the initial map didn't know about.")

    if not navigator.reached_goal:
        print("\nWARNING: goal was not reached within the time budget.")
    exec_trail = np.array(navigator.executed_trail)

    # --- Plot ------------------------------------------------------------
    prob_map = mapping_node.grid_map.probability_map()
    fig, axes = plt.subplots(1, 2, figsize=(15, 7))
    fig.suptitle("Warehouse AMR: SLAM Map, A* Plan, and Executed Path")

    ax = axes[0]
    ax.imshow(prob_map.T, origin="lower", cmap="Greys",
              extent=[0, world.FLOOR_SIZE, 0, world.FLOOR_SIZE], vmin=0, vmax=1)
    for (xmin, ymin, xmax, ymax) in world.SHELF_RACKS:
        ax.add_patch(plt.Rectangle((xmin, ymin), xmax - xmin, ymax - ymin,
                                    facecolor="none", edgecolor="red", linewidth=1.2, linestyle="--"))
    ax.plot(explore_trail[:, 0], explore_trail[:, 1], color="tab:blue", linewidth=1, alpha=0.6, label="exploration path")
    ax.set_title("SLAM occupancy map (grey=P(occupied)) + true shelves (red dashed)")
    ax.set_xlabel("x (m)"); ax.set_ylabel("y (m)")
    ax.set_xlim(0, world.FLOOR_SIZE); ax.set_ylim(0, world.FLOOR_SIZE)
    ax.set_aspect("equal"); ax.legend(fontsize=8, loc="upper left")

    ax = axes[1]
    ax.imshow(prob_map.T, origin="lower", cmap="Greys",
              extent=[0, world.FLOOR_SIZE, 0, world.FLOOR_SIZE], vmin=0, vmax=1)
    if navigator.path is not None:
        path_arr = np.array(navigator.path)
        ax.plot(path_arr[:, 0], path_arr[:, 1], color="tab:green", linewidth=2, linestyle=":", label="A* plan")
    ax.plot(exec_trail[:, 0], exec_trail[:, 1], color="tab:orange", linewidth=1.8, label="executed path")
    ax.plot(*START_XY, "o", color="black", markersize=7, label="start")
    ax.plot(*GOAL_XY, "*", color="gold", markeredgecolor="black", markersize=16, label="goal")
    forklift_pos = world.FORKLIFT.position_at(clock.t)
    ax.add_patch(plt.Circle(forklift_pos, world.FORKLIFT.radius, color="purple", alpha=0.5, label="forklift (final pos.)"))
    ax.set_title("A* plan vs. actually-executed path (with live forklift avoidance)")
    ax.set_xlabel("x (m)"); ax.set_ylabel("y (m)")
    ax.set_xlim(0, world.FLOOR_SIZE); ax.set_ylim(0, world.FLOOR_SIZE)
    ax.set_aspect("equal"); ax.legend(fontsize=8, loc="upper left")

    plt.tight_layout()
    out_path = "warehouse_amr_result.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()
