# Data Engineering — Practical

Runnable companions to the Theory folder (`4) Data Science and AI\7) Data Engineering\Theory\`). Everything below uses SQLite to stand in for a real warehouse, so every Python file runs with zero external services or accounts — same approach as the sibling Database/SQL Practical folder.

## Setup

```bash
pip install pandas
# apache-airflow is optional/heavy -- only needed if you actually want to run 04 for real,
# not just read it as a reference. Everything else needs nothing beyond pandas + the stdlib.
```

## Files, in suggested run order

| File | Theory chapter | What it demonstrates |
|---|---|---|
| `01_idempotent_extract_and_load.py` | ETL and ELT Pipelines | Simulates pulling records from a "source API" (a hardcoded list of dicts) and loading them into SQLite with an idempotent `INSERT ... ON CONFLICT DO UPDATE`. Runs the load **twice** and proves no duplicates result — the concrete payoff of idempotency. |
| `02_star_schema_warehouse_demo.py` | Data Modeling — Star Schemas and dbt | Builds a small star schema in SQLite (one fact table, several dimension tables), seeds it with sample data, then runs an analytical `GROUP BY` query joining the fact table out to dimensions — the working-code version of the Theory file's diagram. |
| `03_scd_type2_dimension_demo.py` | Data Modeling — Star Schemas and dbt (Slowly Changing Dimensions deep dive) | Hand-implements SCD Type 2: a customer moves region, and instead of overwriting the row, a new dimension row is inserted with `valid_from`/`valid_to` dates. A query then correctly joins an *old* fact row to the dimension value that was true *at the time*, proving historical accuracy is preserved. |
| `04_airflow_dag_reference.py` | Workflow Orchestration with Airflow | A real, correctly-structured Airflow DAG (as it would live in a real deployment's `dags/` folder) — 3-4 dependent `PythonOperator` tasks wired with `>>`, plus retry configuration. **Reference file** — needs a real Airflow install to actually execute; read it to understand DAG structure even without running it. |
| `05_dbt_model_reference/` | Data Modeling — Star Schemas and dbt (dbt section) | A small dbt project fragment: `models/stg_orders.sql` (staging), `models/fact_orders.sql` (using `{{ ref() }}` to depend on the staging model), and `models/schema.yml` (`unique`/`not_null`/`relationships` tests). **Reference files** — needs a real dbt project (`dbt_project.yml`, a configured `profiles.yml`, and a warehouse connection) to actually run `dbt run`/`dbt test`; included here to show correct dbt project structure and testing conventions. |

## Notes

- Files `01`–`03` are fully standalone and runnable right now: `python 01_idempotent_extract_and_load.py`, etc. — each creates its own SQLite file, runs the demo, and prints its result to the console.
- Files `04` and `05` are **reference material**, not standalone-runnable — Airflow and dbt are both meant to run inside their own project/deployment context, so dropping either into a real Airflow `dags/` folder or a real dbt project is how you'd actually execute them.
- For the deeper Big Data / Spark side of the pipeline (rather than orchestration/warehousing), see `4) Data Science and AI\6) MLOps and Big Data\Practical\04_pyspark_wordcount_demo.py`.
