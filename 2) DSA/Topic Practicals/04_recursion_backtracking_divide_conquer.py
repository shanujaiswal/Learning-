"""
04_recursion_backtracking_divide_conquer.py

Implements and tests:
    1. Factorial (basic recursion) and naive vs the recursion-tree cost of Fibonacci
    2. Merge sort (divide and conquer)
    3. Backtracking -- generate all subsets, and solve N-Queens

Covers Theory chapter:
    2) DSA/Theory/04 Recursion Backtracking and Divide and Conquer.md

Run:  python 04_recursion_backtracking_divide_conquer.py
"""

import time


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


# ---------------------------------------------------------------------------
# 1) Basic recursion
# ---------------------------------------------------------------------------

def factorial(n):
    if n <= 1:
        return 1
    return n * factorial(n - 1)


def fib_naive(n):          # O(2^n) -- deliberately left unoptimized to demonstrate the cost
    if n <= 1:
        return n
    return fib_naive(n - 1) + fib_naive(n - 2)


def demo_basic_recursion() -> None:
    print_section("1) Basic recursion -- factorial and the cost of naive Fibonacci")
    print(f"factorial(6) = {factorial(6)}")
    assert factorial(6) == 720

    for n in (20, 25, 28):
        start = time.perf_counter()
        result = fib_naive(n)
        elapsed = time.perf_counter() - start
        print(f"fib_naive({n}) = {result}   took {elapsed*1000:.2f} ms  (roughly doubles per +1 -- O(2^n))")
    print("Notice the time roughly doubling each time n increases by 1 -- exactly O(2^n) in action.")


# ---------------------------------------------------------------------------
# 2) Divide and conquer -- merge sort
# ---------------------------------------------------------------------------

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
        if left[i] <= right[j]:
            result.append(left[i]); i += 1
        else:
            result.append(right[j]); j += 1
    result.extend(left[i:])
    result.extend(right[j:])
    return result


def demo_merge_sort() -> None:
    print_section("2) Divide and conquer -- merge sort")
    arr = [38, 27, 43, 3, 9, 82, 10]
    sorted_arr = merge_sort(arr)
    print(f"Original: {arr}")
    print(f"Sorted:   {sorted_arr}")
    assert sorted_arr == sorted(arr)
    print("Assertion passed.")


# ---------------------------------------------------------------------------
# 3) Backtracking -- subsets and N-Queens
# ---------------------------------------------------------------------------

def generate_subsets(items):
    results = []

    def backtrack(start, current):
        results.append(current[:])                # every partial state is itself a valid subset
        for i in range(start, len(items)):
            current.append(items[i])                # choose
            backtrack(i + 1, current)                 # explore
            current.pop()                              # un-choose (backtrack)

    backtrack(0, [])
    return results


def solve_n_queens(n):
    results = []
    board = [-1] * n

    def is_safe(row, col):
        for r in range(row):
            c = board[r]
            if c == col or abs(c - col) == abs(r - row):
                return False
        return True

    def backtrack(row):
        if row == n:
            results.append(board[:])
            return
        for col in range(n):
            if is_safe(row, col):
                board[row] = col
                backtrack(row + 1)
                board[row] = -1

    backtrack(0)
    return results


def demo_backtracking() -> None:
    print_section("3) Backtracking -- generate all subsets, and solve N-Queens")
    items = ["a", "b", "c"]
    subsets = generate_subsets(items)
    print(f"All subsets of {items} ({len(subsets)} total): {subsets}")
    assert len(subsets) == 2 ** len(items)

    n = 6
    solutions = solve_n_queens(n)
    print(f"\n{n}-Queens: found {len(solutions)} solutions. First solution's column placements: {solutions[0]}")
    # Sanity-check the first solution really is valid: no two queens share a column or diagonal
    board = solutions[0]
    for r1 in range(n):
        for r2 in range(r1 + 1, n):
            assert board[r1] != board[r2]
            assert abs(board[r1] - board[r2]) != abs(r1 - r2)
    print("Assertions passed -- subset count matches 2^n, and the N-Queens solution is genuinely valid.")


if __name__ == "__main__":
    demo_basic_recursion()
    demo_merge_sort()
    demo_backtracking()
    print("\nAll Recursion/Backtracking/Divide-and-Conquer demos completed.")
