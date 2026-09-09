# Regular Expressions (RegExp)

--> A pattern used to match character combinations in strings
--> Created using literal syntax /pattern/flags or the constructor new RegExp("pattern", "flags")
--> Common flags --> g (global -- find all matches), i (case-insensitive), m (multiline), s (dotall -- `.` also matches newlines), u (unicode), y (sticky -- matches only from lastIndex, no searching ahead)
--> Sticky (y) example --> const re = /\d+/y; re.lastIndex = 0; const s = "12 34"; re.exec(s) matches "12" and advances lastIndex to 2; re.lastIndex = 2 (skipping the space) would fail since the match must start EXACTLY at lastIndex, unlike `g` which would keep searching forward.

# Common Methods

--> .test(str) --> Returns true/false depending on whether the pattern matches
--> .exec(str) --> Returns match details (or null), can be looped with the g flag
--> str.match(regex) --> Returns an array of matches (or null)
--> str.matchAll(regex) --> Returns an iterator of all matches (requires g flag)
--> str.replace(regex, replacement) --> Replaces matched substrings; without the g flag only replaces the first match
--> str.replaceAll(regex, replacement) --> Replaces every match -- with a regex argument it MUST have the g flag or it throws
--> str.split(regex) --> Splits a string using a pattern instead of a fixed separator

# Common Patterns (Character Classes and Quantifiers)

--> \d --> digit, \D --> non-digit, \w --> word character (letters/digits/underscore), \W --> non-word character, \s --> whitespace, \S --> non-whitespace
--> \* --> 0 or more, + --> 1 or more, ? --> 0 or 1, {n,m} --> between n and m times, {n} --> exactly n times
--> ^ --> start of string (or line, with m flag), $ --> end of string (or line, with m flag)
--> [] --> character set, e.g. [a-z0-9] --> any lowercase letter or digit; [^abc] --> anything except a, b, or c
--> () --> grouping (also captures the matched text), (?:...) --> non-capturing group (groups without saving the match), | --> OR

# Groups and Backreferences

--> Capturing groups `(...)` save the matched substring, accessible via `match[1]`, `match[2]`, etc. (index 0 is the full match).
--> Named groups `(?<name>...)` --> capture with a readable name instead of an index: `match.groups.name`.
--> `$<name>` replacement --> named groups can also be referenced in a replacement string: `"2026-08-05".replace(/(?<y>\d+)-(?<m>\d+)-(?<d>\d+)/, "$<d>/$<m>/$<y>")` --> "05/08/2026".
--> Backreference `\1` --> refers back to the first capturing group's match, e.g. `/(\w+)\s\1/` matches a repeated word like "the the".

# Lookahead and Lookbehind

--> `(?=...)` positive lookahead --> matches a position only if followed by the given pattern, without including it in the match.
--> `(?!...)` negative lookahead --> matches only if NOT followed by the given pattern.
--> `(?<=...)` positive lookbehind --> matches only if preceded by the given pattern.
--> `(?<!...)` negative lookbehind --> matches only if NOT preceded by the given pattern.
--> Common use --> password validation, e.g. requiring a digit anywhere without capturing it: `/(?=.*\d)/`.

# Practical Validation Examples

--> Email (simple) --> `/^[\w.-]+@[\w-]+\.[a-zA-Z]{2,}$/`
--> Phone (10 digits, optional dashes) --> `/^\d{3}-?\d{3}-?\d{4}$/`
--> Password (min 8 chars, at least one letter and one number) --> `/^(?=.*[A-Za-z])(?=.*\d).{8,}$/`
--> Replace with a function --> `str.replace(/\d+/g, (match) => Number(match) * 2)` -- the replacement can be a function that receives the match and returns the new substring, instead of a fixed string.

# Where RegExp Is Commonly Used

--> Form validation (emails, phone numbers, passwords, usernames)
--> `String.prototype` methods (.split, .replace, .match) that accept a regex instead of a plain string
--> Search-and-replace across large text, and parsing simple structured text formats

# Deep Dive -- Catastrophic Backtracking (ReDoS)

--> A poorly-constructed regex with NESTED quantifiers can, on certain crafted inputs, take exponentially longer to evaluate as input length grows -- a genuine security concern known as ReDoS (Regular Expression Denial of Service), directly connecting to the availability-focused attacks covered in the Cyber Security track.

```javascript
const evilRegex = /^(a+)+$/;
evilRegex.test("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!");
// This single test can hang the JS engine for seconds or minutes on a moderately long input --
// the nested (a+)+ creates an enormous number of ways to backtrack and re-try matching combinations
```

--> The pattern to watch for -- a quantifier (`+`, `*`) applied to a GROUP that itself contains a quantifier (`(a+)+`, `(a*)*`), especially when the overall pattern can then fail to match (the trailing `!` above never matches `$`, forcing the engine to exhaustively backtrack through every possible way the inner group could have split up the "a" characters before finally giving up).
--> **Why this matters in real applications** -- if a regex like this is used to validate USER-SUPPLIED input (a form field, an API parameter) on a server, an attacker can submit a carefully crafted string and freeze that server process, a genuine, documented real-world vulnerability class. Avoiding nested quantifiers, using more specific character classes instead of broad `.+`/`.* ` patterns, and testing regexes against pathological inputs before deploying them against untrusted input are the standard mitigations.

# Deep Dive -- When NOT to Use Regex

--> Regex is excellent for pattern MATCHING in flat, relatively simple text, but a poor tool for parsing NESTED, structured formats -- attempting to parse HTML or JSON with regex is a widely-cited anti-pattern, since these formats have recursive/nested structure that regex (a non-recursive pattern matcher) fundamentally cannot reliably handle for anything beyond the simplest cases. Use a real parser (`JSON.parse()`, a DOM parser, or a dedicated HTML/XML parsing library) for structured formats instead -- reach for regex specifically for validating/extracting patterns within otherwise plain text (an email format, a phone number, splitting on a delimiter), not for parsing an entire structured document.
