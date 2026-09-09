# Why `PreparedStatement` Exists

--> `Statement` executes whatever SQL string you hand it, literally -- if any part of that string comes from outside the program (a web form, a CLI argument, an API request body), a malicious or careless value can change the MEANING of the SQL, not just its data. This class of vulnerability is called **SQL injection**, and it remains one of the most dangerous and most preventable classes of security bugs in software.
--> `PreparedStatement` fixes this by separating the SQL STRUCTURE (compiled once, with `?` placeholders marking where values go) from the VALUES themselves (bound in afterward through typed setter methods) -- the database driver sends the structure and the values to the database as SEPARATE pieces of information, so a value can never be reinterpreted as SQL syntax, no matter what characters it contains.

# SQL Injection, Demonstrated

```java
// DANGEROUS -- string concatenation building SQL from user input
String username = "admin' OR '1'='1";     // attacker-supplied input
String sql = "SELECT * FROM users WHERE username = '" + username + "'";
// The SQL actually sent to the database becomes:
//   SELECT * FROM users WHERE username = 'admin' OR '1'='1'
// '1'='1' is always true, so this returns EVERY row in the users table --
// a classic authentication-bypass injection.
```

```java
// An even more destructive example, with a second statement smuggled in
// (whether this specific "stacked query" trick works depends on the driver/DB,
// but the fundamental unsafety of concatenation is the same either way)
String input = "x'; DROP TABLE users; --";
String sql = "SELECT * FROM accounts WHERE id = '" + input + "'";
// SELECT * FROM accounts WHERE id = 'x'; DROP TABLE users; --'
```

--> **The fix is `PreparedStatement`, always, for any SQL containing a value that isn't a compile-time literal you wrote yourself** -- this isn't a "best practice among several reasonable options," it's close to a hard rule in professional Java code.

```java
import java.sql.*;

public class SafeQuery {
    public static void main(String[] args) throws SQLException {
        String url = "jdbc:h2:mem:injectionDemo;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("CREATE TABLE users (username VARCHAR(50), password VARCHAR(50))");
                setup.executeUpdate("INSERT INTO users VALUES ('admin', 'secret123')");
            }

            String maliciousInput = "admin' OR '1'='1";

            // The '?' is a placeholder -- the driver sends the SQL structure and the
            // bound value as SEPARATE pieces to the database. The value is treated
            // strictly as DATA, never re-parsed as SQL syntax, no matter its contents.
            String sql = "SELECT * FROM users WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, maliciousInput);
                try (ResultSet rs = ps.executeQuery()) {
                    int matches = 0;
                    while (rs.next()) matches++;
                    System.out.println("Rows matched: " + matches);   // prints 0 -- injection defused
                }
            }
        }
    }
}
```

# Creating and Using a `PreparedStatement`

```java
String sql = "INSERT INTO employees (name, department, salary) VALUES (?, ?, ?)";
try (PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, "Priya Sharma");     // parameter index is 1-based, same as ResultSet columns
    ps.setString(2, "Engineering");
    ps.setBigDecimal(3, new BigDecimal("87500.00"));

    int rowsInserted = ps.executeUpdate();
    System.out.println("Inserted: " + rowsInserted);
}
```

--> **Parameter indexes are 1-based**, matching the `?` placeholders left to right in the SQL string -- the same 1-based convention as `ResultSet` column access.
--> **Typed setters** -- `setString`, `setInt`, `setLong`, `setBigDecimal`, `setDate`, `setTimestamp`, `setBoolean`, `setNull(index, Types.X)`, and more -- each corresponds to a SQL/Java type mapping the driver understands. Using the correctly typed setter (rather than always calling `setString` and hoping the database coerces it) avoids subtle type-mismatch bugs and lets the driver send the value in its native binary wire format, which is also faster than a text representation for most types.
--> **`setNull`** -- to bind a SQL `NULL`, call `ps.setNull(parameterIndex, java.sql.Types.VARCHAR)` (or whichever `Types` constant matches the column) rather than trying to pass a Java `null` into a typed setter for a primitive-backed type.

## `PreparedStatement` Also Extends `Statement`

