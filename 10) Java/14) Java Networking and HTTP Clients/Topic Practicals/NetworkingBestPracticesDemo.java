/*
 * NetworkingBestPracticesDemo.java
 *
 * This file is a MIX of genuinely runnable code and illustrative reference
 * code, clearly labeled section by section:
 *
 *   - RUNNABLE (uses only java.net.http, JDK 11+, no external dependencies):
 *       1. Connect timeout + request/read timeout configuration on HttpClient
 *       2. A reusable retry-with-exponential-backoff-and-jitter wrapper method
 *       3. Idempotency-aware retry decisions (what NOT to retry)
 *
 *   - ILLUSTRATIVE ONLY (shown as commented reference code -- requires
 *     external libraries like OkHttp/Apache HttpClient, or a real internal
 *     CA trust store file, to actually compile/run):
 *       4. Connection pooling knobs across HttpClient / OkHttp / Apache HttpClient
 *       5. Custom SSLContext / TrustManager configuration for internal CAs
 *       6. The dangerous "trust-everything" anti-pattern (shown to explain
 *          why NOT to do it, never to recommend it)
 *
 * This program DEGRADES GRACEFULLY if there is no network access: every
 * network call is wrapped so it prints a clear message and moves on instead
 * of crashing when offline. The public endpoint used where relevant is
 * httpbin.org, a well-known service for testing HTTP clients.
 *
 * Covers Theory chapter:
 *   14) Java Networking and HTTP Clients/Theory/05 Networking Best Practices.md
 *
 * Compile: javac NetworkingBestPracticesDemo.java
 * Run:     java NetworkingBestPracticesDemo
 */

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class NetworkingBestPracticesDemo {

    // A single shared, reusable HttpClient with BOTH timeout phases configured --
    // this is the "always do this" habit emphasized in Theory file 05.
    //   - connectTimeout: bounds establishing the TCP (+ TLS, for HTTPS) connection
    //   - each individual request's .timeout(...): bounds waiting for the response
    //     once the connection is already established
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))   // fail fast if server/network is unreachable
            .build();

    public static void main(String[] args) throws Exception {
        demoTimeoutConfiguration();
        demoRetryWithBackoff();
        demoIdempotencyAwareRetryDecision();
        demoConnectionPoolingNotes();
        demoSslTlsConcepts();
        System.out.println("\nAll Networking Best Practices demos completed.");
    }

    private static void printSection(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // =====================================================================
    // 1) RUNNABLE -- connect timeout + request/read timeout configuration
    // =====================================================================

    private static void demoTimeoutConfiguration() {
        printSection("1) Timeouts -- connect timeout (client) + request timeout (per-request)");

        System.out.println("CLIENT connectTimeout: 3 seconds (bounds TCP/TLS handshake)");
        System.out.println("Per-request timeout is set independently on each HttpRequest below.");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/get"))
                .timeout(Duration.ofSeconds(8))   // bounds waiting for the response after connecting
                .GET()
                .build();

        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response.statusCode());
        } catch (Exception e) {
            System.out.println("Request failed (likely no network access in this environment): "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        // Demonstrating WHY a missing timeout is dangerous: connecting to a
        // private, non-routable address with an intentionally short connect
        // timeout shows a bounded failure instead of a hang.
        HttpClient shortTimeoutClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest unreachable = HttpRequest.newBuilder()
                .uri(URI.create("http://10.255.255.1/"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        long start = System.nanoTime();
        try {
            shortTimeoutClient.send(unreachable, HttpResponse.BodyHandlers.ofString());
            System.out.println("Unexpectedly connected -- environment network topology differs from assumptions.");
        } catch (Exception e) {
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("Connection attempt to a non-routable address failed after ~" + tookMs
                    + "ms, as bounded by connectTimeout (" + e.getClass().getSimpleName() + ").");
            System.out.println("Without a connect timeout, this call could hang the calling thread indefinitely.");
        }
    }

    // =====================================================================
    // 2) RUNNABLE -- retry with exponential backoff and jitter
    // =====================================================================

    /**
     * Sends a request, retrying on IOException or a 5xx server response, using
     * exponential backoff with jitter between attempts. Does NOT retry on 4xx
     * client errors (those represent a problem retrying won't fix) except that
     * callers should special-case 429 Too Many Requests + Retry-After in real
     * code -- omitted here to keep the wrapper focused and readable.
     *
     * @param client       the HttpClient to use
     * @param request      the request to send (should be idempotent -- see
     *                     demoIdempotencyAwareRetryDecision() below)
     * @param maxRetries   maximum number of RETRY attempts after the first try
     * @param baseDelayMs  base delay in milliseconds for the backoff calculation
     * @return the final HttpResponse, whether success or a non-retryable failure
     * @throws IOException          if every attempt fails with an I/O error
     * @throws InterruptedException if interrupted while sleeping between retries
     */
    private static HttpResponse<String> sendWithRetry(HttpClient client, HttpRequest request,
                                                        int maxRetries, long baseDelayMs)
            throws IOException, InterruptedException {

        IOException lastIoException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 500) {
                    // Success (2xx/3xx), or a 4xx client error that retrying won't fix --
                    // stop and return either way.
                    return response;
                }
                System.out.println("Attempt " + (attempt + 1) + " got server error "
                        + response.statusCode() + ", will retry if attempts remain.");
                // else: fall through to backoff + retry on 5xx server errors

            } catch (IOException e) {
                lastIoException = e;
                System.out.println("Attempt " + (attempt + 1) + " failed with "
                        + e.getClass().getSimpleName() + ", will retry if attempts remain.");
                if (attempt == maxRetries) {
                    throw e;   // out of retries, propagate the failure
                }
            }

            if (attempt == maxRetries) {
                // Ran out of retries on a persistent 5xx -- return the last response
                // rather than throwing, since we do have a real HTTP response.
                break;
            }

            long backoff = baseDelayMs * (1L << attempt);            // 200, 400, 800, 1600 ms...
            long jitter = (long) (Math.random() * backoff * 0.2);     // up to +/-20% randomness
            System.out.println("Backing off " + (backoff + jitter) + "ms before next attempt...");
            Thread.sleep(backoff + jitter);
        }

        if (lastIoException != null) {
            throw lastIoException;
        }
        // Re-send one last time to obtain the final response object to return
        // (kept simple/explicit for demo clarity rather than restructuring the loop).
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void demoRetryWithBackoff() {
        printSection("2) Retry-with-backoff wrapper -- exponential backoff + jitter");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/status/500"))   // deliberately returns HTTP 500
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        try {
            HttpResponse<String> response = sendWithRetry(CLIENT, request, 3, 200);
            System.out.println("Final status after retry attempts: " + response.statusCode());
        } catch (Exception e) {
            System.out.println("All retry attempts exhausted (or no network access): "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    // =====================================================================
    // 3) RUNNABLE -- idempotency-aware retry decision
    // =====================================================================

    /**
     * Returns whether it is generally safe to automatically retry a request
     * of the given HTTP method without an extra safeguard (like an
     * idempotency key). GET/PUT/DELETE/HEAD/OPTIONS are conventionally
     * idempotent; POST and PATCH are frequently NOT (e.g. "create a new
     * order" -- retrying a timed-out POST risks creating it twice if the
     * first attempt actually succeeded server-side but the response was lost).
     */
    private static boolean isSafeToRetryWithoutSafeguard(String httpMethod) {
        switch (httpMethod.toUpperCase()) {
            case "GET":
            case "HEAD":
            case "PUT":
            case "DELETE":
            case "OPTIONS":
                return true;
            case "POST":
            case "PATCH":
                return false;
            default:
                return false;
        }
    }

    private static void demoIdempotencyAwareRetryDecision() {
        printSection("3) Idempotency -- deciding what is safe to retry");

        String[] methods = { "GET", "POST", "PUT", "DELETE", "PATCH" };
        for (String method : methods) {
            boolean safe = isSafeToRetryWithoutSafeguard(method);
            System.out.println(method + " -> " + (safe
                    ? "safe to retry automatically"
                    : "NOT safe to retry without an idempotency key or similar safeguard"));
        }
        System.out.println();
        System.out.println("Note: 429 Too Many Requests is a special case worth retrying (often");
        System.out.println("with a Retry-After header telling you exactly how long to wait), even");
        System.out.println("though it is a 4xx status -- simplified out of the wrapper above for clarity.");
    }

    // =====================================================================
    // 4) ILLUSTRATIVE -- connection pooling in HTTP clients
    // =====================================================================
    //
    // The code below is commented out because it either requires external
    // dependencies (OkHttp, Apache HttpClient 5) not on this repo's plain-JDK
    // classpath, or simply illustrates a concept (java.net.http.HttpClient's
    // pooling is automatic and has no explicit "pool size" knob to print).

    private static void demoConnectionPoolingNotes() {
        printSection("4) Connection pooling in HTTP clients (illustrative notes)");

        System.out.println("java.net.http.HttpClient pools connections automatically and");
        System.out.println("transparently, PROVIDED the same HttpClient instance is reused across");
        System.out.println("requests (as CLIENT is reused throughout this file) -- building a fresh");
        System.out.println("HttpClient per call is a real performance bug: each new instance starts");
        System.out.println("with an empty pool and pays a full TCP+TLS handshake every time.");
        System.out.println();
        System.out.println("OkHttp and Apache HttpClient expose pooling knobs directly -- illustrative,");
        System.out.println("requires those libraries on the classpath (see ThirdPartyHttpClientsDemo.java):");
        System.out.println();

        /*
         * --- OkHttp: tunable idle connection pool ---
         *
         * import okhttp3.ConnectionPool;
         * import java.util.concurrent.TimeUnit;
         *
         * OkHttpClient client = new OkHttpClient.Builder()
         *         .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
         *         // up to 10 idle connections kept alive for 5 minutes each
         *         .build();
         */

        /*
         * --- Apache HttpClient 5: pooling with a global cap AND a per-route cap ---
         *
         * import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
         * import org.apache.hc.client5.http.io.HttpClientConnectionManager;
         * import org.apache.hc.client5.http.impl.classic.HttpClients;
         *
         * HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
         *         .setMaxConnTotal(100)     // global cap across ALL hosts
         *         .setMaxConnPerRoute(20)   // cap for any SINGLE host -- prevents one host
         *                                   // monopolizing the entire pool
         *         .build();
         *
         * CloseableHttpClient pooledClient = HttpClients.custom()
         *         .setConnectionManager(connectionManager)
         *         .build();
         */

        System.out.println("Best practice: size a shared pooled client to the application's actual");
        System.out.println("concurrency needs -- too small causes queuing under load, too large can");
        System.out.println("overwhelm a downstream service or exhaust local file descriptors.");
    }

    // =====================================================================
    // 5) MOSTLY ILLUSTRATIVE -- SSL/TLS / HTTPS basics
    // =====================================================================
    //
    // The certificate-failure handling below IS real, runnable code (it
    // will simply not trigger unless a real handshake failure occurs against
    // httpbin.org, which normally has a valid certificate). The custom
    // SSLContext / trust-store section further down is commented out because
    // it depends on a trust store FILE that does not exist in this repo, and
    // the "trust everything" block is commented out because it is a
    // dangerous anti-pattern shown ONLY to explain why not to do it.

    private static void demoSslTlsConcepts() {
        printSection("5) SSL/TLS basics for HTTPS connections");

        System.out.println("HTTPS = HTTP running on top of a TLS-encrypted connection. TLS provides:");
        System.out.println("  - encryption   : traffic can't be read by a third-party observer");
        System.out.println("  - integrity    : tampering with data in transit is detectable");
        System.out.println("  - authentication: the client can verify it's talking to the real server");
        System.out.println();

        // A real, runnable HTTPS request -- java.net.http.HttpClient handles the
        // entire TLS handshake (certificate verification, key exchange) transparently.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/get"))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTPS request succeeded transparently -- status " + response.statusCode());
        } catch (javax.net.ssl.SSLHandshakeException e) {
            // A real symptom of a certificate problem: expired cert, self-signed
            // cert not in the trust store, hostname mismatch, or a TLS-inspecting
            // corporate proxy presenting its own certificate.
            System.out.println("TLS handshake failed -- likely a certificate trust problem: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("HTTPS request failed (likely no network access in this environment): "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        System.out.println();
        System.out.println("--- Illustrative: custom SSLContext for an internal/self-signed CA ---");
        /*
         * import javax.net.ssl.SSLContext;
         * import javax.net.ssl.TrustManagerFactory;
         * import java.security.KeyStore;
         *
         * KeyStore trustStore = KeyStore.getInstance("JKS");
         * try (var in = new java.io.FileInputStream("internal-ca-truststore.jks")) {
         *     trustStore.load(in, "changeit".toCharArray());
         * }
         * TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
         * tmf.init(trustStore);
         *
         * SSLContext sslContext = SSLContext.getInstance("TLS");
         * sslContext.init(null, tmf.getTrustManagers(), null);
         *
         * HttpClient client = HttpClient.newBuilder()
         *         .sslContext(sslContext)   // trusts exactly the CAs in this custom trust store
         *         .build();
         */
        System.out.println("(Requires a real trust store file containing the internal CA cert --");
        System.out.println(" see Theory file 05 for the full example. Not runnable in this repo.)");

        System.out.println();
        System.out.println("--- Illustrative and DANGEROUS -- shown only to explain why NOT to do it ---");
        /*
         * // DANGEROUS -- disables ALL certificate validation. Never do this in real code.
         * // It defeats the entire purpose of TLS, making the connection trivially
         * // vulnerable to a man-in-the-middle attack, since ANY certificate
         * // (including one presented by an attacker) is accepted as valid.
         *
         * TrustManager[] trustAllCerts = { new X509TrustManager() {
         *     public void checkClientTrusted(X509Certificate[] chain, String authType) {}
         *     public void checkServerTrusted(X509Certificate[] chain, String authType) {}
         *     public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
         * }};
         */
        System.out.println("(Never installed for real anywhere in this file -- illustrating the");
        System.out.println(" anti-pattern only. The correct fix for a legitimate self-signed cert");
        System.out.println(" is to import it into the trust store, not to trust everything.)");

        System.out.println();
        System.out.println("Minimum TLS version: target TLS 1.2 as a floor, TLS 1.3 preferred --");
        System.out.println("current JDKs negotiate this automatically without explicit configuration.");
    }
}
