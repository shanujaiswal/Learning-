# The Decision Isn't "Which Is Better" -- It's "Which Fits This Workload"

--> Files 01-02 covered Spring MVC's internals; files 03-04 covered reactive programming and WebFlux. This file is the payoff -- given both exist and both are fully supported, production-grade choices, how do you actually decide which one to build a given service on? The honest answer, and the one experienced Spring teams converge on: **Spring MVC remains the correct DEFAULT choice for most applications**, and WebFlux is a deliberate opt-in for a specific class of problem, not a general upgrade.

# Thread-Per-Request vs Event-Loop -- The Core Mechanical Difference

--> **Thread-per-request (Spring MVC / Servlet)** -- the servlet container maintains a pool of worker threads (Tomcat's default is 200). Each incoming request is handed exactly one thread for its ENTIRE lifecycle, including every moment spent blocked waiting on a database query, a downstream HTTP call, or disk I/O. Concurrency is bounded by pool size: request #201 arriving while all 200 threads are occupied WAITS in a queue, even if every one of those 200 threads is doing nothing but sitting idle on I/O.
--> **Event-loop (WebFlux / Reactor Netty)** -- a small, fixed number of threads (typically matching CPU core count, so often 4-16) handle ALL requests. No thread ever blocks waiting for I/O -- when a request needs to wait on the database, the thread registers a callback (via the Reactive Streams `Subscriber` machinery from file 03) and immediately moves on to service a DIFFERENT request's next step. When the database responds, whichever event-loop thread is free at that moment resumes that request's pipeline. The number of concurrent IN-FLIGHT requests is no longer bounded by thread count -- it's bounded by memory (each in-flight request holds some pipeline state, which is far cheaper than a blocked thread's full stack).

```text
Thread-per-request, 3 concurrent slow requests, pool size 200:
  Thread 1: [-------- waiting on DB (400ms) --------][done]
  Thread 2: [-------- waiting on DB (400ms) --------][done]
  Thread 3: [-------- waiting on DB (400ms) --------][done]
  -- 3 threads OCCUPIED and IDLE for 400ms each. Fine at low concurrency, doesn't scale past ~pool size.

Event-loop, 3 concurrent slow requests, 4 event-loop threads:
  Thread A: [req1 starts][req2 starts][req3 starts][free to take req4, req5...][req1 resumes when DB responds]
  -- Threads are NEVER idle-blocked. The same handful of threads services far more concurrent
     in-flight requests, because "waiting" doesn't consume a thread at all.
```

--> **This difference only matters when a service is actually I/O-bound and under real concurrent load.** At low request volume, or for CPU-bound work, thread-per-request's "waste" of idle threads is simply invisible -- there's no queue, no contention, nothing to gain by switching models.

# Where Reactive Genuinely Helps

--> **High-concurrency, I/O-bound gateway/aggregation services** -- a service whose main job is to fan out to several other downstream services (microservice orchestration, an API gateway, a BFF -- backend-for-frontend) and combine their responses. This is close to the textbook ideal case: mostly waiting on network I/O, little CPU work, and the ability to run those downstream calls CONCURRENTLY (via `Mono.zip`/`flatMap` chains) rather than sequentially is a direct win reactive expresses naturally.
--> **Streaming / long-lived connections** -- Server-Sent Events, WebSocket-adjacent live feeds, or any endpoint that pushes a continuous or long-running stream of data to a client. A `Flux` maps onto this naturally; representing an open-ended stream in a blocking, thread-per-request model requires holding a thread open for the connection's entire lifetime, which doesn't scale to many simultaneous long-lived connections.
--> **Very high concurrent connection counts with genuinely I/O-heavy per-request work** -- systems that need to serve tens of thousands of concurrent, mostly-idle-on-I/O connections (chat backends, notification services, IoT ingestion) where thread-per-request's per-thread memory overhead becomes the actual bottleneck before CPU or downstream capacity does.
--> **Reactive-native infrastructure already in place** -- if a team is already committed to a fully reactive data layer (R2DBC, reactive Kafka/RabbitMQ clients, reactive Redis) for other reasons, WebFlux is the natural fit for the web layer sitting on top of it, avoiding an awkward reactive-to-blocking seam.

