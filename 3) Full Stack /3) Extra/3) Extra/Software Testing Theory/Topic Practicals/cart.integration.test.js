// cart.integration.test.js
// Integration-style test -- the Testing Pyramid's MIDDLE layer.
//
// Unlike discount.test.js (which tests calculateDiscount completely alone),
// this file exercises TWO real modules together -- an in-memory Cart module
// and the REAL (unmocked) discount.js -- to verify they interact correctly.
// No mocking of discount.js happens here on purpose: mocking it would turn
// this back into a unit test and defeat the point of an integration test,
// which is to catch bugs that only appear when components are wired together.

// --- The "Cart" module under test (small enough to inline here; in a real
// project this would live in its own cart.js file and be require()'d) -------
const { applyDiscountCode } = require("./discount");

class Cart {
  constructor() {
    this.items = [];
    this.appliedCode = null;
  }

  addItem(item) {
    if (typeof item.price !== "number" || item.price < 0) {
      throw new RangeError("item price must be a non-negative number");
    }
    this.items.push(item);
  }

  applyDiscountCode(code) {
    this.appliedCode = code;
  }

  get subtotal() {
    return this.items.reduce((sum, item) => sum + item.price, 0);
  }

  get total() {
    if (!this.appliedCode) {
      return Math.round(this.subtotal * 100) / 100;
    }
    // Real call into discount.js -- this is the "integration" being tested.
    return applyDiscountCode(this.subtotal, this.appliedCode);
  }
}

// -----------------------------------------------------------------------------
// Integration tests: Cart + discount.js working together
// -----------------------------------------------------------------------------

describe("Cart + discount integration", () => {
  test("total equals subtotal when no discount code is applied", () => {
    const cart = new Cart();
    cart.addItem({ name: "Widget", price: 100 });

    expect(cart.total).toBe(100);
  });

  test("applying SAVE20 reduces the real subtotal via the real discount module", () => {
    const cart = new Cart();
    cart.addItem({ name: "Widget", price: 100 });

    cart.applyDiscountCode("SAVE20");

    // If Cart's plumbing to discount.js were wired incorrectly (wrong argument
    // order, wrong field name, etc.), this is the level of test that would
    // catch it -- a pure unit test of discount.js alone never could, since it
    // never calls through Cart at all.
    expect(cart.total).toBe(80);
  });

  test("total is the sum of multiple items before an applied discount", () => {
    const cart = new Cart();
    cart.addItem({ name: "Widget", price: 100 });
    cart.addItem({ name: "Gadget", price: 50 });

    cart.applyDiscountCode("SAVE10");

    expect(cart.subtotal).toBe(150);
    expect(cart.total).toBe(135); // 150 - 10%
  });

  test("propagates a real error from discount.js for an unknown code", () => {
    const cart = new Cart();
    cart.addItem({ name: "Widget", price: 100 });
    cart.applyDiscountCode("BOGUS");

    // discount.js throws; Cart does not swallow it -- confirming the two
    // modules' error-handling contract actually holds when wired together.
    expect(() => cart.total).toThrow("Unknown discount code: BOGUS");
  });

  test("rejects an invalid item at the Cart level before discount logic runs", () => {
    const cart = new Cart();
    expect(() => cart.addItem({ name: "Broken", price: -5 })).toThrow(RangeError);
  });
});
