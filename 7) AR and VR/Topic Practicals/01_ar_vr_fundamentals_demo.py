"""
01 - AR/VR Fundamentals and Hardware: Stereoscopy, Latency Budget, and Angular Resolution
==========================================================================================

Companion practical for Theory/01 AR VR Fundamentals and Hardware.md

We don't have a headset (or even a GPU pipeline) available in this sandbox,
so this script illustrates three of the core *hardware fundamentals* concepts
from the theory file numerically/geometrically instead of just describing them:

1. Binocular disparity / stereoscopic depth perception
   Two eyes (or two headset lens/screen pairs), separated by the
   interpupillary distance (IPD), each see a scene point from a slightly
   different horizontal position. The ANGULAR difference between what the
   left eye sees and what the right eye sees is "binocular disparity", and
   it is the raw geometric signal the brain uses to infer depth. We compute
   left/right eye camera positions from a given IPD, project a 3D scene
   point into each eye's view, and show how disparity shrinks as an object
   gets farther away (exactly why distant objects look "flatter").

2. Motion-to-photon latency budget vs refresh rate
   The theory file explains why VR needs ~90Hz: each frame only gets
   ~11ms, and total motion-to-photon latency (sensor sampling + fusion +
   render + scan-out) must stay under the ~20ms threshold where the brain
   starts noticing a vestibular/visual mismatch. We compute the frame
   budget for several refresh rates and flag which ones can plausibly stay
   under the comfort threshold given a fixed non-render latency overhead.

3. Angular resolution / screen-door effect
   Given a panel's horizontal resolution and the headset's horizontal
   field of view (FOV), we compute pixels-per-degree (PPD). Human foveal
   vision resolves roughly 60 PPD; a headset well below that will show
   visible gaps between pixels magnified by the lens -- the "screen-door
   effect". We compute PPD for a few real-ish headset configurations to
   show why higher resolution AND narrower FOV both help.

Run:
    pip install numpy matplotlib
    python 01_ar_vr_fundamentals_demo.py
"""

import numpy as np
import matplotlib.pyplot as plt

# ---------------------------------------------------------------------------
# 1) Binocular disparity from IPD
# ---------------------------------------------------------------------------

# Average adult IPD is roughly 63mm; we work in meters throughout.
IPD_M = 0.063


def eye_positions(ipd=IPD_M):
    """Return (left_eye_xyz, right_eye_xyz) centered on the origin (the
    "head" position), offset symmetrically along the X (left-right) axis.
    This mirrors how a stereo HMD renders two slightly-shifted camera
    views from a single head pose."""
    left = np.array([-ipd / 2.0, 0.0, 0.0])
    right = np.array([ipd / 2.0, 0.0, 0.0])
    return left, right


def horizontal_angle_to_point(eye_xyz, point_xyz):
    """Angle (radians) between the eye's forward axis (+Z) and the
    direction to a scene point, measured in the horizontal (X-Z) plane.
    This is a simplified stand-in for what a stereo camera pair actually
    projects onto each eye's image plane."""
    dx = point_xyz[0] - eye_xyz[0]
    dz = point_xyz[2] - eye_xyz[2]
    return np.arctan2(dx, dz)


def binocular_disparity(point_xyz, ipd=IPD_M):
    """Return the angular disparity (degrees) between left and right eye
    views of a 3D point -- the geometric basis of stereo depth perception."""
    left_eye, right_eye = eye_positions(ipd)
    angle_left = horizontal_angle_to_point(left_eye, point_xyz)
    angle_right = horizontal_angle_to_point(right_eye, point_xyz)
    return np.degrees(angle_left - angle_right)


# ---------------------------------------------------------------------------
# 2) Motion-to-photon latency budget vs refresh rate
# ---------------------------------------------------------------------------

COMFORT_THRESHOLD_MS = 20.0     # theory: ~20ms is where mismatch becomes noticeable
FIXED_NON_RENDER_OVERHEAD_MS = 5.0  # sensor sampling + fusion + scan-out, held constant


def frame_budget_ms(refresh_hz):
    """Time available to produce one frame, in milliseconds."""
    return 1000.0 / refresh_hz


def worst_case_latency_ms(refresh_hz, non_render_overhead_ms=FIXED_NON_RENDER_OVERHEAD_MS):
    """A simplified latency model: worst case is a full frame of render
    time plus fixed sensor/display overhead (in reality timewarp/reprojection
    shaves this down, per the theory's deep dive, but this shows the raw
    budget pressure that motivates needing reprojection at all)."""
    return frame_budget_ms(refresh_hz) + non_render_overhead_ms


# ---------------------------------------------------------------------------
# 3) Angular resolution / screen-door effect
# ---------------------------------------------------------------------------

FOVEAL_PPD_HUMAN = 60.0  # approx pixels-per-degree the human fovea can resolve


def pixels_per_degree(horizontal_resolution_px, horizontal_fov_deg):
    """Angular resolution of a display: how many pixels cover one degree
    of the user's field of view. Below ~60 PPD, individual pixels (and the
    black gaps between them, magnified by the lens) become perceptible --
    the screen-door effect."""
    return horizontal_resolution_px / horizontal_fov_deg


