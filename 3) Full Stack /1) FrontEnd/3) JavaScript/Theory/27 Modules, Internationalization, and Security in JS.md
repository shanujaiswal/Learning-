# Module Specifiers -- Relative, Bare, and Import Maps

--> A relative specifier (`import x from "./utils.js"` or `"../lib/foo.js"`) resolves directly against the current file's location -- unambiguous, but only works for files you actually control.
--> A "bare" specifier (`import react from "react"`) has no `./`, `../`, or protocol at all -- it's not a file path, it's a NAME the module system has to resolve using its own rules (see file 15 Iterators Proxy and Module Systems for the CommonJS/ESM split this resolution sits on top of).
--> In Node.js, a bare specifier resolves by walking up `node_modules` folders looking for a matching package name, then reading that package's `package.json` to find its actual entry file. Native ESM in the BROWSER has no such resolution algorithm built in -- `import "react"` in a plain `<script type="module">` fails outright, since the browser has no `node_modules` concept.
--> Import maps close that gap -- a `<script type="importmap">` block tells the browser how to resolve bare specifiers itself, without a bundler rewriting every import at build time.

```html
<script type="importmap">
{
  "imports": {
    "lodash": "/vendor/lodash-es/lodash.js",
    "components/": "/src/components/"
  }
}
</script>
<script type="module">
  import debounce from "lodash";              // Resolves via the import map
  import Button from "components/Button.js";   // Trailing "/" maps a whole prefix
</script>
```

# package.json exports and imports Fields

--> The `"exports"` field in a package's `package.json` explicitly declares which files a package consumer is ALLOWED to import -- anything not listed is no longer reachable from outside, even by a correct-looking relative path into the package's folder (a deliberate move away from the old free-for-all where any file in a package was importable).

```json
{
  "name": "my-lib",
  "exports": {
    ".": "./dist/index.js",
    "./utils": "./dist/utils.js",
    "./package.json": "./package.json"
  }
}
```

```javascript
import lib from "my-lib";          // OK -- maps to "."
import utils from "my-lib/utils";   // OK -- explicitly exposed
import internal from "my-lib/dist/internal-helper.js";   // Error -- not listed, even though the file exists
```

--> `"exports"` can also branch by condition -- `"import"` vs `"require"` keys let a single package ship both an ESM and a CommonJS build and have Node.js pick the right one automatically based on how it was loaded, which is the modern replacement for maintaining two separate published packages.
--> The `"imports"` field (note: no `#`-less bare names -- entries must start with `#`) defines internal-only remapping for a package's OWN code, useful for swapping an implementation per environment (e.g. `#fetch-impl` resolving to a Node-specific file vs a browser-specific file) without consumers ever seeing the indirection.

# Intl.RelativeTimeFormat -- Human-Readable Relative Time

--> Formats a numeric offset ("3 days" / "in 3 days" / "3 days ago") in the user's locale and language, instead of hand-rolling "X days ago" string logic yourself.

```javascript
const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
console.log(rtf.format(-1, "day"));    // "yesterday" -- "auto" prefers idiomatic words over raw numbers when one exists
console.log(rtf.format(3, "day"));      // "in 3 days"
console.log(rtf.format(-2, "hour"));    // "2 hours ago"

const rtfEs = new Intl.RelativeTimeFormat("es", { numeric: "auto" });
console.log(rtfEs.format(-1, "day"));   // "ayer" -- fully localized, not just translated punctuation
```

# Intl.Collator -- Locale-Aware String Sorting

--> Default JS string comparison (`<`, `>`, `.sort()`) compares by raw UTF-16 code unit values -- this sorts strings in an order that doesn't match how humans actually alphabetize in many languages (accented characters, case, locale-specific ordering rules).

```javascript
const words = ["café", "cafe", "Cafe", "abc"];
console.log(words.sort());   // Naive sort -- order driven by code unit values, "Cafe" awkwardly separated from "cafe"

const collator = new Intl.Collator("en", { sensitivity: "base" });   // Ignores case/accent differences
console.log(words.sort(collator.compare));   // ["abc", "cafe", "Cafe", "café"] -- a linguistically sensible order

console.log(collator.compare("café", "cafe"));   // 0 -- treated as equal under "base" sensitivity
```

