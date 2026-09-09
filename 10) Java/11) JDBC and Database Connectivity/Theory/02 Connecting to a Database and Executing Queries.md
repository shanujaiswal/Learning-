# The JDBC Object Lifecycle, at a Glance

--> A typical JDBC interaction follows the same four-stage shape every time, regardless of which database sits underneath: **obtain a `Connection`** -> **create a `Statement`** -> **execute SQL and get a `ResultSet` (for queries) or a row count (for updates)** -> **close everything, in reverse order of creation**.

```text
Connection conn = DriverManager.getConnection(url, user, pass);   // 1. open a session
        |
        v
Statement stmt = conn.createStatement();                          // 2. create a SQL executor
        |
        v
ResultSet rs = stmt.executeQuery("SELECT ...");                   // 3. run SQL, get results
        |
        v
while (rs.next()) { ... read columns ... }                        // 4. iterate the cursor
        |
        v
rs.close();  stmt.close();  conn.close();                          // 5. release resources
                                                                    //    (in reverse order)
```

--> Each of `ResultSet`, `Statement`, and `Connection` wraps a real, finite operating-system/network resource underneath (an open cursor on the database server, a socket, memory buffers) -- failing to close them leaks those resources even though the Java OBJECT itself will eventually be garbage collected. This is the single most important operational lesson in this chapter, and `try-with-resources` (below) is how modern Java code avoids it structurally rather than by discipline alone.

# Obtaining a Connection

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectingBasics {
    public static void main(String[] args) {
        String url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
        String user = "sa";
        String password = "";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected: " + !conn.isClosed());
            System.out.println("Catalog: " + conn.getCatalog());
            System.out.println("Auto-commit: " + conn.getAutoCommit());
        } catch (SQLException e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }
}
```

--> **`DB_CLOSE_DELAY=-1`** is an H2-specific connection property meaning "keep the in-memory database alive until the JVM exits, even if all connections to it close" -- without it, an H2 in-memory database is destroyed the instant its LAST open connection closes, which would wipe out data between separate `getConnection(...)` calls in example code. This property is purely an H2 convenience for single-JVM demos/tests; it has no equivalent need on a real server-based database like PostgreSQL or MySQL, since those keep running independently of any one client's connections.
--> A `Connection` represents an open session with the database -- it is NOT free to create (there's a TCP handshake, often TLS negotiation, and authentication involved), which is precisely why chapter 05 exists: repeatedly opening and closing raw connections in a busy application is expensive and doesn't scale.

# `Statement` -- Executing Static SQL

--> `Statement` is the simplest of the three "executor" interfaces -- it runs a literal SQL string with no parameter placeholders. It has three primary execute methods, and choosing the right one matters:

| Method | Use for | Returns |
|---|---|---|
| `executeQuery(String sql)` | `SELECT` statements | `ResultSet` |
| `executeUpdate(String sql)` | `INSERT`, `UPDATE`, `DELETE`, DDL (`CREATE TABLE`, etc.) | `int` (row count affected, or 0 for DDL) |
| `execute(String sql)` | Unknown/mixed SQL, or SQL that might return multiple result sets | `boolean` (true if the first result is a `ResultSet`) -- rarely used in typical application code |

```java
import java.sql.*;

