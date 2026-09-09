## PostgreSQL

--> Every prior file has treated PostgreSQL as "the" reference relational database for MVCC, VACUUM, partitioning syntax, and JSON support -- this section covers the features that are genuinely PostgreSQL-specific, with no real equivalent in MySQL or SQL Server.

# Table Inheritance

--> A PostgreSQL table can INHERIT from a parent table, automatically gaining all of the parent's columns, and a query against the parent automatically includes matching rows from every child table too -- a mechanism that predates, and partially overlaps with, PostgreSQL's own declarative partitioning (covered in the Advanced Indexing, Partitioning and Query Optimizer Internals file).

```sql
CREATE TABLE vehicles (
    id SERIAL PRIMARY KEY,
    manufacturer TEXT
);

CREATE TABLE cars (
    doors INT
) INHERITS (vehicles);   -- "cars" automatically has id, manufacturer, AND doors

SELECT * FROM vehicles;  -- by default, returns rows from vehicles AND every table that inherits from it
```

--> **Why declarative partitioning has mostly replaced this** -- inheritance-based "partitioning" (the old pre-PostgreSQL-10 way of manually building partitioned tables) required hand-written triggers to route inserts to the correct child table and hand-written `CHECK` constraints for partition pruning to work at all -- declarative `PARTITION BY` handles both automatically. Plain table inheritance (without partitioning intent) still shows up for genuine "is-a" data modeling (a `cars` and `trucks` table both being a kind of `vehicle`), though most schemas reach for a shared foreign key relationship instead, since inheritance interacts awkwardly with unique constraints and foreign keys spanning parent and child.

# LISTEN / NOTIFY -- Built-In Pub-Sub

--> PostgreSQL has a lightweight publish-subscribe mechanism built directly into the database -- one session can `NOTIFY` a named channel with an optional payload, and any other session that has `LISTEN`-ed on that channel receives it asynchronously, with no polling required.

```sql
-- Session A:
LISTEN order_updates;

-- Session B, elsewhere, after inserting a new order:
NOTIFY order_updates, '{"order_id": 42, "status": "shipped"}';

-- Session A's client driver receives the notification asynchronously, without re-querying
```

--> **What it's good for and what it isn't** -- this is a genuinely useful way to push "something changed, go re-check" signals to application servers without a separate message broker, commonly wired up via a trigger (covered in the SQL Triggers file) that fires `NOTIFY` on insert/update. It is NOT a durable queue -- a notification sent while no one is listening is simply lost, with no persistence or replay, which is exactly why it's suited to cache-invalidation-style signals rather than the guaranteed-delivery job queues that Change Data Capture (covered in the Database Testing, Multi-Tenancy, and Modern Data Patterns file) or a real message broker are built for.

# Native Arrays

--> Unlike MySQL or SQL Server, a PostgreSQL column can be declared as an ARRAY of any base type directly, without needing a separate join table for what would otherwise be a one-to-many relationship.

```sql
CREATE TABLE posts (
    id SERIAL PRIMARY KEY,
    tags TEXT[]
);

INSERT INTO posts (tags) VALUES ('{"postgres", "database", "sql"}');

SELECT * FROM posts WHERE 'database' = ANY(tags);       -- contains a specific tag
SELECT * FROM posts WHERE tags && ARRAY['sql', 'nosql']; -- overlaps with any of these tags
```

--> **Why this is usually a convenience, not a normalization escape hatch** -- storing tags as an array avoids a join for small, denormalization-tolerant lists, but it sacrifices the referential integrity, easy aggregation, and indexing flexibility a proper join table (covered in the Normalization and Database Design file) gives -- arrays are a reasonable fit for genuinely simple, unstructured lists attached to a row, not a substitute for a real many-to-many relationship that needs its own queries, constraints, or additional attributes on the relationship itself.

# Custom Composite Types, DOMAIN, and ENUM

--> PostgreSQL lets you define reusable custom types beyond the built-in scalar ones.

