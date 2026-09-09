# B-Trees and B+ Trees

--> A B-tree is a self-balancing tree (generalizing the balanced trees covered in the Trees file) where each node holds MULTIPLE keys and MULTIPLE children, not just two -- designed specifically around DISK-based storage, where reading a single disk block is slow but reading many keys from that same block once it's loaded is nearly free.
--> **Why a wide branching factor matters for disk-backed structures** -- a binary tree storing a million keys needs roughly `log2(1,000,000) ≈ 20` levels, each level a separate disk read; a B-tree with a branching factor of, say, 100 needs only `log100(1,000,000) ≈ 3` levels -- since each disk read is the expensive part (not the in-memory comparisons within a loaded node), minimizing the NUMBER OF LEVELS matters far more than minimizing comparisons, which is the opposite trade-off a purely in-memory structure like a BST would optimize for.

```text
A B-tree node (order 4, meaning up to 3 keys, up to 4 children):

              [ 10 | 20 | 35 ]
             /     |     |    \
      (<10)     (10-20) (20-35) (>35)
```

--> **B-tree** -- data (the actual stored records, or pointers to them) lives in BOTH internal nodes and leaf nodes -- a lookup can terminate as soon as the key is found at any level.
--> **B+ tree** -- ALL actual data lives only in the LEAF nodes; internal nodes store only keys used purely for ROUTING the search down to the right leaf -- and critically, the leaves are additionally linked together in a chain, so a range scan (give me everything between key A and key B) is a simple leaf-to-leaf walk once the starting leaf is found, rather than requiring repeated tree descents.
--> **Why B+ trees are the standard choice for database indexes** -- range queries (`WHERE age BETWEEN 20 AND 30`, directly connecting to the Database indexing notes) are extremely common in practice, and the B+ tree's linked-leaf structure answers them far more directly than a plain B-tree, which would require re-descending the tree repeatedly or an in-order traversal that keeps jumping between internal and leaf nodes.

# Fibonacci Heap (Conceptual)

