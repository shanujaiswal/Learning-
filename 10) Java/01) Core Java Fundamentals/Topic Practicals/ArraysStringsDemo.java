/*
 * ArraysStringsDemo.java
 *
 * Demonstrates:
 *     1. 1D array declaration, initialization, and access
 *     2. 2D arrays and jagged (ragged) arrays
 *     3. Arrays.sort / binarySearch / fill / toString
 *     4. Catching ArrayIndexOutOfBoundsException
 *     5. String immutability -- a reference not changing in place
 *     6. == vs .equals() -- string literals vs `new String()` (string pool behavior)
 *     7. Common String methods with printed output
 *     8. StringBuilder -- append / insert / reverse / toString
 *     9. The O(n^2) naive String concatenation trap vs O(n) StringBuilder, timed
 *
 * Covers Theory chapter:
 *     10) Java/01) Core Java Fundamentals/Theory/04 Arrays and Strings.md
 *
 * Compile: javac 04_arrays_strings_demo.java
 * Run:     java ArraysStringsDemo
 */

import java.util.Arrays;

public class ArraysStringsDemo {

    public static void main(String[] args) {
        demo1DArrays();
        demo2DAndJaggedArrays();
        demoArraysUtilityClass();
        demoArrayIndexOutOfBounds();
        demoStringImmutability();
        demoStringPoolEqualityVsEquals();
        demoCommonStringMethods();
        demoStringBuilder();
        demoConcatTrapVsStringBuilder();

        System.out.println("\nAll Arrays & Strings demos completed.");
    }

    // -------------------------------------------------------------------
    // Helper -- prints a section header, mirroring the Python reference style
    // -------------------------------------------------------------------
    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -------------------------------------------------------------------
    // 1) 1D array declaration, initialization, access
    // -------------------------------------------------------------------
    private static void demo1DArrays() {
        printSection("1) 1D array declaration, initialization, access");

        int[] scores = new int[5];          // allocated, all default to 0
        System.out.println("Freshly allocated (defaults): " + Arrays.toString(scores));
        // Expected: [0, 0, 0, 0, 0]

        int[] primes = {2, 3, 5, 7, 11};    // initializer list, length inferred
        System.out.println("Initializer list:             " + Arrays.toString(primes));
        // Expected: [2, 3, 5, 7, 11]

        int[] evens = new int[]{2, 4, 6, 8}; // explicit `new` form of initializer list
        System.out.println("Explicit new + list:          " + Arrays.toString(evens));
        // Expected: [2, 4, 6, 8]

        System.out.println("primes[0] = " + primes[0] + ", primes.length = " + primes.length);
        // Expected: primes[0] = 2, primes.length = 5

        // Arrays are objects -- a second reference points to the SAME heap array
        int[] alias = primes;
        alias[0] = 999;
        System.out.println("After mutating via alias, primes[0] = " + primes[0]);
        // Expected: 999 -- alias and primes reference the same array object
    }

