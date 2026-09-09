"""
airspace_log_generator.py
--------------------------
Generates a synthetic, time-ordered log of the office's "airspace" -- the mix
of 802.11 beacon frames (what every AP constantly broadcasts to advertise
itself) and management frames (here, deauthentication frames) that a wireless
sniffer sitting in monitor mode would capture.

IMPORTANT: this is a pure data simulation. No wireless hardware, monitor mode,
or packet capture library is used or required -- every "frame" is just a
Python dict built from a fixed random seed, so the scenario is 100%
reproducible and runs anywhere.

Injected into an otherwise-normal day of traffic:
  1. Three legitimate corporate APs beaconing "CorpNet-WiFi" (WPA2), plus one
     legitimate guest AP.
  2. A misconfigured legitimate AP: the guest AP broadcasts OPEN (no
     encryption) instead of the required WPA2 -- a weak-security finding.
  3. An evil-twin AP: an unknown BSSID broadcasting the SAME SSID as the
     legitimate corporate network, trying to lure clients to associate with it.
  4. A deauthentication flood: a short, dense burst of deauth frames aimed at
     knocking clients off the legitimate AP -- the classic precursor to
     steering victims onto the evil twin.
  5. A small amount of background deauth "noise" (ordinary roaming) so the
     rate-based detector has to actually discriminate signal from noise.
"""

import random
from typing import Dict, List

SEED = 42

LEGIT_SSID = "CorpNet-WiFi"
GUEST_SSID = "CorpNet-Guest"

LEGIT_BSSIDS = [
    "AA:BB:CC:00:01:01",
    "AA:BB:CC:00:01:02",
    "AA:BB:CC:00:01:03",
]
GUEST_BSSID = "AA:BB:CC:00:02:01"
EVIL_TWIN_BSSID = "DE:AD:BE:EF:13:37"

CLIENT_MACS = [f"CL:IE:NT:00:00:{i:02X}" for i in range(1, 8)]

SIM_DURATION_SECONDS = 120.0


def _beacon(t: float, bssid: str, ssid: str, encryption: str, base_signal: float,
            rng: random.Random, channel: int) -> Dict:
    return {
        "time": round(t, 2),
        "type": "beacon",
        "bssid": bssid,
        "ssid": ssid,
        "encryption": encryption,
        "signal_dbm": round(base_signal + rng.uniform(-3.0, 3.0), 1),
        "channel": channel,
    }


def _deauth(t: float, src: str, dst: str) -> Dict:
    return {
        "time": round(t, 3),
        "type": "deauth",
        "src": src,
        "dst": dst,
    }


def _generate_beacon_stream(rng: random.Random, bssid: str, ssid: str, encryption: str,
                             base_signal: float, interval: float, channel: int,
                             start: float = 0.0, end: float = SIM_DURATION_SECONDS) -> List[Dict]:
    events = []
    t = start
    while t < end:
        events.append(_beacon(t, bssid, ssid, encryption, base_signal, rng, channel))
        t += interval + rng.uniform(-0.15, 0.15)
    return events


def _generate_deauth_flood(rng: random.Random, spoofed_src: str, start: float,
                            duration: float, count: int) -> List[Dict]:
    """A dense burst: many deauth frames in a short window, cycling through
    victim client MACs -- the signature of an active deauth-flood attack."""
    events = []
    for i in range(count):
        t = start + (duration * i / count) + rng.uniform(-0.02, 0.02)
        dst = CLIENT_MACS[i % len(CLIENT_MACS)]
        events.append(_deauth(t, spoofed_src, dst))
    return events


def _generate_background_deauth_noise(rng: random.Random, legit_bssid: str,
                                       start: float, end: float, avg_gap: float) -> List[Dict]:
    """Ordinary, sparse deauths that happen on any real network (a client
    roaming between APs, a device going to sleep, etc). Should NOT trigger
    the burst detector by itself."""
    events = []
    t = start + rng.uniform(0, avg_gap)
    while t < end:
        dst = rng.choice(CLIENT_MACS)
        events.append(_deauth(t, legit_bssid, dst))
        t += rng.uniform(avg_gap * 0.5, avg_gap * 1.5)
    return events


def generate_airspace_log() -> List[Dict]:
    """Build the full, time-sorted synthetic airspace log for the scenario."""
    rng = random.Random(SEED)

    events: List[Dict] = []

    # 1. Legitimate corporate APs -- steady WPA2 beacons all day.
    for idx, bssid in enumerate(LEGIT_BSSIDS):
        events += _generate_beacon_stream(
            rng, bssid, LEGIT_SSID, "WPA2",
            base_signal=-45 - idx * 5, interval=1.0, channel=1 + idx * 5,
        )

    # 2. Legitimate guest AP -- but misconfigured with NO encryption
    #    (should have been WPA2 per policy). A classic weak-security finding.
    events += _generate_beacon_stream(
        rng, GUEST_BSSID, GUEST_SSID, "OPEN",
        base_signal=-58, interval=1.4, channel=11,
    )

    # 3. Evil twin -- unknown hardware, same SSID as the real corporate
    #    network, appears partway through the day (attacker powers it on),
    #    and claims WPA2 in its beacon to look convincing.
    events += _generate_beacon_stream(
        rng, EVIL_TWIN_BSSID, LEGIT_SSID, "WPA2",
        base_signal=-48, interval=1.0, channel=6,
        start=45.0, end=SIM_DURATION_SECONDS,
    )

    # 4. Deauth flood -- shortly after the evil twin appears, attacker
    #    floods deauth frames spoofing the legitimate AP's BSSID as the
    #    source, to force clients to disconnect and roam onto the evil twin.
    events += _generate_deauth_flood(
        rng, spoofed_src=LEGIT_BSSIDS[0], start=52.0, duration=3.0, count=60,
    )

    # 5. Background deauth noise across the whole run (normal roaming).
    events += _generate_background_deauth_noise(
        rng, legit_bssid=LEGIT_BSSIDS[1], start=0.0, end=SIM_DURATION_SECONDS, avg_gap=18.0,
    )

    events.sort(key=lambda e: e["time"])
    return events


def format_event(e: Dict) -> str:
    """Human-readable one-line rendering of a log record, like a sniffer's log line."""
    if e["type"] == "beacon":
        return (f"[t={e['time']:>6.2f}s] BEACON  bssid={e['bssid']} ssid={e['ssid']!r:<16} "
                f"enc={e['encryption']:<4} signal={e['signal_dbm']:>6.1f}dBm ch={e['channel']}")
    return f"[t={e['time']:>6.3f}s] DEAUTH  src={e['src']} -> dst={e['dst']}"


if __name__ == "__main__":
    log = generate_airspace_log()
    print(f"Generated {len(log)} events over {SIM_DURATION_SECONDS:.0f}s of simulated airspace.\n")
    for evt in log[:20]:
        print(format_event(evt))
    print("...")
