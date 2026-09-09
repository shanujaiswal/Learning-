/*
 * ConfigurationManagementDemo.java
 *
 * Demonstrates, fully runnable with ZERO external dependencies (java.util.Properties,
 * System.getProperty, and System.getenv all ship in the JDK itself):
 *     1. Why hardcoding environment-specific values into source code is a problem
 *     2. Loading configuration from a java.util.Properties source (here, an in-memory
 *        String standing in for a real application.properties file on disk/classpath)
 *     3. Reading values with sensible fallback defaults, and parsing non-String types
 *     4. A YAML configuration example, shown as an illustrative comment block only --
 *        parsing REAL YAML needs a library (SnakeYAML, Jackson-YAML) not guaranteed
 *        to be on a bare JDK classpath, so it is not parsed here, just shown for comparison
 *     5. Environment-specific profile switching -- selecting "dev"/"staging"/"prod"
 *        config via System.getProperty (a -D flag) with a System.getenv fallback,
 *        and layering base-config + profile-override the way Spring Boot's
 *        application.yml + application-{profile}.yml pattern does conceptually
 *     6. Secrets management notes -- illustrated with comments and a safe pattern
 *        (never hardcode secrets; read them from an environment variable, fail fast
 *        if missing, and never log the actual value)
 *
 * Covers Theory chapter:
 *     10) Java/13) Logging and Configuration Management/Theory/05 Configuration Management in Java Applications.md
 *
 * Compile: javac ConfigurationManagementDemo.java
 * Run:     java ConfigurationManagementDemo
 *
 * Optional experiment: run with a system property to switch the active profile, e.g.
 *     java -Dapp.profile=prod ConfigurationManagementDemo
 * (with no -D flag given, it falls back to checking the APP_PROFILE environment
 * variable, and finally defaults to "dev" if neither is set.)
 */

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

public class ConfigurationManagementDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) throws IOException {

        // ---------------------------------------------------------------
        // 1) Why hardcoding doesn't scale -- quick illustration
        // ---------------------------------------------------------------
        printSection("1) The problem with hardcoding environment-specific values");
        System.out.println("BAD:  private static final String DB_URL = \"jdbc:postgresql://prod-host/orders\";");
        System.out.println("      -- changing this for staging/dev means editing source, recompiling,");
        System.out.println("         and redeploying; the SAME compiled .jar can't be promoted unchanged");
        System.out.println("         across environments, and a hardcoded secret would leak into version control.");
        System.out.println("GOOD: read it from externalized configuration at startup instead (see below).");

        // ---------------------------------------------------------------
        // 2) Loading configuration via java.util.Properties
        // ---------------------------------------------------------------
        printSection("2) Loading configuration from a .properties source");
        // In a real application this would typically be:
        //     try (InputStream in = ConfigurationManagementDemo.class
        //             .getClassLoader().getResourceAsStream("application.properties")) {
        //         props.load(in);
        //     }
        // Here we use an in-memory String via StringReader purely so this file
        // stays a single, self-contained, zero-dependency demo.
        String propertiesFileContents =
                "app.name=Order Service\n" +
                "app.version=1.4.2\n" +
                "server.port=8080\n" +
                "db.url=jdbc:postgresql://localhost:5432/orders\n" +
                "db.username=app_user\n" +
                "db.pool.max-size=10\n" +
                "feature.new-checkout-flow.enabled=false\n";

        Properties props = new Properties();
        try (StringReader reader = new StringReader(propertiesFileContents)) {
            props.load(reader);
        }

        System.out.println("app.name          = " + props.getProperty("app.name"));
        System.out.println("app.version       = " + props.getProperty("app.version"));
        System.out.println("db.url            = " + props.getProperty("db.url"));

        // ---------------------------------------------------------------
        // 3) Reading with fallback defaults, and parsing non-String types
        // ---------------------------------------------------------------
        printSection("3) Fallback defaults and parsing non-String values");
        // getProperty(key, default) returns the default if the key is absent --
        // note "db.pool.max-size" is just a NAMING CONVENTION with dots; Properties
        // has no real nested structure, unlike YAML (see section 4 below).
        int poolSize = Integer.parseInt(props.getProperty("db.pool.max-size", "5"));
        boolean newCheckoutEnabled = Boolean.parseBoolean(
                props.getProperty("feature.new-checkout-flow.enabled", "false"));
        // A key that was never set at all -- demonstrates the fallback actually firing.
        String missingKeyWithFallback = props.getProperty("cache.ttl-seconds", "300");

        System.out.println("db.pool.max-size (parsed int)              = " + poolSize);
        System.out.println("feature.new-checkout-flow.enabled (bool)   = " + newCheckoutEnabled);
        System.out.println("cache.ttl-seconds (missing key, fallback)  = " + missingKeyWithFallback);

