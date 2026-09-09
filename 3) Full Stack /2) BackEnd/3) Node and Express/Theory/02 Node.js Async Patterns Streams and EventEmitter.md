# Callbacks -- The Original Async Pattern

--> Node's earliest async APIs use callbacks -- a function passed as an argument, called once the operation finishes.
--> Convention: error-first callbacks -- the first parameter is always the error (or null if none), the second is the result.

```javascript
fs.readFile("file.txt", (err, data) => {
  if (err) return console.error(err);
  console.log(data);
});
```

--> "Callback hell" -- deeply nested callbacks when multiple async steps depend on each other, making code hard to read -- Promises and async/await were introduced largely to fix this.

# Promisifying Callback-Based APIs

--> util.promisify() converts an error-first callback function into one that returns a Promise, letting older Node APIs be used with async/await.

```javascript
const util = require("util");
const fs = require("fs");
const readFilePromise = util.promisify(fs.readFile);

async function main() {
  const data = await readFilePromise("file.txt", "utf8");
  console.log(data);
}
```

--> Most core Node modules already ship a Promise-based version (e.g. fs.promises, dns.promises) -- promisify is mainly needed for older custom or third-party callback APIs.

# EventEmitter -- Node's Pub/Sub Pattern

--> EventEmitter is the base class behind most of Node's async, event-driven APIs (HTTP servers, streams, etc.) -- objects can emit named events, and listeners subscribe to react to them.

```javascript
const EventEmitter = require("events");
const emitter = new EventEmitter();

emitter.on("greet", (name) => console.log(`Hello, ${name}!`)); // Register a listener
emitter.emit("greet", "Alice"); // Trigger the event -- prints "Hello, Alice!"

emitter.once("init", () => console.log("Runs only once")); // Listener auto-removed after first call
```

--> Custom classes commonly extend EventEmitter to build their own event-driven objects (e.g. a custom Logger that emits "error" and "info" events).
--> Unhandled "error" events on an EventEmitter throw and crash the process by default -- always attach an error listener when emitting error events.

# Streams -- Processing Data in Chunks

--> A Stream processes data piece-by-piece (in chunks) instead of loading everything into memory at once -- essential for large files, network data, or anything where the full size is unknown/huge upfront.
--> Four types: Readable (source of data, e.g. reading a file), Writable (destination, e.g. writing a file), Duplex (both, e.g. a TCP socket), Transform (Duplex that modifies data as it passes through, e.g. compression).

```javascript
const fs = require("fs");

const readStream = fs.createReadStream("large-file.txt");
const writeStream = fs.createWriteStream("copy.txt");

readStream.on("data", (chunk) => console.log(`Received ${chunk.length} bytes`));
readStream.on("end", () => console.log("Done reading"));
readStream.on("error", (err) => console.error(err));

readStream.pipe(writeStream); // Pipes data from readable directly to writable, handling backpressure automatically
```

--> Backpressure -- when a writable stream can't keep up with a faster readable stream, .pipe() automatically pauses the readable side until the writable side catches up, preventing memory overload.
--> Piping multiple streams together (e.g. read -> gzip transform -> write) is a common, memory-efficient pattern for processing large data.

# Buffers

--> A Buffer is Node's way of handling raw binary data (before it's decoded into a string) -- used when reading files, network data, or images.

```javascript
const buf = Buffer.from("Hello", "utf8"); // Creates a buffer from a string
console.log(buf); // <Buffer 48 65 6c 6c 6f> -- raw bytes in hex
console.log(buf.toString("utf8")); // "Hello" -- decode back to string
```

--> Streams emit data as Buffer chunks by default -- set encoding (readStream.setEncoding("utf8")) to receive strings instead.

# Error Handling in Async Node Code

--> Unhandled promise rejections and uncaught exceptions can crash a Node process -- always wrap async code in try/catch (for async/await) or .catch() (for Promise chains).

```javascript
process.on("unhandledRejection", (reason) => {
  console.error("Unhandled rejection:", reason); // Log and decide whether to gracefully shut down
});

process.on("uncaughtException", (err) => {
  console.error("Uncaught exception:", err);
  process.exit(1); // Best practice: exit and let a process manager (PM2, Docker) restart cleanly, rather than continuing in an unknown state
});
```

## Deep Dive -- The MaxListeners Warning -- A Real Memory Leak Signal

--> By default, an `EventEmitter` warns (`MaxListenersExceededWarning`) if more than 10 listeners are registered for the SAME event on the SAME emitter -- this isn't an arbitrary annoyance, it's a genuine, well-calibrated leak detector. The most common real cause is registering a NEW listener every time a function runs (e.g. inside a request handler) instead of registering it ONCE, outside that repeated code path.

```javascript
// LEAK -- a new listener is added on EVERY request, and old ones are never removed
app.get("/download", (req, res) => {
  someEmitter.on("progress", (percent) => console.log(percent));   // Accumulates forever across requests
});

// FIX -- register once, outside the repeated handler, or explicitly remove listeners when done
someEmitter.on("progress", (percent) => console.log(percent));   // Registered once, at module load time

app.get("/download", (req, res) => {
  // Just trigger/use the already-registered listener's effect, don't re-register it
});
```

--> `emitter.setMaxListeners(20)` can raise the threshold for cases where genuinely many listeners are expected and intentional -- but reaching for this to silence the warning WITHOUT first confirming the listeners aren't actually accumulating unboundedly is treating the symptom, not the underlying leak.

## Deep Dive -- A Real-World Streaming Pattern -- Piping an HTTP Response

--> The `.pipe()` pattern shown above for files applies identically to HTTP responses -- letting a server stream a large file (or a large API response) directly to the client without ever loading the entire thing into memory on the server at once, a significant, practical difference for large downloads.

```javascript
const express = require("express");
const fs = require("fs");
const app = express();

app.get("/download/:filename", (req, res) => {
  const filePath = `./uploads/${req.params.filename}`;
  const readStream = fs.createReadStream(filePath);

  readStream.on("error", (err) => res.status(404).json({ error: "File not found" }));
  readStream.pipe(res);   // Streams the file directly to the HTTP response, chunk by chunk
});
```

--> Compare this to the naive alternative -- `fs.readFile(filePath, (err, data) => res.send(data))` -- which loads the ENTIRE file into server memory before sending anything at all, meaning a 2GB file consumes 2GB of server RAM (multiplied by however many concurrent downloads are happening) and the client waits for the whole read to finish before receiving even the first byte. The streaming version keeps memory usage constant regardless of file size, and starts sending data to the client immediately as each chunk becomes available.
