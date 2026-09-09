# OAuth2 / OIDC in Depth -- The Google/GitHub Login Flow

--> File 06 introduced Passport's pluggable strategy model with a bare LocalStrategy. Social login (Google, GitHub, "Sign in with X") uses OAuth2 -- a flow where your app never sees the user's password at all; the PROVIDER (Google/GitHub) authenticates the user and hands your app a proof of identity.

## The Authorization Code Flow, Step by Step

--> 1. User clicks "Login with Google" -> your app redirects the browser to Google's consent screen, including a `redirect_uri` (a URL on YOUR server that Google is allowed to send the user back to -- must be pre-registered in the Google/GitHub developer console, or Google will reject the request).
--> 2. User approves access on Google's own page (your app never sees their Google password -- this is the entire point).
--> 3. Google redirects the browser BACK to your `redirect_uri`, appending a one-time `code` as a query parameter.
--> 4. Your server exchanges that `code` (server-to-server, not via the browser) for an access token and the user's profile info, using your app's client ID + client secret to prove it's really your app making the exchange.
--> 5. Your app now knows who the user is (email, name, provider ID) and creates a local session or JWT for them, same as any other login (File 06).

```javascript
const passport = require("passport");
const GoogleStrategy = require("passport-google-oauth20").Strategy;

passport.use(new GoogleStrategy({
  clientID: process.env.GOOGLE_CLIENT_ID,
  clientSecret: process.env.GOOGLE_CLIENT_SECRET,
  callbackURL: "/auth/google/callback"   // Must exactly match a URI registered in Google Cloud Console -- step 1's redirect_uri
},
(accessToken, refreshToken, profile, done) => {
  // Passport already performed step 4 (the code exchange) before this callback runs
  findOrCreateUser({ googleId: profile.id, email: profile.emails[0].value, name: profile.displayName })
    .then((user) => done(null, user))
    .catch((err) => done(err));
}));

app.get("/auth/google", passport.authenticate("google", { scope: ["profile", "email"] }));   // Step 1 -- kicks off the redirect

app.get("/auth/google/callback",
  passport.authenticate("google", { failureRedirect: "/login" }),
  (req, res) => {
    // Step 5 -- req.user is now populated; issue a session cookie or JWT (File 06) here
    res.redirect("/dashboard");
  }
);
```

```javascript
// GitHub follows the identical shape, just a different strategy package
const GitHubStrategy = require("passport-github2").Strategy;

passport.use(new GitHubStrategy({
  clientID: process.env.GITHUB_CLIENT_ID,
  clientSecret: process.env.GITHUB_CLIENT_SECRET,
  callbackURL: "/auth/github/callback"
}, (accessToken, refreshToken, profile, done) => {
  findOrCreateUser({ githubId: profile.id, email: profile.emails?.[0]?.value })
    .then((user) => done(null, user))
    .catch((err) => done(err));
}));
```

--> `passport.serializeUser`/`deserializeUser` (paired with `express-session` from File 06) decide what identifying piece of the user gets stored IN the session (typically just the user ID) versus looked up fresh from the database on each request -- keeps the session cookie itself small.
--> OIDC (OpenID Connect) is OAuth2 with a standardized identity layer on top -- it adds an `id_token` (a JWT, same structure File 06 described) containing standardized claims (`sub`, `email`, `name`) so different providers expose user identity in a consistent shape, instead of each OAuth2 provider inventing its own profile format.

# CSRF Protection and Its Relationship to Session Cookies

--> CSRF (Cross-Site Request Forgery) exploits the fact that a browser AUTOMATICALLY attaches cookies (including session cookies from File 06) to requests, even ones triggered by a malicious page the user merely has open in another tab -- a hidden form on `evil.com` can silently POST to `yourbank.com/transfer`, and the browser happily includes the user's real session cookie.
--> This risk is specifically tied to COOKIE-based auth -- a JWT sent manually via an `Authorization: Bearer` header (File 06) is NOT automatically attached by the browser, so a pure header-based JWT API is naturally immune to classic CSRF; CSRF protection matters most for session-cookie or cookie-stored-JWT setups.

```javascript
const csrf = require("csurf");
const cookieParser = require("cookie-parser");

app.use(cookieParser());
app.use(csrf({ cookie: true }));   // Issues a CSRF token tied to the session

app.get("/form", (req, res) => {
  res.render("form", { csrfToken: req.csrfToken() });   // Embed the token in the rendered HTML (File 11's template engine notes)
});

app.post("/transfer", (req, res) => {
  // csurf middleware automatically rejects the request with a 403 if req.body._csrf doesn't match -- no manual check needed here
  res.json({ message: "Transfer complete" });
});
```

