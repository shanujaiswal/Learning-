"""Forward/inverse kinematics for a small 3-DOF benchtop arm -- base yaw +
2-link planar shoulder/elbow, roughly modeled on the geometry of common
low-cost pick-and-place arms (uArm/AL5D-class) used in real small-parts
automation cells.

Also holds the joint <-> servo-degree calibration each joint needs, since a
real servo only understands "go to N degrees," not "go to N radians in the
arm's own kinematic frame."
"""

import numpy as np

BASE_HEIGHT_M = 0.05    # base pivot height off the table
UPPER_ARM_M = 0.20      # shoulder -> elbow link length
FOREARM_M = 0.18        # elbow -> wrist/end-effector link length

# Per-joint (min_rad, max_rad) range mapped linearly onto a servo's 0-180
# degree range -- exactly the calibration table a real servo-driven arm
# needs, since "0 radians in the kinematic model" rarely lines up with
# "0 degrees on the physical servo horn."
JOINT_LIMITS_RAD = [
    (-np.pi / 2, np.pi / 2),   # joint 0: base yaw
    (0.0, np.pi),               # joint 1: shoulder (from horizontal)
    (0.0, np.pi),                # joint 2: elbow (relative to upper arm)
]


def forward_kinematics(theta_base, theta_shoulder, theta_elbow):
    """Standard 2-link planar geometry (shoulder + elbow) rotated about the
    vertical axis by the base yaw. Returns end-effector (x, y, z) in meters.
    """
    r = UPPER_ARM_M * np.cos(theta_shoulder) + FOREARM_M * np.cos(theta_shoulder + theta_elbow)
    z = BASE_HEIGHT_M + UPPER_ARM_M * np.sin(theta_shoulder) + FOREARM_M * np.sin(theta_shoulder + theta_elbow)
    x = r * np.cos(theta_base)
    y = r * np.sin(theta_base)
    return np.array([x, y, z])


def inverse_kinematics(x, y, z):
    """Closed-form geometric IK (law of cosines) for the same 2-link arm.
    Raises ValueError if the target is outside the arm's reachable
    workspace -- exactly the check a real motion-planning layer must run
    BEFORE commanding servos, since a servo given an unreachable target
    silently drives to whatever the math produces (often garbage, or a
    domain error on the same arccos below).
    """
    theta_base = np.arctan2(y, x)
    r = np.hypot(x, y)
    zp = z - BASE_HEIGHT_M
    d = np.hypot(r, zp)

    max_reach = UPPER_ARM_M + FOREARM_M
    min_reach = abs(UPPER_ARM_M - FOREARM_M)
    if d > max_reach or d < min_reach:
        raise ValueError(
            f"Target ({x:.3f}, {y:.3f}, {z:.3f}) m is out of reach: "
            f"distance {d:.3f} m, reachable range [{min_reach:.3f}, {max_reach:.3f}] m")

    cos_elbow = (d ** 2 - UPPER_ARM_M ** 2 - FOREARM_M ** 2) / (2 * UPPER_ARM_M * FOREARM_M)
    theta_elbow = np.arccos(np.clip(cos_elbow, -1.0, 1.0))
    theta_shoulder = np.arctan2(zp, r) - np.arctan2(
        FOREARM_M * np.sin(theta_elbow), UPPER_ARM_M + FOREARM_M * np.cos(theta_elbow))
    return np.array([theta_base, theta_shoulder, theta_elbow])


def joint_rad_to_servo_deg(joint_index, rad):
    lo, hi = JOINT_LIMITS_RAD[joint_index]
    rad = np.clip(rad, lo, hi)
    return (rad - lo) / (hi - lo) * 180.0


def joints_rad_to_servo_deg(joint_rad_array):
    return np.array([joint_rad_to_servo_deg(i, r) for i, r in enumerate(joint_rad_array)])


def servo_deg_to_joint_rad(joint_index, deg):
    lo, hi = JOINT_LIMITS_RAD[joint_index]
    return lo + (np.clip(deg, 0, 180) / 180.0) * (hi - lo)
