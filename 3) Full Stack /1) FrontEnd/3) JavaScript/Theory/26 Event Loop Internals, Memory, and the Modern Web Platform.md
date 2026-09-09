# Microtasks Beyond Promises -- queueMicrotask

--> `Promise.then()` callbacks aren't the only way to schedule a microtask -- `queueMicrotask(callback)` schedules a callback directly on the SAME microtask queue, without needing to create/resolve a Promise first (see file 08 Async JavaScript and Generators for the base event loop model -- call stack -> microtask queue -> one macrotask -> repeat).
--> Useful when you want "run this as soon as possible, but after the current synchronous code finishes" semantics without the overhead/indirection of wrapping something in a Promise just to get `.then()` timing.

```javascript
console.log("1");
queueMicrotask(() => console.log("2: microtask"));
Promise.resolve().then(() => console.log("3: promise microtask"));
console.log("4");
// Output: 1, 4, 2: microtask, 3: promise microtask
// Both queueMicrotask and .then() share the exact same queue -- they run in the order they were scheduled
```

--> A common real use -- normalizing whether a callback-based API resolves synchronously or asynchronously, so callers can always rely on it never running before the current call stack finishes (avoids "sometimes sync, sometimes async" bugs, often called a "Zalgo" API).

# Where rAF and requestIdleCallback Fit in the Queue Order

--> These are NOT microtasks and not ordinary macrotasks either -- they're tied to the browser's rendering pipeline specifically (see file 17 Web Performance and Debugging Tools for the render phases: Style -> Layout -> Paint -> Composite).
--> Full per-frame order: run synchronous code -> drain ALL microtasks -> run `requestAnimationFrame` callbacks (right before the browser paints) -> paint -> if there's spare idle time before the next frame, run `requestIdleCallback` callbacks -> then the event loop picks up the next macrotask (e.g. a `setTimeout`).

```javascript
setTimeout(() => console.log("macrotask"), 0);
requestAnimationFrame(() => console.log("rAF -- right before paint"));
requestIdleCallback(() => console.log("idle callback -- only if spare time exists"));
queueMicrotask(() => console.log("microtask -- always first"));
// Typical order: microtask -> rAF -> (paint) -> idle callback -> macrotask
// The exact rAF/idle/macrotask ordering can shift depending on frame timing --
// microtasks ALWAYS run first, that part is guaranteed.
```

--> `requestIdleCallback` is not supported in Safari and has no ironclad timing guarantee -- it may not run at all if the browser stays busy, so it's only appropriate for genuinely optional work (analytics, prefetching), never anything the page depends on completing.

# Starving the Event Loop -- Microtask Infinite Loops

--> Because the event loop drains the ENTIRE microtask queue before doing anything else (including rendering), a microtask that keeps re-scheduling itself can freeze the page just as badly as a synchronous infinite loop -- the browser never gets a chance to paint or respond to input.

```javascript
function recurse() {
  queueMicrotask(recurse);   // Never lets the macrotask/render phase run -- page appears frozen
}
```

--> This is a subtle failure mode -- unlike a blocking `while(true)`, DevTools may still show the page as "responsive" for a moment since technically the stack keeps unwinding, but no paint or macrotask ever gets a turn.

# Garbage Collection -- Mark-and-Sweep

--> JavaScript engines (V8, SpiderMonkey, etc.) use automatic garbage collection -- you never manually free memory, the engine reclaims it once it determines an object is unreachable.
--> Mark-and-sweep is the core algorithm -- starting from "roots" (global object, currently executing call stack), the engine marks every object reachable by following references. Anything left unmarked after that walk is unreachable and gets swept (freed).
--> Reachability, not reference counting, is what determines whether something is collected -- an object can still be "referenced" by another unreachable object and still get collected, since reference counting alone can't detect circular references between two otherwise-unreachable objects.

# Generational GC

--> V8 splits the heap into a "young generation" (new, small objects) and an "old generation" (long-lived objects that survived several collection cycles).
--> Most objects are short-lived (temporary variables, function-local objects) -- the young generation is collected frequently with a fast, cheap algorithm (Scavenge), while the old generation is collected far less often with a slower, more thorough algorithm (Mark-Compact), since scanning the whole heap every time would be wasteful.
--> An object that survives multiple young-generation collections gets "promoted" to the old generation -- the underlying bet is that anything long-lived enough to survive several rounds is likely to keep living, so it's not worth re-scanning it as often.

# Common Memory Leak Patterns

--> **Detached DOM nodes** -- removing an element from the DOM (`el.remove()`) does NOT free its memory if a JS variable/closure still references it -- the node is "detached" (invisible, non-interactive) but still alive in memory.

```javascript
let leakedNode;
function cacheAndRemove() {
  const el = document.getElementById("panel");
  leakedNode = el;        // Kept alive by this reference even after removal below
  el.remove();
}
```

--> **Forgotten timers/listeners** -- an interval or event listener holding a closure over large data keeps that data alive for as long as the timer/listener exists, even if the rest of the page has moved on.

```javascript
function startPolling(largeDataset) {
  setInterval(() => {
    console.log(largeDataset.length);   // Closure keeps largeDataset alive forever, since the interval never stops
  }, 1000);
}
// clearInterval(id) was never called -- largeDataset can never be collected
```

--> **Growing caches/global collections** -- a `Map`/array used as a cache that only ever gets `.set()`/`.push()`-ed, with no eviction policy, grows unbounded for the life of the page (a `WeakMap`, covered in file 13 Advanced Data Types and Error Handling, is the usual fix when keys should be collectible once nothing else references them).
--> **Closures over large scopes** -- returning a small function from a larger one keeps EVERY variable in that outer scope's closure alive, not just the ones the returned function actually uses -- accidentally holding onto a large unused variable in the same scope as the one you need can leak it indefinitely.

