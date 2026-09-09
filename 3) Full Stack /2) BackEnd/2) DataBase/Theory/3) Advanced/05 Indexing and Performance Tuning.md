## Why Indexes Matter

--> Without an index, finding rows matching a WHERE condition requires a full table scan -- the database checks every single row -- fine for small tables, very slow at scale.
--> An index is a separate data structure (commonly a B-Tree) that stores column values in sorted order alongside pointers to the actual rows -- lets the database jump almost directly to matching rows instead of scanning everything.

```sql
CREATE INDEX idx_users_email ON users(email);
-- Now: SELECT * FROM users WHERE email = 'alice@example.com' can use the index instead of scanning the whole table
```

# How a B-Tree Index Works (Conceptually)

--> Values are stored in a sorted tree structure -- looking up a value means comparing against a small number of nodes (logarithmic time) instead of checking every row (linear time).
--> This is why indexes speed up equality lookups (=) AND range queries (<, >, BETWEEN) AND sorting (ORDER BY on the indexed column) -- all benefit from the data already being in sorted order.

# What to Index

--> Columns frequently used in WHERE clauses.
--> Columns used in JOIN conditions (foreign key columns almost always deserve an index).
--> Columns used in ORDER BY, if the sort is on large result sets.
--> Primary keys are indexed automatically by virtually every database.

# The Cost of Indexes

--> Indexes are NOT free -- every INSERT/UPDATE/DELETE must also update every index on that table, so more indexes mean slower writes.
--> Indexes take up disk space, sometimes significant for large tables with many indexes.
--> Rule of thumb -- index columns that are read/filtered often relative to how often the table is written to; don't index every column "just in case".

# Composite Indexes

--> An index can span multiple columns -- CREATE INDEX idx ON orders(customer_id, order_date) -- useful when queries commonly filter on both together.
--> Column ORDER matters -- a composite index on (a, b) can efficiently satisfy queries filtering on "a" alone or on "a AND b", but generally NOT on "b" alone -- the leftmost column(s) must be used first.

```sql
-- This index helps:
SELECT * FROM orders WHERE customer_id = 5;
SELECT * FROM orders WHERE customer_id = 5 AND order_date > '2025-01-01';

-- This one likely does NOT use the index efficiently (skips the leftmost column):
SELECT * FROM orders WHERE order_date > '2025-01-01';
```

# EXPLAIN -- Reading a Query Plan

--> EXPLAIN (or EXPLAIN ANALYZE in PostgreSQL) shows how the database intends to execute a query -- whether it's using an index scan or falling back to a full table/sequential scan.

```sql
EXPLAIN SELECT * FROM users WHERE email = 'alice@example.com';
-- Look for "Index Scan" (good, using the index) vs "Seq Scan" / "Full Table Scan" (bad on a large table)
```

--> A missing or unused index often shows up as a full scan on a large table in the query plan -- the first thing to check when a query is unexpectedly slow.
--> An index can also be silently unused if the query applies a function to the column (WHERE LOWER(email) = ...) without a matching functional index, or if the column's data type doesn't match what's being compared.

# Other Performance Tuning Basics

--> SELECT only the columns you need -- avoid SELECT * on wide tables when only a few columns are actually used.
--> LIMIT/pagination for large result sets instead of fetching everything and filtering in application code.
--> Query caching (application-level, e.g. Redis) for expensive, frequently-repeated read queries that don't need to be perfectly real-time.
--> Connection pooling -- reusing database connections instead of opening/closing one per request, since establishing a new connection has real overhead.
--> Analyze slow query logs periodically -- most databases can log queries exceeding a time threshold, which is the most direct way to find real (not hypothetical) bottlenecks.

## Deep Dive -- Covering Indexes

--> A "covering index" contains EVERY column a query needs -- both the columns being filtered/joined on AND the columns being selected -- letting the database answer the entire query using ONLY the index, never touching the actual table data at all (called an "index-only scan").

```sql
CREATE INDEX idx_covering ON orders(customer_id, order_date, status);

-- This query can be answered ENTIRELY from the index above, without reading the orders table itself,
-- since customer_id (filter), order_date (filter), and status (selected column) are all present in it
SELECT status FROM orders WHERE customer_id = 5 AND order_date > '2026-01-01';
```

--> This directly connects to why `SELECT *` (flagged as an anti-pattern in the SQL Introduction file) specifically defeats this optimization -- selecting every column makes it far less likely the index contains everything needed, forcing the database back to reading the full table row regardless of how well-designed the index otherwise is.

## Deep Dive -- Index Cardinality -- Not Every Column Benefits Equally

--> "Cardinality" refers to how many DISTINCT values a column has relative to the table's total row count. An index is most valuable on HIGH-cardinality columns (email, a unique ID) where it can narrow a search down to very few matching rows quickly.
--> A LOW-cardinality column (a `status` column with only 3 possible values: "pending"/"shipped"/"delivered") gains far less benefit from a standalone index -- if a third of all rows match any given value, the database may decide a full table scan is actually FASTER than using the index (jumping around via an index has its own overhead, and isn't automatically better than a straightforward sequential scan when a huge fraction of the table matches anyway). The query planner (visible via `EXPLAIN`) makes this call automatically based on stored table statistics, which is exactly why a seemingly "obviously indexed" column sometimes shows a full scan in the execution plan regardless.
--> Composite indexes (covered above) often solve this -- combining a low-cardinality column with a high-cardinality one (`(status, customer_id)`) can still be highly selective and useful, even though `status` alone wouldn't be.

## Deep Dive -- N+1 Query Problem

--> Directly connecting to the GraphQL file's DataLoader discussion -- a extremely common, ORM-driven performance bug where fetching a list of N parent records, then separately querying for each one's related child records in a loop, results in 1 + N total queries instead of a single efficient join.

```python
# The N+1 problem -- 1 query for orders, then N additional queries, one per order, for its customer
orders = Order.objects.all()
for order in orders:
    print(order.customer.name)   # Each access triggers a SEPARATE query if not pre-fetched

# The fix -- eager loading, fetches everything in a constant, small number of queries (often just 1-2)
orders = Order.objects.select_related("customer").all()   # Django ORM example -- uses a SQL JOIN internally
```

--> This is one of the single most common real-world database performance issues found in production applications -- it's invisible in development with small test datasets (N+1 queries against 5 rows is imperceptibly fast) and only becomes a genuine, visible problem once the table grows to thousands of rows, making it a classic case where a code review or a query-count-monitoring tool catches something manual testing alone would miss entirely.
