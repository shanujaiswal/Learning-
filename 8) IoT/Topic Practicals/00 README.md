# IoT Practicals

Hands-on scripts that pair with the theory notes in `7) IoT\Theory\`. No physical
hardware (Arduino / Raspberry Pi / ESP32) is required — everything here runs on a
normal PC. Where a real sensor or microcontroller would normally sit, a small
Python script plays the role of the "simulated hardware" and talks real MQTT,
exactly as a real device would.

## Chapter mapping

| Theory file | Practical(s) |
|---|---|
| `00 IoT Roadmap.md` | Orientation only — no dedicated script. |
| `01 IoT Fundamentals and Architecture.md` | `03_edge_vs_cloud_processing_demo.py` (edge vs. cloud trade-off) |
| `02 Microcontrollers and Single Board Computers.md` | `01_simulated_sensor_publisher.py` (stands in for the MCU/SBC + sensor) |
| `03 IoT Communication Protocols -- MQTT, CoAP and Wireless Standards.md` | `01_simulated_sensor_publisher.py`, `02_dashboard_subscriber.py` (pub/sub, topics, wildcards, LWT) |
| `04 IoT Cloud Platforms and Data Pipelines.md` | `02_dashboard_subscriber.py` (consumer/dashboard side of a data pipeline), `03_edge_vs_cloud_processing_demo.py` |
| `05 IoT Security Fundamentals.md` | `04_device_firmware_signing_demo.py` (firmware signing / secure OTA) |

## Setup

```bash
pip install paho-mqtt cryptography
```

That's the only dependency for all four scripts (`cryptography` is only needed by
script 4).

## MQTT broker: pick ONE of these two options

### Option A — Run a broker locally (recommended, private)

Install Mosquitto:

- Windows: download the installer from https://mosquitto.org/download/ and run it
  (it installs as a service listening on `localhost:1883` by default).
- Alternatively, if you have Docker: `docker run -it -p 1883:1883 eclipse-mosquitto`

Then in every script below, keep the default `BROKER_HOST = "localhost"`.

### Option B — Use a public test broker (quick, but NOT private)

If you don't want to install anything, set `BROKER_HOST = "test.mosquitto.org"`
(port `1883`) at the top of each script. This is a free broker anyone on the
internet can also publish/subscribe to.

**Important note — for learning only, not private/production data:** public test
brokers like `test.mosquitto.org` are shared, unauthenticated, and unencrypted.
Anyone can read your topic if they guess/know it, and messages can be lost or
delayed at any time. Use a random/unique topic prefix (the scripts default to
`study/iot/<random-suffix>` per run) so you don't collide with other learners,
and never publish real credentials, locations, or personal data to it. Treat it
purely as a scratchpad for this exercise, not as infrastructure.

## Running the demo (MQTT scripts)

1. Start a broker (Option A or B above).
2. In one terminal: `python "01_simulated_sensor_publisher.py"`
3. In another terminal: `python "02_dashboard_subscriber.py"`
4. Watch readings from the simulated sensor(s) appear live in the "dashboard".
5. Kill the publisher with Ctrl+C and notice the dashboard report the device as
   offline shortly after — that's the Last Will and Testament (LWT) message
   the broker sends on the publisher's behalf when its connection drops.

## File list

1. `00 README.md` — this file.
2. `01_simulated_sensor_publisher.py` — simulated temperature/humidity sensor, publishes over MQTT with LWT.
3. `02_dashboard_subscriber.py` — MQTT subscriber with `+` wildcard, live text dashboard of all simulated sensors.
4. `03_edge_vs_cloud_processing_demo.py` — edge-filtering vs. naive cloud-everything bandwidth comparison over a simulated day.
5. `04_device_firmware_signing_demo.py` — firmware signing/verification demo (and a failing check on tampered firmware).
