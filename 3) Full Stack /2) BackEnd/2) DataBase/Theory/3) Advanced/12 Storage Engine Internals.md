## Write-Ahead Logging (WAL) -- How Databases Survive a Crash

--> Every file so far has assumed a `COMMIT` durably persists data (that's the "D" in ACID, covered in the Transactions and ACID file) -- WAL is the actual MECHANISM that makes that durability guarantee possible without paying the cost of writing every changed data page to disk on every single transaction.

--> **The core idea** -- before any change is applied to the actual data pages on disk, the database first writes a LOG RECORD describing that change to a sequential, append-only log file, and only considers the change durable once that log record itself is safely on disk. The actual data pages can be updated later, lazily, in memory first and flushed to disk on the database's own schedule -- because if the database crashes before that lazy flush happens, the WAL record is enough to reconstruct the change on restart.

```text
Transaction: UPDATE accounts SET balance = balance - 100 WHERE id = 1;

1. Log record written FIRST, sequentially, to the WAL file on disk:
     [LSN 4001] Tx55: accounts.id=1, old_balance=500, new_balance=400
2. THEN the in-memory copy of that data page is updated (not yet flushed to its real location on disk)
3. Transaction commits -- durable the instant the WAL record hit disk, even though the data
   page itself might still only exist in memory
```

--> **Why sequential writes matter** -- appending to one sequential log file is dramatically cheaper than writing scattered data pages all over the disk in random locations, especially on spinning disks where random I/O is orders of magnitude slower than sequential I/O -- WAL turns the expensive part of durability (guaranteeing survivability) into a cheap sequential append, and defers the expensive part of actually updating scattered pages to a later, batchable point.
--> Every mainstream relational database uses this technique under a different name -- PostgreSQL calls it WAL directly, MySQL/InnoDB calls it the "redo log," SQL Server calls it the "transaction log." The underlying idea is identical in all three.

# Checkpointing

--> The log can't grow forever -- **checkpointing** is the periodic process of actually flushing all the dirty (modified but not-yet-persisted) data pages sitting in memory out to their real locations on disk, after which the WAL records describing those now-flushed changes are no longer needed for crash recovery and can be discarded/recycled.

```text
Checkpoint at LSN 4000: "everything up to this point is guaranteed flushed to the actual data files"

WAL records BEFORE the checkpoint --> safe to discard, already reflected in the data files
WAL records AFTER the checkpoint  --> must be replayed on crash recovery, not yet flushed
```

--> **The trade-off** -- checkpointing too often wastes I/O re-flushing pages repeatedly; checkpointing too rarely means a longer WAL to replay on crash recovery (slower restart) and a larger log file to retain on disk in the meantime. PostgreSQL's `checkpoint_timeout` and `max_wal_size` are exactly the knobs that tune this trade-off in practice.

# Redo and Undo -- Recovering After a Crash

--> On restart after a crash, the database replays the WAL to get back to a consistent state -- this replay has two distinct halves, and confusing them is a common source of misunderstanding how crash recovery actually works.

--> **Redo** -- re-applies every COMMITTED transaction's changes that the WAL recorded but that never made it to the actual data files before the crash (because they were still sitting as dirty pages in memory when power was lost). Redo brings the data files forward to reflect every commit that was ever confirmed.
--> **Undo** -- rolls back any transaction that was IN PROGRESS (had written some changes, but never actually reached `COMMIT`) at the moment of the crash -- using the WAL's own "old value" information to reverse those partial changes, so the database doesn't end up with half of an uncommitted transaction's effects visible.

```text
Crash recovery sequence (this is literally the "ARIES" algorithm most relational engines are based on):
  1. Analysis  -- scan the WAL to determine which transactions were committed vs still in-flight at crash time
  2. Redo      -- replay ALL logged changes forward (even for transactions later found to be uncommitted --
                  redo doesn't discriminate yet, it just gets every page back to its last-known state)
  3. Undo      -- now roll back the specific changes belonging to transactions that never committed
```

--> **Why redo runs before undo, and redoes even uncommitted transactions' changes** -- this sounds backwards, but it's what lets recovery use ONE simple, uniform pass over the log rather than needing to know a transaction's final fate before deciding whether to replay each record -- undo then surgically removes only what shouldn't have survived, using the log's own before-images. This exact two-pass discipline is why a database can crash mid-write, mid-transaction, at literally any instant, and still come back up in a state equivalent to some valid sequence of fully-committed transactions -- the durability and atomicity guarantees from the Transactions and ACID file are not hopeful assumptions, they're what this recovery procedure mechanically enforces.

## Buffer Pool -- The Database's Own Page Cache

--> Reading and writing directly from disk on every single query would be unusably slow -- every mainstream database maintains its own in-memory cache of recently-used data pages, called the **buffer pool** (InnoDB's term) or shared buffers (PostgreSQL's term), sitting between query execution and the actual disk files.

```text
Query needs page 42:
  Page 42 already in buffer pool?  --> YES: serve straight from memory, no disk I/O at all
                                     --> NO:  read from disk into a free buffer pool slot, THEN serve it
```

--> **Why this is separate from the OS's own file cache** -- the operating system already caches recently-read disk blocks in memory too, but the database's own buffer pool understands things the OS cache can't -- which pages are part of the same table, which pages are "dirty" and need eventual flushing, which pages are being accessed by an active transaction right now. This is why database buffer pool sizing (`innodb_buffer_pool_size` in MySQL, `shared_buffers` in PostgreSQL) is one of the single most impactful tuning knobs available -- a buffer pool sized to hold a working set entirely in memory turns what would be constant disk I/O into constant memory access.

# Eviction Policies

--> When the buffer pool is full and a new page needs to be loaded, some existing page must be evicted to make room -- WHICH page gets evicted matters enormously for performance.

--> **LRU (Least Recently Used)** -- evicts whichever page hasn't been touched in the longest time, on the reasonable assumption that a page unused for a while is less likely to be needed again soon than one just accessed. The naive version has a known weakness: a single large sequential scan (a `SELECT *` over a huge table, or a bulk backup) touches a huge number of pages exactly once, which under naive LRU would flush out the buffer pool's genuinely "hot," frequently-reused pages in favor of pages that will never be touched again.
--> **The fix -- a "midpoint insertion" / segmented LRU** -- InnoDB's actual buffer pool splits its LRU list into a "young" segment (pages accessed multiple times, presumed genuinely hot) and an "old" segment (pages seen only once or recently loaded) -- a fresh page enters the "old" segment first, and only gets promoted to "young" if it's accessed AGAIN after some delay, specifically so that one giant sequential scan doesn't evict the genuinely hot working set out from under normal traffic.
--> **Why this matters practically** -- running an unindexed, full-table-scanning report query against a production OLTP database can measurably hurt the performance of completely unrelated queries, purely by disturbing buffer pool contents -- this is one of the concrete mechanical reasons "run heavy analytical queries against a read replica" (covered in the Database Replication and Sharding file) is standard practice, not just a matter of raw CPU contention.

# Dirty Page Flushing

--> A page modified in the buffer pool but not yet written to its real location on disk is called "dirty." Flushing dirty pages back to disk happens on the database's own schedule (checkpointing, background flush threads, or when the buffer pool needs to evict a page and finds it's dirty and must flush it first) -- NOT synchronously on every write, since the WAL already guarantees durability for anything committed.
--> **Why flushing can lag safely** -- because the WAL record for that change already hit disk at commit time, a delayed dirty-page flush isn't a durability risk -- if the database crashes before the flush, the redo phase above simply replays the WAL record and reconstructs the page's correct state anyway. This decoupling (log durability now, data-page durability whenever convenient) is precisely what lets WAL turn synchronous random writes into asynchronous, batchable ones.

