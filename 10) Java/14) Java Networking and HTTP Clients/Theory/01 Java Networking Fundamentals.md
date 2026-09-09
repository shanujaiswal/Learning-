# Why Networking APIs Matter

--> Almost every non-trivial Java application eventually talks to something over a network -- calling a REST API, connecting to a database over TCP, sending mail, or building its own client-server protocol. The `java.net` package (part of the JDK since Java 1.0) provides the low-level building blocks -- `Socket`, `ServerSocket`, `InetAddress`, `URL` -- that everything higher-level (including the modern `HttpClient` covered in file 02) is ultimately built on top of.
--> Understanding sockets first matters even if you'll mostly use `HttpClient` in real projects, because it demystifies what "making an HTTP request" actually does under the hood: open a TCP connection, write bytes, read bytes back, close the connection.

# TCP vs UDP -- The Two Transport Protocols

--> Java's networking APIs are split along the same line as the underlying transport protocols they wrap. Choosing between them is a fundamental design decision for any network-facing application.

| Aspect | TCP (`Socket`/`ServerSocket`) | UDP (`DatagramSocket`/`DatagramPacket`) |
|---|---|---|
| Connection | Connection-oriented -- a handshake establishes a session before data flows | Connectionless -- packets ("datagrams") are just fired off independently |
| Reliability | Guaranteed delivery, in-order, with automatic retransmission of lost packets | No guarantees -- packets can be lost, duplicated, or arrive out of order |
| Overhead | Higher -- handshake, acknowledgments, flow control, congestion control | Lower -- just the packet itself, no session bookkeeping |
| Message boundaries | Byte STREAM -- no inherent message boundaries (you must define your own framing) | Preserves discrete message boundaries -- each `DatagramPacket` is a whole unit |
| Typical uses | HTTP/HTTPS, databases, file transfer, anything needing reliability | DNS, video/audio streaming, online gaming, service discovery -- anything favoring speed/low latency over perfect reliability |
| Java classes | `Socket` (client), `ServerSocket` (server) | `DatagramSocket`, `DatagramPacket` |

--> **Rule of thumb:** if losing or reordering a piece of data would break your application (a bank transfer, a web page), use TCP. If occasional loss is tolerable and low latency matters more (a live video frame, a game position update), UDP is worth considering. The overwhelming majority of application-level Java networking code -- including everything in this topic folder after this file -- uses TCP, because HTTP itself runs on top of TCP (or, for HTTP/3, on top of QUIC which runs on UDP but re-implements reliability itself).

# `InetAddress` -- Representing Hosts

--> `InetAddress` represents an IP address (IPv4 or IPv6), optionally paired with a hostname. It is the class responsible for DNS resolution -- turning a human-readable hostname like `"example.com"` into a numeric IP address the OS network stack can actually route packets to.

```java
import java.net.InetAddress;
import java.net.UnknownHostException;

InetAddress address = InetAddress.getByName("example.com");
System.out.println(address.getHostName());       // example.com
System.out.println(address.getHostAddress());     // e.g. 93.184.216.34
System.out.println(address.isReachable(2000));    // ICMP/TCP probe, true/false, times out after 2s

InetAddress loopback = InetAddress.getLoopbackAddress();   // 127.0.0.1
InetAddress localHost = InetAddress.getLocalHost();         // this machine's own address
```

--> **Gotcha:** `InetAddress.getByName()` performs a real DNS lookup (unless given a literal IP or the caching layer already has an answer) and throws the checked `UnknownHostException` if resolution fails -- always wrap it or declare it, and never assume it returns instantly, since DNS is itself a network call with its own latency and failure modes.
--> `InetAddress.getAllByName("example.com")` returns EVERY IP address a hostname resolves to (many high-traffic sites round-robin across several), useful for basic load distribution or connection retries against a different address if the first one is unreachable.

# `Socket` and `ServerSocket` -- The TCP Building Blocks

--> A **socket** is one endpoint of a two-way TCP connection, identified by an IP address plus a port number. Java models the CLIENT side with `Socket` and the SERVER side (which listens for incoming connections) with `ServerSocket`.