--> A Fibonacci heap is a priority queue (same abstract role as the binary heap in the Heaps file) built from a loose collection of trees rather than one strict, complete tree -- it deliberately allows the structure to become "messy" after operations, only cleaning up lazily when actually necessary, in exchange for better amortized time bounds on specific operations.
--> **The specific improvement over a binary heap** -- a binary heap's `decrease-key` operation (lower an existing element's priority) costs `O(log n)` (it must sift up from wherever that element sits); a Fibonacci heap achieves `decrease-key` in `O(1)` AMORTIZED (directly connecting to the Big-O file's amortized analysis deep dive), by simply cutting the node out of its current tree and splicing it in as a new root, deferring the cleanup of the resulting mess to later operations.
--> **Why this matters specifically for Dijkstra's and Prim's** -- both algorithms (Graphs file) repeatedly call `decrease-key` as shorter paths/cheaper edges are discovered -- using a Fibonacci heap improves Dijkstra's theoretical bound from `O(E log V)` (binary heap) to `O(E + V log V)`, a real asymptotic improvement when `E` is large relative to `V`.
--> **Why it's rarely used in practice despite the better bound** -- the constant-factor overhead and implementation complexity of a Fibonacci heap are large enough that, for most REAL input sizes, a simple binary heap's `O(log n)` operations run faster in wall-clock time -- a recurring theme where a better asymptotic bound doesn't automatically translate into a better practical choice, which is why most production Dijkstra's implementations (including the one in the Graphs file) simply use `heapq`, a binary heap.

# Skip Lists

--> A skip list is a linked list (Linked Lists file) augmented with multiple "levels" of shortcut pointers, each level skipping over an exponentially larger number of elements than the one below it -- giving `O(log n)` expected search, insert, and delete, entirely without the rebalancing logic a balanced BST needs.

```text
Level 2:  1 --------------------> 9 ------------------> 21
Level 1:  1 --------> 5 --------> 9 --------> 15 -------> 21
Level 0:  1 -> 3 -> 5 -> 7 -> 9 -> 11 -> 15 -> 18 -> 21     (the full, ordered list)
```

--> **How the levels are built -- randomization instead of strict rebalancing** -- when inserting a new node, a coin-flip-like random process decides how many levels that node participates in (each additional level roughly 50% as likely as the one before) -- no explicit rotation or rebalancing step is ever needed, unlike an AVL or Red-Black tree (Trees file), because the RANDOMNESS itself statistically guarantees the structure stays roughly balanced across many insertions.
--> **Why `O(log n)` is only "expected," not guaranteed** -- an unlucky sequence of coin flips could theoretically produce a badly skewed skip list (in the same way an unlucky hash function could degrade a hash table, Heaps and Hashing file) -- but this is vanishingly unlikely in practice, and the expected-case guarantee is considered good enough that skip lists are used in real production systems (Redis's sorted sets, for one).
--> **Skip list vs balanced BST** -- both give `O(log n)` typical operations; skip lists are simpler to implement correctly (no rotation logic) and support easy lock-free concurrent implementations, which is precisely why they're favored in some concurrent/distributed systems despite a balanced BST having a tighter WORST-case guarantee.

# Treap -- A Randomized Balanced BST

--> A treap combines a binary search TREE (ordered by key, Trees file) with a HEAP (ordered by a separate, randomly-assigned priority per node, Heaps file) -- hence the name -- and uses the RANDOM priorities specifically to keep the tree balanced with high probability, without any explicit rotation-balancing rules like AVL/Red-Black trees need.

```python
import random

class TreapNode:
    def __init__(self, key):
        self.key = key
        self.priority = random.random()   # random priority -- this is what keeps the tree balanced
        self.left = None
        self.right = None

def rotate_right(node):
    left = node.left
    node.left = left.right
    left.right = node
    return left

def rotate_left(node):
    right = node.right
    node.right = right.left
    right.left = node
    return right

def treap_insert(node, key):
    if node is None:
        return TreapNode(key)
    if key < node.key:
        node.left = treap_insert(node.left, key)
        if node.left.priority > node.priority:      # heap property violated -- fix with a rotation
            node = rotate_right(node)
    else:
        node.right = treap_insert(node.right, key)
        if node.right.priority > node.priority:
            node = rotate_left(node)
    return node
```

--> **Why random priorities produce balance** -- a treap with `n` distinct random priorities is structurally EQUIVALENT to a BST built by inserting keys in random order (a well-known result to give `O(log n)` expected height) -- the priorities effectively simulate "what if we'd gotten lucky and inserted in a good order," regardless of the actual insertion order used.
--> **Treap vs AVL/Red-Black trees** -- same asymptotic guarantees on average, but a treap's insert/delete logic is noticeably simpler to implement correctly (no case-by-case rotation rules to memorize, just "rotate up while priority is violated") -- the trade-off, as with skip lists, is an expected-case rather than a worst-case-guaranteed bound.

# Interval Trees

--> An interval tree stores a collection of INTERVALS (ranges, e.g. `[15, 20]`, `[5, 11]`) and efficiently answers "which stored intervals overlap this query point or range" -- built as a BST ordered by each interval's start point, with each node additionally storing the MAXIMUM end-point across its entire subtree.

```python
class IntervalNode:
    def __init__(self, interval):
        self.interval = interval          # (start, end)
        self.max_end = interval[1]
        self.left = None
        self.right = None

def interval_insert(node, interval):
    if node is None:
        return IntervalNode(interval)
    if interval[0] < node.interval[0]:
        node.left = interval_insert(node.left, interval)
    else:
        node.right = interval_insert(node.right, interval)
    node.max_end = max(node.max_end, interval[1])
    return node

def search_overlap(node, query):        # query: (start, end) -- find any one overlapping interval
    if node is None:
        return None
    if node.interval[0] <= query[1] and query[0] <= node.interval[1]:
        return node.interval
    if node.left and node.left.max_end >= query[0]:
        return search_overlap(node.left, query)
    return search_overlap(node.right, query)
```

--> **Why the `max_end` field is the key trick** -- it lets the search PRUNE entire subtrees -- if the left subtree's maximum end-point is still less than the query's start, NOTHING in that whole subtree could possibly overlap, so it's skipped entirely without visiting a single node inside it, giving `O(log n + k)` (where `k` is the number of overlaps found) instead of checking every stored interval one by one.
--> **Canonical real-world use cases** -- calendar/meeting-room scheduling conflict detection, genomic feature overlap queries, and computational geometry algorithms that need repeated "which shapes overlap this region" queries.

# Sparse Tables -- Static Range Queries

--> A sparse table answers range queries (min, max, GCD -- specifically for operations that are IDEMPOTENT, i.e. combining an overlapping range with itself doesn't change the answer) in `O(1)` per query after an `O(n log n)` preprocessing step -- but ONLY on a STATIC array (no updates allowed afterward), which is precisely the trade-off that lets it beat a segment tree's `O(log n)` per query (Tries and Advanced Data Structures file).

```python
import math

def build_sparse_table(arr):
    n = len(arr)
    k = math.floor(math.log2(n)) + 1
    table = [[0] * k for _ in range(n)]
    for i in range(n):
        table[i][0] = arr[i]                # table[i][j] = min of the range starting at i, length 2^j
    for j in range(1, k):
        for i in range(n - (1 << j) + 1):
            table[i][j] = min(table[i][j - 1], table[i + (1 << (j - 1))][j - 1])
    return table

def query_min(table, log_table, left, right):     # inclusive range [left, right]
    length = right - left + 1
    j = log_table[length]
    return min(table[left][j], table[right - (1 << j) + 1][j])
```

--> **Why `O(1)` per query is possible here but not for a segment tree** -- any range can be covered by exactly TWO precomputed power-of-two-length blocks that OVERLAP each other (rather than needing a non-overlapping partition, which would require summing multiple pieces) -- overlap is only harmless for idempotent operations like min/max/GCD/AND/OR, which is exactly why sparse tables don't work for SUM queries (double-counting the overlapping region would give a wrong total) where a segment tree or Fenwick tree remains necessary.
--> **When to reach for a sparse table over a segment tree** -- specifically when the array is fixed once and queried many times afterward with no updates ever needed -- the `O(1)` query is a genuine win over `O(log n)`, but the inability to update at all makes it useless the moment the array needs to change.

# Patricia Tries / Compressed Tries

--> A plain trie (Tries and Advanced Data Structures file) creates one node per CHARACTER, which wastes significant space on long chains of single-child nodes (e.g. storing just one long word with no shared prefixes creates a full chain of single-character nodes) -- a Patricia trie (also called a radix tree) COMPRESSES any chain of single-child nodes into one node labeled with the whole shared substring, eliminating that waste.

```text
Plain trie for "test" (no siblings anywhere):    Patricia trie for the same:
    t                                                 "test"
    |                                                    *
    e
    |
    s
    |
    t
    *
```

--> **Why compression specifically helps sparse key sets** -- a plain trie's per-character branching only pays off when there ARE branches to make (many words sharing then diverging from a prefix) -- for long stretches with no actual choice to represent (only one possible next character), a Patricia trie collapses that entire non-branching stretch into a single edge, cutting node count (and therefore memory and traversal steps) dramatically.
--> **Canonical real-world use case** -- IP routing tables (longest-prefix matching over sparse, often long bit-strings, mentioned as a trie use case in the Tries file) are frequently implemented as Patricia tries specifically because IP prefixes rarely branch densely, making the compression genuinely pay off.

# K-D Trees (Conceptual)

--> A k-d tree (k-dimensional tree) generalizes a BST (Trees file) to MULTIPLE dimensions -- instead of splitting purely by "less than or greater than one key," it splits space by alternating which DIMENSION is used for comparison at each depth level (e.g. split by x-coordinate at the root, by y-coordinate at the next level down, back to x the level after, and so on for 2D points).

```text
Points: (3,6), (17,15), (13,15), (6,12), (9,1), (2,7), (10,19)

Depth 0 (split by x):        (7,2)
                             /      \
Depth 1 (split by y):    (5,4)    (9,6)
                          ...        ...
```

--> **Why this enables efficient nearest-neighbor and range queries in multiple dimensions** -- each split cuts the remaining SPACE (not just a 1D key range) roughly in half, so a query can prune away entire regions of space that provably can't contain a closer point or a point inside the query range, without visiting every stored point individually -- giving `O(log n)` average-case nearest-neighbor lookup versus `O(n)` for a naive linear scan over all points.
--> **Canonical real-world use cases** -- nearest-neighbor search in machine learning (finding the closest training points to a query point), spatial/GIS queries ("find the nearest restaurant to this location"), and collision detection in computer graphics/game engines, all directly connecting to the closest-pair-of-points problem in the Math and Complexity Theory file's computational geometry primer, which k-d trees are a practical, general-purpose tool for accelerating.

# Deep Dive -- Why So Many Specialized Trees Exist

```text
Structure          Optimizes for
B+ tree             Disk-backed storage, minimizing NUMBER OF DISK READS, range scans
Fibonacci heap       decrease-key specifically, at binary heap's expense elsewhere
Skip list            Simple O(log n) expected ops, concurrency-friendly, no rebalancing code
Treap                Simple O(log n) expected BST ops via randomization, no rotation rules
Interval tree         "Which ranges overlap this query" specifically
Sparse table          O(1) STATIC idempotent range queries (min/max/GCD), no updates ever
Patricia trie         Same as a trie, but space-efficient on sparse/long, non-branching keys
K-D tree              Nearest-neighbor and range queries across MULTIPLE dimensions
```

--> **The general lesson, extending the one from the Tries and Advanced Data Structures file** -- each of these structures exists because a specific access pattern (disk locality, decrease-key frequency, concurrency, overlap queries, static-vs-dynamic data, multi-dimensional locality) wasn't served well enough by the more general-purpose structures covered earlier -- picking the right one is about first identifying precisely which access pattern actually dominates a given problem, then reaching for the structure purpose-built around exactly that pattern.
