/**
 * 04_transactions_second_level_cache_demo.java
 *
 * Demonstrates, with illustrative Spring Data JPA / Hibernate code:
 *     1. @Transactional propagation -- REQUIRED (default) vs REQUIRES_NEW, showing how
 *        an audit log write can survive a rollback of the calling transaction
 *     2. @Version optimistic locking -- Hibernate's automatic version-checked UPDATE,
 *        and handling OptimisticLockException with a retry
 *     3. Pessimistic locking via @Lock(LockModeType.PESSIMISTIC_WRITE) -- a real
 *        database row lock (SELECT ... FOR UPDATE) held for the transaction's duration
 *     4. Second-level cache provider configuration (illustrated as properties +
 *        @Cacheable/@Cache annotations) -- shared ACROSS Sessions, unlike the
 *        first-level cache
 *
 * Covers Theory chapter:
 *     15) Hibernate and Advanced JPA/Theory/04 Transactions and Second-Level Cache.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run:
 *     - spring-boot-starter-data-jpa, spring-retry (for @Retryable)
 *     - a JDBC driver for your target database
 *     - a JCache/Caffeine (or Ehcache/Infinispan/Hazelcast) provider dependency to
 *       actually exercise the second-level cache configuration shown below
 *
 * Run (in a real Spring Boot project, after wiring this into src/main/java/...):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 */

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

// ---------------------------------------------------------------------------
// 1) Entities -- Account carries @Version for optimistic locking. Category
//    is marked @Cacheable for the second-level cache illustration (static,
//    widely-shared reference data -- a good candidate per Theory 04).
// ---------------------------------------------------------------------------

@Entity
@Table(name = "accounts")
class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal balance;

    // @Version -- Hibernate manages this ENTIRELY; never set it yourself.
    // Every UPDATE Hibernate generates for this entity includes the CURRENT
    // version in its WHERE clause and increments it in the SET clause:
    //     UPDATE accounts SET balance = ?, version = 6 WHERE id = ? AND version = 5
    // If zero rows are affected (someone else already bumped the version),
    // Hibernate throws OptimisticLockException -- a loud failure instead of
    // a silent lost update.
    @Version
    private Long version;

    protected Account() { }

    public Account(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public Long getVersion() { return version; }
}

// @Cacheable -- opt-in per entity, NOT on by default even with the second-level
// cache enabled globally (both are required together, see Theory 04's gotcha).
// READ_WRITE is the general-purpose concurrency strategy for mutable cached data;
// READ_ONLY would be even faster/safer here if Category truly never changes
// post-insert, but READ_WRITE is shown as the more broadly applicable default.
@Entity
@Table(name = "categories")
@Cacheable
@org.hibernate.annotations.Cache(usage = org.hibernate.annotations.CacheConcurrencyStrategy.READ_WRITE)
class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    protected Category() { }

    public Category(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
}

@Entity
@Table(name = "audit_entries")
class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String message;

    protected AuditEntry() { }

    public AuditEntry(String message) { this.message = message; }

    public Long getId() { return id; }
    public String getMessage() { return message; }
}

// ---------------------------------------------------------------------------
// 2) Repositories -- AccountRepository adds a PESSIMISTIC_WRITE-locked lookup
//    alongside the plain optimistic-by-default findById().
// ---------------------------------------------------------------------------

interface AccountRepository extends JpaRepository<Account, Long> {

    // Issues "SELECT ... FOR UPDATE" -- acquires an exclusive row lock for
    // this transaction's duration. Any other transaction trying to lock or
    // update the same row BLOCKS until this transaction commits or rolls back.
    // Reserved for genuinely high-contention hotspots (Theory 04) -- not a default.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}

interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> { }

interface CategoryRepository extends JpaRepository<Category, Long> { }

// ---------------------------------------------------------------------------
// 3) Propagation demo -- REQUIRED (default, joins the caller's transaction)
//    vs REQUIRES_NEW (always its own independent transaction). The audit log
//    entry written via REQUIRES_NEW survives even if the OUTER transaction
//    later rolls back.
// ---------------------------------------------------------------------------

@Service
class AuditLogService {

    private final AuditEntryRepository auditEntryRepository;

    public AuditLogService(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    // REQUIRES_NEW -- suspends any existing transaction, starts an independent
    // new one, commits or rolls back on its OWN, then resumes the original.
    // Called from a DIFFERENT bean (AccountTransferService below) so the
    // propagation actually applies -- a self-invocation (this.logAttempt(...))
    // would bypass the Spring proxy and this setting entirely.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAttempt(String message) {
        auditEntryRepository.save(new AuditEntry(message));
        // Commits HERE, independently, regardless of what the caller does next.
    }
}

@Service
class AccountTransferService {

    private final AccountRepository accountRepository;
    private final AuditLogService auditLogService;   // a DIFFERENT bean -- required for REQUIRES_NEW to take effect

