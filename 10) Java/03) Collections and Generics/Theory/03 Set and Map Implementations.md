# The `Set` Interface -- Uniqueness, No Indexing

--> `Set<E>` extends `Collection<E>` and adds ONE rule on top of it: no duplicate elements, determined via `equals()`/`hashCode()`. There is no index-based access (`get(i)` doesn't exist on `Set`).
--> Three main implementations: `HashSet` (fastest, no order guarantee), `LinkedHashSet` (insertion order preserved), `TreeSet` (sorted order, extra operations).

# HashSet -- Backed by a HashMap Internally

--> `HashSet<E>` is literally implemented as a `HashMap<E, Object>` under the hood, where every element you add becomes a KEY in that internal map, and all values point to a single shared dummy constant `PRESENT`. This is why understanding `HashMap` (below) explains `HashSet` almost for free.
--> No ordering guarantee -- iteration order depends on hash bucket placement and can change across JVM runs or after resizing. Never rely on `HashSet` iteration order.

```java
Set<String> set = new HashSet<>();
set.add("banana");
set.add("apple");
set.add("cherry");
set.add("apple");          // ignored -- already present, add() returns false
System.out.println(set);   // order is unspecified, e.g. [banana, cherry, apple]
```

# LinkedHashSet -- HashSet + Insertion-Order Linked List

--> Extends `HashSet` but additionally maintains a doubly-linked list running through all entries, threading them in INSERTION order (not sorted order). Iteration order = the order elements were added, consistently.
--> Slightly more memory and slightly slower inserts than `HashSet` (maintaining the extra links costs something), but still O(1) average for add/remove/contains.

```java
Set<String> linked = new LinkedHashSet<>();
linked.add("banana");
linked.add("apple");
linked.add("cherry");
System.out.println(linked);  // [banana, apple, cherry] -- guaranteed insertion order
```

# TreeSet -- Backed by a Red-Black Tree

--> `TreeSet<E>` is implemented on top of a `TreeMap<E, Object>`, which is a self-balancing binary search tree (specifically a Red-Black Tree). Elements are always kept in SORTED order according to their natural ordering (`Comparable`) or a supplied `Comparator`.
--> Because it's a balanced BST, `add`/`remove`/`contains` are O(log n) -- slower than `HashSet`'s O(1) average, but you get sorted iteration and extra navigation operations for free.

```java
TreeSet<Integer> sorted = new TreeSet<>(List.of(5, 1, 4, 2, 3));
System.out.println(sorted);        // [1, 2, 3, 4, 5] -- always sorted

System.out.println(sorted.first());     // 1
System.out.println(sorted.last());      // 5
System.out.println(sorted.higher(3));   // 4  -- smallest element strictly greater than 3
System.out.println(sorted.lower(3));    // 2  -- largest element strictly less than 3
System.out.println(sorted.ceiling(3));  // 3  -- smallest element >= 3
System.out.println(sorted.floor(3));    // 3  -- largest element <= 3
System.out.println(sorted.headSet(3));  // [1, 2]  -- elements < 3
System.out.println(sorted.tailSet(3));  // [3, 4, 5]  -- elements >= 3
```

# Set Implementations Comparison

| Operation | `HashSet` | `LinkedHashSet` | `TreeSet` |
|---|---|---|---|
| `add`/`remove`/`contains` | O(1) average | O(1) average | O(log n) |
| Iteration order | Unspecified | Insertion order | Sorted order |
| Null elements | One `null` allowed | One `null` allowed | Not allowed (NPE, since it needs to compare) |
| Underlying structure | HashMap (hash table) | HashMap + linked list | Red-Black Tree (via TreeMap) |
| Extra API | None | None | `first()`, `last()`, `higher()`, `floor()`, `headSet()`, etc. (`NavigableSet`) |

# The `Map` Interface -- Key-Value Storage

--> `Map<K,V>` stores unique keys mapped to values -- `put(key, value)`, `get(key)`, `remove(key)`, `containsKey(key)`. Same three-way split as `Set`: `HashMap`, `LinkedHashMap`, `TreeMap`.

# HashMap Internals -- How Hashing Actually Works

--> Internally, `HashMap` holds an array of "buckets" (`Node<K,V>[] table`). To find where a key goes:

```text
1. Compute hash = key.hashCode()
2. HashMap applies its own hash-spreading function: hash ^ (hash >>> 16)
   -- this XORs the high 16 bits into the low 16 bits, so that even objects with
      poor low-bit distribution in hashCode() still spread across buckets well.
3. bucketIndex = hash & (table.length - 1)     -- equivalent to hash % table.length
                                                   when table.length is a power of 2 (it always is)
4. The entry is placed into table[bucketIndex]
```

```text
table.length = 16  (default initial capacity)

Bucket 0: []
Bucket 1: [ ("apple", 1) ]
Bucket 2: [ ("banana", 2) -> ("zebra", 9) ]   <- collision: both hashed to bucket 2
Bucket 3: []
...
```

--> **Collision resolution -- separate chaining:** when two different keys hash to the SAME bucket, `HashMap` stores them as a linked list within that bucket (each `Node` points to the next). Looking up a key in a collided bucket means walking that mini linked-list and calling `.equals()` on each candidate until a match is found (or the end is reached).
--> **Java 8+ optimization -- treeification:** if a single bucket's linked list grows to 8 or more entries AND the table has at least 64 buckets total, that bucket is converted internally into a small RED-BLACK TREE instead of a linked list, changing worst-case lookup in that bucket from O(n) to O(log n). This defends against pathological hash collision attacks/bugs. It converts back to a list if the bucket shrinks below 6 entries.
--> **Why `hashCode()` and `equals()` must be consistent:** if two objects are `.equals()`, they MUST return the same `hashCode()` (this is a hard contract) -- otherwise they could land in different buckets and `HashMap`/`HashSet` would treat equal objects as distinct, silently breaking lookups and allowing "duplicate" entries.

```java
class Point {
    int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point p)) return false;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);   // MUST be consistent with equals()
    }
}

Set<Point> points = new HashSet<>();
points.add(new Point(1, 2));
System.out.println(points.contains(new Point(1, 2)));  // true -- only works because hashCode+equals are overridden correctly
```

# Load Factor and Resizing

--> **Load factor** (default `0.75`) controls the trade-off between memory usage and lookup speed -- it's the fraction full the table can get before it resizes: `threshold = capacity * loadFactor`.
--> When `size > threshold`, the table RESIZES: capacity doubles (e.g., 16 -> 32), and EVERY existing entry is rehashed and redistributed into the new, larger table (an O(n) operation, amortized across many inserts, just like `ArrayList` resizing).

```text
Default initial capacity: 16
Default load factor: 0.75
Resize threshold: 16 * 0.75 = 12   -- resize triggers once the 13th entry is added

After resize: capacity = 32, new threshold = 32 * 0.75 = 24
```

```java
Map<String, Integer> map = new HashMap<>();          // capacity 16, threshold 12
Map<String, Integer> sized = new HashMap<>(200);      // pre-sized -- avoids resize churn for ~150 entries
Map<String, Integer> custom = new HashMap<>(16, 0.9f); // custom load factor -- denser table, more collisions, less memory
```

--> **Why 0.75 is the default:** it's a time-vs-space compromise. A lower load factor (e.g., 0.5) means more empty buckets (more wasted memory) but fewer collisions (faster lookups). A higher load factor (e.g., 0.9) saves memory but increases collision chains, slowing lookups. 0.75 is empirically a good middle ground for general use.
--> **Practical tip:** if you know you'll store roughly N entries, construct with `new HashMap<>((int)(N / 0.75) + 1)` to avoid ANY resizing during population.

# LinkedHashMap and TreeMap

--> **`LinkedHashMap`** -- `HashMap` + a doubly-linked list maintaining either insertion order (default) or ACCESS order (via the `accessOrder` constructor flag) -- access-order mode is the basis for building a simple **LRU (Least Recently Used) cache** by overriding `removeEldestEntry()`.

```java
// A simple LRU cache using LinkedHashMap's access-order mode
Map<Integer, String> lru = new LinkedHashMap<>(16, 0.75f, true) {   // true = access-order
    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
        return size() > 3;   // cap the cache at 3 entries, evict the least-recently-used
    }
};
lru.put(1, "a"); lru.put(2, "b"); lru.put(3, "c");
lru.get(1);              // "touches" key 1, moves it to the most-recently-used end
lru.put(4, "d");          // triggers eviction -- key 2 (least recently used) is removed
System.out.println(lru.keySet());  // [3, 1, 4]
```

--> **`TreeMap`** -- Red-Black Tree backed, keys always in sorted order (natural or `Comparator`), O(log n) for `get`/`put`/`remove`. Implements `NavigableMap`, offering `firstKey()`, `lastKey()`, `higherKey()`, `floorEntry()`, `headMap()`, `tailMap()`, `subMap()` -- the map analog of `TreeSet`'s navigation methods.

```java
TreeMap<String, Integer> scores = new TreeMap<>();
scores.put("charlie", 90);
scores.put("alice", 95);
scores.put("bob", 88);
System.out.println(scores);            // {alice=95, bob=88, charlie=90} -- sorted by key
System.out.println(scores.firstKey()); // alice
System.out.println(scores.ceilingKey("b")); // bob -- smallest key >= "b"
```

# Map Implementations Comparison

| Operation | `HashMap` | `LinkedHashMap` | `TreeMap` |
|---|---|---|---|
| `get`/`put`/`remove` | O(1) average, O(log n) worst (treeified bucket) | O(1) average | O(log n) always |
| Iteration order | Unspecified | Insertion (or access) order | Sorted by key |
| Null keys | One `null` key allowed | One `null` key allowed | Not allowed (NPE) |
| Null values | Allowed | Allowed | Allowed |
| Extra API | None | `removeEldestEntry()` hook (LRU) | `firstKey`, `higherKey`, `subMap`, etc. |
| Thread-safe | No | No | No |

# Common Gotchas

--> **Mutable keys are dangerous** -- if an object used as a `HashMap`/`HashSet` key is mutated AFTER insertion in a way that changes its `hashCode()`, the entry becomes unreachable (it's still physically in its old bucket, but lookups now compute a different bucket index). Prefer immutable keys (`String`, `Integer`, records, or classes with final fields).
--> **`TreeMap`/`TreeSet` throw `NullPointerException` on `null`** because natural ordering can't compare against `null`. `HashMap`/`HashSet` allow one `null`.
--> **Forgetting to override BOTH `equals()` and `hashCode()`** on custom key classes leads to silent bugs -- default `Object.equals()`/`hashCode()` use identity, so two "logically equal" objects will be treated as different map keys.
--> **`HashMap` iteration order is not just "random," it's an implementation detail** that can differ between JDK versions -- never write code (or tests) that depend on it.

# Best Practices

--> Default to `HashMap`/`HashSet` unless you need ordering guarantees.
--> Use `LinkedHashMap`/`LinkedHashSet` when insertion-order iteration matters (e.g., preserving user-input order for display).
--> Use `TreeMap`/`TreeSet` only when you genuinely need sorted iteration or range queries -- the O(log n) cost is real and unnecessary otherwise.
--> Always override `equals()` and `hashCode()` together for custom classes used as map keys or set elements; use `Objects.equals()`/`Objects.hash()` to do it correctly and concisely.
--> Pre-size hash-based collections when the approximate final size is known.