## Physical Row and Page Layout

--> Conceptually, a table's data is stored as fixed-size PAGES (commonly 8 KB in PostgreSQL, 16 KB in InnoDB) -- each page holds multiple rows, plus a small header and a slot/pointer array that lets the engine locate each row's exact byte offset within the page without scanning the whole page byte-by-byte.

```text
A single 8 KB page (simplified):
  [ Page header: page ID, free space pointer, checksum ]
  [ Row 1 data ][ Row 2 data ][ Row 3 data ]   ... free space ...   [ slot 3 ][ slot 2 ][ slot 1 ]
  (row data grows from the front of the page; the slot array pointing to each row grows from the back)
```

--> **Why rows are addressed via a slot array rather than a fixed offset** -- an `UPDATE` that shrinks or grows a row's size (e.g. a `VARCHAR` column getting a longer value) can't just overwrite it in place if the new size doesn't fit the old slot -- the slot array lets the engine move the row's actual bytes elsewhere WITHIN the page (or mark it as moved to another page entirely) while every index and pointer that referenced that row by its slot number keeps working unchanged.
--> **Row fragmentation / page splits** -- when a page fills up and a new row (or a growing row) no longer fits, the engine must SPLIT the page -- allocating a new page and moving roughly half the rows into it. This is inherently disruptive: it's extra I/O, it can temporarily fragment what used to be sequential physical storage, and it's the direct physical reason the UUID vs auto-increment primary key discussion (covered in the Database Testing, Multi-Tenancy, and Modern Data Patterns file) cares so much about INSERT order relative to a clustered index's (covered in the Advanced Indexing, Partitioning and Query Optimizer Internals file) physical row ordering.
--> **Row overflow / TOAST (PostgreSQL)** -- a row too large to fit on a single page at all (a large `TEXT` or `JSONB` value) gets its oversized column stored OUT-OF-LINE in a separate storage area, with only a small pointer left in the main row -- PostgreSQL calls this mechanism TOAST (The Oversized-Attribute Storage Technique); other engines have their own equivalent "overflow page" concepts. This is why a table with a handful of huge `TEXT` columns can have a surprisingly small "main" table size on disk -- the bulk of the actual bytes live elsewhere.

