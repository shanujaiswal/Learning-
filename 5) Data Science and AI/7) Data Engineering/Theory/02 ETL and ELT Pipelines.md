# ETL -- Extract, Transform, Load

--> **Extract** -- pull raw data out of a source system (a production database, a REST API, a CSV drop) -- directly using the SQL and HTTP concepts covered across "2) Full Stack/2) BackEnd".
--> **Transform** -- clean, join, and reshape the data BEFORE it lands anywhere permanent -- historically done on a separate processing server, since early data warehouses had limited compute of their own.
--> **Load** -- write the now-clean, finished data into the destination warehouse.
--> ETL's defining trait: transformation happens in a middle stage, OUTSIDE the destination system, before the data is ever loaded there.

# ELT -- Extract, LOAD, Transform

--> **ELT** flips the last two steps -- raw data is loaded into the warehouse FIRST, in something close to its original shape, and transformation happens AFTER, as SQL running inside the warehouse itself.
--> This became the dominant pattern once cloud warehouses (Snowflake, BigQuery, Redshift -- chapter 04) made near-unlimited, pay-per-use compute cheap and elastic -- there's no longer a good reason to maintain a separate transformation server/cluster when the warehouse can do that work itself, often faster, and the raw data is preserved in the warehouse for re-processing if the transformation logic ever needs to change.
--> ELT also decouples ingestion from transformation logic -- a tool like Fivetran can dump raw data with zero business logic baked in, and dbt (chapter 05) owns 100% of the transformation as version-controlled SQL, layered independently on top.

```
ETL:  Source --Extract--> Transform (separate compute) --Load--> Warehouse (clean data only)
ELT:  Source --Extract--> Load (raw data) --> Warehouse --Transform (warehouse's own compute)--> Clean tables
```

--> ELT's main trade-off: raw, unmodeled data sits in the warehouse taking up storage and, until transformed, isn't directly usable -- an acceptable cost, since warehouse storage is cheap and the raw copy is genuinely useful as an audit trail and a safety net.

# Idempotency -- The Property Every Pipeline Needs

--> A pipeline is **idempotent** if running it twice on the same input produces the SAME end result as running it once -- no duplicated rows, no double-counted revenue, no corrupted state.
--> This matters because pipelines fail and get re-run constantly -- a network blip mid-load, a manual backfill of a specific date, an Airflow retry (chapter 03) -- and a non-idempotent pipeline turns every one of those routine events into a data quality incident.
--> The common failure mode: a pipeline that blindly `INSERT`s every row it extracts. Re-running it after a partial failure re-inserts rows that already loaded successfully, silently doubling counts.
--> The fix is almost always an **upsert** (insert-or-update) keyed on a stable natural or business key, rather than a blind append -- if a row with that key already exists, update it in place; otherwise insert it.

# A Real Idempotent Extraction Script

--> A simple extractor that pulls orders from an API and upserts them into a Postgres staging table, safe to re-run any number of times on the same date range.

```python
import requests
import psycopg2
from datetime import date

def extract_orders(api_key: str, since: date) -> list[dict]:
    """Extract -- pull orders created on or after `since` from the source API."""
    resp = requests.get(
        "https://api.example.com/v1/orders",
        params={"created_since": since.isoformat()},
        headers={"Authorization": f"Bearer {api_key}"},
    )
    resp.raise_for_status()
    return resp.json()["orders"]

def load_orders(orders: list[dict], conn) -> None:
    """Load -- idempotent upsert keyed on order_id, safe to re-run on the same batch."""
    with conn.cursor() as cur:
        for order in orders:
            cur.execute(
                """
                INSERT INTO staging.orders (order_id, customer_id, amount, created_at)
                VALUES (%(order_id)s, %(customer_id)s, %(amount)s, %(created_at)s)
                ON CONFLICT (order_id) DO UPDATE SET
                    customer_id = EXCLUDED.customer_id,
                    amount      = EXCLUDED.amount,
                    created_at  = EXCLUDED.created_at
                """,
                order,
            )
    conn.commit()

if __name__ == "__main__":
    orders = extract_orders(api_key="...", since=date(2026, 8, 1))
    conn = psycopg2.connect("dbname=warehouse user=etl_bot")
    load_orders(orders, conn)
```

--> `ON CONFLICT (order_id) DO UPDATE` is Postgres's upsert syntax -- other warehouses use equivalent constructs (`MERGE` in Snowflake/BigQuery/SQL Server). Whatever the syntax, the principle is identical: match on a unique key, update if found, insert if not.
--> Note this script has no transformation logic at all -- it's a pure ELT extractor, dumping near-raw data into a `staging` schema; the actual cleaning/joining/reshaping happens afterward, in SQL, covered in chapter 05.

# Incremental Extraction vs Full Refresh

--> **Full refresh** -- re-extract the entire source table every run -- simplest to reason about, but wasteful and slow once a source table has millions of rows.
--> **Incremental extraction** -- only pull rows that are new or changed since the last successful run, usually via a `updated_at` timestamp or an auto-incrementing ID watermark (`WHERE updated_at > :last_run_time`).
--> Incremental extraction makes idempotency even more important -- if a run fails partway through, re-running it with the same watermark must not double-count the rows it already loaded before failing, which the upsert pattern above handles correctly regardless of how many times a given watermark window gets re-processed.

# Deep Dive -- Late-Arriving and Out-of-Order Data

--> Real source systems don't always deliver events in perfect chronological order -- a mobile app might batch-upload yesterday's events when the user's device reconnects to the internet, arriving well after "yesterday's" pipeline run already completed and reported final numbers.
--> A pipeline that only ever processes "today's new rows" and never revisits past windows will silently undercount historical periods forever once this happens -- a much harder bug to notice than an outright pipeline failure, since nothing errors, the numbers are just quietly wrong.
--> The practical mitigation is a rolling reprocessing window -- re-run the last N days' incremental extraction and upsert on every run, not just the newest day -- trading some redundant work for correctness against late arrivals, which the idempotent upsert pattern above makes safe to do repeatedly.
