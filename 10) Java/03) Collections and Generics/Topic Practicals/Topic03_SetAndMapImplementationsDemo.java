/*
 * Topic03_SetAndMapImplementationsDemo.java
 *
 * Demonstrates:
 *   1. HashSet vs LinkedHashSet vs TreeSet -- ordering behavior and uniqueness
 *   2. TreeSet's NavigableSet operations (first/last/higher/lower/ceiling/floor/headSet/tailSet)
 *   3. HashMap internals -- hashCode/equals contract, custom key class, why it matters
 *   4. Hash collision simulation -- forcing collisions with a deliberately bad hashCode()
 *   5. Load factor and resizing -- observing capacity growth via pre-sizing vs default growth
 *   6. LinkedHashMap insertion order vs access-order LRU cache
 *   7. TreeMap sorted-key behavior and NavigableMap operations
 *   8. Common gotchas: mutable keys, null handling differences across the three Set/Map families
 *
 * Covers Theory chapter:
 *   10) Java/03) Collections and Generics/Theory/03 Set and Map Implementations.md
 *
 * Compile & run:
 *   javac Topic03_SetAndMapImplementationsDemo.java
 *   java Topic03_SetAndMapImplementationsDemo
 */

import java.util.*;

public class Topic03_SetAndMapImplementationsDemo {

    public static void main(String[] args) {
        demoSetOrderingAndUniqueness();
        demoTreeSetNavigableOperations();
        demoHashMapInternalsAndEqualsHashCodeContract();
        demoHashCollisionSimulation();
        demoLoadFactorAndResizing();
        demoLinkedHashMapAndLruCache();
        demoTreeMapNavigableOperations();
        demoCommonGotchas();
        System.out.println("\nAll Set and Map implementation demos completed.");
    }

    // -----------------------------------------------------------------
    // 1) HashSet vs LinkedHashSet vs TreeSet -- ordering and uniqueness
    // -----------------------------------------------------------------
    private static void demoSetOrderingAndUniqueness() {
        printSection("1) HashSet vs LinkedHashSet vs TreeSet -- ordering & uniqueness");

        Set<String> hashSet = new HashSet<>();
        hashSet.add("banana");
        hashSet.add("apple");
        hashSet.add("cherry");
        boolean addedAgain = hashSet.add("apple");        // duplicate -- ignored, add() returns false
        System.out.println("HashSet (unspecified order):      " + hashSet);
        System.out.println("  re-adding \"apple\" returned: " + addedAgain + " (duplicate rejected)");

        Set<String> linkedHashSet = new LinkedHashSet<>();
        linkedHashSet.add("banana");
        linkedHashSet.add("apple");
        linkedHashSet.add("cherry");
        System.out.println("LinkedHashSet (insertion order):  " + linkedHashSet);

        Set<String> treeSet = new TreeSet<>();
        treeSet.add("banana");
        treeSet.add("apple");
        treeSet.add("cherry");
        System.out.println("TreeSet (sorted order):            " + treeSet);

        // Uniqueness relies on equals()/hashCode() -- demonstrated with Integer boxing
        Set<Integer> ints = new HashSet<>(List.of(1, 2, 2, 3, 3, 3));
        System.out.println("Duplicates collapsed via equals/hashCode: " + ints);
    }

    // -----------------------------------------------------------------
    // 2) TreeSet's NavigableSet operations
    // -----------------------------------------------------------------
    private static void demoTreeSetNavigableOperations() {
        printSection("2) TreeSet NavigableSet operations");

        TreeSet<Integer> sorted = new TreeSet<>(List.of(5, 1, 4, 2, 3));
        System.out.println("TreeSet:        " + sorted);
        System.out.println("first():        " + sorted.first());
        System.out.println("last():         " + sorted.last());
        System.out.println("higher(3):      " + sorted.higher(3) + "  (smallest element strictly > 3)");
        System.out.println("lower(3):       " + sorted.lower(3) + "  (largest element strictly < 3)");
        System.out.println("ceiling(3):     " + sorted.ceiling(3) + "  (smallest element >= 3)");
        System.out.println("floor(3):       " + sorted.floor(3) + "  (largest element <= 3)");
        System.out.println("headSet(3):     " + sorted.headSet(3) + "  (elements < 3)");
        System.out.println("tailSet(3):     " + sorted.tailSet(3) + "  (elements >= 3)");

        // Custom Comparator for descending order
        TreeSet<Integer> descending = new TreeSet<>(Comparator.reverseOrder());
        descending.addAll(List.of(5, 1, 4, 2, 3));
        System.out.println("TreeSet with reverseOrder Comparator: " + descending);
    }

