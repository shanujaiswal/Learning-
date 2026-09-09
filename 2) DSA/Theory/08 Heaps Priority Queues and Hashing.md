# Heaps -- Fast Access to the Min or Max

--> A heap is a specialized tree-shaped structure satisfying the HEAP PROPERTY -- in a MIN-heap, every parent is smaller than or equal to both its children (so the smallest element is always at the root); in a MAX-heap, every parent is larger than or equal to its children.
--> Unlike a BST (covered in the Trees file), a heap makes NO guarantee about ordering between siblings or across the whole structure -- it only guarantees the parent-child relationship, which is exactly why a heap CANNOT be searched efficiently for an arbitrary value, but CAN give instant access to the min/max.

```text
Min-Heap:              Note: this is NOT a valid BST -- 8 and 15 are siblings with no ordering
        4                     requirement between them, only that both are >= their parent 4.
       / \
      8   15
     / \
    20  30
```

# Array Representation -- No Pointers Needed

--> A heap is almost always stored as a plain ARRAY, not as a linked node structure -- the parent/child relationships are calculated purely from index arithmetic, since a heap is always kept "complete" (every level full except possibly the last, filled left to right).

```text
Array:  [4, 8, 15, 20, 30]
Index:   0  1   2   3   4

For any index i:
  left child index  = 2*i + 1
  right child index = 2*i + 2
  parent index       = (i - 1) // 2
```

```python
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
        self._sift_down(0)
        return min_val

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
```