public class StatementCrudBasics {
    public static void main(String[] args) throws SQLException {
        String url = "jdbc:h2:mem:crudDemo;DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {

            // CREATE (DDL) -- executeUpdate is used for DDL too, returning 0
            stmt.executeUpdate("""
                CREATE TABLE employees (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(100) NOT NULL,
                    department VARCHAR(50),
                    salary DECIMAL(10,2)
                )
                """);

            // INSERT (DML) -- executeUpdate returns rows affected
            int inserted = stmt.executeUpdate(
                "INSERT INTO employees (name, department, salary) VALUES ('Asha Rao', 'Engineering', 95000.00)");
            System.out.println("Rows inserted: " + inserted);

            // READ (query) -- executeQuery returns a ResultSet cursor
            try (ResultSet rs = stmt.executeQuery("SELECT id, name, department, salary FROM employees")) {
                while (rs.next()) {
                    System.out.printf("id=%d name=%s dept=%s salary=%.2f%n",
                            rs.getInt("id"), rs.getString("name"),
                            rs.getString("department"), rs.getDouble("salary"));
                }
            }

            // UPDATE
            int updated = stmt.executeUpdate(
                "UPDATE employees SET salary = 100000.00 WHERE name = 'Asha Rao'");
            System.out.println("Rows updated: " + updated);

            // DELETE
            int deleted = stmt.executeUpdate("DELETE FROM employees WHERE name = 'Asha Rao'");
            System.out.println("Rows deleted: " + deleted);
        }
    }
}
```

--> **Why plain `Statement` is dangerous for anything with user input** -- the SQL is built as a literal string, so any value coming from outside your program (form input, an API parameter) that gets concatenated into that string opens the door to **SQL injection**. `Statement` is appropriate ONLY for fixed, hardcoded SQL with no external input (schema setup, fixed reports) -- chapter 03 covers `PreparedStatement`, which is the correct tool the moment any value is not a compile-time constant.

# The `ResultSet` Cursor Model

--> A `ResultSet` is a CURSOR into the query's result rows on the database side -- it does not (by default) load the entire result into Java memory upfront. It starts positioned BEFORE the first row, and `rs.next()` both advances the cursor AND returns `false` once there are no more rows -- this is why the idiomatic read loop is always `while (rs.next()) { ... }`.

```java
try (ResultSet rs = stmt.executeQuery("SELECT id, name FROM employees")) {
    while (rs.next()) {                      // advances cursor; false when exhausted
        int id = rs.getInt("id");            // read by column NAME (readable, refactor-safe)
        int idByIndex = rs.getInt(1);         // read by column INDEX (1-based, not 0-based!)
        String name = rs.getString("name");
    }
}
```

--> **Column indexes are 1-based, not 0-based** -- `rs.getString(1)` gets the FIRST column, not `rs.getString(0)`, which throws `SQLException: invalid column index`. This trips up nearly every Java developer coming from array/list-indexed languages the first time they use positional access.
--> **Reading by name vs by index** -- by-name (`rs.getString("name")`) is more readable and resilient to a `SELECT *` column reordering, at a small (usually negligible) lookup cost; by-index is marginally faster and is the only option for anonymous/computed columns (`SELECT COUNT(*)` has no natural name unless aliased). Prefer by-name for readability unless profiling shows it matters.
--> **A `ResultSet` is only valid while its `Statement` and `Connection` remain open** -- calling `rs.next()` (or any getter) after the underlying statement/connection has closed throws `SQLException: ResultSet is closed` (or similar) -- this is a common bug when a `ResultSet` reference is returned out of a `try-with-resources` block and used afterward.
--> **`ResultSet` types** -- by default a `ResultSet` is `TYPE_FORWARD_ONLY` (cursor moves forward only, one pass) and `CONCUR_READ_ONLY` (can't be used to update the database through it). Scrollable (`TYPE_SCROLL_INSENSITIVE`) and updatable (`CONCUR_UPDATABLE`) result sets exist but are rarely used in modern code -- most applications simply re-query or use an explicit `UPDATE` statement instead, which is clearer and more portable across drivers.

# `try-with-resources` -- Why It's the Only Correct Pattern Today

--> `Connection`, `Statement`, `PreparedStatement`, `CallableStatement`, and `ResultSet` all implement `java.lang.AutoCloseable` -- meaning they all work naturally with `try-with-resources`, introduced in Java 7. This should be considered the DEFAULT way to write JDBC code today; the older manual `finally`-block closing pattern is legacy and error-prone.

```java
// The OLD, error-prone, pre-Java-7 pattern -- included here ONLY to show why it's inferior
Connection conn = null;
Statement stmt = null;
ResultSet rs = null;
try {
    conn = DriverManager.getConnection(url, user, password);
    stmt = conn.createStatement();
    rs = stmt.executeQuery("SELECT * FROM employees");
    // ... use rs ...
} catch (SQLException e) {
    e.printStackTrace();
} finally {
    // Every single resource needs its OWN try/catch here, because closing rs might
    // throw, and if it does, stmt.close() and conn.close() must STILL be attempted.
    // This nested-try boilerplate is exactly what try-with-resources eliminates.
    if (rs != null)   try { rs.close();   } catch (SQLException e) { /* log */ }
    if (stmt != null) try { stmt.close(); } catch (SQLException e) { /* log */ }
    if (conn != null) try { conn.close(); } catch (SQLException e) { /* log */ }
}
```

```java
// The MODERN, correct pattern -- resources declared in the try(...) parentheses
// are closed AUTOMATICALLY, in REVERSE declaration order, even if an exception
// is thrown -- no manual finally-block bookkeeping needed at all.
try (Connection conn = DriverManager.getConnection(url, user, password);
     Statement stmt = conn.createStatement();
     ResultSet rs = stmt.executeQuery("SELECT * FROM employees")) {

    while (rs.next()) {
        System.out.println(rs.getString("name"));
    }
} catch (SQLException e) {
    System.err.println("Database error: " + e.getMessage());
}
// rs.close(), then stmt.close(), then conn.close() are all called automatically here,
// in that reverse order, whether the try block succeeded, threw, or returned early.
```

--> **Closing order matters and try-with-resources gets it right automatically** -- resources must close in the REVERSE of the order they were opened (`ResultSet` before `Statement` before `Connection`), because a `ResultSet` is logically owned by its `Statement`, which is in turn owned by its `Connection`. Multi-resource `try-with-resources` handles this ordering for you correctly every time -- one more reason to prefer it over hand-written cleanup code.
--> **Suppressed exceptions** -- if the try BLOCK throws, and then closing a resource ALSO throws, try-with-resources doesn't lose either exception -- the close-time exception is attached to the original as a "suppressed exception" (`Throwable.getSuppressed()`), rather than silently replacing it the way naive manual cleanup code often does by accident.

# Reading and Executing SQL from Multiple Angles

```java
import java.sql.*;