    // -------------------------------------------------------------------
    // 2) 2D arrays and jagged arrays
    // -------------------------------------------------------------------
    private static void demo2DAndJaggedArrays() {
        printSection("2) 2D arrays and jagged arrays");

        int[][] grid = new int[3][4];   // 3 rows x 4 cols, all default to 0
        int counter = 1;
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                grid[row][col] = counter++;
            }
        }
        System.out.println("Rectangular 2D array:");
        for (int[] row : grid) {
            System.out.println("  " + Arrays.toString(row));
        }
        // Expected:
        //   [1, 2, 3, 4]
        //   [5, 6, 7, 8]
        //   [9, 10, 11, 12]

        // Jagged array -- each row is its own independently-sized array
        int[][] jagged = new int[3][];
        jagged[0] = new int[]{1};
        jagged[1] = new int[]{1, 2, 3};
        jagged[2] = new int[]{1, 2, 3, 4, 5};

        System.out.println("Jagged array (rows of different lengths):");
        for (int[] row : jagged) {
            System.out.println("  " + Arrays.toString(row) + "  (length " + row.length + ")");
        }
        // Expected:
        //   [1]  (length 1)
        //   [1, 2, 3]  (length 3)
        //   [1, 2, 3, 4, 5]  (length 5)
    }

    // -------------------------------------------------------------------
    // 3) Arrays.sort / binarySearch / fill / toString
    // -------------------------------------------------------------------
    private static void demoArraysUtilityClass() {
        printSection("3) Arrays.sort / binarySearch / fill / toString");

        int[] nums = {5, 3, 1, 4, 2};
        System.out.println("Before sort: " + Arrays.toString(nums));
        // Expected: [5, 3, 1, 4, 2]

        Arrays.sort(nums);
        System.out.println("After sort:  " + Arrays.toString(nums));
        // Expected: [1, 2, 3, 4, 5]

        int idx = Arrays.binarySearch(nums, 4);
        System.out.println("binarySearch for 4 -> index " + idx);
        // Expected: index 3

        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3};
        System.out.println("a == b (reference equality):   " + (a == b));
        // Expected: false -- different array objects
        System.out.println("Arrays.equals(a, b) (content):  " + Arrays.equals(a, b));
        // Expected: true -- same contents

        int[] filled = new int[5];
        Arrays.fill(filled, 7);
        System.out.println("After Arrays.fill(filled, 7): " + Arrays.toString(filled));
        // Expected: [7, 7, 7, 7, 7]

        int[] extended = Arrays.copyOf(a, 5);
        System.out.println("Arrays.copyOf(a, 5):           " + Arrays.toString(extended));
        // Expected: [1, 2, 3, 0, 0]
    }

    // -------------------------------------------------------------------
    // 4) Catching ArrayIndexOutOfBoundsException
    // -------------------------------------------------------------------
    private static void demoArrayIndexOutOfBounds() {
        printSection("4) Catching ArrayIndexOutOfBoundsException");

        int[] arr = {10, 20, 30};
        try {
            System.out.println(arr[3]); // valid indices are 0..2, this is out of bounds
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Caught expected exception: " + e.getMessage());
            // Expected: something like "Index 3 out of bounds for length 3"
        }

        try {
            System.out.println(arr[-1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Caught expected exception: " + e.getMessage());
            // Expected: something like "Index -1 out of bounds for length 3"
        }
    }

    // -------------------------------------------------------------------
    // 5) String immutability -- reference does not change in place
    // -------------------------------------------------------------------
    private static void demoStringImmutability() {
        printSection("5) String immutability -- reference does not change in place");

        String s = "hello";
        s.toUpperCase();                 // return value discarded -- s itself is untouched
        System.out.println("After calling s.toUpperCase() and discarding result: " + s);
        // Expected: hello -- NOT HELLO, because Strings never mutate in place

        String upper = s.toUpperCase();  // must capture the returned NEW String object
        System.out.println("Captured return value:                              " + upper);
        // Expected: HELLO
        System.out.println("Original s is still:                                " + s);
        // Expected: hello -- original object was never touched
    }

    // -------------------------------------------------------------------
    // 6) == vs .equals() -- string literals vs new String() (string pool)
    // -------------------------------------------------------------------
    private static void demoStringPoolEqualityVsEquals() {
        printSection("6) == vs .equals() -- string pool behavior");

        String a = "hello";
        String b = "hello";
        String c = new String("hello");

        System.out.println("a == b (both literals, same pooled object): " + (a == b));
        // Expected: true

        System.out.println("a == c (c built with `new`, separate object): " + (a == c));
        // Expected: false

        System.out.println("a.equals(c) (content comparison):            " + a.equals(c));
        // Expected: true -- .equals() always compares content, safe for Strings

        String d = c.intern();
        System.out.println("a == d (d interned into the pool):           " + (a == d));
        // Expected: true -- intern() forces/fetches the pooled instance
    }

    // -------------------------------------------------------------------
    // 7) Common String methods
    // -------------------------------------------------------------------
    private static void demoCommonStringMethods() {
        printSection("7) Common String methods");

        String text = "  Hello, World!  ";

        System.out.println("length():            " + "hello".length());
        // Expected: 5
        System.out.println("charAt(1):           " + "hello".charAt(1));
        // Expected: e
        System.out.println("substring(1, 4):     " + "hello".substring(1, 4));
        // Expected: ell
        System.out.println("indexOf(\"l\"):        " + "hello".indexOf("l"));
        // Expected: 2
        System.out.println("split(\",\"):          " + Arrays.toString("a,b,c".split(",")));
        // Expected: [a, b, c]
        System.out.println("trim():              [" + text.trim() + "]");
        // Expected: [Hello, World!]
        System.out.println("strip():             [" + text.strip() + "]");
        // Expected: [Hello, World!]
        System.out.println("replace('l','L'):    " + "hello".replace('l', 'L'));
        // Expected: heLLo
        System.out.println("toUpperCase():       " + "hi".toUpperCase());
        // Expected: HI
        System.out.println("toLowerCase():       " + "HI".toLowerCase());
        // Expected: hi
        System.out.println("equalsIgnoreCase():  " + "Hi".equalsIgnoreCase("hi"));
        // Expected: true
        System.out.println("concat():            " + "foo".concat("bar"));
        // Expected: foobar
        System.out.println("contains(\"ell\"):     " + "hello".contains("ell"));
        // Expected: true
        System.out.println("startsWith(\"he\"):    " + "hello".startsWith("he"));
        // Expected: true
        System.out.println("endsWith(\"lo\"):      " + "hello".endsWith("lo"));
        // Expected: true
    }

    // -------------------------------------------------------------------
    // 8) StringBuilder -- append / insert / reverse / toString
    // -------------------------------------------------------------------
    private static void demoStringBuilder() {
        printSection("8) StringBuilder -- append / insert / reverse / toString");

        StringBuilder sb = new StringBuilder();
        sb.append("Hello");
        sb.append(", ");
        sb.append("World");
        System.out.println("After appends:  " + sb);
        // Expected: Hello, World

        sb.insert(5, " there");
        System.out.println("After insert:   " + sb);
        // Expected: Hello there, World

        sb.reverse();
        System.out.println("After reverse:  " + sb);
        // Expected: dlroW ,ereht olleH

        sb.reverse(); // flip back for a clean, readable final string
        String finalStr = sb.toString(); // converts the mutable buffer to an immutable String
        System.out.println("Final String:   " + finalStr);
        // Expected: Hello there, World
    }

    // -------------------------------------------------------------------
    // 9) O(n^2) naive concatenation trap vs O(n) StringBuilder, timed
    // -------------------------------------------------------------------
    private static void demoConcatTrapVsStringBuilder() {
        printSection("9) O(n^2) String += trap vs O(n) StringBuilder, timed");
        System.out.println("Each += on a String allocates a brand new String and copies everything");
        System.out.println("accumulated so far -- doing this n times is O(n^2) total, exactly like the");
        System.out.println("naive Python string concat trap in the DSA Big-O file. StringBuilder keeps");
        System.out.println("one resizable internal buffer and appends in amortized O(1), so n appends");
        System.out.println("cost O(n) total.\n");

        int n = 20000;

        long startNaive = System.nanoTime();
        String naive = buildStringNaive(n);
        long naiveMs = (System.nanoTime() - startNaive) / 1_000_000;

        long startBuilder = System.nanoTime();
        String built = buildStringWithBuilder(n);
        long builderMs = (System.nanoTime() - startBuilder) / 1_000_000;

        System.out.printf("Naive += loop (n=%d):        %6d ms  (O(n^2) -- re-copies the growing string every iteration)%n", n, naiveMs);
        System.out.printf("StringBuilder.append (n=%d): %6d ms  (O(n)   -- amortized O(1) per append)%n", n, builderMs);
        // Expected: the StringBuilder version is dramatically faster than the naive loop,
        // and the gap widens sharply if n is increased further (quadratic vs linear growth).

        System.out.println("Both produce the identical result: " + naive.equals(built));
        // Expected: true -- only the COST of getting there differs, not the outcome
    }

    private static String buildStringNaive(int n) {   // O(n^2) -- BAD in a loop
        String result = "";
        for (int i = 0; i < n; i++) {
            result += i;   // allocates + copies the WHOLE growing string, every single iteration
        }
        return result;
    }

    private static String buildStringWithBuilder(int n) {  // O(n) -- GOOD
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(i);   // amortized O(1) -- same doubling-buffer trick as ArrayList underneath
        }
        return sb.toString();
    }
}