```text
   Server                                   Client
   ------                                   ------
   ServerSocket(port)                       
       |  .accept()  <-------- connects --- new Socket(host, port)
       v
   Socket (per-connection,          Socket (this end of the connection)
    returned by accept())
       |                                        |
   getInputStream()/                       getInputStream()/
   getOutputStream()  <====== bytes flow both ways ======>  getOutputStream()
```

--> **`ServerSocket`** binds to a local port and LISTENS for incoming TCP connection attempts. Calling `.accept()` BLOCKS until a client connects, then returns a brand-new `Socket` object representing that specific connection -- the original `ServerSocket` keeps listening for further connections, typically in a loop.
--> **`Socket`** represents one actual TCP connection between two endpoints. The client creates one by connecting to a host/port; the server receives one as the return value of `accept()`. Both sides use the SAME `Socket` API afterward -- `getInputStream()` / `getOutputStream()` -- because once connected, a TCP socket is symmetric (either side can read or write at any time).

## Minimal Server

```java
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;

try (ServerSocket serverSocket = new ServerSocket(5000)) {
    System.out.println("Listening on port 5000...");

    while (true) {                                   // accept connections forever
        try (Socket clientSocket = serverSocket.accept()) {     // blocks until a client connects
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            String line = in.readLine();               // read one line the client sent
            out.println("Echo: " + line);               // send a response back
        }   // clientSocket closed automatically here (try-with-resources)
    }
}
```

## Minimal Client

```java
import java.net.Socket;
import java.io.*;

try (Socket socket = new Socket("localhost", 5000)) {
    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
    BufferedReader in = new BufferedReader(
            new InputStreamReader(socket.getInputStream()));

    out.println("Hello, server!");            // send a line
    String response = in.readLine();           // read the server's reply
    System.out.println("Server said: " + response);
}
```

--> **Why `PrintWriter(..., true)`?** The second constructor argument enables auto-flush after every `println()` call. Streams are buffered by default -- without auto-flush (or an explicit `.flush()` call), bytes can sit in the local buffer and never actually reach the other side, and the reader on the other end blocks forever waiting for a line that was never truly sent over the wire.
--> **Gotcha -- `readLine()` blocks:** both `Socket` reads and `ServerSocket.accept()` are BLOCKING calls by default -- the thread doing the read simply pauses until data arrives (or the connection closes). This is exactly why real servers handle each accepted connection on its own thread (or hand it to a thread pool -- see file 05 of the Multithreading topic) rather than processing connections one at a time on a single thread.

# Handling Multiple Clients -- One Thread Per Connection

--> A `ServerSocket` that only calls `accept()` and then serves that client before looping back to `accept()` again can only serve ONE client at a time -- every other connecting client waits in the OS-level backlog queue. The classic (and still common) fix is spawning a new thread per accepted connection:

```java
try (ServerSocket serverSocket = new ServerSocket(5000)) {
    while (true) {
        Socket clientSocket = serverSocket.accept();          // blocks until next client
        new Thread(() -> handleClient(clientSocket)).start(); // hand off, go back to accept()
    }
}
```

--> This scales reasonably for modest numbers of concurrent connections but suffers the same per-thread overhead discussed in the Multithreading topic at high connection counts -- production-grade servers typically use a bounded `ExecutorService` thread pool instead of raw `new Thread(...)` per connection, or move to a non-blocking I/O model (`java.nio.channels.Selector`) entirely, which is beyond the scope of this introductory file.

# Ports -- The Basics

--> A port is a 16-bit number (0-65535) that lets multiple network services coexist on the same IP address -- the OS uses it to route incoming packets to the right listening process.

| Range | Category | Notes |
|---|---|---|
| 0-1023 | Well-known / system ports | Require elevated privileges on most OSes; e.g. 80 (HTTP), 443 (HTTPS), 22 (SSH) |
| 1024-49151 | Registered ports | Commonly used by specific applications/services by convention |
| 49152-65535 | Dynamic / ephemeral ports | OS typically assigns these automatically to the CLIENT side of an outgoing connection |

