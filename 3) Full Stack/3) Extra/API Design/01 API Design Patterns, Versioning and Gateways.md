# Beyond "It Works" -- Why API Design Is Its Own Discipline

--> The REST API and GraphQL files (in the Node/Express and Extra folders) cover HOW to build an API technically. This file covers the DESIGN decisions that determine whether an API is pleasant and safe to use, evolve, and maintain over YEARS, across many client applications and possibly external third-party developers who don't work at your company at all.

# RESTful Resource Naming Conventions

--> A consistent, predictable URL structure is one of the highest-value, lowest-cost API design decisions -- once a consumer learns the pattern, they can correctly GUESS the URL for a new resource type without needing to look it up.

```
Good (consistent, resource-oriented, plural nouns):
  GET    /users               -- list users
  GET    /users/42             -- get a specific user
  POST   /users                -- create a user
  PUT    /users/42              -- replace a user
  PATCH  /users/42               -- partially update a user
  DELETE /users/42                -- delete a user
  GET    /users/42/orders           -- a user's orders (nested resource relationship)

Bad (inconsistent, verb-based, mixing conventions):
  GET    /getUser?id=42
  POST   /createNewUser
  GET    /user-orders?userId=42
```

--> The HTTP method itself already conveys the ACTION (GET=read, POST=create, PUT/PATCH=update, DELETE=remove) -- repeating that action as a verb in the URL (`/getUser`, `/createNewUser`) is redundant and breaks the pattern that makes REST predictable in the first place, directly echoing the REST API fundamentals file's core principles, but emphasizing the CONSISTENCY discipline specifically.

# Idempotency -- A Property That Matters More Than It Sounds

--> An idempotent operation produces the SAME result no matter how many times it's repeated -- `GET`, `PUT`, and `DELETE` are all expected to be idempotent by REST convention; `POST` is NOT.

```
PUT /users/42 { "name": "Alice" }   -- calling this 5 times in a row leaves the exact same end state as calling it once
POST /orders { "item": "widget" }    -- calling this 5 times in a row creates 5 SEPARATE orders -- NOT idempotent
```

--> Why this matters practically -- if a client's request times out and it's unsure whether the server actually received it, it's SAFE to automatically retry an idempotent request (a `PUT`) without risk of duplicating an effect, but automatically retrying a non-idempotent `POST` risks creating duplicate orders/charges -- a genuinely important, easy-to-overlook distinction directly relevant to building reliable client retry logic, and to the exactly-once vs at-least-once delivery guarantees discussed in the Message Queues file.

## Idempotency Keys -- Making POST Safely Retryable

--> For operations that genuinely need `POST`'s "create a new thing" semantics but ALSO need safe retry behavior (payment processing being the classic example), an idempotency key lets the client supply a unique identifier for the LOGICAL operation, and the server ensures repeating the SAME key doesn't create a duplicate effect.

```
POST /payments
Idempotency-Key: a1b2c3d4-uuid-generated-once-by-the-client

The server checks: "has this exact Idempotency-Key been processed before?"
  If yes -- return the SAME result as the original request, without charging again
  If no  -- process the payment normally, and remember this key's result for future retries
```

# API Versioning Strategies

