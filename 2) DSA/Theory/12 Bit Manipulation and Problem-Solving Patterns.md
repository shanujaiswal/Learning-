# Binary Representation -- The Foundation

--> Every integer is stored in memory as a sequence of bits (0s and 1s) -- bit manipulation operates directly on that binary representation, which is often dramatically faster than the equivalent arithmetic operation, since it maps to a single low-level CPU instruction rather than a more complex calculation.

```text
Decimal 13 in binary:  1101   (8 + 4 + 0 + 1 = 13)
                        ^  ^
                     bit 3  bit 0
```

# The Core Bitwise Operators

```python
a, b = 12, 10          # 1100 and 1010 in binary

a & b   # AND  -- 1000 (8)  -- 1 only where BOTH bits are 1
a | b   # OR   -- 1110 (14) -- 1 where EITHER bit is 1
a ^ b   # XOR  -- 0110 (6)  -- 1 where the bits DIFFER
~a      # NOT  -- flips every bit (in two's complement, equivalent to -(a + 1))
a << 1  # LEFT SHIFT  -- 11000 (24) -- equivalent to multiplying by 2 per shift
a >> 1  # RIGHT SHIFT -- 0110 (6)  -- equivalent to integer-dividing by 2 per shift
```

# Common Bit Tricks

```python
def is_power_of_two(n):
    return n > 0 and (n & (n - 1)) == 0
```
--> **Why this works** -- a power of two in binary is always a single `1` bit followed by zeros (`8 = 1000`). Subtracting 1 flips that single bit to 0 and every bit after it to 1 (`7 = 0111`) -- ANDing the two together always produces 0 exactly when `n` was a clean power of two, and something non-zero otherwise.

```python
def count_set_bits(n):          # Brian Kernighan's algorithm -- O(number of 1 bits), not O(total bits)
    count = 0
    while n:
        n &= (n - 1)              # clears the LOWEST set bit each iteration
        count += 1
    return count
```
--> Each `n & (n-1)` removes exactly one `1` bit (the lowest one) per iteration -- the loop runs exactly as many times as there are `1` bits in `n`, rather than checking every single bit position regardless of how many are actually set.

```python
def find_single_number(nums):    # every number appears twice except exactly one -- find that one, O(n) time, O(1) space
    result = 0
    for num in nums:
        result ^= num              # XOR-ing a number with itself cancels to 0; XOR is commutative and associative
    return result
```
--> **Why XOR solves this elegantly** -- `x ^ x = 0` for any `x`, and XOR-ing zero with anything leaves it unchanged -- so every PAIRED number cancels itself out across the full XOR chain, leaving only the single unpaired number as the final result. This achieves `O(1)` space where a hash-set-based "count occurrences" approach would need `O(n)` space.

```python
def get_bit(n, i):      return (n >> i) & 1              # is the i-th bit set?
def set_bit(n, i):       return n | (1 << i)               # force the i-th bit to 1
def clear_bit(n, i):     return n & ~(1 << i)               # force the i-th bit to 0
def toggle_bit(n, i):    return n ^ (1 << i)                 # flip the i-th bit
```

# Bitmasks -- Representing Sets Compactly

--> An integer can represent an entire SET of small items, where bit `i` being `1` means "item `i` is included" -- an extremely compact way to represent subsets, and the basis for a common DP-over-subsets pattern ("bitmask DP") for problems with a small number of items (commonly up to ~20, since `2^20` states is still computationally manageable).

```python
def subsets(items):
    n = len(items)
    result = []
    for mask in range(1 << n):          # iterate through every possible bitmask from 0 to 2^n - 1
        subset = [items[i] for i in range(n) if mask & (1 << i)]
        result.append(subset)
    return result
```

--> Directly connects to the Backtracking file's subset-generation problem -- this bitmask approach solves the exact same "generate all subsets" problem iteratively, without any recursion at all, by exploiting the fact that every integer from `0` to `2^n - 1` naturally corresponds to exactly one possible subset's inclusion/exclusion pattern.

# Recognizing General Problem-Solving Patterns

--> Across every file in this DSA series, most problems reduce to recognizing one of a fairly small set of recurring PATTERNS -- explicitly naming them is often more useful in practice than knowing any single algorithm in isolation, since recognizing "this is a sliding window problem" or "this is a graph problem in disguise" is usually the actual hard part, not implementing the technique once recognized.

```text
Pattern                          Signal in the problem                              Covered in
Two pointers / sliding window     sorted array, or contiguous subarray/substring       Arrays file
Fast/slow pointers                 linked list, cycle detection                         Linked Lists file
Binary search on the answer         "minimum/maximum X such that condition holds"        Searching file
Backtracking                        "generate all," constraint satisfaction               Recursion file
BFS                                  shortest path, unweighted, "fewest steps"             Graphs file
DFS                                   "does a path exist," connected components, cycles     Graphs file
Dijkstra's / greedy on graphs         shortest path, weighted, non-negative                 Graphs file
DP (memoization/tabulation)          "count the ways," "min/max," overlapping choices       DP file
Greedy                               provably optimal local choice (interval scheduling)     DP file
Heap / priority queue                "top K," "kth largest," repeatedly need min/max          Heaps file
Trie                                 prefix matching, autocomplete, many shared-prefix words   Advanced Structures file
Union-Find                          "are these connected," merging groups, cycle detection     Advanced Structures file
Bitmask                              small fixed set of items (~20 or fewer), subset problems   this file
```

# Deep Dive -- Why Pattern Recognition Matters More Than Memorizing Solutions

--> Real interview and real-world problems are rarely IDENTICAL to a textbook example -- they're usually a familiar pattern wearing unfamiliar dressing (a "shortest path" problem phrased as a word ladder, a "sliding window" problem phrased as finding the smallest substring covering all required characters). Memorizing solutions to specific problems generalizes poorly; internalizing the SIGNALS that point to each pattern (as tabulated above) generalizes to problems never seen before, because it's the actual underlying structure being recognized, not a surface-level match to a previously-seen problem statement.
--> The practical study approach this suggests -- after solving any DSA problem, explicitly name WHICH pattern it used and WHY that pattern applied (what property of the problem made it a fit), rather than just moving on once the code passes -- this is what actually builds the transferable recognition skill, rather than a growing list of memorized, narrowly-applicable solutions.