--> `PreparedStatement` is a subinterface of `Statement`, adding the parameterized/precompiled behavior -- `executeQuery()`, `executeUpdate()`, and `execute()` all exist on it too, but called with NO SQL argument (the SQL was already supplied to `prepareStatement(...)` when the object was created) -- calling the zero-argument overloads is what distinguishes prepared-statement usage from statement usage in code that otherwise looks similar.

# Why `PreparedStatement` Is Also Usually Faster (Not Just Safer)

--> Beyond security, `PreparedStatement` gives the database a chance to **compile the SQL execution plan once** and reuse that compiled plan across multiple executions with different bound parameter values -- many database engines cache prepared statement plans, so executing the same parameterized SQL repeatedly (a common pattern in loops, batch jobs, or a busy web endpoint) avoids re-parsing and re-planning the SQL text every single time.
--> **This performance benefit is secondary to the security benefit** -- always lead with "prevents SQL injection" as the reason to use `PreparedStatement"; the performance angle is a genuine bonus, not the primary justification.

# Batch Updates

--> Executing many similar `INSERT`/`UPDATE` statements ONE AT A TIME means one network round-trip per statement -- for hundreds or thousands of rows, that round-trip latency dominates total time. **Batching** groups many parameter sets into a single network round-trip.

```java
import java.sql.*;
import java.math.BigDecimal;
import java.util.List;

public class BatchInsertDemo {
    record NewEmployee(String name, String department, BigDecimal salary) {}

