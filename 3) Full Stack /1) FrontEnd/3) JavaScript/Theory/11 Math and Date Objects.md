# Math Object

--> Math.round(x) / Math.floor(x) / Math.ceil(x) --> Rounding helpers
--> Math.trunc(x) --> Removes the decimal part entirely (no rounding), unlike floor/ceil which round toward -Infinity/+Infinity.
--> Math.sign(x) --> Returns 1, -1, or 0 depending on the sign of x.
--> Math.max(...values) / Math.min(...values) --> Largest / smallest of the given values
--> Math.random() --> Returns a pseudo-random number between 0 (inclusive) and 1 (exclusive)
--> Math.pow(base, exp) / base \*\* exp --> Exponentiation
--> Math.sqrt(x) / Math.cbrt(x) --> Square root / cube root
--> Math.abs(x) --> Absolute value
--> Math.log(x) / Math.log2(x) / Math.log10(x) --> Natural log / base-2 log / base-10 log
--> Math.hypot(a, b) --> The hypotenuse/Euclidean distance -- sqrt(a*a + b*b), useful for distance-between-points calculations.
--> Math.PI --> The constant π (~3.14159...).

# Generating a Random Integer in a Range

--> Math.random() alone only gives a decimal between 0 and 1 -- combine with floor/round for whole numbers.
--> Math.floor(Math.random() * (max - min + 1)) + min --> A random integer between min and max, inclusive.

# Date Object

--> new Date() --> Creates a Date object for the current date/time
--> new Date("2026-01-01") / new Date(year, month, day) --> Creates a Date for a specific point in time (month is zero-indexed --> 0 = January, 11 = December).
--> .getFullYear() / .getMonth() / .getDate() / .getDay() --> Read individual date parts (getDate is day-of-month, getDay is day-of-week 0-6).
--> .getHours() / .getMinutes() / .getSeconds() --> Read individual time parts
--> .getTime() --> Returns milliseconds since Jan 1, 1970 (Unix epoch)
--> Date.now() --> Returns the current timestamp in milliseconds, without creating a Date object
--> Date.parse("2026-01-01") --> Parses a date string and returns its timestamp in milliseconds (same value a `new Date(str).getTime()` would give).
--> Dates are mutable objects -- methods like .setDate(), .setFullYear(), .setHours() modify the object in place rather than returning a new one.

# Formatting and Comparing Dates

--> .toISOString() --> Returns the date as a standardized string (`2026-01-01T00:00:00.000Z`), commonly used when sending dates to an API.
--> .toLocaleDateString() / .toLocaleTimeString() --> Formats the date/time according to the user's locale, e.g. `toLocaleDateString("en-US")` --> "1/1/2026".
--> Comparing dates --> subtracting two Date objects (date1 - date2) or comparing `.getTime()` works because Dates coerce to their millisecond timestamp; comparing with `<`/`>` also works directly, but `===`/`==` do NOT (compares object references, not value).
--> Date difference in days --> `Math.floor((date2 - date1) / (1000 * 60 * 60 * 24))` -- divide the millisecond difference down to days.

# Intl Object (Internationalization)

--> Intl.NumberFormat --> Formats numbers according to locale/currency rules: `new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(1234.5)` --> "$1,234.50".
--> Intl.DateTimeFormat --> Formats dates according to locale rules, more flexible than toLocaleDateString: `new Intl.DateTimeFormat("en-GB", { dateStyle: "long" }).format(new Date())`.
--> Preferred over manually concatenating date/number parts -- correctly handles locale-specific separators, currency symbols, and ordering.

# Deep Dive -- Dates Are Mutable Objects, a Common Bug Source

--> Unlike strings/numbers (immutable primitives, covered in the String and Number Methods file), a `Date` object CAN be mutated in place -- `.setDate()`, `.setMonth()`, etc. change the SAME object rather than returning a new one, which causes a genuinely common, subtle bug when a Date is shared/passed around.

```javascript
function addDays(date, days) {
  date.setDate(date.getDate() + days);   // Mutates the ORIGINAL date passed in
  return date;
}

const meeting = new Date("2026-01-01");
const followUp = addDays(meeting, 7);
console.log(meeting.toISOString());   // Also changed to Jan 8th! The original "meeting" was mutated too
```

--> The fix -- always clone before mutating: `const newDate = new Date(date); newDate.setDate(...)`, or use a modern immutable date library (`date-fns`, `Temporal` -- the newer, still-emerging built-in API specifically designed to fix this and several other longstanding Date API pain points) that returns new instances instead of mutating.

# Deep Dive -- Time Zones -- The Recurring Practical Headache

--> `new Date()` and most Date methods (`.getHours()`, `.getDate()`) operate in the BROWSER'S LOCAL time zone by default -- a date that looks correct for a developer testing in one time zone can silently show the WRONG day/hour for a user or server in a different time zone.
--> `.toISOString()` always outputs in UTC (marked with the trailing `Z`) -- the safest, most common format for STORING and TRANSMITTING dates (to an API, in a database, covered in the Database SQL Dates file), specifically because it removes time-zone ambiguity entirely; convert to the user's local time zone only at the final display step, using `.toLocaleDateString()`/`Intl.DateTimeFormat` shown above.
