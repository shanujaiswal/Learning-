// discount.test.js
// Unit tests for discount.js -- the Testing Pyramid's BASE layer.
// Fast, isolated, no real dependencies (this module has none to mock).
//
// Written following the TDD Red-Green-Refactor cycle. The comments below
// show, for teaching purposes, what the FIRST test looked like when it was
// written BEFORE calculateDiscount existed at all, and how it evolved.

const { calculateDiscount, applyDiscountCode } = require("./discount");

// -----------------------------------------------------------------------------
// TDD HISTORY (for teaching purposes only -- this is what actually happened,
// commit by commit, before the final test suite below was reached)
// -----------------------------------------------------------------------------
//
// --- RED --------------------------------------------------------------------
// The very first test written, BEFORE discount.js existed:
//
//   const { calculateDiscount } = require("./discount"); // <- file didn't exist yet
//
//   test("calculates a 20% discount correctly", () => {
//     expect(calculateDiscount(100, 20)).toBe(80);
//   });
//
// Running `npx jest` at this point failed immediately:
//   Cannot find module './discount' from 'discount.test.js'
// This is the RED step -- a failing test for behavior that doesn't exist yet.
//
// --- GREEN -------------------------------------------------------------------
// The simplest implementation that could possibly make it pass was written:
//
//   function calculateDiscount(price, percent) {
//     return price - (price * percent / 100);
//   }
//
// Re-running `npx jest` -- the test now PASSES. This is the GREEN step.
//
// --- REFACTOR ----------------------------------------------------------------
// With a passing test as a safety net, the implementation was hardened:
// input validation, rounding to 2 decimals, and JSDoc were added (see the
// final discount.js). The ORIGINAL test kept passing throughout every change,
// confirming the refactor never broke the documented behavior. Additional
// tests below were then added the same way -- write the failing assertion
// first, then extend the implementation to satisfy it.
// -----------------------------------------------------------------------------

describe("calculateDiscount", () => {
  test("calculates a 20% discount correctly", () => {
    expect(calculateDiscount(100, 20)).toBe(80);
  });

  test("returns the original price when discount is 0%", () => {
    expect(calculateDiscount(50, 0)).toBe(50);
  });

  test("returns 0 when discount is 100%", () => {
    expect(calculateDiscount(50, 100)).toBe(0);
  });

  test("rounds to 2 decimal places", () => {
    expect(calculateDiscount(19.99, 33)).toBe(13.39);
  });

  test("throws a RangeError for a negative price", () => {
    expect(() => calculateDiscount(-10, 20)).toThrow(RangeError);
  });

  test("throws a RangeError for a discount percent above 100", () => {
    expect(() => calculateDiscount(100, 150)).toThrow(RangeError);
  });

  test("throws a TypeError when price is not a number", () => {
    expect(() => calculateDiscount("100", 20)).toThrow(TypeError);
  });
});

describe("applyDiscountCode", () => {
  test("applies the SAVE20 code correctly", () => {
    expect(applyDiscountCode(100, "SAVE20")).toBe(80);
  });

  test("throws a descriptive error for an unknown code", () => {
    expect(() => applyDiscountCode(100, "NOTACODE")).toThrow(
      "Unknown discount code: NOTACODE"
    );
  });
});
