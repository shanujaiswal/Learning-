/*
 * WebSocketProgrammingDemo.java
 *
 * ============================== IMPORTANT ==============================
 * THIS FILE DOES NOT COMPILE WITH THE PLAIN JDK.
 * The standard Java WebSocket API (JSR 356) lives in the packages
 *
 *   javax.websocket / javax.websocket.server        (Java EE 8 and earlier)
 *   jakarta.websocket / jakarta.websocket.server     (Jakarta EE 9+, the modern successor)
 *
 * which are SPECIFICATIONS implemented by a servlet container / application
 * server (Tomcat, Jetty, GlassFish, WildFly, etc.) -- they do not ship
 * bundled in the plain JDK the way java.net.http.HttpClient does. To
 * actually compile and run code like this you need one of those
 * containers' WebSocket implementation jar(s) on the classpath, e.g.:
 *
 *   Maven (Jakarta EE 9+ / Tomcat 10+):
 *     <dependency>
 *       <groupId>jakarta.websocket</groupId>
 *       <artifactId>jakarta.websocket-api</artifactId>
 *       <version>2.1.1</version>
 *     </dependency>
 *     <dependency>
 *       <groupId>org.apache.tomcat.embed</groupId>
 *       <artifactId>tomcat-embed-websocket</artifactId>
 *       <version>10.1.x</version>
 *     </dependency>
 *
 * Additionally, a @ServerEndpoint class is NOT invoked by a main() method --
 * it is discovered and instantiated automatically by the container when a
 * WAR/application containing it is deployed and a client performs the
 * WebSocket upgrade handshake against its @ServerEndpoint path. This file
 * is illustrative, well-commented reference code showing the SHAPE of the
 * Java WebSocket API (server endpoint + client endpoint) side by side. It
 * is not meant to be run standalone in this study repo -- treat it as a
 * reading reference to accompany Theory file 04.
 * =========================================================================
 *
 * Demonstrates:
 *   1. @ServerEndpoint -- a chat-room-style server endpoint with broadcast
 *   2. @ClientEndpoint -- a client that connects out to a WebSocket server
 *   3. @OnOpen / @OnMessage / @OnClose / @OnError lifecycle annotations
 *   4. Text vs binary vs pong @OnMessage overloads
 *   5. Synchronous (getBasicRemote) vs asynchronous (getAsyncRemote) sends
 *
 * Covers Theory chapter:
 *   14) Java Networking and HTTP Clients/Theory/04 WebSocket Programming in Java.md
 *
 * "Compile" (once a container's WebSocket API + impl jars are on the classpath):
 *   javac -cp "jakarta.websocket-api-2.1.1.jar;tomcat-embed-websocket-10.1.x.jar" WebSocketProgrammingDemo.java
 * "Run" (server endpoints are NOT launched via `java ClassName` -- they are
 *  deployed inside a container; only the illustrative client main() below
 *  could conceivably be run directly, and only against a real running server):
 *   java -cp ".;jakarta.websocket-api-2.1.1.jar;tomcat-embed-websocket-10.1.x.jar" WebSocketProgrammingDemo$ChatClientEndpoint
 */

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerEndpoint;

import java.net.URI;

public class WebSocketProgrammingDemo {

    public static void main(String[] args) {
        // This class itself is just a narrator -- see the two illustrative
        // endpoint classes below (ChatServerEndpoint and ChatClientEndpoint)
        // for the actual API shapes. Neither can be meaningfully exercised
        // by a plain `java WebSocketProgrammingDemo` run; they require a
        // real WebSocket container. See the file header for details.
        System.out.println("=".repeat(70));
        System.out.println("WebSocket Programming in Java -- illustrative reference code");
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.println("This file cannot be run standalone: the Java WebSocket API");
        System.out.println("(javax.websocket / jakarta.websocket) requires a servlet container");
        System.out.println("such as Tomcat or Jetty to host @ServerEndpoint classes, and a");
        System.out.println("client-side WebSocket engine (e.g. Tomcat's or Jetty's WebSocket");
        System.out.println("client jar) to drive @ClientEndpoint classes.");
        System.out.println();
        System.out.println("Read ChatServerEndpoint and ChatClientEndpoint below as reference");
        System.out.println("material accompanying Theory file 04.");
    }

