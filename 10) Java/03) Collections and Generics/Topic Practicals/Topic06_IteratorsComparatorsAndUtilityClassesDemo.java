/*
 * Topic06_IteratorsComparatorsAndUtilityClassesDemo.java
 *
 * Demonstrates:
 *   1. Iterator -- hasNext/next/remove, and independent iterators over the same List
 *   2. ListIterator -- bidirectional traversal, in-place set(), mid-iteration add(), the next/previous gotcha
 *   3. Comparable -- natural ordering defined inside the class, used by Collections.sort()
 *   4. Comparator -- external ordering, anonymous class vs lambda vs factory methods, chaining with thenComparing
 *   5. Comparable vs Comparator side-by-side on the same data
 *   6. Collections utility class -- sort/reverse/shuffle/max/min/frequency/binarySearch/swap/fill/nCopies,
 *      unmodifiable & synchronized wrapper views, empty/singleton helpers
 *   7. Arrays utility class -- sort/toString/binarySearch/copyOf/copyOfRange/equals/fill, deep variants
 *      for multi-dimensional arrays, and the stream bridge
 *   8. Immutable collections (List.of/Set.of/Map.of/copyOf) vs Collections.unmodifiableList views
 *   9. Common gotchas -- sorting without Comparable, unsorted binarySearch, null-safe Comparator chains
 *
 * Covers Theory chapter:
 *   10) Java/03) Collections and Generics/Theory/06 Iterators Comparators and Utility Classes.md
 *
 * Compile & run:
 *   javac Topic06_IteratorsComparatorsAndUtilityClassesDemo.java
 *   java Topic06_IteratorsComparatorsAndUtilityClassesDemo
 */

import java.util.*;

public class Topic06_IteratorsComparatorsAndUtilityClassesDemo {

    public static void main(String[] args) {
        demoIterator();
        demoListIterator();
        demoComparableNaturalOrdering();
        demoComparatorExternalOrdering();
        demoComparableVsComparatorSideBySide();
        demoCollectionsUtilityClass();
        demoArraysUtilityClass();
        demoImmutableCollections();
        demoCommonGotchas();
        System.out.println("\nAll Iterators, Comparators and Utility Classes demos completed.");
    }

    // -----------------------------------------------------------------
    // 1) Iterator -- hasNext/next/remove, independent iterators
    // -----------------------------------------------------------------
    private static void demoIterator() {
        printSection("1) Iterator -- hasNext()/next()/remove(), independent cursors");

        List<Integer> nums = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        Iterator<Integer> it = nums.iterator();
        while (it.hasNext()) {
            int n = it.next();
            if (n % 2 == 0) {
                it.remove();                 // the ONLY safe way to remove while iterating
            }
        }
        System.out.println("After removing evens via Iterator.remove(): " + nums);

        // Iterable vs Iterator -- each call to iterator() returns a fresh, independent cursor
        List<String> letters = new ArrayList<>(List.of("a", "b", "c"));
        System.out.print("Nested independent iterators over the same list: ");
        for (String outer : letters) {
            for (String inner : letters) {
                System.out.print(outer + inner + " ");
            }
        }
        System.out.println();
    }

    // -----------------------------------------------------------------
    // 2) ListIterator -- bidirectional, set(), add(), the next/previous gotcha
    // -----------------------------------------------------------------
    private static void demoListIterator() {
        printSection("2) ListIterator -- bidirectional traversal, set(), add()");

        List<String> list = new ArrayList<>(List.of("a", "b", "c"));
        ListIterator<String> lit = list.listIterator();
        while (lit.hasNext()) {
            String s = lit.next();
            if (s.equals("b")) {
                lit.set("B");                 // replace in place -- legal, unlike plain Iterator
                lit.add("b2");                 // insert right after current cursor position -- legal too
            }
        }
        System.out.println("After set(\"B\") and add(\"b2\") mid-traversal: " + list);

        ListIterator<String> backward = list.listIterator(list.size());
        System.out.print("Traversing backward via previous(): ");
        while (backward.hasPrevious()) {
            System.out.print(backward.previous() + " ");
        }
        System.out.println();

        // Gotcha: next() then previous() returns the SAME element -- cursor sits BETWEEN elements
        ListIterator<String> demo = list.listIterator();
        String forward = demo.next();
        String back = demo.previous();
        System.out.println("next() returned \"" + forward + "\", immediate previous() returned \"" + back
                + "\" -- same element, because the cursor sits between elements, not on one.");
    }

