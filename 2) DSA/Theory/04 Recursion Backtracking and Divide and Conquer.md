# What Recursion Actually Is

--> A recursive function solves a problem by calling ITSELF on a smaller version of that same problem, until it reaches a case simple enough to answer directly -- every recursive function needs exactly two parts, and getting either one wrong causes infinite recursion (which eventually crashes with a stack overflow, directly connecting to the call-stack space discussion in the Big-O file).

--> **Base case** -- the simplest version of the problem, answered directly with no further recursive call -- this is what actually STOPS the recursion.
--> **Recursive case** -- calls itself on a smaller/simpler input, and combines that result to solve the current input.

```python
def factorial(n):
    if n <= 1:              # base case -- stops the recursion
        return 1
    return n * factorial(n - 1)   # recursive case -- smaller problem (n-1), combined by multiplying
```

```text
factorial(4)
  = 4 * factorial(3)
        = 3 * factorial(2)
              = 2 * factorial(1)
                    = 1                    <-- base case reached, starts returning back up
              = 2 * 1 = 2
        = 3 * 2 = 6
  = 4 * 6 = 24
```

# The Call Stack, Visualized

--> Each recursive call pushes a new FRAME onto the call stack (holding that call's local variables and where to resume afterward) -- frames pile up until the base case is hit, then unwind back down, each frame using its stored state to complete its own pending calculation.

```text
Stack grows downward as calls go deeper, then unwinds upward as they return:

factorial(1) -> returns 1
factorial(2) -> waiting on factorial(1), then computes 2*1
factorial(3) -> waiting on factorial(2), then computes 3*2
factorial(4) -> waiting on factorial(3), then computes 4*6
```

--> This is exactly why deep, unbounded recursion causes a **stack overflow** -- each frame consumes real memory, and a language's call stack has a finite size (Python defaults to roughly 1000 frames before raising `RecursionError`). A recursive solution that could recurse proportional to input size `n` is a real risk for large `n`, which is part of why an ITERATIVE equivalent (using an explicit stack/loop instead of language-level recursion) is sometimes preferred for production code processing unpredictable input sizes.

# Recurrence Relations -- Analyzing Recursive Time Complexity

--> A recurrence relation expresses a recursive algorithm's running time `T(n)` in terms of the running time of its smaller recursive calls -- solving it (or recognizing a known pattern) gives the actual Big-O complexity.

```text
T(n) = T(n-1) + O(1)          -- one recursive call, shrinking by 1 each time, O(1) work besides the call
                                  --> resolves to O(n)                          (factorial above)

T(n) = 2*T(n/2) + O(n)         -- two recursive calls, each on half the input, plus O(n) combining work
                                  --> resolves to O(n log n)                    (merge sort, covered in Sorting file)

T(n) = 2*T(n-1) + O(1)          -- two recursive calls, each only shrinking by 1
                                  --> resolves to O(2^n)                        (naive recursive Fibonacci, below)
```

```python
def fib_naive(n):                  # O(2^n) -- exponential, because of massively repeated re-computation
    if n <= 1:
        return n
    return fib_naive(n - 1) + fib_naive(n - 2)
```

```text
fib(5) recursion tree -- notice fib(3), fib(2), fib(1) are each recomputed from scratch multiple times:

                    fib(5)
                 /          \
            fib(4)          fib(3)
           /     \          /    \
       fib(3)  fib(2)   fib(2)  fib(1)
       /   \
   fib(2) fib(1)
```

--> This exact repeated-recomputation problem is precisely what Dynamic Programming (covered in its own file) fixes -- by caching/memoizing each unique subproblem's result the first time it's computed, the same recurrence collapses from `O(2^n)` down to `O(n)`.

# Divide and Conquer

--> A specific recursive strategy: DIVIDE the problem into smaller independent subproblems, CONQUER each one recursively, then COMBINE their results into the overall answer -- the general pattern behind merge sort, quicksort, and binary search (all covered in their own files).

```python
def merge_sort(arr):
    if len(arr) <= 1:                    # base case
        return arr
    mid = len(arr) // 2
    left = merge_sort(arr[:mid])          # divide + conquer (recursive call on the left half)
    right = merge_sort(arr[mid:])         # divide + conquer (recursive call on the right half)
    return merge(left, right)              # combine the two sorted halves into one sorted result
```

--> **Why this pattern often beats a purely iterative approach for certain problems** -- splitting into independent halves that don't need to know about each other lets each half be solved in isolation (and, in a parallel/multi-core context, genuinely simultaneously) -- the COMBINE step is where the pieces get reassembled, and its cost is exactly the `O(n)` term in the `T(n) = 2*T(n/2) + O(n)` recurrence above.

# Backtracking -- Recursion With Undo

--> Backtracking explores a decision tree of possible choices, and when a choice leads to a dead end (violates a constraint, or a full solution turns out invalid), it UNDOES that choice and tries a different one -- effectively a smart, pruned brute-force search rather than blindly generating every possibility.

```python
def solve_n_queens(n):
    results = []
    board = [-1] * n     # board[row] = column of the queen placed in that row

    def is_safe(row, col):
        for r in range(row):
            c = board[r]
            if c == col or abs(c - col) == abs(r - row):   # same column, or same diagonal
                return False
        return True

    def backtrack(row):
        if row == n:                     # base case -- successfully placed a queen in every row
            results.append(board[:])
            return
        for col in range(n):
            if is_safe(row, col):
                board[row] = col          # make a choice
                backtrack(row + 1)         # explore further with that choice
                board[row] = -1            # undo the choice (backtrack) before trying the next column
    backtrack(0)
    return results
```

--> **The three-part shape every backtracking solution follows** -- CHOOSE (try placing something / picking an option), EXPLORE (recurse deeper assuming that choice), UNCHOOSE (undo the choice before returning, so the NEXT sibling option starts from a clean state). Forgetting the "unchoose" step is the single most common backtracking bug -- it leaves stale state contaminating every subsequent branch of the search.
--> **Pruning** -- `is_safe()` above is a pruning check -- rather than placing all `n` queens and THEN checking validity (which would explore the full `n^n` search space), invalid placements are rejected immediately, cutting off entire useless branches of the recursion tree before they're ever explored. This is exactly what separates backtracking from naive brute force -- both explore a search space, but backtracking actively prunes it as early as possible.
--> **Canonical backtracking problems** -- N-Queens, generating all permutations/subsets/combinations, Sudoku solving, word search in a grid -- all share the "make a choice, recurse, undo, try the next choice" shape above.

# Deep Dive -- Recursion vs Iteration, and Tail Call Considerations

--> Every recursive algorithm CAN be rewritten iteratively using an explicit stack/loop to manage the same state the call stack would otherwise track -- recursion isn't more POWERFUL, just often more READABLE for problems with a naturally recursive structure (trees, divide-and-conquer, backtracking).
--> **Tail recursion** -- when the recursive call is the VERY LAST operation in a function, with nothing left to do after it returns (`return factorial_helper(n-1, n*acc)` rather than `return n * factorial(n-1)`, which still has a multiplication to do AFTER the recursive call returns). Some languages (Scheme, and to a lesser extent JavaScript per spec, though inconsistently implemented in engines) optimize tail calls into a simple loop internally, avoiding stack growth entirely -- Python and Java notably do NOT perform this optimization, so a tail-recursive-STYLE function in Python still consumes stack frames exactly like any other recursive call, and can still overflow on deep inputs.
--> **Practical guidance** -- for problems with unpredictable or unbounded depth in Python specifically (deep tree traversal on untrusted/unbounded data, for instance), converting to an explicit iterative approach with your own stack is a genuine, sometimes necessary safeguard against `RecursionError`, not just a style preference.