# Where MVC Remains the Right Default

--> **Most CRUD applications** -- a typical internal admin tool, a standard REST API backed by a relational database via JPA, moderate traffic. The bottleneck in these systems is almost never "not enough threads waiting on I/O" -- it's database query performance, business logic complexity, or simply that traffic never approaches thread-pool-exhaustion levels in the first place. Reactive buys nothing here and adds real cost (see pitfalls below).
--> **Teams without deep reactive experience** -- the learning curve from file 03 (operators, backpressure, cold vs hot, schedulers) is real, and reactive stack traces (see below) are measurably harder to debug. For a team under delivery pressure, MVC's simpler mental model is often the more PRODUCTIVE choice even where reactive would theoretically scale better -- correctness and maintainability matter more than a scaling headroom the app may never need.
--> **CPU-bound workloads** -- image processing, complex calculations, report generation. Reactive's advantage is specifically about not wasting threads on I/O WAITING; it does nothing for work that's genuinely CPU-bound, since that work still needs to actually execute on some thread regardless of the model.
--> **Heavy reliance on the JDBC/JPA ecosystem** -- Spring Data JPA, Hibernate, and the vast majority of ORM tooling are fundamentally blocking (JDBC itself is a blocking API by design). Using them from WebFlux means either accepting a blocking seam (defeating much of the purpose) or migrating to R2DBC, which is a materially less mature ecosystem with fewer features than JPA (see below).

# Common Pitfalls -- Blocking Calls in Reactive Code

--> **The single most damaging WebFlux mistake**: calling a blocking API (JDBC, a blocking HTTP client, `Thread.sleep()`, blocking file I/O, even a synchronized lock held across I/O) from inside a reactive pipeline running on an event-loop thread. Because event-loop threads are FEW and shared across many concurrent requests, one accidentally-blocked thread doesn't just slow down its own request -- it stalls EVERY OTHER request currently relying on that same thread, a much worse failure mode than in thread-per-request, where a slow request only ever affects itself (until the whole pool is exhausted).

```java
// WRONG -- calling a blocking JPA repository from a WebFlux controller
@GetMapping("/{id}")
public Mono<ProductResponse> getOne(@PathVariable Long id) {
    return Mono.fromCallable(() -> jpaProductRepository.findById(id))   // still BLOCKS the thread it runs on
            .map(ProductMapper::toResponse);
    // Wrapping a blocking call in Mono.fromCallable does NOT make it non-blocking --
    // it just delays WHEN the blocking happens. If this runs on an event-loop thread, it's just as bad.
}

// BETTER (but still not ideal) -- explicitly isolate the blocking call onto a dedicated pool
@GetMapping("/{id}")
public Mono<ProductResponse> getOne(@PathVariable Long id) {
    return Mono.fromCallable(() -> jpaProductRepository.findById(id))
            .subscribeOn(Schedulers.boundedElastic())   // now it blocks a boundedElastic thread, not an event-loop one
            .map(ProductMapper::toResponse);
    // Correct-ish, but at this point you've reintroduced thread-per-request-like blocking
    // for this one call, just on a different pool -- ask whether WebFlux is even buying anything here.

// BEST -- use an actually non-blocking driver (R2DBC) so there's no blocking call to isolate at all
@GetMapping("/{id}")
public Mono<ProductResponse> getOne(@PathVariable Long id) {
    return r2dbcProductRepository.findById(id)   // genuinely non-blocking, no Scheduler juggling needed
            .map(ProductMapper::toResponse);
}
```

--> **Reactor Netty actively detects this class of bug** -- calling `.block()` (or certain other blocking operations) directly on an event-loop thread throws `IllegalStateException: block()/blockFirst()/blockLast() are blocking, which is not supported in thread [...]` specifically so the mistake is loud and immediate rather than a silent throughput cliff discovered only under load in production. Not every blocking call is caught this way, though (a raw `Thread.sleep()` or a blocking JDBC call has no such guard) -- so this protection is a safety net, not a substitute for auditing dependencies for hidden blocking calls.

