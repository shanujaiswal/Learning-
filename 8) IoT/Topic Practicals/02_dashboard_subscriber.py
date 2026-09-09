"""
02 - Dashboard Subscriber (MQTT wildcards, live status via LWT)
=================================================================

Companion practical for:
    Theory/03 IoT Communication Protocols -- MQTT, CoAP and Wireless Standards.md
    Theory/04 IoT Cloud Platforms and Data Pipelines.md

Concept
-------
This script plays the role of the consuming side of an IoT data pipeline:
a dashboard, or a cloud ingestion service, that doesn't know in advance
exactly how many devices exist or when they'll publish. It demonstrates:

    - The `+` single-level wildcard: subscribing to
      "study/iot/<prefix>/devices/+/reading" matches every device's
      reading topic without listing devices individually. New devices
      that start publishing under the same prefix are picked up
      automatically, with no code change here.
    - Full decoupling: this subscriber has never heard of the publisher
      process and never talks to it directly -- both only ever talk to
      the broker. Kill and restart the publisher and this keeps working.
    - Last Will and Testament (LWT) consumption: subscribing to the
      ".../status" topics lets this dashboard show a device as "OFFLINE"
      shortly after its connection drops, even though the device itself
      never got to say goodbye -- the broker said it on the device's
      behalf.
    - Retained messages: on subscribe, the broker immediately delivers
      the last retained status/reading for each device, so the dashboard
      is populated instantly instead of showing blank rows until the next
      publish cycle.
    - An in-memory "live" text dashboard, redrawn on every incoming
      message, showing latest reading + online/offline per device.

Run:
    pip install paho-mqtt
    python 02_dashboard_subscriber.py

Run "01_simulated_sensor_publisher.py" in another terminal first (or at
the same time). IMPORTANT: this script's TOPIC_PREFIX must match the
publisher's -- either hardcode a shared prefix in both files, or paste
the publisher's printed prefix in here (see TOPIC_PREFIX below).
"""

import json
import time

import paho.mqtt.client as mqtt

# ---------------------------------------------------------------------------
# Broker connection settings -- must match 01_simulated_sensor_publisher.py
# ---------------------------------------------------------------------------
BROKER_HOST = "localhost"   # set to "test.mosquitto.org" if you have no local broker
BROKER_PORT = 1883
KEEPALIVE_S = 30

# The publisher generates a random prefix per run and prints it at startup
# (e.g. "study/iot/ab12cd"). Paste that value here so this dashboard
# subscribes to the SAME device fleet. Alternatively, hardcode a fixed
# shared prefix in both scripts, e.g. TOPIC_PREFIX = "study/iot/demo1".
TOPIC_PREFIX = "study/iot/#"  # "#" here means: show me every session's devices

# If you know the exact prefix printed by the publisher, use the `+`
# wildcard form instead for a cleaner, single-fleet dashboard, e.g.:
#     TOPIC_PREFIX = "study/iot/ab12cd"

READING_TOPIC_FILTER = f"{TOPIC_PREFIX}/devices/+/reading" if not TOPIC_PREFIX.endswith("#") \
    else f"{TOPIC_PREFIX.rsplit('/#', 1)[0]}/#"
STATUS_TOPIC_FILTER = READING_TOPIC_FILTER  # same "#" catches status too when using "#"

REFRESH_INTERVAL_S = 2.0

# ---------------------------------------------------------------------------
# In-memory dashboard state: device_id -> {status, temp_c, humidity_pct,
# last_seen}. This is exactly the kind of "device shadow" concept a real
# cloud IoT platform (AWS IoT Core, Azure IoT Hub) maintains server-side.
# ---------------------------------------------------------------------------
devices = {}


def _device_id_from_topic(topic):
    # topics look like: study/iot/<prefix>/devices/<device_id>/reading
    #                or: study/iot/<prefix>/devices/<device_id>/status
    parts = topic.split("/")
    try:
        idx = parts.index("devices")
        return parts[idx + 1]
    except (ValueError, IndexError):
        return None


