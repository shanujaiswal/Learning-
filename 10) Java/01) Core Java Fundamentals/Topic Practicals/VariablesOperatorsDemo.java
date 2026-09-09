/*
 * VariablesOperatorsDemo.java
 *
 * Demonstrates:
 *     1. Declaring all 8 primitive types with sample values, and printing them
 *     2. MIN_VALUE / MAX_VALUE of the corresponding wrapper classes
 *     3. Widening (implicit) casting in action
 *     4. Narrowing (explicit) casting in action, including the resulting data loss
 *     5. Integer overflow -- int wrapping silently to a negative value
 *     6. Operator precedence with a tricky mixed expression, explained
 *     7. Float precision loss -- 0.1 + 0.2 != 0.3
 *     8. Integer division vs floating-point division
 *     9. Bitwise operators (&, |, ^, ~, <<, >>, >>>)
 *    10. Ternary operator
 *    11. Pre-increment vs post-increment / decrement, with output explained
 *
 * Covers Theory chapter:
 *     10) Java/01) Core Java Fundamentals/Theory/02 Variables Data Types and Operators.md
 *
 * Compile: javac 02_variables_operators_demo.java
 * Run:     java VariablesOperatorsDemo
 */
public class VariablesOperatorsDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {
        demoPrimitiveTypes();
        demoWrapperMinMax();
        demoWideningCasting();
        demoNarrowingCasting();
        demoIntegerOverflow();
        demoOperatorPrecedence();
        demoFloatPrecisionLoss();
        demoDivisionTypes();
        demoBitwiseOperators();
        demoTernaryOperator();
        demoIncrementDecrement();
        System.out.println("\nAll Variables & Operators demos completed.");
    }

    // -------------------------------------------------------------------
    // 1) All 8 primitive types
    // -------------------------------------------------------------------
    private static void demoPrimitiveTypes() {
        printSection("1) The 8 primitive types");

        byte byteVal = 100;                    // 8-bit,  -128..127
        short shortVal = 30_000;               // 16-bit, -32768..32767
        int intVal = 2_000_000_000;             // 32-bit
        long longVal = 9_000_000_000L;          // 64-bit -- needs L suffix, exceeds int range
        float floatVal = 3.14f;                 // 32-bit -- needs f suffix
        double doubleVal = 3.14159265358979;    // 64-bit -- default floating type
        char charVal = 'J';                      // 16-bit, unsigned UTF-16 code unit
        boolean boolVal = true;                  // true/false

        System.out.println("byte    = " + byteVal);      // 100
        System.out.println("short   = " + shortVal);     // 30000
        System.out.println("int     = " + intVal);       // 2000000000
        System.out.println("long    = " + longVal);      // 9000000000
        System.out.println("float   = " + floatVal);     // 3.14
        System.out.println("double  = " + doubleVal);    // 3.14159265358979
        System.out.println("char    = " + charVal);      // J
        System.out.println("boolean = " + boolVal);       // true
    }

    // -------------------------------------------------------------------
    // 2) Wrapper class MIN_VALUE / MAX_VALUE
    // -------------------------------------------------------------------
    private static void demoWrapperMinMax() {
        printSection("2) Wrapper class MIN_VALUE / MAX_VALUE");

        System.out.println("Byte:    " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE);       // -128 to 127
        System.out.println("Short:   " + Short.MIN_VALUE + " to " + Short.MAX_VALUE);      // -32768 to 32767
        System.out.println("Integer: " + Integer.MIN_VALUE + " to " + Integer.MAX_VALUE);  // -2147483648 to 2147483647
        System.out.println("Long:    " + Long.MIN_VALUE + " to " + Long.MAX_VALUE);        // -9223372036854775808 to 9223372036854775807
        System.out.println("Float:   " + Float.MIN_VALUE + " to " + Float.MAX_VALUE);      // ~1.4E-45 to ~3.4E38
        System.out.println("Double:  " + Double.MIN_VALUE + " to " + Double.MAX_VALUE);    // ~4.9E-324 to ~1.8E308
        System.out.println("Character max value as int: " + (int) Character.MAX_VALUE);     // 65535
    }

    // -------------------------------------------------------------------
    // 3) Widening (implicit) casting
    // -------------------------------------------------------------------
    private static void demoWideningCasting() {
        printSection("3) Widening (implicit) casting -- byte -> int -> long -> float -> double");

        byte b = 42;
        int fromByte = b;              // byte -> int, automatic, no data loss
        long fromInt = fromByte;        // int -> long, automatic
        float fromLong = fromInt;       // long -> float, automatic (may lose precision on huge values, not here)
        double fromFloat = fromLong;    // float -> double, automatic

        System.out.println("byte b        = " + b);          // 42
        System.out.println("int fromByte  = " + fromByte);   // 42
        System.out.println("long fromInt  = " + fromInt);    // 42
        System.out.println("float fromLong= " + fromLong);   // 42.0
        System.out.println("double fromF  = " + fromFloat);  // 42.0

        char c = 'A';
        int charAsInt = c;               // char -> int widening, uses the underlying code point
        System.out.println("char 'A' widened to int = " + charAsInt);  // 65
    }

    // -------------------------------------------------------------------
    // 4) Narrowing (explicit) casting
    // -------------------------------------------------------------------
    private static void demoNarrowingCasting() {
        printSection("4) Narrowing (explicit) casting -- data loss in action");

        double pi = 9.78;
        int truncated = (int) pi;                 // fractional part discarded, NOT rounded
        System.out.println("(int) 9.78 = " + truncated);   // 9

        long bigL = 130L;
        byte narrowed = (byte) bigL;                // 130 is outside byte range (-128..127) -- wraps
        System.out.println("(byte) 130L = " + narrowed);   // -126 (wraps around via two's complement)

        int intCode = 66;
        char asChar = (char) intCode;                // explicit cast needed: int -> char is narrowing
        System.out.println("(char) 66 = " + asChar);        // B
    }

    // -------------------------------------------------------------------
    // 5) Integer overflow
    // -------------------------------------------------------------------
    private static void demoIntegerOverflow() {
        printSection("5) Integer overflow -- silent wraparound, no exception");

        int maxInt = Integer.MAX_VALUE;
        System.out.println("Integer.MAX_VALUE      = " + maxInt);        // 2147483647
        System.out.println("Integer.MAX_VALUE + 1  = " + (maxInt + 1));  // -2147483648 -- wraps to most negative int

        // Correct way to avoid it: cast an OPERAND to long BEFORE the addition happens
        long safeSum = (long) maxInt + 1;
        System.out.println("(long) MAX_VALUE + 1   = " + safeSum);        // 2147483648 -- correct, no overflow
    }

    // -------------------------------------------------------------------
    // 6) Operator precedence
    // -------------------------------------------------------------------
    private static void demoOperatorPrecedence() {
        printSection("6) Operator precedence -- a tricky mixed expression");

        int a = 5, b = 2, c = 3;
        // Evaluation order: * before +, then relational, then &&
        // Step by step: b * c = 6 ; a + 6 = 11 ; 11 > 10 -> true ; true && (c == 3) -> true && true -> true
        boolean result = a + b * c > 10 && c == 3;
        System.out.println("a + b * c > 10 && c == 3  =  " + result);   // true

        // Another example mixing unary, arithmetic, and bitwise -- read via the precedence table
        // -a is unary first (-5), then * b (-10), then + (c << 1 = 6) -> -10 + 6 = -4
        int tricky = -a * b + (c << 1);
        System.out.println("-a * b + (c << 1)         =  " + tricky);   // -4
    }

    // -------------------------------------------------------------------
    // 7) Float precision loss
    // -------------------------------------------------------------------
    private static void demoFloatPrecisionLoss() {
        printSection("7) Float precision loss -- 0.1 + 0.2 != 0.3");

        double sum = 0.1 + 0.2;
        System.out.println("0.1 + 0.2         = " + sum);                    // 0.30000000000000004
        System.out.println("0.1 + 0.2 == 0.3  = " + (sum == 0.3));           // false

        // The correct way to compare floating-point values: tolerance-based comparison
        double epsilon = 1e-9;
        boolean approximatelyEqual = Math.abs(sum - 0.3) < epsilon;
        System.out.println("Approximately equal (tolerance check) = " + approximatelyEqual);  // true
    }

    // -------------------------------------------------------------------
    // 8) Integer division vs floating-point division
    // -------------------------------------------------------------------
    private static void demoDivisionTypes() {
        printSection("8) Integer division vs floating-point division");

        int x = 7, y = 2;
        double wrongWay = x / y;               // int / int computed FIRST (truncates), THEN widened to double
        double rightWay = (double) x / y;       // one operand cast to double BEFORE dividing -- true division

        System.out.println("x / y assigned to double, no cast   = " + wrongWay);   // 3.0, NOT 3.5
        System.out.println("(double) x / y                       = " + rightWay);  // 3.5

        System.out.println("7 % 2 (remainder)                    = " + (x % y));   // 1
    }

    // -------------------------------------------------------------------
    // 9) Bitwise operators
    // -------------------------------------------------------------------
    private static void demoBitwiseOperators() {
        printSection("9) Bitwise operators");

        int six = 6;    // 0110
        int three = 3;  // 0011

        System.out.println("6 & 3  = " + (six & three));   // 2   (0110 & 0011 = 0010)
        System.out.println("6 | 3  = " + (six | three));   // 7   (0110 | 0011 = 0111)
        System.out.println("6 ^ 3  = " + (six ^ three));   // 5   (0110 ^ 0011 = 0101)
        System.out.println("~6     = " + (~six));           // -7  (two's complement: ~x == -x - 1)
        System.out.println("1 << 4 = " + (1 << 4));         // 16  (shift left = multiply by 2^4)
        System.out.println("-16 >> 2  = " + (-16 >> 2));    // -4  (arithmetic shift, sign-preserving)
        System.out.println("-16 >>> 2 = " + (-16 >>> 2));   // large positive number -- fills with 0, ignores sign
    }

    // -------------------------------------------------------------------
    // 10) Ternary operator
    // -------------------------------------------------------------------
    private static void demoTernaryOperator() {
        printSection("10) Ternary operator");

        int scoreA = 85, scoreB = 92;
        int higherScore = (scoreA > scoreB) ? scoreA : scoreB;
        System.out.println("Higher of 85 and 92 = " + higherScore);   // 92

        int n = 7;
        String parity = (n % 2 == 0) ? "even" : "odd";
        System.out.println("7 is " + parity);                          // 7 is odd
    }

    // -------------------------------------------------------------------
    // 11) Pre-increment vs post-increment / decrement
    // -------------------------------------------------------------------
    private static void demoIncrementDecrement() {
        printSection("11) Pre vs post increment/decrement");

        int i = 5;
        int preResult = ++i;   // i becomes 6 FIRST, then preResult is assigned 6
        System.out.println("i=5, ++i -> i=" + i + ", preResult=" + preResult);   // i=6, preResult=6

        int j = 5;
        int postResult = j++;  // postResult gets the CURRENT value 5 first, THEN j becomes 6
        System.out.println("j=5, j++ -> j=" + j + ", postResult=" + postResult); // j=6, postResult=5

        int k = 5;
        int preDec = --k;      // k becomes 4 first, preDec = 4
        System.out.println("k=5, --k -> k=" + k + ", preDec=" + preDec);          // k=4, preDec=4

        int m = 5;
        int postDec = m--;     // postDec gets 5 first, THEN m becomes 4
        System.out.println("m=5, m-- -> m=" + m + ", postDec=" + postDec);        // m=4, postDec=5

        // Combined expression showing the difference matters for correctness, not just style
        int n = 1;
        int combined = n++ + ++n;   // n++ uses 1 (n becomes 2), then ++n makes n=3 and uses 3 -> 1 + 3 = 4
        System.out.println("n=1, n++ + ++n = " + combined + " (final n=" + n + ")"); // 4 (final n=3)
    }
}
