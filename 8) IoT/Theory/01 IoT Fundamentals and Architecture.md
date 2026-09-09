# The Four-Layer IoT Architecture

--> Nearly every real IoT system, regardless of vendor, decomposes into the same four layers. Learning to place any component into one of these layers makes unfamiliar IoT systems easy to reason about quickly.

--> **Device / Perception Layer** -- the physical hardware: sensors (temperature, humidity, accelerometer, GPS), actuators (relays, motors, valves), and the microcontroller or SBC running them (covered in depth in Chapter 2). This layer's job is purely to sense the physical world and/or act on it.

--> **Connectivity / Network Layer** -- gets data from the device to somewhere useful and carries commands back down. This is WiFi, Bluetooth Low Energy, Zigbee, LoRaWAN, or cellular (Chapter 3), plus the lightweight application protocols (MQTT, CoAP) layered on top of them.

--> **Cloud / Platform Layer** -- ingests telemetry at scale, stores it, manages device identity and state (device shadows), and exposes it to applications. AWS IoT Core, Azure IoT Hub, and Google Cloud IoT Core are the major examples (Chapter 4).

--> **Application Layer** -- the dashboards, mobile apps, alerting rules, and analytics/ML models that consume the data and give it meaning to a human or another system. This is where the "value" of IoT is realized -- everything below it exists to get clean data here reliably.

# Resource Constraints -- Why IoT Hardware Is Different

--> Most software engineering (including most of this vault's backend and cloud content) implicitly assumes near-unlimited RAM, disk, and CPU, and a wall-socket power supply. IoT devices routinely violate every one of those assumptions:

--> **Power** -- a battery-powered sensor node may need to run for 5-10 years on a single coin-cell battery. Radio transmission is by far the most power-hungry operation a device performs, which is the single biggest reason lightweight protocols (Chapter 3) and infrequent transmission (batching, sleep cycles) exist.

--> **Memory** -- a typical microcontroller has 2KB-520KB of RAM, not gigabytes. A full TCP/IP + TLS stack, a JSON parser, and application logic all have to fit and run concurrently in that budget -- there is no virtual memory or swap to fall back on.

--> **Compute** -- clock speeds of 8-240MHz with no floating-point unit on cheaper chips means expensive operations (crypto, floating-point math, JSON serialization) are deliberately avoided or done in fixed-point arithmetic.

--> **Connectivity** -- unlike a server with a guaranteed wired uplink, an IoT device may be offline for hours (dead zone, moved out of range, power-saved radio) and must tolerate that gracefully rather than crash or lose data.

# Edge Computing vs Cloud Computing for IoT

--> **Cloud-centric**: every reading is sent to the cloud, and all logic (thresholds, ML inference, alerting) runs there. Simple to build and centrally manageable, but has real costs at IoT scale:

--> ==> **Latency** -- a safety-critical actuation (e.g., shutting a valve when pressure spikes) round-tripping to a data center and back may be too slow for the physical process it's controlling.

--> ==> **Bandwidth cost** -- thousands of devices each streaming raw sensor data continuously adds up fast, especially over metered cellular/LoRaWAN links where every byte costs money and battery.

--> ==> **Availability** -- if the device's internet link drops, cloud-only logic means the device does nothing useful until it reconnects, even for decisions it's fully capable of making locally.

--> **Edge computing** pushes some computation onto the device itself or a nearby gateway: filtering noise, computing running averages, detecting threshold crossings, and only sending the cloud what matters (an event, a daily aggregate) rather than every raw reading. Modern approaches even run small ML models directly on-device (TinyML) for tasks like keyword spotting or anomaly detection.

--> In practice almost all production IoT systems are a hybrid: time-critical or bandwidth-expensive decisions happen at the edge; data intended for cross-device analytics, long-term storage, dashboards, and model training goes to the cloud. Getting that split right is one of the central design decisions in any IoT architecture.

# Deep Dive -- The "Just Buffer It and Retry" Trap

--> A common mistake when porting cloud-service thinking to IoT is assuming a device can simply buffer failed transmissions in memory and retry later, the way a backend service retries a failed HTTP call. On a device with a few KB of free RAM, an extended outage (a sensor in a basement losing WiFi for a day) fills that buffer in minutes, forcing a choice between dropping data and crashing. Real designs handle this explicitly: write to flash/SD in a ring-buffer format that survives power loss and reboots, cap the buffer with a defined data-loss policy (drop oldest vs drop newest), and resume publishing with either the buffered backlog compressed/summarized or discarded past a defined age. This is a case where a decision that's an afterthought in server-side design (what happens if we can't send this message right now?) has to be a first-class part of the device firmware.

--> Cross-reference: the network layer's behavior under loss (retransmission, timeouts) builds directly on TCP concepts from `3) Security/1) Computer Networks` -- IoT protocols mostly just adjust those trade-offs for a much smaller, less reliable pipe.
