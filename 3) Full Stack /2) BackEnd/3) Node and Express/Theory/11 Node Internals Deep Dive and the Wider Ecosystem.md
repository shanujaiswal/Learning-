# worker_threads -- True Parallelism for CPU-Bound Work

--> Node is single-threaded for JavaScript execution (see File 09's clustering notes), but `cluster` solves SCALING across requests, not a single expensive CPU-bound calculation blocking the event loop. `worker_threads` runs actual JavaScript on a separate OS thread WITHIN the same process, letting one heavy computation (image processing, big JSON parsing, crypto hashing) run without freezing the main thread's ability to handle other requests.

```javascript
// worker.js -- the code that runs ON the worker thread
const { parentPort, workerData } = require("worker_threads");

function fibonacci(n) {
  return n < 2 ? n : fibonacci(n - 1) + fibonacci(n - 2);   // Deliberately expensive, CPU-bound
}

const result = fibonacci(workerData.n);
parentPort.postMessage(result);   // Send the result back to the main thread
```

```javascript
// main.js -- spawns the worker and waits for its result
const { Worker } = require("worker_threads");

function runFibonacci(n) {
  return new Promise((resolve, reject) => {
    const worker = new Worker("./worker.js", { workerData: { n } });
    worker.on("message", resolve);
    worker.on("error", reject);
    worker.on("exit", (code) => {
      if (code !== 0) reject(new Error(`Worker stopped with exit code ${code}`));
    });
  });
}

app.get("/fib/:n", async (req, res) => {
  const result = await runFibonacci(Number(req.params.n));   // Main thread stays free to handle other requests meanwhile
  res.json({ result });
});
```

--> Each worker gets its OWN V8 instance and event loop -- no shared memory by default, `workerData` and `postMessage` copy (serialize) data between threads rather than sharing it directly, similar in spirit to how `cluster` workers don't share memory (File 09).

## SharedArrayBuffer -- Actual Shared Memory Between Threads

--> When copying data back and forth is too slow (e.g. a large buffer updated frequently), `SharedArrayBuffer` gives multiple threads a region of memory they genuinely both read/write -- paired with `Atomics` for safe concurrent access without race conditions.

```javascript
// main.js
const { Worker } = require("worker_threads");

const sharedBuffer = new SharedArrayBuffer(4);   // 4 bytes -- one 32-bit int
const sharedArray = new Int32Array(sharedBuffer);

const worker = new Worker("./incrementer.js", { workerData: { sharedBuffer } });

setTimeout(() => {
  console.log("Counter value:", Atomics.load(sharedArray, 0));   // Reflects the worker's writes -- true shared memory, no message passing needed
}, 1000);
```

```javascript
// incrementer.js
const { workerData } = require("worker_threads");
const sharedArray = new Int32Array(workerData.sharedBuffer);

for (let i = 0; i < 1000; i++) {
  Atomics.add(sharedArray, 0, 1);   // Atomic increment -- safe even if another thread reads/writes concurrently
}
```

--> `Atomics.add`/`Atomics.load`/`Atomics.wait`/`Atomics.notify` exist specifically because ordinary `sharedArray[0]++` is NOT safe across threads -- it can read-modify-write in a way that loses updates when two threads do it "simultaneously." Reach for `SharedArrayBuffer` only when profiling shows message-passing overhead is the actual bottleneck -- it's a much sharper tool with real footguns (manual synchronization) compared to the simpler message-passing model above.

## worker_threads vs cluster -- Choosing the Right Tool

--> `cluster` forks separate OS PROCESSES, each with a full copy of the app, each able to bind the same port -- built for scaling many independent HTTP requests across CPU cores. `worker_threads` runs separate THREADS inside one process -- built for offloading a single CPU-heavy task so it doesn't block that process's event loop.
--> Rule of thumb: use `cluster` (or PM2's `-i max`, File 09) to handle MORE concurrent requests across cores. Use `worker_threads` when ONE request's work (e.g. resizing an uploaded image, generating a PDF, heavy data transformation) is expensive enough to noticeably stall the event loop for everyone else, regardless of how many processes are running.
--> Threads are lighter-weight to spawn than processes and can share memory (`SharedArrayBuffer`) -- but a crash inside a poorly-isolated worker is more likely to be able to affect shared state than a crashed cluster worker, which dies as a fully separate process.

# Writing Custom Streams

--> File 02 covered CONSUMING built-in streams (`fs.createReadStream().pipe(...)`). Building a custom stream means extending `Readable`, `Writable`, or `Transform` and implementing the one method Node calls internally to move data.

## Custom Readable -- `_read`

```javascript
const { Readable } = require("stream");

class CounterStream extends Readable {
  constructor(max, options) {
    super(options);
    this.current = 0;
    this.max = max;
  }

  _read() {
    // Node calls this whenever it wants more data -- push(null) signals "no more data, end the stream"
    if (this.current >= this.max) {
      this.push(null);
      return;
    }
    this.push(`${this.current}\n`);
    this.current++;
  }
}

const counter = new CounterStream(5);
counter.pipe(process.stdout);   // Prints 0 through 4, each on its own line
```

## Custom Writable -- `_write`

```javascript
const { Writable } = require("stream");

class UppercaseLogger extends Writable {
  _write(chunk, encoding, callback) {
    console.log(chunk.toString().toUpperCase());
    callback();   // MUST be called to signal "ready for the next chunk" -- omitting it stalls the stream forever
  }
}

process.stdin.pipe(new UppercaseLogger());   // Echoes typed input back in uppercase
```

## Custom Transform -- `_transform`

--> A `Transform` stream is a `Duplex` that modifies data as it flows through -- readable on one end, writable on the other, with `_transform` bridging the two. This is the shape gzip, encryption, and CSV-parsing streams all take internally.

```javascript
const { Transform } = require("stream");

class RedactSecretsStream extends Transform {
  _transform(chunk, encoding, callback) {
    const redacted = chunk.toString().replace(/password=\S+/g, "password=***");
    callback(null, redacted);   // First arg: error (or null); second arg: the transformed chunk to push downstream
  }
}

fs.createReadStream("access.log")
  .pipe(new RedactSecretsStream())
  .pipe(fs.createWriteStream("access-redacted.log"));   // Same pipe-chain pattern from File 02, now with a custom stage in the middle
```

--> Because these are real `Readable`/`Writable`/`Transform` instances, they get backpressure handling and `.pipe()` compatibility for free -- the same memory-safety benefits File 02 described for built-in streams apply to hand-written ones too, as long as `_read`/`_write`/`_transform` are implemented correctly (calling `callback`/`push` appropriately).

# child_process -- Running Other Programs from Node

--> Sometimes the right tool for a job isn't JavaScript at all -- `child_process` lets Node spawn and communicate with external programs (shell commands, Python scripts, ImageMagick, ffmpeg) or other Node scripts.

## spawn -- Streaming Output, Best for Large or Long-Running Output

```javascript
const { spawn } = require("child_process");

const ls = spawn("ls", ["-la", "/tmp"]);

ls.stdout.on("data", (data) => console.log(`stdout: ${data}`));   // stdout/stderr are STREAMS -- same Readable interface as File 02
ls.stderr.on("data", (data) => console.error(`stderr: ${data}`));
ls.on("close", (code) => console.log(`Process exited with code ${code}`));
```

## exec -- Buffered Output, Convenient for Short Commands

```javascript
const { exec } = require("child_process");

exec("git rev-parse HEAD", (err, stdout, stderr) => {
  if (err) return console.error(err);
  console.log(`Current commit: ${stdout.trim()}`);
});
```

--> `exec` buffers the ENTIRE output in memory before the callback fires -- fine for a short git command, a poor choice for a command that could produce megabytes of output (use `spawn` instead, for the same reason File 02 preferred streaming a file over `fs.readFile`-ing it whole). `exec` also runs the command through a shell, so untrusted input passed into it is a command-injection risk -- `spawn` with an argument array avoids shell interpretation entirely.

## fork -- Spawning Another Node Process, With a Built-In Message Channel

```javascript
// parent.js
const { fork } = require("child_process");

const child = fork("./child.js");
child.send({ task: "processData", payload: [1, 2, 3] });   // fork() gets a built-in IPC channel -- send()/on("message") -- unlike spawn/exec
child.on("message", (result) => console.log("Result from child:", result));
```

```javascript
// child.js
process.on("message", (msg) => {
  if (msg.task === "processData") {
    const doubled = msg.payload.map((n) => n * 2);
    process.send(doubled);
  }
});
```

--> `fork` is specifically for spawning OTHER NODE.JS scripts -- it's how `cluster` (File 09) creates its workers under the hood. Compared to `worker_threads`, a forked child is a full separate process (heavier to start, no shared memory) but is more isolated -- a crash can't corrupt the parent's memory the way a badly-behaved shared buffer theoretically could.

# Template Engines and Server-Side Rendering with Express

--> Not every Express app is a pure JSON API -- when the server needs to render actual HTML pages (traditional web apps, admin dashboards, SEO-sensitive pages), a template engine lets you inject dynamic data into HTML on the server before sending it to the browser.

## EJS -- Embedded JavaScript

```javascript
const express = require("express");
const app = express();

app.set("view engine", "ejs");
app.set("views", "./views");   // Directory containing .ejs template files

app.get("/profile/:id", (req, res) => {
  const user = { name: "Alice", email: "alice@example.com" };
  res.render("profile", { user });   // Renders views/profile.ejs, injecting `user` into its scope
});
```

```html
<!-- views/profile.ejs -->
<h1>Welcome, <%= user.name %></h1>
<p>Email: <%= user.email %></p>
<% if (user.isAdmin) { %>
  <p>Admin panel access enabled</p>
<% } %>
```

--> EJS is plain HTML with `<% %>` tags for embedded JS logic -- the lowest learning curve of the common engines because it doesn't introduce new syntax, just JS inside HTML.

## Pug -- Indentation-Based, Terser Syntax

```pug
// views/profile.pug
h1 Welcome, #{user.name}
p Email: #{user.email}
if user.isAdmin
  p Admin panel access enabled
```

--> Pug trades familiarity for brevity -- no closing tags, indentation defines nesting (Python-like) -- popular in projects that value terse templates but has a steeper learning curve for developers unfamiliar with its syntax.
--> `res.render()` works the same way regardless of which engine is configured via `app.set("view engine", ...)` -- switching engines doesn't change controller code, only the `.ejs`/`.pug` files themselves.

--> SSR (server-side rendering) trade-off: rendering HTML on the server means the browser gets a complete page immediately (good for SEO and slow devices) at the cost of more server CPU work per request, versus an SPA (File 10's GraphQL notes and the frontend folder's React notes) sending a near-empty HTML shell plus JSON that the CLIENT renders into HTML.

# Other Node.js Web Frameworks -- How Express Compares

--> Express (File 03) is minimal and unopinionated by design -- that flexibility is also why alternatives exist, each making different trade-offs.

--> **Fastify** -- built explicitly for speed, using JSON-schema-based route validation and serialization compiled ahead of time -- consistently benchmarks faster than Express for high-throughput APIs, with a plugin architecture similar in spirit to Express middleware but more structured.
--> **Koa** -- built by the original Express team as a lighter, more modern successor -- uses `async`/`await`-based middleware (`ctx` object instead of separate `req`/`res`) from the ground up rather than Express's older callback-style `(req, res, next)`, but ships with almost nothing built in (no router included by default), leaning harder into "bring your own everything."
--> **NestJS** -- a full, opinionated, TypeScript-first framework inspired by Angular's architecture -- enforces structure via decorators, modules, dependency injection, and controllers, trading Express's flexibility for consistency and testability on large teams/codebases. NestJS can actually run ON TOP OF Express (or Fastify) under the hood as its HTTP layer.
--> **Hapi** -- similar goals to Express but configuration-driven rather than middleware-chain-driven -- routes, validation, and auth are declared as config objects, historically popular for its built-in input validation (`joi`) before that became common practice elsewhere.
--> In short: Express for flexibility and the largest ecosystem, Fastify for raw performance, Koa for a minimal modern async core, NestJS for large structured applications, Hapi for configuration-first APIs with strict validation baked in.

# Structured Logging -- Winston and Pino vs morgan

--> `morgan` (mentioned in File 04's middleware notes) logs only HTTP REQUEST lines (method, path, status, response time) -- useful, but it can't log application events (a caught error, a background job outcome, a business event) or attach structured metadata. Winston and Pino are general-purpose APPLICATION loggers for that broader job.

## Winston -- Flexible, Multiple Transports

```javascript
const winston = require("winston");

const logger = winston.createLogger({
  level: "info",
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.json()   // Structured JSON output -- machine-parseable by log aggregators (ELK, Datadog)
  ),
  transports: [
    new winston.transports.Console(),
    new winston.transports.File({ filename: "error.log", level: "error" })   // Errors also written to their own file
  ]
});

logger.info("Server started", { port: 3000 });
logger.error("Database connection failed", { error: err.message });
```

## Pino -- Optimized for Raw Speed

```javascript
const pino = require("pino");
const logger = pino();   // Minimal setup, JSON output by default

app.use((req, res, next) => {
  logger.info({ method: req.method, url: req.url }, "Incoming request");
  next();
});

logger.error({ err }, "Something went wrong");
```

--> Pino is deliberately minimal and extremely fast (it does far less work per log call than Winston, deferring formatting), popular where logging overhead under high request volume genuinely matters. Winston is more configurable out of the box (multiple simultaneous transports, custom formats) at a modest performance cost.
--> Both output structured JSON logs (versus morgan's plain text HTTP line) -- structured logs can be filtered/queried by field (`level:error`, `userId:123`) in log aggregation tools, which plain-text logs can't support without fragile regex parsing.

# TypeScript with Node and Express

--> TypeScript adds static typing on top of JavaScript, catching type errors (calling a function with the wrong argument shape, typo-ing a property name) at compile time rather than at runtime in production.

```bash
npm install typescript ts-node @types/node @types/express --save-dev
npx tsc --init   # Generates tsconfig.json
```

```typescript
// server.ts
import express, { Request, Response, NextFunction } from "express";

const app = express();
app.use(express.json());

interface User {
  id: number;
  name: string;
}

app.get("/users/:id", (req: Request, res: Response) => {
  const user: User = { id: Number(req.params.id), name: "Alice" };
  res.json(user);
});

app.use((err: Error, req: Request, res: Response, next: NextFunction) => {   // Typed error-handling middleware, same signature contract as File 04
  res.status(500).json({ error: err.message });
});

app.listen(3000, () => console.log("Server running on port 3000"));
```

```json
// package.json (relevant scripts)
{
  "scripts": {
    "dev": "ts-node server.ts",
    "build": "tsc",
    "start": "node dist/server.js"
  }
}
```

--> `@types/express` and `@types/node` are DefinitelyTyped community type definitions -- Express itself is written in plain JS, so these packages supply the type information the TypeScript compiler needs to type-check `req`/`res`/etc. `ts-node` runs `.ts` files directly during development (compiling on the fly); production builds compile to plain `.js` via `tsc` first, since Node itself cannot execute TypeScript directly.
--> NestJS (above) is TypeScript-first from the ground up -- if a project is already committing to TypeScript AND wants an opinionated structure, that's a strong reason to consider it over hand-rolling TypeScript on top of plain Express.
