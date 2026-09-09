# Two Fundamental Approaches -- Sessions vs Tokens

--> Session-based auth -- the server creates a session on login, stores it (in memory, a database, or Redis), and gives the client a session ID (usually via a cookie) -- the server looks up that ID on every request to know who's making it. Stateful -- the server must track active sessions.
--> Token-based auth (JWT) -- the server issues a signed token containing the user's identity/claims directly -- the server verifies the SIGNATURE on each request instead of looking anything up in storage. Stateless -- no server-side session store needed, which scales more easily across multiple servers.

# Session-Based Auth with express-session

```javascript
const session = require("express-session");

app.use(session({
  secret: process.env.SESSION_SECRET,
  resave: false,
  saveUninitialized: false,
  cookie: { secure: true, httpOnly: true, maxAge: 1000 * 60 * 60 }   // 1 hour
}));

app.post("/login", (req, res) => {
  const user = authenticateUser(req.body.username, req.body.password);
  req.session.userId = user.id;   // Stored server-side, tied to the session cookie
  res.json({ message: "Logged in" });
});

app.get("/profile", (req, res) => {
  if (!req.session.userId) return res.status(401).json({ error: "Not authenticated" });
  res.json({ userId: req.session.userId });
});
```

--> `httpOnly: true` -- prevents JavaScript from reading the cookie (mitigates XSS-based token theft). `secure: true` -- only sends the cookie over HTTPS.

# JWT -- JSON Web Tokens

--> A JWT has three parts (`header.payload.signature`) -- the payload holds claims (user ID, role, expiry) as plain (NOT encrypted, just base64-encoded) JSON, and the signature proves the token wasn't tampered with since the server issued it.

```javascript
const jwt = require("jsonwebtoken");

app.post("/login", (req, res) => {
  const user = authenticateUser(req.body.username, req.body.password);
  const token = jwt.sign(
    { userId: user.id, role: user.role },
    process.env.JWT_SECRET,
    { expiresIn: "1h" }
  );
  res.json({ token });
});

function authenticateToken(req, res, next) {
  const token = req.headers.authorization?.split(" ")[1];   // "Bearer <token>"
  if (!token) return res.status(401).json({ error: "No token provided" });

  jwt.verify(token, process.env.JWT_SECRET, (err, decoded) => {
    if (err) return res.status(403).json({ error: "Invalid or expired token" });
    req.user = decoded;
    next();
  });
}

app.get("/profile", authenticateToken, (req, res) => {
  res.json({ userId: req.user.userId });
});
```

--> Because a JWT's payload is only encoded, not encrypted, NEVER put sensitive data (passwords, secrets) in it -- anyone with the token can decode and read the payload; only the signature stops them from MODIFYING it undetected.

# Access Tokens and Refresh Tokens

--> A short-lived Access Token (minutes to an hour) minimizes the damage window if it's ever stolen -- but forcing a full re-login that often is bad UX.
--> A long-lived Refresh Token, stored more securely (httpOnly cookie), is used only to silently obtain a new Access Token when the old one expires, without requiring the user to log in again.

# Passport.js -- A Pluggable Authentication Middleware

--> Passport provides a consistent middleware interface across many different authentication "strategies" (local username/password, Google OAuth, GitHub OAuth, JWT) -- so switching or supporting multiple login methods doesn't mean rewriting auth logic from scratch each time.

```javascript
const passport = require("passport");
const LocalStrategy = require("passport-local").Strategy;

passport.use(new LocalStrategy((username, password, done) => {
  const user = findUserByUsername(username);
  if (!user || !checkPassword(password, user.passwordHash)) {
    return done(null, false, { message: "Invalid credentials" });
  }
  return done(null, user);
}));

app.post("/login", passport.authenticate("local"), (req, res) => {
  res.json({ message: "Logged in", user: req.user });
});
```

# Password Storage Reminder

--> Never store plaintext passwords -- hash with bcrypt/Argon2 (covered in depth in the Security folder's Cryptography track) before storing, and compare hashes on login rather than comparing raw passwords.
