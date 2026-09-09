"""Minimal ROS2-style pub/sub shim.

Real ROS2 (`rclpy`) is a full multi-gigabyte OS-level install (a whole ROS2
distro) that most learning machines don't have and that can't run inside a
plain Python environment. This module implements JUST the architectural
pattern every real ROS2 robotics stack is built on -- independent `Node`s
that talk ONLY through named topics, never through direct function calls --
as a small in-process message bus.

Every node class in this project (`nodes.py`) is written against the SAME
API real `rclpy.node.Node` exposes:
    self.create_publisher(msg_type, topic_name, queue_size)
    self.create_subscription(msg_type, topic_name, callback, queue_size)
    self.create_timer(period_seconds, callback)
    publisher.publish(msg)

Porting any node here onto a real ROS2 install later is a two-line change
in `main.py` (`import rclpy` instead of `import ros2_lite as rclpy`, and
`rclpy.spin(node)` instead of this module's cooperative `spin(...)`) --
no node's internal logic changes at all, because the code was written
against the topic/node abstraction from the start rather than against
direct Python function calls between modules. That portability is the
entire reason real robotics teams standardize on ROS2 in the first place.
"""

from collections import defaultdict

_TOPIC_SUBSCRIBERS = defaultdict(list)


class Publisher:
    def __init__(self, topic_name):
        self.topic_name = topic_name

    def publish(self, msg):
        for callback in _TOPIC_SUBSCRIBERS[self.topic_name]:
            callback(msg)


class Node:
    def __init__(self, node_name):
        self.node_name = node_name
        self._timers = []  # list of (period_s, callback)

    def create_publisher(self, msg_type, topic_name, queue_size=10):
        return Publisher(topic_name)

    def create_subscription(self, msg_type, topic_name, callback, queue_size=10):
        _TOPIC_SUBSCRIBERS[topic_name].append(callback)
        return callback

    def create_timer(self, period_seconds, callback):
        self._timers.append([period_seconds, callback, 0.0])

    def get_logger(self):
        return _Logger(self.node_name)


class _Logger:
    def __init__(self, node_name):
        self.node_name = node_name

    def info(self, message):
        print(f"[{self.node_name}] {message}")


def reset_bus():
    """Clears all topic subscriptions -- call before building a fresh set of
    nodes (e.g. between an exploration run and a fresh evaluation run) so
    callbacks from a previous run don't keep firing.
    """
    _TOPIC_SUBSCRIBERS.clear()


class SimClock:
    """Stand-in for ROS2's `/clock` -- simulated wall-clock time (seconds)
    and the fixed tick size, so every node can read "now" and "dt" the same
    way a real node reads `self.get_clock().now()`.
    """

    def __init__(self, dt_s=0.1):
        self.t = 0.0
        self.dt = dt_s


def spin(nodes, duration_s, clock):
    """Cooperative timer scheduler standing in for `rclpy.spin()` /
    ROS2 executors -- ticks every node's timers on their configured period
    for `duration_s` of simulated wall-clock time, advancing `clock` first
    each tick so callbacks can read the current simulated time. A real
    ROS2 executor does the same job (dispatching timer/subscription
    callbacks in time order) using the OS scheduler instead of this loop.
    """
    while clock.t < duration_s - 1e-9:
        clock.t += clock.dt
        for node in nodes:
            for timer in node._timers:
                period_s, callback, elapsed = timer
                elapsed += clock.dt
                if elapsed >= period_s:
                    elapsed = 0.0
                    callback()
                timer[2] = elapsed