    // -----------------------------------------------------------------
    // 3) HashMap internals -- equals/hashCode contract with a custom key
    // -----------------------------------------------------------------
    private static void demoHashMapInternalsAndEqualsHashCodeContract() {
        printSection("3) HashMap internals -- equals()/hashCode() contract");

        // Point overrides both equals() and hashCode() consistently -- lookups work correctly
        Map<Point, String> labels = new HashMap<>();
        labels.put(new Point(1, 2), "origin-ish");
        System.out.println("Correctly overridden key -- contains(new Point(1,2)): "
                + labels.containsKey(new Point(1, 2)));

        // BrokenPoint overrides equals() but NOT hashCode() -- breaks the contract
        Map<BrokenPoint, String> brokenLabels = new HashMap<>();
        brokenLabels.put(new BrokenPoint(1, 2), "origin-ish");
        System.out.println("Broken key (equals overridden, hashCode NOT overridden) -- "
                + "contains(new BrokenPoint(1,2)): " + brokenLabels.containsKey(new BrokenPoint(1, 2))
                + "  <- lost! different hashCode() means it looks in the WRONG bucket");

        // Bucket index math explained explicitly
        String key = "apple";
        int h = key.hashCode();
        int spread = h ^ (h >>> 16);           // HashMap's internal hash-spreading step
        int capacity = 16;                     // default initial capacity
        int bucketIndex = spread & (capacity - 1);
        System.out.println("Key \"apple\" hashCode=" + h + ", spread=" + spread
                + ", bucketIndex (capacity 16) = " + bucketIndex);
    }

