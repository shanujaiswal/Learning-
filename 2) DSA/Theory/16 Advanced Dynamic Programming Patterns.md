# Interval DP -- Matrix Chain Multiplication

--> Interval DP solves problems where the state is a RANGE `[i, j]` over a sequence, and the answer for that range depends on trying every possible way to SPLIT it into two smaller ranges -- a distinct shape from the "prefix up to index i" state seen in the DP file's Knapsack and LCS examples.
--> **The problem** -- given a chain of matrices with compatible dimensions, find the ORDER of multiplication (i.e. where to place parentheses) that minimizes the total number of scalar multiplications -- matrix multiplication is associative (the final result is the same regardless of order), but the amount of WORK to get there varies enormously depending on grouping.

```python
def matrix_chain_order(dims):    # dims: [d0, d1, ..., dn] -- matrix i has shape dims[i] x dims[i+1]
    n = len(dims) - 1              # number of matrices
    dp = [[0] * n for _ in range(n)]     # dp[i][j] = min cost to multiply matrices i..j

    for length in range(2, n + 1):          # length of the chain being solved, smallest first
        for i in range(n - length + 1):
            j = i + length - 1
            dp[i][j] = float('inf')
            for split in range(i, j):        # try every place to split the chain into two halves
                cost = (dp[i][split] + dp[split + 1][j]
                        + dims[i] * dims[split + 1] * dims[j + 1])
                dp[i][j] = min(dp[i][j], cost)
    return dp[0][n - 1]
```

--> **Why the loop order matters -- smallest ranges first** -- computing `dp[i][j]` requires `dp[i][split]` and `dp[split+1][j]`, both STRICTLY SMALLER ranges -- filling the table by increasing `length` guarantees every smaller sub-range this state depends on is already finished, mirroring the DP file's "identify the fill order" step of the standard design process.
--> **Burst Balloons -- the same interval-DP shape** -- given balloons in a row, each burst earning `balloons[left] * balloons[i] * balloons[right]` points based on its currently-alive neighbors, maximize total points -- the trick is defining `dp[i][j]` as "max points from bursting everything strictly BETWEEN `i` and `j`, assuming `i` and `j` themselves burst LAST" -- this reframing turns a seemingly order-dependent problem into the same "try every split point" interval recurrence as matrix chain multiplication, since choosing which balloon bursts last within a range cleanly separates it into two independent sub-ranges.

# DP on Trees

--> DP on trees applies the DP file's core ideas to a tree structure (Trees file) instead of a linear sequence -- the state is typically defined per NODE, and the recurrence combines results from a node's CHILDREN (already solved, since DFS naturally processes children before returning to their parent) rather than from a smaller index range.

```python
def tree_diameter(tree, root):     # tree: {node: [children]} -- find the longest path between any two nodes
    diameter = [0]

    def dfs(node):
        top_two = [0, 0]              # two longest downward paths found through this node's children
        for child in tree.get(node, []):
            child_height = dfs(child) + 1
            if child_height > top_two[0]:
                top_two = [child_height, top_two[0]]
            elif child_height > top_two[1]:
                top_two[1] = child_height
        diameter[0] = max(diameter[0], top_two[0] + top_two[1])    # path THROUGH this node
        return top_two[0]               # height contributed upward to this node's own parent

    dfs(root)
    return diameter[0]
```

