"""Ground-truth city block for the delivery robot -- the ONE place in this
project that knows the full layout (blocked cells, sidewalk/street cell
types, pedestrian schedules) and hands out only what a real robot could
plausibly observe: its own cell, and short-range proximity info about
obstacles/pedestrians, via `DeliveryGridWorld.step()`.

This mirrors Project 1's split between `world.py` (ground truth) and
`hardware.py` (the sensor/actuator interface): here the "sensor" is the
grid-world observation returned by `step()`/`reset()`, and the "actuator"
is the one-of-four discrete `action` the RL agent hands back in.

Why a grid, and why THIS state representation
------------------------------------------------
Tabular Q-learning needs a finite, small state space to be tractable (no
neural net doing function approximation here -- every state gets its own
row in a literal table). A naive state of "every pedestrian's exact cell"
would blow the table up to rows*cols*rows*cols*rows*cols entries for just
2 pedestrians on an 8x8 grid (~1.7M rows) -- workable but wasteful, and it
wouldn't generalize: the agent would have to separately learn "pedestrian
at (3,4)" and "pedestrian at (3,5)" as unrelated situations even though the
right reaction (don't step there) is the same relative fact.

Instead the observed state is:
    (robot_row, robot_col, danger_bitmask)
where `danger_bitmask` is 4 bits -- one per compass direction (N/S/E/W) --
each set if a pedestrian currently occupies the cell immediately adjacent
in that direction. That's the same idea as a real delivery robot's short-
range proximity sensors (ultrasonic/IR bump sensors, or a coarse LIDAR
danger zone) rather than a global tracker: "is something dangerous right
next to me, and which side" generalizes across the whole grid instead of
memorizing per-cell pedestrian coincidences, and keeps the table to
rows * cols * 16 entries -- small enough to learn in seconds.

The static layout (blocked cells / sidewalk vs. street) is NOT re-encoded
into the state on top of (row, col), because (row, col) already uniquely
determines it -- the agent learns "don't step into cell (2,5), it's
blocked" and "cell (4,4) is a street cell, it costs more" directly through
the reward signal at each (row, col), exactly the way tabular Q-learning is
supposed to work for a static part of the environment.
"""

import numpy as np

# --- Grid layout -----------------------------------------------------------
# 8x8 city block. Cell types:
#   '.'  sidewalk (cheap to traverse -- preferred)
#   '#'  static obstacle (parked car / planter / curb -- impassable)
#   'S'  street (passable but costly -- robots should prefer the sidewalk)
#   'D'  depot (start)
#   'G'  doorstep (goal)
GRID_ROWS = 8
GRID_COLS = 8

LAYOUT = [
    "D.....#.",
    ".#.SSSS.",
    ".#.S..#.",
    ".#.S.##.",
    "...S....",
    ".###S.#.",
    ".....S..",
    "#.....SG",
]

ACTIONS = ["N", "S", "E", "W"]
_ACTION_DELTA = {
    "N": (-1, 0),
    "S": (1, 0),
    "E": (0, 1),
    "W": (0, -1),
}

# Reward shaping. Tuned so that:
#  - the shortest sidewalk-only route is clearly the highest-return path,
#  - cutting through the street is allowed but costs more than the detour
#    saves in most cases (a soft preference, not a hard rule -- exactly the
#    kind of thing that's awkward to hand-code into a classical planner's
#    cost function without enumerating every case),
#  - walking into a pedestrian's current cell is a hard failure (ends the
#    episode) -- the delivery robot should never accept that risk,
#  - being adjacent to a pedestrian (but not colliding) is merely
#    discouraged, a "near-miss" penalty, so the policy learns to keep a
#    little distance without being told exactly which cells to avoid.
STEP_PENALTY_SIDEWALK = -1.0
STEP_PENALTY_STREET = -3.0
BUMP_OBSTACLE_PENALTY = -5.0      # walked into a parked car / planter / curb
NEAR_MISS_PENALTY = -2.0          # pedestrian in an adjacent cell this step
PEDESTRIAN_COLLISION_PENALTY = -25.0
GOAL_REWARD = 50.0
MAX_STEPS = 60


class Pedestrian:
    """A pedestrian walking a short fixed beat back and forth (e.g. pacing
    a stretch of sidewalk in front of a shop) -- simple scripted movement,
    not itself learned, exactly like Project 1's forklift following a fixed
    patrol line. The robot has to learn to react to it, not predict it from
    a model.
    """

    def __init__(self, path_cells):
        self.path_cells = path_cells          # list of (row, col), a there-and-back beat
        self._forward = True
        self._idx = 0

    @property
    def position(self):
        return self.path_cells[self._idx]

    def step(self):
        if self._forward:
            if self._idx + 1 < len(self.path_cells):
                self._idx += 1
            else:
                self._forward = False
                self._idx -= 1
        else:
            if self._idx - 1 >= 0:
                self._idx -= 1
            else:
                self._forward = True
                self._idx += 1


