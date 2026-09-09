# Project 5 — Reinforcement Learning Autonomous Delivery Robot

## Real-world scenario

A last-mile delivery company (the same category of product as real
Starship/Kiwibot sidewalk robots) runs small delivery robots from a depot
to customers' doorsteps across a city block. The robot has to:

1. Get from the depot to the doorstep without hitting parked cars,
   planters, or curbs (static obstacles).
2. React sensibly to pedestrians who are walking their own routes on the
   sidewalk, independent of anything the robot does.
3. Prefer the sidewalk over cutting across the street, without that
   preference being an absolute rule the robot must obey even when it
   would be absurd (e.g. if the sidewalk were fully blocked).

Rather than hand-coding a planner and a set of avoidance rules for all of
this (Project 1's approach), this robot's navigation policy is **learned**
from experience via tabular Q-learning, run through thousands of simulated
delivery attempts before ever being deployed.

## Why reinforcement learning here, and NOT another A*-plus-potential-field stack

Project 1's AMR uses a hand-designed pipeline: build a map, run A* over
it, follow the plan with potential-field local avoidance. That is the
right tool when the world's rules are crisp and can be written down as
hard constraints ("don't enter an occupied cell", "stay this far from a
moving obstacle") and when you need a plan you can verify in advance.

This project's world has **soft, competing preferences** that are awkward
to hand-encode as planner cost functions:

- "Prefer the sidewalk over the street, but not so rigidly that the robot
  should refuse to ever cross it."
- "Keep a little distance from pedestrians even when not colliding with
  them" — a *near-miss* penalty, not a hard exclusion zone with a fixed
  radius you have to tune per obstacle.
- Balancing "shortest route" against both of the above at the same time,
  for every possible situation the robot could find itself in.

A classical planner can express all of this too, in principle — but only
by someone sitting down and hand-tuning a cost function with enough terms
to capture it, then re-tuning it every time a new soft preference shows
up (add bike-lane avoidance next year? add "don't idle in front of a
doorway"? each one is another hand-written term). Reinforcement learning
instead lets the **reward function** encode those preferences directly
(`environment.py`'s `STEP_PENALTY_STREET`, `NEAR_MISS_PENALTY`,
`PEDESTRIAN_COLLISION_PENALTY`), and the agent discovers, through its own
trial and error, the routing behavior that best trades them off — without
anyone hand-designing the actual decision rule ("go this way, not that
way, because...").

### The honest trade-off — this is a real, current debate in delivery robotics

This is not a strictly-better replacement for Project 1's approach, and
real autonomous-delivery companies genuinely wrestle with this choice:

- **No optimality or safety guarantee.** A verified A* planner is
  *guaranteed* to find a shortest path across a known map, and a
  potential-field controller's avoidance behavior can be reasoned about
  analytically. A learned Q-table has none of that — it does whatever the
  reward function and training experience happened to teach it, and nothing
  stops it from having a blind spot in a state it rarely saw during
  training. This project's own evaluation numbers below show a *strong*
  policy, not a certified-safe one.
- **Training cost.** The policy here needed ~2,000 simulated episodes
  before it reliably reached the doorstep — cheap in a gridworld, but real
  delivery robots' RL policies are trained (and re-validated) in much more
  expensive photorealistic simulation before ever touching a real
  sidewalk, and still typically ship behind a classical safety layer
  (hard-coded "never enter this zone" checks) precisely because nobody is
  willing to bet pedestrian safety purely on a learned policy's generalization.
- **Explainability.** "The Q-table says action E has the highest value
  here" is a much harder thing to audit after an incident than "A* found
  this specific path because these cells were marked occupied."

In practice, real fleets increasingly do exactly what these two projects
demonstrate side by side: classical planning/control for the parts of the
problem with hard, verifiable constraints, and learned policies layered on
top (or reward-shaped classical costs) for the parts that are genuinely
about balancing soft, hard-to-enumerate preferences. Neither approach on
its own is "the real one" — that tension is exactly why this pairing is
worth building both ways.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `environment.py` | Ground truth: `DeliveryGridWorld` — the 8x8 city block (sidewalk/street/obstacle cells), 2 pedestrians on scripted beats, `step()`/`reset()`/reward design. Also the state representation (`row, col, 4-bit N/S/E/W danger bitmask`) | The physical sidewalk block + short-range proximity sensing |
| `hardware.py` | HAL: `DiffDriveCellHAL` (discrete-move-in / wheel-speed-out call surface), `ProximitySensorHAL` (danger-bitmask read) — same HAL pattern as Project 1's `SimulatedDiffDriveHAL`/`SimulatedLidar2DHAL` | Motor-driver board + a short-range ultrasonic/IR proximity sensor ring |
| `q_learning_agent.py` | `QLearningAgent` — real Q-table, epsilon-greedy with linear decay, the real Bellman update, save/load | The learned-policy artifact a fleet would push out as an OTA update |
| `ros2_lite.py` | Minimal pub/sub + timer/clock shim (identical file to Project 1) | `rclpy` |
| `train.py` | **Offline** training loop: many episodes, epsilon decay, reward-per-episode tracking, saves the learned Q-table + learning-curve plots | Simulation-based policy training/validation before a fleet software push |
| `nodes.py` | **Deployment-side** nodes only: `SensorNode` (publishes `/cell_odom` + `/proximity`), `PolicyNode` (greedy Q-table lookup → publishes `/cmd_action`, this project's `/cmd_vel`), `MotionExecutorNode` (only node allowed to call `drive_cell`) | The already-trained policy running on the robot's onboard compute |
| `main.py` | Loads the trained Q-table, runs the deployment node graph for many evaluation episodes (randomized pedestrian phase), reports success rate / avg steps / collisions, plots the learned route | A fleet's live inference stack + a post-hoc evaluation dashboard |

`PolicyNode` vs. Project 1's `NavigationNode` is the key architectural
contrast this project is built to teach: `NavigationNode` **searches** a
fresh A* plan across the live map every time it (re)plans — real compute,
every time, always reasoning over the actual current map. `PolicyNode`
does no search at all at runtime — it's a constant-time table lookup
against a policy that was fully learned beforehand. All the "thinking"
happened once, offline, during `train.py`; deployment is just execution.

## Run it

Training and deployment are two separate steps on purpose — mirroring how
a real fleet trains/validates a policy offline before ever running it on
a robot, and can then run that same trained policy on the robot as many
times as it wants without re-training.

```bash
cd "5) Reinforcement Learning Autonomous Delivery Robot"
pip install numpy matplotlib

python train.py     # trains the Q-learning policy (a couple of seconds),
                     # saves trained_q_table.npy + training_learning_curve.png

python main.py       # loads trained_q_table.npy, runs 100 evaluation
                     # episodes through the ROS2-style deployment nodes,
                     # saves delivery_policy_result.png
```

`trained_q_table.npy` is **not** committed to this folder — `train.py`
regenerates it deterministically in a couple of seconds, so there's no
reason to ship a binary artifact that's this cheap to reproduce (unlike a
real fleet's policy, which might take days of simulation and genuinely
needs to be versioned/shipped as an artifact). Run `train.py` once before
`main.py`.

### Evidence it actually learned (from an actual run of this code)

Training (2,000 episodes):

| | First 100 episodes | Last 100 episodes |
|---|---|---|
| Avg. reward per episode | -153.9 | +30.4 |
| Goal-reach success rate | 0% | 98% |

The reward-per-episode moving average climbs steadily from roughly -150 to
a stable ~+30 plateau (`training_learning_curve.png`), and the success
rate curve climbs from 0% to a steady ~95-100% over the same training run
— a clear, monotonic learning trend, not noise.

Deployment evaluation (100 episodes, greedy policy, pedestrians started at
a randomized phase of their beat each episode so no single lucky run is
being reported):

| Metric | Result |
|---|---|
| Success rate (reached doorstep) | 100% |
| Avg. steps-to-goal (successful episodes) | 14.0 |
| Pedestrian collisions | 0 / 100 |

(Numbers above are from one actual run of this exact code — re-running
`train.py` with a different seed will move them slightly but should land
in the same range; that run-to-run variation is itself part of the honest
RL trade-off discussed above.)

## Things to try changing

- Raise `NEAR_MISS_PENALTY` in `environment.py` and watch the learned
  route in `delivery_policy_result.png` swing wider around the
  pedestrians' beats, even though it never actually collides either way —
  a direct demonstration of a soft preference a hand-coded planner would
  need an explicit new rule to express.
- Set `STEP_PENALTY_STREET` equal to `STEP_PENALTY_SIDEWALK` and re-train:
  the learned route should start cutting through the street wherever it's
  shorter, since the "prefer sidewalk" preference has been switched off.
- Shrink `agent.epsilon_decay_episodes` in `q_learning_agent.py`/`train.py`
  drastically (e.g. to 50) and watch the success-rate curve in
  `training_learning_curve.png` plateau lower and noisier — too little
  exploration before exploitation kicks in means large parts of the state
  space never get sampled.
- Add a third pedestrian beat in `environment.py` that crosses directly in
  front of the doorstep, and see how much longer training takes to reach
  a high success rate — more moving obstacles near the goal means more
  states the agent has to experience before it reliably learns to time its
  approach around them.
