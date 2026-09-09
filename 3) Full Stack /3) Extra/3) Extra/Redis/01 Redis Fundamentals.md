# What Redis Is

--> Redis (REmote DIctionary Server) is an in-memory data store -- unlike a traditional database (PostgreSQL, MongoDB) that primarily reads/writes from disk, Redis keeps data in RAM, making reads/writes extremely fast (sub-millisecond), at the cost of being more expensive per GB and needing deliberate persistence configuration to survive a restart.
--> Most commonly used as a cache sitting in FRONT of a slower primary database, but also directly usable as a session store, a message broker (Pub/Sub), and a rate-limiter/counter store.

# Core Data Structures

--> Strings -- the simplest type, can hold text, numbers, or serialized JSON.
--> Hashes -- a field-value map, like a mini-object, useful for storing a record without needing a separate key per field.
--> Lists -- ordered collections, efficient push/pop from both ends -- commonly used as a simple queue.
--> Sets -- unordered, unique collections -- efficient membership checks and set operations (union/intersection).
--> Sorted Sets -- like a Set, but every member has an associated score, kept automatically sorted by that score -- ideal for leaderboards, rate limiting windows, and priority queues.

```bash
SET user:1:name "Alice"
GET user:1:name

HSET user:1 name "Alice" age "30"
HGET user:1 name

LPUSH recent_orders "order:123"
LRANGE recent_orders 0 9        # Get the 10 most recent

SADD online_users "user:1" "user:2"
SISMEMBER online_users "user:1"   # 1 (true)

ZADD leaderboard 1500 "player:1"
ZADD leaderboard 2200 "player:2"
ZRANGE leaderboard 0 -1 WITHSCORES REV   # Highest score first
```

# TTL -- Automatic Expiry

--> Any key can be given a Time To Live -- after which Redis automatically deletes it, no manual cleanup job needed. This is the mechanism behind most Redis caching and session-store use cases.

```bash
SET session:abc123 "user_data" EX 3600   # Expires in 1 hour
TTL session:abc123                        # Check remaining seconds
EXPIRE user:1:name 300                     # Set/reset expiry on an existing key
```

# Caching Patterns

--> Cache-Aside (lazy loading) -- application code checks Redis first; on a miss, reads from the primary database, then writes the result into Redis for next time.

```python
import redis
r = redis.Redis()

def get_user(user_id):
    cached = r.get(f"user:{user_id}")
    if cached:
        return json.loads(cached)          # Cache hit -- fast path

    user = db.query_user(user_id)           # Cache miss -- slow path
    r.set(f"user:{user_id}", json.dumps(user), ex=300)
    return user
```

--> Write-Through -- every write goes to Redis AND the primary database at the same time, keeping the cache always fresh at the cost of slightly slower writes.
--> Cache invalidation (deciding when cached data is stale and must be refreshed/removed) is famously one of the harder problems in caching -- a TTL-based expiry (shown above) is the simplest, most common practical compromise between correctness and complexity.

# Pub/Sub -- Simple Real-Time Messaging

--> Redis supports Publish/Subscribe messaging -- a publisher sends a message to a named channel, and every currently-subscribed client receives it instantly.

```python
# Publisher
r.publish("notifications", "New order received")

# Subscriber
pubsub = r.pubsub()
pubsub.subscribe("notifications")
for message in pubsub.listen():
    print(message)
```

--> Unlike a real message queue (Kafka/RabbitMQ, covered separately), Redis Pub/Sub messages are NOT persisted or queued -- a subscriber that isn't connected at publish time simply misses that message. Fine for ephemeral real-time notifications; not a substitute for a durable message queue.

# Persistence Options

--> RDB (snapshotting) -- periodically saves the entire dataset to disk -- fast to restore, but can lose the last few minutes of writes if Redis crashes right before a snapshot.
--> AOF (Append-Only File) -- logs every write operation -- more durable, can be configured to lose almost no data, at the cost of larger files and slightly slower writes.
--> Many production deployments use both together, or accept that Redis holding purely disposable/re-derivable cache data doesn't need strong persistence guarantees at all.

# Redis Cluster -- Scaling Beyond One Server

--> Redis Cluster shards data automatically across multiple nodes (conceptually similar to the sharding covered in the Database Advanced notes), letting Redis scale beyond what a single server's RAM can hold, while also providing replication-based failover.
