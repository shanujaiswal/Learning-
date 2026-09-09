# Why Measure Algorithm Performance Abstractly

--> Two different pieces of code solving the SAME problem can have wildly different performance characteristics as input size grows -- Big-O notation gives a language-agnostic, hardware-agnostic way to describe HOW an algorithm's running time (or memory usage) scales as input size grows, without depending on which specific machine or language it happens to run on.

# What Big-O Actually Measures

--> Big-O describes the WORST-CASE growth rate of an algorithm's running time as a function of input size `n`, focusing on how it scales for LARGE `n`, ignoring constant factors and lower-order terms that become irrelevant at scale.

```
An algorithm that does exactly 3n + 100 operations is still O(n) --
the constant "3" and the added "100" don't change the fundamental
growth pattern as n gets large; only the "n" term matters asymptotically.
```

# Common Complexity Classes, From Best to Worst

## O(1) -- Constant Time

--> The running time doesn't depend on input size at all -- accessing an array element by index, or a hash map lookup (covered in the Data Structures file), are the classic examples.

```javascript
function getFirst(arr) {
  return arr[0];   // Always exactly one operation, regardless of whether arr has 10 or 10 million elements
}
```

## O(log n) -- Logarithmic Time

--> The running time grows very slowly -- doubling the input size adds only ONE more step. Binary search (and balanced tree operations, covered in the Data Structures file) are the canonical example -- each comparison eliminates HALF the remaining search space.

```javascript
function binarySearch(sortedArr, target) {
  let low = 0, high = sortedArr.length - 1;
  while (low <= high) {
    const mid = Math.floor((low + high) / 2);
    if (sortedArr[mid] === target) return mid;
    if (sortedArr[mid] < target) low = mid + 1;
    else high = mid - 1;
  }
  return -1;
}
// A billion-element sorted array needs at most ~30 comparisons -- log2(1,000,000,000) ≈ 30
```

## O(n) -- Linear Time

--> The running time grows in direct proportion to input size -- looking through every element once (a simple loop, `.find()`, `.filter()`, covered in the Array Methods file).

```javascript
function findMax(arr) {
  let max = arr[0];
  for (const n of arr) {          // Visits each element exactly once
    if (n > max) max = n;
  }
  return max;
}
```

## O(n log n) -- Log-Linear Time

