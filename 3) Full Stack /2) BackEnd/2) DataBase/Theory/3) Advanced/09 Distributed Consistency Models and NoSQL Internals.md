## PACELC -- Extending CAP Beyond Just Partitions

--> The NoSQL and MongoDB file's CAP Theorem deep dive covers the Consistency/Availability trade-off during a network PARTITION -- PACELC extends the question to what happens even when the network is perfectly healthy (the "Else" case), which CAP alone doesn't address.

--> **PACELC reads as** -- "if a **P**artition happens, choose between **A**vailability and **C**onsistency; **E**lse (no partition), choose between **L**atency and **C**onsistency."
--> **The second half is the genuinely new insight** -- even with NO network partition at all, a system that wants strong consistency (every replica agreeing before confirming a write) must pay a LATENCY cost waiting for that agreement -- a system willing to accept slightly stale reads instead can respond faster.

```text
PA/EL systems (e.g. Cassandra, DynamoDB default config):
  During a partition: prioritize Availability over Consistency
  Even without a partition: prioritize Latency over Consistency (async replication, eventual consistency)

PC/EC systems (e.g. traditional distributed relational databases, MongoDB's default):
  During a partition: prioritize Consistency over Availability (may refuse requests to stay correct)
  Even without a partition: prioritize Consistency over Latency (wait for replica acknowledgment before confirming a write)
```

--> **Why this matters practically** -- two databases can make the IDENTICAL choice under CAP (both choose AP) yet still behave very differently day-to-day, because PACELC's "Else" half is about ORDINARY, everyday operation, not just rare partition events -- it's the more practically relevant half for a system that (hopefully) experiences partitions rarely but makes this latency/consistency trade-off on every single write.

## Quorum-Based Reads and Writes

--> Many AP-leaning distributed databases (Cassandra, DynamoDB-style systems) let an application TUNE its own consistency/availability trade-off per-operation using quorum parameters, rather than the database forcing one fixed choice for everyone.

--> **N** -- the total number of replica nodes a piece of data is stored on.
--> **W** -- how many replicas must ACKNOWLEDGE a write before it's considered successful.
--> **R** -- how many replicas must respond to a READ before returning a result to the client.

```text
N = 3 (data replicated to 3 nodes)

W = 1: fast writes, but a replica might not have the latest value yet if read from immediately after
W = 3: slower writes, but every replica is guaranteed updated before the write is confirmed

R = 1: fast reads, but might return a stale value from a replica that hasn't caught up yet
R = 3: slower reads, but guaranteed to see the most recent write (compares all 3 and returns the newest)
```

--> **The strong-consistency guarantee: W + R > N** -- if the number of replicas written to PLUS the number read from exceeds the total replica count, at least ONE node in the read set is GUARANTEED to overlap with a node in the write set -- meaning the read is guaranteed to see the latest write. This is exactly how systems like Cassandra let an application dial in strong consistency for CRITICAL data (e.g. `W=3, R=1` or `W=1, R=3` with `N=3`) while using cheaper, faster settings (`W=1, R=1`) for less critical data, all within the same database.
--> Directly connects to the Eventual Consistency deep dive in the NoSQL file -- quorum tuning is the actual DIAL that determines exactly how "eventual" that consistency really is for a given read/write.

## Consistent Hashing

--> Directly connects to the Sharding deep dive in the Replication and Sharding file's hash-based sharding strategy -- consistent hashing solves a specific problem plain hash-based sharding has: REBALANCING when a node is added or removed.

--> **The problem with naive hashing** -- `shard = hash(key) % number_of_nodes` -- if `number_of_nodes` changes (a node is added or removed), the modulo result changes for ALMOST EVERY key, meaning almost all data would need to move to a different node all at once -- a massive, disruptive rebalancing operation.
--> **The consistent hashing fix** -- nodes and keys are both mapped onto positions on a conceptual CIRCLE (a "hash ring") using the same hash function. Each key belongs to the NEXT node found by moving clockwise around the ring from the key's position.

```text
Hash ring (simplified):
        Node A (position 10)
       /                    \
  Node D (270)          Node B (90)
       \                    /
        Node C (180)-------

A key hashing to position 50 belongs to the NEXT node clockwise -- Node B (90).
```

--> **Why this fixes rebalancing** -- adding a new node onto the ring only affects the small ARC of keys between the new node and its immediate predecessor on the ring -- every other key's assignment stays exactly the same, since its "next node clockwise" hasn't changed. Removing a node similarly only reassigns that node's own arc to its neighbor, not the entire dataset.
--> This is the actual mechanism behind how systems like Cassandra, DynamoDB, and many distributed caches (consistent-hash-based client libraries for Redis/Memcached clusters) add/remove nodes with only a small, proportional amount of data movement, instead of a full reshuffle.

## Conflict Resolution in Multi-Master Replication

