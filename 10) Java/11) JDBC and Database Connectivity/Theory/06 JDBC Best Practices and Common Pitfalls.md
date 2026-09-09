# This Chapter's Purpose

--> The previous five chapters covered JDBC feature by feature -- connecting, `Statement`/`PreparedStatement`, transactions, pooling. This closing chapter consolidates the cross-cutting habits that separate correct-but-fragile JDBC code from production-quality JDBC code: resource safety, exception handling discipline, and turning raw `ResultSet` rows into usable Java objects.

# Resource Leak Prevention -- the Recap That's Worth Repeating

--> Every prior chapter has touched this, because it's the single most consequential habit in JDBC code: `Connection`, `Statement`/`PreparedStatement`/`CallableStatement`, and `ResultSet` all implement `AutoCloseable`, and ALL of them must close, in every code path, including exception paths.

```java
// The one pattern to standardize on -- multi-resource try-with-resources,
// resources closed automatically in REVERSE declaration order, on every path.
public List<String> findDepartmentNames(DataSource ds) throws SQLException {
    String sql = "SELECT DISTINCT department FROM employees ORDER BY department";
    List<String> names = new ArrayList<>();

    try (Connection conn = ds.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
            names.add(rs.getString("department"));   // materialize into plain Java objects
        }
    }
    return names;   // the caller receives a plain List<String>, decoupled from any JDBC resource
}
```

--> **Never return a raw `ResultSet` (or `Statement`/`Connection`) from a method** -- by the time the caller tries to use it, the `try-with-resources` block that created it has already closed everything, and every subsequent call throws `SQLException`. Always fully convert `ResultSet` rows into plain domain objects (records, POJOs, DTOs) BEFORE the method returns.
--> **A connection obtained outside try-with-resources, even briefly, is a leak waiting to happen** -- if any code path between `getConnection()` and the `try` block can throw, that connection is never returned to the pool. Always put the acquisition itself inside the `try(...)` parentheses, not before it.

# Exception Handling with `SQLException`

--> `SQLException` is a CHECKED exception -- every JDBC method that can fail declares `throws SQLException`, and calling code must either catch it or declare it further up the call chain. This is deliberate: database failures (network drops, constraint violations, timeouts, deadlocks) are exactly the kind of recoverable-but-must-be-acknowledged failure checked exceptions are designed for (see the Exception Handling and IO chapter for the broader checked-vs-unchecked design philosophy).

```java
try (Connection conn = ds.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, email);
    ps.executeUpdate();
} catch (SQLException e) {
    System.err.println("SQL State: " + e.getSQLState());     // standard 5-char SQL State code (e.g. "23505")
    System.err.println("Vendor code: " + e.getErrorCode());  // database-vendor-specific numeric error code
    System.err.println("Message: " + e.getMessage());
    throw new DataAccessException("Failed to save user with email " + email, e);   // wrap and rethrow
}
```

