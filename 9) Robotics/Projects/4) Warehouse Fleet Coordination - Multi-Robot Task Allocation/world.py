"""Warehouse floor + facility site map -- shelf racks and named pick
locations, plus a static A* global planner and potential-field local
controller that every robot's `RobotControllerNode` uses to get around.

Project 1 built its occupancy map from scratch via SLAM because it modeled
a robot's very first shift on a brand-new floor. This project models an
*operating* fleet on a floor the warehouse operator already surveyed --
exactly like a real deployed AMR fleet, which is handed a fixed facility
site map (from the WMS/warehouse layout system) once at commissioning and
never has to re-discover where the shelf racks are on every shift. That's
why the occupancy grid here is built directly from `SHELF_RACKS` instead of
from noisy sensor fusion -- a deliberate, realistic difference from Project
1, not a shortcut.

Nothing here is off-limits to read (unlike Project 1's `world.py`, which
only `hardware.py` was allowed to touch) -- a known site map is exactly
what a real fleet's navigation stack is allowed to load directly.
"""

import heapq
import numpy as np

FLOOR_SIZE = 14.0  # meters, warehouse floor spans [0, FLOOR_SIZE] in x and y

# Same single-block layout style as Project 1: a picking aisle down the
# left side plus paired racks with a cross-aisle down the middle, sized so
# a fleet of robots has room to pass each other in the aisles.
SHELF_RACKS = [
    (2.0, 1.0, 2.6, 6.0),
    (2.0, 8.0, 2.6, 13.0),
    (5.0, 1.0, 5.6, 6.0),
    (5.0, 8.0, 5.6, 13.0),
    (8.0, 1.0, 8.6, 6.0),
    (8.0, 8.0, 8.6, 13.0),
    (11.0, 1.0, 11.6, 6.0),
    (11.0, 8.0, 11.6, 13.0),
]

# Named pick locations -- points just off the aisle-facing edge of each
# rack, exactly where a real pick-to-light or put-to-light warehouse slot
# sits. `main.py` draws a batch of "orders" (a subset of these) for the
# fleet to service each run.
PICK_LOCATIONS = {
    "R1-A": (1.5, 2.0), "R1-B": (1.5, 4.5), "R1-C": (3.1, 2.5), "R1-D": (3.1, 5.0),
    "R2-A": (1.5, 9.5), "R2-B": (1.5, 12.0), "R2-C": (3.1, 9.0), "R2-D": (3.1, 12.5),
    "R3-A": (4.5, 2.5), "R3-B": (6.1, 3.5), "R3-C": (4.5, 5.5), "R3-D": (6.1, 5.5),
    "R4-A": (4.5, 9.5), "R4-B": (6.1, 10.5), "R4-C": (4.5, 12.5), "R4-D": (6.1, 12.0),
    "R5-A": (7.5, 2.0), "R5-B": (9.1, 3.0), "R5-C": (7.5, 5.5), "R5-D": (9.1, 4.5),
    "R6-A": (7.5, 9.0), "R6-B": (9.1, 10.0), "R6-C": (7.5, 12.5), "R6-D": (9.1, 12.0),
    "R7-A": (10.5, 2.5), "R7-B": (12.1, 3.5), "R7-C": (10.5, 5.5), "R7-D": (12.1, 5.0),
    "R8-A": (10.5, 9.5), "R8-B": (12.1, 10.5), "R8-C": (10.5, 12.5), "R8-D": (12.1, 12.0),
}

PACK_STATION_XY = (13.3, 0.7)  # where a real fleet drops picked totes -- used as a default depot/start

GRID_RESOLUTION_M = 0.2
GRID_CELLS = int(FLOOR_SIZE / GRID_RESOLUTION_M)


def point_in_shelf(x, y, margin_m=0.0):
    for (xmin, ymin, xmax, ymax) in SHELF_RACKS:
        if (xmin - margin_m) <= x <= (xmax + margin_m) and (ymin - margin_m) <= y <= (ymax + margin_m):
            return True
    return False


def build_static_occupancy_grid(resolution_m=GRID_RESOLUTION_M, inflate_m=0.3):
    """The facility site map, rasterized once at commissioning time --
    shelf racks inflated by `inflate_m` (a real planner always inflates
    obstacles by roughly the robot's body radius so A* doesn't plan a path
    that grazes a rack edge).
    """
    n = int(FLOOR_SIZE / resolution_m)
    xs = (np.arange(n) + 0.5) * resolution_m
    ys = (np.arange(n) + 0.5) * resolution_m
    gx, gy = np.meshgrid(xs, ys, indexing="ij")
    occupied = np.zeros((n, n), dtype=bool)
    for (xmin, ymin, xmax, ymax) in SHELF_RACKS:
        occupied |= (gx >= xmin - inflate_m) & (gx <= xmax + inflate_m) & \
                    (gy >= ymin - inflate_m) & (gy <= ymax + inflate_m)
    return occupied  # True = blocked


_STATIC_GRID = build_static_occupancy_grid()


def _world_to_cell(x, y, resolution_m=GRID_RESOLUTION_M):
    n = _STATIC_GRID.shape[0]
    cx = int(np.clip(x / resolution_m, 0, n - 1))
    cy = int(np.clip(y / resolution_m, 0, n - 1))
    return cx, cy


