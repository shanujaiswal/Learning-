# Why Raw `DriverManager` Connections Don't Scale

--> Opening a JDBC connection is EXPENSIVE relative to running a query: it involves a TCP handshake, often TLS negotiation, database-side authentication, and session setup on the server -- all before a single row of your actual SQL runs. Measured in milliseconds this can look small, but a busy application handling many requests per second, each opening and closing its own connection, pays that cost repeatedly and needlessly.
--> **Connection pooling** solves this by keeping a set of already-open, already-authenticated connections ready and reusing them: a piece of code "borrows" a connection from the pool for the duration of one unit of work, then RETURNS it to the pool (rather than truly closing the underlying socket) so the next piece of code can reuse that same live connection immediately.

```text
 Without pooling                          With pooling
 ---------------                          ------------
 Request 1: open conn -> query -> close   Request 1: borrow from pool -> query -> return to pool
 Request 2: open conn -> query -> close   Request 2: borrow from pool -> query -> return to pool
 Request 3: open conn -> query -> close   Request 3: borrow from pool -> query -> return to pool
 (each pays full connect/auth cost)        (connect/auth cost paid once, up front, per pooled connection)
```

--> **The database server itself also has a hard limit on concurrent connections** (commonly a few hundred, configurable but not infinite) -- an application that opens a fresh connection per request can exhaust that limit under load, causing new connection attempts to fail outright; a bounded pool caps how many connections your application will ever hold open at once, protecting the database from being overwhelmed.

# How `javax.sql.DataSource` Fits In

--> Recall from chapter 01: `javax.sql.DataSource` is the standard JDBC interface for obtaining connections. A POOLING `DataSource` implementation (HikariCP, Apache Commons DBCP2, C3P0) implements this same interface -- so application code calling `dataSource.getConnection()` looks IDENTICAL whether or not pooling is happening underneath; pooling is a property of WHICH `DataSource` implementation you configure, not something application code needs to be specially aware of.
--> **What "closing" a pooled connection actually does** -- when code calls `conn.close()` on a connection obtained from a pooling `DataSource`, the pool intercepts that call: instead of truly severing the underlying socket, it returns the connection to the pool's available set for reuse. This is exactly why `try-with-resources` still works perfectly with pooled connections -- `close()` remains the correct, safe thing to always call, its underlying EFFECT is just different (return-to-pool instead of true disconnect).

# HikariCP -- the De Facto Standard Today