class DeliveryGridWorld:
    """Sidewalk delivery-robot gridworld: static obstacles, two pedestrians
    on scripted beats, one depot (start) and one doorstep (goal).
    """

    def __init__(self, seed=0):
        self.rows = GRID_ROWS
        self.cols = GRID_COLS
        self.grid = [list(row) for row in LAYOUT]
        self.start = self._find('D')
        self.goal = self._find('G')
        self.rng = np.random.default_rng(seed)

        # Two pedestrian beats, chosen to cross the robot's natural route
        # near the street crossing and near the final approach -- the two
        # places a real sidewalk-delivery robot actually has to negotiate
        # around foot traffic.
        self.pedestrians = [
            Pedestrian([(4, 1), (4, 2), (4, 3), (4, 4), (4, 5)]),
            Pedestrian([(6, 4), (6, 5), (6, 6), (6, 7)]),
        ]

        self.agent_pos = None
        self.t = 0

    def _find(self, ch):
        for r in range(self.rows):
            for c in range(self.cols):
                if self.grid[r][c] == ch:
                    return (r, c)
        raise ValueError(f"layout has no '{ch}' cell")

    def _cell_type(self, pos):
        return self.grid[pos[0]][pos[1]]

    def _in_bounds(self, pos):
        r, c = pos
        return 0 <= r < self.rows and 0 <= c < self.cols

    def _danger_bitmask(self, pos):
        ped_cells = {p.position for p in self.pedestrians}
        bits = 0
        for i, act in enumerate(ACTIONS):
            dr, dc = _ACTION_DELTA[act]
            neighbor = (pos[0] + dr, pos[1] + dc)
            if neighbor in ped_cells:
                bits |= (1 << i)
        return bits

    def observe(self):
        return (self.agent_pos[0], self.agent_pos[1], self._danger_bitmask(self.agent_pos))

    def reset(self):
        self.agent_pos = self.start
        self.t = 0
        # Reset pedestrians to the start of their beats so every episode's
        # early steps are comparable -- real evaluation runs instead let
        # pedestrian phase vary (see `evaluate.py`) to test robustness.
        for p in self.pedestrians:
            p._idx = 0
            p._forward = True
        return self.observe()

    def step(self, action):
        """Returns (next_state, reward, done, info). `action` is one of
        ACTIONS. Pedestrians advance one beat-step every environment step,
        independent of the robot's action -- they don't wait for it, just
        like real pedestrians.
        """
        assert action in ACTIONS
        self.t += 1
        dr, dc = _ACTION_DELTA[action]
        proposed = (self.agent_pos[0] + dr, self.agent_pos[1] + dc)

        info = {"collision": None}
        reward = 0.0
        done = False

        if not self._in_bounds(proposed) or self._cell_type(proposed) == '#':
            # Bumped a wall or a static obstacle -- stay in place, pay the
            # bump penalty. Not terminal: a real robot backs off and tries
            # something else, it doesn't shut down.
            reward += BUMP_OBSTACLE_PENALTY
            info["collision"] = "obstacle"
        else:
            self.agent_pos = proposed

        # Pedestrians move regardless of what the robot just did.
        for p in self.pedestrians:
            p.step()
        ped_cells = {p.position for p in self.pedestrians}

        if self.agent_pos in ped_cells:
            reward += PEDESTRIAN_COLLISION_PENALTY
            info["collision"] = "pedestrian"
            done = True
        elif self.agent_pos == self.goal:
            reward += GOAL_REWARD
            done = True
        else:
            cell_type = self._cell_type(self.agent_pos)
            reward += STEP_PENALTY_STREET if cell_type == 'S' else STEP_PENALTY_SIDEWALK
            if self._danger_bitmask(self.agent_pos) != 0 and info["collision"] is None:
                reward += NEAR_MISS_PENALTY
                info["near_miss"] = True

        if self.t >= MAX_STEPS and not done:
            done = True
            info["timeout"] = True

        return self.observe(), reward, done, info

    @staticmethod
    def num_states():
        return GRID_ROWS * GRID_COLS * 16

    @staticmethod
    def state_index(state):
        r, c, bitmask = state
        return (r * GRID_COLS + c) * 16 + bitmask
