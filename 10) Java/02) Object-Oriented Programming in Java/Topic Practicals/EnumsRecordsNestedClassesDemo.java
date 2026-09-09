/*
 * EnumsRecordsNestedClassesDemo.java
 *
 * Demonstrates:
 *   1. An enum (Operation) with fields, a constructor, methods, and an abstract
 *      per-constant method that each constant overrides individually.
 *   2. Built-in enum methods -- values(), valueOf(), name(), ordinal().
 *   3. A record (Range) with a compact constructor performing validation.
 *   4. A static nested class (Node) with no link to an outer instance.
 *   5. A non-static inner class (Counter) showing implicit access to outer state.
 *   6. A local class declared inside a method body.
 *   7. An anonymous class implementing an interface inline.
 *
 * Covers Theory chapter:
 *   10) Java/02) Object-Oriented Programming in Java/Theory/06 Enums Records and Nested Classes.md
 *
 * Compile:  javac 06_EnumsRecordsNestedClassesDemo.java
 * Run:      java EnumsRecordsNestedClassesDemo
 */

import java.util.EnumMap;

// ---------------------------------------------------------------------------
// 1) Enum with fields, constructor, methods, and abstract per-constant method
// ---------------------------------------------------------------------------

enum Operation {
    ADD("+") {
        @Override
        public int apply(int a, int b) { return a + b; }
    },
    SUBTRACT("-") {
        @Override
        public int apply(int a, int b) { return a - b; }
    },
    MULTIPLY("*") {
        @Override
        public int apply(int a, int b) { return a * b; }
    },
    DIVIDE("/") {
        @Override
        public int apply(int a, int b) { return a / b; }
    };

    private final String symbol;                 // per-constant field, set via constructor call

    Operation(String symbol) {                    // enum constructors are implicitly private
        this.symbol = symbol;
    }

    public abstract int apply(int a, int b);       // every constant above MUST supply its own body

    public String symbol() {
        return symbol;
    }
}

// ---------------------------------------------------------------------------
// 3) Record with a compact constructor performing validation
// ---------------------------------------------------------------------------

record Range(int min, int max) {
    Range {                                        // compact constructor -- no repeated parameter list
        if (min > max) {
            throw new IllegalArgumentException(
                    "min (" + min + ") cannot be greater than max (" + max + ")");
        }
        // this.min = min; this.max = max; happen automatically after this block
    }

    int span() {                                    // records can also declare ordinary instance methods
        return max - min;
    }
}

// ---------------------------------------------------------------------------
// Main class
// ---------------------------------------------------------------------------

public class EnumsRecordsNestedClassesDemo {

    // -----------------------------------------------------------------------
    // 4) Static nested class -- no implicit link to an outer instance
    // -----------------------------------------------------------------------
    static class Node {
        final int value;
        Node next;

        Node(int value) {
            this.value = value;
        }
    }

    // -----------------------------------------------------------------------
    // 5) Non-static inner class -- carries an implicit reference to the outer instance
    // -----------------------------------------------------------------------
    private int totalCount = 0;                     // outer instance state, accessed by the inner class below

    class Counter {
        void increment() {
            totalCount++;                            // direct access to the outer instance's private field
            System.out.println("Counter incremented -- EnumsRecordsNestedClassesDemo.this.totalCount = "
                    + EnumsRecordsNestedClassesDemo.this.totalCount);
        }
    }

    // -----------------------------------------------------------------------
    // Helper printing utility
    // -----------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -----------------------------------------------------------------------
    // Demo 1: enum with per-constant abstract method + built-in enum methods
    // -----------------------------------------------------------------------
    private static void demoEnum() {
        printSection("1) Enum -- fields, constructor, per-constant abstract method");

        for (Operation op : Operation.values()) {            // values() -- every constant, declaration order
            System.out.printf("6 %s 3 = %d  (name=%s, ordinal=%d)%n",
                    op.symbol(), op.apply(6, 3), op.name(), op.ordinal());
        }

        Operation parsed = Operation.valueOf("MULTIPLY");     // valueOf() -- parse a String into a constant
        assert parsed.apply(4, 5) == 20;
        System.out.println("valueOf(\"MULTIPLY\").apply(4, 5) = " + parsed.apply(4, 5));

        // EnumMap -- array-backed map keyed by an enum, iterates in declaration order
        EnumMap<Operation, String> descriptions = new EnumMap<>(Operation.class);
        descriptions.put(Operation.ADD, "adds two numbers");
        descriptions.put(Operation.DIVIDE, "divides two numbers");
        System.out.println("EnumMap contents: " + descriptions);

        // Enhanced switch expression over an enum -- exhaustively checked by the compiler
        String category = switch (parsed) {
            case ADD, SUBTRACT -> "additive family";
            case MULTIPLY, DIVIDE -> "multiplicative family";
        };
        System.out.println("MULTIPLY belongs to: " + category);
    }

