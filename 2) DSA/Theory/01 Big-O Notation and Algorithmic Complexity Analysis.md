# Why Complexity Analysis Matters

--> Two solutions to the same problem can both be "correct" and still differ by orders of magnitude in how they scale -- an algorithm that works fine on 100 items can become unusable on 10 million. Complexity analysis is the vocabulary for reasoning about that scaling BEFORE running the code, purely by looking at its structure.
--> This is the single most reused skill across every other DSA topic -- every sorting algorithm, every tree operation, every graph traversal in later files gets evaluated primarily by its time and space complexity.

# Big-O Notation -- Describing the Worst Case Growth Rate

--> Big-O describes how an algorithm's running time (or memory use) GROWS as the input size `n` grows, ignoring constant factors and lower-order terms -- it answers "if I double the input, roughly how much more work happens?" not "exactly how many milliseconds will this take."

```text
O(1)        -- Constant       -- array index lookup, hash table lookup
O(log n)    -- Logarithmic     -- binary search, balanced tree operations
O(n)        -- Linear          -- a single loop over the input
O(n log n)  -- Linearithmic    -- efficient sorting (merge sort, quicksort average case)
O(n^2)      -- Quadratic       -- nested loops over the input (bubble sort, naive pair-checking)
O(2^n)      -- Exponential     -- brute-force subsets, naive recursive Fibonacci
O(n!)       -- Factorial       -- brute-force permutations
```

```python
def sum_array(arr):        # O(n) -- one pass, one operation per element
    total = 0
    for x in arr:
        total += x
    return total

def has_duplicate_pair(arr):   # O(n^2) -- nested loop, checks every pair
    for i in range(len(arr)):
        for j in range(i + 1, len(arr)):
            if arr[i] == arr[j]:
                return True
    return False
```

--> **Why constants are dropped** -- `O(3n)` and `O(n)` are both written `O(n)`, because for large enough `n`, the SHAPE of the growth curve (linear vs quadratic vs logarithmic) dominates any fixed multiplier -- an `O(n)` algorithm with a large constant will still eventually beat an `O(n^2)` algorithm as `n` grows, even if it looks slower on tiny inputs.
--> **Why lower-order terms are dropped** -- `O(n^2 + n)` is written `O(n^2)`, because as `n` grows, the `n^2` term completely dwarfs the `n` term -- it stops mattering to the overall growth shape.

# Big-O, Big-Theta, and Big-Omega

--> **Big-O (O)** -- an UPPER bound -- "this algorithm never does worse than this."
--> **Big-Omega (Ω)** -- a LOWER bound -- "this algorithm never does better than this."
--> **Big-Theta (Θ)** -- a TIGHT bound -- both the upper and lower bound match, meaning this is genuinely the algorithm's exact growth rate, not just a worst-case ceiling.
--> **Practical usage note** -- in everyday conversation and interviews, "Big-O" is almost always used loosely to mean "the tight/typical bound," even though strictly it only promises an upper bound -- this file follows that common convention too, but it's worth knowing the more precise terms exist.

# Best, Average, and Worst Case

--> The SAME algorithm can have different complexities depending on the input -- e.g. searching for a target that happens to be the very first element vs one that isn't present at all.

```python
def linear_search(arr, target):
    for i, val in enumerate(arr):
        if val == target:
            return i     # Best case: O(1) -- target is the first element
    return -1            # Worst case: O(n) -- target is absent, scans everything
```

--> **Best case** is rarely useful for decision-making (almost anything has a lucky best case) -- **worst case** is the standard default for comparing algorithms, since it's the guarantee that actually holds regardless of input. **Average case** matters when the worst case is rare in practice but still theoretically possible (quicksort's O(n²) worst case vs its O(n log n) average case, covered in the Sorting file).

# Analyzing Time Complexity by Reading Code Structure

--> **Sequential statements** -- add complexities. A loop followed by another loop is `O(n) + O(n) = O(n)`, not `O(n^2)`.
--> **Nested loops** -- multiply complexities. A loop inside a loop, both over `n`, is `O(n) * O(n) = O(n^2)`.
--> **Loops that halve the input each time** -- logarithmic. A `while n > 1: n = n // 2` runs roughly `log2(n)` times.
--> **Recursive calls** -- complexity depends on both how many recursive calls happen and how the input shrinks each call -- covered in depth in the Recursion file's recurrence relation discussion.

```python
def example(arr):          # arr has n elements
    for x in arr:           # O(n)
        print(x)
    for x in arr:           # O(n) -- sequential with the loop above, not nested
        print(x)
    # Total: O(n) + O(n) = O(n)

def example2(arr):
    for x in arr:            # O(n)
        for y in arr:        # O(n) nested inside -- multiplies
            print(x, y)
    # Total: O(n * n) = O(n^2)
```

# Space Complexity

--> Measures how much EXTRA memory an algorithm uses relative to input size, beyond the input itself -- just as important as time complexity when memory is constrained, and there's frequently a direct trade-off between the two (using more memory to cache results and run faster, or using less memory at the cost of recomputation).

```python
def reverse_in_place(arr):      # O(1) extra space -- modifies the existing array, no new structure allocated
    left, right = 0, len(arr) - 1
    while left < right:
        arr[left], arr[right] = arr[right], arr[left]
        left += 1
        right -= 1

def reverse_new_array(arr):     # O(n) extra space -- allocates a brand new array the size of the input
    return arr[::-1]
```

--> **Recursive call stack space** -- every recursive call adds a frame to the call stack, which counts as space -- a recursive function making `n` nested calls before returning uses O(n) stack space even if it allocates no other data structure at all, which is exactly why very deep recursion (covered in the Recursion file) can hit a stack overflow before it hits any other resource limit.

# Deep Dive -- Why O(n log n) Is the Practical Ceiling for General-Purpose Sorting

--> It can be mathematically proven that any COMPARISON-BASED sorting algorithm (one that only learns information by comparing pairs of elements) requires at least `O(n log n)` comparisons in the worst case -- there are `n!` possible orderings of `n` elements, and each comparison can only narrow down which ordering it is by roughly a factor of 2, so distinguishing between `n!` possibilities takes at least `log2(n!)` comparisons, which works out to `O(n log n)`.
--> This is exactly why merge sort and quicksort (both `O(n log n)`, covered in the Sorting file) are considered near-optimal for general sorting, and why non-comparison-based sorts (like radix sort or counting sort, which exploit extra structure about the values being sorted rather than pure comparisons) are the only way to beat that `O(n log n)` bound -- they're not violating the proof, they're using more information than a pure comparison is allowed to use.

# Deep Dive -- Amortized Complexity

--> Some operations are usually fast but OCCASIONALLY expensive -- amortized analysis looks at the AVERAGE cost across a long sequence of operations, rather than the worst single operation in isolation.
--> **Classic example -- dynamic array resizing** (like Python's `list.append()` or Java's `ArrayList.add()`) -- appending is normally O(1), but when the underlying array is full, it must allocate a new, larger array (commonly double the size) and copy every existing element over, an O(n) operation for that one call.

```text
Appends:  1   2   3   4   5   6   7   8   9  ...
Cost:     1   1   1   4   1   1   1   1   8  ...
                       ^-- resize+copy happens here (array was full at size 4)
```

--> Even though SOME individual appends cost O(n), they happen rarely enough (each resize roughly doubles capacity, so resizes become exponentially less frequent) that the TOTAL cost of `n` appends is still `O(n)` overall -- averaging out to `O(1)` AMORTIZED per append, which is why "appending to a dynamic array is O(1)" is the standard claim, even though it's not literally true for every single call.
