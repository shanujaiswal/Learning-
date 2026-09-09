/*
 * ModernHttpClientDemo.java
 *
 * Demonstrates:
 *   1. Building an HttpClient (timeouts, redirect policy)
 *   2. Building GET/POST HttpRequests with BodyPublishers
 *   3. Synchronous send() -- status code, headers, body
 *   4. Asynchronous sendAsync() with CompletableFuture composition
 *   5. Firing multiple requests concurrently and waiting for all of them
 *   6. Handling a bad host / timeout gracefully (network-independent)
 *
 * This file uses only java.net.http (JDK 11+) -- no external dependencies.
 * It DEGRADES GRACEFULLY if there is no network access: every network call
 * is wrapped so the program prints a clear message and moves on instead of
 * crashing when offline. The public HTTP endpoint used is httpbin.org, a
 * well-known service for testing HTTP clients (echoes back request info).
 *
 * Covers Theory chapter:
 *   14) Java Networking and HTTP Clients/Theory/02 Modern java.net.http.HttpClient.md
 *
 * Compile: javac ModernHttpClientDemo.java
 * Run:     java ModernHttpClientDemo
 */

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ModernHttpClientDemo {

    // A single shared, reusable HttpClient -- built once, used for every request in this demo.
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void main(String[] args) throws Exception {
        demoSynchronousGet();
        demoSynchronousPostWithJsonBody();
        demoAsynchronousGet();
        demoConcurrentRequests();
        demoTimeoutHandling();
        System.out.println("\nAll Modern HttpClient demos completed.");
    }

    private static void printSection(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -------------------------------------------------------------------
    // 1) Synchronous GET
    // -------------------------------------------------------------------

    private static void demoSynchronousGet() {
        printSection("1) Synchronous GET -- send(), status code, headers, body");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/get?demo=networking"))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response.statusCode());
            System.out.println("Content-Type header: " + response.headers().firstValue("content-type").orElse("(none)"));
            String body = response.body();
            System.out.println("Body (truncated): " + body.substring(0, Math.min(200, body.length())) + "...");
        } catch (Exception e) {
            // Covers IOException (no network), HttpTimeoutException, InterruptedException, etc.
            System.out.println("GET request failed (likely no network access in this environment): "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 2) Synchronous POST with a JSON body via BodyPublishers
    // -------------------------------------------------------------------

    private static void demoSynchronousPostWithJsonBody() {
        printSection("2) Synchronous POST -- BodyPublishers.ofString() with a JSON body");

        String json = "{\"topic\":\"Java Networking\",\"chapter\":2}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/post"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response.statusCode());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            System.out.println("Request considered successful: " + success);
        } catch (Exception e) {
            System.out.println("POST request failed (likely no network access in this environment): "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 3) Asynchronous GET with CompletableFuture composition
    // -------------------------------------------------------------------

    private static void demoAsynchronousGet() throws InterruptedException {
        printSection("3) Asynchronous GET -- sendAsync(), thenApply/thenAccept/exceptionally");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/delay/1"))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        CompletableFuture<Void> future = CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::statusCode)
                .thenAccept(status -> System.out.println("Async response status: " + status))
                .exceptionally(ex -> {
                    System.out.println("Async request failed (likely no network access): " + ex.getMessage());
                    return null;
                });

        System.out.println("Main thread continues immediately -- did not block on the async call.");

        // Wait here only so the demo output stays deterministic before main() moves on.
        future.join();
    }

    // -------------------------------------------------------------------
    // 4) Firing multiple requests concurrently, waiting for all of them
    // -------------------------------------------------------------------

    private static void demoConcurrentRequests() {
        printSection("4) Multiple concurrent requests via CompletableFuture.allOf()");

        String[] paths = { "/get?id=1", "/get?id=2", "/get?id=3" };

        CompletableFuture<HttpResponse<String>>[] futures = new CompletableFuture[paths.length];
        for (int i = 0; i < paths.length; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://httpbin.org" + paths[i]))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            futures[i] = CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        }

        try {
            CompletableFuture.allOf(futures).get(15, TimeUnit.SECONDS);
            for (int i = 0; i < futures.length; i++) {
                System.out.println(paths[i] + " -> status " + futures[i].join().statusCode());
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.out.println("Concurrent requests did not all complete (likely no network access): "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 5) Timeout handling against an intentionally unreachable host
    // -------------------------------------------------------------------

    private static void demoTimeoutHandling() {
        printSection("5) Timeout handling -- connecting to a non-routable address");

        // 10.255.255.1 is a private, non-routable address in most networks -- a reliable way to
        // demonstrate a connection timeout without depending on any specific external host being down.
        HttpClient shortTimeoutClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://10.255.255.1/"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        try {
            shortTimeoutClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Unexpectedly connected -- environment network topology differs from assumptions.");
        } catch (Exception e) {
            System.out.println("Connection attempt failed/timed out as expected, handled gracefully: "
                    + e.getClass().getSimpleName());
        }
    }
}
