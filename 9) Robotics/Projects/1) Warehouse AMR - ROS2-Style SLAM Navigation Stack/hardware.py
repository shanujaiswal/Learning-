"""Hardware Abstraction Layer (HAL) -- the ONE place in this project that
knows anything is being simulated at all.

Real differential-drive AMRs (e.g. a warehouse robot built on a Roboclaw or
Cytron motor-driver board, wheel encoders, and a 2D LIDAR like an RPLidar or
Hokuyo) expose drivers with roughly this same shape:
    driver.set_wheel_speeds(left_mps, right_mps)   # motor driver board
    driver.read_wheel_odometry()                    # wheel encoders
    lidar.get_scan()                                 # 2D LIDAR

`nodes.py` is written ONLY against `DiffDriveHAL` and `Lidar2DHAL` below --
never against `world.py` directly. That's the point of a HAL: swap
`SimulatedDiffDriveHAL`/`SimulatedLidar2DHAL` for real classes wrapping
`RPi.GPIO` PWM calls and an RPLidar SDK, and every node in `nodes.py` keeps
working completely unchanged, because it never knew it was talking to a
simulation instead of a physical motor controller.
"""

import numpy as np
import world

RNG = np.random.default_rng(11)


class SimulatedDiffDriveHAL:
    """Implements the same call surface a real differential-drive motor
    controller + wheel-encoder board exposes: command wheel speeds, read
    back (noisy) integrated odometry. Internally integrates true motion for
    the simulation's own bookkeeping -- a real HAL would instead be reading
    actual encoder ticks off the actual motors.
    """

    def __init__(self, x0, y0, heading0, wheel_base_m=0.4):
        self.wheel_base = wheel_base_m
        self.true_x, self.true_y, self.true_heading = x0, y0, heading0
        self.odom_x, self.odom_y, self.odom_heading = x0, y0, heading0

    def set_wheel_speeds(self, left_mps, right_mps, dt_s, body_radius_m=0.22):
        """Command left/right wheel linear speeds for `dt_s` seconds --
        exactly the call a real motor-driver board's API takes. Converts
        to body-frame (v, omega) via standard differential-drive kinematics.

        A real robot chassis physically can't drive through a shelf rack or
        a wall -- if the commanded motion would put the robot's body inside
        one, translation is blocked (the chassis stops against it) while
        rotation still happens, exactly like a real robot bumping a wall
        and pivoting rather than teleporting through it.
        """
        v = (left_mps + right_mps) / 2.0
        omega = (right_mps - left_mps) / self.wheel_base

        new_heading = self.true_heading + omega * dt_s
        new_x = self.true_x + v * np.cos(new_heading) * dt_s
        new_y = self.true_y + v * np.sin(new_heading) * dt_s

        self.true_heading = new_heading
        if not self._collides(new_x, new_y, body_radius_m):
            self.true_x, self.true_y = new_x, new_y

        # Wheel-encoder odometry integrates the COMMANDED v/omega regardless
        # of whether the chassis actually moved -- real encoders measure
        # wheel rotation, not true chassis displacement, so a robot stalled
        # against an obstacle still accumulates "phantom" odometry. This is
        # a real, well-known failure mode (encoder-only dead reckoning
        # silently drifting when wheels slip or the robot is blocked) and
        # exactly why relying on odometry alone is unreliable.
        noisy_v = v + RNG.normal(0, 0.02)
        noisy_omega = omega + RNG.normal(0, 0.01)
        self.odom_heading += noisy_omega * dt_s
        self.odom_x += noisy_v * np.cos(self.odom_heading) * dt_s
        self.odom_y += noisy_v * np.sin(self.odom_heading) * dt_s

    def _collides(self, x, y, margin_m):
        if x <= margin_m or x >= world.FLOOR_SIZE - margin_m or \
           y <= margin_m or y >= world.FLOOR_SIZE - margin_m:
            return True
        for (xmin, ymin, xmax, ymax) in world.SHELF_RACKS:
            if (xmin - margin_m) <= x <= (xmax + margin_m) and \
               (ymin - margin_m) <= y <= (ymax + margin_m):
                return True
        return False

    def read_wheel_odometry(self):
        """Returns (x, y, heading) from wheel-encoder dead reckoning only --
        this is ALL any node above the HAL is ever allowed to use as
        "where am I", exactly like a real robot with no other localization
        source.
        """
        return self.odom_x, self.odom_y, self.odom_heading

    def true_pose(self):
        """Ground truth -- used ONLY by `main.py` to plot/score results
        after the run, never by any node during the run itself.
        """
        return self.true_x, self.true_y, self.true_heading


class SimulatedLidar2DHAL:
    """Implements the same call surface a real 2D LIDAR driver exposes:
    `get_scan()` returning (angles, ranges) for a full rotation.
    """

    def __init__(self, hal, n_rays=48, max_range_m=6.0, range_noise_std_m=0.05):
        self._hal = hal
        self.n_rays = n_rays
        self.max_range_m = max_range_m
        self.range_noise_std_m = range_noise_std_m

    def get_scan(self, t):
        x, y, heading = self._hal.true_pose()
        angles = heading + np.linspace(0, 2 * np.pi, self.n_rays, endpoint=False)
        true_ranges = world.true_ranges_batch(x, y, angles, t, self.max_range_m)
        noisy_ranges = true_ranges + RNG.normal(0, self.range_noise_std_m, size=true_ranges.shape)
        return angles, np.clip(noisy_ranges, 0.0, self.max_range_m)
