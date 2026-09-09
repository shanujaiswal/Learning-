"""Swarm and Human-Robot Interaction Demo

This script simulates multiple simple robots coordinating together and
also includes a human interaction event to demonstrate HRI decision logic.
"""

import math
import random

class Robot:
    def __init__(self, robot_id, x, y):
        self.id = robot_id
        self.x = x
        self.y = y
        self.vx = 0.0
        self.vy = 0.0

    def distance_to(self, other):
        return math.hypot(self.x - other.x, self.y - other.y)

    def apply_velocity(self):
        self.x += self.vx
        self.y += self.vy

    def __repr__(self):
        return f"Robot({self.id}, x={self.x:.2f}, y={self.y:.2f})"

class HumanInteraction:
    def __init__(self, x, y):
        self.x = x
        self.y = y

    def is_near(self, robot):
        return math.hypot(self.x - robot.x, self.y - robot.y) < 1.2

class SwarmSimulation:
    def __init__(self, num_robots=5):
        self.robots = [Robot(i, random.uniform(-2, 2), random.uniform(-2, 2)) for i in range(num_robots)]
        self.human = HumanInteraction(0.0, 0.0)

    def compute_swarm_vectors(self, robot):
        separation_x = 0.0
        separation_y = 0.0
        cohesion_x = 0.0
        cohesion_y = 0.0
        alignment_x = 0.0
        alignment_y = 0.0
        neighbors = 0

        for other in self.robots:
            if other.id == robot.id:
                continue
            dist = robot.distance_to(other)
            if dist < 2.0:
                separation_x += (robot.x - other.x)
                separation_y += (robot.y - other.y)
                cohesion_x += other.x
                cohesion_y += other.y
                alignment_x += other.vx
                alignment_y += other.vy
                neighbors += 1

        if neighbors == 0:
            return 0.0, 0.0, 0.0

        separation_x /= neighbors
        separation_y /= neighbors
        cohesion_x = cohesion_x / neighbors - robot.x
        cohesion_y = cohesion_y / neighbors - robot.y
        alignment_x /= neighbors
        alignment_y /= neighbors

        return separation_x, cohesion_x + alignment_x, cohesion_y + alignment_y

    def step(self):
        for robot in self.robots:
            sep_x, coh_x, align_x = self.compute_swarm_vectors(robot)
            if self.human.is_near(robot):
                robot.vx = (robot.x - self.human.x) * 0.3
                robot.vy = (robot.y - self.human.y) * 0.3
            else:
                robot.vx = sep_x * 0.2 + coh_x * 0.05 + align_x * 0.1
                robot.vy = sep_x * 0.2 + coh_x * 0.05 + align_x * 0.1
            speed = math.hypot(robot.vx, robot.vy)
            if speed > 0.5:
                robot.vx *= 0.5 / speed
                robot.vy *= 0.5 / speed
            robot.apply_velocity()

    def run(self, frames=10):
        for frame in range(frames):
            print(f"Frame {frame + 1}")
            self.step()
            for robot in self.robots:
                human_note = " (near human)" if self.human.is_near(robot) else ""
                print(f"  {robot}{human_note}")
            print("")

if __name__ == "__main__":
    sim = SwarmSimulation()
    sim.run()