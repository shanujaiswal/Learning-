"""
05 - Robotics Perception and SLAM: Mini 2D Occupancy-Grid SLAM Demo
======================================================================

Companion practical for Theory/05 Robotics Perception and SLAM.md

This script simulates a mobile robot driving a known path through a
small 2D world containing obstacles, while only ever having access to:
    - noisy odometry (from commanded velocity + drifting encoder/IMU-like
      noise, exactly like the Sensors chapter's proprioceptive drift), and
    - a noisy simulated mini-LIDAR: a fixed number of range rays cast
      from the robot's true pose out to the nearest obstacle in each
      direction, each corrupted with Gaussian range noise.

From only those noisy proprioceptive + exteroceptive readings it:
    1. Tracks an ESTIMATED pose via dead-reckoning (integrating noisy
       odometry every step) -- and shows this estimate drifting away
       from the true pose over time, exactly as the theory warns.
    2. Builds an OCCUPANCY GRID MAP by projecting each noisy lidar hit
       from the *estimated* pose into world cells and accumulating a
       simple log-odds occupancy update per cell (the standard
       occupancy-grid-mapping approach) -- each additional independent
       observation of a cell makes the map more confident, another
       instance of "never trust one reading, combine many."

This is a simplified, pedagogical dead-reckoning + occupancy-grid
demo, not a full Kalman/particle-filter SLAM implementation -- but it
demonstrates the same core loop the theory describes: predict (move),
observe (sense), and fuse many noisy observations into one map and
pose estimate.

Run:
    pip install numpy matplotlib
    python 05_slam_perception.py
"""

import numpy as np
import matplotlib.pyplot as plt

RNG = np.random.default_rng(7)

# ---------------------------------------------------------------------------
# World: a small square world with a handful of rectangular obstacles
# ---------------------------------------------------------------------------

WORLD_SIZE = 10.0  # meters, world spans [0, WORLD_SIZE] in x and y
OBSTACLES = [
    # (x_min, y_min, x_max, y_max)
    (2.0, 2.0, 2.5, 6.0),
    (5.0, 1.0, 7.5, 1.5),
    (6.5, 4.0, 7.0, 8.0),
    (1.0, 8.0, 4.0, 8.5),
]

GRID_RESOLUTION = 0.2  # meters per occupancy-grid cell
GRID_CELLS = int(WORLD_SIZE / GRID_RESOLUTION)


def ray_distance_to_obstacles(x, y, angle, max_range=6.0):
    """Cast a ray from (x, y) at `angle`, return the true distance to the
    nearest obstacle boundary or wall (simple slab/step-march raycast).
    """
    step = 0.02
    dist = 0.0
    dx, dy = np.cos(angle), np.sin(angle)
    while dist < max_range:
        px, py = x + dx * dist, y + dy * dist
        if px <= 0 or px >= WORLD_SIZE or py <= 0 or py >= WORLD_SIZE:
            return dist
        for (xmin, ymin, xmax, ymax) in OBSTACLES:
            if xmin <= px <= xmax and ymin <= py <= ymax:
                return dist
        dist += step
    return max_range


def simulate_lidar(x, y, heading, n_rays=36, max_range=6.0, range_noise_std=0.05):
    """Cast n_rays evenly spaced around the robot, return noisy ranges."""
    angles = heading + np.linspace(0, 2 * np.pi, n_rays, endpoint=False)
    ranges = np.array([ray_distance_to_obstacles(x, y, a, max_range) for a in angles])
    noisy_ranges = ranges + RNG.normal(0, range_noise_std, size=ranges.shape)
    noisy_ranges = np.clip(noisy_ranges, 0, max_range)
    return angles, noisy_ranges


# ---------------------------------------------------------------------------
# Occupancy grid (log-odds representation, the standard approach)
# ---------------------------------------------------------------------------


