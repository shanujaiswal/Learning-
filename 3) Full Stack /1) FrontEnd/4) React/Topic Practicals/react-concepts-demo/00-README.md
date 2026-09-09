# React Concepts Demo — Snippet Reference

This folder is a personal **snippets reference**, not a runnable scaffolded
project. Two full scaffolded projects (`my-app` and `sticky-note`) already
live alongside this folder in `Practical/`, each with their own
`node_modules` — there's no need for a third one here.

Each `.jsx` file below is self-contained: a component (or small set of
components) plus a comment block explaining what it demonstrates and why.
Assume any file here can be dropped straight into an existing Vite or
Create React App project and rendered — no build setup of its own.

## Index

| File | Concepts demonstrated | Maps to Theory chapter(s) |
|---|---|---|
| `01-hooks-fundamentals.jsx` | `useState`, `useEffect` (with cleanup), `useRef` (DOM access + persisting a value without re-render) | 07) Hooks |
| `02-custom-hook-useFetch.jsx` | Custom hook `useFetch` with loading/error/data state and `AbortController` cleanup | 07) Hooks, 17) Custom Hooks, 20) Data Fetching |
| `03-context-api-theme.jsx` | `createContext` + Provider + `useContext` consumer, avoiding prop drilling | 12) Context API |
| `04-usereducer-and-forms.jsx` | Controlled multi-field form managed with `useReducer` instead of multiple `useState` calls | 04) Forms, 07) Hooks |
| `05-error-boundary-and-suspense.jsx` | Class-based `ErrorBoundary` (`getDerivedStateFromError`/`componentDidCatch`) + `React.lazy` with `Suspense` | 10) Class Components/Lifecycle, 18) Error Boundaries, 19) Code Splitting/Lazy Loading, 15) Server Components/Suspense |
| `06-performance-memo-and-callback.jsx` | `React.memo` on a child, `useMemo` for an expensive calculation, `useCallback` for a stable function reference — with comments on *why* each prevents a re-render | 06) Memo/Styling, 07) Hooks |
| `07-react-query-data-fetching.jsx` | `useQuery` / `useMutation` against a REST endpoint with cache invalidation on mutation success | 20) Data Fetching (React Query/SWR) |

## Notes

- These files intentionally do not form a single running app — each is an
  independent reference snippet you can copy from.
- `07-react-query-data-fetching.jsx` assumes `@tanstack/react-query` is
  already installed and a `QueryClientProvider` exists at the app root in a
  real project (this file includes its own for a self-contained example).
- The Theory folder (`2) Full Stack/1) FrontEnd/4) React/Theory/`) remains
  read-only reference material; nothing there was modified.
- `my-app` and `sticky-note` (including their `node_modules`) were left
  completely untouched.
