"""
04 - Interaction Design, Comfort and Motion Sickness in XR: A Toy Comfort-Risk Model
======================================================================================

Companion practical for Theory/04 Interaction Design, Comfort and Motion
Sickness in XR.md

We can't run a real headset session here, so this script SIMULATES a short
VR session's per-frame telemetry (framerate, head angular velocity, whether
the user is using smooth locomotion vs teleportation, camera roll, and
whether a comfort vignette is active) and computes a simple heuristic
"discomfort score" per frame, grounded directly in concepts from the theory:

  Theory concept                                    -->  Model term
  ------------------------------------------------------------------------
  Motion-to-photon latency / needing ~90Hz           -->  fps_penalty:
  (low framerate widens the vestibular mismatch            large when fps
  window every single frame)                               drops below 90.
  Smooth locomotion vs teleportation (smooth is the  -->  locomotion_penalty:
  single biggest cause of sickness because eyes            active only
  report movement while the inner ear reports              during smooth-
  standing still)                                           locomotion frames.
  Camera roll / stable horizon (roll is far more     -->  roll_penalty:
  nauseating than planar movement)                          scales with
                                                             |camera roll|.
  Angular acceleration during turns (sustained       -->  angular_accel_penalty:
  visual-vestibular mismatch, why snap-turning             scales with the
  beats continuous turning)                                 magnitude of
                                                             head angular
                                                             acceleration.
  Vignetting during movement (narrowing peripheral   -->  vignette_relief:
  FOV reduces the vection cues that trigger                subtracts from
  discomfort while moving)                                  the total when
                                                             the vignette is
                                                             active.

The per-frame scores are combined into a running discomfort score, which we
classify into SAFE / CAUTION / RISK bands and plot over the session timeline
-- with a printed interpretation of which frames would be flagged as an
actual comfort risk in a real comfort-rating pass, and why.

This is a TOY, illustrative model for learning the *shape* of the trade-offs
the theory describes -- it is not a validated simulator-sickness predictor.

Run:
    pip install numpy matplotlib
    python 04_comfort_and_motion_sickness_demo.py
"""

import numpy as np
import matplotlib.pyplot as plt

RNG = np.random.default_rng(42)

# ---------------------------------------------------------------------------
# Session simulation parameters
# ---------------------------------------------------------------------------

NUM_FRAMES = 300           # ~ a few seconds of frame-by-frame telemetry
TARGET_FPS = 90.0          # theory: the comfort baseline for VR


def simulate_session():
    """Build a synthetic per-frame telemetry log for one short VR session
    made of four back-to-back segments, deliberately designed to range
    from clearly comfortable to clearly risky:

      1. Calm teleport segment    -- teleport locomotion, stable fps, low roll.
      2. Smooth locomotion segment-- continuous joystick movement (higher risk).
      3. Frame-drop segment       -- fps stutters well below 90Hz.
      4. Aggressive turn + roll   -- fast head yaw + camera roll (banking), no vignette.
    """
    frames = []
    seg_len = NUM_FRAMES // 4

    # --- Segment 1: calm, teleportation locomotion ---
    for i in range(seg_len):
        frames.append(dict(
            fps=TARGET_FPS + RNG.normal(0, 1.0),
            angular_velocity_deg_s=RNG.normal(0, 3.0),   # small natural head sway
            camera_roll_deg=0.0,
            locomotion="teleport",
            vignette_active=False,
        ))

    # --- Segment 2: smooth locomotion, otherwise healthy fps ---
    for i in range(seg_len):
        frames.append(dict(
            fps=TARGET_FPS + RNG.normal(0, 1.5),
            angular_velocity_deg_s=RNG.normal(0, 5.0),
            camera_roll_deg=0.0,
            locomotion="smooth",
            vignette_active=(i % 10 < 6),  # comfort vignette on most of the time
        ))

    # --- Segment 3: frame-drop / latency spike segment ---
    for i in range(seg_len):
        # fps stutters in a sine-ish dip pattern to simulate a rendering hitch
        dip = 35 * max(0.0, np.sin(i / seg_len * np.pi))
        frames.append(dict(
            fps=TARGET_FPS - dip + RNG.normal(0, 2.0),
            angular_velocity_deg_s=RNG.normal(0, 4.0),
            camera_roll_deg=0.0,
            locomotion="teleport",
            vignette_active=False,
        ))

    # --- Segment 4: aggressive continuous turning + camera roll (banking) ---
    for i in range(seg_len):
        t = i / seg_len
        frames.append(dict(
            fps=TARGET_FPS + RNG.normal(0, 1.0),
            angular_velocity_deg_s=180 * np.sin(t * 2 * np.pi),  # fast continuous turn
            camera_roll_deg=25 * np.sin(t * 2 * np.pi),          # banking turn, tilted horizon
            locomotion="smooth",
            vignette_active=False,
        ))

    return frames