class OccupancyGrid:
    """Log-odds occupancy grid: each cell accumulates evidence of being
    free (negative log-odds) or occupied (positive log-odds) from
    repeated, independent noisy ray observations.
    """

    LOG_ODDS_OCCUPIED = 0.85
    LOG_ODDS_FREE = -0.4
    LOG_ODDS_CLAMP = 8.0

    def __init__(self, size_cells, resolution):
        self.resolution = resolution
        self.log_odds = np.zeros((size_cells, size_cells))

    def world_to_cell(self, x, y):
        # Small epsilon guards against float round-down (e.g. 2.4/0.2
        # landing on 11.999999999998 instead of 12.0) causing an
        # off-by-one cell relative to consistent floor-based indexing.
        eps = 1e-9
        cx = int(np.clip(x / self.resolution + eps, 0, self.log_odds.shape[0] - 1))
        cy = int(np.clip(y / self.resolution + eps, 0, self.log_odds.shape[1] - 1))
        return cx, cy

    def integrate_ray(self, x0, y0, angle, measured_range, max_range):
        """Mark cells along the ray as free, and the end cell (if it's a
        real hit, not max-range) as occupied -- standard beam-model update.
        """
        step = self.resolution * 0.5
        dist = 0.0
        dx, dy = np.cos(angle), np.sin(angle)
        while dist < measured_range:
            px, py = x0 + dx * dist, y0 + dy * dist
            if 0 <= px < WORLD_SIZE and 0 <= py < WORLD_SIZE:
                cx, cy = self.world_to_cell(px, py)
                self.log_odds[cx, cy] += self.LOG_ODDS_FREE
                self.log_odds[cx, cy] = np.clip(
                    self.log_odds[cx, cy], -self.LOG_ODDS_CLAMP, self.LOG_ODDS_CLAMP)
            dist += step

        if measured_range < max_range - 1e-6:
            hx, hy = x0 + dx * measured_range, y0 + dy * measured_range
            if 0 <= hx < WORLD_SIZE and 0 <= hy < WORLD_SIZE:
                cx, cy = self.world_to_cell(hx, hy)
                self.log_odds[cx, cy] += self.LOG_ODDS_OCCUPIED
                self.log_odds[cx, cy] = np.clip(
                    self.log_odds[cx, cy], -self.LOG_ODDS_CLAMP, self.LOG_ODDS_CLAMP)

    def probability_map(self):
        return 1.0 / (1.0 + np.exp(-self.log_odds))


# ---------------------------------------------------------------------------
# Robot: dead-reckoning pose estimate vs true (simulated ground truth) pose
# ---------------------------------------------------------------------------


class Robot:
    """Tracks TRUE pose (ground truth, only used by the simulator to
    generate sensor data) and an ESTIMATED pose obtained purely by
    integrating noisy commanded odometry -- dead reckoning, the simplest
    (and most drift-prone) form of the "predict" half of SLAM.
    """

    def __init__(self, x, y, heading):
        self.true_x, self.true_y, self.true_heading = x, y, heading
        self.est_x, self.est_y, self.est_heading = x, y, heading

    def move(self, forward_speed, angular_speed, dt,
              odom_noise_std=0.02, heading_noise_std=0.01):
        # --- True motion (ground truth) ---
        self.true_heading += angular_speed * dt
        self.true_x += forward_speed * np.cos(self.true_heading) * dt
        self.true_y += forward_speed * np.sin(self.true_heading) * dt

        # --- Noisy odometry used for the pose ESTIMATE (dead reckoning) ---
        noisy_forward = forward_speed + RNG.normal(0, odom_noise_std)
        noisy_angular = angular_speed + RNG.normal(0, heading_noise_std)
        self.est_heading += noisy_angular * dt
        self.est_x += noisy_forward * np.cos(self.est_heading) * dt
        self.est_y += noisy_forward * np.sin(self.est_heading) * dt


# ---------------------------------------------------------------------------
# Demo
# ---------------------------------------------------------------------------


