# Why the Collections Framework Exists

--> Before Java 2 (1998), developers used ad-hoc classes like `Vector`, `Hashtable`, and plain arrays -- each with its own inconsistent API, no shared interfaces, and no way to write code that worked generically across data structures. The **Java Collections Framework (JCF)** unified all of this into a single, consistent set of interfaces, implementations, and algorithms.
--> A "collection" is simply an object that groups multiple elements into a single unit -- a list of names, a set of unique IDs, a map from usernames to accounts. The framework gives you a small number of well-designed **interfaces** (contracts) and multiple **implementations** (concrete classes) of each, so you can pick the right data structure for the job without reinventing it.
--> This is arguably the single most-used part of the entire Java standard library -- almost no real Java program avoids `List`, `Map`, or `Set` entirely.

# The Three Pillars: Interfaces, Implementations, Algorithms

```text
1) INTERFACES   -- abstract data types: Collection, List, Set, Queue, Deque, Map (Map is technically separate, see below)
2) IMPLEMENTATIONS -- concrete classes: ArrayList, HashMap, TreeSet, LinkedList, etc.
3) ALGORITHMS   -- static utility methods that operate on collections: Collections.sort(), Collections.reverse(), Collections.binarySearch()
```

--> Writing code against the INTERFACE (`List<String> names = new ArrayList<>();`) rather than the concrete class is the standard best practice -- it lets you swap `ArrayList` for `LinkedList` later without touching any code that only ever calls `List` methods. This is "program to an interface, not an implementation," and the JCF is designed around it.

# The Full Hierarchy (Text Diagram)

--> The framework has **two separate root hierarchies** that beginners often mistakenly think are one: `Collection` and `Map`. `Map` is NOT a `Collection` -- it stores key-value pairs, not single elements, so it needed its own contract.

```text
java.lang.Iterable<E>
        |
        v
   Collection<E>  ------------------------------------------------
        |                    |                      |             |
        v                    v                      v             v
     List<E>              Set<E>                Queue<E>      (Collection itself
        |                    |                      |          is rarely used
   -----+-----          -----+------            ----+----      directly)
   |    |    |          |    |     |            |        |
ArrayList |  Vector   HashSet |  TreeSet     PriorityQueue  Deque<E>
      LinkedList         LinkedHashSet      (SortedSet)         |
      (also Deque)                          (NavigableSet)  -----+------
                                                              |         |
                                                          ArrayDeque  LinkedList
                                                                      (implements both
                                                                       List and Deque)


                        Map<E>   (separate hierarchy -- NOT a Collection)
                          |
              ------------+-------------
              |           |             |
          HashMap    LinkedHashMap   TreeMap
                                    (SortedMap, NavigableMap)
              |
          Hashtable (legacy, synchronized)
```

--> **`Iterable<E>`** is the root of the `Collection` side -- it's the interface that makes an object usable in a for-each loop (`for (E e : collection)`). Anything implementing `Iterable` must provide an `Iterator<E>` via `iterator()`.
--> **`Collection<E>`** adds the core operations shared by lists, sets, and queues: `add()`, `remove()`, `contains()`, `size()`, `isEmpty()`, `clear()`, `iterator()`, `stream()`.
--> Notice `LinkedList` appears in TWO places -- it implements both `List` and `Deque`, so it can act as an ordered list, a stack, or a double-ended queue.

# Collection vs Map -- The Fundamental Split

