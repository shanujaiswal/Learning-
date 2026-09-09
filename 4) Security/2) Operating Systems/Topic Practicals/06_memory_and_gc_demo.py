"""
06 - Memory and Garbage Collection Demo
==========================================
Theory chapter: "02 Memory Management and Virtual Memory.md"

This demonstrates memory management at the language-RUNTIME level (CPython),
which sits on top of the OS-level virtual memory concepts from the Theory
chapter: the interpreter requests pages of virtual memory from the OS and
then manages objects within that memory itself.

Covers:
  1. sys.getsizeof() -- how much memory individual Python objects occupy.
  2. gc module stats -- generations, thresholds, live object counts.
  3. A deliberate REFERENCE CYCLE (two objects pointing at each other) that
     plain reference counting can NEVER free on its own -- only Python's
     cyclic garbage collector (gc.collect()) can reclaim it. We show object
     counts before and after gc.collect() to prove the cycle was collected.
"""

import sys
import gc


def demo_getsizeof():
    print("--- sys.getsizeof() on various objects ---")
    samples = {
        "int 0": 0,
        "int 10**100": 10**100,
        "empty str": "",
        "short str": "hello",
        "long str": "x" * 1000,
        "empty list": [],
        "list of 1000 ints": list(range(1000)),
        "empty dict": {},
        "dict with 10 items": {i: i * i for i in range(10)},
        "tuple (1,2,3)": (1, 2, 3),
    }
    for label, obj in samples.items():
        print(f"  {label:<22}: {sys.getsizeof(obj):>8} bytes")


def demo_gc_stats():
    print("\n--- gc module stats ---")
    print(f"gc.isenabled(): {gc.isenabled()}")
    print(f"gc.get_threshold() (gen0, gen1, gen2 collection thresholds): {gc.get_threshold()}")
    print(f"gc.get_count() (current allocations per generation): {gc.get_count()}")
    stats = gc.get_stats()
    for i, gen_stats in enumerate(stats):
        print(f"  Generation {i} stats: {gen_stats}")


class Node:
    """A simple node that can point to another node -- used to build a
    deliberate reference cycle below."""

    def __init__(self, name):
        self.name = name
        self.partner = None

    def __repr__(self):
        return f"Node({self.name!r})"

    def __del__(self):
        # This will only run when the object is actually reclaimed, letting
        # us observe collection happening in real time.
        print(f"    __del__ called for {self.name} -- memory reclaimed")


def demo_reference_cycle():
    print("\n--- Deliberate reference cycle ---")

    # Disable automatic collection temporarily so we control exactly when
    # gc.collect() runs, making the before/after counts meaningful.
    was_enabled = gc.isenabled()
    gc.disable()

    print("Creating two Node objects that reference each other (a cycle)...")
    a = Node("A")
    b = Node("B")
    a.partner = b
    b.partner = a  # cycle: a -> b -> a

    before_count = len(gc.get_objects())
    print(f"Live tracked objects before dropping references: {before_count}")

    print("Dropping the local variables a, b (only the cycle references remain)...")
    del a
    del b
    # Note: with a normal (non-cyclic) reference, del-ing the last variable
    # would trigger __del__ immediately via refcounting. Here it will NOT --
    # each node's refcount is still 1 (held by its partner), so nothing is
    # printed yet even though nothing outside the cycle can reach them.

    after_del_count = len(gc.get_objects())
    print(f"Live tracked objects after dropping references (still alive due "
          f"to the cycle keeping refcounts > 0): {after_del_count}")

    print("Calling gc.collect() to run the cyclic garbage collector...")
    collected = gc.collect()
    print(f"gc.collect() reports {collected} unreachable objects collected.")

    after_gc_count = len(gc.get_objects())
    print(f"Live tracked objects after gc.collect(): {after_gc_count}")

    if was_enabled:
        gc.enable()


if __name__ == "__main__":
    demo_getsizeof()
    demo_gc_stats()
    demo_reference_cycle()

    print("\nSummary:")
    print("- sys.getsizeof() shows per-object memory cost at the runtime level.")
    print("- Plain reference counting frees objects the instant refcount hits 0.")
    print("- A reference cycle keeps refcount > 0 forever without a separate")
    print("  cyclic collector -- gc.collect() is what actually frees it, as")
    print("  proven by the __del__ calls appearing only after it runs.")