# Common Pitfalls -- Debugging Reactive Stack Traces

--> A stack trace from a failed reactive pipeline looks NOTHING like a familiar imperative one -- instead of a clean call chain showing your actual business logic, it's dominated by dozens of frames from Reactor's own internal operator machinery (`FluxMap`, `MonoFlatMap`, `FluxSubscribeOn`, scheduler internals), because the ACTUAL execution happens asynchronously, on a different thread, at a different TIME than when the pipeline was originally assembled -- the "call stack" in the traditional sense doesn't meaningfully exist across an async boundary.

```text
// A typical unassisted reactive stack trace -- your code is often barely visible
reactor.core.publisher.FluxOnAssembly$OnAssemblyException:
Assembly trace from producer [reactor.core.publisher.MonoFlatMap] :
        reactor.core.publisher.Mono.flatMap
Error has been observed at the following site(s):
        *__checkpoint => Handler com.example.ProductController#getOne(Long)
        ...
Caused by: java.lang.NullPointerException: ...
        at reactor.core.publisher.FluxMap$MapSubscriber.onNext(FluxMap.java:...)
        at reactor.core.publisher.FluxOnErrorResume$...
        (dozens more Reactor-internal frames)
```

--> **`Hooks.onOperatorDebug()` / the `reactor-tools` `BlockHound` and Reactor's assembly-time tracing** -- Reactor provides ways to make debugging tractable, at a real performance cost, so they're development/staging tools, not always-on production settings:
  - --> **`.checkpoint("some label")`** -- inserted into a pipeline at a meaningful point, adds a labeled marker to the eventual stack trace, making it obvious WHERE in your own pipeline an error surfaced, without the full cost of global operator debugging.
  - --> **`Hooks.onOperatorDebug()`** (or the `-Dreactor.trace.assembly=true` alternative) -- globally captures the assembly-time stack trace for every operator, so error traces point back to where the pipeline was BUILT in your code, not just Reactor's internals. Meaningfully slows down the whole application if left on, so it's a debugging-session tool, not a default production setting.
  - --> **`reactor-tools`' `BlockHound`** -- a Java agent that actively detects blocking calls made from non-blocking threads AT RUNTIME (catching the exact pitfall from the section above, even blocking calls Reactor Netty's own built-in guard doesn't catch), typically wired into integration tests rather than left running in production due to its own overhead.
--> **The practical debugging habit** -- read reactive stack traces bottom-up looking for YOUR class names and `checkpoint` labels first, treat the surrounding Reactor-internal frames as noise to skim past rather than read line-by-line, and reach for `Hooks.onOperatorDebug()` locally the moment a trace is too opaque to make sense of quickly.

# R2DBC -- Reactive Database Access, Briefly

--> **R2DBC (Reactive Relational Database Connectivity)** is the non-blocking counterpart to JDBC -- a driver-level specification (analogous to how JDBC is a spec that vendor-specific drivers implement) built around Reactive Streams from the ground up, so a query genuinely returns a `Publisher` rather than blocking the calling thread until a `ResultSet` is ready.
--> **Spring Data R2DBC** provides the familiar repository abstraction on top -- `ReactiveCrudRepository<T, ID>` mirrors `JpaRepository`'s shape but every method returns `Mono`/`Flux` instead of a plain value:

```java
public interface ProductRepository extends ReactiveCrudRepository<Product, Long> {
    Flux<Product> findByCategory(String category);   // derived query methods still work, same naming convention
    Mono<Boolean> existsByName(String name);
}
```

--> **R2DBC is NOT a drop-in replacement for JPA/Hibernate, and the gap matters when choosing WebFlux for a data-heavy app**:
  - --> **No lazy loading, no first-level cache, no dirty-checking auto-flush** -- Spring Data R2DBC deliberately avoids Hibernate's session/persistence-context machinery (much of which is fundamentally hard to make non-blocking correctly); every fetch/save is explicit.
  - --> **Weaker relationship mapping** -- complex `@OneToMany`/`@ManyToMany` object-graph mapping that JPA handles largely automatically often needs to be done more manually (explicit joins/queries, or multiple `flatMap`-composed queries) in R2DBC.
  - --> **Smaller ecosystem, fewer battle-tested integrations** -- fewer tools, less Stack-Overflow-level accumulated knowledge, fewer vendor drivers with full feature parity, compared to JDBC's decades of maturity.
