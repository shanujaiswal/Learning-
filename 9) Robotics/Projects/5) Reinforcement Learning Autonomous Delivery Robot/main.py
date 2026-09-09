"""Deployment/evaluation run for the sidewalk delivery robot -- loads the
Q-table `train.py` already learned and runs it, greedily (no exploration),
through the ROS2-style node graph in `nodes.py`.

Run (two-step flow -- train once, then deploy/evaluate as many times as
you like against the same learned table):
    python train.py
    python main.py

Because the two pedestrians' scripted beats are deterministic given a
fixed reset, but real sidewalk foot traffic obviously isn't always at the
same phase of its walk when a delivery starts, this script evaluates the
learned policy over multiple episodes with the pedestrians started at
randomized phases of their beats -- exactly so a single lucky/unlucky run
can't be mistaken for the policy's real performance, which is what the
project brief calls out as the honest way to report an RL result.
"""

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from environment import DeliveryGridWorld, GRID_ROWS, GRID_COLS
import hardware
import ros2_lite as rclpy
from nodes import SensorNode, PolicyNode, MotionExecutorNode

Q_TABLE_PATH = "trained_q_table.npy"
NUM_EVAL_EPISODES = 100
RESULT_PLOT_PATH = "delivery_policy_result.png"


def run_one_episode(q_table, clock_dt=0.1, randomize_pedestrian_phase=True, seed=0):
    """Builds a fresh set of nodes wired through `ros2_lite`, resets the
    world, and spins until the episode ends (goal reached, pedestrian
    collision, or timeout). Returns the `MotionExecutorNode` so the caller
    can read off the outcome, plus the environment for plotting.
    """
    rclpy.reset_bus()
    env = DeliveryGridWorld(seed=seed)
    env.reset()

    if randomize_pedestrian_phase:
        rng = np.random.default_rng(seed)
        for p in env.pedestrians:
            p._idx = rng.integers(0, len(p.path_cells))
            p._forward = bool(rng.integers(0, 2))

    clock = rclpy.SimClock(dt_s=clock_dt)
    hal_drive = hardware.DiffDriveCellHAL(env)
    hal_proximity = hardware.ProximitySensorHAL(env)

    sensor_node = SensorNode(hal_drive, hal_proximity, clock)
    policy_node = PolicyNode(q_table, clock)
    executor_node = MotionExecutorNode(hal_drive, clock)

    max_ticks_s = clock_dt * (env.rows + env.cols) * 6  # generous cap vs. MAX_STEPS
    while not executor_node.episode_done and clock.t < max_ticks_s:
        clock.t += clock.dt
        for node in (sensor_node, policy_node, executor_node):
            for timer in node._timers:
                period_s, callback, elapsed = timer
                elapsed += clock.dt
                if elapsed >= period_s:
                    elapsed = 0.0
                    callback()
                timer[2] = elapsed

    return executor_node, env


def main():
    print("=" * 78)
    print("DEPLOYMENT: SIDEWALK DELIVERY ROBOT RUNNING THE TRAINED Q-LEARNING POLICY")
    print("=" * 78)

    try:
        q_table = np.load(Q_TABLE_PATH)
    except FileNotFoundError:
        raise SystemExit(f"No trained Q-table found at '{Q_TABLE_PATH}' -- run `python train.py` first.")

    print(f"Loaded Q-table {q_table.shape} from {Q_TABLE_PATH}.")
    print(f"\nRunning {NUM_EVAL_EPISODES} evaluation episodes (greedy policy, "
          f"randomized pedestrian phase per episode)...")

    successes, steps_list, rewards_list, collisions = [], [], [], 0
    first_episode_exec, first_episode_env = None, None

    for ep in range(NUM_EVAL_EPISODES):
        executor_node, env = run_one_episode(q_table, seed=1000 + ep)
        successes.append(executor_node.reached_goal)
        steps_list.append(executor_node.steps)
        rewards_list.append(executor_node.total_reward)
        if executor_node.collided_with_pedestrian:
            collisions += 1
        if ep == 0:
            first_episode_exec, first_episode_env = executor_node, env

    success_rate = np.mean(successes) * 100
    avg_steps = np.mean([s for s, ok in zip(steps_list, successes) if ok]) if any(successes) else float("nan")
    avg_reward = np.mean(rewards_list)

    print(f"\nResults over {NUM_EVAL_EPISODES} evaluation episodes:")
    print(f"  Success rate (reached doorstep):     {success_rate:5.1f}%")
    print(f"  Avg steps-to-goal (successful eps):  {avg_steps:5.1f}")
    print(f"  Avg total reward per episode:         {avg_reward:6.2f}")
    print(f"  Pedestrian collisions:                {collisions} / {NUM_EVAL_EPISODES}")

    # --- Plot: final path over one representative episode + layout --------
    fig, ax = plt.subplots(figsize=(7, 7))
    env = first_episode_env
    grid_colors = np.zeros((env.rows, env.cols, 3))
    for r in range(env.rows):
        for c in range(env.cols):
            ch = env.grid[r][c]
            if ch == '#':
                grid_colors[r, c] = (0.25, 0.25, 0.25)      # obstacle
            elif ch == 'S':
                grid_colors[r, c] = (0.85, 0.75, 0.55)      # street
            else:
                grid_colors[r, c] = (0.93, 0.93, 0.93)      # sidewalk

    ax.imshow(grid_colors, origin="upper")
    trail = np.array([env.start] + first_episode_exec.trail)
    ax.plot(trail[:, 1], trail[:, 0], color="tab:blue", linewidth=2.5, marker="o",
             markersize=4, label="robot path (learned policy)")
    ax.plot(env.start[1], env.start[0], "s", color="black", markersize=14, label="depot (start)")
    ax.plot(env.goal[1], env.goal[0], "*", color="gold", markeredgecolor="black",
             markersize=20, label="doorstep (goal)")
    for i, p in enumerate(env.pedestrians):
        beat = np.array(p.path_cells)
        ax.plot(beat[:, 1], beat[:, 0], "--", color="tab:red", alpha=0.4, linewidth=1)
        ax.plot(p.position[1], p.position[0], "P", color="tab:red", markersize=12,
                 label=f"pedestrian {i + 1} (final pos.)")

    ax.set_xticks(range(env.cols)); ax.set_yticks(range(env.rows))
    ax.set_xticklabels([]); ax.set_yticklabels([])
    ax.grid(color="white", linewidth=1)
    outcome = "reached doorstep" if first_episode_exec.reached_goal else \
        ("hit a pedestrian" if first_episode_exec.collided_with_pedestrian else "timed out")
    ax.set_title(f"Learned delivery route (episode 1 of {NUM_EVAL_EPISODES}: {outcome})")
    ax.legend(fontsize=8, loc="upper left", bbox_to_anchor=(1.02, 1.0))
    plt.tight_layout()
    plt.savefig(RESULT_PLOT_PATH, dpi=120)
    print(f"\nSaved final path/layout plot to {RESULT_PLOT_PATH}")


if __name__ == "__main__":
    main()
