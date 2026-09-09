/*
 * ConnectionPoolingDemo.java
 *
 * Demonstrates:
 *   1. WHY raw DriverManager connections don't scale (each pays full
 *      connect/auth cost) -- timed comparison of opening N fresh connections
 *      vs. borrowing N times from a small hand-rolled pool
 *   2. A SIMPLE HAND-ROLLED connection pool built on top of H2 connections,
 *      illustrating the CONCEPT HikariCP implements properly in production:
 *        - a bounded set of pre-opened connections
 *        - borrow (checkout) / return (checkin) semantics
 *        - a "close() returns to pool instead of truly disconnecting" wrapper,
 *          via a dynamic Proxy implementing java.sql.Connection
 *        - pool exhaustion behavior: borrowers block up to a timeout, then fail
 *   3. A DataSource configuration illustration matching what HikariCP's real
 *      API looks like (HikariConfig/HikariDataSource), for comparison
 *
 * IMPORTANT: This hand-rolled pool is a TEACHING TOOL ONLY. It is deliberately
 * simplified (no idle-timeout eviction, no max-lifetime retirement, no leak
 * detection, minimal validation) to make the core borrow/return mechanics
 * visible. Real production code should always use a battle-tested pool --
 * HikariCP is the de facto standard in the Java ecosystem today (and the
 * default in Spring Boot since 2.0). See the HikariCP configuration example
 * at the bottom of this file for what that would look like instead.
 *
 * Covers Theory chapter:
 *   11) JDBC and Database Connectivity/Theory/05 Connection Pooling.md
 *
 * REQUIRES the H2 database driver jar on the classpath (a pure-Java, Type 4 JDBC
 * driver that can run entirely in-memory -- no external database server needed).
 * Maven coordinate:  com.h2database:h2:2.2.224
 * (HikariCP itself is NOT required to compile/run this file -- section 3 below
 * is illustrative source code only, wrapped in a comment, not executed.)
 *
 * Compile (with h2-2.2.224.jar on the classpath):
 *     javac -cp .;h2-2.2.224.jar 05_connection_pooling_demo.java
 * Run:
 *     java  -cp .;h2-2.2.224.jar ConnectionPoolingDemo
 * (On macOS/Linux replace ';' with ':' in -cp)
 */

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ConnectionPoolingDemo {

    private static final String URL = "jdbc:h2:mem:poolDemo;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static void main(String[] args) throws Exception {
        printSection("1) Cost comparison: opening N fresh connections vs. borrowing N times from a pool");
        demoConnectionCostComparison();

        printSection("2) Hand-rolled pool: borrow/return semantics, close() returns to pool");
        demoBorrowAndReturn();

        printSection("3) Hand-rolled pool: exhaustion behavior when all connections are checked out");
        demoPoolExhaustion();

        printSection("4) DataSource configuration illustration (HikariCP shape, for comparison)");
        printHikariIllustration();

        printSection("5) Shutting the hand-rolled pool down cleanly");
        demoPoolShutdown();
    }

    // -------------------------------------------------------------------
    // 1) A rough, illustrative timing comparison. Absolute numbers will
    //    vary by machine, but opening a brand-new JDBC connection is
    //    consistently and measurably slower than reusing an already-open,
    //    already-authenticated one from a pool -- this remains true even
    //    against H2's in-process driver, and is dramatically more true
    //    against a real network database server (TCP handshake, auth, etc.).
    // -------------------------------------------------------------------
    private static void demoConnectionCostComparison() throws SQLException {
        int iterations = 20;

        long startFresh = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
                touchDatabase(conn);
            }
        }
        long freshDurationMs = (System.nanoTime() - startFresh) / 1_000_000;

        SimpleConnectionPool pool = new SimpleConnectionPool(URL, USER, PASSWORD, 5);
        long startPooled = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try (Connection conn = pool.getConnection()) {
                touchDatabase(conn);
            }
        }
        long pooledDurationMs = (System.nanoTime() - startPooled) / 1_000_000;
        pool.shutdown();

        System.out.println(iterations + " fresh DriverManager connections took:  " + freshDurationMs + " ms");
        System.out.println(iterations + " pooled borrow/return cycles took:      " + pooledDurationMs + " ms");
        System.out.println("(Even against H2's lightweight in-process driver, reuse tends to win; the gap");
        System.out.println(" widens dramatically against a real network database server with TCP/TLS/auth.)");
    }

    private static void touchDatabase(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1").close();
        }
    }

    // -------------------------------------------------------------------
    // 2) Borrow a connection, use it, "close" it (which really returns it
    //    to the pool), and confirm the underlying physical connection is
    //    reused rather than truly disconnected.
    // -------------------------------------------------------------------
    private static void demoBorrowAndReturn() throws SQLException {
        SimpleConnectionPool pool = new SimpleConnectionPool(URL, USER, PASSWORD, 3);
        System.out.println("Pool created with max size 3. Available right now: " + pool.availableCount());

        Connection first;
        try (Connection conn = pool.getConnection()) {
            first = ((PooledConnectionHandle) Proxy.getInvocationHandler(conn)).physicalConnection;
            System.out.println("Borrowed a connection. Available now: " + pool.availableCount());
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS pool_demo (id INT PRIMARY KEY)");
                stmt.executeUpdate("MERGE INTO pool_demo VALUES (1)");
            }
        } // conn.close() here RETURNS the physical connection to the pool, doesn't truly disconnect it
        System.out.println("After try-with-resources exits, available again: " + pool.availableCount());

        try (Connection conn2 = pool.getConnection()) {
            Connection physical2 = ((PooledConnectionHandle) Proxy.getInvocationHandler(conn2)).physicalConnection;
            System.out.println("Same physical connection object reused? " + (first == physical2));
        }

        pool.shutdown();
    }

    // -------------------------------------------------------------------
    // 3) Pool exhaustion: borrow every connection the pool has, then try
    //    to borrow ONE more -- it must block up to a timeout and then fail,
    //    exactly like a real pool's connectionTimeout backpressure valve.
    // -------------------------------------------------------------------
    private static void demoPoolExhaustion() throws SQLException {
        int poolSize = 2;
        SimpleConnectionPool pool = new SimpleConnectionPool(URL, USER, PASSWORD, poolSize);

        List<Connection> checkedOut = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            checkedOut.add(pool.getConnection());
        }
        System.out.println("Checked out all " + poolSize + " connections. Available: " + pool.availableCount());

        long start = System.nanoTime();
        try {
            // No connections free, and none will be returned before the timeout below --
            // this call blocks, then throws once borrowTimeoutMs elapses.
            pool.getConnection();
            System.out.println("Unexpectedly succeeded borrowing beyond pool capacity!");
        } catch (SQLException e) {
            long waitedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("Pool exhaustion correctly threw after ~" + waitedMs + " ms: " + e.getMessage());
        }

        for (Connection conn : checkedOut) {
            conn.close(); // return every borrowed connection to the pool
        }
        System.out.println("Returned all connections. Available again: " + pool.availableCount());
        pool.shutdown();
    }

    // -------------------------------------------------------------------
    // 4) What the SAME configuration would look like using the real,
    //    production-grade HikariCP pool instead of the hand-rolled one
    //    above. This block is printed as TEXT, not compiled/executed --
    //    the HikariCP jar is not assumed to be on this file's classpath.
    // -------------------------------------------------------------------
    private static void printHikariIllustration() {
        String illustration = """
                // Maven dependency: com.zaxxer:HikariCP:5.1.0  (NOT required to run this demo file)
                import com.zaxxer.hikari.HikariConfig;
                import com.zaxxer.hikari.HikariDataSource;

                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:h2:mem:poolDemo;DB_CLOSE_DELAY=-1");
                config.setUsername("sa");
                config.setPassword("");

                config.setMaximumPoolSize(10);        // hard ceiling on concurrently open connections
                config.setMinimumIdle(2);              // pool tries to keep at least this many idle & ready
                config.setConnectionTimeout(30_000);   // ms to wait for a connection before giving up
                config.setIdleTimeout(600_000);        // ms an idle connection may sit before being retired
                config.setMaxLifetime(1_800_000);      // ms before a connection is retired regardless of use
                config.setLeakDetectionThreshold(60_000); // warn if checked out longer than this

                try (HikariDataSource dataSource = new HikariDataSource(config)) {
                    try (Connection conn = dataSource.getConnection()) {
                        // ... use the connection for one unit of work ...
                    } // conn.close() here RETURNS it to the pool
                } // HikariDataSource.close() shuts the pool down, closing all underlying connections
                """;
        System.out.println(illustration);
        System.out.println("HikariCP is the de facto standard JDBC pool in the Java ecosystem (Spring Boot's");
        System.out.println("default since 2.0). It adds everything this teaching demo deliberately omits:");
        System.out.println("idle-timeout eviction, max-lifetime retirement, connection validation, metrics,");
        System.out.println("and real leak detection with captured stack traces.");
    }

    private static void demoPoolShutdown() throws SQLException {
        SimpleConnectionPool pool = new SimpleConnectionPool(URL, USER, PASSWORD, 2);
        System.out.println("Pool opened with " + pool.availableCount() + " ready connections.");
        pool.shutdown();
        System.out.println("Pool shut down -- all underlying physical connections closed.");
        try {
            pool.getConnection();
            System.out.println("Unexpectedly able to borrow from a shut-down pool!");
        } catch (SQLException e) {
            System.out.println("Expected failure borrowing from a shut-down pool: " + e.getMessage());
        }
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    // =====================================================================
    // A minimal, hand-rolled connection pool -- TEACHING TOOL ONLY.
    // Illustrates the CONCEPT: a bounded set of pre-opened connections,
    // borrow/return semantics, and pool-exhaustion backpressure. Missing,
    // deliberately, everything a production pool like HikariCP provides
    // beyond these basics (idle/lifetime eviction, validation, metrics,
    // leak detection, proper concurrency tuning).
    // =====================================================================
    static class SimpleConnectionPool {
        private final Deque<Connection> available = new ArrayDeque<>();
        private final Object lock = new Object();
        private final int maxSize;
        private volatile boolean shutDown = false;
        private static final long BORROW_TIMEOUT_MS = 500; // analogous to HikariCP's connectionTimeout

        SimpleConnectionPool(String url, String user, String password, int size) throws SQLException {
            this.maxSize = size;
            for (int i = 0; i < size; i++) {
                available.add(DriverManager.getConnection(url, user, password));
            }
        }

        int availableCount() {
            synchronized (lock) {
                return available.size();
            }
        }

        /** Borrows a physical connection, wrapped so close() returns it to the pool instead of disconnecting. */
        Connection getConnection() throws SQLException {
            if (shutDown) {
                throw new SQLException("Cannot borrow a connection: pool has been shut down");
            }
            synchronized (lock) {
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BORROW_TIMEOUT_MS);
                while (available.isEmpty()) {
                    long remainingNanos = deadline - System.nanoTime();
                    if (remainingNanos <= 0) {
                        throw new SQLException("Timed out after " + BORROW_TIMEOUT_MS
                                + " ms waiting for a connection -- pool exhausted (max size " + maxSize + ")");
                    }
                    try {
                        lock.wait(TimeUnit.NANOSECONDS.toMillis(remainingNanos));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrupted while waiting for a pooled connection", ie);
                    }
                }
                Connection physical = available.removeFirst();
                return wrapAsPooled(physical);
            }
        }

        private void returnConnection(Connection physical) {
            synchronized (lock) {
                if (!shutDown) {
                    available.addLast(physical);
                    lock.notifyAll();
                }
            }
        }

        void shutdown() throws SQLException {
            synchronized (lock) {
                shutDown = true;
                for (Connection conn : available) {
                    conn.close();
                }
                available.clear();
            }
        }

        private Connection wrapAsPooled(Connection physical) {
            PooledConnectionHandle handle = new PooledConnectionHandle(physical, this);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    handle);
        }
    }

    /**
     * InvocationHandler backing each borrowed Connection proxy. Every method
     * delegates to the real, physical connection EXCEPT close(), which instead
     * returns the physical connection to the pool -- exactly mirroring how a
     * real pooling DataSource (HikariCP included) intercepts close().
     */
    static class PooledConnectionHandle implements InvocationHandler {
        final Connection physicalConnection;
        private final SimpleConnectionPool owner;
        private volatile boolean returned = false;

        PooledConnectionHandle(Connection physicalConnection, SimpleConnectionPool owner) {
            this.physicalConnection = physicalConnection;
            this.owner = owner;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "close" -> {
                    if (!returned) {
                        returned = true;
                        owner.returnConnection(physicalConnection);
                    }
                    return null;
                }
                case "isClosed" -> {
                    return returned;
                }
                default -> {
                    if (returned) {
                        throw new SQLException("Connection has already been returned to the pool");
                    }
                    try {
                        return method.invoke(physicalConnection, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
            }
        }
    }
}
