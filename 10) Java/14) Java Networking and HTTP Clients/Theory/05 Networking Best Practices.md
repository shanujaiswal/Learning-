# Why Best Practices Matter More in Networking Than Almost Anywhere Else

--> Code that talks over a network is fundamentally different from code that only touches local memory or disk: the other end can be slow, unreachable, overloaded, or malicious, and none of that is under your control. Every one of the practices below exists because "the network call just hangs / fails / gets hit too often" is one of the single most common causes of production incidents in real systems -- a single missing timeout has taken down entire services by exhausting every available thread waiting on one slow dependency.

# Timeouts -- The Single Most Important Habit

--> Every network call has (at minimum) two distinct phases that can each hang independently, and both need their own timeout:

| Timeout type | What it bounds | `HttpClient` equivalent |
|---|---|---|
| Connect timeout | Establishing the TCP (and TLS, for HTTPS) connection itself | `HttpClient.newBuilder().connectTimeout(Duration)` |
| Request/read timeout | Waiting for the response once the connection is established | `HttpRequest.newBuilder().timeout(Duration)` |

```java
HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))     // fail fast if the server/network is unreachable
        .build();

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/api"))
        .timeout(Duration.ofSeconds(10))            // fail fast if the server is slow to respond
        .build();
```

--> **Gotcha -- "no timeout" is not neutral, it's a footgun.** Without an explicit timeout, a hung dependency can block a calling thread FOREVER. In a thread-pool-backed server (Executor framework topic), enough concurrently hung calls exhaust the entire pool, and the server stops making progress on ANY request -- not just the ones talking to the slow dependency. This failure mode (one slow downstream service cascading into a total outage of the calling service) is common enough to have a name: **cascading failure**.
--> **Best practice:** treat "what should this call's timeout be?" as a required question for every network call in application code, the same way null-checking is a required habit -- never rely on defaults you haven't actually verified, since some libraries have surprisingly long (or literally infinite) defaults.
--> Raw sockets need the same discipline: `Socket.setSoTimeout(millis)` (file 01) bounds blocking reads; without it, `read()`/`readLine()` can hang forever on a connection the other side never closes or writes to again.

# Connection Pooling in HTTP Clients

--> Establishing a fresh TCP connection (and, for HTTPS, a fresh TLS handshake on top of it) for every single HTTP request is expensive -- it means paying the full round-trip cost of the TCP three-way handshake and the TLS handshake (itself multiple round trips) before a single byte of actual application data moves. **Connection pooling** keeps a set of already-established, idle connections to recently-used hosts around, ready to reuse for the next request to that same host, entirely avoiding that repeated setup cost.

```text
Without pooling:                          With pooling:
Request 1: TCP+TLS handshake -> data      Request 1: TCP+TLS handshake -> data -> KEEP CONNECTION OPEN
Request 2: TCP+TLS handshake -> data      Request 2: reuse open connection -> data (handshake skipped!)
Request 3: TCP+TLS handshake -> data      Request 3: reuse open connection -> data (handshake skipped!)
   (full setup cost paid every time)         (setup cost paid once per host, amortized across many requests)
```

--> `java.net.http.HttpClient` pools connections automatically and transparently PROVIDED you reuse the same `HttpClient` instance across requests (file 02) -- this is precisely why building a fresh `HttpClient` per call is a real performance bug, not just a style nitpick: each new instance starts with an empty pool.
--> OkHttp exposes its pool directly via `ConnectionPool`, letting you tune how many idle connections are kept and for how long:

```java
import okhttp3.ConnectionPool;
import java.util.concurrent.TimeUnit;

OkHttpClient client = new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))  // up to 10 idle connections, 5 min keep-alive
        .build();
```

--> Apache HttpClient's `PoolingHttpClientConnectionManager` (file 03) exposes the finest control of the three, notably distinguishing a global cap (`setMaxConnTotal`) from a PER-ROUTE cap (`setMaxConnPerRoute`, i.e. per distinct target host) -- important in any service that calls many different downstream hosts, since without a per-route limit one very chatty host could otherwise monopolize the entire pool.
--> **Best practice:** in a server application making outbound HTTP calls, always use a shared, pooled client (whichever library) sized to the application's actual concurrency needs -- too small a pool creates queuing/contention under load, too large a pool can overwhelm a downstream service or exhaust local resources (file descriptors).

