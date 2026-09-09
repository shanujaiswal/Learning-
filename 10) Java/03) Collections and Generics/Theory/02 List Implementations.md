# The `List` Interface -- Ordered, Indexed, Duplicates Allowed

--> `List<E>` extends `Collection<E>` and adds INDEX-based access -- every element has a position (`0` to `size()-1`), duplicates are allowed, and insertion order is preserved unless you explicitly sort it.
--> The three main implementations are `ArrayList`, `LinkedList`, and the legacy `Vector` -- they all satisfy the same `List` contract but have very different internal structures and performance characteristics, which is the entire point of this file.

```java
List<String> list = new ArrayList<>();
list.add("a");            // index 0
list.add("b");            // index 1
list.add(0, "z");         // insert at index 0 -- shifts "a","b" right
System.out.println(list); // [z, a, b]
System.out.println(list.get(1));  // "a"
```

# ArrayList -- Backed by a Dynamic (Resizable) Array

--> Internally, `ArrayList` wraps a plain `Object[]` array. `get(i)` is a direct array index into that backing array -- that's why it's O(1).
--> When the array is full and you `add()` another element, `ArrayList` allocates a NEW, larger array (in OpenJDK, capacity grows to roughly `1.5x` the old capacity) and copies every existing element into it -- this is the same amortized-cost resizing pattern common to all dynamic arrays.

```text
Initial capacity (default, no-arg constructor): 0 (empty array); grows to 10 on first add in older JDKs' internal handling
Growth formula (OpenJDK): newCapacity = oldCapacity + (oldCapacity >> 1)   // roughly old * 1.5
```

```java
List<Integer> nums = new ArrayList<>();       // capacity 0 initially, lazily allocated
for (int i = 0; i < 20; i++) {
    nums.add(i);                               // triggers reallocation a handful of times, not 20
}

List<Integer> presized = new ArrayList<>(1000); // pre-allocate capacity 1000 -- avoids repeated resizing
```

--> **Insertion/removal in the middle or at the front is O(n)** -- every element after the insertion point must be shifted using `System.arraycopy` internally. Appending at the END (`add(element)`, no index) is amortized O(1).

# LinkedList -- Backed by a Doubly-Linked List

--> `LinkedList` stores each element in a separate `Node` object containing the value plus references to the `previous` and `next` nodes -- there is no contiguous array at all.

```text
null <- [prev|A|next] <-> [prev|B|next] <-> [prev|C|next] -> null
              ^head                              ^tail
```

--> **`get(i)` is O(n)** -- there's no direct index; the list must WALK from the head (or tail, whichever is closer) node by node until it reaches index `i`. This is the single biggest reason `LinkedList` is rarely the right default choice.
--> **Insertion/removal at a KNOWN node (e.g., via `Iterator.remove()`, or at the head/tail) is O(1)** -- it's just re-pointing a few references, no shifting required.
--> `LinkedList` implements both `List` and `Deque`, so it natively supports `addFirst()`, `addLast()`, `removeFirst()`, `removeLast()`, `peek()`, `poll()` -- making it usable as a stack, queue, or deque (though `ArrayDeque` is now generally preferred for that role, see File 04).

```java
LinkedList<String> ll = new LinkedList<>();
ll.addFirst("b");
ll.addFirst("a");
ll.addLast("c");                 // [a, b, c]
ll.get(1);                       // O(n) -- walks from head or tail
ll.removeFirst();                // O(1) -- just re-links head pointer
```

# Vector -- The Legacy Synchronized ArrayList

