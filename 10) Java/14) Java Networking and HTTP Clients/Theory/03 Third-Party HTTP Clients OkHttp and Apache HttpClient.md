# Why Reach for a Third-Party HTTP Client at All

--> `java.net.http.HttpClient` (file 02) is a genuinely solid, modern, dependency-free option -- for a large share of projects it is now the right default. Third-party clients like **OkHttp** (Square) and **Apache HttpClient** (Apache Software Foundation) remain extremely widely used, though, for reasons that matter in specific contexts:
  1. **Ecosystem inertia and maturity** -- both predate Java 11's `HttpClient` by many years, so a huge number of existing codebases, tutorials, and libraries (notably Retrofit, which is built directly on OkHttp) are already built around them.
  2. **Features not in the JDK client** -- OkHttp has built-in response caching (an actual HTTP cache respecting `Cache-Control` headers), transparent GZIP compression, and a very ergonomic interceptor chain for cross-cutting concerns (logging, auth headers, retries). Apache HttpClient offers extremely fine-grained connection pool control, both classic blocking and fully async/reactive I/O models, and deep configurability for enterprise proxy/auth scenarios.
  3. **Android compatibility history** -- OkHttp in particular became the de facto standard on Android for years (older Android API levels didn't have Java 11's `HttpClient` available at all).
  4. **Testing utilities** -- OkHttp ships `MockWebServer`, a genuinely excellent library for writing HTTP integration tests against a real (but local, fake) server.

--> **Both libraries require adding an external dependency** (a JAR via Maven/Gradle, or manually on the classpath) -- unlike everything in files 01-02, none of the code below compiles with the JDK alone. The Topic Practicals file for this topic is illustrative/commented code showing the shape of the APIs, not a standalone runnable program.

# OkHttp -- Basic Usage

--> OkHttp's design centers on three classes: `OkHttpClient` (the reusable client, analogous to `HttpClient`), `Request` (analogous to `HttpRequest`), and `Response`/`ResponseBody` (analogous to `HttpResponse`).

```xml
<!-- Maven dependency -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
```

```java
import okhttp3.*;
import java.io.IOException;

OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(5))
        .readTimeout(java.time.Duration.ofSeconds(10))
        .build();

// --- Synchronous GET ---
Request request = new Request.Builder()
        .url("https://example.com/api/users/1")
        .header("Accept", "application/json")
        .build();

try (Response response = client.newCall(request).execute()) {   // .execute() blocks, like HttpClient.send()
    System.out.println("Status: " + response.code());
    System.out.println("Body: " + response.body().string());     // .string() reads and closes the body
}

// --- POST with a JSON body ---
MediaType JSON = MediaType.get("application/json; charset=utf-8");
RequestBody body = RequestBody.create("{\"name\":\"Alice\"}", JSON);
Request postRequest = new Request.Builder()
        .url("https://example.com/api/users")
        .post(body)
        .build();

// --- Asynchronous call ---
client.newCall(request).enqueue(new Callback() {
    @Override public void onFailure(Call call, IOException e) {
        System.out.println("Request failed: " + e.getMessage());
    }
    @Override public void onResponse(Call call, Response response) throws IOException {
        try (response) {
            System.out.println("Async status: " + response.code());
        }
    }
});
```

--> **Gotcha -- `ResponseBody` can only be consumed ONCE**, and it MUST be closed (the try-with-resources on `Response` above handles this, since `Response` implements `Closeable` and closing it closes the body) -- forgetting to close a response leaks the underlying connection and it can't be returned to OkHttp's connection pool.
--> **Interceptors** are OkHttp's signature feature -- a chain of objects that can inspect/modify every request and response, useful for logging, adding auth headers globally, or retry logic without repeating that code at every call site:

```java
OkHttpClient loggingClient = new OkHttpClient.Builder()
        .addInterceptor(chain -> {
            Request original = chain.request();
            System.out.println("--> " + original.method() + " " + original.url());
            Response response = chain.proceed(original);
            System.out.println("<-- " + response.code());
            return response;
        })
        .build();
```

# Apache HttpClient -- Basic Usage

--> Apache HttpClient (currently at major version 5, package `org.apache.hc.client5`) follows a similar shape but with its own naming: `CloseableHttpClient`, `HttpGet`/`HttpPost` request objects, and `ClassicHttpResponse`.

```xml
<!-- Maven dependency -->
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
    <version>5.3</version>
</dependency>
```

