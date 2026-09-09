# RESTful Resource Structure

--> Structure Express routes around resources (nouns), using HTTP methods for actions -- consistent with REST conventions covered in "REST API.md".

```javascript
const router = express.Router();

router.get("/", getAllPosts);       // GET    /posts
router.get("/:id", getPostById);    // GET    /posts/:id
router.post("/", createPost);       // POST   /posts
router.put("/:id", updatePost);     // PUT    /posts/:id
router.delete("/:id", deletePost);  // DELETE /posts/:id

module.exports = router;
```

--> Separating route definitions from handler logic (controllers) keeps route files short and readable -- this is the MVC-inspired pattern most Express apps follow.

# MVC-Style Project Structure

```text
project/
├── routes/       -- defines URL paths and maps them to controller functions
│   └── posts.js
├── controllers/  -- contains the actual request-handling logic
│   └── postController.js
├── models/       -- database schema/queries (Mongoose models, Sequelize models, or raw SQL functions)
│   └── Post.js
├── middleware/   -- custom middleware (auth, validation, error handling)
│   └── auth.js
└── app.js        -- wires everything together
```

```javascript
// controllers/postController.js
const Post = require("../models/Post");

exports.getAllPosts = async (req, res, next) => {
  try {
    const posts = await Post.find(); // Example using Mongoose
    res.json(posts);
  } catch (err) {
    next(err);
  }
};

exports.createPost = async (req, res, next) => {
  try {
    const post = await Post.create(req.body);
    res.status(201).json(post);
  } catch (err) {
    next(err);
  }
};
```

# Connecting to a Database

--> MongoDB with Mongoose (ODM) -- defines schemas and provides query methods.

```javascript
const mongoose = require("mongoose");
mongoose.connect(process.env.MONGO_URI);

const postSchema = new mongoose.Schema({
  title: { type: String, required: true },
  content: String,
  createdAt: { type: Date, default: Date.now },
});

const Post = mongoose.model("Post", postSchema);
module.exports = Post;
```

--> SQL (PostgreSQL/MySQL) with an ORM like Sequelize, or a raw driver (pg, mysql2) -- follows a similar model-definition pattern, see "18 DB Connectors and ORM.md" in the Python notes for the general ORM concepts (SQLAlchemy parallels Sequelize closely).

# Request Validation

--> Never trust client input directly -- validate request bodies before using them, typically with a library like express-validator or Joi/Zod.

```javascript
const { body, validationResult } = require("express-validator");

router.post(
  "/",
  body("title").notEmpty().withMessage("Title is required"),
  body("email").isEmail().withMessage("Must be a valid email"),
  (req, res, next) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ errors: errors.array() });
    }
    next();
  },
  createPost
);
```

# Pagination and Filtering

```javascript
router.get("/", async (req, res) => {
  const page = parseInt(req.query.page) || 1;
  const limit = parseInt(req.query.limit) || 10;
  const skip = (page - 1) * limit;

  const posts = await Post.find().skip(skip).limit(limit); // Mongoose example
  const total = await Post.countDocuments();

  res.json({ posts, page, totalPages: Math.ceil(total / limit) });
});
```

# File Uploads with Multer

```javascript
const multer = require("multer");
const upload = multer({ dest: "uploads/" });

router.post("/upload", upload.single("avatar"), (req, res) => {
  res.json({ file: req.file }); // req.file contains info about the uploaded file
});
```

# Rate Limiting

--> express-rate-limit protects an API from abuse/brute-force by limiting how many requests a client can make in a given time window.

```javascript
const rateLimit = require("express-rate-limit");

const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100,                  // Limit each IP to 100 requests per window
});

app.use("/api/", limiter);
```

# Putting It Together -- app.js

```javascript
require("dotenv").config();
const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const morgan = require("morgan");

const postsRouter = require("./routes/posts");
const errorHandler = require("./middleware/errorHandler");

const app = express();

app.use(helmet());
app.use(cors());
app.use(morgan("dev"));
app.use(express.json());

app.use("/api/posts", postsRouter);

app.use(errorHandler); // Must be last

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Server running on port ${PORT}`));
```

## Deep Dive -- Implementing API Versioning in Express

--> Directly connecting to the API Design Patterns, Versioning and Gateways file's coverage of versioning STRATEGY -- here's the concrete Express implementation of URI-based versioning, the simplest and most common approach.

```javascript
const v1PostsRouter = require("./routes/v1/posts");
const v2PostsRouter = require("./routes/v2/posts");

app.use("/api/v1/posts", v1PostsRouter);
app.use("/api/v2/posts", v2PostsRouter);   // A breaking change lives in its own router, v1 keeps working unchanged
```

--> Keeping each version's routes/controllers in entirely separate files/folders (rather than branching logic inside one shared handler with `if (req.version === 'v2')` checks) keeps each version's behavior clear and independently maintainable -- directly avoiding the kind of tangled, hard-to-reason-about conditional logic that accumulates when multiple API versions are forced to share the same code path.

## Deep Dive -- Idempotency Keys in Practice

--> Directly implementing the Idempotency Key concept covered in the API Design Patterns file -- for a payment or order-creation endpoint where accidental duplicate submissions (a network retry, a double-click) would be genuinely costly, checking a client-supplied idempotency key before processing prevents that duplication.

```javascript
const processedRequests = new Map();   // In production, use Redis (covered in the Full Stack Extra notes) instead of an in-memory Map

router.post("/orders", async (req, res) => {
  const idempotencyKey = req.headers["idempotency-key"];

  if (idempotencyKey && processedRequests.has(idempotencyKey)) {
    return res.json(processedRequests.get(idempotencyKey));   // Return the SAME result as the original request, don't reprocess
  }

  const order = await Order.create(req.body);

  if (idempotencyKey) {
    processedRequests.set(idempotencyKey, order);
  }

  res.status(201).json(order);
});
```

--> A production implementation would use Redis with a TTL (rather than an in-memory `Map`, which is lost on server restart and doesn't work across multiple server instances behind a load balancer) -- directly connecting to the Redis Fundamentals file's caching patterns, applied here to deduplication instead of read-caching.

## Deep Dive -- Structuring Consistent Error Responses

--> A REST API should return errors in a CONSISTENT, predictable shape across every endpoint -- letting frontend code handle errors generically rather than needing custom parsing logic per endpoint.

```javascript
// A consistent error response shape, used everywhere via the centralized error-handling middleware
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Email is required",
    "details": [{ "field": "email", "issue": "required" }]
  }
}
```

```javascript
class ApiError extends Error {
  constructor(statusCode, code, message, details = []) {
    super(message);
    this.statusCode = statusCode;
    this.code = code;
    this.details = details;
  }
}

app.use((err, req, res, next) => {
  const statusCode = err.statusCode || 500;
  res.status(statusCode).json({
    error: { code: err.code || "INTERNAL_ERROR", message: err.message, details: err.details || [] }
  });
});
```

--> This directly connects to the Logging Failures Deep Dive file's coverage of error handling in the Ethical Hacking track -- consistent, structured error responses (that don't leak internal details like stack traces or database error messages to the client, exactly the Security Misconfiguration concern covered there) are both a good API design practice AND a security practice simultaneously.
