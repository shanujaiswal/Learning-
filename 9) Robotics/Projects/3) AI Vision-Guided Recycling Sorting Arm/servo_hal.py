"""Hardware Abstraction Layer (HAL) -- the ONE place in this project that
knows anything is being simulated at all.

A real small-parts sorting arm built on hobby-servo hardware (e.g. an
AL5D/uArm-class arm) drives its joints through a PWM servo driver board
(commonly a PCA9685 I2C 16-channel PWM driver) with a call surface that
looks roughly like this:
    driver.set_channel_angle(channel, degrees)   # PCA9685 / servo driver board
    driver.read_channel_angle(channel)            # last commanded angle
    gripper_driver.open() / gripper_driver.close() # dedicated gripper servo

`nodes.py` is written ONLY against `SimulatedServoHAL` below -- never
against `kinematics.py`'s joint math directly and never against a "world"
ground truth. That's the point of a HAL: swap `SimulatedServoHAL` for a
real class wrapping `adafruit_pca9685`/`board`/`busio` I2C calls, and every
node in `nodes.py` keeps working completely unchanged.
"""

import numpy as np

from kinematics import JOINT_LIMITS_RAD, joints_rad_to_servo_deg, servo_deg_to_joint_rad

RNG = np.random.default_rng(7)

N_ARM_JOINTS = len(JOINT_LIMITS_RAD)  # base yaw, shoulder, elbow

GRIPPER_OPEN_DEG = 60.0
GRIPPER_CLOSED_DEG = 0.0


class SimulatedServoHAL:
    """Implements the same call surface a real PCA9685-driven servo arm
    exposes: command a joint's angle in servo-degrees, read back the last
    commanded angle, and a dedicated gripper open/close call. Internally
    tracks a first-order lag toward the commanded angle plus small settling
    noise -- a real HAL would instead be reading back actual servo horn
    position (most hobby servos are open-loop and don't even report this;
    many real builds just trust the last command, which is what
    `read_joint_angles_rad` mirrors here).
    """

    def __init__(self, start_joint_rad=None, settle_fraction=0.9):
        if start_joint_rad is None:
            start_joint_rad = np.zeros(N_ARM_JOINTS)
        self._commanded_deg = joints_rad_to_servo_deg(np.asarray(start_joint_rad, dtype=float))
        self._actual_deg = self._commanded_deg.copy()
        self._settle_fraction = settle_fraction
        self._gripper_deg = GRIPPER_OPEN_DEG
        self.gripper_closed = False

    def set_joint_angles_rad(self, joint_rad):
        """Commands all arm joints at once from a kinematics-frame radian
        vector -- exactly the call `nodes.ServoDriverNode` makes after
        `kinematics.inverse_kinematics` + `trajectory` produce the next
        setpoint. Internally converts to servo-degree space (the only thing
        the physical driver board understands) via the same calibration
        table `kinematics.py` defines.
        """
        joint_rad = np.clip(joint_rad,
                             [lo for lo, hi in JOINT_LIMITS_RAD],
                             [hi for lo, hi in JOINT_LIMITS_RAD])
        self._commanded_deg = joints_rad_to_servo_deg(joint_rad)
        # First-order settling toward the commanded angle plus small servo
        # jitter -- a real hobby servo doesn't teleport to a new angle
        # instantly, and horn position always has a little play/backlash.
        self._actual_deg += self._settle_fraction * (self._commanded_deg - self._actual_deg)
        self._actual_deg += RNG.normal(0, 0.15, size=self._actual_deg.shape)

    def read_joint_angles_rad(self):
        """Returns the arm's current joint angles in the kinematics frame,
        converted back from servo-degree space -- all any node above the
        HAL is ever allowed to use as "where are my joints right now."
        """
        return np.array([
            servo_deg_to_joint_rad(i, deg) for i, deg in enumerate(self._actual_deg)
        ])

    def open_gripper(self):
        self._gripper_deg = GRIPPER_OPEN_DEG
        self.gripper_closed = False

    def close_gripper(self):
        self._gripper_deg = GRIPPER_CLOSED_DEG
        self.gripper_closed = True

    def gripper_state(self):
        return "closed" if self.gripper_closed else "open"
