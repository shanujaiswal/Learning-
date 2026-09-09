"""
02 - 3D Math and Spatial Computing Basics: Transforms, Quaternions, Projection
================================================================================

Companion practical for Theory/02 3D Math and Spatial Computing Basics.md

This script implements, from scratch with numpy, the core 3D math that every
XR engine (Unity, Unreal, a raw WebXR/Three.js app) performs every frame:

- Vectors as plain numpy arrays (x, y, z).
- 4x4 homogeneous transformation matrices encoding translation, rotation,
  and scale, and how multiplying vectors by them moves points between
  spaces (local space -> world space -> camera/view space -> screen space,
  exactly the pipeline described in the theory file).
- Rotation via quaternions (x, y, z, w) instead of Euler angles, converted
  to a 3x3 rotation matrix, to avoid gimbal lock as the theory explains.
- Composing transforms parent -> child -> grandchild the way a scene graph
  does (chaining matrix multiplications).
- A perspective projection matrix and a full "project a 3D point to 2D
  screen space" pipeline.
- A round-trip sanity check: build a transform, apply it to a point, apply
  the INVERSE transform, and verify we land back on the original point
  within floating-point tolerance -- the same style of correctness check
  used in the Robotics FK/IK practical.

Run:
    pip install numpy matplotlib
    python 02_3d_math_spatial_computing.py
"""

import numpy as np
import matplotlib.pyplot as plt

np.set_printoptions(precision=4, suppress=True)


# ---------------------------------------------------------------------------
# Quaternions
# ---------------------------------------------------------------------------

def quat_from_axis_angle(axis, angle_rad):
    """Build a unit quaternion (x, y, z, w) representing a rotation of
    `angle_rad` radians around `axis` (need not be pre-normalized)."""
    axis = np.asarray(axis, dtype=float)
    axis = axis / np.linalg.norm(axis)
    half = angle_rad / 2.0
    s = np.sin(half)
    return np.array([axis[0] * s, axis[1] * s, axis[2] * s, np.cos(half)])


def quat_to_rotation_matrix(q):
    """Convert a unit quaternion (x, y, z, w) into a 3x3 rotation matrix."""
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w),     2 * (x * z + y * w)],
        [2 * (x * y + z * w),     1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w),     2 * (y * z + x * w),     1 - 2 * (x * x + y * y)],
    ])


def quat_conjugate(q):
    """Conjugate of a unit quaternion == its inverse, i.e. the reverse
    rotation."""
    x, y, z, w = q
    return np.array([-x, -y, -z, w])


# ---------------------------------------------------------------------------
# 4x4 homogeneous transforms: translation, rotation, scale
# ---------------------------------------------------------------------------

def translation_matrix(t):
    """4x4 matrix that translates a homogeneous point by vector t."""
    m = np.eye(4)
    m[:3, 3] = t
    return m


def rotation_matrix_from_quat(q):
    """4x4 matrix that rotates a homogeneous point via quaternion q."""
    m = np.eye(4)
    m[:3, :3] = quat_to_rotation_matrix(q)
    return m


def scale_matrix(s):
    """4x4 matrix that scales a homogeneous point by (sx, sy, sz)."""
    sx, sy, sz = s
    return np.diag([sx, sy, sz, 1.0])


def trs_matrix(translation, quat, scale):
    """Compose Translation * Rotation * Scale into a single 4x4 matrix --
    this is exactly what a Unity Transform / Three.js Object3D computes
    internally from position/rotation/scale fields every frame."""
    T = translation_matrix(translation)
    R = rotation_matrix_from_quat(quat)
    S = scale_matrix(scale)
    return T @ R @ S


def invert_trs_matrix(m):
    """Invert a TRS matrix. For a matrix built purely from rotation +
    uniform/non-uniform scale + translation (no shear), a plain numpy
    matrix inverse is correct and simplest -- included here rather than
    hand-deriving the closed form, since a full generic inverse is the
    more broadly useful pattern to demonstrate."""
    return np.linalg.inv(m)


def transform_point(m, point_xyz):
    """Apply a 4x4 homogeneous transform to a 3D point."""
    p_h = np.array([*point_xyz, 1.0])
    result = m @ p_h
    return result[:3] / result[3]


def transform_vector(m, vector_xyz):
    """Apply a 4x4 homogeneous transform to a DIRECTION (w=0), so
    translation has no effect -- used for normals/directions."""
    v_h = np.array([*vector_xyz, 0.0])
    result = m @ v_h
    return result[:3]


# ---------------------------------------------------------------------------
# Perspective projection: camera/view space -> clip space -> screen space
# ---------------------------------------------------------------------------

