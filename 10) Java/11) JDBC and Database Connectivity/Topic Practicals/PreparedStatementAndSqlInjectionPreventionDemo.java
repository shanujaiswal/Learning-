/*
 * PreparedStatementAndSqlInjectionPreventionDemo.java
 *
 * Demonstrates:
 *   1. A live SQL injection exploit against H2, built via naive string
 *      concatenation with a plain Statement -- the vulnerability is REAL and
 *      REPRODUCIBLE here, not just described in prose
 *   2. The exact same attack input defused by PreparedStatement parameter binding
 *   3. PreparedStatement basics: 1-based '?' placeholders, typed setters, setNull
 *   4. Batch updates via addBatch()/executeBatch(), paired with a transaction
 *   5. CallableStatement usage against an H2 "ALIAS" (H2's mechanism for a
 *      Java-backed stored function), showing the standard JDBC calling
 *      convention: {? = call FUNCTION_NAME()} and registerOutParameter
 *
 * Covers Theory chapter:
 *   11) JDBC and Database Connectivity/Theory/03 PreparedStatement and SQL Injection Prevention.md
 *
 * REQUIRES the H2 database driver jar on the classpath (a pure-Java, Type 4 JDBC
 * driver that can run entirely in-memory -- no external database server needed).
 * Maven coordinate:  com.h2database:h2:2.2.224
 *
 * Compile (with h2-2.2.224.jar on the classpath):
 *     javac -cp .;h2-2.2.224.jar 03_preparedstatement_and_sql_injection_prevention_demo.java
 * Run:
 *     java  -cp .;h2-2.2.224.jar PreparedStatementAndSqlInjectionPreventionDemo
 * (On macOS/Linux replace ';' with ':' in -cp)
 */

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

public class PreparedStatementAndSqlInjectionPreventionDemo {

    private static final String URL = "jdbc:h2:mem:injectionDemo;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static void main(String[] args) {
        printSection("1) SQL injection EXPLOITED via naive string concatenation with Statement");
        demoSqlInjectionVulnerability();

        printSection("2) The SAME malicious input, DEFUSED by PreparedStatement binding");
        demoPreparedStatementDefusesInjection();

        printSection("3) PreparedStatement basics: '?' placeholders, typed setters, setNull");
        demoPreparedStatementBasics();

        printSection("4) Batch updates: addBatch()/executeBatch() paired with a transaction");
        demoBatchUpdates();

