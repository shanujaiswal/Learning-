# Modular Arithmetic

--> Modular arithmetic computes everything "wrapped around" a fixed modulus `m` -- `(a + b) % m`, `(a * b) % m`, etc. -- essential whenever a computed answer could grow astronomically large (factorials, combinatorial counts, big DP sums) but only the RESULT modulo some fixed number (commonly `10^9 + 7`, a large prime, chosen specifically to avoid overflow while staying prime for reasons covered below) is actually needed.
--> **Why addition and multiplication distribute over the modulus cleanly** -- `(a + b) % m == ((a % m) + (b % m)) % m`, and the identical property holds for multiplication -- this is exactly why intermediate results can be reduced modulo `m` at every step of a computation (keeping numbers small throughout) without changing the final answer.
--> **Modular division is NOT simply "divide, then mod"** -- division requires the MODULAR INVERSE of the divisor (a number `x` such that `divisor * x ≡ 1 (mod m)`) -- this is precisely why the modulus is chosen to be PRIME in most competitive/DP contexts, since a modular inverse is guaranteed to exist for every nonzero value when the modulus is prime (directly connecting to Fermat's Little Theorem below, which is the standard way to compute that inverse).

```python
MOD = 10 ** 9 + 7

def modular_inverse(a, mod=MOD):        # requires mod to be PRIME
    return pow(a, mod - 2, mod)           # Fermat's Little Theorem: a^(mod-1) ≡ 1 (mod m), so a^(mod-2) is a's inverse

def modular_divide(a, b, mod=MOD):
    return (a * modular_inverse(b, mod)) % mod
```

# GCD, LCM, and the Euclidean Algorithm

--> The GREATEST COMMON DIVISOR (GCD) of two numbers is the largest number dividing both evenly; the LEAST COMMON MULTIPLE (LCM) is the smallest number divisible by both -- the two are related by a simple identity that makes computing one trivial once the other is known.

```python
def gcd(a, b):                    # Euclidean algorithm
    while b:
        a, b = b, a % b
    return a

def lcm(a, b):
    return a * b // gcd(a, b)      # identity: gcd(a,b) * lcm(a,b) == a * b
```

--> **Why the Euclidean algorithm works, and why it's fast** -- `gcd(a, b) == gcd(b, a % b)`, because any number dividing both `a` and `b` must also divide their difference (and therefore their remainder) -- repeatedly replacing the pair with `(b, a % b)` shrinks the numbers by AT LEAST roughly a factor related to the golden ratio at each step, giving `O(log(min(a, b)))` -- one of the oldest known algorithms (documented over 2000 years ago) and still the standard approach today, precisely because nothing asymptotically faster for this exact problem has displaced it.
--> **Extended Euclidean algorithm (brief mention)** -- a small extension that additionally finds integer coefficients `x, y` such that `a*x + b*y = gcd(a, b)` -- this is the OTHER standard way (besides Fermat's Little Theorem above) to compute a modular inverse, and works even when the modulus isn't prime, as long as the number being inverted and the modulus are coprime.

# Sieve of Eratosthenes -- Finding All Primes Up To N

--> Instead of testing each number individually for primality (`O(sqrt(n))` per number, `O(n * sqrt(n))` total for all numbers up to `n`), the sieve finds ALL primes up to `n` at once by starting from every prime and marking every MULTIPLE of it as composite -- a genuinely different algorithmic shape from "check divisibility one number at a time."

```python
def sieve_of_eratosthenes(n):
    is_prime = [True] * (n + 1)
    is_prime[0] = is_prime[1] = False
    for i in range(2, int(n ** 0.5) + 1):
        if is_prime[i]:
            for multiple in range(i * i, n + 1, i):     # start at i*i -- smaller multiples already marked
                is_prime[multiple] = False
    return [i for i in range(2, n + 1) if is_prime[i]]
```

--> **Why the outer loop only needs to run to `sqrt(n)`** -- any composite number `≤ n` must have at least one prime factor `≤ sqrt(n)` (if both factors of a composite were greater than `sqrt(n)`, their product would exceed `n`) -- so every composite is guaranteed to get marked by the time the sieve has processed all primes up to `sqrt(n)`, making a larger outer bound unnecessary.
--> **Why starting each inner loop at `i*i`** -- every smaller multiple of `i` (like `2*i`, `3*i`, ..., up to `(i-1)*i`) was necessarily already marked composite by some SMALLER prime factor processed earlier -- starting at `i*i` avoids redundant re-marking work.
--> **Overall complexity -- `O(n log log n)`** -- a genuinely near-linear result (the harmonic-like sum `1/2 + 1/3 + 1/5 + 1/7 + ...` over primes converges extremely slowly to `log log n`), making the sieve dramatically faster than checking each number individually for any reasonably large `n`.

# Combinatorics -- Permutations, Combinations, and nCr

--> **Permutations** -- the number of ways to ARRANGE `r` items chosen from `n`, where ORDER matters: `P(n, r) = n! / (n - r)!`.
--> **Combinations** -- the number of ways to CHOOSE `r` items from `n`, where order does NOT matter: `C(n, r) = n! / (r! * (n - r)!)`, commonly written `nCr`.

```python
from math import comb, factorial

def n_choose_r(n, r):
    return comb(n, r)                # Python 3.8+ built-in, handles this directly and efficiently

def n_choose_r_pascals_triangle(n, r):    # DP approach -- avoids computing huge factorials directly
    dp = [[0] * (r + 1) for _ in range(n + 1)]
    for i in range(n + 1):
        dp[i][0] = 1                     # C(i, 0) is always 1 -- exactly one way to choose nothing
        for j in range(1, min(i, r) + 1):
            dp[i][j] = dp[i - 1][j - 1] + dp[i - 1][j]     # Pascal's triangle recurrence
    return dp[n][r]
```

--> **Why Pascal's triangle recurrence holds** -- `C(n, r) = C(n-1, r-1) + C(n-1, r)` -- to choose `r` items from `n`, either a specific item IS included (then choose the remaining `r-1` from the other `n-1`) or it ISN'T (then choose all `r` from the other `n-1`) -- these two cases are mutually exclusive and exhaustive, directly connecting to the DP file's "optimal/valid substructure" framing, just for counting rather than optimizing.
--> **Why the DP/factorial-free approach matters for large n and modular arithmetic** -- computing `n!` directly for large `n` produces an enormous number; combined with the modular-inverse technique above, `nCr mod p` is computed by precomputing factorials and inverse-factorials mod `p` once, then answering each query in `O(1)` -- a standard combined technique in competitive programming.

# Binary (Fast) Exponentiation

--> Computing `a^n` naively by multiplying `a` by itself `n` times costs `O(n)` -- fast exponentiation computes the same result in `O(log n)` by repeatedly SQUARING, exploiting the fact that `a^n` can be built from `a^(n/2)` squared (with an extra factor of `a` if `n` is odd).

```python
def fast_power(a, n, mod=None):
    result = 1
    a = a % mod if mod else a
    while n > 0:
        if n % 2 == 1:               # if the current bit is 1, fold this power of a into the result
            result = (result * a) % mod if mod else result * a
        a = (a * a) % mod if mod else a * a     # square a for the next bit
        n //= 2
    return result
```

--> **The bit-level intuition** -- this is exactly the same "process one bit at a time" idea as the Bit Manipulation file -- `n`'s binary representation directly determines which powers of `a` (`a^1, a^2, a^4, a^8, ...`, each obtained by repeatedly squaring) get multiplied into the final result, since any integer `n` is a sum of distinct powers of 2.
--> **Canonical real-world use case** -- modular exponentiation via this exact technique underlies RSA and other public-key cryptography, where computing `a^n mod m` for enormous `n` (hundreds of digits) must still complete in a practical amount of time -- `O(log n)` multiplications makes this feasible where `O(n)` multiplications would not.

# P vs NP and NP-Completeness (Conceptual)

--> **P** -- the class of decision problems solvable in POLYNOMIAL time (`O(n^k)` for some constant `k`) -- e.g. sorting, shortest path, and most algorithms covered across this entire file series.
--> **NP** -- the class of decision problems where a proposed SOLUTION can be VERIFIED in polynomial time, even if no known way exists to FIND that solution efficiently -- e.g. given a proposed Hamiltonian path (Advanced Graph Algorithms file), checking it's valid is fast, but finding one in the first place has no known polynomial algorithm.
--> **NP-complete** -- the hardest problems within NP, with the special property that EVERY problem in NP can be transformed ("reduced") into an NP-complete problem in polynomial time -- meaning a fast solution to any ONE NP-complete problem would immediately give a fast solution to ALL of them (and thus prove `P = NP`), which is exactly why NP-completeness is treated as strong evidence a problem has no efficient general solution, without it being an actual proof that none exists.
--> **Reductions** -- proving a new problem is NP-complete is normally done by showing a KNOWN NP-complete problem can be transformed into it in polynomial time -- if the new problem were efficiently solvable, that transformation would make the known hard problem efficiently solvable too, a contradiction (assuming `P ≠ NP`).
--> **Canonical NP-complete problems** -- the Hamiltonian path problem and the Traveling Salesman decision variant (both mentioned in the Advanced Graph Algorithms and Advanced DP Patterns files), the knapsack decision variant (the DP file's Knapsack solves it in pseudo-polynomial time specifically because weights are treated as small integers -- it's NOT a counterexample to NP-hardness in general), boolean satisfiability (SAT, the very first problem proven NP-complete), and graph coloring.
--> **Whether `P = NP`** -- one of the most famous open problems in computer science, unresolved as of today -- most researchers believe `P ≠ NP` (that some NP problems genuinely have no polynomial solution), but no proof exists either way.

# Approximation Algorithms (Conceptual)

--> When a problem is NP-hard, an exact optimal solution may be computationally infeasible for large inputs -- an approximation algorithm instead guarantees a solution within some PROVABLE FACTOR of optimal, in polynomial time, trading exactness for tractability.
--> **Canonical example -- the Traveling Salesman Problem** -- the bitmask DP exact solution (Advanced DP Patterns file) is exponential; a simple approximation (build a Minimum Spanning Tree, then traverse it via DFS) guarantees a tour at most 2x the optimal cost for instances satisfying the triangle inequality, in polynomial time -- directly connecting to Prim's/Kruskal's MST algorithms (Graphs file) being reused here as a building block for a completely different problem.
--> **The general trade-off this teaches** -- when exact optimality is provably intractable at scale, a "good enough, provably bounded, fast" answer is often the practically correct engineering choice over insisting on an exact but exponential algorithm -- the same pragmatic spirit as choosing a hash table's average-case `O(1)` over a guaranteed-but-more-complex alternative, covered in the Heaps and Hashing file.

# Quickselect

--> Finds the k-th smallest element in an unsorted array WITHOUT fully sorting it, using the same partitioning idea as quicksort (Sorting file) but recursing into only ONE side of the partition instead of both.

```python
import random

def quickselect(arr, k):        # returns the k-th smallest element (0-indexed)
    if len(arr) == 1:
        return arr[0]
    pivot = random.choice(arr)
    less = [x for x in arr if x < pivot]
    equal = [x for x in arr if x == pivot]
    greater = [x for x in arr if x > pivot]

    if k < len(less):
        return quickselect(less, k)
    elif k < len(less) + len(equal):
        return pivot
    else:
        return quickselect(greater, k - len(less) - len(equal))
```

--> **Why quickselect achieves `O(n)` average case while quicksort is `O(n log n)`** -- quicksort must recurse into BOTH sides of every partition to fully sort everything; quickselect only ever needs to recurse into the ONE side that's known to contain the k-th element, discarding the other side's elements entirely without further examining them -- this halves (roughly) the remaining work at each level rather than branching into two full recursive calls, collapsing the total work from `O(n log n)` down to `O(n)` on average.
--> **Worst case -- `O(n^2)`**, identical to quicksort's worst case (Sorting file) -- a consistently bad pivot choice (always picking the smallest or largest remaining element) degrades to scanning almost the whole array at every recursion level -- RANDOM pivot selection (as above) makes this worst case vanishingly unlikely rather than something an adversarial input could reliably trigger.
--> **Canonical real-world use case** -- finding the median, or any percentile, of a large dataset without paying for a full sort -- exactly the operation behind `numpy`'s `partition` function and database median/percentile queries.

# Randomized Algorithms -- Monte Carlo vs Las Vegas

--> A randomized algorithm deliberately uses randomness as part of its logic (not just as a nice-to-have optimization detail) -- quickselect's random pivot and the treap's random priorities (Advanced Trees file) are both examples already seen, formalized here into two distinct categories.
--> **Monte Carlo algorithms** -- ALWAYS run in bounded time, but have a small, controllable probability of returning an INCORRECT answer -- e.g. the Miller-Rabin primality test, which can occasionally (with tunably tiny probability) declare a composite number "probably prime."
--> **Las Vegas algorithms** -- ALWAYS return a CORRECT answer, but their running TIME is randomized/variable -- e.g. quickselect and randomized quicksort, which always eventually produce the right answer, but might (rarely, with bad luck in pivot selection) take longer than expected.
--> **The practical distinction that matters when choosing between them** -- Monte Carlo trades a tiny, quantifiable CORRECTNESS risk for a hard time guarantee (useful when a strict deadline matters more than absolute certainty, and the error probability can be driven arbitrarily low by repeating the test); Las Vegas trades a variable running time for a guarantee that whatever answer it eventually returns is definitely right (useful whenever correctness cannot be compromised at all).

# Formal Amortized Analysis -- Aggregate, Accounting, and Potential Methods

--> The Big-O file introduces amortized analysis informally via dynamic array resizing -- this section names the three FORMAL techniques used to actually PROVE an amortized bound rigorously, rather than just gesturing at "resizes happen rarely."
--> **Aggregate method** -- compute the TOTAL cost of a sequence of `n` operations directly, then divide by `n` -- e.g. for dynamic array appends, if array doubles each resize, total copying cost across `n` appends is bounded by `n + n/2 + n/4 + ... < 2n`, so amortized cost per append is `O(2n / n) = O(1)`.
--> **Accounting method** -- assign each operation an AMORTIZED CHARGE (possibly different from its actual cost) such that cheap operations are charged slightly MORE than they actually cost, banking the surplus as "credit" that expensive operations later withdraw from -- e.g. charge every append a flat 3 units: 1 unit pays for the append itself, and 2 units are banked as credit on that element, exactly enough credit accumulated by the time a resize is needed to pay for that resize's cost of copying every element.
--> **Potential method** -- define a POTENTIAL FUNCTION `Φ` mapping the data structure's current state to a number representing "stored-up potential energy" -- the amortized cost of an operation is defined as `actual_cost + Φ(after) - Φ(before)` -- e.g. for the dynamic array, `Φ = 2 * (number of used slots) - (array capacity)` rises steadily with each cheap append (banking potential) and drops sharply exactly when a resize consumes it, mirroring the accounting method's credit but expressed as a single mathematical function instead of a per-element ledger.
--> **Why three different methods for the same conclusion** -- aggregate is the simplest but only gives an AVERAGE across the whole sequence, not a per-operation bound; accounting and potential both give legitimate PER-OPERATION amortized bounds (useful when different operation types need different bounds), with potential being the more general and mathematically rigorous tool of the two, favored in formal proofs, while accounting is often the more intuitive one to reason through by hand.

# Computational Geometry Primer

--> **Convex hull** -- the smallest convex polygon (a shape where any line segment between two interior points stays entirely inside it) enclosing a given set of points -- computed efficiently (`O(n log n)`) by algorithms like Graham scan, which sorts points by angle from a reference point and then greedily keeps only points that keep the boundary turning consistently in one direction, discarding any point that would create a "dent."

```python
def convex_hull(points):        # Andrew's monotone chain algorithm, O(n log n)
    points = sorted(set(points))
    if len(points) <= 2:
        return points

    def cross(o, a, b):          # cross product -- positive if a->b turns left from o->a, negative if right
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower = []
    for p in points:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()             # last point creates a clockwise turn (a "dent") -- remove it
        lower.append(p)

    upper = []
    for p in reversed(points):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)

    return lower[:-1] + upper[:-1]      # concatenate both halves, dropping duplicated endpoints
```

--> **Closest pair of points** -- naively checking every pair is `O(n^2)`; a divide-and-conquer approach (directly connecting to the Recursion file's Divide and Conquer section) splits points by x-coordinate, recursively solves each half, then checks only a narrow strip of points near the dividing line for a closer cross-boundary pair -- achieving `O(n log n)` overall, since that boundary-strip check can be proven to only ever need to compare each point against a small constant number of neighbors.
--> **Sweep line technique** -- a general strategy for geometry problems -- imagine a vertical line sweeping left to right across all points/segments, maintaining a data structure (often a balanced BST or heap) of "currently active" geometric objects the line is passing through, updating it only at specific EVENT points (a segment starting, ending, or two segments crossing) rather than at every possible x-coordinate -- used for problems like counting line segment intersections or computing the area of overlapping rectangles, and conceptually similar to how the interval tree (Advanced Trees file) organizes overlap queries, just processed incrementally over a moving coordinate instead of stored in a static structure.

# Deep Dive -- How This File's Topics Actually Connect

```text
Topic                                Where it resurfaces elsewhere
Modular arithmetic / fast exponentiation   RSA cryptography, nCr mod p, any "answer mod 1e9+7" DP result
GCD/Euclidean algorithm                     LCM, modular inverse (extended Euclidean), fraction simplification
Sieve of Eratosthenes                        Any problem needing many primality checks up to a bound
Combinatorics / nCr                          Counting-style DP problems (Advanced DP Patterns file)
NP-completeness                              Explains WHY Hamiltonian path/TSP/knapsack-decision have no known poly algorithm
Approximation algorithms                     The practical fallback when NP-hardness makes exactness infeasible
Quickselect                                   A Las Vegas randomized algorithm; partitioning idea shared with quicksort
Randomized algorithms                         Treaps, skip lists (Advanced Trees file) both lean on randomness for balance
Amortized analysis (formal)                   Formalizes the Big-O file's dynamic array/hash resizing discussion
Computational geometry                        K-D trees (Advanced Trees file) accelerate nearest-neighbor queries geometry needs
```

--> **The overarching theme of this file** -- where the earlier files build UP a toolbox of algorithms for well-defined problems, this file adds the THEORETICAL vocabulary (P/NP, amortized proof techniques) for reasoning about when a fast exact algorithm can or can't exist at all, alongside a set of MATHEMATICAL primitives (modular arithmetic, combinatorics, number theory) that quietly underpin a large fraction of the problems the rest of the series solves.