    // -----------------------------------------------------------------------
    // Demo 2: record with compact constructor validation
    // -----------------------------------------------------------------------
    private static void demoRecord() {
        printSection("2) Record -- compact constructor validation, auto-generated methods");

        Range valid = new Range(2, 10);
        System.out.println("valid   = " + valid + "   span=" + valid.span());

        Range same = new Range(2, 10);
        System.out.println("equals(): " + valid.equals(same) + "   ==: " + (valid == same));
        assert valid.equals(same);          // auto-generated equals() -- compares components, not identity
        assert valid != same;                // still distinct objects

        try {
            Range invalid = new Range(10, 2);    // triggers the compact constructor's validation
            System.out.println("This line should not be reached: " + invalid);
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected exception: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Demo 3: static nested class -- no outer instance needed
    // -----------------------------------------------------------------------
    private static void demoStaticNestedClass() {
        printSection("3) Static nested class -- Node, no outer instance required");

        Node head = new Node(1);                 // instantiated with no EnumsRecordsNestedClassesDemo instance
        head.next = new Node(2);
        head.next.next = new Node(3);

        StringBuilder sb = new StringBuilder();
        for (Node cur = head; cur != null; cur = cur.next) {
            sb.append(cur.value);
            if (cur.next != null) sb.append(" -> ");
        }
        System.out.println("Linked chain: " + sb);
    }

    // -----------------------------------------------------------------------
    // Demo 4: non-static inner class -- implicit outer instance access
    // -----------------------------------------------------------------------
    private static void demoInnerClass() {
        printSection("4) Non-static inner class -- Counter accessing outer state");

        EnumsRecordsNestedClassesDemo outer = new EnumsRecordsNestedClassesDemo();
        EnumsRecordsNestedClassesDemo.Counter counter = outer.new Counter();  // must be created THROUGH an outer instance
        counter.increment();
        counter.increment();
        counter.increment();

        System.out.println("Final outer.totalCount = " + outer.totalCount);
        assert outer.totalCount == 3;
    }

    // -----------------------------------------------------------------------
    // Demo 5: local class -- scoped entirely to this method
    // -----------------------------------------------------------------------
    private static void demoLocalClass() {
        printSection("5) Local class -- Filter, scoped to this method only");

        int threshold = 10;                      // effectively final -- captured by the local class below

        class Filter {                            // local class -- invisible outside demoLocalClass()
            boolean passes(int value) {
                return value > threshold;           // captures the enclosing method's local variable
            }
        }

        Filter filter = new Filter();
        int[] candidates = {3, 15, 8, 22, 10};
        System.out.print("Values passing threshold " + threshold + ": ");
        for (int candidate : candidates) {
            if (filter.passes(candidate)) {
                System.out.print(candidate + " ");
            }
        }
        System.out.println();
    }

    // -----------------------------------------------------------------------
    // Demo 6: anonymous class -- one-off interface implementation, inline
    // -----------------------------------------------------------------------
    interface ClickHandler {
        void onClick(String source);
    }

    private static void demoAnonymousClass() {
        printSection("6) Anonymous class -- inline ClickHandler implementation");

        ClickHandler handler = new ClickHandler() {     // anonymous class implementing ClickHandler
            private int clicks = 0;                       // anonymous classes can even hold their own state

            @Override
            public void onClick(String source) {
                clicks++;
                System.out.println("[" + source + "] click #" + clicks);
            }
        };

        handler.onClick("button-A");
        handler.onClick("button-A");
        handler.onClick("button-B");
    }

    public static void main(String[] args) {
        demoEnum();
        demoRecord();
        demoStaticNestedClass();
        demoInnerClass();
        demoLocalClass();
        demoAnonymousClass();

        System.out.println();
        System.out.println("All Enums/Records/Nested Classes demos completed.");
    }
}
