# Why Cron Isn't Enough

--> A single cron job running a single script works fine for one isolated task -- most real pipelines aren't one task, they're a chain of dependent steps (extract from 3 sources, transform each, join them, load the result, then trigger a downstream dbt run) that need to happen in a specific order, and only proceed once the previous step actually succeeded.
--> Cron has no concept of "wait for the extract to finish before running the transform" -- each cron entry fires purely on a clock, blind to whether anything it depends on actually completed. Chaining tasks with cron alone means either bundling everything into one giant script (impossible to retry a single failed piece) or guessing at how long each step takes and hoping the timing never drifts.
--> An orchestrator like **Apache Airflow** solves exactly this: explicit dependencies between tasks, automatic retries on failure, the ability to re-run ("backfill") a specific past date's pipeline run without re-running everything else, and a visual, queryable history of what ran, when, and whether it succeeded.

# DAGs -- The Core Airflow Abstraction

--> A **DAG (Directed Acyclic Graph)** is Airflow's representation of a pipeline -- a set of tasks, connected by directional edges expressing "this task must finish before that one starts," with no cycles (a task can never depend, even indirectly, on itself).
--> "Acyclic" matters practically, not just mathematically -- a cycle would mean a task waiting on itself to finish, which can never resolve; Airflow's scheduler enforces the DAG structure precisely to make that situation impossible to define in the first place.
--> Each node in the DAG is a **task** (one unit of work -- extract, transform, load, run a dbt model), and the DAG as a whole is scheduled to run on a recurring interval (`@daily`, `@hourly`, a cron expression, or triggered externally).

# A Real Airflow DAG

```python
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.email import EmailOperator
from datetime import datetime, timedelta

default_args = {
    "owner": "data-eng",
    "retries": 3,
    "retry_delay": timedelta(minutes=5),
}

with DAG(
    dag_id="daily_sales_pipeline",
    schedule_interval="@daily",
    start_date=datetime(2026, 1, 1),
    catchup=False,          # don't automatically backfill every day since start_date on first deploy
    default_args=default_args,
) as dag:

    def extract_orders(**context):
        run_date = context["ds"]          # the logical date this run corresponds to
        # ... pull orders created on run_date from the source API ...

    def transform_orders(**context):
        # ... clean/join staged data using the previous task's output ...
        pass

    def load_to_warehouse(**context):
        # ... upsert the transformed rows into the warehouse fact table ...
        pass

    extract_task = PythonOperator(task_id="extract_orders", python_callable=extract_orders)
    transform_task = PythonOperator(task_id="transform_orders", python_callable=transform_orders)
    load_task = PythonOperator(task_id="load_to_warehouse", python_callable=load_to_warehouse)

    alert_on_failure = EmailOperator(
        task_id="alert_on_failure",
        to="data-eng-oncall@example.com",
        subject="Sales pipeline failed",
        html_content="daily_sales_pipeline failed -- check the Airflow UI for the failed task.",
        trigger_rule="one_failed",       # only runs if a preceding task fails
    )

    extract_task >> transform_task >> load_task   # explicit dependency chain
    [extract_task, transform_task, load_task] >> alert_on_failure
```

--> `>>` defines the dependency edges -- `extract_task >> transform_task` reads as "transform_task depends on extract_task." Airflow won't start `transform_task` until `extract_task` reports success.
--> `retries` and `retry_delay` in `default_args` apply automatically to every task in the DAG -- a transient failure (the source API timing out once) gets retried a few times before the whole run is marked failed, without any custom retry logic in the task's own code.
--> `trigger_rule="one_failed"` on `alert_on_failure` inverts the normal "only run if upstream succeeded" rule -- this task exists specifically to fire an alert WHEN something upstream breaks, directly implementing the "visibility/monitoring" requirement that cron alone can't provide.

# Backfilling

--> **Backfilling** means re-running a DAG for a past date (or range of dates) -- essential when a bug is found in a transformation and the last two weeks of data need to be recomputed, or when a new DAG is deployed and needs to populate historical data it never ran for originally.
--> `catchup=False` above disables Airflow's default behavior of automatically running every scheduled interval between `start_date` and now the moment a DAG is first deployed -- almost always the right setting for a DAG being added to an already-running pipeline, to avoid accidentally kicking off months of backfill runs against a live source system.
--> Manual backfills are triggered explicitly (via the Airflow CLI or UI) for a specific date range, and this is exactly why the idempotent upsert pattern from chapter 02 matters so much -- a backfilled run must be safe to execute even though that date's data may have already been loaded once before.

# Scheduling, Retries, and Alerting -- Why All Three Matter Together

--> **Scheduling** alone (what cron already does) tells you WHEN something should run.
--> **Retries** handle the extremely common case of a TRANSIENT failure (a flaky network call, a momentarily unavailable source API) resolving itself on a second or third attempt, without any human intervention.
--> **Alerting** covers the case retries can't -- a genuine, persistent failure (the source API changed its schema, credentials expired) that needs a human to look at it, ideally within minutes rather than being discovered a week later when someone notices a dashboard hasn't updated.
--> Airflow's UI additionally gives visibility into all of this at a glance -- which tasks ran, how long they took, and where in a multi-step DAG a failure occurred -- turning "something in the pipeline is broken" into "task `transform_orders` failed at 3:14 AM with this specific stack trace," a much faster starting point for debugging.

# Deep Dive -- Idempotency Is an Airflow Task Requirement, Not Just a Nice-to-Have

--> Airflow's retry mechanism silently assumes every task is safe to simply run again from scratch -- if `load_to_warehouse` above used blind `INSERT`s instead of the upsert pattern from chapter 02, a single automatic retry after a partial failure would double-load whatever portion of the data made it through before the failure, and the pipeline would report success on the retry while quietly having corrupted the warehouse table.
--> This is precisely why idempotency is framed here as a task-level DESIGN requirement rather than an Airflow feature to configure -- Airflow provides the retry/backfill mechanics, but it is entirely the task author's responsibility to make sure "run this task twice" and "run this task once" are indistinguishable in their effect on the destination table.

# Update Note -- `schedule` Supersedes `schedule_interval`

--> The DAG example above (`schedule_interval="@daily"`) uses the older kwarg. Since **Airflow 2.4**, the DAG constructor accepts a unified `schedule` parameter, and Airflow's own documentation now recommends it over `schedule_interval` -- the old kwarg still works in 2.x but is on a deprecation-leaning path, and **Airflow 3.x** deprecates it further. In new code, prefer `schedule="@daily"` in place of `schedule_interval="@daily"`; everything else about the DAG above (dependency chain, retries, backfilling) is unaffected.
