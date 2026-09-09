# Fragments

--> Lets you group a list of children without adding an extra node to the DOM.
--> Written as <React.Fragment> ... </React.Fragment> or the shorthand <> ... </>
--> Needed because a component can only return a single parent element -- Fragments avoid wrapping everything in an unnecessary <div>.
--> The full <React.Fragment key={id}> syntax is required (instead of <>) when a key needs to be passed, e.g. inside a list rendered with .map().

# Error Boundaries

--> A React component that catches JavaScript errors anywhere in its child component tree, logs them, and displays a fallback UI instead of crashing the whole app.
--> Must be a class component that implements static getDerivedStateFromError() and/or componentDidCatch() -- there's no Hook equivalent yet.
--> Only catches errors during rendering, lifecycle methods, and constructors of the components below it -- it does NOT catch errors in event handlers, async code, or errors in itself.
--> Usage: wrap a part of the tree that might fail --> <ErrorBoundary><RiskyComponent /></ErrorBoundary>

# Higher-Order Components (HOC)

--> A function that takes a component and returns a new, enhanced component: const EnhancedComponent = withSomething(MyComponent)
--> Used to reuse component logic across multiple components (e.g. withAuth, withLogging) -- similar goal to custom Hooks, but Hooks are generally preferred in modern React.
--> Naming convention: HOCs are usually prefixed with "with" (withRouter, withStyles, etc.)

# PropTypes and defaultProps

--> PropTypes is a library used to type-check the props a component receives at runtime (useful before/alongside TypeScript).
--> import PropTypes from "prop-types"; MyComponent.propTypes = { name: PropTypes.string.isRequired, age: PropTypes.number }
--> Logs a console warning in development if a prop doesn't match the expected type -- has no effect in production.
--> defaultProps (or default parameter values in the function signature) supplies a fallback value when a prop isn't passed.

# Suspense and Lazy Loading

--> React.lazy(() => import("./Component")) lets you code-split a component so it's only loaded when actually rendered.
--> Must be wrapped in <Suspense fallback={<Loading />}> to show a fallback UI while the lazy component's code is being fetched.
--> Commonly combined with route-based code splitting so each page is its own chunk (see React Router notes).

# Portals

--> ReactDOM.createPortal(child, domNode) renders children into a DOM node that exists outside the parent component's DOM hierarchy.
--> Commonly used for modals, tooltips, and dropdowns that need to visually "break out" of a parent with overflow:hidden or a specific z-index stacking context.
--> Even though the DOM node is different, the portal still behaves like a normal React child for event bubbling and context.

# Render Props Pattern

--> A component takes a function as a prop (often named render or children) and calls it to decide what to render, sharing logic/state without a HOC.
--> <MouseTracker render={({ x, y }) => <p>{x}, {y}</p>} /> -- MouseTracker tracks mouse position internally and hands it to whatever the caller wants to render.
--> Largely superseded by custom Hooks in modern React, but still seen in some component libraries.

# Compound Components Pattern

--> A group of components that work together to form one UI unit, sharing implicit state via Context, while the parent controls composition/markup.
--> Example shape: <Tabs><Tabs.List><Tabs.Tab>...</Tabs.Tab></Tabs.List><Tabs.Panels>...</Tabs.Panels></Tabs> -- Tabs provides context, its sub-components consume it.
--> Gives the consumer flexibility over layout/markup while the components still coordinate behavior internally (used heavily in headless UI libraries).

# Provider Pattern (Context + Custom Hook)

--> Wrap a Context.Provider inside its own component, and expose a paired custom Hook to read it, instead of consumers calling useContext directly.
--> function AuthProvider({ children }) { const [user, setUser] = useState(null); return <AuthContext.Provider value={{ user, setUser }}>{children}</AuthContext.Provider> } and function useAuth() { return useContext(AuthContext); }
--> Keeps the Context object itself private to the module, lets the hook throw a helpful error if used outside the provider, and centralizes the state logic that goes with the context.

# Performance Optimization

==> React DevTools Profiler
--> A browser extension tab that records render timings for each component, showing WHY a component re-rendered (props changed, state changed, parent re-rendered, context changed) and HOW LONG each render took -- the first place to look before trying to optimize anything.

==> Code-Splitting Strategy
--> Beyond basic `React.lazy()` for a single component (see Suspense/Lazy Loading above), the most common real-world strategy is route-based splitting -- each page/route becomes its own JS chunk, so users only download the code for the page they're actually visiting.
```javascript
const Dashboard = lazy(() => import("./pages/Dashboard"));
const Settings = lazy(() => import("./pages/Settings"));
// Paired with React Router + <Suspense> around <Routes>, each route loads its own chunk on demand
```
--> Large, rarely-used components (modals, rich text editors, charting libraries) are also good lazy-load candidates even outside route boundaries.

==> Virtualization / Windowing for Large Lists
--> Rendering thousands of DOM nodes (e.g. a list of 10,000 rows) is slow and memory-heavy, even if only ~20 are visible on screen at once.
--> Virtualization libraries (`react-window`, `react-virtualized`, `@tanstack/react-virtual`) render ONLY the currently-visible rows (plus a small buffer), recycling DOM nodes as the user scrolls -- turning an O(n) render cost into roughly O(visible items).
```javascript
import { FixedSizeList } from "react-window";

<FixedSizeList height={400} itemCount={10000} itemSize={35} width={300}>
  {({ index, style }) => <div style={style}>Row {index}</div>}
</FixedSizeList>
```

==> Memoization Recap
--> `React.memo`, `useMemo`, `useCallback` (covered in file 06) are the other main performance levers -- but should be applied only where profiling shows an actual bottleneck, not by default everywhere, since memoization itself has a (usually small) cost.

# Security in React

==> dangerouslySetInnerHTML and XSS
--> By default, React automatically escapes any value rendered in JSX (`{userInput}`), preventing Cross-Site Scripting (XSS) -- this is one of React's biggest built-in security wins.
--> `dangerouslySetInnerHTML` bypasses that protection entirely, injecting raw HTML directly into the DOM -- its name is a deliberate warning.
```javascript
// DANGEROUS if `userComment` comes from user input and isn't sanitized:
<div dangerouslySetInnerHTML={{ __html: userComment }} />
```
--> If raw HTML must be rendered (e.g. rich text from a CMS/editor), always sanitize it first with a library like `DOMPurify`:
```javascript
import DOMPurify from "dompurify";
<div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(userComment) }} />
```
--> Other React-specific security notes: never put secrets/API keys in client-side code (anything in a React bundle is publicly visible), and validate/sanitize on the SERVER too -- client-side checks alone are not real security since they can be bypassed by calling the API directly.

# Accessibility (a11y) in React

--> Semantic HTML first -- prefer `<button>` over a `<div onClick={...}>`, real form `<label>`s, and proper heading hierarchy; React doesn't change any of the underlying HTML accessibility rules covered in the HTML theory notes.
--> `useId` (see React Hooks notes) generates stable, unique ids across server/client renders -- the standard way to correctly link a `<label htmlFor={id}>` to its `<input id={id}>` when rendering dynamic/repeated form fields.
--> Manage focus explicitly after route changes, modal opens, and error messages appear -- e.g. calling `.focus()` via a `ref` when a `<Dialog>` opens, so keyboard/screen-reader users land somewhere meaningful instead of losing their place.
--> Error Boundaries (see above) should render an accessible fallback (not just a blank screen) -- include a heading and readable message, not just a console log.
--> Test with the `eslint-plugin-jsx-a11y` plugin, which lints for common accessibility mistakes (missing `alt`, non-interactive elements with click handlers, etc.) directly in JSX.
