"""
01_big_o_complexity_demos.py

Demonstrates, with actual timing measurements:
    1. O(1) vs O(n) vs O(n^2) growth, measured empirically as input size scales
    2. The O(n^2) string-concatenation trap vs the O(n) join fix
    3. Amortized O(1) append on a dynamic array (Python list) vs O(n) insert-at-front

Covers Theory chapter:
    2) DSA/Theory/01 Big-O Notation and Algorithmic Complexity Analysis.md

Run:  python 01_big_o_complexity_demos.py
"""

import time


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


def time_it(fn, *args) -> float:
    start = time.perf_counter()
    fn(*args)
    return time.perf_counter() - start


# ---------------------------------------------------------------------------
# 1) Empirical growth rate comparison
# ---------------------------------------------------------------------------

def constant_time_op(arr):
    return arr[0] if arr else None                # O(1) -- index access, cost independent of len(arr)


def linear_time_op(arr):
    total = 0
    for x in arr:                                   # O(n) -- one pass
        total += x
    return total


def quadratic_time_op(arr):
    count = 0
    for i in range(len(arr)):                        # O(n^2) -- nested loop over the same array
        for j in range(len(arr)):
            if arr[i] == arr[j]:
                count += 1
    return count


def demo_growth_rates() -> None:
    print_section("1) Empirical growth rates -- O(1) vs O(n) vs O(n^2)")
    print("Doubling n should roughly: leave O(1) unchanged, double O(n), and 4x O(n^2).\n")
    for n in (500, 1000, 2000):
        arr = list(range(n))
        t_const = time_it(constant_time_op, arr)
        t_linear = time_it(linear_time_op, arr)
        t_quad = time_it(quadratic_time_op, arr)
        print(f"n={n:5d}  O(1)~{t_const*1e6:8.2f}us   O(n)~{t_linear*1e6:8.2f}us   O(n^2)~{t_quad*1e3:8.2f}ms")


# ---------------------------------------------------------------------------
# 2) The O(n^2) string concatenation trap
# ---------------------------------------------------------------------------

def build_string_naive(words):        # O(n^2) -- each += copies everything accumulated so far
    result = ""
    for w in words:
        result += w
    return result


def build_string_join(words):          # O(n) -- one allocation, one copy
    return "".join(words)


def demo_string_concat_trap() -> None:
    print_section("2) O(n^2) string concatenation trap vs O(n) join")
    words = ["word"] * 20000
    t_naive = time_it(build_string_naive, words)
    t_join = time_it(build_string_join, words)
    print(f"Naive += loop:  {t_naive*1000:8.2f} ms   (O(n^2) -- re-copies the growing string every iteration)")
    print(f"''.join(words): {t_join*1000:8.2f} ms   (O(n)   -- single allocation)")
    assert build_string_naive(["a", "b", "c"]) == build_string_join(["a", "b", "c"])
    print("Both produce the identical result -- only the COST of getting there differs.")


# ---------------------------------------------------------------------------
# 3) Amortized O(1) append vs O(n) insert-at-front
# ---------------------------------------------------------------------------

def demo_amortized_append_vs_front_insert() -> None:
    print_section("3) Amortized O(1) append vs O(n) insert-at-front")
    n = 20000

    def append_n_times():
        arr = []
        for i in range(n):
            arr.append(i)             # amortized O(1) each -- occasional resize+copy, but rare

    def insert_front_n_times():
        arr = []
        for i in range(n):
            arr.insert(0, i)           # O(n) each -- every existing element shifts right every time

    t_append = time_it(append_n_times)
    t_insert_front = time_it(insert_front_n_times)
    print(f"{n} appends at the end:    {t_append*1000:8.2f} ms  (amortized O(1) each -> O(n) total)")
    print(f"{n} inserts at the front:  {t_insert_front*1000:8.2f} ms  (O(n) each -> O(n^2) total)")
    print("Notice the front-insert version is dramatically slower despite doing the 'same' n operations.")


if __name__ == "__main__":
    demo_growth_rates()
    demo_string_concat_trap()
    demo_amortized_append_vs_front_insert()
    print("\nAll Big-O demos completed.")
