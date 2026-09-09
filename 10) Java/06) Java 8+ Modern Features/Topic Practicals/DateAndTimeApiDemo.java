/*
 * DateAndTimeApiDemo.java
 *
 * Demonstrates:
 *     1. LocalDate -- creation, arithmetic, queries
 *     2. LocalTime -- creation, arithmetic
 *     3. LocalDateTime -- combining date + time
 *     4. ZonedDateTime -- timezone-aware date/time, withZoneSameInstant
 *     5. Duration -- time-based amounts, Duration.between
 *     6. Period -- date-based amounts, Period.between (age calculation)
 *     7. DateTimeFormatter -- formatting and parsing, custom patterns
 *     8. Legacy Date/Calendar interop via Instant
 *     9. Common gotchas: end-of-month overflow, immutability
 *
 * Covers Theory chapter:
 *     06) Java 8+ Modern Features/Theory/05 Date and Time API.md
 *
 * Compile & run:
 *     javac 05_date_and_time_api.java
 *     java DateAndTimeApiDemo
 */

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;

public class DateAndTimeApiDemo {

    public static void main(String[] args) {
        printSection("1) LocalDate -- creation, arithmetic, queries");
        localDateDemo();

        printSection("2) LocalTime -- creation, arithmetic");
        localTimeDemo();

        printSection("3) LocalDateTime -- combining date + time");
        localDateTimeDemo();

        printSection("4) ZonedDateTime -- timezones and withZoneSameInstant");
        zonedDateTimeDemo();

        printSection("5) Duration -- time-based amounts");
        durationDemo();

        printSection("6) Period -- date-based amounts (age calculation)");
        periodDemo();

        printSection("7) DateTimeFormatter -- formatting and parsing");
        formatterDemo();

        printSection("8) Legacy Date/Calendar interop via Instant");
        legacyInteropDemo();

        printSection("9) Common gotchas: end-of-month overflow, immutability");
        gotchasDemo();

        System.out.println("\nAll Date and Time API demos completed.");
    }

    private static void localDateDemo() {
        LocalDate today = LocalDate.of(2026, 8, 31);   // fixed for reproducible output
        LocalDate specific = LocalDate.of(2026, Month.AUGUST, 31);
        LocalDate parsed = LocalDate.parse("2026-08-31");

        System.out.println("today (fixed)   = " + today);
        System.out.println("specific        = " + specific);
        System.out.println("parsed from ISO = " + parsed);
        System.out.println("today.equals(specific) = " + today.equals(specific));

        LocalDate nextWeek = today.plusWeeks(1);
        LocalDate lastMonth = today.minusMonths(1);
        System.out.println("today.plusWeeks(1)  = " + nextWeek + "  (today itself unchanged: " + today + ")");
        System.out.println("today.minusMonths(1) = " + lastMonth);

        System.out.println("Day of week   = " + today.getDayOfWeek());
        System.out.println("Is leap year  = " + today.isLeapYear());
        System.out.println("Length of month = " + today.lengthOfMonth());

        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        System.out.println("End of this month = " + endOfMonth);
    }

    private static void localTimeDemo() {
        LocalTime meeting = LocalTime.of(14, 30);
        LocalTime withSeconds = LocalTime.of(14, 30, 15);
        LocalTime parsed = LocalTime.parse("09:15:00");

        System.out.println("meeting     = " + meeting);
        System.out.println("withSeconds = " + withSeconds);
        System.out.println("parsed      = " + parsed);

        LocalTime later = meeting.plusHours(2).plusMinutes(45);
        System.out.println("meeting.plusHours(2).plusMinutes(45) = " + later);

        boolean isBeforeNoon = meeting.isBefore(LocalTime.NOON);
        System.out.println("meeting.isBefore(NOON) = " + isBeforeNoon);
    }

    private static void localDateTimeDemo() {
        LocalDateTime combined = LocalDateTime.of(2026, 8, 31, 14, 30);
        LocalDateTime fromDate = LocalDate.of(2026, 8, 31).atTime(14, 30);
        LocalDateTime fromTime = LocalTime.of(14, 30).atDate(LocalDate.of(2026, 8, 31));

        System.out.println("combined  = " + combined);
        System.out.println("fromDate  = " + fromDate);
        System.out.println("fromTime  = " + fromTime);
        System.out.println("All three equal? " + (combined.equals(fromDate) && fromDate.equals(fromTime)));

        System.out.println("toLocalDate() = " + combined.toLocalDate());
        System.out.println("toLocalTime() = " + combined.toLocalTime());
    }

