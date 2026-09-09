/**
 * 02-middleware-chain.js
 *
 * Covers: 04 Express Middleware Architecture
 *
 * Demonstrates:
 *   - Request logging middleware (applied globally)
 *   - A simple in-memory rate limiter (applied globally)
 *   - Route-specific middleware (applied only to one route)
 *   - Centralized error-handling middleware, reached via next(err)
 *
 * Run: node 02-middleware-chain.js
 * Try:
 *   curl http://localhost:3002/
 *   curl http://localhost:3002/admin        (route-specific middleware demo)
 *   curl http://localhost:3002/boom         (triggers centralized error handler)
 *   for i in 1 2 3 4 5 6; do curl http://localhost:3002/; done   (rate limit demo)
 */

const express = require('express');
const app = express();

app.use(express.json());

// ---------------------------------------------------------------------------
// 1) Request logging middleware (global) -- runs on every request.
// ---------------------------------------------------------------------------
function requestLogger(req, res, next) {
  const start = Date.now();
  res.on('finish', () => {
    const ms = Date.now() - start;
    console.log(`[LOG] ${req.method} ${req.originalUrl} -> ${res.statusCode} (${ms}ms)`);
  });
  next();
}
app.use(requestLogger);

// ---------------------------------------------------------------------------
// 2) Simple in-memory rate limiter (global) -- N requests per IP per window.
// ---------------------------------------------------------------------------
function createRateLimiter({ windowMs = 60_000, max = 5 } = {}) {
  const hits = new Map(); // ip -> { count, windowStart }

  return function rateLimiter(req, res, next) {
    const ip = req.ip;
    const now = Date.now();
    const entry = hits.get(ip);

    if (!entry || now - entry.windowStart > windowMs) {
      hits.set(ip, { count: 1, windowStart: now });
      return next();
    }

    entry.count += 1;
    if (entry.count > max) {
      res.set('Retry-After', String(Math.ceil((entry.windowStart + windowMs - now) / 1000)));
      return res.status(429).json({ error: 'Too many requests, slow down.' });
    }
    next();
  };
}
app.use(createRateLimiter({ windowMs: 60_000, max: 5 }));

// ---------------------------------------------------------------------------
// 3) Route-specific middleware -- only applied to /admin, not globally.
// ---------------------------------------------------------------------------
function requireAdminHeader(req, res, next) {
  if (req.headers['x-admin-token'] !== 'let-me-in') {
    // Delegate to centralized error handler instead of responding directly.
    return next(Object.assign(new Error('Missing or invalid x-admin-token header'), { status: 403 }));
  }
  next();
}

app.get('/', (req, res) => {
  res.status(200).json({ message: 'Public route -- logging + rate limiting applied' });
});

// requireAdminHeader is wired in ONLY for this route, not app-wide.
app.get('/admin', requireAdminHeader, (req, res) => {
  res.status(200).json({ message: 'Welcome, admin.' });
});

// ---------------------------------------------------------------------------
// 4) Route that deliberately throws, to demonstrate next(err) propagation.
// ---------------------------------------------------------------------------
app.get('/boom', (req, res, next) => {
  try {
    throw new Error('Something went wrong on purpose');
  } catch (err) {
    next(err); // hands off to the centralized error handler below
  }
});

// 404 fallback for unmatched routes
app.use((req, res) => {
  res.status(404).json({ error: 'Route not found' });
});

// ---------------------------------------------------------------------------
// Centralized error-handling middleware -- MUST be defined last, with 4 args.
// Any next(err) call anywhere above ends up here.
// ---------------------------------------------------------------------------
app.use((err, req, res, next) => {
  console.error('[ERROR HANDLER]', err.message);
  const status = err.status || 500;
  res.status(status).json({
    error: err.message || 'Internal server error',
  });
});

const PORT = process.env.PORT || 3002;
if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`[02] Middleware chain demo listening on http://localhost:${PORT}`);
  });
}

module.exports = app;
