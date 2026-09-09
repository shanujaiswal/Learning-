## Star Schema and Snowflake Schema -- A Worked Example

--> The Database Fundamentals file's OLTP vs OLAP deep dive mentions the star schema by name -- here's an actual worked example of what one looks like, connecting directly to the Normalization file's normalized-schema approach it deliberately departs from.

# The Normalized (OLTP-style) Starting Point

```text
Orders: OrderID, CustomerID, OrderDate
OrderItems: OrderID, ProductID, Quantity, Price
Products: ProductID, ProductName, CategoryID
Categories: CategoryID, CategoryName
Customers: CustomerID, CustomerName, Region
```

--> Answering "total revenue by product category by region by month" against this normalized design requires joining FIVE tables together -- correct, but exactly the kind of multi-join query that gets slow at data-warehouse scale (billions of historical rows), which is precisely the problem a star schema is designed to avoid.

# The Star Schema -- One Fact Table, Several Dimension Tables

```text
                    DimDate
                       |
DimProduct -- FactSales -- DimCustomer
                       |
                  DimCategory

FactSales (the "fact" table -- one row per sale, holding MEASURES/numbers):
    SaleID, DateKey, ProductKey, CustomerKey, CategoryKey, Quantity, Revenue

DimProduct (a "dimension" table -- descriptive attributes, DENORMALIZED, no further joins needed):
    ProductKey, ProductName, CategoryName   -- CategoryName duplicated here directly, no separate join needed

DimCustomer:
    CustomerKey, CustomerName, Region

DimDate:
    DateKey, Day, Month, Quarter, Year
```

--> **Why it's called a "star"** -- one central FACT table (the numbers being measured -- sales, revenue, quantity) surrounded by DIMENSION tables (the descriptive context those numbers are sliced by) -- visually, drawing the fact table in the middle with lines out to each dimension table looks like a star.
--> Now "total revenue by category by region by month" is a join between the ONE fact table and 3 dimension tables directly on simple key lookups -- no chained multi-hop joins through a normalized `Products → Categories` relationship are needed, since `CategoryName` was deliberately DUPLICATED directly into `DimProduct` -- this is the exact denormalization trade-off the Normalization file's Deep Dive describes: redundancy accepted specifically to make large-scale aggregate reads simpler and faster.

# Snowflake Schema -- A Partially Normalized Variant

```text
DimProduct: ProductKey, ProductName, CategoryKey   -- references a separate dimension instead of embedding CategoryName
DimCategory: CategoryKey, CategoryName              -- category info normalized OUT into its own small table
```

--> A snowflake schema normalizes the DIMENSION tables themselves further (here, `DimProduct` no longer duplicates `CategoryName` directly, referencing `DimCategory` instead) -- reducing redundancy in dimension data at the cost of reintroducing an extra join, a middle ground between a pure star schema's aggressive denormalization and a fully normalized OLTP design.
--> **Practical guidance** -- a star schema is generally preferred for typical data-warehouse/BI reporting workloads specifically BECAUSE query simplicity and read speed matter more there than the storage/redundancy cost of duplicated dimension attributes -- a snowflake schema is chosen when a specific dimension is large/frequently-updated enough that its own redundancy becomes a genuine problem worth the extra join cost to avoid.

## CQRS -- Command Query Responsibility Segregation

--> The SQL Views file's Materialized Views deep dive mentions CQRS only as a passing analogy -- here it is as its own architectural pattern.

--> **The core idea** -- separate the model used for WRITES (commands -- "place this order," "update this profile") from the model used for READS (queries -- "show me this user's order history") -- rather than forcing one single data model/schema to efficiently serve both very different access patterns.

```text
Traditional (single model):
  [ Application ] <---> [ One database, one schema, serving both writes AND reads ]

CQRS:
  [ Application ] --writes--> [ Write model / normalized OLTP schema ]
                                        |
                                (events / sync process)
                                        |
                                        v
  [ Application ] <--reads--- [ Read model / denormalized, query-optimized schema ]
```

