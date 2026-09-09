# The Problem -- One Database Server Isn't Enough Forever

--> A single database server has hard physical limits (CPU, memory, disk I/O) and is a single point of failure -- as an application grows, both READ capacity and total DATA VOLUME can eventually exceed what one server can reasonably handle.
--> Replication and Sharding are two different, often complementary, strategies for scaling a database beyond a single server.

# Replication -- Copying Data Across Multiple Servers

--> Replication maintains one or more COPIES of the same data on additional servers -- primarily used for read scaling and fault tolerance, not for storing more total data (every replica holds the same full dataset).

# Primary-Replica (Master-Slave) Replication

--> One Primary (master) server handles all WRITES -- changes are then automatically streamed to one or more Replica (slave/secondary) servers.
--> Read queries can be distributed across the replicas, offloading read traffic from the primary -- a common, simple way to scale an application that's read-heavy (the typical case for most web apps).
--> Writes MUST still go to the primary -- replication does not help scale write throughput, only read throughput and availability.

```
        WRITES
           |
           v
      [ Primary ] -----replicates changes----> [ Replica 1 ]
                    \                                        (reads)
                     \--replicates changes----> [ Replica 2 ]
                                                                (reads)
```

# Replication Lag

--> Replication is typically ASYNCHRONOUS -- a small delay exists between a write hitting the primary and that same data appearing on a replica.
--> This creates a real, common bug class -- a user updates their profile, the write goes to the primary, but the immediately-following read (routed to a replica) doesn't reflect the change yet because replication hasn't caught up ("eventual consistency" lag). Applications sensitive to this often route a user's own reads to the primary right after they write, or use synchronous replication for specific critical data at the cost of higher write latency.

# Failover -- Replication for High Availability

--> If the primary fails, one replica can be automatically (or manually) PROMOTED to become the new primary -- minimizing downtime compared to a single-server setup with no replica to fail over to.
--> Multi-primary (multi-master) replication -- multiple servers can all accept writes, replicating changes to each other -- offers higher write availability but introduces conflict-resolution complexity when the same data is modified on two primaries near-simultaneously.

# Sharding -- Splitting Data Across Multiple Servers

--> Sharding (horizontal partitioning) splits a large dataset ACROSS multiple servers, where each shard holds only a SUBSET of the total data -- unlike replication, no single server holds the complete dataset. This is how you scale total data volume/write throughput beyond what one server's storage/hardware could hold.

```
All Users
    |
    +--> Shard 1: users with id % 4 == 0
    +--> Shard 2: users with id % 4 == 1
    +--> Shard 3: users with id % 4 == 2
    +--> Shard 4: users with id % 4 == 3
```

# Sharding Strategies

--> Range-based sharding -- split by a value range (user IDs 1-1,000,000 on shard 1, 1,000,001-2,000,000 on shard 2). Simple, but can create uneven ("hot") shards if data/traffic isn't evenly distributed across ranges.
--> Hash-based sharding -- apply a hash function to a shard key (e.g. user ID) to decide which shard a row belongs to -- generally distributes data more evenly than range-based sharding, at the cost of losing easy range-scan queries across data.
--> Directory-based sharding -- a lookup table explicitly maps each shard key to its shard -- most flexible (shards can be rebalanced by updating the directory), but the lookup table itself becomes a new critical, potentially bottlenecking component.

# The Real Cost of Sharding

--> Cross-shard queries/joins (e.g. "find all orders across all shards for a given date range") become significantly harder -- data that would be a simple `JOIN` on one server now needs to be gathered and combined across multiple independent databases at the application level.
--> Rebalancing shards as data grows unevenly is operationally complex -- this is precisely why sharding is usually treated as a last resort, adopted only once vertical scaling, read replicas, caching (Redis, covered elsewhere), and query/index optimization have already been pushed as far as they reasonably can go.

# Replication and Sharding Together

--> Large-scale production systems commonly combine both -- data is SHARDED across many servers for volume/write scaling, and each individual shard ALSO has its own read replicas for read scaling and fault tolerance within that shard.