--> **HikariCP** is the most widely used JDBC connection pool in the modern Java ecosystem (it's the DEFAULT pool in Spring Boot since Spring Boot 2.0), prized for being extremely lightweight and fast relative to older alternatives (C3P0, the original Apache DBCP) while exposing a small, clear configuration surface.

```xml
<!-- Maven dependency -->
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>5.1.0</version>
</dependency>
```

```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class HikariSetupExample {
    public static void main(String[] args) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydatabase");
        config.setUsername("app_user");
        config.setPassword("app_password");

        // --- Pool sizing ---
        config.setMaximumPoolSize(10);      // hard ceiling on concurrently open connections
        config.setMinimumIdle(2);           // pool tries to keep at least this many idle & ready

        // --- Timeouts ---
        config.setConnectionTimeout(30_000);  // ms to wait for a connection before giving up (throws)
        config.setIdleTimeout(600_000);       // ms an idle connection may sit before being retired
        config.setMaxLifetime(1_800_000);     // ms before a connection is retired regardless of use
                                               // (guards against subtly stale connections/DB-side limits)

        // --- Leak detection (see below) ---
        config.setLeakDetectionThreshold(60_000);  // warn if a connection is checked out longer than this

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            try (Connection conn = dataSource.getConnection()) {
                System.out.println("Borrowed pooled connection: " + !conn.isClosed());
                // ... use the connection for one unit of work ...
            }   // conn.close() here RETURNS it to the pool, doesn't truly disconnect
        }   // HikariDataSource.close() here shuts the pool down and closes all underlying connections
    }
}
```

--> **`maximumPoolSize`** -- the single most important tuning knob. HikariCP's own guidance is that this should usually be much SMALLER than intuition suggests -- a common starting formula (from HikariCP's documentation, based on database connection cost vs. CPU/disk resources) is roughly `((core_count * 2) + effective_spindle_count)`, and many real deployments run well with pool sizes in the 10-20 range even under significant load, because a smaller number of connections doing constant useful work usually outperforms a huge number of connections mostly contending for the same underlying database resources.
--> **`connectionTimeout`** -- how long a thread will wait to BORROW a connection before `getConnection()` throws `SQLException` -- this is your pool's backpressure valve: if the pool is exhausted (all connections checked out) longer than this, callers fail fast instead of queueing forever.
--> **`maxLifetime`** -- connections are proactively retired and replaced after this long, even if healthy -- this avoids subtly-stale connections and plays well with database-side or load-balancer-side connection time limits (many managed databases and proxies silently kill connections older than some threshold; retiring them slightly earlier from the pool's side avoids surprising failures).

# Configuring via `application.properties` (Spring Boot Context)

--> Spring Boot auto-configures a HikariCP `DataSource` from properties, without any of the Java setup code above being written by hand -- covered fully in the Spring and Spring Boot chapters, shown here only to connect the concept:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mydatabase
spring.datasource.username=app_user
spring.datasource.password=app_password

spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.leak-detection-threshold=60000
```

# Connection Leaks -- What They Are and How to Catch Them

--> A **connection leak** happens when code borrows a connection from the pool (`dataSource.getConnection()`) and then never returns it (never calls `close()`) -- typically because an exception path skipped the `close()` call, or the connection was stored somewhere and simply forgotten. Each leaked connection permanently reduces the pool's available capacity until the application is restarted -- eventually every connection is checked out and never returned, and `getConnection()` starts blocking until `connectionTimeout` expires, then throwing, for EVERY caller.

```java
// LEAKS a connection on the exception path -- if executeUpdate() throws,
// conn.close() is NEVER reached, and this connection is gone from the pool forever
// until the application restarts.
public void unsafeUpdate(DataSource ds, String sql) throws SQLException {
    Connection conn = ds.getConnection();
    Statement stmt = conn.createStatement();
    stmt.executeUpdate(sql);     // if this throws, close() below is skipped
    stmt.close();
    conn.close();
}
```

```java
// SAFE -- try-with-resources guarantees close() (i.e. return-to-pool) runs regardless
// of whether executeUpdate() succeeds, throws, or the method returns early.
public void safeUpdate(DataSource ds, String sql) throws SQLException {
    try (Connection conn = ds.getConnection();
         Statement stmt = conn.createStatement()) {
        stmt.executeUpdate(sql);
    }
}
```

--> **`leakDetectionThreshold`** (shown in the HikariCP config above) is exactly the mechanism for catching this class of bug during development/testing -- if a borrowed connection stays checked out longer than the configured threshold (without being returned), HikariCP logs a warning WITH A STACK TRACE showing where that connection was borrowed, making it straightforward to find the offending code path. Setting this to something like 60 seconds during development (and leaving it disabled, i.e. `0`, or set high in production if the overhead of tracking matters) is a common, low-cost way to catch leaks long before they cause a production incident.
--> **Leaks are a code-discipline problem, not a pool-configuration problem** -- no pool setting fixes a genuine leak; `leakDetectionThreshold` only helps you FIND one faster. The actual fix is always `try-with-resources` (or equivalent guaranteed cleanup) at every call site that borrows a connection.

# Pool Sizing Trade-offs

| Too small | Too large |
|---|---|
| Threads queue waiting for a connection under load, increasing latency; `connectionTimeout` failures under spikes | Wastes memory/resources on unused connections; can overwhelm the database server's own connection limit; more connections doesn't necessarily mean more throughput once the database's CPU/disk becomes the actual bottleneck |

--> **The right size is empirical, not purely formulaic** -- start from a modest baseline (HikariCP's own docs suggest starting smaller than intuition suggests), load-test, and watch for connection-wait time and database-side saturation metrics rather than guessing a large number "to be safe."

# Common Gotchas

--> **Confusing a pool's "connection" with the DATABASE's connection limit** -- if you run multiple application instances (common in production, e.g. several containers/pods), EACH instance's pool counts separately against the database's total connection limit -- five instances each configured for a max pool size of 20 means up to 100 total connections hitting the database, which may exceed what it allows.
--> **Not closing `DataSource`/pool on application shutdown** -- a `HikariDataSource` itself should be closed (typically once, at application shutdown) to cleanly release all its underlying connections -- frameworks like Spring Boot handle this automatically as part of the application context shutdown lifecycle.
--> **Setting `maximumPoolSize` far higher than needed "just in case"** -- oversized pools can actually reduce throughput by increasing contention on the database server's own resources, contrary to the intuition that "more connections must mean more capacity."
--> **Ignoring leak-detection warnings in logs** -- a leak that's merely slow to manifest (a pool that's large enough to mask it for a while) can still eventually exhaust capacity in production; treat leak-detection log warnings as bugs to fix, not noise to suppress.
--> **Forgetting a connection is single-threaded** -- pooling doesn't change the rule from chapter 04 that one `Connection` must not be used by multiple threads concurrently; the pool's job is to hand out separate borrowed connections to separate threads, not to make one connection safely shared.

# Best Practices Recap

--> Always use a pooling `DataSource` (HikariCP by default in the Spring ecosystem) for any application beyond a one-off script.
--> Start pool sizing modest, measure under realistic load, and adjust based on evidence rather than intuition.
--> Enable `leakDetectionThreshold` at least during development and staging to catch leaked connections before they reach production.
--> Always borrow and release connections with `try-with-resources` (or an equivalent guaranteed-cleanup pattern) -- this single habit prevents the large majority of real-world connection leaks.
--> Account for the multiplicative effect of multiple application instances each holding their own pool against a shared database connection limit when sizing pools in a horizontally-scaled deployment.
