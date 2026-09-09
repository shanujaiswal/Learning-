# Linear Search -- The Baseline

```python
def linear_search(arr, target):
    for i, val in enumerate(arr):
        if val == target:
            return i
    return -1
```

--> `O(n)` -- checks every element until found or exhausted -- works on ANY array regardless of order, but that generality is exactly its weakness: it can't exploit any structure to skip ahead.

# Binary Search -- O(log n) on Sorted Data

--> Requires the array to be SORTED -- repeatedly checks the MIDDLE element and eliminates HALF the remaining search space based on a single comparison, rather than checking elements one at a time.

```python
def binary_search(arr, target):
    low, high = 0, len(arr) - 1
    while low <= high:
        mid = (low + high) // 2
        if arr[mid] == target:
            return mid
        elif arr[mid] < target:
            low = mid + 1        # target must be in the right half
        else:
            high = mid - 1        # target must be in the left half
    return -1
```

```text
Searching for 23 in [2, 5, 8, 12, 16, 23, 38, 45, 56, 72, 91]  (indices 0-10)

low=0, high=10, mid=5 -> arr[5]=23 -- FOUND immediately

Searching for 45 instead:
low=0, high=10, mid=5 -> arr[5]=23 < 45 -- discard entire left half, low=6
low=6, high=10, mid=8 -> arr[8]=56 > 45 -- discard right half, high=7
low=6, high=7, mid=6 -> arr[6]=38 < 45 -- low=7
low=7, high=7, mid=7 -> arr[7]=45 -- FOUND
```

--> Each comparison halves the search space -- starting from `n` elements, after `k` halvings only `n / 2^k` elements remain, so the search terminates once `n / 2^k = 1`, giving `k = log2(n)` -- exactly the `O(log n)` complexity, and exactly why binary search is dramatically faster than linear search on large sorted datasets (searching a billion sorted elements takes at most ~30 comparisons, versus up to a billion for linear search).

# Deep Dive -- The Off-By-One Errors That Make Binary Search Notoriously Hard to Get Right

--> Binary search has a well-known reputation (even among experienced engineers) for subtle boundary bugs -- getting `<=` vs `<`, or `mid + 1` vs `mid`, wrong causes either an infinite loop or missing a valid answer.

```python
# BUGGY -- using < instead of <= misses the case where low == high (a single remaining candidate)
while low < high:          # WRONG for exact-match search -- would skip checking the last remaining element
    ...

# BUGGY -- forgetting the +1/-1 causes an infinite loop when low and high converge without ever changing
elif arr[mid] < target:
    low = mid              # WRONG -- should be mid + 1; without it, low might never actually advance
```

--> **Practical guidance** -- rather than re-deriving the boundary logic from scratch every time, recognize the STANDARD template above (`low <= high`, `mid + 1`/`mid - 1`) and reuse it consistently -- most binary search bugs come from ad hoc variations on this template rather than the core idea itself.

# Binary Search on the Answer -- A Pattern Beyond Simple Lookup

--> Binary search doesn't require an actual sorted ARRAY -- it just requires a MONOTONIC condition (a yes/no question whose answer flips from false to true, or vice versa, exactly once as a parameter increases) -- this generalizes binary search into a powerful pattern for optimization problems, not just "find this value in this list."

```python
def min_capacity_to_ship_in_days(weights, days):
    def can_ship_with_capacity(capacity):
        days_needed, current_load = 1, 0
        for w in weights:
            if current_load + w > capacity:
                days_needed += 1
                current_load = 0
            current_load += w
        return days_needed <= days

    low, high = max(weights), sum(weights)     # search the space of POSSIBLE capacities, not array indices
    while low < high:
        mid = (low + high) // 2
        if can_ship_with_capacity(mid):
            high = mid           # this capacity works -- try to find an even smaller one
        else:
            low = mid + 1          # this capacity is too small -- need more
    return low
```

--> **Why this is still binary search** -- "can this capacity ship everything within the day limit" is MONOTONIC: if a smaller capacity works, every larger capacity also works (more room never hurts); if a smaller capacity fails, that doesn't tell you about larger ones directly, but the overall true/false pattern across increasing capacity values flips exactly once -- this single flip point is precisely what binary search is built to find efficiently, whether the "array" being searched is a real array or, as here, an abstract range of possible answers being tested with a helper function.
--> **Recognizing this pattern** -- look for phrasing like "minimum X such that condition holds" or "maximum X such that condition holds" where testing a single candidate `X` is easy (even if it doesn't say "sorted array" anywhere) -- that's the signal to binary search over the range of POSSIBLE ANSWERS rather than searching a data structure directly.

# Search in a Rotated Sorted Array

```python
def search_rotated(arr, target):
    low, high = 0, len(arr) - 1
    while low <= high:
        mid = (low + high) // 2
        if arr[mid] == target:
            return mid
        if arr[low] <= arr[mid]:                     # left half is the properly sorted portion
            if arr[low] <= target < arr[mid]:
                high = mid - 1
            else:
                low = mid + 1
        else:                                          # right half is the properly sorted portion instead
            if arr[mid] < target <= arr[high]:
                low = mid + 1
            else:
                high = mid - 1
    return -1
```

--> A sorted array that's been rotated (e.g. `[4,5,6,7,0,1,2]`, originally `[0,1,2,4,5,6,7]` rotated) is no longer globally sorted, but at ANY midpoint, at least ONE of the two halves IS still properly sorted -- the algorithm first figures out which half is sorted, then uses that half's known range to decide which side the target could possibly be in, preserving binary search's `O(log n)` behavior even though the array isn't sorted in the usual sense.

# Ternary Search -- A Related But Narrower Technique

--> Splits the search space into THREE parts instead of two at each step -- useful specifically for finding the maximum/minimum of a UNIMODAL function (one that strictly increases then strictly decreases, or vice versa, with a single peak/valley) rather than for exact-value lookup.
--> **Why it's not simply "better" than binary search** -- despite checking two midpoints instead of one, ternary search still only achieves `O(log n)` (with a larger constant factor from doing MORE comparisons per step, not fewer) -- it doesn't asymptotically beat binary search, which is exactly why binary search remains the default choice for anything ternary search could also solve, with ternary search reserved specifically for unimodal-function optimization where binary search's true/false monotonic requirement doesn't directly apply.
