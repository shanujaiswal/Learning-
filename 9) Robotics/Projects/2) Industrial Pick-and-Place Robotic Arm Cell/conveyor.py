"""Ground truth for the conveyor side of the cell -- the ONE place in this
project that knows anything about part arrivals or the pick station.

A real packaging line's conveyor doesn't hand the arm a camera feed of every
part (that's a fundamentally different, vision-guided cell -- Project 3's
job). A fixed low-cost pick station instead uses a single photoelectric
(through-beam or retro-reflective) sensor mounted at a known, fixed point on
the belt: when a part physically breaks the beam, the sensor fires one
digital trigger, and the part is known to be sitting at that exact fixed
X/Y/Z every single time (that fixed positioning is *why* a photoelectric
"pick station" is cheap and reliable in a way a moving/variable pick point
never is). `ConveyorFeeder` below is that photoelectric sensor plus the
belt's fixed timing: parts arrive strictly every `takt_time_s` seconds,
because in a real bottling/packaging line the upstream process (filling,
capping, labeling) feeds the belt at a fixed, metronomic rate -- that fixed
rate IS the takt time the pick-and-place cell must keep up with.
"""

import numpy as np


class ConveyorFeeder:
    """Simulates the belt + photoelectric sensor at the fixed pick station.
    `step(dt_s, t)` is the only call any node makes -- it never hands out
    anything richer than "a part just tripped the sensor, and here is its
    type" (read off a barcode/color-sensor at the same station in a real
    cell), never a live video stream or position other than the one fixed
    pick point.
    """

    def __init__(self, takt_time_s=8.0, part_type_weights=None, n_parts=9, rng_seed=7):
        self.takt_time_s = takt_time_s
        self.n_parts = n_parts
        if part_type_weights is None:
            part_type_weights = {"A": 0.5, "B": 0.5}
        self._part_types = list(part_type_weights.keys())
        weights = np.array(list(part_type_weights.values()), dtype=float)
        self._weights = weights / weights.sum()
        self._rng = np.random.default_rng(rng_seed)

        self._elapsed_since_last_s = 0.0
        self.parts_released = 0
        self.done = False

    def step(self, dt_s, t):
        """Advances the belt clock by `dt_s`. Returns a part-arrival event
        dict the instant the photoelectric sensor trips (exactly once every
        `takt_time_s`), else `None`.
        """
        if self.done:
            return None
        self._elapsed_since_last_s += dt_s
        if self._elapsed_since_last_s < self.takt_time_s:
            return None

        self._elapsed_since_last_s -= self.takt_time_s
        self.parts_released += 1
        part_type = self._rng.choice(self._part_types, p=self._weights)
        event = {"part_id": self.parts_released, "part_type": str(part_type), "t_arrival": t}
        if self.parts_released >= self.n_parts:
            self.done = True
        return event
