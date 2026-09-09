# REST API Basics

--> REST (Representational State Transfer) is an architectural style for designing networked APIs -- not a strict protocol, just a set of conventions built on top of HTTP.
--> A "RESTful" API exposes **resources** (things like users, posts, products) as URLs, and uses HTTP methods to describe what action to perform on that resource.

# HTTP Methods (Verbs)

--> GET --> Retrieve a resource (or list of resources). Should never modify data (safe/idempotent).
--> POST --> Create a new resource. Not idempotent -- calling it twice usually creates two resources.
--> PUT --> Replace/update an entire resource with new data. Idempotent -- calling it twice with the same data has the same end result.
--> PATCH --> Partially update a resource (only the fields provided). Not necessarily idempotent depending on implementation.
--> DELETE --> Remove a resource. Idempotent -- deleting an already-deleted resource still results in "it's gone."

# Resource Naming Conventions

--> URLs should represent resources (nouns), not actions (verbs) -- the HTTP method already provides the action.
--> Good: `GET /users`, `GET /users/5`, `POST /users`, `DELETE /users/5`
--> Bad: `GET /getAllUsers`, `POST /createUser`, `POST /deleteUser/5`
--> Use plural nouns for collections (`/users` not `/user`), and nest related resources logically: `GET /users/5/posts` --> all posts belonging to user 5.
--> Use query parameters for filtering/sorting/pagination, not the path: `GET /users?role=admin&sort=name&page=2&limit=20`

# HTTP Status Codes Cheat Sheet

==> 2xx -- Success
--> 200 OK --> Standard success response (GET, PUT, PATCH, DELETE that return data).
--> 201 Created --> A new resource was successfully created (typical response to POST).
--> 204 No Content --> Success, but there's nothing to send back (common for DELETE).

==> 3xx -- Redirection
--> 301 Moved Permanently --> Resource has a new permanent URL.
--> 304 Not Modified --> Cached version is still valid, client can use its cached copy (used with ETags/If-Modified-Since).

==> 4xx -- Client Errors (something wrong with the request)
--> 400 Bad Request --> The request is malformed/invalid (e.g. missing required field, wrong data type).
--> 401 Unauthorized --> Authentication is required/missing (despite the name, this is about being logged in, not permissions).
--> 403 Forbidden --> Authenticated, but not allowed to access this resource (permissions issue).
--> 404 Not Found --> The resource doesn't exist.
--> 409 Conflict --> The request conflicts with the current state of the resource (e.g. trying to create a user with an email that already exists).
--> 422 Unprocessable Entity --> The request is well-formed but fails validation rules.
--> 429 Too Many Requests --> Rate limit exceeded.

==> 5xx -- Server Errors (something wrong on the server)
--> 500 Internal Server Error --> Generic catch-all -- something broke on the server, often an unhandled exception.
--> 502 Bad Gateway --> A server acting as a proxy/gateway got an invalid response from an upstream server.
--> 503 Service Unavailable --> Server is temporarily overloaded or down for maintenance.

# Request/Response Anatomy

--> Headers --> Key-value metadata sent with a request/response (e.g. `Content-Type: application/json`, `Authorization: Bearer <token>`).
--> Body --> The actual data payload, usually JSON for modern APIs (`Content-Type: application/json`).
--> Query Parameters --> `?key=value&key2=value2` appended to the URL -- used for filtering, sorting, pagination, optional options.
--> Path Parameters --> Part of the URL path itself that identifies a specific resource, e.g. the `5` in `/users/5`.

# Statelessness

--> REST APIs are stateless -- the server does not remember anything about the client between requests. Every request must contain all the information needed to process it (e.g. an auth token in the header), rather than relying on server-side session memory.
--> This is why JWTs (JSON Web Tokens) are popular with REST APIs -- the token itself carries the identity/claims, so the server doesn't need to store session state.

# Versioning APIs