--> `Intl.Collator` is also dramatically faster than repeatedly calling `String.prototype.localeCompare()` inside a sort callback for large arrays, since it does the locale setup work ONCE upfront instead of on every single comparison.

# Intl.PluralRules -- Correct Pluralization Rules

--> English pluralization ("1 item" vs "2 items") looks simple, but many languages have more than two plural categories (zero/one/two/few/many/other) -- `Intl.PluralRules` tells you which CATEGORY a number falls into for a given locale, so you can pick the right translated string.

```javascript
const pr = new Intl.PluralRules("en");
console.log(pr.select(1));   // "one"
console.log(pr.select(2));   // "other"

const prAr = new Intl.PluralRules("ar");   // Arabic has six plural categories
console.log(prAr.select(0));   // "zero"
console.log(prAr.select(1));   // "one"
console.log(prAr.select(2));   // "two"
console.log(prAr.select(3));   // "few"

function pluralize(n, forms) {   // forms = { one: "...", other: "..." }
  return forms[pr.select(n)] ?? forms.other;
}
console.log(pluralize(1, { one: "1 item", other: `${1} items` }));
```

--> `Intl.NumberFormat`'s `style: "unit"`/currency options and `Intl.DateTimeFormat` (both usually introduced earlier in a JS track) share this same underlying idea with `RelativeTimeFormat`/`Collator`/`PluralRules` -- offload locale-specific formatting RULES to the engine's built-in CLDR data, rather than hand-maintaining per-language logic yourself.

# structuredClone -- What It Still Can't Do

--> `structuredClone()` (see file 13 Advanced Data Types and Error Handling for its advantages over `JSON.parse(JSON.stringify())`) is based on the structured clone algorithm used internally by `postMessage` -- but it has real limits worth knowing before relying on it for arbitrary data.
--> Cannot clone -- functions, DOM nodes, `Error` objects (as of most current engine implementations -- support has been inconsistent), property accessors/getters-setters (they get cloned as plain data properties, losing their behavior), and anything with a prototype chain that isn't one of the specifically supported built-ins.

```javascript
structuredClone({ fn: () => {} });         // Throws DataCloneError
structuredClone(document.body);             // Throws DataCloneError -- DOM nodes aren't cloneable
structuredClone({ get x() { return 1; } }); // Clones to a plain { x: 1 } -- the getter itself is lost
```