# ---------------------------------------------------------------------------
# Heuristic comfort/discomfort scoring model
# ---------------------------------------------------------------------------

def compute_angular_acceleration(angular_velocities_deg_s, dt):
    """Finite-difference angular acceleration (deg/s^2) from a velocity
    series -- large values mean a sudden change in rotation rate, which is
    exactly what a snap-turn avoids and continuous turning risks."""
    vel = np.asarray(angular_velocities_deg_s, dtype=float)
    accel = np.gradient(vel, dt)
    return accel


def fps_penalty(fps, target_fps=TARGET_FPS):
    """0 when at/above target fps; grows steeply below it, reflecting the
    theory's point that motion-to-photon latency budget shrinks fast as
    framerate drops (fewer ms per frame headroom)."""
    deficit = max(0.0, target_fps - fps)
    return (deficit / target_fps) * 100.0  # 0-100+ scale


def locomotion_penalty(locomotion_type):
    """Smooth locomotion carries an inherent baseline discomfort risk that
    teleportation does not, per the theory's vestibular-mismatch explanation."""
    return 12.0 if locomotion_type == "smooth" else 0.0


def roll_penalty(camera_roll_deg):
    """Camera roll (tilted horizon) is called out in the theory as
    particularly nauseating -- penalize its magnitude more steeply than
    plain rotation."""
    return abs(camera_roll_deg) * 1.4


def angular_accel_penalty(angular_accel_deg_s2):
    """Penalize sudden changes in head/camera rotation rate."""
    return min(abs(angular_accel_deg_s2) * 0.05, 60.0)  # capped so one spike doesn't dominate


def vignette_relief(vignette_active, raw_motion_penalty):
    """Vignetting cuts peripheral-vision motion cues; model it as removing
    a fraction of the motion-driven discomfort while active."""
    return raw_motion_penalty * 0.4 if vignette_active else 0.0


def classify(score):
    if score < 15:
        return "SAFE"
    elif score < 35:
        return "CAUTION"
    else:
        return "RISK"


