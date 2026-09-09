/**
 * 07-react-query-data-fetching.jsx
 *
 * DEMONSTRATES:
 *  - @tanstack/react-query's useQuery for fetching + caching REST data
 *  - useMutation for creating/updating data
 *  - Cache invalidation on mutation success via queryClient.invalidateQueries
 *    so the list automatically refetches fresh data after a write
 *
 * Maps to Theory chapter: 20) Data Fetching (React Query / SWR)
 *
 * PREREQUISITE (not included, since this is a reference snippet, not a
 * runnable project): npm install @tanstack/react-query
 * and wrap your app root in a <QueryClientProvider client={queryClient}>.
 *
 * Usage: drop into any Vite/CRA project that already has react-query set up.
 */

import {
  QueryClient,
  QueryClientProvider,
  useQuery,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";

const API_BASE = "https://jsonplaceholder.typicode.com";

/* ---------------------------------------------------------------------- */
/* Plain fetch helpers (the actual REST calls)                            */
/* ---------------------------------------------------------------------- */
async function fetchPosts() {
  const res = await fetch(`${API_BASE}/posts?_limit=5`);
  if (!res.ok) throw new Error("Failed to fetch posts");
  return res.json();
}

async function createPost(newPost) {
  const res = await fetch(`${API_BASE}/posts`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(newPost),
  });
  if (!res.ok) throw new Error("Failed to create post");
  return res.json();
}

/* ---------------------------------------------------------------------- */
/* useQuery — fetch + cache the posts list                                */
/* ---------------------------------------------------------------------- */
function PostList() {
  const {
    data: posts,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ["posts"], // cache key — anything using this key shares the cache
    queryFn: fetchPosts,
    staleTime: 30_000, // consider data fresh for 30s before refetching
  });

  const queryClient = useQueryClient();

  // ----------------------------------------------------------------------
  // useMutation — create a post, then invalidate the "posts" query so
  // React Query refetches the list automatically, keeping the UI in sync
  // with the server without any manual cache manipulation.
  // ----------------------------------------------------------------------
  const {
    mutate: addPost,
    isPending: isCreating,
  } = useMutation({
    mutationFn: createPost,
    onSuccess: () => {
      // Marks the "posts" query as stale and triggers a refetch for any
      // component currently subscribed to it.
      queryClient.invalidateQueries({ queryKey: ["posts"] });
    },
  });

  if (isLoading) return <p>Loading posts...</p>;
  if (isError) return <p>Error: {error.message}</p>;

  return (
    <div>
      <ul>
        {posts.map((post) => (
          <li key={post.id}>{post.title}</li>
        ))}
      </ul>

      <button
        disabled={isCreating}
        onClick={() =>
          addPost({ title: "New post from React Query demo", userId: 1 })
        }
      >
        {isCreating ? "Adding..." : "Add post"}
      </button>
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* Top-level wiring (QueryClientProvider is normally set up once, at the   */
/* root of the app — shown here for a self-contained, runnable example)    */
/* ---------------------------------------------------------------------- */
const queryClient = new QueryClient();

export default function ReactQueryDemo() {
  return (
    <QueryClientProvider client={queryClient}>
      <div style={{ fontFamily: "sans-serif", padding: 16 }}>
        <h2>React Query Data Fetching Demo</h2>
        <PostList />
      </div>
    </QueryClientProvider>
  );
}
