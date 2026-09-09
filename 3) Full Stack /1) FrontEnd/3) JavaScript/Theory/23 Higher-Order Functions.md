# What Makes a Function "Higher-Order"

--> A Higher-Order Function (HOF) is any function that does at least one of two things: **takes another function as an argument**, or **returns a function as its result**. This is only possible in a language where functions are "first-class citizens" -- meaning a function can be assigned to a variable, stored in a data structure, passed around, and returned, exactly like a number or a string. JavaScript has treated functions this way since its very first version, which is why HOFs are so central to the language's idiom, not a bolted-on feature.

```javascript
// A plain, "first-order" function -- takes and returns only plain data
function double(n) {
  return n * 2;
}

// A higher-order function -- takes a FUNCTION as an argument
function applyOperation(numbers, operation) {
  const result = [];
  for (const n of numbers) {
    result.push(operation(n));
  }
  return result;
}

applyOperation([1, 2, 3], double);   // [2, 4, 6]
applyOperation([1, 2, 3], n => n * n);   // [1, 4, 9] -- an inline arrow function works just as well
```

--> Notice `double` and the inline arrow function are passed WITHOUT being called (no parentheses after their name) -- `applyOperation` receives the function itself as a value, and decides when and how many times to actually invoke it. This distinction -- passing a function reference vs. calling a function -- is the single most common early mistake when learning HOFs (`applyOperation(numbers, double())` would pass whatever `double()` immediately returns, not the function itself, and is almost never what's intended).

# Why HOFs Exist -- Abstracting Over Behavior, Not Just Data

--> A regular function abstracts over VALUES -- `double(x)` works for any number `x`. A higher-order function abstracts over BEHAVIOR -- `applyOperation` doesn't care whether you want to double, square, or negate every number; it only knows how to loop and apply whatever operation it's given. This is a genuinely different, more powerful axis of reuse: instead of writing a new loop for every operation, you write the LOOPING LOGIC once, and vary only the behavior plugged into it.
--> This is precisely the idea behind the built-in array methods covered in the Array Methods file (`.map()`, `.filter()`, `.reduce()`) -- each one IS a higher-order function, encapsulating a specific looping pattern, letting you focus only on the specific transformation/condition/accumulation logic relevant to your problem.

# Functions as Arguments -- Callbacks

--> Passing a function as an argument to be called later (immediately, or after some delay/event) is usually called a "callback" -- the terminology used throughout the Async JavaScript file for `setTimeout`, promise `.then()` handlers, and event listeners.

```javascript
[1, 2, 3, 4, 5].filter(n => n % 2 === 0);   // the arrow function here is a callback passed to .filter()

button.addEventListener("click", () => console.log("Clicked!"));   // the arrow function is a callback passed to addEventListener

setTimeout(() => console.log("3 seconds passed"), 3000);   // same pattern, for a timer instead of an event
```

--> All three examples above are structurally identical from a HOF perspective -- a function is handed to another function, which decides WHEN to actually invoke it. `.filter()` calls it once per array element, synchronously, right now. `addEventListener` calls it whenever a click actually happens, possibly never. `setTimeout` calls it once, after a delay. The receiving HOF fully controls the calling convention; the caller only supplies the behavior.

# Functions as Return Values -- Function Factories

--> The second half of the HOF definition -- a function that RETURNS a function -- lets you generate specialized functions from a general template, "baking in" some configuration at creation time.

```javascript
function multiplyBy(factor) {
  return function (number) {
    return number * factor;
  };
}

const double = multiplyBy(2);
const triple = multiplyBy(3);

double(10);   // 20
triple(10);   // 30
```

--> This relies directly on closures (covered in their own dedicated file) -- the returned inner function "remembers" the `factor` value from when it was created, even though `multiplyBy` has already finished running by the time `double`/`triple` are actually called. Function factories and closures are two sides of the same coin: virtually every function-returning HOF works BECAUSE of closures, and closures are most commonly demonstrated using exactly this kind of function factory.

# Combining Both Directions -- Functions That Take AND Return Functions

--> The most powerful HOFs do both at once -- accepting a function as input and producing a new, modified function as output.

```javascript
function withLogging(fn) {
  return function (...args) {
    console.log(`Calling ${fn.name} with arguments:`, args);
    const result = fn(...args);
    console.log(`${fn.name} returned:`, result);
    return result;
  };
}

function add(a, b) {
  return a + b;
}

const loggedAdd = withLogging(add);
loggedAdd(2, 3);
// Calling add with arguments: [2, 3]
// add returned: 5
```

--> This is precisely the mechanism underlying decorators/wrappers in JavaScript (conceptually parallel to the Python Decorators file in the Backend Notes, though JavaScript achieves the same effect through plain HOFs rather than a dedicated `@decorator` syntax for regular functions) -- `withLogging` takes ANY function and returns an enhanced version of it, without needing to know or modify that function's internal implementation at all.

# Practical HOF Patterns Beyond map/filter/reduce

## Debouncing -- Limiting How Often a Function Runs

--> A debounced function delays execution until a specified time has passed WITHOUT it being called again -- extremely common for search-as-you-type inputs, where you don't want to fire an API request on every single keystroke, only once the user has paused typing.

```javascript
function debounce(fn, delayMs) {
  let timeoutId;
  return function (...args) {
    clearTimeout(timeoutId);
    timeoutId = setTimeout(() => fn(...args), delayMs);
  };
}

const debouncedSearch = debounce((query) => {
  console.log(`Searching for: ${query}`);
}, 300);

searchInput.addEventListener("input", (e) => debouncedSearch(e.target.value));
// Rapid typing only triggers ONE actual search call, 300ms after the user stops
```

## Throttling -- Capping the Rate of Execution

--> Unlike debouncing (waiting for a pause), throttling guarantees a function runs AT MOST once per specified interval, no matter how often it's triggered -- common for scroll/resize event handlers, where you want regular updates but not hundreds per second.

```javascript
function throttle(fn, intervalMs) {
  let lastCallTime = 0;
  return function (...args) {
    const now = Date.now();
    if (now - lastCallTime >= intervalMs) {
      lastCallTime = now;
      fn(...args);
    }
  };
}

const throttledScrollHandler = throttle(() => {
  console.log("Scroll position:", window.scrollY);
}, 200);

window.addEventListener("scroll", throttledScrollHandler);
```

## Memoization -- Caching Results by Input

--> A memoized function remembers the results of previous calls, and if called again with the SAME arguments, returns the cached result instantly instead of recomputing -- most valuable for expensive, pure (deterministic) computations, revisited more formally in the Pure Functions file since memoization only works correctly on functions without side effects.

```javascript
function memoize(fn) {
  const cache = new Map();
  return function (...args) {
    const key = JSON.stringify(args);
    if (cache.has(key)) {
      return cache.get(key);
    }
    const result = fn(...args);
    cache.set(key, result);
    return result;
  };
}

function slowSquare(n) {
  for (let i = 0; i < 1e8; i++) {}   // Simulate expensive work
  return n * n;
}

const fastSquare = memoize(slowSquare);
fastSquare(5);   // Slow the first time
fastSquare(5);   // Instant -- returned from cache
```

## Once -- Guaranteeing a Function Runs Only a Single Time

```javascript
function once(fn) {
  let called = false;
  let result;
  return function (...args) {
    if (!called) {
      called = true;
      result = fn(...args);
    }
    return result;
  };
}

const initialize = once(() => {
  console.log("Initializing application...");
  return { status: "ready" };
});

initialize();   // Logs and runs
initialize();   // Does nothing, silently returns the cached result -- safe to call defensively as many times as needed
```

# Higher-Order Functions and Array Methods -- Why They're the Same Idea

--> `.map()`, `.filter()`, `.reduce()`, `.forEach()`, `.sort()` (with a comparator), and `.find()` are ALL higher-order functions built into `Array.prototype` -- each one takes a callback describing exactly what varies about that specific use, while the array method itself handles the invariant looping/accumulation mechanics.

```javascript
[1, 2, 3, 4, 5].map(n => n * 2);              // HOF: varies the transform, fixed: applies to every element
[1, 2, 3, 4, 5].filter(n => n % 2 === 0);       // HOF: varies the condition, fixed: keeps matching elements
[1, 2, 3, 4, 5].reduce((acc, n) => acc + n, 0);  // HOF: varies the combining logic, fixed: accumulates left-to-right
```

--> Understanding HOFs as a general concept -- rather than memorizing `.map()`/`.filter()`/`.reduce()` as three unrelated, special methods -- is what lets you recognize (and confidently write) the SAME underlying pattern anywhere else it shows up: middleware chains in Express (covered in the Full Stack Backend notes), React's `useState`/`useEffect` accepting functions, and the debounce/throttle/memoize utilities above.

# Common Pitfalls With Higher-Order Functions

--> **Forgetting to actually return a value from a returned function** -- an easy mistake when a function factory's inner function has multiple statements and the `return` is accidentally omitted, silently producing `undefined` everywhere the factory's output is used.
--> **Losing `this` context** -- passing a method as a bare callback (`array.map(obj.method)`) detaches it from `obj`, exactly the problem covered in depth in the `this`/`call`/`apply`/`bind` file -- a very common bug when mixing HOFs with class methods, usually fixed with an arrow function wrapper (`array.map(x => obj.method(x))`) or `.bind()`.
--> **Overusing memoization** -- caching every function call's result unconditionally can silently grow memory usage indefinitely for functions called with many different arguments over a long-running program's lifetime; a production-grade memoizer typically needs some eviction strategy (a maximum cache size, or a TTL), not just an ever-growing `Map`.
