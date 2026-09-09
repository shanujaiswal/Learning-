"""
03 - XR Scene Graph Simulation: Plane Detection + Anchoring + Per-Frame Reprojection
======================================================================================

Companion practical for Theory/03 Building AR VR Experiences -- Unity XR,
ARKit, ARCore and WebXR.md

*** THIS IS A CONCEPTUAL, PURE-PYTHON SIMULATION FOR LEARNING PURPOSES. ***
*** It is NOT a real Unity, ARKit, ARCore, or WebXR application.        ***

Unity/ARKit/ARCore/WebXR are not installable or runnable headlessly in this
environment (they need a real device, an iOS/Android toolchain, or a
browser + WebGL/WebXR-capable hardware). Instead, this script reimplements
the CORE CONCEPTS those frameworks provide, with plain numpy standing in for
the engine/SDK internals, so the underlying mechanics are transparent:

  Real-world concept                     -->  What this script does
  --------------------------------------------------------------------------
  ARKit/ARCore plane detection           -->  `detect_plane()` fabricates a
  (`ARPlaneAnchor` / `Plane`)                  plane pose (position, normal,
                                                a local basis) the way a real
                                                SLAM system would report a
                                                detected tabletop/floor.
  Anchoring a virtual object to a        -->  `SceneNode.anchor_to_plane()`
  plane (`ARAnchor`, WebXR                     stores the object's pose as a
  `XRAnchor`/hit-test result)                  LOCAL offset from the plane's
                                                pose (a parent/child scene
                                                graph relationship, same idea
                                                as Unity's Transform hierarchy
                                                or a WebXR/Three.js Object3D
                                                parented to an anchor).
  Camera/head tracking driving the       -->  `simulate_camera_path()` moves
  render camera every frame                    a simulated camera through
  (Unity XR Origin, ARKit/ARCore camera        world space frame by frame.
  transform, WebXR XRViewerPose)
  Re-rendering anchored content each     -->  Each frame, every anchored
  frame from the object's WORLD pose           node's world pose is recomputed
  (this is why anchors "stick" as you          from plane_pose @ local_offset,
  walk around them)                            then projected to screen space
                                                with the reused projection
                                                pipeline from script 02.

Run:
    pip install numpy matplotlib
    python 03_xr_scene_graph_simulation.py
"""

import numpy as np
import matplotlib.pyplot as plt

np.set_printoptions(precision=3, suppress=True)


# ---------------------------------------------------------------------------
# Minimal reusable 3D math (kept self-contained; see script 02 for full detail)
# ---------------------------------------------------------------------------

def quat_from_axis_angle(axis, angle_rad):
    axis = np.asarray(axis, dtype=float)
    axis = axis / np.linalg.norm(axis)
    half = angle_rad / 2.0
    s = np.sin(half)
    return np.array([axis[0] * s, axis[1] * s, axis[2] * s, np.cos(half)])


def quat_to_rotation_matrix(q):
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w),     2 * (x * z + y * w)],
        [2 * (x * y + z * w),     1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w),     2 * (y * z + x * w),     1 - 2 * (x * x + y * y)],
    ])


def trs_matrix(translation, quat, scale=(1.0, 1.0, 1.0)):
    T = np.eye(4)
    T[:3, 3] = translation
    R = np.eye(4)
    R[:3, :3] = quat_to_rotation_matrix(quat)
    S = np.diag([scale[0], scale[1], scale[2], 1.0])
    return T @ R @ S


def transform_point(m, point_xyz):
    p_h = np.array([*point_xyz, 1.0])
    r = m @ p_h
    return r[:3] / r[3]


def perspective_projection_matrix(fov_y_deg, aspect_ratio, near, far):
    f = 1.0 / np.tan(np.radians(fov_y_deg) / 2.0)
    m = np.zeros((4, 4))
    m[0, 0] = f / aspect_ratio
    m[1, 1] = f
    m[2, 2] = (far + near) / (near - far)
    m[2, 3] = (2 * far * near) / (near - far)
    m[3, 2] = -1.0
    return m


def look_at_view_matrix(eye, target, up=(0, 1, 0)):
    eye = np.asarray(eye, dtype=float)
    target = np.asarray(target, dtype=float)
    up = np.asarray(up, dtype=float)
    forward = target - eye
    forward /= np.linalg.norm(forward)
    right = np.cross(forward, up)
    right /= np.linalg.norm(right)
    true_up = np.cross(right, forward)
    cam_world = np.eye(4)
    cam_world[:3, :3] = np.column_stack([right, true_up, -forward])
    cam_world[:3, 3] = eye
    return np.linalg.inv(cam_world)