    // -----------------------------------------------------------------
    // 3) Comparable -- natural ordering defined inside the class
    // -----------------------------------------------------------------
    private static void demoComparableNaturalOrdering() {
        printSection("3) Comparable -- natural ordering defined by the class itself");

        List<Employee> employees = new ArrayList<>(List.of(
                new Employee("Alice", 70000),
                new Employee("Bob", 50000),
                new Employee("Carol", 90000)));
        Collections.sort(employees);          // uses compareTo() -- natural order (ascending by salary)
        System.out.println("Sorted by natural order (salary ascending): " + employees);
    }

    private static class Employee implements Comparable<Employee> {
        final String name;
        final int salary;
        Employee(String name, int salary) { this.name = name; this.salary = salary; }

        @Override
        public int compareTo(Employee other) {
            return Integer.compare(this.salary, other.salary);
        }

        @Override
        public String toString() { return name + "($" + salary + ")"; }
    }

    // -----------------------------------------------------------------
    // 4) Comparator -- external ordering: anonymous class, lambda, factory methods, chaining
    // -----------------------------------------------------------------
    private static void demoComparatorExternalOrdering() {
        printSection("4) Comparator -- external, pluggable ordering");

        List<Employee> employees = new ArrayList<>(List.of(
                new Employee("Bob", 70000),
                new Employee("Alice", 70000),
                new Employee("Carol", 50000)));

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

        List<Employee> byNameOldSorted = new ArrayList<>(employees);
        byNameOldSorted.sort(byNameOld);
        System.out.println("Sorted by anonymous-class Comparator (name):  " + byNameOldSorted);

        List<Employee> byNameSorted = new ArrayList<>(employees);
        byNameSorted.sort(byName);
        System.out.println("Sorted by lambda Comparator (name):           " + byNameSorted);

        List<Employee> bySalarySorted = new ArrayList<>(employees);
        bySalarySorted.sort(bySalary);
        System.out.println("Sorted by comparingInt (salary):              " + bySalarySorted);

        List<Employee> chained = new ArrayList<>(employees);
        chained.sort(byNameThenSalary);
        System.out.println("Sorted by name THEN salary (tie-break chain): " + chained);

        List<Employee> descSalary = new ArrayList<>(employees);
        descSalary.sort(bySalaryDesc);
        System.out.println("Sorted by salary descending (.reversed()):    " + descSalary);
    }

    // -----------------------------------------------------------------
    // 5) Comparable vs Comparator side by side
    // -----------------------------------------------------------------
    private static void demoComparableVsComparatorSideBySide() {
        printSection("5) Comparable vs Comparator -- side by side on the same data");

        List<Employee> employees = new ArrayList<>(List.of(
                new Employee("Zack", 60000),
                new Employee("Amy", 80000)));

        List<Employee> naturalOrder = new ArrayList<>(employees);
        Collections.sort(naturalOrder);                          // Comparable.compareTo() -- ONE fixed order
        System.out.println("Comparable (natural order, by salary):     " + naturalOrder);

        List<Employee> byNameOrder = new ArrayList<>(employees);
        byNameOrder.sort(Comparator.comparing(e -> e.name));       // Comparator -- an ALTERNATE order, no class change needed
        System.out.println("Comparator (alternate order, by name):     " + byNameOrder);

        System.out.println("-> Comparable requires modifying the class; Comparator works on any class, "
                + "even ones you don't own, and supports unlimited orderings.");
    }

