# Beyond Caching -- Redis as a Genuine Data and Messaging Platform

--> The previous file establishes Redis as a fast in-memory store, primarily for caching. This file covers four capabilities that go well beyond that: making a sequence of Redis operations genuinely atomic, what actually happens when Redis's memory fills up, a durable messaging primitive (Streams) that closes the gap the previous file names in Pub/Sub, and how Redis achieves both high availability and horizontal scale in production, using two genuinely different mechanisms for two genuinely different problems.

# Redis Transactions -- MULTI/EXEC

--> A single Redis command is always atomic by itself, but a SEQUENCE of commands (read a value, compute something from it, write a new value) is NOT automatically atomic across the sequence -- another client's command could run in between, exactly the same race-condition risk covered for databases in the Transactions and ACID file, just at the level of Redis commands instead of SQL statements.

```bash
MULTI                     # Start queuing commands -- none execute yet
INCR counter:visits
INCR counter:signups
EXEC                       # Execute EVERYTHING queued, as one atomic, uninterruptible block
```

```python
pipe = r.pipeline(transaction=True)   # transaction=True wraps this in MULTI/EXEC
pipe.incr("counter:visits")
pipe.incr("counter:signups")
pipe.execute()
```

--> **What MULTI/EXEC actually guarantees** -- every queued command runs back-to-back, with no OTHER client's command able to interleave in between -- but, importantly, it is NOT the same as a SQL transaction's rollback guarantee: if one queued command fails at execution time (a genuine runtime error, not a syntax error caught at queue time), the OTHER commands in the same MULTI block still run -- there's no automatic all-or-nothing rollback the way a failed SQL transaction rolls back entirely.
--> **WATCH -- optimistic locking for read-then-write sequences** -- `MULTI`/`EXEC` alone doesn't let you make a decision based on a value and safely act on it, since another client could change that value between your read and your `EXEC`. `WATCH` marks a key such that if ANY other client modifies it before your `EXEC` runs, your entire transaction is aborted (returns `null`) rather than executing against a now-stale value.

```python
with r.pipeline() as pipe:
    while True:
        try:
            pipe.watch("account:1:balance")
            balance = int(pipe.get("account:1:balance"))
            pipe.multi()
            pipe.set("account:1:balance", balance - 50)
            pipe.execute()          # succeeds only if balance wasn't changed since WATCH
            break
        except redis.WatchError:
            continue                # someone else changed it -- retry the whole read-modify-write
```

## Lua Scripting -- True Atomicity With Logic

--> MULTI/EXEC queues commands blindly -- it cannot make a decision based on a value read mid-transaction (as the WATCH retry loop above has to work around). A Lua script sent via `EVAL` runs entirely, atomically, INSIDE Redis itself, with the full ability to read a value and branch on it, all as one indivisible unit no other client's command can interleave with.

```lua
-- A Lua script -- runs atomically inside Redis, can read AND branch on that read
-- before deciding what to write, something plain MULTI/EXEC cannot do
local current = tonumber(redis.call("GET", KEYS[1]) or "0")
if current < tonumber(ARGV[1]) then
  return redis.call("INCRBY", KEYS[1], ARGV[1])
else
  return -1
end
```

```python
script = r.register_script(lua_source)
result = script(keys=["rate_limit:user_42"], args=[100])
```

--> **Why this matters for something like rate limiting** -- "check the current count, and only increment it if still under the limit" is exactly the kind of read-then-conditionally-write logic that's genuinely unsafe as two separate Redis commands (another client's request could squeeze in between the check and the increment), and Lua scripting is the standard, production-grade way real Redis-based rate limiters (directly connecting to the Rate-Limiting Algorithms section of the API Design file) implement this safely and efficiently, in one round trip, entirely atomically.

# Eviction Policies -- What Happens When Redis Runs Out of Memory

