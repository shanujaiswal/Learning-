"""
08_heaps_priority_queues_hashing.py

Implements and tests:
    1. A MinHeap from scratch (push/pop/peek via sift-up/sift-down)
    2. Using heapq for a "top K frequent elements" priority-queue pattern
    3. A hash table from scratch with chaining, to show collision handling directly

Covers Theory chapter:
    2) DSA/Theory/08 Heaps Priority Queues and Hashing.md

Run:  python 08_heaps_priority_queues_hashing.py
"""

import heapq
from collections import Counter


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


# ---------------------------------------------------------------------------
# 1) MinHeap from scratch
# ---------------------------------------------------------------------------

class MinHeap:
    def __init__(self):
        self.heap = []

    def push(self, value):
        self.heap.append(value)
        self._sift_up(len(self.heap) - 1)

    def _sift_up(self, i):
        parent = (i - 1) // 2
        while i > 0 and self.heap[i] < self.heap[parent]:
            self.heap[i], self.heap[parent] = self.heap[parent], self.heap[i]
            i, parent = parent, (parent - 1) // 2

    def pop(self):
        self.heap[0], self.heap[-1] = self.heap[-1], self.heap[0]
        min_val = self.heap.pop()
        if self.heap:
            self._sift_down(0)
        return min_val

    def peek(self):
        return self.heap[0] if self.heap else None

    def _sift_down(self, i):
        n = len(self.heap)
        while True:
            smallest, left, right = i, 2 * i + 1, 2 * i + 2
            if left < n and self.heap[left] < self.heap[smallest]:
                smallest = left
            if right < n and self.heap[right] < self.heap[smallest]:
                smallest = right
            if smallest == i:
                break
            self.heap[i], self.heap[smallest] = self.heap[smallest], self.heap[i]
            i = smallest


def demo_min_heap_from_scratch() -> None:
    print_section("1) MinHeap from scratch -- push/pop always in sorted order")
    heap = MinHeap()
    values = [5, 2, 8, 1, 9, 3]
    for v in values:
        heap.push(v)
    print(f"Pushed: {values}")

    popped_order = []
    while heap.heap:
        popped_order.append(heap.pop())
    print(f"Popped order (should be fully sorted): {popped_order}")
    assert popped_order == sorted(values)
    print("Assertion passed -- popping a min-heap repeatedly yields values in sorted order.")


# ---------------------------------------------------------------------------
# 2) heapq for "top K frequent elements"
# ---------------------------------------------------------------------------

def top_k_frequent(nums, k):
    counts = Counter(nums)
    return heapq.nlargest(k, counts.keys(), key=counts.get)


def demo_top_k_frequent() -> None:
    print_section("2) Top-K frequent elements using heapq.nlargest")
    nums = [1, 1, 1, 2, 2, 3, 4, 4, 4, 4, 5]
    k = 2
    result = top_k_frequent(nums, k)
    print(f"nums={nums}, k={k} -> top {k} most frequent: {result}")
    assert set(result) == {4, 1}    # 4 appears 4x, 1 appears 3x -- the two most frequent
    print("Assertion passed.")


# ---------------------------------------------------------------------------
# 3) Hash table from scratch, with chaining -- shows collisions directly
# ---------------------------------------------------------------------------

class SimpleHashTable:
    def __init__(self, size=8):
        self.size = size
        self.buckets = [[] for _ in range(size)]      # chaining -- each bucket is a list of (key, value) pairs

    def _hash(self, key):
        return hash(key) % self.size

    def put(self, key, value):
        index = self._hash(key)
        bucket = self.buckets[index]
        for i, (k, _) in enumerate(bucket):
            if k == key:
                bucket[i] = (key, value)       # update existing key
                return
        bucket.append((key, value))              # new key -- append to this bucket's chain

    def get(self, key):
        index = self._hash(key)
        for k, v in self.buckets[index]:
            if k == key:
                return v
        raise KeyError(key)

    def bucket_sizes(self):
        return [len(bucket) for bucket in self.buckets]


def demo_hash_table_with_collisions() -> None:
    print_section("3) Hash table from scratch with chaining -- observing real collisions")
    table = SimpleHashTable(size=4)          # deliberately small to force visible collisions
    data = {"apple": 1, "banana": 2, "cherry": 3, "date": 4, "elderberry": 5}
    for k, v in data.items():
        table.put(k, v)

    print(f"Inserted keys: {list(data.keys())}")
    print(f"Bucket sizes (size=4 table, {len(data)} keys -- collisions guaranteed): {table.bucket_sizes()}")

    for k, v in data.items():
        retrieved = table.get(k)
        assert retrieved == v
    print("All keys retrieved correctly despite collisions -- chaining resolved every collision correctly.")


if __name__ == "__main__":
    demo_min_heap_from_scratch()
    demo_top_k_frequent()
    demo_hash_table_with_collisions()
    print("\nAll Heaps/Priority-Queue/Hashing demos completed.")