```sql
-- ENUM: a fixed, named set of allowed string values, stored efficiently and sortable in declared order
CREATE TYPE order_status AS ENUM ('pending', 'shipped', 'delivered', 'cancelled');
CREATE TABLE orders (id SERIAL PRIMARY KEY, status order_status DEFAULT 'pending');

-- DOMAIN: a base type plus a reusable constraint, so the validation rule lives in one place
CREATE DOMAIN positive_int AS INT CHECK (VALUE > 0);
CREATE TABLE inventory (id SERIAL PRIMARY KEY, quantity positive_int);

-- Composite type: a named, structured bundle of fields, usable as a single column's type
CREATE TYPE address AS (street TEXT, city TEXT, zip TEXT);
CREATE TABLE customers (id SERIAL PRIMARY KEY, home_address address);
```

--> **Why reach for these over a plain `CHECK` constraint or `VARCHAR`** -- an `ENUM` is more space-efficient than storing repeated strings and self-documents the allowed values directly in the schema; a `DOMAIN` centralizes a validation rule so every column that needs it (`positive_int` might be reused across a dozen tables) enforces it identically instead of the rule being copy-pasted as a `CHECK` clause on each table separately. The cost is real too -- adding a new `ENUM` value is a schema migration, and application code that hardcodes the old set of values needs updating right alongside it, which is why some teams deliberately prefer a plain lookup table with a foreign key instead, trading `ENUM`'s efficiency for the flexibility of adding a new status value with a simple `INSERT` rather than a schema change.

# Logical vs Physical Replication

--> The Database Replication and Sharding file covers replication generally -- PostgreSQL specifically distinguishes two different mechanisms with genuinely different capabilities.

```text
Physical (streaming) replication:
  Ships raw WAL (covered in the Storage Engine Internals file) byte-for-byte to a replica
  Replica is an exact, whole-cluster byte-level copy -- cannot replicate just one table
  Replica must run the SAME major PostgreSQL version

Logical replication:
  Decodes WAL into logical row-level CHANGES (this INSERT, that UPDATE) and ships those instead
  Can replicate a SUBSET of tables, and even into a DIFFERENT PostgreSQL major version
  Enables selective replication, and is the actual mechanism many CDC (Change Data Capture,
  covered in the Database Testing, Multi-Tenancy, and Modern Data Patterns file) tools plug into
```

--> **Why the distinction matters practically** -- physical replication is what you reach for to build a simple, whole-database read replica or standby for failover; logical replication is what you reach for when you need something more selective -- replicating only certain tables, replicating into a different schema/version for a migration, or streaming changes out to an external system like Kafka via a tool built on PostgreSQL's logical decoding.

# pg_dump and pg_stat_activity

--> `pg_dump` is PostgreSQL's standard logical backup tool -- it produces a portable SQL (or custom-format) representation of a database's schema and data, usable for backups, migrations, or seeding a new environment, as distinct from a physical file-system-level backup that only works against the exact same PostgreSQL version and platform.

```bash
pg_dump -Fc mydb > mydb_backup.dump     # custom format -- compressed, supports selective/parallel restore
pg_restore -d mydb_new mydb_backup.dump
```

--> `pg_stat_activity` is a system view showing every currently active connection/session in real time -- essential for diagnosing a database that's suddenly slow or unresponsive.

```sql
-- Find long-running queries and what they're blocked on right now
SELECT pid, state, query, now() - query_start AS duration
FROM pg_stat_activity
WHERE state != 'idle'
ORDER BY duration DESC;

SELECT pg_terminate_backend(12345);   -- forcibly kill a runaway session by its process ID
```

--> This is the practical, real-time counterpart to `EXPLAIN` (covered in the Query Execution Plans file) -- `EXPLAIN` tells you how one query WOULD run; `pg_stat_activity` tells you what's ACTUALLY running, right now, across the whole database, which is where an operator looks first when something is wrong in production rather than reproducing a single query in isolation.

# Foreign Data Wrappers

--> A Foreign Data Wrapper (FDW) lets PostgreSQL query an EXTERNAL data source (another PostgreSQL database, MySQL, a plain CSV file, even a REST API) as if it were a local table, transparently pushing filters down to the remote source where possible.

