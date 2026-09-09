## Database Testing Practices

--> Every file so far has covered the database itself -- this section covers how disciplined teams actually TEST code that depends on one, which brings its own distinct set of problems (test isolation, realistic data, migration safety) that don't show up in ordinary application-code testing.

# Testcontainers and Ephemeral Databases

--> Testing against a shared, long-lived "test database" is a common early mistake -- tests leave behind data, run in unpredictable order, and interfere with each other when run in parallel. **Testcontainers** (and similar tooling) solves this by spinning up a real, throwaway database instance (in an actual Docker container) fresh for a test run, and destroying it afterward.

```python
# Python example, using the testcontainers library
from testcontainers.postgres import PostgresContainer

def test_user_repository():
    with PostgresContainer("postgres:16") as postgres:
        engine = create_engine(postgres.get_connection_url())
        run_migrations(engine)          # apply the real schema migrations against this fresh instance
        # ... test actual queries against a REAL PostgreSQL, not a mock ...
    # container is destroyed automatically here -- next test run starts from a clean slate
```

--> **Why a real database beats mocking the database entirely** -- mocking out the database layer means tests never actually exercise real SQL, real constraint enforcement, or real transaction behavior -- a mocked test can pass while the actual query has a syntax error, violates a foreign key, or behaves differently under MVCC (covered in the MVCC, Locking and Distributed Transactions file) than the mock assumed. Testcontainers gives up some speed (starting a real database instance per test run) in exchange for tests that actually catch database-shaped bugs, which is generally judged the right trade for anything beyond the fastest unit-level tests of pure application logic.
--> **The trade-off against speed** -- spinning up a full container per test is slower than an in-memory fake, which is why many teams share ONE container across an entire test suite run (rather than one per test) and rely on the transaction-rollback pattern below for per-test isolation within that single shared instance.

# Test Data Fixtures

--> A **fixture** is a known, deliberately-constructed set of data loaded before a test runs, so the test has a predictable starting state to assert against rather than depending on whatever happens to already be in the database.

```python
@pytest.fixture
def sample_customers(db_session):
    customers = [
        Customer(name="Alice", tier="gold"),
        Customer(name="Bob", tier="silver"),
    ]
    db_session.add_all(customers)
    db_session.commit()
    return customers

def test_gold_tier_discount(sample_customers, db_session):
    alice = sample_customers[0]
    assert calculate_discount(alice) == 0.20
```

--> **Why fixtures matter beyond just convenience** -- a test that depends on "whatever's already in the database" is fragile and order-dependent (it can pass in isolation but fail when run after a different test that changed the same rows) -- fixtures make every test's starting state explicit and reproducible, which is exactly what lets tests run safely in parallel or in any order.

# Wrapping Tests in a Rolled-Back Transaction

--> The single most effective technique for fast per-test database isolation is wrapping EACH test in its own transaction that's rolled back at the end, regardless of whether the test passed -- the database's own rollback mechanism (covered in the Transactions and ACID file) does all the cleanup work, instead of writing manual teardown code to delete every row a test created.

```python
@pytest.fixture(autouse=True)
def rollback_after_test(db_connection):
    transaction = db_connection.begin()
    yield
    transaction.rollback()   # every INSERT/UPDATE the test made vanishes -- the database is exactly
                              # as it was before the test ran, with zero manual cleanup code
```

--> **Why this is both faster and more reliable than manual cleanup** -- manual teardown (deleting rows a test created) is easy to get subtly wrong -- a test that fails partway through might skip its own cleanup step entirely, leaving polluted state for the next test. A transaction rollback undoes EVERYTHING unconditionally, succeeds or not, and is typically much faster than actually re-running `DELETE` statements since rollback just discards uncommitted changes rather than executing more SQL.
--> **The one thing this pattern can't test** -- code that deliberately calls `COMMIT` mid-test (or that spans multiple real connections, each seeing the other's uncommitted changes differently) doesn't compose cleanly with wrapping the whole test in one outer transaction -- for that narrower category of test, a full Testcontainers-style fresh instance (or explicit setup/teardown) is still the right tool.

# Migration Testing

--> A schema migration (adding a column, changing a type, backfilling data) is itself code that can have bugs -- and unlike most application code, a broken migration run against production can be genuinely difficult to reverse cleanly once it's partially applied.

