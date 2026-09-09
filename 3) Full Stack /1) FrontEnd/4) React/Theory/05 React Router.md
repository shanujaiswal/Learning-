# React Router

--> React Router is a popular library used to enable client-side navigation in React applications.
--> It allows users to switch between different views (pages) without reloading the browser.

# Add React Router

--> To add React Router in your application, run this in the terminal from the root directory of the application:
npm i -D react-router-dom
npm i -D react-router-dom@latest //for upgrading the react router

--> Note: `react-router-dom` is a RUNTIME dependency, not a dev dependency -- application code imports and executes it directly in the browser (`BrowserRouter`, `Routes`, `Route`, `useNavigate`, etc.), so it should be installed as `npm i react-router-dom` (no `-D`). Dev dependencies (`-D`/`--save-dev`) are for tooling only needed while working on the project (linters, test runners, type defs) that never ships as part of the running app; installing a package the app actually runs with `-D` risks it being stripped out of a production-only install (e.g. `npm ci --omit=dev`), breaking the app.
==> Basic Usage
--> See Example
--> We wrap our content first with <BrowserRouter>.
--> Then we define our <Routes>. An application can have multiple <Routes>. Our basic example only uses one.
--> <Route>s can be nested. The first <Route> has a path of / and renders the Layout component.
--> The nested <Route>s inherit and add to the parent route. So the blogs path is combined with the parent and becomes /blogs.
--> The Home component route does not have a path but has an index attribute. That specifies this route as the default route for the parent route, which is /.
--> Setting the path to \* will act as a catch-all for any undefined URLs. This is great for a 404 error page
==> Pages / Components
--> The Layout component has <Outlet> and <Link> elements.
--> The <Outlet> renders the current route selected.
--> <Link> is used to set the URL and keep track of browsing history.
--> Anytime we link to an internal path, we will use <Link> instead of <a href="">.
--> The "layout route" is a shared component that inserts common content on all pages, such as a navigation menu.

# useNavigate Hook

--> Returns a function that lets you navigate programmatically (e.g. after form submission or a button click) instead of using a <Link>.
--> const navigate = useNavigate(); navigate("/home") --> Navigates to a path.
--> navigate(-1) --> Goes back one entry in the history stack, like the browser's back button.
--> navigate("/login", { replace: true }) --> Replaces the current entry instead of pushing a new one (useful after login/logout).

# useParams Hook

--> Returns an object of key/value pairs from the dynamic URL params matched by the current <Route>.
--> Route path="/blogs/:id" --> const { id } = useParams() gives access to the id segment of the URL.

# useLocation Hook

--> Returns the current location object (pathname, search, hash, state), similar to window.location.
--> Useful for reading query strings or knowing which route is currently active (e.g. highlighting a nav link).

# Navigate Component

--> <Navigate to="/login" /> declaratively redirects to another route when rendered.
--> Commonly used inside conditional rendering to build protected/private routes:
--> { isLoggedIn ? <Dashboard /> : <Navigate to="/login" /> }

# Lazy Loading Routes

--> React.lazy() + <Suspense> can be combined with React Router to code-split routes, so each page's code is only downloaded when the user navigates to it.
--> const Home = React.lazy(() => import("./Home"));
--> Wrap the <Routes> in <Suspense fallback={<Loading />}> to show a fallback while the chunk loads.

# Data APIs (v6.4+) -- createBrowserRouter, loader, action

--> Since v6.4, React Router recommends "data routers" instead of (or alongside) the plain hooks-based approach above.
--> createBrowserRouter([{ path: "/", element: <Root />, loader: rootLoader, action: rootAction }]) -- creates the router; render it with <RouterProvider router={router} />.
--> loader --> a function attached to a route that fetches data before the route renders; access the result in the component with useLoaderData().
--> action --> a function attached to a route that handles data mutations (e.g. form submissions) instead of a manual onSubmit handler; a <Form> from react-router-dom posts to it automatically.
--> useLoaderData() / useActionData() --> read the data returned by the matching route's loader/action.
--> Benefit: data fetching starts in parallel with rendering the route (no waterfall from a useEffect fetch), and it's the pattern Remix (and Next.js's newer conventions) share.

# Deep Dive -- Nested Layouts With Multiple Outlets

--> A single top-level `<Outlet>` renders one level of nested routes -- but layouts can nest arbitrarily deep, each with its OWN `<Outlet>`, letting a section of an app (e.g. a dashboard) have its own persistent shared UI (a sidebar) surrounding whichever specific dashboard page is currently active.

```jsx
<Routes>
  <Route path="/dashboard" element={<DashboardLayout />}>   {/* Has its own <Outlet> for dashboard sub-pages */}
    <Route index element={<Overview />} />
    <Route path="settings" element={<Settings />} />
    <Route path="billing" element={<Billing />} />
  </Route>
</Routes>
```

```jsx
function DashboardLayout() {
  return (
    <div>
      <Sidebar />          {/* Persists across all dashboard sub-pages, never re-mounts on navigation between them */}
      <Outlet />            {/* Renders whichever specific dashboard route currently matches */}
    </div>
  );
}
```

--> This is precisely why the Sidebar's own internal state (e.g. a "collapsed/expanded" toggle) survives navigating between `Overview`/`Settings`/`Billing` -- the layout component itself doesn't re-mount, only the nested `<Outlet>` content changes.

# Deep Dive -- Route-Level Error Handling

--> Data routers (`createBrowserRouter`, covered above) support an `errorElement` per route -- if a route's `loader`/`action` throws, or the route component itself throws during render, React Router automatically renders the nearest `errorElement` instead of crashing the whole app, directly connecting to the Error Boundaries concept covered in its own file, but built into the router itself for route-specific errors.

```javascript
const router = createBrowserRouter([
  {
    path: "/products/:id",
    element: <ProductPage />,
    loader: productLoader,
    errorElement: <ProductErrorPage />,   // Shown if productLoader throws, or ProductPage itself throws
  },
]);
```

```javascript
import { useRouteError } from "react-router-dom";

function ProductErrorPage() {
  const error = useRouteError();
  return <p>Failed to load this product: {error.message}</p>;
}
```

--> This gives per-route error isolation for free -- a failure loading one specific product's data shows a contained error just for that route, rather than needing a manually-placed Error Boundary wrapping every individual route by hand.
