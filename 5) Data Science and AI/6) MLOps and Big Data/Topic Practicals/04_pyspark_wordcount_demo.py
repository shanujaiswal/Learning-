"""
04 - PySpark Word Count Demo
===============================
Chapter: Big Data Ecosystem (Hadoop / Spark)

The classic "hello world" of big-data processing: count word occurrences
across a text file using Spark's DataFrame API. Spark runs here in
"local mode", meaning it simulates a cluster using threads on your own
machine -- no Hadoop cluster, YARN, or external Spark cluster is required
to run this demo. In production, the same code would run unmodified on a
real multi-node cluster by changing the `.master(...)` setting.

Install (optional/large -- only needed for this file):
    pip install pyspark

Requirements:
    - A Java runtime (JDK 8/11/17) must be installed and on PATH; PySpark
      needs it under the hood even in local mode.

Run:
    python 04_pyspark_wordcount_demo.py

What it does:
    1. Writes a tiny sample text file (if it doesn't already exist).
    2. Starts a local SparkSession.
    3. Reads the text file into a Spark DataFrame (one row per line).
    4. Splits lines into words, groups by word, and counts occurrences.
    5. Prints the word counts sorted by frequency, descending.
"""

import os

SAMPLE_TEXT_PATH = "sample_text.txt"

SAMPLE_TEXT = """\
Spark is a fast and general engine for large scale data processing.
Spark provides high level APIs in Java Scala Python and R.
Spark runs on Hadoop Mesos Kubernetes standalone or in the cloud.
Spark can access diverse data sources including HDFS S3 and Hive.
Fast means Spark keeps data in memory whenever possible.
General means Spark supports SQL streaming and machine learning workloads.
"""


def ensure_sample_file():
    if not os.path.exists(SAMPLE_TEXT_PATH):
        with open(SAMPLE_TEXT_PATH, "w", encoding="utf-8") as f:
            f.write(SAMPLE_TEXT)
        print(f"Created sample input file: {SAMPLE_TEXT_PATH}")
    return SAMPLE_TEXT_PATH


def run_word_count(text_path: str):
    from pyspark.sql import SparkSession
    from pyspark.sql import functions as F

    spark = (
        SparkSession.builder
        .appName("WordCountDemo")
        .master("local[*]")  # use all available local CPU cores, no cluster needed
        .getOrCreate()
    )
    spark.sparkContext.setLogLevel("WARN")  # quiet down Spark's default verbose logging

    # One row per line of the text file.
    lines_df = spark.read.text(text_path)

    words_df = (
        lines_df
        # Lowercase and strip punctuation so "Spark" and "Spark." count together.
        .select(F.explode(F.split(F.lower(F.regexp_replace("value", "[^a-zA-Z\\s]", "")), r"\s+")).alias("word"))
        .filter(F.col("word") != "")
    )

    counts_df = (
        words_df
        .groupBy("word")
        .count()
        .orderBy(F.desc("count"))
    )

    print("\nWord counts (top results):")
    counts_df.show(20, truncate=False)

    spark.stop()


def main():
    text_path = ensure_sample_file()
    run_word_count(text_path)


if __name__ == "__main__":
    main()
