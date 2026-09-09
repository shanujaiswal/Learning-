"""Ground-truth warehouse floor -- shelf racks (static) and a forklift
(dynamic obstacle) crossing an aisle.

Nothing except `hardware.py` is allowed to read this module. On a real
robot, "the world" isn't a Python module at all -- it's whatever the real
LIDAR/motors physically measure. Keeping the ground truth walled off behind
`hardware.py`'s sensor/actuator interface is what forces the rest of the
stack (`nodes.py`, `mapping_and_planning.py`) to work the same way it would
against real hardware: through noisy sensor readings only, never by
peeking at reality directly.
"""

import numpy as np

FLOOR_SIZE = 14.0  # meters, warehouse floor spans [0, FLOOR_SIZE] in x and y

# Shelf racks, modeled as rectangular obstacles -- a realistic single-block
# warehouse layout with a central cross-aisle and a picking aisle down the
# left side, rather than an arbitrary maze.
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


class Forklift:
    """A forklift that repeatedly drives the cross-aisle (y ~= 7.0) picking
    up pallets -- the dynamic obstacle the AMR has to react to at runtime,
    since it can never appear on a pre-built static map.
    """

    def __init__(self, x0=1.0, x1=13.0, y=7.0, speed_mps=0.9, radius_m=0.5):
        self.x0, self.x1, self.y = x0, x1, y
        self.speed = speed_mps
        self.radius = radius_m
        self._length = x1 - x0

    def position_at(self, t):
        travel = (self.speed * t) % (2 * self._length)
        x = self.x0 + travel if travel <= self._length else self.x1 - (travel - self._length)
        return np.array([x, self.y])


FORKLIFT = Forklift()


def point_in_shelf(x, y):
    for (xmin, ymin, xmax, ymax) in SHELF_RACKS:
        if xmin <= x <= xmax and ymin <= y <= ymax:
            return True
    return False


def point_on_forklift(x, y, t):
    fx, fy = FORKLIFT.position_at(t)
    return np.hypot(x - fx, y - fy) <= FORKLIFT.radius


def true_ranges_batch(x, y, angles, t, max_range_m=6.0, include_forklift=True, step_m=0.05):
    """Vectorized true (noise-free) distance from (x, y) along each angle in
    `angles` to the nearest shelf rack, wall, or (if included) the forklift
    at time t -- what a perfect sensor would report for an entire LIDAR scan
    at once. `hardware.py` adds real sensor noise on top of this.

    Marching every ray one Python `while` step at a time (the natural way
    to write a raycast) is far too slow once you're calling it 48 times a
    tick for thousands of ticks -- a real LIDAR driver's ASIC does this in
    hardware; this instead does all rays x all sample-distances as one
    batch of numpy array operations, which is the equivalent trick in
    software.
    """
    angles = np.asarray(angles)
    n_steps = int(max_range_m / step_m)
    sample_dists = np.arange(1, n_steps + 1) * step_m  # (n_steps,)

    dx = np.cos(angles)[:, None]          # (n_rays, 1)
    dy = np.sin(angles)[:, None]
    px = x + dx * sample_dists[None, :]    # (n_rays, n_steps)
    py = y + dy * sample_dists[None, :]

    blocked = (px <= 0) | (px >= FLOOR_SIZE) | (py <= 0) | (py >= FLOOR_SIZE)
    for (xmin, ymin, xmax, ymax) in SHELF_RACKS:
        blocked |= (px >= xmin) & (px <= xmax) & (py >= ymin) & (py <= ymax)
    if include_forklift:
        fx, fy = FORKLIFT.position_at(t)
        blocked |= np.hypot(px - fx, py - fy) <= FORKLIFT.radius

    any_blocked = blocked.any(axis=1)
    first_blocked_idx = blocked.argmax(axis=1)  # first True per row (0 if none found)
    ranges = np.where(any_blocked, sample_dists[first_blocked_idx], max_range_m)
    return ranges
