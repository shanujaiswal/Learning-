# What a Transaction Is, and Why It Matters

--> A **transaction** is a group of one or more SQL statements treated as a single, indivisible unit of work -- either ALL of them succeed and their effects become permanent (**commit**), or if anything goes wrong partway through, NONE of them take effect (**rollback**), as if none had ever run. This all-or-nothing guarantee is what lets you safely express operations that must happen together, like transferring money between two accounts (debit one, credit the other -- both must succeed, or neither should).
--> Transactions are conventionally summarized by the **ACID** properties:

| Property | Meaning |
|---|---|
| **Atomicity** | The transaction's statements are all-or-nothing -- no partial application if something fails midway |
| **Consistency** | A transaction moves the database from one VALID state to another, never leaving it in a state that violates its own constraints (foreign keys, checks, etc.) |
| **Isolation** | Concurrently running transactions don't see each other's uncommitted, in-progress changes (to a degree controlled by the isolation LEVEL, below) |
| **Durability** | Once a transaction commits, its effects survive -- even a crash immediately afterward won't lose it (the database has persisted it to durable storage) |

# Auto-Commit Mode -- the Default You Need to Know About

--> **Every new JDBC `Connection` starts in auto-commit mode by default** (`conn.getAutoCommit()` returns `true` immediately after connecting) -- in this mode, EVERY individual SQL statement is automatically wrapped in and committed as its OWN implicit transaction, the instant it finishes executing. This is convenient for simple one-off statements, but it's exactly wrong the moment you need multiple statements to succeed or fail TOGETHER.

```java
import java.sql.*;
import java.math.BigDecimal;

public class TransactionBasics {
    public static void main(String[] args) throws SQLException {
        String url = "jdbc:h2:mem:txnDemo;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("""
                    CREATE TABLE accounts (
                        id INT PRIMARY KEY, owner VARCHAR(50), balance DECIMAL(12,2)
                    )""");
                setup.executeUpdate("INSERT INTO accounts VALUES (1, 'Alice', 1000.00)");
                setup.executeUpdate("INSERT INTO accounts VALUES (2, 'Bob', 500.00)");
            }

            transferFunds(conn, 1, 2, new BigDecimal("200.00"));

            try (Statement check = conn.createStatement();
                 ResultSet rs = check.executeQuery("SELECT owner, balance FROM accounts ORDER BY id")) {
                while (rs.next()) {
                    System.out.printf("%s: %.2f%n", rs.getString("owner"), rs.getBigDecimal("balance"));
                }
            }
        }
    }

    static void transferFunds(Connection conn, int fromId, int toId, BigDecimal amount) throws SQLException {
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);   // start an explicit transaction spanning multiple statements

            try (PreparedStatement debit = conn.prepareStatement(
                    "UPDATE accounts SET balance = balance - ? WHERE id = ?")) {
                debit.setBigDecimal(1, amount);
                debit.setInt(2, fromId);
                debit.executeUpdate();
            }

            try (PreparedStatement credit = conn.prepareStatement(
                    "UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
                credit.setBigDecimal(1, amount);
                credit.setInt(2, toId);
                credit.executeUpdate();
            }

            conn.commit();               // BOTH updates become permanent together
            System.out.println("Transfer committed.");
        } catch (SQLException e) {
            conn.rollback();             // EITHER update failing undoes both
            System.err.println("Transfer failed, rolled back: " + e.getMessage());
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);   // restore the connection's prior mode
        }
    }
}
```

--> **The core pattern to memorize**: `setAutoCommit(false)` -> run multiple statements -> `commit()` on success, `rollback()` in a `catch` block on failure -> restore auto-commit mode in a `finally` block (especially important if this `Connection` came from a pool and will be reused by someone else afterward -- see chapter 05).
--> **Why restore auto-commit afterward matters** -- a pooled connection returned to the pool while still in manual-commit mode silently changes behavior for whoever borrows it next; always leave a borrowed connection the way you found it, or the pool/framework you're using may do this for you (Spring's transaction management, for instance, handles this automatically -- see the Spring Boot chapters).