def perspective_projection_matrix(fov_y_deg, aspect_ratio, near, far):
    """Standard right-handed perspective projection matrix (OpenGL-style),
    the same shape of matrix Unity/Three.js build from a camera's FOV,
    aspect ratio, and near/far clip planes."""
    fov_y_rad = np.radians(fov_y_deg)
    f = 1.0 / np.tan(fov_y_rad / 2.0)
    m = np.zeros((4, 4))
    m[0, 0] = f / aspect_ratio
    m[1, 1] = f
    m[2, 2] = (far + near) / (near - far)
    m[2, 3] = (2 * far * near) / (near - far)
    m[3, 2] = -1.0
    return m


def project_to_screen(world_point, view_matrix, proj_matrix, screen_w, screen_h):
    """Full pipeline: world space -> camera/view space -> clip space ->
    normalized device coords -> pixel screen space. Mirrors the chain
    described in the theory file (local -> world -> view -> screen)."""
    p_world_h = np.array([*world_point, 1.0])
    p_view = view_matrix @ p_world_h
    p_clip = proj_matrix @ p_view

    if p_clip[3] == 0:
        return None  # degenerate

    ndc = p_clip[:3] / p_clip[3]  # normalized device coords, roughly [-1, 1]

    # A point is off-screen/behind the camera either because its NDC falls
    # outside [-1, 1] or because clip-space w <= 0 (the point is behind the
    # near plane / behind the camera entirely).
    in_view_bounds = (-1.0 <= ndc[0] <= 1.0) and (-1.0 <= ndc[1] <= 1.0)
    behind_or_offscreen = (not in_view_bounds) or (p_clip[3] <= 0)

    screen_x = (ndc[0] * 0.5 + 0.5) * screen_w
    screen_y = (1.0 - (ndc[1] * 0.5 + 0.5)) * screen_h  # flip Y for image coords
    return screen_x, screen_y, behind_or_offscreen


def look_at_view_matrix(eye, target, up=(0, 1, 0)):
    """Build a view matrix (world -> camera space) for a camera at `eye`
    looking at `target`. This is the inverse of the camera's own TRS
    matrix, per the theory's 'camera/view space via the camera's inverse
    transform' description."""
    eye = np.asarray(eye, dtype=float)
    target = np.asarray(target, dtype=float)
    up = np.asarray(up, dtype=float)

    forward = target - eye
    forward = forward / np.linalg.norm(forward)
    right = np.cross(forward, up)
    right = right / np.linalg.norm(right)
    true_up = np.cross(right, forward)

    # Camera's world-space TRS (rotation columns = right, true_up, -forward
    # for a right-handed, camera-looks-down--Z convention)
    rot = np.column_stack([right, true_up, -forward])
    cam_world = np.eye(4)
    cam_world[:3, :3] = rot
    cam_world[:3, 3] = eye

    return np.linalg.inv(cam_world)


