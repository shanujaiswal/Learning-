# Robotics Practicals

These scripts support the theory notes in `8) Robotics/Theory/`.
The files demonstrate key robotics concepts and workflow patterns using Python simulation and ROS-style approaches.

## Chapter mapping

| Theory file                                                                    | Practical(s)                           |
| ------------------------------------------------------------------------------ | -------------------------------------- |
| `00 Robotics Roadmap.md`                                                       | Orientation only — no dedicated script |
| `01 Robotics Fundamentals and Kinematics.md`                                   | `01_forward_and_inverse_kinematics.py` |
| `02 Sensors and Actuators.md`                                                  | `02_sensors_and_actuators.py`          |
| `03 Control Systems and PID Controllers.md`                                    | `03_pid_control.py`                    |
| `04 Robot Operating System (ROS) Basics.md`                                    | `04_ros_basics_simulation.py`          |
| `05 Robotics Perception and SLAM.md`                                           | `05_slam_perception.py`                |
| `06 Mobile Robotics, Localization and Autonomous Navigation.md`                | `04_ros_basics_simulation.py`          |
| `07 Human-Robot Interaction, Swarm Robotics and Ethical Autonomous Systems.md` | `06_swarm_and_hri_demo.py`             |

## Setup

These scripts use only the Python standard library and are runnable on any normal PC.

## Files

1. `00 README.md` — this file.
2. `01_forward_and_inverse_kinematics.py` — forward and inverse kinematics for a simple 2-joint arm.
3. `02_sensors_and_actuators.py` — sensor simulation and actuator control loop examples.
4. `03_pid_control.py` — PID controller example for a motor-like system.
5. `04_ros_basics_simulation.py` — simulated ROS-style publisher/subscriber pipeline and message flow.
6. `05_slam_perception.py` — basic SLAM concept demo with map-building and pose estimation.
7. `06_swarm_and_hri_demo.py` — multi-robot coordination and simple human-robot interaction workflow.