--> The previous file notes TTL as Redis's mechanism for automatic expiry, but TTL alone doesn't answer what happens once Redis's configured memory limit (`maxmemory`) is actually reached and new writes keep coming in -- eviction policy is the setting that decides which existing keys get sacrificed to make room.

```bash
CONFIG SET maxmemory 100mb
CONFIG SET maxmemory-policy allkeys-lru
```

```
noeviction        -- refuses new writes once memory is full (returns an error) --
                      the safest choice for data that must NEVER be silently
                      dropped, but means the application must handle write
                      failures gracefully.

allkeys-lru        -- evicts the Least Recently Used key, among ALL keys,
                       regardless of whether it has a TTL set -- a good general
                       default for a pure cache, since "not accessed in a while"
                       is a reasonable proxy for "safe to evict."

volatile-lru       -- evicts the Least Recently Used key, but ONLY among keys
                       that HAVE a TTL set -- keys with no expiry are treated as
                       permanent and never evicted this way, useful when Redis
                       holds a MIX of genuinely permanent data and disposable
                       cached data in the same instance.

allkeys-lfu        -- evicts the Least FREQUENTLY Used key (tracks an approximate
                       access-frequency counter per key, not just recency) --
                       a better fit than LRU when a key accessed constantly until
                       recently, then not accessed for a short lull, shouldn't be
                       evicted just because of that one lull (LRU would evict it;
                       LFU, remembering it was frequently used overall, would not).

volatile-ttl        -- evicts the key with the SHORTEST remaining TTL first,
                        among keys that have one -- prioritizing keys already
                        closest to expiring naturally anyway.
```

