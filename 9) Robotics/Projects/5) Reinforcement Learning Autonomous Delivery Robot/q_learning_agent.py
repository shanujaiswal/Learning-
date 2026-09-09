"""Tabular Q-learning agent -- the actual reinforcement-learning algorithm
this project is teaching. No neural net, no toy stand-in: a real Q-table,
a real epsilon-greedy behavior policy with decay, and the real Bellman
optimality update, learned from live interaction with `DeliveryGridWorld`.

    Q(s, a) <- Q(s, a) + alpha * (r + gamma * max_a' Q(s', a') - Q(s, a))

This is appropriate here (rather than deep RL) precisely because the state
space is small and fully enumerable (see `environment.py`'s docstring on
the state representation) -- exactly the regime tabular Q-learning is
designed for, and it converges to the same thing a much heavier deep-RL
setup would, just faster and with a table you can print and read.
"""

import numpy as np

from environment import ACTIONS, DeliveryGridWorld


class QLearningAgent:
    def __init__(self, num_states, num_actions=len(ACTIONS), alpha=0.15, gamma=0.95,
                 epsilon_start=1.0, epsilon_end=0.05, epsilon_decay_episodes=800, seed=0):
        self.num_states = num_states
        self.num_actions = num_actions
        self.alpha = alpha
        self.gamma = gamma
        self.epsilon_start = epsilon_start
        self.epsilon_end = epsilon_end
        self.epsilon_decay_episodes = epsilon_decay_episodes
        self.rng = np.random.default_rng(seed)
        self.q_table = np.zeros((num_states, num_actions), dtype=np.float64)

    def epsilon_for_episode(self, episode_idx):
        """Linear decay from `epsilon_start` to `epsilon_end` over
        `epsilon_decay_episodes`, then held at `epsilon_end` -- standard
        exploration schedule: explore heavily early (the Q-table is all
        zeros and knows nothing), exploit more as estimates firm up.
        """
        frac = min(1.0, episode_idx / max(1, self.epsilon_decay_episodes))
        return self.epsilon_start + frac * (self.epsilon_end - self.epsilon_start)

    def select_action(self, state_idx, epsilon):
        """Epsilon-greedy: explore uniformly at random with prob. epsilon,
        else exploit the current best-known action (ties broken randomly
        so the agent doesn't get stuck always preferring action index 0
        during early training when many Q-values are still exactly 0).
        """
        if self.rng.random() < epsilon:
            return self.rng.integers(self.num_actions)
        row = self.q_table[state_idx]
        best = np.flatnonzero(row == row.max())
        return int(self.rng.choice(best))

    def greedy_action(self, state_idx):
        row = self.q_table[state_idx]
        best = np.flatnonzero(row == row.max())
        return int(self.rng.choice(best))

    def update(self, state_idx, action_idx, reward, next_state_idx, done):
        """The real Bellman update -- off-policy TD(0) target using max
        over next-state actions (Q-learning proper, as opposed to SARSA's
        on-policy update).
        """
        current_q = self.q_table[state_idx, action_idx]
        target = reward if done else reward + self.gamma * np.max(self.q_table[next_state_idx])
        self.q_table[state_idx, action_idx] = current_q + self.alpha * (target - current_q)

    def save(self, path):
        np.save(path, self.q_table)

    def load(self, path):
        self.q_table = np.load(path)


def state_to_index(state):
    return DeliveryGridWorld.state_index(state)


def index_to_action(idx):
    return ACTIONS[idx]


def action_to_index(action):
    return ACTIONS.index(action)