# ---------------------------------------------------------------------------
def main():
    print("=" * 78)
    print("3D MATH FOR XR: TRANSFORMS, QUATERNIONS, SCENE GRAPH, PROJECTION")
    print("=" * 78)

    # -----------------------------------------------------------------
    # Demo 1: build a TRS transform, apply it, then invert and round-trip
    # -----------------------------------------------------------------
    print("\n[1] TRS transform round-trip check")
    print("-" * 78)
    translation = np.array([2.0, 0.5, -1.0])
    quat = quat_from_axis_angle(axis=[0, 1, 0], angle_rad=np.radians(40))
    scale = np.array([1.0, 1.0, 1.0])

    M = trs_matrix(translation, quat, scale)
    print("Transform matrix M (translate (2, 0.5, -1), rotate 40 deg about Y):")
    print(M)

    original_point = np.array([1.0, 0.0, 0.0])
    transformed = transform_point(M, original_point)
    print(f"\noriginal local point:      {original_point}")
    print(f"transformed to world:      {transformed}")

    M_inv = invert_trs_matrix(M)
    recovered = transform_point(M_inv, transformed)
    error = np.linalg.norm(recovered - original_point)
    print(f"recovered via inverse(M):  {recovered}")
    print(f"round-trip error:          {error:.2e}")
    assert error < 1e-9, "Round-trip transform/inverse check failed!"
    print("Round-trip check PASSED (error below 1e-9 tolerance).")

    # -----------------------------------------------------------------
    # Demo 2: scene-graph style parent -> child -> grandchild composition
    # -----------------------------------------------------------------
    print("\n[2] Scene graph composition (parent -> child -> grandchild)")
    print("-" * 78)
    # Parent: a "table" placed 3m ahead
    parent_local_to_world = trs_matrix(
        translation=[0.0, 0.0, 3.0],
        quat=quat_from_axis_angle([0, 1, 0], np.radians(0)),
        scale=[1.0, 1.0, 1.0],
    )
    # Child: a "cup" sitting 0.4m up and rotated 90 deg, relative to the table
    child_local_to_parent = trs_matrix(
        translation=[0.0, 0.4, 0.0],
        quat=quat_from_axis_angle([0, 1, 0], np.radians(90)),
        scale=[1.0, 1.0, 1.0],
    )
    # Grandchild: a "handle" offset from the cup's own local origin
    grandchild_local_to_child = trs_matrix(
        translation=[0.1, 0.0, 0.0],
        quat=quat_from_axis_angle([0, 1, 0], np.radians(0)),
        scale=[1.0, 1.0, 1.0],
    )

    # Composing transforms is just chained matrix multiplication, exactly
    # as the theory file describes.
    child_local_to_world = parent_local_to_world @ child_local_to_parent
    grandchild_local_to_world = child_local_to_world @ grandchild_local_to_child

    origin = np.array([0.0, 0.0, 0.0])
    print(f"table (parent) world position:   {transform_point(parent_local_to_world, origin)}")
    print(f"cup (child) world position:       {transform_point(child_local_to_world, origin)}")
    print(f"handle (grandchild) world pos:    {transform_point(grandchild_local_to_world, origin)}")
    print("\nNote how the handle's world position reflects ALL three chained")
    print("transforms -- move/rotate the table and every descendant follows,")
    print("the core behavior a scene graph provides for free.")

    # -----------------------------------------------------------------
    # Demo 3: perspective projection into screen space
    # -----------------------------------------------------------------
    print("\n[3] Perspective projection: world point -> 2D screen coordinates")
    print("-" * 78)
    screen_w, screen_h = 1920, 1080
    proj = perspective_projection_matrix(fov_y_deg=90, aspect_ratio=screen_w / screen_h,
                                          near=0.05, far=100.0)
    view = look_at_view_matrix(eye=[0, 1.6, 0], target=[0, 1.6, 1], up=[0, 1, 0])

    world_points = {
        "straight ahead, 2m out": [0.0, 1.6, 2.0],
        "1m left, 3m out":        [-1.0, 1.6, 3.0],
        "1m right + up, 2m out":  [1.0, 2.0, 2.0],
        "behind the camera":      [0.0, 1.6, -1.0],
    }
    for label, wp in world_points.items():
        result = project_to_screen(wp, view, proj, screen_w, screen_h)
        sx, sy, off = result
        status = "OFF-SCREEN / behind camera" if off else "visible"
        print(f"  {label:28s} world={wp}  ->  screen=({sx:8.1f}, {sy:8.1f}) px  [{status}]")

    # -----------------------------------------------------------------
    # Plot: visualize the scene graph points and the projected screen points
    # -----------------------------------------------------------------
    fig, axes = plt.subplots(1, 2, figsize=(13, 5.5))

    ax = axes[0]
    pts = {
        "table (parent)": transform_point(parent_local_to_world, origin),
        "cup (child)": transform_point(child_local_to_world, origin),
        "handle (grandchild)": transform_point(grandchild_local_to_world, origin),
    }
    for label, p in pts.items():
        ax.scatter(p[0], p[2], s=80, label=label)
        ax.annotate(label, (p[0], p[2]), textcoords="offset points", xytext=(6, 6), fontsize=8)
    ax.scatter(0, 0, marker="^", s=100, color="black", label="world origin")
    ax.set_xlabel("X (m)")
    ax.set_ylabel("Z (m, forward)")
    ax.set_title("Scene graph: world positions (top-down view)")
    ax.grid(True, alpha=0.3)
    ax.set_aspect("equal")
    ax.legend(fontsize=7, loc="upper left")

    ax = axes[1]
    for label, wp in world_points.items():
        sx, sy, off = project_to_screen(wp, view, proj, screen_w, screen_h)
        color = "tab:red" if off else "tab:green"
        ax.scatter(sx, sy, s=80, color=color)
        ax.annotate(label, (sx, sy), textcoords="offset points", xytext=(6, 6), fontsize=7)
    ax.set_xlim(0, screen_w)
    ax.set_ylim(screen_h, 0)  # image coords: y grows downward
    ax.set_xlabel("screen X (px)")
    ax.set_ylabel("screen Y (px)")
    ax.set_title("Projected screen-space positions\n(green=visible, red=off-screen/behind)")
    ax.add_patch(plt.Rectangle((0, 0), screen_w, screen_h, fill=False, edgecolor="gray"))

    plt.tight_layout()
    out_path = "3d_math_spatial_computing_demo.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()