--> **Test migrations against a realistic copy of production data, not just an empty schema** -- a migration that works fine against a freshly-created, empty test database can fail outright (or silently do the wrong thing) against a table with millions of real rows, unexpected `NULL`s, or values that violate a new constraint the migration is trying to add.
--> **Test the rollback/down migration too, not just the forward one** -- a migration framework's "down" step is written far less carefully in practice than the "up" step, since it's exercised far less often -- but it's precisely the step that matters most during an actual production incident where a bad migration needs to be reversed under pressure.
--> **Run migrations in CI against a Testcontainers-provisioned database seeded with realistic sample data**, as a required step before a migration is allowed to merge -- catching a migration that would fail (or run for an unacceptably long time) against real-shaped data long before it's ever run against the real production database.

## ORMs Beyond Python

--> The ORM SQLAlchemy and Django ORM file covers Python's two dominant ORMs in depth -- every major backend ecosystem has its own equivalent, and while they all solve the same core problem (mapping objects/classes to relational rows), their specific design choices differ in ways worth knowing by name.

```text
Prisma (Node.js/TypeScript):
  Schema-first -- you define models in a dedicated .prisma schema file, and Prisma GENERATES a
  fully-typed client from it. Migrations are generated and tracked from schema file diffs.
  Known for excellent TypeScript type inference -- query results are fully typed with no manual casting.

TypeORM (Node.js/TypeScript):
  Code-first, decorator-based -- models are TypeScript classes with @Entity/@Column decorators,
  closer in spirit to Django's model-class approach than Prisma's separate schema file.
  Supports both an ActiveRecord-style and a Data Mapper/Repository-style usage pattern side by side.

Hibernate / JPA (Java):
  The original mainstream ORM that popularized much of what "ORM" means today -- JPA (Java
  Persistence API) is the SPECIFICATION; Hibernate is its most widely-used IMPLEMENTATION.
  Famous for its session/entity lifecycle model and for exactly the N+1 query problem (covered in
  the ORM SQLAlchemy and Django ORM file) that later ORMs in other ecosystems inherited the same risk of.

Entity Framework (.NET):
  Microsoft's ORM for C#/.NET -- code-first (classes with attributes/fluent configuration) or
  database-first (generating classes FROM an existing schema). LINQ integration lets queries be
  written as ordinary C# expressions that EF translates into SQL at execution time.

ActiveRecord (Ruby on Rails):
  THE original template for the "ActiveRecord pattern" by name -- a model class IS the row, and
  directly carries its own persistence methods (Model.find, instance.save) rather than routing
  through a separate session/repository object the way Hibernate or SQLAlchemy's ORM layer does.
```

--> **Why the ActiveRecord vs Data Mapper distinction (raised briefly in the ORM SQLAlchemy and Django ORM file) is worth reinforcing here** -- Rails' ActiveRecord and Django's ORM both follow the ActiveRecord PATTERN (the model object knows how to save/load/delete itself directly) -- Hibernate and SQLAlchemy's ORM layer instead follow the Data Mapper pattern (a separate session/mapper object handles persistence, keeping the model class itself ignorant of the database) -- this is a genuine architectural fork across the ORM landscape, not just cosmetic API differences, and it's why patterns that feel natural in Django/Rails (`user.save()`) look different in Hibernate/SQLAlchemy (`session.add(user); session.commit()`).
--> **The N+1 query problem, eager loading, and the query-builder-vs-raw-SQL escape hatch** all generalize across every ORM in this list -- the specific method names differ (`.include()` in Rails, `JOIN FETCH` in JPA/Hibernate, `.include()` in Prisma, `relations` in TypeORM) but the underlying problem and fix are the exact same concept the ORM SQLAlchemy and Django ORM file walks through for Python's ecosystem.

## Multi-Tenancy Design Patterns

--> A multi-tenant application serves multiple distinct customers ("tenants") from one deployment, and each tenant's data needs to stay logically isolated from every other tenant's -- there are three broad architectural patterns for actually achieving that isolation at the database level, each with a real trade-off between isolation strength and operational complexity.

```text
Shared schema, tenant_id column:
  ONE database, ONE set of tables, every row carries a tenant_id column, every query filters on it
  Isolation:    weakest -- a single missing "WHERE tenant_id = ?" is a cross-tenant data leak
  Operational cost: lowest -- one schema to migrate, one set of connections/indexes to manage
  Scaling:      easiest to scale to a very large NUMBER of small tenants (thousands+)

Schema-per-tenant:
  ONE database, but each tenant gets its OWN schema/namespace with identical table structure
  Isolation:    stronger -- a query naturally can't see another schema's rows without explicitly
                cross-referencing it
  Operational cost: moderate -- migrations must run against EVERY tenant's schema individually
  Scaling:      workable into the hundreds of tenants; thousands of schemas gets unwieldy to manage

Database-per-tenant:
  Each tenant gets an ENTIRELY separate database (possibly on separate infrastructure/servers)
  Isolation:    strongest -- physically separate, easiest to reason about for compliance/security
  Operational cost: highest -- N databases to provision, back up, monitor, and migrate
  Scaling:      natural fit for a SMALL NUMBER of large, high-value tenants (e.g. enterprise clients)
```