        // ---------------------------------------------------------------
        // 4) YAML -- illustrative only, NOT parsed here (needs SnakeYAML/Jackson-YAML,
        // not guaranteed to be present on a bare JDK classpath)
        // ---------------------------------------------------------------
        printSection("4) YAML configuration -- illustrative comparison only, not parsed in this demo");
        System.out.println("The equivalent configuration above, expressed as YAML (application.yml),");
        System.out.println("would look like this -- note genuine nesting and a native list, both of which");
        System.out.println("flat .properties files can only fake via dot-naming or numbered keys:");
        System.out.println();
        System.out.println("    app:");
        System.out.println("      name: Order Service");
        System.out.println("      version: 1.4.2");
        System.out.println();
        System.out.println("    server:");
        System.out.println("      port: 8080");
        System.out.println();
        System.out.println("    db:");
        System.out.println("      url: jdbc:postgresql://localhost:5432/orders");
        System.out.println("      username: app_user");
        System.out.println("      pool:");
        System.out.println("        max-size: 10");
        System.out.println();
        System.out.println("    feature:");
        System.out.println("      new-checkout-flow:");
        System.out.println("        enabled: false");
        System.out.println();
        System.out.println("    allowed-origins:");
        System.out.println("      - https://app.example.com");
        System.out.println("      - https://admin.example.com");
        System.out.println();
        System.out.println("(Reminder: YAML indentation is significant -- mixing tabs/spaces or an");
        System.out.println(" inconsistent indent level silently changes the parsed structure.)");

        // ---------------------------------------------------------------
        // 5) Environment-specific profile switching
        // ---------------------------------------------------------------
        printSection("5) Environment-specific profile switching");
        // Precedence used here (highest wins), mirroring the general layered-config
        // idea from the theory chapter: JVM system property > environment variable > default.
        String profile = System.getProperty("app.profile");
        if (profile == null) {
            profile = System.getenv("APP_PROFILE");
        }
        if (profile == null) {
            profile = "dev"; // sensible local default when nothing else was specified
        }
        System.out.println("Active profile resolved to: " + profile);
        System.out.println("(Try: java -Dapp.profile=prod ConfigurationManagementDemo, or set");
        System.out.println(" the APP_PROFILE environment variable, to see this change.)");

        // Layer base config + profile-specific overrides, exactly the base-plus-override
        // pattern described in the theory chapter (application.yml + application-{profile}.yml).
        Properties effectiveConfig = new Properties();
        effectiveConfig.putAll(props); // start from the shared/base values loaded in section 2

        switch (profile) {
            case "prod" -> {
                effectiveConfig.setProperty("logging.level", "WARN");
                effectiveConfig.setProperty("db.url", "jdbc:postgresql://prod-host:5432/orders");
            }
            case "staging" -> {
                effectiveConfig.setProperty("logging.level", "INFO");
                effectiveConfig.setProperty("db.url", "jdbc:postgresql://staging-host:5432/orders");
            }
            default -> { // "dev" and anything unrecognized falls back to verbose local defaults
                effectiveConfig.setProperty("logging.level", "DEBUG");
                // db.url stays as the localhost value already loaded from the base properties
            }
        }
        System.out.println("Effective logging.level for profile '" + profile + "' = "
                + effectiveConfig.getProperty("logging.level"));
        System.out.println("Effective db.url for profile '" + profile + "'        = "
                + effectiveConfig.getProperty("db.url"));

        // ---------------------------------------------------------------
        // 6) Secrets management -- notes and a safe pattern
        // ---------------------------------------------------------------
        printSection("6) Secrets management basics");
        // -----------------------------------------------------------------
        // NEVER do this:
        //     private static final String DB_PASSWORD = "hunter2";
        // A hardcoded secret ends up committed to version control, visible to
        // everyone with repo access, forever (git history retains old commits
        // even after the line is later removed).
        //
        // Best practices (see theory chapter for the full table):
        //   - Read secrets from environment variables (or a dedicated secrets
        //     manager: HashiCorp Vault, AWS Secrets Manager, Azure Key Vault,
        //     Kubernetes Secrets) -- never from a checked-in config file's
        //     literal value.
        //   - In checked-in config, use placeholder/reference syntax like
        //     ${DB_PASSWORD}, documenting WHAT is needed without the value itself.
        //   - Fail fast and loudly at startup if a required secret is missing,
        //     rather than letting a null surface as a confusing NPE deep in
        //     business logic later.
        //   - NEVER log the actual secret value, even at DEBUG level.
        //   - Rotate secrets periodically, and immediately after any suspected leak.
        // -----------------------------------------------------------------
        String dbPassword = System.getenv("DB_PASSWORD"); // null if not set -- this is normal for a demo

        if (dbPassword == null) {
            System.out.println("DB_PASSWORD environment variable is NOT set (expected in this demo environment).");
            System.out.println("A real application should FAIL FAST here with a clear error, e.g.:");
            System.out.println("    throw new IllegalStateException(\"Required DB_PASSWORD env var is not set\");");
            System.out.println("...rather than silently proceeding with a null credential.");
        } else {
            // Demonstrates the correct habit even when a value IS present: never print it.
            System.out.println("DB_PASSWORD is set (length=" + dbPassword.length()
                    + " characters) -- value itself is intentionally NOT logged.");
        }

        printSection("All configuration management demos completed.");
    }
}
