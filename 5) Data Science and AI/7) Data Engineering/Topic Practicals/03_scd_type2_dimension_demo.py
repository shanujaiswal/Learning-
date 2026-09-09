"""
03_scd_type2_dimension_demo.py

Demonstrates a SLOWLY CHANGING DIMENSION (SCD) TYPE 2 by hand: a
dim_customers table where a customer's region changes over time, handled by
INSERTING A NEW ROW with valid_from/valid_to dates rather than overwriting
the existing row -- so historical facts can still be joined to the
dimension value that was TRUE AT THE TIME the fact occurred.

Covers Theory chapter:
    05 Data Modeling -- Star Schemas and dbt.md
        - "Deep Dive -- Slowly Changing Dimensions"

The naive (WRONG) alternative would be:
    UPDATE dim_customers SET region = 'EU' WHERE customer_id = 501;
That destroys the historical fact that, at the time of the customer's
earlier order, they were in a DIFFERENT region -- every past report re-run
after that UPDATE would silently attribute old orders to the new region.

SCD Type 2 instead:
    1. Closes out the current row (sets valid_to = the change date).
    2. Inserts a brand-new row with a new surrogate key, the updated
       attribute, valid_from = the change date, and valid_to = NULL
       ("open"/current).

A fact table stores the surrogate customer_key that was current AT ORDER
TIME (not the natural customer_id) -- that's what makes the historically
correct join possible.

Run:  python 03_scd_type2_dimension_demo.py
Produces: scd_warehouse.db (in the same folder).
"""

import os
import sqlite3

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scd_warehouse.db")


def create_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        DROP TABLE IF EXISTS fact_orders;
        DROP TABLE IF EXISTS dim_customers;

        -- surrogate customer_key (PK) is DIFFERENT from the natural customer_id --
        -- one customer_id can have multiple customer_key rows over time (one per version).
        CREATE TABLE dim_customers (
            customer_key   INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id    INTEGER NOT NULL,     -- stable natural/business key
            customer_name  TEXT NOT NULL,
            region         TEXT NOT NULL,
            valid_from     TEXT NOT NULL,
            valid_to       TEXT                  -- NULL means "current"/open row
        );

        CREATE TABLE fact_orders (
            order_id       INTEGER PRIMARY KEY,
            customer_key   INTEGER NOT NULL REFERENCES dim_customers(customer_key),
            order_date     TEXT NOT NULL,
            amount         REAL NOT NULL
        );
        """
    )
    conn.commit()


def seed_initial_dimension_and_facts(conn: sqlite3.Connection) -> None:
    """Customer 501 starts out in the 'US-WEST' region. An order is placed
    while that row is current -- the fact stores THAT row's surrogate key."""
    cur = conn.cursor()

    cur.execute(
        """
        INSERT INTO dim_customers (customer_id, customer_name, region, valid_from, valid_to)
        VALUES (501, 'Alice Chen', 'US-WEST', '2025-01-01', NULL)
        """
    )
    conn.commit()

    original_key = cur.execute(
        "SELECT customer_key FROM dim_customers WHERE customer_id = 501 AND valid_to IS NULL"
    ).fetchone()[0]

    # Old fact: placed on 2026-03-10, while customer 501 was still US-WEST.
    cur.execute(
        """
        INSERT INTO fact_orders (order_id, customer_key, order_date, amount)
        VALUES (9001, ?, '2026-03-10', 129.99)
        """,
        (original_key,),
    )
    conn.commit()
    print(f"Seeded dim_customers row (customer_key={original_key}, region=US-WEST, valid_to=NULL)")
    print("Seeded fact_orders row 9001 on 2026-03-10, pointing at that customer_key\n")


def apply_scd_type2_region_change(
    conn: sqlite3.Connection, customer_id: int, new_region: str, change_date: str
) -> int:
    """The SCD Type 2 update itself: close the old row, insert a new one.

    Returns the new (current) customer_key.
    """
    cur = conn.cursor()

    # Step 1: close out the currently-open row for this customer_id.
    cur.execute(
        """
        UPDATE dim_customers
        SET valid_to = ?
        WHERE customer_id = ? AND valid_to IS NULL
        """,
        (change_date, customer_id),
    )

    # Step 2: fetch the row we just closed, to carry forward its unchanged attributes.
    old_row = cur.execute(
        """
        SELECT customer_name FROM dim_customers
        WHERE customer_id = ? AND valid_to = ?
        """,
        (customer_id, change_date),
    ).fetchone()
    customer_name = old_row[0]

    # Step 3: insert a NEW row (new surrogate key) with the updated attribute,
    # open-ended (valid_to = NULL) -- this is now the current version.
    cur.execute(
        """
        INSERT INTO dim_customers (customer_id, customer_name, region, valid_from, valid_to)
        VALUES (?, ?, ?, ?, NULL)
        """,
        (customer_id, customer_name, new_region, change_date),
    )
    conn.commit()

    new_key = cur.execute(
        "SELECT customer_key FROM dim_customers WHERE customer_id = ? AND valid_to IS NULL",
        (customer_id,),
    ).fetchone()[0]
    return new_key


