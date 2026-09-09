# The Problem -- Node.js Is Single-Threaded

--> A single Node.js process runs JavaScript on ONE thread -- it can only use one CPU core, no matter how many cores the server actually has. A production server with 4 or 8 cores would leave most of its capacity unused running just one Node process.

# The Cluster Module -- Multi-Core Node.js

--> Node's built-in `cluster` module forks multiple worker processes (typically one per CPU core), each running its own copy of the application, sharing the same server port -- incoming connections are distributed across workers automatically.

```javascript
const cluster = require("cluster");
const os = require("os");
const express = require("express");

if (cluster.isPrimary) {
  const numCPUs = os.cpus().length;
  for (let i = 0; i < numCPUs; i++) {
    cluster.fork();
  }

  cluster.on("exit", (worker) => {
    console.log(`Worker ${worker.process.pid} died -- starting a replacement`);
    cluster.fork();   // Automatically restart a crashed worker
  });
} else {
  const app = express();
  app.get("/", (req, res) => res.send("Hello from a worker"));
  app.listen(3000);
}
```

--> Each worker is a fully separate process with its own memory -- they don't share application state directly, which is exactly why an external shared store (Redis, a database) is needed for anything that must be consistent across all workers (sessions, caches, rate-limit counters).

# PM2 -- A Production Process Manager

--> PM2 wraps clustering, automatic restarts, logging, and monitoring into one tool, so you don't hand-write the cluster boilerplate above yourself.

```bash
npm install -g pm2

pm2 start app.js -i max          # -i max = one worker process per available CPU core
pm2 list                          # View running processes and their status
pm2 logs                           # Stream logs from all instances
pm2 restart app                    # Zero-downtime restart (PM2 restarts workers one at a time)
pm2 stop app
pm2 startup                        # Configure PM2 to auto-start on server reboot
```

--> PM2 automatically restarts a crashed process, which is a meaningful reliability improvement over a bare `node app.js` that simply exits and stays down if it crashes.

# Graceful Shutdown

--> When a process receives a termination signal (e.g. during a deployment or a Kubernetes pod being rescheduled -- see the DevOps folder's Kubernetes notes), it should finish IN-FLIGHT requests before actually exiting, rather than abruptly dropping active connections.

```javascript
process.on("SIGTERM", () => {
  console.log("SIGTERM received -- shutting down gracefully");
  server.close(() => {
    console.log("All connections closed");
    process.exit(0);
  });

  setTimeout(() => {
    console.error("Forcing shutdown -- connections took too long to close");
    process.exit(1);
  }, 10000);   // Safety timeout so a stuck connection doesn't block shutdown forever
});
```

# Environment-Specific Configuration

--> Production configuration (database URLs, secrets, log levels) should come from environment variables (`process.env`), never hardcoded -- the `dotenv` package loads a local `.env` file into `process.env` during development, while production typically injects real environment variables directly through the hosting platform.

# Reverse Proxy in Front of Node

--> Node apps in production almost always sit behind a reverse proxy (Nginx, or a cloud load balancer as covered in the AWS notes) -- handling TLS termination, serving static files, request buffering, and load-balancing across multiple Node processes/servers, rather than exposing Node directly to the internet.
