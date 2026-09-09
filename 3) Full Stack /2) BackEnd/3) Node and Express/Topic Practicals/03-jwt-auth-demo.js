/**
 * 03-jwt-auth-demo.js
 *
 * Covers: 06 Authentication JWT Sessions and Passport
 *
 * A minimal, working JWT auth flow:
 *   POST /login              -> verifies credentials, issues a signed JWT
 *   GET  /protected/profile  -> requires a valid JWT via authenticateToken middleware
 *
 * Run: node 03-jwt-auth-demo.js
 *
 * Try it end-to-end:
 *   curl -X POST http://localhost:3003/login -H "Content-Type: application/json" \
 *     -d '{"username":"alice","password":"password123"}'
 *   # -> { "token": "<JWT>" }
 *
 *   curl http://localhost:3003/protected/profile \
 *     -H "Authorization: Bearer <JWT>"
 *   # -> { "message": "Welcome alice", "user": { "username": "alice" } }
 *
 * Deliberately-wrong-token test case (expected 403):
 *   curl http://localhost:3003/protected/profile \
 *     -H "Authorization: Bearer this.is.not.a.valid.jwt"
 *   # -> HTTP 403 { "error": "Invalid or expired token" }
 *
 * Also expected 403/401 without a token at all:
 *   curl http://localhost:3003/protected/profile
 *   # -> HTTP 401 { "error": "Authorization token required" }
 */

const express = require('express');
const jwt = require('jsonwebtoken');

const app = express();
app.use(express.json());

// In real projects this MUST come from an environment variable / secret store.
const JWT_SECRET = process.env.JWT_SECRET || 'dev-only-secret-do-not-use-in-prod';

// Fake user "database" -- demo only, never store plaintext passwords in real apps.
const USERS = [
  { username: 'alice', password: 'password123' },
  { username: 'bob', password: 'hunter2' },
];

// ---------------------------------------------------------------------------
// POST /login -- verify credentials, issue a JWT valid for 1 hour.
// ---------------------------------------------------------------------------
app.post('/login', (req, res) => {
  const { username, password } = req.body || {};

  if (!username || !password) {
    return res.status(400).json({ error: 'username and password are required' });
  }

  const user = USERS.find((u) => u.username === username && u.password === password);
  if (!user) {
    return res.status(401).json({ error: 'Invalid username or password' });
  }

  const token = jwt.sign({ username: user.username }, JWT_SECRET, { expiresIn: '1h' });
  res.status(200).json({ token });
});

// ---------------------------------------------------------------------------
// authenticateToken middleware -- verifies the Bearer JWT on protected routes.
// ---------------------------------------------------------------------------
function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1]; // "Bearer <token>"

  if (!token) {
    return res.status(401).json({ error: 'Authorization token required' });
  }

  jwt.verify(token, JWT_SECRET, (err, decoded) => {
    if (err) {
      // Wrong/expired/tampered token -> 403, per standard JWT middleware convention.
      return res.status(403).json({ error: 'Invalid or expired token' });
    }
    req.user = decoded;
    next();
  });
}

// ---------------------------------------------------------------------------
// Protected routes
// ---------------------------------------------------------------------------
app.get('/protected/profile', authenticateToken, (req, res) => {
  res.status(200).json({
    message: `Welcome ${req.user.username}`,
    user: { username: req.user.username },
  });
});

const PORT = process.env.PORT || 3003;
if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`[03] JWT auth demo listening on http://localhost:${PORT}`);
  });
}

module.exports = { app, authenticateToken };
