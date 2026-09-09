/*
 * I18nLocalizationDemo.java
 *
 * Demonstrates:
 *     1. Locale construction (Locale.US, Locale.FRANCE, Locale.JAPAN, and the
 *        builder-based Locale.Builder form)
 *     2. ResourceBundle lookups against sibling .properties files, including the
 *        fallback/lookup chain (language_COUNTRY -> language -> base)
 *     3. MessageFormat for substituting placeholders into a bundle's raw template
 *        string, instead of unsafe string concatenation
 *     4. NumberFormat.getNumberInstance -- locale-aware decimal/grouping separators
 *     5. NumberFormat.getCurrencyInstance -- locale-aware currency symbol, position,
 *        and precision (including Yen's zero-decimal rounding)
 *     6. DateTimeFormatter.ofLocalizedDate -- locale-aware date formatting via
 *        java.time (preferred over legacy SimpleDateFormat)
 *     7. Locale-aware NUMBER PARSING of user input (contrasted with locale-blind
 *        Double.parseDouble, which the Theory file flags as a common gotcha)
 *     8. At least 3 locales (US, France, Japan) shown side by side for every example
 *
 * Covers Theory chapter:
 *     10) Java/19) Desktop GUI and Internationalization/Theory/05 Internationalization
 *     and Localization in Java.md
 *
 * This file is GENUINELY RUNNABLE, standalone JDK code -- java.util.Locale/ResourceBundle
 * and java.text/java.time.format are all part of the JDK, no external dependencies needed.
 *
 * Uses a properties-based ResourceBundle approach with real, accompanying sibling
 * files (cleaner than inlining bundle data in Java, and demonstrates the actual
 * baseName_language[_COUNTRY].properties file-naming convention from the Theory file):
 *     messages.properties      (default/English fallback)
 *     messages_fr.properties   (French)
 *     messages_ja.properties   (Japanese)
 *
 * IMPORTANT -- because this program loads ResourceBundle files from the classpath,
 * run it with its directory on the classpath (the default when compiling/running
 * from within this folder, as shown below).
 *
 * Compile: javac 05_i18n_localization_demo.java
 * Run:     java I18nLocalizationDemo
 */

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.ResourceBundle;

public class I18nLocalizationDemo {

