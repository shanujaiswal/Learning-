# Beyond Conventions -- Formalizing and Protecting an API's Contract

--> The previous file covers the DESIGN conventions (naming, versioning, pagination, gateways) that make an API pleasant to use. This file covers the tooling and deeper mechanics that make an API's contract EXPLICIT, DISCOVERABLE, SAFE under load, and SAFE to evolve without silently breaking the services that depend on it -- the layer that turns "we agreed on this API in a meeting" into something machine-checkable.

# OpenAPI/Swagger -- The API's Contract as a Machine-Readable Document

--> An OpenAPI specification (still commonly called "Swagger," the tool that popularized it before the spec itself was donated to the Linux Foundation) is a YAML/JSON document describing EVERY endpoint an API exposes -- its paths, HTTP methods, request/response schemas, authentication requirements, and possible error codes -- in a format both humans and tooling can read.

```yaml
openapi: 3.0.3
info:
  title: Orders API
  version: 1.2.0
paths:
  /orders/{orderId}:
    get:
      summary: Retrieve a single order
      parameters:
        - name: orderId
          in: path
          required: true
          schema: { type: string }
      responses:
        "200":
          description: Order found
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Order"
        "404":
          description: Order not found
components:
  schemas:
    Order:
      type: object
      properties:
        id: { type: string }
        total: { type: number }
        status: { type: string, enum: [pending, paid, shipped] }
      required: [id, total, status]
```

--> **Why writing this down formally matters** -- once an API's shape exists as a structured document instead of only as prose in a wiki page or tribal knowledge, an entire ecosystem of tooling becomes possible from the SAME source of truth: auto-generated interactive documentation (Swagger UI, Redoc), auto-generated client SDKs in multiple languages, auto-generated server-side request validation, and auto-generated mock servers for frontend teams to develop against before the real backend exists.
--> **Spec-first vs code-first** -- some teams write the OpenAPI spec FIRST and generate server stubs/client code from it (forcing an explicit design conversation before any implementation code exists); others annotate existing route handlers with decorators/comments and generate the spec FROM the code afterward (faster to start, but the spec is only ever as accurate as whichever developer remembered to update the annotation). Spec-first scales better for external, multi-team, or multi-language APIs specifically because the contract is agreed and reviewable BEFORE anyone starts building against it.
--> This directly connects to the Contract Testing section further below -- an OpenAPI spec describes the SHAPE of a contract, but says nothing about whether either side's actual runtime behavior still matches it; contract testing is what verifies that ongoing agreement.

# The Richardson Maturity Model -- How "RESTful" Is This API, Really

--> "REST" gets used loosely to describe almost any HTTP/JSON API, but Leonard Richardson's Maturity Model breaks REST compliance into four distinct levels (0 through 3), each adding a genuinely separate capability on top of the last -- a useful lens for evaluating exactly how far along the REST spectrum a given API actually sits.

```
Level 0 -- The Swamp of POX (Plain Old XML/JSON over HTTP)
  ONE endpoint, ONE HTTP verb (usually POST), the "real" operation is
  encoded inside the request body itself.
    POST /api  { "action": "getUser", "id": 42 }
    POST /api  { "action": "deleteUser", "id": 42 }

Level 1 -- Resources
  Separate URIs per resource, but still mostly one HTTP verb (POST) for everything.
    POST /users/42/get
    POST /users/42/delete

Level 2 -- HTTP Verbs
  Proper use of GET/POST/PUT/PATCH/DELETE and status codes -- this is
  what the previous file's "RESTful Resource Naming" section describes,
  and where the vast majority of real-world "REST APIs" actually sit.
    GET    /users/42
    DELETE /users/42

Level 3 -- Hypermedia Controls (HATEOAS)
  Responses include LINKS describing what the client can legally do NEXT,
  rather than the client needing prior out-of-band knowledge of the API's
  URL structure to know that.
```

## HATEOAS -- Hypermedia As The Engine Of Application State

