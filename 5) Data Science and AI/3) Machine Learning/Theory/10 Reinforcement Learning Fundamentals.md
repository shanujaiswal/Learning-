# Recap -- Where RL Fits Among the Three ML Paradigms

--> The Machine Learning Fundamentals file introduced Reinforcement Learning (RL) as the third major ML paradigm, alongside Supervised and Unsupervised Learning, but deferred a full treatment to this file. Unlike Supervised Learning (learning from labeled examples of correct answers) or Unsupervised Learning (finding structure with no labels at all), RL learns through TRIAL AND ERROR -- an agent takes actions in an environment and learns from the CONSEQUENCES of those actions, with no dataset of "correct answers" provided upfront at all.

# The Core RL Framework

--> **Agent** -- the learner/decision-maker (a game-playing AI, a robot, a trading algorithm).
--> **Environment** -- everything the agent interacts with and that responds to its actions (a game board, the physical world, a stock market simulation).
--> **State** -- the current situation the agent observes (the board position in chess, a robot's current sensor readings).
--> **Action** -- a choice the agent makes (a chess move, a robot's motor command).
--> **Reward** -- a numeric signal the environment gives back after each action, indicating how good/bad that action was -- the ONLY feedback signal RL has to learn from, a fundamentally different learning signal from Supervised Learning's direct labeled examples.

```
       ┌─────────┐  action   ┌─────────────┐
       │  Agent   │ --------> │ Environment │
       │          │ <-------- │             │
       └─────────┘  reward,   └─────────────┘
                    new state
```

--> This loop repeats continuously -- the agent observes a state, takes an action, receives a reward and a new state, and uses that experience to gradually improve its decision-making, called its "policy."

# The Policy -- What the Agent Actually Learns

--> A Policy is the agent's strategy -- a mapping from STATES to ACTIONS, essentially "what should I do given this situation" -- the ENTIRE goal of RL training is to find a policy that maximizes CUMULATIVE reward over time, not just the immediate next reward.
--> **Why cumulative, not just immediate, reward matters** -- a chess move that captures a piece (immediate reward) but leads to checkmate against you three moves later (a huge eventual negative reward) is a BAD move overall, despite looking good in the immediate moment -- RL must learn to value actions based on their long-term consequences, not just their instant payoff, directly motivating the concept of "discounted future reward" covered next.

# The Exploration vs Exploitation Trade-off

--> At any point, an agent must choose between EXPLOITING what it currently believes is the best known action, or EXPLORING a different, untried action that MIGHT turn out to be even better -- purely exploiting known-good actions risks getting permanently stuck with a mediocre strategy simply because a better one was never tried; purely exploring randomly forever means never actually capitalizing on what's already been learned.
--> **Epsilon-Greedy** -- a simple, common strategy -- with probability epsilon (e.g. 10%), take a completely random action (explore); otherwise, take the currently-best-known action (exploit) -- epsilon is often gradually DECREASED over training, so the agent explores heavily early on (when it knows little) and increasingly exploits its accumulated knowledge as training progresses.

# Q-Learning -- A Foundational RL Algorithm

--> Q-Learning learns a "Q-value" for every (state, action) pair -- an estimate of the total future reward expected from taking that specific action in that specific state, and then acting optimally afterward.

```python
import numpy as np

# A simplified Q-table: rows = states, columns = actions
q_table = np.zeros((num_states, num_actions))

learning_rate = 0.1
discount_factor = 0.95   # How much future reward matters vs immediate reward

for episode in range(num_episodes):
    state = env.reset()
    done = False
    while not done:
        if np.random.random() < epsilon:
            action = env.action_space.sample()          # Explore -- random action
        else:
            action = np.argmax(q_table[state])            # Exploit -- best known action

        next_state, reward, done, _ = env.step(action)

        # The core Q-Learning update rule
        best_next_q = np.max(q_table[next_state])
        q_table[state, action] += learning_rate * (
            reward + discount_factor * best_next_q - q_table[state, action]
        )
        state = next_state
```

--> The **discount factor** (`0.95` above) determines how much the agent values FUTURE reward compared to immediate reward -- a value close to 1 makes the agent very "far-sighted" (heavily weighting long-term consequences), a value close to 0 makes it "short-sighted" (caring mostly about immediate reward) -- directly analogous to a financial discount rate valuing money received today more than the same amount received far in the future.
--> **The core update rule** is, at its heart, the same gradient-based error-correction idea covered in the Calculus and Optimization file -- the agent compares its CURRENT estimate of a state-action's value against a slightly better-informed estimate (based on the actual reward just received plus the best next state's value), and nudges its estimate toward that improved target, repeated over and over across many episodes of experience.

# Deep Reinforcement Learning -- Combining RL With Neural Networks

--> A plain Q-table (as shown above) only works when the number of possible states is small enough to enumerate explicitly -- completely impractical for a real chess board's astronomical number of possible positions, or for continuous sensor readings from a real robot.
--> **Deep Q-Networks (DQN)** replace the Q-table with a NEURAL NETWORK (covered in the Deep Learning folder) that takes a state as input and outputs an estimated Q-value for every possible action -- letting the agent generalize its learned knowledge to states it has never exactly seen before, rather than needing to have visited every single state individually during training.

# Landmark Real-World Successes

--> **AlphaGo** (referenced in the AI Fundamentals and Search Algorithms file) combined Deep Reinforcement Learning with the Monte Carlo Tree Search technique (an extension of the search algorithms covered in that file) to defeat the world's best human Go players -- a landmark result specifically because Go's search space is vastly too large for the classical Minimax/Alpha-Beta pruning approaches used for simpler games like chess to handle directly, requiring learned position evaluation (via the neural network) to guide the search efficiently.
--> **Robotics** -- RL is used to train robots to walk, grasp objects, and perform complex physical manipulation tasks through simulated trial and error (millions of simulated attempts, since real-world physical trial and error would be far too slow and potentially damaging to actual hardware) before being deployed to real hardware.
--> **RLHF in Large Language Models** -- directly connecting to the Generative AI, LLMs and AI Ethics file's coverage of Reinforcement Learning from Human Feedback -- the "reward" in this specific application comes from human raters' preferences between different model outputs, rather than a game score or a robot's sensor readings, illustrating that RL's core framework (agent, action, reward, policy) generalizes well beyond games and robotics into shaping language model behavior itself.

# Why RL Is Considered Harder Than Supervised Learning in Practice

--> **Sparse and delayed rewards** -- in many real problems, meaningful reward signal arrives only rarely (winning or losing a whole game, after potentially hundreds of individual moves) -- making it genuinely difficult for the agent to figure out WHICH of its many earlier actions actually deserves credit for an eventual win or loss, a challenge specifically called the "credit assignment problem."
--> **Sample inefficiency** -- RL agents frequently require an enormous number of trial-and-error interactions to learn effectively, which is straightforward in a fast simulated environment (a video game) but can be prohibitively slow, expensive, or physically risky in the real world (training a physical robot, or a real financial trading strategy) -- directly motivating the heavy reliance on simulation-based training before any real-world deployment.
--> **Non-stationarity during training** -- as the agent's policy improves, the DISTRIBUTION of states/situations it actually encounters changes too (a better chess player reaches very different board positions than a beginner), making RL training a genuinely more unstable, harder-to-diagnose process than standard Supervised Learning's fixed, unchanging training dataset.
