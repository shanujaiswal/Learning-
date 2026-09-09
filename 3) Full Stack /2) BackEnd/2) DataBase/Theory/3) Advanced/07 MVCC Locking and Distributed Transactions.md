## MVCC -- How Modern Databases Actually Implement Isolation

--> The Transactions and ACID file covers isolation levels and locking conceptually -- MVCC (Multi-Version Concurrency Control) is the actual MECHANISM PostgreSQL, MySQL's InnoDB, and Oracle use under the hood to achieve most of that isolation WITHOUT blocking readers against writers.

--> **The core idea** -- instead of a writer locking a row and making readers wait, the database keeps MULTIPLE VERSIONS of a row simultaneously. A writer creates a NEW version of the row rather than overwriting the old one in place. Each transaction sees a consistent SNAPSHOT of the data as it existed at some point in time, ignoring versions created by transactions that started later or haven't committed yet.

```text
Row with CustomerID=1, before any update:      Version 1: {Name: "Alice", updated_by: Tx100}

Transaction 200 updates the name:              Version 2: {Name: "Alicia", updated_by: Tx200}
(Version 1 isn't deleted immediately -- it stays around for any transaction still reading the old snapshot)

A transaction that started BEFORE Tx200 committed still sees Version 1.
A transaction that starts AFTER Tx200 commits sees Version 2.
```

--> **Why this matters practically** -- under MVCC, a long-running `SELECT` does NOT block a concurrent `UPDATE`, and vice versa (readers don't block writers, writers don't block readers) -- a huge concurrency win compared to a purely lock-based approach where every read would need to wait for a write lock to release. This is exactly why PostgreSQL and MySQL/InnoDB can sustain far higher concurrent read/write throughput than a naive "lock everything" design would allow.
--> **The cost** -- old row versions can't be deleted immediately (some other transaction might still need to see them) -- they accumulate as "dead" versions until no transaction needs them anymore, at which point they're reclaimed. In PostgreSQL, this reclaiming is exactly what `VACUUM` (covered in the Indexing and Query Optimization file) does -- a table that's never vacuumed accumulates dead row versions indefinitely, a phenomenon called "table bloat."
--> MVCC is precisely why `Repeatable Read` (covered in the Transactions file) is efficient to implement -- the transaction simply keeps using its OWN starting snapshot for every read, rather than needing to hold locks on every row it has ever read.

## Two-Phase Locking (2PL)

--> 2PL is the classic LOCK-BASED alternative/complement to MVCC for enforcing serializable-style isolation -- it guarantees that a set of concurrent transactions produces a result equivalent to running them one at a time, purely through a disciplined locking protocol.

--> **The two phases**:
1. **Growing phase** -- a transaction can ACQUIRE locks (but never release any) as it reads/writes rows.
2. **Shrinking phase** -- once a transaction releases its FIRST lock, it can never acquire any NEW lock again -- only release the ones it already holds.

```text
Transaction timeline under 2PL:
  Acquire lock(A) --> Acquire lock(B) --> Acquire lock(C) --> [ COMMIT ] --> Release A, B, C
  ^--------------- growing phase ---------------^              ^------ shrinking phase ------^
```

--> In practice, most real databases use a simplified variant -- "strict 2PL" -- where ALL locks are held until the transaction actually commits or rolls back (the shrinking phase collapses to a single instant at the very end), which is simpler to reason about and is what most production lock-based isolation actually implements.
--> **Why the discipline matters** -- if a transaction were allowed to acquire a NEW lock after releasing an old one, two transactions could interleave their lock acquisition/release in a way that produces a result NO serial (one-at-a-time) execution could ever produce -- 2PL's strict ordering rule is precisely what rules that out and guarantees serializability.

## Lock Granularity

--> Locks can be taken at different GRANULARITIES -- a fundamental trade-off between concurrency (finer locks let more transactions proceed at once) and overhead (finer locks mean more individual locks to track and check).

