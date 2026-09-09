# What Dynamic Programming Actually Solves

--> Dynamic Programming (DP) applies to problems with two specific properties -- when BOTH hold, a naive exponential recursive solution can be transformed into a polynomial one by simply avoiding repeated work.

--> **Overlapping subproblems** -- the same smaller subproblem gets solved over and over again during a naive recursive solution -- directly connecting to the naive Fibonacci recursion tree in the Recursion file, where `fib(3)` gets recomputed from scratch multiple times.
--> **Optimal substructure** -- the optimal solution to the overall problem can be constructed directly from optimal solutions to its subproblems -- e.g. the shortest path from A to C through B is the shortest path from A to B, plus the shortest path from B to C; you don't need to consider non-optimal sub-paths at all.

# Memoization -- Top-Down DP

--> Keeps the natural recursive structure, but CACHES each subproblem's result the first time it's computed -- any later call with the same input returns the cached value instantly instead of recomputing.

```python
def fib_memo(n, cache=None):
    if cache is None:
        cache = {}
    if n in cache:
        return cache[n]
    if n <= 1:
        return n
    cache[n] = fib_memo(n - 1, cache) + fib_memo(n - 2, cache)
    return cache[n]
```

```text
Without memoization: fib(5) recomputes fib(3) twice, fib(2) three times (see Recursion file's tree) -- O(2^n)
With memoization: each unique n from 0 to 5 is computed EXACTLY ONCE, then reused -- O(n)
```

--> `functools.lru_cache` in Python automates exactly this pattern as a decorator, without manually threading a cache dictionary through every call:
```python
from functools import lru_cache

@lru_cache(maxsize=None)
def fib_memo(n):
    if n <= 1:
        return n
    return fib_memo(n - 1) + fib_memo(n - 2)
```

# Tabulation -- Bottom-Up DP

--> Instead of starting from the top question and recursing down, tabulation builds the answer from the SMALLEST subproblems UPWARD, filling in a table iteratively until the final answer is reached -- avoids recursion (and its call-stack overhead/depth limits, covered in the Recursion file) entirely.

```python
def fib_tab(n):
    if n <= 1:
        return n
    dp = [0] * (n + 1)
    dp[1] = 1
    for i in range(2, n + 1):
        dp[i] = dp[i - 1] + dp[i - 2]     # build each answer directly from already-solved smaller ones
    return dp[n]
```

--> **Memoization vs tabulation, practically** -- memoization is usually easier to WRITE (it mirrors the natural recursive definition of the problem directly), while tabulation avoids recursion depth limits and often uses less memory (no call stack overhead, and sometimes the table can be compressed to only the last couple of rows needed, as shown in the space-optimization deep dive below) -- both compute the exact same answer with the same asymptotic time complexity.

# Classic DP Problem -- 0/1 Knapsack

--> Given items each with a weight and a value, and a knapsack with a maximum weight capacity, maximize total value without exceeding capacity -- each item can be taken ONCE or not at all (the "0/1" in the name).

```python
def knapsack(weights, values, capacity):
    n = len(weights)
    dp = [[0] * (capacity + 1) for _ in range(n + 1)]   # dp[i][c] = max value using first i items, capacity c

    for i in range(1, n + 1):
        for c in range(capacity + 1):
            dp[i][c] = dp[i - 1][c]                        # option 1: don't take item i-1
            if weights[i - 1] <= c:
                dp[i][c] = max(dp[i][c], dp[i - 1][c - weights[i - 1]] + values[i - 1])  # option 2: take it
    return dp[n][capacity]
```

--> **Why the 2D table shape** -- the state genuinely depends on TWO things -- which items have been considered so far, AND how much capacity remains -- this "identify what the state actually depends on" step is the single most important part of designing any DP solution, and is exactly where most DP difficulty actually lives, more so than the transition logic itself.

# Classic DP Problem -- Longest Common Subsequence (LCS)

