# The `Queue` Interface -- FIFO Processing

--> `Queue<E>` extends `Collection<E>` and models a FIFO (First-In-First-Out) structure: elements are added at the tail and removed from the head, like a real-world line.
--> `Queue` deliberately offers TWO forms of most operations -- one that throws an exception on failure, one that returns a special value (`null` or `false`) instead:

| Operation | Throws exception on failure | Returns special value on failure |
|---|---|---|
| Insert | `add(e)` -- throws `IllegalStateException` if capacity-restricted and full | `offer(e)` -- returns `false` |
| Remove | `remove()` -- throws `NoSuchElementException` if empty | `poll()` -- returns `null` |
| Examine (peek without removing) | `element()` -- throws `NoSuchElementException` if empty | `peek()` -- returns `null` |

--> **Practical guidance:** prefer the `offer`/`poll`/`peek` family in most code -- checking for `null`/`false` is usually more convenient than catching exceptions, especially in loops that drain a queue until empty.

```java
Queue<Integer> queue = new LinkedList<>();     // LinkedList implements Queue too
queue.offer(1);
queue.offer(2);
queue.offer(3);
System.out.println(queue.poll());   // 1 -- removes and returns the head
System.out.println(queue.peek());   // 2 -- looks at head without removing
while (!queue.isEmpty()) {
    System.out.println(queue.poll());
}
```

# PriorityQueue -- A Binary Heap, Not FIFO Order

--> `PriorityQueue<E>` does NOT preserve insertion order -- it always returns the SMALLEST element first (natural ordering, via `Comparable`) unless a `Comparator` is supplied at construction. Internally it's a **binary min-heap** stored in a plain array.
--> **Heap structure:** a complete binary tree stored implicitly in an array, where for index `i`: left child = `2*i + 1`, right child = `2*i + 2`, parent = `(i - 1) / 2`. The heap INVARIANT is that every parent is <= both its children (for a min-heap) -- this guarantees the smallest element is always at index 0, but the array is NOT fully sorted otherwise.

```java
PriorityQueue<Integer> minHeap = new PriorityQueue<>();
minHeap.offer(5);
minHeap.offer(1);
minHeap.offer(3);
minHeap.offer(2);
System.out.println(minHeap.poll());  // 1  -- smallest first
System.out.println(minHeap.poll());  // 2
// NOTE: iterating the queue directly (for-each) does NOT give sorted order -- only repeated poll() does!

PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
maxHeap.offer(5); maxHeap.offer(1); maxHeap.offer(3);
System.out.println(maxHeap.poll());  // 5 -- largest first

// Custom objects need a Comparator or must implement Comparable
record Task(String name, int priority) {}
PriorityQueue<Task> tasks = new PriorityQueue<>(Comparator.comparingInt(Task::priority));
tasks.offer(new Task("low", 5));
tasks.offer(new Task("urgent", 1));
System.out.println(tasks.poll());  // Task[name=urgent, priority=1]
```