--> **The general pattern** -- a post-order DFS (process all children fully, THEN combine their results at the current node, directly connecting to the Trees file's post-order traversal) computes each node's own DP value from its already-computed children's values -- exactly the same "smaller subproblems first" discipline as linear DP, just following the tree's parent-child structure as the dependency order instead of an increasing index.
--> **Canonical real-world use cases** -- maximum independent set on a tree (e.g. "invite the most people to a party such that no two direct friends both attend," modeled as a tree), and the diameter example above (longest path in a tree, useful in network topology analysis).

# Digit DP

--> Digit DP solves problems of the form "count numbers in range `[0, N]` satisfying some digit-based property" (e.g. "how many numbers ≤ N have digit sum divisible by 3") by processing the number DIGIT BY DIGIT rather than iterating over every number individually, which would be infeasible for large `N` (e.g. `N` up to 10^18).
--> **The state** -- typically `(position, tight, other_property...)`, where `position` is which digit is currently being decided, and `tight` is a boolean tracking whether the digits chosen SO FAR exactly match `N`'s digits up to this point (constraining future digit choices) or have already gone strictly below `N` (freeing every remaining digit to be anything 0-9).

```python
from functools import lru_cache

def count_with_digit_sum_divisible(n, divisor):
    digits = list(map(int, str(n)))

    @lru_cache(maxsize=None)
    def solve(pos, digit_sum, tight):
        if pos == len(digits):
            return 1 if digit_sum % divisor == 0 else 0

        limit = digits[pos] if tight else 9
        total = 0
        for d in range(limit + 1):
            new_tight = tight and (d == limit)         # still bound by N's digits only if we matched exactly
            total += solve(pos + 1, digit_sum + d, new_tight)
        return total

    return solve(0, 0, True)
```

--> **Why `tight` is the essential trick** -- without it, the recursion couldn't tell whether digit `d` at this position is even LEGAL to place (choosing a digit larger than `N`'s corresponding digit while still "tight" would produce a number bigger than `N`) -- once a chosen digit falls strictly below `N`'s digit at that position, every digit placed afterward is unconstrained, since the number is already guaranteed smaller than `N` regardless of what follows.
--> **Canonical real-world use case** -- "count numbers in a range with no repeated digits," "count numbers whose digits sum to a multiple of k" -- any large-range digit-property counting problem where brute-force iteration is computationally infeasible.

# Bitmask DP -- Traveling Salesman Problem Worked Through

--> Bitmask DP represents a SUBSET of items (e.g. "which cities have been visited so far") as the bits of an integer -- a subset of `n` items has `2^n` possible states, each representable as one integer from `0` to `2^n - 1`, letting a subset be used directly as a DP array index.
--> **The TSP state** -- `dp[mask][i]` = the minimum cost of a path that has visited EXACTLY the set of cities in `mask`, ending currently at city `i` -- the "exactly this set, ending here" framing is what makes transitions well-defined: from this state, the path can extend to any city `j` NOT yet in `mask`.

```python
def tsp(dist):        # dist[i][j] = cost from city i to city j; assume city 0 is the start
    n = len(dist)
    FULL = (1 << n) - 1
    dp = [[float('inf')] * n for _ in range(1 << n)]
    dp[1][0] = 0            # mask = 1 (only city 0 visited), currently at city 0, cost 0

    for mask in range(1 << n):
        for i in range(n):
            if dp[mask][i] == float('inf') or not (mask & (1 << i)):
                continue                       # this state was never reached, or i isn't actually in mask
            for j in range(n):
                if mask & (1 << j):
                    continue                     # j already visited -- can't revisit in TSP
                new_mask = mask | (1 << j)
                new_cost = dp[mask][i] + dist[i][j]
                if new_cost < dp[new_mask][j]:
                    dp[new_mask][j] = new_cost

    return min(dp[FULL][i] + dist[i][0] for i in range(n))    # return to the start city at the end
```

--> **Why this beats brute-force permutations** -- trying every possible city ORDER directly costs `O(n!)`; bitmask DP instead has only `2^n * n` distinct `(mask, i)` states, each with `O(n)` transitions, giving `O(2^n * n^2)` -- a genuine improvement (e.g. `n=15`: `15! ≈ 1.3 trillion` vs `2^15 * 15^2 ≈ 7.4 million`), though still exponential -- bitmask DP doesn't make TSP POLYNOMIAL (it remains NP-hard, previewed in the Math and Complexity Theory file), it just shrinks the exponential base substantially by eliminating the massive redundancy across permutations that revisit the same "which cities visited, currently where" state.
--> **The assignment problem, briefly** -- "assign `n` workers to `n` jobs minimizing total cost" follows the identical bitmask shape, with `dp[mask]` = minimum cost to assign jobs represented in `mask` to the first `popcount(mask)` workers, transitioning by trying every unused job for the next worker -- the same "which subset has been committed so far" state that makes TSP tractable at this scale applies directly.