    // -----------------------------------------------------------------
    // 6) Collections utility class
    // -----------------------------------------------------------------
    private static void demoCollectionsUtilityClass() {
        printSection("6) java.util.Collections -- static utility methods");

        List<Integer> nums = new ArrayList<>(List.of(5, 3, 8, 1, 9));
        System.out.println("Original:                " + nums);

        Collections.sort(nums);
        System.out.println("sort() natural order:    " + nums);

        Collections.sort(nums, Comparator.reverseOrder());
        System.out.println("sort() reverseOrder:     " + nums);

        Collections.reverse(nums);
        System.out.println("reverse():               " + nums);

        System.out.println("max():                   " + Collections.max(nums));
        System.out.println("min():                   " + Collections.min(nums));
        System.out.println("frequency(nums, 5):      " + Collections.frequency(nums, 5));

        List<Integer> sortedForSearch = new ArrayList<>(nums);
        Collections.sort(sortedForSearch);
        System.out.println("sorted for binarySearch: " + sortedForSearch);
        System.out.println("binarySearch(sorted, 8): " + Collections.binarySearch(sortedForSearch, 8));

        Collections.swap(nums, 0, 1);
        System.out.println("swap(0, 1):              " + nums);

        List<Integer> toFill = new ArrayList<>(List.of(1, 2, 3));
        Collections.fill(toFill, 0);
        System.out.println("fill(0):                 " + toFill);

        System.out.println("nCopies(3, \"x\"):        " + Collections.nCopies(3, "x"));

        // Wrapper methods -- unmodifiable and synchronized views
        List<Integer> readOnly = Collections.unmodifiableList(nums);
        try {
            readOnly.add(99);
        } catch (UnsupportedOperationException e) {
            System.out.println("unmodifiableList view rejects mutation as expected");
        }
        // Gotcha: it's a VIEW -- mutating the ORIGINAL still shows through the wrapper
        nums.add(77);
        System.out.println("Original mutated -> visible through the read-only view too: " + readOnly);

        List<Integer> threadSafe = Collections.synchronizedList(new ArrayList<>(List.of(1, 2, 3)));
        System.out.println("synchronizedList wrapper: " + threadSafe + " (per-method locking added)");

        System.out.println("emptyList():             " + Collections.emptyList());
        System.out.println("singleton(\"only-one\"):  " + Collections.singleton("only-one"));
    }

    // -----------------------------------------------------------------
    // 7) Arrays utility class
    // -----------------------------------------------------------------
    private static void demoArraysUtilityClass() {
        printSection("7) java.util.Arrays -- static utility methods for plain arrays");

        int[] arr = {5, 3, 8, 1, 9};
        System.out.println("Before sort: " + Arrays.toString(arr) + "  (Arrays.toString gives a readable form)");

        Arrays.sort(arr);
        System.out.println("After sort:  " + Arrays.toString(arr));

        int idx = Arrays.binarySearch(arr, 8);
        System.out.println("binarySearch(arr, 8): index " + idx);

        int[] copy = Arrays.copyOf(arr, 10);
        System.out.println("copyOf(arr, 10) [zero-padded]: " + Arrays.toString(copy));

        int[] range = Arrays.copyOfRange(arr, 1, 3);
        System.out.println("copyOfRange(arr, 1, 3): " + Arrays.toString(range));

        int[] arrDuplicate = Arrays.copyOf(arr, arr.length);
        System.out.println("equals(arr, duplicate) [element-wise]: " + Arrays.equals(arr, arrDuplicate));
        System.out.println("arr == duplicate (reference equality): " + (arr == arrDuplicate));

        int[] toFill = new int[5];
        Arrays.fill(toFill, 7);
        System.out.println("fill(toFill, 7): " + Arrays.toString(toFill));

        List<Integer> boxedList = Arrays.asList(1, 2, 3);          // FIXED-SIZE view backed by the array
        System.out.println("Arrays.asList(1,2,3) [fixed-size view]: " + boxedList);

        int[][] matrix = {{1, 2}, {3, 4}};
        System.out.println("deepToString(matrix): " + Arrays.deepToString(matrix));
        System.out.println("deepEquals(matrix, matrix): " + Arrays.deepEquals(matrix, matrix));

        int sum = Arrays.stream(arr).sum();
        System.out.println("Arrays.stream(arr).sum(): " + sum);
    }

