# MLOps and Big Data — Practical

Index of the runnable demos in this folder and how each maps back to the
Theory notes in `4) Data Science and AI\6) MLOps and Big Data\Theory\`.

## File index

| File | What it does | Theory chapter |
|---|---|---|
| `01_train_and_serialize_model.py` | Trains a scikit-learn logistic regression on a small synthetic dataset and serializes it with `joblib` to `model.pkl`. First step of the model lifecycle. | `00 MLOps and Big Data Roadmap.md` and `01 MLOps Fundamentals and Model Deployment.md` |
| `02_fastapi_model_serving.py` | Loads `model.pkl` (produced by file 01) and serves predictions over HTTP via a FastAPI app (`/predict`, `/health`). | `01 MLOps Fundamentals and Model Deployment.md` |
| `03_model_monitoring_drift_demo.py` | Simulates production data that gradually drifts from the training distribution and detects it with PSI and the KS test — post-deployment monitoring. | `01 MLOps Fundamentals and Model Deployment.md` |
| `04_pyspark_wordcount_demo.py` | Classic word-count job on Spark's DataFrame API, run locally (no cluster/Hadoop/YARN needed). | `02 Big Data Ecosystem - Hadoop and Spark.md` |
| `05_simple_etl_pipeline.py` | Minimal Extract-Transform-Load example: CSV in, pandas cleaning, SQLite out. | `03 Data Engineering Pipelines and ETL.md` |

## Setup

Most of the demos share one small set of dependencies:

```bash
pip install fastapi uvicorn scikit-learn joblib pandas
```

`03_model_monitoring_drift_demo.py` additionally needs `numpy` and `scipy`
(usually already pulled in transitively, but install explicitly if needed):

```bash
pip install numpy scipy
```

`04_pyspark_wordcount_demo.py` needs PySpark, which is **optional and large**
— only install it if you intend to run that specific file, and note it also
requires a Java runtime (JDK 8/11/17) on PATH:

```bash
pip install pyspark
```

## Suggested run order

1. `python 01_train_and_serialize_model.py` — produces `model.pkl`
2. `python 02_fastapi_model_serving.py` — serves the model produced in step 1
3. `python 03_model_monitoring_drift_demo.py` — standalone, no dependency on steps 1-2
4. `python 04_pyspark_wordcount_demo.py` — standalone (requires PySpark + Java)
5. `python 05_simple_etl_pipeline.py` — standalone

## Note on file 05 and the Data Engineering folder

`05_simple_etl_pipeline.py` is intentionally brief — it illustrates the core
Extract/Transform/Load concept end-to-end in about 80 lines using just
pandas and SQLite. The deeper, production-style treatment of data
engineering (Airflow DAGs, dbt models, data warehousing, incremental loads,
orchestration, etc.) lives in the separate folder:

```
4) Data Science and AI\7) Data Engineering\Practical
```

Go there for the full pipeline treatment; this file is just a lightweight
on-ramp to the ETL concept in the context of the MLOps/Big Data chapter.