```sql
CREATE EXTENSION postgres_fdw;
CREATE SERVER remote_db FOREIGN DATA WRAPPER postgres_fdw OPTIONS (host 'other-host', dbname 'sales');
CREATE FOREIGN TABLE remote_orders (id INT, amount NUMERIC)
    SERVER remote_db OPTIONS (table_name 'orders');

SELECT * FROM remote_orders WHERE amount > 1000;   -- looks local, actually queries the remote database
```

--> **What this is genuinely good for** -- federated queries across systems without a full ETL pipeline, and incremental migrations where a table needs to keep working for existing queries while its actual data slowly moves to a new database. It is not a substitute for proper CDC or a data warehouse's ETL/ELT pipeline (covered in the Data Warehousing, CQRS and Caching Strategies file) at any real scale -- FDW query performance depends entirely on how well the remote source can execute the pushed-down filter, and can be genuinely slow for anything beyond simple, selective queries.

# Extensions -- PostGIS and pgvector

--> PostgreSQL's extension system lets it add entirely new data types, operators, and index support without touching the core engine -- two extensions are worth knowing by name specifically because of how widely they're used.

--> **PostGIS** adds full geospatial data types and operations -- points, polygons, spatial indexes (built on the GiST index type covered in the Advanced Indexing, Partitioning and Query Optimizer Internals file), and queries like "find every store within 5 km of this point" -- turning PostgreSQL into a genuinely capable GIS database without a separate specialized system.
--> **pgvector** adds a `VECTOR` column type and nearest-neighbor search operators, letting PostgreSQL store embeddings (the numeric representations produced by machine learning models) and run similarity search directly in SQL -- the reason it matters now is that it lets a team building a retrieval/semantic-search or RAG feature keep vector search in the SAME database as their normal relational data, rather than standing up a dedicated separate vector database purely for that one feature.

```sql
CREATE EXTENSION vector;
CREATE TABLE documents (id SERIAL PRIMARY KEY, content TEXT, embedding VECTOR(1536));

-- Find the 5 most similar documents to a given embedding, by cosine distance
SELECT id, content FROM documents ORDER BY embedding <=> '[0.012, -0.045, ...]' LIMIT 5;
```

--> **Why this is worth calling out specifically** -- both extensions are the concrete answer to "can PostgreSQL do X" for two very different X's (geospatial, and vector similarity search) that would otherwise seem to require an entirely separate specialized database -- extensions are precisely the mechanism that lets PostgreSQL absorb these workloads instead.

## MySQL

--> MySQL's most important vendor-specific quirks are less about added capabilities and more about historical decisions and defaults that are still worth knowing, several of which have caused genuine production incidents industry-wide.

# Statement-Based vs Row-Based vs Mixed Binlog Replication

--> MySQL's replication (the mechanism underlying the Database Replication and Sharding file's coverage) records changes into a "binlog" that replicas consume -- HOW that binlog records a change comes in three formats, each with a real trade-off.

```text
Statement-based (SBR):  logs the literal SQL statement executed (e.g. "UPDATE orders SET x = NOW()")
  Risk: NON-DETERMINISTIC statements (NOW(), RAND(), UUID()) can produce a DIFFERENT result on the
        replica than on the primary if replayed at a different moment -- a genuine correctness bug

Row-based (RBR):        logs the actual ROWS changed and their new values, not the statement that caused it
  Safer -- replica gets the EXACT same data regardless of when/how the statement is replayed
  Cost: can produce a much larger binlog for a statement that changes many rows at once

Mixed:                  MySQL automatically uses statement-based normally, switching to row-based
                         specifically for statements it detects as non-deterministic
```

--> **Why this still matters** -- row-based replication is the safer default for most modern setups (and is what many managed MySQL offerings default to), but statement-based can still be encountered in older configurations, and understanding WHY a `NOW()`-based update could silently diverge between primary and replica is the kind of subtle correctness issue that's easy to miss until it actually causes a data mismatch in production.

# The utf8 vs utf8mb4 Trap

