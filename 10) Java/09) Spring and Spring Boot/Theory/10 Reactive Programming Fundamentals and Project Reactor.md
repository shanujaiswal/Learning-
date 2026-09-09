# Why Reactive Programming Exists at All

--> Everything covered so far (files 01-02, and the REST APIs chapter) is fundamentally IMPERATIVE and BLOCKING -- a request thread calls a service, which calls a repository, which calls JDBC, and that thread sits idle, doing nothing, WAITING for the database to respond before it can move to the next line. Under the classic Servlet model, Tomcat hands each incoming request its OWN thread from a pool (thread-per-request), and that thread is occupied for the entire duration of the request, including all the time spent blocked on I/O.
--> **The core problem reactive programming addresses**: threads are a relatively expensive OS resource (each one reserves its own stack memory, megabytes by default in the JVM, and context-switching between them isn't free) -- so a thread pool has a hard ceiling on concurrent requests. If a typical request spends 90% of its time waiting on a slow downstream call (a database, another microservice, a third-party API) and only 10% actually doing CPU work, thread-per-request means 90% of every thread's lifetime is pure waste, and the SIZE of the thread pool becomes the hard limit on how many requests the app can handle at once -- regardless of how little actual CPU work is happening.
--> **Reactive programming's answer**: don't dedicate a thread to sit and wait. Instead, describe the work as a PIPELINE of transformations to be applied to data WHEN it eventually arrives, hand that pipeline to a small, fixed pool of worker threads (an "event loop"), and let those threads pick up other work while any given operation is waiting on I/O. The same handful of threads can service thousands of concurrent in-flight requests, because none of them ever sit idle blocked on I/O -- they're always either doing real CPU work or free to pick up someone else's.

# Reactive Streams -- The Specification Underneath It All

--> **Reactive Streams** is a Java specification (not a Spring-specific thing -- it lives in `java.util.concurrent.Flow` since Java 9, and predates that as its own spec that RxJava, Reactor, Akka Streams, and others all implement) defining four core interfaces that any reactive library must implement to interoperate:

```text
Publisher<T>   -- a source of data. Something that CAN emit a stream of T values over time.
Subscriber<T>  -- a consumer of data. Reacts to values as the Publisher emits them.
Subscription   -- the link between a specific Publisher and Subscriber, used to request
                  more data or cancel early.
Processor<T,R> -- both a Subscriber AND a Publisher at once (a transformation stage in a pipeline).
```

--> **The interaction contract, in words**: a `Subscriber` subscribes to a `Publisher`. The `Publisher` calls `onSubscribe`, handing over a `Subscription`. The `Subscriber` then calls `subscription.request(n)` to say "I'm ready for up to `n` more items" -- data only flows once REQUESTED, never pushed unconditionally. The `Publisher` responds by calling `onNext(item)` zero or more times (never more than what was requested), then eventually either `onComplete()` (success, stream finished) or `onError(throwable)` (failure, stream terminates) -- exactly one of those two, exactly once, ending the interaction.
--> **This `request(n)` mechanism IS backpressure** -- the single most important concept that separates "reactive" from merely "asynchronous." Without it, a fast Publisher (e.g. a database returning a million rows) could overwhelm a slow Subscriber (e.g. a network socket that can only write so fast) by pushing data faster than it can be consumed, exhausting memory as unconsumed items pile up in a buffer. Backpressure inverts the flow of CONTROL: the consumer declares its own capacity, and the producer is contractually obligated to respect it.
--> **You will almost never implement these four interfaces directly** -- Project Reactor's `Mono`/`Flux` (and RxJava's `Observable`/`Single`, and WebFlux's use of Reactor under the hood) are built ON TOP of Reactive Streams and give you a rich, fluent API for composing pipelines. Knowing the spec exists explains WHY operators like `Flux.limitRate()` or WebClient's internal buffering behave the way they do, even though you rarely touch `Subscriber`/`Subscription` by hand.

# Project Reactor -- Mono and Flux

--> **Project Reactor** is the reactive library Spring WebFlux is built on (developed by the Spring team specifically for this purpose). It provides exactly two core publisher types, distinguished by CARDINALITY -- how many items they can ever emit:

| Type | Emits | Real-World Analogy | Typical Use |
|---|---|---|---|
| `Mono<T>` | 0 or 1 item, then completes (or errors) | A `CompletableFuture<T>` / an `Optional<T>` that arrives asynchronously | A single database row lookup by ID, a single HTTP call's response |
| `Flux<T>` | 0 to N items (including infinite), then completes (or errors) | A `Stream<T>` / an async `Iterable<T>` that arrives over time | A list of query results, a stream of server-sent events, a live feed |

```java
// Mono -- at most one value
Mono<Product> product = productRepository.findById(42L);

// Flux -- a stream of values, potentially many, potentially never-ending
Flux<Product> allProducts = productRepository.findAll();
Flux<Long> ticker = Flux.interval(Duration.ofSeconds(1));   // never completes on its own
```

--> **Nothing happens until someone subscribes -- this is the single most important mental shift.** `Mono` and `Flux` are LAZY, DECLARATIVE descriptions of a computation, not eagerly-running tasks. `productRepository.findById(42L)` does NOT hit the database the instant that line executes -- it builds up a pipeline object describing "when someone subscribes, do this query, then apply these transformations." The actual work only starts when something SUBSCRIBES (in WebFlux, the framework itself subscribes for you when returning a `Mono`/`Flux` from a controller method; in your own code, `.subscribe()`, or blocking calls like `.block()` in tests, trigger it).

```java
Mono<Product> pipeline = productRepository.findById(42L)
        .map(p -> { p.setName(p.getName().toUpperCase()); return p; })
        .doOnNext(p -> log.info("Found: {}", p));

// Up to this point, NOTHING has executed -- no query has run. The pipeline is just a description.
pipeline.subscribe(p -> System.out.println("Got: " + p));   // NOW it actually runs.
```

# Composing Pipelines -- The Operators You'll Use Constantly

```java
// map -- synchronous, 1-to-1 transformation
Mono<ProductResponse> dto = productRepository.findById(id)
        .map(ProductMapper::toResponse);            // Product -> ProductResponse, no async work involved

// flatMap -- 1-to-1(or more), but the transformation itself returns ANOTHER Mono/Flux
// (i.e. chaining another asynchronous operation) -- this is the reactive equivalent of
// "await this, then do this OTHER async thing with the result"
Mono<OrderConfirmation> result = orderRepository.findById(orderId)
        .flatMap(order -> paymentService.charge(order.getTotal())      // returns Mono<PaymentResult>
                .map(payment -> new OrderConfirmation(order, payment)));

// filter -- drop items that don't match a predicate
Flux<Product> inStock = productRepository.findAll()
        .filter(Product::isInStock);

// switchIfEmpty -- what to do if the source completes with NO items at all
Mono<Product> productOrError = productRepository.findById(id)
        .switchIfEmpty(Mono.error(new ProductNotFoundException(id)));

// onErrorResume -- reactive's try/catch: recover from an error with a fallback publisher
Mono<Product> withFallback = productRepository.findById(id)
        .onErrorResume(ex -> Mono.just(Product.placeholder()));

// zip -- combine multiple independent publishers, waiting for all of them
Mono<ProductPage> combined = Mono.zip(
        productRepository.findAll().collectList(),
        productRepository.count(),
        (products, total) -> new ProductPage(products, total));
```

--> **`map` vs `flatMap` is the single most common point of confusion for newcomers** -- use `map` when your transformation function returns a PLAIN value synchronously; use `flatMap` when it returns ANOTHER `Mono`/`Flux` (i.e. you're kicking off another asynchronous operation as part of the pipeline). Using `map` where `flatMap` is needed produces a nonsensical nested type (`Mono<Mono<X>>`), which won't even compile against a `Mono<X>`-typed variable -- the compiler catches this class of mistake immediately, which is a small mercy.

# Imperative vs Reactive -- The Same Logic, Two Styles

```java
// Imperative (blocking) -- Spring MVC style. Each line WAITS for the previous one.
public ProductResponse getProduct(Long id) {
    Product product = productRepository.findById(id)              // thread blocks here
            .orElseThrow(() -> new ProductNotFoundException(id));
    Reviews reviews = reviewService.getReviews(id);                // thread blocks here too
    return ProductMapper.toResponse(product, reviews);
}

// Reactive (non-blocking) -- WebFlux style. Describes a pipeline; nothing blocks the calling thread.
public Mono<ProductResponse> getProduct(Long id) {
    return productRepository.findById(id)
            .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
            .zipWith(reviewService.getReviews(id))                 // both fetched concurrently, combined when both arrive
            .map(tuple -> ProductMapper.toResponse(tuple.getT1(), tuple.getT2()));
}
```

--> Notice the reactive version doesn't just look different syntactically -- it's STRUCTURALLY concurrent by default (`zipWith` runs both underlying operations without either one blocking the other), whereas the imperative version runs them strictly sequentially unless you deliberately introduce a separate thread/`CompletableFuture` yourself. This is a genuine capability gain, not just a stylistic one -- though it also means reactive code demands thinking in terms of DATA FLOW rather than step-by-step instructions, which is the real learning curve.

# Cold vs Hot Publishers

--> **Cold** (the default, and by far the most common) -- the underlying work is (re-)executed FRESH for every new subscriber. `productRepository.findById(42L)` run by two different subscribers triggers the database query TWICE, independently, each getting its own execution.
--> **Hot** -- the underlying producer runs independent of subscribers and just broadcasts values to whoever happens to be listening at the time (a live sensor feed, a shared WebSocket stream); subscribing late means missing earlier values. `Flux.share()` / `ConnectableFlux` convert a cold source into a hot, shared one when that's genuinely the semantics you want (e.g. multiple clients subscribing to the same live price ticker without each triggering an independent upstream connection).

# Schedulers -- Which Thread Actually Runs What

--> Reactor pipelines don't run on "no thread" -- they run on whatever `Scheduler` is in effect, and controlling that explicitly matters once real I/O or CPU-bound work enters the pipeline.

| Scheduler | Backed By | Use For |
|---|---|---|
| `Schedulers.parallel()` | Fixed pool sized to CPU core count | CPU-bound work (real computation, not I/O) |
| `Schedulers.boundedElastic()` | Elastic pool, bounded size, grows/shrinks as needed | Blocking calls you CANNOT avoid (a legacy blocking JDBC driver, a blocking third-party SDK) -- deliberately isolates blocking work away from the small event-loop threads |
| `Schedulers.immediate()` | The calling thread itself | Testing, or explicitly opting OUT of thread-switching |

```java
Mono<Report> report = Mono.fromCallable(() -> legacyBlockingReportGenerator.generate())
        .subscribeOn(Schedulers.boundedElastic());   // isolates the blocking call off the event loop
```

--> **`subscribeOn` vs `publishOn`** -- `subscribeOn` affects which thread the SOURCE (the beginning of the pipeline) runs on, and applies regardless of where it's placed in the chain; `publishOn` switches the thread for everything DOWNSTREAM of that point in the chain, from that point on. Getting this distinction right matters for pipelines that mix a blocking source with non-blocking downstream operators, but for the vast majority of everyday WebFlux code (where WebClient and R2DBC drivers are already non-blocking end-to-end), you don't need to manage schedulers by hand at all -- it's mainly relevant when a genuinely blocking call is unavoidable.

# Common Gotchas

--> **Calling `.block()` inside a reactive pipeline (or inside WebFlux request-handling code at all)** -- `.block()` forces a `Mono`/`Flux` to synchronously wait for its result, which defeats the ENTIRE point of being reactive and, worse, can flat-out throw `IllegalStateException` when called from one of WebFlux's own event-loop threads (Reactor Netty explicitly disallows blocking on its own worker threads by default, precisely to surface this mistake loudly rather than silently degrading throughput). `.block()` has a legitimate place only in tests or genuinely non-reactive bootstrapping code, never inside a reactive request-handling chain.
--> **Forgetting a `Mono`/`Flux` is lazy and never gets subscribed to** -- building a pipeline and never returning it from a WebFlux handler (or never explicitly `.subscribe()`-ing it) means the described work simply never runs -- no exception, no log, nothing -- because nothing ever triggered it. If a WebFlux endpoint "does nothing," check whether the returned `Mono` is actually the one being built, not a fire-and-forgotten side pipeline.
--> **Treating `map` and `flatMap` as interchangeable** -- covered above, but worth repeating as a gotcha: reaching for `map` when the transformation is itself asynchronous produces a compile error (`Mono<Mono<X>>`), which is at least caught early, but reaching for `flatMap` when `map` would do adds unnecessary subscription overhead.
--> **Side effects hidden inside `map`** -- `map` is meant for pure transformation; stuffing logging, mutation of external state, or (worse) another blocking call inside a `map` lambda works but obscures intent -- use `doOnNext`/`doOnError`/`doOnComplete` for side effects, keeping `map`/`flatMap` reserved for actual data transformation.
--> **Assuming reactive code is automatically faster** -- for CPU-bound work, or for a downstream dependency that is ITSELF blocking (a JDBC-backed database with no reactive driver), wrapping it in `Mono`/`Flux` adds complexity without adding throughput -- the benefit specifically comes from avoiding thread-per-request WAITING on I/O, not from reactive code being intrinsically faster at computation. This is elaborated further in file 05.

# Best Practices Summary

--> Understand Reactive Streams' `request(n)` / backpressure contract conceptually even though you rarely implement it directly -- it explains why reactive pipelines don't just blindly push as fast as possible.
--> Use `Mono` for "zero or one" results, `Flux` for "zero to many" -- pick based on cardinality, not on habit.
--> Reach for `map` when the transformation is synchronous/pure, `flatMap` when it kicks off another `Mono`/`Flux`-returning operation.
--> Never call `.block()` inside reactive request-handling code -- treat it as strictly a testing/bootstrapping-only escape hatch.
--> Remember publishers are lazy -- nothing runs until something subscribes; always make sure the pipeline you built is the one actually returned/subscribed to.
--> Isolate unavoidable blocking calls onto `Schedulers.boundedElastic()` rather than letting them run on event-loop threads.
--> Treat reactive programming as a tool for I/O-bound concurrency at scale, not a universal performance upgrade -- see file 05 for when it's genuinely worth adopting.