--> **Why this can genuinely help** -- the WRITE side benefits from a normalized schema (covered in the Normalization file) that keeps individual transactions fast and consistent; the READ side often wants a totally different, DENORMALIZED shape (much like the star schema above, or a set of precomputed materialized views) optimized for exactly the queries the application's UI actually needs, without forcing every read to pay the cost of joining the normalized write-side tables on every single request.
--> **The real cost** -- the read model isn't updated in the SAME instant as the write (whether via a materialized view's periodic refresh, or an event-driven sync process) -- CQRS inherently introduces some degree of eventual consistency between the write and read sides, directly connecting to the Eventual Consistency deep dive in the NoSQL file. This is exactly why CQRS is adopted deliberately for systems where read and write access patterns are genuinely very different and reads vastly outnumber writes (a dashboard, an activity feed), not as a default architectural starting point for every application.

## Time-Series Database Concepts

--> Ordinary relational tables CAN store time-series data (a `sensor_readings` table with a timestamp column) -- but purpose-built time-series databases (InfluxDB, TimescaleDB) add specific features that plain relational storage doesn't offer out of the box.

--> **Downsampling** -- automatically reducing OLDER, less-frequently-needed data to a coarser time resolution -- e.g. keeping every individual reading for the last 24 hours, but automatically rolling anything older than a week into hourly averages, and anything older than a year into daily averages. Saves enormous storage for data where old fine-grained detail is rarely queried, while still preserving long-term trend visibility.
```sql
-- TimescaleDB example -- a continuous aggregate, automatically maintained, roughly analogous to a
-- self-refreshing materialized view scoped specifically to time-bucketed downsampling
CREATE MATERIALIZED VIEW hourly_avg WITH (timescaledb.continuous) AS
SELECT time_bucket('1 hour', time) AS bucket, AVG(temperature) AS avg_temp
FROM sensor_readings
GROUP BY bucket;
```
--> **Retention policies** -- automatically DELETING data older than a defined age, rather than requiring a manually-scheduled cleanup job -- directly connects to the Partitioning deep dive's point about dropping an entire old partition being far cheaper than a row-by-row `DELETE`; time-series databases often implement retention precisely by dropping old time-based partitions/chunks entirely.
```sql
SELECT add_retention_policy('sensor_readings', INTERVAL '90 days');   -- TimescaleDB -- auto-drops data older than 90 days
```
--> **Why a specialized time-series database rather than just a relational table** -- write patterns are typically an unending stream of NEW, append-only, time-ordered inserts (rarely updates to old rows) and queries are overwhelmingly time-range-based ("last 24 hours," "this month vs last month") -- time-series databases optimize their internal storage and indexing specifically around this narrow but extremely common shape, at the cost of being a poor general-purpose fit for arbitrary relational queries unrelated to time.

## Zero-Downtime Migration Patterns -- Expand/Contract

--> The Database Fundamentals file's Migration Tooling deep dive covers HOW migrations are tracked and applied (Alembic, Django migrations, Flyway) -- this covers the specific SEQUENCING discipline needed to apply a migration to a LIVE production database with zero downtime, when the application code and the database schema can't be updated in the exact same instant.

--> **The core problem** -- during a deployment, OLD application code and NEW application code are often briefly running SIMULTANEOUSLY (a rolling deployment across multiple servers) -- a migration that renames a column, for instance, would immediately break the OLD code still running on servers not yet updated, even though the NEW code expects the new name.

# The Expand/Contract Pattern

```text
Goal: rename the "name" column to "full_name" on a live Users table, with zero downtime

Step 1 (EXPAND) -- add the NEW column alongside the old one, don't remove anything yet:
    ALTER TABLE Users ADD COLUMN full_name VARCHAR(255);
    -- Backfill: UPDATE Users SET full_name = name;  (existing rows get the new column populated)
    -- Deploy application code that WRITES to BOTH columns, but still READS from the old "name" column

Step 2 (MIGRATE) -- deploy application code that reads from the NEW "full_name" column instead,
    while still writing to both (so a rollback to the previous app version would still see correct data)

Step 3 (CONTRACT) -- once every server is confirmed running the new code and nothing reads/writes
    the old column anymore, drop it:
    ALTER TABLE Users DROP COLUMN name;
```