## LSM-Trees -- The Structure Behind Cassandra, RocksDB, and LevelDB

--> Every index discussion so far (the Indexing and Performance Tuning file, the Advanced Indexing, Partitioning and Query Optimizer Internals file) has assumed a B-Tree -- a great structure for READ-heavy, in-place-update workloads. An **LSM-tree** (Log-Structured Merge-tree) is a fundamentally different structure optimized for the opposite: very high WRITE throughput, at some cost to read performance.

--> **The core idea -- never do random writes at all.** Instead of updating a row's page in place (which for a B-Tree can mean a random disk seek), an LSM-tree buffers writes in memory (a "memtable"), and once that buffer fills up, flushes the ENTIRE buffer to disk as one new, immutable, sorted file in one sequential write -- an "SSTable" (Sorted String Table). A write is NEVER an in-place disk modification; it's always an append of a new immutable file.

```text
Writes come in --> buffered in an in-memory memtable (also written to a WAL first, for crash safety)
Memtable fills up --> flushed to disk as a new immutable, sorted SSTable file -- one sequential write
Over time:  SSTable_1 (oldest), SSTable_2, SSTable_3, ... SSTable_N (newest), plus the current memtable

A READ for key "X" must potentially check: the memtable, then EACH SSTable, newest first,
until it finds "X" (or confirms it's absent) -- this is the read-side cost of the write-side speed.
```

