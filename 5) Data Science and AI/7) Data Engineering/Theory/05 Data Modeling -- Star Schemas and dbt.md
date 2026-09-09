# Star Schemas -- Fact Tables and Dimension Tables

--> A **star schema** is the standard way analytical data is modeled inside a warehouse -- one central **fact table** surrounded by several **dimension tables**, resembling a star when diagrammed.
--> **Fact table** -- one row per business EVENT (an order, a page view, a payment) -- mostly numeric measures (amount, quantity) plus foreign keys pointing out to dimension tables. Fact tables are typically the largest tables in a warehouse, growing by one row per event forever.
--> **Dimension tables** -- descriptive context ABOUT those events -- a `dim_customers` table (name, signup date, segment), a `dim_products` table (name, category, price), a `dim_date` table (calendar attributes like day-of-week, is_holiday, fiscal_quarter). Dimensions are much smaller and change far less often than the fact table.

```
                dim_customers
                     |
dim_date  --  fact_orders  --  dim_products
                     |
                dim_region
```

```sql
-- A typical star-schema query joins the fact table out to whichever dimensions the question needs
SELECT
    d.fiscal_quarter,
    p.category,
    SUM(f.amount) AS revenue
FROM fact_orders f
JOIN dim_date d     ON f.order_date_key = d.date_key
JOIN dim_products p ON f.product_key    = p.product_key
GROUP BY 1, 2;
```

# Why Warehouses Deliberately Denormalize

--> "3) Advanced/03 Normalization and Database Design.md" (in the Database folder) treats normalization to 3NF as the default for OLTP schemas -- eliminating redundancy so that updating a fact means updating exactly one row, which matters enormously for a system handling constant writes.
--> A warehouse has almost the opposite workload -- overwhelmingly READS, rebuilt on a schedule from upstream sources rather than edited row-by-row by end users, and the "update anomaly" risk normalization protects against barely applies when a dimension table is simply regenerated wholesale on every pipeline run rather than hand-edited in place.
--> A star schema is a deliberate, DENORMALIZED design relative to 3NF -- `dim_products` might repeat a category name across thousands of rows rather than normalizing it into a separate `categories` table, purely because it means one fewer JOIN for every analyst or BI tool querying it, and simpler, more obviously correct queries matter more here than eliminating a small amount of redundancy that gets rebuilt fresh every pipeline run anyway.
--> This is the exact same denormalization trade-off discussed in the Normalization file's own "Denormalization" section, just applied by DEFAULT rather than as an exception -- OLTP schemas normalize first and denormalize only where profiling proves it's needed; warehouse schemas start from a denormalized star schema because the read-heavy, batch-rebuilt workload makes that the right default from the outset, not a shortcut.
--> Snowflake/BigQuery/Redshift's columnar storage (chapter 04) also makes wide, denormalized dimension tables cheap to store and scan -- a repeated `category` string column compresses extremely well in columnar storage, further reducing the practical cost of the redundancy a 3NF-purist would otherwise object to.

# dbt -- Transformations as Version-Controlled, Testable SQL

--> **dbt (data build tool)** lets the transformation step of an ELT pipeline (chapter 02) be written as plain SQL `SELECT` statements, organized as "models" in a Git repository, rather than as ad-hoc scripts or manually-run SQL nobody tracks -- transformation logic gets the same rigor as application code: version control, code review, and automated testing.
--> A dbt **model** is just a `.sql` file containing a `SELECT` -- dbt handles compiling it, figuring out the dependency order between models (similar in spirit to Airflow's DAG in chapter 03, but for SQL transformations specifically rather than a whole pipeline), and materializing it as a table or view in the warehouse.

```sql
-- models/marts/fact_orders.sql
-- Builds the fact table by joining and cleaning two upstream staging models
select
    o.order_id,
    o.customer_id,
    p.product_key,
    d.date_key as order_date_key,
    o.amount
from {{ ref('stg_orders') }} o
join {{ ref('stg_products') }} p on o.product_id = p.product_id
join {{ ref('dim_date') }} d on o.order_date = d.calendar_date
where o.amount > 0   -- exclude bad/refunded rows with non-positive amounts
```

--> `{{ ref('stg_orders') }}` is dbt's way of referencing another model instead of hardcoding a schema/table name -- dbt uses these references to automatically build the dependency graph and run models in the correct order, exactly analogous to Airflow's `>>` operator building a task dependency graph in chapter 03.

# Testing dbt Models

--> dbt supports declarative tests defined alongside a model, checked automatically every run -- directly extending the automated data quality checks concept from chapter 01/02 into something version-controlled and enforced on every deploy, the same discipline the Testing Pyramid/TDD concepts elsewhere in this vault apply to application code.

```yaml
# models/marts/schema.yml
version: 2
models:
  - name: fact_orders
    columns:
      - name: order_id
        tests:
          - unique
          - not_null
      - name: amount
        tests:
          - not_null
      - name: customer_id
        tests:
          - relationships:
              to: ref('dim_customers')
              field: customer_id
```

--> `unique` and `not_null` catch the most common real-world data bugs (a duplicate `order_id` from a non-idempotent upstream load, chapter 02) directly at the model level, before anyone downstream ever sees the bad row.
--> `relationships` is a referential-integrity check -- every `customer_id` in `fact_orders` must actually exist in `dim_customers` -- catching a broken JOIN or a late-arriving dimension update (chapter 02's late-arriving data problem) before it silently produces `NULL`s or dropped rows in downstream reports.
--> Running `dbt test` in CI (connecting to the CI/CD concepts in the Full Stack track) before merging a change to a model means a broken transformation is caught in a pull request, not discovered three days later when someone notices a dashboard number looks wrong.

# Deep Dive -- Slowly Changing Dimensions

--> Dimension attributes DO occasionally change -- a customer moves regions, a product gets reclassified into a different category -- and naively overwriting the dimension row in place destroys the historical fact that, at the time of a past order, that customer was in a DIFFERENT region.
--> **Slowly Changing Dimension (SCD) Type 2** is the standard pattern for handling this -- instead of updating a dimension row in place, insert a NEW row with the updated attribute value, and mark both rows with validity date ranges (`valid_from`/`valid_to`) so historical facts can join to the dimension row that was TRUE at the time the fact occurred, not the dimension's current state.
--> dbt has built-in support for this via snapshots (`dbt snapshot`), which automatically detect changes to a source table over time and build exactly this kind of historized dimension table -- turning a genuinely subtle data modeling problem (most beginners' first star schema silently gets this wrong) into a one-time configuration rather than hand-written change-tracking logic.
