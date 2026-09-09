# Data Engineering vs Data Science -- A Role Distinction

--> Everything in the Data Science and Machine Learning folders assumes clean, accessible data is already sitting somewhere ready to be analyzed/modeled -- Data Engineering is the discipline that actually BUILDS and maintains the pipelines making that data available, reliably and on schedule, in the first place. A Data Engineer's "customers" are often Data Analysts, Data Scientists, and ML systems themselves.

# ETL -- Extract, Transform, Load

--> **Extract** -- pulling raw data from its source (a production database, an API, log files, third-party data) -- directly connecting to the SQL and REST API concepts covered across the Full Stack track.
--> **Transform** -- cleaning, reshaping, and enriching the data (echoing the Data Cleaning file's techniques, but automated and repeatable rather than done manually in a notebook).
--> **Load** -- writing the finished, clean data into its destination -- typically a data warehouse (a database optimized specifically for analytics queries, e.g. Snowflake, BigQuery, Redshift) rather than the original production database.

```
Production DB (source of truth for the app)
       |
       v  Extract
Raw staging area
       |
       v  Transform (clean, join, aggregate)
Cleaned, modeled data
       |
       v  Load
Data Warehouse (what Analysts/Data Scientists actually query)
```

# ELT -- A Modern Variation

--> Extract, LOAD, Transform -- loads raw data into the warehouse FIRST, then transforms it using the warehouse's own powerful compute (rather than transforming in a separate processing step before loading) -- increasingly common now that modern cloud data warehouses have enough compute power to handle heavy transformation workloads directly and efficiently.

# Why a Separate Data Warehouse Exists

--> Running heavy analytical queries (aggregating millions of rows) directly against a PRODUCTION database (the one covered throughout the Full Stack Database track, serving live application traffic) risks slowing down or even crashing the actual application -- a separate warehouse, optimized specifically for large analytical reads rather than many small transactional writes, avoids that conflict entirely.
--> This connects directly to the Replication concepts covered in the Full Stack Database Advanced notes -- read replicas serve a similar decoupling purpose, though a dedicated analytical warehouse typically also restructures the data itself for analytical query patterns, not just copying it as-is.

# Data Pipeline Orchestration

--> Real pipelines involve many interdependent steps (extract from 5 sources, transform each, join them together, load the result) that need to run in the correct order, on a schedule, with failure handling and alerting if something breaks -- orchestration tools manage exactly this.
--> **Apache Airflow** -- the most widely used orchestration tool -- pipelines are defined as DAGs (Directed Acyclic Graphs) of tasks with explicit dependencies, similar in spirit to a CI/CD pipeline's stage dependencies (covered in the Full Stack GitHub Actions/AWS CI-CD notes), just applied to data workflows instead of code deployment.

```python
from airflow import DAG
from airflow.operators.python import PythonOperator
from datetime import datetime

with DAG("daily_sales_pipeline", schedule_interval="@daily", start_date=datetime(2026, 1, 1)) as dag:
    extract_task = PythonOperator(task_id="extract", python_callable=extract_sales_data)
    transform_task = PythonOperator(task_id="transform", python_callable=clean_sales_data)
    load_task = PythonOperator(task_id="load", python_callable=load_to_warehouse)

    extract_task >> transform_task >> load_task   # Defines the required execution order
```

# Data Quality Checks Within a Pipeline

--> A production pipeline should validate data automatically at each stage (row counts within an expected range, no unexpected nulls in a critical column, values within expected bounds) and alert/halt if something looks wrong -- directly extending the manual Data Validation concepts from the Data Cleaning file into an automated, continuously-running safeguard, rather than a one-time manual check.

# Why This File Closes Out the Entire Data Science and AI Section

--> Every folder in this section depends on data engineering working correctly upstream -- an analyst's dashboard, a data scientist's model, and an MLOps pipeline's production predictions are all only as reliable as the data pipeline feeding them, making this quietly one of the most foundational (if least visible) disciplines in the entire section.

# Update Note -- Airflow API Drift in the DAG Example Above

--> The DAG example above uses `schedule_interval="@daily"`. Since **Airflow 2.4**, the recommended kwarg is `schedule` (e.g. `schedule="@daily"`) -- `schedule_interval` still works but is deprecated-leaning in 2.x and further deprecated in Airflow 3.x.
--> Separately, if this DAG were extended with an `EmailOperator` (as the fuller example in the Data Engineering folder's "03 Workflow Orchestration with Airflow.md" does), note that `EmailOperator` no longer ships in Airflow core -- it now lives in a separate provider package (`apache-airflow-providers-smtp`, or the `standard` provider on some distributions) that must be installed separately, with an import path like `from airflow.providers.smtp.operators.smtp import EmailOperator` rather than `from airflow.operators.email import EmailOperator`.
