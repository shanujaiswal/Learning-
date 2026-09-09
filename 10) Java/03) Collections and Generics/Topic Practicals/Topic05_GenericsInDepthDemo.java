/*
 * Topic05_GenericsInDepthDemo.java
 *
 * Demonstrates:
 *   1. Raw Object-based container vs a generic class -- casts eliminated, compile-time safety gained
 *   2. Generic classes with one and multiple type parameters (Box<T>, Pair<K,V>)
 *   3. Generic methods -- type inference, explicit type witnesses, multiple type parameters
 *   4. Bounded type parameters -- <T extends Comparable<T>> and multiple bounds (Number & Comparable)
 *   5. Wildcards -- unbounded ?, upper-bounded ? extends T, lower-bounded ? super T, and the PECS rule
 *   6. Why List<? extends T> forbids add() -- the compile-time safety reasoning
 *   7. Type erasure -- proving List<String> and List<Integer> share one Class object at runtime
 *   8. Erasure consequences -- no new T(), no generic arrays, no instanceof on parameterized types,
 *      no overloads that erase identically, no static fields of type T
 *
 * Covers Theory chapter:
 *   10) Java/03) Collections and Generics/Theory/05 Generics In Depth.md
 *
 * Compile & run:
 *   javac Topic05_GenericsInDepthDemo.java
 *   java Topic05_GenericsInDepthDemo
 */

import java.util.*;

public class Topic05_GenericsInDepthDemo {

    public static void main(String[] args) {
        demoWhyGenericsExist();
        demoGenericClasses();
        demoGenericMethods();
        demoBoundedTypeParameters();
        demoWildcards();
        demoWhyExtendsWildcardForbidsAdd();
        demoTypeErasure();
        demoErasureConsequences();
        System.out.println("\nAll Generics in depth demos completed.");
    }

    // -----------------------------------------------------------------
    // 1) Why generics exist -- raw Object container vs generic container
    // -----------------------------------------------------------------
    @SuppressWarnings("rawtypes")
    private static void demoWhyGenericsExist() {
        printSection("1) Why generics exist -- casts and runtime ClassCastException vs compile-time safety");

        List rawList = new ArrayList();
        rawList.add("hello");
        rawList.add(42);                       // legal for raw type -- no compile-time check at all
        String s = (String) rawList.get(0);    // manual cast required -- can fail at runtime
        System.out.println("Raw list manual cast for element 0: " + s);
        try {
            String bad = (String) rawList.get(1);   // fails -- element 1 is actually an Integer
            System.out.println(bad);
        } catch (ClassCastException e) {
            System.out.println("Raw list cast of element 1 threw ClassCastException at RUNTIME: " + e.getMessage());
        }

        List<String> typedList = new ArrayList<>();
        typedList.add("hello");
        // typedList.add(42);                  // would NOT compile -- caught immediately, this is the whole point
        String s2 = typedList.get(0);           // no cast needed -- compiler already knows the type
        System.out.println("Generic list, no cast needed: " + s2);
    }

    // -----------------------------------------------------------------
    // 2) Generic classes -- one type parameter and multiple type parameters
    // -----------------------------------------------------------------
    private static void demoGenericClasses() {
        printSection("2) Generic classes -- Box<T> and Pair<K, V>");

        Box<String> stringBox = new Box<>();
        stringBox.set("hello");
        System.out.println("Box<String>.get(): " + stringBox.get());

        Box<Integer> intBox = new Box<>();
        intBox.set(42);
        System.out.println("Box<Integer>.get(): " + intBox.get());
        // intBox.set("wrong type");            // COMPILE ERROR -- caught immediately

        Pair<String, Integer> entry = new Pair<>("age", 30);
        System.out.println("Pair<String, Integer>: " + entry + ", key=" + entry.getKey() + ", value=" + entry.getValue());
    }

    private static class Box<T> {
        private T content;
        public void set(T content) { this.content = content; }
        public T get() { return content; }
    }

    private static class Pair<K, V> {
        private final K key;
        private final V value;
        public Pair(K key, V value) { this.key = key; this.value = value; }
        public K getKey() { return key; }
        public V getValue() { return value; }

        @Override
        public String toString() { return "(" + key + ", " + value + ")"; }
    }