        printSection("5) CallableStatement -- invoking an H2 stored function (ALIAS)");
        demoCallableStatement();
    }

    // -------------------------------------------------------------------
    // 1) THE VULNERABILITY, LIVE. This is not illustrative pseudocode --
    //    running this method actually bypasses the intended WHERE filter
    //    against a real H2 database, because the attacker's input contains
    //    SQL syntax ( ' OR '1'='1 ) that gets concatenated directly into
    //    the executed SQL string.
    // -------------------------------------------------------------------
    private static void demoSqlInjectionVulnerability() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("DROP TABLE IF EXISTS users");
                setup.executeUpdate("CREATE TABLE users (username VARCHAR(50), password VARCHAR(50))");
                setup.executeUpdate("INSERT INTO users VALUES ('admin', 'secret123')");
                setup.executeUpdate("INSERT INTO users VALUES ('bob', 'bobspassword')");
            }

            // Attacker-supplied input -- pretend this arrived from a login form's
            // "username" field, with no validation or escaping.
            String maliciousInput = "admin' OR '1'='1";

            // DANGEROUS: building SQL by string concatenation with external input.
            String unsafeSql = "SELECT * FROM users WHERE username = '" + maliciousInput + "'";
            System.out.println("Actual SQL sent to the database:");
            System.out.println("  " + unsafeSql);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(unsafeSql)) {
                int matches = 0;
                while (rs.next()) {
                    matches++;
                    System.out.println("  Leaked row -> username=" + rs.getString("username")
                            + ", password=" + rs.getString("password"));
                }
                System.out.println("Rows returned: " + matches
                        + " (expected 0 legitimate matches -- injection returned every row instead!)");
            }
        } catch (SQLException e) {
            System.err.println("Injection demo failed unexpectedly: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 2) The exact same malicious input, this time bound as a PARAMETER
    //    through PreparedStatement -- the driver sends SQL structure and
    //    value as separate pieces, so the value can never be reinterpreted
    //    as SQL syntax, no matter what characters it contains.
    // -------------------------------------------------------------------
    private static void demoPreparedStatementDefusesInjection() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            // Table already exists from the previous demo's setup (same in-memory DB),
            // but recreate defensively so this method also works if run in isolation.
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("CREATE TABLE IF NOT EXISTS users (username VARCHAR(50), password VARCHAR(50))");
                setup.executeUpdate("DELETE FROM users");
                setup.executeUpdate("INSERT INTO users VALUES ('admin', 'secret123')");
                setup.executeUpdate("INSERT INTO users VALUES ('bob', 'bobspassword')");
            }

            String maliciousInput = "admin' OR '1'='1";
            String sql = "SELECT * FROM users WHERE username = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, maliciousInput);
                try (ResultSet rs = ps.executeQuery()) {
                    int matches = 0;
                    while (rs.next()) matches++;
                    System.out.println("Rows matched with PreparedStatement: " + matches
                            + " (correctly 0 -- the ' OR '1'='1 payload is treated as literal DATA, not SQL)");
                }
            }
        } catch (SQLException e) {
            System.err.println("Safe query demo failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 3) PreparedStatement basics: 1-based '?' placeholders, typed setters
    //    (setString/setBigDecimal/etc.), and setNull for binding a SQL NULL.
    // -------------------------------------------------------------------
    private static void demoPreparedStatementBasics() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("DROP TABLE IF EXISTS employees");
                setup.executeUpdate("""
                    CREATE TABLE employees (
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        name VARCHAR(100), department VARCHAR(50), salary DECIMAL(10,2)
                    )""");
            }

            String insertSql = "INSERT INTO employees (name, department, salary) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, "Priya Sharma");     // parameter index is 1-based
                ps.setString(2, "Engineering");
                ps.setBigDecimal(3, new BigDecimal("87500.00"));
                int rowsInserted = ps.executeUpdate();
                System.out.println("Inserted: " + rowsInserted);
            }

            // setNull -- binding a SQL NULL for a column with no known department yet.
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, "Contractor Jane");
                ps.setNull(2, Types.VARCHAR);        // department is unknown -> SQL NULL
                ps.setBigDecimal(3, new BigDecimal("40000.00"));
                ps.executeUpdate();
                System.out.println("Inserted a row with a NULL department via setNull().");
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name, department, salary FROM employees ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.printf("  name=%s dept=%s salary=%.2f%n",
                            rs.getString("name"), rs.getString("department"), rs.getDouble("salary"));
                }
            }
        } catch (SQLException e) {
            System.err.println("PreparedStatement basics demo failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 4) Batch updates -- queue many parameter sets with addBatch(), send
    //    them all in ONE round-trip with executeBatch(), wrapped in a
    //    transaction so all inserts succeed or fail together.
    // -------------------------------------------------------------------
    private static void demoBatchUpdates() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("DROP TABLE IF EXISTS new_hires");
                setup.executeUpdate("""
                    CREATE TABLE new_hires (
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        name VARCHAR(100), department VARCHAR(50), salary DECIMAL(10,2)
                    )""");
            }

            record NewEmployee(String name, String department, BigDecimal salary) {}
            List<NewEmployee> newHires = List.of(
                    new NewEmployee("Rahul Mehta", "Sales", new BigDecimal("60000")),
                    new NewEmployee("Sofia Chen", "Engineering", new BigDecimal("98000")),
                    new NewEmployee("Liam O'Brien", "Marketing", new BigDecimal("72000"))
            );

            String sql = "INSERT INTO new_hires (name, department, salary) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false); // batch inserts are typically wrapped in one transaction too

                for (NewEmployee emp : newHires) {
                    ps.setString(1, emp.name());
                    ps.setString(2, emp.department());
                    ps.setBigDecimal(3, emp.salary());
                    ps.addBatch(); // queue this parameter set, don't execute yet
                }

                int[] results = ps.executeBatch(); // ONE round-trip for all queued statements
                conn.commit();

                System.out.println("Batch executed, rows affected per statement: "
                        + Arrays.toString(results));
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("Batch failed, rolled back: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }

            try (Statement check = conn.createStatement();
                 ResultSet rs = check.executeQuery("SELECT name, department, salary FROM new_hires ORDER BY id")) {
                System.out.println("Rows now in new_hires:");
                while (rs.next()) {
                    System.out.printf("  %s / %s / %.2f%n",
                            rs.getString("name"), rs.getString("department"), rs.getDouble("salary"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Batch update demo failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 5) CallableStatement -- invokes a stored routine using JDBC's standard
    //    escape syntax. H2 doesn't support full vendor stored-procedure
    //    dialects (PL/SQL, T-SQL, etc.), but it DOES support defining a
    //    Java-backed "ALIAS" function, which is enough to demonstrate the
    //    real CallableStatement calling convention end-to-end: registering
    //    an OUT parameter and reading it back after execute().
    // -------------------------------------------------------------------
    private static void demoCallableStatement() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("DROP TABLE IF EXISTS salaries");
                setup.executeUpdate("CREATE TABLE salaries (id INT PRIMARY KEY, salary DECIMAL(10,2))");
                setup.executeUpdate("INSERT INTO salaries VALUES (1, 50000), (2, 60000), (3, 72500.50)");

                // H2's ALIAS mechanism lets us define a Java-method-backed "stored
                // function" purely for demo purposes -- this is H2-specific syntax;
                // real Oracle/PostgreSQL/SQL Server stored procedures use their own
                // vendor SQL dialects (PL/SQL, PL/pgSQL, T-SQL respectively). What
                // stays STANDARD across all of them is the JDBC CallableStatement
                // calling convention used below.
                setup.execute("""
                    CREATE ALIAS IF NOT EXISTS GET_SALARY_TOTAL AS $$
                    Double getSalaryTotal(java.sql.Connection conn) throws java.sql.SQLException {
                        try (java.sql.Statement s = conn.createStatement();
                             java.sql.ResultSet rs = s.executeQuery("SELECT SUM(salary) FROM salaries")) {
                            rs.next();
                            return rs.getDouble(1);
                        }
                    }
                    $$;
                    """);
            }

            // Standard JDBC escape syntax for invoking a stored function that returns a value:
            // {? = call FUNCTION_NAME(args...)}
            try (CallableStatement cs = conn.prepareCall("{? = call GET_SALARY_TOTAL()}")) {
                cs.registerOutParameter(1, Types.DOUBLE); // MUST be called before execute()
                cs.execute();
                double total = cs.getDouble(1);
                System.out.println("Total salary (via CallableStatement / stored function): " + total);
            }

            System.out.println();
            System.out.println("Note: full IN/OUT/INOUT parameter stored procedures (with vendor-specific");
            System.out.println("procedural SQL bodies) are not portable across databases -- only the JDBC");
            System.out.println("calling convention shown here (CallableStatement, {call ...} escape syntax,");
            System.out.println("registerOutParameter) is standard across Oracle/PostgreSQL/SQL Server/MySQL.");
        } catch (SQLException e) {
            System.err.println("CallableStatement demo failed: " + e.getMessage());
        }
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
