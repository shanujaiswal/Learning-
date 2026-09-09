/*
 * Topic02_ListImplementationsDemo.java
 *
 * Demonstrates:
 *   1. ArrayList vs LinkedList vs Vector -- basic usage and behavior
 *   2. Timing comparison: get(index) cost on ArrayList vs LinkedList
 *   3. Timing comparison: insert-at-front cost on ArrayList vs LinkedList
 *   4. All the iteration methods (for-each, indexed, Iterator, ListIterator, forEach, stream)
 *   5. Fail-fast ConcurrentModificationException vs the correct Iterator.remove() fix
 *   6. The remove(int index) vs remove(Object) autoboxing gotcha
 *
 * Covers Theory chapter:
 *   10) Java/03) Collections and Generics/Theory/02 List Implementations.md
 *
 * Compile & run:
 *   javac Topic02_ListImplementationsDemo.java
 *   java Topic02_ListImplementationsDemo
 */

import java.util.*;

public class Topic02_ListImplementationsDemo {

    public static void main(String[] args) {
        demoBasicUsage();
        demoGetPerformance();
        demoInsertAtFrontPerformance();
        demoIterationMethods();
        demoFailFastVsCorrectRemoval();
        demoRemoveIndexVsObjectGotcha();
        System.out.println("\nAll List implementation demos completed.");
    }

    // -----------------------------------------------------------------
    // 1) Basic usage across the three main List implementations
    // -----------------------------------------------------------------
    private static void demoBasicUsage() {
        printSection("1) ArrayList vs LinkedList vs Vector -- basic usage");

        List<String> arrayList = new ArrayList<>();
        arrayList.add("a");
        arrayList.add("b");
        arrayList.add(0, "z");                 // insert at front -- O(n) shift for ArrayList
        System.out.println("ArrayList:  " + arrayList);

        LinkedList<String> linkedList = new LinkedList<>();
        linkedList.addLast("a");
        linkedList.addLast("b");
        linkedList.addFirst("z");               // O(1) for LinkedList -- just re-links head pointer
        System.out.println("LinkedList: " + linkedList + "  (also usable as Deque: " + linkedList.peekFirst() + ")");

        Vector<String> vector = new Vector<>();
        vector.add("a");
        vector.add("b");
        vector.add(0, "z");
        System.out.println("Vector:     " + vector + "  (synchronized -- legacy, avoid in new code)");
    }

    // -----------------------------------------------------------------
    // 2) get(index) performance: O(1) ArrayList vs O(n) LinkedList
    // -----------------------------------------------------------------
    private static void demoGetPerformance() {
        printSection("2) get(index) cost -- ArrayList O(1) vs LinkedList O(n)");

        int n = 50_000;
        List<Integer> arrayList = new ArrayList<>();
        List<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            arrayList.add(i);
            linkedList.add(i);
        }

        long startArray = System.nanoTime();
        long sumArray = 0;
        for (int i = 0; i < arrayList.size(); i++) {
            sumArray += arrayList.get(i);           // O(1) each -> O(n) total
        }
        long arrayTimeMs = (System.nanoTime() - startArray) / 1_000_000;

        long startLinked = System.nanoTime();
        long sumLinked = 0;
        // NOTE: intentionally using indexed get() here to PROVE the O(n^2) trap -- see comment below
        int smallerN = 4_000;                        // kept small -- O(n^2) would be too slow at n=50000
        for (int i = 0; i < smallerN; i++) {
            sumLinked += linkedList.get(i);            // O(n) each -> O(n^2) total for the whole loop!
        }
        long linkedTimeMs = (System.nanoTime() - startLinked) / 1_000_000;

