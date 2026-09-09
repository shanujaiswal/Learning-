# Why a New HTTP Client Was Added in Java 11

--> Before Java 11, the only built-in HTTP client was the old `HttpURLConnection` (file 01) -- functional, but verbose, blocking-only, awkward to configure, and predating HTTP/2 entirely. `java.net.http.HttpClient` (introduced as an incubating API in Java 9, standardized in Java 11) is a ground-up modern replacement: a fluent builder API, native HTTP/2 support (with automatic fallback to HTTP/1.1), both synchronous AND asynchronous request execution, and pluggable request/response body handling.
--> This is now the recommended default for HTTP calls from Java code that doesn't already depend on a third-party client (file 03 covers when OkHttp or Apache HttpClient are still worth reaching for).

# The Three Core Classes

--> Working with the modern API always involves the same three pieces, used together:

| Class | Role |
|---|---|
| `HttpClient` | The reusable client itself -- holds connection pooling, timeouts, redirect policy, executor. Build once, reuse across many requests. |
| `HttpRequest` | An immutable description of ONE request -- method, URI, headers, body. Built via `HttpRequest.newBuilder()`. |
| `HttpResponse<T>` | The result of sending a request -- status code, headers, and a body of type `T` (String, byte[], InputStream, etc., depending on the `BodyHandler` used). |

```text
HttpClient (build once)  --send(request, bodyHandler)-->  HttpResponse<T>
      ^                            ^
      |                            |
  HttpClient.newBuilder()    HttpRequest.newBuilder()
   .connectTimeout(...)       .uri(...)
   .build()                   .GET() / .POST(...) / etc.
                              .build()
```

# Building an `HttpClient`

```java
import java.net.http.HttpClient;
import java.time.Duration;

HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)          // preferred; falls back to HTTP_1_1 if server doesn't support it
        .connectTimeout(Duration.ofSeconds(5))        // max time to establish the TCP/TLS connection
        .followRedirects(HttpClient.Redirect.NORMAL)  // follow redirects except HTTPS->HTTP downgrades
        .build();

// Or, for quick one-off usage with all defaults:
HttpClient simple = HttpClient.newHttpClient();
```

--> **`HttpClient` instances are immutable and thread-safe** -- build ONE and reuse it for every request in your application. Each new `HttpClient` maintains its own connection pool; creating a fresh one per request throws away connection reuse and is a common performance mistake (expanded on in file 05).
--> Other useful builder options: `.executor(ExecutorService)` (controls what thread pool async callbacks run on), `.proxy(ProxySelector)`, `.authenticator(Authenticator)` (for HTTP Basic auth handled transparently), `.sslContext(SSLContext)` / `.sslParameters(SSLParameters)` for custom TLS configuration (file 05).

# Building an `HttpRequest`

```java
import java.net.http.HttpRequest;
import java.net.URI;
import java.time.Duration;

HttpRequest getRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/api/users/1"))
        .header("Accept", "application/json")
        .timeout(Duration.ofSeconds(10))              // per-request timeout, overrides client default
        .GET()                                          // GET is the default if no method is specified
        .build();
```

--> `HttpRequest` objects are IMMUTABLE once built -- the builder pattern produces a new, unchangeable request description each time, which is what makes it safe to build a request once and send it (or a copy of it) multiple times, even concurrently.
--> Common builder methods: `.GET()`, `.POST(bodyPublisher)`, `.PUT(bodyPublisher)`, `.DELETE()`, or the generic `.method(String, bodyPublisher)` for anything else (e.g. `PATCH`, which has no dedicated shortcut method). `.header(name, value)` adds one header; `.headers(k1, v1, k2, v2, ...)` adds several at once.

## `HttpRequest.BodyPublishers` -- Sending Request Bodies

--> A `BodyPublisher` describes the CONTENT of an outgoing request body (used with `POST`/`PUT`/etc.) and how it should be streamed to the server. `HttpRequest.BodyPublishers` provides ready-made implementations for the common cases.

