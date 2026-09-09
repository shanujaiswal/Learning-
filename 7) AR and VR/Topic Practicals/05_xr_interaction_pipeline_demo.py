"""XR Interaction Pipeline Demo

This script simulates an XR application pipeline in Python. It demonstrates:
- tracking pose updates for a headset and a controller
- scene graph update and object anchoring
- interaction event handling (gaze + controller selection)
- frame time budgeting and performance logging

Run this script in a normal Python interpreter; it prints pipeline stages for each frame.
"""

import math
import random
import time

FRAME_TIME_BUDGET_MS = 11.11  # 90 FPS target

class Pose:
    def __init__(self, x=0.0, y=0.0, z=0.0, yaw=0.0, pitch=0.0, roll=0.0):
        self.x = x
        self.y = y
        self.z = z
        self.yaw = yaw
        self.pitch = pitch
        self.roll = roll

    def translate(self, dx, dy, dz):
        self.x += dx
        self.y += dy
        self.z += dz

    def rotate(self, dyaw, dpitch, droll):
        self.yaw += dyaw
        self.pitch += dpitch
        self.roll += droll

    def __repr__(self):
        return f"Pose(x={self.x:.2f}, y={self.y:.2f}, z={self.z:.2f}, yaw={self.yaw:.2f})"

class SceneObject:
    def __init__(self, name, anchor_pose):
        self.name = name
        self.anchor_pose = anchor_pose
        self.visible = True

    def update(self, headset_pose):
        distance = math.sqrt(
            (self.anchor_pose.x - headset_pose.x) ** 2
            + (self.anchor_pose.y - headset_pose.y) ** 2
            + (self.anchor_pose.z - headset_pose.z) ** 2
        )
        if distance > 4.0:
            self.visible = False
        else:
            self.visible = True

    def __repr__(self):
        visibility = "visible" if self.visible else "hidden"
        return f"{self.name} at {self.anchor_pose} ({visibility})"

class XRApplication:
    def __init__(self):
        self.headset_pose = Pose(0.0, 1.6, 0.0)
        self.controller_pose = Pose(0.2, 1.4, 0.5)
        self.scene_objects = [
            SceneObject("Menu", Pose(0.0, 1.5, 2.0)),
            SceneObject("InfoPanel", Pose(1.0, 1.3, 1.8)),
            SceneObject("VirtualBall", Pose(-0.8, 1.0, 1.2)),
        ]
        self.button_pressed = False

    def track_headset(self):
        self.headset_pose.translate(random.uniform(-0.02, 0.02), 0.0, random.uniform(-0.02, 0.02))
        self.headset_pose.rotate(random.uniform(-0.5, 0.5), 0.0, 0.0)

    def track_controller(self):
        self.controller_pose.translate(random.uniform(-0.01, 0.01), 0.0, random.uniform(-0.01, 0.01))

    def update_scene(self):
        for obj in self.scene_objects:
            obj.update(self.headset_pose)

    def handle_input(self):
        self.button_pressed = random.random() < 0.1
        if self.button_pressed:
            selected = random.choice([obj for obj in self.scene_objects if obj.visible])
            print(f"Interaction: selected {selected.name}")

    def render_frame(self):
        visible_objects = [obj.name for obj in self.scene_objects if obj.visible]
        print(f"Render: visible objects = {visible_objects}")

    def run(self, frames=20):
        start_time = time.time()
        for frame in range(frames):
            frame_start = time.time()
            print(f"\nFrame {frame + 1}")
            self.track_headset()
            print(f"  Headset pose: {self.headset_pose}")
            self.track_controller()
            print(f"  Controller pose: {self.controller_pose}")
            self.update_scene()
            self.handle_input()
            self.render_frame()
            frame_time = (time.time() - frame_start) * 1000
            print(f"  Frame time: {frame_time:.2f} ms")
            if frame_time > FRAME_TIME_BUDGET_MS:
                print("  WARNING: frame over budget")
            time.sleep(max(0, FRAME_TIME_BUDGET_MS / 1000 - (time.time() - frame_start)))
        total = (time.time() - start_time) * 1000
        print(f"\nTotal runtime: {total:.2f} ms")

if __name__ == "__main__":
    app = XRApplication()
    app.run()
