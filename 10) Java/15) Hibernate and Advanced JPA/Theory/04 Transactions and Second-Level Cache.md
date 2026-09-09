# Beyond `@Transactional` Basics -- What's New Here

--> The Spring Data JPA chapter covers `@Transactional`, default rollback rules, `readOnly`, and a brief mention of propagation. This chapter goes deep on propagation's actual runtime behavior, the concurrency problem transactions alone don't solve (optimistic vs pessimistic locking), `@Version`, and the SECOND-level cache -- a topic not touched at all in that chapter.

# Transaction Propagation -- What Actually Happens at Each Boundary

--> **Propagation** governs what happens when a `@Transactional` method is called from code that is ALREADY inside a transaction. Spring's proxy checks the current thread's transactional context and decides, per propagation setting, whether to join it, suspend it, or reject the call.

| Propagation | Existing transaction present | No existing transaction |
|---|---|---|
| `REQUIRED` (default) | Joins it -- becomes part of the same transaction | Starts a new one |
| `REQUIRES_NEW` | Suspends the existing one, starts an independent new one, resumes the original after | Starts a new one (same as REQUIRED here) |
| `NESTED` | Starts a SAVEPOINT within the existing transaction (can roll back to it without rolling back the whole thing) | Starts a new one (same as REQUIRED here) |
| `SUPPORTS` | Joins it | Runs non-transactionally |
| `NOT_SUPPORTED` | Suspends it, runs non-transactionally, resumes after | Runs non-transactionally |
| `MANDATORY` | Joins it | **Throws** `IllegalTransactionStateException` |
| `NEVER` | **Throws** `IllegalTransactionStateException` | Runs non-transactionally |

