"""Pixel -> world-frame grasp planning for the recycling sorting cell.

A real vision-guided pick cell never hands the arm pixel coordinates --
somewhere between perception and motion planning there is always a
camera-to-robot-frame calibration (in a real cell: a one-time extrinsic
calibration, typically a checkerboard-based homography or a simple known
mounting-geometry affine transform when the camera looks straight down at a
flat work surface, which is the case here). This module is that transform,
plus the per-class grasp policy: how deep to descend, and which bin a class
gets sorted into.
"""

import numpy as np

import vision

# --------------------------------------------------------------------------
# Camera -> world (arm base frame) calibration.
#
# The overhead camera looks straight down at the tray. The tray occupies a
# known rectangle of the arm's reachable workspace (see kinematics.py:
# UPPER_ARM_M + FOREARM_M = 0.38 m max reach, |UPPER_ARM_M - FOREARM_M| =
# 0.02 m min reach), so every pixel maps to a reachable (x, y) by a fixed
# affine transform -- exactly the one-time calibration step a real cell
# performs once at commissioning and then reuses for every frame.
# --------------------------------------------------------------------------

TRAY_X_RANGE_M = (0.15, 0.32)   # arm-frame x spanned by the tray, meters
TRAY_Y_RANGE_M = (-0.13, 0.13)  # arm-frame y spanned by the tray, meters

_FRAME_H, _FRAME_W = vision.FRAME_SIZE


def pixel_to_world(px, py):
    """Maps an overhead-camera pixel (px, py) to arm-base-frame (x, y) on
    the tray plane, via the fixed camera calibration above.
    """
    u = px / _FRAME_W
    v = py / _FRAME_H
    x = TRAY_X_RANGE_M[0] + u * (TRAY_X_RANGE_M[1] - TRAY_X_RANGE_M[0])
    y = TRAY_Y_RANGE_M[0] + v * (TRAY_Y_RANGE_M[1] - TRAY_Y_RANGE_M[0])
    return float(x), float(y)


def world_to_pixel(x, y):
    """Inverse of `pixel_to_world` -- used only by `main.py`/tests to place
    synthetic objects at a known pixel location for a given world target.
    """
    u = (x - TRAY_X_RANGE_M[0]) / (TRAY_X_RANGE_M[1] - TRAY_X_RANGE_M[0])
    v = (y - TRAY_Y_RANGE_M[0]) / (TRAY_Y_RANGE_M[1] - TRAY_Y_RANGE_M[0])
    return u * _FRAME_W, v * _FRAME_H


# --------------------------------------------------------------------------
# Per-class grasp policy: descent depth and sorting bin.
#
# The arm here is a 3-DOF (base yaw + shoulder + elbow) position-only arm --
# no independent wrist-roll servo -- so the gripper always closes at a fixed
# orientation set by the base yaw IK already drives to reach (x, y). A real
# 4-DOF+ cell would add a wrist-roll servo and actively align the gripper to
# `angle_deg` before closing (particularly important for the elongated bar
# class, to avoid grasping it near one end and having it tip out of the
# gripper); we still report `angle_deg` from the vision detection in the
# planned grasp so that upgrade is a drop-in addition, not a redesign.
# --------------------------------------------------------------------------

APPROACH_HEIGHT_M = 0.14   # hover height before descending onto an object
PLACE_APPROACH_HEIGHT_M = 0.14

CLASS_GRASP_PARAMS = {
    "bottle_cap": {"grasp_z_m": 0.045, "place_z_m": 0.06},
    "block":      {"grasp_z_m": 0.05,  "place_z_m": 0.07},
    "bar":        {"grasp_z_m": 0.05,  "place_z_m": 0.06},
}

# One sorting bin per class, positioned within the arm's reachable workspace
# but outside the tray footprint -- exactly how a real cell lays out bins
# around the edge of the arm's reach so a pick-and-place motion never has to
# cross back over the tray it's sorting from.
BIN_POSITIONS_M = {
    "bottle_cap": (0.05, 0.32),
    "block":      (-0.28, 0.15),
    "bar":        (-0.28, -0.15),
}


def plan_grasp(detection):
    """Turns one `vision.detect_objects` detection into a full grasp plan:
    world-frame pick position, class-specific approach/grasp/place heights,
    and the target sorting bin -- the payload a real `GraspPlannerNode`
    publishes for a motion-control node to execute.
    """
    cls = detection["class"]
    params = CLASS_GRASP_PARAMS[cls]
    px, py = detection["pixel_xy"]
    world_xy = pixel_to_world(px, py)
    bin_xy = BIN_POSITIONS_M[cls]
    return {
        "class": cls,
        "confidence": detection["confidence"],
        "pixel_xy": detection["pixel_xy"],
        "angle_deg": detection["angle_deg"],
        "world_xy": world_xy,
        "approach_z": APPROACH_HEIGHT_M,
        "grasp_z": params["grasp_z_m"],
        "place_z": params["place_z_m"],
        "bin_xy": bin_xy,
    }
