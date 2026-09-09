"""
04_airflow_dag_reference.py

REFERENCE FILE -- this is a real, correctly-structured Apache Airflow DAG.
It is NOT meant to be run with `python 04_airflow_dag_reference.py` (Airflow
DAGs are parsed by the Airflow scheduler, not executed top-to-bottom as a
script). To actually run it you would need a real Airflow install and this
file dropped into that install's `dags/` folder:

    pip install apache-airflow
    airflow db init
    airflow webserver -p 8080          # in one terminal
    airflow scheduler                  # in another
    cp 04_airflow_dag_reference.py  $AIRFLOW_HOME/dags/

Airflow is deliberately NOT exercised end-to-end in this Practical folder --
it's a heavyweight system (its own metadata database, scheduler process, and
webserver) that is overkill to stand up just to demonstrate DAG syntax. What
matters pedagogically is that the DAG structure below -- dependencies,
retries, task isolation -- is correct and would run as-is in a real install.

Covers Theory chapter:
    03 Workflow Orchestration with Airflow.md
        - "DAGs -- The Core Airflow Abstraction"
        - "A Real Airflow DAG"
        - "Backfilling"
        - "Scheduling, Retries, and Alerting"
        - "Deep Dive -- Idempotency Is an Airflow Task Requirement"

This DAG models a 4-task daily pipeline:

    extract_orders  -->  transform_orders  -->  load_to_warehouse  -->  run_dbt_models

Every task is idempotent (see 01_idempotent_extract_and_load.py) -- safe to
retry automatically or backfill for a past date without double-counting
anything, which is exactly what makes Airflow's `retries` setting below safe
to rely on.
"""

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.python import PythonOperator

default_args = {
    "owner": "data-eng",
    "retries": 3,
    "retry_delay": timedelta(minutes=5),
    "retry_exponential_backoff": True,
    "max_retry_delay": timedelta(minutes=30),
}


def extract_orders(**context) -> None:
    """EXTRACT task -- pulls new/updated orders for this DAG run's logical date.

    `context["ds"]` is the run's logical date (YYYY-MM-DD) -- using it rather
    than "today" is what makes a manual backfill for a past date correct: a
    backfill run for 2026-01-15 extracts 2026-01-15's data, not whatever
    day the backfill happens to be executed on.
    """
    run_date = context["ds"]
    print(f"[extract_orders] pulling orders created on {run_date} from the source API")
    # In a real task: call the source API / DB, write raw results to a
    # staging location (e.g. a bucket or a staging table), keyed on a stable
    # business key so the next task's load is itself idempotent.


def transform_orders(**context) -> None:
    """TRANSFORM task -- cleans/joins the raw staged data from extract_orders.

    Reads the same run's staged data via `context["ds"]`, applying the
    business rules that turn raw source rows into warehouse-ready rows
    (e.g. dropping cancelled orders, normalizing currency).
    """
    run_date = context["ds"]
    print(f"[transform_orders] cleaning/joining staged orders for {run_date}")


def load_to_warehouse(**context) -> None:
    """LOAD task -- idempotent UPSERT into the warehouse fact table.

    This MUST use the upsert pattern from 01_idempotent_extract_and_load.py
    (INSERT ... ON CONFLICT DO UPDATE / MERGE) -- see the Theory chapter's
    "Deep Dive -- Idempotency Is an Airflow Task Requirement" section: if
    this task used blind INSERTs, an automatic retry after a partial
    failure would silently double-load whatever rows made it through
    before the failure.
    """
    run_date = context["ds"]
    print(f"[load_to_warehouse] upserting transformed orders for {run_date} into fact_orders")


def run_dbt_models(**context) -> None:
    """FINAL task -- triggers the downstream dbt run (chapter 05) that
    rebuilds star-schema models on top of the freshly loaded fact table.

    A real task here would typically shell out to `dbt run --select
    fact_orders+` (via BashOperator) or use the dbt Cloud API/Airflow's
    Cosmos provider -- represented here as a PythonOperator stub for
    simplicity.
    """
    run_date = context["ds"]
    print(f"[run_dbt_models] triggering dbt run for models built on data through {run_date}")


with DAG(
    dag_id="daily_orders_pipeline",
    description="Extract, transform, and load daily orders into the warehouse, then rebuild dbt models",
    schedule_interval="@daily",
    start_date=datetime(2026, 1, 1),
    catchup=False,  # don't auto-backfill every day since start_date on first deploy
    default_args=default_args,
    tags=["orders", "elt", "reference"],
) as dag:

    extract_task = PythonOperator(
        task_id="extract_orders",
        python_callable=extract_orders,
    )

    transform_task = PythonOperator(
        task_id="transform_orders",
        python_callable=transform_orders,
    )

    load_task = PythonOperator(
        task_id="load_to_warehouse",
        python_callable=load_to_warehouse,
    )

    dbt_task = PythonOperator(
        task_id="run_dbt_models",
        python_callable=run_dbt_models,
    )

    # Explicit dependency chain: each task waits for the previous one to
    # succeed. Airflow's scheduler will not start transform_task until
    # extract_task reports success, etc.
    extract_task >> transform_task >> load_task >> dbt_task
