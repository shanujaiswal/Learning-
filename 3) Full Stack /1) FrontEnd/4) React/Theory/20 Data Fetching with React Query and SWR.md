# The Problem With Fetching Data Manually

--> Plain `useEffect` + `fetch` (as shown in the Custom Hooks file's `useFetch` example) technically works, but re-implementing loading/error states, caching, re-fetching on window focus, retrying failed requests, and avoiding duplicate simultaneous requests for the same data quickly becomes a lot of repeated, error-prone boilerplate across a real app.
--> Data-fetching libraries (React Query / TanStack Query, and SWR) solve this whole category of problems as a dedicated, battle-tested layer -- often described as a "server state" management library, distinct from client state tools like Redux/Zustand.

# React Query (TanStack Query)

--> Wraps any async fetch function with automatic caching, background re-fetching, loading/error state, and request de-duplication -- all keyed by a "query key" you provide.

```javascript
import { useQuery } from "@tanstack/react-query";

function UserProfile({ userId }) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["user", userId],
    queryFn: () => fetch(`/api/users/${userId}`).then(res => res.json()),
  });

  if (isLoading) return <p>Loading...</p>;
  if (isError) return <p>Error: {error.message}</p>;
  return <h1>{data.name}</h1>;
}
```

--> The query key (`["user", userId]`) is the cache identity -- calling `useQuery` with the same key anywhere else in the app instantly reuses the cached data instead of re-fetching, and a change to `userId` automatically triggers a re-fetch for the new key.

# Mutations -- Writing Data

```javascript
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useUpdateUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (updatedUser) =>
      fetch(`/api/users/${updatedUser.id}`, {
        method: "PUT",
        body: JSON.stringify(updatedUser),
      }),
    onSuccess: (data, updatedUser) => {
      queryClient.invalidateQueries({ queryKey: ["user", updatedUser.id] });   // Refetch fresh data
    },
  });
}
```

--> `invalidateQueries` marks matching cached queries as stale, triggering an automatic background re-fetch -- keeping the UI in sync with the server after a write, without manually managing that logic yourself.

# Automatic Background Behavior

--> Refetch on window focus -- if a user switches tabs away and back, data is automatically refreshed, catching changes made elsewhere.
--> Stale-while-revalidate -- cached data is shown IMMEDIATELY (no loading spinner on repeat visits) while a fresh copy is fetched silently in the background and swapped in when ready.
--> Automatic retry with backoff on failed requests, configurable per query.

# SWR -- A Lighter Alternative

--> Built by Vercel, SWR ("stale-while-revalidate," the same caching strategy by name) offers a similar core value proposition to React Query with a smaller API surface and bundle size -- a common choice for projects (often Next.js ones) that don't need React Query's more extensive mutation/pagination feature set.

```javascript
import useSWR from "swr";

const fetcher = (url) => fetch(url).then(res => res.json());

function UserProfile({ userId }) {
  const { data, error, isLoading } = useSWR(`/api/users/${userId}`, fetcher);

  if (isLoading) return <p>Loading...</p>;
  if (error) return <p>Error loading user</p>;
  return <h1>{data.name}</h1>;
}
```

# When to Reach for One of These

--> Any component fetching data from a server is a candidate -- these libraries have effectively become the default choice over hand-rolled `useEffect` fetching in modern React codebases, precisely because caching/refetching/race-condition handling are genuinely hard to get right by hand and easy to get for free here.
