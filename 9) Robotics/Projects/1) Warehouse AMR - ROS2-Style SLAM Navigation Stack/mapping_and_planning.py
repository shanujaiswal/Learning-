"""SLAM-lite occupancy-grid mapping + A* global planning + potential-field
local obstacle avoidance -- the "brain" of the AMR, wired into ROS2-style
nodes in `nodes.py`. Every function here takes only sensor-derived data
(a pose estimate, a LIDAR scan, a probability grid) -- never `world.py`.
"""

import heapq
import numpy as np
import world

GRID_RESOLUTION_M = 0.25
GRID_CELLS = int(world.FLOOR_SIZE / GRID_RESOLUTION_M)


class OccupancyGridMap:
    """Log-odds occupancy grid -- the standard real-world SLAM mapping
    representation. Each cell accumulates evidence of being free (negative
    log-odds) or occupied (positive log-odds) from many independent noisy
    LIDAR rays; repeated agreeing observations make the map more confident.
    """

    LOG_ODDS_OCCUPIED = 0.85
    LOG_ODDS_FREE = -0.4
    LOG_ODDS_CLAMP = 8.0

    def __init__(self, size_cells=GRID_CELLS, resolution_m=GRID_RESOLUTION_M):
        self.resolution = resolution_m
        self.log_odds = np.zeros((size_cells, size_cells))

    def world_to_cell(self, x, y):
        eps = 1e-9
        cx = int(np.clip(x / self.resolution + eps, 0, self.log_odds.shape[0] - 1))
        cy = int(np.clip(y / self.resolution + eps, 0, self.log_odds.shape[1] - 1))
        return cx, cy

    def cell_to_world(self, cx, cy):
        return (cx + 0.5) * self.resolution, (cy + 0.5) * self.resolution

    def integrate_scan(self, robot_x, robot_y, angles, ranges, max_range_m):
        """Fuse an entire LIDAR scan (all rays at once) into the grid.

        Marching each ray one small step at a time in a Python loop is the
        natural way to describe a beam-model update, but far too slow once
        it's run 48 times a tick for thousands of ticks -- exactly the same
        raycast-batching trick as `world.true_ranges_batch` is applied here:
        every ray's sampled free-space cells and its one occupied hit-cell
        are computed as numpy arrays and applied with a single `np.add.at`,
        instead of one Python-level array write per sample point.
        """
        angles = np.asarray(angles)
        ranges = np.asarray(ranges)
        n_rows, n_cols = self.log_odds.shape
        delta = np.zeros(self.log_odds.size)

        step = self.resolution * 0.5
        max_steps = max(1, int(np.max(ranges) / step) + 1)
        sample_dists = np.arange(max_steps) * step          # (max_steps,)

        dx = np.cos(angles)[:, None]                          # (n_rays, 1)
        dy = np.sin(angles)[:, None]
        px = robot_x + dx * sample_dists[None, :]              # (n_rays, max_steps)
        py = robot_y + dy * sample_dists[None, :]

        # Free-space samples: on the floor, and strictly before this ray's hit.
        on_floor = (px >= 0) & (px < world.FLOOR_SIZE) & (py >= 0) & (py < world.FLOOR_SIZE)
        before_hit = sample_dists[None, :] < ranges[:, None]
        free_mask = on_floor & before_hit

        cx = np.clip((px / self.resolution).astype(int), 0, n_rows - 1)
        cy = np.clip((py / self.resolution).astype(int), 0, n_cols - 1)
        flat_idx = cx * n_cols + cy
        np.add.at(delta, flat_idx[free_mask], self.LOG_ODDS_FREE)

        # Occupied hit cell: one per ray that actually returned a hit
        # (didn't just max out at max_range_m with nothing in front of it).
        is_real_hit = ranges < (max_range_m - 1e-6)
        hx = robot_x + np.cos(angles) * ranges
        hy = robot_y + np.sin(angles) * ranges
        hit_on_floor = is_real_hit & (hx >= 0) & (hx < world.FLOOR_SIZE) & (hy >= 0) & (hy < world.FLOOR_SIZE)
        hcx = np.clip((hx / self.resolution).astype(int), 0, n_rows - 1)
        hcy = np.clip((hy / self.resolution).astype(int), 0, n_cols - 1)
        hit_flat_idx = hcx * n_cols + hcy
        np.add.at(delta, hit_flat_idx[hit_on_floor], self.LOG_ODDS_OCCUPIED)

        self.log_odds += delta.reshape(n_rows, n_cols)
        np.clip(self.log_odds, -self.LOG_ODDS_CLAMP, self.LOG_ODDS_CLAMP, out=self.log_odds)

    def probability_map(self):
        return 1.0 / (1.0 + np.exp(-self.log_odds))

    def is_free(self, cx, cy, occupied_threshold=0.6):
        return self.probability_map()[cx, cy] < occupied_threshold