```java
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ContentType;

try (CloseableHttpClient client = HttpClients.createDefault()) {

    // --- GET ---
    HttpGet getRequest = new HttpGet("https://example.com/api/users/1");
    getRequest.setHeader("Accept", "application/json");

    client.execute(getRequest, response -> {                 // response consumed inside the lambda
        System.out.println("Status: " + response.getCode());
        String body = EntityUtils.toString(response.getEntity());
        System.out.println("Body: " + body);
        return null;
    });

    // --- POST with a JSON body ---
    HttpPost postRequest = new HttpPost("https://example.com/api/users");
    postRequest.setEntity(new StringEntity("{\"name\":\"Alice\"}", ContentType.APPLICATION_JSON));

    client.execute(postRequest, response -> {
        System.out.println("POST status: " + response.getCode());
        return null;
    });
}
```

--> Apache HttpClient's connection pooling is explicit and highly configurable via `PoolingHttpClientConnectionManager` -- you set max total connections and max connections PER ROUTE (per target host), which matters a great deal in server applications calling many different downstream hosts (expanded on in file 05):

```java
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;

HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
        .setMaxConnTotal(100)
        .setMaxConnPerRoute(20)
        .build();

CloseableHttpClient pooledClient = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .build();
```

--> **Gotcha:** `EntityUtils.toString(response.getEntity())` fully buffers the response body in memory -- like `HttpClient.BodyHandlers.ofString()`, avoid it for very large responses in favor of streaming the entity's `InputStream` directly.

# Comparison Table

| Aspect | `java.net.http.HttpClient` (JDK 11+) | OkHttp | Apache HttpClient 5 |
|---|---|---|---|
| Dependency | None -- built into the JDK | External JAR | External JAR |
| Sync support | Yes (`send`) | Yes (`execute`) | Yes (`execute`) |
| Async support | Yes (`sendAsync` -> `CompletableFuture`) | Yes (`enqueue` -> `Callback`) | Yes (via `httpclient5` async module, reactive `HttpAsyncClient`) |
| HTTP/2 | Yes, built-in with automatic negotiation | Yes, built-in | Yes, via the async module |
| Built-in response caching | No | Yes, opt-in `Cache` respecting `Cache-Control` | No (would need a separate caching layer) |
| Interceptor / middleware chain | No direct equivalent (can wrap calls manually) | Yes -- a first-class, well-documented feature | Yes, via `HttpRequestInterceptor`/`HttpResponseInterceptor` |
| Connection pool tuning | Coarse (client-level only) | Good (`ConnectionPool` class, per-client) | Very fine-grained (per-route limits, custom eviction) |
| Testing tools | None built-in | `MockWebServer` (excellent) | No dedicated equivalent bundled |
| Common historical home | New/modern JVM projects | Android, Retrofit-based projects | Enterprise Java, Spring-based projects (often via `RestTemplate`/`WebClient` internals historically) |
| Learning curve | Low -- fluent, small surface area | Low-medium | Medium-high -- more configuration surface |

--> **Practical guidance:** start with the built-in `HttpClient` for new projects with no existing dependency on the alternatives -- it covers the large majority of needs with zero added dependencies. Reach for OkHttp when you want built-in HTTP caching, its interceptor model, or you're already in an OkHttp-based ecosystem (e.g. using Retrofit). Reach for Apache HttpClient in larger enterprise codebases that need very fine connection-pool control, or where it's already a well-established dependency across the codebase and consistency matters more than trying something new.

# Common Gotchas and Best Practices Recap

--> **Both libraries require explicit dependency management** -- version mismatches (e.g. mixing OkHttp 3.x and 4.x transitively) are a common source of `NoSuchMethodError` at runtime; always check for dependency convergence issues in a multi-module build.
--> **Response bodies must be closed exactly once** -- OkHttp's `Response`/`ResponseBody` and Apache's entity streams both hold live connections; leaking them exhausts the connection pool over time, a subtle and hard-to-diagnose production issue (file 05).
--> **Reuse the client instance** -- both `OkHttpClient` and `CloseableHttpClient` are designed to be built once and shared, exactly like `java.net.http.HttpClient`; each holds its own connection pool and thread resources internally.
--> **Prefer streaming APIs for large payloads** in either library -- avoid `.string()` / `EntityUtils.toString()` style full-buffering methods on anything that might be large.
