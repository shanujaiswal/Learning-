# Why This File Exists

--> The MLOps Fundamentals file covered deploying, serving, and monitoring a model once it's already trained -- this file covers the equally important discipline of tracking HOW a model was produced in the first place (which code, data, and hyperparameters), managing model versions and the data feeding them over time, more rigorous statistical drift detection than "the distributions look different," safer deployment ROLLOUT strategies, and a deeper look at Spark's internals, since the Big Data Ecosystem file's coverage stayed intentionally introductory.

# Experiment Tracking -- Why It's Needed

--> A single model in production is typically the survivor of dozens or hundreds of training runs, each with slightly different hyperparameters (connecting to the Hyperparameter Tuning methodology in the Machine Learning folder's advanced techniques file), feature sets, or code versions -- without systematic tracking, it becomes impossible to reliably answer "which exact configuration produced our best model, and can we reproduce it?"

--> **MLflow** -- an open-source platform that logs every training run's hyperparameters, resulting metrics, and the trained model artifact itself, letting a team compare runs side by side and reliably reproduce or roll back to any past run.

```python
import mlflow

with mlflow.start_run():
    mlflow.log_param("n_estimators", 100)
    mlflow.log_param("max_depth", 8)

    model = RandomForestClassifier(n_estimators=100, max_depth=8)
    model.fit(X_train, y_train)
    accuracy = model.score(X_test, y_test)

    mlflow.log_metric("accuracy", accuracy)
    mlflow.sklearn.log_model(model, "model")   # The trained model itself, saved as a versioned artifact
```

--> **Weights & Biases (W&B)** -- a similar experiment-tracking platform, particularly popular for Deep Learning workflows, that additionally provides rich live dashboards (loss curves updating in real time DURING training, not just a final summary) and easy visual comparison across many simultaneous training runs -- conceptually the same core value as MLflow (structured, comparable records of what was tried and what resulted), with a stronger focus on real-time visualization for long-running neural network training jobs specifically.

```python
import wandb

wandb.init(project="fraud-detection", config={"n_estimators": 100, "max_depth": 8})
model.fit(X_train, y_train)
wandb.log({"accuracy": model.score(X_test, y_test)})
```

# DVC -- Data Version Control

--> Git (covered throughout the Full Stack track) is excellent at versioning CODE, but is fundamentally impractical for versioning large datasets or trained model files directly (multi-gigabyte files bloat a repository badly, and Git's line-by-line diffing makes no sense for a binary model file or a huge CSV) -- **DVC** solves this by storing large files in external storage (cloud storage, a local cache) while keeping only small, lightweight POINTER files (metadata + a content hash) inside the actual Git repository.

```bash
dvc add training_data.csv          # Moves the large file to DVC's storage, leaves a small .dvc pointer file behind
git add training_data.csv.dvc      # Only this tiny pointer file actually goes into Git
git commit -m "Add v1 training data"

dvc push                            # Uploads the actual large file to configured remote storage (S3, GCS, etc.)
```

--> **Why data versioning matters as much as code versioning** -- reproducing a past model's exact results, or debugging why a newly retrained model performs differently from a previous version, requires knowing EXACTLY which version of the training data produced each model -- without this, "which data trained this model" becomes an unanswerable, and quietly dangerous, question once a pipeline has been retrained many times over months.

# The Model Registry -- A Single Source of Truth for Model Versions

--> A **Model Registry** (MLflow includes one built-in; cloud platforms like SageMaker and Vertex AI offer their own) is a centralized catalog of every trained model version, tracking each one's stage (e.g. "Staging," "Production," "Archived"), its lineage (which experiment run, which data version, which code commit produced it), and providing one authoritative place to answer "what's actually running in production right now, and what was running last month."

```python
mlflow.register_model("runs:/<run_id>/model", "fraud-detection-model")

client = mlflow.tracking.MlflowClient()
client.transition_model_version_stage(
    name="fraud-detection-model", version=3, stage="Production"
)
```

--> This directly extends the CI/CD for Machine Learning discipline from the MLOps Fundamentals file -- an automated retraining pipeline can push a newly trained model into the registry's "Staging" stage automatically, but promotion to "Production" is where automated evaluation checks (and often a human review gate) actually happens, giving a controlled, auditable, reversible path from a fresh training run to a model actually serving real traffic.

# Feature Stores -- Solving Training/Serving Feature Skew

--> Feature Engineering (Machine Learning folder) produces the features a model trains on -- but in production, those SAME features often need to be computed again, in real time, for a live prediction request -- and if the training-time feature computation logic and the serving-time feature computation logic drift apart even slightly, the model behaves inconsistently between training and production in a way that's notoriously hard to detect (this exact problem is called "training/serving skew").
--> **Feast** (an open-source feature store) centralizes feature definitions in ONE place, computed by ONE shared pipeline, then serves them consistently to BOTH the offline training process (as a batch) and the online real-time serving process (as fast, low-latency lookups) -- eliminating the risk of the two paths silently diverging over time.

```python
from feast import FeatureStore

store = FeatureStore(repo_path=".")

# Offline: fetch historical feature values for training, joined against a set of past timestamps/entities
training_df = store.get_historical_features(
    entity_df=entity_df, features=["customer_features:avg_transaction_amount"]
).to_df()

# Online: fetch the CURRENT value of that exact same feature, with low latency, for a live prediction request
online_features = store.get_online_features(
    features=["customer_features:avg_transaction_amount"], entity_rows=[{"customer_id": 1001}]
).to_dict()
```

--> A feature store also naturally enables feature REUSE across different models/teams -- once "average transaction amount over the last 30 days" is defined and computed once in Feast, any other model needing that same feature reuses the identical, already-validated definition instead of every team re-implementing slightly different, subtly inconsistent versions of the same underlying logic.

# Detailed Drift Detection -- Actual Statistical Tests

--> The MLOps Fundamentals file introduced Data Drift and Concept Drift conceptually -- detecting drift RELIABLY in practice relies on actual statistical tests comparing a production data window against the original training data's distribution, rather than just eyeballing a chart.

--> **Kolmogorov-Smirnov (KS) Test** -- for a single numeric feature, compares the training data's distribution against a recent production data window's distribution, and returns a p-value indicating how likely the two samples came from the SAME underlying distribution -- a small p-value (conventionally < 0.05) suggests the two distributions have genuinely, statistically significantly diverged, rather than differing just by ordinary random sampling noise.

```python
from scipy.stats import ks_2samp

statistic, p_value = ks_2samp(training_data["transaction_amount"], production_data["transaction_amount"])

if p_value < 0.05:
    print("Significant drift detected in transaction_amount -- distributions have diverged")
```

--> **Population Stability Index (PSI)** -- buckets a feature's values into ranges, compares what FRACTION of training data fell into each bucket against what fraction of recent production data falls into each SAME bucket, and combines those differences into one summary score.

```
PSI = sum over all buckets of:  (production_% - training_%) * ln(production_% / training_%)

PSI < 0.1   -->  No significant shift -- no action needed
0.1 <= PSI < 0.25  -->  Moderate shift -- worth investigating
PSI >= 0.25  -->  Major shift -- strong signal the feature's distribution has genuinely changed
```

```python
import numpy as np

def calculate_psi(training_values, production_values, buckets=10):
    breakpoints = np.percentile(training_values, np.linspace(0, 100, buckets + 1))
    train_pct = np.histogram(training_values, breakpoints)[0] / len(training_values)
    prod_pct = np.histogram(production_values, breakpoints)[0] / len(production_values)

    # Small epsilon avoids division by zero for any empty bucket
    train_pct, prod_pct = np.clip(train_pct, 1e-4, None), np.clip(prod_pct, 1e-4, None)
    return np.sum((prod_pct - train_pct) * np.log(prod_pct / train_pct))
```

--> **KS Test vs PSI in practice** -- the KS Test gives a rigorous statistical significance answer (is this difference likely real, or just noise) for one feature at a time, while PSI gives a single, easily-thresholded summary NUMBER that's simpler to wire into an automated monitoring dashboard/alert across many features at once -- production monitoring systems commonly use PSI as the everyday automated alerting signal, with the KS Test (or a closer manual look) as the rigorous follow-up once PSI flags something worth investigating.

# Shadow and Canary Deployment -- Rolling Out Safely

--> The MLOps Fundamentals file covered A/B testing model versions with real user-facing traffic split between them -- **shadow** and **canary** deployment are two additional, generally SAFER rollout strategies for a new model version, sitting at different points on the risk spectrum before a full A/B test or complete rollout.
--> **Shadow Deployment** -- the new model version runs ALONGSIDE the current production model on real live traffic, but its predictions are only LOGGED for comparison -- they never actually get shown to real users or affect any real decision -- letting a team validate a new model's real-world behavior against genuinely live data with ZERO risk of it actually impacting users, at the cost of extra compute spent running two models on every request during the shadow period.
--> **Canary Deployment** -- the new model version is rolled out to a small, carefully limited percentage of REAL traffic (e.g. 1-5%), with close monitoring for problems, before gradually increasing that percentage if everything looks healthy -- unlike shadow deployment, canary traffic DOES receive the new model's actual predictions, so it carries some real risk, but that risk is deliberately contained to a small slice of traffic rather than being an all-or-nothing switch.

```
Shadow:   100% of users see the OLD model's predictions; the NEW model runs silently alongside, logged only, for comparison.
Canary:    ~95% of users see the OLD model; ~5% see the NEW model's real, actual predictions; the % grows gradually if healthy.
A/B Test:  A fixed, deliberate split (e.g. 50/50) between old and new, specifically to measure a real business-metric difference.
```

--> **Choosing among the three** -- shadow deployment first, specifically to validate a genuinely new/risky model with zero user-facing risk; canary next, to confirm real-world serving behavior (latency, error rates, not just predictive accuracy) is healthy at small scale; A/B testing once both of those look solid, specifically to rigorously measure the new model's actual business impact before a full rollout -- a progressively more exposed, but progressively more informative, sequence.

# Spark Depth -- Spark SQL, the Catalyst Optimizer and Performance Tuning

--> The Big Data Ecosystem file introduced Spark's DataFrame API at a Pandas-like surface level -- underneath that friendly API, several mechanisms determine whether a Spark job actually runs efficiently at scale or becomes a slow, resource-wasting bottleneck.

--> **Spark SQL** -- lets Spark DataFrames be queried using actual SQL syntax (directly connecting to the SQL concepts covered across the Full Stack Database track) instead of, or alongside, the DataFrame method-chaining syntax -- both styles compile down to the exact same underlying execution plan, so the choice between them is purely a matter of team preference/readability, not performance.

```python
df.createOrReplaceTempView("transactions")
result = spark.sql("""
    SELECT region, SUM(revenue) as total_revenue
    FROM transactions
    GROUP BY region
    ORDER BY total_revenue DESC
""")
```

--> **The Catalyst Optimizer** -- Spark SQL/DataFrame operations are NOT executed literally in the order written -- Catalyst first analyzes the full requested computation and rewrites it into a more efficient EXECUTION PLAN before anything actually runs (e.g. applying filters as early as possible to shrink the data being processed in every later step, reordering joins to process smaller datasets first) -- directly analogous in spirit to a SQL database's own query planner/optimizer (Full Stack Database track), just operating across an entire distributed cluster's worth of data instead of one machine's local database.

```python
df.filter(df.revenue > 1000).select("region", "revenue").explain(True)
# .explain(True) prints Catalyst's actual execution plan -- showing exactly how it reordered/optimized
# the requested operations, which is the right first tool to reach for when a Spark job runs slower than expected
```

--> **Partitioning** -- Spark splits a dataset into PARTITIONS, distributed across the cluster's machines, and processes each partition largely independently in parallel -- too few partitions leaves some machines idle while others do all the work (poor parallelism); too many partitions adds excessive per-partition scheduling overhead relative to the actual work each partition does -- tuning partition count/size to match both the cluster's size and the data's actual volume is a common, concrete performance lever.

```python
df = df.repartition(200, "region")   # Explicitly repartition, e.g. so all rows for a given region end up
                                        # on the same partition -- directly useful before a groupBy/join on that column
```

--> **Shuffling -- The Expensive Operation to Minimize** -- some operations (`groupBy`, `join`, `orderBy` across the full dataset) require data to be physically MOVED between machines/partitions so that related rows end up together, called a "shuffle" -- shuffling involves genuine network transfer and disk I/O across the cluster, making it by far the most expensive common operation in Spark, and the primary target when optimizing a slow job.
--> **Practical shuffle-reduction tactics** -- filtering data down to what's actually needed BEFORE a join/groupBy (so less data needs to be shuffled in the first place), and using `broadcast()` joins when joining a large DataFrame against a genuinely small one (sending the small DataFrame's full copy to every machine, avoiding a full shuffle of the large DataFrame entirely).

```python
from pyspark.sql.functions import broadcast

# When one side of a join is small enough to fit comfortably in memory on every machine,
# broadcasting it entirely avoids shuffling the large DataFrame across the cluster
result = large_df.join(broadcast(small_lookup_df), on="region")
```

# How This File Closes Out the MLOps and Big Data Folder

--> Every technique here addresses the same underlying theme as the MLOps Fundamentals file -- a model or pipeline isn't truly production-grade just because it works ONCE -- reliable experiment tracking and data versioning make it reproducible, a model registry and safe rollout strategies (shadow/canary) make updating it low-risk, rigorous statistical drift detection (KS Test, PSI) catches silent degradation the Fundamentals file's monitoring section only described conceptually, and Spark performance tuning ensures the underlying data infrastructure feeding all of this scales without becoming the actual bottleneck -- together, these are exactly the tools that separate a one-off successful training notebook from a genuinely operable, trustworthy, long-lived production ML system.
