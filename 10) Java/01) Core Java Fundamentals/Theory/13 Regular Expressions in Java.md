# Why This Matters

--> Regular expressions ("regex") are a mini-language for describing PATTERNS in text -- validating an email format, extracting dates from a log line, splitting a CSV row, replacing every occurrence of a placeholder. Java's regex support lives primarily in `java.util.regex` (`Pattern` and `Matcher`), plus several convenience methods on `String` itself that delegate to it internally. This file is a standalone topic from annotations/reflection (Files 01-05) -- it's grouped in this chapter only because both are commonly taught together as "inspecting/transforming code or text programmatically."

# `Pattern` and `Matcher` -- The Core API

```java
import java.util.regex.*;

Pattern pattern = Pattern.compile("\\d+");     // compile the regex ONCE -- this is the expensive step
Matcher matcher = pattern.matcher("Order 42, ship to zone 7");

while (matcher.find()) {
    System.out.println(matcher.group());       // "42", then "7"
}
```

--> `Pattern.compile(regex)` parses the regex string into an internal, efficient representation -- this compilation step is the costly part, so a `Pattern` should be compiled ONCE (typically as a `static final` field) and reused across many inputs, never re-compiled per call in a loop. `Matcher` is created from a compiled `Pattern` against a SPECIFIC input string and holds the mutable state of a search (current position, last match) -- a `Matcher` is cheap to create and is NOT thread-safe, unlike the `Pattern` it came from (which IS safe to share across threads once compiled).

```java
static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

boolean isValid = EMAIL.matcher("user@example.com").matches();
```

## Key `Matcher` Methods

| Method | Behavior |
|---|---|
| `matches()` | Does the ENTIRE input string match the pattern, start to end? |
| `find()` | Does the pattern occur ANYWHERE in the input (searching from current position onward)? Advances position on each call -- use in a `while` loop to find all matches |
| `lookingAt()` | Does the input match the pattern starting at position 0 -- but NOT necessarily consuming the whole string? |
| `group()` / `group(n)` | The full matched text, or the text captured by group `n` |
| `start()` / `end()` | Index bounds of the current match (or a specific group) |
| `replaceAll(repl)` | Replace every match in the whole input |
| `replaceFirst(repl)` | Replace only the first match |
| `reset()` | Reset the matcher to search from the beginning again |

```text
matches()    "42"        against  "\\d+"       -> true  (whole string is digits)
matches()    "x42"       against  "\\d+"       -> false (whole string must match, "x" breaks it)
find()       "x42"       against  "\\d+"       -> true  (finds "42" as a SUBSTRING match)
lookingAt()  "42x"       against  "\\d+"       -> true  (matches from the start, doesn't need to consume all)
```

# Common Regex Syntax Reference

| Syntax | Meaning |
|---|---|
| `.` | Any character except line terminator (unless `Pattern.DOTALL`) |
| `\d` / `\D` | Digit `[0-9]` / non-digit |
| `\w` / `\W` | Word character `[a-zA-Z0-9_]` / non-word |
| `\s` / `\S` | Whitespace / non-whitespace |
| `^` / `$` | Start / end of string (or line, in `MULTILINE` mode) |
| `\b` / `\B` | Word boundary / non-word-boundary |
| `[abc]` | Character class -- any one of `a`, `b`, `c` |
| `[^abc]` | Negated character class -- any character EXCEPT `a`, `b`, `c` |
| `[a-z]` | Character range |
| `a\|b` | Alternation -- `a` OR `b` |
| `(...)` | Capturing group |
| `(?:...)` | Non-capturing group -- groups for precedence/quantifying without creating a capture |
| `(?<name>...)` | Named capturing group (Java 7+) |
| `*` | 0 or more (greedy) |
| `+` | 1 or more (greedy) |
| `?` | 0 or 1 (greedy) |
| `{n}` | Exactly n |
| `{n,}` | n or more |
| `{n,m}` | Between n and m, inclusive |
| `*?` `+?` `??` `{n,m}?` | Lazy (reluctant) versions of the above -- match as FEW characters as possible |
| `*+` `++` `?+` | Possessive versions -- match greedily and NEVER backtrack (performance-oriented, Java-specific extension) |
| `(?=...)` | Positive lookahead |
| `(?!...)` | Negative lookahead |
| `(?<=...)` | Positive lookbehind |
| `(?<!...)` | Negative lookbehind |
| `\\` | Escape a metacharacter (in a Java string literal, needs `\\\\` for one literal backslash, or `\\.` for a literal dot) |

```java
Pattern.compile("\\bcat\\b");        // matches "cat" as a whole word, not inside "category" or "concatenate"
Pattern.compile("colou?r");          // matches both "color" and "colour"
Pattern.compile("(?i)hello");        // (?i) inline flag -- case-insensitive for the rest of the pattern
```

