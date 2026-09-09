# Why Not Just Use HTTP

--> HTTP was designed for request/response between machines with generous power and bandwidth: every request carries verbose text headers, opens a TCP connection (plus a TLS handshake if secure), and assumes a client that's actively asking for something rather than a sensor that wants to push a reading whenever it has one. For a battery-powered device sending a few bytes every few minutes, the overhead of headers and repeated handshakes can dwarf the actual payload -- both in bandwidth (which costs battery to transmit, and money on metered links like cellular) and in the RAM needed to hold an HTTP client implementation. This is the core motivation for MQTT and CoAP: keep the framing overhead tiny and match the actual traffic pattern of IoT devices, which is usually many small, infrequent messages rather than large request/response exchanges.

# MQTT -- Publish/Subscribe Messaging

--> **MQTT (Message Queuing Telemetry Transport)** is a lightweight publish/subscribe protocol built on TCP. Three roles: **publishers** (devices sending data), **subscribers** (anything consuming it -- a dashboard, a cloud ingestion service, another device), and a **broker** that sits in the middle, receiving all published messages and forwarding them to whoever subscribed to the matching topic. Publishers and subscribers never talk to each other directly or even need to know about each other -- they're fully decoupled through the broker.

--> **Topics** are hierarchical strings like `home/livingroom/temperature` or `factory/line3/motor7/vibration`, used as the routing key. Subscribers can use wildcards: `+` matches exactly one level (`home/+/temperature`), `#` matches everything below a point (`home/#`).

--> **QoS (Quality of Service)** levels control delivery guarantees, trading reliability for overhead: **QoS 0** ("at most once") fires and forgets -- fine for frequent, individually-unimportant readings like a temperature stream. **QoS 1** ("at least once") guarantees delivery but may duplicate messages -- the receiver must tolerate duplicates. **QoS 2** ("exactly once") adds a four-way handshake to guarantee no duplicates -- most expensive, reserved for messages where duplication is actually harmful, like a billing event or a one-shot actuation command.

--> **Retained messages** and **Last Will and Testament (LWT)** are two MQTT features specific to IoT's flaky connectivity: a retained message is held by the broker and delivered immediately to any new subscriber (so a dashboard immediately sees the last known state, not silence until the next publish). An LWT is a message the device pre-registers with the broker to be published automatically if the device disconnects uncleanly -- effectively a dead-man's-switch for detecting offline devices.

```python
# Real paho-mqtt example: a sensor node publishing readings,
# and a subscriber consuming them.

import paho.mqtt.client as mqtt
import json, time, random

BROKER = "broker.local"
TOPIC = "home/livingroom/temperature"

def publisher():
    client = mqtt.Client(client_id="sensor-node-1")
    client.connect(BROKER, 1883, keepalive=60)
    client.will_set(f"{TOPIC}/status", payload="offline", retain=True)
    client.publish(f"{TOPIC}/status", "online", retain=True)

    while True:
        reading = {"tempC": round(20 + random.uniform(-2, 2), 1), "ts": time.time()}
        client.publish(TOPIC, json.dumps(reading), qos=1)
        time.sleep(30)

def on_message(client, userdata, msg):
    print(f"{msg.topic}: {msg.payload.decode()}")

def subscriber():
    client = mqtt.Client(client_id="dashboard-1")
    client.on_message = on_message
    client.connect(BROKER, 1883, keepalive=60)
    client.subscribe("home/+/temperature", qos=1)
    client.loop_forever()
```

# CoAP -- A Lighter, REST-Style Alternative

--> **CoAP (Constrained Application Protocol)** takes a different approach: instead of publish/subscribe, it mirrors REST/HTTP semantics (GET, POST, PUT, DELETE against a resource URI) but runs over **UDP** instead of TCP, with a binary header as small as 4 bytes instead of HTTP's verbose text headers. This makes it a natural fit when a device genuinely needs request/response semantics (query a specific device's current state on demand) but still can't afford TCP's connection setup or HTTP's overhead. CoAP adds its own lightweight reliability layer on top of UDP (message IDs and acknowledgements) since UDP itself gives no delivery guarantee -- and supports "observe," a subscribe-like extension for getting updates on a resource without repeated polling.

--> **MQTT vs CoAP in short**: choose MQTT when the pattern is many devices pushing data to interested consumers through a central point (telemetry, events); choose CoAP when the pattern is closer to "ask this specific device something and get an answer" (a direct query/command to one node), especially in extremely constrained mesh networks.

# Wireless Standards Compared

| Standard | Range | Power Use | Bandwidth | Typical Use |
|---|---|---|---|---|
| WiFi | ~50m indoor | High | High (Mbps+) | Video, high-rate sensor streams, always-powered devices |
| Bluetooth Low Energy (BLE) | ~10-30m | Very low | Low (~1 Mbps) | Wearables, phone-paired devices, short bursts |
| Zigbee | ~10-100m (mesh) | Low | Low (250 kbps) | Home automation, mesh sensor networks |
| LoRaWAN | 2-15km+ | Very low | Very low (kbps) | Agriculture, utility metering, sparse rural sensors |
| Cellular / NB-IoT | Cellular coverage area | Moderate | Low-moderate | Mobile assets, remote sites with no local infra |

--> The general pattern: range and power efficiency trade directly against bandwidth. WiFi gives you speed at the cost of power and range; LoRaWAN gives you kilometers of range on a battery for years, but only a few bytes per message and infrequently. Picking a wireless standard is really picking where on that trade-off curve a given device's use case sits -- a soil sensor in a field wants LoRaWAN's range and power efficiency; a smart speaker streaming audio needs WiFi's bandwidth and can afford to be plugged in.

# Deep Dive -- MQTT's Broker Is a Single Point of Failure (and Attack Surface)

--> Because every publisher and subscriber connects only to the broker, and never directly to each other, the broker becomes both a reliability chokepoint and, if left unsecured, a way to read or inject data across every device on the network at once -- subscribing to `#` on a misconfigured public broker exposes everything flowing through it. Production deployments always run the broker with TLS (port 8883) and per-client authentication, and frequently partition topics with ACLs so a compromised device can only publish/subscribe to its own namespace, not `#`. This connects directly to network segmentation ideas covered in Chapter 5 -- the broker is exactly the kind of central chokepoint that benefits from being on its own restricted network segment rather than reachable from the general LAN or internet.

--> Cross-reference: packet-level inspection of MQTT or CoAP traffic on the wire uses exactly the Wireshark techniques from `3) Security/1) Computer Networks/Theory/06 Packet Capture Fundamentals with Wireshark.md`, and scripting a rogue MQTT client or CoAP request for security testing builds on `3) Security/3) Python for Security`.