```java
import java.net.http.HttpRequest.BodyPublishers;

// Plain text / JSON string body
HttpRequest jsonPost = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/api/users"))
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString("{\"name\":\"Alice\",\"age\":30}"))
        .build();

// Raw bytes
HttpRequest byteBody = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/upload"))
        .POST(BodyPublishers.ofByteArray(new byte[]{1, 2, 3}))
        .build();

// Upload a file directly from disk (streamed, doesn't load the whole file into memory)
HttpRequest fileUpload = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/upload"))
        .POST(BodyPublishers.ofFile(java.nio.file.Path.of("data.zip")))
        .build();

// A request with NO body at all (e.g. for GET/DELETE, or a POST that intentionally sends nothing)
HttpRequest noBody = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/ping"))
        .POST(BodyPublishers.noBody())
        .build();

// Form-encoded body (application/x-www-form-urlencoded) -- built manually since there's no dedicated helper
String form = "username=" + java.net.URLEncoder.encode("alice", java.nio.charset.StandardCharsets.UTF_8)
            + "&age=30";
HttpRequest formPost = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/login"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(BodyPublishers.ofString(form))
        .build();
```

--> **Gotcha:** `HttpClient` does NOT set `Content-Type` automatically based on the body publisher used -- you must set it yourself via `.header("Content-Type", ...)`, or many servers will reject/misinterpret the request body.

# Sending Requests -- Synchronous vs Asynchronous

--> This is the central choice every call site makes: block the current thread until the response arrives (`send`), or get a `CompletableFuture` immediately and react when the response eventually arrives (`sendAsync`).

## Synchronous -- `send()`

```java
import java.net.http.HttpResponse;

HttpResponse<String> response = client.send(getRequest, HttpResponse.BodyHandlers.ofString());

System.out.println("Status: " + response.statusCode());
System.out.println("Body: " + response.body());
System.out.println("Headers: " + response.headers().map());
```

--> `send()` BLOCKS the calling thread until the full response is received (or a timeout/error occurs). It throws the checked `IOException` (network problems) and `InterruptedException` (if the thread is interrupted while waiting) -- both must be handled or declared.
--> Simple, easy to reason about, and perfectly fine for CLI tools, batch scripts, or any code path where blocking one thread while waiting for one HTTP call is acceptable.

## Asynchronous -- `sendAsync()`

```java
import java.util.concurrent.CompletableFuture;

CompletableFuture<HttpResponse<String>> future =
        client.sendAsync(getRequest, HttpResponse.BodyHandlers.ofString());

future
    .thenApply(HttpResponse::body)                       // transform: response -> just its body
    .thenAccept(body -> System.out.println("Got: " + body))
    .exceptionally(ex -> {
        System.out.println("Request failed: " + ex.getMessage());
        return null;
    });

System.out.println("This line runs immediately -- doesn't wait for the HTTP response");
```

