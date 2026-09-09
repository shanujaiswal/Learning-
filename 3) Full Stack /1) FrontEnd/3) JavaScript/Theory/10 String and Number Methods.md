# String Methods

--> .length --> Returns the number of characters in a string
--> .slice(start, end) --> Extracts a section of a string, supports negative indices
--> .substring(start, end) --> Similar to slice but doesn't support negative indices
--> .trim() --> Removes whitespace from both ends
--> .toUpperCase() / .toLowerCase() --> Changes the case of a string
--> .includes(value) --> Checks if a string contains a given substring, returns boolean
--> .startsWith(value) / .endsWith(value) --> Checks the beginning/end of a string
--> .replace(old, new) --> Replaces the first match; .replaceAll(old, new) replaces every match
--> .split(separator) --> Splits a string into an array of substrings
--> .concat(str2) --> Joins two or more strings (template literals / + are preferred)
--> .repeat(n) --> Repeats a string n times
--> .padStart(len, char) / .padEnd(len, char) --> Pads a string to a given length
--> .charAt(index) / [index] --> Returns the character at a given index
--> .indexOf(value) / .lastIndexOf(value) --> Returns the position of a substring, -1 if not found
--> .at(index) --> Returns the character at a given index, supports negative indices (e.g. str.at(-1) gets the last character)
--> .normalize() --> Returns the Unicode Normalization Form of a string, useful when comparing strings with accented characters that can be encoded differently
--> String.raw --> A tag function that returns the raw (unescaped) form of a template literal, e.g. String.raw`\n` returns the literal characters \ and n instead of a newline

# Template Literals

--> Created using backticks ( \` \` ) instead of quotes
--> Allow embedded expressions using ${expression}
--> Support multi-line strings without needing \n

# Number Methods

--> Number.isInteger(value) --> Checks whether a value is an integer
--> Number.isNaN(value) --> Checks whether a value is NaN (safer than global isNaN())
--> Number.parseInt(str) / parseInt(str) --> Converts a string to an integer, stops at first non-numeric character
--> Number.parseFloat(str) / parseFloat(str) --> Converts a string to a floating point number
--> .toFixed(n) --> Rounds a number to n decimal places, returns a string
--> .toString(base) --> Converts a number to a string, optionally in a given base (e.g. binary, hex)
--> isNaN(value) --> Global function to check if a value is Not-a-Number (coerces the value first)
--> Number.EPSILON --> The smallest difference representable between two numbers, used to compare floats safely (e.g. Math.abs(a - b) < Number.EPSILON) instead of a === b which can fail due to floating-point rounding
--> .toPrecision(n) --> Formats a number to n total significant digits, returns a string

# Deep Dive -- Strings Are Immutable

--> Every string method that appears to "modify" a string (`.toUpperCase()`, `.trim()`, `.replace()`) actually returns a brand-new string, leaving the original completely untouched -- strings are a primitive type (covered in the Basics and Variables file) and primitives can never be mutated in place.

```javascript
let name = "alice";
name.toUpperCase();
console.log(name);   // "alice" -- unchanged! toUpperCase() returned a new string that was never captured

name = name.toUpperCase();   // Must explicitly reassign to actually use the new value
console.log(name);   // "ALICE"
```

--> This is a genuinely common beginner mistake -- calling a string method and expecting the original variable to have changed, forgetting that strings (like all primitives) are immutable and every "transforming" method returns a new value rather than altering the original.

# Deep Dive -- Floating-Point Precision Issues

--> JavaScript represents all numbers (both integers and decimals) using the IEEE 754 double-precision floating-point format -- a format that CANNOT represent every decimal value exactly in binary, leading to small, well-known rounding errors.

```javascript
console.log(0.1 + 0.2);          // 0.30000000000000004 -- NOT exactly 0.3, due to binary floating-point representation
console.log(0.1 + 0.2 === 0.3);   // false -- direct equality comparison fails because of this tiny discrepancy
```

--> This is NOT a JavaScript-specific bug -- virtually every programming language using IEEE 754 floating-point (which is nearly all of them) exhibits the exact same behavior for the exact same reason.
--> **The fix for comparing floats** -- use `Number.EPSILON` (mentioned above) to check if two numbers are "close enough" rather than exactly equal: `Math.abs(a - b) < Number.EPSILON`.
--> **The fix for financial/monetary calculations** -- avoid floating-point arithmetic entirely for money. Store amounts as INTEGER CENTS (`1050` instead of `10.50`) and only convert to a decimal dollar display at the very end, or use a dedicated decimal/currency library -- a real, practical discipline directly motivated by this precision limitation, and a common source of subtle bugs in e-commerce/billing code that doesn't follow it.
