"""
03 - Edge Filtering vs. Naive Cloud-Everything: Bandwidth Comparison
======================================================================

Companion practical for:
    Theory/01 IoT Fundamentals and Architecture.md ("Edge Computing vs Cloud
    Computing for IoT")
    Theory/04 IoT Cloud Platforms and Data Pipelines.md

Concept
-------
This script needs no MQTT broker and no network at all -- it's a
self-contained simulation of the core architectural trade-off from the
theory notes: do you send every raw sensor reading to the cloud, or do
you filter/aggregate at the edge (on the device or a local gateway) and
only send what actually matters?

Two strategies are simulated over one full (simulated) day of a
temperature sensor sampled once per second (86,400 samples):

1. NAIVE CLOUD-EVERYTHING
   Every single raw reading is transmitted to the cloud, exactly as
   sampled. Simple to build, but bandwidth/battery cost scales linearly
   with sample rate regardless of whether the data is actually changing.

2. EDGE FILTERING (report-on-change + periodic keepalive)
   The device only transmits when:
     (a) the reading has drifted more than DELTA_THRESHOLD from the last
         value actually sent ("meaningful change"), or
     (b) MAX_SILENCE_S has elapsed since the last transmission (a
         keepalive/heartbeat, so the cloud can tell "quiet because
         nothing changed" apart from "device went silent/died").
   This is the standard "report by exception" edge pattern: the edge
   does cheap local comparison work continuously, and only pays the
   (expensive, battery-costly) radio transmission cost when there's
   something worth saying.

We measure, for each strategy: total messages sent, total simulated
bytes transmitted (assuming a small fixed-size payload per message, as
a real compact binary/JSON telemetry payload would be), and report the
percentage reduction edge filtering achieves -- directly illustrating
the "bandwidth cost" and "just buffer it and retry" discussion from the
fundamentals chapter.

Run:
    python 03_edge_vs_cloud_processing_demo.py

No third-party dependencies -- pure standard library.
"""

import math
import random
import statistics

# ---------------------------------------------------------------------------
# Simulation parameters
# ---------------------------------------------------------------------------
SECONDS_PER_DAY = 24 * 60 * 60      # 86,400 samples: one raw reading per second
SAMPLE_INTERVAL_S = 1

BASE_TEMP_C = 21.0
DAILY_AMPLITUDE_C = 4.0             # how much temperature swings over the day
NOISE_STD_C = 0.05                  # small per-sample sensor noise

# Occasional short "event" periods (e.g. a door opens, a heater kicks on)
# where the temperature moves faster/further than the smooth daily cycle,
# so edge filtering has genuinely interesting deltas to catch.
NUM_EVENTS = 6
EVENT_DURATION_S = 900              # 15 simulated minutes
EVENT_MAGNITUDE_C = 2.5

# Edge filtering thresholds
DELTA_THRESHOLD_C = 0.3             # send if reading moved more than this...
MAX_SILENCE_S = 600                 # ...or if this long has passed regardless

# Payload size assumption: a compact telemetry message (device id, value,
# timestamp) as compact JSON or a fixed binary struct. Used only to turn
# "message count" into a more tangible "bytes transmitted" figure.
BYTES_PER_MESSAGE = 48


def generate_day_of_readings(seed=42):
    """Generate one simulated day of per-second temperature readings.

    Combines a smooth daily sinusoid, small Gaussian sensor noise, and a
    handful of localized "event" bumps -- meant to resemble a real
    temperature trace closely enough for the comparison to be meaningful,
    without needing numpy.
    """
    rng = random.Random(seed)
    readings = []

    # Pick random, non-overlapping-ish event windows across the day.
    event_starts = sorted(rng.sample(range(0, SECONDS_PER_DAY - EVENT_DURATION_S),
                                      NUM_EVENTS))

    for t in range(0, SECONDS_PER_DAY, SAMPLE_INTERVAL_S):
        daily = DAILY_AMPLITUDE_C * math.sin(2 * math.pi * t / SECONDS_PER_DAY)
        noise = rng.gauss(0, NOISE_STD_C)

        event_bump = 0.0
        for es in event_starts:
            if es <= t < es + EVENT_DURATION_S:
                # Smooth ramp up/down within the event window (half-sine)
                progress = (t - es) / EVENT_DURATION_S
                event_bump = EVENT_MAGNITUDE_C * math.sin(math.pi * progress)
                break

        temp_c = BASE_TEMP_C + daily + noise + event_bump
        readings.append((t, round(temp_c, 3)))

    return readings


def naive_cloud_everything(readings):
    """Strategy 1: transmit every single raw reading, unconditionally."""
    messages_sent = len(readings)
    return messages_sent


def edge_filtering(readings, delta_threshold=DELTA_THRESHOLD_C, max_silence_s=MAX_SILENCE_S):
    """Strategy 2: report-on-change with a periodic keepalive.

    Returns (messages_sent, sent_readings) where sent_readings is the
    subsequence of (t, value) pairs that were actually transmitted --
    useful for sanity-checking that meaningful changes weren't lost.
    """
    if not readings:
        return 0, []

    sent_readings = [readings[0]]  # always send the first reading
    last_sent_t, last_sent_val = readings[0]

    for t, val in readings[1:]:
        moved_enough = abs(val - last_sent_val) >= delta_threshold
        silence_expired = (t - last_sent_t) >= max_silence_s
        if moved_enough or silence_expired:
            sent_readings.append((t, val))
            last_sent_t, last_sent_val = t, val

    return len(sent_readings), sent_readings