    private static class Point {
        final int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Point)) return false;
            Point p = (Point) o;
            return x == p.x && y == p.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);          // consistent with equals() -- required by the contract
        }
    }

    // Deliberately broken: equals() overridden, hashCode() NOT overridden (uses Object identity hash)
    private static class BrokenPoint {
        final int x, y;
        BrokenPoint(int x, int y) { this.x = x; this.y = y; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BrokenPoint)) return false;
            BrokenPoint p = (BrokenPoint) o;
            return x == p.x && y == p.y;
        }
        // hashCode() intentionally NOT overridden -- inherits Object's identity-based hashCode()
    }

    // -----------------------------------------------------------------
    // 4) Hash collision simulation -- forcing all keys into one bucket
    // -----------------------------------------------------------------
    private static void demoHashCollisionSimulation() {
        printSection("4) Hash collision simulation via a deliberately bad hashCode()");

        // CollidingKey always returns the same hashCode() -- every instance lands in the SAME bucket,
        // forcing HashMap to fall back to its separate-chaining linked list (or tree, if >= 8 entries).
        Map<CollidingKey, Integer> map = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            map.put(new CollidingKey(i), i * i);
        }
        System.out.println("Inserted 10 entries whose hashCode() is ALWAYS 42 (all collide into one bucket).");
        System.out.println("Map still works correctly via equals() fallback within the bucket's chain:");
        System.out.println("  get(CollidingKey(5)) = " + map.get(new CollidingKey(5)));
        System.out.println("  size() = " + map.size());
        System.out.println("-> Lookups here are O(n) worst-case per query (or O(log n) if treeified at >= 8 "
                + "entries and >= 64 buckets), instead of HashMap's usual O(1) average -- this is exactly "
                + "why a good hashCode() distribution matters.");
    }

    private static class CollidingKey {
        final int id;
        CollidingKey(int id) { this.id = id; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CollidingKey)) return false;
            return ((CollidingKey) o).id == id;
        }

        @Override
        public int hashCode() {
            return 42;              // deliberately identical for every instance -- forces collisions
        }
    }

    // -----------------------------------------------------------------
    // 5) Load factor and resizing
    // -----------------------------------------------------------------
    private static void demoLoadFactorAndResizing() {
        printSection("5) Load factor and resizing");

        System.out.println("Default: initial capacity 16, load factor 0.75 -> resize threshold = 12");
        System.out.println("(the 13th put() triggers a resize to capacity 32, threshold becomes 24)");

        // Default-capacity map -- will resize internally as we cross the threshold, transparently
        Map<Integer, Integer> defaultMap = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            defaultMap.put(i, i);
        }
        System.out.println("Default HashMap after 20 puts -- size=" + defaultMap.size()
                + " (capacity grew 16 -> 32 internally, transparently, no data lost)");

        // Pre-sized map -- avoids resize churn entirely for a known target size
        Map<Integer, Integer> preSized = new HashMap<>((int) (200 / 0.75) + 1);
        for (int i = 0; i < 150; i++) {
            preSized.put(i, i);
        }
        System.out.println("Pre-sized HashMap (capacity chosen for ~200 entries) after 150 puts -- size="
                + preSized.size() + " (no resize needed along the way)");

        // Custom load factor -- denser table, fewer resizes, more collisions per bucket on average
        Map<String, Integer> denseMap = new HashMap<>(16, 0.9f);
        denseMap.put("a", 1);
        System.out.println("Custom load factor 0.9 map created: " + denseMap
                + "  (threshold = 16*0.9 = 14 before first resize, vs default's 12)");
    }

    // -----------------------------------------------------------------
    // 6) LinkedHashMap -- insertion order vs access-order LRU cache
    // -----------------------------------------------------------------
    private static void demoLinkedHashMapAndLruCache() {
        printSection("6) LinkedHashMap -- insertion order vs access-order LRU cache");

        Map<String, Integer> insertionOrdered = new LinkedHashMap<>();
        insertionOrdered.put("charlie", 3);
        insertionOrdered.put("alice", 1);
        insertionOrdered.put("bob", 2);
        System.out.println("LinkedHashMap (default insertion order): " + insertionOrdered);

        // Access-order mode (true flag) + removeEldestEntry() override => simple LRU cache
        Map<Integer, String> lru = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                return size() > 3;         // cap the cache at 3 entries
            }
        };
        lru.put(1, "a");
        lru.put(2, "b");
        lru.put(3, "c");
        lru.get(1);                          // "touches" key 1 -- moves it to the most-recently-used end
        lru.put(4, "d");                     // triggers eviction of the least-recently-used entry (key 2)
        System.out.println("LRU cache (capacity 3) keys after touching 1 then inserting 4: " + lru.keySet());
        System.out.println("-> key 2 was evicted because it was the least recently used, not the oldest inserted.");
    }

    // -----------------------------------------------------------------
    // 7) TreeMap -- sorted keys and NavigableMap operations
    // -----------------------------------------------------------------
    private static void demoTreeMapNavigableOperations() {
        printSection("7) TreeMap -- sorted keys and NavigableMap operations");

        TreeMap<String, Integer> scores = new TreeMap<>();
        scores.put("charlie", 90);
        scores.put("alice", 95);
        scores.put("bob", 88);
        System.out.println("TreeMap (sorted by key):  " + scores);
        System.out.println("firstKey():               " + scores.firstKey());
        System.out.println("lastKey():                " + scores.lastKey());
        System.out.println("ceilingKey(\"b\"):          " + scores.ceilingKey("b") + "  (smallest key >= \"b\")");
        System.out.println("floorKey(\"b\"):            " + scores.floorKey("b") + "  (largest key <= \"b\")");
        System.out.println("headMap(\"bob\"):           " + scores.headMap("bob") + "  (keys < \"bob\")");
        System.out.println("tailMap(\"bob\"):           " + scores.tailMap("bob") + "  (keys >= \"bob\")");
        System.out.println("firstEntry():             " + scores.firstEntry());
    }

    // -----------------------------------------------------------------
    // 8) Common gotchas
    // -----------------------------------------------------------------
    private static void demoCommonGotchas() {
        printSection("8) Common gotchas");

        // Gotcha A: mutating a key AFTER insertion breaks lookups (hashCode changes -> wrong bucket)
        List<Integer> mutableKeyParts = new ArrayList<>(List.of(1, 2));
        // Using the list ITSELF as a key is illustrative but dangerous -- List's hashCode depends on contents
        Map<List<Integer>, String> mutableKeyMap = new HashMap<>();
        mutableKeyMap.put(mutableKeyParts, "original");
        System.out.println("Before mutating key: contains(original key list) = "
                + mutableKeyMap.containsKey(mutableKeyParts));
        mutableKeyParts.add(3);                 // mutates the key IN PLACE -- changes its hashCode()
        System.out.println("After mutating the key list's contents: contains(same reference) = "
                + mutableKeyMap.containsKey(mutableKeyParts)
                + "  <- entry became unreachable, still physically in its OLD bucket");

        // Gotcha B: null handling differs across implementations
        Set<String> hashSetNulls = new HashSet<>();
        hashSetNulls.add(null);                 // allowed -- one null permitted
        System.out.println("HashSet allows one null element: " + hashSetNulls);

        try {
            Set<String> treeSetNulls = new TreeSet<>();
            treeSetNulls.add(null);              // NOT allowed -- needs to compare, NPE
        } catch (NullPointerException e) {
            System.out.println("TreeSet.add(null) threw NullPointerException as expected (can't compare null)");
        }

        Map<String, String> hashMapNulls = new HashMap<>();
        hashMapNulls.put(null, "value-for-null-key");   // allowed -- one null key permitted
        hashMapNulls.put("key-for-null-value", null);    // null values always allowed
        System.out.println("HashMap allows null key and null values: " + hashMapNulls);

        try {
            Map<String, String> treeMapNulls = new TreeMap<>();
            treeMapNulls.put(null, "x");           // NOT allowed -- NPE
        } catch (NullPointerException e) {
            System.out.println("TreeMap.put(null, ...) threw NullPointerException as expected");
        }
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