# Longest Increasing Subsequence (LIS)

--> Find the length of the longest subsequence (not necessarily contiguous) of an array that is strictly increasing.

```python
def lis_on_squared(nums):                # O(n^2) -- the direct DP formulation
    n = len(nums)
    dp = [1] * n                            # dp[i] = length of the longest increasing subsequence ENDING at i
    for i in range(n):
        for j in range(i):
            if nums[j] < nums[i]:
                dp[i] = max(dp[i], dp[j] + 1)
    return max(dp) if dp else 0

def lis_n_log_n(nums):                    # O(n log n) -- patience sorting / binary search approach
    import bisect
    tails = []                               # tails[k] = smallest possible tail value of an increasing
                                              # subsequence of length k+1 found so far
    for num in nums:
        pos = bisect.bisect_left(tails, num)
        if pos == len(tails):
            tails.append(num)
        else:
            tails[pos] = num
    return len(tails)
```

--> **The O(n^2) version's recurrence** -- `dp[i]` looks back at every earlier index `j`, extending the best subsequence ending at any smaller-valued `j` -- straightforward, but the double loop is exactly where the `O(n^2)` cost comes from, following the DP file's "identify the state, write the recurrence" process directly.
--> **Why the O(n log n) version works, intuitively** -- `tails` is NOT the actual LIS -- it's a running record of "the best (smallest) possible ending value for each achievable subsequence length so far" -- keeping the tail value as small as possible for each length maximizes the chance a FUTURE number can extend that subsequence, and because `tails` is always sorted, binary search (Searching file) finds the right position to update in `O(log n)` instead of the inner `O(n)` scan the direct DP version needs.
--> **Important nuance** -- `tails`'s final LENGTH equals the true LIS length, but `tails` itself is generally NOT a valid LIS of the original array (its values get overwritten as better options are found) -- reconstructing the actual subsequence (not just its length) requires additional bookkeeping (parent pointers) alongside this trick.

# Kadane's Algorithm -- Maximum Subarray Sum

--> Find the contiguous subarray with the largest sum, in a single `O(n)` pass -- a DP problem in disguise, where the state is simply "the best sum of a subarray ENDING at the current index."

```python
def max_subarray_sum(nums):
    best_ending_here = best_overall = nums[0]
    for num in nums[1:]:
        best_ending_here = max(num, best_ending_here + num)   # extend the previous subarray, or restart here
        best_overall = max(best_overall, best_ending_here)
    return best_overall
```

--> **Why restarting is sometimes correct** -- if `best_ending_here` (the best sum of a subarray ending at the PREVIOUS index) is already negative, extending it into the current number can only make things worse than just starting fresh at the current number alone -- this greedy-looking "reset when negative" choice is provably safe precisely because any subarray with a negative-sum prefix can always be improved by dropping that prefix entirely.
--> **Kadane's as the O(1)-space special case of the general DP pattern** -- exactly the same space-optimization idea as the DP file's Fibonacci space-optimization deep dive: since `dp[i]` (best sum ending at `i`) only ever depends on `dp[i-1]`, the full array of `dp` values collapses to a single running variable.

# Edit Distance (Levenshtein Distance)

--> The minimum number of single-character insertions, deletions, or substitutions needed to transform one string into another -- a close relative of the Longest Common Subsequence problem in the DP file, but modeling TRANSFORMATION COST rather than shared structure.

```python
def edit_distance(s1, s2):
    m, n = len(s1), len(s2)
    dp = [[0] * (n + 1) for _ in range(m + 1)]

    for i in range(m + 1):
        dp[i][0] = i          # transforming s1[:i] into "" costs i deletions
    for j in range(n + 1):
        dp[0][j] = j          # transforming "" into s2[:j] costs j insertions

    for i in range(1, m + 1):
        for j in range(1, n + 1):
            if s1[i - 1] == s2[j - 1]:
                dp[i][j] = dp[i - 1][j - 1]                 # characters already match -- no cost
            else:
                dp[i][j] = 1 + min(
                    dp[i - 1][j],       # delete from s1
                    dp[i][j - 1],        # insert into s1
                    dp[i - 1][j - 1]      # substitute
                )
    return dp[m][n]
```

