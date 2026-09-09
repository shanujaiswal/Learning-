# What Is Node.js

--> Node.js is a JavaScript runtime built on Chrome's V8 engine that lets JavaScript run OUTSIDE the browser -- on servers, command-line tools, desktop apps (Electron), etc.
--> Single-threaded, non-blocking, event-driven architecture -- one thread handles many concurrent connections by never blocking on I/O (file reads, network calls, DB queries).
--> Best suited for I/O-heavy applications (APIs, real-time apps) -- less suited for CPU-heavy work (video encoding, heavy computation) since that blocks the single thread.

# The Event Loop

--> Node's event loop is what enables non-blocking I/O -- it continuously checks for completed async operations and runs their callbacks, without waiting idle.
--> Phases (simplified): Timers (setTimeout/setInterval) -> Pending callbacks -> Poll (I/O callbacks) -> Check (setImmediate) -> Close callbacks -- each phase processes its queue before moving to the next.
--> process.nextTick() and Promise callbacks (microtasks) run BETWEEN phases, before the event loop continues -- similar priority to microtasks in the browser.

```javascript
console.log("1");
setTimeout(() => console.log("2"), 0);   // Timer phase
setImmediate(() => console.log("3"));     // Check phase
process.nextTick(() => console.log("4")); // Runs before the event loop's next phase
console.log("5");
// Output: 1, 5, 4, 2, 3 (order of 2 vs 3 can vary depending on context)
```

# Modules -- CommonJS vs ES Modules

--> CommonJS (the original Node module system) -- uses require() to import and module.exports to export, loaded synchronously.

```javascript
// math.js
function add(a, b) { return a + b; }
module.exports = { add };

// app.js
const { add } = require("./math");
console.log(add(2, 3));
```

--> ES Modules (modern standard, also used in browsers) -- uses import/export syntax, loaded asynchronously.
--> To use ESM in Node -- either name files .mjs, or set "type": "module" in package.json.

```javascript
// math.mjs
export function add(a, b) { return a + b; }

// app.mjs
import { add } from "./math.mjs";
```

--> Key difference: CommonJS require() is dynamic (can be called conditionally, anywhere in code); ESM import is static (must be at the top level, enables better tree-shaking/bundler optimization).

# npm and package.json

--> npm (Node Package Manager) ships with Node -- installs, manages, and publishes JavaScript packages.
--> package.json -- the manifest file for a Node project -- lists dependencies, scripts, metadata, and entry point.

```json
{
  "name": "my-app",
  "version": "1.0.0",
  "main": "index.js",
  "scripts": {
    "start": "node index.js",
    "dev": "nodemon index.js"
  },
  "dependencies": { "express": "^4.18.0" },
  "devDependencies": { "nodemon": "^3.0.0" }
}
```

--> npm install -- installs all dependencies listed in package.json into node_modules/.
--> npm install <package> -- installs and adds to "dependencies"; npm install -D <package> -- adds to "devDependencies" (dev-only tools like testing/linting).
--> package-lock.json -- locks exact installed versions (including nested dependencies) so installs are reproducible across machines.
--> npx -- runs a package's executable without installing it globally (e.g. npx create-react-app my-app).

# Global Objects and process

--> __dirname / __filename -- absolute path of the current module's directory/file (CommonJS only; ESM uses import.meta.url instead).
--> process.env -- access to environment variables, e.g. process.env.PORT, process.env.NODE_ENV -- commonly used with a .env file and the dotenv package.
--> process.argv -- array of command-line arguments passed when running the script.
--> global -- Node's equivalent of the browser's window -- global scope object (rarely used directly).

# The fs (File System) Module

```javascript
const fs = require("fs");

// Synchronous -- blocks the event loop until done, simple but avoid in server request handlers
const data = fs.readFileSync("file.txt", "utf8");

// Asynchronous (callback-based) -- non-blocking
fs.readFile("file.txt", "utf8", (err, data) => {
  if (err) throw err;
  console.log(data);
});

// Promise-based (fs.promises) -- preferred with async/await
const fsPromises = require("fs/promises");
async function readFile() {
  const data = await fsPromises.readFile("file.txt", "utf8");
  console.log(data);
}
```

--> Always prefer the async or promise-based versions in server code -- the sync versions block the single event loop thread, freezing ALL other requests until the file operation completes.

# The path and url Modules

--> path -- handles file paths in a cross-platform way (Windows uses \, Unix uses /).

```javascript
const path = require("path");
path.join(__dirname, "files", "data.txt"); // Builds a path safely across OSes
path.resolve("data.txt");                   // Resolves to an absolute path
path.extname("file.txt");                   // ".txt"
path.basename("/a/b/file.txt");             // "file.txt"
```

--> url -- parses and constructs URLs.

```javascript
const { URL } = require("url");
const myUrl = new URL("https://example.com/path?name=Alice");
myUrl.hostname; // "example.com"
myUrl.searchParams.get("name"); // "Alice"
```

## Deep Dive -- Node Isn't Purely Single-Threaded -- libuv and the Thread Pool

--> The commonly-repeated claim "Node is single-threaded" is a useful simplification but not the whole picture -- YOUR JavaScript code runs on a single thread, but Node itself (via libuv, the C library underlying Node's async I/O) maintains a background THREAD POOL (4 threads by default) for operations the underlying OS can't handle asynchronously on its own.
--> File system operations (`fs.readFile`), DNS lookups (`dns.lookup`), and some crypto operations (`crypto.pbkdf2`) are offloaded to this thread pool -- genuinely running in parallel, behind the scenes, while your single JS thread continues handling other requests. Network I/O (HTTP requests, database calls over a socket), by contrast, typically uses the OS's own native async networking APIs directly, without needing the thread pool at all.

```javascript
// This doesn't block the main JS thread -- libuv's thread pool handles the actual disk read
fs.readFile("large-file.txt", (err, data) => { /* callback fires when the thread pool finishes */ });
```

--> **Why this matters practically** -- the thread pool has a FIXED size (`UV_THREADPOOL_SIZE`, default 4) -- if an application does heavy file I/O or crypto work across many concurrent requests, ALL 4 threads can become saturated, causing seemingly unrelated file/crypto operations to queue up and slow down, even though the main JS event loop itself isn't blocked. This is a real, sometimes surprising bottleneck distinct from the CPU-bound blocking problem the Concurrency file's Python coverage describes for a different language, but rhymes with the same underlying "not everything is actually non-blocking just because it looks async" lesson.
--> For genuinely CPU-bound JavaScript work (heavy synchronous computation, not I/O), directly connecting to the Web Workers concept covered in the Full Stack JavaScript notes -- Node's equivalent is `worker_threads`, spinning up actual separate JS execution contexts, distinct from both the main event loop and the libuv I/O thread pool described here.

## Deep Dive -- Scaling Beyond One Core -- the cluster Module

--> Because the main JS thread is effectively single-threaded for CPU-bound work, a single Node process can only fully utilize ONE CPU core, no matter how many cores the server has -- directly connecting to the Production Deployment, Clustering and PM2 file, which covers exactly this limitation and its solutions (Node's built-in `cluster` module, or PM2's `-i max` flag) in depth.
