# Promises

--> An object representing the eventual completion (or failure) of an asynchronous operation
--> Has three states: pending, fulfilled, rejected
--> .then() handles success, .catch() handles error, .finally() runs regardless
--> Promise.all(), Promise.race(), Promise.allSettled() run multiple promises together

# Async / Await

--> Syntactic sugar built on top of Promises to write asynchronous code that looks synchronous
--> async function always returns a Promise
--> await pauses execution until the Promise resolves/rejects
--> Use try...catch around await to handle errors

# Event Loop

--> JavaScript is single-threaded; the event loop lets it handle async operations without blocking
--> Call Stack executes code line by line
--> Web APIs (setTimeout, fetch, DOM events) handle async tasks in the background
--> Callback Queue (macrotask) holds callbacks like setTimeout
--> Microtask Queue holds Promise callbacks and runs before the macrotask queue
--> Event loop constantly checks if the call stack is empty, then pushes queued tasks (microtasks first) onto it

==> Traced Example -- Predicting the Output Order

```javascript
console.log("1: Start");

setTimeout(() => {
  console.log("2: setTimeout callback");   // Macrotask
}, 0);

Promise.resolve().then(() => {
  console.log("3: Promise.then callback");   // Microtask
});

console.log("4: End");

// Actual output order:
// 1: Start
// 4: End
// 3: Promise.then callback
// 2: setTimeout callback
```

--> Why this order, step by step:
1. `console.log("1: Start")` runs immediately -- goes straight on the Call Stack and prints first.
2. `setTimeout(...)` hands its callback off to the Web API to wait, then queues it in the Macrotask (Callback) Queue once the 0ms timer expires -- it does NOT run immediately, even with a 0ms delay.
3. `Promise.resolve().then(...)` schedules its callback in the Microtask Queue (this happens fast, but still not synchronously).
4. `console.log("4: End")` runs immediately (still the same synchronous pass) -- prints second.
5. Only once the Call Stack is completely empty does the Event Loop check the queues -- it ALWAYS drains the entire Microtask Queue first, so the Promise callback ("3") runs before anything in the Macrotask Queue.
6. Finally, once microtasks are empty, the Event Loop pulls the next task from the Macrotask Queue -- the `setTimeout` callback ("2") runs last.

--> Key takeaway: `setTimeout(fn, 0)` does NOT mean "run immediately" -- it means "run after the current synchronous code AND all pending microtasks have finished." This is the most common event-loop question asked in interviews, and the ordering (sync code -> all microtasks -> one macrotask -> repeat) is fixed and predictable.

# Try, Catch, Finally

--> try block contains code that might throw an error
--> catch block runs if an error occurs, receives the error object
--> finally block always runs, regardless of whether an error occurred
--> throw is used to manually create/throw a custom error

# Fetch API

--> fetch(url) --> Used to make HTTP requests (GET, POST, etc.) to a server/API
--> Returns a Promise that resolves to a Response object
--> Commonly chained with .then(res => res.json()) or used with async/await
--> try/catch is used to handle network errors

# Generators

--> function* is a generator function that can pause and resume execution using yield
--> Returns an iterator object with a .next() method
--> Useful for lazy evaluation and custom iteration logic
--> yield* delegation --> function* inner() { yield 1; yield 2 } function* outer() { yield* inner(); yield 3 } -- delegates iteration to another generator/iterable, yielding all its values before continuing
--> Async generators --> async function* fetchPages() { while (hasMore) { yield await getNextPage() } } -- combines async/await with generators, each yield can be awaited
--> for await...of --> used to consume async generators or any async iterable one resolved value at a time: for await (const page of fetchPages()) { console.log(page) }

# AbortController

--> new AbortController() creates a controller with a .signal that can be passed to fetch() (or other APIs) to cancel an in-progress operation
--> const controller = new AbortController(); fetch(url, { signal: controller.signal }); controller.abort() -- cancels the fetch, which then rejects with an AbortError

# Promise.any()

