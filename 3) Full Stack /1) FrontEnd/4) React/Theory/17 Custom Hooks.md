# What a Custom Hook Is

--> A custom Hook is just a JavaScript function whose name starts with `use` and that calls other Hooks (`useState`, `useEffect`, etc.) inside it -- it's a way to extract and REUSE stateful logic between components, without repeating that logic in every component that needs it.
--> Custom Hooks don't share state between components that use them -- each call gets its own independent instance of the state, exactly like calling `useState` directly in each component would.

# Why Extract Logic Into a Custom Hook

--> Before custom Hooks, sharing stateful logic across components meant Higher-Order Components or render props (both add extra wrapping layers to the component tree). Custom Hooks share the LOGIC directly, with no extra component nesting at all.

# Example -- useFetch

```javascript
function useFetch(url) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);

    fetch(url)
      .then(res => res.json())
      .then(json => { if (!cancelled) { setData(json); setLoading(false); } })
      .catch(err => { if (!cancelled) { setError(err); setLoading(false); } });

    return () => { cancelled = true; };   // Avoid setting state after the component unmounts
  }, [url]);

  return { data, loading, error };
}
```

```javascript
function UserProfile({ userId }) {
  const { data, loading, error } = useFetch(`/api/users/${userId}`);

  if (loading) return <p>Loading...</p>;
  if (error) return <p>Error loading profile</p>;
  return <h1>{data.name}</h1>;
}
```

--> The exact same `useFetch` Hook can now be reused in any component needing to fetch data, with each usage getting its own independent `data`/`loading`/`error` state.

# Example -- useLocalStorage

```javascript
function useLocalStorage(key, initialValue) {
  const [value, setValue] = useState(() => {
    const stored = localStorage.getItem(key);
    return stored ? JSON.parse(stored) : initialValue;
  });

  useEffect(() => {
    localStorage.setItem(key, JSON.stringify(value));
  }, [key, value]);

  return [value, setValue];
}
```

```javascript
const [theme, setTheme] = useLocalStorage("theme", "light");
```

# Rules of Hooks Apply to Custom Hooks Too

--> Only call Hooks at the top level of a function -- never inside conditionals, loops, or nested functions -- and only call them from React function components or other custom Hooks, never from plain JS functions. These rules exist because React tracks Hook state by CALL ORDER between renders; breaking that order desyncs state.

# Composing Custom Hooks

--> Custom Hooks can call other custom Hooks, letting you build higher-level behavior out of smaller, independently reusable pieces.

```javascript
function useAuthenticatedFetch(url) {
  const { token } = useAuth();          // Another custom hook
  const result = useFetch(url, { headers: { Authorization: `Bearer ${token}` } });
  return result;
}
```