    // =====================================================================
    // 1) SERVER ENDPOINT -- a minimal broadcast chat room
    // =====================================================================
    //
    // Picked up automatically by a servlet container that scans for
    // @ServerEndpoint-annotated classes at startup (e.g. inside a WAR
    // deployed to Tomcat/Jetty). Reachable at ws://<host>:<port>/<context>/chat
    // once deployed. Nothing here is invoked by main() -- the container
    // instantiates one instance of this class PER connected client and
    // calls the lifecycle methods below as events happen on that client's
    // connection.

    @ServerEndpoint("/chat")
    public static class ChatServerEndpoint {

        @OnOpen
        public void onOpen(Session session) {
            System.out.println("New connection: " + session.getId());
        }

        @OnMessage
        public void onTextMessage(String message, Session session) throws Exception {
            System.out.println("Received from " + session.getId() + ": " + message);

            // Broadcast to every other currently-open session on this endpoint --
            // the standard building block for chat-room-style fan-out.
            for (Session peer : session.getOpenSessions()) {
                if (peer.isOpen() && !peer.getId().equals(session.getId())) {
                    // getBasicRemote().sendText(...) is SYNCHRONOUS -- it blocks
                    // until the message is sent. Fine for a small demo; in a hot
                    // broadcast loop over many peers, getAsyncRemote() (below)
                    // avoids one slow peer stalling delivery to everyone else.
                    peer.getBasicRemote().sendText("Peer said: " + message);
                }
            }
        }

        @OnMessage
        public void onBinaryMessage(byte[] data, Session session) {
            System.out.println("Received " + data.length + " binary bytes from " + session.getId());
        }

        @OnMessage
        public void onPongMessage(PongMessage pong, Session session) {
            // WebSocket-level keepalive response -- the container sends PING
            // frames to detect dead connections; the peer's WebSocket engine
            // answers with PONG automatically. This handler is optional and
            // mainly useful for logging/diagnostics.
            System.out.println("Pong received from " + session.getId());
        }

        @OnClose
        public void onClose(Session session, CloseReason reason) {
            System.out.println("Closed: " + session.getId() + " (" + reason.getReasonPhrase() + ")");
        }

        @OnError
        public void onError(Session session, Throwable error) {
            System.err.println("Error on session " + session.getId() + ": " + error.getMessage());
        }

        // Example of an asynchronous, non-blocking send -- returns a Future
        // immediately rather than blocking the calling thread until the
        // message is actually transmitted. Preferred in broadcast loops with
        // many recipients, since one slow/blocked peer won't stall the rest.
        private void broadcastAsync(Session session, String message) {
            for (Session peer : session.getOpenSessions()) {
                if (peer.isOpen()) {
                    peer.getAsyncRemote().sendText(message);
                }
            }
        }
    }

    // =====================================================================
    // 2) CLIENT ENDPOINT -- connecting OUT to a WebSocket server
    // =====================================================================
    //
    // Unlike the server endpoint, a @ClientEndpoint class CAN be driven from
    // a main() method -- but only against a real, already-running WebSocket
    // server, and only once a client-side WebSocket engine implementation
    // (e.g. Tomcat's or Jetty's WebSocket client jar) is on the classpath to
    // back ContainerProvider.getWebSocketContainer().

    @ClientEndpoint
    public static class ChatClientEndpoint {

        @OnOpen
        public void onOpen(Session session) {
            System.out.println("Connected to server, session id: " + session.getId());
        }

        @OnMessage
        public void onMessage(String message) {
            System.out.println("Server says: " + message);
        }

        @OnClose
        public void onClose(CloseReason reason) {
            System.out.println("Disconnected: " + reason.getReasonPhrase());
        }

        @OnError
        public void onError(Throwable error) {
            System.err.println("Client-side error: " + error.getMessage());
        }

        // Illustrative only -- requires a real server listening at this
        // ws:// address and a client WebSocket engine on the classpath.
        public static void main(String[] args) throws Exception {
            // Note the URI scheme: ws:// for plain WebSocket (analogous to
            // http://), and wss:// for WebSocket-over-TLS (analogous to
            // https://) -- production traffic should essentially always use
            // wss://, for the same reasons plain HTTP is avoided in
            // production (see Theory file 05, Networking Best Practices).
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();

            try (Session session = container.connectToServer(
                    ChatClientEndpoint.class, URI.create("ws://localhost:8080/chat"))) {

                session.getBasicRemote().sendText("Hello from client!");

                // Keep the connection open long enough to receive replies.
                // In a real application this would be driven by the
                // application's own lifecycle rather than a fixed sleep.
                Thread.sleep(5000);
            }
        }
    }
}