# ---------------------------------------------------------------------------
def main():
    print("=" * 78)
    print("XR COMFORT / MOTION-SICKNESS RISK: TOY HEURISTIC MODEL")
    print("=" * 78)

    frames = simulate_session()
    dt = 1.0 / TARGET_FPS

    fps_series = np.array([f["fps"] for f in frames])
    ang_vel_series = np.array([f["angular_velocity_deg_s"] for f in frames])
    roll_series = np.array([f["camera_roll_deg"] for f in frames])
    locomotion_series = [f["locomotion"] for f in frames]
    vignette_series = [f["vignette_active"] for f in frames]

    ang_accel_series = compute_angular_acceleration(ang_vel_series, dt)

    scores = []
    for i in range(NUM_FRAMES):
        p_fps = fps_penalty(fps_series[i])
        p_loco = locomotion_penalty(locomotion_series[i])
        p_roll = roll_penalty(roll_series[i])
        p_accel = angular_accel_penalty(ang_accel_series[i])

        motion_driven = p_loco + p_accel  # the part vignetting can mitigate
        relief = vignette_relief(vignette_series[i], motion_driven)

        total = p_fps + motion_driven + p_roll - relief
        total = max(0.0, total)
        scores.append(total)

    scores = np.array(scores)

    # Smooth with a short moving average -- real discomfort builds up over
    # time rather than reacting to a single instantaneous frame.
    window = 15
    kernel = np.ones(window) / window
    smoothed_scores = np.convolve(scores, kernel, mode="same")

    labels = [classify(s) for s in smoothed_scores]

    # -----------------------------------------------------------------
    # Segment summaries
    # -----------------------------------------------------------------
    seg_len = NUM_FRAMES // 4
    seg_names = [
        "1) Calm / teleport",
        "2) Smooth locomotion (vignette on)",
        "3) Frame-drop / latency spike",
        "4) Aggressive turning + camera roll",
    ]
    print("\nPer-segment summary:")
    print("-" * 78)
    for idx, name in enumerate(seg_names):
        seg_scores = smoothed_scores[idx * seg_len:(idx + 1) * seg_len]
        seg_fps = fps_series[idx * seg_len:(idx + 1) * seg_len]
        worst = seg_scores.max()
        mean = seg_scores.mean()
        risk_frames = np.sum([classify(s) == "RISK" for s in seg_scores])
        print(f"  {name:38s} mean_fps={seg_fps.mean():5.1f}  "
              f"mean_score={mean:5.1f}  worst_score={worst:5.1f}  "
              f"RISK frames={risk_frames:3d}/{seg_len}  "
              f"overall={classify(mean)}")

    total_risk_frames = int(np.sum(np.array(labels) == "RISK"))
    total_caution_frames = int(np.sum(np.array(labels) == "CAUTION"))
    print(f"\nTotal frames flagged RISK:    {total_risk_frames} / {NUM_FRAMES}")
    print(f"Total frames flagged CAUTION: {total_caution_frames} / {NUM_FRAMES}")

    print("\nInterpretation:")
    print("-" * 78)
    print("  Segment 1 (teleport, stable fps) stays SAFE -- exactly the theory's")
    print("  claim that teleportation avoids the sustained visual-vestibular")
    print("  mismatch that causes sickness.")
    print("  Segment 2 (smooth locomotion) trends into CAUTION even with the")
    print("  vignette active, showing the vignette provides partial relief but")
    print("  does not fully eliminate smooth-locomotion risk.")
    print("  Segment 3 (frame-drop) rises with fps -- confirming why the theory")
    print("  treats framerate/latency as the single most important comfort lever.")
    print("  Segment 4 (fast continuous turning + camera roll, no vignette) is")
    print("  the worst offender -- combining exactly the two factors the theory")
    print("  singles out as most nauseating: rotational mismatch and horizon roll.")

    # -----------------------------------------------------------------
    # Plot: telemetry + discomfort score timeline with risk bands
    # -----------------------------------------------------------------
    fig, axes = plt.subplots(3, 1, figsize=(12, 9), sharex=True)
    frame_idx = np.arange(NUM_FRAMES)

    ax = axes[0]
    ax.plot(frame_idx, fps_series, color="tab:blue", linewidth=1)
    ax.axhline(TARGET_FPS, color="green", linestyle="--", linewidth=1, label="90 Hz target")
    ax.set_ylabel("FPS")
    ax.set_title("Simulated per-frame telemetry")
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3)

    ax = axes[1]
    ax.plot(frame_idx, ang_vel_series, color="tab:purple", linewidth=1, label="angular velocity (deg/s)")
    ax.plot(frame_idx, roll_series, color="tab:brown", linewidth=1, label="camera roll (deg)")
    ax.set_ylabel("deg or deg/s")
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3)

    ax = axes[2]
    ax.plot(frame_idx, smoothed_scores, color="black", linewidth=1.5, label="discomfort score (smoothed)")
    ax.axhspan(0, 15, color="green", alpha=0.12, label="SAFE")
    ax.axhspan(15, 35, color="orange", alpha=0.12, label="CAUTION")
    ax.axhspan(35, max(smoothed_scores.max(), 40), color="red", alpha=0.12, label="RISK")
    for idx in range(1, 4):
        ax.axvline(idx * seg_len, color="gray", linestyle=":", linewidth=1)
    ax.set_ylabel("Discomfort score")
    ax.set_xlabel("Frame")
    ax.legend(fontsize=8, loc="upper left")
    ax.grid(True, alpha=0.3)

    plt.tight_layout()
    out_path = "comfort_and_motion_sickness_demo.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()
