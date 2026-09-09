# Why `java.time` Replaced `Date` and `Calendar`

--> Before Java 8, date/time handling relied on `java.util.Date` and `java.util.Calendar` -- both are famously bad APIs: `Date` is mutable, its months are zero-indexed (`Calendar.JANUARY == 0`), most of its constructors were deprecated decades ago, and neither class is thread-safe, which caused real production bugs when a shared `SimpleDateFormat` instance was used across threads.
--> Java 8 introduced `java.time` (JSR-310, authored by the creator of Joda-Time) -- a complete redesign built on three principles: **immutability** (every operation returns a new object, never mutates the original), **clarity** (separate types for date-only, time-only, date+time, and zoned date+time -- no more one class trying to be everything), and **thread-safety** (immutable objects are inherently safe to share).

# The Core Types at a Glance

| Type | Represents | Example |
|---|---|---|
| `LocalDate` | Date only, no time, no timezone | `2026-08-31` |
| `LocalTime` | Time only, no date, no timezone | `14:30:00` |
| `LocalDateTime` | Date + time, no timezone | `2026-08-31T14:30:00` |
| `ZonedDateTime` | Date + time + timezone | `2026-08-31T14:30:00+05:30[Asia/Kolkata]` |
| `Instant` | A single point on the timeline (UTC epoch-seconds), for machine timestamps | `2026-08-31T09:00:00Z` |
| `Duration` | A time-based amount (hours/minutes/seconds/nanos) | `PT2H30M` |
| `Period` | A date-based amount (years/months/days) | `P1Y2M3D` |

--> **Rule of thumb:** use `LocalDate`/`LocalTime`/`LocalDateTime` for human-facing, "wall clock" scheduling (birthdays, appointments, business hours) where timezone doesn't matter or is implicit; use `ZonedDateTime` when timezone genuinely matters (a meeting across regions); use `Instant` for machine timestamps (logging, "this event happened at exactly this moment on the universal timeline").

# `LocalDate` -- Date Without Time

```java
import java.time.LocalDate;
import java.time.Month;
import java.time.DayOfWeek;

LocalDate today = LocalDate.now();                       // system clock, default timezone
LocalDate specific = LocalDate.of(2026, Month.AUGUST, 31);
LocalDate parsed = LocalDate.parse("2026-08-31");         // ISO-8601 by default

LocalDate nextWeek = today.plusWeeks(1);                  // new object -- today is unchanged
LocalDate lastMonth = today.minusMonths(1);
DayOfWeek dow = today.getDayOfWeek();                     // e.g. MONDAY
boolean isLeap = today.isLeapYear();
LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
```

--> Every `plusX`/`minusX`/`withX` method returns a **new** `LocalDate` -- the original is never mutated. Forgetting to capture the return value (`today.plusDays(1);` without assigning it) is a common beginner mistake that silently does nothing.

# `LocalTime` -- Time Without Date

```java
import java.time.LocalTime;

LocalTime now = LocalTime.now();
LocalTime meeting = LocalTime.of(14, 30);                 // 14:30:00
LocalTime withSeconds = LocalTime.of(14, 30, 15);
LocalTime parsed = LocalTime.parse("09:15:00");

LocalTime later = meeting.plusHours(2).plusMinutes(45);   // 17:15
boolean isBefore = meeting.isBefore(LocalTime.NOON);
```

# `LocalDateTime` -- Combining Both

```java
import java.time.LocalDateTime;

LocalDateTime now = LocalDateTime.now();
LocalDateTime combined = LocalDateTime.of(2026, 8, 31, 14, 30);
LocalDateTime fromParts = LocalDate.of(2026, 8, 31).atTime(14, 30);   // build from a LocalDate + time
LocalDateTime fromParts2 = LocalTime.of(14, 30).atDate(LocalDate.of(2026, 8, 31));

LocalDate justTheDate = combined.toLocalDate();
LocalTime justTheTime = combined.toLocalTime();
```

--> **`LocalDateTime` still has NO timezone.** `LocalDateTime.of(2026, 8, 31, 14, 30)` means "2:30 PM on the wall clock" -- it does not pin down whether that's 2:30 PM in Tokyo or New York. Don't use it for anything that crosses timezones or needs to be compared against a timestamp from another region.

# `ZonedDateTime` -- Date + Time + Timezone

