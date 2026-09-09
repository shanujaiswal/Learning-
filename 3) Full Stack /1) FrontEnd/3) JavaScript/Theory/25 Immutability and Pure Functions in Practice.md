# What Makes a Function "Pure"

--> A pure function satisfies two conditions: **(1) given the same inputs, it always returns the same output**, and **(2) it has no observable side effects** -- it doesn't modify anything outside itself (no mutating an argument, no changing a global variable, no writing to a file/database, no logging, no network calls). Every other higher-order function pattern covered in this pair of files (composition, currying, memoization) relies on the functions involved being pure, or at least behaving predictably enough to be treated as such.

```javascript
// Pure -- same input always produces same output, touches nothing outside itself
function add(a, b) {
  return a + b;
}

// Impure -- depends on external, changing state (Date.now()), not just its arguments
function getGreeting(name) {
  const hour = new Date().getHours();
  return hour < 12 ? `Good morning, ${name}` : `Good day, ${name}`;
}

// Impure -- has a side effect (mutates the array argument passed in)
function addItem(cart, item) {
  cart.push(item);   // Mutates the caller's array directly
  return cart;
}
```

# Why Purity Matters -- Predictability and Testability

--> A pure function is trivially testable -- call it with an input, assert on the output, done. No need to set up mocks for external state, no need to worry about test order affecting results, no need to reset anything between test runs.
--> A pure function is safely memoizable (covered in the Higher-Order Functions file) -- memoization assumes "same input → same output," which is only guaranteed to hold for a pure function; memoizing an impure function (like `getGreeting` above) would return stale, wrong results once the actual current time changes.
--> A pure function is safe to run in any order, or even in parallel, since it can't interfere with anything else by side effect -- a property that becomes increasingly valuable as an application's logic grows more concurrent (connecting to the Async JavaScript file's concerns about ordering and timing).

# Side Effects Aren't "Bad" -- They're Just Not Pure

--> Every real application NEEDS side effects eventually -- rendering to the screen, saving to a database, making an API call, are all side effects, and a program that never had any side effect would be entirely useless (it would compute things and never let anyone observe the result). The goal of functional programming isn't eliminating side effects entirely, but ISOLATING them -- keeping the core business logic pure and pushing side effects to the edges of the program (event handlers, a dedicated "effects" layer), a philosophy visible directly in React's `useEffect` Hook naming and design, covered in the React Hooks file.

# Mutation vs Immutability

--> Mutation means changing an existing object/array IN PLACE. Immutability means never changing existing data -- instead, creating and returning a NEW copy with the desired change applied, leaving the original completely untouched.

```javascript
// Mutating (changes the original array)
function addToCartMutable(cart, item) {
  cart.push(item);
  return cart;
}

const cart1 = ["apple"];
const cart2 = addToCartMutable(cart1, "banana");
console.log(cart1);   // ["apple", "banana"] -- the ORIGINAL was changed too, possibly unexpectedly for other code holding a reference to it

// Immutable (returns a new array, original untouched)
function addToCartImmutable(cart, item) {
  return [...cart, item];
}

const cart3 = ["apple"];
const cart4 = addToCartImmutable(cart3, "banana");
console.log(cart3);   // ["apple"] -- untouched
console.log(cart4);   // ["apple", "banana"] -- the new result
```

# Why Immutability Matters in Practice

--> **Avoiding "spooky action at a distance"** -- if a function mutates an object passed into it, ANY other part of the program holding a reference to that same object is silently affected too -- a notoriously hard class of bug to track down, since the mutation could happen far away from where the bug's symptom is observed.
--> **React's rendering model depends on it directly** -- React (covered extensively in its own folder) decides whether to re-render a component largely by checking if props/state REFERENCES have changed (`===` comparison, extremely fast) rather than deeply comparing every value -- mutating state directly means the reference stays the same, and React may not realize anything changed at all, a very common source of "my UI isn't updating" bugs for React beginners.

```javascript
// WRONG in React -- mutates existing state, React may not detect the change
function addTodo(todos, newTodo) {
  todos.push(newTodo);
  setTodos(todos);   // Same array reference as before -- React might skip re-rendering
}

// CORRECT -- creates a new array reference, React reliably detects the change
function addTodo(todos, newTodo) {
  setTodos([...todos, newTodo]);
}
```

# Common Immutable Update Patterns

## Arrays

```javascript
const arr = [1, 2, 3];

const added = [...arr, 4];                          // Add to the end
const addedFront = [0, ...arr];                       // Add to the beginning
const removed = arr.filter(n => n !== 2);              // Remove an item
const updated = arr.map(n => (n === 2 ? 20 : n));       // Update an item
const inserted = [...arr.slice(0, 1), 1.5, ...arr.slice(1)];   // Insert in the middle
```

## Objects

```javascript
const user = { name: "Alice", age: 30 };

const updated = { ...user, age: 31 };                    // Update one field, keep the rest
const withNewField = { ...user, email: "a@example.com" };   // Add a new field
const { age, ...withoutAge } = user;                        // Remove a field (destructure it out)
```

## Nested Structures -- Where It Gets Harder

```javascript
const state = {
  user: { name: "Alice", address: { city: "NYC", zip: "10001" } }
};

// Updating a deeply nested field immutably requires spreading at EVERY level along the path
const updated = {
  ...state,
  user: {
    ...state.user,
    address: {
      ...state.user.address,
      city: "Boston"
    }
  }
};
```

--> This nested-spreading pattern is verbose and error-prone to write by hand as nesting grows -- exactly the problem libraries like Immer (commonly paired with Redux, covered in the React Redux file) solve, letting you write code that LOOKS like direct mutation but actually produces a properly immutable update behind the scenes.

```javascript
import { produce } from "immer";

const updated = produce(state, (draft) => {
  draft.user.address.city = "Boston";   // Looks like mutation, but Immer produces a new immutable object
});
```

# Object.freeze -- Enforcing Immutability at Runtime

--> `Object.freeze()` makes an object's top-level properties genuinely unchangeable -- attempting to mutate a frozen object silently fails (or throws, in strict mode) rather than succeeding unexpectedly.

```javascript
const config = Object.freeze({ apiUrl: "https://api.example.com", timeout: 5000 });
config.timeout = 10000;   // Silently fails (or throws in strict mode) -- config.timeout is still 5000
```

--> **Important limitation** -- `Object.freeze()` is SHALLOW -- it only protects the object's immediate properties, not nested objects within it. A frozen object's nested object can still be mutated freely unless it's ALSO explicitly frozen.

```javascript
const state = Object.freeze({ user: { name: "Alice" } });
state.user.name = "Bob";     // This WORKS -- "user" itself wasn't frozen, only the outer object was
console.log(state.user.name);  // "Bob"
```

# The Performance Trade-off -- Immutability Isn't Free

--> Creating new copies instead of mutating in place does have a real computational and memory cost, especially for large arrays/objects updated frequently -- this is a genuine trade-off, not a free lunch. In practice, the predictability and debuggability benefits (covered above, and directly enabling React's fast reference-equality checks) are judged worth that cost for most application state, while performance-critical inner loops (heavy numeric computation, covered in the Data Science and AI folder's NumPy content) often deliberately favor mutation for speed, since that code's correctness doesn't depend on the same "who else might be holding a reference to this" concerns that make immutability valuable at the application-state level.