def project_to_screen(world_point, view_matrix, proj_matrix, screen_w, screen_h):
    p_view = view_matrix @ np.array([*world_point, 1.0])
    p_clip = proj_matrix @ p_view
    if p_clip[3] == 0:
        return None
    ndc = p_clip[:3] / p_clip[3]
    visible = (-1.0 <= ndc[0] <= 1.0) and (-1.0 <= ndc[1] <= 1.0) and p_clip[3] > 0
    sx = (ndc[0] * 0.5 + 0.5) * screen_w
    sy = (1.0 - (ndc[1] * 0.5 + 0.5)) * screen_h
    return sx, sy, visible


# ---------------------------------------------------------------------------
# Simulated plane detection (stand-in for ARKit/ARCore SLAM plane detection)
# ---------------------------------------------------------------------------

def detect_plane(center, normal=(0, 1, 0), extent=(1.0, 1.0)):
    """Fabricate a detected-plane result, mirroring the shape of a real
    ARPlaneAnchor / ARCore Plane: a center pose (position + orientation)
    plus an extent (width/height of the detected surface, e.g. a tabletop).

    We only support horizontal (floor/table, normal=+Y) planes here for
    simplicity, which covers the most common real-world case."""
    center = np.array(center, dtype=float)
    normal = np.array(normal, dtype=float)
    normal = normal / np.linalg.norm(normal)
    # Identity orientation is fine for a horizontal plane; a vertical
    # (wall) plane would need a basis rotation here in a fuller sim.
    pose = trs_matrix(center, quat_from_axis_angle([0, 1, 0], 0.0))
    return {
        "pose": pose,           # plane's local-to-world transform
        "center": center,
        "normal": normal,
        "extent": extent,       # (width, depth) in meters
    }


# ---------------------------------------------------------------------------
# Scene graph node: virtual content anchored to a detected plane
# ---------------------------------------------------------------------------

class SceneNode:
    """A minimal AR scene-graph node. Once anchored, its world pose each
    frame is `plane_pose @ local_offset` -- i.e. it is defined RELATIVE to
    the plane anchor, not to a fixed world coordinate. This is exactly why
    anchored AR content stays glued to a real surface even as the plane's
    own estimate is refined, and why moving the camera (not the object)
    is what changes its rendered screen position frame to frame."""

    def __init__(self, name, local_offset_matrix):
        self.name = name
        self.local_offset = local_offset_matrix
        self.anchor_plane = None

    def anchor_to_plane(self, plane):
        self.anchor_plane = plane

    def world_matrix(self):
        if self.anchor_plane is None:
            return self.local_offset
        return self.anchor_plane["pose"] @ self.local_offset

    def world_position(self):
        return transform_point(self.world_matrix(), [0, 0, 0])


# ---------------------------------------------------------------------------
# Simulated camera path (stand-in for headset/phone 6DoF tracking each frame)
# ---------------------------------------------------------------------------

def simulate_camera_path(num_frames, radius=2.0, height=1.6):
    """Simulate a user walking in a slow arc around the anchored content,
    the way you'd naturally move around a placed AR object to inspect it
    from different angles. Returns a list of (eye_position, look_target)."""
    frames = []
    for i in range(num_frames):
        t = i / (num_frames - 1)
        angle = np.radians(-60 + 120 * t)  # sweep from -60 to +60 degrees
        eye = np.array([radius * np.sin(angle), height, radius * np.cos(angle) * -1 + radius])
        target = np.array([0.0, 0.3, 0.0])  # always look toward the anchored content
        frames.append((eye, target))
    return frames