--> The distinguishing feature of true Level 3 REST -- rather than a client needing documentation or hardcoded knowledge of every URL it might need, the SERVER includes links directly in each response, describing the valid NEXT actions available from the current state, the same way a human browsing web pages follows links without pre-memorizing the entire site's URL structure.

```json
GET /orders/42

{
  "id": 42,
  "status": "pending",
  "total": 99.99,
  "_links": {
    "self":   { "href": "/orders/42" },
    "cancel": { "href": "/orders/42/cancel", "method": "POST" },
    "pay":    { "href": "/orders/42/pay",    "method": "POST" }
  }
}
```

--> **Why this matters in theory** -- if an order's status is "shipped" instead of "pending," the server simply omits the `cancel` link entirely -- the client doesn't need its OWN business logic duplicating the server's rule about which orders are cancellable; it just checks whether the link is present. This decouples client logic from server-side business rules, and lets the server change a URL's path or add a new valid action without the client needing a code change or a new API version at all.
--> **Why almost nobody actually builds Level 3 in practice** -- it requires real discipline on both ends (the server must correctly compute every valid link on every response; the client must be written to actually FOLLOW links dynamically rather than hardcoding paths, defeating much of the point), and most internal, single-consumer, or tightly-coordinated-frontend-and-backend APIs simply don't have enough independent, decoupled clients to justify the overhead. Level 2 remains the pragmatic industry default -- HATEOAS shows up mostly in APIs explicitly designed for long-lived, third-party, loosely-coupled consumption (payment gateways like Stripe/PayPal use hypermedia-style links for exactly this reason).

# Rate-Limiting Algorithms in Depth

--> The previous file's Rate Limiting section covers the CLIENT-facing headers; this section covers how a rate limiter is actually IMPLEMENTED underneath those headers -- a genuinely important distinction, since each algorithm makes a different trade-off between burst tolerance, smoothness, and memory cost.

## Token Bucket

--> A bucket holds up to N tokens, refilled at a steady rate; each request consumes one token, and a request with no tokens available is rejected (or queued).

```
Bucket capacity: 10 tokens, refill rate: 1 token/second

t=0:  bucket has 10 tokens -- a sudden BURST of 10 requests all succeed immediately
t=0:  bucket now has 0 tokens -- an 11th request is rejected
t=1:  1 token refilled -- exactly 1 more request can succeed
```

--> **Allows controlled bursts** -- a client that's been idle can spend its entire saved-up bucket at once, which matches real traffic patterns (a user loading a page that fires several API calls at once) better than a rigid per-second cap would. This is the algorithm AWS API Gateway and Stripe's API use internally.

## Leaky Bucket

--> Conceptually the mirror image of Token Bucket -- requests fill the bucket instead of draining it, and the bucket "leaks" (processes requests) at a fixed, constant rate regardless of how fast they arrived; a request that arrives when the bucket is already full is dropped.

```
Requests arrive in BURSTS --> queue into the bucket --> leak out to the backend
                                                          at a strictly constant rate

Effect: smooths bursty traffic into a steady, predictable outbound rate --
        good for protecting a downstream service that genuinely cannot handle spikes,
        at the cost of added queuing latency for requests that arrive in a burst.
```

--> **Token Bucket vs Leaky Bucket** -- Token Bucket allows the OUTPUT rate to burst (spend saved tokens fast); Leaky Bucket forces the output rate to stay constant no matter how bursty the input was. Token Bucket is the far more common choice for API rate limiting specifically because occasional legitimate bursts (a client's own retry logic, a user's browser firing a batch of calls) are normal and shouldn't be punished; Leaky Bucket suits protecting a fixed-throughput downstream resource (a legacy system, a physical device) that truly cannot absorb any burst at all.

## Fixed Window Counter

```
Window: 100 requests per 1-minute window, window boundaries at :00, :01, :02...

00:00:00 - 00:00:59 -- counter resets to 0, counts up to 100, then rejects further requests
00:01:00             -- counter resets to 0 again
```

--> Simple to implement (one counter + a fixed reset timestamp per client) but has a genuine boundary flaw -- a client can send 100 requests at 00:00:59 and another 100 at 00:01:00, a full 200 requests in under 2 seconds, despite the nominal limit being 100/minute, because the two bursts land in two DIFFERENT windows.