    private static void zonedDateTimeDemo() {
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        ZoneId newYork = ZoneId.of("America/New_York");

        ZonedDateTime meeting = LocalDateTime.of(2026, 8, 31, 14, 30).atZone(kolkata);
        ZonedDateTime sameInstantTokyo = meeting.withZoneSameInstant(tokyo);
        ZonedDateTime sameInstantNewYork = meeting.withZoneSameInstant(newYork);

        System.out.println("Meeting in Kolkata:      " + meeting);
        System.out.println("Same instant in Tokyo:   " + sameInstantTokyo);
        System.out.println("Same instant in New York:" + sameInstantNewYork);

        // Contrast withZoneSameLocal -- same wall-clock numbers, DIFFERENT instant
        ZonedDateTime sameLocalTokyo = meeting.withZoneSameLocal(tokyo);
        System.out.println("withZoneSameLocal (same numbers, different instant): " + sameLocalTokyo);

        boolean sameInstant = meeting.toInstant().equals(sameInstantTokyo.toInstant());
        boolean sameInstantLocal = meeting.toInstant().equals(sameLocalTokyo.toInstant());
        System.out.println("meeting and sameInstantTokyo represent the same instant? " + sameInstant);
        System.out.println("meeting and sameLocalTokyo represent the same instant?   " + sameInstantLocal);
    }

    private static void durationDemo() {
        Duration twoAndHalfHours = Duration.ofHours(2).plusMinutes(30);
        System.out.println("Duration.ofHours(2).plusMinutes(30) = " + twoAndHalfHours);

        LocalDateTime start = LocalDateTime.of(2026, 8, 31, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 31, 17, 30);
        Duration worked = Duration.between(start, end);

        System.out.println("Workday start = " + start + ", end = " + end);
        System.out.println("Duration.between -> " + worked);
        System.out.println("worked.toHours()    = " + worked.toHours());
        System.out.println("worked.toMinutes()  = " + worked.toMinutes());
        System.out.println("worked.getSeconds() = " + worked.getSeconds());

        Duration explicitMinutes = Duration.of(90, ChronoUnit.MINUTES);
        System.out.println("Duration.of(90, MINUTES) = " + explicitMinutes);
    }

    private static void periodDemo() {
        Period twoMonthsTenDays = Period.of(0, 2, 10);
        System.out.println("Period.of(0, 2, 10) = " + twoMonthsTenDays);

        LocalDate birthDate = LocalDate.of(1990, 5, 15);
        LocalDate referenceDate = LocalDate.of(2026, 8, 31);
        Period age = Period.between(birthDate, referenceDate);

        System.out.println("Birth date = " + birthDate + ", reference date = " + referenceDate);
        System.out.println("Age (Period.between) = " + age.getYears() + " years, "
                + age.getMonths() + " months, " + age.getDays() + " days");

        long totalDaysBetween = ChronoUnit.DAYS.between(birthDate, referenceDate);
        System.out.println("ChronoUnit.DAYS.between (raw day count) = " + totalDaysBetween);
    }

    private static void formatterDemo() {
        LocalDateTime dt = LocalDateTime.of(2026, 8, 31, 14, 30, 0);

        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        DateTimeFormatter custom = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
        DateTimeFormatter withLocale = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.ENGLISH);

        System.out.println("ISO_LOCAL_DATE_TIME -> " + dt.format(iso));
        System.out.println("Custom pattern      -> " + dt.format(custom));
        System.out.println("With locale         -> " + dt.format(withLocale));

        String formatted = dt.format(custom);
        LocalDateTime parsedBack = LocalDateTime.parse(formatted, custom);
        System.out.println("Parsed back from custom format -> " + parsedBack + " (equals original: " + parsedBack.equals(dt) + ")");

        try {
            LocalDate.parse("not-a-date");
        } catch (DateTimeParseException e) {
            System.out.println("Parsing \"not-a-date\" threw DateTimeParseException: " + e.getMessage());
        }
    }

    private static void legacyInteropDemo() {
        Date legacyDate = new Date(1756636200000L);   // fixed epoch millis for reproducibility
        Instant instant = legacyDate.toInstant();
        Date backToLegacy = Date.from(instant);

        System.out.println("Legacy java.util.Date -> Instant: " + instant);
        System.out.println("Instant -> back to java.util.Date: " + backToLegacy);
        System.out.println("Round-trip equal? " + legacyDate.equals(backToLegacy));

        ZonedDateTime zdtFromInstant = instant.atZone(ZoneId.of("UTC"));
        System.out.println("Instant viewed as ZonedDateTime (UTC): " + zdtFromInstant);
    }

    private static void gotchasDemo() {
        // Gotcha: end-of-month overflow clamps instead of rolling over
        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        LocalDate plusOneMonth = jan31.plusMonths(1);
        System.out.println("Jan 31 + 1 month = " + plusOneMonth + " (clamped to Feb's last day, not Mar 3)");

        // Gotcha: immutability -- calling a method without reassigning does nothing useful
        LocalDate date = LocalDate.of(2026, 8, 31);
        date.plusDays(5);   // return value discarded -- date is UNCHANGED
        System.out.println("After date.plusDays(5) WITHOUT reassignment, date is still: " + date);
        date = date.plusDays(5);   // correct usage -- reassign
        System.out.println("After date = date.plusDays(5), date is now: " + date);

        // Gotcha: month is 1-indexed, unlike legacy Calendar
        LocalDate jan1 = LocalDate.of(2026, 1, 1);
        System.out.println("LocalDate.of(2026, 1, 1) is January (1-indexed, unlike Calendar.JANUARY==0): " + jan1.getMonth());
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