def _cell_to_world(cx, cy, resolution_m=GRID_RESOLUTION_M):
    return (cx + 0.5) * resolution_m, (cy + 0.5) * resolution_m


def astar(start_xy, goal_xy, resolution_m=GRID_RESOLUTION_M):
    """A* over the static facility grid -- run once per task assignment
    (in `RobotControllerNode`), not every control tick. Same algorithm as
    Project 1's planner, just against a known map instead of a SLAM one.
    """
    grid = _STATIC_GRID
    n = grid.shape[0]
    start = _world_to_cell(*start_xy, resolution_m)
    goal = _world_to_cell(*goal_xy, resolution_m)

    def heuristic(a, b):
        return np.hypot(a[0] - b[0], a[1] - b[1])

    neighbors = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]
    open_set = [(0.0, start)]
    came_from = {}
    g_score = {start: 0.0}
    visited = set()

    while open_set:
        _, current = heapq.heappop(open_set)
        if current in visited:
            continue
        visited.add(current)
        if current == goal:
            path = [current]
            while current in came_from:
                current = came_from[current]
                path.append(current)
            path.reverse()
            return [_cell_to_world(cx, cy, resolution_m) for (cx, cy) in path]

        for dcx, dcy in neighbors:
            nxt = (current[0] + dcx, current[1] + dcy)
            if not (0 <= nxt[0] < n and 0 <= nxt[1] < n):
                continue
            if grid[nxt[0], nxt[1]]:
                continue
            step_cost = np.hypot(dcx, dcy)
            tentative_g = g_score[current] + step_cost
            if tentative_g < g_score.get(nxt, np.inf):
                came_from[nxt] = current
                g_score[nxt] = tentative_g
                heapq.heappush(open_set, (tentative_g + heuristic(nxt, goal), nxt))

    return None


def simplify_path(path, target_waypoints=12):
    """Same rationale as Project 1: a raw 0.2 m-grid A* path has far more
    waypoints than a potential-field controller should chase (dense
    waypoints barely a body-length apart are exactly what causes
    orbiting/oscillation), so subsample down to a manageable number,
    always keeping the final goal waypoint.
    """
    if path is None or len(path) <= target_waypoints:
        return path
    stride = max(1, len(path) // target_waypoints)
    simplified = path[::stride]
    if simplified[-1] != path[-1]:
        simplified.append(path[-1])
    return simplified


def path_length_m(path):
    if path is None or len(path) < 2:
        return 0.0
    pts = np.array(path)
    return float(np.sum(np.hypot(np.diff(pts[:, 0]), np.diff(pts[:, 1]))))


def shelf_repulsion(x, y, repel_radius_m=0.5, repel_gain=0.6):
    """Repulsion away from the nearest point on each rack rectangle --
    lets the potential-field controller hug the aisle centerline instead
    of grazing a rack, without re-running A* every tick. Only 8 racks, so
    a plain Python loop here (unlike the raycast-batching in Project 1)
    is not a performance concern -- it runs once per robot per tick, not
    once per ray per robot per tick.
    """
    repel = np.zeros(2)
    for (xmin, ymin, xmax, ymax) in SHELF_RACKS:
        nearest_x = np.clip(x, xmin, xmax)
        nearest_y = np.clip(y, ymin, ymax)
        dx, dy = x - nearest_x, y - nearest_y
        dist = np.hypot(dx, dy)
        if dist < repel_radius_m:
            dist = max(dist, 0.05)
            strength = repel_gain * (1.0 / dist - 1.0 / repel_radius_m)
            repel += strength * np.array([dx, dy]) / dist
    return repel


def potential_field_step(x, y, waypoint_xy, neighbor_positions,
                          attract_gain=1.0, robot_repel_gain=1.3, robot_repel_radius_m=1.2,
                          shelf_repel_radius_m=0.5, shelf_repel_gain=0.6):
    """One local-avoidance velocity vector: attraction to the current A*
    waypoint, repulsion from nearby shelf racks, AND repulsion from every
    other robot's current position -- the same potential-field mechanism
    Project 1 used for the forklift, extended so every OTHER ROBOT is also
    a moving repulsive obstacle. This is exactly the standard "global task
    plan + local reactive separation" split real multi-robot warehouse
    fleets run: nobody re-plans a global path around a neighbor robot every
    tick, they just nudge away from it locally.
    """
    to_waypoint = np.array(waypoint_xy) - np.array([x, y])
    dist = np.linalg.norm(to_waypoint) + 1e-9
    attract = attract_gain * to_waypoint / dist

    repel = shelf_repulsion(x, y, shelf_repel_radius_m, shelf_repel_gain)

    for (nx, ny) in neighbor_positions:
        ndist = np.hypot(x - nx, y - ny)
        if ndist < robot_repel_radius_m:
            ndist = max(ndist, 0.05)
            strength = robot_repel_gain * (1.0 / ndist - 1.0 / robot_repel_radius_m)
            away = np.array([x - nx, y - ny]) / ndist
            repel += strength * away

    return attract + repel
