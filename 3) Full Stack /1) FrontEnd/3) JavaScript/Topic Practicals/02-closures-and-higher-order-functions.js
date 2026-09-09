/**
 * 02-closures-and-higher-order-functions.js
 * HOW TO RUN: plain Node.js -> `node 02-closures-and-higher-order-functions.js`
 * (No DOM APIs used; setTimeout is available in both Node and browsers.)
 *
 * Covers (Theory folder):
 *  - Chapter 18: Closures
 *  - Chapter 23: Higher-Order Functions
 *  - Chapter 22: Function Composition / Currying (module-pattern angle here;
 *    dedicated composition/currying demo lives in 08-functional-composition-and-immutability.js)
 */

"use strict";

// ===========================================================================
// PART 1: Memoization higher-order function built with closures.
// A HOF that takes a (possibly expensive) function and returns a wrapped
// version that caches results per unique argument list, using a closure
// over a private `cache` Map.
// ===========================================================================
function memoize(fn) {
  const cache = new Map(); // private state, kept alive only via closure

  return function memoized(...args) {
    const key = JSON.stringify(args);
    if (cache.has(key)) {
      console.log(`[memoize] cache HIT for args=${key}`);
      return cache.get(key);
    }
    console.log(`[memoize] cache MISS for args=${key} -> computing...`);
    const result = fn(...args);
    cache.set(key, result);
    return result;
  };
}

// A deliberately slow function to prove memoization matters.
function slowFibonacci(n) {
  if (n <= 1) return n;
  return slowFibonacci(n - 1) + slowFibonacci(n - 2);
}

const memoFibonacci = memoize(slowFibonacci);

console.log("=== Memoization demo ===");
console.time("first call (n=28)");
console.log(`Result: ${memoFibonacci(28)}`);
console.timeEnd("first call (n=28)");

console.time("second call (n=28, should be instant via cache)");
console.log(`Result: ${memoFibonacci(28)}`);
console.timeEnd("second call (n=28, should be instant via cache)");

// ===========================================================================
// PART 2: Debounce and throttle implementations.
// Both are HOFs that close over timing state (timeoutId / lastRun) to control
// how often an inner function actually executes.
// ===========================================================================
function debounce(fn, delayMs) {
  let timeoutId; // closed over between calls
  return function debounced(...args) {
    clearTimeout(timeoutId);
    timeoutId = setTimeout(() => fn(...args), delayMs);
  };
}

function throttle(fn, intervalMs) {
  let lastRunAt = 0; // closed over between calls
  return function throttled(...args) {
    const now = Date.now();
    if (now - lastRunAt >= intervalMs) {
      lastRunAt = now;
      fn(...args);
    } else {
      console.log(`[throttle] skipped call (only ${now - lastRunAt}ms since last run)`);
    }
  };
}

console.log("\n=== Debounce demo (simulating rapid keystrokes) ===");
const debouncedSearch = debounce((query) => {
  console.log(`[debounce] Searching for: "${query}" (only the LAST call within the window fires)`);
}, 200);

// Simulate a user typing quickly - only the final call should actually run.
["r", "re", "rea", "reac", "react"].forEach((partial, i) => {
  setTimeout(() => debouncedSearch(partial), i * 50);
});

console.log("\n=== Throttle demo (simulating a scroll/resize flood) ===");
const throttledLog = throttle((x) => {
  console.log(`[throttle] Handling event at x=${x}`);
}, 150);

for (let i = 0; i < 6; i++) {
  setTimeout(() => throttledLog(i), i * 60);
}

// ===========================================================================
// PART 3: Private-counter module pattern via closures (no classes needed).
// The returned object's methods all share access to a variable (`count`)
// that is otherwise completely inaccessible from the outside.
// ===========================================================================
function createCounter(initialValue = 0, step = 1) {
  let count = initialValue; // truly private - no way to reach this from outside

  return Object.freeze({
    increment() {
      count += step;
      return count;
    },
    decrement() {
      count -= step;
      return count;
    },
    reset() {
      count = initialValue;
      return count;
    },
    get value() {
      return count;
    },
  });
}

setTimeout(() => {
  console.log("\n=== Private counter module pattern demo ===");
  const counter = createCounter(10, 5);
  console.log(`Initial value: ${counter.value}`);
  console.log(`After increment(): ${counter.increment()}`);
  console.log(`After increment(): ${counter.increment()}`);
  console.log(`After decrement(): ${counter.decrement()}`);
  console.log(`After reset(): ${counter.reset()}`);
  console.log(`Direct access to internal 'count' variable? Impossible: counter.count = ${counter.count}`);
}, 500);