--> **`getSQLState()`** -- a standardized (X/Open SQLstate) 5-character string identifying the general CATEGORY of error across database vendors -- e.g. class `23` is "integrity constraint violation" (so `23505` on many databases means a unique-constraint violation), class `08` is a connection exception. This is the more PORTABLE way to programmatically distinguish error categories across different database vendors.
--> **`getErrorCode()`** -- a vendor-specific numeric code with meaning defined by that particular database (PostgreSQL, MySQL, and Oracle each assign their own numbers) -- useful when you know you're targeting one specific vendor and need finer-grained detail than SQL State provides.
--> **`SQLException` is chainable** -- a single failure can produce a LINKED LIST of `SQLException` causes (`e.getNextException()`), separate from the normal Java `getCause()` chain, because some drivers/databases report multiple related warnings/errors for one failed operation. Iterate `getNextException()` when you need every reported detail, not just the first.
--> **Wrapping `SQLException` in a domain-specific unchecked exception** (as shown above with a hypothetical `DataAccessException`) is a common and often better pattern in layered applications -- it keeps JDBC-specific exception types from leaking into higher application layers (service/controller code) that shouldn't need to know or care that persistence happens to be implemented via JDBC. This is exactly the pattern Spring's `DataAccessException` hierarchy formalizes, covered in the Spring Data chapters.
--> **Never swallow `SQLException` silently** -- an empty `catch (SQLException e) {}` block hides real failures (a failed `INSERT` looks, from the caller's perspective, identical to a successful one) -- at minimum, log it; ideally, propagate it (wrapped or not) so the failure is visible to whoever needs to react to it.

# `ResultSetMetaData` -- Inspecting Results Generically

--> Covered briefly in chapter 02; worth a closer, practical look here since it's the foundation of writing GENERIC row-mapping code that doesn't need to know column names in advance.

```java
import java.sql.*;
import java.util.*;

public class GenericRowMapper {
    /** Converts any ResultSet's current row into a column-name -> value Map. */
    public static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>();   // preserves column order

        for (int i = 1; i <= columnCount; i++) {
            row.put(meta.getColumnLabel(i), rs.getObject(i));
        }
        return row;
    }

    public static List<Map<String, Object>> queryToMaps(Connection conn, String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(rowToMap(rs));
            }
        }
        return results;
    }
}
```

--> **`getColumnLabel(i)` vs `getColumnName(i)`** -- `getColumnLabel` returns the COLUMN ALIAS if the query used `AS`, falling back to the actual column name otherwise; `getColumnName` returns the underlying column's real name, ignoring any alias. For most application purposes (especially generic mapping like above), `getColumnLabel` is the more useful one, since it respects however the query author chose to name the output column.
--> **When generic row-to-map mapping is appropriate** -- ad hoc reporting tools, database browsers, generic export utilities -- anywhere the shape of the data isn't known at compile time. For typical application CRUD code where you DO know your columns ahead of time, a dedicated mapping method per entity (below) is clearer and type-safe.

# Manually Mapping Rows to Domain Objects

--> Before reaching for an ORM (Hibernate/JPA, covered in a later chapter) or a lightweight mapping helper (Spring's `JdbcTemplate`/`RowMapper`), it's worth understanding the manual pattern those tools are automating -- it's exactly this:

```java
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public class EmployeeRepository {

    public record Employee(int id, String name, String department, BigDecimal salary) {}

    private final DataSource dataSource;

    public EmployeeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Maps exactly one ResultSet row (must already be positioned via rs.next()) to an Employee. */
    private Employee mapRow(ResultSet rs) throws SQLException {
        return new Employee(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("department"),
                rs.getBigDecimal("salary")
        );
    }

    public Optional<Employee> findById(int id) throws SQLException {
        String sql = "SELECT id, name, department, salary FROM employees WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public List<Employee> findByDepartment(String department) throws SQLException {
        String sql = "SELECT id, name, department, salary FROM employees WHERE department = ? ORDER BY name";
        List<Employee> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, department);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }
}
```

--> **Why a dedicated `mapRow` method** -- centralizing row-to-object mapping in one place per entity means a column rename or type change only needs updating in one method, rather than scattered across every query that touches that table.
--> **`Optional<Employee>` for a possibly-absent single row** -- clearly communicates "this query might legitimately find nothing" through the type system, rather than returning `null` and relying on every caller to remember to check for it (see the Java 8+ Modern Features chapter for `Optional` in depth).
--> **Java records are a natural fit for immutable row-mapped data** -- a `record` gives you a constructor, accessors, `equals`/`hashCode`/`toString` for free, matching the typically immutable, data-only nature of a row fetched from a database.
--> **This IS essentially what an ORM's mapping layer or Spring's `RowMapper` automates** -- understanding the manual version first (as shown here) makes those higher-level tools feel like a natural extension rather than an opaque black box, and remains useful for the cases where a full ORM is overkill (simple read-heavy queries, reporting, batch jobs).

# Common Pitfalls Across the Whole JDBC Workflow (Consolidated)

| Pitfall | Why it's a problem | Fix |
|---|---|---|
| Not using `try-with-resources` | Leaks connections/statements/result sets on exception paths | Always wrap JDBC resources in `try-with-resources` |
| String-concatenated SQL with any external input | SQL injection | Always use `PreparedStatement` with bound parameters |
| Returning a raw `ResultSet`/`Statement`/`Connection` from a method | Caller gets a `SQLException` the instant the underlying resource is closed | Fully map rows to plain objects before returning |
| Forgetting `setAutoCommit(false)` for multi-statement units of work | Partial application if a later statement fails | Explicit transaction boundaries with commit/rollback |
| Silently swallowing `SQLException` | Real failures go unnoticed | At minimum log; ideally propagate (wrapped or not) |
| Opening a raw `DriverManager` connection per request in a live application | Doesn't scale; can exhaust DB connection limits | Use a pooling `DataSource` (HikariCP) |
| 0-based column/parameter index assumptions | `SQLException: invalid column index` | Remember JDBC indexes are 1-based throughout |
| Oversized or unmeasured connection pool | Can reduce throughput via DB-side contention; wastes resources | Size modestly, measure, adjust based on load |
| No leak detection enabled during development | Leaked connections go unnoticed until production incident | Enable `leakDetectionThreshold` (HikariCP) in dev/staging |

# Where JDBC Sits Relative to Higher-Level Tools

--> **Everything in this chapter is what higher-level persistence tools are built ON TOP OF** -- Spring's `JdbcTemplate` automates resource closing and exception wrapping around this exact `Connection`/`Statement`/`ResultSet` pattern; JPA/Hibernate (a later chapter) generates the SQL and mapping code that would otherwise be written by hand as shown here. None of those tools remove the underlying JDBC concepts -- they remove BOILERPLATE around them, which is precisely why understanding raw JDBC first makes every layer built on top of it easier to reason about (and easier to debug, since a mysterious "N+1 query" problem or a connection-pool exhaustion incident in a Hibernate/Spring application is, underneath, exactly the JDBC-level behavior covered across this entire chapter set).

# Best Practices Recap (Whole-Unit Summary)

--> Prefer `PreparedStatement` over `Statement` by default; reserve `Statement` for fixed, hardcoded SQL only.
--> Wrap every JDBC resource acquisition in `try-with-resources`, and never let a `ResultSet`/`Statement`/`Connection` escape the method that created it.
--> Use a pooling `DataSource` (HikariCP) for any real application; tune pool size from measurement, not intuition.
--> Wrap multi-statement units of work in explicit transactions (`setAutoCommit(false)` + `commit()`/`rollback()`), keeping their scope as short as possible.
--> Handle `SQLException` deliberately -- inspect `getSQLState()`/`getErrorCode()` where useful, never swallow it silently, and consider wrapping it in a domain-specific exception at architectural boundaries.
--> Map `ResultSet` rows into plain, immutable domain objects (records are a natural fit) as close as possible to the data-access layer, so the rest of the application never touches JDBC types directly.
