# Function Composition -- Building Complex Behavior From Simple Pieces

--> Composition means combining two or more simple functions into a new function, where the output of one becomes the input of the next -- the same idea as mathematical function composition (f(g(x))), applied directly to JavaScript functions. Rather than writing one large function that does everything, composition encourages writing many small, single-purpose functions and combining them as needed.

```javascript
const trim = (str) => str.trim();
const toLowerCase = (str) => str.toLowerCase();
const removeSpaces = (str) => str.replace(/\s+/g, "");

// Manual composition -- calling each function on the previous result
function normalizeUsername(input) {
  return removeSpaces(toLowerCase(trim(input)));
}

normalizeUsername("  John Smith  ");   // "johnsmith"
```

--> The manual version above works, but reads "inside-out" (you have to read from the innermost call outward to understand the actual order of operations) and doesn't scale well past 2-3 functions. A generic `compose` helper fixes both problems.

# Writing a Generic compose Function

```javascript
function compose(...fns) {
  return function (initialValue) {
    return fns.reduceRight((acc, fn) => fn(acc), initialValue);
  };
}

const normalizeUsername = compose(removeSpaces, toLowerCase, trim);
normalizeUsername("  John Smith  ");   // "johnsmith"
```

--> `compose` applies its functions RIGHT TO LEFT, mirroring mathematical notation `f(g(h(x)))` -- `trim` runs first (rightmost), then `toLowerCase`, then `removeSpaces` (leftmost) runs last. This is the convention used by most established functional libraries (like Redux's `compose`, covered briefly in the React Redux file).
--> `pipe` is the same idea, but LEFT TO RIGHT -- often considered more intuitive to read since it matches the actual execution order visually.

```javascript
function pipe(...fns) {
  return function (initialValue) {
    return fns.reduce((acc, fn) => fn(acc), initialValue);
  };
}

const normalizeUsername = pipe(trim, toLowerCase, removeSpaces);
normalizeUsername("  John Smith  ");   // "johnsmith" -- same result, reads left-to-right in execution order
```

# Why Composition Matters -- Beyond Just Convenience

--> Each individual function (`trim`, `toLowerCase`, `removeSpaces`) is trivially testable in isolation, reusable in other compositions, and easy to reason about on its own -- composition lets complexity emerge from combining simple, well-understood pieces, rather than living inside one large, harder-to-test function that does everything at once.
--> This directly parallels the Unix philosophy of small, focused command-line tools piped together (`cat file.txt | grep "error" | wc -l`, referenced in the Linux Terminal notes) -- each command does one thing well, and the pipe operator composes them, exactly like `pipe()` composes functions here.

# Currying -- Transforming Multi-Argument Functions

--> Currying transforms a function that takes multiple arguments into a sequence of functions that each take ONE argument, returning the next function in the chain until all arguments have been supplied.

```javascript
// A normal, non-curried function
function add(a, b, c) {
  return a + b + c;
}
add(1, 2, 3);   // 6

// The same logic, curried by hand
function curriedAdd(a) {
  return function (b) {
    return function (c) {
      return a + b + c;
    };
  };
}
curriedAdd(1)(2)(3);   // 6
```

--> At first glance this looks like unnecessary complexity for the same result -- the real value shows up with PARTIAL APPLICATION, covered next.

# Partial Application -- Currying's Practical Payoff

--> Because each step of a curried function returns a new function, you can supply SOME arguments now and the rest LATER, producing a specialized, reusable function along the way.

```javascript
const add5 = curriedAdd(5);       // Partially applied -- "a" is now fixed at 5
const add5and10 = add5(10);        // "b" is now also fixed at 10

add5and10(20);   // 35
add5(1)(1);        // 7 -- add5 can be reused with different remaining arguments each time
```

--> This is the exact same underlying idea as the `multiplyBy` function factory from the Higher-Order Functions file, generalized into a systematic pattern -- currying is really "function factories, formalized" for any function, not just ones you specifically design as factories from the start.

# A Generic curry Helper

--> Writing the nested-function version by hand for every function is tedious -- a generic `curry` helper automatically curries ANY function, regardless of how many arguments it takes.

```javascript
function curry(fn) {
  return function curried(...args) {
    if (args.length >= fn.length) {
      return fn.apply(this, args);
    }
    return function (...moreArgs) {
      return curried.apply(this, [...args, ...moreArgs]);
    };
  };
}

function multiply(a, b, c) {
  return a * b * c;
}

const curriedMultiply = curry(multiply);

curriedMultiply(2)(3)(4);      // 24
curriedMultiply(2, 3)(4);       // 24 -- works with multiple arguments per call too
curriedMultiply(2, 3, 4);        // 24 -- works fully uncurried too, if you supply everything at once
```

--> `fn.length` returns the number of parameters a function was DECLARED with -- the generic curry helper uses this to know how many arguments it's still waiting for before it should actually invoke the original function. This is a genuinely clever, commonly-cited trick, though it only works correctly for functions without default parameters or rest parameters, which change what `fn.length` reports.

# Point-Free Style -- Omitting the Data Argument Entirely

--> "Point-free" (also called "tacit programming") means defining a function WITHOUT explicitly mentioning the argument(s) it operates on -- composing existing functions directly, letting the data flow through implicitly rather than naming it in every step.

```javascript
// Not point-free -- "user" is explicitly named and threaded through manually
const getActiveUserNames = (users) => users.filter(u => u.isActive).map(u => u.name);

// Point-free -- built purely by composing functions, no explicit data parameter mentioned
const isActive = (user) => user.isActive;
const getName = (user) => user.name;

const getActiveUserNames = pipe(
  (users) => users.filter(isActive),
  (users) => users.map(getName)
);
```

--> Point-free style is popular in functional-programming-heavy codebases because it emphasizes WHAT is being computed (composition of named, meaningful operations) over HOW (the explicit looping/threading mechanics) -- but taken too far, deeply point-free code can become genuinely harder to read than the straightforward version, since there's no argument name left to anchor what's actually flowing through the pipeline. Most JavaScript codebases use it selectively, not as a strict universal rule.

# Where These Patterns Actually Show Up in Real Code

--> Redux middleware and enhancers use `compose` directly to combine multiple store enhancers into one (covered briefly in the React Redux and Zustand file).
--> Currying/partial application appears naturally whenever you configure a function once and reuse the specialized version repeatedly -- API client factories (`createApiClient(baseUrl)` returning a function that always uses that base URL), the `debounce`/`throttle` utilities from the Higher-Order Functions file (each one is itself a form of partial application, fixing the delay/interval and returning a specialized function).
--> Lodash/Ramda (popular functional utility libraries) provide battle-tested, edge-case-hardened versions of `compose`, `curry`, `pipe`, and dozens of related helpers -- in most production code, reaching for one of these libraries is preferred over hand-rolling the versions shown here, which are written for understanding the underlying mechanics rather than for production robustness.