--> **Compaction** -- since older SSTables accumulate (and may contain stale/overwritten/deleted versions of the same key that a newer SSTable has since superseded), a background COMPACTION process periodically merges multiple SSTables into fewer, larger ones -- discarding superseded versions and keeping the total number of files a read has to check bounded, at the cost of real, ongoing background I/O and CPU work.
--> **Bloom filters make the read-side cost tolerable** -- each SSTable keeps a small, probabilistic Bloom filter that can answer "is this key DEFINITELY NOT in this file?" extremely cheaply, letting a read skip the vast majority of SSTables that certainly don't contain the key, checking only the ones a Bloom filter says MIGHT contain it (a Bloom filter can false-positive, never false-negative, so it never causes a wrongly-skipped file).
--> **Why LSM-trees fit Cassandra, RocksDB, and LevelDB specifically** -- these are all workloads or engines that prioritize sustained high write throughput (Cassandra's write path, covered structurally in the Distributed Consistency Models and NoSQL Internals file) over the read latency a B-Tree gives -- and compaction's background cost is an accepted trade for turning writes into cheap sequential appends instead of random in-place updates.
--> **B-Tree vs LSM-tree, side by side**:

```text
B-Tree (PostgreSQL, InnoDB default):
  Writes:  in-place, can require random disk I/O to update the exact page a row lives on
  Reads:   direct, single well-defined path down the tree to the row -- no need to check multiple structures
  Best for: read-heavy or balanced OLTP workloads, where read latency matters as much as write throughput

LSM-tree (Cassandra, RocksDB, LevelDB):
  Writes:  always sequential appends -- extremely high sustained write throughput
  Reads:   must potentially check several SSTables (mitigated by Bloom filters) -- higher read latency
  Best for: write-heavy workloads (time-series ingestion, logging, event streams) that can tolerate
            slightly costlier reads in exchange for much cheaper, faster writes
```

## Storage Engine Comparison -- InnoDB vs MyISAM

--> MySQL is unusual among mainstream relational databases in letting different TABLES use entirely different storage engines underneath the same SQL interface -- InnoDB and MyISAM are the two that matter historically, and the contrast is a genuinely useful case study in storage engine trade-offs generally.

```text
                    InnoDB                              MyISAM
Transactions:       Full ACID, COMMIT/ROLLBACK           None -- no transaction support at all
Locking:            Row-level (covered in the MVCC       Table-level only (an UPDATE locks the
                    Locking and Distributed                whole table, blocking all other writers)
                    Transactions file)
Crash recovery:     WAL-based (redo log), survives        No WAL -- a crash mid-write can leave a
                    a crash cleanly                        table corrupted, often needing REPAIR TABLE
Foreign keys:       Enforced                              Not enforced at all
Concurrency:        MVCC -- readers don't block writers   Poor -- table locks serialize writers,
                                                            and often block readers too
```

--> **Why InnoDB won and is now MySQL's default** -- nearly every property a production OLTP workload actually needs (crash safety, row-level concurrency, real transactions, foreign key integrity) is exactly where MyISAM is weakest -- MyISAM's only genuine advantage was being simpler and marginally faster for pure read-only or single-writer workloads, an advantage that's mattered less and less as InnoDB's own performance has matured.
--> This same "different engine, different trade-offs, same SQL surface" idea is why the WAL/redo-log, buffer pool, and B-Tree/LSM-tree material above matters even for people who never touch MySQL directly -- every relational and NoSQL database is making some version of these exact same underlying storage engine decisions, just usually without exposing the choice as literally as MySQL's `ENGINE=InnoDB` / `ENGINE=MyISAM` syntax does.

## Checksums and Corruption Detection

--> Disks and network transfers occasionally corrupt bytes silently -- a bit flips, a partial write happens during a power loss, a bad disk sector returns wrong data without erroring -- storage engines defend against this by storing a CHECKSUM (a small computed value derived from a page's actual contents) alongside every page.

```text
Page written to disk:  [ page data ... ][ checksum computed from that data ]

Page read back later:  recompute the checksum from the bytes actually read,
                        compare against the stored checksum
                        --> mismatch means the page was corrupted somewhere between write and read
```

--> **What happens on a mismatch** -- the database refuses to use the corrupted page and raises an error rather than silently returning wrong data to a query -- corruption caught this way is a serious operational event (usually meaning restoring from a known-good backup or replica), but it is far preferable to the alternative of a corrupted page being silently read as if it were valid data, which could feed wrong values into query results indefinitely without anyone noticing.
--> PostgreSQL supports page-level checksums via `data_checksums` (enabled at `initdb` time, or added later via `pg_checksums`); InnoDB has checksums enabled by default on every page. Enabling checksums has a small, usually negligible CPU cost, which is why it's overwhelmingly considered worth it for anything beyond a throwaway development database.
--> **Why this connects to replication** -- a checksum failure on a primary is exactly the kind of event a healthy replica (covered in the Database Replication and Sharding file) exists to recover from cleanly -- corruption caught early, with an independent, unaffected copy of the data available, is a routine incident; corruption caught late, after it's already propagated into every backup taken since, is a genuine data-loss event.
