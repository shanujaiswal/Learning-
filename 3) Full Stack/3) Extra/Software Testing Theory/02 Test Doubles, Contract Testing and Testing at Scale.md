# Beyond the Pyramid -- The Vocabulary and Practices That Make It Work

--> The previous file establishes WHAT proportion of tests to write and WHEN in the development cycle. This file fills in several pieces that file deliberately left unaddressed -- the precise vocabulary for the fake dependencies unit tests rely on, how contract testing fits into the pyramid's integration layer for a microservices system, an entire category of testing (non-functional) the pyramid's shape doesn't even attempt to capture, and the practical realities of running a large test suite over YEARS -- coverage metrics, flaky tests, mutation testing, regression strategy, and test data management.

# Test Doubles -- Mock vs Stub vs Spy vs Fake

--> The previous file's unit test examples wave at "mocked/stubbed out" dependencies without distinguishing between them -- in practice, these are four genuinely DIFFERENT tools, each suited to a different question a test needs answered, and using the wrong one is a common source of tests that are either too weak (missing real bugs) or too brittle (breaking on harmless refactors).

## Stub -- Provides Canned Answers, Nothing More

--> A stub is a fake dependency that returns a PREDETERMINED answer when called, and nothing more sophisticated -- it doesn't verify anything about HOW it was called, it simply stands in so the test doesn't need a real dependency.

```javascript
// A stub -- always returns the same canned response, regardless of input
const stubPaymentGateway = {
  charge: () => ({ status: "success", id: "ch_123" }),
};

test("order is marked paid when the payment gateway succeeds", () => {
  const order = placeOrder({ total: 50 }, stubPaymentGateway);
  expect(order.status).toBe("paid");
});
```

--> Used when the test's whole point is about the CODE UNDER TEST's own logic given a certain input -- here, "does `placeOrder` correctly mark an order paid when charging succeeds" -- and the specific behavior of the dependency itself is irrelevant beyond supplying that one canned answer.

## Mock -- Verifies HOW It Was Called

--> A mock is a fake dependency the test uses to VERIFY specific interactions actually happened -- the right method was called, with the right arguments, the right number of times -- making the test's assertion about BEHAVIOR/COMMUNICATION rather than about a returned value.

```javascript
// A mock -- the test asserts on how it was CALLED, not just what it returns
test("placing an order calls the payment gateway with the correct total", () => {
  const mockGateway = { charge: jest.fn().mockReturnValue({ status: "success" }) };

  placeOrder({ total: 50 }, mockGateway);

  expect(mockGateway.charge).toHaveBeenCalledWith(50);
  expect(mockGateway.charge).toHaveBeenCalledTimes(1);
});
```

--> Used when the interaction ITSELF is the behavior worth verifying -- e.g. confirming a welcome email was actually triggered exactly once, not confirming anything about the email service's own internal behavior. **The real risk with overusing mocks** -- a test asserting on exact call signatures becomes brittle, breaking on a harmless internal refactor (e.g. calling the same dependency with arguments in a different but equivalent order) that changed nothing a real user would notice -- a well-known criticism sometimes summarized as testing the IMPLEMENTATION rather than the BEHAVIOR.

## Spy -- A Real Object, With Recording Bolted On

--> A spy wraps a REAL implementation but additionally records how it was called, letting a test both exercise genuine behavior AND assert on specific calls -- a middle ground between a stub's "just return canned data" and a full mock's "replace it entirely with fake, controllable behavior."

```javascript
// A spy -- the REAL emailService.send still runs, but calls are also recorded
const sendSpy = jest.spyOn(emailService, "send");

notifyUserOfOrder(order);

expect(sendSpy).toHaveBeenCalledWith(order.userEmail, expect.stringContaining("shipped"));
sendSpy.mockRestore();   // Important -- restore the real implementation afterward
```

--> Useful specifically when you want the REAL code path to actually run (catching genuine bugs in it) while still confirming a specific call happened along the way -- less commonly needed than a plain stub or mock, but valuable when a dependency's real behavior matters AND its call pattern needs verifying simultaneously.

## Fake -- A Working, Simplified Implementation

--> A fake is a genuinely FUNCTIONING alternative implementation of a dependency -- simpler than the real one, but with real, working logic of its own, rather than canned answers or recorded calls.

```javascript
// A fake -- a genuinely working in-memory database, not just a canned answer
class FakeUserRepository {
  constructor() { this.users = new Map(); }
  save(user) { this.users.set(user.id, user); }
  findById(id) { return this.users.get(id) ?? null; }
}

test("saving then retrieving a user returns the same data", () => {
  const repo = new FakeUserRepository();
  repo.save({ id: 1, name: "Alice" });
  expect(repo.findById(1)).toEqual({ id: 1, name: "Alice" });
});
```

