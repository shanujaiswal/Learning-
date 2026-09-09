"""
04 - Robot Operating System (ROS) Basics: Conceptual Pub/Sub Simulation
=========================================================================

Companion practical for Theory/04 Robot Operating System (ROS) Basics.md

IMPORTANT: This is NOT real ROS. ROS/ROS2 (rospy/rclpy, roscore/DDS
discovery, actual inter-process transport, message serialization, etc.)
is not installed here. This script is a minimal, pure-Python, in-process
simulation of the *concepts* the theory describes -- nodes, topics, the
publish/subscribe pattern, message queues with bounded size, and a
simple synchronous service call -- so you can see the pattern work
without installing ROS.

How this maps onto real ROS:

    This simulation          Real ROS2 (rclpy) equivalent
    ------------------------ --------------------------------------------
    Node class                rclpy.node.Node subclass
    Broker.publish(topic,msg) self.publisher_.publish(msg)  (via create_publisher)
    Broker.subscribe(topic,cb)self.create_subscription(MsgType, topic, cb, qos)
    queue_size on a topic     the trailing queue-depth arg to
                              create_publisher / create_subscription
    Service.call(request)     a synchronous rclpy service client call
    Broker.spin_once()        rclpy.spin_once(node) / rclpy.spin(node)

    Real ROS nodes are separate OS processes discovered over DDS with
    real network transport and message (de)serialization; here it's all
    one Python process with an in-memory dict-of-lists broker. The
    *pattern* -- decoupled publishers/subscribers that never reference
    each other directly, plus request/response services for one-off
    calls -- is the same, which is the point of this exercise.

Run:
    python 04_ros_basics_simulation.py
"""

from collections import deque, defaultdict


# ---------------------------------------------------------------------------
# Minimal pub/sub broker (stand-in for ROS's DDS-based topic transport)
# ---------------------------------------------------------------------------


class Broker:
    """In-process message broker: nodes publish/subscribe to named topics.

    Mirrors ROS's decoupling guarantee: a publisher never knows who (if
    anyone) is subscribed, and subscribers never reference the publisher.
    Each topic also has a bounded queue (mirrors ROS's queue_size), so a
    slow subscriber that never drains its queue drops the oldest messages
    -- the exact "message rate mismatch" bug class the theory calls out.
    """

    def __init__(self):
        self._subscribers = defaultdict(list)   # topic -> [callback, ...]
        self._queues = defaultdict(lambda: deque(maxlen=10))  # topic -> queue
        self._services = {}                      # service_name -> handler fn

    def publish(self, topic, message):
        self._queues[topic].append(message)
        for callback in self._subscribers[topic]:
            callback(message)

    def subscribe(self, topic, callback):
        self._subscribers[topic].append(callback)

    def advertise_service(self, service_name, handler):
        self._services[service_name] = handler

    def call_service(self, service_name, request):
        if service_name not in self._services:
            raise KeyError(f"No service advertised at '{service_name}'")
        return self._services[service_name](request)

    def queue_depth(self, topic):
        return len(self._queues[topic])


# ---------------------------------------------------------------------------
# Node base class (stand-in for rclpy.node.Node)
# ---------------------------------------------------------------------------


class Node:
    """Base class for a simulated ROS-like node.

    Real rclpy equivalent: `class MyNode(Node): def __init__(self): ...`
    with self.create_publisher / self.create_subscription /
    self.create_service calls inside __init__.
    """

    def __init__(self, name, broker):
        self.name = name
        self.broker = broker
        print(f"[node started] {self.name}")

    def create_publisher(self, topic):
        def publish(message):
            self.broker.publish(topic, message)
        return publish

    def create_subscription(self, topic, callback):
        self.broker.subscribe(topic, callback)

    def create_service(self, service_name, handler):
        self.broker.advertise_service(service_name, handler)

    def log(self, message):
        print(f"[{self.name}] {message}")


# ---------------------------------------------------------------------------
# Example nodes: a sensor publisher, a controller subscriber, a logger
# subscriber, and a "reset odometry" service
# ---------------------------------------------------------------------------


class DistanceSensorNode(Node):
    """Publishes simulated distance readings to the '/scan/front' topic,
    mirroring a real LIDAR/ultrasonic driver node publishing sensor_msgs.
    """

    def __init__(self, broker, readings):
        super().__init__("distance_sensor_node", broker)
        self._publish = self.create_publisher("/scan/front")
        self._readings = readings
        self.tick = 0

    def spin_once(self):
        if self.tick < len(self._readings):
            distance = self._readings[self.tick]
            self._publish({"tick": self.tick, "distance_m": distance})
            self.log(f"published distance={distance:.2f} m on /scan/front")
            self.tick += 1