# Isolation Levels

--> **Isolation level** controls how much one transaction can "see" of another transaction's uncommitted or concurrently-changing data. Stricter isolation prevents more anomalies but costs more in locking/contention; looser isolation scales better but permits more surprising concurrent behavior.

| Anomaly | What it means |
|---|---|
| **Dirty read** | Transaction A reads a row that Transaction B has modified but NOT yet committed -- if B then rolls back, A read data that never actually existed |
| **Non-repeatable read** | Transaction A reads the same row twice within itself, and gets a DIFFERENT value the second time, because B committed a change to that row in between |
| **Phantom read** | Transaction A re-runs the same query twice within itself, and a DIFFERENT SET OF ROWS comes back the second time, because B inserted or deleted rows matching that query's condition in between |

| Isolation level | Dirty read | Non-repeatable read | Phantom read | Typical use |
|---|---|---|---|---|
| `TRANSACTION_READ_UNCOMMITTED` | Possible | Possible | Possible | Rare; only when raw throughput matters more than any consistency guarantee |
| `TRANSACTION_READ_COMMITTED` | Prevented | Possible | Possible | **Most common default** (PostgreSQL, SQL Server, Oracle default here) |
| `TRANSACTION_REPEATABLE_READ` | Prevented | Prevented | Possible (technically, though MySQL's InnoDB implementation also largely prevents phantoms here via its specific MVCC scheme) | Stronger consistency for multi-step logic re-reading the same rows |
| `TRANSACTION_SERIALIZABLE` | Prevented | Prevented | Prevented | Strongest guarantee -- transactions behave as if run one at a time, at real cost to concurrency |

```java
// Setting an explicit isolation level on a Connection
conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

int current = conn.getTransactionIsolation();
System.out.println("Current isolation level code: " + current);
```

--> **The JDBC constants map directly to the table above**: `Connection.TRANSACTION_READ_UNCOMMITTED`, `TRANSACTION_READ_COMMITTED`, `TRANSACTION_REPEATABLE_READ`, `TRANSACTION_SERIALIZABLE` -- as `int` constants on `java.sql.Connection`.
--> **The database's own default usually wins if you never call `setTransactionIsolation`** -- most applications never need to touch this and simply accept the database's default (commonly `READ_COMMITTED`) -- reach for a stricter level only when you've identified a specific concurrency anomaly that's actually causing a bug, since stricter isolation trades away concurrency/throughput.
--> **Not every database supports every level** -- call `DatabaseMetaData.supportsTransactionIsolationLevel(int level)` to check before relying on one, if portability across database vendors matters for your application.

# Savepoints -- Partial Rollback Within a Transaction

--> A **savepoint** marks a point WITHIN an in-progress transaction that you can roll back TO, without undoing the entire transaction -- useful when a multi-step transaction has an optional or risky sub-step you want to be able to abandon while keeping everything before it.

```java
import java.sql.*;

public class SavepointDemo {
    public static void main(String[] args) throws SQLException {
        String url = "jdbc:h2:mem:savepointDemo;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("CREATE TABLE orders (id INT PRIMARY KEY, item VARCHAR(50))");
            }

            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO orders VALUES (1, 'Laptop')");

                Savepoint sp1 = conn.setSavepoint("afterFirstOrder");

                stmt.executeUpdate("INSERT INTO orders VALUES (2, 'Mouse')");

                // Suppose we decide the second insert should be undone, but we want
                // to KEEP the first insert and the transaction itself still open.
                conn.rollback(sp1);   // undoes only the 'Mouse' insert; 'Laptop' insert remains staged

                stmt.executeUpdate("INSERT INTO orders VALUES (3, 'Keyboard')");

                conn.commit();   // commits 'Laptop' and 'Keyboard'; 'Mouse' was already rolled back
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

            try (Statement check = conn.createStatement();
                 ResultSet rs = check.executeQuery("SELECT id, item FROM orders ORDER BY id")) {
                while (rs.next()) {
                    System.out.println(rs.getInt("id") + ": " + rs.getString("item"));
                }
                // Prints: 1: Laptop   3: Keyboard   -- 'Mouse' (id 2) is absent
            }
        }
    }
}
```

--> **`conn.setSavepoint()` / `setSavepoint(String name)`** creates a named or anonymous `Savepoint` marker; **`conn.rollback(savepoint)`** undoes everything back to that point WITHOUT ending the transaction; a plain **`conn.rollback()`** (no argument) undoes the ENTIRE transaction back to its start.
--> **Savepoints only make sense with auto-commit OFF** -- they exist within a manually-managed transaction; calling `setSavepoint()` while auto-commit is on either throws or is meaningless, since every statement is already its own committed transaction.
--> **Releasing a savepoint** -- `conn.releaseSavepoint(savepoint)` discards a savepoint you no longer need without rolling back to it, freeing any resources the driver was holding for it; not calling it isn't usually harmful for short transactions, but is good hygiene for long ones with many savepoints.

# Connection Management Beyond Transactions

--> **`conn.isValid(timeoutSeconds)`** -- checks whether the connection is still usable (not dead due to a network drop, database restart, etc.) within the given timeout -- pooling frameworks use this internally to validate connections before handing them out (see chapter 05).
--> **`conn.isClosed()`** -- reports whether `close()` has already been called on this `Connection` object -- calling most other methods on an already-closed connection throws `SQLException`.
--> **`conn.getMetaData()`** -- returns a `DatabaseMetaData` object describing the database itself (product name/version, supported features, table/column introspection) -- useful for writing database-agnostic tooling, rarely needed in typical business CRUD code.
--> **A `Connection` is not thread-safe** -- never share a single `Connection` object across multiple threads running concurrently; each thread/request should have its own (typically borrowed from a pool for the duration of one unit of work, then returned) -- this is another strong argument for pooling over manual connection management, covered next in chapter 05.

# Common Gotchas

--> **Forgetting `setAutoCommit(false)` before a multi-statement transaction** -- without it, each statement commits independently the instant it runs, so a failure on statement 3 of 4 leaves statements 1 and 2 permanently applied -- exactly the partial-application bug transactions exist to prevent.
--> **Forgetting to restore auto-commit mode afterward** -- especially dangerous with pooled connections, since the next borrower of that connection inherits whatever transaction mode you left it in.
--> **Catching `SQLException` but forgetting to call `rollback()`** -- an exception during a manually-managed transaction does NOT automatically roll back anything; you must call `conn.rollback()` explicitly in the `catch` block, or the partially-applied changes remain pending (and may even get committed accidentally by later code).
--> **Choosing `SERIALIZABLE` isolation "to be safe" without measuring the cost** -- the strictest isolation level can dramatically reduce concurrency/throughput under load; pick the loosest isolation level that actually prevents the specific anomaly your application is vulnerable to.
--> **Long-running transactions holding locks** -- a transaction left open for an extended period (waiting on a slow external call, user input, etc. while inside a transaction) holds database locks and can block other transactions -- keep transaction scope as short as possible, ideally excluding any slow I/O or user-facing waits.

# Best Practices Recap

--> Default to auto-commit for single, independent statements; switch to manual commit ONLY for the scope of a genuine multi-statement unit of work, and restore auto-commit immediately after.
--> Always pair `commit()` in the success path with `rollback()` in the failure (`catch`) path -- never one without the other.
--> Keep transactions as short-lived as possible -- do slow/external work OUTSIDE the transaction boundary wherever feasible.
--> Reach for a stricter isolation level only in response to a concretely identified concurrency bug, not preemptively.
--> In frameworks like Spring, prefer declarative transaction management (`@Transactional`) over hand-rolled commit/rollback code where available -- covered in the Spring and Spring Boot chapters -- but understanding the raw JDBC mechanics here is exactly what makes that declarative layer's behavior predictable rather than "magic."
