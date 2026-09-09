/**
 * 02-custom-hook-useFetch.jsx
 *
 * DEMONSTRATES:
 *  - Building a real, reusable CUSTOM HOOK: useFetch(url)
 *  - loading / error / data state management
 *  - Cancelling an in-flight request with AbortController inside the
 *    useEffect cleanup function (prevents "setState after unmount" and
 *    race conditions when the url changes quickly, e.g. fast typing)
 *
 * Maps to Theory chapters: 07) Hooks, 17) Custom Hooks, 20) Data Fetching
 *
 * Usage: drop into any Vite/CRA project. Replace the demo URL with a real
 * endpoint. Works with any REST API that returns JSON.
 */

import { useState, useEffect } from "react";

/**
 * useFetch — generic data-fetching hook.
 *
 * @param {string} url - endpoint to fetch. Pass null/undefined to skip.
 * @returns {{ data: any, loading: boolean, error: Error|null }}
 */
function useFetch(url) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(Boolean(url));
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!url) {
      setLoading(false);
      return undefined;
    }

    const controller = new AbortController();
    let isActive = true; // extra guard alongside AbortController

    setLoading(true);
    setError(null);

    fetch(url, { signal: controller.signal })
      .then((res) => {
        if (!res.ok) {
          throw new Error(`Request failed with status ${res.status}`);
        }
        return res.json();
      })
      .then((json) => {
        if (isActive) {
          setData(json);
          setLoading(false);
        }
      })
      .catch((err) => {
        // Ignore the error thrown by our own abort — it's not a real failure.
        if (err.name === "AbortError") return;
        if (isActive) {
          setError(err);
          setLoading(false);
        }
      });

    // Cleanup: runs when `url` changes again OR the component unmounts.
    // Aborting the fetch prevents a stale/late response from overwriting
    // fresher state (a classic race condition), and stops any state
    // update from firing after the component is gone.
    return () => {
      isActive = false;
      controller.abort();
    };
  }, [url]);

  return { data, loading, error };
}

/* ---------------------------------------------------------------------- */
/* Example consumer component                                             */
/* ---------------------------------------------------------------------- */
export default function UserProfile({ userId = 1 }) {
  const url = `https://jsonplaceholder.typicode.com/users/${userId}`;
  const { data: user, loading, error } = useFetch(url);

  if (loading) return <p>Loading user...</p>;
  if (error) return <p>Error: {error.message}</p>;
  if (!user) return null;

  return (
    <div style={{ fontFamily: "sans-serif", padding: 16 }}>
      <h3>{user.name}</h3>
      <p>{user.email}</p>
      <p>{user.company?.name}</p>
    </div>
  );
}

export { useFetch };
