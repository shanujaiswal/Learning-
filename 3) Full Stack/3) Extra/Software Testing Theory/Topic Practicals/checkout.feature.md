# Checkout Discount -- BDD Feature Spec

--> Gherkin-style Given-When-Then specification, matching the Theory file's BDD section. Written in near-plain-English so a non-technical stakeholder (product manager, QA) can read and confirm it accurately describes the intended business behavior BEFORE a developer implements the matching code. This represents the Testing Pyramid's TIP -- the full, business-critical user journey that a real E2E tool would eventually automate against a running app.

```gherkin
Feature: Checkout Discount

  As a customer
  I want a discount code to reduce my order total at checkout
  So that I pay the correct, discounted price before confirming my order

  Scenario: Applying a valid discount code at checkout
    Given a cart containing an item priced at $100
    When the customer applies the discount code "SAVE20"
    And the customer proceeds to checkout
    Then the cart total should be $80
    And the checkout confirmation should display "Discount applied: SAVE20"

  Scenario: Applying an invalid discount code at checkout
    Given a cart containing an item priced at $100
    When the customer applies the discount code "FAKECODE"
    Then the customer should see an error message "Unknown discount code: FAKECODE"
    And the cart total should remain $100

  Scenario: Checking out with no discount code applied
    Given a cart containing an item priced at $100
    When the customer proceeds to checkout without entering a discount code
    Then the cart total should be $100
```

## Mapping to Cucumber Step Definitions

--> Each `Given`/`When`/`Then` line above corresponds to one JavaScript step definition function, matched by Cucumber via a string/regex pattern with placeholders (`{int}`, `{string}`) that capture the values written in the feature file. This is the executable code living BEHIND the readable spec:

```javascript
// step_definitions/checkout.steps.js
const { Given, When, Then } = require("@cucumber/cucumber");
const assert = require("assert");
const { applyDiscountCode } = require("../discount");

let cart, total, errorMessage;

Given("a cart containing an item priced at ${int}", function (price) {
  cart = { subtotal: price, discountCode: null };
});

When("the customer applies the discount code {string}", function (code) {
  cart.discountCode = code;
});

When("the customer proceeds to checkout", function () {
  try {
    total = cart.discountCode
      ? applyDiscountCode(cart.subtotal, cart.discountCode)
      : cart.subtotal;
  } catch (err) {
    errorMessage = err.message;
  }
});

Then("the cart total should be ${int}", function (expectedTotal) {
  assert.strictEqual(total, expectedTotal);
});

Then("the customer should see an error message {string}", function (expectedMessage) {
  assert.strictEqual(errorMessage, expectedMessage);
});
```

--> One feature file, several `Scenario` blocks, and one shared set of step definitions -- Cucumber matches each Gherkin line to a step definition by pattern, regardless of which scenario it appears in, which is why `Given a cart containing an item priced at $100` only needs to be implemented ONCE even though it's reused across all three scenarios above.
