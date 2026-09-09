# Why Automated Testing

--> Manually re-clicking through an app after every change doesn't scale -- automated tests are code that verifies OTHER code behaves correctly, run in seconds, and catch regressions before they reach production.
--> A "test" in the unit-testing sense is a small, isolated piece of code that calls a function/module with known inputs and asserts the output/behavior matches what's expected -- if the assertion fails, the test fails loudly instead of the bug silently shipping.

# Test Runners -- Jest, Vitest, Mocha (Conceptually)

--> A test runner is the tool that discovers test files, executes them, runs assertions, and reports pass/fail results -- the specific syntax differs slightly between tools, but the underlying shape (`describe` groups related tests, `it`/`test` defines one test case) is shared across nearly all of them.
--> **Jest** -- the long-standing default for React/general JS projects, ships with an assertion library, mocking, and a test runner all bundled together, no separate config typically needed for basic use.
--> **Vitest** -- a newer runner built specifically to reuse Vite's config/transform pipeline, so it understands the exact same ESM/TypeScript/JSX setup as the app's actual build tool, and runs noticeably faster than Jest in Vite-based projects as a result.
--> **Mocha** -- an older, more minimal runner that deliberately does NOT bundle an assertion library or mocking -- it's commonly paired with a separate library like Chai (assertions) and Sinon (mocks/spies), giving more choice but requiring more setup than Jest/Vitest's "batteries included" approach.

```javascript
// The same basic shape works nearly identically across Jest, Vitest, and Mocha+Chai
describe("sum()", () => {
  it("adds two positive numbers", () => {
    expect(sum(2, 3)).toBe(5);   // Jest/Vitest assertion style
  });

  it("handles negative numbers", () => {
    expect(sum(-2, -3)).toBe(-5);
  });
});
```

# Assertions

--> An assertion is a statement that must be true or the test fails -- `expect(actual).toBe(expected)` (Jest/Vitest), `assert.equal(actual, expected)` (Node's built-in `assert`, Chai), `actual.should.equal(expected)` (Chai's alternate style) are all the same underlying idea in different syntaxes.
--> Common assertion categories -- equality (`toBe` for primitives/reference equality, `toEqual` for deep structural equality on objects/arrays), truthiness (`toBeTruthy`, `toBeNull`, `toBeUndefined`), exceptions (`toThrow`), and async-specific assertions (`resolves`/`rejects`, shown below).

```javascript
expect(2 + 2).toBe(4);                                  // Primitive equality
expect({ a: 1 }).toEqual({ a: 1 });                       // Deep equality -- toBe would fail here (different object references)
expect({ a: 1 }).not.toBe({ a: 1 });                      // toBe fails -- not the SAME object in memory
expect(() => JSON.parse("{bad json")).toThrow();          // Asserts the function throws when called
await expect(fetchUser(1)).resolves.toEqual({ id: 1 });    // Asserts a Promise resolves to a matching value
await expect(fetchUser(-1)).rejects.toThrow("not found");  // Asserts a Promise rejects with a matching error
```

# Test Doubles -- Mocks, Stubs, and Spies

--> A "test double" is any fake stand-in for a real dependency, used to isolate the specific unit under test from the behavior/cost/unpredictability of what it depends on (a real network call, a database, the current date, a slow computation). The three common flavors are distinguished by WHAT they're used to verify.
--> **Stub** -- replaces a real function with one that returns a canned, predetermined value, with no concern for whether/how it was called. Used purely to control the INPUT the code under test receives.

```javascript
const stubFetchUser = () => Promise.resolve({ id: 1, name: "Alice" });   // Always returns the same fake user
```

--> **Spy** -- wraps a REAL (or fake) function to record how it was called (arguments, call count, return value) while still (usually) letting the original behavior run -- used to verify a side effect happened, without needing to inspect the final visible output directly.

```javascript
const logSpy = vi.spyOn(console, "log");   // Vitest spy -- wraps console.log, letting it still actually log
doSomethingThatLogs();
expect(logSpy).toHaveBeenCalledWith("expected message");
```

--> **Mock** -- a fake with pre-programmed expectations about how it SHOULD be called, often failing the test itself if those expectations aren't met (e.g. "this function must be called exactly once, with these exact arguments") -- the strictest of the three, verifying BEHAVIOR/interaction rather than just controlling input or observing calls.

```javascript
const mockSendEmail = vi.fn();   // A full mock function -- tracks calls AND lets you assert on them
mockSendEmail("alice@example.com", "Welcome!");
expect(mockSendEmail).toHaveBeenCalledTimes(1);
expect(mockSendEmail).toHaveBeenCalledWith("alice@example.com", "Welcome!");
```

--> In casual practice these three terms get used loosely/interchangeably (most people just say "mock" for all three) -- the precise distinction matters mainly for understanding WHAT a given test double is actually verifying: input control (stub), observed calls (spy), or enforced expectations (mock).

# Mocking Modules and Timers