## Sliding Window (Log or Counter)

--> Fixes the boundary flaw by evaluating the limit over a continuously moving window ending at "now," rather than a fixed, discretely-resetting one.

```
Sliding Window Log:   store the TIMESTAMP of every request; on each new request,
                       discard timestamps older than (now - window), then count what's left.
                       Perfectly accurate, but memory cost grows with request volume.

Sliding Window Counter: an approximation -- keep TWO fixed-window counters (current + previous),
                        and weight the previous window's count by how much of it still
                        overlaps the current sliding window. Nearly as accurate as the log
                        approach, with the fixed, small memory footprint of a plain counter --
                        the algorithm most production rate limiters (including Cloudflare's
                        and Kong's) actually use in practice as the pragmatic middle ground.
```

## Where Enforcement Happens -- Gateway vs Per-Service

--> Directly connecting to the API Gateway section of the previous file -- rate limiting can be enforced at either layer, and real systems frequently do BOTH, for different reasons.
--> **At the Gateway** -- a single, centralized rate limit applied uniformly across every backend service, based on API key/client identity, before a request ever reaches any internal service. Cheap to reason about, and protects EVERY downstream service uniformly without each one needing its own rate-limiting logic -- but it's necessarily coarse, since the gateway doesn't know each individual service's own specific capacity limits.
--> **Per-service** -- a specific service (e.g. one running an expensive ML inference call, or hitting a third-party API with its OWN strict rate limit) enforces a tighter, more specific limit that reflects ITS particular constraints, independent of whatever the gateway already allowed through. This is essential for protecting a service whose actual capacity is genuinely lower than the gateway's general-purpose limit, or that depends on an external API with its own hard cap the service must not exceed regardless of how much traffic the gateway lets through.
--> **The practical pattern** -- gateway-level limiting as a broad, cheap first line of defense against abuse and overall traffic shaping; per-service limiting as a narrower, more precise safeguard for a specific service's actual bottleneck -- the same "defense in depth, cheap and coarse first, precise where it matters" logic that shows up throughout the AWS WAF and network security notes.

# Standardized Error Responses -- RFC 7807 Problem Details

--> Without a shared convention, every service in an organization (or every endpoint within the same API) tends to invent its OWN error response shape -- one returns `{ "error": "message" }`, another `{ "message": "...", "code": 42 }`, another a bare string -- forcing every client to write bespoke error-parsing logic per endpoint. RFC 7807 defines a standard JSON shape for error responses specifically to eliminate this inconsistency.

```json
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "type": "https://api.example.com/errors/insufficient-stock",
  "title": "Insufficient stock",
  "status": 422,
  "detail": "Only 3 units of SKU 'widget-blue' remain, but 5 were requested.",
  "instance": "/orders/789"
}
```

--> `type` -- a URI identifying the specific error CATEGORY (ideally one a developer can visit for more documentation); `title` -- a short, human-readable summary of that category, stable across occurrences; `status` -- the HTTP status code, repeated in the body so it survives even if something strips HTTP headers along the way; `detail` -- the specific, human-readable explanation for THIS particular occurrence; `instance` -- optionally, the specific resource/request this error occurred on.
--> **Why standardizing this matters at scale** -- once every service in a microservices architecture (connecting to the Software Architecture files) returns errors in the same shape, a single shared error-handling/logging/alerting layer can be written ONCE and reused everywhere, instead of every client needing service-specific error-parsing logic -- precisely the same "solve a cross-cutting concern once, centrally" logic that motivates the API Gateway pattern in the first place.

# Bulk/Batch Operations

--> A pure REST-by-the-book design (one resource, one request) becomes genuinely impractical when a client legitimately needs to create/update/delete hundreds of records at once -- firing hundreds of individual HTTP requests adds massive overhead (hundreds of TCP/TLS handshakes, hundreds of round trips) compared to one request carrying a batch.

