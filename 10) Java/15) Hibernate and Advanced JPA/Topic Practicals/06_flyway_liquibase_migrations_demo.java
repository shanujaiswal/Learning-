/**
 * 06_flyway_liquibase_migrations_demo.java
 *
 * Demonstrates, with illustrative migration content and a small Java startup-check
 * class:
 *     1. Flyway migration file content (V1__..., V2__..., V3__...) shown as string/
 *        comment blocks, following the strict V<version>__<description>.sql convention
 *     2. A Liquibase changelog in YAML, shown as a comment block, using changeSet
 *        id + author with an explicit rollback block
 *     3. A small Java class illustrating how ddl-auto=validate PAIRS with migrations --
 *        Hibernate only VERIFIES the schema Flyway/Liquibase already created; it never
 *        mutates it. Includes a simulated "startup validation" walkthrough.
 *
 * Covers Theory chapter:
 *     15) Hibernate and Advanced JPA/Theory/06 Database Migrations with Flyway and Liquibase.md
 *
 * IMPORTANT -- this file is illustrative, NOT a standalone runnable program.
 * It requires a real Spring Boot project on the classpath to compile/run:
 *     - spring-boot-starter-data-jpa
 *     - EITHER flyway-core OR liquibase-core (not both, in a real project) as a dependency
 *     - a JDBC driver for your target database
 *     - the ACTUAL migration files placed at their real locations (see below), since a
 *       .sql/.yaml file cannot literally live inside a .java source file -- shown here
 *       as string/comment content purely for illustration
 *
 * Run (in a real Spring Boot project, after placing the real migration files and
 * wiring this class into src/main/java/...):
 *     mvn spring-boot:run
 *   or
 *     ./gradlew bootRun
 */

import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

// ---------------------------------------------------------------------------
// 1) FLYWAY -- migration file content, following V<version>__<description>.sql.
// In a real project these would be separate files at:
//     src/main/resources/db/migration/V1__create_products_table.sql
//     src/main/resources/db/migration/V2__create_categories_table.sql
//     src/main/resources/db/migration/V3__add_discontinued_column_to_products.sql
// Shown here as string constants purely so this single .java file can carry
// their illustrative content.
// ---------------------------------------------------------------------------

final class FlywayMigrationsIllustrated {

    private FlywayMigrationsIllustrated() { }   // not instantiable -- constants holder only

    // File: V1__create_products_table.sql
    static final String V1_CREATE_PRODUCTS_TABLE = """
        CREATE TABLE products (
            id BIGSERIAL PRIMARY KEY,
            name VARCHAR(255) NOT NULL,
            price NUMERIC(10, 2) NOT NULL,
            category_id BIGINT
        );
        """;

    // File: V2__create_categories_table.sql
    static final String V2_CREATE_CATEGORIES_TABLE = """
        CREATE TABLE categories (
            id BIGSERIAL PRIMARY KEY,
            name VARCHAR(60) NOT NULL UNIQUE
        );

        ALTER TABLE products
            ADD CONSTRAINT fk_products_category
            FOREIGN KEY (category_id) REFERENCES categories(id);
        """;

    // File: V3__add_discontinued_column_to_products.sql
    // A genuinely additive, backward-compatible change -- safe to deploy
    // alongside old application code still running during a rolling deploy
    // (the "expand" step of the expand-contract pattern from Theory 06).
    static final String V3_ADD_DISCONTINUED_COLUMN = """
        ALTER TABLE products ADD COLUMN discontinued BOOLEAN NOT NULL DEFAULT FALSE;
        """;

    /*
     * Spring Boot integration -- application.properties, close to zero-config:
     * (shown as a comment; this is NOT parsed from this Java file, it is a
     * separate real properties file in a real project)
     *
     * spring.flyway.enabled=true
     * spring.flyway.locations=classpath:db/migration
     *
     * spring.jpa.hibernate.ddl-auto=validate   # see ValidateOnlySchemaCheck below for WHY
     *
     * Flyway runs automatically on application startup, BEFORE Hibernate touches
     * the schema at all -- Flyway creates and owns a "flyway_schema_history" table
     * tracking each migration's version, description, a CHECKSUM of its content,
     * when it ran, and whether it succeeded. Editing an already-applied V3__...sql
     * file causes a checksum mismatch on the next startup -- Flyway refuses to
     * proceed by default. The fix is always a NEW migration (e.g. V4__...), never
     * editing history.
     */
}

// ---------------------------------------------------------------------------
// 2) LIQUIBASE -- the same three schema changes expressed as a YAML changelog
// instead. In a real project this would be a separate file at:
//     src/main/resources/db/changelog/changelog-master.yaml
// Shown here as a comment block since YAML cannot appear as literal, parsed
// content inside a .java file.
// ---------------------------------------------------------------------------

/*
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
              - column:
                  name: category_id
                  type: BIGINT

  - changeSet:
      id: 2
      author: vanisha
      changes:
        - createTable:
            tableName: categories
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
              - column:
                  name: name
                  type: VARCHAR(60)
                  constraints:
                    nullable: false
                    unique: true
        - addForeignKeyConstraint:
            baseTableName: products
            baseColumnNames: category_id
            referencedTableName: categories
            referencedColumnNames: id
            constraintName: fk_products_category

  - changeSet:
      id: 3
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
      # dropColumn is the OBVIOUS auto-inferred rollback for addColumn -- shown
      # here explicitly anyway for illustration. Not every changeset type has
      # such an obvious reverse (e.g. a data-migrating "sql:" changeset needs a
      # hand-written rollback block, as demonstrated in Theory 06).
      rollback:
        - dropColumn:
            tableName: products
            columnName: discontinued
*/