    public AccountTransferService(AccountRepository accountRepository, AuditLogService auditLogService) {
        this.accountRepository = accountRepository;
        this.auditLogService = auditLogService;
    }

    // Default propagation = REQUIRED -- this method either starts a new
    // transaction or joins an existing one.
    @Transactional
    public void withdraw(Long accountId, BigDecimal amount) {
        // logAttempt() runs in its OWN REQUIRES_NEW transaction -- it commits
        // independently of whatever happens below.
        auditLogService.logAttempt("Withdrawal attempted: account=" + accountId + " amount=" + amount);

        Account account = accountRepository.findById(accountId).orElseThrow();

        if (account.getBalance().compareTo(amount) < 0) {
            // Rolls back THIS transaction's own work (none yet, in this simple
            // example) -- but the audit log entry above ALREADY committed and
            // stays, because REQUIRES_NEW made it an independent transaction.
            throw new IllegalStateException("Insufficient funds for account " + accountId);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        // If another transaction updated (and version-bumped) this same row
        // between our read and this flush, OptimisticLockException is thrown
        // HERE at flush/commit time -- never a silent lost update (see below).
    }

    // -------------------------------------------------------------------
    // OPTIMISTIC LOCKING -- @Retryable re-invokes the whole method on
    // OptimisticLockException, up to 3 attempts, rather than swallowing
    // the conflict or letting it silently overwrite someone else's change.
    // -------------------------------------------------------------------
    @Retryable(value = OptimisticLockException.class, maxAttempts = 3)
    @Transactional
    public void withdrawWithRetry(Long accountId, BigDecimal amount) {
        withdraw(accountId, amount);
    }

    // -------------------------------------------------------------------
    // PESSIMISTIC LOCKING -- proactively locks the row at READ time via
    // SELECT ... FOR UPDATE, so a concurrent transaction attempting the same
    // lock BLOCKS until this one commits/rolls back. Reserved for genuinely
    // high-contention hotspots (Theory 04); overusing this "to be safe"
    // serializes transactions and reduces throughput.
    // -------------------------------------------------------------------
    @Transactional
    public void withdrawWithPessimisticLock(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findByIdForUpdate(accountId).orElseThrow();
        // No other transaction can concurrently read-for-update or write this
        // same row until THIS transaction ends -- no OptimisticLockException
        // is possible here because no concurrent writer can even get in.
        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds for account " + accountId);
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
    }
}

// ---------------------------------------------------------------------------
// 4) Second-level cache provider configuration -- illustrated as the
// application.properties block a real Spring Boot project would add (shown
// as a comment, since properties files can't live inside a .java file), plus
// the @Cacheable/@Cache annotations already applied to Category above.
// ---------------------------------------------------------------------------

/*
# application.properties -- enabling the second-level cache via a JCache
# (JSR-107) provider backed by Caffeine. Both this GLOBAL enablement AND the
# per-entity @Cacheable annotation (see Category above) are required together
# -- @Cacheable alone, without this config, has no effect (Theory 04's gotcha).

spring.jpa.properties.hibernate.cache.use_second_level_cache=true
spring.jpa.properties.hibernate.cache.region.factory_class=org.hibernate.cache.jcache.JCacheRegionFactory
spring.jpa.properties.hibernate.javax.cache.provider=com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider

# Optional: also cache QUERY RESULT SETS (lists of entity IDs), not just
# individual entities by ID -- a separate opt-in layer on top of entity caching.
spring.jpa.properties.hibernate.cache.use_query_cache=true

# Enable Hibernate statistics to observe cache hit/miss ratios in logs/JMX --
# also doubles as one of the systematic N+1 detection techniques from Theory 03.
spring.jpa.properties.hibernate.generate_statistics=true
*/

/*
 * Reminder on WHAT belongs in the second-level cache (from Theory 04):
 * good candidates are relatively static, frequently-read, rarely-written
 * reference/lookup data (Category, as modeled above) -- POOR candidates are
 * frequently-updated, per-user, or highly transactional data (an Account's
 * live balance, as modeled above) -- caching an Account risks serving a
 * stale balance across requests and defeats the whole point of the
 * optimistic/pessimistic locking demonstrated in this same file. Account is
 * therefore deliberately NOT marked @Cacheable here.
 */

/*
 * NOTE on annotations used above:
 * This snippet uses real JPA (jakarta.persistence.*), Hibernate-native caching
 * annotations (org.hibernate.annotations.Cache/CacheConcurrencyStrategy), and Spring
 * (org.springframework.transaction.annotation.*, org.springframework.retry.annotation.*)
 * annotations exactly as they'd appear in a real Spring Boot project. Exercising the
 * second-level cache requires a JCache/Caffeine (or Ehcache/Infinispan/Hazelcast)
 * dependency and the properties shown above; exercising @Retryable requires the
 * spring-retry dependency plus @EnableRetry on a configuration class -- both
 * intentionally omitted here since this file is illustrative only (see header).
 */
