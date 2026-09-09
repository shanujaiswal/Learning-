"""Training loop for the sidewalk delivery robot's Q-learning policy.

This is the OFFLINE step: run many episodes of trial and error against
`DeliveryGridWorld`, letting the agent update its Q-table after every
single step (standard online tabular Q-learning), track reward-per-episode
as the evidence of learning, then save the learned table to disk.

This step has NO ROS2-style nodes at all -- training is not something a
real deployed delivery robot does on the sidewalk in front of customers;
it happens offline/in simulation beforehand, exactly like real delivery
robot companies train/validate policies in simulation before pushing a
policy update to the fleet. The ROS2-style node graph in `nodes.py` is for
the DEPLOYMENT/inference side only (see `main.py`), where the already-
trained table is just looked up, never updated.

Run:
    python train.py
"""

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from environment import DeliveryGridWorld, ACTIONS
from q_learning_agent import QLearningAgent, state_to_index

NUM_EPISODES = 2000
Q_TABLE_PATH = "trained_q_table.npy"
CURVE_PATH = "training_learning_curve.png"


def run_episode(env, agent, epsilon, learn=True):
    state = env.reset()
    state_idx = state_to_index(state)
    total_reward = 0.0
    steps = 0
    collided_with_pedestrian = False
    reached_goal = False

    done = False
    while not done:
        action_idx = agent.select_action(state_idx, epsilon)
        action = ACTIONS[action_idx]
        next_state, reward, done, info = env.step(action)
        next_state_idx = state_to_index(next_state)

        if learn:
            agent.update(state_idx, action_idx, reward, next_state_idx, done)

        state_idx = next_state_idx
        total_reward += reward
        steps += 1
        if info.get("collision") == "pedestrian":
            collided_with_pedestrian = True
        if env.agent_pos == env.goal:
            reached_goal = True

    return total_reward, steps, reached_goal, collided_with_pedestrian


def moving_average(values, window=50):
    values = np.asarray(values, dtype=np.float64)
    if len(values) < window:
        return values
    kernel = np.ones(window) / window
    return np.convolve(values, kernel, mode="valid")


def main():
    print("=" * 78)
    print("TRAINING: Q-LEARNING POLICY FOR SIDEWALK DELIVERY NAVIGATION")
    print("=" * 78)

    env = DeliveryGridWorld()
    agent = QLearningAgent(num_states=DeliveryGridWorld.num_states())

    episode_rewards = []
    episode_steps = []
    episode_success = []

    for ep in range(NUM_EPISODES):
        epsilon = agent.epsilon_for_episode(ep)
        reward, steps, reached_goal, _ = run_episode(env, agent, epsilon, learn=True)
        episode_rewards.append(reward)
        episode_steps.append(steps)
        episode_success.append(reached_goal)

        if (ep + 1) % 200 == 0:
            recent = episode_rewards[-200:]
            recent_success = np.mean(episode_success[-200:]) * 100
            print(f"Episode {ep + 1:5d}/{NUM_EPISODES} | epsilon={epsilon:.3f} | "
                  f"avg reward (last 200)={np.mean(recent):7.2f} | "
                  f"success rate (last 200)={recent_success:5.1f}%")

    agent.save(Q_TABLE_PATH)
    print(f"\nSaved learned Q-table to {Q_TABLE_PATH} "
          f"(shape {agent.q_table.shape}, {np.count_nonzero(agent.q_table)} nonzero entries).")

    # --- Learning-curve evidence -------------------------------------------
    first_100_avg = np.mean(episode_rewards[:100])
    last_100_avg = np.mean(episode_rewards[-100:])
    first_100_success = np.mean(episode_success[:100]) * 100
    last_100_success = np.mean(episode_success[-100:]) * 100
    print(f"\nFirst 100 episodes: avg reward={first_100_avg:7.2f}, success rate={first_100_success:5.1f}%")
    print(f"Last  100 episodes: avg reward={last_100_avg:7.2f}, success rate={last_100_success:5.1f}%")
    print(f"Improvement: {last_100_avg - first_100_avg:+.2f} avg reward, "
          f"{last_100_success - first_100_success:+.1f} pts success rate.")

    fig, axes = plt.subplots(1, 2, figsize=(13, 5))
    fig.suptitle("Q-Learning Training: Reward per Episode")

    ax = axes[0]
    ax.plot(episode_rewards, color="tab:blue", alpha=0.25, linewidth=0.6, label="reward per episode")
    ma = moving_average(episode_rewards, window=50)
    ax.plot(np.arange(len(ma)) + 49, ma, color="tab:red", linewidth=2.2, label="50-episode moving average")
    ax.set_xlabel("episode"); ax.set_ylabel("total reward")
    ax.set_title("Reward per episode (raw + moving average)")
    ax.legend(fontsize=8); ax.grid(alpha=0.3)

    ax = axes[1]
    success_ma = moving_average([1.0 if s else 0.0 for s in episode_success], window=50) * 100
    ax.plot(np.arange(len(success_ma)) + 49, success_ma, color="tab:green", linewidth=2.0)
    ax.set_xlabel("episode"); ax.set_ylabel("success rate, % (50-ep moving avg)")
    ax.set_title("Goal-reach success rate over training")
    ax.set_ylim(0, 105); ax.grid(alpha=0.3)

    plt.tight_layout()
    plt.savefig(CURVE_PATH, dpi=120)
    print(f"Saved learning-curve plot to {CURVE_PATH}")


if __name__ == "__main__":
    main()
