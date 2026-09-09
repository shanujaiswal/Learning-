# i18n and l10n -- Definitions and Why They're Separate Concerns

--> **Internationalization (i18n)** is DESIGNING and BUILDING an application so it CAN be adapted to different languages/regions WITHOUT code changes -- externalizing text, avoiding hardcoded date/number formats, avoiding assumptions baked into code (e.g. "names always have exactly a first and last part," "text always reads left-to-right"). The "18" in "i18n" counts the 18 letters between the first "i" and the last "n" -- a numeronym, same pattern as "l10n."
--> **Localization (l10n)** is the ACT of actually adapting an internationalized application to a SPECIFIC locale -- translating the externalized text, adjusting date/currency formats, sometimes adjusting layout/images for cultural fit. i18n is a one-time ENGINEERING investment; l10n is an ONGOING, repeatable process (translators, reviewers) that i18n makes possible without re-touching code.
--> **The relationship** -- you cannot localize an app that wasn't internationalized (hardcoded English strings scattered through the codebase can't be swapped per-locale without a rewrite), but internationalizing an app doesn't automatically localize it (you still need actual translations and locale-specific formatting rules supplied). Java's standard library gives strong built-in support for the i18n half; l10n's translation work is a content/process problem layered on top.

# The Locale Class -- Identifying "Which Region/Language"

--> **`java.util.Locale`** represents a specific geographical, political, or cultural region -- it's the KEY used throughout the rest of Java's i18n APIs (`ResourceBundle`, `NumberFormat`, `DateTimeFormatter`, etc.) to decide which formatting/translation rules apply. A `Locale` is typically a LANGUAGE code (ISO 639, lowercase, e.g. `en`, `fr`, `ja`) optionally combined with a COUNTRY/region code (ISO 3166, uppercase, e.g. `US`, `FR`, `JP`).

```java
Locale us = Locale.US;                      // en_US
Locale uk = Locale.UK;                      // en_GB
Locale france = Locale.FRANCE;               // fr_FR
Locale custom = new Locale("de", "AT");     // German as spoken in Austria
Locale current = Locale.getDefault();        // the JVM's default locale (from the OS, unless overridden)

// Modern builder-based construction (preferred since Java 7+ over chained constructors)
Locale japan = new Locale.Builder()
        .setLanguage("ja")
        .setRegion("JP")
        .build();
```

