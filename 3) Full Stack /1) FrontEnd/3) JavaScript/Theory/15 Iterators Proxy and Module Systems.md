# Iterator Protocol

--> An object is an "iterator" if it has a .next() method that returns { value, done }.
--> An object is "iterable" if it implements Symbol.iterator, a method that returns an iterator -- this is what lets for...of, spread (...), and destructuring work on it.
--> Arrays, Strings, Maps, and Sets all have a built-in Symbol.iterator implementation.

# Custom Iterables

--> A plain object can be made iterable by manually defining [Symbol.iterator]() on it.
--> const range = { from: 1, to: 3, [Symbol.iterator]() { let cur = this.from, last = this.to; return { next() { return cur <= last ? { value: cur++, done: false } : { done: true } } } } }
--> for (const n of range) { ... } now works even though range is a plain object.
--> Generators (function*, see Async JavaScript notes) are the easier way to write a custom iterator -- yield automatically produces the {value, done} sequence.

# Async Iterators and for await...of

--> An object is an "async iterable" if it implements Symbol.asyncIterator, a method returning an async iterator whose .next() returns a Promise resolving to { value, done }.
--> for await...of consumes an async iterable one resolved value at a time -- e.g. iterating over an async generator (see file 08 Async JavaScript and Generators for yield*/async generators in depth).

# Proxy

--> new Proxy(target, handler) creates a wrapper around an object that lets you intercept and customize fundamental operations (get, set, delete, etc.) on it.
--> const p = new Proxy(obj, { get(target, prop) { console.log("read", prop); return target[prop] } })
--> handler traps: get, set, has, deleteProperty, and more -- each corresponds to an operation on the object.
--> Used for validation, logging/debugging, reactive frameworks (e.g. Vue's reactivity system is built on Proxy), and default values for missing properties.
--> Proxy.revocable(target, handler) --> creates a { proxy, revoke } pair -- calling revoke() permanently disables the proxy (further operations throw), useful for revoking access to an object on demand.

# Reflect

--> A built-in object providing methods that mirror the internal operations Proxy can intercept (Reflect.get, Reflect.set, Reflect.has, Reflect.deleteProperty, etc.).
--> Commonly used inside a Proxy handler to forward an operation to the original target after custom logic runs: get(target, prop) { return Reflect.get(target, prop) }
--> Provides a more reliable/functional way to perform operations that otherwise rely on operators (like delete or in), useful for generic/meta-programming code.

# CommonJS vs ES Modules

--> CommonJS (CJS) --> Node.js's original module system. require("./file") to import, module.exports = value / exports.name = value to export. Synchronous, loaded at runtime.
--> ES Modules (ESM) --> The standard JavaScript module system. import x from "./file.js" to import, export default / export { name } to export. Can be statically analyzed (imports are known before running), which enables tree-shaking.
--> Node.js supports both -- .mjs files (or "type": "module" in package.json) use ESM, .cjs files (or default) use CommonJS.
--> Browsers natively support ESM via <script type="module">; CommonJS only runs in Node or after being bundled.

# Bundlers (brief)

--> Tools like Webpack, Vite, and esbuild take multiple module files and combine them into optimized bundle(s) for the browser.
--> Tree-shaking --> removing unused exported code from the final bundle, only possible because ESM's static import/export structure can be analyzed ahead of time.
--> Also typically handle transpiling modern JS/JSX (via Babel/SWC) down to a version older browsers can run, and code-splitting bundles by route (see React.lazy in the React notes).

# Deep Dive -- Writing a Custom Iterable With a Generator

--> Generators (`function*`) are the far easier way to implement `Symbol.iterator` compared to the manual `{next() {...}}` object shown above -- `yield` automatically produces the `{value, done}` sequence the iterator protocol expects.

```javascript
const range = {
  from: 1,
  to: 5,
  [Symbol.iterator]: function* () {
    for (let i = this.from; i <= this.to; i++) {
      yield i;
    }
  }
};

console.log([...range]);        // [1, 2, 3, 4, 5] -- spread works because range is now properly iterable
for (const n of range) console.log(n);   // for...of works too
```

# Deep Dive -- Practical Proxy Use Cases

--> **Validation** -- intercepting `set` to reject invalid assignments before they ever happen, rather than validating after the fact.

```javascript
const validatedUser = new Proxy({}, {
  set(target, prop, value) {
    if (prop === "age" && (typeof value !== "number" || value < 0)) {
      throw new TypeError("age must be a non-negative number");
    }
    target[prop] = value;
    return true;   // Must return true to indicate the set succeeded
  }
});

validatedUser.age = -5;   // Throws immediately, before the invalid value is ever stored
```

--> **Default values for missing properties** -- intercepting `get` to return a fallback instead of `undefined`.

```javascript
const withDefaults = new Proxy({}, {
  get(target, prop) {
    return prop in target ? target[prop] : `No value set for "${prop}"`;
  }
});
console.log(withDefaults.name);   // 'No value set for "name"' -- instead of undefined
```

--> This exact `get`/`set` trap mechanism is precisely how Vue's reactivity system (covered in the Other Frontend Frameworks file) automatically detects when reactive state changes, without requiring an explicit `setState()` call the way React does.

# Deep Dive -- CommonJS/ESM Interop Gotchas

--> Mixing CommonJS and ES Modules in the same project is a genuinely common source of confusion -- `require()`-ing an ES Module, or `import`-ing a CommonJS module, doesn't always behave exactly as expected, since the two systems have fundamentally different loading semantics (CJS is synchronous and dynamic, ESM is static and can be asynchronous).

```javascript
// A CommonJS module being imported into an ESM file generally works, but its default export
// is the ENTIRE module.exports object, not necessarily what you'd expect from a named import:
import pkg from "some-old-commonjs-package";
const { specificFunction } = pkg;   // Often needed, rather than a direct named import
```

--> Node.js determines which system a file uses based on its extension (`.mjs` = ESM, `.cjs` = CommonJS) or the `"type"` field in the nearest `package.json` -- a package intended for both environments typically ships "dual" builds with separate entry points, configured via the `"exports"` field in `package.json`, specifically to avoid forcing consumers into one module system or the other.
