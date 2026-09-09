# React Server Components (RSC)

--> A new component type (React 18+, used by Next.js App Router) that renders ONLY on the server -- its code, including any imports (a database client, a large parsing library), never gets sent to the browser at all
--> Reduces client bundle size significantly since server-only logic and dependencies are stripped out of what ships to the browser
--> Can be async and fetch data directly, without useEffect/useState

```javascript
// Server Component (default in app/ directory -- no directive needed)
async function ProductList() {
  const products = await db.product.findMany(); // runs only on the server, db client never reaches the browser
  return <ul>{products.map((p) => <li key={p.id}>{p.name}</li>)}</ul>;
}
```

# Server Components vs Client Components

--> Server Components -- can't use useState/useEffect/browser APIs/event handlers (onClick etc.) -- they render once on the server and produce static output for that request
--> Client Components -- marked with "use client" at the top of the file -- behave like traditional React components, can use hooks, state, and event handlers, and their JS IS sent to the browser

```javascript
"use client"; // marks everything below as a Client Component

import { useState } from "react";

export default function Counter() {
  const [count, setCount] = useState(0);
  return <button onClick={() => setCount(count + 1)}>{count}</button>;
}
```

--> A Server Component can import and render a Client Component (composition works top-down) -- but a Client Component cannot import a Server Component directly, since by the time it runs client-side, server-only code can't execute
--> Passing a Server Component as a `children` prop INTO a Client Component is allowed -- the boundary is about direct imports, not the render tree shape

# Why Split This Way

--> Not every component needs interactivity -- a page might be 90% static content (product descriptions, layout) and 10% interactive (an "Add to Cart" button)
--> Only the interactive 10% needs to ship JS to the client -- RSC lets you keep the rest server-only, cutting bundle size and improving initial load

---

# Suspense

--> <Suspense> lets a component tree "wait" for something (data, a lazy-loaded component) before rendering, showing a fallback UI in the meantime -- without manually tracking loading state
--> Originally used for React.lazy() (code-splitting), extended in React 18+ to support data fetching

```javascript
import { Suspense } from "react";

function App() {
  return (
    <Suspense fallback={<Spinner />}>
      <ProductList /> {/* if this is an async Server Component, Suspense shows the fallback while it awaits data */}
    </Suspense>
  );
}
```

# Suspense for Code-Splitting (React.lazy)

```javascript
import { lazy, Suspense } from "react";

const Settings = lazy(() => import("./Settings")); // Settings.js is only downloaded when actually rendered

function App() {
  return (
    <Suspense fallback={<p>Loading settings...</p>}>
      <Settings />
    </Suspense>
  );
}
```

# Suspense for Data Fetching

--> An async Server Component that awaits data effectively "suspends" -- React shows the nearest Suspense boundary's fallback until the await resolves
--> The use() hook (React 19) lets Client Components also suspend on a Promise passed down as a prop

```javascript
// Client Component reading a Promise with use()
"use client";
import { use, Suspense } from "react";

function Reviews({ reviewsPromise }) {
  const reviews = use(reviewsPromise); // suspends until the promise resolves
  return <ul>{reviews.map((r) => <li key={r.id}>{r.text}</li>)}</ul>;
}

<Suspense fallback={<p>Loading reviews...</p>}>
  <Reviews reviewsPromise={fetchReviews()} />
</Suspense>
```

# Streaming with Suspense (App Router)

--> Next.js can stream HTML to the browser in pieces -- the shell of the page renders immediately, and each Suspense boundary's content streams in as it becomes ready, instead of blocking the whole page on the slowest data fetch
--> loading.js files (App Router convention) automatically wrap a route segment in a Suspense boundary with that file as the fallback

# Error Boundaries Alongside Suspense

--> Suspense handles the "still loading" state -- an Error Boundary (a class component with static getDerivedStateFromError, or a library like react-error-boundary) handles the "it failed" state
--> They're typically paired -- an Error Boundary wraps a Suspense boundary so a rejected promise/fetch shows an error UI instead of crashing the whole app

```javascript
<ErrorBoundary fallback={<p>Something went wrong.</p>}>
  <Suspense fallback={<Spinner />}>
    <ProductList />
  </Suspense>
</ErrorBoundary>
```