```html
<!-- The embedded token, submitted alongside the real form data -->
<form action="/transfer" method="POST">
  <input type="hidden" name="_csrf" value="<%= csrfToken %>">
  <input type="text" name="amount">
  <button type="submit">Transfer</button>
</form>
```

--> The defense works because `evil.com` can trick the browser into SENDING the cookie, but has no way to read or forge the correct CSRF token value for that user's session -- the token isn't a cookie, so it isn't auto-attached, it must be explicitly read out of the page and included by legitimate JS/forms.
--> `SameSite=Lax` or `SameSite=Strict` on the session cookie itself (a `cookie` option alongside `httpOnly`/`secure` from File 06) is a second, complementary layer -- it tells the browser NOT to send the cookie at all on many cross-site requests, closing off a large share of CSRF vectors before a token check is even needed.

# JWT Revocation and Blacklisting

--> File 06 noted JWTs are stateless -- the server verifies a signature instead of looking anything up. That statelessness is also JWT's biggest operational weakness: once issued, a valid JWT stays valid until it EXPIRES, with no built-in way to invalidate it early (e.g. on logout, or after a password change/compromise).

## Strategy 1 -- A Denylist (Blacklist) in Redis

```javascript
const redis = require("redis");
const client = redis.createClient();

app.post("/logout", authenticateToken, async (req, res) => {
  const token = req.headers.authorization.split(" ")[1];
  const decoded = jwt.decode(token);
  const secondsRemaining = decoded.exp - Math.floor(Date.now() / 1000);

  await client.set(`blacklist:${token}`, "1", { EX: secondsRemaining });   // Stored only until the token would have expired anyway -- no point keeping it longer
  res.json({ message: "Logged out" });
});

async function authenticateToken(req, res, next) {
  const token = req.headers.authorization?.split(" ")[1];
  if (!token) return res.status(401).json({ error: "No token provided" });

  const isBlacklisted = await client.get(`blacklist:${token}`);
  if (isBlacklisted) return res.status(403).json({ error: "Token revoked" });

  jwt.verify(token, process.env.JWT_SECRET, (err, decoded) => {
    if (err) return res.status(403).json({ error: "Invalid or expired token" });
    req.user = decoded;
    next();
  });
}
```

--> This reintroduces a lookup on every request (checking Redis) -- meaningfully trading away some of JWT's "no storage lookup needed" advantage from File 06, specifically to gain the ability to revoke early. It's a deliberate, common trade-off in real systems, not a contradiction of JWT's design.

## Strategy 2 -- Short-Lived Access Tokens + a Revocable Refresh Token

--> Instead of blacklisting the (short-lived) access token itself, keep access tokens very short-lived (e.g. 5-15 minutes) as File 06 described, and store refresh tokens SERVER-SIDE (a database row per issued refresh token). Revoking is then just deleting that row -- the compromised access token naturally expires within minutes regardless, and no per-request blacklist lookup is needed for the frequent, short-lived token.
--> This is generally preferred at scale over Strategy 1 -- it avoids a Redis lookup on every single request, accepting only a brief window (until the access token naturally expires) where a stolen access token remains valid.

# Background Jobs and Task Queues

--> Some work shouldn't happen inline during a request -- sending a welcome email, resizing an uploaded image, generating a large report -- doing it synchronously makes the user wait for work they don't need to wait for, and ties up the request-handling process the way File 11's `worker_threads` discussion described for CPU-bound work, but here the fix is usually "do it later," not "do it on another thread."

## Bull / BullMQ -- Redis-Backed Job Queues

```javascript
const { Queue, Worker } = require("bullmq");
const connection = { host: "127.0.0.1", port: 6379 };   // Redis, same store File 09's clustering notes recommend for shared state across cluster workers

const emailQueue = new Queue("emails", { connection });

app.post("/signup", async (req, res) => {
  const user = await createUser(req.body);
  await emailQueue.add("welcomeEmail", { userId: user.id, email: user.email });   // Returns almost immediately -- the actual sending happens separately
  res.status(201).json({ message: "Account created" });
});
```

```javascript
// A separate worker process (or the same process, run alongside the server) actually processes jobs
const worker = new Worker("emails", async (job) => {
  const { userId, email } = job.data;
  await sendWelcomeEmail(email);
  console.log(`Welcome email sent for user ${userId}`);
}, { connection });

worker.on("failed", (job, err) => console.error(`Job ${job.id} failed:`, err.message));
```

