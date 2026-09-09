"""Hardware Abstraction Layer (HAL) -- the ONE place in this project that
knows anything is being simulated at all.

A real low-cost servo-driven arm cell (uArm/AL5D-class) is built on a PCA9685
PWM driver board talked to over I2C, exposed to application code through a
library like Adafruit's `adafruit_servokit.ServoKit`:
    kit = ServoKit(channels=16)
    kit.servo[0].angle = 90          # command channel 0 to 90 degrees
    angle = kit.servo[0].angle        # last commanded angle

`ServoDriverHAL` below exposes that exact same call shape
(`set_angle(channel, deg)` / `set_angles({...})` / `get_angle(channel)`).
`nodes.py` is written ONLY against this class, never against anything else.
Swapping this class for a thin wrapper around a real `ServoKit` instance is
the ONLY change needed to run this project's node graph on physical servos
plugged into a real PCA9685 board -- every node above the HAL keeps working
completely unmodified, because it never knew it was talking to a simulation.
"""

import numpy as np


class ServoDriverHAL:
    """Simulates a PCA9685-driven servo bank with a first-order slew-rate
    model: a real hobby/industrial servo cannot snap instantly to a newly
    commanded angle, it physically rotates its horn at a bounded maximum
    rate (typically expressed on the datasheet as seconds-per-60-degrees).
    `set_angle`/`set_angles` only ever update the COMMANDED target; the
    servo's actual position only catches up to that target when `step(dt_s)`
    is called, exactly like a real servo's horn only catching up to a new
    PWM pulse width at its own mechanical speed.
    """

    def __init__(self, num_channels=4, slew_rate_deg_s=300.0,
                 gripper_channel=3, gripper_open_deg=90.0, gripper_close_deg=10.0,
                 initial_deg=90.0):
        self.num_channels = num_channels
        self.slew_rate_deg_s = slew_rate_deg_s
        self.gripper_channel = gripper_channel
        self.gripper_open_deg = gripper_open_deg
        self.gripper_close_deg = gripper_close_deg

        self._target_deg = np.full(num_channels, float(initial_deg))
        self._current_deg = np.full(num_channels, float(initial_deg))

    def set_angle(self, channel, deg):
        """Command a single channel to `deg` (0-180) -- same call shape as
        `ServoKit.servo[channel].angle = deg`.
        """
        self._target_deg[channel] = np.clip(deg, 0.0, 180.0)

    def set_angles(self, channel_to_deg):
        """Command several channels at once from a dict OR a sequence
        indexed by channel number 0..N-1 (a real driver board call would
        normally be one `servo[i].angle = ...` per channel; this is just a
        convenience batch form used by `ServoDriverNode` so one `/joint_cmd`
        message updates every joint channel in one call).
        """
        if isinstance(channel_to_deg, dict):
            items = channel_to_deg.items()
        else:
            items = enumerate(channel_to_deg)
        for channel, deg in items:
            self.set_angle(channel, deg)

    def open_gripper(self):
        self.set_angle(self.gripper_channel, self.gripper_open_deg)

    def close_gripper(self):
        self.set_angle(self.gripper_channel, self.gripper_close_deg)

    def get_angle(self, channel):
        """Returns the servo's actual (slew-limited) current angle -- what a
        real setup would only know via an external position sensor; most
        real hobby-servo rigs don't have one and just trust the commanded
        angle, but exposing the true lagged position here is what lets
        `ServoDriverNode` publish a realistic `/joint_states`.
        """
        return self._current_deg[channel]

    def get_angles(self):
        return self._current_deg.copy()

    def step(self, dt_s):
        """Advances every channel's actual angle toward its commanded target
        by at most `slew_rate_deg_s * dt_s` degrees -- the rate-limited
        ("first-order slew-rate") model of real servo motion. Call this once
        per simulation tick, exactly like a real servo's horn is physically
        still moving between one PWM update and the next.
        """
        max_step = self.slew_rate_deg_s * dt_s
        delta = self._target_deg - self._current_deg
        clipped = np.clip(delta, -max_step, max_step)
        self._current_deg += clipped
