"""
02_arrays_strings_two_pointer_sliding_window.py

Implements and tests:
    1. Two-sum on a sorted array (opposite-direction two pointers)
    2. Remove duplicates in-place from a sorted array (same-direction two pointers)
    3. Longest substring without repeating characters (variable-size sliding window)
    4. Maximum sum of a fixed-size window

Covers Theory chapter:
    2) DSA/Theory/02 Arrays Strings and Two-Pointer Sliding Window Techniques.md

Run:  python 02_arrays_strings_two_pointer_sliding_window.py
"""


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


# ---------------------------------------------------------------------------
# 1) Opposite-direction two pointers -- two-sum on sorted input
# ---------------------------------------------------------------------------

def two_sum_sorted(arr, target):
    left, right = 0, len(arr) - 1
    while left < right:
        current = arr[left] + arr[right]
        if current == target:
            return (left, right)
        elif current < target:
            left += 1
        else:
            right -= 1
    return None


def demo_two_sum_sorted() -> None:
    print_section("1) Two-sum on a sorted array (opposite-direction two pointers)")
    arr = [2, 7, 11, 15, 20, 24]
    target = 26
    result = two_sum_sorted(arr, target)
    print(f"arr={arr}, target={target} -> indices {result} "
          f"(values {arr[result[0]]} + {arr[result[1]]} = {target})")
    assert result == (0, 5)
    assert two_sum_sorted(arr, 100) is None
    print("Assertions passed.")


# ---------------------------------------------------------------------------
# 2) Same-direction two pointers -- remove duplicates in-place
# ---------------------------------------------------------------------------

def remove_duplicates_sorted(arr):
    if not arr:
        return 0
    slow = 0
    for fast in range(1, len(arr)):
        if arr[fast] != arr[slow]:
            slow += 1
            arr[slow] = arr[fast]
    return slow + 1


def demo_remove_duplicates() -> None:
    print_section("2) Remove duplicates in-place (same-direction two pointers)")
    arr = [1, 1, 2, 2, 2, 3, 4, 4, 5]
    unique_count = remove_duplicates_sorted(arr)
    print(f"After dedup: first {unique_count} slots = {arr[:unique_count]}")
    assert arr[:unique_count] == [1, 2, 3, 4, 5]
    print("Assertion passed.")


# ---------------------------------------------------------------------------
# 3) Variable-size sliding window -- longest substring without repeats
# ---------------------------------------------------------------------------

def longest_substring_without_repeat(s):
    seen = set()
    left = 0
    max_len = 0
    best_start = 0
    for right in range(len(s)):
        while s[right] in seen:
            seen.remove(s[left])
            left += 1
        seen.add(s[right])
        if right - left + 1 > max_len:
            max_len = right - left + 1
            best_start = left
    return max_len, s[best_start:best_start + max_len]


def demo_longest_substring() -> None:
    print_section("3) Longest substring without repeating characters (variable sliding window)")
    s = "abcabcbbde"
    length, substring = longest_substring_without_repeat(s)
    print(f"s='{s}' -> longest unique-char substring length={length} ('{substring}')")
    assert length == 3  # the longest run without a repeat here is "abc" (or "bca"/"cab"/"bde") -- length 3
    print("Assertion passed.")


# ---------------------------------------------------------------------------
# 4) Fixed-size sliding window -- max sum of any window of size k
# ---------------------------------------------------------------------------

def max_sum_fixed_window(arr, k):
    window_sum = sum(arr[:k])
    max_sum = window_sum
    for i in range(k, len(arr)):
        window_sum += arr[i] - arr[i - k]
        max_sum = max(max_sum, window_sum)
    return max_sum


def demo_max_sum_window() -> None:
    print_section("4) Maximum sum of a fixed-size window (k=3)")
    arr = [2, 1, 5, 1, 3, 2]
    k = 3
    result = max_sum_fixed_window(arr, k)
    print(f"arr={arr}, k={k} -> max window sum = {result}")
    assert result == 9  # window [5,1,3]
    print("Assertion passed.")


if __name__ == "__main__":
    demo_two_sum_sorted()
    demo_remove_duplicates()
    demo_longest_substring()
    demo_max_sum_window()
    print("\nAll Arrays/Strings/Two-Pointer/Sliding-Window demos completed.")
