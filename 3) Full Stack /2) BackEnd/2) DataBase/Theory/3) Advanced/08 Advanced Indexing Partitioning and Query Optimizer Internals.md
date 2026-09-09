## Clustered vs Non-Clustered Indexes

--> The Indexing and Performance Tuning file explains the B-Tree structure generically -- this distinction is about HOW the table's actual data is physically arranged on disk relative to an index, and it changes real performance characteristics.

--> **Clustered index** -- the table's actual ROW DATA is physically stored in the same sorted order as the index itself -- there's effectively no separate "table" apart from the index; the index IS the table's physical storage order. A table can have at most ONE clustered index, since data can only be physically sorted one way at a time.
--> **Non-clustered index** -- a SEPARATE structure that stores the indexed column's values in sorted order alongside a POINTER back to where the actual row lives (either a physical row location, or the clustered index's key, depending on the database) -- the table's row data itself stays in its own separate, unsorted (or differently-sorted) storage. A table can have MANY non-clustered indexes.

```text
Clustered index on OrderID:                   Non-clustered index on CustomerName:
  [ OrderID=1, full row data ]                  [ "Adams" --> points to row physically stored elsewhere ]
  [ OrderID=2, full row data ]                  [ "Baker" --> points to row physically stored elsewhere ]
  [ OrderID=3, full row data ]                  [ "Clark" --> points to row physically stored elsewhere ]
  (rows are PHYSICALLY in this order on disk)   (this index is sorted, but the actual rows aren't)
```

