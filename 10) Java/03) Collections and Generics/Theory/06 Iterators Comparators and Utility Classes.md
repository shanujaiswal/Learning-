# Iterator -- The Universal Traversal Contract

--> `Iterator<E>` is the interface that powers the enhanced for-loop and gives SAFE removal during traversal. It has three core methods: `hasNext()`, `next()`, and `remove()` (optional, default throws `UnsupportedOperationException` if not overridden).

```java
List<Integer> nums = new ArrayList<>(List.of(1, 2, 3, 4, 5));
Iterator<Integer> it = nums.iterator();
while (it.hasNext()) {
    int n = it.next();
    if (n % 2 == 0) {
        it.remove();          // the ONLY safe way to remove while iterating (see File 02's fail-fast section)
    }
}
System.out.println(nums);     // [1, 3, 5]
```

--> **`Iterator.remove()` internals:** it removes the element most recently returned by `next()`, and crucially updates the iterator's own `modCount` snapshot to match the collection's, so the fail-fast check doesn't trip on the very modification the iterator itself just made.
--> **`Iterable` vs `Iterator`:** `Iterable<E>` is what a class implements to say "I can be iterated" (it has one method, `iterator()`, which returns a fresh `Iterator<E>`). The `Iterator` itself is a separate, stateful, single-use cursor object. This is why you can iterate the same `List` in two nested for-each loops simultaneously -- each loop gets its OWN independent `Iterator`.

# ListIterator -- Bidirectional, List-Only

--> `ListIterator<E>` extends `Iterator<E>` and is available only on `List` implementations (via `list.listIterator()`). It adds: backward traversal (`hasPrevious()`, `previous()`), index awareness (`nextIndex()`, `previousIndex()`), in-place replacement (`set()`), and insertion (`add()`) during iteration.

```java
List<String> list = new ArrayList<>(List.of("a", "b", "c"));
ListIterator<String> lit = list.listIterator();
while (lit.hasNext()) {
    String s = lit.next();
    if (s.equals("b")) {
        lit.set("B");           // replace in place -- legal, unlike plain Iterator
        lit.add("b2");           // insert right after current position -- legal too
    }
}
System.out.println(list);        // [a, B, b2, c]

// Traversing backward -- start at the end
ListIterator<String> backward = list.listIterator(list.size());
while (backward.hasPrevious()) {
    System.out.println(backward.previous());
}
```

--> **Gotcha:** calling `next()` then `previous()` returns the SAME element twice in a row -- the iterator's cursor sits BETWEEN elements, not on one; `next()` returns the element after the cursor and advances past it, `previous()` returns the element before the cursor and moves back past it.

# Comparable -- "Natural Ordering," Defined by the Class Itself

--> A class implements `Comparable<T>` to define its OWN default/natural sort order via `compareTo()`, which returns negative/zero/positive to mean "less than / equal to / greater than."

```java
class Employee implements Comparable<Employee> {
    String name;
    int salary;
    Employee(String name, int salary) { this.name = name; this.salary = salary; }

    @Override
    public int compareTo(Employee other) {
        return Integer.compare(this.salary, other.salary);   // natural order: ascending by salary
    }

    @Override
    public String toString() { return name + "($" + salary + ")"; }
}

List<Employee> employees = new ArrayList<>(List.of(
    new Employee("Alice", 70000), new Employee("Bob", 50000), new Employee("Carol", 90000)));
Collections.sort(employees);              // uses compareTo() -- natural order
System.out.println(employees);            // [Bob($50000), Alice($70000), Carol($90000)]
```

--> **Contract requirements:** `compareTo()` must be consistent (roughly) with `equals()` -- `a.compareTo(b) == 0` should generally imply `a.equals(b) == true` -- `TreeSet`/`TreeMap` use `compareTo()` ALONE to determine equality/uniqueness, ignoring `equals()` entirely, so inconsistency here causes confusing bugs (a `TreeSet` might "lose" elements that are `.equals()`-different but `compareTo()`-equal).

# Comparator -- External, Pluggable Ordering

--> `Comparator<T>` defines ordering EXTERNALLY to the class -- useful when you need multiple different orderings, or can't modify the class (e.g., it's from a library) to implement `Comparable`.

