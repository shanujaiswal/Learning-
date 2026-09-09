"""Hardware Abstraction Layer -- one `SimulatedDiffDriveHAL` instance per
robot in the fleet, exposing exactly the same call surface as Project 1's
HAL: a real warehouse AMR's motor-driver board + wheel encoders.

    hal.set_wheel_speeds(left_mps, right_mps, dt_s)   # motor driver board
    hal.read_wheel_odometry()                          # wheel encoders

Each robot's HAL only knows about the STATIC facility map (shelf racks /
walls, via `world.py`) -- exactly like a real chassis, which physically
can't be driven through a rack regardless of what the software does or
doesn't know. A HAL instance deliberately has NO knowledge of any other
robot; that's the whole point of the fleet-coordination layer this project
is about -- inter-robot separation is handled above the HAL, reactively,
by `RobotControllerNode` reading OTHER robots' `/odom` topics (see
`nodes.py`), the same way a real multi-robot ROS2 stack keeps per-robot
hardware drivers dumb and puts fleet-awareness in the navigation layer.
"""

import numpy as np
import world

RNG = np.random.default_rng(7)


class SimulatedDiffDriveHAL:
    """One per robot. Internally integrates true motion for the
    simulation's own bookkeeping -- a real HAL would instead be reading
    actual encoder ticks off actual motors.
    """

    def __init__(self, x0, y0, heading0, wheel_base_m=0.4, body_radius_m=0.22, seed=None):
        self.wheel_base = wheel_base_m
        self.body_radius = body_radius_m
        self.true_x, self.true_y, self.true_heading = x0, y0, heading0
        self.odom_x, self.odom_y, self.odom_heading = x0, y0, heading0
        self._rng = np.random.default_rng(seed) if seed is not None else RNG

    def set_wheel_speeds(self, left_mps, right_mps, dt_s):
        """Command left/right wheel linear speeds for `dt_s` seconds.
        Translation is blocked if it would drive the chassis into a shelf
        rack or a wall (the robot pivots against it instead of teleporting
        through it, exactly like Project 1's HAL) -- inter-ROBOT collision
        is intentionally NOT blocked here (see module docstring); that's
        the reactive-avoidance layer's job, not the chassis's.
        """
        v = (left_mps + right_mps) / 2.0
        omega = (right_mps - left_mps) / self.wheel_base

        new_heading = self.true_heading + omega * dt_s
        new_x = self.true_x + v * np.cos(new_heading) * dt_s
        new_y = self.true_y + v * np.sin(new_heading) * dt_s

        self.true_heading = new_heading
        if not self._collides_static(new_x, new_y):
            self.true_x, self.true_y = new_x, new_y

        # Encoder odometry integrates the COMMANDED v/omega regardless of
        # whether the chassis actually moved (real encoders measure wheel
        # rotation, not true displacement) plus small realistic drift.
        noisy_v = v + self._rng.normal(0, 0.015)
        noisy_omega = omega + self._rng.normal(0, 0.008)
        self.odom_heading += noisy_omega * dt_s
        self.odom_x += noisy_v * np.cos(self.odom_heading) * dt_s
        self.odom_y += noisy_v * np.sin(self.odom_heading) * dt_s

    def _collides_static(self, x, y):
        m = self.body_radius
        if x <= m or x >= world.FLOOR_SIZE - m or y <= m or y >= world.FLOOR_SIZE - m:
            return True
        return world.point_in_shelf(x, y, margin_m=m)

    def read_wheel_odometry(self):
        """(x, y, heading) from wheel-encoder dead reckoning -- ALL any
        node above the HAL is allowed to use as "where am I".
        """
        return self.odom_x, self.odom_y, self.odom_heading

    def true_pose(self):
        """Ground truth -- used ONLY by `main.py` for plotting/metrics
        after the run and for the fleet-wide near-collision check (a real
        ops dashboard reads this off precise UWB/mocap positioning, not
        off each robot's own drifting dead reckoning).
        """
        return self.true_x, self.true_y, self.true_heading