--> **Why the shared-schema pattern's isolation risk is a genuinely serious, recurring category of real-world bug** -- forgetting a `tenant_id` filter on even ONE query path is a full cross-tenant data leak, not a minor bug -- the standard mitigation is enforcing tenant isolation at a layer that CAN'T be forgotten, such as PostgreSQL Row-Level Security (covered in the Database Security, Encryption and Administration file), which transparently applies the tenant filter to every query against the table regardless of whether the application code remembered to add it.
--> **Why the choice correlates with tenant size and count, not just a technical preference** -- a SaaS product serving thousands of small teams almost always uses shared-schema (the operational cost of thousands of schemas or databases would be untenable), while a platform serving a handful of large enterprise customers -- especially ones with contractual or regulatory demands for physical data separation -- often justifies database-per-tenant's higher operational cost in exchange for its much stronger isolation and the ability to size/scale/back up each large tenant's database independently.
--> **A hybrid is common in practice** -- most large tenants get their own database, while many small tenants share one schema together -- letting an operator apply the more expensive, stronger-isolation pattern only where a specific tenant's size or contractual requirements actually justify it.

## UUID vs Auto-Increment Primary Keys, in Depth

--> The Advanced Indexing, Partitioning and Query Optimizer Internals file's clustered index coverage and the Storage Engine Internals file's page-layout coverage both set up exactly why this choice is a genuine, non-trivial performance decision rather than a stylistic one.

```text
Auto-increment INT/BIGINT:
  New rows insert with a monotonically INCREASING key -- always appending at the "end" of the
  clustered index's physical order (covered in the Advanced Indexing file)
  Result: inserts mostly APPEND to the last page -- rarely trigger a page split (covered in the
  Storage Engine Internals file), and stay compact/sequential on disk

UUID (especially UUIDv4, fully random):
  New rows insert with an effectively RANDOM key value, landing at a random position in the
  clustered index's sort order almost every single time
  Result: inserts scatter across MANY different pages rather than one hot page at the end --
  triggers far more page splits, and leaves the table's physical storage badly fragmented over time
```

--> **Why this is specifically about clustered index locality, not UUIDs being "slow" in general** -- a UUID stored as a SECONDARY, non-clustered index value (or used only as an opaque external identifier while an internal auto-increment key drives physical storage order) doesn't have this problem at all -- the cost is specifically tied to using a random value as the CLUSTERING key, the thing that determines physical row placement, because that's exactly where insert locality matters.
--> **Why UUIDs are still often chosen anyway** -- auto-increment keys are guessable and SEQUENTIALLY enumerable (a competitor or attacker can infer `/orders/1044` probably exists if `/orders/1043` does), they don't work well across multiple independent systems generating IDs without central coordination (two services can't both safely hand out the "next" auto-increment value without talking to the same database), and they leak a rough signal of creation order/volume. UUIDs solve all three at the cost of the locality problem above.
--> **UUIDv7 as the practical middle ground** -- a newer UUID variant that embeds a timestamp in its leading bits, making UUIDv7 values MONOTONICALLY INCREASING (mostly) over time, just like an auto-increment key, while keeping the rest of a UUID's benefits (global uniqueness without coordination, non-guessability in the non-timestamp bits). This directly restores insert locality -- new UUIDv7 values mostly append near the end of the clustered index's sort order rather than scattering randomly -- which is exactly why it's increasingly the recommended default over UUIDv4 specifically for primary keys, while UUIDv4 remains fine for non-clustering-key uses (external tokens, non-indexed identifiers) where locality was never the concern.
--> **The pragmatic compromise many production schemas land on** -- an internal auto-increment `BIGINT` as the actual primary/clustering key (for storage locality), plus a separate `UUID` column exposed externally in URLs/APIs (for non-guessability) -- getting both properties without forcing a single column to serve both purposes.

## Soft Deletes vs Hard Deletes

--> A **hard delete** actually removes a row (`DELETE FROM orders WHERE id = 42`) -- it's gone. A **soft delete** instead flags a row as deleted (commonly a `deleted_at` timestamp column, `NULL` meaning "not deleted") while leaving the row physically present, and every normal query filters out soft-deleted rows.

