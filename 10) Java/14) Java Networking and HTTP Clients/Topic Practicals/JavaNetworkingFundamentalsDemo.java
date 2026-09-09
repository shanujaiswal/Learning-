/*
 * JavaNetworkingFundamentalsDemo.java
 *
 * Demonstrates:
 *   1. InetAddress -- resolving hostnames, loopback address
 *   2. A fully self-contained TCP client-server demo over loopback (127.0.0.1)
 *      using ServerSocket + Socket, with a background server thread
 *   3. Multiple sequential client connections handled by one server loop
 *   4. Socket read timeouts (setSoTimeout) and connect timeouts
 *   5. Graceful handling of a timeout / no-response scenario
 *
 * This file is FULLY SELF-CONTAINED and requires NO network access or
 * external dependencies -- everything runs over the local loopback interface
 * (127.0.0.1), so it works offline and needs only the plain JDK.
 *
 * Covers Theory chapter:
 *   14) Java Networking and HTTP Clients/Theory/01 Java Networking Fundamentals.md
 *
 * Compile: javac JavaNetworkingFundamentalsDemo.java
 * Run:     java JavaNetworkingFundamentalsDemo
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class JavaNetworkingFundamentalsDemo {

    public static void main(String[] args) throws Exception {
        demoInetAddress();
        demoLoopbackClientServer();
        demoMultipleSequentialClients();
        demoSocketReadTimeout();
        System.out.println("\nAll Java Networking Fundamentals demos completed.");
    }

    private static void printSection(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    // -------------------------------------------------------------------
    // 1) InetAddress basics
    // -------------------------------------------------------------------

    private static void demoInetAddress() {
        printSection("1) InetAddress -- loopback and local host info");

        InetAddress loopback = InetAddress.getLoopbackAddress();
        System.out.println("Loopback address: " + loopback.getHostAddress());

        try {
            InetAddress localHost = InetAddress.getLocalHost();
            System.out.println("Local host name: " + localHost.getHostName());
            System.out.println("Local host address: " + localHost.getHostAddress());
        } catch (IOException e) {
            // getLocalHost() can fail in some sandboxed/offline environments -- degrade gracefully
            System.out.println("Could not resolve local host in this environment: " + e.getMessage());
        }

        // A real DNS lookup -- may fail with no network access, so this is caught and doesn't crash the demo
        try {
            InetAddress example = InetAddress.getByName("example.com");
            System.out.println("example.com resolved to: " + example.getHostAddress());
        } catch (IOException e) {
            System.out.println("DNS lookup for example.com failed (likely no network access here): "
                    + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 2) Self-contained client-server demo over loopback
    // -------------------------------------------------------------------

    private static void demoLoopbackClientServer() throws Exception {
        printSection("2) TCP client-server demo over loopback (127.0.0.1)");

        // Bind to port 0 -- let the OS pick any free ephemeral port, avoiding "address already in use"
        try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();
            System.out.println("Server listening on 127.0.0.1:" + port);

            CountDownLatch serverReady = new CountDownLatch(1);
            Thread serverThread = new Thread(() -> {
                serverReady.countDown();
                try (Socket clientSocket = serverSocket.accept()) {         // blocks until the client connects
                    handleOneClient(clientSocket);
                } catch (IOException e) {
                    System.out.println("Server error: " + e.getMessage());
                }
            }, "server-thread");
            serverThread.start();

            serverReady.await();          // make sure the server thread has started before the client connects

            // --- Client side ---
            try (Socket clientSocket = new Socket()) {
                clientSocket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));

                out.println("Hello from the client!");
                String response = in.readLine();
                System.out.println("Client received: " + response);
            }

            serverThread.join(2000);
        }
    }

    private static void handleOneClient(Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);

        String line = in.readLine();
        System.out.println("Server received: " + line);
        out.println("Echo: " + line);
    }

    // -------------------------------------------------------------------
    // 3) One server loop handling several sequential clients
    // -------------------------------------------------------------------

    private static void demoMultipleSequentialClients() throws Exception {
        printSection("3) One server handling multiple sequential clients");

        final int clientCount = 3;

        try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();

            Thread serverThread = new Thread(() -> {
                for (int i = 0; i < clientCount; i++) {
                    try (Socket clientSocket = serverSocket.accept()) {
                        handleOneClient(clientSocket);
                    } catch (IOException e) {
                        System.out.println("Server error on connection " + i + ": " + e.getMessage());
                    }
                }
            }, "multi-client-server-thread");
            serverThread.start();

            Thread.sleep(100); // give the server a brief head start before the first client dials in

            for (int i = 1; i <= clientCount; i++) {
                try (Socket clientSocket = new Socket()) {
                    clientSocket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));

                    out.println("Message #" + i);
                    System.out.println("Client " + i + " received: " + in.readLine());
                }
            }

            serverThread.join(3000);
        }
    }

    // -------------------------------------------------------------------
    // 4) Socket read timeout -- client connects but server never replies
    // -------------------------------------------------------------------

    private static void demoSocketReadTimeout() throws Exception {
        printSection("4) Socket read timeout -- graceful handling of a hung server");

        try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();

            // A server that accepts the connection but deliberately never writes anything back,
            // simulating a hung/unresponsive server.
            Thread serverThread = new Thread(() -> {
                try (Socket clientSocket = serverSocket.accept()) {
                    Thread.sleep(5000);   // pretend to be stuck/slow -- long enough to trigger the client timeout
                } catch (IOException | InterruptedException e) {
                    // expected once the client gives up and closes its side -- nothing to act on here
                }
            }, "hung-server-thread");
            serverThread.setDaemon(true);
            serverThread.start();

            try (Socket clientSocket = new Socket()) {
                clientSocket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
                clientSocket.setSoTimeout(500);        // don't wait more than 500ms for a reply

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                try {
                    String response = in.readLine();
                    System.out.println("Unexpectedly received: " + response);
                } catch (SocketTimeoutException e) {
                    System.out.println("Read timed out after 500ms as expected -- server never responded. "
                            + "Handled gracefully instead of hanging forever.");
                }
            }
        }
    }
}
