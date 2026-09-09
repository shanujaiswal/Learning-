/**
 * 05-error-boundary-and-suspense.jsx
 *
 * DEMONSTRATES:
 *  - A class-based ErrorBoundary (getDerivedStateFromError + componentDidCatch)
 *    catching a render-time error thrown by a child component.
 *  - React.lazy + <Suspense> for code-splitting: the lazy component's code
 *    is only downloaded when it's actually rendered, showing a fallback UI
 *    while it loads.
 *
 * Maps to Theory chapters: 10) Class Components/Lifecycle, 18) Error
 *                           Boundaries, 19) Code Splitting/Lazy Loading,
 *                           15) Server Components/Suspense
 *
 * NOTE: Error boundaries currently MUST be class components — there is no
 * hook equivalent (as of React 18/19) for getDerivedStateFromError.
 *
 * Usage: drop into any Vite/CRA project. `./HeavyChart` below is a
 * placeholder import path — point it at any component you want to
 * code-split in your real project.
 */

import { Component, Suspense, lazy, useState } from "react";

/* ---------------------------------------------------------------------- */
/* 1) Error Boundary (class component — required by React)                */
/* ---------------------------------------------------------------------- */
class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  // Called during rendering, right after a descendant throws.
  // Must return the new state synchronously.
  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  // Called after an error is caught — good place for side effects like
  // logging to an error-reporting service (Sentry, etc.).
  componentDidCatch(error, info) {
    console.error("ErrorBoundary caught an error:", error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ color: "red" }}>
          <p>Something went wrong: {this.state.error?.message}</p>
          <button
            onClick={() => this.setState({ hasError: false, error: null })}
          >
            Try again
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

/* ---------------------------------------------------------------------- */
/* 2) A component that can throw during render                            */
/* ---------------------------------------------------------------------- */
function BuggyCounter({ shouldThrow }) {
  if (shouldThrow) {
    throw new Error("Boom! Counter blew up.");
  }
  return <p>Counter is fine.</p>;
}

/* ---------------------------------------------------------------------- */
/* 3) React.lazy — code-split component, only loaded when rendered        */
/* ---------------------------------------------------------------------- */
// In a real project this would point at a real file, e.g. "./HeavyChart".
// Here it's simulated with a dynamic import wrapped in a Promise/timeout
// so this file stays fully self-contained.
const LazyHeavyWidget = lazy(
  () =>
    new Promise((resolve) => {
      setTimeout(() => {
        resolve({
          default: function HeavyWidget() {
            return <p>Heavy widget loaded via React.lazy!</p>;
          },
        });
      }, 1000);
    })
);

/* ---------------------------------------------------------------------- */
/* 4) Demo wiring                                                          */
/* ---------------------------------------------------------------------- */
export default function ErrorBoundaryAndSuspenseDemo() {
  const [shouldThrow, setShouldThrow] = useState(false);
  const [showLazy, setShowLazy] = useState(false);

  return (
    <div style={{ fontFamily: "sans-serif", padding: 16 }}>
      <h2>Error Boundary Demo</h2>
      <ErrorBoundary>
        <BuggyCounter shouldThrow={shouldThrow} />
      </ErrorBoundary>
      <button onClick={() => setShouldThrow(true)}>Trigger error</button>

      <hr />

      <h2>Suspense + React.lazy Demo</h2>
      <button onClick={() => setShowLazy(true)}>Load heavy widget</button>
      {showLazy && (
        <Suspense fallback={<p>Loading widget...</p>}>
          <LazyHeavyWidget />
        </Suspense>
      )}
    </div>
  );
}
