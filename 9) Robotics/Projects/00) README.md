# Robotics Projects

Where `Theory/` teaches the concepts and `Topic Practicals/` gives you one short
script per chapter, this folder is the third leg: **full-length, multi-file,
real-world-framed projects** that combine several chapters into one coherent
system you build and run end-to-end — the way robotics is actually practiced,
not a cleaned-up textbook re-run of the theory demos.

Every project shares the same three real-engineering ingredients:

1. **A Hardware Abstraction Layer (`hardware.py` / `servo_hal.py`)** — every
   module talks only to a HAL class with the same call surface a real driver
   board or library exposes (`set_wheel_speeds(...)`, `set_angle(channel, deg)`,
   ...). Swap the simulated HAL for one wrapping real GPIO/PWM/I2C calls and
   nothing else in the project changes — the actual pattern real robotics
   codebases use to keep simulation and hardware interchangeable.
2. **A real ROS2-style node/topic architecture (`ros2_lite.py`, `nodes.py`)**
   — nodes never call each other's methods directly, only publish/subscribe
   on named topics, written against the same API real `rclpy` uses.
   `ros2_lite.py` is a from-scratch pub/sub shim (no ROS2 install required)
   that's identical across all five projects.
3. **A real scenario with real constraints** — a warehouse floor, a
   manufacturing takt time, a recycling line, a fleet dispatch problem, a
   delivery robot — not an abstract "reach a point" demo. Two projects also
   integrate real AI: a trained scikit-learn classifier in a live perception
   loop (Project 3), and a from-scratch reinforcement-learning policy with a
   measured learning curve (Project 5).

## Projects and what they teach

| # | Project | Scenario | Core theory chapters | AI? | Verified result |
|---|---------|----------|------------------------|-----|------------------|
| 1 | `1) Warehouse AMR - ROS2-Style SLAM Navigation Stack` | New AMR explores an unmapped warehouse floor, plans a route, and **recovers live** when its own map turns out to be wrong | 01 Kinematics, 02 Sensors, 05 Perception/SLAM, 06 Navigation | — | Reaches goal after 2 real-time stall-triggered replans around an unmapped shelf |
| 2 | `2) Industrial Pick-and-Place Robotic Arm Cell` | Servo-driven 3-DOF arm sorting parts off a conveyor against a manufacturing **takt time** | 01 Kinematics, 03 Control/PID | — | 9/9 parts, 7.15s/cycle vs. 8.0s takt (0 misses); backlog logic stress-tested |
| 3 | `3) AI Vision-Guided Recycling Sorting Arm` | Camera-guided arm sorting recyclables by shape, using **real OpenCV + a trained classifier** | 01 Kinematics, 02 Sensors, 05 Perception | **Yes — supervised ML perception** | 98.9% held-out classifier accuracy; 6/6 tray objects correctly sorted |
| 4 | `4) Warehouse Fleet Coordination - Multi-Robot Task Allocation` | 5-robot fleet servicing 18 pick tasks via **real auction-based dispatch** | 07 HRI/Swarm Robotics | — | 18/18 tasks done, 52.9s makespan, 86% utilization, 0 collisions |
| 5 | `5) Reinforcement Learning Autonomous Delivery Robot` | Sidewalk delivery robot **learning** a navigation policy around pedestrians via Q-learning | 06 Navigation | **Yes — reinforcement learning control** | Reward -153.9 -> +30.4, success rate 0% -> 98% over training; 100% success in evaluation |

## Setup

```bash
pip install numpy matplotlib scikit-learn opencv-python
```

`numpy` + `matplotlib` are enough for Projects 1, 2, 4, 5. Project 3
additionally needs `scikit-learn` and `opencv-python` (`cv2`) — the actual
AI/vision libraries, not stand-ins.

## How to use this folder

1. Read the project's own `README.md` first — it explains the scenario, the
   architecture (which module does what and what real hardware/library it
   stands in for), and which theory chapters it draws on, before you look at
   any code.
2. Run `main.py` inside the project folder (Project 5 trains first via
   `train.py`, then `main.py` runs the trained policy — its README says so).
   Every project prints its pipeline's progress step by step and saves at
   least one plot (`.png`, not committed — generated fresh each run) so you
   can see the result, not just read numbers in a terminal.
3. Read the modules in the order each README lists them — every project is
   layered as hardware/sensing -> perception/mapping -> planning/decision ->
   control, mirroring how a real robotics stack is layered.
4. Modify parameters (obstacle layout, arm link lengths, fleet size, grid
   size, reward shaping) and re-run — the fastest way to actually understand
   *why* a planner, controller, or learned policy behaves the way it does is
   to break it on purpose.
