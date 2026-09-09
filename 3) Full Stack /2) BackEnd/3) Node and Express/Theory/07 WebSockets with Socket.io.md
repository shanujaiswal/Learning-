# Why WebSockets Instead of HTTP

--> Regular HTTP is request-response -- the client always initiates, the server can never push data to the client on its own. Features like live chat, real-time notifications, or collaborative editing need the SERVER to push updates the instant something happens, without the client having to repeatedly ask "anything new?" (polling).
--> WebSockets establish a single, persistent, two-way connection -- once open, either side can send messages to the other at any time, with far less overhead than repeated HTTP requests.

# Socket.io -- WebSockets Made Practical

--> Socket.io wraps the raw WebSocket protocol with a friendlier event-based API, automatic reconnection, and a fallback to HTTP long-polling for environments where WebSockets aren't available -- reasons it's used far more often in practice than the native `WebSocket` API directly.

```javascript
const express = require("express");
const http = require("http");
const { Server } = require("socket.io");

const app = express();
const server = http.createServer(app);
const io = new Server(server);

io.on("connection", (socket) => {
  console.log(`Client connected: ${socket.id}`);

  socket.on("chatMessage", (data) => {
    io.emit("chatMessage", data);   // Broadcast to EVERY connected client, including the sender
  });

  socket.on("disconnect", () => {
    console.log(`Client disconnected: ${socket.id}`);
  });
});

server.listen(3000);
```

```javascript
// Client-side
const socket = io("http://localhost:3000");

socket.on("chatMessage", (data) => {
  console.log(`${data.user}: ${data.message}`);
});

socket.emit("chatMessage", { user: "Alice", message: "Hello!" });
```

# Broadcasting Options

--> `io.emit()` -- sends to every connected client.
--> `socket.emit()` -- sends only to the specific client that triggered this handler.
--> `socket.broadcast.emit()` -- sends to every client EXCEPT the sender -- common for "someone else did X" notifications where the sender already knows they did it.

# Rooms -- Grouping Connections

--> Rooms let you broadcast to a SUBSET of connected clients (e.g. everyone in a specific chat channel or game session) instead of every single connection on the server.

```javascript
socket.on("joinRoom", (roomName) => {
  socket.join(roomName);
});

socket.on("roomMessage", ({ room, message }) => {
  io.to(room).emit("roomMessage", message);   // Only clients in this room receive it
});
```

# Authentication for Socket Connections

--> Unlike a normal HTTP request, a WebSocket connection is established once and stays open -- auth typically happens during the initial handshake (via a token passed in the connection handshake), verified once, rather than on every individual message.

```javascript
io.use((socket, next) => {
  const token = socket.handshake.auth.token;
  jwt.verify(token, process.env.JWT_SECRET, (err, decoded) => {
    if (err) return next(new Error("Authentication failed"));
    socket.user = decoded;
    next();
  });
});
```

# Scaling WebSockets Across Multiple Servers

--> A basic Socket.io setup keeps connection state in a single server's memory -- broadcasting a message only reaches clients connected to THAT server instance, breaking down once you run multiple server instances behind a load balancer.
--> The Redis Adapter (`socket.io-redis`) solves this -- all server instances publish/subscribe to shared Redis Pub/Sub channels, so a message emitted on one server instance correctly reaches clients connected to any other instance too.