--> **Why language AND country both matter** -- `en_US` and `en_GB` share a language but differ in spelling conventions, date formats (`MM/dd/yyyy` vs `dd/MM/yyyy`), and currency symbol placement/default currency (`$` vs `£`). Locale-sensitive formatting APIs key off the FULL locale, not just the language, precisely to capture these regional differences within the same language.
--> **`Locale.getDefault()`** returns the JVM's default locale, normally inherited from the host OS's settings at JVM startup -- relying on it implicitly for a server-side app is a common mistake (see Gotchas below), since a server's OS locale has nothing to do with any individual user's actual preference.
--> **Locale from an HTTP request (web context)** -- in a Spring Boot web app, the `Accept-Language` HTTP header (which browsers send based on the user's browser/OS language settings) is the typical source of "what locale should this request be handled in," resolved via Spring's `LocaleResolver` -- distinct from the low-level `java.util.Locale` API itself, but built entirely on top of it.

# ResourceBundle -- Externalizing Translatable Text

--> **`ResourceBundle`** is Java's mechanism for looking up locale-specific values (almost always translated strings, but can be any key-value data) by KEY, automatically selecting the right underlying file based on a given `Locale`. The most common backing format is `.properties` files, one per locale, sharing a common BASE NAME.

```properties
# messages.properties (the DEFAULT/fallback bundle -- used when no more specific match exists)
greeting=Hello, {0}!
farewell=Goodbye!
button.save=Save
button.cancel=Cancel
```

```properties
# messages_fr.properties (French)
greeting=Bonjour, {0} !
farewell=Au revoir !
button.save=Enregistrer
button.cancel=Annuler
```

```properties
# messages_fr_CA.properties (Canadian French -- overrides just what differs from messages_fr.properties)
farewell=À la prochaine !
```

```java
ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.FRANCE);
String save = bundle.getString("button.save");     // "Enregistrer"

ResourceBundle englishBundle = ResourceBundle.getBundle("messages", Locale.US);
String saveEn = englishBundle.getString("button.save");   // "Save" (falls back to messages.properties)
```

--> **The fallback/lookup chain** -- `ResourceBundle.getBundle("messages", new Locale("fr", "CA"))` searches, in order: `messages_fr_CA.properties` -> `messages_fr.properties` -> `messages.properties` (the base, no-suffix file) -> (if even that's missing) the JVM's own default locale's bundle -> an exception if NOTHING matches. This lets you supply a full translation for the general language (`fr`) and override ONLY the handful of strings that genuinely differ for a specific region (`fr_CA`), rather than duplicating every key in every regional variant file.
--> **Naming convention** -- `baseName_language_COUNTRY.properties` (e.g. `messages_de_DE.properties`), with the plain `baseName.properties` acting as both the ultimate fallback and (by convention) often written in the project's primary development language (commonly English).
--> **Parameterized messages with `MessageFormat`** -- `ResourceBundle.getString()` returns the RAW string, including any `{0}`, `{1}` placeholders -- substituting actual values requires `MessageFormat` explicitly:

```java
String template = bundle.getString("greeting");           // "Hello, {0}!"
String message = MessageFormat.format(template, "Alice");  // "Hello, Alice!"
```

--> **Encoding gotcha** -- `.properties` files are read as **ISO-8859-1 (Latin-1)** by default on older Java behavior; non-Latin characters (Japanese, Cyrillic, most accented characters outside Latin-1) traditionally needed to be escaped as `\uXXXX` Unicode escapes (commonly generated by the `native2ascii` tool) unless you configure UTF-8 explicitly. Since **Java 9**, `ResourceBundle.getBundle` reading `.properties` files defaults to UTF-8, removing most of this historical pain -- but it's still worth being aware of when working with an older codebase or an unusually-configured build.
--> **`ListResourceBundle`** is a code-based alternative to `.properties` files -- a Java class overriding `getContents()` to return key-value pairs directly -- useful when values aren't simple strings (e.g. locale-specific icons, formatted objects) but less common than `.properties` for straightforward text translation.

# Formatting Numbers, Dates, and Currency per Locale

--> Hardcoding format patterns (`"MM/dd/yyyy"`, manually prepending `"$"`) bakes in ONE culture's conventions -- Java's `java.text` (and, for dates, `java.time`) packages provide LOCALE-AWARE formatters that adapt automatically.

## Numbers -- NumberFormat

```java
double amount = 1234567.891;

NumberFormat usFormat = NumberFormat.getNumberInstance(Locale.US);
System.out.println(usFormat.format(amount));      // 1,234,567.891

NumberFormat deFormat = NumberFormat.getNumberInstance(Locale.GERMANY);
System.out.println(deFormat.format(amount));      // 1.234.567,891  (comma/period roles SWAPPED vs US)
```

--> **This swap (comma vs period as the decimal separator) is exactly why hardcoding number formatting is dangerous** -- a string like `"1.234"` means "one point two three four" in the US convention but "one thousand two hundred thirty-four" in much of continental Europe; locale-aware parsing/formatting is what prevents this class of silent data-corruption bug when accepting numeric input from users in different regions.

## Currency -- NumberFormat.getCurrencyInstance

```java
NumberFormat usCurrency = NumberFormat.getCurrencyInstance(Locale.US);
System.out.println(usCurrency.format(1234.5));    // $1,234.50

NumberFormat jpCurrency = NumberFormat.getCurrencyInstance(Locale.JAPAN);
System.out.println(jpCurrency.format(1234.5));    // ¥1,235  (Yen has no minor/decimal unit, rounds automatically)

NumberFormat frCurrency = NumberFormat.getCurrencyInstance(Locale.FRANCE);
System.out.println(frCurrency.format(1234.5));    // 1 234,50 €  (symbol AFTER the number, space as grouping separator)
```

--> **`getCurrencyInstance(locale)` picks BOTH the currency AND the display convention from the locale** -- symbol choice, symbol position (before/after), decimal precision (Yen has none; most Western currencies have two decimal places), and grouping separators all come from the locale automatically. Passing the WRONG locale for the actual currency you mean (e.g. formatting a USD amount with a French locale) produces a nonsensical result (Euro symbol on a dollar amount) -- currency formatting needs to consider both "what currency is this actually in" and "how should numbers look to this reader," which aren't always the same locale in real multi-currency applications (handle that case with `Currency` object overrides on the formatter, not by picking a "creative" locale).

## Dates and Times -- DateTimeFormatter (modern) vs java.text.DateFormat (legacy)

```java
LocalDate date = LocalDate.of(2026, 9, 1);

DateTimeFormatter usFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US);
System.out.println(date.format(usFormatter));      // Sep 1, 2026

DateTimeFormatter frFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.FRANCE);
System.out.println(date.format(frFormatter));      // 1 sept. 2026
```

```java
// Legacy java.text API -- still seen in older codebases, java.time/DateTimeFormatter is preferred in new code
Date legacyDate = new Date();
DateFormat legacyFormat = DateFormat.getDateInstance(DateFormat.LONG, Locale.GERMANY);
System.out.println(legacyFormat.format(legacyDate));   // 1. September 2026
```

--> **`java.text.DateFormat`/`SimpleDateFormat`** are the ORIGINAL locale-aware date formatting classes (pre-Java 8), operating on the legacy `java.util.Date`/`Calendar` types -- still common in older codebases, but `SimpleDateFormat` is notoriously NOT THREAD-SAFE (a shared instance used across threads can produce corrupted output) and considered legacy since `java.time` (Java 8+) was introduced.
--> **`java.time.format.DateTimeFormatter`** is the modern replacement, works with `java.time` types (`LocalDate`, `LocalDateTime`, `ZonedDateTime`), and IS thread-safe/immutable -- `ofLocalizedDate(FormatStyle)` (with `SHORT`/`MEDIUM`/`LONG`/`FULL` style constants) gives locale-appropriate formatting without hand-writing a pattern string, exactly like `NumberFormat.getNumberInstance` does for numbers. Prefer this over `SimpleDateFormat` in any new code.

# Best Practices for i18n-Ready Java Applications

--> **Never concatenate translated fragments to build a sentence** -- `bundle.getString("you.have") + count + bundle.getString("items")` breaks in languages with different word order or grammar (e.g. verb-final languages, or languages needing different plural forms per count). Use a single, complete `MessageFormat` template with placeholders instead: `"Vous avez {0} article(s)"`.
--> **Handle PLURALIZATION explicitly** -- "1 item" vs "2 items" is trivial in English (just append "s"), but many languages have MORE than two plural forms (some Slavic languages distinguish singular / few / many / other) -- `java.text.ChoiceFormat` (legacy) or, for serious i18n needs, a dedicated library implementing Unicode CLDR plural rules is the correct tool; don't assume English's singular/plural binary applies universally.
--> **Never hardcode date/number/currency format PATTERNS in application code** -- always go through a `Locale`-driven formatter (`NumberFormat`, `DateTimeFormatter`) rather than a fixed pattern string like `"MM/dd/yyyy"`, even if the app currently only ships in one locale -- retrofitting this later means touching every call site.
--> **Externalize ALL user-facing text to resource bundles from the start**, even for a single-locale app -- adding a second language later to an app with strings scattered through the codebase is a much larger, riskier refactor than starting with `ResourceBundle` lookups from day one.
--> **Don't assume text expansion/contraction** -- translated text can be significantly LONGER than the source (German text is often 20-30% longer than English for the same meaning) -- UI layouts (especially fixed-width Swing/JavaFX components) need to accommodate this, not assume a label's English width is universally safe.
--> **Be careful with `Locale.getDefault()` in server-side/multi-user code** -- a web server handles requests for MANY different users, potentially in many different locales SIMULTANEOUSLY; using the JVM-wide default locale for formatting responses ignores the actual requesting user's preference entirely. Always thread the REQUEST-specific `Locale` explicitly through formatting calls (e.g. via Spring's `LocaleContextHolder`/`LocaleResolver` in a web app) rather than relying on the JVM's single global default.
--> **Test with a "pseudo-locale" or at least one genuinely different locale early** -- a right-to-left language (Arabic, Hebrew) or a language with much longer average word length (German, Finnish) surfaces layout and hardcoded-assumption bugs far earlier than testing only in the original development locale.

# Common Gotchas

--> **Assuming one `.properties` fallback file is "good enough" forever** -- shipping only `messages.properties` (implicitly English) works until the FIRST localization request arrives, at which point every string needs auditing for whether it was ever concatenated/hardcoded outside the bundle -- much cheaper to build the discipline in from the start.
--> **Mixing up decimal/grouping separators when parsing user input** -- accepting a raw numeric string from a form field and parsing it with `Double.parseDouble` ignores locale entirely (it only understands the `.`-as-decimal convention) -- use `NumberFormat.getInstance(locale).parse(input)` for locale-aware parsing of user-entered numbers.
--> **`SimpleDateFormat` shared across threads** -- a `static final SimpleDateFormat` field reused across concurrent requests is a classic concurrency bug (it's mutable, stateful, NOT thread-safe) producing garbled or wrong dates under load; use `java.time.DateTimeFormatter` instead, or create a new `SimpleDateFormat` per use/thread if stuck maintaining legacy code.
--> **Relying on `Locale.getDefault()` server-side** -- covered above; it reflects the SERVER's environment, not any individual request's user.
--> **Forgetting UTF-8 considerations on `.properties` files in an older Java/build setup** -- if a resource bundle displays `?` characters or mojibake instead of expected non-Latin characters, check whether the encoding is genuinely UTF-8 end-to-end (both the file's actual bytes and how it's being read) -- especially relevant on Java 8 and earlier where UTF-8 wasn't yet the `ResourceBundle` default.

# Best Practices Summary

--> Treat i18n as a day-one architectural decision, not a later retrofit -- externalize text and avoid hardcoded formats from the very first feature.
--> Use `ResourceBundle` + `.properties` files (or an equivalent, e.g. a Spring `MessageSource`) for all user-facing text, structured with a clear base name and fallback chain.
--> Always format numbers, currency, and dates through a `Locale`-aware API (`NumberFormat`, `DateTimeFormatter`) -- never a hardcoded pattern string.
--> Use `MessageFormat` (or an equivalent templating approach) for parameterized/composed messages instead of string concatenation.
--> Prefer `java.time`/`DateTimeFormatter` over legacy `Date`/`SimpleDateFormat` in new code -- immutable, thread-safe, and locale-aware by design.
--> In server-side/multi-user contexts, always thread the request-specific `Locale` explicitly through formatting logic rather than relying on the JVM's global default.
--> Plan UI layouts to tolerate text length variation across languages, and test against at least one genuinely different locale early rather than only at release time.
