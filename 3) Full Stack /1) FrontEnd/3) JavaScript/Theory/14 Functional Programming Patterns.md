# Higher-Order Functions

--> A function that either takes another function as an argument, returns a function, or both.
--> map(), filter(), reduce() are all built-in higher-order functions -- they accept a callback.
--> Useful for writing reusable, composable logic instead of repeating loops.

# Pure Functions

--> A function that always returns the same output for the same input and has no side effects (doesn't modify external state, doesn't log, doesn't mutate its arguments).
--> Easier to test, debug, and reason about -- the basis for predictable state updates in React/Redux.
--> function add(a, b) { return a + b } is pure. function addToCart(item) { cart.push(item) } is not (mutates external cart).

# Currying

--> Transforms a function that takes multiple arguments into a sequence of functions that each take one argument.
--> const add = (a) => (b) => a + b; add(2)(3) --> 5
--> Useful for creating specialized versions of a function by partially applying arguments: const add5 = add(5); add5(10) --> 15

# Function Composition

--> Combining two or more functions to produce a new function, where the output of one becomes the input of the next.
--> const compose = (f, g) => (x) => f(g(x));
--> const pipe = (...fns) => (x) => fns.reduce((acc, fn) => fn(acc), x); -- runs functions left to right instead of right to left.
--> Encourages building complex logic out of small, single-purpose functions.

# Memoization

--> Caching a function's results by its input arguments so repeated calls with the same input skip recomputation and return the cached value instantly.
--> function memoize(fn) { const cache = new Map(); return (...args) => { const key = JSON.stringify(args); if (cache.has(key)) return cache.get(key); const result = fn(...args); cache.set(key, result); return result } }
--> Useful for expensive pure functions (recursive Fibonacci, heavy calculations) -- only works correctly on pure functions since it assumes same input always gives same output.

# Immutability and Copying Values

--> Treating data as read-only -- instead of mutating an array/object, create a new one with the change applied (common pattern in React state updates).
--> Shallow copy --> copies only the top level; nested objects/arrays are still shared by reference. Done with the spread operator ({ ...obj }, [...arr]) or Object.assign({}, obj).
--> Deep copy --> copies every nested level so there's no shared reference at any depth. structuredClone(obj) is the modern built-in way; JSON.parse(JSON.stringify(obj)) was the old workaround (but drops functions, undefined, and Dates become strings).
--> Mutating array methods (push, pop, splice, sort, reverse) change the original array -- prefer non-mutating equivalents (spread + push logic, slice, map/filter, [...arr].sort()) when immutability matters.
--> Why it matters --> React (and Redux) detect changes by reference comparison, so mutating state in place can silently fail to trigger a re-render.

# Tagged Template Literals

--> A function call where the function "tags" a template literal, receiving the string parts and interpolated values separately instead of a single combined string.
--> function tag(strings, ...values) { console.log(strings, values) } tag\`Hello ${name}, you are ${age}\`
--> strings is an array of the literal text chunks, values is an array of the interpolated expressions in order.
--> Used for things like styled-components (css\`...\`), safely escaping user input, or building custom string-formatting utilities.
--> Concrete example (HTML-escaping tag) --> function safeHtml(strings, ...values) { return strings.reduce((out, str, i) => out + str + (values[i] !== undefined ? String(values[i]).replace(/</g, "&lt;").replace(/>/g, "&gt;") : ""), "") } safeHtml\`<p>${userInput}</p>\` -- escapes only the interpolated values, leaving the literal markup untouched, which prevents basic HTML/script injection from user input.

# Deep Dive -- Function Arity and .length

--> A function's "arity" is the number of parameters it's declared with, exposed via `fn.length` -- this is precisely the mechanism a generic `curry()` helper uses to know how many arguments it's still waiting for before actually invoking the wrapped function.

```javascript
function add(a, b, c) { return a + b + c; }
console.log(add.length);   // 3

const arrow = (x, y) => x + y;
console.log(arrow.length);   // 2
```

--> **Caveat** -- `.length` only counts parameters BEFORE the first default value or rest parameter; both stop the count early, since the function is considered variadic/optional from that point onward.

```javascript
function f(a, b = 1, ...rest) {}
console.log(f.length);   // 1 -- only "a" is counted; "b" (has a default) and "rest" are excluded
```

--> This is exactly why a hand-rolled generic curry utility (like the one covered in the Function Composition, Currying and Point-Free Style file) can behave unexpectedly if the wrapped function uses default parameters or rest parameters -- `.length` no longer reflects the "real" number of expected arguments in those cases.

# Further Depth on These Patterns

--> This file introduces Higher-Order Functions, Pure Functions, Currying, Composition, and Immutability at a survey level -- each topic is covered in far greater depth, with more worked examples and real-world patterns (debounce/throttle/memoize as HOFs, `compose`/`pipe` implementations, partial application, and the full immutable-update patterns for arrays/objects), in the dedicated Higher-Order Functions, Function Composition/Currying/Point-Free Style, and Immutability and Pure Functions in Practice files.
