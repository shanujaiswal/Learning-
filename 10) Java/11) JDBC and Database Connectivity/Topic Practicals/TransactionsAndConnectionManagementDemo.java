/*
 * TransactionsAndConnectionManagementDemo.java
 *
 * Demonstrates:
 *   1. Auto-commit mode (the JDBC default) vs. an explicit multi-statement
 *      transaction using setAutoCommit(false) + commit()/rollback()
 *   2. A genuine rollback: a simulated failure partway through a fund
 *      transfer leaves the database EXACTLY as it was before the transaction
 *   3. Isolation levels: reading/setting them via setTransactionIsolation()
 *      and getTransactionIsolation(), and checking driver support for each
 *   4. Savepoints: setSavepoint(), partial rollback via rollback(savepoint),
 *      and releaseSavepoint()
 *   5. Connection housekeeping methods: isValid(), isClosed(), getMetaData()
 *
 * Covers Theory chapter:
 *   11) JDBC and Database Connectivity/Theory/04 Transactions and Connection Management.md
 *
 * REQUIRES the H2 database driver jar on the classpath (a pure-Java, Type 4 JDBC
 * driver that can run entirely in-memory -- no external database server needed).
 * Maven coordinate:  com.h2database:h2:2.2.224
 *
 * Compile (with h2-2.2.224.jar on the classpath):
 *     javac -cp .;h2-2.2.224.jar 04_transactions_and_connection_management_demo.java
 * Run:
 *     java  -cp .;h2-2.2.224.jar TransactionsAndConnectionManagementDemo
 * (On macOS/Linux replace ';' with ':' in -cp)
 */

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;

public class TransactionsAndConnectionManagementDemo {

    private static final String URL = "jdbc:h2:mem:txnDemo;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static void main(String[] args) {
        printSection("1) Auto-commit (default) vs. an explicit multi-statement transaction");
        demoAutoCommitVsExplicitTransaction();

        printSection("2) A genuine rollback -- a failed transfer leaves balances untouched");
        demoRollbackOnFailure();

        printSection("3) Isolation levels: reading, setting, and checking driver support");
        demoIsolationLevels();

        printSection("4) Savepoints: partial rollback within an open transaction");
        demoSavepoints();