    // -----------------------------------------------------------------
    // 8) Immutable collections vs Collections.unmodifiableList views
    // -----------------------------------------------------------------
    private static void demoImmutableCollections() {
        printSection("8) Immutable collections (Java 9+) vs unmodifiable VIEWS");

        List<String> immutableList = List.of("a", "b", "c");
        Set<Integer> immutableSet = Set.of(1, 2, 3);
        Map<String, Integer> immutableMap = Map.of("a", 1, "b", 2);
        Map<String, Integer> biggerMap = Map.ofEntries(
                Map.entry("a", 1), Map.entry("b", 2), Map.entry("c", 3));
        System.out.println("List.of: " + immutableList + ", Set.of: " + immutableSet
                + ", Map.of: " + immutableMap + ", Map.ofEntries: " + biggerMap);

        try {
            immutableList.add("d");
        } catch (UnsupportedOperationException e) {
            System.out.println("List.of().add() threw UnsupportedOperationException as expected");
        }
        try {
            List.of("a", null);          // rejects null outright, unlike ArrayList
        } catch (NullPointerException e) {
            System.out.println("List.of(\"a\", null) threw NullPointerException as expected");
        }
        try {
            Set.of(1, 1);                 // rejects duplicates at creation time, not silently ignored
        } catch (IllegalArgumentException e) {
            System.out.println("Set.of(1, 1) threw IllegalArgumentException as expected (duplicate)");
        }

        // Real independent immutable snapshot vs a thin mutable-backed view
        List<String> mutable = new ArrayList<>(List.of("x", "y"));
        List<String> snapshot = List.copyOf(mutable);              // independent immutable copy
        List<String> unmodifiableView = Collections.unmodifiableList(mutable);  // thin view over `mutable`
        mutable.add("z");
        System.out.println("List.copyOf() snapshot after mutating source (unaffected): " + snapshot);
        System.out.println("unmodifiableList() view after mutating source (reflects it): " + unmodifiableView);
    }

    // -----------------------------------------------------------------
    // 9) Common gotchas
    // -----------------------------------------------------------------
    private static void demoCommonGotchas() {
        printSection("9) Common gotchas");

        // Gotcha A: sorting a list of non-Comparable objects with no Comparator -> ClassCastException
        try {
            List<Object> mixed = new ArrayList<>(List.of(new Object(), new Object()));
            Collections.sort((List) mixed);
        } catch (ClassCastException e) {
            System.out.println("Collections.sort() on non-Comparable objects threw ClassCastException as expected");
        }

        // Gotcha B: Arrays.asList() is fixed-size
        try {
            List<Integer> fixed = Arrays.asList(1, 2, 3);
            fixed.add(4);
        } catch (UnsupportedOperationException e) {
            System.out.println("Arrays.asList().add() threw UnsupportedOperationException as expected -- "
                    + "wrap with 'new ArrayList<>(Arrays.asList(...))' for a fully mutable copy");
        }

        // Gotcha C: binarySearch on an UNSORTED list gives undefined results
        List<Integer> unsorted = List.of(5, 1, 9, 3);
        int result = Collections.binarySearch(unsorted, 9);
        System.out.println("binarySearch on an UNSORTED list " + unsorted + " for value 9 returned: " + result
                + "  <- undefined/unreliable; binarySearch does NOT verify the input is sorted");

        // Gotcha D: Comparator.comparing() throws NPE on a null field by default; nullsFirst/nullsLast fix it
        List<String> withNull = new ArrayList<>(Arrays.asList("banana", null, "apple"));
        try {
            withNull.sort(Comparator.naturalOrder());
        } catch (NullPointerException e) {
            System.out.println("Sorting a list containing null with naturalOrder() threw NullPointerException");
        }
        withNull.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        System.out.println("Sorted with nullsFirst(naturalOrder()): " + withNull);
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
