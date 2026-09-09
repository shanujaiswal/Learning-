# Mobile Robotics, Localization and Autonomous Navigation

- Mobile robot platforms and locomotion methods
- Localization techniques: odometry, GPS, and Monte Carlo localization
- Autonomous navigation pipelines and path planning
- Obstacle avoidance and sensor fusion for mobile robots
- Practical considerations for indoor and outdoor navigation
- Integration with ROS for autonomous robot behavior

## Mobile Robot Platforms and Locomotion Methods

Mobile robots use wheels, tracks, legs, or hybrid mechanisms. Each locomotion method has tradeoffs in terrain adaptability, speed, stability, and mechanical complexity.

## Localization Techniques: Odometry, GPS, and Monte Carlo Localization

Localization estimates a robot’s position in the environment. Odometry uses wheel encoders and motion models, GPS provides global positioning outdoors, and Monte Carlo localization uses particle filters to fuse sensor data into a probabilistic estimate.

## Autonomous Navigation Pipelines and Path Planning

Autonomous navigation combines mapping, localization, path planning, and motion control. Path planning algorithms such as A*, D*, RRT, and obstacle-aware graph search generate safe trajectories from start to goal.

## Obstacle Avoidance and Sensor Fusion for Mobile Robots

Obstacle avoidance uses sensors like lidar, sonar, and cameras to detect hazards. Sensor fusion combines data from multiple sources to produce a reliable world model and support reactive navigation around moving or static obstacles.

## Practical Considerations for Indoor and Outdoor Navigation

Indoor navigation often relies on SLAM and landmarks, while outdoor navigation may use GPS, visual odometry, and large-area maps. Environmental factors like lighting, uneven terrain, and signal availability influence sensor choice and navigation strategy.

## Integration with ROS for Autonomous Robot Behavior

ROS provides tools and libraries for mobile robotics, including mapping, localization, planning, and control packages. Integrating ROS components enables modular development of autonomous behavior and easier experimentation with navigation stacks.

# Why Chapter 06 Needs a Deep Dive

--> Chapter 06 correctly names the right concepts -- odometry, GPS, Monte Carlo localization, A*/D*/RRT, sensor fusion -- but states them as one-line definitions with no worked math and no code, unlike chapters 01-05. This file fills that gap: a concrete localization estimator built from the Kalman-filter machinery chapter 05 already established, and a concrete navigation-stack example (costmap + planner) showing how a real mobile robot turns a map and a pose estimate into an actual velocity command.

# Worked Example: Odometry + IMU Fusion With an Extended Kalman Filter

--> Chapter 06 says "odometry uses wheel encoders and motion models" and stops there. In isolation, wheel odometry alone is exactly the drifting proprioceptive estimate chapter 02 warns about -- small per-step errors (wheel slip, encoder quantization) accumulate into large positional drift over time, with nothing to correct it. The standard fix, and a direct application of chapter 05's Kalman filter predict/update cycle, is to fuse odometry (which is good at short-term relative motion) with an IMU's heading rate (which is good at short-term rotational rate but drifts on position) and, when available, an absolute sensor (GPS or a SLAM pose) that periodically pulls the estimate back to ground truth.

--> The state to track for a 2D mobile robot is `[x, y, theta]` -- position and heading. The **predict** step uses the odometry motion model (differential-drive kinematics: forward distance and heading change from the two wheel encoders); the **update** step corrects heading using the IMU's measured yaw, which is far less prone to long-term drift than integrating wheel odometry's heading estimate alone.