```java
import java.time.ZonedDateTime;
import java.time.ZoneId;

ZoneId kolkata = ZoneId.of("Asia/Kolkata");
ZoneId tokyo = ZoneId.of("Asia/Tokyo");

ZonedDateTime nowInKolkata = ZonedDateTime.now(kolkata);
ZonedDateTime meeting = LocalDateTime.of(2026, 8, 31, 14, 30).atZone(kolkata);

ZonedDateTime sameInstantInTokyo = meeting.withZoneSameInstant(tokyo);  // converts, same instant on timeline
System.out.println(meeting);              // 2026-08-31T14:30+05:30[Asia/Kolkata]
System.out.println(sameInstantInTokyo);   // 2026-08-31T18:00+09:00[Asia/Tokyo]
```

--> **`withZoneSameInstant` vs `withZoneSameLocal`** -- `withZoneSameInstant` keeps the same point on the universal timeline and recalculates the local wall-clock time for the new zone (use this for "what time is it *there* when it's this time *here*"). `withZoneSameLocal` keeps the same numbers (14:30) but reinterprets them in the new zone, producing a DIFFERENT instant -- rarely what you want, easy to misuse by accident.
--> Always use full IANA zone IDs like `"Asia/Kolkata"` or `"America/New_York"`, never fixed offsets like `"+05:30"` as a substitute for a zone -- offsets don't account for daylight saving transitions, but zone IDs do.

# `Instant` -- A Point on the Machine Timeline

```java
import java.time.Instant;

Instant now = Instant.now();                 // UTC, epoch-based
Instant later = now.plusSeconds(3600);
long epochMillis = now.toEpochMilli();

Instant fromMillis = Instant.ofEpochMilli(System.currentTimeMillis());
```

--> `Instant` has no concept of "year" or "month" directly usable for display -- convert to `ZonedDateTime` (`instant.atZone(ZoneId.of("UTC"))`) before showing it to a human.

# `Duration` -- Time-Based Amounts

```java
import java.time.Duration;

Duration twoAndHalfHours = Duration.ofHours(2).plusMinutes(30);
Duration explicit = Duration.of(90, java.time.temporal.ChronoUnit.MINUTES);

LocalDateTime start = LocalDateTime.of(2026, 8, 31, 9, 0);
LocalDateTime end = LocalDateTime.of(2026, 8, 31, 17, 30);
Duration worked = Duration.between(start, end);            // PT8H30M

System.out.println(worked.toHours());        // 8  (truncated, not rounded)
System.out.println(worked.toMinutes());       // 510
System.out.println(worked.getSeconds());      // 30600
```

--> `Duration` is for **time-based** amounts -- hours, minutes, seconds, nanoseconds -- and works with `LocalTime`, `LocalDateTime`, `Instant`, and `ZonedDateTime`. It measures exact elapsed time, so `Duration.between` across a daylight-saving transition still counts real elapsed seconds.

# `Period` -- Date-Based Amounts

```java
import java.time.Period;

Period twoMonthsTenDays = Period.of(0, 2, 10);
Period explicit = Period.ofDays(10);

LocalDate birthDate = LocalDate.of(1990, 5, 15);
LocalDate today = LocalDate.of(2026, 8, 31);
Period age = Period.between(birthDate, today);

System.out.println(age.getYears() + " years, " + age.getMonths() + " months, " + age.getDays() + " days");
```

--> **`Period` is for calendar-based amounts** -- years, months, days -- and only works with date types (`LocalDate`). Unlike `Duration`, `Period` is calendar-aware: "1 month" from Jan 31 correctly lands on Feb 28 (or 29), rather than trying to represent a fixed number of seconds.
--> **Common trap: mixing up `Duration` and `Period`.** `Duration.between(localDate1, localDate2)` does NOT compile in a useful way for pure dates without time -- `Duration` wants a time-aware type. Use `Period.between` for two `LocalDate`s, and `Duration.between` for time-aware types (`LocalDateTime`, `Instant`, `LocalTime`).

# Formatting and Parsing with `DateTimeFormatter`

```java
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Locale;

LocalDateTime dt = LocalDateTime.of(2026, 8, 31, 14, 30, 0);

DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE_TIME;         // built-in constant
DateTimeFormatter custom = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
DateTimeFormatter withLocale = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.ENGLISH);

String formatted1 = dt.format(custom);          // "31-Aug-2026 14:30:00"
String formatted2 = dt.format(withLocale);       // "Monday, 31 August 2026"

LocalDateTime parsedBack = LocalDateTime.parse("31-Aug-2026 14:30:00", custom);
```

