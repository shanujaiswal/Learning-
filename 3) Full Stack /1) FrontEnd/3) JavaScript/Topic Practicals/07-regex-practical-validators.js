/**
 * 07-regex-practical-validators.js
 * HOW TO RUN: plain Node.js -> `node 07-regex-practical-validators.js`
 * (No DOM APIs used. Also runs fine pasted into a browser console.)
 *
 * Covers (Theory folder):
 *  - Chapter 12: Regular Expressions
 *
 * Demonstrates real, usable validators (email, phone, password strength)
 * each exercised with pass/fail test cases, plus a dedicated
 * lookahead/lookbehind example.
 */

"use strict";

function runTestCases(title, testFn, cases) {
  console.log(`\n=== ${title} ===`);
  cases.forEach(({ input, expected }) => {
    const actual = testFn(input);
    const verdict = actual === expected ? "PASS" : "FAIL (unexpected!)";
    console.log(`[${verdict}] testFn(${JSON.stringify(input)}) -> ${actual} (expected ${expected})`);
  });
}

// ===========================================================================
// 1) Email validator.
// Pragmatic pattern: local-part @ domain . tld, no spaces, at least one dot
// in the domain, 2+ letter TLD. (Real RFC 5322 emails are far messier - this
// covers the common real-world cases you'd actually want to accept/reject.)
// ===========================================================================
const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;

function isValidEmail(email) {
  return EMAIL_REGEX.test(email);
}

runTestCases("Email validator", isValidEmail, [
  { input: "vanisha@warpx.ai", expected: true },
  { input: "first.last+tag@sub.example.com", expected: true },
  { input: "not-an-email", expected: false },
  { input: "missing@domain", expected: false },
  { input: "spaced out@example.com", expected: false },
  { input: "@example.com", expected: false },
]);

// ===========================================================================
// 2) Phone number validator.
// Accepts optional country code, and separators of space/dash/dot, for a
// 10-digit number, e.g. "+91 98765 43210", "987-654-3210", "9876543210".
// ===========================================================================
const PHONE_REGEX = /^(\+\d{1,3}[\s.-]?)?(\d{3,5}[\s.-]?){2,3}\d{2,4}$/;

// Simpler, stricter check used alongside the flexible one: exactly 10 digits
// once all separators are stripped (a common real-world rule).
function isValidPhone(phone) {
  const digitsOnly = phone.replace(/[\s.-]/g, "").replace(/^\+\d{1,3}/, "");
  return PHONE_REGEX.test(phone) && /^\d{10}$/.test(digitsOnly);
}

runTestCases("Phone validator", isValidPhone, [
  { input: "9876543210", expected: true },
  { input: "987-654-3210", expected: true },
  { input: "+91 98765 43210", expected: true },
  { input: "123", expected: false },
  { input: "98765-4321", expected: false }, // only 9 digits
  { input: "abcdefghij", expected: false },
]);

// ===========================================================================
// 3) Password strength validator.
// Requires: 8+ chars, at least one lowercase, one uppercase, one digit, and
// one special character. Each rule is checked with its own lookahead so we
// can also report WHICH rule failed, not just pass/fail.
// ===========================================================================
const PASSWORD_RULES = [
  { name: "min length 8", test: (pw) => pw.length >= 8 },
  { name: "has lowercase", test: (pw) => /(?=.*[a-z])/.test(pw) },
  { name: "has uppercase", test: (pw) => /(?=.*[A-Z])/.test(pw) },
  { name: "has digit", test: (pw) => /(?=.*\d)/.test(pw) },
  { name: "has special char", test: (pw) => /(?=.*[!@#$%^&*])/.test(pw) },
];

// Combined single regex using lookaheads (all conditions must hold from position 0).
const STRONG_PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*]).{8,}$/;

function isStrongPassword(password) {
  return STRONG_PASSWORD_REGEX.test(password);
}

function describePasswordStrength(password) {
  const failedRules = PASSWORD_RULES.filter((rule) => !rule.test(password)).map((rule) => rule.name);
  return failedRules.length === 0 ? "STRONG" : `WEAK (missing: ${failedRules.join(", ")})`;
}

runTestCases("Password strength validator (regex-only pass/fail)", isStrongPassword, [
  { input: "Str0ng!Pass", expected: true },
  { input: "weakpass", expected: false },
  { input: "NoDigits!", expected: false },
  { input: "nouppercase1!", expected: false },
  { input: "NOLOWERCASE1!", expected: false },
  { input: "NoSpecial123", expected: false },
]);

console.log("\n=== Password strength validator (detailed breakdown) ===");
["Str0ng!Pass", "weakpass", "N0Special"].forEach((pw) => {
  console.log(`"${pw}" -> ${describePasswordStrength(pw)}`);
});

// ===========================================================================
// 4) Lookahead / lookbehind example.
// Task: extract the numeric amount from price strings ONLY when preceded by
// a currency symbol (lookbehind), and reject amounts followed by "% off"
// (negative lookahead) since those are discounts, not prices.
// ===========================================================================
console.log("\n=== Lookahead / lookbehind example ===");

// (?<=[$₹]) -> positive lookbehind: must be preceded by $ or ₹ (not captured)
// (?!%)      -> negative lookahead applied after the number via a second pass below
const PRICE_WITH_LOOKBEHIND_REGEX = /(?<=[$₹])(\d+(?:\.\d{1,2})?)/g;

const priceStrings = [
  "Price: $49.99, was $79.99",
  "Discount: 20% off marked items",
  "Total in rupees: ₹1500",
  "Just a number: 100 (no currency symbol, should NOT match)",
];

priceStrings.forEach((str) => {
  const matches = [...str.matchAll(PRICE_WITH_LOOKBEHIND_REGEX)].map((m) => m[0]);
  console.log(`"${str}"\n  -> extracted amounts (lookbehind for currency symbol): [${matches.join(", ")}]`);
});

// Negative lookahead: match a number NOT immediately followed by "%".
const NUMBER_NOT_PERCENT_REGEX = /\b\d+(?!%)\b(?!\.\d)/g;
const mixedNumbers = "We have 20% off, but also 15 items and 100 points, not 30%.";
const nonPercentNumbers = [...mixedNumbers.matchAll(NUMBER_NOT_PERCENT_REGEX)].map((m) => m[0]);
console.log(
  `\n"${mixedNumbers}"\n  -> numbers NOT followed by '%' (negative lookahead): [${nonPercentNumbers.join(", ")}]`
);
