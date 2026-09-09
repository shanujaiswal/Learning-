"""
01_idempotent_extract_and_load.py

Simulates a real ELT extract-and-load step:

    "source API" (hardcoded list of dicts, standing in for a requests.get() response)
                        |
                        v
              idempotent UPSERT load
                        |
                        v
              orders.db :: staging_orders  (SQLite, standing in for a warehouse's staging schema)

Covers Theory chapter:
    02 ETL and ELT Pipelines.md
        - "Idempotency -- The Property Every Pipeline Needs"
        - "A Real Idempotent Extraction Script"
        - "Incremental Extraction vs Full Refresh"

The whole point of this script is the DEMONSTRATION at the bottom of main():
the exact same batch is loaded TWICE in a row, and we prove the table ends up
with the same row count and the same (updated) data both times -- no duplicates,
no double-counted rows. That's what "idempotent" means in practice.

Run:  python 01_idempotent_extract_and_load.py
Produces: orders.db (in the same folder).
"""

import os
import sqlite3
from datetime import date

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "orders.db")


def extract_orders_from_source_api(since: date) -> list[dict]:
    """EXTRACT step.

    Stands in for something like:

        resp = requests.get(
            "https://api.example.com/v1/orders",
            params={"created_since": since.isoformat()},
            headers={"Authorization": f"Bearer {api_key}"},
        )
        resp.raise_for_status()
        return resp.json()["orders"]

    Here the "API response" is just a hardcoded list of dicts, keyed on a
    stable business key (order_id) exactly like a real source system would
    provide -- that stable key is what makes the upsert in load_orders()
    possible at all.
    """
    return [
        {"order_id": 1001, "customer_id": 501, "amount": 129.99, "status": "shipped", "updated_at": "2026-08-01T10:15:00"},
        {"order_id": 1002, "customer_id": 502, "amount": 39.50, "status": "pending", "updated_at": "2026-08-01T11:02:00"},
        {"order_id": 1003, "customer_id": 501, "amount": 89.00, "status": "delivered", "updated_at": "2026-08-01T12:47:00"},
        {"order_id": 1004, "customer_id": 503, "amount": 15.25, "status": "cancelled", "updated_at": "2026-08-01T13:30:00"},
        {"order_id": 1005, "customer_id": 504, "amount": 249.00, "status": "shipped", "updated_at": "2026-08-01T14:05:00"},
    ]


def create_staging_table(conn: sqlite3.Connection) -> None:
    """Create the staging table once. NOT dropped/recreated on every run --
    a real staging table persists across pipeline runs; only the rows inside
    it get upserted incrementally."""
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS staging_orders (
            order_id     INTEGER PRIMARY KEY,
            customer_id  INTEGER NOT NULL,
            amount       REAL NOT NULL,
            status       TEXT NOT NULL,
            updated_at   TEXT NOT NULL,
            loaded_at    TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """
    )
    conn.commit()


def load_orders(orders: list[dict], conn: sqlite3.Connection) -> None:
    """LOAD step -- idempotent UPSERT keyed on order_id.

    SQLite's `INSERT ... ON CONFLICT (col) DO UPDATE` is the equivalent of
    Postgres's `ON CONFLICT DO UPDATE` / Snowflake-BigQuery's `MERGE`
    (Theory chapter 02 + chapter 04). Whichever warehouse's syntax you use,
    the principle is identical: match on a unique key, update if found,
    insert if not -- so running this function N times on the same batch
    leaves the table in exactly the same state as running it once.
    """
    cur = conn.cursor()
    for order in orders:
        cur.execute(
            """
            INSERT INTO staging_orders (order_id, customer_id, amount, status, updated_at)
            VALUES (:order_id, :customer_id, :amount, :status, :updated_at)
            ON CONFLICT (order_id) DO UPDATE SET
                customer_id = excluded.customer_id,
                amount      = excluded.amount,
                status      = excluded.status,
                updated_at  = excluded.updated_at
            """,
            order,
        )
    conn.commit()


def snapshot(conn: sqlite3.Connection) -> tuple[int, list[tuple]]:
    """Returns (row_count, all_rows_sorted_by_order_id) for before/after comparison."""
    cur = conn.cursor()
    count = cur.execute("SELECT COUNT(*) FROM staging_orders").fetchone()[0]
    rows = cur.execute(
        "SELECT order_id, customer_id, amount, status, updated_at FROM staging_orders ORDER BY order_id"
    ).fetchall()
    return count, rows


def main() -> None:
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)

    conn = sqlite3.connect(DB_PATH)
    try:
        create_staging_table(conn)

        batch = extract_orders_from_source_api(since=date(2026, 8, 1))

        # ---- RUN 1: first load of the batch --------------------------------
        load_orders(batch, conn)
        count_after_run1, rows_after_run1 = snapshot(conn)
        print(f"After run 1: {count_after_run1} rows in staging_orders")

        # ---- Simulate a late-arriving update: order 1002 ships, and the
        #      pipeline is simply re-run on the SAME batch window (exactly the
        #      "network blip mid-load" / "manual backfill" scenario from the
        #      Theory chapter). A non-idempotent, blind-INSERT pipeline would
        #      duplicate every row here.
        batch[1]["status"] = "shipped"
        batch[1]["updated_at"] = "2026-08-01T16:40:00"

        # ---- RUN 2: re-run the SAME extract+load on the (slightly updated) batch
        load_orders(batch, conn)
        count_after_run2, rows_after_run2 = snapshot(conn)
        print(f"After run 2: {count_after_run2} rows in staging_orders")

        # ---- Proof of idempotency ------------------------------------------
        assert count_after_run1 == count_after_run2 == len(batch), (
            "Row count changed between runs -- the load is NOT idempotent!"
        )
        print(f"\nRow count stayed at {count_after_run2} across both runs -- no duplicates. Idempotency holds.")

        updated_row = next(r for r in rows_after_run2 if r[0] == 1002)
        print(f"order_id 1002 was updated in place (not duplicated): {updated_row}")

        print("\nFull table after both runs:")
        for row in rows_after_run2:
            print(f"  {row}")

    finally:
        conn.close()


if __name__ == "__main__":
    main()
