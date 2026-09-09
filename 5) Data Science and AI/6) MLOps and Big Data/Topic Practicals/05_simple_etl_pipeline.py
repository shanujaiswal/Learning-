"""
05 - Simple ETL Pipeline (Extract - Transform - Load)
=========================================================
Chapter: Data Engineering Pipelines / ETL

This is an intentionally brief, introductory ETL example: it reads a small
CSV (Extract), cleans and reshapes it with pandas (Transform), and loads
the result into a local SQLite table (Load).

NOTE: This is deliberately kept simple. A much deeper, production-style
treatment of data engineering -- Airflow DAGs, dbt models, data warehouses,
incremental loads, orchestration, etc. -- lives in the separate folder:
    4) Data Science and AI\\7) Data Engineering\\Practical
Go there for the full pipeline treatment. This file just illustrates the
core ETL concept end-to-end in ~80 lines.

Install:
    pip install pandas

(sqlite3 is part of the Python standard library -- no install needed.)

Run:
    python 05_simple_etl_pipeline.py
"""

import os
import sqlite3

import pandas as pd

RAW_CSV_PATH = "raw_orders.csv"
DB_PATH = "etl_demo.db"
TABLE_NAME = "clean_orders"

RAW_CSV_CONTENT = """order_id,customer_name,order_amount,order_date
1,  Alice Smith ,120.50,2024-01-05
2,bob jones,,2024-01-06
3,Carla Ruiz,89.99,2024-01-06
4,Dev Patel,-15.00,2024-01-07
5,  Alice Smith ,120.50,2024-01-05
6,Erin Lee,250.00,not_a_date
"""


# ---------------------------------------------------------------------------
# EXTRACT
# ---------------------------------------------------------------------------
def extract() -> pd.DataFrame:
    """Read raw data from a source system -- here, a CSV file.

    In a real pipeline, extraction might instead pull from an API,
    a transactional database, or a message queue.
    """
    if not os.path.exists(RAW_CSV_PATH):
        with open(RAW_CSV_PATH, "w", encoding="utf-8") as f:
            f.write(RAW_CSV_CONTENT)
        print(f"Created sample raw source file: {RAW_CSV_PATH}")

    df = pd.read_csv(RAW_CSV_PATH)
    print(f"Extracted {len(df)} raw rows from {RAW_CSV_PATH}")
    return df


# ---------------------------------------------------------------------------
# TRANSFORM
# ---------------------------------------------------------------------------
def transform(df: pd.DataFrame) -> pd.DataFrame:
    """Clean and reshape the raw data into an analysis-ready table.

    Steps: trim whitespace, drop rows with missing/invalid amounts,
    drop rows with unparseable dates, remove duplicates, and drop
    negative order amounts (data-quality guard).
    """
    df = df.copy()

    df["customer_name"] = df["customer_name"].str.strip()

    df["order_date"] = pd.to_datetime(df["order_date"], errors="coerce")
    df["order_amount"] = pd.to_numeric(df["order_amount"], errors="coerce")

    before = len(df)
    df = df.dropna(subset=["order_amount", "order_date"])
    df = df[df["order_amount"] >= 0]
    df = df.drop_duplicates(subset=["customer_name", "order_amount", "order_date"])
    after = len(df)

    print(f"Transformed data: {before} rows -> {after} rows after cleaning")
    return df


# ---------------------------------------------------------------------------
# LOAD
# ---------------------------------------------------------------------------
def load(df: pd.DataFrame):
    """Load the cleaned data into a local SQLite table."""
    conn = sqlite3.connect(DB_PATH)
    try:
        df.to_sql(TABLE_NAME, conn, if_exists="replace", index=False)
        print(f"Loaded {len(df)} rows into '{TABLE_NAME}' table in {DB_PATH}")

        # Quick sanity check: read a few rows back.
        result = pd.read_sql(f"SELECT * FROM {TABLE_NAME} ORDER BY order_date", conn)
        print("\nPreview of loaded data:")
        print(result)
    finally:
        conn.close()


def main():
    raw_df = extract()
    clean_df = transform(raw_df)
    load(clean_df)


if __name__ == "__main__":
    main()
