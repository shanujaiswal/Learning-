# Why Control Flow Matters

--> Every program beyond the trivial needs to make decisions and repeat work -- control flow statements are the mechanism for both: `if`/`switch` decide WHICH code runs, and `for`/`while`/`do-while` decide HOW MANY TIMES code runs.
--> Java's control flow looks similar to C/C++ on the surface, but has evolved significantly in recent versions -- modern switch expressions (Java 14) and pattern matching for switch (Java 21) make it noticeably safer and more expressive than the "classic" switch most tutorials still teach first.

# if / else if / else -- Branching on a Condition

--> The condition in an `if` MUST be a `boolean` expression -- unlike C, Java does not allow `if (1)` or `if (someInt)`, which eliminates an entire class of "assignment instead of comparison" bugs (`if (x = 5)` is a compile error in Java, not a silent bug).

```java
int score = 72;

if (score >= 90) {
    System.out.println("Grade: A");
} else if (score >= 80) {
    System.out.println("Grade: B");
} else if (score >= 70) {
    System.out.println("Grade: C");
} else {
    System.out.println("Grade: F");
}
```

--> **Evaluation order matters** -- conditions are checked top to bottom, and only the FIRST matching branch runs -- a score of 95 matches `>= 90` and never even evaluates the later conditions, so ordering from most-specific to least-specific (or highest threshold to lowest, as above) is essential to avoid a wrong branch shadowing a right one.
--> **Braces are optional for single statements** but omitting them is a common source of bugs when someone later adds a second line expecting it to be part of the `if` -- this codebase (and most style guides) always use braces even for one-liners.

## Nesting if Statements

--> Nested `if`s check a condition WITHIN an already-true branch -- useful when the second condition only makes sense given the first.

```java
int age = 25;
boolean hasLicense = true;

if (age >= 18) {
    if (hasLicense) {
        System.out.println("Can drive.");
    } else {
        System.out.println("Old enough, but needs a license.");
    }
} else {
    System.out.println("Too young to drive.");
}
```

--> Deep nesting (3+ levels) hurts readability fast -- combining conditions with `&&` (as in `if (age >= 18 && hasLicense)`) or using early `return`/"guard clause" style is usually clearer than pyramids of nested `if`s.

# The Classic switch Statement

--> `switch` picks one branch out of many based on the value of a single expression -- historically limited to `byte`, `short`, `char`, `int` (and their wrapper types), `String` (since Java 7), and `enum` values.

```java
int day = 3;
String name;

switch (day) {
    case 1:
        name = "Monday";
        break;
    case 2:
        name = "Tuesday";
        break;
    case 3:
        name = "Wednesday";
        break;
    default:
        name = "Unknown";
}
System.out.println(name);      // Wednesday
```

## Fall-Through -- The Classic switch's Most Notorious Gotcha

--> Without a `break`, execution FALLS THROUGH into the next `case` regardless of whether its label matches -- this is a deliberate C-inherited design (it enables intentional multi-case grouping, shown below), but it is also the single most common switch bug in Java code.

```java
// BUGGY -- missing break statements
int day = 2;
switch (day) {
    case 1:
        System.out.println("Monday");
    case 2:
        System.out.println("Tuesday");
    case 3:
        System.out.println("Wednesday");
    default:
        System.out.println("Some other day");
}
// Output for day = 2:
//   Tuesday
//   Wednesday
//   Some other day
// --> Execution "fell through" case 2, case 3, AND default, because none of
//     them had a break to stop it -- only "Tuesday" was actually intended.
```

```java
// CORRECTED -- explicit break on every case
int day = 2;
switch (day) {
    case 1:
        System.out.println("Monday");
        break;
    case 2:
        System.out.println("Tuesday");
        break;
    case 3:
        System.out.println("Wednesday");
        break;
    default:
        System.out.println("Some other day");
        break;      // technically optional on the last branch, but good practice
}
// Output for day = 2:  Tuesday
```

--> **Intentional fall-through is a real, useful pattern** -- grouping several labels to share one body relies on it:

```java
int month = 4;      // April
int daysInMonth;
switch (month) {
    case 1: case 3: case 5: case 7: case 8: case 10: case 12:
        daysInMonth = 31;
        break;
    case 4: case 6: case 9: case 11:
        daysInMonth = 30;
        break;
    case 2:
        daysInMonth = 28;      // ignoring leap years for simplicity
        break;
    default:
        daysInMonth = -1;
        break;
}
```

--> This exact ambiguity -- "did the author forget `break`, or mean to fall through?" -- is precisely what modern switch expressions (below) were designed to eliminate.

# Modern switch Expressions (Java 14+)

--> Java 14 standardized a new switch FORM that is an EXPRESSION (it produces and returns a value) rather than only a statement, using arrow (`->`) syntax -- each case executes ONLY its own branch, with no fall-through at all, by design.

