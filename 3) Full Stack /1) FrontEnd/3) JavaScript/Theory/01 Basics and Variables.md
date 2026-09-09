--> Common Practice to end statement in javascript with a semi-colon (;)
--> In JavaScript, variable names are case-sensitive. This means that name and NAME would be treated as two different variables.
--> function is used to define a function in JavaScript
--> ${name} inserts the value of the name variable into the string.

# alert() – Displays a simple pop-up message

# prompt() – Asks the user for input and returns the value.

# confirm() – Shows a confirmation dialog with OK and Cancel buttons.

# window.print() – Opens the print dialog for the current page.

# Variable

--> Point to a specific Memory address that store a value
--> Giving a name
--> It is done with let,Const & Var
--> Assign a value to variable using assignment Operator ( = )
--> Assigning a value to a variable at moment of its declaration is known as Initialization

--> Var --> Used for very old Java script
--> Const --> Value Cannot change
--> Let --> Value Can be changed

# Primitive Data Types

--> Not created using ( ) -- primitives are simple values assigned directly (e.g. let x = 5), not constructed by calling something
--> number , string ,boolean ,null ,undefined ,symbol(introduced in ES6) ,bigint (introduced in ES11)
--> Primitive are saved in stack
--> 1, 2, 3, 4, "String", null, undefined

# Type Coercion & Equality

--> Type coercion is automatic or implicit conversion of values from one data type to another (e.g. "5" + 1 = "51", "5" - 1 = 4)
--> == (loose equality) compares values after converting them to the same type
--> === (strict equality) compares both value and type, no conversion happens
--> Always prefer === to avoid unexpected bugs from coercion

# Truthy and Falsy Values

--> Falsy values: false, 0, "", null, undefined, NaN
--> Everything else is truthy (including "0", "false", [], {})
--> Used heavily in conditionals, default values, and short-circuiting

# Ternary Operator

--> condition ? valueIfTrue : valueIfFalse
--> Shorthand replacement for simple if-else statements

# Optional Chaining (?.)

--> Safely access deeply nested object properties without throwing an error if a reference is null or undefined
--> obj?.address?.city returns undefined instead of throwing if address doesn't exist

# Nullish Coalescing (??)

--> Returns the right-hand value only if the left-hand value is null or undefined (not for other falsy values like 0 or "")
--> let value = input ?? "default"

# Strict Mode

--> "use strict"; enables a restricted variant of JS that catches common mistakes (e.g. undeclared variables)
--> Helps write more secure and optimized code

# typeof Operator

--> Returns a string indicating the data type of a value (e.g. "string", "number", "boolean", "object", "undefined", "function", "symbol", "bigint")
--> typeof null returns "object" (a well-known historical bug in JS)
--> typeof [] and typeof {} both return "object" -- use Array.isArray() to distinguish arrays

# Logical Operators

--> && (AND) --> Returns the first falsy value, or the last value if all are truthy
--> || (OR) --> Returns the first truthy value, or the last value if all are falsy
--> ! (NOT) --> Reverses the boolean value of its operand
--> Short-circuiting --> && stops at the first falsy value, || stops at the first truthy value, so the rest of the expression is never evaluated
--> Commonly used for default values (before ??) and conditional rendering (e.g. isLoggedIn && "Welcome")

# Deep Dive -- var vs let vs const in Loops

--> A classic, historically confusing gotcha directly caused by `var` being function-scoped rather than block-scoped -- every iteration of a `var` loop shares the SAME variable binding, so callbacks capturing it (via closures) all see its FINAL value once the loop finishes.

```javascript
for (var i = 0; i < 3; i++) {
  setTimeout(() => console.log(i), 100);
}
// Logs: 3, 3, 3 -- every callback closed over the SAME "i", already finished looping to 3

for (let j = 0; j < 3; j++) {
  setTimeout(() => console.log(j), 100);
}
// Logs: 0, 1, 2 -- "let" creates a NEW binding of "j" for each iteration
```

--> This single behavioral difference is one of the most concrete, practical reasons `let`/`const` replaced `var` as the modern default -- it makes closures inside loops behave the way most developers intuitively expect, with zero extra effort.

# Deep Dive -- Type Coercion Edge Cases Worth Memorizing

```javascript
"5" + 1        // "51"  -- + with a string operand always coerces to string concatenation
"5" - 1        // 4     -- but - has no string meaning, so it coerces both sides to numbers
"5" * "2"      // 10    -- same logic -- * forces numeric coercion
[] + []        // ""    -- both arrays coerce to empty strings, then concatenate
[] + {}        // "[object Object]" -- array coerces to "", object coerces to its string tag
+"42"          // 42    -- unary + is a quick, common way to force string-to-number conversion
!!"hello"      // true  -- double-negation is a quick way to force a value to its boolean equivalent
```

--> These specific examples are frequently used interview questions precisely because they reveal whether someone understands JS's coercion RULES rather than having memorized the output of common expressions -- the underlying rule to actually remember: `+` prefers string concatenation if EITHER side is a string; every other arithmetic operator forces numeric coercion on both sides.

# Deep Dive -- Primitive vs Reference Assignment

--> Primitives (`number`, `string`, `boolean`, `null`, `undefined`, `symbol`, `bigint`) are copied BY VALUE on assignment -- each variable gets its own independent copy. Objects/arrays are copied BY REFERENCE -- assigning one variable to another just copies the reference (memory address), so both variables point to the SAME underlying object.

```javascript
let a = 5;
let b = a;
b = 10;
console.log(a);   // 5 -- unaffected, "a" and "b" are independent copies

let obj1 = { value: 5 };
let obj2 = obj1;
obj2.value = 10;
console.log(obj1.value);   // 10 -- "obj1" and "obj2" point to the SAME object in memory
```

--> This is precisely why comparing two structurally-identical objects with `===` returns `false` (they're different objects in memory, even with identical contents), and why the Immutability file's spread-based update patterns exist -- to create a genuinely NEW object/array rather than mutating a shared reference that other code might still be relying on.