--> An in-memory fake database is the classic example -- it genuinely stores and retrieves data (unlike a stub, which would just return one fixed canned record regardless of what was saved), letting a test exercise realistic save-then-read behavior WITHOUT needing a real database connection, dramatically speeding up the test while still catching logic bugs a plain stub would miss entirely. Fakes require more upfront effort to build than a stub, but pay that cost back across every test that reuses them.

## Choosing Among the Four

--> **Stub** -- "I need this dependency to return something, and don't care about anything else." **Mock** -- "I need to verify a specific call/interaction actually happened." **Spy** -- "I need the real behavior to run, but ALSO want to verify a call happened." **Fake** -- "I need genuinely working, simplified behavior, reused across many tests, without the cost/flakiness of the real dependency." A healthy test suite uses all four, deliberately, rather than defaulting to "mock everything" out of habit -- overusing mocks specifically is the most common way a unit test suite ends up testing its own assumptions about a dependency's internals rather than genuine behavior.

# Contract Testing -- Where It Fits in the Pyramid

--> Directly picking up the API Design file's Contract Testing section and the Software Architecture files' microservices communication patterns -- contract testing (Pact and consumer-driven contracts) occupies a genuinely distinct slot in the testing pyramid that neither classic unit nor classic integration tests cover.

```
                    /\
                   /  \        E2E Tests
                  /----\
                 /Integr-\      <-- Contract tests sit HERE conceptually --
                /--ation--\        they verify cross-service behavior like
               /------------\      integration tests do, but run FAST and
              /  Unit Tests   \    in ISOLATION like unit tests do, because
             /__________________\  neither side needs the other actually running.
```

--> **Why contract tests aren't simply "an integration test"** -- a genuine integration test between two SEPARATE services would need both actually running together (or a shared staging environment), which is slow, flaky under real network conditions, and doesn't scale as the number of service pairs grows. A contract test achieves the SAME confidence -- "does the provider's real behavior still match what a consumer actually depends on" -- using the recorded pact file mechanism described in the API Design file, without ever needing both services simultaneously running.
--> **Why contract tests aren't simply "a unit test" either** -- a unit test only ever verifies code within one service's own boundary, in complete isolation; it structurally cannot catch "the payments service quietly renamed a response field the orders service depends on," because that bug lives entirely in the GAP between two services' code, not inside either one.
--> **The practical payoff for a microservices test strategy** -- contract tests let each team keep their OWN test suite fast and independent (no shared staging environment full of every other team's services required to run tests) while still catching the exact class of cross-service breaking change that would otherwise only surface in an expensive, slow, flaky full E2E environment, or worse, in production.

# Non-Functional Testing -- A Category the Pyramid Doesn't Capture

--> Everything in the previous file's pyramid (unit/integration/E2E) tests WHETHER the system produces the correct output for a given input -- functional correctness. Non-functional testing asks a genuinely different question: does the system behave acceptably under REALISTIC CONDITIONS (load, diverse users, adverse environments) even when its functional logic is entirely correct.

## Performance and Load Testing

--> Verifies the system meets acceptable response-time and throughput targets under realistic (and above-realistic, "stress") traffic volumes -- a feature can be functionally 100% correct in every unit/integration/E2E test and still be unacceptable in production if it takes 8 seconds to respond under normal peak load.

```javascript
// k6 -- a load testing tool, scripting realistic concurrent traffic against an endpoint
import http from "k6/http";
import { check } from "k6";

export const options = {
  vus: 200,          // 200 Virtual Users, simulating concurrent traffic
  duration: "30s",
};

export default function () {
  const res = http.get("https://staging.example.com/api/products");
  check(res, { "status is 200": (r) => r.status === 200, "fast enough": (r) => r.timings.duration < 300 });
}
```

--> **Load testing** -- confirms behavior under EXPECTED peak traffic. **Stress testing** -- deliberately pushes traffic BEYOND expected peak, to find the actual breaking point and confirm the system degrades gracefully (e.g. returning `503` and shedding load) rather than crashing outright or corrupting data. **Soak testing** -- runs a sustained, moderate load for an extended period (hours), specifically to catch slow resource leaks (memory growth, unclosed connections) that only become visible over TIME, not under any single short burst.

## Accessibility Testing

--> Verifies the application is genuinely usable by people using assistive technology (screen readers, keyboard-only navigation, high-contrast/zoomed displays) -- a category of correctness that ordinary functional tests, which typically simulate a mouse-driven, sighted user, never exercise at all.

```javascript
// axe-core -- automated accessibility rule-checking, integrated into an E2E test
const { injectAxe, checkA11y } = require("axe-playwright");

test("checkout page has no automatically-detectable accessibility violations", async ({ page }) => {
  await page.goto("/checkout");
  await injectAxe(page);
  await checkA11y(page);
});
```

--> **Why automated tools are necessary but not sufficient** -- axe-core-style tools reliably catch objective, rule-based violations (missing `alt` text, insufficient color contrast, missing form labels, incorrect ARIA attributes) automatically and cheaply, integrated directly into the existing E2E suite -- but genuinely judging whether a screen-reader user's actual EXPERIENCE navigating a complex flow is coherent and usable still requires real manual testing with assistive technology, which automated tools cannot substitute for.

# Test Coverage Metrics and Their Limits

--> Code coverage measures what PERCENTAGE of the codebase's lines/branches/functions are executed by the test suite -- a common, easily-generated number (e.g. via Jest's `--coverage` flag, or Istanbul/nyc) that's frequently over-relied on as a proxy for "how well-tested is this code."

