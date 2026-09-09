# 05_dbt_model_reference

REFERENCE dbt project fragment -- not a runnable Python script. These are
real dbt model/config files, structured exactly as they would sit inside a
real dbt project's `models/` directory, but there is no live warehouse
connection configured here (no `profiles.yml`, no `dbt_project.yml` at a
real project root) -- the point is to show correct dbt syntax and project
structure, not to execute a build.

Covers Theory chapter:
    05 Data Modeling -- Star Schemas and dbt.md
        - "dbt -- Transformations as Version-Controlled, Testable SQL"
        - "Testing dbt Models"

## Layout

```
05_dbt_model_reference/
    models/
        stg_orders.sql      -- staging model: light cleaning of the raw source table
        fact_orders.sql      -- mart model: joins staging models via {{ ref() }}
        schema.yml            -- declarative tests: unique, not_null, relationships
```

## How this maps to a real dbt project

To actually run this, you'd need a full dbt project (`dbt init`) with:

- `dbt_project.yml` at the project root, pointing `models-paths` at a
  `models/` directory like the one here.
- `profiles.yml` (usually in `~/.dbt/`) with real warehouse connection
  credentials (Snowflake/BigQuery/Redshift -- chapter 04).
- A `source()` definition (in a `sources.yml`, not included here) that
  `stg_orders.sql` would reference instead of a hardcoded raw table name --
  omitted here to keep this a self-contained, readable reference rather
  than a full working project.

With that in place:

```bash
dbt run    # builds stg_orders, then fact_orders, in dependency order
dbt test   # runs the unique/not_null/relationships tests in schema.yml
```

`dbt run` figures out that `fact_orders` depends on `stg_orders` purely from
the `{{ ref('stg_orders') }}` call inside `fact_orders.sql` -- exactly the
dependency-graph mechanism described in the Theory chapter, analogous to
Airflow's `>>` operator building a task graph (chapter 03) but scoped to
SQL transformations.