--> The Replication and Sharding file flags that multi-primary replication "introduces conflict-resolution complexity" -- this is what that complexity actually looks like when two primaries accept CONFLICTING writes to the same piece of data near-simultaneously.

--> **Last-Write-Wins (LWW)** -- each write is tagged with a timestamp; when two conflicting versions are compared, the one with the LATER timestamp simply wins, and the other is silently discarded. Simple to implement, but can silently lose a legitimate update if two writes happen close enough together that clock skew between servers makes the "later" timestamp not actually correspond to which write truly happened later in reality.
```text
Node A writes: {value: "red", timestamp: 100}
Node B writes: {value: "blue", timestamp: 105}   -- LWW keeps this one, "red" is discarded, even though
                                                     both writes may have been made by different users
                                                     who each thought their write would simply apply
```
--> **Vector clocks** -- instead of a single timestamp, each write carries a vector of counters, one per node, tracking causal history ("this write happened after having seen version 3 from Node A and version 1 from Node B") -- lets the system DETECT when two writes are genuinely CONCURRENT (neither is causally aware of the other) rather than just picking whichever has a later wall-clock timestamp. When a genuine conflict is detected this way, the system typically surfaces BOTH versions to the application (or the end user) to resolve manually, rather than silently picking one.
--> **CRDTs (Conflict-free Replicated Data Types)** -- specially designed data structures (counters, sets, sorted lists) whose MERGE operation is mathematically guaranteed to produce the same, correct result regardless of the order concurrent updates are applied in -- avoiding the need for either LWW's silent data loss or vector clocks' manual conflict surfacing, for the specific, more limited set of operations a CRDT supports (e.g. a CRDT counter can be incremented concurrently on two nodes and merged into a correct combined total, without either increment being lost).
--> **Why this matters practically** -- choosing multi-master replication isn't just a scaling decision -- it's an implicit commitment to handling THIS exact class of problem somewhere, whether that's accepting LWW's occasional silent data loss, building vector-clock-based conflict surfacing into the application, or restricting the data model to CRDT-compatible operations only.

## Column-Family and Graph Database Structural Mechanics

--> The NoSQL file names these two categories alongside document/key-value but only explains document stores (MongoDB) in structural depth -- here's how the other two are actually organized internally.

# Column-Family Stores (Cassandra, HBase)

--> Data is organized by ROW KEY, but unlike a relational table, each row doesn't need the same set of columns -- columns are grouped into "column families," and a given row can have wildly different columns present in one family versus another row in the same table.

```text
Row key: "user123"
  Column family "profile":   {name: "Alice", email: "alice@x.com"}
  Column family "activity":  {last_login: "2026-08-19", login_count: 42}

Row key: "user456"
  Column family "profile":   {name: "Bob"}   -- no email at all for this row, and that's fine
  Column family "activity":  {last_login: "2026-08-01"}
```

--> Physically, each column family is often stored SEPARATELY on disk, sorted by row key -- this means reading just the "profile" data for many users is efficient (only that column family's data needs to be scanned), which is the whole point of the design: extremely fast reads/writes over specific columns across a HUGE number of rows, at the cost of the flexible, schema-light row shape making full-row-shaped queries (like a relational `SELECT *`) less natural than in a document store.
--> Cassandra specifically distributes rows across nodes via consistent hashing on the row key (directly connecting to the deep dive above), which is why choosing a good row key (with high, evenly-distributed cardinality) is as important a design decision in Cassandra as choosing a good sharding key is in any horizontally-scaled system.

# Graph Databases (Neo4j)

--> Data is modeled explicitly as NODES (entities, roughly like rows) and EDGES (relationships between them, which can themselves carry properties, e.g. a "FOLLOWS" edge might have a "since" date) -- and critically, RELATIONSHIPS ARE STORED AS FIRST-CLASS DATA, not reconstructed via a JOIN at query time.

```text
(Alice)-[:FOLLOWS {since: 2023}]->(Bob)-[:FOLLOWS {since: 2024}]->(Carol)
```

```cypher
// Cypher query language (Neo4j) -- find everyone Alice's followers also follow, 2 hops away
MATCH (alice:Person {name: "Alice"})-[:FOLLOWS]->()-[:FOLLOWS]->(fof)
RETURN fof.name;
```

--> **Why a relational JOIN struggles here that a graph database doesn't** -- a relational "find friends-of-friends 5 levels deep" query needs 5 chained `JOIN`s (or a recursive CTE, covered in its own file) -- each additional level of traversal depth adds real cost and query complexity. A graph database TRAVERSES relationships directly, following stored edge pointers, with cost proportional to how much of the graph is actually visited rather than growing with the number of join levels written into the query -- this is precisely why graph databases are the natural fit for social networks, recommendation engines, and fraud-detection network analysis, where "how are these two things connected, possibly through several hops" is the core, recurring question being asked.