def on_connect(client, userdata, flags, rc):
    if rc != 0:
        print(f"Connection failed (rc={rc}). Is the broker reachable?")
        return
    print(f"Connected to {BROKER_HOST}:{BROKER_PORT} (rc={rc})")
    # Subscribing with a wildcard here means we never need to know device
    # names or count in advance -- this is the core pub/sub decoupling
    # win over, say, a hardcoded list of HTTP endpoints to poll.
    if TOPIC_PREFIX.endswith("#"):
        client.subscribe(TOPIC_PREFIX, qos=1)
        print(f"Subscribed to: {TOPIC_PREFIX}")
    else:
        reading_filter = f"{TOPIC_PREFIX}/devices/+/reading"
        status_filter = f"{TOPIC_PREFIX}/devices/+/status"
        client.subscribe(reading_filter, qos=1)
        client.subscribe(status_filter, qos=1)
        print(f"Subscribed to: {reading_filter}")
        print(f"Subscribed to: {status_filter}")


def on_message(client, userdata, msg):
    device_id = _device_id_from_topic(msg.topic)
    if device_id is None:
        return

    entry = devices.setdefault(device_id, {
        "status": "unknown",
        "temp_c": None,
        "humidity_pct": None,
        "last_seen": None,
        "message_count": 0,
    })
    entry["message_count"] += 1
    entry["last_seen"] = time.time()

    if msg.topic.endswith("/status"):
        # This is either a normal "online" announcement, OR the broker
        # delivering the LWT payload ("offline") on the device's behalf
        # after an unclean disconnect. Either way, we just trust the topic.
        entry["status"] = msg.payload.decode(errors="replace")
    elif msg.topic.endswith("/reading"):
        try:
            data = json.loads(msg.payload.decode())
        except (json.JSONDecodeError, UnicodeDecodeError):
            return
        entry["temp_c"] = data.get("temp_c")
        entry["humidity_pct"] = data.get("humidity_pct")
        # A device publishing readings is implicitly online, even if we
        # missed its retained "online" status message for some reason.
        if entry["status"] == "unknown":
            entry["status"] = "online"


def render_dashboard():
    # Very simple text "redraw" -- clears via blank lines rather than
    # ANSI escapes, so it stays portable across terminals.
    print("\n" * 2)
    print("=" * 78)
    print(f"IoT LIVE DASHBOARD  (topic filter: {TOPIC_PREFIX})   "
          f"[{time.strftime('%H:%M:%S')}]")
    print("=" * 78)
    if not devices:
        print("(no devices seen yet -- is the publisher running and connected "
              "to the same broker/prefix?)")
        print("=" * 78)
        return

    header = f"{'DEVICE':<20}{'STATUS':<10}{'TEMP (C)':<12}{'HUMIDITY (%)':<14}{'LAST SEEN':<12}{'MSGS'}"
    print(header)
    print("-" * len(header))
    for device_id in sorted(devices):
        e = devices[device_id]
        age_s = time.time() - e["last_seen"] if e["last_seen"] else None
        age_str = f"{age_s:5.1f}s ago" if age_s is not None else "n/a"
        status = e["status"].upper()
        # Staleness heuristic on top of the explicit status: if we haven't
        # heard from a device in a while even without an explicit LWT
        # firing yet (e.g. keepalive hasn't timed out at the broker), flag
        # it as STALE so a human notices before the LWT even lands.
        if status == "ONLINE" and age_s is not None and age_s > (KEEPALIVE_S * 1.5):
            status = "STALE?"
        temp_str = f"{e['temp_c']:.2f}" if e["temp_c"] is not None else "n/a"
        hum_str = f"{e['humidity_pct']:.2f}" if e["humidity_pct"] is not None else "n/a"
        print(f"{device_id:<20}{status:<10}{temp_str:<12}{hum_str:<14}{age_str:<12}{e['message_count']}")
    print("=" * 78)


def main():
    print("=" * 78)
    print("DASHBOARD SUBSCRIBER (MQTT '+'/'#' wildcards, LWT-driven status)")
    print("=" * 78)
    print(f"Broker: {BROKER_HOST}:{BROKER_PORT}")
    print("Press Ctrl+C to stop.")
    print("=" * 78)

    client = mqtt.Client(client_id="dashboard-subscriber")
    client.on_connect = on_connect
    client.on_message = on_message

    try:
        client.connect(BROKER_HOST, BROKER_PORT, KEEPALIVE_S)
    except Exception as exc:
        print(f"ERROR: could not connect to broker: {exc}")
        print("Is Mosquitto running on localhost:1883? See '00 README.md'.")
        return

    client.loop_start()  # background thread handles on_message callbacks

    try:
        while True:
            render_dashboard()
            time.sleep(REFRESH_INTERVAL_S)
    except KeyboardInterrupt:
        print("\nShutting down dashboard...")
        client.loop_stop()
        client.disconnect()
        print("Done.")


if __name__ == "__main__":
    main()
