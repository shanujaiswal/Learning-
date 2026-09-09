/*
 * ConnectingAndExecutingQueriesDemo.java
 *
 * Demonstrates:
 *   1. The Connection -> Statement -> ResultSet lifecycle, obtained via try-with-resources
 *      so every resource closes automatically, in reverse order, on every code path
 *   2. Basic CRUD (CREATE TABLE, INSERT, SELECT, UPDATE, DELETE) using a plain
 *      java.sql.Statement against an H2 in-memory database
 *   3. Reading a ResultSet by column name AND by 1-based column index
 *   4. Inspecting a ResultSet's shape at runtime via ResultSetMetaData
 *   5. The old, error-prone manual finally-block closing pattern, shown ONLY
 *      as a contrast to explain why try-with-resources is strictly better
 *
 * Covers Theory chapter:
 *   11) JDBC and Database Connectivity/Theory/02 Connecting to a Database and Executing Queries.md
 *
 * REQUIRES the H2 database driver jar on the classpath (a pure-Java, Type 4 JDBC
 * driver that can run entirely in-memory -- no external database server needed).
 * Maven coordinate:  com.h2database:h2:2.2.224
 *
 * Compile (with h2-2.2.224.jar on the classpath):
 *     javac -cp .;h2-2.2.224.jar 02_connecting_and_executing_queries_demo.java
 * Run:
 *     java  -cp .;h2-2.2.224.jar ConnectingAndExecutingQueriesDemo
 * (On macOS/Linux replace ';' with ':' in -cp)
 */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectingAndExecutingQueriesDemo {

    // A single shared in-memory database URL used across every demo method below.
    // DB_CLOSE_DELAY=-1 is H2-specific: it keeps the in-memory DB alive for the whole
    // JVM lifetime even when every connection to it closes in between demo methods.
    private static final String URL = "jdbc:h2:mem:queriesDemo;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static void main(String[] args) {
        printSection("1) The Connection -> Statement -> ResultSet lifecycle");
        demoLifecycle();

        printSection("2) Basic CRUD with plain Statement: CREATE, INSERT, SELECT, UPDATE, DELETE");
        demoCrud();

        printSection("3) Reading a ResultSet by column name vs. 1-based column index");
        demoColumnAccess();

        printSection("4) ResultSetMetaData -- inspecting a result set's shape at runtime");
        demoResultSetMetaData();

        printSection("5) The old manual finally-block pattern, shown only to contrast with #1");
        demoOldStyleManualClosing();
    }

    // -------------------------------------------------------------------
    // 1) The canonical four-stage JDBC shape:
    //    obtain Connection -> create Statement -> execute SQL / get ResultSet
    //    -> iterate -> everything closes automatically in REVERSE order.
    // -------------------------------------------------------------------
    private static void demoLifecycle() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS lifecycle_demo (id INT PRIMARY KEY, note VARCHAR(100))");
            stmt.executeUpdate("DELETE FROM lifecycle_demo");
            stmt.executeUpdate("INSERT INTO lifecycle_demo VALUES (1, 'Connection -> Statement -> ResultSet')");

            try (ResultSet rs = stmt.executeQuery("SELECT id, note FROM lifecycle_demo")) {
                while (rs.next()) {
                    System.out.println("Row -> id=" + rs.getInt("id") + ", note=" + rs.getString("note"));
                }
            }
            // rs closes here (end of its own try-with-resources block)
        } catch (SQLException e) {
            System.err.println("Lifecycle demo failed: " + e.getMessage());
        }
        // stmt closes, then conn closes, here -- automatically, in reverse declaration order
        System.out.println("All resources (ResultSet, Statement, Connection) are now closed.");
    }

    // -------------------------------------------------------------------
    // 2) Basic CRUD using a plain Statement. Notice every value here is a
    //    hardcoded literal -- Statement is only appropriate for fixed SQL
    //    with no external/user input (see chapter 03 for why).
    // -------------------------------------------------------------------
    private static void demoCrud() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            // CREATE (DDL) -- executeUpdate is used for DDL too, and returns 0
            stmt.executeUpdate("DROP TABLE IF EXISTS employees");
            stmt.executeUpdate("""
                CREATE TABLE employees (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(100) NOT NULL,
                    department VARCHAR(50),
                    salary DECIMAL(10,2)
                )
                """);
            System.out.println("Table 'employees' created.");

            // INSERT (DML) -- executeUpdate returns the number of rows affected
            int inserted = stmt.executeUpdate(
                "INSERT INTO employees (name, department, salary) VALUES ('Asha Rao', 'Engineering', 95000.00)");
            stmt.executeUpdate(
                "INSERT INTO employees (name, department, salary) VALUES ('Ben Turner', 'Sales', 62000.00)");
            System.out.println("Rows inserted (first statement): " + inserted);

            // READ (query) -- executeQuery returns a ResultSet cursor
            System.out.println("All employees after INSERT:");
            printAllEmployees(stmt);

            // UPDATE
            int updated = stmt.executeUpdate(
                "UPDATE employees SET salary = 100000.00 WHERE name = 'Asha Rao'");
            System.out.println("Rows updated: " + updated);
            System.out.println("All employees after UPDATE:");
            printAllEmployees(stmt);

            // DELETE
            int deleted = stmt.executeUpdate("DELETE FROM employees WHERE name = 'Ben Turner'");
            System.out.println("Rows deleted: " + deleted);
            System.out.println("All employees after DELETE:");
            printAllEmployees(stmt);

        } catch (SQLException e) {
            System.err.println("CRUD demo failed: " + e.getMessage());
        }
    }

    private static void printAllEmployees(Statement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SELECT id, name, department, salary FROM employees ORDER BY id")) {
            while (rs.next()) {
                System.out.printf("  id=%d name=%s dept=%s salary=%.2f%n",
                        rs.getInt("id"), rs.getString("name"),
                        rs.getString("department"), rs.getDouble("salary"));
            }
        }
    }

    // -------------------------------------------------------------------
    // 3) Column access by NAME vs. by 1-based INDEX. JDBC column indexes
    //    start at 1, NOT 0 -- rs.getString(0) throws SQLException.
    // -------------------------------------------------------------------
    private static void demoColumnAccess() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DROP TABLE IF EXISTS column_access_demo");
            stmt.executeUpdate("CREATE TABLE column_access_demo (id INT PRIMARY KEY, name VARCHAR(50))");
            stmt.executeUpdate("INSERT INTO column_access_demo VALUES (1, 'Column access demo')");

            try (ResultSet rs = stmt.executeQuery("SELECT id, name FROM column_access_demo")) {
                while (rs.next()) {
                    // By name -- readable, resilient to column reordering in SELECT *
                    int idByName = rs.getInt("id");
                    String nameByName = rs.getString("name");

                    // By 1-based index -- column 1 is "id", column 2 is "name"
                    int idByIndex = rs.getInt(1);
                    String nameByIndex = rs.getString(2);

                    System.out.println("By name:  id=" + idByName + ", name=" + nameByName);
                    System.out.println("By index: id=" + idByIndex + ", name=" + nameByIndex);
                }
            }

            // Demonstrate the classic off-by-one mistake, caught safely here for illustration
            try (ResultSet rs = stmt.executeQuery("SELECT id, name FROM column_access_demo")) {
                if (rs.next()) {
                    rs.getString(0); // 0-based access -- always invalid in JDBC
                }
            } catch (SQLException e) {
                System.out.println("Expected failure using 0-based index: " + e.getMessage());
            }

        } catch (SQLException e) {
            System.err.println("Column access demo failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 4) ResultSetMetaData -- lets code discover a result set's SHAPE
    //    (column count, names, SQL types) without knowing the query in advance.
    // -------------------------------------------------------------------
    private static void demoResultSetMetaData() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DROP TABLE IF EXISTS books");
            stmt.executeUpdate("CREATE TABLE books (id INT PRIMARY KEY, title VARCHAR(200), price DECIMAL(6,2))");
            stmt.executeUpdate("INSERT INTO books VALUES (1, 'Effective Java', 45.99)");

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM books")) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                System.out.println("Columns returned: " + columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    System.out.printf("  [%d] %s (SQL type: %s)%n",
                            i, meta.getColumnName(i), meta.getColumnTypeName(i));
                }

                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        System.out.print(meta.getColumnName(i) + "=" + rs.getObject(i) + "  ");
                    }
                    System.out.println();
                }
            }
        } catch (SQLException e) {
            System.err.println("ResultSetMetaData demo failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 5) The OLD, error-prone, pre-Java-7 manual closing pattern -- shown
    //    ONLY as a contrast. Every resource needs its OWN nested try/catch
    //    on close, because closing rs might throw, yet stmt.close() and
    //    conn.close() must STILL be attempted regardless. This is exactly
    //    the boilerplate try-with-resources eliminates.
    // -------------------------------------------------------------------
    private static void demoOldStyleManualClosing() {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
            stmt = conn.createStatement();
            stmt.executeUpdate("DROP TABLE IF EXISTS old_style_demo");
            stmt.executeUpdate("CREATE TABLE old_style_demo (id INT PRIMARY KEY, note VARCHAR(100))");
            stmt.executeUpdate("INSERT INTO old_style_demo VALUES (1, 'manual cleanup is verbose and error-prone')");

            rs = stmt.executeQuery("SELECT id, note FROM old_style_demo");
            while (rs.next()) {
                System.out.println("(old style) id=" + rs.getInt("id") + ", note=" + rs.getString("note"));
            }
        } catch (SQLException e) {
            System.err.println("Old-style demo failed: " + e.getMessage());
        } finally {
            // Every single resource needs its own try/catch here -- if rs.close() throws,
            // stmt.close() and conn.close() must still be attempted regardless.
            if (rs != null) {
                try { rs.close(); } catch (SQLException e) { System.err.println("Failed closing rs: " + e.getMessage()); }
            }
            if (stmt != null) {
                try { stmt.close(); } catch (SQLException e) { System.err.println("Failed closing stmt: " + e.getMessage()); }
            }
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { System.err.println("Failed closing conn: " + e.getMessage()); }
            }
        }
        System.out.println("Notice how much more boilerplate that was vs. try-with-resources above.");
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
