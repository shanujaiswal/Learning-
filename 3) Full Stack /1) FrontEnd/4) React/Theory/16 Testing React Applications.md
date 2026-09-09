# Why Testing React Components Differs From Testing Plain JS

--> Components render UI, respond to user interaction, and manage state -- testing them well means verifying observable BEHAVIOR (what a user sees/can do), not internal implementation details (which state variable changed, which method fired).

# React Testing Library (RTL) -- The Standard Approach

--> RTL's guiding principle: "the more your tests resemble the way your software is used, the more confidence they give you" -- so tests query the rendered output the way a real user would (by visible text, label, or role), not by digging into component internals.

```javascript
import { render, screen, fireEvent } from "@testing-library/react";
import LoginForm from "./LoginForm";

test("submits the entered username", () => {
  render(<LoginForm />);

  const input = screen.getByLabelText("Username");
  fireEvent.change(input, { target: { value: "alice" } });

  fireEvent.click(screen.getByRole("button", { name: "Log In" }));

  expect(screen.getByText("Welcome, alice")).toBeInTheDocument();
});
```

--> Query priority (RTL's own recommendation, most to least preferred): `getByRole`, `getByLabelText`, `getByText`, and only as a last resort `getByTestId` -- favoring queries that reflect how an actual user or assistive technology identifies elements.

# Jest -- The Test Runner and Assertion Library

--> Jest provides the surrounding test infrastructure -- `test()`/`describe()` blocks, `expect()` assertions, mocking, and snapshot testing -- RTL plugs into it (Create React App and most modern setups ship with both pre-configured).

```javascript
test("adds numbers correctly", () => {
  expect(sum(2, 3)).toBe(5);
});
```

# Testing Async Behavior

--> Components that fetch data or update after a delay need `findBy*` queries (which wait/retry) or explicit `waitFor`, rather than assuming the UI has already updated immediately after an action.

```javascript
test("shows fetched user data", async () => {
  render(<UserProfile userId={1} />);

  const heading = await screen.findByText("Alice Johnson");   // Waits for it to appear
  expect(heading).toBeInTheDocument();
});
```

# Mocking API Calls

--> Tests shouldn't depend on a real backend being available -- Mock Service Worker (MSW) intercepts actual network requests at the network layer, letting components call `fetch`/axios completely normally while tests control what response comes back.

```javascript
import { rest } from "msw";
import { setupServer } from "msw/node";

const server = setupServer(
  rest.get("/api/user/1", (req, res, ctx) =>
    res(ctx.json({ name: "Alice Johnson" }))
  )
);
```

# Update Note: MSW v2 API

--> The example directly above uses the Mock Service Worker v1 API (`import { rest } from "msw"`, handlers built with `(req, res, ctx) => res(ctx.json(data))`). MSW v2 (current) removed the `rest` export and replaced it with a new API aligned to the standard Fetch `Request`/`Response` model.
--> Corrected v2 equivalent:

```javascript
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

const server = setupServer(
  http.get("/api/user/1", () => {
    return HttpResponse.json({ name: "Alice Johnson" });
  })
);
```

--> What changed: `rest` --> `http` (namespace renamed to mirror real HTTP methods -- `http.get`, `http.post`, etc.), and the three-argument `(req, res, ctx)` resolver was replaced by a single resolver returning an `HttpResponse` (a thin wrapper over the native `Response`, e.g. `HttpResponse.json(data)`, `HttpResponse.text(str)`). Server lifecycle setup (`server.listen()`/`resetHandlers()`/`close()`) is unchanged between v1 and v2 -- only the handler-definition API changed. Any code copied from older MSW v1 tutorials needs this `rest`/`ctx` --> `http`/`HttpResponse` rewrite to work against current `msw` versions.

# Testing Custom Hooks

--> `renderHook` (from RTL) lets you test a custom hook's behavior in isolation, without needing to mount it inside a full component.

```javascript
import { renderHook, act } from "@testing-library/react";
import useCounter from "./useCounter";

test("increments the counter", () => {
  const { result } = renderHook(() => useCounter());

  act(() => result.current.increment());

  expect(result.current.count).toBe(1);
});
```

# What NOT to Test

--> Avoid testing implementation details -- internal state variable names, which specific internal function got called, exact prop values passed to a child component. These couple tests tightly to HOW a component is built, so refactoring (even without changing behavior) breaks tests unnecessarily.
--> Snapshot tests (comparing rendered output against a saved reference) are useful for catching unintended markup changes, but low-value if overused -- a snapshot that changes on every minor styling tweak just gets rubber-stamp-updated rather than genuinely reviewed.
