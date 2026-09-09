# Why So Many Sorting Algorithms Exist

--> No single sorting algorithm is best in every situation -- they trade off time complexity, space complexity, stability, and behavior on nearly-sorted or specially-structured data differently, which is exactly why picking the "right" one depends on the actual data and constraints, not just picking whichever has the best average-case Big-O on paper.

# Bubble Sort -- O(n^2)

```python
def bubble_sort(arr):
    n = len(arr)
    for i in range(n):
        swapped = False
        for j in range(n - i - 1):
            if arr[j] > arr[j + 1]:
                arr[j], arr[j + 1] = arr[j + 1], arr[j]
                swapped = True
        if not swapped:        # already sorted -- no point continuing
            break
```

--> Repeatedly steps through the array, swapping adjacent out-of-order pairs -- each full pass "bubbles" the largest unsorted element to its correct position at the end. `O(n^2)` worst/average case, but the `swapped` early-exit gives it `O(n)` BEST case on already-sorted input -- purely educational in practice, essentially never used in production code.

# Selection Sort -- O(n^2)

```python
def selection_sort(arr):
    n = len(arr)
    for i in range(n):
        min_idx = i
        for j in range(i + 1, n):
            if arr[j] < arr[min_idx]:
                min_idx = j
        arr[i], arr[min_idx] = arr[min_idx], arr[i]
```