        printSection("5) Connection housekeeping: isValid(), isClosed(), getMetaData()");
        demoConnectionHousekeeping();
    }

    // -------------------------------------------------------------------
    // 1) Every new Connection starts in auto-commit mode: each individual
    //    statement is its own implicit transaction, committed the instant
    //    it finishes. Switching to setAutoCommit(false) lets multiple
    //    statements succeed or fail TOGETHER as one unit of work.
    // -------------------------------------------------------------------
    private static void demoAutoCommitVsExplicitTransaction() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("DROP TABLE IF EXISTS accounts");
                setup.executeUpdate("""
                    CREATE TABLE accounts (
                        id INT PRIMARY KEY, owner VARCHAR(50), balance DECIMAL(12,2)
                    )""");
                setup.executeUpdate("INSERT INTO accounts VALUES (1, 'Alice', 1000.00)");
                setup.executeUpdate("INSERT INTO accounts VALUES (2, 'Bob', 500.00)");
            }

            System.out.println("Default auto-commit mode: " + conn.getAutoCommit());

            transferFunds(conn, 1, 2, new BigDecimal("200.00"));

            printAccountBalances(conn);
        } catch (SQLException e) {
            System.err.println("Transaction demo failed: " + e.getMessage());
        }
    }

    private static void transferFunds(Connection conn, int fromId, int toId, BigDecimal amount) throws SQLException {
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false); // start an explicit transaction spanning multiple statements

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

            conn.commit(); // BOTH updates become permanent together
            System.out.println("Transfer of " + amount + " committed.");
        } catch (SQLException e) {
            conn.rollback(); // EITHER update failing undoes both
            System.err.println("Transfer failed, rolled back: " + e.getMessage());
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit); // restore the connection's prior mode
        }
    }

    // -------------------------------------------------------------------
    // 2) A DELIBERATE failure partway through a transfer (crediting a
    //    non-existent account triggers a foreign-key-style guard via a
    //    manual check) demonstrates that rollback() truly undoes the
    //    already-executed debit -- balances end up EXACTLY as they started.
    // -------------------------------------------------------------------
    private static void demoRollbackOnFailure() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("DROP TABLE IF EXISTS accounts2");
                setup.executeUpdate("""
                    CREATE TABLE accounts2 (
                        id INT PRIMARY KEY, owner VARCHAR(50), balance DECIMAL(12,2)
                    )""");
                setup.executeUpdate("INSERT INTO accounts2 VALUES (1, 'Alice', 1000.00)");
                setup.executeUpdate("INSERT INTO accounts2 VALUES (2, 'Bob', 500.00)");
            }

            System.out.println("Balances BEFORE the failed transfer:");
            printAccountBalances2(conn);

            conn.setAutoCommit(false);
            try {
                try (PreparedStatement debit = conn.prepareStatement(
                        "UPDATE accounts2 SET balance = balance - ? WHERE id = ?")) {
                    debit.setBigDecimal(1, new BigDecimal("300.00"));
                    debit.setInt(2, 1);
                    debit.executeUpdate(); // Alice is debited -- but NOT committed yet
                }

                // Simulate a failure discovered mid-transaction (e.g. business-rule
                // validation, a downstream service call failing, etc.) -- id 99
                // does not exist, so this credit intentionally affects 0 rows, which
                // we treat as a failure condition worth rolling back for.
                int rowsAffected;
                try (PreparedStatement credit = conn.prepareStatement(
                        "UPDATE accounts2 SET balance = balance + ? WHERE id = ?")) {
                    credit.setBigDecimal(1, new BigDecimal("300.00"));
                    credit.setInt(2, 99); // no such account
                    rowsAffected = credit.executeUpdate();
                }

                if (rowsAffected == 0) {
                    throw new SQLException("Credit target account 99 does not exist -- aborting transfer");
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Transfer intentionally failed and was rolled back: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }

            System.out.println("Balances AFTER rollback (must match BEFORE exactly):");
            printAccountBalances2(conn);
        } catch (SQLException e) {
            System.err.println("Rollback demo failed unexpectedly: " + e.getMessage());
        }
    }

    private static void printAccountBalances(Connection conn) throws SQLException {
        try (Statement check = conn.createStatement();
             ResultSet rs = check.executeQuery("SELECT owner, balance FROM accounts ORDER BY id")) {
            while (rs.next()) {
                System.out.printf("  %s: %.2f%n", rs.getString("owner"), rs.getBigDecimal("balance"));
            }
        }
    }

    private static void printAccountBalances2(Connection conn) throws SQLException {
        try (Statement check = conn.createStatement();
             ResultSet rs = check.executeQuery("SELECT owner, balance FROM accounts2 ORDER BY id")) {
            while (rs.next()) {
                System.out.printf("  %s: %.2f%n", rs.getString("owner"), rs.getBigDecimal("balance"));
            }
        }
    }

    // -------------------------------------------------------------------
    // 3) Isolation levels control how much one transaction can see of
    //    another's uncommitted/concurrent changes. Stricter = fewer
    //    anomalies but more contention.
    // -------------------------------------------------------------------
    private static void demoIsolationLevels() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            int defaultLevel = conn.getTransactionIsolation();
            System.out.println("Default isolation level code: " + defaultLevel
                    + " (" + isolationLevelName(defaultLevel) + ")");

            DatabaseMetaData meta = conn.getMetaData();
            int[] allLevels = {
                    Connection.TRANSACTION_READ_UNCOMMITTED,
                    Connection.TRANSACTION_READ_COMMITTED,
                    Connection.TRANSACTION_REPEATABLE_READ,
                    Connection.TRANSACTION_SERIALIZABLE
            };
            for (int level : allLevels) {
                boolean supported = meta.supportsTransactionIsolationLevel(level);
                System.out.println("  " + isolationLevelName(level) + " supported by this driver/DB? " + supported);
            }

            // Explicitly set a stricter isolation level, then confirm the change stuck.
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            int current = conn.getTransactionIsolation();
            System.out.println("After setTransactionIsolation(SERIALIZABLE), current level: "
                    + isolationLevelName(current));

            // Restore a more typical default before this connection is used elsewhere.
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            System.out.println("Restored to: " + isolationLevelName(conn.getTransactionIsolation()));
        } catch (SQLException e) {
            System.err.println("Isolation level demo failed: " + e.getMessage());
        }
    }

    private static String isolationLevelName(int level) {
        return switch (level) {
            case Connection.TRANSACTION_NONE -> "TRANSACTION_NONE";
            case Connection.TRANSACTION_READ_UNCOMMITTED -> "TRANSACTION_READ_UNCOMMITTED";
            case Connection.TRANSACTION_READ_COMMITTED -> "TRANSACTION_READ_COMMITTED";
            case Connection.TRANSACTION_REPEATABLE_READ -> "TRANSACTION_REPEATABLE_READ";
            case Connection.TRANSACTION_SERIALIZABLE -> "TRANSACTION_SERIALIZABLE";
            default -> "UNKNOWN(" + level + ")";
        };
    }

    // -------------------------------------------------------------------
    // 4) Savepoints -- mark a point WITHIN an open transaction that can be
    //    rolled back to WITHOUT undoing the entire transaction.
    // -------------------------------------------------------------------
    private static void demoSavepoints() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement setup = conn.createStatement()) {
                setup.executeUpdate("DROP TABLE IF EXISTS orders");
                setup.executeUpdate("CREATE TABLE orders (id INT PRIMARY KEY, item VARCHAR(50))");
            }

            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO orders VALUES (1, 'Laptop')");

                Savepoint sp1 = conn.setSavepoint("afterFirstOrder");

                stmt.executeUpdate("INSERT INTO orders VALUES (2, 'Mouse')");

                // Suppose we decide the second insert should be undone, but we want
                // to KEEP the first insert and the transaction itself still open.
                conn.rollback(sp1); // undoes only the 'Mouse' insert; 'Laptop' insert remains staged

                stmt.executeUpdate("INSERT INTO orders VALUES (3, 'Keyboard')");

                // Demonstrate releaseSavepoint() -- discards a savepoint we no longer
                // need without rolling back to it (good hygiene for long transactions).
                Savepoint sp2 = conn.setSavepoint("beforeFinalCommit");
                conn.releaseSavepoint(sp2);

                conn.commit(); // commits 'Laptop' and 'Keyboard'; 'Mouse' was already rolled back
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

            try (Statement check = conn.createStatement();
                 ResultSet rs = check.executeQuery("SELECT id, item FROM orders ORDER BY id")) {
                System.out.println("Final orders (expect id 1 'Laptop' and id 3 'Keyboard' only):");
                while (rs.next()) {
                    System.out.println("  " + rs.getInt("id") + ": " + rs.getString("item"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Savepoint demo failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // 5) Connection housekeeping: isValid() checks liveness within a
    //    timeout, isClosed() reports whether close() was already called,
    //    getMetaData() describes the database itself.
    // -------------------------------------------------------------------
    private static void demoConnectionHousekeeping() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(URL, USER, PASSWORD);

            System.out.println("isValid(2 seconds)? " + conn.isValid(2));
            System.out.println("isClosed()? " + conn.isClosed());

            DatabaseMetaData meta = conn.getMetaData();
            System.out.println("Database product: " + meta.getDatabaseProductName()
                    + " " + meta.getDatabaseProductVersion());
            System.out.println("Supports transactions: " + meta.supportsTransactions());

            conn.close();
            System.out.println("After close() -- isClosed()? " + conn.isClosed());

            // Calling most methods on an already-closed connection throws SQLException.
            try {
                conn.createStatement();
            } catch (SQLException e) {
                System.out.println("Expected failure using a closed connection: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.err.println("Connection housekeeping demo failed: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    if (!conn.isClosed()) conn.close();
                } catch (SQLException ignored) {
                    // best-effort cleanup only
                }
            }
        }
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