--> This is one of the most notorious historical footguns in MySQL specifically -- MySQL's character set literally named `utf8` is NOT full UTF-8 -- it only supports up to 3 bytes per character, which silently EXCLUDES large swaths of valid Unicode, most infamously emoji and many CJK (Chinese/Japanese/Korean) characters, which require 4 bytes.

```sql
-- The trap: this looks like proper UTF-8 support, but isn't
CREATE TABLE messages (id INT, body VARCHAR(255)) CHARACTER SET utf8;

INSERT INTO messages (body) VALUES ('Hello 😀');   -- can fail outright, or silently truncate/corrupt
                                                     -- the emoji, depending on MySQL version/mode

-- The actual fix -- utf8mb4 is MySQL's name for genuinely complete UTF-8 (up to 4 bytes/char)
CREATE TABLE messages (id INT, body VARCHAR(255)) CHARACTER SET utf8mb4;
```

--> **Why the name is so misleading** -- `utf8` was defined early in MySQL's history before the need for 4-byte UTF-8 characters was fully appreciated, and by the time `utf8mb4` was added as the genuinely correct option, `utf8` couldn't be silently redefined without breaking existing installations -- so the confusingly-named, incomplete `utf8` stuck around as MySQL's default for a very long time (only changing to `utf8mb4` as the true default starting in MySQL 8.0). Any MySQL schema still using plain `utf8` for user-facing text should be treated as a latent data-corruption bug waiting for the first emoji or certain CJK input, not a stylistic choice.

# Implicit Type Coercion Oddities

--> MySQL has historically been unusually permissive about silently coercing values between types in a comparison, in ways that can produce genuinely surprising results rather than an error.

```sql
SELECT 'abc' = 0;         -- historically TRUE in many MySQL versions/modes --
                           -- the string is coerced to a number, and 'abc' coerces to 0

SELECT * FROM users WHERE phone_number = 123456789;
-- if phone_number is stored as VARCHAR, this comparison coerces the column, and depending on
-- version/mode may not use an index efficiently, or may match rows a strict comparison wouldn't
```

--> **Why this has gotten stricter over time, but not disappeared** -- MySQL 8.0's default SQL mode is considerably stricter than older defaults (rejecting some of the most dangerous silent truncations/coercions outright), but comparisons across mismatched types remain something to actively watch for in MySQL specifically, more so than in PostgreSQL, which is comparatively strict about type mismatches by default and simply errors instead of guessing.

# The Enduring Lack of Native FULL OUTER JOIN

--> Every major relational database except MySQL supports `FULL OUTER JOIN` (covered generally in the SQL Joins file) directly -- MySQL still does not, and the standard workaround is a `UNION` of a `LEFT JOIN` and a `RIGHT JOIN`.

```sql
-- What you'd write elsewhere:
SELECT * FROM orders o FULL OUTER JOIN customers c ON o.customer_id = c.id;

-- The MySQL workaround -- union of both one-sided outer joins, deduplicated
SELECT * FROM orders o LEFT JOIN customers c ON o.customer_id = c.id
UNION
SELECT * FROM orders o RIGHT JOIN customers c ON o.customer_id = c.id;
```

--> This has been a known, unaddressed gap for MySQL's entire history -- worth knowing by name specifically so a `FULL OUTER JOIN` failing with a syntax error in MySQL isn't mistaken for a typo, and so the `UNION`-based workaround is recognized as the accepted, standard pattern rather than an unusual hack.

## SQL Server

--> SQL Server's standout vendor-specific features lean toward built-in temporal/analytical capability and high-availability tooling that would otherwise require bolting on separate systems.

# Temporal Tables -- Automatic History Tracking

--> A SQL Server "system-versioned temporal table" automatically keeps a full history of every change to every row, without any application code or trigger having to maintain it manually -- SQL Server itself moves the PREVIOUS version of a row into a linked history table every time an `UPDATE` or `DELETE` happens.