```sql
-- Soft delete
UPDATE orders SET deleted_at = NOW() WHERE id = 42;

-- Every "normal" query needs the filter added everywhere (or hidden behind an ORM default scope)
SELECT * FROM orders WHERE deleted_at IS NULL AND customer_id = 7;
```

--> **Why teams choose soft deletes** -- preserving an audit trail, supporting an "undo"/restore feature, and satisfying referential integrity for foreign keys that still point at the "deleted" row from other tables that haven't been cleaned up -- all of which a hard delete makes impossible after the fact.
--> **The indexing cost this creates** -- every index on a heavily soft-deleted table keeps accumulating entries for rows that will basically never be queried for again in normal operation, bloating the index and forcing every query to filter them out. The standard fix is a **partial index** (covered conceptually alongside the Advanced Indexing file's other index types) that only indexes the NOT-deleted rows in the first place.

```sql
-- PostgreSQL partial index -- only indexes rows that are NOT soft-deleted, keeping the index
-- small and fast even as millions of soft-deleted rows accumulate in the underlying table
CREATE INDEX idx_active_orders ON orders (customer_id) WHERE deleted_at IS NULL;
```

--> **Why an unbounded soft-delete table is still a real operational problem** -- soft deletes trade "data is truly gone" for "data lingers forever unless something else cleans it up" -- a table that's mostly soft-deleted rows still consumes disk, still needs to be vacuumed/maintained (covered in the Advanced Indexing file's VACUUM section), and still costs something even with a partial index sparing the query side -- most production systems pair soft deletes with a periodic background job that HARD-deletes (or archives to cold storage) rows soft-deleted past some retention window, getting the short-term recoverability benefit without the unbounded long-term accumulation cost.

## Change Data Capture (CDC)

--> Change Data Capture is the practice of capturing every row-level INSERT/UPDATE/DELETE a database makes, as a stream of individual change events, so OTHER systems can react to those changes without polling the database or having the application explicitly publish an event on every write.

```text
Database's own WAL/binlog (covered in the Storage Engine Internals file)
        |
        v
   Debezium (reads the WAL/binlog directly -- the exact mechanism PostgreSQL's logical
             replication, covered in the Vendor-Specific Features file, exposes for this purpose)
        |
        v
   Kafka topic: "orders.changes"
        |
        +--> A search-index updater service, keeping Elasticsearch in sync
        +--> A cache-invalidation service
        +--> A downstream analytics/warehouse pipeline (covered in the Data Warehousing,
             CQRS and Caching Strategies file)
```

--> **Why reading the WAL/binlog directly beats having the application publish events itself** -- if application code is responsible for publishing an event every time it writes to the database, ANY write path that forgets to publish (a direct SQL fix, a bulk import script, a different service touching the same table) silently produces no event, and the two systems (database and event stream) can drift out of sync. CDC tools like Debezium instead read the database's OWN replication stream directly -- the exact same mechanism a physical replica consumes -- meaning literally every committed change is captured, regardless of what code path caused it, with zero risk of an application forgetting to publish something.
--> **Where this connects to everything else** -- CDC is the general mechanism that pairs naturally with the CQRS pattern's write-model-to-read-model synchronization (covered in the Data Warehousing, CQRS and Caching Strategies file) and with cache invalidation strategies for the same file's caching section -- rather than an application explicitly writing to two places (its main database, and a cache/read-model/search index) and risking the two falling out of sync if one write succeeds and the other fails, CDC lets the SOURCE of truth's own committed changes drive every downstream system reliably, off a single write path.

## A Coda on Normalization -- 4NF, 5NF, and 6NF

--> The Normalization and Database Design file covers 1NF through BCNF in depth, which is genuinely as far as most real-world schema design needs to go. The higher normal forms exist for specific, narrower problems BCNF doesn't address, worth knowing about by name even if rarely applied directly.

--> **4NF -- multivalued dependencies** -- BCNF only concerns itself with FUNCTIONAL dependencies (one column's value determines another's). A **multivalued dependency** is a different, subtler kind of relationship: knowing one column's value determines a whole SET of values in another column, independently of any third column -- and a table can satisfy BCNF while still redundantly repeating data because of this.

```text
A table tracking which languages an employee speaks AND which skills they have, with one row
per (employee, language, skill) combination:

  employee | language | skill
  Alice    | English  | SQL
  Alice    | English  | Python
  Alice    | Spanish  | SQL
  Alice    | Spanish  | Python

Alice's languages and Alice's skills are entirely INDEPENDENT of each other, yet every language
must be paired with every skill for the table to stay consistent -- pure redundant bloat.
4NF fixes this by splitting into two separate tables: (employee, language) and (employee, skill).
```

