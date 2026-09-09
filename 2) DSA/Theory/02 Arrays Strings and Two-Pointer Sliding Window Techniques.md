# Arrays -- The Foundational Data Structure

--> An array stores elements in CONTIGUOUS memory, each accessible by an index -- this contiguity is exactly why index access is `O(1)`: the address of element `i` is just `base_address + i * element_size`, a direct calculation rather than a search.

```text
Array in memory:  [ 10 | 20 | 30 | 40 | 50 ]
Index:               0    1    2    3    4
Accessing arr[3] -- jumps directly to base_address + 3*element_size -- no traversal needed
```

--> **Static vs dynamic arrays** -- a static array (like a C array) has a fixed size set at creation; a dynamic array (Python's `list`, Java's `ArrayList`, JavaScript's `Array`) can grow, backed internally by a static array that gets reallocated and doubled in size when it fills up -- directly connecting to the amortized complexity deep dive in the Big-O file.
--> **Insertion/deletion cost depends on POSITION** -- appending/removing at the END is O(1) (or amortized O(1) for a dynamic array); inserting/removing at the BEGINNING or MIDDLE is O(n), since every subsequent element must physically shift over by one slot.

```python
arr = [10, 20, 30, 40]
arr.append(50)        # O(1) -- adds at the end, no shifting needed
arr.insert(0, 5)       # O(n) -- every existing element shifts right by one to make room
arr.pop()               # O(1) -- removes from the end
arr.pop(0)              # O(n) -- every remaining element shifts left by one
```

# Strings -- Arrays With Extra Rules

--> A string is conceptually an array of characters -- most array reasoning (indexing, iteration cost) applies directly, but strings in many languages (Python, Java, JavaScript) are IMMUTABLE, meaning any "modification" actually creates a brand new string.

```python
s = "hello"
s2 = s + " world"     # Creates an ENTIRELY NEW string -- O(n) copy, doesn't modify the original "hello" in place

# A naive loop building a string with += repeatedly is a classic hidden performance trap:
result = ""
for word in ["a", "b", "c", "d"]:
    result += word     # Each += creates a new string, copying everything accumulated so far -- O(n) EACH time
# Total cost across the whole loop: O(n^2), not O(n), because of repeated full copies

# The fix -- build a list, then join once at the end:
result = "".join(["a", "b", "c", "d"])   # O(n) total -- one single allocation and copy
```

--> This exact `+=` trap is one of the most common accidental `O(n^2)` bugs in real code -- it looks like a simple, cheap loop, but each iteration silently re-copies everything accumulated so far.

# Two-Pointer Technique

--> A pattern where TWO index variables traverse a structure (usually a sorted array or a string) simultaneously, from different positions, to solve a problem in a single pass -- often converting a naive `O(n^2)` nested-loop approach into `O(n)`.

# Opposite-Direction Two Pointers -- Pair-Finding in a Sorted Array

```python
def two_sum_sorted(arr, target):
    left, right = 0, len(arr) - 1
    while left < right:
        current_sum = arr[left] + arr[right]
        if current_sum == target:
            return (left, right)
        elif current_sum < target:
            left += 1        # sum too small -- need a bigger left value, move left pointer up
        else:
            right -= 1        # sum too big -- need a smaller right value, move right pointer down
    return None
```

--> **Why this works, and why it needs a SORTED array** -- because the array is sorted, moving `left` forward only ever increases the sum, and moving `right` backward only ever decreases it -- this monotonic property is exactly what lets each pointer move confidently in one direction without ever needing to backtrack, collapsing what would otherwise be an `O(n^2)` check-every-pair search into a single `O(n)` pass.

# Same-Direction Two Pointers -- Removing/Partitioning In Place

```python
def remove_duplicates_sorted(arr):
    if not arr:
        return 0
    slow = 0                        # slow points to the last confirmed-unique position
    for fast in range(1, len(arr)):  # fast scans ahead looking for the next unique value
        if arr[fast] != arr[slow]:
            slow += 1
            arr[slow] = arr[fast]
    return slow + 1                  # number of unique elements, now packed at the front of arr
```

--> `fast` explores ahead while `slow` marks the boundary of what's already been finalized -- a common shape for in-place array modification problems (removing duplicates, partitioning by a condition) that need `O(1)` extra space instead of building a new array.

# Sliding Window Technique

--> A specialization of two pointers specifically for CONTIGUOUS SUBARRAY/SUBSTRING problems -- instead of recomputing a result from scratch for every possible window position (`O(n)` work per window, `O(n^2)` total), a sliding window incrementally updates the result as the window's edges move, doing `O(1)` work per step.

# Fixed-Size Window

```python
def max_sum_fixed_window(arr, k):
    window_sum = sum(arr[:k])       # cost of the very first window
    max_sum = window_sum
    for i in range(k, len(arr)):
        window_sum += arr[i] - arr[i - k]   # add the new element entering, remove the one leaving
        max_sum = max(max_sum, window_sum)
    return max_sum
```

--> Without the sliding technique, computing every window's sum from scratch is `O(n*k)`; incrementally updating (add what enters, subtract what leaves) drops this to `O(n)` total.

# Variable-Size Window -- Expand and Shrink

```python
def longest_substring_without_repeat(s):
    seen = set()
    left = 0
    max_length = 0
    for right in range(len(s)):
        while s[right] in seen:            # shrink from the left until the duplicate is gone
            seen.remove(s[left])
            left += 1
        seen.add(s[right])                  # expand the window by including s[right]
        max_length = max(max_length, right - left + 1)
    return max_length
```

--> `right` always expands the window forward by one each iteration; `left` only moves forward when the current window becomes invalid (here, when a repeated character is found) -- both pointers only ever move FORWARD, never backward, which is exactly why the total work across the whole algorithm is `O(n)` rather than `O(n^2)`, even though there's a `while` loop nested inside a `for` loop.

# Deep Dive -- Recognizing When Two Pointers/Sliding Window Applies

--> **Look for these signals in a problem statement**: "sorted array" + "pair/triplet" (opposite-direction two pointers), "contiguous subarray/substring" + "longest/shortest/sum/count" (sliding window), "in-place" + "remove/partition" (same-direction two pointers).
--> **The core requirement these techniques rely on** -- some MONOTONIC property that guarantees a pointer never needs to move backward once it's moved forward -- for sorted-array two pointers, it's the sort order itself; for sliding window, it's that expanding a window can only ever help satisfy a "maximize" condition or hurt a "minimize" condition (or vice versa) in one consistent direction. Without this monotonic guarantee, a pointer might need to backtrack, and the technique's `O(n)` guarantee breaks down.
