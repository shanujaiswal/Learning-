/**
 * 04-websocket-chat-server.js
 *
 * Covers: 07 WebSockets with Socket.io
 *
 * A minimal, real Socket.io chat server:
 *   - Accepts connections
 *   - Broadcasts every "chat message" to ALL connected clients (including sender)
 *   - Announces when a user joins / disconnects
 *
 * Run: node 04-websocket-chat-server.js
 * Then open 04-websocket-client-example.html in a browser (as a file, or
 * served statically) -- open it in two tabs to see broadcast in action.
 */

const http = require('http');
const express = require('express');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*' }, // demo only -- lock this down in real deployments
});

app.get('/', (req, res) => {
  res.send('Socket.io chat server is running. Open 04-websocket-client-example.html to connect.');
});

let connectedCount = 0;

io.on('connection', (socket) => {
  connectedCount += 1;
  console.log(`[socket] client connected: ${socket.id} (total: ${connectedCount})`);

  // Let everyone know someone joined.
  io.emit('system message', `A new user joined the chat (${connectedCount} online)`);

  // Broadcast an incoming chat message to every connected client.
  socket.on('chat message', (payload) => {
    const message = {
      id: socket.id,
      text: String(payload && payload.text ? payload.text : payload),
      at: new Date().toISOString(),
    };
    console.log(`[socket] message from ${socket.id}:`, message.text);
    io.emit('chat message', message); // broadcast to ALL clients, sender included
  });

  socket.on('disconnect', (reason) => {
    connectedCount = Math.max(0, connectedCount - 1);
    console.log(`[socket] client disconnected: ${socket.id} (${reason}) (total: ${connectedCount})`);
    io.emit('system message', `A user left the chat (${connectedCount} online)`);
  });
});

const PORT = process.env.PORT || 3004;
if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`[04] Socket.io chat server listening on http://localhost:${PORT}`);
  });
}

module.exports = { app, server, io };