--> **Complexity:** `offer()`/`add()` is O(log n) (sift-up to restore heap order), `poll()`/`remove()` is O(log n) (swap root with last element, then sift-down), `peek()` is O(1) (always the array's first slot).
--> **Common use cases:** Dijkstra's shortest path (DSA File 09), task scheduling by priority, "K largest/smallest elements" problems, merge-K-sorted-lists.

# The `Deque` Interface -- Double-Ended Queue

--> `Deque<E>` ("deck") extends `Queue<E>` and supports insertion/removal at BOTH ends: `addFirst`/`addLast`, `removeFirst`/`removeLast`, `peekFirst`/`peekLast`, each with `offer`-style non-throwing counterparts too.
--> Because it supports both ends, `Deque` can act as EITHER a stack (LIFO, use the "First" methods) OR a queue (FIFO, use `addLast`/`pollFirst`) OR both at once.

```java
Deque<Integer> deque = new ArrayDeque<>();
deque.addFirst(1);       // [1]
deque.addLast(2);        // [1, 2]
deque.addFirst(0);        // [0, 1, 2]
System.out.println(deque.peekFirst());  // 0
System.out.println(deque.peekLast());   // 2
deque.pollFirst();        // removes 0 -> [1, 2]
deque.pollLast();         // removes 2 -> [1]
```

# ArrayDeque -- The Modern Default for Stack AND Queue

--> `ArrayDeque` is backed by a resizable circular array (a "circular buffer") -- it keeps `head` and `tail` indices that wrap around the array bounds, so adding/removing at EITHER end is O(1) amortized with no shifting required.

```text
Circular buffer of capacity 8, head=2, tail=5:
index:   0    1    2    3    4    5    6    7
value:  [ ]  [ ]  [A]  [B]  [C]  [ ]  [ ]  [ ]
                   ^head          ^tail (next free slot)

addFirst(X) -> head decrements (wrapping to 7 if at 0), X placed there -- no shifting of A, B, C needed
```

--> **Why `ArrayDeque` beats `LinkedList` for stack/queue use in almost all cases:** no per-element `Node` object overhead (better memory density and cache locality), and no `null` element allowed (which actually helps -- `LinkedList` allows `null`, which creates ambiguity with `poll()`'s "empty means null" signal; `ArrayDeque` disallows `null` specifically to avoid that ambiguity).
--> `ArrayDeque` does NOT implement `List` (unlike `LinkedList`), so it has no `get(index)` -- it's purely a deque/stack/queue, which is exactly its point: a smaller, more focused, faster contract.

```java
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1);      // push/pop = stack operations, alias for addFirst/removeFirst
stack.push(2);
stack.push(3);
System.out.println(stack.pop());   // 3 -- LIFO
System.out.println(stack.peek());  // 2

Deque<Integer> asQueue = new ArrayDeque<>();
asQueue.offer(1);   // offer/poll = queue operations, alias for addLast/removeFirst (via Queue interface)
asQueue.offer(2);
System.out.println(asQueue.poll()); // 1 -- FIFO
```

# The Legacy `Stack` Class -- Avoid in New Code

--> `java.util.Stack` extends `Vector` (!), meaning it inherits ALL of `Vector`'s `List` methods (`get(i)`, `add(i, e)`, `remove(i)`) in addition to stack operations (`push`, `pop`, `peek`) -- this breaks encapsulation badly: nothing stops code from calling `stack.add(0, x)` and inserting at the BOTTOM of what's supposed to be a stack, silently corrupting LIFO order.
--> It's also `synchronized` on every method (inherited from `Vector`), adding unnecessary locking overhead in single-threaded code.

```java
Stack<Integer> stack = new Stack<>();     // legacy -- generally avoid in new code
stack.push(1);
stack.push(2);
stack.pop();               // 2
// stack.add(0, 99);       // legal! breaks the stack invariant -- this is the core design flaw

// Modern replacement:
Deque<Integer> modernStack = new ArrayDeque<>();  // no List methods to accidentally misuse
modernStack.push(1);
modernStack.push(2);
modernStack.pop();
```

--> The official Java documentation itself recommends `Deque` (`ArrayDeque`) over `Stack` for stack behavior, and over `LinkedList` for queue behavior, in modern code.

# Comparison Table

| Structure | Backing | Ends supported | Ordering | Thread-safe | Modern recommendation |
|---|---|---|---|---|---|
| `LinkedList` (as Queue/Deque) | Doubly-linked nodes | Both | FIFO or LIFO | No | Acceptable, but `ArrayDeque` usually faster |
| `ArrayDeque` | Circular array | Both | FIFO or LIFO | No | **Preferred** for stack and queue use |
| `PriorityQueue` | Binary heap (array) | Head only (min/max) | Priority order, not FIFO | No | Use when priority ordering is needed |
| `Stack` (legacy) | `Vector` (dynamic array) | Top only (conceptually) | LIFO | Yes (coarse) | Avoid -- use `ArrayDeque` |

# Real-World Use Cases

--> **Stack (`ArrayDeque` via push/pop):** undo/redo functionality, expression evaluation and parsing (matching brackets, converting infix to postfix), depth-first search (DFS) traversal, backtracking algorithms, call-stack simulation.
--> **Queue (`ArrayDeque` via offer/poll, or `LinkedList`):** breadth-first search (BFS) traversal, task/job scheduling (process in arrival order), producer-consumer pipelines, print/request queues.
--> **PriorityQueue:** Dijkstra's algorithm and A* pathfinding (always expand the lowest-cost node next), scheduling by urgency, event simulation (process the next event chronologically), "top-K" style problems.
--> **Deque used as both ends:** sliding window algorithms (maintain a window of max/min efficiently -- the "monotonic deque" pattern), palindrome checking, work-stealing thread pools (steal from the opposite end other threads push to, in concurrent variants).

# Common Gotchas

--> **`PriorityQueue`'s iterator does not return sorted order** -- only repeatedly calling `poll()` gives elements in priority order; iterating with a for-each just walks the internal array in heap-storage order.
--> **`ArrayDeque` does not allow `null` elements** -- `NullPointerException` is thrown, unlike `LinkedList` which permits `null`.
--> **`Queue.remove()`/`element()` throw on empty; `poll()`/`peek()` return `null`** -- mixing these up is a common source of unexpected `NoSuchElementException` vs silent `null` bugs.
--> **`Stack` (legacy class) is not a `Deque`** -- it predates the `Deque` interface entirely and is unrelated to `ArrayDeque`'s design.

# Best Practices

--> Use `ArrayDeque` for BOTH stack and queue needs in new code -- avoid `java.util.Stack` and generally avoid `LinkedList` for this role too.
--> Use `PriorityQueue` whenever "always process the smallest/largest/highest-priority item next" is the requirement, not plain FIFO.
--> Prefer `offer`/`poll`/`peek` over `add`/`remove`/`element` when writing loops that need to check for empty/full without exception-handling overhead.
--> When ordering by a custom rule, pass a `Comparator` to `PriorityQueue`'s constructor rather than making the element type implement `Comparable` if the "natural" order and the "priority" order might differ.