# Groups and Capturing

```java
Pattern datePattern = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
Matcher m = datePattern.matcher("Event on 2026-09-01 confirmed");

if (m.find()) {
    System.out.println(m.group(0));   // "2026-09-01" -- group 0 is always the ENTIRE match
    System.out.println(m.group(1));   // "2026" -- year
    System.out.println(m.group(2));   // "09"   -- month
    System.out.println(m.group(3));   // "01"   -- day
}
```

## Named Groups (Java 7+)

```java
Pattern p = Pattern.compile("(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})");
Matcher m = p.matcher("2026-09-01");
if (m.matches()) {
    System.out.println(m.group("year"));    // "2026" -- clearer than a numeric index, especially with many groups
}
```

--> Named groups trade a little verbosity for much better readability/maintainability once a pattern has more than two or three groups -- `m.group("year")` self-documents in a way `m.group(1)` never does, and survives someone later inserting a new group earlier in the pattern (which would silently renumber every subsequent numeric index).

## Non-Capturing Groups

```java
Pattern.compile("(?:Mr|Mrs|Ms)\\. \\w+");   // groups for alternation precedence, but doesn't create a numbered group
```

--> Use `(?:...)` whenever a group is needed only to scope `|` or a quantifier, not to extract a value -- keeps group numbering predictable and slightly reduces matching overhead.

# `String` Methods Backed by Regex

```java
"hello123world".matches("[a-z]+\\d+[a-z]+");     // true -- delegates to Pattern.matches(), whole-string match

"a1b2c3".replaceAll("\\d", "#");                  // "a#b#c#" -- delegates to Pattern.compile(regex).matcher(...).replaceAll(...)
"a1b2c3".replaceFirst("\\d", "#");                // "a#b2c3"

"one,two,,three".split(",");                      // ["one", "two", "", "three"] -- trailing empty strings dropped by default
"one,two,,three".split(",", -1);                   // ["one", "two", "", "three"] -- limit=-1 keeps ALL trailing empties

"  padded  ".strip();                              // NOT regex -- but String.trim()/strip() are often confused with regex cleanup
```

--> **`String.matches()` always matches the WHOLE string**, same as `Matcher.matches()` -- a very common beginner mistake is calling `"abc123".matches("\\d+")` expecting a partial match (`false`, because letters remain unmatched); use `Pattern.compile(regex).matcher(input).find()` instead when a partial/substring match is intended.
--> **Every call to `String.matches()`/`replaceAll()`/`split()` COMPILES the regex from scratch internally** -- fine for a one-off, wasteful in a loop; if the same pattern is used repeatedly, compile a `Pattern` once explicitly and reuse it instead of relying on these `String` convenience methods.

```java
"a.b.c".split("\\.");        // ["a", "b", "c"] -- "." must be ESCAPED, since split() treats its argument as regex,
                              // and "." unescaped means "any character," splitting on every character otherwise

"a.b.c".split(".");          // WRONG -- unescaped "." matches every character, result is []  (an easy real bug)
```

# Pattern Flags

```java
Pattern.compile("hello", Pattern.CASE_INSENSITIVE);
Pattern.compile("^start", Pattern.MULTILINE);        // ^ and $ match at EVERY line boundary, not just string start/end
Pattern.compile("a.b", Pattern.DOTALL);               // "." also matches line terminators
Pattern.compile("# comment \\d+  # another", Pattern.COMMENTS);   // whitespace/comments ignored in the pattern itself
```

| Flag | Effect |
|---|---|
| `CASE_INSENSITIVE` | Case-insensitive matching (ASCII by default; combine with `UNICODE_CASE` for full Unicode case folding) |
| `MULTILINE` | `^`/`$` match at line boundaries within the input, not just the very start/end |
| `DOTALL` | `.` also matches line-terminator characters |
| `COMMENTS` | Whitespace and `#`-to-end-of-line comments in the pattern are ignored -- lets complex patterns be formatted readably |
| `UNICODE_CASE` | Enables Unicode-aware case-insensitive matching when combined with `CASE_INSENSITIVE` |

# Common Pitfalls

## Greedy vs. Lazy Quantifiers

```java
Pattern greedy = Pattern.compile("<.+>");
Matcher g = greedy.matcher("<b>bold</b>");
g.find();
System.out.println(g.group());     // "<b>bold</b>" -- greedy "+" grabs as MUCH as possible, then backtracks minimally

Pattern lazy = Pattern.compile("<.+?>");
Matcher l = lazy.matcher("<b>bold</b>");
l.find();
System.out.println(l.group());     // "<b>" -- lazy "+?" grabs as LITTLE as possible while still matching
```

