# The Problem -- One Component Crash Shouldn't Kill the Whole App

--> By default, a JavaScript error thrown during rendering unmounts the ENTIRE React component tree, leaving a blank white screen -- one broken widget takes down the whole page for the user.
--> Error Boundaries catch JavaScript errors ANYWHERE in their child component tree, log them, and display a fallback UI instead of crashing the whole app.

# What Error Boundaries Can and Cannot Catch

--> They DO catch errors during rendering, in lifecycle methods, and in constructors of the components below them in the tree.
--> They do NOT catch errors in: event handlers (use a regular `try/catch` there instead), async code (`setTimeout`, promises), server-side rendering, or errors thrown in the Error Boundary itself.

# Class Component Requirement

--> As of current React versions, Error Boundaries can ONLY be implemented as class components -- there is no Hook equivalent (`useErrorBoundary` doesn't exist as a built-in Hook), because the underlying lifecycle methods they rely on have no functional-component equivalent yet.

```javascript
class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error) {
    // Runs during the render phase -- update state to trigger the fallback UI
    return { hasError: true };
  }

  componentDidCatch(error, info) {
    // Runs during the commit phase -- side effects like logging are safe here
    console.error("Caught by ErrorBoundary:", error, info);
    logErrorToService(error, info);
  }

  render() {
    if (this.state.hasError) {
      return <h2>Something went wrong. Please refresh the page.</h2>;
    }
    return this.props.children;
  }
}
```

```javascript
<ErrorBoundary>
  <UserDashboard />
</ErrorBoundary>
```

# Placement Strategy

--> A single Error Boundary at the very top of the app is simple but coarse -- ANY error anywhere blanks the entire app with one fallback message.
--> Placing multiple, smaller Error Boundaries around independent sections (a sidebar widget, a comments section, a chart) contains a crash to just that section, letting the rest of the page keep working -- generally the better real-world pattern for anything beyond a trivial app.

```javascript
<Layout>
  <ErrorBoundary fallback={<SidebarError />}><Sidebar /></ErrorBoundary>
  <ErrorBoundary fallback={<FeedError />}><MainFeed /></ErrorBoundary>
</Layout>
```

# react-error-boundary (Community Library)

--> A widely used library providing a functional-component-friendly API (`<ErrorBoundary>` as a wrapper you configure with props, plus a `useErrorHandler` Hook) so most day-to-day usage doesn't require hand-writing the class boilerplate above.

```javascript
import { ErrorBoundary } from "react-error-boundary";

<ErrorBoundary
  fallback={<p>Something went wrong.</p>}
  onError={(error, info) => logErrorToService(error, info)}
>
  <UserDashboard />
</ErrorBoundary>
```

# Combining With Error Reporting Services

--> In production, `componentDidCatch`/`onError` is the natural place to forward caught errors to a monitoring service (Sentry, Bugsnag) so crashes are visible to the team even though the user just sees a graceful fallback message instead of a blank page.
