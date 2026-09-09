# Fiber -- React's Reconciliation Engine

--> **Fiber** is the internal reconciliation algorithm/data structure React has used since React 16, replacing the older "stack reconciler" -- it's not a feature you use directly, but understanding it explains WHY concurrent rendering (below) is even possible.
--> The old stack reconciler walked the component tree recursively and synchronously -- once it started rendering, it couldn't stop until the whole tree was done, blocking the main thread (and therefore user input/animations) on a large update.
--> Fiber represents each component instance as a linked-list-like unit of work (a "fiber" node) with a pointer to its parent, first child, and next sibling -- this lets React PAUSE work after any fiber, hand control back to the browser (so it can handle a click or paint a frame), and RESUME later exactly where it left off.
--> This pausable/resumable structure is the foundation for everything else in this file -- priority-based scheduling, `startTransition`, and Suspense's ability to "wait" without blocking the whole app all depend on React being able to interrupt rendering mid-tree.

# Priority Lanes -- How React Decides What Renders First

--> React internally assigns updates to different **priority lanes** (a bitmask-based scheduling model) -- not all state updates are treated equally urgent.
--> Discrete user input (typing, clicking) gets the highest priority and is rendered synchronously so the UI never feels laggy in response to direct interaction.
--> Updates marked as transitions (see below) get a LOWER priority lane -- React can interrupt an in-progress low-priority render if a higher-priority update (e.g. the user typing another character) comes in, throwing away the stale work-in-progress and restarting with the newer input.
--> This is the concrete mechanism behind React's "concurrent" branding -- it's not multi-threading (JavaScript is still single-threaded), it's React being able to interleave and reprioritize chunks of rendering work instead of committing to one synchronous pass.

```javascript
import { useState, useTransition } from "react";

function SearchPage() {
  const [query, setQuery] = useState("");
  const [isPending, startTransition] = useTransition();

  function handleChange(e) {
    setQuery(e.target.value);              // Urgent -- input must feel instant
    startTransition(() => {
      setSearchResults(filterHugeList(e.target.value)); // Low priority -- can be interrupted/delayed
    });
  }

  return (
    <>
      <input value={query} onChange={handleChange} />
      {isPending && <span>Updating results...</span>}
    </>
  );
}
```

--> `useTransition` and the standalone `startTransition` are the main developer-facing API for this -- marking an update as "not urgent" lets React keep the UI responsive to more important input while the expensive update happens in the background, without needing manual debouncing.
--> `useDeferredValue` is the related hook for deferring a VALUE (rather than wrapping a state setter call) -- useful when you don't control the update itself (e.g. a value coming from a prop) but still want to let a slow re-render lag behind.

# Streaming Server-Side Rendering

--> Beyond the App Router's built-in streaming (covered in the Next.js and SSR file), React itself exposes lower-level streaming SSR APIs directly -- relevant when working outside a framework, or understanding what a framework is actually calling under the hood.
--> `renderToString` (the classic API) is synchronous and blocking -- it must finish rendering the ENTIRE tree to a string before sending anything, so one slow data dependency anywhere delays the whole response.
--> `renderToPipeableStream` (Node.js) and `renderToReadableStream` (Web Streams / edge runtimes) render and FLUSH HTML to the response incrementally, as each Suspense boundary resolves, instead of waiting for everything.

```javascript
// Node.js server (Express-style), streaming API
import { renderToPipeableStream } from "react-dom/server";

app.get("/", (req, res) => {
  const { pipe, abort } = renderToPipeableStream(<App />, {
    onShellReady() {
      res.statusCode = 200;
      res.setHeader("Content-Type", "text/html");
      pipe(res);                          // Shell streams immediately; Suspense-wrapped content streams in after
    },
    onShellError() {
      res.statusCode = 500;
      res.send("<h1>Something went wrong</h1>");
    },
    onError(err) {
      console.error(err);
    },
  });

  setTimeout(abort, 10000);               // Safety timeout -- abort a stream that's taking too long
});
```

```javascript
// Edge/Web Streams runtime (e.g. Cloudflare Workers, Deno)
import { renderToReadableStream } from "react-dom/server";

const stream = await renderToReadableStream(<App />, {
  bootstrapScripts: ["/main.js"],
});
return new Response(stream, { headers: { "Content-Type": "text/html" } });
```