--> Greedy quantifiers (`*`, `+`, `?`, `{n,m}`) consume as much input as possible first, then backtrack only as needed to let the rest of the pattern succeed. Lazy quantifiers (`*?`, `+?`, `??`, `{n,m}?`) do the opposite -- consume as little as possible, expanding only when forced to. **Parsing HTML/XML-like tags with `.+` instead of `.+?` is a classic beginner bug**, producing one giant match spanning multiple tags instead of the intended single tag.

## Catastrophic Backtracking

```java
// DANGEROUS pattern -- nested quantifiers over overlapping character sets
Pattern evil = Pattern.compile("(a+)+b");
String input = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaac";
// evil.matcher(input).matches()   -- can hang the thread for an effectively unbounded time (exponential blowup)
```

--> When a regex contains NESTED or OVERLAPPING quantifiers (`(a+)+`, `(a|a)*`, `(a*)*`) and the input ultimately FAILS to match after a long run of matching characters, the engine can be forced to try an EXPONENTIAL number of ways to partition the matched text among the nested groups before concluding failure -- this is "catastrophic backtracking," and it can turn a normal-looking regex call into a multi-minute (or effectively infinite) hang on a moderately long adversarial input.
--> This is a genuine, exploitable **ReDoS (Regular Expression Denial of Service)** vulnerability class -- any regex applied to UNTRUSTED input (user-submitted strings, request headers) should be reviewed for nested-quantifier patterns, and ideally load-tested against a long repetitive/almost-matching string before shipping.

```text
Safer rewrite strategies:
  - Eliminate the nesting: (a+)+  ->  a+                         (often the nested group was redundant anyway)
  - Make alternatives non-overlapping: (a|a)*  ->  a*
  - Use possessive quantifiers to forbid backtracking entirely: (a++)+b  -- a++ commits to its match, never
    gives characters back, trading "can express slightly less" for "can never blow up"
  - Impose an explicit timeout around matching for regexes applied to untrusted input, since Java's regex
    engine (backtracking-based, like most mainstream engines) has no built-in execution time cap
```

## Other Frequent Mistakes

```java
"3.14".split("\\.");          // must escape "." -- unescaped it means "any character"
"C:\\path".matches("C:\\\\.*"); // Java string escaping AND regex escaping both apply -- one literal backslash
                                 // in the actual regex needs "\\\\" in the Java source string
Pattern.quote("a.b*c");        // wraps text so it's treated as a LITERAL string, not a regex -- use when
                                 // building a pattern from untrusted/dynamic text that isn't meant to BE regex
```

--> **Two layers of escaping stack up in Java regex source**: the Java string literal escaping (`\\` for one backslash) THEN the regex engine's own escaping (`\.` for a literal dot) -- a literal single backslash character in the actual regex requires FOUR backslashes in the Java source string (`"\\\\"`), a well-known source of confusion. `Pattern.quote(s)` (or `Matcher.quoteReplacement(s)` for replacement strings) sidesteps this entirely by treating a whole string as literal text with no regex interpretation.

# Gotchas and Best Practices

--> **Compile `Pattern`s once, reuse them** -- `Pattern.compile` is the expensive step; hold compiled patterns as `static final` fields rather than recompiling inside a method called per request/iteration (mirrors the "cache reflective lookups" advice from File 05 -- both are "expensive setup, cheap reuse" APIs).
--> **`matches()` requires a FULL match; `find()` allows a partial/substring match** -- conflating the two is probably the single most common Java regex bug.
--> **Escape regex metacharacters when splitting/matching on literal characters** -- `.`, `*`, `+`, `?`, `(`, `)`, `[`, `]`, `{`, `}`, `|`, `^`, `$`, `\` all carry special meaning; use `Pattern.quote()` or backslash-escape them explicitly when the text being matched is meant literally.
--> **Watch for nested/overlapping quantifiers on any pattern applied to untrusted input** -- review for catastrophic backtracking risk (`(x+)+`, `(x*)*`, `(x|x)*`-shaped patterns) before shipping regex-based validation that a user directly controls the input to.
--> **Greedy is the default -- reach for lazy (`?` suffix) explicitly whenever "shortest match" is intended**, especially around paired delimiters (quotes, tags, brackets).
--> **For genuinely structured formats (JSON, HTML, XML, CSV with quoting/escaping), prefer a real parser over regex** -- regex works well for FLAT, regular patterns (emails, phone numbers, simple tokens) but becomes unreadable and fragile for anything with nested or context-sensitive structure; "now you have two problems" is a real risk, not just a joke, once a regex tries to fully parse a recursive grammar.
--> **Named groups (`(?<name>...)`) are worth the extra characters once a pattern has more than ~2 groups** -- much easier to maintain than tracking numeric positions, and immune to renumbering bugs when a group is inserted later.
