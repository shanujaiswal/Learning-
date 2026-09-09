# Rendering Strategies Overview

--> CSR (Client-Side Rendering) -- plain React app (e.g. Create React App/Vite) ships an almost-empty HTML file, the browser downloads JS and renders everything -- slower first paint, worse SEO, but simpler hosting
--> SSR (Server-Side Rendering) -- the server renders the React tree to HTML for EVERY request and sends fully-formed HTML to the browser, then React "hydrates" it (attaches event listeners) to make it interactive
--> SSG (Static Site Generation) -- HTML is generated once at BUILD time and served as static files -- fastest, but content is fixed until the next build
--> ISR (Incremental Static Regeneration) -- static pages that automatically re-generate in the background after a set time interval, combining SSG's speed with fresher data

# Why Next.js

--> Next.js is a React framework (built by Vercel) that provides file-based routing, SSR/SSG/ISR out of the box, image/font optimization, and API routes -- solves most of the setup work a plain React app leaves to you
--> Comparable frameworks -- Remix (also SSR-focused), Gatsby (SSG-focused)

# File-Based Routing

--> Pages Router (older, still supported) -- every file in /pages becomes a route, e.g. pages/about.js -> /about, pages/blog/[slug].js -> /blog/:slug
--> App Router (current default, Next.js 13+) -- every folder in /app with a page.js file becomes a route, e.g. app/about/page.js -> /about
--> Dynamic segments -- app/blog/[slug]/page.js -> /blog/hello-world, the slug value is available via params

```javascript
// app/blog/[slug]/page.js
export default function BlogPost({ params }) {
  return <h1>Post: {params.slug}</h1>;
}
```

# Data Fetching Methods (Pages Router)

--> getServerSideProps -- runs on the SERVER on every request -- use for data that must always be fresh (dashboards, user-specific pages) -- this is SSR
--> getStaticProps -- runs at BUILD time -- use for content that rarely changes (blog posts, marketing pages) -- this is SSG
--> getStaticProps + revalidate option -- enables ISR, regenerating the page in the background after N seconds

```javascript
export async function getServerSideProps(context) {
  const res = await fetch(`https://api.example.com/user/${context.params.id}`);
  const user = await res.json();
  return { props: { user } }; // passed to the page component as props
}

export async function getStaticProps() {
  const posts = await fetchPosts();
  return { props: { posts }, revalidate: 60 }; // ISR -- regenerate at most once per 60s
}
```

# Data Fetching (App Router)

--> Server Components can fetch data directly with async/await in the component body -- no special exported function needed
--> fetch() calls in Server Components are automatically deduped and cached by Next.js, with cache behavior controlled per-call

```javascript
// app/blog/page.js -- a Server Component by default
async function getPosts() {
  const res = await fetch("https://api.example.com/posts", { cache: "force-cache" }); // SSG-like
  // { cache: "no-store" } -- SSR-like, always fresh
  return res.json();
}

export default async function BlogPage() {
  const posts = await getPosts();
  return <ul>{posts.map((p) => <li key={p.id}>{p.title}</li>)}</ul>;
}
```

# Hydration

--> After the server sends fully-rendered HTML, React runs on the client and "hydrates" it -- reusing the existing DOM nodes and attaching event handlers, instead of re-rendering everything from scratch
--> Hydration mismatch -- an error that occurs when the server-rendered HTML doesn't match what the client would render (e.g. using Date.now() or Math.random() directly in render, or browser-only APIs like window during SSR) -- fix by deferring such logic to useEffect (client-only)

# API Routes

--> Next.js lets you write backend endpoints inside the same project -- app/api/users/route.js (App Router) or pages/api/users.js (Pages Router)
--> Useful for small backends, webhooks, or proxying third-party APIs without needing a separate server

```javascript
// app/api/users/route.js
export async function GET() {
  const users = await db.user.findMany();
  return Response.json(users);
}
```

# Image and Font Optimization

--> next/image -- automatically serves resized/optimized images in modern formats (WebP/AVIF), lazy-loads by default, prevents layout shift by requiring width/height
--> next/font -- self-hosts Google/local fonts at build time, eliminating render-blocking font requests and layout shift from font swapping

# SEO Benefits of SSR/SSG

--> Search engine crawlers historically struggled to execute JS to see CSR content -- SSR/SSG send full HTML immediately, ensuring content is indexable
--> Next.js's generateMetadata (App Router) or the <Head> component (Pages Router) let each page set its own title/meta tags dynamically based on fetched data

# Deep Dive -- Server Components vs Client Components (App Router)

--> The App Router's biggest conceptual shift from the Pages Router -- every component is a **Server Component** by default, rendered entirely on the server with ZERO JavaScript shipped to the browser for it, unless explicitly opted out with the `"use client"` directive.

```javascript
// A Server Component (default, no directive needed) -- can be async, can directly query a database
// This component's code NEVER ships to the browser at all
async function ProductList() {
  const products = await db.product.findMany();
  return <ul>{products.map(p => <li key={p.id}>{p.name}</li>)}</ul>;
}
```

```javascript
"use client";   // Opts THIS component (and everything it imports) into client-side rendering

import { useState } from "react";

function AddToCartButton({ productId }) {
  const [added, setAdded] = useState(false);   // Hooks require a Client Component -- Server Components can't use them at all
  return <button onClick={() => setAdded(true)}>{added ? "Added!" : "Add to Cart"}</button>;
}
```

--> **Why this matters** -- Server Components can directly access backend resources (a database, the filesystem, secret API keys) without ever exposing that code or those credentials to the browser, and they add ZERO JavaScript to the client bundle, directly improving the load-time metrics covered in the Web Performance file. Client Components are needed specifically for anything requiring interactivity (state, effects, event handlers) or browser-only APIs.
--> **The composition rule** -- a Server Component CAN render a Client Component (passing it data as props), but a Client Component CANNOT directly import and render a Server Component the other way around -- once you're in client territory, everything nested further down is also client-rendered, since the server has already handed off control to the browser at that point.

# Deep Dive -- Streaming and Suspense in the App Router

--> The App Router integrates directly with the Suspense concept covered in the React Server Components and Suspense file -- wrapping a slow-loading Server Component in `<Suspense>` lets Next.js STREAM the rest of the page to the browser immediately, showing a fallback for just the slow part, rather than making the entire page wait for the slowest piece of data.

```javascript
import { Suspense } from "react";

export default function Page() {
  return (
    <div>
      <Header />                                          {/* Renders immediately */}
      <Suspense fallback={<ProductListSkeleton />}>
        <ProductList />                                    {/* Streams in once its data resolves */}
      </Suspense>
    </div>
  );
}
```

--> This directly improves perceived performance metrics (LCP, covered in the Web Performance file) -- users see and can interact with the fast parts of the page immediately, instead of a blank screen while waiting on the single slowest data source.
