# Symbol

--> A primitive data type introduced in ES6 that creates a unique, immutable identifier
--> Even two symbols with the same description are never equal to each other
--> Commonly used as unique object keys to avoid property name collisions
--> Symbol.iterator --> a well-known symbol used to make an object iterable with for...of (see Iterators notes)
--> Symbols are NOT included in Object.keys(), for...in, or JSON.stringify() -- they're effectively hidden from normal enumeration, which is part of why they're used for "private-ish" or metadata keys

# BigInt

--> A primitive type (introduced in ES11) for representing integers larger than Number.MAX_SAFE_INTEGER
--> Created by appending n to an integer literal (e.g. 12345678901234567890n) or using BigInt(value)
--> Cannot be mixed with regular Numbers in arithmetic without explicit conversion
--> typeof 10n --> "bigint" (a distinct primitive type from "number")

# WeakMap and WeakSet

--> Similar to Map and Set, but keys (WeakMap) / values (WeakSet) must be objects
--> References are held "weakly" -- if there's no other reference to the object, it can be garbage collected
--> Not iterable and have no .size property, unlike Map/Set
--> Useful for storing metadata about an object without preventing it from being garbage collected

# WeakRef and FinalizationRegistry

--> WeakRef holds a "weak" reference to an object -- one that does NOT prevent that object from being garbage collected, unlike a normal variable reference.
--> Useful for caches or lookup tables where you want to keep a reference to an object ONLY as long as something else is still using it -- once nothing else references it, it can be freed automatically, and your weak reference simply stops resolving.

```javascript
let obj = { data: "large dataset" };
const ref = new WeakRef(obj);

console.log(ref.deref());   // { data: "large dataset" } -- still alive

obj = null;   // Remove the only strong reference
// Sometime later, after garbage collection runs:
console.log(ref.deref());   // undefined -- the object may have been collected
```

--> FinalizationRegistry lets you register a callback to run AFTER an object has been garbage collected -- useful for cleanup logic (e.g. releasing an external resource tied to a JS object).

```javascript
const registry = new FinalizationRegistry((heldValue) => {
  console.log(`Cleaned up: ${heldValue}`);
});

let obj = { name: "resource" };
registry.register(obj, "resource-1");
obj = null;   // Once garbage collected, the registry callback will eventually fire
```

--> Caution: garbage collection timing is NOT guaranteed or immediate -- these APIs should never be relied on for critical/time-sensitive cleanup, only as a backup/optimization. Both are relatively advanced/rare in everyday app code.

# JSON.stringify() Edge Cases

--> Circular references -- if an object (directly or indirectly) references itself, `JSON.stringify()` throws a TypeError, since it cannot represent an infinite structure as text.
```javascript
const obj = { name: "Alice" };
obj.self = obj;   // Circular reference

JSON.stringify(obj);
// Uncaught TypeError: Converting circular structure to JSON
```
--> Fix: use `structuredClone()` instead (handles circular references correctly), or strip the circular property before stringifying, or use a custom `replacer` function that tracks already-seen objects.

--> Functions, `undefined`, and Symbols are silently DROPPED (for object properties) or converted to `null` (inside arrays) by `JSON.stringify()` -- they are not valid JSON values.
```javascript
JSON.stringify({ a: 1, fn: () => {}, b: undefined, c: Symbol() });
// '{"a":1}' -- fn, b, and c all disappear

JSON.stringify([1, undefined, () => {}, 2]);
// '[1,null,null,2]' -- inside arrays, unsupported values become null instead of vanishing
```

# Sparse Arrays and Typed Arrays

--> A sparse array has "holes" -- missing indices with no value at all (different from a value that is explicitly `undefined`).
```javascript
const sparse = [1, , 3];       // Hole at index 1
console.log(sparse.length);     // 3
console.log(sparse[1]);         // undefined
console.log(1 in sparse);       // false -- the index doesn't actually exist

sparse.forEach(x => console.log(x));   // Skips the hole entirely -- only logs 1 and 3
```
--> Iteration methods behave differently around holes: `forEach`, `map`, `filter` all SKIP holes, but a plain `for` loop or `at()` will still report `undefined` for that index.