# Temporal API -- A Better Date

--> `Temporal` (a newer standard built specifically to replace the well-known flaws of `Date` -- mutability, 0-indexed months, unreliable parsing, no real timezone support) represents dates/times as distinct, immutable value types for different use cases, rather than one overloaded `Date` object trying to do everything.
--> `Temporal.PlainDate` -- a calendar date with no time or timezone at all (e.g. a birthday).

```javascript
const bday = Temporal.PlainDate.from("1995-06-15");
console.log(bday.year, bday.month, bday.day);   // 1995 6 15 -- month is 1-indexed, unlike Date
const nextYear = bday.add({ years: 1 });          // Immutable -- returns a NEW PlainDate, doesn't mutate bday
console.log(bday.toString(), nextYear.toString()); // "1995-06-15" "1996-06-15"
```

--> `Temporal.Instant` -- an exact, fixed point on the UTC timeline (like a Unix timestamp), with no calendar or timezone attached at all -- the right type for "when precisely did this event happen," e.g. a server log entry.

```javascript
const now = Temporal.Now.instant();
const later = now.add({ hours: 3 });
console.log(now.epochMilliseconds, later.epochMilliseconds);
console.log(now.until(later).toString());   // "PT3H" -- an ISO 8601 Duration string
```

--> Arithmetic across types is explicit and safe from the classic `Date` footguns (e.g. adding a month to Jan 31st silently rolling into March because February doesn't have 31 days) -- `Temporal` methods make this kind of overflow an explicit, configurable choice rather than a silent surprise.

```javascript
const jan31 = Temporal.PlainDate.from("2024-01-31");
console.log(jan31.add({ months: 1 }).toString());              // "2024-02-29" -- constrained (default), clamps to the last valid day
console.log(jan31.add({ months: 1 }, { overflow: "reject" }));  // Throws instead of silently clamping
```

--> As of this writing, `Temporal` has broad-but-not-universal browser support -- check current compatibility before relying on it without a polyfill in production code.

# MutationObserver -- Watching DOM Changes

--> Watches a DOM subtree for changes (added/removed children, attribute changes, text changes) and fires a callback with a batch of mutation records, instead of you polling the DOM manually.

```javascript
const observer = new MutationObserver((mutations) => {
  for (const mutation of mutations) {
    console.log(mutation.type, mutation.target);   // "childList" / "attributes" / "characterData"
  }
});

observer.observe(document.getElementById("list"), {
  childList: true,   // Watch for added/removed children
  attributes: true,  // Watch for attribute changes
  subtree: true       // Also watch all descendants, not just direct children
});

observer.disconnect();   // Stops watching -- important to call once no longer needed, same reasoning as terminating a Web Worker
```

# IntersectionObserver -- Watching Visibility

--> Efficiently detects when an element enters/exits the viewport (or another container) WITHOUT the performance cost of manually checking `getBoundingClientRect()` on every scroll event -- the browser does the intersection math off the main thread's critical path.
--> The classic use cases -- lazy-loading images/components only once they're about to be visible, infinite-scroll pagination, and firing analytics "ad was actually seen" events.

```javascript
const observer = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.src = entry.target.dataset.src;   // Lazy-load the real image only once visible
      observer.unobserve(entry.target);                // Stop watching it -- it's loaded, no need to keep checking
    }
  });
}, { threshold: 0.1, rootMargin: "100px" });   // Fire slightly before it's fully in view

document.querySelectorAll("img[data-src]").forEach((img) => observer.observe(img));
```

# ResizeObserver -- Watching Size Changes

--> Fires a callback whenever an observed element's content box size changes -- for reasons other than the window itself resizing (e.g. its content grew, a sibling's layout shifted it, a CSS media query changed its width) -- something `window.addEventListener("resize", ...)` cannot detect at all, since that only fires for viewport-level resizes.

```javascript
const resizeObserver = new ResizeObserver((entries) => {
  for (const entry of entries) {
    const { width, height } = entry.contentRect;
    console.log(`Element resized to ${width}x${height}`);
  }
});

resizeObserver.observe(document.querySelector(".resizable-panel"));
```

--> All three observers (`Mutation`, `Intersection`, `Resize`) follow the same shape deliberately -- construct with a callback, `.observe(target)` one or more elements, `.disconnect()`/`.unobserve()` when done -- and all three deliver their callbacks as a MICROTASK-adjacent batch rather than synchronously on every individual change, which is precisely why they're cheap enough to use liberally instead of manual polling loops.

# Deep Dive -- Why Detached Nodes and Closures Are the Same Root Cause

--> Both leak patterns above trace back to one underlying idea -- V8's mark-and-sweep only frees what's UNREACHABLE, and a reference from a live closure, a forgotten `setInterval`, or a stray variable is enough to keep an entire object graph reachable indefinitely, no matter how "removed" or "done" it looks from the outside.

```javascript
function attachHandler() {
  const hugeCache = new Array(1_000_000).fill("x");   // Large, seemingly scoped to this function
  const button = document.getElementById("btn");

  button.addEventListener("click", () => {
    console.log("clicked");   // Doesn't use hugeCache at all --
  });                          // but it's still in the SAME closure scope, so V8 may keep it alive anyway
}
```

--> Taking two heap snapshots in DevTools (see file 17 Web Performance and Debugging Tools) before and after repeating an action several times, then diffing them, is the standard way to confirm a suspected leak -- a steadily growing object count for a type that should have been transient is the tell.
