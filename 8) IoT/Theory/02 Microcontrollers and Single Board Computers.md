# Microcontroller vs Single-Board Computer -- The Core Distinction

--> **Arduino (microcontroller)** -- no operating system. You write one program (a "sketch") that runs directly on the chip in an infinite loop, with no scheduler, no filesystem (beyond raw flash access), and no multitasking unless you build it yourself. Classic boards (Uno, Nano) use an 8-bit AVR chip at 16MHz with 2KB of RAM. This gets you deterministic, low-latency timing and very low power draw, at the cost of having to hand-manage everything.

--> **Raspberry Pi (single-board computer)** -- a real computer. It boots a full Linux distribution, has a filesystem, runs multiple processes concurrently, and you SSH into it like any Linux box. This buys you a full software stack (Python, databases, web servers, even a full MQTT broker) at the cost of higher power draw, slower boot time, and no hard real-time guarantees -- Linux's scheduler can always delay your process by milliseconds for something else.

--> **ESP32 / ESP8266 (microcontroller + radio)** -- same "no OS, one loop" model as Arduino, but with WiFi (both) and Bluetooth (ESP32 only) built into the chip itself, plus considerably more RAM and clock speed than a classic Arduino Uno. This makes it the default choice for a huge share of hobbyist and even production IoT devices today -- Arduino's ecosystem and IDE, with networking included on the chip.

--> **When to use which**: reach for an ESP32/Arduino when the job is "read a sensor and/or drive an actuator, deterministically, on minimal power" -- a soil sensor, a door lock controller, an LED strip. Reach for a Raspberry Pi when the job needs a real filesystem, a database, computer vision, or you want to run a full application stack (a local hub aggregating multiple sensor nodes, running Node-RED or Home Assistant) directly on the device.

# GPIO, Digital vs Analog I/O, and PWM

--> **GPIO (General Purpose Input/Output)** pins are the physical pins used to read sensors and drive actuators. Each pin can typically be configured as input or output in software.

--> **Digital I/O** reads or writes exactly two states, HIGH (logic 1, usually 3.3V or 5V) or LOW (logic 0, ~0V) -- a button press, a PIR motion sensor's output, turning an LED fully on or off.

--> **Analog input** reads a continuous voltage rather than just two states, via an onboard ADC (Analog-to-Digital Converter) -- a potentiometer, an LDR (light sensor), most temperature sensors' raw output. Classic Arduino boards expose dedicated `A0`-`A5` pins for this; the Raspberry Pi has no built-in ADC at all and needs an external ADC chip (e.g., MCP3008) to read analog signals.

--> **PWM (Pulse Width Modulation)** approximates an analog *output* on a pin that can only truly be HIGH or LOW, by switching rapidly between the two and varying the fraction of time spent HIGH (the duty cycle). This is how you dim an LED smoothly or control a DC motor's speed from a digital-only pin.

# Real Arduino/ESP32 Sketch -- Reading a Sensor, Driving an LED

```cpp
// Reads an analog temperature sensor on A0, turns on an LED (pin 9)
// via PWM brightness proportional to temperature, once per second.

const int SENSOR_PIN = A0;
const int LED_PIN = 9;
const float VOLTAGE_REF = 5.0;

void setup() {
  pinMode(LED_PIN, OUTPUT);
  Serial.begin(9600);
}

void loop() {
  int raw = analogRead(SENSOR_PIN);          // 0-1023 (10-bit ADC)
  float voltage = raw * (VOLTAGE_REF / 1023.0);
  float tempC = (voltage - 0.5) * 100.0;     // TMP36-style sensor

  int brightness = constrain(map(tempC, 15, 35, 0, 255), 0, 255);
  analogWrite(LED_PIN, brightness);          // PWM output

  Serial.print("Temp: ");
  Serial.print(tempC);
  Serial.println(" C");

  delay(1000);
}
```

--> Note the absence of any operating system call: `setup()` runs once at power-on, `loop()` runs forever, and `delay()` genuinely blocks the entire chip for that duration -- there is nothing else to preempt it.

# Real Python Example -- Raspberry Pi GPIO

```python
# Blinks an LED on GPIO pin 17 and reads a button on GPIO pin 27,
# using RPi.GPIO. Runs as a normal Linux process under an OS scheduler.

import RPi.GPIO as GPIO
import time

LED_PIN = 17
BUTTON_PIN = 27

GPIO.setmode(GPIO.BCM)
GPIO.setup(LED_PIN, GPIO.OUT)
GPIO.setup(BUTTON_PIN, GPIO.IN, pull_up_down=GPIO.PUD_UP)

try:
    while True:
        if GPIO.input(BUTTON_PIN) == GPIO.LOW:  # button pressed (active-low)
            GPIO.output(LED_PIN, GPIO.HIGH)
        else:
            GPIO.output(LED_PIN, GPIO.LOW)
        time.sleep(0.05)
except KeyboardInterrupt:
    GPIO.cleanup()
```

--> This looks similar to the Arduino sketch conceptually, but it's a genuinely different execution model -- this is a normal Python process that Linux can pause, swap, or kill, and `time.sleep()` yields the CPU rather than halting the whole system.

# Deep Dive -- Why "Just Use a Raspberry Pi for Everything" Is a Trap

--> A Raspberry Pi's flexibility hides a real cost at the device layer: it draws roughly 100-500x the current of a well-tuned deep-sleep microcontroller (hundreds of milliamps vs low microamps), takes tens of seconds to boot Linux from an SD card, and an abrupt power loss can corrupt its filesystem the way it never can corrupt an Arduino's flash-stored sketch. A battery-powered sensor node that needs to sleep for 99% of its life and wake briefly to take a reading is a job for a microcontroller with deep-sleep modes, not an SBC -- reserve the Raspberry Pi for the local hub/gateway role (aggregating several sensor nodes, running an MQTT broker, doing edge inference) where its OS and always-on power draw are already a given.

--> Cross-reference: once a device is powered and running Linux, applying general Python skills from `2) Full Stack/2) BackEnd/1) Python Notes` (and later, MQTT client code) works exactly as it would on any other Linux box -- the SBC path is where this vault's existing backend knowledge transfers most directly into IoT.
