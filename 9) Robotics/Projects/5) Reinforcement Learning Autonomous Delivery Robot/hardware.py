"""Hardware Abstraction Layer (HAL) -- same role as Project 1's `hardware.py`:
the one place in this project that touches the simulated world directly.
Every node in `nodes.py` talks only to the classes below, never to
`DeliveryGridWorld` itself.

A real sidewalk delivery robot (Starship/Kiwibot-style) is still a
differential-drive chassis underneath -- it just executes discrete
"advance one sidewalk segment" / "turn to face the next segment" moves at
the routing layer, translated down into continuous `set_wheel_speeds`
calls at the motor-driver layer exactly like Project 1's AMR. This HAL
exposes that same discrete-move-in, wheel-speed-out shape:

    hal.drive_cell(direction)          # command one grid-cell move
    hal.read_odometry_cell()           # where the chassis ended up
    hal.get_danger_bitmask()           # short-range proximity sensor read

`DiffDriveCellHAL.drive_cell()` internally converts the requested
direction into a differential-drive (v, omega) command sequence -- turn to
face the new heading, then drive forward one cell length -- the same
translation a real navigation stack's local controller does when it
converts a discrete route step into wheel commands. Swap this class for
one that calls a real motor-driver board's `set_wheel_speeds` and a real
short-range sensor's API, and every node above the HAL keeps working
unmodified, exactly as in Project 1.
"""

import numpy as np

from environment import DeliveryGridWorld, ACTIONS

_HEADING_FOR_ACTION = {
    "N": np.pi / 2,
    "S": -np.pi / 2,
    "E": 0.0,
    "W": np.pi,
}


class DiffDriveCellHAL:
    """Wraps `DeliveryGridWorld` behind a differential-drive-shaped call
    surface: commanding a cell move is modeled as (1) rotate in place to
    the target heading, (2) drive forward one cell length -- exactly the
    two-phase motion a real diff-drive chassis performs, just quantized to
    the grid. Cell-length and wheel-base are nominal (delivery robots are
    small, ~0.5 m footprint on a ~1 m sidewalk-segment grid).
    """

    def __init__(self, env: DeliveryGridWorld, cell_length_m=1.0, wheel_base_m=0.35):
        self.env = env
        self.cell_length_m = cell_length_m
        self.wheel_base_m = wheel_base_m
        self.heading = _HEADING_FOR_ACTION["E"]
        self._last_reward = 0.0
        self._last_info = {}
        self._done = False

    def drive_cell(self, direction):
        """Commands one grid-cell move in `direction` ('N'/'S'/'E'/'W').
        Internally this is "rotate to heading, then drive forward one cell
        length" -- reported here as the equivalent wheel-speed command a
        real motor-driver board would receive, purely for realism/logging;
        the actual world update goes through `DeliveryGridWorld.step()`,
        which is the ground-truth kinematics for this project (a real HAL
        would instead be reading back actual encoder ticks).
        """
        assert direction in ACTIONS
        target_heading = _HEADING_FOR_ACTION[direction]
        omega_command = target_heading - self.heading   # rotate-in-place command
        v_command = self.cell_length_m                   # drive-forward command (one cell)
        self.heading = target_heading

        next_state, reward, done, info = self.env.step(direction)
        self._last_reward, self._last_info, self._done = reward, info, done
        return {"v_mps": v_command, "omega_rad_s": omega_command}

    def read_odometry_cell(self):
        """Returns the chassis's current (row, col) cell -- the discrete
        equivalent of reading back wheel-encoder dead reckoning.
        """
        return self.env.agent_pos

    def last_step_result(self):
        return self._last_reward, self._last_info, self._done


class ProximitySensorHAL:
    """Stand-in for a real short-range proximity sensor ring (ultrasonic /
    IR bump sensors, or a coarse LIDAR danger zone) -- returns the 4-bit
    N/S/E/W danger bitmask described in `environment.py`, without exposing
    anything else about the world (no pedestrian identities, no future
    positions -- just "something is right next to me, which side").
    """

    def __init__(self, env: DeliveryGridWorld):
        self.env = env

    def get_danger_bitmask(self):
        return self.env._danger_bitmask(self.env.agent_pos)