--> Promise.any(promises) resolves as soon as ANY one of the given promises fulfills, ignoring rejections
--> Only rejects (with an AggregateError) if ALL promises reject -- opposite emphasis from Promise.race(), which settles on whichever promise finishes first regardless of success/failure

# Race Conditions in Asynchronous Code

--> A race condition happens when multiple async operations run concurrently and the FINAL result depends on unpredictable timing -- e.g. whichever request happens to finish last "wins," even if it wasn't the last one started.
--> Classic example: a search-as-you-type input that fires a new fetch on every keystroke. If a slower request for an earlier keystroke resolves AFTER a faster request for a later keystroke, the UI can end up showing stale/wrong results.

```javascript
let latestQuery = "";

async function search(query) {
  latestQuery = query;   // Track the most recent query requested
  const response = await fetch(`/api/search?q=${query}`);
  const data = await response.json();

  if (query === latestQuery) {   // Only update the UI if this is still the latest request
    renderResults(data);
  }
  // Otherwise: a newer request has already started, silently discard this stale response
}
```

--> AbortController (see above) is another common fix -- cancel the previous in-flight request entirely as soon as a new one starts, so the stale response never has a chance to resolve and interfere.
--> Race conditions can also occur with shared state updates (e.g. two async functions both reading, then writing, the same variable) -- the fix there is usually to serialize the updates (await one fully before starting the next) instead of firing them concurrently.

# Deep Dive -- Sequential vs Concurrent await

--> A very common, easy-to-miss performance mistake -- `await`-ing multiple independent operations one after another runs them SEQUENTIALLY, even though they don't actually depend on each other.

```javascript
// Sequential -- each request waits for the previous one to fully finish first (slow)
const user = await fetchUser(id);
const posts = await fetchPosts(id);
const comments = await fetchComments(id);
// Total time = sum of all three request times

// Concurrent -- all three requests fire at once, since none depends on another's result
const [user, posts, comments] = await Promise.all([
  fetchUser(id),
  fetchPosts(id),
  fetchComments(id)
]);
// Total time = the SLOWEST single request, not the sum of all three
```

--> `Promise.all` is only appropriate when the operations are genuinely independent -- if one request's result is needed as an INPUT to another, sequential `await` is correct and unavoidable.

# Deep Dive -- Promise.all Fails Fast, Promise.allSettled Doesn't

--> `Promise.all` rejects IMMEDIATELY if any single promise in the group rejects, discarding the results of every other promise even if they later would have succeeded -- appropriate when every operation is required for the overall task to make sense at all.
--> `Promise.allSettled` always waits for EVERY promise to finish, returning an array of `{status, value}` or `{status, reason}` objects for each -- appropriate when partial success is still useful (e.g. sending notifications to 10 users, where one failing shouldn't prevent reporting success for the other 9).

```javascript
const results = await Promise.allSettled([sendEmail(a), sendEmail(b), sendEmail(c)]);
results.forEach((result, i) => {
  if (result.status === "rejected") {
    console.error(`Email ${i} failed:`, result.reason);
  }
});
```

# Deep Dive -- Common async/await Error-Handling Mistake

--> Wrapping an `await` in `try/catch` only catches errors from THAT specific awaited call -- a common mistake is assuming a single outer `try` block automatically catches errors from promises created but not yet awaited inside it, or from callbacks scheduled asynchronously within the try block that run after it has already exited.

```javascript
async function loadData() {
  try {
    const data = await fetchData();       // Errors here ARE caught
    setTimeout(() => {
      throw new Error("delayed error");    // NOT caught -- runs after the try block has already completed
    }, 1000);
  } catch (err) {
    console.error("Caught:", err);
  }
}
```

--> Asynchronous errors thrown OUTSIDE the current `await` chain (inside an uncaptured `setTimeout`, or an unawaited "fire and forget" promise) need their own explicit handling -- a global `window.addEventListener("unhandledrejection", ...)` handler is a common safety net for catching promise rejections that otherwise slip past every local `try/catch`.