def astar(grid_map, start_xy, goal_xy, occupied_threshold=0.6):
    """A* over the robot's OWN occupancy grid (not ground truth) -- cells
    the robot never observed default to 0.5 probability (`is_free` treats
    anything below the occupied threshold as traversable), matching the
    common real-world planning assumption "unknown is provisionally
    passable until proven otherwise" for a single-pass warehouse map.
    """
    start = grid_map.world_to_cell(*start_xy)
    goal = grid_map.world_to_cell(*goal_xy)
    size = grid_map.log_odds.shape[0]

    def heuristic(a, b):
        return np.hypot(a[0] - b[0], a[1] - b[1])

    neighbors = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]
    open_set = [(0.0, start)]
    came_from = {}
    g_score = {start: 0.0}

    while open_set:
        _, current = heapq.heappop(open_set)
        if current == goal:
            path = [current]
            while current in came_from:
                current = came_from[current]
                path.append(current)
            path.reverse()
            return [grid_map.cell_to_world(cx, cy) for (cx, cy) in path]

        for dcx, dcy in neighbors:
            nxt = (current[0] + dcx, current[1] + dcy)
            if not (0 <= nxt[0] < size and 0 <= nxt[1] < size):
                continue
            if not grid_map.is_free(*nxt, occupied_threshold):
                continue
            step_cost = np.hypot(dcx, dcy)
            tentative_g = g_score[current] + step_cost
            if tentative_g < g_score.get(nxt, np.inf):
                came_from[nxt] = current
                g_score[nxt] = tentative_g
                f_score = tentative_g + heuristic(nxt, goal)
                heapq.heappush(open_set, (f_score, nxt))

    return None  # no path found against the currently-known map


def simplify_path(path, target_waypoints=15):
    """A* over a 0.25 m grid produces one waypoint per cell -- far more
    than a real path-following controller needs, and dense enough that the
    controller ends up chasing waypoints barely a body-length apart, which
    is exactly what causes the orbiting/oscillation failure mode common to
    potential-field controllers. Real navigation stacks always simplify a
    raw grid path before handing it to the controller; here that's just an
    even stride subsample that always keeps the final goal waypoint.
    """
    if len(path) <= target_waypoints:
        return path
    stride = max(1, len(path) // target_waypoints)
    simplified = path[::stride]
    if simplified[-1] != path[-1]:
        simplified.append(path[-1])
    return simplified


def potential_field_step(robot_x, robot_y, waypoint_xy, obstacle_readings,
                          attract_gain=1.0, repel_gain=0.5, repel_radius_m=0.5):
    """One local-avoidance velocity vector: attraction toward the current
    A* waypoint, repulsion from any LIDAR return closer than `repel_radius_m`
    -- this is what lets the robot swerve around the forklift in real time
    WITHOUT re-running A* every tick, since re-planning the full global path
    every control cycle is far too slow for a real onboard controller.
    """
    to_waypoint = np.array(waypoint_xy) - np.array([robot_x, robot_y])
    dist_to_waypoint = np.linalg.norm(to_waypoint) + 1e-9
    attract = attract_gain * to_waypoint / dist_to_waypoint

    repel = np.zeros(2)
    for angle, rng in obstacle_readings:
        if rng < repel_radius_m:
            obstacle_xy = np.array([robot_x + rng * np.cos(angle), robot_y + rng * np.sin(angle)])
            away = np.array([robot_x, robot_y]) - obstacle_xy
            strength = repel_gain * (1.0 / max(rng, 0.05) - 1.0 / repel_radius_m)
            repel += strength * away / (np.linalg.norm(away) + 1e-9)

    return attract + repel
