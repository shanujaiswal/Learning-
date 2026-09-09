"""
10_dynamic_programming_greedy.py

Implements, tests, and benchmarks:
    1. Naive recursive Fibonacci vs memoized vs tabulated (timing shows the O(2^n) -> O(n) jump)
    2. 0/1 Knapsack via bottom-up DP
    3. Longest Common Subsequence via bottom-up DP
    4. Greedy coin change -- shown working on standard denominations, then FAILING on [1,3,4]

Covers Theory chapter:
    2) DSA/Theory/10 Dynamic Programming and Greedy Algorithms.md

Run:  python 10_dynamic_programming_greedy.py
"""

import time
from functools import lru_cache


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


# ---------------------------------------------------------------------------
# 1) Fibonacci -- naive vs memoized vs tabulated
# ---------------------------------------------------------------------------

def fib_naive(n):
    if n <= 1:
        return n
    return fib_naive(n - 1) + fib_naive(n - 2)


@lru_cache(maxsize=None)
def fib_memo(n):
    if n <= 1:
        return n
    return fib_memo(n - 1) + fib_memo(n - 2)


def fib_tab(n):
    if n <= 1:
        return n
    dp = [0] * (n + 1)
    dp[1] = 1
    for i in range(2, n + 1):
        dp[i] = dp[i - 1] + dp[i - 2]
    return dp[n]


def demo_fibonacci_comparison() -> None:
    print_section("1) Fibonacci -- naive O(2^n) vs memoized/tabulated O(n)")
    n = 30

    start = time.perf_counter()
    naive_result = fib_naive(n)
    t_naive = time.perf_counter() - start

    start = time.perf_counter()
    memo_result = fib_memo(n)
    t_memo = time.perf_counter() - start

    start = time.perf_counter()
    tab_result = fib_tab(n)
    t_tab = time.perf_counter() - start

    print(f"fib_naive({n}) = {naive_result}   took {t_naive*1000:8.3f} ms")
    print(f"fib_memo({n})  = {memo_result}    took {t_memo*1000:8.3f} ms")
    print(f"fib_tab({n})   = {tab_result}     took {t_tab*1000:8.3f} ms")
    assert naive_result == memo_result == tab_result
    print(f"\nMemoized/tabulated versions were roughly {t_naive / max(t_memo, 1e-9):.0f}x+ faster -- "
          "eliminating the naive version's massively repeated re-computation.")


# ---------------------------------------------------------------------------
# 2) 0/1 Knapsack
# ---------------------------------------------------------------------------

def knapsack(weights, values, capacity):
    n = len(weights)
    dp = [[0] * (capacity + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        for c in range(capacity + 1):
            dp[i][c] = dp[i - 1][c]
            if weights[i - 1] <= c:
                dp[i][c] = max(dp[i][c], dp[i - 1][c - weights[i - 1]] + values[i - 1])
    return dp[n][capacity]


def demo_knapsack() -> None:
    print_section("2) 0/1 Knapsack via bottom-up DP")
    weights = [2, 3, 4, 5]
    values = [3, 4, 5, 6]
    capacity = 5
    result = knapsack(weights, values, capacity)
    print(f"weights={weights}, values={values}, capacity={capacity} -> max value = {result}")
    assert result == 7   # items with weights 2+3=5, values 3+4=7
    print("Assertion passed.")


# ---------------------------------------------------------------------------
# 3) Longest Common Subsequence
# ---------------------------------------------------------------------------

def lcs(s1, s2):
    m, n = len(s1), len(s2)
    dp = [[0] * (n + 1) for _ in range(m + 1)]
    for i in range(1, m + 1):
        for j in range(1, n + 1):
            if s1[i - 1] == s2[j - 1]:
                dp[i][j] = dp[i - 1][j - 1] + 1
            else:
                dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])
    return dp[m][n]


def demo_lcs() -> None:
    print_section("3) Longest Common Subsequence via bottom-up DP")
    s1, s2 = "ABCBDAB", "BDCABA"
    result = lcs(s1, s2)
    print(f"s1='{s1}', s2='{s2}' -> LCS length = {result}")
    assert result == 4   # e.g. "BCBA" or "BDAB"
    print("Assertion passed.")


# ---------------------------------------------------------------------------
# 4) Greedy coin change -- works on standard denominations, fails on [1,3,4]
# ---------------------------------------------------------------------------

def coin_change_greedy(coins, amount):
    coins = sorted(coins, reverse=True)
    count = 0
    remaining = amount
    for coin in coins:
        count += remaining // coin
        remaining %= coin
    return count if remaining == 0 else -1


def coin_change_dp_minimum(coins, amount):
    dp = [0] + [float('inf')] * amount
    for a in range(1, amount + 1):
        for coin in coins:
            if coin <= a and dp[a - coin] + 1 < dp[a]:
                dp[a] = dp[a - coin] + 1
    return dp[amount] if dp[amount] != float('inf') else -1


def demo_greedy_vs_dp_coin_change() -> None:
    print_section("4) Greedy coin change -- correct on standard coins, WRONG on [1,3,4]")

    standard_coins = [25, 10, 5, 1]
    amount = 41
    greedy_result = coin_change_greedy(standard_coins, amount)
    dp_result = coin_change_dp_minimum(standard_coins, amount)
    print(f"Standard US coins {standard_coins}, amount={amount}:")
    print(f"  Greedy result: {greedy_result} coins")
    print(f"  DP result:     {dp_result} coins")
    assert greedy_result == dp_result
    print("  -> MATCH -- greedy happens to be optimal for these denominations.")

    tricky_coins = [1, 3, 4]
    amount2 = 6
    greedy_result2 = coin_change_greedy(tricky_coins, amount2)
    dp_result2 = coin_change_dp_minimum(tricky_coins, amount2)
    print(f"\nTricky coins {tricky_coins}, amount={amount2}:")
    print(f"  Greedy result: {greedy_result2} coins  (takes 4+1+1)")
    print(f"  DP result:     {dp_result2} coins  (takes 3+3 -- the true optimum)")
    assert greedy_result2 != dp_result2
    assert dp_result2 < greedy_result2
    print("  -> MISMATCH -- greedy is provably WRONG here, exactly demonstrating why the greedy-choice "
          "property doesn't hold for arbitrary coin denominations, and DP is the safe general solution.")


if __name__ == "__main__":
    demo_fibonacci_comparison()
    demo_knapsack()
    demo_lcs()
    demo_greedy_vs_dp_coin_change()
    print("\nAll Dynamic Programming/Greedy demos completed.")