--> **`REQUIRED` vs `REQUIRES_NEW` -- the practically important distinction:** with `REQUIRED`, if the OUTER transaction later rolls back, everything the inner call did rolls back too (they're the same transaction). With `REQUIRES_NEW`, the inner call's work is committed or rolled back INDEPENDENTLY -- if the outer transaction later fails and rolls back, the inner `REQUIRES_NEW` work that already committed STAYS committed.

```java
@Service
class OrderService {
    private final AuditLogService auditLogService;   // a DIFFERENT bean -- required for the proxy to apply (see below)

    @Transactional
    public void placeOrder(OrderRequest request) {
        auditLogService.logAttempt(request);          // REQUIRES_NEW -- commits independently

        Order order = new Order(request);
        orderRepository.save(order);

        if (inventoryIsInsufficient(request)) {
            throw new InsufficientStockException();    // rolls back the Order save...
            // ...but the audit log entry from logAttempt() ALREADY COMMITTED and stays,
            // because it ran in its own REQUIRES_NEW transaction, independent of this one.
        }
    }
}

@Service
class AuditLogService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAttempt(OrderRequest request) {
        auditRepository.save(new AuditEntry(request));   // always persisted, regardless of the caller's outcome
    }
}
```

--> **`NESTED` vs `REQUIRES_NEW`** -- `NESTED` uses a database SAVEPOINT within the SAME physical transaction/connection: if the nested unit fails, only IT rolls back (to the savepoint), while the outer transaction can continue and still ultimately commit everything else. `REQUIRES_NEW` uses a genuinely separate physical transaction (its own connection, its own commit/rollback) -- more isolated, but also means the inner work can commit even if the outer one later fails, which `NESTED` does not allow (a savepoint rollback in the outer failure would also discard the nested work if the whole outer transaction rolls back). `NESTED` requires the underlying JDBC driver/database to support savepoints; not all combinations do.
--> **Recall the proxy mechanism from the Spring Data chapter** -- propagation only takes effect on calls arriving THROUGH the Spring-managed proxy, i.e. from a DIFFERENT bean. Calling `this.logAttempt(...)` from within the same class bypasses the proxy and propagation settings entirely, same as any other `@Transactional` self-invocation pitfall.

# Optimistic vs Pessimistic Locking -- The Problem Transactions Alone Don't Solve

--> A database transaction guarantees atomicity/consistency for the operations INSIDE it, but does not by itself prevent a "lost update" -- two concurrent transactions each reading the same row, each computing a change based on that stale read, and the second one's `UPDATE` silently overwriting the first's, with no error raised.

```text
Time -->
T1: SELECT balance FROM accounts WHERE id=1   (reads 100)
T2:                                            SELECT balance FROM accounts WHERE id=1  (also reads 100)
T1: UPDATE accounts SET balance = 100 - 30     (commits: balance = 70)
T2:                                            UPDATE accounts SET balance = 100 - 50    (commits: balance = 50)
-- Both withdrawals should have applied (100 -> 70 -> 20), but T2 overwrote T1's
-- result because it computed its update from a STALE read. Final balance is
-- wrong (50 instead of 20), and NEITHER transaction failed or complained.
```

--> **Optimistic locking** assumes conflicts are RARE -- it doesn't block anyone from reading, but detects a conflict at WRITE time by checking whether the row changed since it was read, and fails loudly if so (leaving retry/conflict-handling to the application). This is JPA's built-in, `@Version`-based mechanism (below).
--> **Pessimistic locking** assumes conflicts are COMMON ENOUGH to prevent proactively -- it acquires an actual database-level lock on the row(s) at READ time, blocking (or immediately failing) any other transaction trying to read/write the same row until the lock is released.

| | Optimistic | Pessimistic |
|---|---|---|
| Mechanism | A version column, checked at UPDATE time | A real database row lock (`SELECT ... FOR UPDATE`), held for the transaction's duration |
| Blocks other readers? | No -- everyone can read freely | Yes -- other transactions requesting the same lock block or fail |
| Conflict detected | At commit/flush time -- as an exception | Proactively -- the second transaction can't even proceed until the first releases the lock |
| Best for | Low-contention data, most typical web app CRUD | High-contention hotspots (e.g. a single shared counter, inventory decrement under heavy concurrent load) |
| Cost | Occasional retries needed on genuine conflict | Reduced concurrency/throughput -- transactions serialize around the lock |

# `@Version` -- Optimistic Locking in Practice

--> Add a `@Version` field to any entity you want optimistic locking on. Hibernate manages it entirely -- you never set it yourself.

```java
@Entity
class Account {
    @Id @GeneratedValue private Long id;

    private BigDecimal balance;

    @Version                       // Hibernate increments this automatically on every UPDATE
    private Long version;          // starts at 0 when first persisted
}
```

--> **What Hibernate actually does with it** -- every `UPDATE` statement Hibernate generates for a `@Version`-annotated entity includes the CURRENT version in its `WHERE` clause, and sets the new (incremented) version in the `SET` clause:

```sql
UPDATE accounts SET balance = ?, version = 6 WHERE id = ? AND version = 5
```

--> **If ZERO rows are affected** by that `UPDATE` (because some other transaction already changed the row and bumped its version past what THIS transaction read), Hibernate detects the row-count mismatch and throws `OptimisticLockException` (JPA) / `StaleObjectStateException` (Hibernate-native) -- the second writer's transaction fails LOUDLY instead of silently overwriting the first's committed change.

```java
@Transactional
public void withdraw(Long accountId, BigDecimal amount) {
    Account account = accountRepository.findById(accountId).orElseThrow();
    account.setBalance(account.getBalance().subtract(amount));
    accountRepository.save(account);
    // If another transaction updated (and version-bumped) this same row between
    // our read and this flush, an OptimisticLockException is thrown HERE at
    // flush/commit time -- never a silent lost update.
}
```

--> **Handling the exception -- typically a retry loop or a user-facing "please retry" response**, not something to swallow silently:

```java
@Retryable(value = OptimisticLockException.class, maxAttempts = 3)
@Transactional
public void withdrawWithRetry(Long accountId, BigDecimal amount) {
    withdraw(accountId, amount);   // Spring Retry re-invokes this whole method on conflict, up to 3 times
}
```

# Pessimistic Locking in Practice

--> JPA exposes pessimistic locking via `LockModeType`, either on `EntityManager.find()` directly or via a repository method annotated `@Lock`:

```java
interface AccountRepository extends JpaRepository<Account, Long> {

    // Issues SELECT ... FOR UPDATE -- acquires an exclusive row lock for this transaction's
    // duration. Any other transaction trying to SELECT ... FOR UPDATE (or update) the same
    // row BLOCKS until this transaction commits or rolls back.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
```

| `LockModeType` | Behavior |
|---|---|
| `PESSIMISTIC_READ` | Shared lock -- other readers with `PESSIMISTIC_READ` are fine, but writers block |
| `PESSIMISTIC_WRITE` | Exclusive lock -- blocks all other locking readers AND writers on that row |
| `PESSIMISTIC_FORCE_INCREMENT` | Exclusive lock, AND forces a `@Version` bump even if no field actually changed -- signals "I touched this" to other optimistic readers |
| `OPTIMISTIC` | JPA-level check that the version hasn't changed at COMMIT time (a lighter-weight, spec-defined alternative to relying purely on `@Version`'s automatic UPDATE-clause behavior) |
| `OPTIMISTIC_FORCE_INCREMENT` | Same, and also forces a version bump on commit |

--> **Deadlock risk with pessimistic locking** -- if two transactions each hold a lock the other needs and each waits on the other, the database detects the cycle and forcibly aborts one of them with a deadlock error. Minimizing this means always acquiring locks on multiple rows in a CONSISTENT ORDER across your whole codebase (e.g. always lock by ascending ID) so two transactions never wait on each other in opposite directions.

# Second-Level Cache -- Concept and Providers

--> Recall the first-level cache (Theory 01) is per-Session and disappears when the Session closes. The **second-level cache** sits at the `SessionFactory` level -- shared ACROSS Sessions/transactions, and can persist entity data in memory (or a distributed cache) well beyond any single unit of work.