--> Repeatedly finds the MINIMUM remaining element and swaps it into its correct position -- always exactly `O(n^2)` regardless of input order (no early-exit benefit like bubble sort), but only performs `O(n)` swaps total (versus bubble sort's potentially many more), which matters when a swap is an expensive operation (e.g. swapping large records rather than small values).

# Insertion Sort -- O(n^2) Worst, O(n) Best

```python
def insertion_sort(arr):
    for i in range(1, len(arr)):
        key = arr[i]
        j = i - 1
        while j >= 0 and arr[j] > key:      # shift larger elements right to make room
            arr[j + 1] = arr[j]
            j -= 1
        arr[j + 1] = key
```

--> Builds up a sorted portion at the front, one element at a time, inserting each new element into its correct position within the already-sorted portion -- genuinely `O(n)` on already (or nearly) sorted input, since the `while` loop barely runs at all. This is exactly why insertion sort is the go-to choice for SMALL arrays or NEARLY-SORTED data, and why many production sort implementations (Python's `sorted()`, Java's `Arrays.sort()`) actually switch to insertion sort internally for small sub-arrays/partitions rather than using their main algorithm all the way down.

# Merge Sort -- O(n log n), Stable, Not In-Place

```python
def merge_sort(arr):
    if len(arr) <= 1:
        return arr
    mid = len(arr) // 2
    left = merge_sort(arr[:mid])
    right = merge_sort(arr[mid:])
    return merge(left, right)

def merge(left, right):
    result = []
    i = j = 0
    while i < len(left) and j < len(right):
        if left[i] <= right[j]:      # <= (not <) preserves original relative order of equal elements
            result.append(left[i]); i += 1
        else:
            result.append(right[j]); j += 1
    result.extend(left[i:])
    result.extend(right[j:])
    return result
```

--> Directly the Divide and Conquer pattern from the Recursion file -- split in half, recursively sort each half, merge the two sorted halves back together. GUARANTEED `O(n log n)` in every case (best, average, AND worst) -- no input can make merge sort degrade, unlike quicksort below.
--> **Stability** -- a sort is STABLE if elements that compare EQUAL keep their original relative order after sorting -- merge sort's `<=` comparison above preserves this. Stability matters when sorting records by one field while wanting ties broken by original insertion order (e.g. sorting a list of orders by date, where same-date orders should stay in the order they were originally listed).
--> **The real cost** -- `O(n)` EXTRA space for the merge step's temporary arrays -- not in-place, which is the main trade-off against quicksort's typically better real-world speed and lower memory footprint.

# Quicksort -- O(n log n) Average, O(n^2) Worst, In-Place

```python
def quicksort(arr, low=0, high=None):
    if high is None:
        high = len(arr) - 1
    if low < high:
        pivot_index = partition(arr, low, high)
        quicksort(arr, low, pivot_index - 1)
        quicksort(arr, pivot_index + 1, high)

def partition(arr, low, high):
    pivot = arr[high]
    i = low - 1
    for j in range(low, high):
        if arr[j] <= pivot:
            i += 1
            arr[i], arr[j] = arr[j], arr[i]
    arr[i + 1], arr[high] = arr[high], arr[i + 1]
    return i + 1
```

--> Picks a PIVOT element, partitions the array so everything smaller ends up to its left and everything larger to its right, then recursively sorts each side -- unlike merge sort, this happens IN-PLACE (no extra array needed beyond the recursion's own call stack), which is exactly why quicksort tends to be faster in practice despite having a worse theoretical worst case.
--> **Why the worst case is O(n^2)** -- if the pivot chosen is consistently the SMALLEST or LARGEST remaining element (e.g. always picking the last element on an already-sorted or reverse-sorted array, as the naive version above does), each partition step only shrinks the problem by one element instead of roughly halving it -- degrading to the same shape as selection sort.
--> **Why this worst case rarely bites in practice** -- choosing the pivot RANDOMLY, or using a "median of three" (comparing the first, middle, and last elements and picking the median of those as the pivot), makes the pathological worst-case input extremely unlikely to occur by chance, pushing real-world behavior reliably back toward the `O(n log n)` average case.
--> **Not stable** -- the swapping during partitioning can reorder equal elements relative to each other, unlike merge sort.

# Heap Sort -- O(n log n), In-Place, Not Stable

--> Builds a MAX-HEAP (covered in the Heaps file) from the array, then repeatedly extracts the maximum element and places it at the end -- guaranteed `O(n log n)` in every case like merge sort, but in-place like quicksort, combining the guarantee of one with the space efficiency of the other.
--> **The real trade-off** -- heap sort has notably worse CACHE PERFORMANCE in practice than quicksort, because heap operations jump around the array by index in a pattern that doesn't access nearby memory sequentially -- which is exactly why quicksort (with good pivot selection) is still generally preferred in real-world general-purpose sorting despite heap sort's cleaner worst-case guarantee.

# Counting Sort and Radix Sort -- Beating O(n log n)

--> Both exploit EXTRA information about the values being sorted (not just pairwise comparisons) to beat the `O(n log n)` comparison-sort floor proven in the Big-O file's deep dive.

```python
def counting_sort(arr, max_val):
    counts = [0] * (max_val + 1)
    for x in arr:
        counts[x] += 1                # tally how many times each value appears
    result = []
    for value, count in enumerate(counts):
        result.extend([value] * count)   # rebuild the sorted array from the tallies
    return result
```

--> **Counting sort** -- `O(n + k)` where `k` is the range of possible values -- works by counting occurrences of each distinct value directly, rather than comparing elements at all. Only practical when `k` (the range of values) isn't dramatically larger than `n` (a huge range wastes memory on mostly-empty counters).
--> **Radix sort** -- sorts numbers digit by digit (typically using counting sort as its underlying step for each digit), from least significant to most significant -- `O(d * (n + k))` where `d` is the number of digits, effectively `O(n)` for fixed-width numbers. Used for sorting large sets of integers or fixed-length strings where the comparison-sort floor genuinely doesn't apply, since no pairwise comparisons are involved.

# Choosing a Sorting Algorithm -- Practical Summary

```text
Situation                              Best Choice
General-purpose default                Quicksort (or the language's built-in sort -- see below)
Need a GUARANTEED worst case            Merge sort or Heap sort
Need STABILITY                          Merge sort (or a stable built-in sort)
Small array (< ~20 elements)            Insertion sort
Nearly-sorted data                       Insertion sort
Sorting integers/fixed-width keys        Radix sort or Counting sort
Memory is severely constrained           Heap sort or Quicksort (both in-place)
```

--> **Real-world built-in sorts** -- Python's `sorted()`/`list.sort()` uses **Timsort**, a hybrid of merge sort and insertion sort specifically designed to exploit already-sorted "runs" within real-world data (which appears far more often than random-data benchmarks suggest) -- it's stable AND adapts its performance to existing order, essentially combining the best practical properties of several algorithms above rather than being a single "pure" textbook algorithm.
