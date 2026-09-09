# JavaScript Practical — Index

Top-level practical files for the JavaScript Theory chapters. Each file is
self-contained, runnable, and commented. The `Code with Harshit` subfolder is
a separate personal course-log and is out of scope for this index.

| File | Theory chapter(s) covered | How to run |
|---|---|---|
| `01-arrays-and-array-methods.js` | Ch. 2 (Arrays/Objects/Collections), Ch. 5 (Array Methods) | `node 01-arrays-and-array-methods.js` |
| `02-closures-and-higher-order-functions.js` | Ch. 18 (Closures), Ch. 23 (Higher-Order Functions), Ch. 22 (Function Composition — module-pattern angle) | `node 02-closures-and-higher-order-functions.js` |
| `03-async-patterns.js` | Ch. 8 (Async JS / Generators) | `node 03-async-patterns.js` (Node 18+; needs network access for the AbortController demo, but degrades gracefully offline) |
| `04-oop-and-prototypes.js` | Ch. 6 (OOP / Prototypes) | `node 04-oop-and-prototypes.js` |
| `05-error-handling-and-custom-errors.js` | Ch. 13 (Advanced Data Types / Error Handling) | `node 05-error-handling-and-custom-errors.js` |
| `06-dom-and-events.html` | Ch. 7 (Events / DOM) | Open directly in a browser (double-click or File > Open) — needs an HTML page, not Node. Open DevTools console for extra log detail. |
| `07-regex-practical-validators.js` | Ch. 12 (Regular Expressions) | `node 07-regex-practical-validators.js` |
| `08-functional-composition-and-immutability.js` | Ch. 24 (Function Composition / Currying), Ch. 25 (Immutability / Pure Functions) | `node 08-functional-composition-and-immutability.js` (Node 17+ for `structuredClone`) |

## Notes

- All Node-runnable files also work if pasted directly into a browser
  console, since none of them depend on Node-only APIs (only the standard
  `fetch`, `AbortController`, `structuredClone`, timers, etc., which exist in
  both environments).
- `03-async-patterns.js` requires internet access for its AbortController
  section; if offline, that section logs a clear fallback message instead of
  failing silently, and the rest of the file still runs.
- Every console.log in every file is labeled (e.g. `[retry]`, `[memoize]`,
  `[delegation]`) so output is self-explanatory without reading the source.
- Modern JS features used throughout: `??=`/`??`, `Array.prototype.at`,
  `structuredClone`, optional chaining (`?.`), private class fields (`#`),
  `Error` cause chaining, top-level `for await...of`.