--> `DateTimeFormatter` instances are **immutable and thread-safe** -- unlike the old `SimpleDateFormat`, they can safely be shared as `static final` constants across threads without synchronization. Always define formatters once and reuse them rather than recreating them per call.

```java
// Common pattern letters
// y -> year          M -> month        d -> day of month
// H -> hour (0-23)   h -> hour (1-12)  m -> minute      s -> second
// E -> day-of-week name    a -> AM/PM marker
```

--> Parsing failures throw `DateTimeParseException` (unchecked) -- always validate untrusted input strings or wrap parsing in a try/catch when the format isn't guaranteed.

```java
try {
    LocalDate.parse("not-a-date");
} catch (java.time.format.DateTimeParseException e) {
    System.out.println("Invalid date format: " + e.getMessage());
}
```

# Comparing and Querying Dates

```java
LocalDate d1 = LocalDate.of(2026, 8, 31);
LocalDate d2 = LocalDate.of(2026, 12, 25);

boolean before = d1.isBefore(d2);      // true
boolean after = d1.isAfter(d2);        // false
boolean equal = d1.isEqual(d2);        // false
int cmp = d1.compareTo(d2);            // negative

long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(d1, d2);
```

--> `ChronoUnit.between` is a flexible, unit-agnostic alternative to `Period`/`Duration` when you just need a single number (`DAYS.between`, `HOURS.between`, `MONTHS.between`, etc.) rather than a structured breakdown.

# Legacy Interop -- Converting `Date`/`Calendar` to `java.time`

```java
import java.util.Date;
import java.time.Instant;

Date legacyDate = new Date();
Instant instant = legacyDate.toInstant();                 // Date -> Instant
Date backToLegacy = Date.from(instant);                    // Instant -> Date

java.util.Calendar cal = java.util.Calendar.getInstance();
ZonedDateTime zdt = ZonedDateTime.ofInstant(cal.toInstant(), cal.getTimeZone().toZoneId());
```

--> When integrating with older libraries/APIs that still expect `java.util.Date`, convert at the boundary via `Instant` -- don't let `Date` leak into new code beyond that conversion point.

# Common Gotchas

--> **Gotcha 1: `LocalDate.plusMonths` and end-of-month overflow.** `LocalDate.of(2026, 1, 31).plusMonths(1)` gives `2026-02-28`, NOT an invalid `2026-02-31` or a rollover into March -- `java.time` clamps to the last valid day of the target month automatically.
--> **Gotcha 2: Month is 1-indexed**, unlike the old `Calendar` (`Calendar.JANUARY == 0`). `LocalDate.of(2026, 1, 1)` is January 1st, as expected -- one of the deliberate fixes over the legacy API.
--> **Gotcha 3: All `java.time` types are immutable** -- `date.plusDays(5)` does nothing useful unless you capture the return value: `date = date.plusDays(5);`.
--> **Gotcha 4: Comparing a `LocalDateTime` to a `ZonedDateTime` directly doesn't compile** -- they're different types by design, forcing you to be explicit about timezone handling rather than silently comparing apples to oranges.
--> **Gotcha 5: `DateTimeFormatter.ofPattern` pattern letters are case-sensitive and easy to confuse** -- `MM` (month) vs `mm` (minute), `HH` (24-hour) vs `hh` (12-hour, needs an `a` pattern for AM/PM). A swapped case is a very common silent bug.

# Best Practices Summary

--> Prefer the narrowest type that captures what you actually need: `LocalDate` for dates, `LocalTime` for times, `LocalDateTime` when both matter but timezone doesn't, `ZonedDateTime` when timezone matters, `Instant` for raw machine timestamps.
--> Use `Period` for calendar-based differences (years/months/days) and `Duration` for time-based differences (hours/minutes/seconds) -- don't mix them up.
--> Define `DateTimeFormatter` instances once as shared, immutable constants -- they are thread-safe, unlike legacy `SimpleDateFormat`.
--> Convert legacy `Date`/`Calendar` to `java.time` types at API boundaries via `Instant`, and don't let the old types spread through new code.
--> Always handle `DateTimeParseException` when parsing untrusted or externally-sourced date strings.
