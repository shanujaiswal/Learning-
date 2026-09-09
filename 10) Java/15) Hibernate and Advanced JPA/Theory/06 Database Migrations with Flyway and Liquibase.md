# Why Schema Changes Need Their Own Tooling

--> Hibernate's `ddl-auto=update` (or `hibernate.hbm2ddl.auto`) is convenient in local development -- it inspects your `@Entity` classes and tries to evolve the schema to match -- but it is explicitly **unsafe for any environment where the database holds real data**: it can silently drop columns it thinks are unused, doesn't handle data transformations (renaming a column loses the old column's data rather than migrating it), can't be reviewed in a pull request the way code can, and gives every environment (dev/staging/prod) a schema that evolved independently rather than through the same, auditable sequence of steps.
--> **Migration tools** (Flyway, Liquibase) solve this by treating schema changes as **versioned, ordered, immutable scripts** -- once a migration has run in any environment, it is never edited again; a NEW migration is written instead. This gives you: a full audit trail of every schema change ever made, the SAME sequence of scripts applied identically to dev/staging/prod (no more "works on my machine" schema drift), safe rollback strategies, and a natural fit into CI/CD (the migration runs automatically as part of deployment, before the new application code that depends on the new schema goes live).

```text
Without a migration tool                         With a migration tool
------------------------------------------------ -------------------------------------------------
ddl-auto=update guesses the DDL from entities     Explicit, hand-written SQL/changelog per change
Schema drift between dev/staging/prod is common   Every environment applies the SAME script history
No audit trail of "what changed and why"          Full versioned history, reviewable in git/PRs
Renaming a column loses data                       Migration can explicitly ALTER + backfill safely
Risky/disabled in production                       Designed FOR production from the start
```

# Flyway -- Convention-Based, SQL-First Migrations

--> **Flyway's philosophy**: migrations are plain `.sql` files (or Java-based migrations for logic too complex for SQL alone), named following a strict convention, placed in a known folder (`src/main/resources/db/migration` by default in a Spring Boot project), and applied **in order, exactly once each**, tracked via a `flyway_schema_history` table Flyway creates and manages in the target database itself.

```text
Naming convention:  V<version>__<description>.sql
                     |          |
                     |          +-- Description, spaces become underscores in the filename
                     +-- Version number, must sort correctly (1, 2, 3... or 1.1, 1.2...)

Examples:
  V1__create_products_table.sql
  V2__create_categories_table.sql
  V3__add_discontinued_column_to_products.sql
  V4__backfill_default_category.sql
```

```sql
-- V1__create_products_table.sql
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    category_id BIGINT
);

-- V3__add_discontinued_column_to_products.sql
ALTER TABLE products ADD COLUMN discontinued BOOLEAN NOT NULL DEFAULT FALSE;
```

--> **Spring Boot integration is close to zero-config**: add `flyway-core` (plus the relevant JDBC driver) as a dependency, drop `.sql` files in `db/migration`, and Flyway runs automatically on application startup, BEFORE Hibernate touches the schema at all -- which is why `ddl-auto` should be set to `validate` (or `none`) once Flyway is in play: Flyway now OWNS the schema, and Hibernate's job is only to confirm its entity mappings still match what Flyway created, never to alter the schema itself.

```yaml
# application.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate     # Hibernate checks the schema matches entities -- never mutates it
  flyway:
    enabled: true
    locations: classpath:db/migration
```

--> **The `flyway_schema_history` table** -- Flyway creates this itself; each row records a migration's version, description, a checksum of the script's content, when it ran, and whether it succeeded. **The checksum is what makes migrations immutable in practice**: if you edit an already-applied `V3__...sql` file, Flyway detects the checksum mismatch on the next startup and refuses to proceed (by default) -- protecting against the exact "environments end up on different actual schemas because someone edited history" problem migrations exist to prevent.
--> **Repair and out-of-order migrations** -- `flyway repair` re-calculates checksums for a history table that's fallen out of sync (e.g. after a deliberately-approved edit); `out-of-order = true` (used sparingly) lets a lower-numbered migration apply after a higher one already has, useful when two feature branches each add their own migration and merge in an order that doesn't match version numbers.

# Liquibase -- Changelog-Based, Format-Flexible Migrations

--> **Liquibase's philosophy** differs in one key way: instead of raw SQL files, Liquibase migrations are typically written as **changesets** in a structured format (XML, YAML, JSON, or SQL) collected into a **changelog**, where each changeset describes a change in a database-agnostic way that Liquibase can translate into the correct SQL for whichever database it's actually running against.

```yaml
# db/changelog/changelog-master.yaml
databaseChangeLog:
  - changeSet:
      id: 1
      author: vanisha
      changes:
        - createTable:
            tableName: products
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
              - column:
                  name: name
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: price
                  type: NUMERIC(10,2)
                  constraints:
                    nullable: false

  - changeSet:
      id: 2
      author: vanisha
      changes:
        - addColumn:
            tableName: products
            columns:
              - column:
                  name: discontinued
                  type: BOOLEAN
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
```

--> **Each changeset is tracked by `id` + `author` + the changelog file it lives in** (not a sequential version number the way Flyway uses filenames) -- Liquibase records applied changesets in a `DATABASECHANGELOG` table, and (like Flyway) computes a checksum per changeset to detect edits to already-applied history.
--> **Rollback support is a first-class, built-in concept** -- many changeset types (like `createTable`, `addColumn`) have an OBVIOUS automatic rollback Liquibase can infer (`dropTable`, `dropColumn`); for changes where the reverse isn't obvious, you write an explicit `rollback:` block yourself:

```yaml
  - changeSet:
      id: 3
      author: vanisha
      changes:
        - sql:
            sql: UPDATE products SET category_id = 1 WHERE category_id IS NULL
      rollback:
        - sql:
            sql: UPDATE products SET category_id = NULL WHERE category_id = 1
```

```yaml
# application.yml -- Spring Boot integration via the liquibase-core dependency
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/changelog-master.yaml
  jpa:
    hibernate:
      ddl-auto: validate     # same principle as Flyway -- Liquibase owns the schema
```

# Flyway vs Liquibase -- Choosing Between Them

| Dimension | Flyway | Liquibase |
|---|---|---|
| Migration format | Plain SQL (primarily), or Java for complex logic | XML/YAML/JSON (primarily), or plain SQL |
| Ordering | Strict version-number-in-filename sequence | Changeset id + author + changelog file, more flexible |
| Rollback | Manual (write an "undo" migration yourself) -- Community edition has no auto-rollback | Many changeset types can auto-generate a rollback; explicit rollback blocks for the rest |
| Database portability | You write dialect-specific SQL yourself | Changelog format can be dialect-agnostic; Liquibase generates the target SQL |
| Learning curve | Lower -- it's "just SQL files, numbered" | Higher -- an extra changelog format/DSL to learn |
| Best fit | Teams comfortable writing raw SQL, want minimal abstraction | Teams wanting DB-agnostic changelogs, built-in rollback, or multi-DB-vendor support |

--> **In practice**, both are mature, well-supported tools that solve the same core problem; the choice often comes down to team preference (SQL-first vs changelog-DSL-first) rather than one being objectively superior. Many Spring Boot shops default to Flyway simply because writing raw SQL is a skill every backend developer already has, while Liquibase's rollback and multi-format flexibility appeal to teams managing multiple database vendors or wanting migration history reviewable independent of SQL dialect knowledge.

# Migration Strategy in CI/CD

--> **The standard pattern**: migrations run as an explicit step in the deployment pipeline, BEFORE the new application version is rolled out to serve traffic -- never "whenever the app happens to start," which risks multiple app instances racing to apply the same migration concurrently during a rolling deployment.

```text
Typical CI/CD migration flow:
  1. New code (with new migration files) merges to main
  2. CI pipeline builds the application
  3. A dedicated migration step runs FIRST, against the target environment's database,
     using the SAME Flyway/Liquibase CLI (or a Maven/Gradle plugin) the app itself would use
  4. Only if migrations succeed does the pipeline proceed to deploy the new app version
  5. New app version starts with ddl-auto=validate, confirming the schema Flyway/Liquibase
     already applied actually matches what the entities expect
```

--> **Backward-compatible migrations for zero-downtime deploys** -- during a rolling deployment, OLD and NEW application code may briefly run simultaneously against the SAME database. A migration that drops a column the old code still reads, or renames a column the old code still writes by its old name, breaks the old instances before they've fully drained. The safe pattern is the **expand-contract** approach: (1) *expand* -- add the new column/table alongside the old one, deploy app code that writes to BOTH; (2) *migrate* -- backfill data, deploy app code that reads from the new column exclusively; (3) *contract* -- once no code references the old column anymore, drop it in a LATER migration. Each step is its own safe, backward-compatible migration rather than one big destructive change.
--> **Environment-specific data vs schema** -- migrations should manage SCHEMA (tables, columns, indexes, constraints) and, where genuinely needed, small reference/seed data (e.g. a fixed set of `role` rows); they should NOT be used to load bulk test fixtures or environment-specific business data, which belongs in separate seeding scripts or test setup, not the permanent migration history every environment replays forever.

# Common Gotchas

--> **Editing an already-applied migration file** -- both tools detect this via checksum mismatch and refuse to proceed (by default); the fix is always a NEW migration, never editing history, even for a "tiny typo fix."
--> **Relying on `ddl-auto=update`/`create` in any shared environment** -- fine for a solo local sandbox, actively dangerous anywhere else; pair Flyway/Liquibase with `ddl-auto=validate` so Hibernate only checks, never mutates.
--> **A destructive migration (DROP COLUMN/TABLE) that breaks old app instances still running during a rolling deploy** -- use the expand-contract pattern across multiple migrations instead of one destructive step.
--> **Migrations that only work on the developer's local database engine** -- an H2-flavored SQL script silently failing against production PostgreSQL is a classic surprise; test migrations against the SAME database engine/version production actually uses.
--> **Forgetting a `countQuery`-equivalent problem for migrations**: not applicable here, but the analogous mistake is forgetting to test a migration against a database that already has real data in it -- a migration that works on an empty test database can still fail against production-sized, non-null-constrained existing rows.
--> **Multiple app instances racing to apply migrations concurrently** -- both tools use a database-level lock to serialize migration application, but this only helps if migrations are actually triggered as part of controlled deployment rather than happening to run inside every instance's own startup in an uncoordinated rolling deploy.

# Best Practices Summary

--> Treat schema migrations as versioned, reviewable, immutable code -- never edit an already-applied migration; write a new one.
--> Pair Flyway or Liquibase with `ddl-auto=validate` (never `update`/`create`) in every shared environment, letting the migration tool own the schema entirely.
--> Run migrations as an explicit CI/CD pipeline step before the new application version receives traffic, not implicitly on each instance's own startup during a rolling deploy.
--> Use the expand-contract pattern for any destructive or renaming change, so old and new app code can coexist safely during a rolling deployment.
--> Keep migrations focused on schema (and minimal fixed reference data) -- never bulk business data or environment-specific fixtures.
--> Test migrations against the same database engine and a realistically-populated dataset, not just an empty local database.
--> Choose Flyway for SQL-first simplicity, Liquibase for changelog portability and built-in rollback support -- both are legitimate, well-supported choices.