# ---------------------------------------------------------------------------
def main():
    print("=" * 78)
    print("XR SCENE GRAPH SIMULATION: PLANE DETECTION -> ANCHORING -> TRACKING")
    print("(conceptual simulation only -- not a real Unity/ARKit/ARCore/WebXR app)")
    print("=" * 78)

    # -----------------------------------------------------------------
    # Step 1: simulate plane detection (like ARKit/ARCore finding a table)
    # -----------------------------------------------------------------
    table_plane = detect_plane(center=[0, 0.0, 0], normal=[0, 1, 0], extent=(1.2, 0.8))
    print("\n[1] Simulated plane detection result (mirrors ARPlaneAnchor / ARCore Plane):")
    print(f"    center = {table_plane['center']}")
    print(f"    normal = {table_plane['normal']}")
    print(f"    extent = {table_plane['extent']} m (width x depth)")

    # -----------------------------------------------------------------
    # Step 2: anchor virtual objects to the plane (local offsets)
    # -----------------------------------------------------------------
    mug = SceneNode("virtual mug", trs_matrix([0.2, 0.05, 0.1], quat_from_axis_angle([0, 1, 0], 0)))
    lamp = SceneNode("virtual lamp", trs_matrix([-0.3, 0.15, -0.2], quat_from_axis_angle([0, 1, 0], np.radians(30))))
    mug.anchor_to_plane(table_plane)
    lamp.anchor_to_plane(table_plane)
    nodes = [mug, lamp]

    print("\n[2] Anchored virtual objects (local offset from plane):")
    for n in nodes:
        print(f"    {n.name:16s} world position = {n.world_position()}")

    # -----------------------------------------------------------------
    # Step 3: simulate the camera moving around the scene, frame by frame
    # -----------------------------------------------------------------
    NUM_FRAMES = 7
    SCREEN_W, SCREEN_H = 1280, 720
    proj = perspective_projection_matrix(fov_y_deg=90, aspect_ratio=SCREEN_W / SCREEN_H, near=0.05, far=50.0)
    camera_frames = simulate_camera_path(NUM_FRAMES)

    print(f"\n[3] Simulating {NUM_FRAMES} camera frames as the user walks around the table")
    print("    (anchored objects stay in place in WORLD space; only their")
    print("    projected SCREEN position changes as the camera moves --")
    print("    this is the essence of why AR anchors appear to 'stick'.)")
    print("-" * 78)

    per_node_screen_history = {n.name: [] for n in nodes}
    for frame_idx, (eye, target) in enumerate(camera_frames):
        view = look_at_view_matrix(eye, target)
        print(f"\n  Frame {frame_idx}: camera eye={np.round(eye, 2)}")
        for n in nodes:
            world_pos = n.world_position()  # unchanged across frames -- it's anchored
            sx, sy, visible = project_to_screen(world_pos, view, proj, SCREEN_W, SCREEN_H)
            per_node_screen_history[n.name].append((sx, sy, visible))
            vis_str = "visible" if visible else "off-screen"
            print(f"      {n.name:16s} world={np.round(world_pos, 2)}  ->  "
                  f"screen=({sx:7.1f}, {sy:7.1f})  [{vis_str}]")

    # -----------------------------------------------------------------
    # Plot: top-down view of camera path + anchors, and screen-space tracks
    # -----------------------------------------------------------------
    fig, axes = plt.subplots(1, 2, figsize=(13, 5.5))

    ax = axes[0]
    eyes = np.array([f[0] for f in camera_frames])
    ax.plot(eyes[:, 0], eyes[:, 2], "-o", color="tab:gray", label="camera path")
    for i, e in enumerate(eyes):
        ax.annotate(str(i), (e[0], e[2]), fontsize=7, textcoords="offset points", xytext=(4, 4))
    for n, color in zip(nodes, ["tab:blue", "tab:orange"]):
        p = n.world_position()
        ax.scatter(p[0], p[2], s=100, color=color, label=n.name)
    rect_w, rect_d = table_plane["extent"]
    cx, cz = table_plane["center"][0], table_plane["center"][2]
    ax.add_patch(plt.Rectangle((cx - rect_w / 2, cz - rect_d / 2), rect_w, rect_d,
                                fill=False, edgecolor="green", linestyle="--", label="detected plane"))
    ax.set_xlabel("X (m)")
    ax.set_ylabel("Z (m)")
    ax.set_title("Top-down: camera path around anchored plane/objects")
    ax.set_aspect("equal")
    ax.grid(True, alpha=0.3)
    ax.legend(fontsize=7)

    ax = axes[1]
    for n, color in zip(nodes, ["tab:blue", "tab:orange"]):
        history = per_node_screen_history[n.name]
        xs = [h[0] for h in history]
        ys = [h[1] for h in history]
        ax.plot(xs, ys, "-o", color=color, label=n.name)
        for i, (x, y, _v) in enumerate(history):
            ax.annotate(str(i), (x, y), fontsize=7, textcoords="offset points", xytext=(4, 4))
    ax.set_xlim(0, SCREEN_W)
    ax.set_ylim(SCREEN_H, 0)
    ax.add_patch(plt.Rectangle((0, 0), SCREEN_W, SCREEN_H, fill=False, edgecolor="gray"))
    ax.set_xlabel("screen X (px)")
    ax.set_ylabel("screen Y (px)")
    ax.set_title("Anchored objects' screen-space trajectory\nas the camera moves (numbers = frame index)")
    ax.legend(fontsize=7)

    plt.tight_layout()
    out_path = "xr_scene_graph_simulation.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()
