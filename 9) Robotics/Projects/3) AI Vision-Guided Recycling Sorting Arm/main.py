"""AI Vision-Guided Recycling Sorting Arm -- end to end.

Step 0 (Train):   generate a synthetic labeled dataset from the REAL
                   render -> cv2 segment -> feature-extraction pipeline,
                   train a RandomForestClassifier, report held-out accuracy.
Step 1 (Perceive): capture an overhead camera frame of a cluttered tray,
                   segment + classify every object on it with the trained
                   model.
Step 2 (Plan):     turn each detection into a world-frame grasp + bin plan.
Step 3 (Control):  stream minimum-jerk joint trajectories through the servo
                   HAL to pick every object into its class's bin.

Run:
    pip install numpy matplotlib opencv-python scikit-learn
    python main.py
"""

import numpy as np
import matplotlib.pyplot as plt
import cv2

import vision
import grasp_planner
import kinematics
import servo_hal
import ros2_lite as rclpy
from nodes import CameraNode, PerceptionNode, GraspPlannerNode, ServoDriverNode, ArmSortingNode

SEED = 2026
CLASS_COLOR_BGR = {
    "bottle_cap": (60, 60, 230),   # red-ish
    "block": (230, 140, 40),       # blue-ish
    "bar": (60, 200, 60),          # green-ish
}


def build_tray_scene(n_objects=6, seed=SEED):
    """Randomly scatters `n_objects` recyclable parts across the tray,
    rejecting placements that would overlap -- the same kind of cluttered-
    but-non-touching tray a real feeder/singulation stage hands a sorting
    cell.
    """
    rng = np.random.default_rng(seed)
    h, w = vision.FRAME_SIZE
    objects = []
    min_center_dist = 85
    attempts = 0
    while len(objects) < n_objects and attempts < 500:
        attempts += 1
        cls = vision.CLASSES[len(objects) % len(vision.CLASSES)] if len(objects) < len(vision.CLASSES) \
            else rng.choice(vision.CLASSES)
        cx = rng.uniform(110, w - 110)
        cy = rng.uniform(110, h - 110)
        if any(np.hypot(cx - o["pixel_xy"][0], cy - o["pixel_xy"][1]) < min_center_dist for o in objects):
            continue
        objects.append({
            "id": len(objects),
            "class": cls,
            "pixel_xy": (cx, cy),
            "scale": float(rng.uniform(0.9, 1.1)),
            "angle_deg": float(rng.uniform(0, 180)),
        })
    return objects


def annotate_frame(frame_bgr, detections):
    annotated = frame_bgr.copy()
    for det in detections:
        x, y, w, h = det["bbox"]
        color = CLASS_COLOR_BGR[det["class"]]
        cv2.rectangle(annotated, (x, y), (x + w, y + h), color, 2)
        label = f'{det["class"]} {det["confidence"]*100:.0f}%'
        cv2.putText(annotated, label, (x, max(y - 8, 12)),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1, cv2.LINE_AA)
    return annotated