--> Beyond mocking individual functions, test runners can mock an entire module's exports (e.g. replacing a real API client module with a fake one for every test in a file) or fake the passage of time itself, so tests involving `setTimeout`/`setInterval`/`Date` don't need to actually wait in real time.

```javascript
vi.mock("./api-client.js", () => ({
  fetchUser: vi.fn().mockResolvedValue({ id: 1, name: "Alice" }),
}));

vi.useFakeTimers();
const callback = vi.fn();
setTimeout(callback, 1000);
vi.advanceTimersByTime(1000);   // Instantly "fast-forwards" time -- no real 1-second wait in the test run
expect(callback).toHaveBeenCalled();
```

# TDD -- Test-Driven Development Basics

--> TDD's red-green-refactor cycle -- (1) write a failing test for behavior that doesn't exist yet ("red"), (2) write the minimum code to make it pass ("green"), (3) refactor the implementation with confidence, since the test suite immediately flags any regression.

```javascript
// Step 1 -- RED: write the test first, before writing isSlug() at all
test("isSlug rejects strings with spaces", () => {
  expect(isSlug("hello world")).toBe(false);
});
// Running this now fails -- isSlug doesn't exist yet, which is expected and correct at this stage

// Step 2 -- GREEN: write just enough implementation to pass
function isSlug(str) {
  return !str.includes(" ");
}

// Step 3 -- REFACTOR: now harden the implementation, re-running the test after each change
function isSlug(str) {
  return /^[a-z0-9-]+$/.test(str);   // Stricter, more correct -- the original test still passes throughout
}
```

--> The practical value isn't dogmatically writing every line of test-before-code -- it's that a test suite written this way tends to actually test BEHAVIOR (what the function should do) rather than accidentally testing IMPLEMENTATION DETAILS (how it happens to be written today), since the test was written before any particular implementation existed to copy from.
--> Testing pure functions (see file 25 Immutability and Pure Functions in Practice) is dramatically easier than testing code full of side effects/mutable shared state -- same input always produces the same output, with nothing to mock/stub/fake at all, which is a large part of why the functional patterns in that file are worth learning independent of testing.

# Generators -- .send() Equivalent: Passing Values Into next()

--> `function*`/`yield` were introduced in file 08 Async JavaScript and Generators as a way to produce a sequence of values -- but a generator can also RECEIVE a value back at each pause point, since `.next(value)` passes `value` in as the result of the `yield` expression that was paused.

```javascript
function* conversation() {
  const name = yield "What is your name?";
  const age = yield `Hi ${name}, how old are you?`;
  return `${name} is ${age} years old.`;
}

const gen = conversation();
console.log(gen.next().value);          // "What is your name?" -- first .next() has no meaningful argument, just starts the generator
console.log(gen.next("Alice").value);     // "Hi Alice, how old are you?" -- "Alice" becomes the value of the first yield expression
console.log(gen.next("30").value);        // "Alice is 30 years old." -- the return value, done: true
```

--> This two-way communication is precisely the mechanism coroutine-style async libraries (and, historically, generator-based async patterns that predated native async/await) were built on -- a driver function repeatedly calls `.next(resolvedValue)` each time a yielded Promise settles, which is essentially what `async`/`await` desugars to internally.

# Generators -- .throw() to Inject an Error

--> `.throw(error)` resumes a paused generator by throwing `error` at the exact point where it's currently paused, as if a `throw` statement had appeared right there -- letting the generator's own internal `try/catch` handle it, rather than the error simply propagating out of `.throw()` unhandled.

```javascript
function* resilientTask() {
  try {
    yield "step 1";
    yield "step 2";
  } catch (err) {
    console.log("Caught inside generator:", err.message);
    yield "recovered";
  }
}

const gen = resilientTask();
console.log(gen.next().value);              // "step 1"
console.log(gen.throw(new Error("boom")).value);   // Logs "Caught inside generator: boom", then yields "recovered"
```

--> `.return(value)` is the third control method -- forces the generator to act as though a `return value` statement occurred at the paused point, immediately finishing it (running any `finally` blocks first) rather than resuming normal execution.

# yield* Delegation Patterns Beyond the Basics

--> `yield*` (introduced briefly in file 08) delegates not just VALUES but also `.next(value)`/`.throw(err)`/`.return(val)` calls THROUGH to the inner generator -- the outer generator becomes a transparent pass-through for the entire duration of the delegated call, which is what makes composing generators out of smaller generators actually work correctly.

```javascript
function* inner() {
  const x = yield "give me x";
  return x * 2;
}

function* outer() {
  const doubled = yield* inner();   // Delegates next()/value passing through transparently
  yield `doubled result: ${doubled}`;
}

const gen = outer();
console.log(gen.next().value);        // "give me x" -- from inner(), reached transparently through outer()
console.log(gen.next(5).value);        // "doubled result: 10" -- the 5 was passed through to inner()'s yield
```

