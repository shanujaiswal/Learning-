"""
deauth_detector.py
--------------------
The management-frame half of the auditor: rate-based detection of a
deauthentication flood, the classic pre-cursor to an evil-twin attack (knock
clients off the real AP so they roam onto the attacker's look-alike).

A single deauth frame is completely normal Wi-Fi housekeeping (an AP
disconnecting a sleeping or roaming client). What is NOT normal is a *burst*
-- many deauth frames arriving in a short time window. This mirrors how real
WIDS/WIPS products (Kismet's deauth-flood alert, Cisco aWIPS "deauth flood"
signature) work: a sliding time window with a frame-count threshold.
"""

from typing import Dict, List


def detect_deauth_bursts(deauth_events: List[Dict], window_seconds: float = 5.0,
                          threshold: int = 10) -> List[Dict]:
    """
    Sliding-window rate detector over deauth frames.

    For every deauth frame, count how many deauth frames (including itself)
    fall within [t, t + window_seconds). If that count reaches `threshold`,
    the window is flagged as part of an attack burst. Consecutive/overlapping
    flagged windows are merged into a single burst alert so one attack
    produces one alert, not dozens.

    Returns a list of burst alert dicts:
        {
            "start_time": float,
            "end_time": float,
            "frame_count": int,
            "kind": "DEAUTH_FLOOD",
            "detail": str,
        }
    """
    events = sorted(deauth_events, key=lambda e: e["time"])
    n = len(events)

    flagged_indices = set()  # indices of frames that are part of *some* flagged window

    left = 0
    for right in range(n):
        # Advance the left edge so the window only spans window_seconds.
        while events[right]["time"] - events[left]["time"] > window_seconds:
            left += 1
        count = right - left + 1
        if count >= threshold:
            flagged_indices.update(range(left, right + 1))

    if not flagged_indices:
        return []

    # Merge contiguous flagged indices into bursts.
    sorted_idx = sorted(flagged_indices)
    bursts: List[Dict] = []
    burst_start_i = sorted_idx[0]
    prev_i = sorted_idx[0]

    def _emit(start_i: int, end_i: int):
        frames = events[start_i:end_i + 1]
        start_t = frames[0]["time"]
        end_t = frames[-1]["time"]
        sources = {f["src"] for f in frames}
        targets = {f["dst"] for f in frames}
        bursts.append({
            "start_time": start_t,
            "end_time": end_t,
            "frame_count": len(frames),
            "kind": "DEAUTH_FLOOD",
            "detail": (
                f"{len(frames)} deauth frames in {max(end_t - start_t, 0.001):.2f}s "
                f"(>= {threshold} per {window_seconds:.0f}s window). "
                f"Apparent source(s): {sorted(sources)}. "
                f"{len(targets)} distinct client(s) targeted: {sorted(targets)}."
            ),
        })

    for i in sorted_idx[1:]:
        if i == prev_i + 1:
            prev_i = i
            continue
        _emit(burst_start_i, prev_i)
        burst_start_i = i
        prev_i = i
    _emit(burst_start_i, prev_i)

    return bursts
