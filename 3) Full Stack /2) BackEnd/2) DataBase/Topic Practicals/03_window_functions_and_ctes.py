"""
03_window_functions_and_ctes.py

Demonstrates, against practice.db:
    1. ROW_NUMBER() / RANK() — "top N per group" (best-selling product per category)
    2. SUM() OVER (...) — running total of daily revenue
    3. Recursive CTE #1 — walking a category hierarchy (built ad-hoc in this script)
    4. Recursive CTE #2 — generating a date series

Covers Theory chapters:
    2) SQL/21 Window Functions.md
    2) SQL/22 Common Table Expressions and Recursive Queries.md

Run:  python 01_setup_schema_and_seed_data.py   (once, to create practice.db)
      python 03_window_functions_and_ctes.py

All queries use parameterized placeholders ("?") even where no user input is
involved, to model the habit of never building SQL via string formatting.
"""

import os
import sqlite3

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "practice.db")


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


def run_and_print(conn: sqlite3.Connection, sql: str, params: tuple = ()) -> None:
    print("\nSQL:")
    print(sql.strip())
    print("\nResults:")
    cur = conn.execute(sql, params)
    columns = [d[0] for d in cur.description]
    print(" | ".join(columns))
    print("-" * 60)
    rows = cur.fetchall()
    for row in rows:
        print(" | ".join(str(v) for v in row))
    print(f"\n({len(rows)} row(s))")


def demo_row_number_top_n_per_group(conn: sqlite3.Connection) -> None:
    print_section("1) ROW_NUMBER() — top 2 best-selling products per category (by units sold)")
    print(
        "Demonstrates: PARTITION BY resets the row-numbering per category,\n"
        "ORDER BY inside OVER() ranks rows within each partition, and the\n"
        "outer WHERE keeps only rank <= 2 -- the classic 'top N per group' idiom."
    )
    sql = """
        WITH product_sales AS (
            SELECT p.product_id, p.product_name, p.category,
                   SUM(oi.quantity) AS units_sold
            FROM products AS p
            JOIN order_items AS oi ON oi.product_id = p.product_id
            GROUP BY p.product_id, p.product_name, p.category
        ),
        ranked AS (
            SELECT product_name, category, units_sold,
                   ROW_NUMBER() OVER (
                       PARTITION BY category ORDER BY units_sold DESC
                   ) AS rn,
                   RANK() OVER (
                       PARTITION BY category ORDER BY units_sold DESC
                   ) AS rnk
            FROM product_sales
        )
        SELECT category, product_name, units_sold, rn, rnk
        FROM ranked
        WHERE rn <= ?
        ORDER BY category, rn
    """
    run_and_print(conn, sql, (2,))


def demo_running_total(conn: sqlite3.Connection) -> None:
    print_section("2) SUM() OVER (...) — running total of daily revenue")
    print(
        "Demonstrates: a window function computing a cumulative SUM ordered by\n"
        "order_date, without collapsing rows the way a GROUP BY would."
    )
    sql = """
        WITH daily_revenue AS (
            SELECT o.order_date,
                   ROUND(SUM(oi.quantity * oi.unit_price), 2) AS revenue
            FROM orders AS o
            JOIN order_items AS oi ON oi.order_id = o.order_id
            GROUP BY o.order_date
        )
        SELECT order_date,
               revenue,
               ROUND(SUM(revenue) OVER (ORDER BY order_date), 2) AS running_total
        FROM daily_revenue
        ORDER BY order_date
    """
    run_and_print(conn, sql)


def demo_recursive_cte_hierarchy(conn: sqlite3.Connection) -> None:
    print_section("3) Recursive CTE — walking a category hierarchy")
    print(
        "Demonstrates: a self-referencing CTE that starts at root categories\n"
        "(parent_id IS NULL) and recursively joins children to build a full\n"
        "tree with a computed depth, printed as an indented path."
    )

    cur = conn.cursor()
    cur.executescript(
        """
        DROP TABLE IF EXISTS categories;
        CREATE TABLE categories (
            category_id   INTEGER PRIMARY KEY,
            category_name TEXT NOT NULL,
            parent_id     INTEGER REFERENCES categories(category_id)
        );
        """
    )
    rows = [
        (1, "Electronics", None),
        (2, "Computers", 1),
        (3, "Laptops", 2),
        (4, "Desktops", 2),
        (5, "Accessories", 1),
        (6, "Keyboards", 5),
        (7, "Mice", 5),
        (8, "Furniture", None),
        (9, "Office Furniture", 8),
        (10, "Chairs", 9),
    ]
    cur.executemany(
        "INSERT INTO categories (category_id, category_name, parent_id) VALUES (?, ?, ?)",
        rows,
    )
    conn.commit()

    sql = """
        WITH RECURSIVE category_tree (category_id, category_name, depth, path) AS (
            SELECT category_id, category_name, 0 AS depth, category_name AS path
            FROM categories
            WHERE parent_id IS NULL

            UNION ALL

            SELECT c.category_id, c.category_name, ct.depth + 1,
                   ct.path || ' > ' || c.category_name
            FROM categories AS c
            JOIN category_tree AS ct ON c.parent_id = ct.category_id
        )
        SELECT category_id, depth, path
        FROM category_tree
        ORDER BY path
    """
    run_and_print(conn, sql)


def demo_recursive_cte_date_series(conn: sqlite3.Connection) -> None:
    print_section("4) Recursive CTE — generating a date series")
    print(
        "Demonstrates: a recursive CTE used purely to generate rows (there is\n"
        "no source table) -- a common trick for calendar tables / gap-filling."
    )
    sql = """
        WITH RECURSIVE date_series (d) AS (
            SELECT date(?)
            UNION ALL
            SELECT date(d, '+1 day')
            FROM date_series
            WHERE d < date(?)
        )
        SELECT d AS calendar_date
        FROM date_series
    """
    run_and_print(conn, sql, ("2023-02-01", "2023-02-10"))


def main() -> None:
    if not os.path.exists(DB_PATH):
        raise SystemExit(
            "practice.db not found. Run 01_setup_schema_and_seed_data.py first."
        )

    conn = sqlite3.connect(DB_PATH)
    try:
        conn.execute("PRAGMA foreign_keys = ON")
        demo_row_number_top_n_per_group(conn)
        demo_running_total(conn)
        demo_recursive_cte_hierarchy(conn)
        demo_recursive_cte_date_series(conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