--> `sendAsync()` returns IMMEDIATELY with a `CompletableFuture<HttpResponse<T>>` -- the actual network I/O happens on a background thread (from the client's configured executor, or a default shared pool), and callbacks chained with `.thenApply()`/`.thenAccept()`/etc. run when the response arrives. Unlike `send()`, network/protocol errors surface through the future's exceptional completion path rather than as thrown checked exceptions -- catch them with `.exceptionally()` or `.handle()`.
--> **Best practice:** use `sendAsync()` (or its `CompletableFuture` composition, e.g. `CompletableFuture.allOf(...)` to fire many requests concurrently and wait for all of them) whenever a single thread needs to juggle multiple in-flight HTTP calls, such as a server handling many incoming requests each needing to call out to other services -- blocking one server thread per outgoing call scales poorly under load, mirroring the same "don't block a thread waiting on I/O" lesson from the Executor framework topic.

```java
// Firing multiple requests concurrently and waiting for all of them
CompletableFuture<HttpResponse<String>> f1 = client.sendAsync(request1, HttpResponse.BodyHandlers.ofString());
CompletableFuture<HttpResponse<String>> f2 = client.sendAsync(request2, HttpResponse.BodyHandlers.ofString());

CompletableFuture.allOf(f1, f2).join();     // blocks only until BOTH finish, but both ran concurrently
System.out.println(f1.join().statusCode() + " / " + f2.join().statusCode());
```

# `HttpResponse.BodyHandlers` -- Consuming Response Bodies

--> Just as `BodyPublishers` describes an outgoing body, `BodyHandlers` describes how to interpret the INCOMING response body -- as a `String`, raw bytes, a file on disk, a stream, or discarded entirely.

| `BodyHandlers` factory | `HttpResponse<T>`'s `T` | Use case |
|---|---|---|
| `ofString()` | `String` | Text/JSON responses, small enough to hold fully in memory |
| `ofByteArray()` | `byte[]` | Binary responses (images, etc.), held fully in memory |
| `ofInputStream()` | `InputStream` | Large responses -- read incrementally instead of buffering it all |
| `ofFile(Path)` | `Path` | Stream the response body straight to disk (e.g. downloading a large file) |
| `discarding()` | `Void` | You only care about the status code / headers, not the body content |

```java
// Streaming a large download directly to disk instead of loading it into memory
HttpRequest download = HttpRequest.newBuilder(URI.create("https://example.com/big-file.zip")).build();
HttpResponse<java.nio.file.Path> saved = client.send(download,
        HttpResponse.BodyHandlers.ofFile(java.nio.file.Path.of("big-file.zip")));
System.out.println("Saved to: " + saved.body());
```

# Reading Status Codes and Headers

```java
HttpResponse<String> response = client.send(getRequest, HttpResponse.BodyHandlers.ofString());

int status = response.statusCode();                       // e.g. 200, 404, 500
boolean ok = status >= 200 && status < 300;                 // HttpClient has no built-in "isSuccessful()" helper

java.net.http.HttpHeaders headers = response.headers();
headers.firstValue("Content-Type").ifPresent(System.out::println);
headers.map().forEach((name, values) -> System.out.println(name + ": " + values));

URI finalUri = response.uri();          // the URI actually used, after following any redirects
HttpClient.Version protocol = response.version();   // which HTTP version was actually negotiated
```

--> **Gotcha:** `HttpClient` does NOT throw an exception for HTTP error status codes (404, 500, etc.) -- a `4xx`/`5xx` response is still a perfectly normal, successfully-received `HttpResponse`. Checked/unchecked exceptions (`IOException`, `ConnectException`, `HttpTimeoutException`) represent NETWORK-level failures (couldn't connect, timed out, connection reset), not application-level HTTP error codes. Always check `statusCode()` explicitly if the calling code cares about success vs failure.

# Timeouts

--> Two independent timeout settings exist, and conflating them is a common bug:

```java
HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))    // max time to establish the connection itself
        .build();

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/slow-endpoint"))
        .timeout(Duration.ofSeconds(10))            // max time for the WHOLE request/response exchange
        .build();
```

--> `connectTimeout` (set on the `HttpClient`) bounds only the TCP/TLS handshake. `timeout` (set per-`HttpRequest`) bounds the entire request, including waiting for the response body -- if it elapses, `send()`/`sendAsync()` fails with `HttpTimeoutException`. Without an explicit request timeout, a slow or hanging server can leave `send()` blocked indefinitely (see file 05 for why this is a serious production risk).

# Common Gotchas and Best Practices Recap

--> **Build one `HttpClient` and reuse it** -- it is immutable, thread-safe, and pools connections internally; constructing a new one per request defeats connection reuse (file 05) and wastes resources.
--> **A successful HTTP exchange with a 404/500 status is still a normal `HttpResponse`** -- always check `statusCode()` yourself; exceptions signal network-level failure, not HTTP-level failure.
--> **Set `Content-Type` explicitly** -- `BodyPublishers` never infers or sets it for you.
--> **Always set a request timeout** -- an `HttpRequest` with no `.timeout(...)` can hang the calling thread (or the async callback) indefinitely if the server never responds.
--> **Prefer `sendAsync()` when a single thread must juggle multiple outstanding HTTP calls** -- e.g. a server fanning out to several downstream services per incoming request; use `send()` for simple sequential scripts/tools where blocking is fine.
--> **`ofString()`/`ofByteArray()` buffer the ENTIRE response body in memory** -- for large downloads, use `ofFile(Path)` or `ofInputStream()` instead to avoid `OutOfMemoryError` on huge responses.