--> DOES correctly handle circular references, `Map`, `Set`, `Date`, `RegExp`, typed arrays, and `ArrayBuffer` (including transferring them, same mechanism used by Web Workers' `postMessage` in file 20 Web Workers) -- squarely aimed at "real JS data," not arbitrary object graphs with behavior attached.

# XSS -- Cross-Site Scripting

--> XSS happens when untrusted input ends up executed AS CODE in a page rather than treated as inert data -- most commonly by getting inserted into the DOM in a way the browser interprets as markup/script rather than plain text.

```javascript
// Vulnerable -- attacker-controlled comment text gets parsed as HTML
element.innerHTML = userComment;   // If userComment is "<img src=x onerror=stealCookies()>", it just RUNS

// Safe -- treated strictly as text, HTML-escaped automatically, never parsed as markup
element.textContent = userComment;   // (see file 07 Events and DOM for the innerHTML vs textContent distinction)
```

--> The core JS-side mitigation is simple to state and easy to forget under deadline pressure -- never pass untrusted strings to `innerHTML`, `document.write()`, `eval()`, `new Function(...)`, or a templating engine configured to skip auto-escaping. Prefer `textContent`, `setAttribute` (for attributes, not full markup), or a framework's built-in escaping (JSX's `{expression}`, Vue's `{{ }}`) which escapes by default and requires an explicit opt-out (`dangerouslySetInnerHTML` in React) to disable.
--> A library like DOMPurify is the standard answer when you genuinely need to render user-supplied HTML (a rich-text comment, for example) -- it sanitizes the markup, stripping dangerous tags/attributes (`<script>`, `onerror=`) while keeping safe formatting tags intact.

# CSP -- Content Security Policy

--> A CSP is a header (or `<meta>` tag) the SERVER sends that tells the browser which sources of scripts/styles/images/etc. are allowed to load or execute on the page at all -- a second layer of defense that limits the damage even if an XSS injection point exists.

```
Content-Security-Policy: default-src 'self'; script-src 'self' https://trusted-cdn.com; object-src 'none'
```

--> `default-src 'self'` -- only load resources from the page's own origin unless overridden per-directive. `script-src` restricts which origins scripts can load from -- crucially, a strict CSP without `'unsafe-inline'` blocks INLINE `<script>` tags and `onclick="..."` attributes entirely, which is precisely what neutralizes a huge share of real-world XSS payloads, since an attacker's injected `<script>` tag simply refuses to execute.
--> `'unsafe-inline'` and `'unsafe-eval'` opt back into the risky behaviors CSP exists to prevent -- a strong CSP avoids both, using nonces (`script-src 'nonce-r4nd0m'`, a fresh random value the server generates per response and only legitimate script tags know) when inline scripts are genuinely unavoidable.

# CSRF -- Cross-Site Request Forgery

--> CSRF tricks a logged-in user's browser into making an unwanted request to a site they're authenticated on -- the browser automatically attaches cookies to same-origin-looking requests regardless of which page/origin actually triggered them, which a malicious page can exploit.

```html
<!-- Hosted on evil.com -- if the victim is logged into bank.com in the same browser, this
     form auto-submits and the request goes out WITH the victim's bank.com session cookie attached -->
<form action="https://bank.com/transfer" method="POST" id="f">
  <input type="hidden" name="amount" value="1000">
  <input type="hidden" name="to" value="attacker-account">
</form>
<script>document.getElementById("f").submit();</script>
```

--> Mitigations live mostly server-side, but the JS-relevant pieces -- cookies set with `SameSite=Strict` or `SameSite=Lax` (see file 09 JSON Storage and Modules for cookie attributes) stop the browser from attaching them to most cross-site requests in the first place; a CSRF token (a random value embedded in the legitimate page and required on every state-changing request) works because an attacker's page has no way to read that token from a page it doesn't control, thanks to the same-origin policy.
--> `fetch`'s `credentials` option matters here too -- `credentials: "same-origin"` (the default for same-origin requests) vs `"include"` (forces cookies to be sent even cross-origin, only if the server's CORS response explicitly allows it) is a deliberate choice point, not an accident, when calling APIs across origins.

# crypto.getRandomValues() vs Math.random()

--> `Math.random()` is a PSEUDO-random number generator optimized for speed and statistical distribution in things like animations/games -- it is NOT cryptographically secure. Its internal algorithm and state are, in practice, predictable/reproducible given enough outputs, meaning it must never be used for anything security-sensitive (tokens, password reset codes, session identifiers).

```javascript
// Insecure -- do not use for anything security-relevant
const insecureToken = Math.random().toString(36).slice(2);

// Secure -- cryptographically strong random bytes from the platform's CSPRNG
const bytes = new Uint8Array(16);
crypto.getRandomValues(bytes);
const secureToken = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
```

--> `crypto.getRandomValues()` is available globally in browsers (`window.crypto`) and Node.js (`globalThis.crypto` in modern versions, or the `node:crypto` module) -- it fills a typed array with values from the operating system's cryptographically secure random source, making its output unpredictable even to someone who knows the algorithm.
--> The broader `crypto.subtle` (Web Crypto API) provides actual cryptographic operations (hashing, encryption, signing) built on top of this same secure randomness -- relevant whenever a task description says "generate a secure token/ID," never `Math.random()`.

# Unicode Internals -- Code Units vs Code Points