--> **Gotcha -- `BindException: Address already in use`:** trying to bind a `ServerSocket` to a port some other process (or a previous run of your own program still shutting down) already holds throws this immediately from the constructor. Common fixes during development: use a different port, wait a moment (TCP's `TIME_WAIT` state can hold a port briefly after a socket closes), or call `serverSocket.setReuseAddress(true)` before binding.
--> Passing `0` as the port (`new ServerSocket(0)`) asks the OS to pick any free ephemeral port automatically -- retrieve it afterward with `serverSocket.getLocalPort()`. Handy for tests where the exact port number doesn't matter.

# Timeouts on Sockets

--> By default, a blocking socket read (`InputStream.read()`, `readLine()`, etc.) can block FOREVER if the other side never sends anything and never closes the connection -- a silent, permanently hung thread. `Socket.setSoTimeout(millis)` puts an upper bound on how long a read can block before giving up.

```java
Socket socket = new Socket("localhost", 5000);
socket.setSoTimeout(3000);          // reads block at most 3 seconds

try {
    String line = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
} catch (java.net.SocketTimeoutException e) {
    System.out.println("No response within 3 seconds -- giving up");
}
```

--> Connecting itself can also hang -- `new Socket(host, port)` uses the OS default connect timeout, which can be very long. Prefer the no-arg constructor plus explicit `connect()` with a timeout for full control:

```java
Socket socket = new Socket();                                    // unconnected
socket.connect(new java.net.InetSocketAddress("localhost", 5000), 2000);  // 2s connect timeout
```

# Closing Sockets Properly

--> Sockets hold OS-level resources (file descriptors) and, on the server side, an unclosed `Socket` can leave the corresponding TCP connection lingering in a half-open state. Always close sockets (and the streams built on them) deterministically -- try-with-resources is the natural fit, since both `Socket` and `ServerSocket` implement `AutoCloseable`.
--> Closing a `Socket` closes both its input and output streams; you generally do NOT need to separately close the streams obtained via `getInputStream()`/`getOutputStream()` if you close the socket itself, though closing them explicitly is harmless.

# `URL` and `URLConnection` -- The Legacy HTTP Path

--> Before `java.net.http.HttpClient` (Java 11, covered in file 02), the only built-in way to make an HTTP request was `java.net.URL` plus `HttpURLConnection`. It still works and still appears in older/legacy code, but is verbose, awkward with modern async patterns, and generally superseded.

```java
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.*;

URL url = new URL("https://example.com");
HttpURLConnection conn = (HttpURLConnection) url.openConnection();
conn.setRequestMethod("GET");
conn.setConnectTimeout(3000);
conn.setReadTimeout(3000);

int status = conn.getResponseCode();
try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
    String body = reader.lines().reduce("", (a, b) -> a + b + "\n");
    System.out.println("Status: " + status + ", body length: " + body.length());
}
conn.disconnect();
```

--> **Why this API is mostly avoided today:** no built-in JSON handling, clunky checked-exception-heavy stream APIs, no first-class async support, manual redirect and connection-pool management, and `new URL(String)`'s constructor was deprecated starting Java 20 in favor of `URI.create(...).toURL()` due to inconsistent parsing/equality behavior. File 02 covers the modern replacement in depth.

# Common Gotchas and Best Practices Recap

--> **`accept()` and blocking reads pause a thread indefinitely by default** -- always consider `setSoTimeout()` for client-side reads, and a threading/pooling strategy for servers accepting more than one client.
--> **Always flush or use auto-flushing writers** -- buffered output that's never flushed never actually reaches the other side, and the reader on the other end silently hangs.
--> **Close sockets with try-with-resources** -- both `Socket` and `ServerSocket` are `AutoCloseable`; leaking them leaks OS file descriptors and can leave TCP connections half-open.
--> **TCP has no inherent message framing** -- if you send multiple logical messages over one connection, YOU must define how a reader knows where one message ends and the next begins (e.g. newline-delimited text, a length-prefix header, or a well-defined protocol like HTTP itself).
--> **Prefer the modern `HttpClient` (file 02) for anything HTTP-shaped** -- raw sockets are the right tool when you're implementing your own protocol or need to understand what's happening below HTTP, not for calling REST APIs in application code.
--> **DNS resolution (`InetAddress.getByName`) is itself a network call** -- it can be slow or fail, and its result can be cached by the JVM (`networkaddress.cache.ttl` security property) in ways that surprise people expecting it to reflect DNS changes instantly.
