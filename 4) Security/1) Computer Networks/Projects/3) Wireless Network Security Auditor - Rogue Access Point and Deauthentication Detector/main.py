"""
main.py
--------
Wireless Network Security Auditor -- ties everything together.

Pipeline (mirrors how a real WIDS/WIPS console works):
  1. Load the synthetic airspace log (airspace_log_generator).
  2. Run the beacon frames through rogue_ap_detector -> evil-twin AP alerts
     and weak-encryption alerts.
  3. Run the deauth frames through deauth_detector -> deauth-flood burst alerts.
  4. Print a running, time-ordered alert feed as if watching the console live.
  5. Print a summary of everything found.
  6. Save a PNG timeline (signal strength over time + deauth events) with
     every flagged item visually highlighted.
"""

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from airspace_log_generator import generate_airspace_log, format_event, LEGIT_SSID
from known_ap_inventory import APPROVED_NETWORKS
from rogue_ap_detector import detect_rogue_and_weak_aps
from deauth_detector import detect_deauth_bursts

OUTPUT_PNG = "wireless_audit_result.png"


def run_audit():
    log = generate_airspace_log()
    beacons = [e for e in log if e["type"] == "beacon"]
    deauths = [e for e in log if e["type"] == "deauth"]

    print("=" * 78)
    print("WIRELESS NETWORK SECURITY AUDITOR")
    print(f"Analyzing {len(log)} captured 802.11 frames "
          f"({len(beacons)} beacons, {len(deauths)} deauth frames)")
    print("=" * 78)
    print()

    rogue_alerts = detect_rogue_and_weak_aps(beacons)
    deauth_bursts = detect_deauth_bursts(deauths, window_seconds=5.0, threshold=10)

    # ---- Build a single, time-ordered alert feed -------------------------
    feed = []
    for a in rogue_alerts:
        feed.append((a["time"], "ROGUE", a))
    for b in deauth_bursts:
        feed.append((b["start_time"], "DEAUTH", b))
    feed.sort(key=lambda x: x[0])

    print("--- LIVE ALERT FEED " + "-" * 57)
    if not feed:
        print("  (no alerts -- airspace looks clean)")
    for t, category, alert in feed:
        if category == "ROGUE":
            tag = "!! EVIL TWIN AP  " if alert["kind"] == "EVIL_TWIN" else "!! WEAK ENCRYPTION"
            print(f"[t={t:>6.2f}s] {tag} | {alert['detail']}")
        else:
            print(f"[t={alert['start_time']:>6.2f}s] !! DEAUTH FLOOD    | {alert['detail']}")
    print()

    # ---- Summary -----------------------------------------------------------
    evil_twins = [a for a in rogue_alerts if a["kind"] == "EVIL_TWIN"]
    weak_aps = [a for a in rogue_alerts if a["kind"] == "WEAK_ENCRYPTION"]

    print("--- SUMMARY " + "-" * 65)
    print(f"Rogue / evil-twin APs found : {len(evil_twins)}")
    for a in evil_twins:
        print(f"    - BSSID {a['bssid']} impersonating SSID '{a['ssid']}' "
              f"(first seen t={a['time']:.2f}s)")

    print(f"Weak-encryption APs found   : {len(weak_aps)}")
    for a in weak_aps:
        print(f"    - BSSID {a['bssid']} SSID '{a['ssid']}' -> {a['detail']}")

    print(f"Deauth-flood bursts found   : {len(deauth_bursts)}")
    for b in deauth_bursts:
        print(f"    - {b['start_time']:.2f}s - {b['end_time']:.2f}s: "
              f"{b['frame_count']} frames")
    print("=" * 78)

    save_timeline_plot(beacons, deauths, evil_twins, weak_aps, deauth_bursts)
    print(f"\nSaved visual timeline to: {OUTPUT_PNG}")

    return {
        "evil_twins": evil_twins,
        "weak_aps": weak_aps,
        "deauth_bursts": deauth_bursts,
    }


def save_timeline_plot(beacons, deauths, evil_twins, weak_aps, deauth_bursts):
    evil_twin_bssids = {a["bssid"] for a in evil_twins}
    weak_bssids = {a["bssid"] for a in weak_aps}

    # Group beacon signal readings by BSSID for separate colored lines.
    by_bssid = {}
    for b in beacons:
        by_bssid.setdefault(b["bssid"], {"ssid": b["ssid"], "t": [], "s": []})
        by_bssid[b["bssid"]]["t"].append(b["time"])
        by_bssid[b["bssid"]]["s"].append(b["signal_dbm"])

    fig, (ax1, ax2) = plt.subplots(
        2, 1, figsize=(13, 8), sharex=True,
        gridspec_kw={"height_ratios": [3, 1]},
    )

    palette = ["#2b6cb0", "#2f855a", "#805ad5", "#718096", "#dd6b20"]
    color_i = 0
    for bssid, data in sorted(by_bssid.items()):
        label = f"{data['ssid']} ({bssid})"
        if bssid in evil_twin_bssids:
            ax1.plot(data["t"], data["s"], color="crimson", linewidth=2.0,
                     marker="x", markersize=5, label=label + "  [EVIL TWIN]")
        elif bssid in weak_bssids:
            ax1.plot(data["t"], data["s"], color="darkorange", linewidth=2.0,
                     marker="o", markersize=3, label=label + "  [WEAK ENCRYPTION]")
        else:
            ax1.plot(data["t"], data["s"], color=palette[color_i % len(palette)],
                     linewidth=1.3, alpha=0.85, label=label)
            color_i += 1

    for b in deauth_bursts:
        ax1.axvspan(b["start_time"], b["end_time"], color="red", alpha=0.15,
                    label="Deauth flood window" if b is deauth_bursts[0] else None)

    ax1.set_ylabel("Signal strength (dBm)")
    ax1.set_title("Wireless Airspace Audit -- Beacon Signal Timeline")
    ax1.legend(loc="lower left", fontsize=8, framealpha=0.9)
    ax1.grid(alpha=0.25)

    deauth_t = [d["time"] for d in deauths]
    ax2.eventplot([deauth_t], colors="black", lineoffsets=1, linelengths=0.8)
    for b in deauth_bursts:
        ax2.axvspan(b["start_time"], b["end_time"], color="red", alpha=0.25)
    ax2.set_yticks([])
    ax2.set_xlabel("Time (seconds)")
    ax2.set_ylabel("Deauth\nframes")
    ax2.grid(alpha=0.25, axis="x")

    fig.tight_layout()
    fig.savefig(OUTPUT_PNG, dpi=130)
    plt.close(fig)


if __name__ == "__main__":
    run_audit()