--> **5NF -- join dependencies** -- a table satisfies 5NF (also called Project-Join Normal Form) when it CANNOT be split into smaller tables that, when rejoined, reconstruct it losslessly, unless the split was already implied by a candidate key. In practice, this addresses a narrower and rarer case than 4NF -- a three-way relationship (e.g. which agents sell which products from which companies) that can't be correctly decomposed into three separate two-way relationship tables without reconstructing spurious combinations that never actually existed -- and reaching 5NF means recognizing when a relationship is genuinely three-way (or n-way) and can't be safely decomposed into pairs at all.
--> **6NF -- temporal modeling** -- 6NF decomposes a table so far that every non-key column lives in its OWN table alongside just the primary key and a validity time period -- the practical use case is tracking each individual attribute's history of changes INDEPENDENTLY, with each attribute getting its own timeline rather than one shared "row changed" timestamp for the whole record. SQL Server's temporal tables (covered in the Vendor-Specific Features file) solve a related but coarser problem -- whole-row versioning -- without going all the way to genuine 6NF's per-attribute decomposition; true 6NF is rarely implemented by hand in mainstream applications, showing up mostly in specialized temporal/bitemporal data warehousing and certain financial/regulatory systems that need to answer "what did we believe THIS SPECIFIC FIELD was, as of THIS SPECIFIC DATE" with full historical precision, independently per field.
--> **Why these rarely get applied in practice** -- BCNF resolves the overwhelming majority of real redundancy problems, and 4NF/5NF/6NF's stricter guarantees come at a real cost in query complexity (more joins to reconstruct what used to be one row) -- most teams that hit a genuine multivalued-dependency-shaped bug fix that ONE specific table without formally pursuing 4NF/5NF/6NF compliance as a general schema-wide goal, treating these higher normal forms as a diagnostic vocabulary for specific problems rather than a checklist to chase everywhere.

## Parameter Sniffing and Plan Caching Pitfalls

--> The Advanced Indexing, Partitioning and Query Optimizer Internals file covers how the optimizer picks a plan using statistics -- **parameter sniffing** is what happens when that plan-picking process interacts badly with a PARAMETERIZED query's plan being cached and reused for different parameter values later.

```text
First execution: EXEC GetOrdersByStatus @status = 'cancelled'   -- a rare status, matches 200 rows
  Optimizer sees @status = 'cancelled' is highly selective --> picks an INDEX SEEK plan
  This plan gets CACHED, keyed by the query's shape (not by the specific parameter value)

Later execution: EXEC GetOrdersByStatus @status = 'pending'     -- a common status, matches 4 million rows
  The CACHED index-seek plan (built for 200 rows) gets REUSED as-is, because query plan caching
  by default doesn't re-optimize per parameter value
  Result: the same plan that was fast for 200 rows can be disastrously slow for 4 million rows,
  because an index seek appropriate for a small result set is a poor choice for a huge one
```

--> **Why this is genuinely confusing to debug** -- the exact same stored procedure, with the exact same code, can be fast for one caller and catastrophically slow for another, purely because of WHICH parameter value happened to be used the FIRST time the plan was compiled and cached -- a developer testing locally with a "convenient" test value can see great performance, while production sees the pathological case regularly, and nothing about the query text itself looks wrong.
--> **The fix patterns**:

```sql
-- SQL Server: force a fresh plan compilation on every execution -- avoids sniffing entirely,
-- at the cost of paying compilation overhead every single time (worth it for wildly skewed data)
EXEC GetOrdersByStatus @status = 'pending' WITH RECOMPILE;

-- Alternative: hint the optimizer to plan for "average" selectivity rather than whatever
-- value happened to compile the cached plan, trading away best-case performance for
-- more CONSISTENT performance across all parameter values
SELECT * FROM orders WHERE status = @status OPTION (OPTIMIZE FOR UNKNOWN);
```

--> **Why this connects directly to the optimizer internals and statistics coverage** -- parameter sniffing is not a bug in the optimizer -- it's a direct, logical consequence of the cost-based, statistics-driven plan selection covered in the Advanced Indexing, Partitioning and Query Optimizer Internals file, COMBINED with plan caching's assumption that a query's shape (not its specific parameter values) is what should determine which cached plan to reuse. Recognizing "the query text hasn't changed, but performance varies wildly by which value is passed in" as parameter sniffing specifically -- rather than assuming a missing index or stale statistics -- is what turns a confusing intermittent production slowdown into a well-understood, nameable problem with known fixes.
