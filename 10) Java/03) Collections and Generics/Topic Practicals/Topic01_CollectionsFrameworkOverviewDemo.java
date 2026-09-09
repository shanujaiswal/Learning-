/*
 * Topic01_CollectionsFrameworkOverviewDemo.java
 *
 * Demonstrates:
 *   1. Collection vs Map -- the two root hierarchies
 *   2. Programming to the interface, not the implementation
 *   3. Raw types vs generic types -- why generics were added
 *   4. Arrays.asList() fixed-size trap vs List.of() true immutability
 *   5. A simple "choose the right collection" decision walkthrough
 *
 * Covers Theory chapter:
 *   10) Java/03) Collections and Generics/Theory/01 Java Collections Framework Overview.md
 *
 * Compile & run:
 *   javac Topic01_CollectionsFrameworkOverviewDemo.java
 *   java Topic01_CollectionsFrameworkOverviewDemo
 */

import java.util.*;

public class Topic01_CollectionsFrameworkOverviewDemo {

    public static void main(String[] args) {
        demoCollectionVsMap();
        demoProgramToInterface();
        demoRawTypesVsGenerics();
        demoArraysAsListVsListOf();
        demoChoosingTheRightCollection();
        System.out.println("\nAll Collections Framework overview demos completed.");
    }

    // -----------------------------------------------------------------
    // 1) Collection vs Map -- two separate hierarchies
    // -----------------------------------------------------------------
    private static void demoCollectionVsMap() {
        printSection("1) Collection vs Map");

        // Collection side -- single elements, directly iterable
        List<String> names = new ArrayList<>();
        names.add("Alice");
        names.add("Bob");
        System.out.println("Collection (List) direct iteration:");
        for (String name : names) {                 // Collection extends Iterable -- works directly
            System.out.println("  " + name);
        }

        // Map side -- key/value pairs, NOT a Collection, needs entrySet()/keySet()/values()
        Map<String, Integer> ages = new HashMap<>();
        ages.put("Alice", 30);
        ages.put("Bob", 25);
        System.out.println("Map iteration via entrySet():");
        for (Map.Entry<String, Integer> entry : ages.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println("Map keys only:   " + ages.keySet());
        System.out.println("Map values only: " + ages.values());
    }

    // -----------------------------------------------------------------
    // 2) Program to the interface -- swap implementations freely
    // -----------------------------------------------------------------
    private static void demoProgramToInterface() {
        printSection("2) Programming to the interface, not the implementation");

        // Declared as List -- caller code below doesn't care which concrete class backs it
        List<String> fruits = new ArrayList<>();
        fillAndPrint(fruits, "ArrayList");

        fruits = new LinkedList<>();               // swapped implementation, zero changes needed below
        fillAndPrint(fruits, "LinkedList");
    }

    // This method only knows about the List CONTRACT -- works identically regardless of implementation
    private static void fillAndPrint(List<String> list, String implName) {
        list.add("apple");
        list.add("banana");
        list.add("cherry");
        System.out.println(implName + " -> " + list + " (size=" + list.size() + ")");
    }

    // -----------------------------------------------------------------
    // 3) Raw types vs generics -- why generics were introduced
    // -----------------------------------------------------------------
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void demoRawTypesVsGenerics() {
        printSection("3) Raw types (pre-Java 5 style) vs generics");

        // Raw type -- compiles, but no compile-time type checking at all
        List rawList = new ArrayList();
        rawList.add("text");
        rawList.add(42);                  // legal for a raw type -- mixed types silently allowed
        System.out.println("Raw list (mixed types allowed): " + rawList);
        try {
            for (Object o : rawList) {
                String s = (String) o;    // fails on the Integer element -- classic runtime surprise
                System.out.println("  cast ok: " + s);
            }
        } catch (ClassCastException e) {
            System.out.println("  ClassCastException at RUNTIME on the Integer element: " + e.getMessage());
        }

        // Generic type -- compiler enforces type safety, no cast needed, no runtime surprise possible
        List<String> typedList = new ArrayList<>();
        typedList.add("text");
        // typedList.add(42);   // would NOT compile -- caught immediately, this is the whole point
        String s = typedList.get(0);      // no cast required
        System.out.println("Generic list (type-safe): " + typedList + " -> get(0) = " + s);
    }

    // -----------------------------------------------------------------
    // 4) Arrays.asList() fixed-size trap vs List.of() true immutability
    // -----------------------------------------------------------------
    private static void demoArraysAsListVsListOf() {
        printSection("4) Arrays.asList() vs List.of() vs new ArrayList<>(...)");

        List<String> fixedSize = Arrays.asList("a", "b", "c");
        fixedSize.set(0, "A");            // set() IS allowed -- writes through to the backing array
        System.out.println("Arrays.asList after set(0, \"A\"): " + fixedSize);
        try {
            fixedSize.add("d");            // add() is NOT allowed -- fixed-size list
        } catch (UnsupportedOperationException e) {
            System.out.println("  Arrays.asList().add() threw UnsupportedOperationException as expected");
        }

        List<String> immutable = List.of("x", "y", "z");
        try {
            immutable.set(0, "X");          // NOT allowed at all -- truly immutable, unlike Arrays.asList
        } catch (UnsupportedOperationException e) {
            System.out.println("  List.of().set() threw UnsupportedOperationException as expected");
        }

        // Fully mutable copy -- the common fix when you need a resizable list from fixed data
        List<String> mutableCopy = new ArrayList<>(List.of("p", "q"));
        mutableCopy.add("r");
        System.out.println("Fully mutable copy after add: " + mutableCopy);
    }

    // -----------------------------------------------------------------
    // 5) Choosing the right collection -- a worked walkthrough
    // -----------------------------------------------------------------
    private static void demoChoosingTheRightCollection() {
        printSection("5) Choosing the right collection -- worked examples");

        // Need key->value lookup -> Map
        Map<String, Double> priceByItem = new HashMap<>();
        priceByItem.put("apple", 0.5);
        priceByItem.put("banana", 0.3);
        System.out.println("Need lookup -> HashMap: " + priceByItem);

        // Need uniqueness, order doesn't matter -> HashSet
        Set<String> uniqueTags = new HashSet<>(List.of("java", "generics", "java", "collections"));
        System.out.println("Need uniqueness -> HashSet (duplicates dropped): " + uniqueTags);

        // Need FIFO processing order -> ArrayDeque as a Queue
        Queue<String> printQueue = new ArrayDeque<>();
        printQueue.offer("job1");
        printQueue.offer("job2");
        System.out.println("Need FIFO -> ArrayDeque as Queue, next to process: " + printQueue.peek());

        // Need indexed random access, mostly appends -> ArrayList
        List<Integer> scores = new ArrayList<>();
        scores.add(90);
        scores.add(85);
        System.out.println("Need indexed access -> ArrayList, scores.get(0): " + scores.get(0));
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
