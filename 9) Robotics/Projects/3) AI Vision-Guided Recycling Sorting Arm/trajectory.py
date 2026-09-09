"""Joint-space trajectory generation for the 3-DOF sorting arm.

A real motion-planning layer never commands a servo to "teleport" straight
to a target angle -- that produces a visible jerk, stresses the gear train,
and (for anything holding an object) can fling it out of the gripper. Real
pick-and-place cells instead interpolate a smooth joint-space profile between
the current pose and the target pose and stream it to the servo driver one
tick at a time. This module implements exactly that: a minimum-jerk
(quintic) time-scaling of each joint, which is the same profile family
industrial motion controllers (and RC servo sequencers) use because it has
zero velocity AND zero acceleration at both endpoints -- no start/stop jerk.
"""

import numpy as np


def _minimum_jerk_s(tau):
    """Canonical 0->1 minimum-jerk time-scaling, tau in [0, 1]."""
    tau = np.clip(tau, 0.0, 1.0)
    return 10 * tau ** 3 - 15 * tau ** 4 + 6 * tau ** 5


def generate_joint_trajectory(start_rad, goal_rad, duration_s, dt_s):
    """Returns an (N, n_joints) array of joint angles (radians) tracing a
    minimum-jerk path from `start_rad` to `goal_rad` over `duration_s`,
    sampled every `dt_s` -- exactly what a real trajectory-streaming servo
    controller consumes one row at a time.
    """
    start_rad = np.asarray(start_rad, dtype=float)
    goal_rad = np.asarray(goal_rad, dtype=float)
    n_steps = max(2, int(np.ceil(duration_s / dt_s)) + 1)
    times = np.linspace(0.0, duration_s, n_steps)
    s = _minimum_jerk_s(times / duration_s) if duration_s > 0 else np.ones_like(times)
    return start_rad[None, :] + s[:, None] * (goal_rad - start_rad)[None, :]


def stitch_trajectories(*legs):
    """Concatenates several `generate_joint_trajectory` legs (e.g. approach,
    descend, lift, transit, place) into a single streamed sequence, exactly
    how a real pick-and-place motion sequencer chains sub-moves of a single
    pick cycle.
    """
    return np.concatenate(legs, axis=0)
