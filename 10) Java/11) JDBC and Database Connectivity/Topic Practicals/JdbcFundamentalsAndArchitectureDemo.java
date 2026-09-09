/*
 * JdbcFundamentalsAndArchitectureDemo.java
 *
 * Demonstrates:
 *   1. Obtaining a Connection via DriverManager, using the JDBC URL format
 *   2. Obtaining a Connection via a DataSource (org.h2.jdbcx.JdbcDataSource)
 *   3. Inspecting DatabaseMetaData (product name/version, driver info) --
 *      i.e. what a JDBC driver actually reports about itself and the database
 *   4. That the same JDBC interfaces (Connection, Statement, ResultSet) work
 *      identically regardless of which concrete driver/database sits underneath
 *
 * Covers Theory chapter:
 *   11) JDBC and Database Connectivity/Theory/01 JDBC Fundamentals and Architecture.md
 *
 * REQUIRES the H2 database driver jar on the classpath (a pure-Java, Type 4 JDBC
 * driver that can run entirely in-memory -- no external database server needed).
 * Maven coordinate:  com.h2database:h2:2.2.224
 *
 * Compile (with h2-2.2.224.jar on the classpath):
 *     javac -cp .;h2-2.2.224.jar 01_jdbc_fundamentals_and_architecture_demo.java
 * Run:
 *     java  -cp .;h2-2.2.224.jar JdbcFundamentalsAndArchitectureDemo
 * (On macOS/Linux replace ';' with ':' in -cp)
 */

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class JdbcFundamentalsAndArchitectureDemo {

    public static void main(String[] args) {
        printSection("1) Connecting via DriverManager, and reading the JDBC URL format");
        demoDriverManagerConnection();

        printSection("2) Connecting via a DataSource (the modern, pool-friendly approach)");
        demoDataSourceConnection();

        printSection("3) Inspecting DatabaseMetaData -- what the driver reports about itself");
        demoDatabaseMetaData();

        printSection("4) Same JDBC interfaces, regardless of underlying driver/database");
        demoInterfaceIndependence();
    }

    // -------------------------------------------------------------------
    // 1) DriverManager -- the original, simplest connection factory.
    //    Notice the JDBC URL shape:  jdbc:<subprotocol>:<subname>
    //    Since JDBC 4.0 (Java 6+), the H2 driver on the classpath registers
    //    itself automatically via the ServiceLoader/SPI mechanism -- no
    //    Class.forName("org.h2.Driver") call is needed (that idiom is legacy).
    // -------------------------------------------------------------------
    private static void demoDriverManagerConnection() {
        // DB_CLOSE_DELAY=-1 keeps this in-memory H2 database alive for the whole
        // JVM lifetime, even if every connection to it closes in between --
        // an H2-specific convenience with no real-server equivalent needed.
        String url = "jdbc:h2:mem:fundamentalsDemo;DB_CLOSE_DELAY=-1";
        String user = "sa";
        String password = "";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected via DriverManager: " + !conn.isClosed());
            System.out.println("URL used: " + url);
            System.out.println("Auto-commit (default is true): " + conn.getAutoCommit());
            System.out.println("Catalog: " + conn.getCatalog());
        } catch (SQLException e) {
            System.err.println("DriverManager connection failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 2) DataSource -- the modern factory interface (javax.sql.DataSource).
    //    org.h2.jdbcx.JdbcDataSource is H2's own simple, non-pooling
    //    DataSource implementation -- real production code would typically
    //    use a POOLING DataSource (HikariCP) instead; see chapter 05.
    // -------------------------------------------------------------------
    private static void demoDataSourceConnection() {
        org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:fundamentalsDemo;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection conn = dataSource.getConnection()) {
            System.out.println("Connected via DataSource: " + !conn.isClosed());
            System.out.println("Notice application code never referenced any H2-specific");
            System.out.println("concrete class beyond construction -- conn is typed as");
            System.out.println("java.sql.Connection, the vendor-neutral JDBC interface.");
        } catch (SQLException e) {
            System.err.println("DataSource connection failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 3) DatabaseMetaData -- lets code ask the driver/database questions
    //    about itself at runtime: product name, version, driver version,
    //    supported features. Rarely needed in everyday CRUD code, but
    //    useful for diagnostics and database-agnostic tooling.
    // -------------------------------------------------------------------
    private static void demoDatabaseMetaData() {
        String url = "jdbc:h2:mem:fundamentalsDemo;DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            DatabaseMetaData meta = conn.getMetaData();

            System.out.println("Database product name:    " + meta.getDatabaseProductName());
            System.out.println("Database product version: " + meta.getDatabaseProductVersion());
            System.out.println("JDBC driver name:          " + meta.getDriverName());
            System.out.println("JDBC driver version:       " + meta.getDriverVersion());
            System.out.println("JDBC major/minor version:  "
                    + meta.getJDBCMajorVersion() + "." + meta.getJDBCMinorVersion());
            System.out.println("Supports transactions:     " + meta.supportsTransactions());
            System.out.println("Max connections (0=unknown/no limit reported): "
                    + meta.getMaxConnections());
        } catch (SQLException e) {
            System.err.println("Metadata inspection failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 4) The point of the whole chapter, made concrete: this method takes
    //    a plain java.sql.Connection -- it has ZERO H2-specific code in it.
    //    The exact same method would work unmodified if handed a Connection
    //    obtained from a PostgreSQL, MySQL, or Oracle driver instead --
    //    only the URL/driver jar used to OBTAIN the connection would differ.
    // -------------------------------------------------------------------
    private static void demoInterfaceIndependence() {
        String url = "jdbc:h2:mem:fundamentalsDemo;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            runVendorNeutralSetupAndQuery(conn);
        } catch (SQLException e) {
            System.err.println("Vendor-neutral demo failed: " + e.getMessage());
        }
    }

    /** Coded entirely against java.sql.* interfaces -- no vendor-specific types anywhere. */
    private static void runVendorNeutralSetupAndQuery(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS drivers_demo (id INT PRIMARY KEY, note VARCHAR(100))");
            stmt.executeUpdate("DELETE FROM drivers_demo");
            stmt.executeUpdate("INSERT INTO drivers_demo VALUES (1, 'Same JDBC code works across vendors')");

            try (ResultSet rs = stmt.executeQuery("SELECT id, note FROM drivers_demo")) {
                while (rs.next()) {
                    System.out.println("Row -> id=" + rs.getInt("id") + ", note=" + rs.getString("note"));
                }
            }
        }
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
