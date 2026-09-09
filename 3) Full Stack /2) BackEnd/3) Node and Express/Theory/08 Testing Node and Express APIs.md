# Testing an Express API -- What's Different From Frontend Testing

--> There's no UI to click through -- API tests send actual HTTP requests to Express routes/middleware and assert on the response (status code, body, headers), the same fundamental idea as the API Testing file in the Testing folder, but written as code integrated into the project's own test suite rather than a separate tool like Postman.

# Jest and Supertest -- The Standard Combo

--> Jest provides the test runner/assertions (`describe`, `test`, `expect`) -- the same tool used for frontend testing. Supertest wraps an Express app to let you send requests to it directly in-process, without needing an actual running server/port.

```javascript
const request = require("supertest");
const app = require("../app");   // The Express app, NOT app.listen()'d yet

describe("GET /api/users/:id", () => {
  test("returns a user for a valid ID", async () => {
    const response = await request(app).get("/api/users/1");

    expect(response.status).toBe(200);
    expect(response.body).toHaveProperty("name");
  });

  test("returns 404 for a nonexistent user", async () => {
    const response = await request(app).get("/api/users/99999");
    expect(response.status).toBe(404);
  });
});
```

--> Testing against the app instance directly (not a running server) makes tests fast -- no real network overhead, no port conflicts between parallel test runs.

# Testing POST/PUT Requests With a Body

```javascript
test("creates a new user", async () => {
  const response = await request(app)
    .post("/api/users")
    .send({ name: "Alice", email: "alice@example.com" })
    .set("Content-Type", "application/json");

  expect(response.status).toBe(201);
  expect(response.body.name).toBe("Alice");
});
```

# Testing Authenticated Routes

```javascript
test("rejects requests without a valid token", async () => {
  const response = await request(app).get("/api/profile");
  expect(response.status).toBe(401);
});

test("allows access with a valid token", async () => {
  const token = generateTestToken({ userId: 1 });
  const response = await request(app)
    .get("/api/profile")
    .set("Authorization", `Bearer ${token}`);

  expect(response.status).toBe(200);
});
```

# Isolating Tests From a Real Database

--> Hitting a real production/shared database in tests is slow and makes tests interfere with each other -- common approaches: a dedicated test database that's reset between test runs, an in-memory database (e.g. `mongodb-memory-server` for MongoDB), or mocking the database layer entirely for pure unit tests of route logic.

```javascript
beforeEach(async () => {
  await db.query("DELETE FROM users");   // Reset state before each test for isolation
});
```

# Mocking External Services

--> Routes that call third-party APIs (payment providers, email services) shouldn't actually call them during tests -- `jest.mock()` replaces the real module with a controllable fake, so tests can verify behavior for both success AND failure responses from that dependency, deterministically.

```javascript
jest.mock("../services/emailService");
const emailService = require("../services/emailService");

test("sends a welcome email on signup", async () => {
  emailService.sendWelcomeEmail.mockResolvedValue(true);

  await request(app).post("/api/signup").send({ email: "new@example.com" });

  expect(emailService.sendWelcomeEmail).toHaveBeenCalledWith("new@example.com");
});
```

# Test Structure -- Unit vs Integration for APIs

--> Unit tests -- test individual functions/middleware in isolation (a validation function, a data-transform helper), mocking everything around them.
--> Integration tests (what Supertest examples above mostly show) -- test a full request going through routing, middleware, and (a test) database together, verifying the pieces actually work correctly as a whole, closer to how the API behaves in production.
