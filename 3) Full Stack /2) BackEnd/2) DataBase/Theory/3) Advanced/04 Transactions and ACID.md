## What Is a Transaction

--> A transaction is a group of one or more SQL statements executed as a single unit -- either ALL of them succeed and are saved (committed), or if any fails, ALL are undone (rolled back), leaving the database as if none of them ran.
--> Classic example -- transferring money between two bank accounts requires both a debit and a credit to happen together; if the credit fails after the debit succeeded, the transaction rolls back the debit too, so money isn't lost.

```sql
BEGIN TRANSACTION;

UPDATE accounts SET balance = balance - 100 WHERE id = 1;
UPDATE accounts SET balance = balance + 100 WHERE id = 2;

COMMIT;   -- both updates are saved together
-- or: ROLLBACK;   -- if something went wrong, undo both updates entirely
```

# ACID Properties

--> Atomicity -- the transaction is all-or-nothing -- partial completion is never left visible to the rest of the database.
--> Consistency -- a transaction can only bring the database from one valid state to another -- it can't violate constraints (foreign keys, unique, check) it's bound by.
--> Isolation -- concurrent transactions don't interfere with each other's intermediate (uncommitted) state -- each transaction behaves as if it were running alone, to a degree controlled by the isolation level.
--> Durability -- once a transaction is committed, it survives permanently (even a crash immediately after) -- typically guaranteed via a write-ahead log flushed to disk.

# Isolation Levels

--> Isolation levels trade off consistency guarantees against performance -- stricter isolation prevents more anomalies but allows less concurrency.
--> Read Uncommitted -- can see other transactions' uncommitted changes ("dirty reads") -- rarely used, essentially no isolation.
--> Read Committed -- only sees committed data, but re-reading the same row within the same transaction can return different values if another transaction commits in between ("non-repeatable read") -- common default (PostgreSQL, SQL Server).
--> Repeatable Read -- the same row read twice within a transaction always returns the same value, but new rows matching a query can still appear on re-run ("phantom read") -- MySQL's default (InnoDB).
--> Serializable -- strictest -- transactions behave as if executed one at a time, sequentially -- prevents all anomalies but can significantly reduce concurrency/throughput.

# Common Anomalies (What Isolation Levels Prevent)

--> Dirty Read -- reading another transaction's uncommitted (possibly-to-be-rolled-back) changes.
--> Non-Repeatable Read -- re-reading the same row within one transaction gives a different value because another transaction committed an update in between.
--> Phantom Read -- re-running the same query within one transaction returns a different SET of rows because another transaction inserted/deleted matching rows in between.

# Locking

--> Databases use locks internally to enforce isolation -- a transaction updating a row typically takes a lock preventing other transactions from modifying (sometimes even reading, depending on isolation level) that same row until it commits/rolls back.
--> Deadlock -- two transactions each hold a lock the other needs, and both wait forever -- databases detect this and forcibly roll back one of the transactions to break the cycle.
--> Optimistic locking (application-level, e.g. a version column checked on update) is a common alternative to heavy DB-level locking, useful when conflicts are expected to be rare.

# Transactions in Application Code

```python
# SQLAlchemy example
with session.begin():           # transaction starts
    account1.balance -= 100
    account2.balance += 100
    # commits automatically if no exception; rolls back automatically if one is raised
```

--> Keep transactions as SHORT as possible -- a long-running transaction holds locks longer, increasing contention and the chance of deadlocks under concurrent load.