/*
 * Spring Boot integration -- application.properties (shown as a comment):
 *
 * spring.liquibase.enabled=true
 * spring.liquibase.change-log=classpath:db/changelog/changelog-master.yaml
 *
 * spring.jpa.hibernate.ddl-auto=validate   # same principle as Flyway -- Liquibase owns the schema
 *
 * Each changeset is tracked by id + author + the changelog file it lives in
 * (not a sequential filename-embedded version number the way Flyway uses) --
 * Liquibase records applied changesets in a "DATABASECHANGELOG" table, and
 * (like Flyway) computes a checksum per changeset to detect edits to
 * already-applied history.
 */

// ---------------------------------------------------------------------------
// 3) A small Java class illustrating how ddl-auto=validate PAIRS with
// migrations -- Hibernate's job, once Flyway/Liquibase owns the schema, is
// ONLY to confirm its entity mappings still match what the migration tool
// already created, never to alter the schema itself.
// ---------------------------------------------------------------------------

// The @Entity below must match EXACTLY what V1/V2/V3 (or changeSets 1/2/3)
// actually created -- ddl-auto=validate will make Hibernate throw at startup
// (SchemaManagementException) if there is any mismatch, e.g. a missing column,
// a wrong type, or a mapped column the migration never created.
@Entity
@Table(name = "products")
class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private java.math.BigDecimal price;

    @Column(name = "category_id")
    private Long categoryId;

    // Maps to the column added by V3__add_discontinued_column_to_products.sql /
    // changeSet id 3 above -- if this migration had NOT been run yet, Hibernate's
    // validate check would fail fast at startup with a clear error, rather than
    // the application starting successfully and failing later with a confusing
    // "column does not exist" SQL error at first actual query time.
    @Column(nullable = false)
    private boolean discontinued;

    protected Product() { }

    public Long getId() { return id; }
    public String getName() { return name; }
    public java.math.BigDecimal getPrice() { return price; }
    public Long getCategoryId() { return categoryId; }
    public boolean isDiscontinued() { return discontinued; }
}

// A CommandLineRunner that runs once at application startup, AFTER Flyway/
// Liquibase has already applied its migrations and AFTER Hibernate's own
// ddl-auto=validate check has already passed (or the application would have
// failed to start at all) -- this class exists purely to make that ordering
// and guarantee CONCRETE and inspectable, not to replace Hibernate's own check.
@Component
class MigrationOwnedSchemaStartupCheck implements CommandLineRunner {

    private final DataSource dataSource;

    public MigrationOwnedSchemaStartupCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        // By the time this runs:
        //   1. Flyway/Liquibase has already applied every pending migration
        //      (V1, V2, V3 / changeSets 1, 2, 3) against the target database,
        //      tracked in flyway_schema_history / DATABASECHANGELOG.
        //   2. Hibernate has already run its OWN validate check against the
        //      @Entity mappings (Product above) and would have thrown a
        //      startup-fatal SchemaManagementException if anything mismatched --
        //      so simply reaching this line is itself evidence validation passed.
        // This method just demonstrates INSPECTING that the expected column
        // genuinely exists, as a concrete, runnable confirmation of the same
        // guarantee ddl-auto=validate already silently gave us.
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, "products", "discontinued")) {
                boolean columnExists = columns.next();
                if (!columnExists) {
                    // In practice this branch is UNREACHABLE if ddl-auto=validate
                    // already passed -- shown only to make the invariant explicit.
                    throw new IllegalStateException(
                        "Expected column 'discontinued' on 'products' -- migration V3 " +
                        "(or changeSet id 3) has not been applied. ddl-auto=validate " +
                        "should already have failed startup before reaching this check.");
                }
                System.out.println("Schema check passed: 'products.discontinued' exists, " +
                                    "as created by the migration tool -- Hibernate never touched the schema.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to inspect schema metadata", e);
        }
    }
}

/*
 * NOTE on the CI/CD ordering this pairing assumes (Theory 06):
 *   1. New code (with new migration files) merges to main.
 *   2. CI builds the application.
 *   3. A dedicated migration step runs FIRST, against the target environment's
 *      database, using the SAME Flyway/Liquibase CLI (or Maven/Gradle plugin)
 *      the app itself would use.
 *   4. Only if migrations succeed does the pipeline deploy the new app version.
 *   5. The new app version starts with ddl-auto=validate, confirming the schema
 *      the migration tool already applied actually matches what the entities
 *      (Product, above) expect -- exactly what MigrationOwnedSchemaStartupCheck
 *      re-confirms explicitly and inspectably in this file.
 *
 * NOTE on annotations/APIs used above:
 * This file mixes plain JPA (jakarta.persistence.*) entity mapping with Spring Boot
 * (org.springframework.boot.CommandLineRunner, @Component) wiring exactly as a real
 * Spring Boot project would. It requires EITHER flyway-core OR liquibase-core (with
 * the real .sql/.yaml files placed at their real classpath locations, not embedded in
 * this .java file), a JDBC driver, and a live database to actually run end-to-end --
 * intentionally omitted here since this file is illustrative only (see header).
 */