--> `Vector` predates the Collections Framework (it's from Java 1.0) and was retrofitted to implement `List` in Java 1.2. Structurally it's almost identical to `ArrayList` (backed by a dynamic array), but EVERY method is `synchronized`.
--> **Why it's mostly avoided today:** synchronizing every single method (even in single-threaded code where no synchronization is needed) adds locking overhead for no benefit, and method-level synchronization doesn't even make compound operations (like "check then add") thread-safe anyway -- you'd still need external locking for those. Modern code uses `Collections.synchronizedList(new ArrayList<>())` or, better, `CopyOnWriteArrayList` (from `java.util.concurrent`) for genuinely concurrent needs.
--> `Vector`'s growth strategy also differs slightly: by default it DOUBLES capacity (`newCapacity = oldCapacity * 2`) rather than `ArrayList`'s 1.5x, unless a `capacityIncrement` is specified in its constructor.

# Time Complexity Comparison

| Operation | `ArrayList` | `LinkedList` | `Vector` |
|---|---|---|---|
| `get(index)` | O(1) | O(n) | O(1) |
| `add(element)` (at end) | O(1) amortized | O(1) | O(1) amortized (synchronized) |
| `add(index, element)` | O(n) | O(n) to find + O(1) to link | O(n) |
| `remove(index)` | O(n) | O(n) to find + O(1) to unlink | O(n) |
| `addFirst()` / `removeFirst()` | O(n) (shifts everything) | O(1) | O(n) |
| `contains(element)` | O(n) | O(n) | O(n) |
| Memory overhead per element | Low (just the reference in array) | High (Node object: value + 2 references) | Low |
| Thread-safe? | No | No | Yes (coarse-grained) |

--> **Practical takeaway:** `ArrayList` wins for almost every real workload because random access is O(1), it has better cache locality (contiguous memory beats scattered `Node` objects for CPU cache performance), and even "insert at front" workloads are often better served by `ArrayDeque` than `LinkedList`.

# Iteration Methods

```java
List<String> list = new ArrayList<>(List.of("a", "b", "c"));

// 1) Enhanced for-loop (for-each) -- uses Iterator internally, cleanest for read-only iteration
for (String s : list) {
    System.out.println(s);
}

// 2) Classic indexed for-loop -- only efficient for ArrayList (O(1) get); avoid on LinkedList (O(n) get each time -> O(n^2) total!)
for (int i = 0; i < list.size(); i++) {
    System.out.println(list.get(i));
}

// 3) Iterator -- required when you need to REMOVE elements during iteration safely
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String s = it.next();
    if (s.equals("b")) {
        it.remove();               // safe -- removes via the iterator, not the list directly
    }
}

// 4) ListIterator -- bidirectional, allows set() and add() during iteration (List-only, see File 06)
ListIterator<String> lit = list.listIterator();
while (lit.hasNext()) {
    lit.set(lit.next().toUpperCase());
}

// 5) forEach with a lambda (Java 8+)
list.forEach(System.out::println);

// 6) Stream (Java 8+) -- for transformation/filtering pipelines, not simple iteration
list.stream().filter(s -> s.startsWith("a")).forEach(System.out::println);
```

--> **Gotcha: classic indexed loop on a `LinkedList`** -- `for (int i = 0; i < list.size(); i++) list.get(i)` on a `LinkedList` is silently O(n^2) overall, because each `get(i)` re-walks the list from an end. Always use an iterator or for-each on a `LinkedList`.

# Fail-Fast vs Fail-Safe Iterators

--> **Fail-fast** (default for `ArrayList`, `LinkedList`, `HashMap`, `HashSet`, etc.): the iterator keeps an internal `modCount` (modification count) snapshot taken when it was created. If the underlying collection is structurally modified (add/remove, but NOT via the iterator's own `remove()`/`add()`) while iterating, the next call to `next()` throws `ConcurrentModificationException` (CME) -- it fails FAST and LOUD rather than silently producing wrong results.

```java
List<Integer> nums = new ArrayList<>(List.of(1, 2, 3, 4));
for (Integer n : nums) {
    if (n == 2) {
        nums.remove(n);            // structural modification during for-each -- NOT via iterator
    }
}
// Throws ConcurrentModificationException on the next call to next()
```

```java
// Correct way to remove during iteration -- use the Iterator's own remove()
Iterator<Integer> it = nums.iterator();
while (it.hasNext()) {
    if (it.next() == 2) {
        it.remove();                // legal -- keeps modCount in sync internally
    }
}
```

--> **Fail-safe** (used by `CopyOnWriteArrayList`, `ConcurrentHashMap`, and other `java.util.concurrent` collections): the iterator either works on a SNAPSHOT/COPY of the data at iteration-start time, or tolerates concurrent structural changes without throwing -- at the cost of NOT reflecting the very latest state, and (for copy-on-write structures) higher memory/CPU cost on every mutation.
--> **Important nuance:** fail-fast is a BEST-EFFORT mechanism, not a guarantee -- the JavaDoc explicitly states CME should not be relied on for correctness/thread-safety, only used to detect bugs during development. It can occasionally fail to detect a modification, or (rarely) throw spuriously.

| Aspect | Fail-Fast (`ArrayList`, `HashMap`) | Fail-Safe (`CopyOnWriteArrayList`, `ConcurrentHashMap`) |
|---|---|---|
| Behavior on concurrent modification | Throws `ConcurrentModificationException` | Does not throw; iterates a snapshot or tolerates changes |
| Extra memory during iteration | None | Often yes (snapshot/copy) |
| Sees latest updates while iterating? | N/A (throws before it matters) | Usually no (works off snapshot taken at iterator creation) |
| Typical use case | Single-threaded or externally-synchronized code | Multi-threaded, read-heavy, infrequent-write scenarios |

# Common Gotchas

--> **`Arrays.asList()` is fixed-size** -- backed directly by the array; `add()`/`remove()` throw `UnsupportedOperationException`, but element mutation via `set()` writes through to the original array.
--> **`subList()` returns a VIEW, not a copy** -- structural changes to the sublist affect the parent list and vice versa; the original list also throws `ConcurrentModificationException` if modified directly while a sublist view is in use.
--> **Autoboxing traps with `remove()`** -- `list.remove(2)` on a `List<Integer>` removes the element AT INDEX 2, while `list.remove(Integer.valueOf(2))` removes the element EQUAL TO 2. This ambiguity is a classic interview gotcha.

```java
List<Integer> nums = new ArrayList<>(List.of(10, 20, 30));
nums.remove(2);                 // removes element at INDEX 2 -> removes 30 -> [10, 20]
nums.remove(Integer.valueOf(20)); // removes the VALUE 20 -> [10]
```

# Best Practices

--> Default to `ArrayList` unless you have a measured, specific reason not to.
--> Pre-size `ArrayList` with an expected capacity (`new ArrayList<>(expectedSize)`) when the approximate final size is known, to avoid repeated array copies.
--> Never use a classic indexed loop on a `LinkedList` -- use an iterator, for-each, or streams.
--> Use `Iterator.remove()` (or `removeIf()`) instead of calling `list.remove()` directly inside a for-each loop.
--> Prefer `ArrayDeque` over `LinkedList` for stack/queue-style usage (File 04 covers why).
