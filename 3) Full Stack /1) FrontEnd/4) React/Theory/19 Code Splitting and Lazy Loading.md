# The Problem -- One Giant Bundle

--> By default, a React build bundles the ENTIRE application's JavaScript into one (or a few) files -- every route, every rarely-used feature, all downloaded before the user sees anything, even if they only ever visit the homepage.
--> Code splitting breaks the bundle into smaller chunks, loaded ONLY when actually needed -- dramatically improving initial load time for large applications.

# React.lazy and Suspense

--> `React.lazy()` lets you import a component dynamically -- its code isn't included in the main bundle at all, and only gets fetched from the network the first time it's actually rendered.
--> Must be wrapped in `<Suspense>`, which shows a fallback UI while the lazy component's code is still being downloaded.

```javascript
import { lazy, Suspense } from "react";

const Dashboard = lazy(() => import("./Dashboard"));
const Settings = lazy(() => import("./Settings"));

function App() {
  return (
    <Suspense fallback={<p>Loading page...</p>}>
      <Dashboard />
    </Suspense>
  );
}
```

--> Under the hood, `import("./Dashboard")` is a dynamic import -- a standard JS feature (not React-specific) that returns a Promise resolving to the module, which bundlers like Webpack/Vite automatically split into a separate chunk file.

# Route-Based Code Splitting

--> The most common and highest-value place to apply code splitting -- each route/page only downloads its own code when the user actually navigates there, instead of the whole app's routes loading upfront.

```javascript
import { lazy, Suspense } from "react";
import { Routes, Route } from "react-router-dom";

const Home = lazy(() => import("./pages/Home"));
const Profile = lazy(() => import("./pages/Profile"));
const Settings = lazy(() => import("./pages/Settings"));

function App() {
  return (
    <Suspense fallback={<p>Loading...</p>}>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/profile" element={<Profile />} />
        <Route path="/settings" element={<Settings />} />
      </Routes>
    </Suspense>
  );
}
```

# Component-Level Splitting

--> Beyond whole routes, heavy individual components that aren't needed immediately (a rich text editor, a charting library, a modal that's rarely opened) are also good lazy-loading candidates.

```javascript
const HeavyChart = lazy(() => import("./HeavyChart"));

function AnalyticsPage() {
  const [showChart, setShowChart] = useState(false);

  return (
    <div>
      <button onClick={() => setShowChart(true)}>Show Chart</button>
      {showChart && (
        <Suspense fallback={<p>Loading chart...</p>}>
          <HeavyChart />
        </Suspense>
      )}
    </div>
  );
}
```

# Error Handling for Lazy Components

--> A failed dynamic import (network failure while fetching the chunk) throws an error during rendering -- pair lazy-loaded routes/components with an Error Boundary (covered in the previous file) so a failed chunk load shows a retry message instead of crashing the app.

```javascript
<ErrorBoundary fallback={<p>Failed to load. Please retry.</p>}>
  <Suspense fallback={<p>Loading...</p>}>
    <Dashboard />
  </Suspense>
</ErrorBoundary>
```

# Measuring the Impact

--> Bundle analyzer tools (`webpack-bundle-analyzer`, Vite's built-in visualizer) show exactly how large each chunk is and what's inside it -- essential for identifying which parts of an app are actually worth splitting out, rather than guessing.
