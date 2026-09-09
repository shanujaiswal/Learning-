# Node and Express -- Practical Demos

A small, self-contained Express project with one runnable file per topic from
the [`Theory`](../Theory) chapters. Each demo is a real, working program --
not pseudocode -- meant to be run and poked at while reading the matching
theory note.

## Setup

```bash
npm install
```

Run once from this folder. After that, each demo below is started with its
own `npm run <script>` command (or `node <file>.js` directly).

## File index

| File | Theory chapter(s) covered | Run command | What it does |
|---|---|---|---|
| `01-rest-api-server.js` | 03 Express.js Fundamentals, 05 Building REST APIs with Express | `npm run rest-api` | A real Express REST API for a "tasks" resource: full GET/POST/PUT/DELETE, JSON body parsing via `express.json()`, input validation with 400 responses, proper status codes (200/201/204/404). Exports the `app` (without `.listen()`) so it can be tested directly. |
| `02-middleware-chain.js` | 04 Express Middleware Architecture | `npm run middleware` | Custom middleware chain: request logger, in-memory rate limiter, route-specific middleware (`/admin` only), and a centralized error handler reached via `next(err)`. |
| `03-jwt-auth-demo.js` | 06 Authentication JWT Sessions and Passport | `npm run jwt-auth` | `POST /login` issues a signed JWT for valid credentials; `GET /protected/profile` is guarded by an `authenticateToken` middleware. Comments in the file show the exact `curl` commands for a valid token, a missing token (401), and a deliberately-wrong token (403). |
| `04-websocket-chat-server.js` | 07 WebSockets with Socket.io | `npm run chat-server` | A real Socket.io server: broadcasts `chat message` events to every connected client, announces joins/disconnects, cleans up on `disconnect`. |
| `04-websocket-client-example.html` | 07 WebSockets with Socket.io | Open in a browser (server must be running) | Companion browser client for the chat server above. Open it in two tabs to see live broadcast between clients. |
| `05-streams-and-eventemitter.js` | 02 Node.js Async Patterns Streams and EventEmitter | `npm run streams` | A custom `OrderProcessor` class extending `EventEmitter` (emits `placed`/`processed`/`error`), plus a real `Readable -> Transform -> Writable` pipe that reads `data/input.txt`, uppercases it line by line, and writes `data/output.txt`. |
| `06-api.test.js` | 08 Testing Node and Express APIs | `npm test` | Jest + Supertest tests against `01-rest-api-server.js`, imported as a module (no open port needed). Covers GET list, POST create, POST validation failure (400), GET 404, PUT update, and DELETE. |

Chapters not covered by a standalone runnable demo in this folder
(01 Node.js Fundamentals, 09 Production Deployment, 10 GraphQL) are
conceptual/operational topics best read directly in `Theory/` -- clustering
and PM2 config, and GraphQL schema design, don't lend themselves to a single
quick-run script the way the above topics do.

## Notes

- Express's built-in `express.json()` is used everywhere instead of the
  deprecated standalone `body-parser` package (unnecessary since Express 4.16+).
- Each server file only calls `.listen()` when run directly
  (`require.main === module`), so `01-rest-api-server.js` can be safely
  `require()`-d by the test file without binding a port.
- Ports used, so you can run several demos side by side:
  - `01-rest-api-server.js` -> 3001
  - `02-middleware-chain.js` -> 3002
  - `03-jwt-auth-demo.js` -> 3003
  - `04-websocket-chat-server.js` -> 3004
