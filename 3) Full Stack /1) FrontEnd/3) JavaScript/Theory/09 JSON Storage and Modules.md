# JSON

--> JSON.stringify(obj) --> Converts a JS object/array into a JSON string
--> JSON.parse(str) --> Converts a JSON string back into a JS object/array
--> Commonly used for sending/receiving data via APIs and storing data in localStorage

# Object Methods

--> Object.keys(obj) --> Returns an array of an object's keys
--> Object.values(obj) --> Returns an array of an object's values
--> Object.entries(obj) --> Returns an array of \[key, value\] pairs
--> Object.assign(target, source) --> Copies properties from source object(s) into target
--> Object.freeze(obj) --> Prevents adding, removing, or modifying properties

# Modules (import / export)

--> Allows splitting code into separate reusable files
--> export default / export { name } to expose values from a file
--> import name from "./file.js" to bring values into another file
--> Helps with code organization and avoiding global namespace pollution
--> Named vs default exports --> a file can have multiple named exports (export { a, b }, imported with matching names in {}) but only ONE default export (export default x, imported with any name, no braces)
--> Dynamic import() --> import("./file.js").then(module => ...) loads a module at runtime instead of at the top of the file -- returns a Promise, useful for code-splitting/lazy-loading (e.g. loading a module only when a button is clicked)

# LocalStorage & SessionStorage

--> Web Storage APIs to store key-value data in the browser
--> localStorage --> Data persists even after the browser is closed
--> sessionStorage --> Data is cleared when the tab/browser is closed
--> Only stores strings, so objects need JSON.stringify()/JSON.parse()

# Cookies (Basics and Limitations)

--> Cookies are small pieces of data (key=value pairs) stored in the browser and automatically sent to the server with every matching HTTP request -- unlike localStorage/sessionStorage, which stay in the browser and are never sent over the network automatically.
--> Set/read from JavaScript via `document.cookie` -- a single string of `key=value; key2=value2` pairs, awkward to work with directly (usually wrapped in a small helper function or a library).
--> Key attributes: `expires`/`max-age` (when the cookie is deleted), `path` (which URL paths it's sent to), `Secure` (only sent over HTTPS), `HttpOnly` (not accessible to JavaScript at all -- set by the server, protects against XSS reading it), `SameSite` (controls whether it's sent on cross-site requests, protects against CSRF).
--> Limitations compared to localStorage/sessionStorage: much smaller size limit (~4KB per cookie vs 5-10MB for Web Storage), sent with EVERY request to the matching domain (adds overhead to every network call), and plain `document.cookie` cookies (without `HttpOnly`) are readable by any JS on the page, including malicious injected scripts.
--> Common use case today: session/auth tokens (especially `HttpOnly` cookies set by the server), since Web Storage can't be marked HttpOnly and is fully exposed to any JS running on the page.

# Storage Events and Cross-Tab Communication

--> The `storage` event fires on the `window` object of OTHER open tabs/windows (same origin) whenever localStorage is changed in one of them -- it does NOT fire in the tab that made the change itself.
--> Useful for keeping multiple open tabs of the same app in sync (e.g. logging out in one tab should log out all open tabs).

```javascript
window.addEventListener("storage", (event) => {
  console.log(event.key);       // Which key changed
  console.log(event.oldValue);   // Previous value
  console.log(event.newValue);   // New value
  console.log(event.url);        // URL of the tab that made the change
});

// In another tab, this triggers the "storage" listener above in all OTHER open tabs:
localStorage.setItem("theme", "dark");
```
--> BroadcastChannel API -- a more general alternative for cross-tab communication, letting tabs send arbitrary messages to each other directly (not just reacting to storage changes).

# Debounce and Throttle

--> Debounce --> Delays execution of a function until after a certain time has passed since it was last called (e.g. search input)
--> Throttle --> Ensures a function runs at most once every specified time interval (e.g. scroll/resize events)
--> Both used to improve performance by limiting how often a function runs

--> Debounce example:
function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

--> Throttle example:
function throttle(fn, limit) {
  let waiting = false;
  return (...args) => {
    if (!waiting) {
      fn(...args);
      waiting = true;
      setTimeout(() => (waiting = false), limit);
    }
  };
}

# Deep Dive -- JSON.stringify Gotchas

--> `JSON.stringify` silently DROPS certain values rather than erroring -- `undefined`, functions, and `Symbol` values are simply omitted from object properties (or converted to `null` inside arrays), which can produce confusing, silently-incomplete serialized data if not anticipated.

```javascript
const data = { name: "Alice", greet: () => {}, id: undefined, tag: Symbol("x") };
console.log(JSON.stringify(data));   // '{"name":"Alice"}' -- greet, id, and tag all vanished silently

console.log(JSON.stringify([1, undefined, function(){}]));   // '[1,null,null]' -- in arrays, they become null instead of being dropped
```

--> `JSON.stringify` also throws a `TypeError` on circular references (an object that contains a reference back to itself, directly or indirectly) -- `structuredClone()` (covered in the Arrays Objects and Collections file's shallow vs deep copy section) handles circular references correctly, making it the safer choice for deep-cloning genuinely complex object graphs.
--> The lesser-known second and third arguments -- `JSON.stringify(obj, replacerFn, indentSpaces)` -- a replacer function can filter/transform values during serialization, and a number for `indentSpaces` (e.g. `JSON.stringify(obj, null, 2)`) pretty-prints the output with readable indentation, commonly used when logging or writing JSON to a file for human inspection.

# Deep Dive -- ES Modules and Tree-Shaking

--> ES Modules' STATIC structure (imports/exports must be declared at the top level, not conditionally computed at runtime) is precisely what allows build tools (Webpack/Vite, referenced in the React Code Splitting and DevOps notes) to perform "tree-shaking" -- analyzing the import graph ahead of time and eliminating exported code that's never actually imported anywhere, reducing final bundle size.

```javascript
// utils.js -- exports several functions
export function usedFunction() { /* ... */ }
export function neverImportedFunction() { /* ... */ }   // Tree-shaken out of the final bundle entirely

// app.js
import { usedFunction } from "./utils.js";   // Only this one is ever referenced
```

--> This static analysis is NOT possible with the older CommonJS module system (`require()`/`module.exports`, covered in the Node.js Fundamentals file), since `require()` calls can be conditional/dynamic at runtime -- a bundler generally can't safely determine ahead of time whether a given export will actually be used, which is precisely why ES Modules are the modern default for browser-targeted, bundled frontend code.