--> **Why the recurrence's three options correspond exactly to the three allowed edits** -- `dp[i-1][j]` represents having already solved the problem for a shorter `s1` (as if a character was deleted), `dp[i][j-1]` for a shorter `s2` (as if a character was inserted into `s1` to match), and `dp[i-1][j-1] + 1` for swapping a mismatched pair (substitution) -- each transition literally IS the corresponding edit operation, applied at unit cost.
--> **Canonical real-world use cases** -- spell-checkers suggesting corrections (directly connecting to the trie-based spell-checking use case in the Tries file, often used together -- trie for candidate generation, edit distance for ranking how "close" each candidate is), DNA sequence alignment (alongside LCS), and `diff`-style tools.

# Coin Change -- Minimum Coins and Count of Ways

--> Two closely related but DIFFERENTLY-STRUCTURED DP problems that are frequently confused -- both use unlimited supply of each coin denomination, but ask fundamentally different questions.

```python
def min_coins(coins, amount):                # minimum number of coins to make amount, or -1 if impossible
    dp = [float('inf')] * (amount + 1)
    dp[0] = 0
    for a in range(1, amount + 1):
        for coin in coins:
            if coin <= a and dp[a - coin] != float('inf'):
                dp[a] = min(dp[a], dp[a - coin] + 1)
    return dp[amount] if dp[amount] != float('inf') else -1

def count_ways(coins, amount):                # number of DISTINCT combinations that sum to amount
    dp = [0] * (amount + 1)
    dp[0] = 1                                   # exactly one way to make 0 -- use no coins
    for coin in coins:                           # coin is the OUTER loop here -- this ordering matters
        for a in range(coin, amount + 1):
            dp[a] += dp[a - coin]
    return dp[amount]
```

--> **Why `min_coins` loops amount-outer, coin-inner, but `count_ways` loops coin-outer, amount-inner** -- `min_coins` only cares about the best result for each amount regardless of WHICH coins contributed, so loop order doesn't affect correctness; `count_ways` is counting DISTINCT combinations, and processing one coin denomination fully before moving to the next is exactly what prevents counting `{1, 2}` and `{2, 1}` as two different ways -- each coin type is "decided how many times to use" once, in one pass, rather than being revisited in different orders across the amount loop.
--> **This is the general coin-change problem the DP file's Greedy section referenced directly** -- recall the `[1, 3, 4]`, target `6` counterexample where greedy fails (`4+1+1` vs the true optimal `3+3`) -- `min_coins` above is the correct, general DP solution to exactly that failure case, since it exhaustively considers every denomination's contribution to every amount rather than committing to the largest coin first.

# Deep Dive -- Recognizing Which Advanced DP Shape Applies

```text
Signal in the problem                                          Pattern
"best way to split/merge/parenthesize a sequence"                 Interval DP
State naturally lives on parent/child relationships                DP on trees
"count numbers in [0, N] with a digit property," N very large       Digit DP
"which subset of items," subset itself affects transitions           Bitmask DP
"longest/best subsequence," ORDER preserved but not contiguous        LIS-style DP (+ binary search for n log n)
"best contiguous run," single pass, resettable state                  Kadane's-style DP
"transform one sequence into another at minimum cost"                  Edit distance
"unlimited supply of items, minimum count OR number of combinations"    Coin-change-style DP
```

--> **The thread connecting all of this back to the DP file** -- every pattern here is still just "define the state, write the recurrence from smaller states, decide the fill order" -- what changes across this file is WHAT the state represents (a range, a subset, a digit position, a tree node) and what the transitions loop over, but the underlying discipline for designing a correct DP solution is identical to the DP file's process, just applied to shapes beyond a simple 1D or 2D index.