def main():
    print("=" * 70)
    print("MINI 2D SLAM DEMO: DEAD-RECKONING POSE + OCCUPANCY GRID MAPPING")
    print("=" * 70)
    print(f"World: {WORLD_SIZE}x{WORLD_SIZE} m, {len(OBSTACLES)} rectangular "
          f"obstacles, grid resolution={GRID_RESOLUTION} m/cell\n")

    robot = Robot(x=1.0, y=1.0, heading=0.3)
    occupancy_grid = OccupancyGrid(GRID_CELLS, GRID_RESOLUTION)

    dt = 0.2
    n_steps = 120
    forward_speed = 0.5   # m/s
    max_lidar_range = 6.0

    true_traj_x, true_traj_y = [], []
    est_traj_x, est_traj_y = [], []

    for step in range(n_steps):
        # Simple driving pattern: gentle loop around the interior
        angular_speed = 0.15 * np.sin(step / 15.0)
        robot.move(forward_speed, angular_speed, dt)

        true_traj_x.append(robot.true_x)
        true_traj_y.append(robot.true_y)
        est_traj_x.append(robot.est_x)
        est_traj_y.append(robot.est_y)

        # Lidar is physically mounted on the robot -> uses TRUE pose to
        # generate realistic returns, but the MAP is built using the
        # robot's (imperfect) pose ESTIMATE, exactly the real-world
        # situation where you never have access to ground truth.
        angles, ranges = simulate_lidar(robot.true_x, robot.true_y,
                                         robot.true_heading, n_rays=36,
                                         max_range=max_lidar_range)
        for angle, measured_range in zip(angles, ranges):
            occupancy_grid.integrate_ray(robot.est_x, robot.est_y, angle,
                                          measured_range, max_lidar_range)

    true_traj_x, true_traj_y = np.array(true_traj_x), np.array(true_traj_y)
    est_traj_x, est_traj_y = np.array(est_traj_x), np.array(est_traj_y)

    # -----------------------------------------------------------------
    # Report pose drift
    # -----------------------------------------------------------------
    pose_errors = np.hypot(true_traj_x - est_traj_x, true_traj_y - est_traj_y)
    print(f"Steps simulated: {n_steps}  (dt={dt}s, {n_steps * dt:.1f}s total)")
    print(f"Dead-reckoning position error: start={pose_errors[0]:.4f} m, "
          f"mid={pose_errors[n_steps // 2]:.4f} m, "
          f"final={pose_errors[-1]:.4f} m")
    assert pose_errors[-1] > pose_errors[0], \
        "Dead-reckoning error should grow over time (drift), as the theory predicts"
    print("Check: pose estimate drifted away from ground truth over time, "
          "matching the theory's drift warning.\n")

    # -----------------------------------------------------------------
    # Report map quality: check that grid cells at true obstacle
    # locations were correctly identified as occupied (p > 0.5), and
    # a known open area was correctly identified as free (p < 0.5)
    # -----------------------------------------------------------------
    prob_map = occupancy_grid.probability_map()

    def cell_prob(x, y):
        cx, cy = occupancy_grid.world_to_cell(x, y)
        return prob_map[cx, cy]

    obstacle_sample = (2.4, 4.0)   # on the near face of the first obstacle
    free_sample = (1.0, 5.0)       # open area the robot passes near

    p_obstacle = cell_prob(*obstacle_sample)
    p_free = cell_prob(*free_sample)
    print(f"Occupancy probability at known obstacle location {obstacle_sample}: "
          f"{p_obstacle:.3f}")
    print(f"Occupancy probability at known free-space location {free_sample}: "
          f"{p_free:.3f}")
    assert p_obstacle > 0.5, "Known obstacle cell should be mapped as occupied"
    assert p_free < 0.5, "Known free-space cell should be mapped as free"
    print("Check: occupancy grid correctly distinguished obstacle vs free space "
          "from noisy accumulated lidar returns.\n")

    n_confident_occupied = np.sum(prob_map > 0.7)
    n_confident_free = np.sum(prob_map < 0.3)
    print(f"Confidently occupied cells: {n_confident_occupied}  |  "
          f"confidently free cells: {n_confident_free}  |  "
          f"unknown cells: {prob_map.size - n_confident_occupied - n_confident_free}")

    # -----------------------------------------------------------------
    # Plot: occupancy grid map + true vs estimated trajectory
    # -----------------------------------------------------------------
    fig, axes = plt.subplots(1, 2, figsize=(14, 6.5))
    fig.suptitle("Mini SLAM Demo: Occupancy Grid Mapping + Dead-Reckoning Drift")

    ax = axes[0]
    # Ground-truth obstacles for reference
    for (xmin, ymin, xmax, ymax) in OBSTACLES:
        ax.add_patch(plt.Rectangle((xmin, ymin), xmax - xmin, ymax - ymin,
                                    facecolor="none", edgecolor="red",
                                    linewidth=1.5, linestyle="--"))
    ax.imshow(prob_map.T, origin="lower", cmap="Greys",
              extent=[0, WORLD_SIZE, 0, WORLD_SIZE], vmin=0, vmax=1)
    ax.plot(true_traj_x, true_traj_y, color="tab:blue", linewidth=1.5, label="true path")
    ax.plot(est_traj_x, est_traj_y, color="tab:orange", linewidth=1.5,
            linestyle="--", label="estimated (dead-reckoning) path")
    ax.set_title("Occupancy grid (grey=P(occupied)) + true obstacles (red dashed)")
    ax.set_xlabel("x (m)")
    ax.set_ylabel("y (m)")
    ax.legend(fontsize=8, loc="upper right")
    ax.set_xlim(0, WORLD_SIZE)
    ax.set_ylim(0, WORLD_SIZE)
    ax.set_aspect("equal")

    ax = axes[1]
    ax.plot(np.arange(n_steps) * dt, pose_errors, color="tab:red")
    ax.set_title("Dead-reckoning position error over time (drift)")
    ax.set_xlabel("time (s)")
    ax.set_ylabel("position error (m)")
    ax.grid(alpha=0.3)

    plt.tight_layout()
    out_path = "slam_perception_demo.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()