```java
// Classic anonymous class style (pre-Java 8)
Comparator<Employee> byNameOld = new Comparator<Employee>() {
    @Override
    public int compare(Employee a, Employee b) {
        return a.name.compareTo(b.name);
    }
};

// Modern lambda style
Comparator<Employee> byName = (a, b) -> a.name.compareTo(b.name);

// Comparator factory methods (Java 8+) -- the idiomatic modern approach
Comparator<Employee> bySalary = Comparator.comparingInt(e -> e.salary);
Comparator<Employee> byNameThenSalary = Comparator.comparing((Employee e) -> e.name)
                                                    .thenComparingInt(e -> e.salary);
Comparator<Employee> bySalaryDesc = Comparator.comparingInt((Employee e) -> e.salary).reversed();

employees.sort(byNameThenSalary);              // List.sort() -- in-place, uses the given Comparator
employees.sort(Comparator.comparing(Employee::salary).reversed());  // method reference version
```

# Comparable vs Comparator

| Aspect | `Comparable<T>` | `Comparator<T>` |
|---|---|---|
| Defined | Inside the class being compared | Externally, as a separate object/lambda |
| Method | `compareTo(T other)` | `compare(T a, T b)` |
| Number of orderings | One "natural" order per class | Unlimited -- as many as needed |
| Used by | `Collections.sort(list)`, `TreeSet`/`TreeMap` default | `Collections.sort(list, comparator)`, `list.sort(comparator)`, `TreeSet`/`TreeMap` alternate constructor |
| Requires modifying the class? | Yes (must implement the interface) | No -- works with any class, even ones you don't own |

# The `Collections` Utility Class

--> `java.util.Collections` (not to be confused with `java.util.Collection`, singular, the interface) is a class of STATIC utility methods that operate on or return collections.

```java
List<Integer> nums = new ArrayList<>(List.of(5, 3, 8, 1, 9));

Collections.sort(nums);                        // [1, 3, 5, 8, 9] -- natural order, in place
Collections.sort(nums, Comparator.reverseOrder()); // [9, 8, 5, 3, 1]
Collections.reverse(nums);                       // reverses current order in place
Collections.shuffle(nums);                        // randomizes order in place
Collections.max(nums);                             // largest element
Collections.min(nums);                             // smallest element
Collections.frequency(nums, 5);                    // count of occurrences of 5
Collections.binarySearch(sortedNums, 8);            // O(log n) search -- LIST MUST ALREADY BE SORTED
Collections.swap(nums, 0, 1);                        // swaps elements at two indices
Collections.fill(nums, 0);                            // overwrites every element with 0
Collections.nCopies(3, "x");                          // immutable list: [x, x, x]

// Wrapper methods -- unmodifiable and synchronized views
List<Integer> readOnly = Collections.unmodifiableList(nums);   // throws on any mutation attempt
List<Integer> threadSafe = Collections.synchronizedList(new ArrayList<>()); // adds locking per method

// Empty singletons -- avoid allocating for common "nothing" cases
List<String> empty = Collections.emptyList();
Set<String> singleton = Collections.singleton("only-one");
```

--> **Gotcha with `synchronizedList`:** wrapping a list makes individual method calls thread-safe, but COMPOUND operations (like "check `contains()` then `add()`") still need external synchronization on the returned object, and iteration MUST be manually synchronized (`synchronized(list) { for (...) ... }`) or it can throw `ConcurrentModificationException` from a concurrent modification.
--> **Gotcha with `unmodifiableList`:** it's a VIEW, not a deep copy -- mutating the ORIGINAL list still changes what's visible through the unmodifiable wrapper; only direct mutation attempts on the wrapper itself throw.

# The `Arrays` Utility Class

--> `java.util.Arrays` provides static utilities for working with plain arrays -- separate from `Collections` because arrays predate the Collections Framework and aren't `Collection`s themselves.

```java
int[] arr = {5, 3, 8, 1, 9};

Arrays.sort(arr);                          // sorts in place, O(n log n) (dual-pivot quicksort for primitives)
System.out.println(Arrays.toString(arr));  // [1, 3, 5, 8, 9] -- readable string form, unlike default array toString()
int idx = Arrays.binarySearch(arr, 8);     // O(log n) -- array MUST be sorted first
int[] copy = Arrays.copyOf(arr, 10);        // new array, length 10, extra slots zero-filled
int[] range = Arrays.copyOfRange(arr, 1, 3); // elements at index 1 and 2
boolean eq = Arrays.equals(arr, copy);        // element-wise equality (== on arrays is reference equality!)
Arrays.fill(arr, 0);                           // overwrite every element

List<Integer> boxedList = Arrays.asList(1, 2, 3);   // FIXED-SIZE list view backed by the array (see File 01/02 gotcha)

// Multi-dimensional arrays need the "deep" variants
int[][] matrix = {{1, 2}, {3, 4}};
System.out.println(Arrays.deepToString(matrix));    // [[1, 2], [3, 4]]
System.out.println(Arrays.deepEquals(matrix, matrix)); // true -- element-wise, recursively

// Java 8+ stream bridge
int sum = Arrays.stream(arr).sum();
```

