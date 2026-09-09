# Batch vs Streaming -- Two Different Latency Contracts

--> Every pipeline covered so far in this folder (chapters 01-05) is **batch** -- it runs on a schedule (Airflow's `@daily`, chapter 03), processes a defined chunk of data, and finishes. That's the right design whenever "as of this morning" is a good enough answer.
--> **Streaming** processes events continuously, one (or a small "micro-batch" of) record at a time, as they arrive -- the right design when the ANSWER goes stale within minutes, not hours: fraud detection that has to block a transaction before it clears, a live "orders in the last 5 minutes" dashboard, real-time personalization while a user is still on the page.
--> The trade-off is real, not just a latency dial -- streaming infrastructure is meaningfully more complex to build, operate, and reason about (out-of-order events, exactly-once delivery, state that has to live somewhere between micro-batches) than a batch job that can simply be re-run from chapter 02's idempotent-upsert pattern if something goes wrong. The practical default across most data teams is still batch-first, reaching for streaming only for the specific use cases where the latency genuinely justifies that added operational cost -- not because streaming is more advanced or more "correct."

```
Batch:      [-------- collect all of today's data --------] -> process once -> done
Streaming:  event -> process -> event -> process -> event -> process -> ...  (continuous, no "done")
```

# Kafka -- the Standard Backbone for Streaming Data

--> **Apache Kafka** is a distributed, durable message log -- **producers** write events to named **topics**, and **consumers** read those events, independently and at their own pace, without producers and consumers ever talking to each other directly.
--> Kafka's core design choice is that a topic is an ordered, append-only LOG that consumers read from at whatever position they choose -- unlike a traditional message queue where a consumed message is gone, multiple independent consumer groups can each read the same topic from the beginning, or from wherever they last left off, entirely independently of each other.

```python
# Producer -- an application emitting an event every time an order is placed
from kafka import KafkaProducer
import json

producer = KafkaProducer(
    bootstrap_servers="kafka-broker:9092",
    value_serializer=lambda v: json.dumps(v).encode("utf-8"),
)

producer.send("orders", {"order_id": 4821, "customer_id": 199, "amount": 59.99})
producer.flush()
```

```python
# Consumer -- a separate process reading orders as they arrive, independent of the producer's timing
from kafka import KafkaConsumer
import json

consumer = KafkaConsumer(
    "orders",
    bootstrap_servers="kafka-broker:9092",
    value_deserializer=lambda v: json.loads(v.decode("utf-8")),
    group_id="fraud-detection-service",   # consumers sharing a group_id split the topic's partitions between them
)

for message in consumer:
    order = message.value
    # ... run a real-time fraud check on this single order as it arrives ...
```

--> A topic is split into **partitions** for scalability -- each partition is strictly ordered on its own, but there's no guaranteed order ACROSS partitions, which is why a producer typically sends related events (all events for one `customer_id`) to the same partition via a partition key, when their relative order actually matters downstream.
--> `group_id` is what enables horizontal scaling on the consumer side -- multiple consumer processes sharing the same `group_id` automatically divide a topic's partitions among themselves, so adding more consumers to the group increases total throughput without any code change, up to the number of partitions the topic has.

# Spark Structured Streaming -- Processing the Stream, Conceptually

--> Where Kafka durably transports events, **Spark Structured Streaming** is a processing engine that consumes them and applies transformations -- and its core idea is treating a stream as an unbounded, continuously-appending TABLE, so the exact same DataFrame operations covered for batch Spark jobs elsewhere in this vault apply to a live stream almost unchanged.

```python
from pyspark.sql import SparkSession
from pyspark.sql.functions import window, avg

spark = SparkSession.builder.appName("OrderMonitor").getOrCreate()

raw_stream = (
    spark.readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", "kafka-broker:9092")
    .option("subscribe", "orders")
    .load()
)

# Same DataFrame API as a normal batch job -- group into 5-minute windows and average order amount
windowed_avg = (
    raw_stream
    .groupBy(window("timestamp", "5 minutes"))
    .agg(avg("amount").alias("avg_order_amount"))
)

query = windowed_avg.writeStream.outputMode("update").format("console").start()
query.awaitTermination()
```

--> **Micro-batching** is how Structured Streaming actually executes this under the hood -- rather than processing one event in complete isolation the instant it arrives, it collects events into very small time-bounded batches (configurable down to sub-second) and runs the same batch-style query engine on each one -- a deliberate middle ground that gets most of streaming's latency benefit while reusing the mature, well-optimized batch execution engine rather than a wholly separate real-time-only code path.
--> **Windowing** (`window("timestamp", "5 minutes")` above) is the standard way to make aggregation meaningful over an unbounded stream -- since a stream never "ends" the way a batch table does, `GROUP BY` has to be scoped to a specific time window rather than the whole (infinite) stream, directly analogous to the `PARTITION BY` scoping in the Data Analyst folder's window function file, just scoped by TIME instead of by a key column.
--> **Watermarking** handles late-arriving events (the same problem chapter 02's Deep Dive covers for batch pipelines, now on a much shorter timescale) by declaring how long the engine should keep waiting for stragglers before finalizing a window's result and moving on -- an explicit trade-off between waiting longer for more complete data and reporting results sooner.

# Data Lakes and the Lakehouse

--> A **data lake** stores raw files (Parquet, JSON, CSV) directly in cheap object storage (S3, GCS, Azure Blob) with no enforced schema or transactional guarantees -- the opposite philosophy from the structured, schema-enforced warehouse tables covered in chapter 04, and historically valued for being cheap and able to hold literally anything (unstructured logs, images, raw JSON) without a schema having to be designed up front.
--> The classic data lake's weakness is exactly what a warehouse enforces by design -- no `ACID` transactions (a job reading a table while another job writes to it can see a half-written, inconsistent result), no schema enforcement (a producer can silently start writing a differently-shaped file into the same "table" and nothing stops it), and no efficient way to `UPDATE`/`DELETE` individual rows the way an upsert (chapter 02) requires -- a data lake alone was historically append-only and read-only in practice.
--> A **lakehouse** is the architectural answer to that weakness -- add a transactional metadata layer ON TOP of plain files in object storage, giving the lake warehouse-like guarantees (ACID transactions, schema enforcement, row-level updates/deletes, time travel) while keeping the lake's original cost advantage and its ability to hold unstructured data a warehouse table format was never designed for.

## Delta Lake, Iceberg, and Hudi -- the Lakehouse Table Formats

--> All three solve the same core problem -- a transaction log recording every change made to a table of files, so a query engine can read a table like it read a warehouse table (a consistent snapshot, real schema, `MERGE`/upsert support) even though the underlying data is still just Parquet files sitting in object storage.

```python
# Delta Lake -- an ACID upsert directly against files in a data lake, no separate warehouse needed
from delta.tables import DeltaTable

delta_table = DeltaTable.forPath(spark, "s3://data-lake/orders")

delta_table.alias("target").merge(
    new_orders_df.alias("source"),
    "target.order_id = source.order_id",
) \
.whenMatchedUpdateAll() \
.whenNotMatchedInsertAll() \
.execute()
```

--> This `MERGE` is the exact same upsert idea as chapter 02's `ON CONFLICT DO UPDATE` Postgres pattern and chapter 04's warehouse `MERGE` -- Delta Lake brings that same idempotent-write guarantee to plain files in a data lake, which classic Parquet-on-S3 with no table-format layer never supported.
--> **Delta Lake** (from Databricks/Spark's ecosystem), **Apache Iceberg** (originally Netflix, now widely engine-agnostic), and **Apache Hudi** (originally Uber) differ mainly in ecosystem fit and specific optimization trade-offs (Iceberg emphasizes broad multi-engine compatibility; Hudi emphasizes fast incremental upserts for near-real-time lakes) -- but the underlying value proposition, a transaction log giving warehouse-like guarantees over lake-cheap file storage, is shared by all three, and picking between them is largely a question of which query engines and streaming tools a given stack already uses.
--> **Time travel** -- because every change is a new entry in the transaction log rather than an in-place overwrite, all three formats can query a table AS OF a previous version or timestamp -- directly useful for reproducing a report exactly as it looked on a past date, or recovering from a bad transformation run by simply reading the version from before it ran.

## Medallion Architecture -- Bronze, Silver, Gold

--> A layering convention (popularized by Databricks, but conceptually applicable to any lakehouse) that organizes a lake into three progressively more refined zones -- directly analogous to the raw-staging vs cleaned-modeled distinction chapter 01 draws for warehouse storage, just made explicit as three named layers instead of two.

```
Bronze (raw)                Silver (cleaned)              Gold (business-ready)
-----------------           -----------------             -----------------
Exact copy of the           Deduplicated, schema-          Aggregated, joined,
source data, as-is,         enforced, type-cast,           denormalized tables
including bad/duplicate      bad rows filtered/quarantined  matching the star
rows -- nothing discarded                                   schema pattern from
here, purely an audit trail                                  chapter 05
```

--> **Bronze** preserves the raw ingested data untouched, exactly the same rationale as ELT's "load raw data first" principle from chapter 02 -- if a downstream transformation bug is discovered weeks later, the untouched bronze copy means reprocessing from the true original source rather than from an already-transformed, possibly-corrupted intermediate.
--> **Silver** applies the cleaning, deduplication, and schema conformance that turns bronze's raw mess into trustworthy, well-typed tables -- comparable to a warehouse's staging schema, but sitting in the lake/lakehouse rather than inside the warehouse proper.
--> **Gold** is the final, business-consumable layer -- aggregated and joined into the fact/dimension shapes covered in chapter 05, exactly what a BI tool or analyst would actually query, with bronze and silver existing purely as internal scaffolding most end users never touch directly.

# File Formats and Their Performance Implications

--> The file FORMAT data is stored in (independent of whether it sits in a lake, a lakehouse, or gets loaded into a warehouse) has real, measurable performance consequences -- this is the file-level version of the row-based-vs-columnar storage argument chapter 04 makes at the warehouse-table level.

```
Format    Layout      Schema         Compression    Best for
-------   ---------   -----------    -----------    -----------------------------------
CSV       row-based   none (text)    poor           Human-readable interchange only --
                                                       avoid for anything performance-sensitive
JSON      row-based   flexible/none  poor            Nested/semi-structured data, APIs
Avro      row-based   enforced       good            Write-heavy pipelines, schema evolution,
                                                       Kafka payloads (schema travels with the data)
Parquet   columnar    enforced       excellent       Analytical reads -- the standard lake/lakehouse
                                                       default, matches chapter 04's columnar argument
ORC       columnar    enforced       excellent       Similar to Parquet -- historically favored in the
                                                       Hive/Hadoop ecosystem specifically
```

--> **Parquet** is the de facto default for data lakes and lakehouse bronze/silver/gold tables precisely because its columnar layout gives the exact same "only read the columns a query actually needs" benefit chapter 04 describes for warehouse tables, now applied to plain files -- a `SELECT amount FROM parquet_orders` style read touches only the `amount` column's physical bytes, not every field of every row.
--> **Avro** is row-based by design and is the more common choice specifically for data IN MOTION rather than data at rest -- a Kafka message is naturally one complete record at a time (matching row-based layout), and Avro's schema travels alongside the data itself, which matters enormously for schema evolution (a producer safely adding a new optional field without breaking consumers still running the old schema) in a way a raw JSON payload with no enforced schema can't guarantee.
--> The practical rule of thumb: Avro (or JSON, for less rigor) for STREAMING transport where records arrive one at a time and get processed immediately; Parquet (or ORC) for STORAGE and analytical querying where the columnar layout's read-efficiency actually pays off -- which is exactly why a real pipeline commonly converts Avro-encoded Kafka events into Parquet files once they land in the bronze layer.

# Change Data Capture -- Streaming Straight From a Database's Own Log

--> Every ingestion pattern in chapter 02 pulls data by QUERYING a source (an API call, a `SELECT ... WHERE updated_at > :watermark`) -- **Change Data Capture (CDC)** instead reads the source database's own internal transaction/replication LOG directly, capturing every insert/update/delete as it happens, without ever running a query against the live production tables at all.
--> This directly avoids the exact OLTP-contention hazard chapter 01 warns about -- a polling query, however well incremental (chapter 02), still touches the production database on every run; log-based CDC reads a stream the database ALREADY produces for its own internal replication purposes, imposing near-zero additional load on the source system.
--> **Debezium** is the standard open-source tool for this -- it attaches to a source database's replication log (Postgres's WAL, MySQL's binlog) and publishes every row-level change as a structured event onto a Kafka topic, turning "watch this table for changes" into a live event stream any downstream consumer can subscribe to.

```
Postgres write-ahead log (WAL)
        |
        v
   Debezium connector  -->  Kafka topic "orders.public.orders"
                                    |
                                    v
                    Consumers: streaming ETL job, cache invalidation,
                    search-index updater, audit log -- all reading the
                    SAME change stream independently
```

--> CDC captures every intermediate state change (an order created, then updated, then updated again), not just the final state a polling query would see on its next run -- valuable when the intermediate states themselves matter (an audit trail, an analytics use case tracking exactly how long an order sat in each status), something incremental polling based on a single `updated_at` timestamp inherently can't reconstruct.
--> CDC events land as exactly the kind of continuous stream Kafka/Spark Structured Streaming (above) are built to consume -- CDC is commonly the actual SOURCE of the events flowing through a streaming pipeline in practice, connecting this ingestion pattern directly back to the streaming architecture earlier in this file.

# Data Quality and Observability as Their Own Discipline

--> dbt's `unique`/`not_null`/`relationships` tests (chapter 05) check specific, individually-declared assertions about a model at build time. **Data observability** is the broader, ongoing discipline of continuously monitoring a pipeline's HEALTH -- not just "does this model pass its tests today," but "is this data still arriving on time, does its volume/shape look normal, and would anyone even notice if it silently stopped."

## Great Expectations -- Declarative Data Quality, Conceptually

```python
import great_expectations as gx

context = gx.get_context()
validator = context.sources.pandas_default.read_csv("orders.csv")

validator.expect_column_values_to_not_be_null("order_id")
validator.expect_column_values_to_be_between("amount", min_value=0, max_value=100_000)
validator.expect_column_values_to_be_in_set("status", ["pending", "shipped", "delivered", "cancelled"])

results = validator.validate()
```

--> This is conceptually the same declarative-assertion idea as dbt's schema tests (chapter 05), generalized beyond just uniqueness/not-null/referential-integrity checks into arbitrary statistical and business-rule expectations about a dataset's actual values -- and, critically, runnable against data BEFORE it's even loaded into a warehouse table at all, not only after a dbt model has already materialized.
--> A validation suite like this is typically wired into the pipeline itself (an Airflow task, chapter 03, that runs validation and fails the DAG run -- triggering the retry/alert behavior chapter 03 already covers -- if expectations aren't met) rather than run manually, turning "is this data actually trustworthy" into an automated gate rather than something discovered only when a stakeholder complains a dashboard number looks wrong.

## Freshness Monitoring

--> Checks a much simpler but extremely common failure mode -- not "is the data wrong," but "did the data even ARRIVE." A table that was supposed to update at 6 AM every day and silently didn't (an upstream API went down, a DAG failed and the alert (chapter 03) got missed) produces no error at all in most systems -- yesterday's data just quietly sits there looking normal, and a stale dashboard is a much harder failure to notice than an outright crash.
--> Freshness monitoring tracks the time since a table's `MAX(updated_at)` (or a dedicated pipeline run log) and alerts when that gap exceeds an expected threshold -- directly extending chapter 03's alerting discussion, but monitoring for the ABSENCE of an expected event rather than the presence of a failed one.

## Anomaly Detection on Pipelines

--> Beyond fixed rules (a value must be `>= 0`), anomaly detection flags when a pipeline metric deviates unusually far from its OWN historical pattern -- row count suddenly dropping 90% between runs, a numeric column's average suddenly doubling -- catching problems no one thought to write an explicit Great Expectations rule for in advance.
--> This is the same underlying idea as statistical outlier detection covered in the Data Science folder's EDA/Data Cleaning files, just applied to PIPELINE METADATA (row counts, null rates, run duration) over time rather than to a dataset's own business columns -- treating the pipeline's health metrics as a time series worth monitoring in their own right, connecting directly to this folder's own chapter 03 alerting and the Data Science folder's Time Series file for the underlying anomaly-detection technique.

# Data Governance, Cataloging, and Lineage

--> As a data stack grows past a handful of tables, two questions become surprisingly hard to answer without dedicated tooling -- "what does this column actually mean and can I trust it," and "if I change this upstream table, what breaks downstream." Governance/cataloging/lineage tools exist specifically to answer both.
--> A **data catalog** (DataHub, Amundsen, or a managed warehouse's built-in catalog) is a searchable inventory of every table and column across the stack -- who owns it, what it means, how fresh it is, and often its dbt-generated documentation (chapter 05) surfaced directly alongside it -- turning "ask in Slack and hope someone remembers" into a searchable, self-serve reference.
--> **Lineage** traces exactly how data flows from a source table, through every transformation, into a final report -- **table-level lineage** shows "this dashboard is built from `fact_orders`, which is built from `stg_orders` and `stg_customers`"; **column-level lineage** goes further, showing that a specific dashboard number is built from exactly THESE upstream columns and no others.
--> dbt's own `ref()`-based dependency graph (chapter 05) is a genuine source of automatic lineage information -- a catalog tool can ingest dbt's compiled manifest and render that dependency graph visually, without anyone manually documenting it by hand, directly reusing the same dependency metadata dbt already computes to decide model run order.
--> Column-level lineage answers the specific, high-stakes question governance exists for -- "if I change or drop this column, exactly which downstream dashboards and models break" -- turning what would otherwise be a scary, blind schema change into a scoped, checkable one, the data-modeling equivalent of a code IDE's "find all references" before renaming a function.

# Reverse ETL -- Closing the Loop Back Into Operational Tools

--> Every pipeline direction covered in chapters 01-05 (and everything above in this file) moves data FROM source systems INTO the warehouse/lakehouse for analysis. **Reverse ETL** deliberately moves it back the OTHER way -- from the warehouse, where all the cleaning/joining/modeling already happened, back OUT into the operational SaaS tools a business team actually works in (Salesforce, HubSpot, an email/marketing platform, a support tool).
--> The motivating problem it solves -- a customer's true lifetime value, computed correctly in the warehouse from a well-modeled `fact_orders` (chapter 05) joined against marketing spend, is genuinely useful to a sales rep deciding who to prioritize -- but that rep lives in Salesforce all day and will never run a warehouse query to get it. Reverse ETL syncs that already-computed warehouse column directly into a custom field on the Salesforce record itself, so it's simply there when the rep looks.

```
Warehouse (gold-layer table)  --Reverse ETL sync-->  Salesforce custom field
    customer_ltv_score                                  "Lifetime Value Score"
```

--> Tools like **Census** and **Hightouch** are the reverse-ETL-specific equivalents of Fivetran/Airbyte from chapter 01 -- managed connectors, just running the modern data stack's ingestion direction backward, syncing a warehouse column into an operational tool's field on a schedule rather than pulling a source system's data INTO the warehouse.
--> This closes the loop the modern-data-stack diagram in chapter 01 draws left-to-right (source -> ingestion -> storage -> transformation -> serving) into something closer to a full circle -- the warehouse becomes not just a passive read destination for dashboards, but an active input back into the operational tools that originally generated the raw data in the first place, and the exact same idempotent-upsert discipline from chapter 02 applies just as much to a reverse ETL sync (re-running it must not create duplicate Salesforce records) as it does to any forward-direction load.
