/**
 * 05-error-handling-and-custom-errors.js
 * HOW TO RUN: plain Node.js -> `node 05-error-handling-and-custom-errors.js`
 * (No DOM APIs used. Also runs fine pasted into a browser console.)
 *
 * Covers (Theory folder):
 *  - Chapter 13: Advanced Data Types / Error Handling
 *
 * Demonstrates:
 *  1. Custom Error subclasses with extra structured data.
 *  2. try/catch/finally patterns (including re-throwing and cleanup).
 *  3. A retry-with-exponential-backoff wrapper function.
 */

"use strict";

// ===========================================================================
// PART 1: Custom Error subclasses.
// Each carries semantic meaning + extra structured data beyond a plain message.
// ===========================================================================
class ValidationError extends Error {
  constructor(message, { field } = {}) {
    super(message);
    this.name = "ValidationError";
    this.field = field;
  }
}

class NotFoundError extends Error {
  constructor(message, { resourceId } = {}) {
    super(message);
    this.name = "NotFoundError";
    this.resourceId = resourceId;
  }
}

class NetworkError extends Error {
  constructor(message, { statusCode, cause } = {}) {
    super(message, cause ? { cause } : undefined); // ES2022 error cause chaining
    this.name = "NetworkError";
    this.statusCode = statusCode;
  }
}

function validateUser(user) {
  if (!user?.email?.includes("@")) {
    throw new ValidationError("Email must contain '@'", { field: "email" });
  }
  if (!user?.age || user.age < 0) {
    throw new ValidationError("Age must be a non-negative number", { field: "age" });
  }
  return true;
}

console.log("=== Custom Error subclasses ===");
const testUsers = [
  { email: "bad-email", age: 30 },
  { email: "good@example.com", age: -5 },
  { email: "good@example.com", age: 30 },
];

for (const user of testUsers) {
  try {
    validateUser(user);
    console.log(`VALID user: ${JSON.stringify(user)}`);
  } catch (err) {
    if (err instanceof ValidationError) {
      console.log(`INVALID user ${JSON.stringify(user)} -> [${err.name}] field="${err.field}": ${err.message}`);
    } else {
      throw err; // unknown error type - don't swallow it
    }
  }
}

// ===========================================================================
// PART 2: try/catch/finally patterns.
// Shows: catching a specific error type, re-throwing an unrecognised one,
// and guaranteed cleanup via finally regardless of success/failure.
// ===========================================================================
console.log("\n=== try/catch/finally patterns ===");

function findResource(db, id) {
  const found = db.find((item) => item.id === id);
  if (!found) throw new NotFoundError(`Resource ${id} was not found`, { resourceId: id });
  return found;
}

function lookupWithCleanup(db, id) {
  console.log(`[lookup] opening "connection" for id=${id}`);
  try {
    const resource = findResource(db, id);
    console.log(`[lookup] SUCCESS: found ${JSON.stringify(resource)}`);
    return resource;
  } catch (err) {
    if (err instanceof NotFoundError) {
      console.log(`[lookup] handled gracefully: ${err.message} (resourceId=${err.resourceId})`);
      return null;
    }
    // Not a NotFoundError - we don't know how to handle it here, so re-throw
    // and let a higher-level caller deal with it.
    console.log("[lookup] unrecognised error - re-throwing");
    throw err;
  } finally {
    // finally ALWAYS runs, whether we returned normally or threw/re-threw.
    console.log(`[lookup] closing "connection" for id=${id}\n`);
  }
}

const database = [{ id: 1, name: "Widget" }];
lookupWithCleanup(database, 1);
lookupWithCleanup(database, 999);

// ===========================================================================
// PART 3: Retry-with-exponential-backoff wrapper.
// Wraps any async function so that transient failures are retried a fixed
// number of times, waiting longer between each attempt (2^attempt * baseMs).
// ===========================================================================
function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function withRetry(asyncFn, { maxAttempts = 4, baseDelayMs = 100 } = {}) {
  return async function retrying(...args) {
    let lastError;
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        const result = await asyncFn(...args);
        if (attempt > 1) {
          console.log(`[retry] succeeded on attempt ${attempt}/${maxAttempts}`);
        }
        return result;
      } catch (err) {
        lastError = err;
        const backoffMs = baseDelayMs * 2 ** (attempt - 1);
        console.log(
          `[retry] attempt ${attempt}/${maxAttempts} failed (${err.message}). ` +
            `${attempt < maxAttempts ? `Retrying in ${backoffMs}ms...` : "No attempts left."}`
        );
        if (attempt < maxAttempts) await wait(backoffMs);
      }
    }
    throw new NetworkError(`All ${maxAttempts} attempts failed`, { cause: lastError });
  };
}

// Simulates a flaky network call that fails the first N times, then succeeds.
function makeFlakyCall(failuresBeforeSuccess) {
  let attempts = 0;
  return async function flakyCall() {
    attempts++;
    if (attempts <= failuresBeforeSuccess) {
      throw new Error(`simulated transient failure #${attempts}`);
    }
    return `data fetched successfully on internal attempt #${attempts}`;
  };
}

async function runRetryDemo() {
  console.log("=== Retry-with-backoff demo (succeeds on the 3rd attempt) ===");
  const flakyCall = makeFlakyCall(2); // fails twice, then succeeds
  const reliableCall = withRetry(flakyCall, { maxAttempts: 4, baseDelayMs: 50 });

  try {
    const result = await reliableCall();
    console.log(`FINAL RESULT: ${result}`);
  } catch (err) {
    console.log(`FINAL FAILURE after retries: ${err.message}`);
  }

  console.log("\n=== Retry-with-backoff demo (exhausts all attempts and fails) ===");
  const alwaysFailingCall = makeFlakyCall(Infinity); // never succeeds
  const doomedCall = withRetry(alwaysFailingCall, { maxAttempts: 3, baseDelayMs: 50 });

  try {
    await doomedCall();
  } catch (err) {
    console.log(`FINAL FAILURE (expected): [${err.name}] ${err.message}`);
    console.log(`  underlying cause: ${err.cause?.message}`);
  }
}

runRetryDemo();