```java
int day = 3;
String name = switch (day) {
    case 1 -> "Monday";
    case 2 -> "Tuesday";
    case 3 -> "Wednesday";
    case 4 -> "Thursday";
    case 5 -> "Friday";
    case 6, 7 -> "Weekend";        // multi-label case -- comma-separated, no fall-through needed
    default -> "Unknown";
};
System.out.println(name);          // Wednesday
```

--> **`yield` for multi-statement branches** -- the arrow form directly produces a value for single expressions, but if a branch needs a block of statements (e.g. some computation before producing the result), `yield` explicitly returns the value from that block, playing a role similar to `return` inside a method:

```java
int score = 72;
String grade = switch (score / 10) {
    case 10, 9 -> "A";
    case 8 -> "B";
    case 7 -> {
        System.out.println("Borderline C range");   // extra statement allowed inside a block
        yield "C";
    }
    default -> "F";
};
```

--> **Exhaustiveness** -- when switching over an `enum`, the compiler can verify every constant is handled and the `default` branch becomes optional (or even flaggable as unreachable) -- this is a genuine safety improvement over the classic switch, where a forgotten case just silently does nothing.
--> Modern switch expressions can still be used as plain STATEMENTS too (ignoring the produced value) -- the arrow syntax and lack of fall-through apply either way, so there is rarely a reason to reach for the old colon-and-break form in new code.

# Pattern Matching for switch (Java 21+)

--> Java 21 finalized PATTERN MATCHING for `switch`, extending case labels beyond constants to also match on an object's TYPE (and optionally deconstruct it), directly replacing long `if (x instanceof A) ... else if (x instanceof B) ...` chains.

```java
// Requires Java 21+
static String describe(Object obj) {
    return switch (obj) {
        case Integer i when i < 0 -> "negative integer: " + i;
        case Integer i            -> "integer: " + i;
        case String s             -> "string of length " + s.length();
        case null                 -> "it's null";
        default                   -> "something else: " + obj;
    };
}
```

--> **`case Integer i ->`** -- this is a TYPE PATTERN: it matches if `obj` is an `Integer`, and simultaneously binds it to a new local variable `i` of that narrowed type, no manual cast required (compare to classic Java: `if (obj instanceof Integer) { Integer i = (Integer) obj; ... }`).
--> **Guarded patterns with `when`** -- a `when` clause adds an extra boolean condition on top of the type match -- `case Integer i when i < 0` only matches integers that ALSO satisfy `i < 0`, letting you express "type AND condition" as a single readable case instead of nesting an `if` inside the branch.
--> **`case null`** -- pattern-matching switch can match `null` directly as its own case (previously a `switch` on a null reference always threw `NullPointerException`), which forces you to consciously decide how null should be handled rather than crashing by default.
--> **Case order matters here too** -- like the classic switch's top-to-bottom evaluation, patterns are tried in order, so the more specific guarded case (`i when i < 0`) must appear BEFORE the more general one (`Integer i`), or the general case would always match first and the guarded case would be unreachable (a compile error in Java for provably-unreachable cases).

# The Three Loop Types

--> All three loop types can express the same repetition, but each communicates INTENT differently -- picking the right one makes code self-documenting.

## for -- When You Know the Iteration Count/Range Up Front

```java
for (int i = 0; i < 5; i++) {
    System.out.println("Iteration " + i);
}
```

--> The three clauses -- initialization, condition, update -- run in a fixed pattern: initialize once, then repeat (check condition -> run body -> run update) until the condition is false. Use `for` whenever the number of iterations (or the range being walked) is known or computable before the loop starts.

## while -- When the Continuation Condition Isn't Naturally a Counter

```java
int n = 100;
int steps = 0;
while (n != 1) {                 // Collatz conjecture step count -- unknown iterations in advance
    n = (n % 2 == 0) ? n / 2 : 3 * n + 1;
    steps++;
}
System.out.println("Steps: " + steps);
```

--> Use `while` when continuation depends on some evolving state (a flag, a computed value, external input) rather than a simple counter, and when it's possible the body might not need to run even once.

## do-while -- When the Body Must Run At Least Once

```java
java.util.Scanner scanner = new java.util.Scanner(System.in);
int input;
do {
    System.out.println("Enter a positive number:");
    input = 42;                  // stand-in for scanner.nextInt() in a real program
} while (input <= 0);
```

--> `do-while` checks its condition AFTER the body runs, guaranteeing at least one execution -- the natural fit for menu loops and input validation, where you must show the prompt / attempt the read before you have anything to check.

# Enhanced for-each Loop

--> The for-each loop (`for (Type item : collection)`) iterates every element of an array or any `Iterable` (lists, sets, etc.) without manual indexing -- it trades the ability to know the current index (or to skip/step) for clarity and fewer off-by-one bugs.

```java
int[] numbers = {10, 20, 30, 40};
for (int n : numbers) {
    System.out.println(n);
}

java.util.List<String> names = java.util.List.of("Ann", "Bo", "Cid");
for (String name : names) {
    System.out.println(name);
}
```

