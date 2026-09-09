# Software Testing Theory Practical -- Index

--> A small, real, correct Jest example project demonstrating the Testing Pyramid concretely: a unit-tested pure function (base), an integration test across two modules (middle), and a Gherkin-style BDD feature spec (matching the Theory file's Given-When-Then section) representing what an E2E/acceptance test would verify.

## How to Run

```bash
npm install jest --save-dev
npx jest
```

--> All test files in this folder end in `.test.js`, which is Jest's default discovery pattern -- no config file is required to run them.

## Files

1. **`discount.js`** -- Pure function module (`calculateDiscount`) that is the unit under test -- the pyramid's base.
2. **`discount.test.js`** -- Unit tests for `discount.js`, written with visible TDD Red-Green-Refactor comments showing the failing test that existed before the implementation.
3. **`cart.integration.test.js`** -- Integration-style test exercising a `Cart` module and the real (unmocked) `discount.js` module together -- the pyramid's middle tier.
4. **`checkout.feature.md`** -- Gherkin-style Given-When-Then BDD feature spec for a checkout discount scenario, with a note on mapping it to Cucumber step definitions -- representing the pyramid's tip (the acceptance-level behavior an E2E test would ultimately automate).

## Why This Maps to the Pyramid

--> `discount.test.js` alone could have dozens of near-instant unit tests (the wide base). `cart.integration.test.js` represents a smaller number of slower tests checking real component interaction (the middle). `checkout.feature.md` describes the single, business-critical, full user journey (the narrow tip) that a real E2E tool (Playwright/Cucumber) would eventually automate against a running app.