--> Queues built on Redis provide retries with backoff, concurrency limits, delayed jobs, and persistence (a job survives a server restart because it lives in Redis, not just in process memory) -- meaningfully more robust than firing off an unawaited async function inline, which is lost entirely if the process crashes mid-task.
--> Running the worker in a SEPARATE process (or even a separate machine) from the API server is common -- it lets job processing scale independently of request-handling capacity, and an image-processing job spike doesn't compete with the API server's event loop for the same resources.

## node-cron -- Scheduled, Recurring Tasks

```javascript
const cron = require("node-cron");

cron.schedule("0 2 * * *", async () => {   // Standard cron syntax -- "every day at 2:00 AM"
  console.log("Running nightly cleanup job");
  await deleteExpiredSessions();
});
```

--> `node-cron` is for TIME-based recurring tasks (nightly cleanup, hourly report generation) running inside a Node process -- it is NOT a substitute for a job queue handling on-demand, potentially-retried, potentially-high-volume work; the two solve different problems and are often used together (a cron job that itself enqueues a batch of BullMQ jobs, for example).

# API Documentation with Swagger / OpenAPI

--> OpenAPI (formerly "Swagger") is a standardized, machine-readable format (JSON/YAML) describing an API's routes, parameters, request/response shapes, and auth requirements -- from that spec, tools can auto-generate interactive documentation, client SDKs, and even server stub code.

```javascript
const swaggerJsdoc = require("swagger-jsdoc");
const swaggerUi = require("swagger-ui-express");

const swaggerSpec = swaggerJsdoc({
  definition: {
    openapi: "3.0.0",
    info: { title: "User API", version: "1.0.0" }
  },
  apis: ["./routes/*.js"]   // Scans route files for the JSDoc-style comments below
});

app.use("/api-docs", swaggerUi.serve, swaggerUi.setup(swaggerSpec));   // Serves an interactive, try-it-yourself docs page at /api-docs
```

```javascript
/**
 * @swagger
 * /users/{id}:
 *   get:
 *     summary: Get a user by ID
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *     responses:
 *       200:
 *         description: User found
 *       404:
 *         description: User not found
 */
app.get("/users/:id", (req, res) => {
  // Route handler as normal -- the comment above is what swagger-jsdoc scans to build the spec
});
```

--> Keeping the documentation as comments directly above each route (rather than a separately maintained doc file) makes it far more likely to stay in sync with the actual code as routes change -- the same "single source of truth" motivation behind co-locating validation schemas with routes.

# npm in Depth

## Semver Ranges in package.json

--> `^4.2.0` -- allows updates that don't change the LEFT-most non-zero digit (so `4.2.0` up to, but not including, `5.0.0`) -- the default npm uses, assuming semver's promise that minor/patch updates don't break the public API.
--> `~4.2.0` -- allows only PATCH updates (`4.2.0` up to, but not including, `4.3.0`) -- stricter than `^`, used when even minor updates feel risky for a given dependency.
--> An exact version (`4.2.0`, no prefix) locks to that precise version -- no automatic updates at all from a fresh `npm install` resolving ranges, though see below for why `package-lock.json` already pins exact versions regardless of the range in `package.json`.

## npm install vs npm ci

```bash
npm install    # Reads package.json, resolves semver ranges, may UPDATE package-lock.json if ranges allow a newer version, and reuses/updates node_modules incrementally
npm ci          # Reads package-lock.json ONLY, requires it to exist and be in sync with package.json, deletes node_modules first, installs EXACTLY what the lockfile specifies
```

--> `npm ci` is faster and, critically, deterministic -- it guarantees CI/production installs get the exact dependency tree that was tested and committed, with no chance of a semver range silently resolving to a newer (potentially breaking) version between a developer's machine and a deployment. This is why `npm ci` is the standard for CI pipelines (the DevOps folder's pipeline notes) and production builds, while `npm install` is more common for day-to-day local development where picking up a fresh compatible patch version is fine.
--> `npm ci` FAILS outright (rather than silently fixing things) if `package.json` and `package-lock.json` are out of sync -- a deliberate guardrail, forcing the mismatch to be resolved (and re-committed) rather than papered over automatically.

## npm Workspaces -- Monorepo Dependency Management

```json
// root package.json
{
  "name": "my-monorepo",
  "workspaces": ["packages/*"]
}
```