--> **Why each step matters** -- at every single point in this sequence, BOTH the old and new application code versions (which may briefly coexist during a rolling deploy) can successfully read and write the table without erroring -- the schema is never in a state that only ONE version of the code understands. Skipping straight to a single-step rename would work fine in a maintenance-window deployment (old code fully stopped, then migration runs, then new code starts) but breaks the zero-downtime requirement for a rolling deployment where old and new code overlap in time.
--> This same expand/then-contract discipline applies broadly beyond renames -- changing a column's data type, splitting one table into two, or changing a NOT NULL constraint on an existing column all follow the same shape: add the new thing, migrate usage over gradually while both old and new remain valid, then remove the old thing only once nothing depends on it anymore.

## Caching Strategies

--> The Indexing file mentions Redis-based query caching only as a one-line bullet -- here's the actual strategy space that decision opens up.

# Cache-Aside (Lazy Loading) -- The Most Common Pattern

```python
def get_user(user_id):
    cached = redis.get(f"user:{user_id}")
    if cached:
        return cached                      # cache hit -- skip the database entirely
    user = db.query("SELECT * FROM users WHERE id = %s", user_id)
    redis.set(f"user:{user_id}", user, ex=300)   # cache miss -- store it for next time, expire after 5 minutes
    return user
```

--> The application checks the cache FIRST, only falling back to the database on a miss, then populates the cache for next time -- simple, and the cache only ever holds data that's actually been requested (no wasted space on data nobody reads).

# Write-Through -- Keeping the Cache Always Current

--> Every WRITE updates the cache AND the database together, in the same operation, rather than only updating the database and letting the cache go stale until it separately expires.
```python
def update_user(user_id, data):
    db.execute("UPDATE users SET ... WHERE id = %s", user_id)
    redis.set(f"user:{user_id}", data)   # cache updated immediately, in lockstep with the database write
```
--> Reads are always fresh (no risk of serving stale cached data), at the cost of every write now being slightly slower (it has to update two systems, not just one).

# Write-Behind (Write-Back) -- Deferring the Database Write

--> The write goes to the CACHE immediately (fast), and the actual database write happens ASYNCHRONOUSLY afterward, in the background, rather than the caller waiting for both to complete.
--> Faster writes from the caller's perspective, but introduces real risk -- if the cache/process crashes before the deferred database write actually happens, that write is LOST entirely, since it was never durably persisted anywhere else first. Generally reserved for data where losing a very recent write occasionally is an acceptable trade-off for write speed (a view counter, not a financial transaction).

# Cache Invalidation -- "The Hard Problem"

--> **Time-based expiration (TTL)** -- the simplest approach (shown in the cache-aside example's `ex=300`) -- the cached value automatically expires after a fixed duration, accepting some staleness for that window in exchange for needing no explicit invalidation logic at all.
--> **Explicit invalidation on write** -- when data changes, actively DELETE (or update) its cache entry immediately, rather than waiting for a TTL to expire -- more precise, but requires every code path that modifies the underlying data to remember to also invalidate the correct cache key(s), a genuinely common source of subtle bugs (a forgotten invalidation path leaves stale data cached indefinitely until its TTL eventually expires, if one is even set).
```python
def update_user(user_id, data):
    db.execute("UPDATE users SET ... WHERE id = %s", user_id)
    redis.delete(f"user:{user_id}")   # invalidate rather than update -- next read will re-fetch fresh data from the DB
```
--> **Why this is considered "one of the two hard problems in computer science"** (alongside naming things) -- as a system grows, the same underlying data often ends up cached under MULTIPLE different keys/shapes for different access patterns (a user cached by ID, and separately as part of a cached "team members" list) -- a single update needs to correctly invalidate EVERY one of those cached representations, and missing even one leaves genuinely inconsistent, stale data visible somewhere in the system with no error or warning that anything is wrong.