--> **In SQL Server** -- the PRIMARY KEY creates a clustered index by default (though this can be changed) -- every other index on the table is non-clustered.
--> **In MySQL/InnoDB** -- the PRIMARY KEY is ALWAYS the clustered index (InnoDB requires one; if no primary key is declared, InnoDB silently creates its own hidden one) -- this is exactly why InnoDB primary key lookups are especially fast, and why choosing a primary key that matches the table's most common access pattern (often an auto-incrementing ID, so new rows append at the end rather than inserting into the middle of the sorted physical order) is a genuine, non-trivial performance decision, not just a formality.
--> **PostgreSQL** -- has no true clustered index concept by default -- every index (including the primary key's) is non-clustered/secondary, though a one-time `CLUSTER` command can physically reorder a table's storage to match a chosen index (this ordering isn't automatically maintained afterward, unlike SQL Server/InnoDB's clustered index).
--> **Why this matters practically** -- a non-clustered index lookup often requires a SECOND step (following the pointer back to the actual row) if the query needs columns not present in the index itself -- this "extra hop" is precisely what a covering index (covered in the Indexing file) avoids, and it's why clustered index lookups (where the "pointer" and the "data" are the same thing) tend to be faster for range scans on the clustering key specifically.

## Other Index Types

--> B-Tree (covered in the Indexing file) is the general-purpose default, but several specialized index types exist for access patterns B-Tree doesn't handle well.

--> **Hash index** -- stores a HASH of the indexed value, giving extremely fast O(1) EQUALITY lookups (`WHERE x = value`) -- but CANNOT support range queries (`<`, `>`, `BETWEEN`) or sorting, since hashing destroys the original value's ordering. PostgreSQL supports `USING HASH` explicitly; many in-memory stores (Redis) are essentially built entirely around this idea.
```sql
CREATE INDEX idx_hash_email ON users USING HASH (email);   -- PostgreSQL -- fast for = only, useless for range queries
```
--> **Bitmap index** -- stores a bitmap (a sequence of 1s/0s) per distinct value, marking which rows have that value -- extremely space-efficient and fast for LOW-cardinality columns (a `status` column with only 3-4 possible values) combined together with `AND`/`OR` across multiple bitmap indexes, which is precisely the opposite sweet spot from a standard B-Tree (covered in the Indexing file's cardinality deep dive). Common in data-warehouse/OLAP systems (Oracle, some columnar databases) rather than typical OLTP workloads, since bitmap indexes are comparatively expensive to update on frequent writes.
--> **GiST / SP-GiST (PostgreSQL)** -- generalized index structures supporting non-standard data types and query types a B-Tree can't express at all -- geometric/spatial "does this point fall inside this polygon" queries, full-text search ranking (alongside GIN, already covered in the LIKE Operator file's full-text search deep dive), and range-overlap queries (`daterange && daterange`, "do these two date ranges overlap"). Reach for these specifically when the query isn't a simple equality/range comparison a B-Tree naturally supports.

## Table Partitioning

--> Partitioning splits ONE logical table into multiple physical pieces, TRANSPARENT to queries (the application still queries what looks like a single table) -- distinct from Sharding (covered in the Replication and Sharding file), which splits data across separate SERVERS. Partitioning happens within a single database instance.

# Range Partitioning

```sql
-- PostgreSQL example -- partition an Orders table by year, so each year's data lives in its own physical partition
CREATE TABLE orders (
    order_id INT,
    order_date DATE,
    amount DECIMAL
) PARTITION BY RANGE (order_date);

CREATE TABLE orders_2025 PARTITION OF orders
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
CREATE TABLE orders_2026 PARTITION OF orders
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
```

# List and Hash Partitioning

--> **List partitioning** -- each partition holds rows matching a specific set of discrete values (e.g. one partition per Region: "US", "EU", "APAC").
--> **Hash partitioning** -- rows are distributed across a fixed number of partitions based on a hash of a column -- used when there's no natural range/list to split on, but even distribution is still wanted.

# Why Partitioning Helps

--> **Partition pruning** -- a query filtering on the partitioning column (`WHERE order_date >= '2026-01-01'`) lets the database skip scanning partitions it can prove don't contain any matching rows entirely, rather than scanning the whole table and then filtering -- a huge win on very large tables.
--> **Easier maintenance** -- dropping an entire partition (e.g. deleting all orders older than 7 years for a retention policy) is a fast, near-instant metadata operation (`DROP TABLE orders_2018`), dramatically cheaper than a `DELETE` scanning and removing millions of individual rows one at a time.
--> **Smaller working sets per index** -- each partition can have its own, smaller indexes, which can be faster to scan and easier to keep in memory than one giant index spanning the entire unpartitioned table.
--> **The real cost** -- queries that DON'T filter on the partitioning column get no pruning benefit and may even perform WORSE than an equivalent unpartitioned table in some cases, and cross-partition queries (`JOIN`s, unique constraints spanning multiple partitions) add real design complexity -- partitioning is generally adopted only once a table has grown large enough (tens of millions of rows and beyond) that these costs are clearly worth it.

## Query Optimizer Internals -- How the Planner Actually Decides

--> The Query Execution Plans file shows HOW to read `EXPLAIN` output -- this covers what's actually happening inside the planner to PRODUCE that plan.

--> **Cost-based optimization** -- the planner doesn't guess; it assigns an estimated numeric COST to each possible way of executing a query (which index to use, which join order, which join algorithm) and picks the plan with the LOWEST estimated total cost. Cost is typically modeled as a combination of estimated disk I/O and CPU work.
--> **Statistics and histograms** -- the planner's cost estimates depend entirely on STATISTICS the database keeps about each table -- row counts, how many DISTINCT values a column has (cardinality, covered in the Indexing file), and often a HISTOGRAM describing the actual distribution of values in a column (are values evenly spread out, or heavily clustered around a few common values?).
```sql
-- Manually refresh a table's statistics -- needed after a large bulk load/delete the planner doesn't yet know about
ANALYZE orders;              -- PostgreSQL
UPDATE STATISTICS orders;    -- SQL Server
ANALYZE TABLE orders;        -- MySQL
```
--> **Selectivity estimation** -- for `WHERE status = 'shipped'`, the planner uses the histogram to estimate roughly WHAT FRACTION of rows will match -- a highly selective filter (matches very few rows) makes using an index cheap and attractive; a poorly selective filter (matches a third of the table) may make a full scan cheaper than the overhead of jumping around via an index -- this is the exact mechanism behind the Indexing file's cardinality discussion of why a low-cardinality column sometimes gets skipped by the planner even when indexed.
--> **Why stale statistics cause bad plans** -- if a table has grown 100x since statistics were last collected, the planner's cost estimates are based on the OLD, much smaller row counts -- it might choose a nested loop join appropriate for a small table against data that's now enormous, producing a genuinely slow plan for reasons that have nothing to do with missing indexes at all. This is exactly the "large gap between estimated and actual row count" red flag the Query Execution Plans file's `EXPLAIN ANALYZE` section flags -- the fix there is refreshing statistics, not necessarily adding a new index.

## VACUUM and Database Maintenance

--> Directly connecting to the MVCC deep dive (Concurrency Control file) -- PostgreSQL's MVCC design means an `UPDATE`/`DELETE` doesn't immediately reclaim the OLD row version's space -- `VACUUM` is the maintenance process that reclaims it.

```sql
VACUUM orders;              -- reclaims dead row-version space, makes it available for reuse
VACUUM ANALYZE orders;      -- also refreshes the table's statistics in the same pass
VACUUM FULL orders;         -- more aggressive -- physically rewrites the table to reclaim space at the OS level,
                             -- but requires an exclusive lock for its duration, unlike a plain VACUUM
```

--> **Table/index bloat** -- a table that's never vacuumed accumulates dead row versions indefinitely ("bloat") -- wasting disk space and, since the database still has to skip PAST all those dead versions during scans, slowing down queries over time even though the actual live row count hasn't grown.
--> Most managed/modern PostgreSQL setups run `autovacuum` automatically in the background -- but a workload with unusually heavy update/delete churn can still outpace autovacuum's default settings, making manual/tuned vacuuming a genuine, recurring operational concern for a busy production database, not just a one-time setup step.

## Connection Pooling

--> Opening a brand-new database connection is genuinely EXPENSIVE -- TCP handshake, authentication, and the database allocating memory/resources for the new session -- all before a single query even runs. A web application handling many short-lived requests, each opening its own fresh connection, pays this cost repeatedly and wastefully.

--> **Connection pooling** maintains a fixed set of ALREADY-OPEN connections, handed out to application code on demand and returned to the pool when done, rather than closed -- reusing the expensive setup work across many requests instead of repeating it every time.

```python
# SQLAlchemy example -- the engine maintains its own connection pool internally
engine = create_engine("postgresql://user:pass@host/db", pool_size=10, max_overflow=5)
# pool_size: how many connections to keep open and ready
# max_overflow: how many EXTRA temporary connections beyond pool_size are allowed under heavy load
```

--> **Pool exhaustion** -- if every pooled connection is currently busy and the pool is already at its max size, the next request must WAIT for one to free up (or fail outright, depending on configuration) -- a genuinely common production issue when a pool is sized too small for actual concurrent load, or when application code forgets to properly RELEASE a connection back to the pool after use (a "connection leak"), gradually starving the pool over time.
--> **External connection poolers** -- tools like PgBouncer (PostgreSQL) sit BETWEEN the application and the database as their own dedicated layer, pooling connections across MULTIPLE application server instances -- useful when the database itself has a hard cap on total connections (common in managed cloud database offerings) that many separate application servers, each with their own internal pool, could otherwise exceed collectively even if each individual pool looks reasonably sized.
