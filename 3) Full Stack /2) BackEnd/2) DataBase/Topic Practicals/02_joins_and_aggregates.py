"""
02_joins_and_aggregates.py

Demonstrates, against practice.db:
    1. INNER JOIN
    2. LEFT JOIN (including rows with no match)
    3. GROUP BY ... HAVING
    4. Subquery (correlated and non-correlated)

Covers Theory chapters:
    2) SQL/09 SQL Joins INNER LEFT RIGHT FULL and Self Join.md
    2) SQL/10 UNION UNION ALL GROUP BY and HAVING.md
    2) SQL/11 EXISTS ANY ALL and SELECT INTO.md
    2) SQL/05 Aggregate Functions MIN MAX COUNT SUM AVG.md

Run:  python 01_setup_schema_and_seed_data.py   (once, to create practice.db)
      python 02_joins_and_aggregates.py

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


def demo_inner_join(conn: sqlite3.Connection) -> None:
    print_section("1) INNER JOIN — orders that DO have a matching customer")
    print(
        "Demonstrates: only rows with a match in BOTH tables are returned.\n"
        "Here every order (>= 1 status filter) is joined to its customer."
    )
    sql = """
        SELECT o.order_id, o.order_date, o.status,
               c.first_name || ' ' || c.last_name AS customer_name,
               c.city
        FROM orders AS o
        INNER JOIN customers AS c ON o.customer_id = c.customer_id
        WHERE o.status = ?
        ORDER BY o.order_date
        LIMIT 10
    """
    run_and_print(conn, sql, ("delivered",))


def demo_left_join(conn: sqlite3.Connection) -> None:
    print_section("2) LEFT JOIN — every product, even ones never ordered")
    print(
        "Demonstrates: LEFT JOIN keeps all rows from the left table (products)\n"
        "and fills NULLs when there is no matching order_items row, letting us\n"
        "spot products that have zero sales (total_ordered IS NULL / 0)."
    )
    sql = """
        SELECT p.product_id, p.product_name, p.category,
               COALESCE(SUM(oi.quantity), 0) AS total_units_ordered
        FROM products AS p
        LEFT JOIN order_items AS oi ON p.product_id = oi.product_id
        GROUP BY p.product_id, p.product_name, p.category
        ORDER BY total_units_ordered ASC
        LIMIT 10
    """
    run_and_print(conn, sql)


def demo_group_by_having(conn: sqlite3.Connection) -> None:
    print_section("3) GROUP BY ... HAVING — customers with total spend > $150")
    print(
        "Demonstrates: GROUP BY aggregates order_items per customer, and HAVING\n"
        "filters on the aggregated SUM (WHERE cannot filter on aggregates)."
    )
    sql = """
        SELECT c.customer_id,
               c.first_name || ' ' || c.last_name AS customer_name,
               COUNT(DISTINCT o.order_id) AS num_orders,
               ROUND(SUM(oi.quantity * oi.unit_price), 2) AS total_spend
        FROM customers AS c
        JOIN orders AS o ON c.customer_id = o.customer_id
        JOIN order_items AS oi ON o.order_id = oi.order_id
        GROUP BY c.customer_id, customer_name
        HAVING SUM(oi.quantity * oi.unit_price) > ?
        ORDER BY total_spend DESC
    """
    run_and_print(conn, sql, (150.0,))


def demo_subquery(conn: sqlite3.Connection) -> None:
    print_section("4a) Non-correlated subquery — products priced above the overall average")
    print(
        "Demonstrates: the inner SELECT computes a single scalar value (AVG price)\n"
        "which the outer query then compares against."
    )
    sql = """
        SELECT product_name, category, unit_price
        FROM products
        WHERE unit_price > (SELECT AVG(unit_price) FROM products)
        ORDER BY unit_price DESC
    """
    run_and_print(conn, sql)

    print_section("4b) Correlated subquery — customers whose order count is above their own city's average")
    print(
        "Demonstrates: the inner SELECT re-runs per outer row, referencing the\n"
        "outer row's city (c.city) each time -- a classic correlated subquery."
    )
    sql = """
        SELECT c.customer_id,
               c.first_name || ' ' || c.last_name AS customer_name,
               c.city,
               (SELECT COUNT(*) FROM orders o WHERE o.customer_id = c.customer_id) AS my_order_count
        FROM customers AS c
        WHERE (
            SELECT COUNT(*) FROM orders o WHERE o.customer_id = c.customer_id
        ) > (
            SELECT AVG(order_count) FROM (
                SELECT COUNT(*) AS order_count
                FROM orders o2
                JOIN customers c2 ON c2.customer_id = o2.customer_id
                WHERE c2.city = c.city
                GROUP BY o2.customer_id
            )
        )
        ORDER BY my_order_count DESC
    """
    run_and_print(conn, sql)


def main() -> None:
    if not os.path.exists(DB_PATH):
        raise SystemExit(
            "practice.db not found. Run 01_setup_schema_and_seed_data.py first."
        )

    conn = sqlite3.connect(DB_PATH)
    try:
        conn.execute("PRAGMA foreign_keys = ON")
        demo_inner_join(conn)
        demo_left_join(conn)
        demo_group_by_having(conn)
        demo_subquery(conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
