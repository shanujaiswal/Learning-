"""
07 - Simple Reinforcement Learning (Q-Learning)
==================================================
Demonstrates: Reinforcement Learning Fundamentals.

A from-scratch tabular Q-learning agent that learns to navigate a tiny
grid world. No external RL libraries (e.g. gym) are used -- the
environment is a plain Python class with a handful of states/actions,
and the agent is a plain numpy Q-table updated with the classic
Q-learning rule:

    Q(s, a) <- Q(s, a) + alpha * (reward + gamma * max_a' Q(s', a') - Q(s, a))

The grid is a 4x4 world:

    S  .  .  .
    .  #  .  .
    .  #  .  .
    .  .  .  G

S = start, G = goal (+10 reward), # = wall (blocked move, small penalty),
every other step costs -1 (encourages the agent to find a short path).
"""

import numpy as np

GRID_SIZE = 4
START = (0, 0)
GOAL = (3, 3)
WALLS = {(1, 1), (2, 1)}

ACTIONS = ["up", "down", "left", "right"]
ACTION_DELTAS = {
    "up": (-1, 0),
    "down": (1, 0),
    "left": (0, -1),
    "right": (0, 1),
}


class GridWorld:
    """A tiny deterministic grid-world environment."""

    def __init__(self):
        self.state = START

    def reset(self):
        self.state = START
        return self.state

    def step(self, action):
        row, col = self.state
        d_row, d_col = ACTION_DELTAS[action]
        new_row, new_col = row + d_row, col + d_col

        # Out of bounds -> stay in place, small penalty.
        if not (0 <= new_row < GRID_SIZE and 0 <= new_col < GRID_SIZE):
            return self.state, -1.0, False

        # Wall -> stay in place, small penalty.
        if (new_row, new_col) in WALLS:
            return self.state, -1.0, False

        self.state = (new_row, new_col)

        if self.state == GOAL:
            return self.state, 10.0, True

        return self.state, -1.0, False


def state_to_index(state):
    row, col = state
    return row * GRID_SIZE + col


def choose_action(q_table, state_idx, epsilon):
    if np.random.rand() < epsilon:
        return np.random.randint(len(ACTIONS))
    return int(np.argmax(q_table[state_idx]))


def train(num_episodes=500, alpha=0.1, gamma=0.95, epsilon=0.2, max_steps=100):
    env = GridWorld()
    n_states = GRID_SIZE * GRID_SIZE
    q_table = np.zeros((n_states, len(ACTIONS)))

    episode_rewards = []

    for episode in range(num_episodes):
        state = env.reset()
        state_idx = state_to_index(state)
        total_reward = 0.0

        for _ in range(max_steps):
            action_idx = choose_action(q_table, state_idx, epsilon)
            action = ACTIONS[action_idx]

            next_state, reward, done = env.step(action)
            next_state_idx = state_to_index(next_state)

            # Q-learning update rule.
            best_next = np.max(q_table[next_state_idx])
            td_target = reward + gamma * best_next
            td_error = td_target - q_table[state_idx, action_idx]
            q_table[state_idx, action_idx] += alpha * td_error

            state_idx = next_state_idx
            total_reward += reward

            if done:
                break

        episode_rewards.append(total_reward)

    return q_table, episode_rewards


def extract_greedy_path(q_table, max_steps=20):
    env = GridWorld()
    state = env.reset()
    path = [state]

    for _ in range(max_steps):
        state_idx = state_to_index(state)
        action_idx = int(np.argmax(q_table[state_idx]))
        action = ACTIONS[action_idx]
        state, _, done = env.step(action)
        path.append(state)
        if done:
            break

    return path


def main():
    np.random.seed(42)

    print("Grid world (4x4): S=start, G=goal, #=wall")
    print("S  .  .  .")
    print(".  #  .  .")
    print(".  #  .  .")
    print(".  .  .  G")

    q_table, episode_rewards = train(num_episodes=500)

    # Show improvement across training by averaging rewards in blocks.
    n_blocks = 10
    block_size = len(episode_rewards) // n_blocks
    print("\n=== Average total reward per training block (learning curve) ===")
    for block in range(n_blocks):
        start_idx = block * block_size
        end_idx = start_idx + block_size
        block_avg = np.mean(episode_rewards[start_idx:end_idx])
        episodes_label = f"episodes {start_idx + 1}-{end_idx}"
        print(f"{episodes_label:>20}: avg reward = {block_avg:.2f}")

    first_block_avg = np.mean(episode_rewards[:block_size])
    last_block_avg = np.mean(episode_rewards[-block_size:])
    print(
        f"\nImprovement: {first_block_avg:.2f} -> {last_block_avg:.2f} "
        "(reward increases as the agent learns a shorter path to the goal)."
    )

    print("\n=== Learned Q-table (rows = states, cols = actions) ===")
    print(f"{'state':>10}" + "".join(f"{a:>10}" for a in ACTIONS))
    for row in range(GRID_SIZE):
        for col in range(GRID_SIZE):
            state = (row, col)
            idx = state_to_index(state)
            values = "".join(f"{q_table[idx, a]:>10.2f}" for a in range(len(ACTIONS)))
            print(f"{str(state):>10}{values}")

    path = extract_greedy_path(q_table)
    print("\n=== Greedy path learned by the agent (start -> goal) ===")
    print(" -> ".join(str(s) for s in path))


if __name__ == "__main__":
    main()