```sql
CREATE TABLE employees (
    id INT PRIMARY KEY,
    salary DECIMAL,
    valid_from DATETIME2 GENERATED ALWAYS AS ROW START,
    valid_to DATETIME2 GENERATED ALWAYS AS ROW END,
    PERIOD FOR SYSTEM_TIME (valid_from, valid_to)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = dbo.employees_history));

UPDATE employees SET salary = 90000 WHERE id = 1;
-- The OLD row automatically moves into employees_history -- no trigger, no application code needed

-- Query the table AS IT EXISTED at a specific past moment
SELECT * FROM employees FOR SYSTEM_TIME AS OF '2026-01-01' WHERE id = 1;
```

--> **Why this beats a hand-rolled audit trigger** -- the pattern of manually writing a trigger (covered in the SQL Triggers file) that copies the old row into a separate history table on every `UPDATE` is common across every database, but it's easy to get subtly wrong (forgetting `DELETE`, forgetting a column added later) -- temporal tables make this a first-class, engine-guaranteed feature, and the `FOR SYSTEM_TIME AS OF` query syntax is a genuinely elegant way to ask "what did this data look like at a specific point in the past" that a hand-rolled audit table doesn't give you for free.
--> This is also a practical, ready-made building block for bitemporal/temporal modeling questions that otherwise require deliberate schema design -- directly connects to the brief 6NF/temporal-modeling coda in the Database Testing, Multi-Tenancy, and Modern Data Patterns file.

# Columnstore Indexes

--> A standard index (covered in the Indexing and Performance Tuning file) is ROW-oriented -- it stores data organized by row. A **columnstore index** instead stores each COLUMN's values together, contiguously -- a structural shift that dramatically favors analytical (OLAP-style, covered in the Database Fundamentals file's OLTP/OLAP distinction) queries that aggregate over a few columns across millions of rows.

```sql
CREATE COLUMNSTORE INDEX cs_idx ON sales_fact (product_id, quantity, sale_amount);

-- A query summing sale_amount across millions of rows only needs to read the sale_amount
-- column's own contiguous storage -- not every other column in the row it doesn't care about
SELECT SUM(sale_amount) FROM sales_fact WHERE product_id = 42;
```

--> **Why column orientation helps here specifically** -- an aggregation query touching only 2-3 of a table's 50 columns, over millions of rows, wastes enormous I/O reading full ROWS (all 50 columns) off disk under normal row-oriented storage just to use 3 of them -- columnstore storage lets it read only the columns actually needed, and the resulting columns of largely-similar values also compress far better than a row-oriented layout does. This is precisely the structural idea underlying dedicated columnar data warehouse engines (covered in the Data Warehousing, CQRS and Caching Strategies file) -- SQL Server just offers it as an index type you can add to an otherwise normal row-oriented table, rather than requiring a wholly separate warehouse system.

# Always On Availability Groups

--> SQL Server's flagship high-availability feature -- a group of databases that fail over together as a single unit across multiple SQL Server instances, with one PRIMARY replica accepting reads/writes and up to several SECONDARY replicas kept in sync (synchronously or asynchronously, a direct trade-off with the replication lag concepts in the Database Replication and Sharding file) for failover and read-scaling.

```text
Availability Group "OrdersAG":
  Primary replica    (Server A) -- accepts all writes, and reads unless redirected
  Secondary replica  (Server B) -- synchronous commit -- kept perfectly in sync, ready for instant failover
  Secondary replica  (Server C) -- asynchronous commit -- may lag slightly, used for reporting/read-offload

If Server A fails --> automatic failover promotes Server B (synchronous, guaranteed up to date) to primary
```

--> **The synchronous/asynchronous choice per replica is the same fundamental trade-off as the PACELC "Else" case** (covered in the Distributed Consistency Models and NoSQL Internals file) -- a synchronous secondary guarantees zero data loss on failover but adds latency to every write waiting for its acknowledgment; an asynchronous secondary keeps writes fast but risks losing the last few unacknowledged transactions if the primary fails before they replicate. Always On lets an operator make this choice per-replica rather than being forced into one setting for the whole cluster, mirroring the same tunable dial quorum-based systems expose (covered in the same Distributed Consistency file's quorum section), just expressed through SQL Server's own configuration model instead of `N`/`W`/`R` parameters.
