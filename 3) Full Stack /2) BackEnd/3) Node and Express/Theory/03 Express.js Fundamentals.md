# What Is Express.js

--> Express is a minimal, unopinionated web framework for Node.js -- provides routing, middleware support, and request/response helpers on top of Node's raw http module.
--> It's the most widely used Node.js backend framework -- most tutorials, job listings, and boilerplates assume Express as the default choice.

# Setting Up a Basic Server

```javascript
const express = require("express");
const app = express();
const PORT = process.env.PORT || 3000;

app.get("/", (req, res) => {
  res.send("Hello World");
});

app.listen(PORT, () => console.log(`Server running on port ${PORT}`));
```

--> app.listen() starts the underlying HTTP server -- Express is really just a layer of convenience on top of Node's http.createServer().

# Routing

--> A route matches an HTTP method + URL path to a handler function.

```javascript
app.get("/users", (req, res) => { res.json({ users: [] }); });      // GET
app.post("/users", (req, res) => { res.status(201).json(req.body); }); // POST
app.put("/users/:id", (req, res) => { res.json({ updated: req.params.id }); }); // PUT
app.delete("/users/:id", (req, res) => { res.sendStatus(204); });    // DELETE
```

# Route Parameters and Query Strings

```javascript
// Route parameter -- part of the URL path itself
app.get("/users/:id", (req, res) => {
  res.json({ id: req.params.id }); // /users/5 -> req.params.id === "5"
});

// Query string -- ?key=value appended to the URL
app.get("/search", (req, res) => {
  res.json({ query: req.query.q }); // /search?q=node -> req.query.q === "node"
});
```

--> req.params -- named route segments (:id).
--> req.query -- parsed query string parameters.
--> req.body -- parsed request body (requires the express.json() middleware for JSON bodies -- see next file).

# The Request and Response Objects

--> req (Request) -- represents the incoming HTTP request. Key properties: req.method, req.url, req.headers, req.params, req.query, req.body.
--> res (Response) -- used to send data back. Key methods:
--> res.send(data) -- sends a response (string, HTML, buffer, or object).
--> res.json(data) -- sends a JSON response with the correct Content-Type header.
--> res.status(code) -- sets the HTTP status code -- commonly chained: res.status(404).json({ error: "Not found" }).
--> res.redirect(url) -- sends a redirect response.
--> res.sendStatus(code) -- sends just a status code with its standard text (e.g. 404 -> "Not Found").

# Router -- Modularizing Routes

--> express.Router() lets routes be organized into separate files/modules instead of all living in one giant app.js.

```javascript
// routes/users.js
const express = require("express");
const router = express.Router();

router.get("/", (req, res) => res.json({ users: [] }));
router.get("/:id", (req, res) => res.json({ id: req.params.id }));

module.exports = router;

// app.js
const usersRouter = require("./routes/users");
app.use("/users", usersRouter); // All routes in usersRouter are now prefixed with /users
```

--> This pattern scales cleanly -- each resource (users, posts, orders) gets its own router file, keeping the codebase organized as it grows.

# Serving Static Files

```javascript
app.use(express.static("public")); // Serves files in the "public" folder directly, e.g. public/style.css -> /style.css
```

# Environment-Based Configuration

--> Combine with the dotenv package to load environment variables from a .env file during development.

```javascript
require("dotenv").config(); // Loads variables from .env into process.env
const PORT = process.env.PORT || 3000;
const DB_URL = process.env.DATABASE_URL;
```

--> .env files should always be added to .gitignore -- they typically contain secrets (API keys, DB credentials) that must never be committed.

## Deep Dive -- Route Matching Order and the app.all() Wildcard

--> Express matches routes in the EXACT ORDER they're registered, top to bottom -- the first matching route wins, and any routes registered after it for the same path/method are never reached for that request. This is a genuinely common source of bugs when a more general route is accidentally registered BEFORE a more specific one.

```javascript
// BUG: this general route matches "/users/new" too, since ":id" matches ANY string
app.get("/users/:id", (req, res) => res.json({ id: req.params.id }));
app.get("/users/new", (req, res) => res.send("New user form"));   // NEVER reached -- the route above already matched

// FIX: register the more specific route FIRST
app.get("/users/new", (req, res) => res.send("New user form"));
app.get("/users/:id", (req, res) => res.json({ id: req.params.id }));
```

--> `app.all(path, handler)` matches EVERY HTTP method for a given path -- useful for applying logic (like authentication logging) that should run regardless of whether the request is a GET, POST, or otherwise, without registering the same handler five separate times for each method.

```javascript
app.all("/admin/*", (req, res, next) => {
  console.log(`Admin access attempt: ${req.method} ${req.url}`);
  next();
});
```

## Deep Dive -- Route Path Patterns Beyond Simple Params

--> Express route paths support more than just plain `:param` segments -- optional parameters, wildcards, and regex patterns all work directly in the path string.

```javascript
app.get("/users/:id?", handler);        // ":id?" -- the "?" makes this parameter OPTIONAL
app.get("/files/*", handler);             // "*" -- matches anything after /files/, available via req.params[0]
app.get(/.*fly$/, handler);                // A regular expression directly as the route path -- matches "butterfly", "dragonfly", etc.
```

--> These are used less often than plain named parameters, but are genuinely useful for catch-all routes (serving a Single Page Application's `index.html` for any unmatched client-side route, a common Express + React production deployment pattern) or flexible URL matching that a fixed `:param` pattern can't express.