# Retry Strategies

--> Transient failures -- a momentary network blip, a downstream service briefly overloaded, a single dropped packet -- are common enough in real networks that blindly failing on the very first error is often the wrong default. Retrying is the standard mitigation, but done carelessly it can make things WORSE, not better.

## Idempotency -- The Precondition for Safe Retries

--> **Only retry operations that are safe to run more than once.** `GET`, `PUT`, and `DELETE` are conventionally idempotent (repeating them has the same effect as doing them once). `POST` frequently is NOT idempotent (e.g. "create a new order" -- retrying a timed-out `POST` risks creating the order twice if the first attempt actually succeeded server-side but the response was lost). Never blindly retry a non-idempotent request without a safeguard (e.g. an idempotency key the server can use to detect and ignore a duplicate).

## Exponential Backoff with Jitter

--> Retrying immediately, in a tight loop, is a classic anti-pattern -- if a downstream service is struggling because it's overloaded, an army of clients all retrying instantly makes the overload worse, potentially preventing the service from ever recovering (a **retry storm**). The standard fix is **exponential backoff**: wait progressively longer between each retry attempt, and add **jitter** (a small random variation) so that many clients retrying after the same failure don't all retry at exactly the same moment in lockstep.

```java
int maxRetries = 4;
long baseDelayMs = 200;

for (int attempt = 0; attempt <= maxRetries; attempt++) {
    try {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 500) {
            // Success, or a client error (4xx) that retrying won't fix -- stop here either way
            return response;
        }
        // else: fall through and retry on 5xx server errors
    } catch (java.io.IOException e) {
        if (attempt == maxRetries) throw e;   // out of retries, propagate the failure
    }

    long backoff = baseDelayMs * (1L << attempt);                       // 200, 400, 800, 1600 ms...
    long jitter = (long) (Math.random() * backoff * 0.2);                // up to +/-20% randomness
    Thread.sleep(backoff + jitter);
}
```

--> **What NOT to retry:** most `4xx` client errors (bad request, unauthorized, not found) represent a problem that repeating the exact same request will not fix -- retrying `400`/`401`/`403`/`404` is typically wasted effort (with the notable exception of `429 Too Many Requests`, which explicitly means "you're being rate-limited, try again later," often with a `Retry-After` header telling you exactly how long to wait).
--> **Best practice -- cap the total retry budget** (a max attempt count AND/OR a max total elapsed time), so a persistently failing dependency fails the calling code within a bounded, predictable time rather than retrying indefinitely.

## Circuit Breakers (Conceptual Extension)

--> For services that call the same downstream dependency very frequently, retrying every single failing call independently still means every caller pays the full retry delay before giving up during an extended outage. A **circuit breaker** pattern (implemented by libraries like Resilience4j, not something covered by the JDK itself) tracks the recent failure rate to a dependency and, once it crosses a threshold, "opens the circuit" -- failing fast immediately without even attempting the call for a cooldown period, then cautiously testing whether the dependency has recovered. This is a natural next step once basic retry-with-backoff is in place, worth knowing exists even if implementing one from scratch is out of scope here.

# SSL/TLS Basics for HTTPS Connections

--> HTTPS is simply HTTP running on top of a TLS-encrypted connection -- TLS provides three guarantees on top of plain TCP: **encryption** (a third party observing the traffic can't read it), **integrity** (tampering with data in transit is detectable), and **authentication** (the client can cryptographically verify it's actually talking to the server it intended to, not an impostor).

```text
Plain HTTP:                              HTTPS:
Client <---- readable bytes ----> Server  Client <-- TLS handshake (cert verification, key exchange) --> Server
        (anyone on the network              Client <====== encrypted tunnel ======> Server
         path can read/modify it)                    (HTTP request/response flow inside this)
```

## The Handshake, Briefly