    public static void main(String[] args) throws SQLException {
        String url = "jdbc:h2:mem:batchDemo;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("""
                    CREATE TABLE employees (
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        name VARCHAR(100), department VARCHAR(50), salary DECIMAL(10,2)
                    )""");
            }

            List<NewEmployee> newHires = List.of(
                    new NewEmployee("Rahul Mehta", "Sales", new BigDecimal("60000")),
                    new NewEmployee("Sofia Chen", "Engineering", new BigDecimal("98000")),
                    new NewEmployee("Liam O'Brien", "Marketing", new BigDecimal("72000"))
            );

            String sql = "INSERT INTO employees (name, department, salary) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);   // batch inserts are typically wrapped in one transaction too

                for (NewEmployee emp : newHires) {
                    ps.setString(1, emp.name());
                    ps.setString(2, emp.department());
                    ps.setBigDecimal(3, emp.salary());
                    ps.addBatch();            // queue this parameter set, don't execute yet
                }

                int[] results = ps.executeBatch();   // ONE round-trip for all queued statements
                conn.commit();

                System.out.println("Batch executed, rows affected per statement: "
                        + java.util.Arrays.toString(results));
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
```

--> **`addBatch()` queues a parameter set; `executeBatch()` sends them all together** and returns an `int[]` where each element is the update count for that position in the batch (or a special negative constant like `Statement.SUCCESS_NO_INFO` if the driver can't report an exact count for that entry).
--> **`BatchUpdateException`** -- if one statement in a batch fails, drivers vary on whether they stop immediately or attempt the rest; catch `java.sql.BatchUpdateException` specifically (it's a subclass of `SQLException`) to inspect `getUpdateCounts()` and determine exactly how far the batch got before failing.
--> **Pairing batching with a transaction** (as above, via `setAutoCommit(false)` / `commit()` / `rollback()`) is the typical production pattern -- either all rows in the batch commit together, or none do, and the whole thing still costs only one network round-trip for the statement execution itself. Transactions are covered in full in chapter 04.

# `CallableStatement` -- Invoking Stored Procedures

--> A **stored procedure** is a named, precompiled block of SQL/procedural logic stored and executed ON THE DATABASE SERVER itself. `CallableStatement` is the JDBC interface specifically for invoking one, using JDBC's standard escape syntax `{call procedure_name(?, ?, ...)}` (or `{? = call function_name(?, ...)}` for a function with a return value).

```java
import java.sql.*;

public class CallableStatementDemo {
    public static void main(String[] args) throws SQLException {
        String url = "jdbc:h2:mem:procDemo;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("CREATE TABLE employees (id INT PRIMARY KEY, salary DECIMAL(10,2))");
                setup.executeUpdate("INSERT INTO employees VALUES (1, 50000), (2, 60000)");

                // H2 supports creating a Java-backed "stored procedure" (ALIAS) for demo purposes.
                // Full PL/SQL/T-SQL stored-procedure syntax is vendor-specific (Oracle, SQL Server,
                // PostgreSQL, MySQL all differ) -- this illustrates the JDBC CALLING CONVENTION,
                // which is standard across all of them via CallableStatement.
                setup.execute("""
                    CREATE ALIAS GET_SALARY_TOTAL AS $$
                    Double getSalaryTotal(java.sql.Connection conn) throws java.sql.SQLException {
                        try (java.sql.Statement s = conn.createStatement();
                             java.sql.ResultSet rs = s.executeQuery("SELECT SUM(salary) FROM employees")) {
                            rs.next();
                            return rs.getDouble(1);
                        }
                    }
                    $$;
                    """);
            }

            // Standard JDBC escape syntax for invoking a stored function that returns a value
            try (CallableStatement cs = conn.prepareCall("{? = call GET_SALARY_TOTAL()}")) {
                cs.registerOutParameter(1, Types.DOUBLE);   // declare parameter 1 as an OUT parameter
                cs.execute();
                double total = cs.getDouble(1);
                System.out.println("Total salary (via stored procedure): " + total);
            }
        }
    }
}
```

--> **`registerOutParameter(index, sqlType)`** must be called BEFORE `execute()` for every `OUT` or `INOUT` parameter, telling the driver what SQL type to expect back -- this is the piece with no equivalent in `PreparedStatement`, since `PreparedStatement` only ever sends values IN, never receives them OUT through the same placeholder mechanism.
--> **`IN`, `OUT`, and `INOUT` parameters** -- a stored procedure parameter can be input-only (bind with a normal setter), output-only (register with `registerOutParameter`, then read with a getter after `execute()`), or both (register it AND set it beforehand).
--> **Vendor-specific stored procedure SQL, standard JDBC calling convention** -- the SQL used to DEFINE a stored procedure (PL/pgSQL for PostgreSQL, PL/SQL for Oracle, T-SQL for SQL Server) is entirely vendor-specific and not portable; but the JDBC code used to CALL an already-defined procedure (`CallableStatement`, `{call ...}` escape syntax, `registerOutParameter`) is standard across all of them.
--> **When stored procedures make sense** -- centralizing complex, performance-sensitive logic close to the data (avoiding pulling large datasets across the network just to process them in the app tier), enforcing business rules atomically at the database layer, or working with a database team that owns and maintains procedure-based APIs. Many modern application architectures avoid stored procedures in favor of keeping logic entirely in application code (for version control, testability, and portability across database vendors) -- which approach fits depends on team and organizational conventions, not a universal rule.

# Common Gotchas

--> **Falling back to `Statement` "just this once" for a quick query with user input** -- there is no safe amount of string-concatenated user input in SQL; this is the single most important rule in this entire chapter.
--> **Forgetting a parameter, or getting the 1-based index wrong** -- `PreparedStatement` throws `SQLException: Parameter not set` (or similar wording depending on driver) if you `execute()` without binding every `?` placeholder.
--> **Reusing a `PreparedStatement` object across unrelated SQL strings** -- a `PreparedStatement` is bound to ONE precompiled SQL string for its lifetime; to run different SQL, create a new `PreparedStatement` (or use `Statement` if genuinely there's no fixed SQL structure at all).
--> **Not batching large bulk inserts** -- executing thousands of individual `INSERT` statements one at a time in a loop, each with its own `executeUpdate()` call, is dramatically slower than the same inserts batched with `addBatch()`/`executeBatch()`.
--> **Forgetting `registerOutParameter` before `execute()` on a `CallableStatement`** -- throws an exception when you try to read the OUT parameter's value afterward, since the driver never knew to expect one.

# Best Practices Recap

--> Use `PreparedStatement` for essentially all SQL that includes ANY value not hardcoded by you at compile time -- treat this as a non-negotiable default, not a case-by-case judgment call.
--> Use the correctly-typed setter method for each parameter (`setInt`, `setBigDecimal`, `setTimestamp`, ...) rather than defaulting to `setString` for everything.
--> Batch bulk `INSERT`/`UPDATE` operations with `addBatch()`/`executeBatch()`, typically wrapped in a single explicit transaction.
--> Reach for `CallableStatement` specifically (and only) when invoking a stored procedure/function already defined on the database server.