    // The three locales demonstrated side by side throughout -- deliberately
    // picked to show: a shared-script/different-punctuation pair (US vs a
    // Latin-script European locale) AND a genuinely different script (Japan),
    // per the Theory file's "test against at least one genuinely different
    // locale early" best practice.
    private static final Locale[] DEMO_LOCALES = { Locale.US, Locale.FRANCE, Locale.JAPAN };

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {

        // ---------------------------------------------------------------
        // 1) Locale construction -- a few different ways to obtain one.
        // ---------------------------------------------------------------
        printSection("1) Locale construction");
        Locale us = Locale.US;                       // en_US
        Locale france = Locale.FRANCE;                 // fr_FR
        Locale japan = Locale.JAPAN;                    // ja_JP
        // Modern builder-based construction (preferred since Java 7+ over
        // chained constructors) -- German as spoken in Austria.
        Locale austria = new Locale.Builder().setLanguage("de").setRegion("AT").build();
        System.out.println("us      = " + us + "  (displayName=" + us.getDisplayName() + ")");
        System.out.println("france  = " + france + "  (displayName=" + france.getDisplayName() + ")");
        System.out.println("japan   = " + japan + "  (displayName=" + japan.getDisplayName() + ")");
        System.out.println("austria = " + austria + "  (displayName=" + austria.getDisplayName() + ")");
        System.out.println("JVM default locale (Locale.getDefault()) = " + Locale.getDefault());
        // Expected: us=en_US, france=fr_FR, japan=ja_JP, austria=de_AT, plus
        // whatever locale this machine's JVM defaults to.

        // ---------------------------------------------------------------
        // 2) ResourceBundle -- lookups + fallback chain, across all 3 demo locales.
        // ---------------------------------------------------------------
        printSection("2) ResourceBundle lookups (messages.properties / messages_fr / messages_ja)");
        for (Locale locale : DEMO_LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
            String save = bundle.getString("button.save");
            String cancel = bundle.getString("button.cancel");
            String farewell = bundle.getString("farewell");
            System.out.println(locale + " -> button.save=\"" + save + "\", button.cancel=\"" + cancel
                    + "\", farewell=\"" + farewell + "\"");
        }
        // Expected:
        //   en_US -> button.save="Save", button.cancel="Cancel", farewell="Goodbye!"
        //   fr_FR -> button.save="Enregistrer", button.cancel="Annuler", farewell="Au revoir !"
        //   ja_JP -> button.save="保存", button.cancel="キャンセル", farewell="さようなら！"

        // Fallback chain demo -- request a Canadian-French locale for which NO
        // messages_fr_CA.properties file exists on disk; ResourceBundle falls
        // back to messages_fr.properties (language-only match), NOT all the
        // way to the base messages.properties, since "fr" IS available.
        Locale canadianFrench = new Locale("fr", "CA");
        ResourceBundle fallbackBundle = ResourceBundle.getBundle("messages", canadianFrench);
        System.out.println(canadianFrench + " (no messages_fr_CA.properties on disk) -> button.save=\""
                + fallbackBundle.getString("button.save") + "\"  (fell back to messages_fr.properties)");
        // Expected: fr_CA (no messages_fr_CA.properties on disk) -> button.save="Enregistrer"

        // ---------------------------------------------------------------
        // 3) MessageFormat -- substituting {0} placeholders from a raw bundle
        //    template, instead of concatenating translated fragments (which the
        //    Theory file warns breaks word order/grammar across languages).
        // ---------------------------------------------------------------
        printSection("3) MessageFormat -- parameterized messages, not string concatenation");
        for (Locale locale : DEMO_LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
            String greetingTemplate = bundle.getString("greeting");        // raw: "Hello, {0}!" / etc.
            String greetingMessage = MessageFormat.format(greetingTemplate, "Ada");
            String itemsTemplate = bundle.getString("items.count");
            String itemsMessage = MessageFormat.format(itemsTemplate, 3);
            System.out.println(locale + " -> " + greetingMessage + "   |   " + itemsMessage);
        }
        // Expected:
        //   en_US -> Hello, Ada!   |   You have 3 item(s) in your cart.
        //   fr_FR -> Bonjour, Ada !   |   Vous avez 3 article(s) dans votre panier.
        //   ja_JP -> こんにちは、Adaさん！   |   カートに3点の商品があります。

        // ---------------------------------------------------------------
        // 4) NumberFormat -- locale-aware decimal/grouping separators.
        // ---------------------------------------------------------------
        printSection("4) NumberFormat.getNumberInstance -- decimal/grouping separators differ per locale");
        double amount = 1234567.891;
        for (Locale locale : DEMO_LOCALES) {
            NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
            System.out.println(locale + " -> " + numberFormat.format(amount));
        }
        // Expected (exact digit grouping/rounding depends on the JDK's locale
        // data, but the separator ROLES are the key point being demonstrated):
        //   en_US -> 1,234,567.891   (comma=grouping, period=decimal)
        //   fr_FR -> 1 234 567,891   (space=grouping, comma=decimal)
        //   ja_JP -> 1,234,567.891   (comma=grouping, period=decimal -- same convention as US)

        // ---------------------------------------------------------------
        // 5) Currency -- getCurrencyInstance picks BOTH currency and display
        //    convention (symbol, position, precision) from the locale.
        // ---------------------------------------------------------------
        printSection("5) NumberFormat.getCurrencyInstance -- symbol, position, and precision per locale");
        double price = 1234.5;
        for (Locale locale : DEMO_LOCALES) {
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
            System.out.println(locale + " -> " + currencyFormat.format(price));
        }
        // Expected (exact rounding depends on the JDK's default HALF_EVEN
        // rounding for the amount used -- the key point is the ZERO decimal
        // places for Yen, not the specific digit):
        //   en_US -> $1,234.50
        //   fr_FR -> 1 234,50 €   (symbol AFTER the number, space as grouping separator)
        //   ja_JP -> ￥1,234  (fullwidth yen sign -- Yen has no minor/decimal unit,
        //                          so the JDK's currency formatter drops the fractional part)

        // ---------------------------------------------------------------
        // 6) Dates -- DateTimeFormatter.ofLocalizedDate (modern, preferred over
        //    legacy SimpleDateFormat, which is NOT thread-safe).
        // ---------------------------------------------------------------
        printSection("6) DateTimeFormatter.ofLocalizedDate -- locale-appropriate date formatting");
        LocalDate date = LocalDate.of(2026, 9, 1);
        for (Locale locale : DEMO_LOCALES) {
            DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
            System.out.println(locale + " -> " + date.format(formatter));
        }
        // Expected (JDK/CLDR-data-dependent exact wording, but each is idiomatic
        // for its locale):
        //   en_US -> Sep 1, 2026
        //   fr_FR -> 1 sept. 2026
        //   ja_JP -> 2026/09/01   (year-month-day order, common in Japanese formatting)

        // ---------------------------------------------------------------
        // 7) Locale-aware PARSING of user input -- contrasted with the
        //    locale-blind Double.parseDouble the Theory file's "Common
        //    Gotchas" section warns against.
        // ---------------------------------------------------------------
        printSection("7) Locale-aware number PARSING -- avoiding the decimal/grouping-separator gotcha");
        // NOTE: the French locale's actual grouping separator character is a
        // NON-BREAKING space (U+00A0), not a plain space -- NumberFormat.parse
        // stops at the first character it doesn't recognize, so a plain space
        // here would silently truncate the parse instead of throwing. Building
        // this string with the exact separator the formatter itself produced
        // (see section 5's output) is the realistic way this input would arrive
        // if it came from round-tripping a value this same JDK formatted.
        String frenchInput = "1" + ' ' + "234,5";   // French convention: NBSP=grouping, comma=decimal
        String usInput = "1,234.5";                        // US convention: comma=grouping, period=decimal
        try {
            Number parsedFromFrench = NumberFormat.getInstance(Locale.FRANCE).parse(frenchInput);
            Number parsedFromUs = NumberFormat.getInstance(Locale.US).parse(usInput);
            System.out.println("Parsed \"" + frenchInput + "\" with Locale.FRANCE -> " + parsedFromFrench);
            System.out.println("Parsed \"" + usInput + "\" with Locale.US -> " + parsedFromUs);
            // Expected: both parse to the numeric value 1234.5 -- despite looking
            // completely different as raw text, because each was parsed with the
            // matching locale's separator conventions.
        } catch (ParseException e) {
            System.out.println("Unexpected parse failure: " + e.getMessage());
        }
        // Contrast -- naively parsing the FRENCH-formatted string with
        // Double.parseDouble (which only understands the "." decimal convention,
        // per the Theory file's gotcha) fails outright instead of silently
        // misreading it, since this text isn't valid Java double syntax at all:
        try {
            Double.parseDouble(frenchInput);
            System.out.println("Unexpected: Double.parseDouble accepted \"" + frenchInput + "\"");
        } catch (NumberFormatException expected) {
            System.out.println("Double.parseDouble(french-formatted input) threw as expected (locale-blind): "
                    + expected.getMessage());
        }
        // Expected: Double.parseDouble(french-formatted input) threw as expected
        // (locale-blind): For input string: "1 234,5"

        printSection("8) Recap");
        System.out.println("Every value above was produced by threading an explicit Locale through");
        System.out.println("ResourceBundle/NumberFormat/DateTimeFormatter -- never a hardcoded pattern");
        System.out.println("string or Locale.getDefault(), per the Theory file's best-practices summary.");
        System.out.println("\nAll internationalization and localization demos completed.");
    }
}
