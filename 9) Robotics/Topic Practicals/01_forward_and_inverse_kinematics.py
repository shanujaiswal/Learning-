"""
01 - Forward and Inverse Kinematics: 2-Link Planar Arm
========================================================

Companion practical for Theory/Kinematics.md

This script simulates a 2-link planar robot arm (like a simple SCARA
or a human arm viewed from above, moving in a single plane).

Forward Kinematics (FK):
    Given joint angles (theta1, theta2), find where the end-effector
    (the "hand" / tip of the arm) ends up in (x, y) space.

Inverse Kinematics (IK):
    Given a desired end-effector position (x, y), solve for the joint
    angles (theta1, theta2) that would put the hand there.

We use the classic analytical (closed-form) 2-link IK solution, which
exists because a 2-link planar arm has exactly the right number of
degrees of freedom (2) to reach a target (x, y) with (generally) two
possible "elbow up" / "elbow down" solutions.

Run:
    pip install numpy matplotlib
    python 01_forward_and_inverse_kinematics.py
"""

import numpy as np
import matplotlib.pyplot as plt

# ---------------------------------------------------------------------------
# Arm geometry: lengths of link 1 (shoulder->elbow) and link 2 (elbow->hand)
# ---------------------------------------------------------------------------
L1 = 1.0
L2 = 0.75


def forward_kinematics(theta1, theta2, l1=L1, l2=L2):
    """Given joint angles (radians), return (elbow_xy, end_effector_xy).

    theta1 is measured from the positive x-axis to link 1.
    theta2 is measured relative to link 1 (i.e. the elbow bend angle).
    """
    # Position of the elbow joint
    elbow_x = l1 * np.cos(theta1)
    elbow_y = l1 * np.sin(theta1)

    # Position of the end effector (hand)
    hand_x = elbow_x + l2 * np.cos(theta1 + theta2)
    hand_y = elbow_y + l2 * np.sin(theta1 + theta2)

    return (elbow_x, elbow_y), (hand_x, hand_y)


def inverse_kinematics(x, y, l1=L1, l2=L2, elbow="down"):
    """Given a target end-effector position (x, y), solve for (theta1, theta2).

    Uses the law of cosines to find the elbow angle, then geometry to
    find the shoulder angle. Returns None if the target is unreachable
    (outside the annulus |l1-l2| <= r <= l1+l2).

    elbow: "down" or "up" -- selects which of the two IK solutions to
    return (elbow-down = theta2 >= 0, elbow-up = theta2 <= 0).
    """
    r_sq = x * x + y * y
    r = np.sqrt(r_sq)

    # Reachability check
    if r > (l1 + l2) or r < abs(l1 - l2):
        return None

    # --- Elbow angle (theta2) via law of cosines ---
    cos_theta2 = (r_sq - l1 ** 2 - l2 ** 2) / (2 * l1 * l2)
    cos_theta2 = np.clip(cos_theta2, -1.0, 1.0)  # guard tiny float errors
    theta2_mag = np.arccos(cos_theta2)
    theta2 = theta2_mag if elbow == "down" else -theta2_mag

    # --- Shoulder angle (theta1) ---
    k1 = l1 + l2 * np.cos(theta2)
    k2 = l2 * np.sin(theta2)
    theta1 = np.arctan2(y, x) - np.arctan2(k2, k1)

    return theta1, theta2


def plot_arm(ax, theta1, theta2, target=None, label="", color="tab:blue"):
    (ex, ey), (hx, hy) = forward_kinematics(theta1, theta2)
    ax.plot([0, ex, hx], [0, ey, hy], "-o", color=color, linewidth=3,
             markersize=8, label=label)
    if target is not None:
        ax.plot(target[0], target[1], "x", color="red", markersize=12,
                 markeredgewidth=3)


def main():
    print("=" * 70)
    print("2-LINK PLANAR ARM: FORWARD + INVERSE KINEMATICS")
    print("=" * 70)
    print(f"Link lengths: L1={L1}, L2={L2}\n")

    # -----------------------------------------------------------------
    # Demo 1: Forward kinematics for a couple of joint configurations
    # -----------------------------------------------------------------
    configs = [
        (np.radians(30), np.radians(45)),
        (np.radians(90), np.radians(-60)),
    ]
    for t1, t2 in configs:
        (ex, ey), (hx, hy) = forward_kinematics(t1, t2)
        print(f"FK: theta1={np.degrees(t1):6.1f} deg, "
              f"theta2={np.degrees(t2):6.1f} deg  ->  "
              f"elbow=({ex:.3f}, {ey:.3f})  hand=({hx:.3f}, {hy:.3f})")

    # -----------------------------------------------------------------
    # Demo 2: Inverse kinematics for a target point, then verify with FK
    # -----------------------------------------------------------------
    print("\nInverse kinematics targets:")
    targets = [(1.2, 0.5), (0.3, 1.0), (-1.0, 0.8)]
    fig, axes = plt.subplots(1, len(targets), figsize=(14, 5))
    fig.suptitle("Inverse Kinematics: solve angles for target, verify with FK")

    for ax, (tx, ty) in zip(axes, targets):
        sol = inverse_kinematics(tx, ty, elbow="down")
        if sol is None:
            print(f"  target=({tx}, {ty}) -> UNREACHABLE")
            continue
        theta1, theta2 = sol

        # Verify round-trip: FK of the IK solution should reproduce target
        (_, _), (hx, hy) = forward_kinematics(theta1, theta2)
        error = np.hypot(hx - tx, hy - ty)

        print(f"  target=({tx:5.2f}, {ty:5.2f}) -> "
              f"theta1={np.degrees(theta1):7.2f} deg, "
              f"theta2={np.degrees(theta2):7.2f} deg  |  "
              f"FK round-trip=({hx:.4f}, {hy:.4f})  error={error:.2e}")

        assert error < 1e-9, "Round-trip FK/IK check failed!"

        plot_arm(ax, theta1, theta2, target=(tx, ty),
                  label=f"target=({tx},{ty})", color="tab:blue")
        ax.set_xlim(-2, 2)
        ax.set_ylim(-2, 2)
        ax.set_aspect("equal")
        ax.grid(True, alpha=0.3)
        ax.axhline(0, color="black", linewidth=0.5)
        ax.axvline(0, color="black", linewidth=0.5)
        ax.legend(loc="upper right", fontsize=8)
        ax.set_title(f"target=({tx}, {ty})")

    plt.tight_layout()
    out_path = "kinematics_demo.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()