--> `onShellReady` fires once the initial, non-Suspense-wrapped HTML "shell" is ready -- that's the earliest safe moment to start sending bytes to the browser, with any slower Suspense-wrapped sections streaming in afterward as inline `<script>` tags that swap the fallback for real content once resolved.
--> This is the exact mechanism the Next.js App Router's Suspense streaming (covered in the Next.js and SSR file, and the React Server Components and Suspense file) is built on top of -- frameworks wrap these lower-level APIs so you rarely call them directly, but the underlying behavior is the same.

# React DevTools Profiler

--> Distinct from the "Components" tab (which lets you inspect props/state/context of any component live) -- the **Profiler** tab specifically records timing data across a render pass to diagnose performance, extending the brief mention in the Advanced React Patterns file.
--> Workflow -- click record, interact with the app (type, click, navigate), stop recording, then inspect the results across two main views.
--> **Flame graph** -- a per-commit view where each bar is a component; bar WIDTH represents relative render time, and color (yellow/orange toward red) highlights the slowest components in that commit -- a wide, dark-colored bar is the first place to look.
--> **Commit timeline** -- a ranked bar chart across ALL commits during the recording, letting you spot which specific user interaction triggered an unusually expensive render pass, then jump into that commit's flame graph.
--> Hovering/selecting any component in the flame graph shows WHY it re-rendered ("props changed," "state changed," "hooks changed," "parent component rendered") -- directly actionable for deciding whether `React.memo`/`useMemo`/`useCallback` (covered in the React Memo and Styling file) would actually help, versus optimizing something that isn't actually the bottleneck.
--> The Profiler also exposes a programmatic `<Profiler>` component (`import { Profiler } from "react"`) that fires an `onRender` callback with timing data -- useful for logging real-user render performance in production, not just during local DevTools sessions.

# Vitest -- The Vite-Era Test Runner

--> **Vitest** is a test runner built specifically for Vite-based projects, positioned as a modern alternative to Jest (covered in the Testing React Applications file) -- it reuses Vite's own transform pipeline and dev server, so it needs no separate Babel/webpack configuration for JSX, TypeScript, or CSS imports that a Vite project already handles.
--> API-compatible with Jest by design -- `describe`, `test`/`it`, `expect`, `beforeEach` all work the same way, and most existing Jest test files run under Vitest with little to no rewriting.
--> The main practical draw is SPEED -- Vitest runs tests in the same module graph/transform cache Vite already built for the dev server, and uses worker threads for parallelism, typically resulting in noticeably faster watch-mode re-runs than Jest on the same Vite project.

```javascript
// vitest.config.js
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",         // DOM APIs available in tests, same role as Jest's jsdom testEnvironment
    globals: true,                 // describe/test/expect available without importing them
    setupFiles: "./src/test-setup.js",
  },
});
```

```javascript
import { render, screen } from "@testing-library/react";
import { describe, test, expect } from "vitest";
import Greeting from "./Greeting";

describe("Greeting", () => {
  test("renders the provided name", () => {
    render(<Greeting name="Alice" />);
    expect(screen.getByText("Hello, Alice")).toBeInTheDocument();
  });
});
```

--> React Testing Library (covered in the Testing React Applications file) plugs into Vitest exactly the same way it plugs into Jest -- RTL's queries/philosophy are entirely test-runner-agnostic, so nothing about testing PHILOSOPHY changes, only which runner executes the tests.
--> Practical rule of thumb -- a project already on Create React App/webpack with Jest configured has little reason to migrate; a project built with Vite (or migrating to it) gets Vitest's speed and config-sharing for free, which is why Vitest has become the default pairing for new Vite-based React apps.

# Accessibility Testing Tooling

--> Manual accessibility review (semantic HTML, focus management, `eslint-plugin-jsx-a11y` -- all covered in the Advanced React Patterns file) catches a lot, but automated a11y testing catches regressions in CI before they ship.
--> **axe-core** is the underlying accessibility rules engine (checks for missing alt text, insufficient color contrast, invalid ARIA usage, missing form labels, etc.) that powers most automated a11y tools, including the axe browser DevTools extension.
--> **jest-axe** wraps axe-core as a Jest/Vitest matcher, letting an accessibility check run as part of an ordinary component test rather than a separate manual audit step.

