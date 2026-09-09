# Why WebSockets Exist

--> HTTP (files 01-03) follows a strict request-response model -- the client always initiates, and the server can only reply to a request it already received. That's a poor fit for anything needing the SERVER to push data to the client the moment something happens (a chat message, a live stock price tick, a multiplayer game state update) without the client having to repeatedly ask "anything new yet?" (a wasteful and laggy technique called polling).
--> **WebSocket** (RFC 6455) is a separate protocol that starts as a normal HTTP request (an "upgrade" handshake) and then switches the same underlying TCP connection into a persistent, full-duplex channel -- both sides can send messages to each other at any time, with much lower per-message overhead than repeated HTTP requests.

```text
Client                                          Server
  |------ HTTP GET + Upgrade: websocket ------->|     (handshake, looks like a normal HTTP request)
  |<----- HTTP 101 Switching Protocols ---------|     (server agrees to upgrade)
  |                                              |
  |<===== same TCP connection, now WebSocket ===>|     (either side can send a message at any moment)
  |------ message ------------------------------>|
  |<----- message -------------------------------|
  |------ message ------------------------------>|
  |                    ... until either side closes the connection ...
```

--> **Requires external infrastructure:** unlike files 01-02, WebSocket support is not something you use with a bare `javac`/`java` setup -- the standard Java WebSocket API (JSR 356, `javax.websocket`/its Jakarta EE successor `jakarta.websocket`) needs a compliant servlet container or application server (Tomcat, Jetty, GlassFish, WildFly, etc.) to actually run, since it's a specification implemented BY those containers, not something that ships bundled in the plain JDK the way `HttpClient` does. The Topic Practicals file for this topic is illustrative, well-commented code demonstrating the API shapes rather than a program you compile and run standalone.

# The Java WebSocket API -- `javax.websocket` / `jakarta.websocket`

--> JSR 356 standardized a Java API for both WRITING WebSocket endpoints (server-side) and CONNECTING to them (client-side), using an annotation-driven model similar in spirit to how JAX-RS handles REST endpoints. The package was renamed from `javax.websocket` to `jakarta.websocket` when Java EE moved to the Eclipse Foundation as Jakarta EE (post Java EE 8) -- both refer to essentially the same API, just under different group/package namespaces depending on which generation of the spec a given server implements.

| Annotation | Purpose |
|---|---|
| `@ServerEndpoint("/path")` | Marks a class as a WebSocket server endpoint reachable at the given URI path |
| `@ClientEndpoint` | Marks a class as a WebSocket client endpoint (used when connecting OUT to some other WebSocket server) |
| `@OnOpen` | Method called when a new WebSocket connection (a `Session`) is established |
| `@OnMessage` | Method called when a text or binary message arrives on the connection |
| `@OnClose` | Method called when the connection is closed (by either side) |
| `@OnError` | Method called when an error occurs on the connection |

# A Server Endpoint

```java
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/chat")
public class ChatEndpoint {

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("New connection: " + session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) throws Exception {
        System.out.println("Received: " + message);
        // Broadcast to every other currently-open session on this endpoint
        for (Session peer : session.getOpenSessions()) {
            if (peer.isOpen() && !peer.getId().equals(session.getId())) {
                peer.getBasicRemote().sendText("Peer said: " + message);
            }
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        System.out.println("Closed: " + session.getId() + " (" + reason.getReasonPhrase() + ")");
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.err.println("Error on session " + session.getId() + ": " + error.getMessage());
    }
}
```

--> A `Session` represents one connected client and is the handle used to send messages back to it -- `getBasicRemote().sendText(...)` sends synchronously (blocks until sent), while `getAsyncRemote().sendText(...)` returns a `Future` and does not block. `session.getOpenSessions()` (as used above) returns every currently-open session sharing this same endpoint class, which is the standard building block for broadcast-style features like chat rooms.
--> **Container requirement:** this class is picked up automatically by a servlet container that scans for `@ServerEndpoint`-annotated classes at startup (in a WAR deployed to Tomcat/Jetty/etc.) -- it is not something `main()` invokes directly.

# A Client Endpoint

