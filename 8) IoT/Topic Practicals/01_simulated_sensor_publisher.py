"""
01 - Simulated Sensor Publisher (MQTT, LWT, drifting readings)
================================================================

Companion practical for:
    Theory/02 Microcontrollers and Single Board Computers.md
    Theory/03 IoT Communication Protocols -- MQTT, CoAP and Wireless Standards.md

Concept
-------
In real IoT deployments, a microcontroller or SBC reads a physical sensor
(temperature, humidity, ...) and publishes readings to an MQTT broker over
a lightweight publish/subscribe protocol, rather than polling or holding a
persistent request/response connection the way HTTP would. This script
plays the role of that hardware: no real Arduino/ESP32/Raspberry Pi is
needed, but it speaks genuine MQTT to a real broker exactly as a physical
device would, using the `paho-mqtt` client library.

What this script demonstrates:
    - Publish/subscribe messaging over MQTT (as opposed to request/response).
    - Hierarchical topics, e.g. "study/iot/<prefix>/devices/<device_id>/reading".
    - Last Will and Testament (LWT): registered with the broker at connect
      time, delivered automatically by the BROKER (not this script) if the
      connection drops uncleanly -- a dead-man's-switch for detecting
      offline devices without the subscriber needing a heartbeat timeout.
    - Retained "online" status messages so a dashboard that (re)subscribes
      later still immediately knows a device's last known status.
    - Realistic-ish sensor simulation: values drift slowly via a random
      walk plus daily sinusoidal variation, instead of pure random noise,
      the way a real temperature/humidity sensor's readings would behave.
    - Multiple simulated devices from a single process (easy to extend to
      more by adding entries to SIMULATED_DEVICES).

Run:
    pip install paho-mqtt
    python 01_simulated_sensor_publisher.py

Requires a broker reachable at BROKER_HOST:BROKER_PORT (see "00 README.md"
for how to run Mosquitto locally, or use a public test broker instead).
Run "02_dashboard_subscriber.py" in another terminal to watch the
readings arrive live. Press Ctrl+C here and watch the dashboard mark the
device offline shortly after -- that's the LWT firing.
"""

import json
import math
import random
import string
import time

import paho.mqtt.client as mqtt

# ---------------------------------------------------------------------------
# Broker connection settings
# ---------------------------------------------------------------------------
BROKER_HOST = "localhost"   # set to "test.mosquitto.org" if you have no local broker
BROKER_PORT = 1883
KEEPALIVE_S = 30

# A random suffix per run keeps this demo from colliding with other
# learners' traffic if using a shared/public test broker. Two scripts run
# in the SAME terminal session of this exercise must agree on this value
# -- see TOPIC_PREFIX override note below.
_RANDOM_SUFFIX = "".join(random.choices(string.ascii_lowercase + string.digits, k=6))
TOPIC_PREFIX = f"study/iot/{_RANDOM_SUFFIX}"

# To pair this publisher with a specific dashboard run (rather than a fresh
# random prefix each time), hardcode a shared prefix here, e.g.:
#     TOPIC_PREFIX = "study/iot/demo1"
# Uncomment the line below to use a fixed, easy-to-remember prefix instead:
# TOPIC_PREFIX = "study/iot/demo1"

PUBLISH_INTERVAL_S = 3.0

# ---------------------------------------------------------------------------
# Simulated fleet: each entry stands in for one physical sensor node
# (e.g. an ESP32 with a DHT22 temperature/humidity sensor attached).
# Add more entries here to simulate a larger fleet -- nothing else in the
# script needs to change.
# ---------------------------------------------------------------------------
SIMULATED_DEVICES = [
    {"device_id": "livingroom-01", "base_temp_c": 21.0, "base_humidity": 45.0},
    {"device_id": "greenhouse-02", "base_temp_c": 27.0, "base_humidity": 65.0},
    {"device_id": "garage-03", "base_temp_c": 16.0, "base_humidity": 50.0},
]


class SimulatedSensor:
    """Stateful random-walk + daily-cycle sensor simulator.

    Real sensors don't jump around randomly reading to reading -- they
    drift slowly (thermal mass, humidity diffusion) and follow a rough
    daily cycle (warmer in "daytime"). We approximate both:
        reading(t) = base + daily_amplitude * sin(2*pi*t/day) + random_walk
    """

    def __init__(self, base_temp_c, base_humidity, seed=None):
        self._rng = random.Random(seed)
        self.base_temp_c = base_temp_c
        self.base_humidity = base_humidity
        # Random-walk state, reset drift each call
        self._temp_drift = 0.0
        self._humidity_drift = 0.0
        self._start_time = time.time()
        # Compress a full "day" into a short window so drift/cycle is
        # visible during a live demo instead of taking 24 real hours.
        self._simulated_day_s = 600.0  # one simulated "day" every 10 minutes

    def _daily_phase(self):
        elapsed = time.time() - self._start_time
        return 2 * math.pi * (elapsed % self._simulated_day_s) / self._simulated_day_s

    def read(self):
        # Slow random walk: small independent nudge each reading, clamped
        # so it doesn't wander off unrealistically far from baseline.
        self._temp_drift = max(-3.0, min(3.0, self._temp_drift + self._rng.uniform(-0.15, 0.15)))
        self._humidity_drift = max(-8.0, min(8.0, self._humidity_drift + self._rng.uniform(-0.5, 0.5)))

        phase = self._daily_phase()
        temp_c = self.base_temp_c + 3.0 * math.sin(phase) + self._temp_drift
        humidity = self.base_humidity - 10.0 * math.sin(phase) + self._humidity_drift
        humidity = max(0.0, min(100.0, humidity))

        return round(temp_c, 2), round(humidity, 2)


