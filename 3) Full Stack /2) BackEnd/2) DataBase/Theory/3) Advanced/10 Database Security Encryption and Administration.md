## Encryption at Rest and in Transit

--> The Access Control (GRANT/REVOKE) file covers WHO can access data -- encryption protects the data itself even if that access control is somehow bypassed (a stolen physical disk, an intercepted network connection).

# Encryption at Rest

--> Protects data as it's actually STORED on disk -- if an attacker steals the physical storage (a disk, a backup file, a decommissioned drive that wasn't properly wiped), the data is unreadable without the encryption key.
--> **Transparent Data Encryption (TDE)** -- supported by SQL Server, Oracle, and available via extensions/cloud offerings for PostgreSQL/MySQL -- encrypts the entire database's files at the storage layer, TRANSPARENTLY to queries (the application and its SQL queries are completely unaware encryption is happening -- no application code changes needed).
```sql
-- SQL Server TDE setup (conceptual) -- once enabled, every read/write is transparently encrypted/decrypted
CREATE DATABASE ENCRYPTION KEY WITH ALGORITHM = AES_256 ENCRYPTION BY SERVER CERTIFICATE MyServerCert;
ALTER DATABASE MyAppDB SET ENCRYPTION ON;
```
--> **Column-level encryption** -- a more targeted alternative -- encrypt only specific SENSITIVE columns (a social security number, a credit card number) rather than the entire database, often using application-level encryption/decryption logic rather than a database-transparent feature, trading transparency for the ability to keep the decryption key entirely OUTSIDE the database's own reach (so even a database administrator with full access can't read the plaintext without also having the separate application-held key).

# Encryption in Transit

--> Protects data as it TRAVELS over the network between the application and the database server -- without it, anyone who can intercept network traffic (on a shared network, a compromised router) can read every query and every result in plain text, including credentials sent during connection.
```sql
-- Requiring an SSL/TLS-encrypted connection (MySQL example)
ALTER USER 'app_user'@'%' REQUIRE SSL;
```
```python
# Application connection string requiring SSL/TLS -- PostgreSQL example
"postgresql://user:pass@host:5432/mydb?sslmode=require"
```
--> **Why both matter together, not just one** -- encryption at rest alone doesn't help if an attacker just intercepts a query's plaintext results over an unencrypted network connection; encryption in transit alone doesn't help if an attacker steals the physical disk instead. A genuinely secure setup needs both, layered alongside the access-control and least-privilege practices already covered in the GRANT/REVOKE file.

## Row-Level Security, Column-Level Permissions, and Data Masking

--> The GRANT/REVOKE file covers table- and database-level permission scope -- several databases support FINER-GRAINED security models below the table level.

# Row-Level Security (RLS)

--> Restricts WHICH ROWS a given user/role can see or modify, enforced automatically by the database itself, rather than relying on every application query to remember to add the right `WHERE` filter.

```sql
-- PostgreSQL example -- a sales rep can only ever see their OWN region's orders, enforced at the database level
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;

CREATE POLICY region_policy ON orders
    USING (region = current_setting('app.current_user_region'));

-- Even a query with NO WHERE clause at all only returns rows the policy allows:
SELECT * FROM orders;   -- silently filtered to the current user's region automatically
```

--> **Why this is a genuinely stronger guarantee than an application-level filter** -- if a developer forgets to add a `WHERE region = ...` filter in some newly-written application code path, RLS still enforces the restriction at the database layer regardless -- the SAME kind of "last line of defense" argument the Constraints file makes for `CHECK`/`NOT NULL` constraints, just applied to row-level access instead of data validity.

# Column-Level Permissions

```sql
-- Grant access to only specific columns, not the whole table (extends the GRANT/REVOKE file's table-level examples)
GRANT SELECT (CustomerName, City) ON Customers TO 'support_agent'@'%';
-- support_agent can query CustomerName and City, but querying Email or PaymentInfo is rejected
```

# Data Masking

--> Returns a MODIFIED, non-sensitive version of a value to unauthorized users instead of either the real value or a flat access denial -- useful when a role genuinely needs to know a column EXISTS and has some value (for testing, support workflows) without seeing the real sensitive data.

```sql
-- SQL Server Dynamic Data Masking example
ALTER TABLE Customers ALTER COLUMN CreditCardNumber ADD MASKED WITH (FUNCTION = 'partial(0,"XXXX-XXXX-XXXX-",4)');
-- A support agent querying this column sees "XXXX-XXXX-XXXX-1234" instead of the full real number
```

--> Commonly used for exposing realistic-looking but non-sensitive data to a staging/test environment seeded from production, or for support staff who need to verify "yes, a card is on file ending in 1234" without ever seeing the complete number.