public class ResultSetMetadataPeek {
    public static void main(String[] args) throws SQLException {
        String url = "jdbc:h2:mem:metaDemo;DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {

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
        }
    }
}
```

--> `ResultSetMetaData` (covered more in chapter 06) lets code inspect the SHAPE of a result set at runtime without knowing the query in advance -- useful for generic tooling (database browsers, generic row-to-object mappers) but rarely needed in typical business-logic CRUD code where you already know your columns.

# Common Gotchas

--> **Off-by-one on column index** -- JDBC column indexes start at 1, not 0. This is one of the most common first-week JDBC bugs.
--> **Reading a `ResultSet` after its `Statement`/`Connection` closed** -- a classic bug when a `ResultSet` escapes its `try-with-resources` block (e.g. returned from a method) -- always fully consume/convert a `ResultSet` into plain Java objects (a `List<Employee>`, etc.) BEFORE its enclosing resources close, never return the raw `ResultSet` itself.
--> **Using `Statement` with any user-supplied value** -- string-concatenating input into SQL is the classic SQL injection vector -- see chapter 03 for why `PreparedStatement` is the fix, not an optional nicety.
--> **Forgetting `DB_CLOSE_DELAY=-1` with H2 in-memory databases across multiple connections** -- without it, the database is wiped the moment the last connection to it closes, which surprises people writing multi-connection demos or tests against H2.
--> **Not closing resources at all** -- relying on garbage collection to eventually finalize a `Connection` is not a substitute for closing it -- GC timing is unpredictable, and a busy application can exhaust the database's connection limit (or the OS's open-socket/file-descriptor limit) long before GC gets around to it.

# Best Practices Recap

--> Always use `try-with-resources` for `Connection`, `Statement`, and `ResultSet` -- never manual `finally`-block closing in new code.
--> Fully materialize `ResultSet` rows into plain Java objects before the underlying resources close; never let a raw `ResultSet` escape the method that created it.
--> Reach for `executeQuery` for `SELECT`, `executeUpdate` for `INSERT`/`UPDATE`/`DELETE`/DDL -- don't reach for the generic `execute` unless you genuinely don't know the SQL's shape ahead of time.
--> Treat `Statement` as reserved for fixed, hardcoded SQL only -- the instant a value comes from outside the program, move to `PreparedStatement` (chapter 03).