```text
First-level cache   -->  per Session/EntityManager, always on, cannot be disabled, gone when Session closes
Second-level cache  -->  per SessionFactory, OPT-IN, shared across ALL Sessions, survives individual transactions
Query cache         -->  an optional additional layer caching QUERY RESULT SETS (lists of entity IDs), keyed by query+params
```

--> **Why it needs a separate provider** -- unlike the first-level cache (just a `Map` Hibernate manages internally), the second-level cache needs its own storage/eviction/concurrency implementation, so Hibernate delegates to a pluggable caching PROVIDER rather than building one in.

| Provider | Notes |
|---|---|
| **Ehcache** | The traditional, most common default pairing with Hibernate; in-JVM by default, supports disk overflow |
| **Infinispan** | JBoss/Red Hat's distributed data grid -- suited to clustered/multi-node deployments needing a shared cache across instances |
| **Caffeine** | A modern, high-performance in-JVM cache; commonly wired in via `hibernate-jcache` + a JCache (JSR-107) provider |
| **Hazelcast** | Another distributed in-memory data grid option, similar niche to Infinispan |

--> **Enabling it (conceptually)** -- requires the provider's dependency, enabling the second-level cache in Hibernate config, and marking specific entities `@Cacheable` with a concurrency strategy:

```properties
spring.jpa.properties.hibernate.cache.use_second_level_cache=true
spring.jpa.properties.hibernate.cache.region.factory_class=org.hibernate.cache.jcache.JCacheRegionFactory
spring.jpa.properties.hibernate.javax.cache.provider=com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider
```

```java
@Entity
@Cacheable                                                        // opt-in, per entity -- NOT on by default even with the cache enabled globally
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
class Category {
    @Id @GeneratedValue private Long id;
    private String name;
}
```

| `CacheConcurrencyStrategy` | Meaning |
|---|---|
| `READ_ONLY` | Fastest, safest -- for data that never changes after insert (e.g. lookup/reference tables) |
| `READ_WRITE` | Supports updates safely via a "soft lock" during writes -- the general-purpose default for mutable cached data |
| `NONSTRICT_READ_WRITE` | Slight risk of briefly stale reads after a concurrent update -- trades a little consistency for less locking overhead |
| `TRANSACTIONAL` | Fully transactional cache consistency -- requires a JTA-aware cache provider, rarely needed outside distributed-transaction environments |

--> **What belongs in the second-level cache, and what doesn't** -- good candidates are relatively static, frequently-read, rarely-written reference/lookup data (categories, countries, product types) shared identically across many requests. POOR candidates are frequently-updated, per-user, or highly transactional data (an account balance, an order's live status) -- caching that risks serving stale data or adds cache-invalidation complexity disproportionate to the benefit. **When in doubt, measure before enabling** -- the second-level cache adds real complexity (cache invalidation across a cluster, memory overhead, staleness windows) and is not a default-on "make everything faster" switch.

# Common Gotchas

--> **Assuming `@Transactional` alone prevents lost updates under concurrency** -- it guarantees atomicity of the transaction's own operations, not isolation from concurrent transactions reading/writing the same row; that requires explicit optimistic (`@Version`) or pessimistic locking.
--> **Catching and swallowing `OptimisticLockException` instead of retrying or surfacing it** -- the whole point of optimistic locking is to make conflicts visible; silently ignoring the exception just reintroduces the lost-update bug it was meant to prevent.
--> **Overusing pessimistic locking "to be safe"** -- it serializes transactions around the locked rows, directly reducing throughput; reach for it only on genuinely high-contention hotspots, not by default.
--> **Locking rows in inconsistent orders across different code paths** -- a common source of database deadlocks under pessimistic locking; always acquire multi-row locks in a fixed, consistent order.
--> **Enabling the second-level cache for volatile, per-user, or transactional data** -- risks serving stale reads and adds invalidation complexity for little benefit; it's best suited to relatively static, widely-shared reference data.
--> **Assuming `@Cacheable` alone is enough** -- it also requires the second-level cache to be enabled globally AND a caching provider configured; without both, `@Cacheable` has no effect.

# Best Practices Summary

--> Reach for `REQUIRES_NEW` deliberately (audit logs, notifications that must survive a rollback) -- not as a default, since it means the inner work escapes the outer transaction's atomicity guarantee.
--> Default to optimistic locking (`@Version`) for most entities; reserve pessimistic locking for specific, measured high-contention hotspots.
--> Always have a retry (or clear user-facing failure) strategy for `OptimisticLockException` -- never silently swallow it.
--> Keep multi-row lock acquisition order consistent across the codebase to avoid deadlocks under pessimistic locking.
--> Treat the second-level cache as an opt-in optimization for specific, measured, largely-static reference data -- not a blanket performance switch, and always weigh the added cache-invalidation complexity against the measured benefit.
--> Remember propagation only applies through the Spring proxy -- calls via `this.` inside the same bean bypass propagation settings entirely, same as any other `@Transactional` self-invocation pitfall.