```java
import jakarta.websocket.*;
import java.net.URI;

@ClientEndpoint
public class ChatClient {

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("Connected to server");
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Server says: " + message);
    }

    @OnClose
    public void onClose(CloseReason reason) {
        System.out.println("Disconnected: " + reason.getReasonPhrase());
    }

    public static void main(String[] args) throws Exception {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try (Session session = container.connectToServer(ChatClient.class, URI.create("ws://localhost:8080/chat"))) {
            session.getBasicRemote().sendText("Hello from client!");
            Thread.sleep(5000);       // keep the connection open long enough to receive replies
        }
    }
}
```

--> `ContainerProvider.getWebSocketContainer()` obtains a client-side WebSocket engine implementation (provided by the same container library, e.g. Tomcat's or Jetty's WebSocket client jar) and `connectToServer(...)` performs the HTTP upgrade handshake, returning a live `Session` once it succeeds.
--> Note the URI scheme: `ws://` for plain WebSocket (analogous to `http://`), and `wss://` for WebSocket-over-TLS (analogous to `https://`) -- production WebSocket traffic should essentially always use `wss://`, for the same reasons plain HTTP is avoided in production (file 05).

# Text vs Binary Messages

--> WebSocket messages come in two flavors, and `@OnMessage` methods are matched by the type of their parameter:

```java
@OnMessage
public void onTextMessage(String message, Session session) { /* ... */ }

@OnMessage
public void onBinaryMessage(byte[] data, Session session) { /* ... */ }

@OnMessage
public void onPongMessage(PongMessage pong, Session session) { /* ... */ }   // WebSocket-level keepalive response
```

--> A single endpoint class can define multiple `@OnMessage` methods as long as each has a distinct parameter type (text, binary, or `PongMessage`) -- the container dispatches based on the frame type received.

# Use Cases Where WebSockets Are the Right Tool

--> **Chat and messaging applications** -- the canonical example; both sides send messages unpredictably and low latency matters.
--> **Live dashboards and monitoring** -- server pushes metric updates the moment they change, rather than the client polling every few seconds.
--> **Collaborative editing** -- multiple clients need to see each other's changes reflected near-instantly (e.g. shared documents, whiteboards).
--> **Multiplayer games and live position updates** -- frequent, small, latency-sensitive messages in both directions.
--> **Financial tickers / live pricing** -- server-driven push of frequently changing data to many subscribed clients at once.

# When WebSockets Are NOT the Right Tool

--> **Simple request-response APIs** -- if the client always initiates and there's no need for the server to push unprompted, plain HTTP (`HttpClient`, file 02) is simpler, more cacheable, and easier to scale behind standard HTTP infrastructure (load balancers, CDNs, standard auth).
--> **One-off or infrequent server-to-client notifications** -- for occasional push needs without full bidirectional chat, Server-Sent Events (SSE, a simpler one-way-only push mechanism built on plain HTTP) is often a lighter-weight fit than a full WebSocket connection.
--> **Stateless, horizontally-scaled architectures** -- a WebSocket connection is inherently STATEFUL and pinned to one specific server instance for its lifetime, which complicates load balancing and scaling compared to stateless HTTP requests that any server instance can handle interchangeably; scaling WebSocket servers typically requires sticky sessions or a shared pub/sub backend (e.g. Redis) to broadcast messages across server instances.

# Common Gotchas and Best Practices Recap

--> **The Java WebSocket API needs a container** -- it is a specification, not a JDK feature; you need Tomcat, Jetty, or a similar server to actually host `@ServerEndpoint` classes (or the equivalent client library to use `@ClientEndpoint`).
--> **`javax.websocket` vs `jakarta.websocket` is a package-naming/version split, not two different APIs** -- know which one your target server/version actually implements before writing endpoint code.
--> **A `Session` becomes invalid once closed** -- always check `session.isOpen()` before sending, especially when broadcasting to a list of sessions where some may have disconnected without the server noticing yet.
--> **`getBasicRemote()` sends block; `getAsyncRemote()` sends don't** -- picking the wrong one in a hot broadcast loop (e.g. blocking sends to many slow clients one by one) can seriously degrade throughput for an entire chat room.
--> **Connections are stateful and server-pinned** -- plan for this in any horizontally-scaled deployment; it is one of the most common production surprises when moving a WebSocket feature from "works on my single dev server" to a multi-instance production deployment.
--> **Always use `wss://` (TLS) in production**, exactly as `https://` is required over plain `http://` -- see file 05 for the underlying TLS concepts that apply equally here.