```python
def lcs(s1, s2):
    m, n = len(s1), len(s2)
    dp = [[0] * (n + 1) for _ in range(m + 1)]
    for i in range(1, m + 1):
        for j in range(1, n + 1):
            if s1[i - 1] == s2[j - 1]:
                dp[i][j] = dp[i - 1][j - 1] + 1          # characters match -- extend the common subsequence
            else:
                dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])  # take the best of skipping a char from either string
    return dp[m][n]
```

--> The classic building block behind diff tools (like `git diff`, covered conceptually in the GitHub notes) and DNA sequence alignment -- both are fundamentally asking "how much do these two sequences share, preserving relative order."

# Recognizing a DP Problem

--> **Signals in a problem statement** -- "maximum/minimum," "count the number of ways," "is it possible to," combined with some notion of making a SEQUENCE of choices (which items to take, which path to follow, which characters to match) where an earlier choice affects what's optimal later.
--> **The standard design process** -- (1) define what the STATE represents (what varies between subproblems -- an index, a remaining capacity, a remaining target sum), (2) write the RECURRENCE relating a state to smaller states, (3) identify the BASE CASE(S), (4) decide the ORDER subproblems must be filled in (which smaller states a given state's transition depends on), (5) implement bottom-up or top-down.

# Deep Dive -- Space Optimization in DP

--> Many DP tables only ever reference the immediately PREVIOUS row/state (as in the Fibonacci and Knapsack examples above, where `dp[i]` only depends on `dp[i-1]` and possibly `dp[i-2]`) -- when that's true, the full table can be replaced with just a couple of variables/rows, reducing space from `O(n)` or `O(n*m)` down to `O(1)` or `O(m)`.

```python
def fib_optimized(n):          # O(1) space instead of fib_tab's O(n) array
    if n <= 1:
        return n
    prev2, prev1 = 0, 1
    for _ in range(2, n + 1):
        prev2, prev1 = prev1, prev2 + prev1
    return prev1
```

--> **Practical guidance** -- design the DP solution normally first (correctness first, with the full table), THEN look for this space optimization once the algorithm is proven correct -- prematurely optimizing space can make an already-tricky recurrence harder to reason about while debugging.

# Greedy Algorithms

--> A greedy algorithm makes the choice that looks BEST RIGHT NOW at each step, without reconsidering that choice later -- much simpler and faster than DP (no table, no exploring alternate choices), but only produces the globally OPTIMAL answer for problems where the "greedy choice property" genuinely holds -- picking the local best doesn't lead to the global best for every problem.

```python
def coin_change_greedy(coins, amount):     # coins sorted descending, e.g. [25, 10, 5, 1]
    count = 0
    for coin in coins:
        count += amount // coin
        amount %= coin
    return count if amount == 0 else -1
```

--> **Where greedy works** -- standard US coin denominations (25, 10, 5, 1) -- always taking the largest coin that fits happens to produce the true minimum number of coins.
--> **Where greedy FAILS** -- coin denominations `[1, 3, 4]`, target `6` -- greedy takes `4 + 1 + 1 = 3 coins`, but the true optimal is `3 + 3 = 2 coins`. This exact failure is why "minimum coin change" in GENERAL (arbitrary denominations) is solved with DP, not greedy -- the greedy choice property simply doesn't hold for arbitrary coin sets, even though it happens to hold for the specific, commonly-used denominations above.
--> **Where greedy is PROVABLY correct** -- Dijkstra's algorithm and Prim's/Kruskal's MST algorithms (covered in the Graphs file) are all greedy, and it's a proven mathematical property of those SPECIFIC problems (not a general fact about greedy algorithms) that the locally cheapest choice at each step provably leads to a globally optimal result.
--> **Practical guidance for recognizing which applies** -- if a locally optimal choice can be proven to never rule out a globally optimal solution (often via an "exchange argument" -- showing any optimal solution can be modified to include the greedy choice without getting worse), greedy is valid and dramatically simpler than DP; if that proof doesn't hold (as in the `[1,3,4]` coin example), DP's exhaustive-but-efficient exploration of all choices is the safe, correct fallback.