        System.out.println("ArrayList  indexed get() over " + n + " elements: " + arrayTimeMs + " ms (sum=" + sumArray + ")");
        System.out.println("LinkedList indexed get() over " + smallerN + " elements (smaller n!): " + linkedTimeMs + " ms (sum=" + sumLinked + ")");
        System.out.println("-> LinkedList indexed access is dramatically slower per-element -- always prefer an Iterator on LinkedList.");
    }

    // -----------------------------------------------------------------
    // 3) Insert-at-front performance: O(n) ArrayList vs O(1) LinkedList
    // -----------------------------------------------------------------
    private static void demoInsertAtFrontPerformance() {
        printSection("3) addFirst()/add(0, x) cost -- ArrayList O(n) vs LinkedList O(1)");

        int n = 20_000;

        long startArray = System.nanoTime();
        List<Integer> arrayList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            arrayList.add(0, i);                    // O(n) every time -- shifts everything right
        }
        long arrayTimeMs = (System.nanoTime() - startArray) / 1_000_000;

        long startLinked = System.nanoTime();
        LinkedList<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            linkedList.addFirst(i);                  // O(1) every time -- just re-links the head node
        }
        long linkedTimeMs = (System.nanoTime() - startLinked) / 1_000_000;

        System.out.println(n + " front-inserts into ArrayList:  " + arrayTimeMs + " ms  (O(n) each -> O(n^2) total)");
        System.out.println(n + " front-inserts into LinkedList: " + linkedTimeMs + " ms  (O(1) each -> O(n) total)");
        System.out.println("-> For front-heavy workloads LinkedList (or better, ArrayDeque) wins decisively.");
    }

    // -----------------------------------------------------------------
    // 4) All the iteration methods
    // -----------------------------------------------------------------
    private static void demoIterationMethods() {
        printSection("4) Iteration methods on a List");

        List<String> list = new ArrayList<>(List.of("a", "b", "c"));

        System.out.print("Enhanced for-loop: ");
        for (String s : list) {
            System.out.print(s + " ");
        }
        System.out.println();

        System.out.print("Classic indexed for-loop: ");
        for (int i = 0; i < list.size(); i++) {
            System.out.print(list.get(i) + " ");
        }
        System.out.println();

        System.out.print("Iterator: ");
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            System.out.print(it.next() + " ");
        }
        System.out.println();

        System.out.print("ListIterator (forward then modifying to uppercase): ");
        ListIterator<String> lit = list.listIterator();
        while (lit.hasNext()) {
            lit.set(lit.next().toUpperCase());
        }
        System.out.println(list);

        System.out.print("forEach with lambda: ");
        list.forEach(s -> System.out.print(s + " "));
        System.out.println();

        System.out.print("Stream pipeline (filter startsWith B): ");
        list.stream().filter(s -> s.startsWith("B")).forEach(s -> System.out.print(s + " "));
        System.out.println();
    }

    // -----------------------------------------------------------------
    // 5) Fail-fast ConcurrentModificationException vs correct removal
    // -----------------------------------------------------------------
    private static void demoFailFastVsCorrectRemoval() {
        printSection("5) Fail-fast CME vs Iterator.remove()");

        List<Integer> nums = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        System.out.println("Original: " + nums);

        // INCORRECT -- structurally modifying the list directly during a for-each throws CME
        try {
            for (Integer n : nums) {
                if (n % 2 == 0) {
                    nums.remove(n);              // structural modification NOT via the iterator
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("Direct removal during for-each threw ConcurrentModificationException as expected.");
        }

        // CORRECT -- use the Iterator's own remove()
        List<Integer> nums2 = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        Iterator<Integer> it = nums2.iterator();
        while (it.hasNext()) {
            if (it.next() % 2 == 0) {
                it.remove();                      // safe -- keeps modCount in sync
            }
        }
        System.out.println("Correctly filtered via Iterator.remove(): " + nums2);

        // ALSO CORRECT (Java 8+) -- removeIf() is the simplest fix for straightforward predicates
        List<Integer> nums3 = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        nums3.removeIf(n -> n % 2 == 0);
        System.out.println("Correctly filtered via removeIf():      " + nums3);
    }

    // -----------------------------------------------------------------
    // 6) remove(int index) vs remove(Object) autoboxing gotcha
    // -----------------------------------------------------------------
    private static void demoRemoveIndexVsObjectGotcha() {
        printSection("6) remove(int) vs remove(Object) autoboxing gotcha");

        List<Integer> nums = new ArrayList<>(List.of(10, 20, 30));
        System.out.println("Original: " + nums);

        nums.remove(2);                            // removes element AT INDEX 2 -> removes 30
        System.out.println("After remove(2) [removes by INDEX]: " + nums);

        List<Integer> nums2 = new ArrayList<>(List.of(10, 20, 30));
        nums2.remove(Integer.valueOf(20));           // removes the VALUE 20 -> removes element equal to 20
        System.out.println("After remove(Integer.valueOf(20)) [removes by VALUE]: " + nums2);
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
