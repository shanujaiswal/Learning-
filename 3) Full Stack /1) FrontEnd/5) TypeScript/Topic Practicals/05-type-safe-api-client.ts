/**
 * 05 - A Type-Safe API Client (typed fetch wrapper)
 * Covers Theory chapters: 05 (Generics Deep Dive), 06 (Utility Types),
 *   08 (Type Narrowing and Guards) -- and connects conceptually to the
 *   vault's Node/Express and Full Stack API notes: the *server* defines
 *   response shapes, and typing the *client* against those same shapes
 *   prevents an entire class of "the API changed and the UI didn't notice"
 *   bugs at compile time instead of at runtime.
 *
 * Run:   npx tsc --noEmit "05-type-safe-api-client.ts"
 * Or:    npx ts-node "05-type-safe-api-client.ts"
 *
 * Note: this file type-checks standalone. Actually executing it needs a
 * `fetch` implementation in scope (built into modern Node/browsers, or
 * available via `"lib": ["dom"]` / `"lib": ["esnext"]` in tsconfig).
 */

// ---------------------------------------------------------------------------
// Shapes returned by the API (mirrors what an Express/Node backend would
// send as JSON). Keeping these as named interfaces means the client and
// any UI code consuming it share one source of truth for the response shape.
// ---------------------------------------------------------------------------
interface User {
  id: string;
  name: string;
  email: string;
}

interface Post {
  id: string;
  authorId: string;
  title: string;
  body: string;
  publishedAt: string; // ISO date string, as JSON has no native Date type
}

// A generic envelope some APIs use to wrap every response.
interface ApiEnvelope<T> {
  success: boolean;
  data: T;
}

// A typed shape for API error responses, so failures can be narrowed too.
interface ApiErrorBody {
  success: false;
  error: {
    code: string;
    message: string;
  };
}

// ---------------------------------------------------------------------------
// A small typed error class so callers can `catch` and inspect structured
// info instead of parsing strings.
// ---------------------------------------------------------------------------
class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
  ) {
    super(`API error ${status}: ${code}`);
    this.name = "ApiError";
  }
}

// Runtime type guard: narrows an unknown JSON body to ApiErrorBody.
function isApiErrorBody(body: unknown): body is ApiErrorBody {
  return (
    typeof body === "object" &&
    body !== null &&
    (body as { success?: unknown }).success === false &&
    typeof (body as { error?: unknown }).error === "object"
  );
}

// ---------------------------------------------------------------------------
// The generic fetch wrapper. Callers specify what shape `T` they expect
// back, and get a `Promise<T>` -- no `any` involved, and no repeated
// `res.json() as SomeType` casts scattered through the codebase.
// ---------------------------------------------------------------------------
async function apiGet<T>(url: string): Promise<T> {
  const res = await fetch(url, {
    method: "GET",
    headers: { Accept: "application/json" },
  });

  const body: unknown = await res.json();

  if (!res.ok) {
    if (isApiErrorBody(body)) {
      throw new ApiError(res.status, body.error.code);
    }
    throw new ApiError(res.status, "UNKNOWN_ERROR");
  }

  // We trust the caller-specified `T` here (a "trusted cast"), the same way
  // a hand-written runtime validator/schema would ultimately hand back a
  // typed value. In a production client this line is where a schema
  // validator (zod, io-ts, etc.) would go instead of a direct cast.
  return body as T;
}

async function apiPost<TBody, TResponse>(url: string, payload: TBody): Promise<TResponse> {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(payload),
  });

  const body: unknown = await res.json();

  if (!res.ok) {
    if (isApiErrorBody(body)) {
      throw new ApiError(res.status, body.error.code);
    }
    throw new ApiError(res.status, "UNKNOWN_ERROR");
  }

  return body as TResponse;
}

// ---------------------------------------------------------------------------
// Usage examples: the return type of each call is fully inferred, so
// autocompletion and compile-time checks work all the way through.
// ---------------------------------------------------------------------------
async function loadUserAndPosts(userId: string): Promise<{ user: User; posts: Post[] }> {
  const userEnvelope = await apiGet<ApiEnvelope<User>>(`/api/users/${userId}`);
  const postsEnvelope = await apiGet<ApiEnvelope<Post[]>>(`/api/users/${userId}/posts`);

  const user = userEnvelope.data; // typed as User, no cast needed
  const posts = postsEnvelope.data; // typed as Post[]

  // Because `user` is typed, this typo would be caught at compile time:
  // console.log(user.naem);
  console.log(`Loaded ${user.name} with ${posts.length} post(s)`);

  return { user, posts };
}

interface CreatePostInput {
  title: string;
  body: string;
}

async function createPost(authorId: string, input: CreatePostInput): Promise<Post> {
  const envelope = await apiPost<CreatePostInput, ApiEnvelope<Post>>(
    `/api/users/${authorId}/posts`,
    input,
  );
  return envelope.data;
}

async function main(): Promise<void> {
  try {
    const { user, posts } = await loadUserAndPosts("u1");
    const created = await createPost(user.id, {
      title: "Typed clients FTW",
      body: "Generics make the API layer safer.",
    });
    console.log("Existing posts:", posts.length, "| newly created:", created.title);
  } catch (err) {
    if (err instanceof ApiError) {
      console.error(`Request failed (${err.status}): ${err.code}`);
    } else {
      console.error("Unexpected error:", err);
    }
  }
}

// Not invoked automatically at module load in this notes file -- call
// `main()` yourself in an environment with a real API and `fetch`.
export {
  User,
  Post,
  ApiEnvelope,
  ApiErrorBody,
  ApiError,
  apiGet,
  apiPost,
  loadUserAndPosts,
  createPost,
  main,
};
