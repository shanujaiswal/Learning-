/*
 * ThirdPartyHttpClientsDemo.java
 *
 * ============================== IMPORTANT ==============================
 * THIS FILE DOES NOT COMPILE WITH THE PLAIN JDK.
 * It requires the following external dependencies on the classpath:
 *
 *   Maven:
 *     <dependency>
 *       <groupId>com.squareup.okhttp3</groupId>
 *       <artifactId>okhttp</artifactId>
 *       <version>4.12.0</version>
 *     </dependency>
 *     <dependency>
 *       <groupId>org.apache.httpcomponents.client5</groupId>
 *       <artifactId>httpclient5</artifactId>
 *       <version>5.3</version>
 *     </dependency>
 *
 *   Or, manually: download the okhttp, okio, and httpclient5 (+ httpcore5)
 *   jars and add them with -cp when compiling/running.
 *
 * This file is illustrative, well-commented reference code showing the
 * SHAPE of the OkHttp and Apache HttpClient 5 APIs side by side. It is not
 * meant to be run standalone in this study repo -- treat it as a reading
 * reference to accompany Theory file 03.
 * =========================================================================
 *
 * Demonstrates:
 *   1. OkHttp -- building a client, synchronous GET, POST with JSON body
 *   2. OkHttp -- asynchronous call via enqueue()/Callback
 *   3. OkHttp -- a logging interceptor
 *   4. Apache HttpClient 5 -- building a client, GET, POST with JSON body
 *   5. Apache HttpClient 5 -- a pooled connection manager with per-route limits
 *
 * Covers Theory chapter:
 *   14) Java Networking and HTTP Clients/Theory/03 Third-Party HTTP Clients OkHttp and Apache HttpClient.md
 *
 * Compile (once dependencies are on the classpath):
 *   javac -cp "okhttp-4.12.0.jar;okio-3.6.0.jar;kotlin-stdlib-1.9.jar;httpclient5-5.3.jar;httpcore5-5.2.jar" ThirdPartyHttpClientsDemo.java
 * Run:
 *   java -cp ".;okhttp-4.12.0.jar;okio-3.6.0.jar;kotlin-stdlib-1.9.jar;httpclient5-5.3.jar;httpcore5-5.2.jar" ThirdPartyHttpClientsDemo
 */

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.time.Duration;

public class ThirdPartyHttpClientsDemo {

    public static void main(String[] args) throws Exception {
        demoOkHttpSynchronous();
        demoOkHttpAsynchronous();
        demoOkHttpInterceptor();
        demoApacheHttpClientSynchronous();
        demoApacheHttpClientPooling();
        System.out.println("\nAll Third-Party HTTP Client demos completed (illustrative reference code).");
    }

    private static void printSection(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -------------------------------------------------------------------
    // 1) OkHttp -- synchronous GET and POST
    // -------------------------------------------------------------------

    private static void demoOkHttpSynchronous() throws IOException {
        printSection("1) OkHttp -- synchronous GET and POST");

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();

        // --- GET ---
        Request getRequest = new Request.Builder()
                .url("https://httpbin.org/get")
                .header("Accept", "application/json")
                .build();

        try (Response response = client.newCall(getRequest).execute()) {
            System.out.println("GET status: " + response.code());
            System.out.println("GET body length: " + response.body().string().length());
        }

        // --- POST with a JSON body ---
        MediaType jsonType = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create("{\"topic\":\"OkHttp\"}", jsonType);
        Request postRequest = new Request.Builder()
                .url("https://httpbin.org/post")
                .post(body)
                .build();

        try (Response response = client.newCall(postRequest).execute()) {
            System.out.println("POST status: " + response.code());
        }
    }

    // -------------------------------------------------------------------
    // 2) OkHttp -- asynchronous call
    // -------------------------------------------------------------------

    private static void demoOkHttpAsynchronous() throws InterruptedException {
        printSection("2) OkHttp -- asynchronous enqueue() with a Callback");

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("https://httpbin.org/get").build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.out.println("Async OkHttp call failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    System.out.println("Async OkHttp status: " + response.code());
                }
            }
        });

        // In real code you would NOT sleep to "wait" for an async callback -- this is purely
        // to keep console output readable in a simple linear demo script.
        Thread.sleep(2000);
    }

    // -------------------------------------------------------------------
    // 3) OkHttp -- logging interceptor
    // -------------------------------------------------------------------

    private static void demoOkHttpInterceptor() throws IOException {
        printSection("3) OkHttp -- a request/response logging interceptor");

        OkHttpClient loggingClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    long start = System.nanoTime();
                    System.out.println("--> " + original.method() + " " + original.url());

                    Response response = chain.proceed(original);

                    long tookMs = (System.nanoTime() - start) / 1_000_000;
                    System.out.println("<-- " + response.code() + " (" + tookMs + "ms)");
                    return response;
                })
                .build();

        try (Response response = loggingClient.newCall(
                new Request.Builder().url("https://httpbin.org/get").build()).execute()) {
            System.out.println("Final status observed by caller: " + response.code());
        }
    }

    // -------------------------------------------------------------------
    // 4) Apache HttpClient 5 -- synchronous GET and POST
    // -------------------------------------------------------------------

    private static void demoApacheHttpClientSynchronous() throws IOException {
        printSection("4) Apache HttpClient 5 -- synchronous GET and POST");

        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpGet getRequest = new HttpGet("https://httpbin.org/get");
            getRequest.setHeader("Accept", "application/json");

            client.execute(getRequest, response -> {
                System.out.println("GET status: " + response.getCode());
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("GET body length: " + responseBody.length());
                return null;
            });

            HttpPost postRequest = new HttpPost("https://httpbin.org/post");
            postRequest.setEntity(new StringEntity("{\"topic\":\"Apache HttpClient\"}", ContentType.APPLICATION_JSON));

            client.execute(postRequest, response -> {
                System.out.println("POST status: " + response.getCode());
                return null;
            });
        }
    }

    // -------------------------------------------------------------------
    // 5) Apache HttpClient 5 -- pooled connection manager
    // -------------------------------------------------------------------

    private static void demoApacheHttpClientPooling() throws IOException {
        printSection("5) Apache HttpClient 5 -- pooled connection manager (max total + per-route)");

        HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(100)     // global cap across ALL hosts
                .setMaxConnPerRoute(20)   // cap for any SINGLE host, prevents one host monopolizing the pool
                .build();

        try (CloseableHttpClient pooledClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build()) {

            HttpGet request = new HttpGet("https://httpbin.org/get");
            pooledClient.execute(request, response -> {
                System.out.println("Pooled client GET status: " + response.getCode());
                return null;
            });

            System.out.println("Connection returned to the pool for reuse by the next request to this host.");
        }
    }
}
