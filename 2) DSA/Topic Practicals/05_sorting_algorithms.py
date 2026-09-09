"""
05_sorting_algorithms.py

Implements, tests, and benchmarks:
    1. Bubble sort, selection sort, insertion sort (the O(n^2) family)
    2. Merge sort and quicksort (the O(n log n) family)
    3. Counting sort (the non-comparison O(n+k) family)
    4. A timing comparison across all of them on the same input

Covers Theory chapter:
    2) DSA/Theory/05 Sorting Algorithms.md

Run:  python 05_sorting_algorithms.py
"""

import random
import time


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


# ---------------------------------------------------------------------------
# The O(n^2) family
# ---------------------------------------------------------------------------

def bubble_sort(arr):
    arr = arr[:]
    n = len(arr)
    for i in range(n):
        swapped = False
        for j in range(n - i - 1):
            if arr[j] > arr[j + 1]:
                arr[j], arr[j + 1] = arr[j + 1], arr[j]
                swapped = True
        if not swapped:
            break
    return arr


def selection_sort(arr):
    arr = arr[:]
    n = len(arr)
    for i in range(n):
        min_idx = i
        for j in range(i + 1, n):
            if arr[j] < arr[min_idx]:
                min_idx = j
        arr[i], arr[min_idx] = arr[min_idx], arr[i]
    return arr


def insertion_sort(arr):
    arr = arr[:]
    for i in range(1, len(arr)):
        key = arr[i]
        j = i - 1
        while j >= 0 and arr[j] > key:
            arr[j + 1] = arr[j]
            j -= 1
        arr[j + 1] = key
    return arr


# ---------------------------------------------------------------------------
# The O(n log n) family
# ---------------------------------------------------------------------------

def merge_sort(arr):
    if len(arr) <= 1:
        return arr
    mid = len(arr) // 2
    left = merge_sort(arr[:mid])
    right = merge_sort(arr[mid:])
    return _merge(left, right)


def _merge(left, right):
    result, i, j = [], 0, 0
    while i < len(left) and j < len(right):
        if left[i] <= right[j]:
            result.append(left[i]); i += 1
        else:
            result.append(right[j]); j += 1
    result.extend(left[i:])
    result.extend(right[j:])
    return result


def quicksort(arr):
    arr = arr[:]
    _quicksort_inplace(arr, 0, len(arr) - 1)
    return arr


def _quicksort_inplace(arr, low, high):
    if low < high:
        pivot_index = _partition(arr, low, high)
        _quicksort_inplace(arr, low, pivot_index - 1)
        _quicksort_inplace(arr, pivot_index + 1, high)


def _partition(arr, low, high):
    pivot = arr[high]
    i = low - 1
    for j in range(low, high):
        if arr[j] <= pivot:
            i += 1
            arr[i], arr[j] = arr[j], arr[i]
    arr[i + 1], arr[high] = arr[high], arr[i + 1]
    return i + 1


# ---------------------------------------------------------------------------
# The non-comparison family
# ---------------------------------------------------------------------------

def counting_sort(arr):
    if not arr:
        return []
    max_val = max(arr)
    counts = [0] * (max_val + 1)
    for x in arr:
        counts[x] += 1
    result = []
    for value, count in enumerate(counts):
        result.extend([value] * count)
    return result


# ---------------------------------------------------------------------------
# Demos
# ---------------------------------------------------------------------------

def demo_correctness() -> None:
    print_section("1) Correctness check -- every algorithm against Python's sorted()")
    arr = [64, 34, 25, 12, 22, 11, 90, 1, 5, 77]
    expected = sorted(arr)
    algorithms = {
        "bubble_sort": bubble_sort,
        "selection_sort": selection_sort,
        "insertion_sort": insertion_sort,
        "merge_sort": merge_sort,
        "quicksort": quicksort,
        "counting_sort": counting_sort,
    }
    for name, fn in algorithms.items():
        result = fn(arr)
        status = "OK" if result == expected else "MISMATCH"
        print(f"  {name:16s} -> {result}   [{status}]")
        assert result == expected, f"{name} produced an incorrect result"
    print("\nAll algorithms produced the correct sorted output.")


def demo_timing_comparison() -> None:
    print_section("2) Timing comparison on a larger random array (n=3000)")
    random.seed(42)
    base_arr = [random.randint(0, 5000) for _ in range(3000)]

    for name, fn in [
        ("insertion_sort (O(n^2))", insertion_sort),
        ("merge_sort (O(n log n))", merge_sort),
        ("quicksort (O(n log n) avg)", quicksort),
        ("counting_sort (O(n+k))", counting_sort),
    ]:
        start = time.perf_counter()
        fn(base_arr)
        elapsed = time.perf_counter() - start
        print(f"  {name:28s} took {elapsed*1000:8.2f} ms")
    print("\nNotice insertion_sort falling noticeably behind the O(n log n) algorithms at this size.")


def demo_insertion_sort_best_case() -> None:
    print_section("3) Insertion sort's O(n) best case on already-sorted data")
    already_sorted = list(range(3000))
    start = time.perf_counter()
    insertion_sort(already_sorted)
    elapsed = time.perf_counter() - start
    print(f"insertion_sort on already-sorted n=3000: {elapsed*1000:.2f} ms "
          f"(dramatically faster than the random-data timing above -- this is its O(n) best case)")


if __name__ == "__main__":
    demo_correctness()
    demo_timing_comparison()
    demo_insertion_sort_best_case()
    print("\nAll Sorting Algorithm demos completed.")