--> **Push (insert)** -- add the new value at the end of the array, then "sift up" (repeatedly swap with its parent while it's smaller) until the heap property is restored -- `O(log n)`, since it moves up at most the height of the tree.
--> **Pop (extract min/max)** -- swap the root with the LAST element, remove and return the old root, then "sift down" (repeatedly swap with its smaller child) from the new root until the heap property is restored -- also `O(log n)`.
--> **Peek** -- just read index 0 -- `O(1)`, since the min/max is always guaranteed to be at the root by the heap property.

```python
import heapq
min_heap = [5, 2, 8, 1]
heapq.heapify(min_heap)         # O(n) -- rearranges an existing list in-place into valid heap order
heapq.heappush(min_heap, 3)      # O(log n)
smallest = heapq.heappop(min_heap)  # O(log n)
```

--> Python's `heapq` module implements only a MIN-heap directly -- a max-heap is commonly simulated by pushing NEGATED values (`heapq.heappush(heap, -value)`) and negating again on pop.

# Priority Queues -- The Heap's Primary Use Case

--> A priority queue is an ABSTRACT concept -- "give me the highest-priority item next," regardless of insertion order -- a heap is simply the standard, efficient CONCRETE data structure used to implement one, in exactly the same way a hash table is the standard implementation of the abstract "map/dictionary" concept.
--> **Canonical use cases** -- Dijkstra's shortest-path algorithm and Prim's MST algorithm (both covered in the Graphs file, both repeatedly need "the next closest/cheapest unvisited node"), task schedulers (always run the highest-priority pending task next), and the "top K elements" pattern below.

```python
def top_k_frequent(nums, k):
    from collections import Counter
    import heapq
    counts = Counter(nums)
    return heapq.nlargest(k, counts.keys(), key=counts.get)   # internally uses a heap of size k, O(n log k)
```

--> **Why a heap beats fully sorting for "top K"** -- fully sorting all `n` elements to then take the top `k` costs `O(n log n)`; maintaining a heap of size `k` while scanning through `n` elements costs only `O(n log k)` -- a real, meaningful difference when `k` is small relative to `n` (finding the top 10 out of a million records), since you're paying the log-cost of a small heap rather than the full dataset.

# Hashing -- O(1) Average-Case Lookup

--> A hash table maps KEYS to VALUES by running each key through a HASH FUNCTION that produces an array index, then storing the value at that index -- this is what gives a well-implemented hash table its `O(1)` AVERAGE-case lookup, insert, and delete, a fundamentally different approach from a tree's comparison-based `O(log n)`.

```text
hash("apple")  = 3817... --> % table_size (say, 10) --> index 7
hash("banana") = 2904... --> % table_size (10)      --> index 4

Table: [ _, _, _, _, "banana", _, _, "apple", _, _ ]
                        index 4                index 7
```

```python
d = {}
d["apple"] = 10      # O(1) average -- hash("apple") computes the index directly, no searching
d["banana"] = 20
value = d["apple"]    # O(1) average -- same direct computation to find it again
```

# Hash Collisions

--> Two different keys can hash to the SAME index (a "collision") -- since the hash function's output space is typically much smaller than all possible key values, collisions are mathematically inevitable, not a bug or an edge case to be avoided entirely.

--> **Chaining** -- each array slot holds a small LIST (or linked list) of all key-value pairs that hashed to that index -- a lookup hashes to the right slot, then does a short linear scan through that slot's list to find the exact key.
--> **Open addressing** -- instead of a list per slot, a collision causes the algorithm to PROBE for the next available slot (linear probing checks the next slot, quadratic probing jumps by increasing steps, double hashing uses a second hash function to decide the jump) -- the value is stored in whichever open slot is found.

```text
Chaining:                                Open Addressing (linear probing):
index 4: [("banana", 20)]                index 4: ("banana", 20)
index 7: [("apple", 10), ("grape", 5)]    index 7: ("apple", 10)
         ^-- both hashed to 7,            index 8: ("grape", 5)
             stored together in a list        ^-- "grape" also hashed to 7, but 7 was taken,
                                                    so it probed forward to the next open slot
```

--> **Why collisions degrade performance if too frequent** -- with enough collisions, chained lists grow long (turning lookups into `O(n)` linear scans within a slot) or open-addressing probing chains grow long (many wasted probe attempts) -- this is exactly why the LOAD FACTOR (number of stored items divided by table size) is monitored, and the table is automatically RESIZED (a new, larger array allocated, every existing item re-hashed into it) once the load factor crosses a threshold (commonly around 0.7), keeping average performance close to `O(1)` -- directly connecting to the amortized-cost dynamic array resizing deep dive in the Big-O file, since hash table resizing follows the exact same amortized-cost logic.

# What Makes a Good Hash Function

--> **Deterministic** -- the same key must always produce the same hash, every time, or lookups would never find previously-stored data.
--> **Uniform distribution** -- keys should spread out roughly EVENLY across all possible index slots -- a hash function that clusters many different keys onto the same few slots causes excessive collisions regardless of how large the table is.
--> **Fast to compute** -- since it runs on every single insert/lookup/delete, an expensive hash function directly slows down every operation, undermining the whole point of near-`O(1)` access.
--> **Practical guidance** -- in real code, use the language's BUILT-IN hashing (Python's `dict`, `hash()`; Java's `HashMap`; JavaScript's `Map`/plain objects) rather than writing a custom hash function -- these are heavily engineered and tested against exactly the uniformity/collision/speed concerns above; a hand-rolled hash function is a common, easy-to-get-subtly-wrong source of real performance bugs.

# Deep Dive -- Why Hash Table Lookup Is "Average-Case" O(1), Not Guaranteed

--> The `O(1)` claim assumes a reasonably uniform hash function and a load factor kept under control by resizing -- in the theoretical WORST case (every single key colliding into the same slot, whether through bad luck or an adversarially chosen input designed to exploit a known hash function), a hash table degrades to `O(n)` per operation, no better than a plain unsorted list.
--> This worst case is precisely why some languages' hash table implementations use RANDOMIZED hash seeding (a different, randomly-chosen seed value each time the program runs) -- specifically to prevent an attacker who knows the hash algorithm from crafting inputs guaranteed to collide and deliberately degrade a hash table's performance in a public-facing application (a real, documented denial-of-service technique against naive hash implementations).
