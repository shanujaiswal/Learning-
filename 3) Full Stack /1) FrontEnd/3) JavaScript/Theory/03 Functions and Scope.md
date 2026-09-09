# Function

--> Need reusable logic that performs an action, such as calculations, event handling, or API calls.
--> It provides more useful properties.
--> Only function provide prototype property.
--> Function Decleration
--> function functionName(parameters) {
// code to be executed
}

# Function expression

--> way to define a function in programming (usually JavaScript) using an expression.
--> const j = function () {
console.log("happy birthday to you ......")
}
j()

--> Named function expression --> const j = function greet() { ... } -- gives the function a name usable for recursion/debugging inside itself, but the name isn't accessible outside j

# Arrow Function

--> const k = () => {
console.log("My name is shanu jaiswal")
}
k()

# One line Function

--> const n = Number => Number % 2 === 0;
console.log(n(14))

# JavaScript Hoisting

--> Hoisting --> during compilation, JS moves variable and function declarations to the top of their scope before executing the code. A variable can be used before it has been declared / declared after it has been used, but the behavior differs by keyword.
--> var --> hoisted AND initialized with undefined -- accessing it before the actual line runs gives undefined, not an error.
--> let and const --> hoisted but NOT initialized -- accessing them before their declaration throws a ReferenceError.
--> Function Declarations (`function foo() {}`) --> fully hoisted, including the function body -- can be called before the line they're written on.
--> Function Expressions and Arrow Functions (`const foo = () => {}`) --> only the variable is hoisted (per the var/let/const rule above), not the function body -- calling them before the line throws.

# Temporal Dead Zone (TDZ)

--> The TDZ is the period between entering a scope and the actual `let`/`const` declaration line, during which the variable exists but cannot be accessed.
--> console.log(x); let x = 5; --> throws "Cannot access 'x' before initialization" because x is in the TDZ at that point.
--> Exists specifically to catch bugs that var's "hoisted as undefined" behavior used to hide silently.

# Default Parameter

--> Provide a default value for a parameter if no value is provided.

# Rest Parameter

--> Collect all remaining arguments into an array.

# Destructuring

--> Simplifies extracting values from arrays and objects.
--> It makes the function parameters more readable, especially when working with complex data structures.
--> can use default values to ensure that a parameter has a fallback value.

# The arguments Object

--> Available inside regular functions (not arrow functions) -- an array-like object containing all arguments passed to the function, even ones without a matching named parameter.
--> const sum = function() { return [...arguments].reduce((a, b) => a + b, 0) }
--> Arrow functions don't have their own arguments -- they inherit it from the nearest enclosing regular function's scope. Use rest parameters (...args) instead in arrow functions.

# Callback function

--> A callback function is simply a function that is passed as an argument to another function, to be invoked later (either synchronously or asynchronously).
--> Format :- (callback(element, index, array), thisArg);
--> Is a function that is passed as an argument to another function and is executed after the completion of that function's execution.
--> It allows to customize the behavior of the function that are passing it to, based on certain conditions, or to be executed once some task is finished.
--> A function that tests each element in the array. It takes the following arguments:
--> Element: The current element being processed in the array.
--> Index (optional): The index of the current element.
--> Array (optional): The array that find() is called on.
--> ThisArg (optional): A value to use as this when executing the callback.

# Lexical Scope

-–> This concept refers to the scope in which a variable is defined. When a value is requested, the program first checks if it is available within the current scope. If it’s not found, the search continues in the parent scope, and so on, moving upwards through the scope chain until the value is found or the global scope is reached.

# Block scope

--> let and const are block scope
--> let and const are only access in that block scope
--> we can use same name variable if we are using block scope
--> we can not call variable in different block

# Function scope

--> var is function scope
--> var is access from outside of block scope
--> we can call variable in different block

# Closures

--> A closure is formed when a function remembers and can access variables from its outer (lexical) scope even after the outer function has returned
--> Used for data privacy, creating function factories, and maintaining state (e.g. counters)

# IIFE (Immediately Invoked Function Expression)

--> A function that runs as soon as it is defined
--> (function () { console.log("runs immediately"); })();
--> Used to create a private scope and avoid polluting the global namespace

# Deep Dive -- The Scope Chain, Step by Step

--> When a variable is referenced, the JS engine looks it up in a specific order: first the CURRENT function's local scope, then the scope of the function it's nested inside, then that function's outer scope, and so on outward, until it reaches the global scope -- this ordered chain of nested scopes is the "scope chain," and it's fixed at the time the function is WRITTEN (lexically), not at the time it's called.

```javascript
const globalVar = "global";

function outer() {
  const outerVar = "outer";

  function inner() {
    const innerVar = "inner";
    console.log(innerVar, outerVar, globalVar);   // All three are reachable via the scope chain
  }

  inner();
}
```

--> If a variable isn't found anywhere along the entire chain, JS throws a `ReferenceError` -- it does NOT silently return `undefined` the way accessing a missing object property would.
--> This lexical (write-time) scoping is precisely what makes Closures (covered in depth in the dedicated Closures file) possible -- an inner function's scope chain is determined by WHERE it was defined in the source code, not by where it's later called from, which is why a returned inner function keeps working correctly even after being invoked far away from its original defining context.

# Deep Dive -- Named Function Expressions and Recursion

--> A named function expression's name is only visible INSIDE the function's own body -- useful specifically for self-referencing recursion without depending on an outer variable name that might later be reassigned.

```javascript
const factorial = function fact(n) {
  return n <= 1 ? 1 : n * fact(n - 1);   // "fact" refers reliably to itself, even if "factorial" is later reassigned
};

const otherRef = factorial;
factorial = null;
console.log(otherRef(5));   // Still works -- 120 -- because the internal recursive call uses "fact", not the outer "factorial" binding
```

# Deep Dive -- Parameter Destructuring in Practice

--> Combining destructuring with default parameters is a common, idiomatic pattern for functions accepting an options object, avoiding a long list of individual positional parameters and letting callers pass only the options they actually care about.

```javascript
function createUser({ name, age = 18, role = "member" } = {}) {
  return { name, age, role };
}

createUser({ name: "Alice" });                 // { name: "Alice", age: 18, role: "member" }
createUser({ name: "Bob", role: "admin" });     // { name: "Bob", age: 18, role: "admin" }
createUser();                                     // Works too -- the "= {}" default prevents a crash when called with no argument at all
```

# Deep Dive -- Function Scope vs Block Scope, a Common var Pitfall

--> Because `var` is function-scoped (not block-scoped), it "leaks" out of `if`/`for`/`while` blocks in a way that surprises developers coming from block-scoped languages -- this is one of the concrete, practical reasons `let`/`const` are now preferred defaults over `var`.

```javascript
if (true) {
  var leaked = "I'm accessible outside this block";
  let notLeaked = "I'm only accessible inside this block";
}
console.log(leaked);       // Works -- "I'm accessible outside this block"
console.log(notLeaked);    // ReferenceError -- notLeaked is not defined out here
```

# Related Files

--> Closures are covered in full depth in the dedicated Closures file, including private state patterns and the classic loop-and-closure pitfall.
--> Higher-order functions (functions that take or return other functions, building directly on everything in this file) are covered in the Higher-Order Functions file.
--> `this` binding inside functions -- and how it differs between regular functions and arrow functions -- is covered in the `this` Keyword, call, apply and bind file.