--> Use a classic indexed `for` instead of for-each when you need the index itself, need to iterate backwards, need to modify the underlying array/list structurally during iteration, or need to walk two collections in lockstep.

# break and continue

--> **`break`** exits the innermost enclosing loop (or switch) immediately -- nothing after it in that iteration or in later iterations runs.
--> **`continue`** skips the REST of the current iteration's body and jumps straight to the next iteration's condition check (the update step, for a `for` loop).

```java
for (int i = 1; i <= 10; i++) {
    if (i == 7) {
        break;                  // stop the loop entirely once i reaches 7
    }
    if (i % 2 == 0) {
        continue;               // skip printing even numbers, but keep looping
    }
    System.out.println(i);      // prints 1, 3, 5
}
```

# Labeled Statements -- break/continue for Nested Loops

--> A plain `break` or `continue` only affects the INNERMOST loop it's written in -- when you need to break or continue an OUTER loop from inside a nested one, you label the outer loop and reference that label.

```java
outer:
for (int i = 0; i < 3; i++) {
    for (int j = 0; j < 3; j++) {
        if (i == 1 && j == 1) {
            break outer;         // exits BOTH loops immediately, not just the inner one
        }
        System.out.println(i + "," + j);
    }
}
// Output: 0,0  0,1  0,2  1,0  -- then breaks out entirely when i=1, j=1
```

--> **Why this is genuinely needed** -- without a label, escaping both loops from deep inside the inner one requires an awkward workaround, such as a boolean "found" flag checked by an extra `if` after the inner loop on every outer iteration:

```java
// Workaround WITHOUT a label -- extra flag variable and extra condition check
boolean found = false;
for (int i = 0; i < 3 && !found; i++) {
    for (int j = 0; j < 3; j++) {
        if (i == 1 && j == 1) {
            found = true;
            break;                // only exits the inner loop
        }
        System.out.println(i + "," + j);
    }
}
```

--> `continue label` works the same way but skips to the next iteration of the LABELED loop instead of exiting entirely -- useful for "abandon this outer iteration and move to the next one" once some inner condition is met.

# Infinite Loops

--> A loop is infinite when its continuation condition never becomes false (or is never checked at all) -- sometimes intentional, sometimes an accidental bug.

```java
// Intentional -- typical for a server/event loop that runs until explicitly stopped
while (true) {
    // process incoming events...
    if (shutdownRequested()) {
        break;                    // the ONLY way out -- an explicit break
    }
}

for (;;) {                        // equivalent intentional infinite for-loop (all clauses omitted)
    // ...
}
```

```java
// ACCIDENTAL infinite loop -- classic bug: forgetting to update the loop variable
int i = 0;
while (i < 5) {
    System.out.println(i);
    // BUG: no "i++" here -- i stays 0 forever, condition never becomes false
}
```

--> Intentional infinite loops are a legitimate pattern (servers, game loops, listeners) as long as there is a clear, reachable exit path (a `break`, `return`, or `System.exit`) -- the danger is purely in loops that were meant to terminate but have a broken or missing update to their condition variables.

# Deep Dive -- if vs switch: When to Use Which

| Situation | Prefer |
|---|---|
| Complex boolean conditions (ranges, `&&`/`\|\|`, multiple unrelated variables) | `if` / `else if` |
| Testing one variable against many discrete, known values | `switch` |
| Need to test different variables in different branches | `if` / `else if` |
| Result needs to be assigned as an expression (Java 14+) | switch expression |
| Branching on an `enum` where you want compiler-enforced exhaustiveness | `switch` |
| Branching on an object's runtime TYPE (Java 21+) | pattern-matching `switch` |
| Only 1-2 conditions total | `if` (a switch is overkill for very few branches) |

--> As a rule of thumb: reach for `switch` (preferably the modern expression form) the moment you notice a chain of `else if (x == someConstant)` comparisons against the SAME variable -- that pattern is exactly what switch was designed to express more safely and, for enums, more completely (via exhaustiveness checking) than an equivalent `if` chain ever can.

# Deep Dive -- Why the Classic switch Still Exists Despite Its Fall-Through Danger

--> Java could not simply remove or silently change the classic colon-and-`break` switch without breaking every existing Java program ever compiled against it -- Java's backward-compatibility guarantee means old syntax keeps working forever, so instead of replacing it, Java 14+ ADDED the arrow-based expression form alongside it as the recommended default for new code.
--> The fall-through behavior itself is not a "mistake" in the language -- it mirrors C's switch precisely, which was a deliberate design choice at the time to allow the multi-label grouping and shared-logic patterns shown earlier -- it only becomes a "gotcha" because most cases in most real switches are NOT intended to fall through, so the language's default behavior (fall through unless told otherwise) is the opposite of what's usually wanted, which is exactly the ergonomic problem the new arrow syntax fixes by making "no fall-through" the default instead.