```
Line coverage:     % of executable lines run by at least one test
Branch coverage:    % of if/else (and similar) branches taken by at least one test
                     -- a stricter measure than line coverage, since a line inside
                     an `if` can be "covered" while the `else` branch never runs
Function coverage:   % of functions called by at least one test
```

--> **The critical limitation, stated plainly** -- coverage measures whether a line EXECUTED during a test run, not whether the test actually ASSERTED anything meaningful about its result. A test that calls a function and checks nothing about its output achieves full coverage of that function while catching zero bugs.

```javascript
// 100% line coverage, ZERO actual verification -- a classic coverage-metric trap
test("calculateDiscount runs without throwing", () => {
  calculateDiscount(100, 20);   // executes the line -- coverage tool marks it "covered"
  // no expect() at all -- this test would pass even if the function returned garbage
});
```

--> **The practical takeaway** -- coverage percentage is a useful, cheap signal for finding COMPLETELY untested code (a genuinely valuable use -- "this entire error-handling branch has 0% coverage" is a real, actionable finding), but a high percentage is not proof of a well-tested codebase, and chasing a specific target number (a common but misguided team policy, "we require 90% coverage") can perversely incentivize exactly the shallow, assertion-free tests shown above, written purely to move the number rather than to catch real bugs. Mutation testing, covered next, is the more rigorous check on whether tests actually verify anything.

# Mutation Testing -- Testing the Tests Themselves

--> Mutation testing directly attacks coverage's blind spot -- rather than asking "did a test execute this line," it deliberately introduces small, artificial bugs ("mutants") into the code -- flipping a `>` to `>=`, changing a `+` to a `-`, deleting a line -- and checks whether the EXISTING test suite actually fails as a result. A test suite that still passes despite the introduced bug clearly wasn't actually verifying that logic, no matter how high its coverage number looked.

```
Original code:              if (price > 100) applyDiscount();
Mutant introduced:           if (price >= 100) applyDiscount();   // boundary condition flipped

If the existing test suite still passes with this mutant in place,
that's a "SURVIVED" mutant -- a real gap in verification the coverage
number alone would never have revealed (the line was still "covered"
either way).

If a test correctly fails against the mutant, it's a "KILLED" mutant --
proof that test genuinely verifies that specific piece of logic.
```

```bash
# Stryker -- a mutation testing tool for JavaScript
npx stryker run
# Reports a "mutation score" -- the % of introduced mutants actually KILLED by the
# existing test suite, a meaningfully stronger signal of real test quality than
# line/branch coverage alone
```

--> **The real cost** -- mutation testing is computationally expensive (the entire test suite reruns once per mutant, and a codebase can generate thousands of mutants), which is why it's typically run periodically or on CI for critical modules specifically, rather than on every single commit the way fast unit tests are -- a genuine trade-off between the depth of the signal and the cost of obtaining it.

# Flaky Test Management

--> A flaky test is one that sometimes PASSES and sometimes FAILS against the exact same, unchanged code -- a corrosive problem specifically because it erodes trust in the ENTIRE suite; once developers learn that a red CI run might just be "that flaky test again," they start reflexively re-running failed builds instead of investigating them, which can eventually let a genuine, real failure slip through unnoticed, mistaken for just another flake.

## Common Root Causes

