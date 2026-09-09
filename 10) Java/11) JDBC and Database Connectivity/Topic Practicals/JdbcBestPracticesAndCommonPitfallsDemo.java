/*
 * JdbcBestPracticesAndCommonPitfallsDemo.java
 *
 * Demonstrates:
 *   1. Resource leak prevention: try-with-resources vs. a manual-close path
 *      that LEAKS a connection when an exception is thrown mid-method
 *   2. SQLException handling: getSQLState(), getErrorCode(), and iterating
 *      chained exceptions via getNextException() (triggered by a real
 *      unique-constraint violation against H2)
 *   3. ResultSetMetaData usage for generic, column-name-agnostic row mapping
 *   4. Manually mapping ResultSet rows to a simple POJO/record (the pattern
 *      ORMs and Spring's RowMapper automate)
 *
 * Covers Theory chapter:
 *   11) JDBC and Database Connectivity/Theory/06 JDBC Best Practices and Common Pitfalls.md
 *
 * REQUIRES the H2 database driver jar on the classpath (a pure-Java, Type 4 JDBC
 * driver that can run entirely in-memory -- no external database server needed).
 * Maven coordinate:  com.h2database:h2:2.2.224
 *
 * Compile (with h2-2.2.224.jar on the classpath):
 *     javac -cp .;h2-2.2.224.jar 06_jdbc_best_practices_and_common_pitfalls_demo.java
 * Run:
 *     java  -cp .;h2-2.2.224.jar JdbcBestPracticesAndCommonPitfallsDemo
 * (On macOS/Linux replace ';' with ':' in -cp)
 */

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcBestPracticesAndCommonPitfallsDemo {

    private static final String URL = "jdbc:h2:mem:bestPracticesDemo;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static void main(String[] args) {
        printSection("1) Resource leak: manual closing vs. try-with-resources");
        demoResourceLeakVsTryWithResources();

        printSection("2) SQLException details: getSQLState(), getErrorCode(), getNextException()");
        demoSqlExceptionHandling();

        printSection("3) ResultSetMetaData -- generic, column-name-agnostic row-to-map mapping");
        demoGenericRowMapping();

        printSection("4) Manually mapping ResultSet rows to a POJO/record repository pattern");
        demoManualRowMappingRepository();
    }

    // -------------------------------------------------------------------
    // 1) The unsafe method below leaks its Connection whenever executeUpdate()
    //    throws, because close() is only reached on the success path. The
    //    safe method wraps acquisition in try-with-resources, so close() is
    //    GUARANTEED regardless of whether the SQL succeeds, throws, or the
    //    method returns early.
    // -------------------------------------------------------------------
    private static void demoResourceLeakVsTryWithResources() {
        try (Connection setupConn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement setup = setupConn.createStatement()) {
            setup.executeUpdate("DROP TABLE IF EXISTS leak_demo");
            setup.executeUpdate("CREATE TABLE leak_demo (id INT PRIMARY KEY, note VARCHAR(50))");
        } catch (SQLException e) {
            System.err.println("Setup failed: " + e.getMessage());
            return;
        }

        // --- UNSAFE: leaks the connection on the exception path ---
        System.out.println("Calling the UNSAFE method with SQL that will fail (duplicate primary key)...");
        try {
            unsafeInsert("INSERT INTO leak_demo VALUES (1, 'first')");
            unsafeInsert("INSERT INTO leak_demo VALUES (1, 'duplicate id -- violates PRIMARY KEY')");
        } catch (SQLException e) {
            System.out.println("unsafeInsert threw as expected: " + e.getMessage());
            System.out.println("Notice: if this were a pooled connection, it would now be PERMANENTLY");
            System.out.println("checked out and lost to the pool, because close() was never reached.");
        }

        // --- SAFE: try-with-resources guarantees close() runs on every path ---
        System.out.println();
        System.out.println("Calling the SAFE method with the same failing SQL...");
        try {
            safeInsert("INSERT INTO leak_demo VALUES (1, 'duplicate id again')");
        } catch (SQLException e) {
            System.out.println("safeInsert threw as expected: " + e.getMessage());
            System.out.println("But the Connection and Statement were both closed automatically regardless.");
        }
    }

    /** UNSAFE: leaks conn/stmt if executeUpdate() throws -- close() below is skipped on that path. */
    private static void unsafeInsert(String sql) throws SQLException {
        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        Statement stmt = conn.createStatement();
        stmt.executeUpdate(sql); // if this throws, the two close() calls below are NEVER reached
        stmt.close();
        conn.close();
    }

    /** SAFE: try-with-resources guarantees close() runs whether executeUpdate() succeeds or throws. */
    private static void safeInsert(String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    // -------------------------------------------------------------------
    // 2) Trigger a REAL unique-constraint violation against H2, then inspect
    //    the resulting SQLException: its standardized SQL State, H2's own
    //    vendor error code, and any chained exceptions via getNextException().
    // -------------------------------------------------------------------
    private static void demoSqlExceptionHandling() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("DROP TABLE IF EXISTS users_unique");
                setup.executeUpdate("CREATE TABLE users_unique (email VARCHAR(100) PRIMARY KEY, name VARCHAR(100))");
                setup.executeUpdate("INSERT INTO users_unique VALUES ('a@example.com', 'Alice')");
            }

            String sql = "INSERT INTO users_unique (email, name) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "a@example.com"); // duplicate primary key -- will fail
                ps.setString(2, "Alice Clone");
                ps.executeUpdate();
                System.out.println("Unexpectedly succeeded inserting a duplicate primary key!");
            } catch (SQLException e) {
                System.out.println("SQL State:   " + e.getSQLState()
                        + "  (class '23' = integrity constraint violation, e.g. a unique-key clash)");
                System.out.println("Error code:  " + e.getErrorCode() + "  (H2's own vendor-specific error number)");
                System.out.println("Message:     " + e.getMessage());

                System.out.println("Chained exceptions via getNextException():");
                SQLException chained = e.getNextException();
                int count = 0;
                while (chained != null) {
                    count++;
                    System.out.println("  [" + count + "] " + chained.getMessage());
                    chained = chained.getNextException();
                }
                if (count == 0) {
                    System.out.println("  (none -- this particular failure reported only a single exception;");
                    System.out.println("   getNextException() still matters for drivers/operations that report");
                    System.out.println("   multiple related errors/warnings for one failed operation, e.g. some");
                    System.out.println("   batch failures via BatchUpdateException.getNextException())");
                }

                // Demonstrate wrapping in a domain-specific unchecked exception --
                // the pattern Spring's DataAccessException hierarchy formalizes.
                DataAccessException wrapped = new DataAccessException(
                        "Failed to save user with email a@example.com", e);
                System.out.println("Wrapped as: " + wrapped.getClass().getSimpleName()
                        + " -- cause: " + wrapped.getCause().getClass().getSimpleName());
            }
        } catch (SQLException e) {
            System.err.println("SQLException handling demo failed unexpectedly: " + e.getMessage());
        }
    }

    /** A hypothetical domain-specific unchecked exception, decoupling callers from java.sql.SQLException. */
    static class DataAccessException extends RuntimeException {
        DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // -------------------------------------------------------------------
    // 3) ResultSetMetaData -- generic row-to-map conversion that works for
    //    ANY query's result set without knowing its columns in advance.
    // -------------------------------------------------------------------
    private static void demoGenericRowMapping() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("DROP TABLE IF EXISTS products");
                setup.executeUpdate("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(100), price DECIMAL(8,2))");
                setup.executeUpdate("INSERT INTO products VALUES (1, 'Widget', 9.99)");
                setup.executeUpdate("INSERT INTO products VALUES (2, 'Gadget', 19.99)");
            }

            List<Map<String, Object>> rows = queryToMaps(conn, "SELECT id, name, price AS unit_price FROM products");
            System.out.println("Generic rows (column-name-agnostic, respects the 'AS unit_price' alias):");
            for (Map<String, Object> row : rows) {
                System.out.println("  " + row);
            }
        } catch (SQLException e) {
            System.err.println("Generic row mapping demo failed: " + e.getMessage());
        }
    }

    /** Converts any ResultSet's current row into a column-label -> value Map. */
    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>(); // preserves column order

        for (int i = 1; i <= columnCount; i++) {
            // getColumnLabel respects a query's "AS alias", falling back to the real
            // column name otherwise -- generally more useful than getColumnName here.
            row.put(meta.getColumnLabel(i), rs.getObject(i));
        }
        return row;
    }

    private static List<Map<String, Object>> queryToMaps(Connection conn, String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(rowToMap(rs));
            }
        }
        return results;
    }

    // -------------------------------------------------------------------
    // 4) Manually mapping ResultSet rows into an immutable domain record --
    //    exactly the pattern an ORM or Spring's RowMapper automates, and
    //    fully materializing rows BEFORE the ResultSet closes so nothing is
    //    left dangling on a closed JDBC resource.
    // -------------------------------------------------------------------
    private static void demoManualRowMappingRepository() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("DROP TABLE IF EXISTS employees");
                setup.executeUpdate("""
                    CREATE TABLE employees (
                        id INT PRIMARY KEY, name VARCHAR(100), department VARCHAR(50), salary DECIMAL(10,2)
                    )""");
                setup.executeUpdate("INSERT INTO employees VALUES (1, 'Asha Rao', 'Engineering', 95000.00)");
                setup.executeUpdate("INSERT INTO employees VALUES (2, 'Ben Turner', 'Sales', 62000.00)");
                setup.executeUpdate("INSERT INTO employees VALUES (3, 'Sofia Chen', 'Engineering', 98000.00)");
            }

            EmployeeRepository repo = new EmployeeRepository(URL, USER, PASSWORD);

            Optional<EmployeeRepository.Employee> found = repo.findById(2);
            System.out.println("findById(2): " + found);

            Optional<EmployeeRepository.Employee> notFound = repo.findById(999);
            System.out.println("findById(999): " + notFound + "  (Optional.empty(), not null)");

            List<EmployeeRepository.Employee> engineers = repo.findByDepartment("Engineering");
            System.out.println("findByDepartment(\"Engineering\"):");
            engineers.forEach(e -> System.out.println("  " + e));
        } catch (SQLException e) {
            System.err.println("Manual row mapping demo failed: " + e.getMessage());
        }
    }

    /**
     * A small repository showing the manual ResultSet-to-record mapping pattern.
     * In a real application, the Connection would come from a pooling DataSource
     * (chapter 05) rather than DriverManager -- DriverManager is used directly here
     * only to keep this file self-contained and independently runnable.
     */
    static class EmployeeRepository {
        record Employee(int id, String name, String department, BigDecimal salary) {}

        private final String url;
        private final String user;
        private final String password;

        EmployeeRepository(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        /** Maps exactly one ResultSet row (must already be positioned via rs.next()) to an Employee. */
        private Employee mapRow(ResultSet rs) throws SQLException {
            return new Employee(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("department"),
                    rs.getBigDecimal("salary"));
        }

        Optional<Employee> findById(int id) throws SQLException {
            String sql = "SELECT id, name, department, salary FROM employees WHERE id = ?";
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs)); // materialized BEFORE rs/ps/conn close
                    }
                    return Optional.empty();
                }
            }
        }

        List<Employee> findByDepartment(String department) throws SQLException {
            String sql = "SELECT id, name, department, salary FROM employees WHERE department = ? ORDER BY name";
            List<Employee> results = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, department);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapRow(rs)); // every row converted to a plain object before returning
                    }
                }
            }
            return results; // caller receives plain Employee records, decoupled from any JDBC resource
        }
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