--> As an API evolves, breaking changes need a way to not break existing clients. Common approaches:
--> URL versioning --> `/api/v1/users`, `/api/v2/users` -- simplest, most common, easy to see the version at a glance.
--> Header versioning --> `Accept: application/vnd.myapi.v2+json` -- keeps URLs clean but less visible/discoverable.

# REST vs GraphQL (Conceptual Comparison)

--> REST --> Multiple fixed endpoints, each returning a fixed shape of data -- simple, cacheable, but can lead to over-fetching (getting fields you don't need) or under-fetching (needing multiple requests to assemble one view).
--> GraphQL --> A single endpoint where the client specifies EXACTLY which fields it wants in the query itself -- solves over/under-fetching, but adds complexity (schema definition, resolvers) and is harder to cache with standard HTTP caching.
--> Rule of thumb: REST is simpler and battle-tested for most CRUD-style apps; GraphQL shines when clients have very different, flexible data needs (e.g. a mobile app and a web app needing different subsets of the same data).

# Testing APIs Manually

--> Postman / Thunder Client (VS Code extension) / Insomnia --> GUI tools to manually send requests (GET/POST/PUT/DELETE) with custom headers/body, without writing any frontend code -- essential for testing backend endpoints during development.
--> `curl` (see Linux Terminal.md) --> Command-line alternative for quick one-off requests, useful in scripts/CI.

# CORS (Cross-Origin Resource Sharing)

--> Browsers enforce the "Same-Origin Policy" by default -- JavaScript running on one origin (protocol + domain + port) cannot make requests to a different origin unless that origin explicitly allows it.
--> CORS is the mechanism a SERVER uses to relax this restriction -- by sending back specific response headers telling the browser "requests from this other origin are allowed."
--> Key headers:
    --> `Access-Control-Allow-Origin: https://example.com` (or `*` for any origin) -- which origins are allowed to read the response.
    --> `Access-Control-Allow-Methods: GET, POST, PUT, DELETE` -- which HTTP methods are allowed.
    --> `Access-Control-Allow-Headers` -- which custom headers the client is allowed to send.
--> Preflight requests --> For "non-simple" requests (e.g. PUT/DELETE, or custom headers like `Authorization`), the browser first sends an automatic `OPTIONS` request to ask the server "would you allow this actual request?" before sending the real one -- this happens transparently, without any code needed from the developer.
--> A CORS error (`has been blocked by CORS policy` in the console) is a BROWSER-side block based on missing/incorrect response headers from the server -- it is not something the frontend code can bypass; it must be fixed on the server by adding the right CORS headers (or via a proxy during development).
--> Common gotcha: CORS errors only happen in browsers -- tools like Postman or `curl` never trigger them, which is why "it works in Postman but not in my React app" is a classic CORS symptom.

# Authentication vs Authorization

--> Authentication (AuthN) --> Verifying WHO you are (e.g. logging in with a username/password).
--> Authorization (AuthZ) --> Verifying WHAT you're allowed to do once identified (e.g. only admins can delete users).
--> 401 Unauthorized technically means "not authenticated" (despite the name); 403 Forbidden means "authenticated, but not authorized for this action."

# JWT (JSON Web Tokens) -- High-Level Overview

--> A JWT is a compact, self-contained string used to represent a user's identity/claims, commonly used for stateless authentication in REST APIs.
--> Structure: three Base64-encoded parts separated by dots -- `header.payload.signature`
    --> Header --> Metadata about the token (algorithm used, token type).
    --> Payload --> The actual claims/data (e.g. user ID, role, expiry time) -- NOT encrypted, just encoded, so it should never contain sensitive secrets.
    --> Signature --> Created by signing the header+payload with a secret key known only to the server -- lets the server verify the token hasn't been tampered with.
--> Typical flow: user logs in --> server verifies credentials --> server creates and signs a JWT --> sends it to the client --> client stores it (often in an `HttpOnly` cookie or memory) and sends it back in the `Authorization: Bearer <token>` header on every subsequent request --> server verifies the signature (no database lookup needed) to confirm the request is legitimate.
--> Key property: JWTs are stateless -- the server doesn't need to store session data, it just re-verifies the signature each time. Downside: since the server doesn't track active tokens, a JWT can't easily be "revoked" before it naturally expires (mitigated with short expiry times + refresh tokens).

# OAuth Basics ("Login with Google/GitHub")

--> OAuth is a protocol that lets a user grant a third-party app limited access to their account on another service, WITHOUT sharing their password with that third-party app.
--> Typical flow ("Login with Google"): user clicks "Login with Google" --> redirected to Google's login/consent screen --> user approves --> Google redirects back to the app with an authorization code --> the app's server exchanges that code (plus a secret) for an access token --> the app uses the token to fetch basic profile info (name/email) from Google to create/log in the user.
--> The app never sees the user's actual Google password -- it only ever receives a token scoped to specific permissions (e.g. "read profile info" but not "read Gmail").

# Deep Dive -- The Richardson Maturity Model -- How "RESTful" Is an API, Really?

--> Many APIs calling themselves "REST" only implement a fraction of REST's original architectural principles -- the Richardson Maturity Model describes four levels, useful for honestly assessing where a given API actually sits.
--> **Level 0** -- a single URL endpoint, using only POST, with the actual "action" specified inside the request body -- essentially RPC (Remote Procedure Call) dressed up in HTTP, not really REST at all despite superficially using HTTP.
--> **Level 1** -- introduces separate URLs per RESOURCE (`/users`, `/orders`) -- but still might only use POST for everything, ignoring other HTTP methods' semantic meaning.
--> **Level 2** -- uses HTTP methods (GET/POST/PUT/DELETE) and status codes CORRECTLY and meaningfully -- this is where the vast majority of real-world "REST APIs" actually sit, including virtually every example covered elsewhere in this file.
--> **Level 3 (HATEOAS)** -- "Hypermedia as the Engine of Application State" -- responses include LINKS to related actions/resources, letting a client navigate the API dynamically by following links rather than needing prior out-of-band knowledge of every possible URL.

```json
// A Level 3/HATEOAS-style response -- includes links describing what you can DO next
{
  "id": 5,
  "status": "pending",
  "total": 49.99,
  "_links": {
    "self": { "href": "/orders/5" },
    "cancel": { "href": "/orders/5/cancel", "method": "DELETE" },
    "customer": { "href": "/customers/42" }
  }
}
```

--> **Why Level 3 is rarely fully implemented in practice** -- it adds genuine complexity (every response needs to correctly compute which actions/links are currently valid) for a benefit (dynamic client discoverability) that most internal/first-party APIs don't actually need, since the client's development team already knows the API's structure from documentation. Most real-world "RESTful" APIs deliberately stop at Level 2, which is a legitimate, pragmatic choice, not a failure to be "truly RESTful."

# Deep Dive -- ETags and Conditional Requests

--> An `ETag` (Entity Tag) is a version identifier for a specific resource's current state, letting a client and server avoid re-transferring data that hasn't actually changed, and detect conflicting concurrent edits.

```http
GET /users/5
Response:
ETag: "a1b2c3d4"

-- Later, the client re-requests the same resource:
GET /users/5
If-None-Match: "a1b2c3d4"

-- If unchanged, the server responds with almost no data at all:
304 Not Modified
```

--> **Optimistic concurrency control with ETags** -- when UPDATING a resource, a client can include the ETag it last saw in an `If-Match` header -- if another client has since modified the resource (changing its ETag), the server rejects the update with a `412 Precondition Failed` instead of silently overwriting the other client's more recent change, directly connecting to the Optimistic Locking concept covered in the Transactions and ACID file, just implemented at the HTTP/API layer here instead of the database layer.

```http
PUT /users/5
If-Match: "a1b2c3d4"
-- If the resource's CURRENT ETag no longer matches "a1b2c3d4" (someone else updated it in between),
-- the server responds 412 Precondition Failed instead of blindly applying this update on top
```