--> **Timing/race conditions** -- a test asserts on something before an async operation has genuinely finished (a classic source in E2E tests specifically, echoing the previous file's note that E2E tests are inherently more brittle).
--> **Shared/leaking state** -- one test's data (a row inserted into a shared test database, a global variable mutated) leaks into and affects a LATER test's result, making pass/fail depend on execution ORDER, which can vary between runs.
--> **External dependencies** -- a test calling a real third-party API or relying on real network conditions inherits THAT dependency's own occasional unreliability, unrelated to anything the test is actually meant to verify.
--> **Non-deterministic test data** -- randomly generated test data that occasionally produces an edge case (e.g. a randomly generated string that happens to violate a length constraint) the test wasn't designed to handle.

## Managing It

```bash
# Quarantine -- explicitly mark a known-flaky test, excluding it from blocking CI
# while making its flakiness visible and TRACKED, rather than silently ignored
test.skip("known flaky -- tracked in JIRA-4821", () => { ... });
```

--> **Quarantine, don't ignore** -- a flaky test should be explicitly tagged/skipped with a TRACKED follow-up ticket, not simply deleted (losing whatever real coverage it did provide) or silently left in place to keep intermittently failing builds for no clear reason. **Root-cause, don't just retry** -- automatically retrying a failed test a few times before declaring it failed is a common CI-level mitigation, but treating it as the PERMANENT fix rather than a stopgap just hides the underlying race condition/state leak instead of resolving it -- exactly the kind of accumulating technical debt that eventually makes an entire suite untrustworthy.

# Regression Testing Strategy

--> A regression is a previously-working feature breaking as an unintended side effect of an UNRELATED change elsewhere in the codebase -- regression testing is the discipline of re-running previous tests after every change specifically to catch this, rather than only testing whatever feature the current change was actually about.
--> **Why the testing pyramid IS a regression-testing strategy, not a separate thing** -- the entire reason for maintaining a large, fast, automatically-rerun-on-every-commit unit/integration suite (rather than only writing tests once, for the feature being built at the time) is precisely to continuously catch regressions in code nobody is currently thinking about, automatically, on every future change.
--> **Prioritizing regression coverage as a system grows** -- exhaustively re-testing EVERY possible flow on every change doesn't scale; a practical strategy weights regression effort toward a system's most business-critical flows (checkout, authentication, payment -- the same flows the previous file singles out as worth a FEW carefully-chosen E2E tests) and toward areas with a history of frequently breaking, rather than spreading effort perfectly evenly across a codebase where some parts are far more failure-prone or business-critical than others.

# Test Data Management and Fixtures

--> Every test above needs realistic INPUT data to run against, and how that data is managed has a real effect on both test reliability and speed.

```javascript
// A fixture -- a reusable, known-good piece of test data, defined once, reused everywhere
const validUserFixture = {
  id: "user_1",
  email: "test@example.com",
  role: "customer",
  createdAt: "2024-01-01T00:00:00Z",
};

test("a customer can view their own order history", () => {
  const user = { ...validUserFixture };   // start from the known-good baseline, override only what THIS test cares about
  expect(canViewOrderHistory(user, user.id)).toBe(true);
});
```

--> **Fixtures** -- pre-defined, reusable test data objects, kept in one shared location specifically so every test isn't independently hand-rolling its own slightly-different "valid user" object -- both reducing duplication and, importantly, making it obvious at a glance what a genuinely VALID input actually looks like for the domain.
--> **Factories** -- a step up from static fixtures, a factory function GENERATES test data with sensible defaults, letting each individual test override only the specific fields it actually cares about, rather than needing many near-duplicate static fixture objects for every slightly different scenario.

```javascript
function createUser(overrides = {}) {
  return { id: "user_1", email: "test@example.com", role: "customer", ...overrides };
}

test("an admin can view any user's order history", () => {
  const admin = createUser({ role: "admin" });   // only the relevant field overridden
  expect(canViewOrderHistory(admin, "some_other_user_id")).toBe(true);
});
```

--> **Test database isolation** -- integration tests (the previous file's Supertest example) need a genuinely clean, known starting state for every run, typically achieved by wrapping each test in its own transaction that's rolled back afterward, or by fully resetting/reseeding a dedicated test database between runs -- without this discipline, tests that read/write a SHARED database become exactly the "leaking state" root cause of flakiness described above, since one test's leftover data silently changes what a later, supposedly-independent test actually sees.
--> **Never test against production data** -- beyond the obvious data-privacy risk (directly connecting to the data handling concerns in the Cyber Security track), real production data is never guaranteed to contain the SPECIFIC edge cases a test suite needs to reliably exercise (a null field, a boundary value, an unusual-but-valid combination) -- deliberately constructed fixtures/factories guarantee exactly the scenario each test intends to check, every single run, regardless of what happens to exist in any real dataset at any given time.