class DevicePublisher:
    """Wraps one paho-mqtt client acting as one simulated device."""

    def __init__(self, device_id, sensor, topic_prefix, broker_host, broker_port, keepalive):
        self.device_id = device_id
        self.sensor = sensor
        self.topic_base = f"{topic_prefix}/devices/{device_id}"
        self.status_topic = f"{self.topic_base}/status"
        self.reading_topic = f"{self.topic_base}/reading"
        self.broker_host = broker_host
        self.broker_port = broker_port
        self.keepalive = keepalive

        self.client = mqtt.Client(client_id=f"sim-{device_id}", clean_session=True)

        # --- Last Will and Testament ---
        # If this client disconnects without calling disconnect() cleanly
        # (crash, killed process, network drop, Ctrl+C without cleanup),
        # the BROKER publishes this message on the device's behalf. This
        # is what lets a subscriber distinguish "device is fine but quiet"
        # from "device dropped off the network."
        self.client.will_set(self.status_topic, payload="offline", qos=1, retain=True)

        self.client.on_connect = self._on_connect

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            print(f"[{self.device_id}] connected to {self.broker_host}:{self.broker_port} "
                  f"(rc={rc})")
            # Announce ourselves online. Retained so a dashboard that
            # subscribes AFTER this point still immediately sees "online"
            # rather than waiting indefinitely for the next publish.
            client.publish(self.status_topic, "online", qos=1, retain=True)
        else:
            print(f"[{self.device_id}] connection failed (rc={rc})")

    def connect(self):
        self.client.connect(self.broker_host, self.broker_port, self.keepalive)
        self.client.loop_start()  # background network thread

    def publish_reading(self):
        temp_c, humidity = self.sensor.read()
        payload = {
            "device_id": self.device_id,
            "temp_c": temp_c,
            "humidity_pct": humidity,
            "ts": time.time(),
        }
        # QoS 1 ("at least once"): fine for a frequent telemetry stream
        # where an occasional duplicate reading is harmless, but where we
        # would still like delivery guaranteed over a flaky link.
        self.client.publish(self.reading_topic, json.dumps(payload), qos=1)
        print(f"[{self.device_id}] -> {self.reading_topic}  "
              f"temp={temp_c:5.2f}C  humidity={humidity:5.2f}%")

    def disconnect_cleanly(self):
        # A clean disconnect does NOT trigger the LWT -- only an unclean
        # drop does. Publish an explicit "offline" first so a graceful
        # shutdown is reflected immediately rather than only on timeout.
        self.client.publish(self.status_topic, "offline", qos=1, retain=True)
        self.client.loop_stop()
        self.client.disconnect()


def main():
    print("=" * 78)
    print("SIMULATED SENSOR PUBLISHER (MQTT, LWT, drifting readings)")
    print("=" * 78)
    print(f"Broker:       {BROKER_HOST}:{BROKER_PORT}")
    print(f"Topic prefix: {TOPIC_PREFIX}")
    print(f"Devices:      {[d['device_id'] for d in SIMULATED_DEVICES]}")
    print(f"Interval:     every {PUBLISH_INTERVAL_S}s per device")
    print("Press Ctrl+C to stop (watch the LWT-driven 'offline' status appear\n"
          "in 02_dashboard_subscriber.py).")
    print("=" * 78)

    publishers = []
    for i, cfg in enumerate(SIMULATED_DEVICES):
        sensor = SimulatedSensor(cfg["base_temp_c"], cfg["base_humidity"], seed=i)
        pub = DevicePublisher(
            device_id=cfg["device_id"],
            sensor=sensor,
            topic_prefix=TOPIC_PREFIX,
            broker_host=BROKER_HOST,
            broker_port=BROKER_PORT,
            keepalive=KEEPALIVE_S,
        )
        try:
            pub.connect()
        except Exception as exc:
            print(f"[{cfg['device_id']}] ERROR: could not connect to broker: {exc}")
            print("Is Mosquitto running on localhost:1883? See '00 README.md'.")
            return
        publishers.append(pub)

    time.sleep(1.0)  # give connections a moment to establish before first publish

    try:
        while True:
            for pub in publishers:
                pub.publish_reading()
            time.sleep(PUBLISH_INTERVAL_S)
    except KeyboardInterrupt:
        print("\nShutting down publishers (clean disconnect, LWT will NOT fire)...")
        for pub in publishers:
            pub.disconnect_cleanly()
        print("Done.")


if __name__ == "__main__":
    main()