```javascript
import { render } from "@testing-library/react";
import { axe, toHaveNoViolations } from "jest-axe";
import { expect, test } from "vitest";
import LoginForm from "./LoginForm";

expect.extend(toHaveNoViolations);

test("LoginForm has no accessibility violations", async () => {
  const { container } = render(<LoginForm />);
  const results = await axe(container);
  expect(results).toHaveNoViolations();
});
```

--> This catches a specific, narrow class of issues (the ones axe's ruleset can detect statically from rendered markup) -- it is NOT a substitute for actual screen-reader/keyboard-only manual testing, since axe can't judge whether the reading ORDER makes sense or whether a focus trap in a modal is genuinely usable, only whether the markup follows known accessibility rules.

# State Management Beyond Redux and Zustand

--> The Redux and Zustand file covers the two most common general-purpose state libraries -- several other approaches solve the same underlying problem with different trade-offs, worth knowing conceptually even without deep API mastery of each.

--> **Jotai** -- an "atomic" state library -- state is defined as small, independent `atom()` units rather than one big store object, and a component subscribes only to the specific atoms it reads via `useAtom`, giving fine-grained re-rendering without needing manual selector functions the way `useSelector`/Zustand selectors require.
```javascript
import { atom, useAtom } from "jotai";

const countAtom = atom(0);

function Counter() {
  const [count, setCount] = useAtom(countAtom); // Only re-renders when countAtom itself changes
  return <button onClick={() => setCount((c) => c + 1)}>{count}</button>;
}
```
--> **Recoil** -- Meta's own atom-based library, conceptually similar to Jotai (predates it, and directly inspired it) -- atoms and derived `selector`s, designed specifically to integrate with React's concurrent rendering model; largely superseded by Jotai/Zustand in new projects but still found in existing Meta-adjacent codebases.
--> **XState** -- a fundamentally different model: instead of storing arbitrary state values, XState models a component/feature as an explicit FINITE STATE MACHINE (or statechart) -- a fixed set of named states ("idle," "loading," "success," "error") with explicit, declared TRANSITIONS between them, rather than state that could in theory be any combination of booleans/flags at once.
```javascript
import { createMachine } from "xstate";

const fetchMachine = createMachine({
  id: "fetch",
  initial: "idle",
  states: {
    idle: { on: { FETCH: "loading" } },
    loading: { on: { SUCCESS: "success", ERROR: "failure" } },
    success: { on: { FETCH: "loading" } },
    failure: { on: { FETCH: "loading" } },
  },
});
```
--> The value of XState's approach -- it makes "impossible states" (e.g. `isLoading: true` AND `isError: true` at once, a real bug class that plain `useState` flags allow by accident) structurally impossible, since the machine can only ever be in exactly ONE named state at a time. The trade-off is more upfront ceremony for genuinely simple state, so it's typically reached for on complex, multi-step flows (checkout wizards, multi-stage forms) rather than by default everywhere.
--> **Choosing among them** -- Redux/RTK for large teams needing enforced structure and time-travel debugging; Zustand or Jotai for less boilerplate on small-to-medium apps (Zustand for centralized store-shaped state, Jotai for granular atom-shaped state); XState specifically when a feature's logic is genuinely about which STATE something is in and how it can transition, not just what VALUE it holds.

# Meta-Frameworks Beyond Next.js

--> Next.js (covered in its own file) is the dominant React meta-framework, but several others solve the same "routing + data-fetching + rendering strategy" problem with different philosophies.

--> **Remix** -- built around web-standard `loader`/`action` functions per route, deliberately leaning on native browser forms and HTTP semantics rather than client-side-only data fetching.
```javascript
// app/routes/users.$id.jsx
export async function loader({ params }) {
  const user = await db.user.findUnique({ where: { id: params.id } });
  return Response.json(user);              // Runs server-side, fetched automatically when the route is visited
}

export async function action({ request }) {
  const formData = await request.formData();
  await db.user.update({ where: { id: formData.get("id") }, data: { name: formData.get("name") } });
  return Response.json({ ok: true });      // Runs server-side in response to a form POST
}

export default function UserPage() {
  const user = useLoaderData();            // Reads the loader's returned data
  return (
    <Form method="post">
      <input name="name" defaultValue={user.name} />
      <button type="submit">Save</button>
    </Form>
  );
}
```
--> `loader` runs on the server before the route renders (comparable in spirit to `getServerSideProps`, covered in the Next.js file, but co-located per-route rather than a separately-named export) and `action` runs on the server in response to a form submission -- Remix's `<Form>` works even with JavaScript disabled (a true HTML form POST), then gets progressively enhanced once JS loads.
--> **Astro** -- an "islands architecture" framework aimed at content-heavy sites (blogs, marketing pages, docs) -- by default, EVERY component renders to static HTML with zero JavaScript shipped, and you explicitly opt individual components INTO client-side interactivity as isolated "islands."
```astro
---
// Astro component -- runs at build/request time, no client JS by default
const posts = await fetchPosts();
---
<Layout>
  <PostList posts={posts} />
  <LikeButton client:load />   {/* Only this component ships JS and hydrates on the client */}
</Layout>
```
--> `client:load` (hydrate immediately), `client:idle` (hydrate when the browser is idle), and `client:visible` (hydrate only once scrolled into view) are Astro's hydration directives -- each is an explicit, granular choice about WHEN a specific island becomes interactive, inverting the default from "everything is client-rendered unless opted out" (typical React SPA) to "nothing is client-rendered unless opted in."
--> Astro islands can be authored in React, Vue, Svelte, or Astro's own component syntax simultaneously in the same project -- it's framework-agnostic by design, unlike Next.js/Remix which are React-specific.
--> **Qwik** -- takes resumability to its logical extreme -- rather than hydrating the whole app on load (even progressively, as Next.js/Remix do), Qwik serializes not just the HTML but the application STATE and event-listener wiring into the HTML itself, and defers downloading/executing any JavaScript until the exact moment a user actually interacts with a specific piece of UI.
--> This means a Qwik app's initial JS execution cost approaches ZERO regardless of how large the app is -- as opposed to hydration-based frameworks, where even "streaming" SSR still eventually needs to download and run JS for every interactive component, just spread out over time rather than all at once.

# SolidJS -- Fine-Grained Reactivity Without a Virtual DOM

--> SolidJS uses JSX syntax that looks almost identical to React at a glance, but its underlying rendering model is fundamentally different -- there is NO Virtual DOM, and (unlike React) a Solid component function runs exactly ONCE, not on every state change.

```jsx
import { createSignal } from "solid-js";

function Counter() {
  const [count, setCount] = createSignal(0);   // A "signal," not useState -- notice no array destructuring re-render implication

  return (
    <button onClick={() => setCount(count() + 1)}>
      Count: {count()}   {/* count() is a function call, not a variable read */}
    </button>
  );
}
```

--> `createSignal` returns a getter FUNCTION (`count()`, called) and a setter, rather than React's plain value + setter pair -- calling the getter inside JSX lets Solid's compiler track exactly which specific DOM text node depends on that signal, and update ONLY that node directly when it changes, with no component re-execution and no diffing step at all.
--> This is conceptually closer to Vue's Proxy-based fine-grained reactivity (covered in the Vue.js Fundamentals file in the Other Frontend Frameworks folder) or Svelte's compile-time reactivity (covered in the Angular and Svelte Fundamentals file) than to React's re-render-and-diff model -- Solid is often described as combining "React's JSX syntax" with "Svelte/Vue's reactivity engine."
--> Practical implication -- because the component function body runs once, patterns that rely on React re-running the whole function on every render (destructuring props at the top, conditional early returns that change what hooks run) don't carry over directly; Solid has its own idioms (`<Show>`, `<For>` control-flow components) instead of plain JS `if`/`.map()` inside JSX, specifically because plain JS conditionals/loops would only run once, not react to later changes.

# Animation and Design-System Tooling (Brief Overview)

--> **Framer Motion** -- the most widely used animation library in the React ecosystem, providing a declarative `<motion.div>` API for animating layout, entrance/exit transitions, drag gestures, and shared-element transitions between routes, without hand-writing CSS keyframes or manually orchestrating `requestAnimationFrame`.
```jsx
import { motion, AnimatePresence } from "framer-motion";

function FadeInBox({ visible }) {
  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 20 }}
        >
          Content
        </motion.div>
      )}
    </AnimatePresence>
  );
}
```
--> `<AnimatePresence>` specifically solves the problem plain conditional rendering can't -- animating a component OUT before it's removed from the DOM, since a plain `{visible && <div>}` unmounts immediately with no chance to play an exit animation.
--> **Storybook** -- a tool for developing and documenting UI components in ISOLATION, outside of the actual application -- each component gets one or more "stories" (a specific props/state configuration), rendered in its own sandboxed page, letting designers/developers browse, visually test, and review every state of a component (loading, error, empty, populated) without navigating the real app into that state.
```javascript
// Button.stories.jsx
export default { component: Button };

export const Primary = { args: { variant: "primary", children: "Click me" } };
export const Disabled = { args: { variant: "primary", disabled: true, children: "Click me" } };
```
--> Storybook is especially valuable for shared component/design-system libraries used across multiple apps/teams -- it becomes the living, interactive documentation for what components exist and how to use them, and integrates with accessibility testing (an axe addon) and visual regression testing tools to catch unintended visual changes.

# React Hook Form + Zod -- A Complete Worked Example

--> React Hook Form was mentioned briefly in the Forms file as the uncontrolled-first form library; Zod was mentioned there as a schema validation option -- combined, they're the most common modern pattern for real-world form handling, worth seeing end to end.
--> React Hook Form registers inputs as UNCONTROLLED (via refs, not `value`/`onChange` state) by default, so typing into a field does NOT trigger a React re-render on every keystroke -- a meaningful performance advantage over hand-rolled `useState`-per-field forms (covered in the Forms file) on large forms.
--> Zod defines the validation schema once, as actual TypeScript-inferrable code, and `@hookform/resolvers/zod` plugs that schema directly into React Hook Form's validation step, so validation rules live in ONE place instead of being duplicated across manual `if` checks and type definitions.

```javascript
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";

const signupSchema = z.object({
  email: z.string().email("Enter a valid email address"),
  password: z.string().min(8, "Password must be at least 8 characters"),
  age: z.coerce.number().min(18, "You must be at least 18"),
});

// type SignupData = z.infer<typeof signupSchema>; -- in a TypeScript project, the schema IS the type (see TypeScript file)

function SignupForm() {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(signupSchema),
  });

  async function onSubmit(data) {
    // `data` is already validated and shaped to match signupSchema at this point
    await fetch("/api/signup", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <input {...register("email")} placeholder="Email" />
      {errors.email && <p role="alert">{errors.email.message}</p>}

      <input {...register("password")} type="password" placeholder="Password" />
      {errors.password && <p role="alert">{errors.password.message}</p>}

      <input {...register("age")} placeholder="Age" />
      {errors.age && <p role="alert">{errors.age.message}</p>}

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Signing up..." : "Sign Up"}
      </button>
    </form>
  );
}
```

--> `register("email")` spreads `{ onChange, onBlur, name, ref }` onto the input -- this is how React Hook Form wires up an uncontrolled input without you writing an `onChange` handler or `useState` call yourself.
--> `handleSubmit(onSubmit)` runs the Zod schema validation FIRST -- `onSubmit` only fires at all if validation passes, and `formState.errors` is automatically populated field-by-field on failure, matching each field's name to its corresponding Zod issue message.
--> `z.coerce.number()` is a genuinely useful Zod detail here -- raw form input values are always strings, so `coerce` converts the string to a number before running the `.min(18)` check, instead of requiring a separate manual `Number(...)` conversion step.
--> This combination (uncontrolled inputs for performance + schema-driven validation for correctness and DRY type safety) is why React Hook Form + Zod has become the de facto default for non-trivial forms in modern React/Next.js/TypeScript codebases, ahead of both hand-rolled `useState` forms and the older Formik + Yup pairing mentioned in the Forms file.
