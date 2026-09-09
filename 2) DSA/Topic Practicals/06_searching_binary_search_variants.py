"""
06_searching_binary_search_variants.py

Implements and tests:
    1. Linear search vs classic binary search, with a timing comparison
    2. Search in a rotated sorted array
    3. Binary search on the answer -- minimum capacity to ship packages within D days

Covers Theory chapter:
    2) DSA/Theory/06 Searching and Binary Search Variants.md

Run:  python 06_searching_binary_search_variants.py
"""

import time


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


# ---------------------------------------------------------------------------
# 1) Linear vs binary search
# ---------------------------------------------------------------------------

def linear_search(arr, target):
    for i, val in enumerate(arr):
        if val == target:
            return i
    return -1


def binary_search(arr, target):
    low, high = 0, len(arr) - 1
    while low <= high:
        mid = (low + high) // 2
        if arr[mid] == target:
            return mid
        elif arr[mid] < target:
            low = mid + 1
        else:
            high = mid - 1
    return -1


def demo_linear_vs_binary() -> None:
    print_section("1) Linear search vs binary search -- correctness and timing")
    arr = list(range(0, 2_000_000, 2))       # 1,000,000 sorted even numbers
    target = 1_999_998                        # near the end -- worst case for linear search

    start = time.perf_counter()
    li = linear_search(arr, target)
    t_linear = time.perf_counter() - start

    start = time.perf_counter()
    bi = binary_search(arr, target)
    t_binary = time.perf_counter() - start

    print(f"linear_search found index {li} in {t_linear*1000:.3f} ms")
    print(f"binary_search  found index {bi} in {t_binary*1000:.3f} ms")
    assert li == bi
    print(f"Binary search was roughly {t_linear / max(t_binary, 1e-9):.0f}x faster on this near-worst-case target.")


# ---------------------------------------------------------------------------
# 2) Search in a rotated sorted array
# ---------------------------------------------------------------------------

def search_rotated(arr, target):
    low, high = 0, len(arr) - 1
    while low <= high:
        mid = (low + high) // 2
        if arr[mid] == target:
            return mid
        if arr[low] <= arr[mid]:
            if arr[low] <= target < arr[mid]:
                high = mid - 1
            else:
                low = mid + 1
        else:
            if arr[mid] < target <= arr[high]:
                low = mid + 1
            else:
                high = mid - 1
    return -1


def demo_search_rotated() -> None:
    print_section("2) Search in a rotated sorted array")
    arr = [4, 5, 6, 7, 0, 1, 2]      # originally [0,1,2,4,5,6,7], rotated
    for target in (0, 7, 4, 3):
        idx = search_rotated(arr, target)
        found = f"index {idx}" if idx != -1 else "not found"
        print(f"  Searching for {target} in {arr} -> {found}")
    assert search_rotated(arr, 0) == 4
    assert search_rotated(arr, 7) == 3
    assert search_rotated(arr, 3) == -1
    print("Assertions passed.")


# ---------------------------------------------------------------------------
# 3) Binary search on the answer
# ---------------------------------------------------------------------------

def min_capacity_to_ship_in_days(weights, days):
    def can_ship_with_capacity(capacity):
        days_needed, current_load = 1, 0
        for w in weights:
            if current_load + w > capacity:
                days_needed += 1
                current_load = 0
            current_load += w
        return days_needed <= days

    low, high = max(weights), sum(weights)
    while low < high:
        mid = (low + high) // 2
        if can_ship_with_capacity(mid):
            high = mid
        else:
            low = mid + 1
    return low


def demo_binary_search_on_answer() -> None:
    print_section("3) Binary search on the answer -- minimum shipping capacity within D days")
    weights = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
    days = 5
    result = min_capacity_to_ship_in_days(weights, days)
    print(f"weights={weights}, days={days} -> minimum capacity needed = {result}")
    assert result == 15
    print("Assertion passed -- notice no 'array' is being searched at all, just a range of candidate capacities.")


if __name__ == "__main__":
    demo_linear_vs_binary()
    demo_search_rotated()
    demo_binary_search_on_answer()
    print("\nAll Searching/Binary-Search-Variant demos completed.")