--> Delegating to a nested generator this way is what lets a large generator-based state machine or parser be decomposed into many small, individually-testable generator functions, each handling one concern, composed together with `yield*` rather than one giant unwieldy function.

# Client-Side Routing -- history.pushState and popstate

--> A Single Page Application changes the URL and the visible content WITHOUT a full page reload by using the History API directly, rather than letting the browser make a real navigation request.

```javascript
function navigateTo(path) {
  history.pushState({}, "", path);   // Changes the URL bar and adds a history entry, without reloading the page
  renderRouteFor(path);                // App-level function that swaps the visible content to match
}

document.querySelectorAll("a[data-link]").forEach((link) => {
  link.addEventListener("click", (e) => {
    e.preventDefault();          // Stop the browser's default full-page navigation
    navigateTo(link.getAttribute("href"));
  });
});
```

--> `pushState(state, title, url)` -- the `state` object is stored WITH that history entry and handed back later; `title` is largely ignored by all current browsers; `url` is what actually changes the address bar (must be same-origin).
--> `history.replaceState(...)` -- same idea, but replaces the CURRENT entry instead of adding a new one, useful for redirects where you don't want the replaced URL reachable via the back button.
--> The `popstate` event fires when the user navigates via the browser's back/forward buttons (or `history.back()`/`.forward()`) -- crucially, it does NOT fire for `pushState`/`replaceState` calls themselves, only for actual back/forward navigation, so the app must handle both cases (the click handler above, and this listener) to stay in sync.

```javascript
window.addEventListener("popstate", (event) => {
  console.log(event.state);          // Whatever object was passed to pushState for this entry
  renderRouteFor(location.pathname);   // Re-render to match wherever the user navigated back/forward to
});
```

--> A real router library (React Router, Vue Router) is this exact mechanism wrapped with route-matching (turning `/users/:id` patterns into params), nested layouts, and integration with a framework's rendering -- the raw `pushState`/`popstate` pairing above is the entire mechanism underneath all of them.

# Accessibility From a JS Interaction Standpoint

--> Accessibility (a11y) via semantic HTML/ARIA attributes is usually covered as markup -- but a large share of REAL accessibility bugs in dynamic apps come from JavaScript-driven interactions that visually work fine but leave assistive technology (screen readers, keyboard-only navigation) behind.

# Focus Management

--> Focus does not move automatically when content changes via JS -- opening a modal, navigating a client-side route, or showing a new panel leaves keyboard/screen-reader focus wherever it happened to be, unless the code explicitly moves it.

```javascript
function openModal(modalEl) {
  modalEl.hidden = false;
  const firstFocusable = modalEl.querySelector("button, [href], input, [tabindex]");
  firstFocusable?.focus();   // Explicitly move focus INTO the modal -- otherwise a screen reader user has no idea it opened
}

function closeModal(modalEl, triggerButton) {
  modalEl.hidden = true;
  triggerButton.focus();   // Explicitly return focus to whatever opened the modal -- don't just let it fall back to <body>
}
```

--> **Focus trapping** -- while a modal is open, Tab/Shift+Tab should cycle only among the modal's own focusable elements, not escape into the page behind it (which is invisible/inert to the user but would otherwise still be reachable by keyboard) -- typically implemented by listening for `keydown` on Tab and explicitly wrapping focus back to the first/last focusable element inside the modal.
--> `element.focus()` only works on elements that are natively focusable (links, buttons, inputs) OR have an explicit `tabindex` attribute (`tabindex="0"` makes an arbitrary element focusable and reachable in the normal tab order; `tabindex="-1"` makes it programmatically focusable via `.focus()` but skips it in normal Tab-key navigation -- exactly right for a modal container that should be focused on open but not tabbed to directly afterward).

# ARIA Live Regions -- Announcing Dynamic Content

--> A screen reader only announces new content automatically if it's inside a region explicitly marked as "live" -- content that simply appears in the DOM (e.g. a form validation error, a "3 new messages" count updating) is otherwise silent to a screen reader user unless they happen to navigate directly to it.

```html
<div aria-live="polite" id="status"></div>
```

```javascript
function announce(message) {
  document.getElementById("status").textContent = message;   // Screen reader announces this automatically
}

announce("3 new messages received");
```

--> `aria-live="polite"` -- announces the change once the screen reader finishes whatever it's currently reading, without interrupting (the right default for most status updates). `aria-live="assertive"` -- interrupts immediately, reserved for genuinely urgent/time-sensitive messages (a session-expiring warning), since overuse is disruptive and trains users to ignore it.
--> A common mistake -- adding `aria-live` to an element and setting its text at the SAME time it's inserted into the DOM often gets missed by screen readers, since some implementations only detect the region as "live" after it already exists; the safer pattern is to have the (empty) live region present in the DOM from page load, and only update its `textContent` afterward, as shown above.
--> `role="alert"` is a shorthand that implies `aria-live="assertive"` -- commonly used on form-level error summaries so a screen reader user is told immediately that a submission failed, without needing to discover it by chance.
