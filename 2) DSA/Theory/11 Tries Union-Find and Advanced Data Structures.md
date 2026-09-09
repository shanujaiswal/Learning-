# Tries (Prefix Trees)

--> A trie is a tree specialized for storing STRINGS, where each node represents one CHARACTER, and the path from the root down to any node spells out a PREFIX -- words sharing a common prefix literally share the same path in the tree, only branching where their characters diverge.

```text
Inserting "cat", "car", "dog":

           (root)
           /    \
          c      d
          |      |
          a      o
         / \      \
        t   r      g
        *   *      *      (* marks "end of a complete word" at that node)
```

```python
class TrieNode:
    def __init__(self):
        self.children = {}
        self.is_end_of_word = False

class Trie:
    def __init__(self):
        self.root = TrieNode()

    def insert(self, word):
        node = self.root
        for char in word:
            if char not in node.children:
                node.children[char] = TrieNode()
            node = node.children[char]
        node.is_end_of_word = True

    def search(self, word):
        node = self.root
        for char in word:
            if char not in node.children:
                return False
            node = node.children[char]
        return node.is_end_of_word

    def starts_with(self, prefix):
        node = self.root
        for char in prefix:
            if char not in node.children:
                return False
            node = node.children[char]
        return True
```

--> **Why a trie beats a hash set for prefix-based problems** -- a hash set can check "does this exact word exist" in O(1), but has NO efficient way to answer "does anything START WITH this prefix" without checking every single stored word individually -- a trie answers a prefix check in `O(m)` (where `m` is the prefix length), completely independent of how many total words are stored, since it's just walking down a fixed number of tree levels.
--> **Canonical real-world use cases** -- autocomplete/typeahead search suggestions, spell-checkers, IP routing tables (longest-prefix matching), and word-search-style problems in a grid where you're checking many candidate words against a shared dictionary simultaneously.

# Union-Find (Disjoint Set Union)

--> Union-Find tracks a collection of elements partitioned into disjoint (non-overlapping) SETS, and efficiently answers two questions: "are these two elements in the same set" (`find`) and "merge these two sets into one" (`union`) -- both achievable in nearly `O(1)` with the right optimizations.

```python
class UnionFind:
    def __init__(self, n):
        self.parent = list(range(n))     # initially, every element is its own separate set/root
        self.rank = [0] * n

    def find(self, x):
        if self.parent[x] != x:
            self.parent[x] = self.find(self.parent[x])   # path compression -- flatten the tree as we go
        return self.parent[x]

    def union(self, x, y):
        root_x, root_y = self.find(x), self.find(y)
        if root_x == root_y:
            return False                    # already in the same set -- union would be a no-op
        if self.rank[root_x] < self.rank[root_y]:         # union by rank -- attach smaller tree under bigger
            root_x, root_y = root_y, root_x
        self.parent[root_y] = root_x
        if self.rank[root_x] == self.rank[root_y]:
            self.rank[root_x] += 1
        return True
```

--> **Path compression** -- during `find`, every node visited along the way gets directly re-pointed at the ROOT, so future `find` calls on those same nodes become instant -- the tree flattens itself over time purely as a side effect of normal use.
--> **Union by rank** -- always attaches the SMALLER tree underneath the LARGER tree's root (rather than an arbitrary choice), keeping the overall structure shallow and preventing a long chain from forming.
--> **Combined, both optimizations give amortized nearly-`O(1)` per operation** (technically `O(α(n))`, where `α` is the inverse Ackermann function -- for any input size that could realistically exist, this value is smaller than 5, making it practically constant) -- directly connecting to the amortized analysis deep dive in the Big-O file.
--> **Canonical use case -- Kruskal's MST algorithm** (covered in the Graphs file) -- uses Union-Find specifically to detect, in near-`O(1)`, whether adding a candidate edge would create a cycle (it would, if both endpoints are already in the same set).
--> **Other use cases** -- detecting cycles in an undirected graph, checking network connectivity ("are these two computers on the same network"), and grouping items by a "connected if related" relationship (friend circles, account merging by shared email/phone).

# Segment Trees

--> A segment tree answers RANGE QUERIES (sum, min, max over a range of indices) AND supports point UPDATES, both in `O(log n)` -- a plain array gives `O(1)` point update but `O(n)` range query (must sum every element in the range); a precomputed prefix-sum array gives `O(1)` range query but `O(n)` update (the whole prefix array shifts after any change). A segment tree gives `O(log n)` for BOTH, trading a bit of each extreme for balance.

```text
Array: [1, 3, 5, 7, 9, 11]

Segment tree (sum), built bottom-up from leaves representing single elements:
                        36
                    /        \
                  9            27
                /   \         /    \
               4     5      16      11
              / \          /  \
             1   3        7    9
                          (leaves are single array elements; internal nodes are sums of their children)
```

--> A range sum query for indices `[1, 4]` doesn't need to touch every element individually -- it combines a small number of precomputed subtree sums that together exactly cover that range, which is exactly why it only takes `O(log n)` node visits rather than `O(n)` individual element additions.
--> An update to a single element only needs to propagate that change UP through the `O(log n)` ancestors of that element's leaf, recomputing each ancestor's sum -- not touching the rest of the tree at all.
--> **A Fenwick Tree (Binary Indexed Tree)** achieves the same `O(log n)` range-sum-and-point-update behavior as a segment tree, using a more compact array-based implementation with clever bit manipulation (covered in the Bit Manipulation file) instead of an explicit tree structure -- generally preferred over a segment tree specifically for SUM queries because of its smaller memory footprint and simpler code, while a full segment tree remains more flexible for other range operations (min, max, GCD) that a Fenwick Tree doesn't handle as naturally.

# Deep Dive -- Choosing Between These Advanced Structures

```text
Need                                                    Structure
Fast prefix/autocomplete lookups over many strings        Trie
"Are these connected/in the same group" + merging groups   Union-Find
Range sum/min/max query AND point updates, repeatedly       Segment Tree (or Fenwick Tree for sums specifically)
Just need min/max repeatedly, no arbitrary range queries     Heap (covered in the Heaps file) -- simpler, sufficient
```

--> **The general lesson these advanced structures teach** -- when a plain array, hash table, or basic tree's Big-O for your SPECIFIC access pattern isn't good enough, the fix is rarely "write cleverer code around the basic structure" -- it's recognizing which specialized structure was already designed around exactly that access pattern, and reaching for it directly rather than reinventing an ad hoc, likely-inferior version of the same idea.