--> Typed Arrays (`Int8Array`, `Uint8Array`, `Float64Array`, etc.) are fixed-length, fixed-type array-like structures backed by raw binary data (an `ArrayBuffer`) -- used for performance-critical or binary data work (e.g. WebGL, audio/video processing, file/network buffers).
```javascript
const buffer = new ArrayBuffer(8);        // 8 bytes of raw memory
const view = new Int32Array(buffer);      // View the buffer as 32-bit integers
view[0] = 42;
console.log(view[0]);   // 42
console.log(view.length); // 2 -- 8 bytes / 4 bytes per Int32 = 2 elements
```
--> Unlike regular arrays, typed arrays cannot grow/shrink, and every element must be the same numeric type -- trading flexibility for raw performance and memory efficiency.

# Object.is() and Value Comparison

--> Object.is(a, b) --> similar to === but fixes two edge cases: Object.is(NaN, NaN) is true (=== says false), and Object.is(0, -0) is false (=== says true).
--> Rarely needed day-to-day, but useful when comparisons involving NaN need to behave predictably.

# structuredClone (Deep Cloning)

--> structuredClone(obj) --> built-in way to deep-clone an object/array, handling nested structures, Dates, Maps, and Sets correctly.
--> Preferred over the old JSON.parse(JSON.stringify(obj)) trick, which silently drops functions/undefined and turns Dates into strings.
--> Cannot clone functions or DOM nodes -- throws a DataCloneError if the value isn't structured-clone-compatible.

# try / catch / finally Recap

--> try block contains code that might throw; catch(err) runs if it does; finally always runs regardless of whether an error occurred.
--> A single catch can handle multiple error types by checking `err instanceof SomeErrorClass` inside it (see Custom Error Classes below).
--> Errors not caught by any try/catch propagate up the call stack until caught somewhere, or crash the program/reject the promise if never caught.
--> Gotcha --> a `return` (or `throw`) inside `finally` overrides any `return`/`throw` from the `try`/`catch` block -- e.g. `function f() { try { return 1 } finally { return 2 } }` returns 2, silently discarding the try's return value.

# The Built-in Error Object

--> new Error("message") --> creates a generic error with a `.message` and a `.stack` (stack trace string, useful for debugging).
--> Other built-in error types --> TypeError (wrong type used, e.g. calling a non-function), ReferenceError (using an undeclared variable), RangeError (a value outside an allowed range, e.g. invalid array length), SyntaxError (invalid code, usually from JSON.parse on bad input), AggregateError (wraps multiple errors into one, thrown e.g. by Promise.any() when all promises reject -- `.errors` holds the individual errors).
--> `error.name` tells you which type it is without needing instanceof, useful when logging.

# Custom Error Classes

--> Can extend the built-in Error class to create custom, descriptive error types
--> class ValidationError extends Error { constructor(message) { super(message); this.name = "ValidationError" } }
--> Lets catch blocks distinguish error types using instanceof (e.g. if (err instanceof ValidationError))
--> Useful for separating expected/handled errors from unexpected bugs

# Error Cause (ES2022)

--> new Error("Failed to save", { cause: originalError }) --> attaches the original underlying error as `.cause` on a new, higher-level error.
--> Useful when re-throwing a more descriptive error without losing the root cause -- `err.cause` can be logged/inspected to trace back to what actually failed.

# Async Error Handling

--> Inside an async function, wrap `await` calls in try/catch to handle rejected promises -- an unhandled rejection otherwise surfaces as an "Uncaught (in promise)" console error.
--> Promise.reject(err) / .catch(err => ...) --> the promise-chain equivalent of throw/catch for non-async-await code.
--> window.addEventListener("unhandledrejection", handler) --> a last-resort global hook for promise rejections that were never caught anywhere.

# Deep Dive -- The extends Error Transpilation Gotcha

--> Extending the built-in `Error` class (shown above in Custom Error Classes) has a well-known historical quirk when code is transpiled down to an older JavaScript target (ES5) by tools like Babel/TypeScript (covered in the TypeScript Fundamentals file) -- the resulting instance can fail `instanceof CustomError` checks, because ES5 transpilation can't fully replicate how native class inheritance from a built-in like `Error` actually works under the hood.

```javascript
class ValidationError extends Error {
  constructor(message) {
    super(message);
    this.name = "ValidationError";
    Object.setPrototypeOf(this, ValidationError.prototype);   // The standard workaround for ES5 transpilation targets
  }
}
```

--> `Object.setPrototypeOf(this, ValidationError.prototype)` explicitly re-establishes the correct prototype chain after `super()`, fixing `instanceof` checks on transpiled code -- unnecessary if your build target is modern JS (ES2015+) natively, but a genuinely common fix needed in codebases still supporting older environments.
