// discount.js
// A small, pure function module -- the unit under test for the Testing
// Pyramid's base layer. No side effects, no dependencies: easy to test.

/**
 * Calculates the price after applying a percentage discount.
 * @param {number} price - original price, must be >= 0
 * @param {number} discountPercent - percentage to discount, 0-100
 * @returns {number} discounted price, rounded to 2 decimal places
 */
function calculateDiscount(price, discountPercent) {
  if (typeof price !== "number" || typeof discountPercent !== "number") {
    throw new TypeError("price and discountPercent must be numbers");
  }
  if (price < 0) {
    throw new RangeError("price cannot be negative");
  }
  if (discountPercent < 0 || discountPercent > 100) {
    throw new RangeError("discountPercent must be between 0 and 100");
  }

  const discounted = price - (price * discountPercent) / 100;
  return Math.round(discounted * 100) / 100;
}

/**
 * Applies a fixed set of known discount codes on top of calculateDiscount.
 * Used by cart.integration.test.js to demonstrate cross-module behavior.
 */
const DISCOUNT_CODES = {
  SAVE10: 10,
  SAVE20: 20,
  SAVE50: 50,
};

function applyDiscountCode(price, code) {
  const percent = DISCOUNT_CODES[code];
  if (percent === undefined) {
    throw new Error(`Unknown discount code: ${code}`);
  }
  return calculateDiscount(price, percent);
}

module.exports = { calculateDiscount, applyDiscountCode, DISCOUNT_CODES };