```json
POST /orders/batch
{
  "orders": [
    { "item": "widget", "quantity": 2 },
    { "item": "gadget", "quantity": 1 }
  ]
}

Response -- 207 Multi-Status, since some items in the batch may succeed while others fail:
{
  "results": [
    { "status": 201, "id": "order_101" },
    { "status": 422, "error": { "title": "Out of stock", "detail": "..." } }
  ]
}
```

--> **The key design decision** -- a batch endpoint must decide (and clearly document) whether it's ALL-OR-NOTHING (one failing item rolls back the entire batch, giving simpler client-side error handling at the cost of an entire batch failing over one bad record) or PARTIAL SUCCESS (each item succeeds/fails independently, using `207 Multi-Status` and a per-item result array as shown above, giving more resilience at the cost of more complex client-side handling of a mixed-result response). Most real-world bulk APIs (Stripe's bulk operations, Elasticsearch's `_bulk` endpoint referenced in the Elasticsearch file) choose partial success specifically because one malformed record among thousands shouldn't block the other 999 from succeeding.

# Contract Testing -- Verifying the Agreement Actually Holds

--> Directly motivated by the Microservices communication patterns covered in the Software Architecture files -- once Service A (a "consumer") depends on Service B's (a "provider") API, ordinary unit tests inside each service in isolation CANNOT catch the case where B changes its response shape in a way that breaks A, since neither service's own test suite ever actually exercises the other side. Full end-to-end integration tests catch this, but are slow, require standing up every dependent service together, and are exactly the "integration testing complexity across many independently-deployed services" cost flagged in the Software Architecture file's microservices trade-offs.

## Consumer-Driven Contracts

--> The consumer (Service A) writes down its EXPECTATIONS of the provider's API -- the specific requests it will send and the specific response shape it needs back -- as a machine-readable "contract" file, WITHOUT needing the real provider running at all.

```javascript
// Pact.js -- the consumer (Order Service) defines what it expects from the Payments provider
const { PactV3 } = require("@pact-foundation/pact");

const provider = new PactV3({ consumer: "OrderService", provider: "PaymentsService" });

provider
  .given("a valid payment method exists")
  .uponReceiving("a request to charge a card")
  .withRequest({ method: "POST", path: "/charges", body: { amount: 1000 } })
  .willRespondWith({
    status: 200,
    body: { id: "ch_123", status: "succeeded" },
  });
```

--> This generates a "pact file" -- a saved, shareable JSON contract describing exactly what the consumer expects. The provider team then runs a SEPARATE verification step: replay every recorded request from the pact file against the REAL provider service, and confirm its actual responses still match what every consumer expects.

```javascript
// Pact.js -- the provider (Payments Service) verifies it still satisfies EVERY consumer's contract
const { Verifier } = require("@pact-foundation/pact");

new Verifier({
  provider: "PaymentsService",
  providerBaseUrl: "http://localhost:8080",
  pactUrls: ["./pacts/orderservice-paymentsservice.json"],
}).verifyProvider();
```

--> **Why this is faster and more reliable than full E2E tests, and doesn't require a shared network of running services** -- the consumer's tests run against a lightweight MOCK generated from the contract (no real provider needed at all); the provider's verification runs against the REAL provider but with recorded requests, not a live consumer -- neither side ever needs the OTHER side actually running to test against, which is exactly what makes contract tests dramatically faster and less flaky than spinning up an entire dependency graph of services for an E2E test, while still catching the specific "did we silently break what a consumer expects" class of bug that isolated unit tests structurally cannot.
--> **The Pact Broker** -- a central service that stores every consumer's contract and every provider's verification results, and can answer "is it SAFE for Provider B to deploy this new version, given every consumer's currently-recorded expectations?" before a deploy even happens -- directly enabling a practice called "can-i-deploy," a genuinely powerful safety net in a microservices organization with many independently-deployed services and many consumer/provider pairs to track.
--> This connects back to the OpenAPI section above -- an OpenAPI spec documents the INTENDED shape of a contract at a point in time; consumer-driven contract testing continuously VERIFIES that the provider's actual, live behavior still honors what its real consumers actually depend on, which can drift from the spec in ways a static document alone can never catch.