def main():
    print("=" * 78)
    print("AI VISION-GUIDED RECYCLING SORTING ARM")
    print("=" * 78)

    # --- Step 0: train + evaluate the real sklearn classifier ------------
    print("\nStep 0/3 -- Training shape classifier on a synthetic labeled "
          "dataset (render -> cv2 segment -> Hu-moment/geometric features)...")
    model, report, test_accuracy = vision.train_classifier(n_per_class=200, test_size=0.3, seed=123)
    print(f"Held-out test accuracy: {test_accuracy*100:.1f}%\n")
    print(report)

    # --- Step 1: perceive a cluttered tray ---------------------------------
    print("Step 1/3 -- Capturing overhead camera frame of the tray and "
          "classifying every object on it...")
    tray_objects = build_tray_scene(n_objects=6, seed=SEED)
    initial_frame = vision.render_scene(tray_objects, rng=np.random.default_rng(SEED))
    initial_detections = vision.detect_objects(initial_frame, model)
    print(f"Tray has {len(tray_objects)} ground-truth objects; "
          f"detected {len(initial_detections)} objects on the tray:")
    for det in initial_detections:
        px, py = det["pixel_xy"]
        print(f"  - {det['class']:<11s} conf={det['confidence']*100:5.1f}%  "
              f"pixel=({px:6.1f}, {py:6.1f})  angle={det['angle_deg']:6.1f} deg")

    # --- Step 2 + 3: plan + drive the arm through the full sort cycle ------
    print("\nStep 2/3 -- Planning grasp + bin targets for each detection...")
    print("Step 3/3 -- Driving the arm through pick -> sort -> place for "
          "every object on the tray...")

    rclpy.reset_bus()
    clock = rclpy.SimClock(dt_s=0.05)
    hal = servo_hal.SimulatedServoHAL(start_joint_rad=np.array([0.0, np.pi / 2, np.pi / 2]))

    camera_node = CameraNode(tray_objects, clock, period_s=2.0)
    perception_node = PerceptionNode(model, clock)
    planner_node = GraspPlannerNode(clock)
    servo_node = ServoDriverNode(hal, clock)
    arm_node = ArmSortingNode(clock, move_duration_s=1.2)

    nodes = [camera_node, perception_node, planner_node, servo_node, arm_node]
    max_duration_s = 2.0 + len(tray_objects) * 7.5 + 4.0
    rclpy.spin(nodes, max_duration_s, clock)

    n_picked = len(arm_node.completed_picks)
    print(f"\nSort cycle finished at t={clock.t:.1f}s -- picked {n_picked}/"
          f"{len(tray_objects)} detected objects.")
    per_class_counts = {}
    for pick in arm_node.completed_picks:
        per_class_counts[pick["class"]] = per_class_counts.get(pick["class"], 0) + 1
    for cls in vision.CLASSES:
        print(f"  -> bin[{cls:<11s}] received {per_class_counts.get(cls, 0)} object(s)")
    if n_picked < len(initial_detections):
        print("WARNING: not all detected objects were picked within the time budget.")

    # --- Plot --------------------------------------------------------------
    annotated = annotate_frame(initial_frame, initial_detections)
    annotated_rgb = cv2.cvtColor(annotated, cv2.COLOR_BGR2RGB)

    ee_history = np.array(arm_node.ee_history) if arm_node.ee_history else np.zeros((0, 4))

    fig, axes = plt.subplots(1, 2, figsize=(15, 7))
    fig.suptitle("AI Vision-Guided Recycling Sorting Arm: Detection + Executed Sort Path")

    ax = axes[0]
    ax.imshow(annotated_rgb, origin="upper")
    ax.set_title(f"Overhead camera frame -- {len(initial_detections)} objects detected & classified")
    ax.set_xlabel("pixel x"); ax.set_ylabel("pixel y")

    ax = axes[1]
    if len(ee_history):
        ax.plot(ee_history[:, 1], ee_history[:, 2], color="tab:orange", linewidth=1.2, label="executed EE path (top view)")
    for cls, (bx, by) in grasp_planner.BIN_POSITIONS_M.items():
        ax.plot(bx, by, "s", markersize=12, color="tab:purple")
        ax.annotate(f"bin: {cls}", (bx, by), textcoords="offset points", xytext=(6, 6), fontsize=8)
    for pick in arm_node.completed_picks:
        wx, wy = pick["world_xy"]
        ax.plot(wx, wy, "o", markersize=6, color="black")
    tray_rect = plt.Rectangle(
        (grasp_planner.TRAY_X_RANGE_M[0], grasp_planner.TRAY_Y_RANGE_M[0]),
        grasp_planner.TRAY_X_RANGE_M[1] - grasp_planner.TRAY_X_RANGE_M[0],
        grasp_planner.TRAY_Y_RANGE_M[1] - grasp_planner.TRAY_Y_RANGE_M[0],
        facecolor="none", edgecolor="gray", linestyle="--", linewidth=1.2)
    ax.add_patch(tray_rect)
    ax.plot(0, 0, "^", markersize=10, color="black", label="arm base")
    ax.set_title("Arm base frame (top view): executed path, picks, and sorting bins")
    ax.set_xlabel("x (m)"); ax.set_ylabel("y (m)")
    ax.set_aspect("equal"); ax.legend(fontsize=8, loc="upper right")

    plt.tight_layout()
    out_path = "recycling_sorting_result.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()