--> JavaScript strings are sequences of UTF-16 CODE UNITS (16-bit values), not Unicode CODE POINTS (the actual character numbers defined by the Unicode standard) -- for most common characters these are the same thing, which is exactly why this distinction goes unnoticed until it doesn't.
--> Characters outside the "Basic Multilingual Plane" (many emoji, some CJK characters, mathematical symbols) require TWO UTF-16 code units to represent one code point -- a "surrogate pair." `.length` counts code UNITS, so a string containing surrogate pairs reports a `.length` larger than its actual number of visible characters.

```javascript
const heart = "❤️";     // Actually TWO code points: heart (U+2764) + variation selector (U+FE0F)
const emoji = "😀";      // ONE code point (U+1F600), but represented by a SURROGATE PAIR of code units

console.log(emoji.length);          // 2 -- counts UTF-16 code units, not the "1 character" a human sees
console.log([...emoji].length);      // 1 -- spreading a string iterates by CODE POINT, correctly counting 1
console.log(emoji.charCodeAt(0));    // 55357 -- the high surrogate code unit alone, meaningless on its own
console.log(emoji.codePointAt(0));   // 128512 -- the FULL code point, correctly combining both surrogates
```

--> `charAt`/`charCodeAt`/`[index]` all operate on code UNITS and can slice a surrogate pair in half, producing an invalid/unprintable half-character -- `codePointAt()` and the string iterator (`for...of`, spread `[...str]`, `Array.from(str)`) are the code-point-aware equivalents, and are the correct choice whenever a string might contain non-BMP characters.

```javascript
const str = "a😀b";
console.log(str.slice(1, 2));       // A broken half-surrogate, not "😀" -- sliced by code unit, not code point
console.log([...str][1]);            // "😀" -- correct, code-point-aware indexing via the iterator
```

# Grapheme Clusters -- What a Human Actually Sees as "One Character"

--> Even code points aren't always what a human perceives as a single character -- a "grapheme cluster" (e.g. an emoji with a skin-tone modifier, or a base letter plus a combining accent mark) can be MULTIPLE code points that together render as one visual unit, one more layer above the code-unit/code-point distinction.

```javascript
const flag = "👨‍👩‍👧‍👦";   // A "family" emoji -- actually 4 person emoji joined by 3 Zero-Width Joiner (U+200D) code points
console.log([...flag].length);   // 7 -- code-point iteration still splits it into its individual pieces

const segmenter = new Intl.Segmenter("en", { granularity: "grapheme" });
console.log([...segmenter.segment(flag)].length);   // 1 -- correctly treats it as ONE user-perceived character
```

--> `Intl.Segmenter` (also supports `granularity: "word"` and `"sentence"` for correctly splitting text in locale-aware ways that plain whitespace-splitting gets wrong for many languages) is the correct tool whenever code needs to count, truncate, or iterate "characters" the way a human would actually perceive them -- e.g. a character-limit counter on a social media post input that shouldn't cut a combined emoji in half.

# Deep Dive -- Why String Length Alone Is an Unreliable Truncation Boundary

--> Directly connecting the two ideas above -- truncating user-supplied text with `str.slice(0, 100)` based on raw `.length` can silently split a surrogate pair or a grapheme cluster in half, corrupting the displayed text at the cut point (a broken emoji, a stray combining accent with no base letter).

```javascript
function truncateSafely(str, maxGraphemes) {
  const segmenter = new Intl.Segmenter(undefined, { granularity: "grapheme" });
  const graphemes = [...segmenter.segment(str)].map((s) => s.segment);
  return graphemes.length > maxGraphemes
    ? graphemes.slice(0, maxGraphemes).join("") + "…"
    : str;
}

console.log(truncateSafely("Hello 👨‍👩‍👧‍👦 World", 8));   // Cuts cleanly, never splits the family emoji
```

--> This is the same category of bug as the naive string-sort problem `Intl.Collator` solves above -- code that silently assumes "1 character = 1 array index = 1 visual glyph" works fine in testing (which tends to use plain ASCII) and then breaks specifically on real-world international/emoji-containing input in production.