--> An API used by external clients (or even internal clients you don't control the deployment timing of) can't simply change its behavior overnight without breaking whoever's currently using it -- versioning gives a structured way to evolve an API while keeping existing consumers working.

## URI Versioning

```
GET /v1/users/42
GET /v2/users/42
```

--> The simplest, most explicit approach -- the version is directly visible in every URL. Easy for consumers to understand and easy to route differently on the server side, but can feel like it "pollutes" the resource-oriented URL structure with what's really metadata about the request, not part of the resource's identity.

## Header-Based Versioning

```
GET /users/42
Accept: application/vnd.myapi.v2+json
```

--> Keeps URLs clean (the resource's URL doesn't change between versions) at the cost of being less discoverable/debuggable -- you can't just look at a URL in a browser and know which version you're hitting; you need to inspect request headers specifically.

## Why Versioning Discipline Matters

--> **Breaking changes require a new version** -- removing a field, changing a field's type or meaning, or changing required parameters are all breaking changes that would silently break existing consumers if deployed to an EXISTING version.
--> **Non-breaking changes generally don't** -- ADDING a new optional field, or adding a new endpoint entirely, doesn't require a version bump, since well-behaved existing clients simply ignore fields they don't recognize.
--> **Deprecation policy** -- a mature API publishes a clear timeline for how long an OLD version remains supported before being shut down entirely, giving consumers fair, predictable notice to migrate -- an API that silently breaks or removes an old version without warning damages trust with every consumer relying on it, both internal and external.

# Pagination -- Handling Large Result Sets

--> Returning EVERY matching record in one response doesn't scale -- a `/users` endpoint with a million users needs a way to return results in manageable chunks.

## Offset-Based Pagination

```
GET /users?limit=20&offset=40    -- returns records 41-60
```

--> Simple and intuitive, directly mapping onto the SQL `LIMIT`/`OFFSET` concepts covered in the Database SQL files -- but genuinely inefficient for very large offsets (the database still has to scan/skip past every earlier row) and can produce inconsistent results if records are added/removed WHILE a client is paging through (a record shifting position between page 1 and page 2 requests, potentially causing it to be skipped or duplicated across pages).

## Cursor-Based Pagination

```
GET /users?limit=20&after=user_id_12345
```

--> Instead of a numeric offset, the client passes a "cursor" -- typically the last-seen record's ID or a similar stable marker -- and the server returns the next batch AFTER that specific point. More resilient to concurrent inserts/deletes (a new record added doesn't shift everyone else's position relative to the cursor), and more efficient at scale (no need to scan/skip past earlier records), which is why most large-scale, high-traffic APIs (social media feeds, for example) use cursor-based pagination rather than simple offsets.

# API Gateways -- A Single Front Door for Multiple Services

--> Directly connecting to the Microservices architecture covered in the Software Architecture files -- when a system is split into many separate services, exposing each one DIRECTLY to external clients creates real problems: clients need to know every service's specific address, and cross-cutting concerns (authentication, rate limiting, logging) would need to be re-implemented in every single service independently.
--> An API Gateway sits in FRONT of all backend services, acting as the single entry point clients actually talk to -- it routes each incoming request to the appropriate backend service, and centralizes cross-cutting concerns in ONE place instead of duplicating them everywhere.

```
Client --> API Gateway --> routes to --> Users Service
                        --> routes to --> Orders Service
                        --> routes to --> Payments Service

The Gateway handles, ONCE, for every request regardless of destination:
  - Authentication/authorization (connecting to the JWT/Sessions file)
  - Rate limiting (preventing any single client from overwhelming backend services)
  - Request logging and monitoring (connecting to the Observability concepts in the DevOps notes)
  - SSL/TLS termination
```

--> This is exactly the role AWS API Gateway (referenced in the AWS Cloud Platform file, paired with Lambda) or a self-hosted gateway like Kong/Nginx plays in a real production microservices deployment -- letting individual backend services stay simple and focused purely on their own business logic, while the gateway handles the shared, repetitive infrastructure concerns every service would otherwise need to duplicate.

# Rate Limiting -- Protecting an API From Abuse and Overload

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 42
X-RateLimit-Reset: 1717440000
```

--> Standard practice for any public or high-traffic API -- returning these headers lets well-behaved clients know exactly how close they are to their limit and adjust their own request pacing accordingly, rather than discovering the limit only by hitting a `429 Too Many Requests` error unexpectedly. This directly connects to the rate-limiting concepts covered in the AWS WAF file, just implemented at the application/API layer here rather than at the network edge.

# Why These Design Decisions Compound Over Time

--> Every pattern in this file matters disproportionately MORE as an API grows in usage and lifespan -- inconsistent naming, missing versioning, and poor pagination choices are cheap to fix on day one of a new API, and increasingly expensive (sometimes genuinely impossible without breaking real external consumers) to fix years later once the API has real, dependent traffic relying on its current exact behavior.