# ---------------------------------------------------------------------------
def main():
    print("=" * 78)
    print("AR/VR FUNDAMENTALS: STEREOSCOPY, LATENCY BUDGET, ANGULAR RESOLUTION")
    print("=" * 78)

    # -----------------------------------------------------------------
    # Demo 1: binocular disparity vs distance
    # -----------------------------------------------------------------
    print(f"\n[1] Binocular disparity (IPD = {IPD_M * 1000:.0f}mm)")
    print("-" * 78)
    distances_m = np.array([0.3, 0.5, 1.0, 2.0, 5.0, 10.0, 25.0])
    disparities_deg = []
    for d in distances_m:
        point = np.array([0.0, 0.0, d])  # straight ahead, distance d meters
        disp = binocular_disparity(point)
        disparities_deg.append(disp)
        print(f"  distance={d:6.2f} m  ->  binocular disparity = {disp:7.4f} deg")
    disparities_deg = np.array(disparities_deg)

    print("\n  Interpretation: disparity is largest for near objects and shrinks")
    print("  rapidly with distance -- this is precisely why stereo depth cues")
    print("  become nearly useless beyond a few meters (the theory's point that")
    print("  distant scenery relies on other depth cues, not stereopsis).")

    # -----------------------------------------------------------------
    # Demo 2: latency budget vs refresh rate
    # -----------------------------------------------------------------
    print(f"\n[2] Motion-to-photon latency budget (comfort threshold = "
          f"{COMFORT_THRESHOLD_MS:.0f}ms, fixed overhead = "
          f"{FIXED_NON_RENDER_OVERHEAD_MS:.0f}ms)")
    print("-" * 78)
    refresh_rates = [30, 60, 72, 90, 120]
    latencies = []
    for hz in refresh_rates:
        budget = frame_budget_ms(hz)
        worst = worst_case_latency_ms(hz)
        latencies.append(worst)
        flag = "OK (under threshold)" if worst <= COMFORT_THRESHOLD_MS else "RISK (over threshold)"
        print(f"  {hz:4d} Hz  ->  frame budget={budget:6.2f}ms  "
              f"worst-case motion-to-photon={worst:6.2f}ms  [{flag}]")

    print("\n  Interpretation: at 30/60Hz the raw per-frame budget alone already")
    print("  blows past the ~20ms comfort threshold once fixed sensor/display")
    print("  overhead is added -- this is exactly why VR needs ~90Hz+ AND")
    print("  predictive reprojection/timewarp rather than just 'a faster GPU'.")

    # -----------------------------------------------------------------
    # Demo 3: angular resolution / screen-door effect
    # -----------------------------------------------------------------
    print(f"\n[3] Angular resolution (human fovea resolves ~{FOVEAL_PPD_HUMAN:.0f} PPD)")
    print("-" * 78)
    headsets = [
        ("Early mobile VR (e.g. Cardboard-era)", 1280, 100),
        ("Meta Quest 2-class", 1832, 97),
        ("Meta Quest 3-class", 2064, 96),
        ("High-end / Vision-Pro-class", 3660, 100),
    ]
    ppds = []
    for name, res_px, fov_deg in headsets:
        ppd = pixels_per_degree(res_px, fov_deg)
        ppds.append(ppd)
        pct_of_foveal = 100.0 * ppd / FOVEAL_PPD_HUMAN
        flag = "screen-door visible" if ppd < FOVEAL_PPD_HUMAN else "near/at foveal acuity"
        print(f"  {name:38s} {res_px:5d}px / {fov_deg:3d} deg FOV  ->  "
              f"{ppd:5.1f} PPD ({pct_of_foveal:5.1f}% of human foveal)  [{flag}]")

    # -----------------------------------------------------------------
    # Plot: disparity-vs-distance curve and PPD comparison, saved to file
    # -----------------------------------------------------------------
    fig, axes = plt.subplots(1, 2, figsize=(13, 5))

    ax = axes[0]
    ax.plot(distances_m, disparities_deg, "-o", color="tab:blue")
    ax.set_xlabel("Distance to scene point (m)")
    ax.set_ylabel("Binocular disparity (degrees)")
    ax.set_title("Stereo disparity falls off with distance")
    ax.grid(True, alpha=0.3)

    ax = axes[1]
    names_short = [h[0] for h in headsets]
    bars = ax.bar(range(len(headsets)), ppds, color="tab:orange")
    ax.axhline(FOVEAL_PPD_HUMAN, color="red", linestyle="--",
               label=f"human foveal acuity (~{FOVEAL_PPD_HUMAN:.0f} PPD)")
    ax.set_xticks(range(len(headsets)))
    ax.set_xticklabels([n.split(" (")[0] for n in names_short], rotation=25,
                        ha="right", fontsize=8)
    ax.set_ylabel("Pixels per degree (PPD)")
    ax.set_title("Angular resolution vs screen-door threshold")
    ax.legend(fontsize=8)
    ax.grid(True, axis="y", alpha=0.3)

    plt.tight_layout()
    out_path = "ar_vr_fundamentals_demo.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()