--> The complexity class most efficient general-purpose SORTING algorithms achieve (Merge Sort, Quick Sort's average case, Heap Sort) -- generally considered "good enough" performance for sorting, and provably the best possible for any COMPARISON-based sorting algorithm.

## O(n²) -- Quadratic Time

--> Typically arises from NESTED loops, each iterating over the input -- a common, often-avoidable performance trap.

```javascript
function hasDuplicate(arr) {
  for (let i = 0; i < arr.length; i++) {
    for (let j = i + 1; j < arr.length; j++) {   // Nested loop -- O(n) work for EACH of the n outer iterations
      if (arr[i] === arr[j]) return true;
    }
  }
  return false;
}
// For 10 elements: ~50 comparisons. For 10,000 elements: ~50,000,000 comparisons -- growth is dramatic
```

--> This exact function can be rewritten to O(n) using a hash set (covered in the Data Structures file) to track seen values -- illustrating precisely why understanding complexity matters practically, not just academically: the SAME problem, solved with a different data structure, can be orders of magnitude faster at scale.

```javascript
function hasDuplicateFast(arr) {
  const seen = new Set();
  for (const n of arr) {
    if (seen.has(n)) return true;   // O(1) average-case hash set lookup, instead of an inner O(n) loop
    seen.add(n);
  }
  return false;
}
```

## O(2ⁿ) and O(n!) -- Exponential and Factorial Time

--> Growth so explosive that even modest input sizes become computationally infeasible -- a naive recursive Fibonacci implementation (recomputing the same subproblems repeatedly) is O(2ⁿ); the classic Traveling Salesman brute-force solution (trying every possible ordering of cities) is O(n!). Algorithms in this class are generally only usable for very small inputs, or require an entirely different, smarter approach (like Dynamic Programming, covered below) to become practical.

# Space Complexity -- The Other Half of the Trade-off

--> Big-O also describes MEMORY usage, not just time -- an algorithm can trade time for space or vice versa, and the "best" algorithm depends on which resource is more constrained for a specific situation (e.g. embedded/IoT devices, covered in the Ethical Hacking IoT file, often have severe memory constraints that make a slower, memory-lean algorithm preferable to a faster, memory-hungry one).

```javascript
// O(n) time, O(n) extra space -- uses a hash set to track duplicates
function hasDuplicateFast(arr) { /* as shown above */ }

// O(n log n) time, O(1) extra space -- sorts in place first, then checks adjacent elements
function hasDuplicateSorted(arr) {
  const sorted = [...arr].sort((a, b) => a - b);
  for (let i = 1; i < sorted.length; i++) {
    if (sorted[i] === sorted[i - 1]) return true;
  }
  return false;
}
```

# Core Algorithmic Techniques

## Divide and Conquer

--> Break a problem into smaller subproblems of the SAME type, solve each recursively, then combine their results -- Merge Sort is the canonical example, recursively splitting an array in half until each piece has one element, then merging sorted halves back together.

```javascript
function mergeSort(arr) {
  if (arr.length <= 1) return arr;
  const mid = Math.floor(arr.length / 2);
  const left = mergeSort(arr.slice(0, mid));
  const right = mergeSort(arr.slice(mid));
  return merge(left, right);
}

function merge(left, right) {
  const result = [];
  let i = 0, j = 0;
  while (i < left.length && j < right.length) {
    result.push(left[i] <= right[j] ? left[i++] : right[j++]);
  }
  return [...result, ...left.slice(i), ...right.slice(j)];
}
```

## Dynamic Programming -- Avoiding Redundant Recomputation

--> Applicable when a problem can be broken into OVERLAPPING subproblems -- rather than recomputing the same subproblem's answer repeatedly (as the naive exponential Fibonacci does), Dynamic Programming stores ("memoizes," directly connecting to the Memoization pattern covered in the Higher-Order Functions file) each subproblem's result the first time it's computed, and reuses it instantly on every subsequent need.

```javascript
// Naive recursive Fibonacci -- O(2^n), recomputes the same values over and over
function fibNaive(n) {
  if (n <= 1) return n;
  return fibNaive(n - 1) + fibNaive(n - 2);
}

// Dynamic Programming version -- O(n), each value computed exactly once
function fibDP(n) {
  const memo = [0, 1];
  for (let i = 2; i <= n; i++) {
    memo[i] = memo[i - 1] + memo[i - 2];
  }
  return memo[n];
}
```

--> This exact technique -- trading memory for a dramatic time improvement by caching subproblem results -- underlies solutions to a huge range of classic problems (shortest paths, edit distance between two strings, knapsack-style optimization problems) and is one of the most consistently high-value algorithmic techniques to recognize when it applies.

## Greedy Algorithms

--> Makes the locally optimal choice at each step, without reconsidering past choices, hoping (and for SPECIFIC problems, being provably guaranteed) that this leads to a globally optimal result -- simpler and faster than exhaustively exploring every possibility, but only correct for certain problem structures, not universally applicable.
--> Dijkstra's shortest-path algorithm is a classic greedy algorithm -- at each step, it commits to the closest unvisited node found so far, and this greedy commitment is provably always correct for graphs with non-negative edge weights, directly connecting to the A* search algorithm covered in the Artificial Intelligence folder, which is essentially Dijkstra's algorithm enhanced with a heuristic to guide the search more efficiently toward a specific goal.

# Why This Matters in Everyday Full-Stack Work

--> Most day-to-day web development doesn't require inventing new algorithms -- it requires RECOGNIZING when an existing piece of code has accidentally fallen into an inefficient pattern (a nested loop searching a large array repeatedly, an N+1 query pattern covered in the GraphQL file) and knowing which data structure or technique fixes it. Big-O thinking is less about esoteric algorithm design and more about developing the instinct to ask "how does this scale as the data grows" BEFORE it becomes a real production performance problem.