--> **LRU vs LFU, concretely** -- imagine a key accessed 1000 times an hour, then not accessed for the last 5 minutes, versus a key accessed only once, 4 minutes ago. LRU (purely "when was it LAST touched") would evict the first key (touched 5 minutes ago, "less recent" than 4 minutes ago) despite it being far more generally valuable -- LFU correctly favors keeping the heavily-used key, since it weighs overall FREQUENCY, not just recency, exactly the scenario where LRU's simpler heuristic gives the wrong answer.
--> **Choosing a policy in practice** -- `allkeys-lru` is the sane default for a pure cache-aside deployment (from the previous file's Caching Patterns section), since evicting whatever hasn't been touched recently is a reasonable, cheap approximation of "least valuable to keep"; `noeviction` is correct when Redis holds data that isn't reproducible/re-fetchable from a primary source (e.g. it IS the primary store for some specific data), where silently losing a key to eviction would be a genuine data-loss bug, not just a cache miss.

# Redis Streams -- A Lighter Alternative to Kafka

--> The previous file's Pub/Sub explicitly flags its own key limitation -- a message published while no subscriber is connected is simply lost forever, with no persistence or replay. Redis Streams is Redis's answer to that gap -- an append-only, persistent log data structure, deliberately similar in spirit to Kafka's Topics/partitions (covered in the Message Queues file) but implemented as a native Redis data type, without needing a separate messaging system.

```bash
XADD orders * event OrderPlaced order_id 123 total 99.99
# * -- let Redis auto-generate a unique, time-ordered ID for this entry

XREAD COUNT 10 STREAMS orders 0
# Read from the beginning; unlike Pub/Sub, this DOES work even for
# messages published before this reader connected -- the log persists.

XGROUP CREATE orders analytics_service 0
XREADGROUP GROUP analytics_service consumer_1 COUNT 10 STREAMS orders >
XACK orders analytics_service 1234567890-0
```

--> **Consumer Groups within Streams** -- directly echoing Kafka's consumer group concept from the Message Queues file -- multiple consumers in the SAME group split the work of reading a stream, and `XACK` explicitly acknowledges a message as processed, letting Redis track exactly which messages a group has and hasn't yet confirmed, so an unacknowledged message (from a consumer that crashed mid-processing) can be claimed and retried by another consumer via `XCLAIM`.
--> **Why choose Streams over actually deploying Kafka** -- if a system already runs Redis (for caching/sessions), and its messaging needs are modest (moderate throughput, no need for Kafka's cross-datacenter replication, long-term retention policies, or a dedicated Schema Registry), Streams provides durable, replayable, consumer-group-based messaging WITHOUT the genuine operational overhead of standing up and running an entirely separate Kafka cluster -- a real and common "right-sized tool" trade-off, not simply a lesser substitute.
--> **Where Streams genuinely falls short of Kafka** -- Kafka's partitioning (Message Queues file) gives genuine horizontal read parallelism across many independent partitions and brokers, and Kafka is built from the ground up for very high sustained throughput with strong durability/replication guarantees across a cluster; a single Redis Stream doesn't partition the same way, and Redis overall remains fundamentally an in-memory-first system (the Persistence Options in the previous file) rather than a system designed around disk-backed durability as its primary guarantee -- Streams suit a genuinely lighter-weight need, not a full Kafka replacement at serious scale.

# Redis Sentinel vs Redis Cluster -- Two Different Problems

--> The previous file's Redis Cluster section names sharding/scaling in one line; it's worth being explicit that Sentinel and Cluster solve two GENUINELY DIFFERENT problems, and conflating them is a common source of confusion.

## Redis Sentinel -- High Availability via Failover

--> Sentinel solves "what happens if my ONE Redis primary goes down" -- it does NOT shard data; every node still holds the FULL dataset, just replicated.

```
Primary (holds full dataset, handles all writes)
   |
   +-- Replica 1 (full copy, read-only)
   +-- Replica 2 (full copy, read-only)

Sentinel processes continuously monitor the Primary's health.
If the Primary fails:
  Sentinels reach quorum ("yes, it's genuinely down, not just a network blip")
  --> automatically PROMOTE one Replica to become the new Primary
  --> reconfigure the other replicas to follow the new Primary
  --> notify connected clients of the new Primary's address
```

--> **What this buys you** -- automatic failover with minimal manual intervention when a primary node dies, keeping the SAME full dataset available on a promoted replica -- but it does nothing for capacity; every node, including every replica, must still hold the entire dataset in memory, so Sentinel alone doesn't help once your data genuinely exceeds what one machine's RAM can hold.

## Redis Cluster -- Horizontal Scaling via Sharding

--> Cluster solves the OPPOSITE problem -- "my dataset is too large (or my write throughput too high) for one node," by SHARDING data across multiple nodes, each holding only a SLICE of the total keyspace, directly the sharding concept referenced in the Database Advanced notes.

```
16384 hash slots, distributed across nodes:

Node A: slots 0-5460       (holds keys hashing into this range)
Node B: slots 5461-10922
Node C: slots 10923-16383

GET user:42   --> Redis computes CRC16("user:42") % 16384 to determine
                   which node actually owns that key, and routes accordingly

Each node in Cluster mode can ALSO have its own replica(s) for that
SHARD specifically -- combining sharding (Cluster's core job) with
per-shard failover (conceptually Sentinel's job, but built into Cluster
natively for each individual shard).
```

--> **What this buys you** -- a dataset far larger than any single machine's RAM, and write throughput distributed across multiple nodes instead of bottlenecked on one -- but at real added complexity (multi-key operations spanning DIFFERENT shards, like a MULTI/EXEC transaction or a Lua script touching keys on two different nodes, are restricted or unsupported, since Cluster can only guarantee atomicity within a single node/shard).

## Choosing Between Them

--> **Sentinel** -- the dataset comfortably fits on one machine, and the actual requirement is simply "don't go down if that one machine dies." **Cluster** -- the dataset (or required throughput) has genuinely outgrown what one machine can hold/handle, and horizontal capacity is the actual requirement, accepting Cluster's added complexity and its restrictions on cross-shard atomic operations as the necessary cost. Many production deployments that only need HA, not scale, correctly choose Sentinel specifically to AVOID Cluster's added operational complexity and cross-shard limitations they don't actually need.