--> **Row-level locking** -- locks only the specific row(s) actually being modified -- the finest common granularity, maximizing concurrency (two transactions updating DIFFERENT rows in the same table don't block each other at all). Used by InnoDB (MySQL), PostgreSQL.
--> **Page-level locking** -- locks an entire physical storage page (which might contain many rows) -- coarser than row-level, meaning updates to unrelated rows that happen to share a page can block each other unnecessarily.
--> **Table-level locking** -- locks the ENTIRE table -- coarsest granularity, simplest to implement, but means only ONE write can happen against that table at a time regardless of which rows are actually involved (MyISAM, MySQL's older storage engine, relies heavily on table-level locking, which is a large part of why InnoDB with row-level locking largely replaced it as the default).

```sql
-- An explicit table-level lock (rarely needed directly, but illustrates the concept)
LOCK TABLES Orders WRITE;
-- ... perform operations ...
UNLOCK TABLES;
```

--> **Practical guidance** -- row-level locking is why modern OLTP workloads (many small, independent transactions -- covered in the Database Fundamentals file's OLTP/OLAP deep dive) scale well under concurrent load -- two customers placing orders on different rows don't block each other at all, which would NOT be true under table-level locking.

## Distributed Transactions and Two-Phase Commit (2PC)

--> A normal transaction (covered in the Transactions file) coordinates changes within ONE database. A DISTRIBUTED transaction needs to coordinate a single atomic all-or-nothing outcome ACROSS multiple separate databases/services (e.g. debiting an account in Database A while crediting one in Database B, where A and B are entirely separate systems).

--> **Two-Phase Commit (2PC)** is the classic protocol for this, coordinated by a designated "coordinator":

--> **Phase 1 -- Prepare** -- the coordinator asks EVERY participant "can you commit this?" -- each participant does everything needed to guarantee it CAN commit (writes to its own log, acquires necessary locks) but does NOT actually commit yet, and responds "yes" or "no."
--> **Phase 2 -- Commit/Abort** -- if EVERY participant said "yes," the coordinator tells all of them to actually COMMIT. If even ONE participant said "no" (or failed to respond), the coordinator tells everyone to ABORT/rollback instead.

```text
Coordinator                    Database A                Database B
     |-- PREPARE? ------------------>|                          |
     |-- PREPARE? ---------------------------------------------->|
     |<---------------- "yes, ready" |                          |
     |<---------------------------------------------- "yes, ready"|
     |-- COMMIT ---------------------->|                          |
     |-- COMMIT ----------------------------------------------->|
     -- Both A and B commit together, or if either said "no," both would have aborted instead --
```

--> **The real cost** -- 2PC requires every participant to hold its locks/resources through BOTH round trips, meaning it's noticeably slower than a single-database transaction, and if the COORDINATOR itself crashes between phase 1 and phase 2, participants can be left "blocked," uncertain whether to commit or abort until the coordinator recovers. This is precisely why many modern distributed/microservices architectures AVOID 2PC where possible, favoring alternative patterns like the SAGA pattern (a sequence of local transactions with defined COMPENSATING actions to undo earlier steps if a later one fails) -- directly connecting to the Microservices Architecture concepts covered in the Full Stack Backend notes, which accept eventual consistency in exchange for avoiding this cross-service blocking risk.

## SELECT ... FOR UPDATE -- Explicit Row Locking

--> Beyond the isolation levels a transaction runs under by default, `SELECT ... FOR UPDATE` lets application code explicitly LOCK the specific rows it just read, for the remainder of its transaction -- preventing any OTHER transaction from modifying (or, depending on the database, even reading with their own `FOR UPDATE`) those same rows until this transaction commits or rolls back.

```sql
BEGIN TRANSACTION;

-- Lock this specific row -- any other transaction's own SELECT ... FOR UPDATE on the same row now waits
SELECT quantity FROM inventory WHERE product_id = 42 FOR UPDATE;

-- Safe to check-then-act now -- no other transaction can sneak in a conflicting update on this row in between
UPDATE inventory SET quantity = quantity - 1 WHERE product_id = 42;

COMMIT;
```

--> **The classic use case it solves** -- a naive "read the current stock, check if enough is available, then update it" sequence has a race condition under normal isolation: two concurrent transactions could BOTH read the same starting quantity, both decide there's enough stock, and both proceed to oversell the last unit. `FOR UPDATE` closes that gap by making the second transaction's own `SELECT ... FOR UPDATE` on that row WAIT until the first transaction fully commits (or rolls back), at which point it sees the truly up-to-date, already-decremented quantity.
--> Directly connects to the Optimistic Locking mention in the Transactions file -- `FOR UPDATE` is the PESSIMISTIC alternative: it assumes conflicts are likely enough to be worth actively preventing via a lock upfront, rather than optimistic locking's approach of proceeding hopefully and only checking for a conflict (via a version column) at the final update.