def reconstruct_via_hold_last_value(sent_readings, all_timestamps):
    """Approximate what the cloud "sees" under edge filtering: it holds
    the last transmitted value until the next message arrives. Used only
    to report how much reconstruction error this costs, for context --
    edge filtering isn't free, it trades some fidelity for bandwidth.
    """
    reconstructed = []
    idx = 0
    current_val = sent_readings[0][1]
    for t in all_timestamps:
        while idx < len(sent_readings) - 1 and sent_readings[idx + 1][0] <= t:
            idx += 1
            current_val = sent_readings[idx][1]
        reconstructed.append(current_val)
    return reconstructed


def main():
    print("=" * 78)
    print("EDGE FILTERING vs. NAIVE CLOUD-EVERYTHING: BANDWIDTH COMPARISON")
    print("=" * 78)
    print(f"Simulated period: {SECONDS_PER_DAY:,} seconds (1 full day, 1 sample/sec)")
    print(f"Edge thresholds:  delta >= {DELTA_THRESHOLD_C} C, "
          f"or keepalive every {MAX_SILENCE_S}s")
    print(f"Assumed payload:  {BYTES_PER_MESSAGE} bytes/message")
    print("=" * 78)

    readings = generate_day_of_readings()
    raw_values = [v for _, v in readings]
    print(f"\nGenerated {len(readings):,} raw readings.")
    print(f"  min={min(raw_values):.2f}C  max={max(raw_values):.2f}C  "
          f"mean={statistics.mean(raw_values):.2f}C  "
          f"stdev={statistics.pstdev(raw_values):.3f}C")

    # -----------------------------------------------------------------
    # Strategy 1: naive cloud-everything
    # -----------------------------------------------------------------
    naive_messages = naive_cloud_everything(readings)
    naive_bytes = naive_messages * BYTES_PER_MESSAGE

    # -----------------------------------------------------------------
    # Strategy 2: edge filtering
    # -----------------------------------------------------------------
    edge_messages, sent_readings = edge_filtering(readings)
    edge_bytes = edge_messages * BYTES_PER_MESSAGE

    # -----------------------------------------------------------------
    # Fidelity cost: how far off is the cloud's "last known value" view
    # under edge filtering, compared to having every raw sample?
    # -----------------------------------------------------------------
    all_timestamps = [t for t, _ in readings]
    reconstructed = reconstruct_via_hold_last_value(sent_readings, all_timestamps)
    errors = [abs(r - v) for r, v in zip(reconstructed, raw_values)]
    max_error = max(errors)
    mean_error = statistics.mean(errors)

    # -----------------------------------------------------------------
    # Report
    # -----------------------------------------------------------------
    print("\n" + "-" * 78)
    print(f"{'STRATEGY':<28}{'MESSAGES':>14}{'BYTES':>16}{'BYTES/HOUR':>16}")
    print("-" * 78)
    print(f"{'Naive cloud-everything':<28}{naive_messages:>14,}{naive_bytes:>16,}"
          f"{naive_bytes / 24:>16,.0f}")
    print(f"{'Edge filtering':<28}{edge_messages:>14,}{edge_bytes:>16,}"
          f"{edge_bytes / 24:>16,.0f}")
    print("-" * 78)

    msg_reduction_pct = 100.0 * (1 - edge_messages / naive_messages)
    byte_reduction_pct = 100.0 * (1 - edge_bytes / naive_bytes)

    print(f"\nMessage count reduction: {msg_reduction_pct:5.1f}%  "
          f"({naive_messages:,} -> {edge_messages:,})")
    print(f"Bandwidth reduction:     {byte_reduction_pct:5.1f}%  "
          f"({naive_bytes:,} bytes -> {edge_bytes:,} bytes)")
    print(f"\nReconstruction fidelity cost of edge filtering "
          f"(cloud's 'last known value' vs. true raw reading):")
    print(f"  mean error = {mean_error:.4f} C   max error = {max_error:.4f} C")

    print("\n" + "=" * 78)
    print("TAKEAWAY")
    print("=" * 78)
    print(
        "Naive cloud-everything sends a message every second regardless of\n"
        "whether anything changed -- simple, but bandwidth/battery cost is\n"
        "constant and scales purely with sample rate. Edge filtering sends a\n"
        "message only on a meaningful change or a periodic keepalive, cutting\n"
        f"transmitted messages/bytes by ~{msg_reduction_pct:.0f}% here, at the cost of only\n"
        f"~{mean_error:.3f}C average 'staleness' error in the cloud's view between\n"
        "updates -- exactly the bandwidth-vs-fidelity trade-off edge computing\n"
        "is meant to make explicit, per Theory/01 IoT Fundamentals and\n"
        "Architecture.md."
    )


if __name__ == "__main__":
    main()
