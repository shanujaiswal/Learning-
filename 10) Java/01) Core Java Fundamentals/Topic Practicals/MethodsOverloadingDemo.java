/*
 * MethodsOverloadingDemo.java
 *
 * Demonstrates:
 *     1. Method declaration anatomy + overloading by parameter count/type/order
 *     2. Overload resolution order (exact match -> widening -> autoboxing -> varargs)
 *     3. Varargs (sum(int...)) called with 0, 1, and many arguments
 *     4. Pass-by-value semantics -- primitive reassignment vs object mutation vs
 *        object reference reassignment (the centerpiece of this file)
 *     5. Recursion -- factorial and fibonacci, with a printed call trace
 *
 * Covers Theory chapter:
 *     10) Java/01) Core Java Fundamentals/Theory/05 Methods and Method Overloading.md
 *
 * Compile: javac MethodsOverloadingDemo.java
 * Run:     java MethodsOverloadingDemo
 */
public class MethodsOverloadingDemo {

    // -------------------------------------------------------------------
    // Helper -- section header printer
    // -------------------------------------------------------------------
    static void printSection(String title) {
        System.out.println();
        System.out.println("======================================================================");
        System.out.println(title);
        System.out.println("======================================================================");
    }

    // -------------------------------------------------------------------
    // 1) Method overloading -- same name "print", different signatures
    //    (differ by parameter TYPE and by parameter COUNT)
    // -------------------------------------------------------------------
    static void print(String s) {
        System.out.println("print(String):  \"" + s + "\"");
    }

    static void print(int n) {
        System.out.println("print(int):     " + n);
    }

    static void print(double d) {
        System.out.println("print(double):  " + d);
    }

    static void print(int a, int b) {
        System.out.println("print(int,int): " + a + ", " + b);
    }

    // Overload distinguished by ORDER of parameter types, not just count/type
    static void describe(String name, int age) {
        System.out.println("describe(String,int): name=" + name + " age=" + age);
    }

    static void describe(int age, String name) {
        System.out.println("describe(int,String): age=" + age + " name=" + name);
    }

    static void demoOverloadingBasics() {
        printSection("1) Method Overloading -- resolved by parameter list, not return type");

        print("hello");     // compiler picks print(String) -- exact match on argument type
        print(42);           // compiler picks print(int)    -- exact match
        print(3.14);         // compiler picks print(double)  -- exact match
        print(1, 2);          // compiler picks print(int,int) -- differs by parameter COUNT

        describe("Vanisha", 5);   // matches describe(String,int) -- order matters
        describe(5, "Vanisha");    // matches describe(int,String) -- a DIFFERENT signature,
                                     // even though it's the "same two values" in a different order
    }

    // -------------------------------------------------------------------
    // 2) Overload resolution order: exact match -> widening -> autoboxing -> varargs
    // -------------------------------------------------------------------
    static void choose(long x) {
        System.out.println("choose(long) called -- argument WIDENED from int to long");
    }

    static void choose(Integer x) {
        System.out.println("choose(Integer) called -- argument AUTOBOXED from int to Integer");
    }

    static void choose(int... x) {
        System.out.println("choose(int...) called -- VARARGS, last resort match");
    }

    static void demoOverloadResolutionOrder() {
        printSection("2) Overload Resolution Order -- widening beats autoboxing beats varargs");

        // Passing a plain "int" literal -- all three overloads above COULD apply,
        // but Java tries them in a strict order and stops at the first phase that matches:
        //   Phase 1 (exact match)      -- no choose(int) exists, so this phase finds nothing
        //   Phase 2 (widening)          -- int -> long widens with NO boxing -> choose(long) MATCHES
        //   Phase 3 (autoboxing)        -- never reached, Phase 2 already succeeded
        //   Phase 4 (varargs)           -- never reached
        choose(5);   // Expected output: "choose(long) called..."

        // Remove choose(long) mentally: with only choose(Integer) and choose(int...) left,
        // an int argument would autobox to Integer (Phase 3) before falling back to varargs (Phase 4).
        // We demonstrate that narrower case with a dedicated pair below.
        pickNarrower(7);   // Expected output: "pickNarrower(Integer) -- autoboxed"
    }

    static void pickNarrower(Integer x) {
        System.out.println("pickNarrower(Integer) -- autoboxed");
    }

    static void pickNarrower(int... x) {
        System.out.println("pickNarrower(int...) -- varargs fallback");
    }

    // -------------------------------------------------------------------
    // 3) Varargs -- sum(int...) called with 0, 1, and many arguments
    // -------------------------------------------------------------------
    static int sum(int... numbers) {
        // Internally "numbers" is just an int[] -- varargs desugars to an array
        int total = 0;
        for (int n : numbers) {
            total += n;
        }
        return total;
    }

    static void demoVarargs() {
        printSection("3) Varargs -- sum(int... numbers)");

        System.out.println("sum()          = " + sum());              // Expected: 0  (empty array)
        System.out.println("sum(5)         = " + sum(5));               // Expected: 5
        System.out.println("sum(1,2,3,4,5) = " + sum(1, 2, 3, 4, 5));    // Expected: 15

        int[] preBuiltArray = {10, 20, 30};
        System.out.println("sum(int[])     = " + sum(preBuiltArray));    // Expected: 60
        // ^-- an actual array can be passed directly too, proving varargs IS an array under the hood
    }

    // -------------------------------------------------------------------
    // 4) Pass-by-value -- THE CENTERPIECE DEMO
    // -------------------------------------------------------------------

