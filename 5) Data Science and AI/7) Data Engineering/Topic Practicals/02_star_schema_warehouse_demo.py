"""
02_star_schema_warehouse_demo.py

Builds a small STAR SCHEMA in SQLite (standing in for a real warehouse such
as Snowflake/BigQuery/Redshift) and runs an analytical query against it.

    dim_customers        dim_products
           \\                  /
            \\                /
             fact_orders (one row per order EVENT)
                    |
                dim_date

Covers Theory chapter:
    05 Data Modeling -- Star Schemas and dbt.md
        - "Star Schemas -- Fact Tables and Dimension Tables"
        - "Why Warehouses Deliberately Denormalize"

One fact table (fact_orders) holds numeric measures (amount, quantity) plus
foreign keys out to three dimension tables. The dimensions are small,
descriptive, and deliberately denormalized (e.g. dim_products repeats the
category string rather than normalizing it into its own table) -- exactly
the trade-off the Theory chapter describes as the warehouse default.

The demo query at the bottom joins fact_orders out to all three dimensions
and does a GROUP BY to answer a real analytical question: total revenue by
product category and customer segment.

Run:  python 02_star_schema_warehouse_demo.py
Produces: warehouse.db (in the same folder).
"""

import os
import sqlite3

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "warehouse.db")


def create_schema(conn: sqlite3.Connection) -> None:
    """DDL for the star schema: 3 dimension tables + 1 fact table."""
    conn.executescript(
        """
        DROP TABLE IF EXISTS fact_orders;
        DROP TABLE IF EXISTS dim_customers;
        DROP TABLE IF EXISTS dim_products;
        DROP TABLE IF EXISTS dim_date;

        CREATE TABLE dim_customers (
            customer_key   INTEGER PRIMARY KEY,
            customer_name  TEXT NOT NULL,
            segment        TEXT NOT NULL      -- e.g. 'consumer', 'enterprise' -- denormalized, repeated across rows on purpose
        );

        CREATE TABLE dim_products (
            product_key    INTEGER PRIMARY KEY,
            product_name   TEXT NOT NULL,
            category       TEXT NOT NULL      -- denormalized: category string repeated rather than a separate categories table
        );

        CREATE TABLE dim_date (
            date_key       INTEGER PRIMARY KEY,   -- YYYYMMDD surrogate key, standard warehouse convention
            calendar_date  TEXT NOT NULL,
            day_of_week    TEXT NOT NULL,
            fiscal_quarter TEXT NOT NULL
        );

        CREATE TABLE fact_orders (
            order_id       INTEGER PRIMARY KEY,   -- one row per business EVENT (an order)
            customer_key   INTEGER NOT NULL REFERENCES dim_customers(customer_key),
            product_key    INTEGER NOT NULL REFERENCES dim_products(product_key),
            date_key       INTEGER NOT NULL REFERENCES dim_date(date_key),
            amount         REAL NOT NULL,         -- numeric measure
            quantity       INTEGER NOT NULL       -- numeric measure
        );
        """
    )
    conn.commit()


def load_sample_data(conn: sqlite3.Connection) -> None:
    """Real (if small) sample data for every table -- dimensions first, then the fact table
    that references them, matching the order a real warehouse load would enforce via FKs."""
    cur = conn.cursor()

    cur.executemany(
        "INSERT INTO dim_customers (customer_key, customer_name, segment) VALUES (?, ?, ?)",
        [
            (1, "Alice Chen", "consumer"),
            (2, "Bob Martinez", "enterprise"),
            (3, "Priya Nair", "consumer"),
            (4, "Global Retail Corp", "enterprise"),
        ],
    )

    cur.executemany(
        "INSERT INTO dim_products (product_key, product_name, category) VALUES (?, ?, ?)",
        [
            (10, "Wireless Mouse", "electronics"),
            (11, "Standing Desk", "furniture"),
            (12, "USB-C Hub", "electronics"),
            (13, "Office Chair", "furniture"),
        ],
    )

    cur.executemany(
        "INSERT INTO dim_date (date_key, calendar_date, day_of_week, fiscal_quarter) VALUES (?, ?, ?, ?)",
        [
            (20260701, "2026-07-01", "Wednesday", "Q3-2026"),
            (20260715, "2026-07-15", "Wednesday", "Q3-2026"),
            (20260802, "2026-08-02", "Sunday", "Q3-2026"),
            (20260805, "2026-08-05", "Wednesday", "Q3-2026"),
        ],
    )

    cur.executemany(
        """
        INSERT INTO fact_orders (order_id, customer_key, product_key, date_key, amount, quantity)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        [
            (5001, 1, 10, 20260701, 29.99, 1),
            (5002, 2, 11, 20260701, 349.00, 1),
            (5003, 1, 12, 20260715, 19.50, 2),
            (5004, 4, 13, 20260715, 899.00, 5),
            (5005, 3, 10, 20260802, 29.99, 3),
            (5006, 4, 11, 20260802, 349.00, 2),
            (5007, 2, 12, 20260805, 19.50, 1),
            (5008, 3, 13, 20260805, 179.80, 1),
        ],
    )
    conn.commit()


def analytical_query(conn: sqlite3.Connection) -> list[tuple]:
    """The whole point of a star schema: join the fact table out to whichever
    dimensions the analytical question needs, then GROUP BY.

    Question: total revenue and units sold, broken down by product category
    AND customer segment.
    """
    cur = conn.cursor()
    cur.execute(
        """
        SELECT
            p.category,
            c.segment,
            SUM(f.amount)    AS revenue,
            SUM(f.quantity)  AS units_sold,
            COUNT(*)         AS order_count
        FROM fact_orders f
        JOIN dim_products  p ON f.product_key  = p.product_key
        JOIN dim_customers c ON f.customer_key = c.customer_key
        JOIN dim_date      d ON f.date_key     = d.date_key
        GROUP BY p.category, c.segment
        ORDER BY revenue DESC
        """
    )
    return cur.fetchall()


def main() -> None:
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)

    conn = sqlite3.connect(DB_PATH)
    try:
        create_schema(conn)
        load_sample_data(conn)

        print("Star schema built: fact_orders + dim_customers + dim_products + dim_date\n")

        rows = analytical_query(conn)
        print("Revenue and units sold by product category x customer segment:")
        print(f"{'category':<12} {'segment':<12} {'revenue':>10} {'units':>7} {'orders':>7}")
        for category, segment, revenue, units, order_count in rows:
            print(f"{category:<12} {segment:<12} {revenue:>10.2f} {units:>7} {order_count:>7}")

    finally:
        conn.close()


if __name__ == "__main__":
    main()