--> **The practical implication**: choosing WebFlux for a data-heavy CRUD application is choosing R2DBC's real limitations too, not just gaining Reactor's concurrency model -- factor this into the decision, not just the web-layer threading question alone. A common, entirely valid middle ground is a WebFlux front end that calls OUT to other services reactively via `WebClient` while still using JPA/JDBC for its own primary datastore, accepting a deliberate, well-understood blocking seam wrapped in `Schedulers.boundedElastic()` rather than migrating the whole data layer.

# A Practical Decision Checklist

| Question | Leans MVC | Leans WebFlux |
|---|---|---|
| Is the service mostly CPU-bound business logic? | Yes | -- |
| Is the service mostly orchestrating/aggregating calls to other services? | -- | Yes |
| Does it need to serve long-lived streaming connections (SSE/WebSocket-adjacent)? | -- | Yes |
| Is the team already fluent in reactive programming? | No -> lean MVC | Yes -> WebFlux viable |
| Is the primary datastore relational, accessed via JPA, with complex entity graphs? | Yes | Requires accepting R2DBC's gaps or a blocking seam |
| Is expected peak concurrency well within what a few hundred threads can serve? | Yes | -- |
| Is there a genuine, measured need for tens of thousands of concurrent connections? | -- | Yes |

# Common Gotchas

--> **Adopting WebFlux "because it's more modern" without a concurrency or streaming need** -- this trades away MVC's simplicity and JPA's maturity for a scaling benefit the app will likely never exercise, while paying the real costs (learning curve, harder debugging, R2DBC limitations) unconditionally.
--> **Half-reactive architectures that block on an event-loop thread** -- covered above, but worth restating as the single highest-impact mistake: it doesn't just underperform, it can cascade into stalling unrelated requests sharing the same small thread pool.
--> **Assuming WebFlux is automatically faster in benchmarks** -- for CPU-bound or low-concurrency workloads, WebFlux can perform the SAME or occasionally WORSE than MVC (added pipeline/operator overhead with no I/O-wait to reclaim) -- reactive's advantage is specifically about concurrency under I/O-bound load, not raw single-request latency.
--> **Underestimating the R2DBC gap when planning a WebFlux migration for an existing JPA-backed app** -- teams sometimes discover mid-migration that a relied-upon JPA feature (complex lazy associations, `@Query` with JPQL specifics, auditing via Hibernate listeners) has no clean R2DBC equivalent, forcing workarounds late in the project.
--> **Not turning on any reactive debugging aid until already stuck** -- `checkpoint()` costs little and pays for itself the first time a production reactive error needs tracing back to its origin; retrofitting it after the fact, mid-incident, is a worse time to start.

# Best Practices Summary

--> Default to Spring MVC; choose WebFlux only for a concrete, identified need (I/O-bound aggregation/gateway workloads, streaming endpoints, or genuinely massive concurrent-connection counts) -- never as a default "best practice" upgrade.
--> Treat "no blocking calls on event-loop threads" as a hard architectural rule in any WebFlux codebase, not a best-effort guideline -- one violation degrades unrelated requests, not just the offending one.
--> Budget real time for the reactive learning curve and for R2DBC's feature gaps before committing a data-heavy application to WebFlux.
--> Use `checkpoint()` proactively at meaningful pipeline boundaries, and reach for `Hooks.onOperatorDebug()`/BlockHound in development the moment a reactive stack trace becomes hard to read -- don't wait for a production incident to learn these tools.
--> Where only PART of a system benefits from reactive (e.g. an aggregation gateway calling several slow downstream services), it's entirely valid to build just that part in WebFlux while the rest of the system stays on MVC -- reactive adoption doesn't have to be all-or-nothing across an organization.