--> When connecting to an `https://` URL, before any HTTP request/response is exchanged, client and server perform a TLS handshake: the server presents a **certificate** (issued by a trusted Certificate Authority, or CA) proving its identity, the client verifies that certificate's signature chain against a set of trusted root CAs it already knows about, and both sides negotiate a shared symmetric encryption key for the rest of the session. `java.net.http.HttpClient`, OkHttp, and Apache HttpClient all handle this transparently for any `https://` URI -- application code generally never touches TLS mechanics directly unless it needs custom trust configuration.

## Certificate Validation Failures

--> `javax.net.ssl.SSLHandshakeException` (often wrapping a `sun.security.validator.ValidatorException` or `CertificateException`) is the typical symptom of a certificate problem: an expired certificate, a self-signed certificate not in the trust store, a hostname mismatch, or (in corporate networks) a TLS-inspecting proxy presenting its own certificate in place of the real one.

```java
try {
    client.send(request, HttpResponse.BodyHandlers.ofString());
} catch (javax.net.ssl.SSLHandshakeException e) {
    System.out.println("TLS handshake failed -- likely a certificate trust problem: " + e.getMessage());
}
```

--> **Gotcha -- never disable certificate validation as a "fix."** A common but dangerous shortcut seen in some codebases is installing a trust-everything `TrustManager` to make handshake errors go away:

```java
// DANGEROUS -- disables all certificate validation. Do not do this in real code.
TrustManager[] trustAllCerts = { new X509TrustManager() {
    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
}};
```

--> This defeats the entire purpose of TLS -- it makes the connection trivially vulnerable to a man-in-the-middle attack, since ANY certificate (including one presented by an attacker) is now accepted as valid. The correct fix for a legitimate internal/self-signed certificate is to import it into the JVM's trust store (`keytool -importcert`) or configure a custom `SSLContext`/`TrustManager` that trusts specifically that certificate/CA -- not one that trusts everything.

## `SSLContext` for Custom Trust Configuration

```java
import javax.net.ssl.SSLContext;

// Using a custom trust store file containing a specific internal CA certificate
KeyStore trustStore = KeyStore.getInstance("JKS");
try (var in = new java.io.FileInputStream("internal-ca-truststore.jks")) {
    trustStore.load(in, "changeit".toCharArray());
}
TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trustStore);

SSLContext sslContext = SSLContext.getInstance("TLS");
sslContext.init(null, tmf.getTrustManagers(), null);

HttpClient client = HttpClient.newBuilder()
        .sslContext(sslContext)          // trusts exactly the CAs in this custom trust store, nothing more
        .build();
```

## Minimum TLS Version

--> TLS 1.0 and 1.1 are deprecated and considered insecure by modern standards (both have known weaknesses and are disabled by default in current JDKs and browsers). Modern deployments should target **TLS 1.2 as a floor, with TLS 1.3 preferred** where the server supports it -- current JDKs negotiate this automatically and generally do not need explicit configuration, but it's worth confirming when auditing an older system or a custom `SSLContext`.

# Common Gotchas and Best Practices Recap

--> **Never make a network call without both a connect timeout and a request/read timeout** -- this single habit prevents the majority of "one slow dependency freezes the whole application" incidents.
--> **Always reuse a single, shared, pooled HTTP client instance** rather than constructing a new one per call -- true for `HttpClient`, OkHttp's `OkHttpClient`, and Apache's `CloseableHttpClient` alike.
--> **Only retry idempotent operations without extra safeguards** -- and always back off exponentially with jitter rather than retrying in a tight loop, to avoid worsening an already-struggling dependency.
--> **Cap retry attempts and/or total retry time** -- unbounded retries just delay an inevitable failure while consuming more resources.
--> **Never disable TLS certificate validation to silence an error** -- fix the actual trust configuration (import the right CA certificate) instead of trusting everything.
--> **Prefer TLS 1.2+ and let the JDK negotiate the best mutually-supported version** rather than hand-pinning an older, weaker protocol version.
--> **Close every response body/connection you open** -- leaked connections silently shrink the effective size of your connection pool over time until the whole application starts timing out under normal load, often the actual root cause behind mysterious slow-down-then-outage incidents that look unrelated to networking at first glance.