class ControllerNode(Node):
    """Subscribes to '/scan/front' and issues a simple stop/go velocity
    command based on the latest reading -- mirroring a real obstacle-
    avoidance node subscribing to a sensor topic and publishing /cmd_vel.
    """

    STOP_THRESHOLD_M = 0.5

    def __init__(self, broker):
        super().__init__("controller_node", broker)
        self._publish_cmd = self.create_publisher("/cmd_vel")
        self.create_subscription("/scan/front", self._on_scan)
        self.last_command = None

    def _on_scan(self, message):
        distance = message["distance_m"]
        if distance < self.STOP_THRESHOLD_M:
            command = {"linear_x": 0.0, "reason": "obstacle too close"}
        else:
            command = {"linear_x": 0.5, "reason": "path clear"}
        self.last_command = command
        self._publish_cmd(command)
        self.log(f"received distance={distance:.2f} m -> "
                  f"cmd_vel.linear_x={command['linear_x']} ({command['reason']})")


class LoggerNode(Node):
    """Second, independent subscriber to the SAME '/scan/front' topic --
    demonstrates that adding a new subscriber never requires touching
    the publisher, exactly as the theory describes.
    """

    def __init__(self, broker):
        super().__init__("logger_node", broker)
        self.received = []
        self.create_subscription("/scan/front", self._on_scan)

    def _on_scan(self, message):
        self.received.append(message)


class OdometryServiceNode(Node):
    """Advertises a synchronous '/reset_odometry' service -- mirrors a
    ROS service (request/response, not continuous streaming).
    """

    def __init__(self, broker):
        super().__init__("odometry_service_node", broker)
        self.odometry_x = 12.7  # pretend accumulated odometry drift
        self.create_service("/reset_odometry", self._handle_reset)

    def _handle_reset(self, request):
        old_value = self.odometry_x
        self.odometry_x = 0.0
        self.log(f"service '/reset_odometry' called -> reset x from "
                  f"{old_value:.2f} to 0.00")
        return {"success": True, "previous_x": old_value}


# ---------------------------------------------------------------------------
# Demo
# ---------------------------------------------------------------------------


def main():
    print("=" * 70)
    print("ROS BASICS: PUB/SUB + SERVICE SIMULATION (NOT REAL ROS)")
    print("=" * 70)
    print("This is a conceptual, in-process stand-in for rclpy/rospy pub/sub.\n")

    broker = Broker()

    # Simulated distance readings: robot approaches, then hits, an obstacle
    readings = [2.0, 1.5, 1.1, 0.8, 0.6, 0.45, 0.3, 0.9, 1.8]

    sensor_node = DistanceSensorNode(broker, readings)
    controller_node = ControllerNode(broker)
    logger_node = LoggerNode(broker)
    odom_service_node = OdometryServiceNode(broker)

    print("\n--- Spinning nodes (simulated timer ticks) ---")
    for _ in range(len(readings)):
        sensor_node.spin_once()

    print(f"\nLogger node independently received {len(logger_node.received)} "
          f"messages on /scan/front without the sensor node knowing it exists.")
    assert len(logger_node.received) == len(readings), \
        "Logger subscriber should have received every published message"
    print("Check: pub/sub decoupling confirmed -- one publish reached two "
          "independent subscribers.")

    stop_events = [m for m in logger_node.received
                   if m["distance_m"] < ControllerNode.STOP_THRESHOLD_M]
    print(f"\n{len(stop_events)} reading(s) were below the "
          f"{ControllerNode.STOP_THRESHOLD_M} m stop threshold: "
          f"{[round(m['distance_m'], 2) for m in stop_events]}")
    assert controller_node.last_command["linear_x"] == 0.5, \
        "Final reading (1.8m, clear) should leave the robot commanded to move"
    print("Check: controller correctly issued stop commands near the obstacle "
          "and resumed once clear.")

    print("\n--- Calling the '/reset_odometry' service (request/response) ---")
    response = broker.call_service("/reset_odometry", request={})
    print(f"Service response: {response}")
    assert response["success"] and odom_service_node.odometry_x == 0.0
    print("Check: service call completed synchronously with a single "
          "request/response, unlike the fire-and-forget topics above.")

    print(f"\nFinal /scan/front queue depth (maxlen=10): "
          f"{broker.queue_depth('/scan/front')} of {len(readings)} messages published "
          f"({'no drops, still under cap' if broker.queue_depth('/scan/front') == len(readings) else 'oldest messages dropped once cap hit'})")


if __name__ == "__main__":
    main()
