"""
04_transactions_and_constraints.py

Demonstrates, against practice.db:
    1. A multi-statement transaction with an explicit COMMIT.
    2. A multi-statement transaction that hits a simulated failure partway
       through and is explicitly ROLLBACK-ed, proving the partial change
       (the first INSERT) is undone.
    3. Constraint violations (UNIQUE and FOREIGN KEY) caught with proper
       error handling (sqlite3.IntegrityError), instead of crashing.

Covers Theory chapters:
    3) Advanced/04 Transactions and ACID.md
    2) SQL/16 Constraints NOT NULL UNIQUE PRIMARY KEY.md
    2) SQL/17 FOREIGN KEY CHECK and DEFAULT Constraints.md

Run:  python 01_setup_schema_and_seed_data.py   (once, to create practice.db)
      python 04_transactions_and_constraints.py

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


def count_customers(conn: sqlite3.Connection) -> int:
    return conn.execute("SELECT COUNT(*) FROM customers").fetchone()[0]


def demo_commit(conn: sqlite3.Connection) -> None:
    print_section("1) Transaction with explicit COMMIT")
    print(
        "Inserting a new customer AND their first order as one atomic unit.\n"
        "Both statements succeed, so we COMMIT and both changes persist."
    )

    before = count_customers(conn)
    try:
        conn.execute("BEGIN")
        conn.execute(
            """
            INSERT INTO customers (first_name, last_name, email, city, country, signup_date)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ("Uma", "Verma", "uma.verma@example.com", "Pune", "India", "2023-06-01"),
        )
        new_customer_id = conn.execute(
            "SELECT customer_id FROM customers WHERE email = ?",
            ("uma.verma@example.com",),
        ).fetchone()[0]

        conn.execute(
            "INSERT INTO orders (customer_id, order_date, status) VALUES (?, ?, ?)",
            (new_customer_id, "2023-06-02", "pending"),
        )
        conn.commit()
        print("COMMIT succeeded.")
    except sqlite3.Error as exc:
        conn.rollback()
        print(f"Unexpected error, rolled back: {exc}")

    after = count_customers(conn)
    print(f"Customer count before={before}, after={after} (expected after = before + 1)")


def demo_rollback(conn: sqlite3.Connection) -> None:
    print_section("2) Transaction with simulated failure -> explicit ROLLBACK")
    print(
        "Inserting a new customer, then intentionally hitting a failure\n"
        "(inserting an order for a non-existent customer_id) before committing.\n"
        "We ROLLBACK, and the first INSERT (the new customer) must be undone."
    )

    before = count_customers(conn)
    try:
        conn.execute("BEGIN")
        conn.execute(
            """
            INSERT INTO customers (first_name, last_name, email, city, country, signup_date)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ("Victor", "Nguyen", "victor.nguyen@example.com", "Hanoi", "Vietnam", "2023-06-05"),
        )

        # Simulate a downstream failure: order for a customer_id that does not exist.
        FAKE_MISSING_CUSTOMER_ID = 999999
        conn.execute(
            "INSERT INTO orders (customer_id, order_date, status) VALUES (?, ?, ?)",
            (FAKE_MISSING_CUSTOMER_ID, "2023-06-06", "pending"),
        )
        conn.commit()
        print("COMMIT succeeded (unexpected).")
    except sqlite3.IntegrityError as exc:
        conn.rollback()
        print(f"IntegrityError caught, ROLLBACK issued: {exc}")

    after = count_customers(conn)
    print(f"Customer count before={before}, after={after} (expected after == before, rollback undid the insert)")

    still_there = conn.execute(
        "SELECT COUNT(*) FROM customers WHERE email = ?",
        ("victor.nguyen@example.com",),
    ).fetchone()[0]
    print(f"Rows for victor.nguyen@example.com after rollback: {still_there} (expected 0)")


def demo_unique_violation(conn: sqlite3.Connection) -> None:
    print_section("3a) Constraint violation — duplicate UNIQUE email, caught")
    existing_email = conn.execute("SELECT email FROM customers LIMIT 1").fetchone()[0]
    print(f"Attempting to insert a second customer with the already-used email: {existing_email}")

    try:
        conn.execute(
            """
            INSERT INTO customers (first_name, last_name, email, city, country, signup_date)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ("Duplicate", "Person", existing_email, "Nowhere", "USA", "2023-06-10"),
        )
        conn.commit()
        print("Insert succeeded (unexpected -- constraint did not fire).")
    except sqlite3.IntegrityError as exc:
        conn.rollback()
        print(f"Caught expected sqlite3.IntegrityError: {exc}")


def demo_foreign_key_violation(conn: sqlite3.Connection) -> None:
    print_section("3b) Constraint violation — invalid FOREIGN KEY, caught")
    FAKE_PRODUCT_ID = 999999
    print(f"Attempting to insert an order_item referencing non-existent product_id={FAKE_PRODUCT_ID}")

    order_id = conn.execute("SELECT order_id FROM orders LIMIT 1").fetchone()[0]
    try:
        conn.execute(
            "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
            (order_id, FAKE_PRODUCT_ID, 1, 9.99),
        )
        conn.commit()
        print("Insert succeeded (unexpected -- FK enforcement may be off).")
    except sqlite3.IntegrityError as exc:
        conn.rollback()
        print(f"Caught expected sqlite3.IntegrityError: {exc}")


def main() -> None:
    if not os.path.exists(DB_PATH):
        raise SystemExit(
            "practice.db not found. Run 01_setup_schema_and_seed_data.py first."
        )

    conn = sqlite3.connect(DB_PATH)
    try:
        # Foreign key enforcement is OFF by default in SQLite; must opt in per connection.
        conn.execute("PRAGMA foreign_keys = ON")

        demo_commit(conn)
        demo_rollback(conn)
        demo_unique_violation(conn)
        demo_foreign_key_violation(conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