```python
import math

class Pose2DEKF:
    """Extended Kalman Filter fusing differential-drive odometry (predict)
    with IMU heading (update). State: [x, y, theta]. This is the same
    predict/update anatomy as chapter 05's Kalman filter -- only the motion
    model is now nonlinear (heading rotates the direction of the position
    update), which is exactly why this is an *Extended* KF rather than a
    plain linear one."""

    def __init__(self, x=0.0, y=0.0, theta=0.0):
        self.x, self.y, self.theta = x, y, theta
        # Covariance: how uncertain we are about [x, y, theta]. Starts small
        # if we know our starting pose exactly; grows during predict.
        self.P = [[0.01, 0, 0], [0, 0.01, 0], [0, 0, 0.01]]
        self.Q = [[0.02, 0, 0], [0, 0.02, 0], [0, 0, 0.01]]  # process noise (odometry error)
        self.R_theta = 0.05  # measurement noise variance for the IMU heading reading

    def predict(self, delta_dist, delta_theta_odom):
        """delta_dist: forward distance traveled this tick, from wheel encoders.
        delta_theta_odom: heading change this tick, from wheel encoder differential."""
        # Motion model: move forward along the *current* heading, then rotate.
        # Using the midpoint heading (theta + delta/2) instead of the start
        # heading is a standard trick that halves the linearization error for
        # a given time step -- larger delta_theta needs finer discretization.
        mid_theta = self.theta + delta_theta_odom / 2.0
        self.x += delta_dist * math.cos(mid_theta)
        self.y += delta_dist * math.sin(mid_theta)
        self.theta += delta_theta_odom

        # Uncertainty grows every predict step, exactly as in chapter 05 --
        # we moved based on an imperfect model, so we trust our estimate less.
        for i in range(3):
            for j in range(3):
                self.P[i][j] += self.Q[i][j]

    def update_heading(self, imu_theta):
        """Correct heading using an independent IMU yaw reading. This is the
        chapter 05 Kalman 'update': blend prediction and measurement,
        weighted by relative confidence (Kalman gain)."""
        innovation = imu_theta - self.theta
        # Wrap to [-pi, pi] so a flip across the +/-180 degree boundary
        # doesn't get treated as a huge heading error.
        innovation = (innovation + math.pi) % (2 * math.pi) - math.pi

        S = self.P[2][2] + self.R_theta          # innovation covariance
        K = self.P[2][2] / S                      # Kalman gain for theta

        self.theta += K * innovation
        self.P[2][2] *= (1 - K)                   # confident IMU reading shrinks uncertainty

# Simulate 5 ticks: robot commanded to drive forward, encoders report
# ~0.5m/tick with a slight (unmodeled) rightward drift; IMU periodically
# corrects the accumulating heading error.
ekf = Pose2DEKF()
encoder_readings = [(0.5, 0.02)] * 5       # (delta_dist, delta_theta) per tick -- drifting right
imu_headings = [0.0, 0.0, 0.0, 0.0, 0.0]   # IMU says "you should be pointing straight ahead"

for (delta_dist, delta_theta), imu_theta in zip(encoder_readings, imu_headings):
    ekf.predict(delta_dist, delta_theta)
    ekf.update_heading(imu_theta)
    print(f"x={ekf.x:.3f} y={ekf.y:.3f} theta={math.degrees(ekf.theta):.2f} deg")
```

--> Without the IMU update, `theta` would drift to `5 * 0.02 = 0.1` radians (~5.7 degrees) purely from the encoders' unmodeled rightward bias, and that heading error would then bend every subsequent `x, y` prediction in the wrong direction, compounding. With the update step pulling `theta` back toward the IMU's reading every tick (weighted by the Kalman gain `K`, exactly as chapter 05 describes for a generic sensor), the heading error stays bounded instead of growing unboundedly -- this is the concrete mechanism behind chapter 06's one-line "sensor fusion combines data from multiple sources to produce a reliable world model."

--> Note what this fusion does *not* fix: if the IMU itself is biased (a real gyro has its own slow drift, as chapter 02 notes), `update_heading` will happily pull the estimate toward a wrong reading with full confidence. Fusion reduces reliance on any one sensor's flaws; it does not eliminate the need to characterize each sensor's actual error behavior in the first place.

# Worked Example: A Minimal Costmap + Planner Navigation Stack

--> Chapter 06 says "autonomous navigation combines mapping, localization, path planning, and motion control" and "path planning algorithms such as A*... generate safe trajectories," but never shows how a raw occupancy grid becomes a plannable, safety-margined map, nor how planning output becomes an actual velocity command. Real navigation stacks (ROS2 Nav2 being the standard example, building directly on the ROS2 concepts from chapter 04) insert a **costmap** layer between "raw obstacle map" and "planner," specifically so a planner doesn't route the robot along a path that grazes an obstacle by millimeters just because that cell wasn't technically occupied.

```python
import heapq

def inflate_costmap(occupancy_grid, inflation_radius=2):
    """Take a binary occupancy grid (1 = obstacle, 0 = free) and inflate
    obstacles by `inflation_radius` cells, assigning a decaying cost so cells
    near an obstacle are expensive (but not forbidden) and cells far from any
    obstacle are cheap. This directly encodes the robot's physical footprint
    and a safety margin into the planning problem, rather than treating the
    robot as an infinitesimal point -- the single most common gap between a
    "textbook A*" and a costmap a real robot can safely follow."""
    rows, cols = len(occupancy_grid), len(occupancy_grid[0])
    costmap = [[0.0] * cols for _ in range(rows)]

    obstacle_cells = [(r, c) for r in range(rows) for c in range(cols)
                      if occupancy_grid[r][c] == 1]

    for r in range(rows):
        for c in range(cols):
            if occupancy_grid[r][c] == 1:
                costmap[r][c] = float('inf')  # lethal -- never plan through this
                continue
            min_dist = min((abs(r - or_) + abs(c - oc) for or_, oc in obstacle_cells),
                           default=float('inf'))
            if min_dist <= inflation_radius:
                # Cost decays linearly with distance from the nearest obstacle.
                costmap[r][c] = (inflation_radius - min_dist + 1) * 10.0
            else:
                costmap[r][c] = 1.0  # baseline traversal cost, far from any obstacle
    return costmap

def astar(costmap, start, goal):
    """A* over the costmap, exactly the algorithm from chapter 05, but now
    g(n) accumulates *costmap cost*, not just cell count -- so the optimal
    path A* finds is the cheapest (safest) path, not merely the shortest."""
    rows, cols = len(costmap), len(costmap[0])

    def heuristic(a, b):
        return abs(a[0] - b[0]) + abs(a[1] - b[1])  # Manhattan distance, admissible on a grid

    open_set = [(heuristic(start, goal), 0, start, [start])]
    visited = set()

    while open_set:
        f, g, current, path = heapq.heappop(open_set)
        if current == goal:
            return path
        if current in visited:
            continue
        visited.add(current)

        r, c = current
        for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            nr, nc = r + dr, c + dc
            if 0 <= nr < rows and 0 <= nc < cols and costmap[nr][nc] != float('inf'):
                if (nr, nc) not in visited:
                    new_g = g + costmap[nr][nc]
                    new_f = new_g + heuristic((nr, nc), goal)
                    heapq.heappush(open_set, (new_f, new_g, (nr, nc), path + [(nr, nc)]))
    return None  # no path exists -- goal is unreachable given current obstacles

# 0 = free, 1 = obstacle. A wall with a single-cell gap.
grid = [
    [0, 0, 0, 0, 0],
    [0, 0, 1, 0, 0],
    [0, 0, 1, 0, 0],
    [0, 0, 0, 0, 0],  # gap in the wall at row 3
    [0, 0, 0, 0, 0],
]
costmap = inflate_costmap(grid, inflation_radius=1)
path = astar(costmap, start=(0, 0), goal=(4, 4))
print("Planned path:", path)
```