--> **`Arrays.sort()` uses different algorithms for primitives vs objects:** primitive arrays use a dual-pivot quicksort variant (in-place, no extra memory, but NOT stable -- equal elements might reorder relative to each other, which is fine since primitives have no identity beyond value); object arrays use a modified mergesort/Timsort (stable -- preserves relative order of equal elements, which matters when sorting objects by ONE field while wanting ties to keep their original relative order).

# Immutable Collections

--> Java 9 introduced factory methods for creating genuinely immutable, compact collections: `List.of(...)`, `Set.of(...)`, `Map.of(...)`, `Map.ofEntries(...)`.

```java
List<String> immutableList = List.of("a", "b", "c");
Set<Integer> immutableSet = Set.of(1, 2, 3);
Map<String, Integer> immutableMap = Map.of("a", 1, "b", 2);
Map<String, Integer> biggerMap = Map.ofEntries(
    Map.entry("a", 1), Map.entry("b", 2), Map.entry("c", 3));

// immutableList.add("d");   // UnsupportedOperationException
// List.of("a", null);       // NullPointerException -- unlike ArrayList, null elements are rejected outright
// Set.of(1, 1);             // IllegalArgumentException -- duplicates rejected at creation time, not silently ignored
```

--> **Differences from `Collections.unmodifiableList()`:** `List.of()` is a real, independent immutable object (not a view over a mutable backing list someone else can still change), rejects `null`, and is typically more memory-compact internally. `Collections.unmodifiableList(list)` is a thin wrapper that still reflects changes made to the underlying mutable `list`.
--> **`List.copyOf()` / `Set.copyOf()` / `Map.copyOf()`** create an immutable snapshot from an existing (possibly mutable) collection -- if the source is already an immutable collection of the matching type, it may return it directly rather than copying, as an optimization.

```java
List<String> mutable = new ArrayList<>(List.of("x", "y"));
List<String> snapshot = List.copyOf(mutable);   // independent immutable copy
mutable.add("z");                                // does NOT affect snapshot
System.out.println(snapshot);                     // [x, y]
```

# Common Gotchas

--> **`Collections.sort()` on a list of objects with no `Comparable`/`Comparator`** throws `ClassCastException` at runtime, since generics can't statically verify sortability across arbitrary custom types without a bound.
--> **`Arrays.asList()` returned list is fixed-size** -- passing it directly to code that calls `add()`/`remove()` throws `UnsupportedOperationException`; wrap in `new ArrayList<>(Arrays.asList(...))` for a fully mutable copy.
--> **`binarySearch` on an unsorted list/array gives undefined results** -- it does NOT check that the input is sorted; it just runs the algorithm and may return a wrong index or a wrong "not found" result.
--> **Comparator chains and `null` handling** -- `Comparator.comparing()` throws `NullPointerException` on a `null` field by default; use `Comparator.nullsFirst()`/`nullsLast()` wrappers to handle `null` values gracefully in a sort.

# Best Practices

--> Use `Iterator.remove()` (or `Collection.removeIf(predicate)`, which is simpler for straightforward filtering) instead of manual index-based removal loops.
--> Prefer `Comparator` factory methods (`comparing`, `thenComparing`, `reversed`, `naturalOrder`) over hand-written `compare()` bodies -- more concise and less error-prone.
--> Return immutable collections (`List.of()`, `Collections.unmodifiableList()`) from public API methods when callers shouldn't be able to mutate internal state.
--> Use `Arrays.toString()`/`Arrays.deepToString()` for debugging array contents -- printing an array directly gives an unhelpful hash-based string like `[I@1b6d3586`.
--> Always specify a `Comparator` explicitly rather than relying on natural ordering when the "obvious" sort behavior isn't guaranteed (e.g., sorting case-insensitively, sorting nulls to one end).
