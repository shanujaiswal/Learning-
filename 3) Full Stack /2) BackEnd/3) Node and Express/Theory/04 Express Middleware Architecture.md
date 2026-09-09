# What Is Middleware

--> Middleware is a function that runs BETWEEN receiving a request and sending a response -- it has access to req, res, and a next() function to pass control to the next middleware/route handler.
--> Middleware runs in the ORDER it's registered with app.use()/app.get() etc. -- order matters significantly.

```javascript
function logger(req, res, next) {
  console.log(`${req.method} ${req.url}`);
  next(); // MUST call next() or the request hangs forever (unless this middleware sends a response itself)
}

app.use(logger); // Applied to every incoming request, regardless of path
```

--> If next() is never called and no response is sent, the client's request will hang until it times out.

# Built-in Middleware

--> express.json() -- parses incoming requests with a JSON body, populating req.body. Required for any route reading JSON from POST/PUT requests.
--> express.urlencoded({ extended: true }) -- parses form-submitted data (application/x-www-form-urlencoded), e.g. traditional HTML form submissions.
--> express.static(folder) -- serves static files (images, CSS, JS) directly from a folder.

```javascript
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
```

# Third-Party Middleware

--> cors -- enables Cross-Origin Resource Sharing, letting a frontend on a different origin (e.g. localhost:3000) call this API (e.g. localhost:5000).

```javascript
const cors = require("cors");
app.use(cors()); // Allows all origins -- for production, configure specific allowed origins:
app.use(cors({ origin: "https://myapp.com" }));
```

--> helmet -- sets various security-related HTTP headers automatically (prevents common attacks like clickjacking, MIME sniffing).

```javascript
const helmet = require("helmet");
app.use(helmet());
```

--> morgan -- HTTP request logger, useful for development and production request auditing.

```javascript
const morgan = require("morgan");
app.use(morgan("dev")); // Logs each request in a concise, colored format
```

# Custom Middleware Patterns

--> Authentication middleware -- checks for a valid token before allowing access to protected routes.

```javascript
function requireAuth(req, res, next) {
  const token = req.headers.authorization?.split(" ")[1]; // "Bearer <token>"
  if (!token) return res.status(401).json({ error: "No token provided" });

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    req.user = decoded; // Attach decoded user info to the request for downstream handlers to use
    next();
  } catch (err) {
    res.status(401).json({ error: "Invalid token" });
  }
}

app.get("/profile", requireAuth, (req, res) => {
  res.json({ user: req.user }); // Only reached if requireAuth called next()
});
```

--> Middleware can be applied globally (app.use), to a specific route (app.get("/path", middleware, handler)), or to a whole router (router.use(middleware)).

# Error-Handling Middleware

--> Error-handling middleware is defined with FOUR parameters (err, req, res, next) -- Express recognizes this signature and treats it specially.
--> Must be registered LAST, after all other routes/middleware.

```javascript
// Regular routes throw or call next(err) to trigger error handling
app.get("/risky", (req, res, next) => {
  try {
    riskyOperation();
  } catch (err) {
    next(err); // Passes control to the error-handling middleware below
  }
});

// Error-handling middleware -- catches errors from anywhere in the app
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(err.status || 500).json({ error: err.message || "Internal Server Error" });
});
```

--> Async route handlers need extra care -- a thrown error inside an async function is NOT automatically caught by Express (pre-Express 5). Wrap in try/catch and call next(err), or use a helper wrapper.

```javascript
function asyncHandler(fn) {
  return (req, res, next) => fn(req, res, next).catch(next); // Automatically forwards rejected promises to error middleware
}

app.get("/users/:id", asyncHandler(async (req, res) => {
  const user = await User.findById(req.params.id); // If this rejects, asyncHandler catches it and calls next(err)
  res.json(user);
}));
```

# 404 Handling

--> A catch-all middleware placed after all defined routes (but before the error handler) handles requests that matched no route.

```javascript
app.use((req, res) => {
  res.status(404).json({ error: "Route not found" });
});
```

## Deep Dive -- Visualizing the Middleware Chain

--> Every middleware function forms one link in a chain -- a request flows through each one, IN REGISTRATION ORDER, until something either sends a response or the chain reaches the final route handler. This directly parallels the Node/Express Middleware discussion in the Python Web Frameworks file's own middleware coverage, and the general "cross-cutting concerns" philosophy from the API Design Patterns file's API Gateway section -- just implemented as an in-process function chain here rather than a separate infrastructure layer.

```
Request
   |
   v
[helmet()] ---next()---> [cors()] ---next()---> [express.json()] ---next()---> [morgan()] ---next()---> [requireAuth] ---next()---> [Route Handler] ---> Response
     |                       |                        |                            |                         |
     |                       |                        |                            |                         |
  (could call             (could call              (could call                 (could call               (could call
   res.end() and           res.end() and             res.end() and              res.end() and             res.status(401)
   skip the rest)          skip the rest)             skip the rest)             skip the rest)             and skip the route)
```

--> Any middleware in the chain can short-circuit the flow by sending a response directly instead of calling `next()` -- exactly what `requireAuth` does above when a token is missing or invalid, preventing the request from ever reaching the actual route handler.

## Deep Dive -- next(err) Skips Straight to Error Middleware

--> Calling `next()` with NO arguments passes control to the NEXT regular middleware in the chain. Calling `next(err)` WITH an argument does something different -- Express skips every remaining REGULAR middleware and route handler, jumping directly to the nearest ERROR-HANDLING middleware (the four-parameter kind covered above).

```javascript
app.use((req, res, next) => {
  console.log("This regular middleware runs normally");
  next();
});

app.get("/risky", (req, res, next) => {
  next(new Error("Something broke"));   // Skips any OTHER regular middleware/routes registered after this point
});

app.use((req, res, next) => {
  console.log("This regular middleware is SKIPPED because next(err) was called upstream");
  next();
});

app.use((err, req, res, next) => {   // Reached directly, bypassing the skipped middleware above
  res.status(500).json({ error: err.message });
});
```

--> This is precisely why authentication/validation middleware typically calls `next(err)` (or directly sends an error response) rather than plain `next()` on failure -- it deliberately routes the request away from the normal flow and into centralized error handling, rather than letting a broken/unauthorized request continue toward a route handler that assumes everything upstream succeeded.
