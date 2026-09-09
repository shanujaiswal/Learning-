"""Quintic-polynomial joint-space trajectory generation.

A real industrial motion controller never commands a servo to "jump" straight
from angle A to angle B -- that produces a velocity step (infinite
acceleration) at both ends of the move, which is exactly what shakes a
benchtop arm, skips steps on a stepper, or trips a servo's current-limit
protection. Instead it fits a smooth polynomial time-scaling between the two
angles that starts and ends at zero velocity AND zero acceleration, so
consecutive motion segments blend together without a jerk at the boundary.
The quintic (5th-order) polynomial is the standard minimum-order polynomial
that can satisfy all six boundary conditions (start/end position, velocity,
acceleration) -- exactly the technique used by real robot controllers
(industrial arm controllers, 3D-printer/CNC motion planners, `ros2_control`
joint trajectory controllers) wherever a smooth point-to-point move is
needed.
"""

import numpy as np


def quintic_time_scaling(t, T):
    """Returns (s, s_dot, s_ddot) -- a dimensionless 0->1 progress scalar and
    its first two derivatives, for a 5th-order polynomial with zero velocity
    and zero acceleration at both t=0 and t=T. `s` is then applied uniformly
    to every joint (`q(t) = q_start + s(t) * (q_end - q_start)`), which
    coordinates all joints to start and stop moving at exactly the same
    instant -- the same "coordinated joint move" every industrial arm
    controller defaults to.
    """
    tau = np.clip(t / T, 0.0, 1.0)
    s = 10 * tau ** 3 - 15 * tau ** 4 + 6 * tau ** 5
    s_dot = (30 * tau ** 2 - 60 * tau ** 3 + 30 * tau ** 4) / T
    s_ddot = (60 * tau - 180 * tau ** 2 + 120 * tau ** 3) / (T ** 2)
    return s, s_dot, s_ddot


def joint_space_quintic_trajectory(q_start, q_end, duration_s, dt_s):
    """Samples a quintic-blended joint-space move from `q_start` to `q_end`
    (both length-N joint-angle arrays, radians) over `duration_s`, every
    `dt_s`. Returns (times, positions, velocities, accelerations), each
    `positions`/`velocities`/`accelerations` shaped (n_samples, N).

    Includes t=0 but the *last* sample is the final waypoint at t=duration_s
    (so consecutive segments can be concatenated without a duplicated or
    missing sample at the shared boundary).
    """
    q_start = np.asarray(q_start, dtype=float)
    q_end = np.asarray(q_end, dtype=float)
    n_samples = max(2, int(round(duration_s / dt_s)) + 1)
    times = np.linspace(0.0, duration_s, n_samples)

    positions = np.zeros((n_samples, len(q_start)))
    velocities = np.zeros_like(positions)
    accelerations = np.zeros_like(positions)
    delta = q_end - q_start
    for i, t in enumerate(times):
        s, s_dot, s_ddot = quintic_time_scaling(t, duration_s)
        positions[i] = q_start + s * delta
        velocities[i] = s_dot * delta
        accelerations[i] = s_ddot * delta
    return times, positions, velocities, accelerations