    // 4a) Primitive: reassignment inside the method is invisible to the caller
    static void reassignPrimitive(int x) {
        System.out.println("   inside reassignPrimitive: x was " + x + ", reassigning to 999");
        x = 999;   // only the LOCAL COPY changes
        System.out.println("   inside reassignPrimitive: x is now " + x);
    }

    // 4b) Object/array: mutating through the reference IS visible to the caller
    static void mutateThroughReference(int[] arr) {
        System.out.println("   inside mutateThroughReference: arr[0] was " + arr[0] + ", setting to 999");
        arr[0] = 999;   // follows the SHARED reference -- changes the one array both sides see
    }

    // 4c) Object/array: reassigning the parameter itself is NOT visible to the caller
    static void reassignReference(int[] arr) {
        System.out.println("   inside reassignReference: repointing local 'arr' to a brand new array");
        arr = new int[] { -1, -1, -1 };   // only the LOCAL reference variable is repointed;
        System.out.println("   inside reassignReference: arr[0] is now " + arr[0] + " (local copy only)");
    }

    static void demoPassByValue() {
        printSection("4) Pass-by-Value -- primitive reassignment vs object mutation vs reference reassignment");

        // ---- 4a) Primitive ----
        System.out.println("-- 4a) Primitive int --");
        int num = 5;
        System.out.println("BEFORE call: num = " + num);          // Expected: 5
        reassignPrimitive(num);
        System.out.println("AFTER  call: num = " + num);           // Expected: 5 (UNCHANGED)
        System.out.println("Explanation: only a COPY of the value 5 was passed in; reassigning");
        System.out.println("the parameter inside the method never touches the caller's variable.\n");

        // ---- 4b) Mutate through reference ----
        System.out.println("-- 4b) Object/array MUTATION through a shared reference --");
        int[] data = { 1, 2, 3 };
        System.out.println("BEFORE call: data[0] = " + data[0]);   // Expected: 1
        mutateThroughReference(data);
        System.out.println("AFTER  call: data[0] = " + data[0]);    // Expected: 999 (CHANGED!)
        System.out.println("Explanation: 'data' and the method's local 'arr' both COPY the same");
        System.out.println("reference (the same heap address) -- mutating the object through either");
        System.out.println("variable is visible through the other, because it's the SAME object.\n");

        // ---- 4c) Reassign the reference itself ----
        System.out.println("-- 4c) Reassigning the REFERENCE parameter itself --");
        int[] original = { 7, 8, 9 };
        System.out.println("BEFORE call: original[0] = " + original[0]);  // Expected: 7
        reassignReference(original);
        System.out.println("AFTER  call: original[0] = " + original[0]);   // Expected: 7 (UNCHANGED)
        System.out.println("Explanation: reassignReference() repointed its OWN local copy of the");
        System.out.println("reference to a brand new array -- the caller's 'original' variable still");
        System.out.println("points at the original heap object, completely untouched. This proves");
        System.out.println("Java passes the REFERENCE BY VALUE, not the object by reference.");
    }

    // -------------------------------------------------------------------
    // 5) Recursion -- factorial and fibonacci with a printed call trace
    // -------------------------------------------------------------------

    static long factorial(int n) {
        return factorial(n, 0);   // kick off the traced version at depth 0
    }

    // Overloaded helper carrying a "depth" parameter purely to indent the trace --
    // also doubles as another example of overloading (factorial(int) vs factorial(int,int))
    static long factorial(int n, int depth) {
        String indent = "  ".repeat(depth);
        System.out.println(indent + "factorial(" + n + ") called");

        if (n <= 1) {                                 // BASE CASE
            System.out.println(indent + "-> base case reached, returning 1");
            return 1;
        }

        long result = n * factorial(n - 1, depth + 1);  // RECURSIVE CASE
        System.out.println(indent + "-> factorial(" + n + ") returning " + result);
        return result;
    }

    static long fibonacci(int n) {
        if (n <= 1) {          // BASE CASE -- covers both n=0 and n=1
            return n;
        }
        return fibonacci(n - 1) + fibonacci(n - 2);   // RECURSIVE CASE -- two smaller calls
    }

    static void demoRecursion() {
        printSection("5) Recursion -- factorial (traced) and fibonacci");

        System.out.println("-- factorial(5) with call trace --");
        long fact5 = factorial(5);
        System.out.println("Result: factorial(5) = " + fact5);   // Expected: 120
        System.out.println("Notice the call stack builds up 5 nested frames before the base case,");
        System.out.println("then unwinds, multiplying as each frame returns -- O(n) stack space.\n");

        System.out.println("-- fibonacci(0..10) --");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= 10; i++) {
            sb.append(fibonacci(i));
            if (i < 10) sb.append(", ");
        }
        System.out.println("fibonacci(0..10) = " + sb);
        // Expected: 0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55
        System.out.println("Naive recursive fibonacci recomputes overlapping subproblems --");
        System.out.println("O(2^n) time, since fibonacci(n-2) is recomputed by both branches above it.");
    }

    // -------------------------------------------------------------------
    // main -- runs every demo in order
    // -------------------------------------------------------------------
    public static void main(String[] args) {
        demoOverloadingBasics();
        demoOverloadResolutionOrder();
        demoVarargs();
        demoPassByValue();
        demoRecursion();

        System.out.println();
        System.out.println("All Methods & Overloading demos completed.");
    }
}
