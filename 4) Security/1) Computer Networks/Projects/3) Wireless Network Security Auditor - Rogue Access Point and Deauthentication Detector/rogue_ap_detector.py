"""
rogue_ap_detector.py
---------------------
The beacon-frame half of the auditor. Compares every observed beacon frame
against the trusted known_ap_inventory baseline and raises two kinds of
alerts, the same two things a real WIDS/WIPS (Cisco aWIPS, Aruba RFProtect,
Kismet's "rogue AP" alerting) is built to catch:

  1. EVIL_TWIN  -- a beacon advertises an SSID we own, but from a BSSID that
     is not in our inventory. Same name, different (unknown) radio hardware
     -- the textbook evil-twin / rogue-AP signature.

  2. WEAK_ENCRYPTION -- a beacon (whether from a known or unknown BSSID)
     advertises encryption weaker than the corporate policy requires for
     that SSID (e.g. OPEN or WEP where WPA2/WPA3 is mandated).

Detection here is purely a lookup/comparison problem -- no rate/statistics
needed, unlike deauth_detector.py.
"""

from typing import Dict, List

from known_ap_inventory import (
    APPROVED_NETWORKS,
    is_approved_bssid,
    meets_policy,
    required_encryption_for,
)


def detect_rogue_and_weak_aps(beacon_events: List[Dict]) -> List[Dict]:
    """
    Scan beacon events and return a list of alert dicts, each shaped like:
        {
            "time": float,
            "kind": "EVIL_TWIN" | "WEAK_ENCRYPTION",
            "bssid": str,
            "ssid": str,
            "detail": str,
        }
    One alert is emitted per *first sighting* of a violating (ssid, bssid,
    kind) combination, so a repeatedly-beaconing rogue AP doesn't flood the
    alert feed with duplicates -- exactly like a real WIDS deduplicates by
    BSSID rather than alerting on every single beacon.
    """
    alerts: List[Dict] = []
    already_alerted = set()  # (kind, bssid, ssid)

    for e in beacon_events:
        if e["type"] != "beacon":
            continue

        ssid = e["ssid"]
        bssid = e["bssid"]
        encryption = e["encryption"]

        # --- Evil twin check: SSID we own, but BSSID we don't recognize ---
        if ssid in APPROVED_NETWORKS and not is_approved_bssid(ssid, bssid):
            key = ("EVIL_TWIN", bssid, ssid)
            if key not in already_alerted:
                already_alerted.add(key)
                alerts.append({
                    "time": e["time"],
                    "kind": "EVIL_TWIN",
                    "bssid": bssid,
                    "ssid": ssid,
                    "detail": (
                        f"SSID '{ssid}' is broadcast by unrecognized BSSID {bssid} "
                        f"(approved BSSIDs: {APPROVED_NETWORKS[ssid].approved_bssids}). "
                        f"Likely evil-twin AP impersonating the corporate network."
                    ),
                })

        # --- Weak-encryption / policy-violation check ---
        if ssid in APPROVED_NETWORKS and not meets_policy(ssid, encryption):
            key = ("WEAK_ENCRYPTION", bssid, ssid)
            if key not in already_alerted:
                already_alerted.add(key)
                required = required_encryption_for(ssid)
                alerts.append({
                    "time": e["time"],
                    "kind": "WEAK_ENCRYPTION",
                    "bssid": bssid,
                    "ssid": ssid,
                    "detail": (
                        f"AP {bssid} broadcasts SSID '{ssid}' with encryption="
                        f"{encryption}, but policy requires at least {required}."
                    ),
                })

    alerts.sort(key=lambda a: a["time"])
    return alerts