def add_new_order_after_change(conn: sqlite3.Connection, new_customer_key: int) -> None:
    """A new order placed AFTER the region change points at the NEW surrogate key."""
    conn.execute(
        """
        INSERT INTO fact_orders (order_id, customer_key, order_date, amount)
        VALUES (9002, ?, '2026-08-05', 75.00)
        """,
        (new_customer_key,),
    )
    conn.commit()


def query_facts_with_point_in_time_region(conn: sqlite3.Connection) -> list[tuple]:
    """The payoff query: join each fact row to the dimension row whose
    validity window CONTAINS the fact's order_date -- i.e. the region that
    was true AT THAT TIME, not the customer's current region.

    order_date BETWEEN valid_from AND COALESCE(valid_to, '9999-12-31')
    is the standard SCD Type 2 point-in-time join.
    """
    cur = conn.cursor()
    cur.execute(
        """
        SELECT
            f.order_id,
            f.order_date,
            f.amount,
            dc.region        AS region_at_order_time,
            dc.valid_from,
            dc.valid_to
        FROM fact_orders f
        JOIN dim_customers dc
            ON f.customer_key = dc.customer_key
        WHERE dc.customer_id = 501
           OR f.customer_key IN (SELECT customer_key FROM dim_customers WHERE customer_id = 501)
        ORDER BY f.order_date
        """
    )
    return cur.fetchall()


def show_full_dimension_history(conn: sqlite3.Connection) -> list[tuple]:
    cur = conn.cursor()
    cur.execute(
        """
        SELECT customer_key, customer_id, customer_name, region, valid_from, valid_to
        FROM dim_customers
        WHERE customer_id = 501
        ORDER BY valid_from
        """
    )
    return cur.fetchall()


def main() -> None:
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)

    conn = sqlite3.connect(DB_PATH)
    try:
        create_schema(conn)
        seed_initial_dimension_and_facts(conn)

        print("--- Customer 501 moves from US-WEST to EU on 2026-08-01 ---")
        print("(SCD Type 2: close the old row, insert a NEW row -- never UPDATE region in place)\n")
        new_key = apply_scd_type2_region_change(
            conn, customer_id=501, new_region="EU", change_date="2026-08-01"
        )
        add_new_order_after_change(conn, new_key)

        print("Full dim_customers history for customer_id=501:")
        for row in show_full_dimension_history(conn):
            print(f"  customer_key={row[0]} region={row[3]:<8} valid_from={row[4]} valid_to={row[5]}")

        print("\nFacts joined to the dimension value TRUE AT ORDER TIME:")
        print(f"{'order_id':<10}{'order_date':<12}{'amount':>8}   {'region_at_order_time':<20}")
        for order_id, order_date, amount, region, valid_from, valid_to in query_facts_with_point_in_time_region(conn):
            print(f"{order_id:<10}{order_date:<12}{amount:>8.2f}   {region:<20}")

        # ---- Proof it's correct: the OLD order (9001, before the change) must
        #      still show US-WEST, and the NEW order (9002, after the change)
        #      must show EU -- even though the customer's CURRENT region is EU.
        facts = query_facts_with_point_in_time_region(conn)
        old_order = next(r for r in facts if r[0] == 9001)
        new_order = next(r for r in facts if r[0] == 9002)
        assert old_order[3] == "US-WEST", "Old fact should still join to the historical US-WEST region!"
        assert new_order[3] == "EU", "New fact should join to the current EU region!"
        print(
            "\nCorrectness check passed: order 9001 (placed before the change) still shows "
            "US-WEST, while order 9002 (placed after) shows EU -- history was preserved."
        )

    finally:
        conn.close()


if __name__ == "__main__":
    main()