    // -----------------------------------------------------------------
    // 3) Generic methods -- inference, explicit type witness, multiple params
    // -----------------------------------------------------------------
    private static void demoGenericMethods() {
        printSection("3) Generic methods -- type inference and type witnesses");

        String first = firstElement(List.of("a", "b", "c"));      // T inferred as String
        Integer firstNum = firstElement(List.of(1, 2, 3));         // T inferred as Integer
        System.out.println("firstElement(List<String>)  -> " + first);
        System.out.println("firstElement(List<Integer>) -> " + firstNum);

        Integer[] arr = {1, 2, 3};
        swap(arr, 0, 2);
        System.out.println("After swap(arr, 0, 2): " + Arrays.toString(arr));

        Pair<String, Integer> p1 = Topic05_GenericsInDepthDemo.<String, Integer>makePair("x", 1);  // explicit witness
        Pair<String, Integer> p2 = makePair("y", 2);                                                // usually inference suffices
        System.out.println("Explicit type witness: " + p1 + ", inferred: " + p2);
    }

    private static <T> T firstElement(List<T> list) {
        return list.get(0);
    }

    private static <T> void swap(T[] array, int i, int j) {
        T temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    private static <K, V> Pair<K, V> makePair(K key, V value) {
        return new Pair<>(key, value);
    }

    // -----------------------------------------------------------------
    // 4) Bounded type parameters -- single bound and multiple bounds
    // -----------------------------------------------------------------
    private static void demoBoundedTypeParameters() {
        printSection("4) Bounded type parameters -- <T extends Comparable<T>> and multiple bounds");

        System.out.println("max(List.of(3, 7, 2, 9, 4)) = " + max(List.of(3, 7, 2, 9, 4)));
        System.out.println("max(List.of(\"banana\", \"apple\", \"cherry\")) = "
                + max(List.of("banana", "apple", "cherry")));

        // Multiple bounds: T must be a Number AND Comparable<T> -- class bound (if any) must be listed first
        System.out.println("maxNumeric(List.of(1.5, 3.2, 2.7)) = " + maxNumeric(List.of(1.5, 3.2, 2.7)));
    }

    // Only types implementing Comparable<T> are accepted -- this lets us safely call compareTo()
    private static <T extends Comparable<T>> T max(List<T> list) {
        T max = list.get(0);
        for (T item : list) {
            if (item.compareTo(max) > 0) {
                max = item;
            }
        }
        return max;
    }

    private static <T extends Number & Comparable<T>> T maxNumeric(List<T> list) {
        T max = list.get(0);
        for (T item : list) {
            if (item.compareTo(max) > 0) max = item;
        }
        return max;
    }

    // -----------------------------------------------------------------
    // 5) Wildcards -- unbounded, upper-bounded (PECS producer), lower-bounded (PECS consumer)
    // -----------------------------------------------------------------
    private static void demoWildcards() {
        printSection("5) Wildcards -- ?, ? extends T, ? super T, and PECS");

        System.out.println("sumAll(List<Integer>): " + sumAll(List.of(1, 2, 3)));
        System.out.println("sumAll(List<Double>):  " + sumAll(List.of(1.5, 2.5)));

        List<Number> numbers = new ArrayList<>();
        addNumbers(numbers);                    // List<Number> accepted -- Number is a supertype of Integer
        System.out.println("addNumbers() wrote Integers into a List<Number>: " + numbers);

        printAll(List.of("a", "b", "c"));        // unbounded wildcard -- doesn't care about element type
        printAll(List.of(1, 2, 3));
    }

    // Producer -- only READS Number-compatible values out, so "? extends Number" (PECS: extends for producers)
    private static double sumAll(List<? extends Number> list) {
        double sum = 0;
        for (Number n : list) {
            sum += n.doubleValue();
        }
        // list.add(5);   // COMPILE ERROR -- see demoWhyExtendsWildcardForbidsAdd() for why
        return sum;
    }

    // Consumer -- only WRITES Integer values in, so "? super Integer" (PECS: super for consumers)
    private static void addNumbers(List<? super Integer> list) {
        list.add(1);
        list.add(2);
        // Integer x = list.get(0);   // only safe to read as Object -- list could be List<Number>/List<Object>
    }

    // Unbounded -- method genuinely doesn't care about the element type at all
    private static void printAll(List<?> list) {
        System.out.print("printAll: ");
        for (Object o : list) {                  // elements can only be read as Object -- type truly unknown
            System.out.print(o + " ");
        }
        System.out.println("(size=" + list.size() + ")");
    }

    // -----------------------------------------------------------------
    // 6) Why List<? extends T> forbids add() -- the compile-time reasoning
    // -----------------------------------------------------------------
    private static void demoWhyExtendsWildcardForbidsAdd() {
        printSection("6) Why List<? extends T> forbids add() -- compile-time safety reasoning");

        List<Integer> actualIntegers = new ArrayList<>(List.of(1, 2, 3));
        List<? extends Number> viewedAsNumbers = actualIntegers;   // legal -- Integer IS-A Number
        System.out.println("List<Integer> viewed as List<? extends Number>: " + viewedAsNumbers);
        // viewedAsNumbers.add(3.14);   // COMPILE ERROR if uncommented -- would insert a Double into
                                          // what is ACTUALLY a List<Integer> at runtime, corrupting it.
                                          // The compiler forbids this for ANY concrete type except null,
                                          // because it cannot verify what the real backing type is.
        System.out.println("-> add() is compile-blocked on List<? extends Number> because the compiler");
        System.out.println("   cannot prove the real list can safely hold whatever type you'd pass in.");
    }

    // -----------------------------------------------------------------
    // 7) Type erasure -- proving generic type info vanishes at runtime
    // -----------------------------------------------------------------
    private static void demoTypeErasure() {
        printSection("7) Type erasure -- what actually happens at runtime");

        List<String> strings = new ArrayList<>();
        List<Integer> ints = new ArrayList<>();
        System.out.println("strings.getClass() == ints.getClass() ? " + (strings.getClass() == ints.getClass())
                + "  <- true! Both are just ArrayList.class at runtime; List<String> vs List<Integer>");
        System.out.println("   only exists for the COMPILER -- the JVM erases it entirely.");

        System.out.println("strings.getClass().getName() = " + strings.getClass().getName());
        System.out.println("ints.getClass().getName()     = " + ints.getClass().getName());
    }

    // -----------------------------------------------------------------
    // 8) Erasure consequences -- the classic "generic gotchas"
    // -----------------------------------------------------------------
    private static void demoErasureConsequences() {
        printSection("8) Erasure consequences -- the classic generic gotchas");

        // (a) Cannot create an instance of a type parameter directly -- Factory.create() below returns null instead
        Factory<String> factory = new Factory<>();
        System.out.println("(a) Factory<T>.create() cannot do 'new T()' -- returns: " + factory.create());

        // (b) Cannot create a generic array directly -- requires an unchecked cast workaround
        Container<String> container = new Container<>();
        System.out.println("(b) Container<T>'s internal array uses an unchecked (T[]) cast workaround, length="
                + container.arrayLength());

        // (c) Cannot use instanceof with a parameterized type -- only the unbounded wildcard form compiles
        List<String> list = new ArrayList<>();
        // if (list instanceof List<String>) {}   // COMPILE ERROR -- erased at runtime, type unknown
        if (list instanceof List<?>) {
            System.out.println("(c) 'list instanceof List<?>' compiles (unbounded wildcard); "
                    + "'instanceof List<String>' would NOT compile.");
        }

        // (d) Cannot overload methods that erase to the same signature
        // void process(List<String> list) {}
        // void process(List<Integer> list) {}   // COMPILE ERROR -- both erase to process(List)
        System.out.println("(d) process(List<String>) and process(List<Integer>) cannot coexist as overloads "
                + "-- both erase to process(List) at the bytecode level.");

        // (e) Static context cannot reference a class's own type parameter
        // class Holder<T> { static T instance; }   // COMPILE ERROR
        System.out.println("(e) 'static T instance;' inside a generic class does not compile -- static members "
                + "are shared across ALL parameterizations (Holder<String>, Holder<Integer>, ...), but T is "
                + "only known per-instance.");

        // Raw type still compiles for backward compatibility, but loses all type safety
        @SuppressWarnings({"rawtypes", "unchecked"})
        List rawList = new ArrayList();
        rawList.add("mixed");
        rawList.add(123);
        System.out.println("Raw type List still compiles (with an 'unchecked' warning) for backward "
                + "compatibility: " + rawList);
    }

    private static class Factory<T> {
        T create() {
            // return new T();     // COMPILE ERROR -- the JVM doesn't know what T is at runtime
            return null;
        }
    }

    private static class Container<T> {
        @SuppressWarnings("unchecked")
        T[] array = (T[]) new Object[10];   // workaround -- unchecked cast, use with care
        int arrayLength() { return array.length; }
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