| Aspect | `Collection` (List/Set/Queue) | `Map` |
|---|---|---|
| Stores | Single elements | Key-value pairs |
| Root interface | `Iterable<E>` -> `Collection<E>` | Standalone `Map<K,V>` |
| Iteration | `for (E e : collection)` directly | Must go via `.keySet()`, `.values()`, or `.entrySet()` |
| Duplicates | Depends on subtype (List allows, Set doesn't) | Keys must be unique, values can duplicate |
| Example use | A list of orders, a set of tags | A lookup table: userId -> User object |

```java
// Collection side -- single elements
List<String> names = new ArrayList<>();
names.add("Alice");

// Map side -- key/value pairs, NOT iterable directly
Map<String, Integer> ages = new HashMap<>();
ages.put("Alice", 30);
for (Map.Entry<String, Integer> entry : ages.entrySet()) {   // must use entrySet()
    System.out.println(entry.getKey() + " -> " + entry.getValue());
}
```

--> **Why isn't `Map` a `Collection`?** -- A `Map.get(key)` conceptually returns ONE value per key, and `add(element)` doesn't make sense for a pair -- the semantics don't match `Collection`'s single-element contract cleanly enough, so the JCF designers kept them as sibling hierarchies that share only common design patterns (both have `HashMap`/`HashSet`-style hash-based implementations, both have sorted `Tree...` variants, etc.) rather than a common supertype.

# The Core Interfaces at a Glance

| Interface | Ordered? | Duplicates? | Null allowed? | Typical use |
|---|---|---|---|---|
| `List<E>` | Yes (insertion order, indexed) | Yes | Yes (impl-dependent) | Ordered sequence, access by index |
| `Set<E>` | Depends on impl | No | Impl-dependent | Uniqueness enforcement |
| `Queue<E>` | Yes (processing order) | Yes | Impl-dependent | FIFO processing, task scheduling |
| `Deque<E>` | Yes (both ends) | Yes | Impl-dependent | Stack, queue, or both |
| `Map<K,V>` | Depends on impl | Keys: no, Values: yes | Impl-dependent | Key-based lookup |

# Choosing the Right Collection -- A Decision Guide

```text
Do you need key -> value lookups?
    YES -> Map (HashMap for speed, LinkedHashMap for insertion order, TreeMap for sorted keys)
    NO  -> continue

Do duplicates matter, or do you need uniqueness?
    NEED UNIQUENESS -> Set (HashSet for speed, LinkedHashSet for order, TreeSet for sorted)
    DUPLICATES OK   -> continue

Do you need FIFO/LIFO processing order (queue/stack behavior)?
    YES -> Queue / Deque (ArrayDeque is the modern default for both stack and queue)
    NO  -> continue

Do you need indexed random access (get(i)) and mostly read/append?
    YES -> ArrayList
    NO, mostly inserting/removing at the ends or middle -> LinkedList (rare in practice; ArrayDeque
           usually wins even for queue-like use because of better cache locality)
```

--> **Rule of thumb used by most working Java developers:** default to `ArrayList` for lists, `HashMap` for maps, `HashSet` for sets, and `ArrayDeque` for stacks/queues -- only reach for the other implementations (`LinkedList`, `TreeMap`, `LinkedHashSet`, etc.) when you have a SPECIFIC reason (ordering guarantees, sorted iteration, frequent insert/remove at both ends of a huge sequence).

# Generics and Collections -- Why They're Always Paired

--> Before Java 5 (2004), collections stored `Object` -- you'd write `List list = new ArrayList(); list.add("hi");` and get back `Object`, requiring an explicit cast (`(String) list.get(0)`) that could fail at RUNTIME with a `ClassCastException` if the wrong type had been added.
--> Generics (`List<String>`) let the COMPILER enforce type safety -- `list.add(42)` on a `List<String>` is now a compile error, not a runtime surprise. This is covered in depth in File 05, but it's worth knowing upfront that "Collections" and "Generics" are taught together in this module precisely because the entire JCF was retrofitted with generics in Java 5 and has been used generically ever since.

```java
List rawList = new ArrayList();          // raw type -- legal but avoid: no compile-time type checking
rawList.add("text");
rawList.add(42);                          // compiles fine, no error -- mixed types silently allowed
// Object o = rawList.get(1);  String s = (String) o;  // ClassCastException at RUNTIME

List<String> typedList = new ArrayList<>();   // generic -- modern, correct way
typedList.add("text");
// typedList.add(42);   // COMPILE ERROR -- caught immediately, not at runtime
```

# Common Gotchas

--> **`Arrays.asList()` returns a fixed-size list** -- it's backed by the original array, so `add()`/`remove()` throw `UnsupportedOperationException`, but `set()` works and mutates the underlying array.
--> **`List.of()`, `Set.of()`, `Map.of()` (Java 9+) are truly IMMUTABLE** -- any mutation attempt throws `UnsupportedOperationException`, and unlike `Arrays.asList()`, they also reject `null` elements.
--> **Comparing collections with `==` compares references, not contents** -- use `.equals()` (which `List`, `Set`, and `Map` all override sensibly) to compare contents.
--> **Not all collections preserve insertion order** -- `HashSet` and `HashMap` iteration order is unspecified and can even change between JVM runs; only reach for them when order doesn't matter, or switch to the `Linked...` variants when it does.

# Best Practices

--> Always declare variables using the **interface type** (`List<String>`, `Map<String, Integer>`) and instantiate with the **concrete implementation** (`new ArrayList<>()`), never the reverse.
--> Prefer the **diamond operator** `<>` (Java 7+) over repeating the generic type on both sides -- `new ArrayList<>()` instead of `new ArrayList<String>()`.
--> Use the enhanced for-loop or Streams for read-only iteration; use `Iterator.remove()` (File 06) when you need to remove elements while iterating.
--> Initialize collections with an expected capacity when the size is known upfront (`new ArrayList<>(1000)`, `new HashMap<>(64)`) to avoid repeated internal resizing -- covered in detail in Files 02 and 03.