## Database Monitoring and Observability

--> Beyond the slow query log mentioned briefly in the Indexing file, a genuinely production-ready database needs ongoing visibility into its own health, not just after-the-fact log review.

--> **Key metrics to track** -- query latency (average AND percentiles like p95/p99, since averages hide occasional very slow outliers), active connection count relative to the connection pool/max-connections limit, replication lag (directly connecting to the Replication file's lag discussion), disk I/O and free space, lock wait times/deadlock frequency, and cache hit ratio (what fraction of reads are served from memory vs requiring an actual disk read).
--> **Alerting** -- metrics are only useful if something actually NOTICES when they cross a concerning threshold (connections approaching the max limit, replication lag growing instead of staying near zero, disk filling up) and notifies a human BEFORE it becomes an outage, rather than metrics simply being available for someone to check manually after something has already broken.
--> **Health checks** -- a lightweight, frequent automated query (e.g. `SELECT 1`) that application infrastructure (a load balancer, a container orchestrator) uses to verify the database is actually responsive, distinct from full metric collection -- often what actually decides whether traffic gets routed to a given database replica at all.
--> **Slow query logs, revisited** -- logging every query exceeding a time threshold (already mentioned in the Indexing file) is the most direct way to find real, currently-happening bottlenecks -- but is fundamentally REACTIVE (it tells you about a problem that already occurred); metrics/alerting close the gap by surfacing trouble proactively, ideally before a slow query even happens by catching resource pressure building up first.

## Database Authentication Mechanisms

--> Beyond `CREATE USER ... IDENTIFIED BY 'password'` (covered in the GRANT/REVOKE file), production database deployments typically layer on stronger authentication.

--> **Password policies** -- enforcing minimum complexity, rotation schedules, and lockout after repeated failed attempts, directly analogous to the application-level password policy concepts covered in the Cyber Security/IAM notes, just applied to database accounts specifically.
--> **Certificate-based authentication (mutual TLS)** -- the connecting client presents a CERTIFICATE (rather than, or in addition to, a password) that the database verifies against a trusted certificate authority -- commonly used for service-to-service database connections where a hardcoded password would be a weaker, more easily leaked credential.
--> **IAM-integrated authentication** -- cloud-managed databases (AWS RDS, Google Cloud SQL) can authenticate database connections using the CLOUD PROVIDER'S own identity system (IAM roles) instead of a separate database-specific password at all -- meaning access can be granted/revoked through the same centralized IAM policies already governing everything else in the cloud account, and short-lived IAM-issued tokens replace a long-lived static database password that could otherwise leak and remain valid indefinitely.
```text
-- Conceptual flow for IAM database authentication
Application --> requests a short-lived auth token from AWS IAM --> connects to RDS using that token instead of a password
-- The token expires automatically after a short window, unlike a traditional password that's valid until manually changed
```

## High Availability Clustering and Consensus

--> The Replication and Sharding file covers simple primary-replica failover (one replica promoted when the primary fails) -- more sophisticated HA clustering setups use formal CONSENSUS PROTOCOLS to coordinate this more rigorously across many nodes.

--> **The core problem simple failover doesn't fully solve** -- if the primary merely becomes slow/unreachable rather than fully crashing, and TWO different replicas each independently decide "the primary is gone, I should become the new primary," you get a **split-brain** scenario -- two nodes both believing they're the authoritative primary, potentially accepting conflicting writes simultaneously.
--> **Raft and Paxos** -- consensus algorithms that let a cluster of nodes agree on a single, unambiguous decision (like "which node is the new primary") even when some nodes are slow, unreachable, or have failed -- requiring a MAJORITY (quorum) of nodes to agree before any decision is considered final, which is precisely what prevents the split-brain scenario above: two different nodes can't BOTH simultaneously claim a majority of the same fixed set of voters.
--> **Where this shows up in real database systems** -- etcd and Consul (used to coordinate leader election for many distributed systems, including some database clustering setups) implement Raft directly; distributed SQL databases designed for strong consistency across many nodes (CockroachDB, Google Spanner-style systems) use Raft or Paxos-family protocols internally to agree on transaction ordering across nodes, not just leader election.
--> **Why this connects back to CAP/PACELC** -- a consensus-based HA cluster is making an explicit CP choice (covered in the NoSQL file's CAP Theorem deep dive) -- during a genuine network partition, a minority partition of nodes will REFUSE to elect a new primary (since it can't reach a majority), correctly prioritizing consistency/correctness over that minority partition's availability, rather than risking a split-brain by letting an isolated minority act unilaterally.