--> Workspaces let a single repo hold multiple packages (e.g. `packages/api`, `packages/shared-utils`) that can depend on EACH OTHER by name, with npm hoisting shared dependencies into one top-level `node_modules` instead of duplicating them per package -- `npm install` run once at the repo root installs and links everything. This is the same underlying problem tools like Lerna and Turborepo also address, with npm workspaces being the built-in, dependency-free baseline option.

# Load and Performance Testing -- k6 and Artillery

--> File 08's testing notes cover CORRECTNESS (does the endpoint return the right response) -- load testing answers a different question: how does this API behave under many CONCURRENT users, and where does it start to degrade or fail.

```javascript
// k6 script (JavaScript, run via the k6 CLI, not through Node/npm)
import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 50,          // 50 "virtual users" simulated concurrently
  duration: "30s"
};

export default function () {
  const res = http.get("https://api.example.com/users");
  check(res, { "status is 200": (r) => r.status === 200 });
  sleep(1);
}
```

```yaml
# Artillery config (YAML)
config:
  target: "https://api.example.com"
  phases:
    - duration: 60
      arrivalRate: 20   # 20 new virtual users arriving per second, for 60 seconds
scenarios:
  - flow:
      - get:
          url: "/users"
```

--> Both tools report throughput (requests/second sustained), latency distribution (p50/p95/p99 -- the tail matters more than the average, since a slow p99 means a real fraction of actual users have a bad experience even if the average looks fine), and error rates as load increases -- used to find the point where a `cluster`/PM2 setup (File 09) needs more instances, a database becomes the bottleneck, or a memory leak (File 02's MaxListeners note is one concrete example of a leak source) causes degradation under sustained load rather than immediately.
--> k6 scripts are written in JS but run by a separate Go-based binary (not Node) for higher-throughput load generation than Node itself could produce; Artillery is npm-installable and Node-based, generally simpler to get started with for a JS-focused team at the cost of being less efficient at generating extremely high load itself.

# Lightweight Alternatives to Socket.io -- Native ws and Server-Sent Events

--> File 07 covered Socket.io's event-based abstraction, reconnection handling, and long-polling fallback. Both add real overhead (a larger client bundle, protocol negotiation) that isn't always needed -- two lighter options cover common cases.

## The `ws` Library -- Native WebSockets Without the Abstraction

```javascript
const WebSocket = require("ws");
const wss = new WebSocket.Server({ port: 8080 });

wss.on("connection", (socket) => {
  socket.on("message", (data) => {
    wss.clients.forEach((client) => {
      if (client.readyState === WebSocket.OPEN) client.send(data);   // Manual broadcast -- no io.emit() helper, this is the raw protocol
    });
  });
});
```

```javascript
// Client-side -- the actual native browser WebSocket API, no library needed at all
const socket = new WebSocket("ws://localhost:8080");
socket.onmessage = (event) => console.log("Received:", event.data);
socket.send("Hello server");
```

--> `ws` is a thin, fast implementation of the raw WebSocket protocol with none of Socket.io's extras -- reconnection, rooms, and fallback transports must all be hand-built if needed. A reasonable choice when the client is known to reliably support WebSockets (e.g. a controlled internal tool, not the general public internet) and Socket.io's abstraction/overhead isn't worth it.

## Server-Sent Events (SSE) -- One-Way Server-to-Client Push Over Plain HTTP

```javascript
app.get("/events", (req, res) => {
  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");

  const interval = setInterval(() => {
    res.write(`data: ${JSON.stringify({ time: Date.now() })}\n\n`);   // SSE's required wire format -- "data: <payload>\n\n"
  }, 1000);

  req.on("close", () => clearInterval(interval));   // Clean up when the client disconnects, same idea as graceful shutdown in File 09
});
```

```javascript
// Client-side -- also a native browser API, no library needed
const eventSource = new EventSource("/events");
eventSource.onmessage = (event) => console.log("Update:", JSON.parse(event.data));
```

--> SSE is plain HTTP (not a protocol upgrade like WebSockets) and is inherently ONE-WAY -- server pushes to client only, with no equivalent path for the client to send messages back over the same connection. It's the right fit for pure notification/live-update feeds (stock tickers, progress bars, live scoreboards) where the client never needs to talk back, and it needs none of the WebSocket handshake machinery, working through plain HTTP infrastructure (proxies, load balancers) that may need explicit configuration to support WebSockets properly.
--> Decision guide: Socket.io (File 07) for full-featured bidirectional real-time features with broad client compatibility concerns already handled; raw `ws` for bidirectional communication where the extra abstraction isn't needed; SSE for one-way server push where the client never replies over the same channel.