--> The planner's output, `path`, is a sequence of grid waypoints -- exactly chapter 05's closing point that "A*'s output becomes the setpoint stream fed into the control loop." The remaining piece chapter 06 never shows is turning a waypoint sequence into an actual velocity command every control tick: a simple **pure pursuit** style controller picks the next waypoint some lookahead distance ahead on the path, computes the heading error between the robot's current EKF-estimated heading (from the fusion example above) and the bearing to that waypoint, and feeds that heading error into a PID controller (chapter 03) commanding angular velocity, while linear velocity is commanded from a separate, simpler rule (e.g. proportional to remaining distance, capped by `max_speed`, and additionally capped by the safety interlock's speed zones). This is the point where chapter 06's five listed pieces -- mapping, localization, planning, control, and the ROS integration that carries messages between them -- become one running system rather than five separate bullet points.

# Deep Dive: Costmaps Must Be Re-Inflated as the Robot Moves, Not Computed Once

--> A subtle failure mode specific to navigation stacks, not present in the static A* example from chapter 05: the costmap above is computed once from a static occupancy grid, but a real environment has moving obstacles (people, other robots) that the static map never captured and a SLAM map that only ever reflects where the *sensor already saw* an obstacle, not where an obstacle currently is right now. Production navigation stacks solve this by maintaining a **local costmap**, recomputed every cycle from live sensor data (LIDAR/camera returns) within a small window around the robot, layered on top of the **global costmap** (the static, SLAM-built long-range map) built once. The planner replans against the combined layers continuously, not once at the start of a goal.

--> This creates a real trade-off chapter 06 doesn't surface: replanning the full global A* path every cycle is expensive and unnecessary (the long-range route rarely needs to change because a person walked into the hallway 2 meters ahead), while *never* checking the local costmap against the planned path risks driving straight into something the global map never knew about. The standard resolution is a two-tier planner -- a slower **global planner** (A*/D*) that computes a long-range route occasionally or on request, and a fast **local planner/controller** (e.g. dynamic window approach, or the pure-pursuit-plus-PID sketch above) that re-evaluates the immediate few meters of that route against the live local costmap every control tick, and can locally deviate or brake without waiting for a full global replan. This mirrors the same "cheap continuous correction beats occasional-but-perfect recomputation" principle behind PID's every-tick feedback (chapter 03) and the Kalman filter's every-tick predict/update cycle (chapter 05 and the fusion example above) — the robot never trusts a single computation to stay valid indefinitely; it keeps re-checking against the newest data, cheaply and often, rather than expensively and rarely.

# Cross-References

--> Chapter 05 ("Robotics Perception and SLAM") -- the Kalman filter predict/update anatomy this file's EKF fusion example extends to a nonlinear differential-drive motion model, and the A* algorithm this file's costmap-based planner extends with per-cell traversal cost.
--> Chapter 03 ("Control Systems and PID Controllers") -- the PID loop that turns this file's planned path into an actual angular-velocity command, and the "cheap continuous correction" principle underlying both PID and costmap replanning.
--> Chapter 04 ("Robot Operating System (ROS) Basics") -- the node/topic architecture (e.g. a `/local_costmap` topic feeding a planner node feeding a controller node) that a real navigation stack like Nav2 is built from.
--> Chapter 02 ("Sensors and Actuators") -- IMU drift and encoder quantization error, the two concrete noise sources this file's EKF fusion example is built to counteract.
--> "07b ... Correction and Deep Dive" -- the safety-interlock speed cap that sits on top of whatever linear velocity this file's pure-pursuit sketch commands, and the sensor-redundancy principle applied there to hazard detection rather than localization.
