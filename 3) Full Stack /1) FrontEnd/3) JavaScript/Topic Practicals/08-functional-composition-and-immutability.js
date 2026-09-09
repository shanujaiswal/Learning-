/**
 * 08-functional-composition-and-immutability.js
 * HOW TO RUN: plain Node.js -> `node 08-functional-composition-and-immutability.js`
 * (Uses structuredClone, built into Node 17+ and modern browsers.
 * Also runs fine pasted into a browser console.)
 *
 * Covers (Theory folder):
 *  - Chapter 24: Function Composition / Currying
 *  - Chapter 25: Immutability / Pure Functions
 *
 * Demonstrates:
 *  1. pipe / compose utilities.
 *  2. A curry implementation.
 *  3. Immutable update patterns on nested objects, both by hand and via a
 *     naive Immer-style `produce` mini-implementation.
 */

"use strict";

// ===========================================================================
// PART 1: pipe / compose utilities.
// pipe(f, g, h)(x)   === h(g(f(x)))   -- left to right, reads like a pipeline
// compose(f, g, h)(x) === f(g(h(x)))  -- right to left, classic math notation
// ===========================================================================
const pipe = (...fns) => (initialValue) => fns.reduce((value, fn) => fn(value), initialValue);
const compose = (...fns) => (initialValue) => fns.reduceRight((value, fn) => fn(value), initialValue);

const double = (n) => n * 2;
const addTen = (n) => n + 10;
const square = (n) => n * n;

console.log("=== pipe vs compose ===");
const pipedFn = pipe(double, addTen, square); // square(addTen(double(5)))
const composedFn = compose(square, addTen, double); // same order of operations, written the other way

console.log(`pipe(double, addTen, square)(5)    = ${pipedFn(5)}  (expect square(addTen(double(5))) = square(20) = 400)`);
console.log(`compose(square, addTen, double)(5) = ${composedFn(5)}  (same computation, opposite reading order)`);

// A more realistic pipeline: normalize -> validate-ish transform -> format, on a string.
const trim = (s) => s.trim();
const toLowerCase = (s) => s.toLowerCase();
const collapseSpaces = (s) => s.replace(/\s+/g, " ");
const titleCase = (s) => s.replace(/\b\w/g, (c) => c.toUpperCase());

const normalizeName = pipe(trim, toLowerCase, collapseSpaces, titleCase);
console.log(`\nnormalizeName("   jOHN   miDDLE   doe  ") -> "${normalizeName("   jOHN   miDDLE   doe  ")}"`);

// ===========================================================================
// PART 2: Curry implementation.
// A generic curry() that lets any function be called with arguments spread
// across multiple invocations, only actually running once enough args exist.
// ===========================================================================
function curry(fn) {
  const arity = fn.length;
  return function curried(...args) {
    if (args.length >= arity) {
      return fn(...args);
    }
    // Not enough args yet - return a function that collects the rest.
    return (...moreArgs) => curried(...args, ...moreArgs);
  };
}

const addThree = (a, b, c) => a + b + c;
const curriedAddThree = curry(addThree);

console.log("\n=== Curry implementation ===");
console.log(`curriedAddThree(1)(2)(3)   = ${curriedAddThree(1)(2)(3)}`);
console.log(`curriedAddThree(1, 2)(3)   = ${curriedAddThree(1, 2)(3)}`);
console.log(`curriedAddThree(1)(2, 3)   = ${curriedAddThree(1)(2, 3)}`);
console.log(`curriedAddThree(1, 2, 3)   = ${curriedAddThree(1, 2, 3)}`);

// Practical use: partially apply a curried validator/formatter.
const formatCurrency = curry((symbol, decimals, amount) => `${symbol}${amount.toFixed(decimals)}`);
const formatUSD = formatCurrency("$", 2); // partially applied - symbol and decimals fixed
console.log(`\nformatUSD(49.9)  -> "${formatUSD(49.9)}"`);
console.log(`formatUSD(1250)  -> "${formatUSD(1250)}"`);

// ===========================================================================
// PART 3: Immutable update patterns on nested objects.
// ===========================================================================
console.log("\n=== Immutable updates: by hand (spread + structuredClone) ===");

const originalState = {
  user: { id: 1, name: "Asha Rao", address: { city: "Pune", zip: "411001" } },
  cart: { items: [{ sku: "SKU-1", qty: 2 }], total: 1598 },
};

// (a) Shallow-safe update using spread at every nested level we touch.
const updatedStateByHand = {
  ...originalState,
  user: {
    ...originalState.user,
    address: {
      ...originalState.user.address,
      city: "Mumbai", // only this leaf actually changes
    },
  },
};

console.log(`Original city: ${originalState.user.address.city}`);
console.log(`Updated city:  ${updatedStateByHand.user.address.city}`);
console.log(
  `Original state untouched? ${originalState.user.address.city === "Pune"} ` +
    `(mutation-free - originalState was never modified)`
);
console.log(`Same object reference reused where nothing changed? cart: ${updatedStateByHand.cart === originalState.cart}`);

// (b) structuredClone (ES2022+ global) for a full deep clone before mutating
// a throwaway copy - useful when the update touches many scattered leaves.
console.log("\n=== Immutable updates: structuredClone + mutate-the-clone ===");
const clonedState = structuredClone(originalState);
clonedState.cart.items.push({ sku: "SKU-2", qty: 1 }); // safe: this is a deep clone
clonedState.cart.total += 299;

console.log(`Original cart items count: ${originalState.cart.items.length}`);
console.log(`Cloned cart items count:   ${clonedState.cart.items.length}`);
console.log(`Original left untouched?   ${originalState.cart.items.length === 1}`);

// ===========================================================================
// (c) Naive Immer-style `produce` mini-implementation.
// Real Immer uses Proxies to track mutations transparently and only clones
// the paths that were actually touched. This mini version keeps the same
// ergonomic API (`produce(state, draft => { mutate draft })`) but implements
// it simply via structuredClone + a plain mutation callback, trading some of
// Immer's copy-on-write efficiency for clarity.
// ===========================================================================
function produce(baseState, recipe) {
  const draft = structuredClone(baseState); // deep clone to mutate freely
  recipe(draft); // caller "mutates" the draft directly - feels natural
  return draft; // return the new, independent state
}

console.log("\n=== Naive Immer-style produce() ===");
const nextState = produce(originalState, (draft) => {
  draft.user.address.city = "Bengaluru";
  draft.cart.items.push({ sku: "SKU-3", qty: 3 });
  draft.cart.total += 450;
});

console.log(`Original city: ${originalState.user.address.city} (unchanged)`);
console.log(`produce() result city: ${nextState.user.address.city}`);
console.log(`Original cart items: ${originalState.cart.items.length} (unchanged)`);
console.log(`produce() result cart items: ${nextState.cart.items.length}`);
console.log(`originalState === nextState? ${originalState === nextState} (always false - a new object every time)`);
