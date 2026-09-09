"""
01_setup_schema_and_seed_data.py

Creates practice.db (SQLite) with a small but realistic relational schema:

    customers (1) ----- (many) orders (1) ----- (many) order_items (many) ----- (1) products

Covers Theory chapters:
    2) SQL/14 CREATE DROP and BACKUP DATABASE.md
    2) SQL/15 CREATE DROP ALTER TABLE and TRUNCATE.md
    2) SQL/16 Constraints NOT NULL UNIQUE PRIMARY KEY.md
    2) SQL/17 FOREIGN KEY CHECK and DEFAULT Constraints.md
    2) SQL/18 CREATE INDEX AUTO INCREMENT and Dates.md
    3) Advanced/03 Normalization and Database Design.md

Run:  python 01_setup_schema_and_seed_data.py
Produces: practice.db (in the same folder), ready for scripts 02-05.
"""

import sqlite3
import os
from datetime import date, timedelta

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "practice.db")


def create_schema(conn: sqlite3.Connection) -> None:
    """DROP + CREATE all tables with PRIMARY KEY / FOREIGN KEY / CHECK / DEFAULT constraints."""
    cur = conn.cursor()

    # Order matters: drop children before parents to respect FK relationships.
    cur.executescript(
        """
        DROP TABLE IF EXISTS order_items;
        DROP TABLE IF EXISTS orders;
        DROP TABLE IF EXISTS products;
        DROP TABLE IF EXISTS customers;
        """
    )

    cur.executescript(
        """
        CREATE TABLE customers (
            customer_id     INTEGER PRIMARY KEY AUTOINCREMENT,
            first_name      TEXT NOT NULL,
            last_name       TEXT NOT NULL,
            email           TEXT NOT NULL UNIQUE,
            city            TEXT NOT NULL,
            country         TEXT NOT NULL DEFAULT 'USA',
            signup_date     TEXT NOT NULL
        );

        CREATE TABLE products (
            product_id      INTEGER PRIMARY KEY AUTOINCREMENT,
            product_name    TEXT NOT NULL,
            category        TEXT NOT NULL,
            unit_price      REAL NOT NULL CHECK (unit_price >= 0),
            stock_quantity  INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0)
        );

        CREATE TABLE orders (
            order_id        INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id     INTEGER NOT NULL,
            order_date      TEXT NOT NULL,
            status          TEXT NOT NULL DEFAULT 'pending'
                                CHECK (status IN ('pending', 'shipped', 'delivered', 'cancelled')),
            FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
                ON DELETE CASCADE
        );

        CREATE TABLE order_items (
            order_item_id   INTEGER PRIMARY KEY AUTOINCREMENT,
            order_id        INTEGER NOT NULL,
            product_id      INTEGER NOT NULL,
            quantity        INTEGER NOT NULL CHECK (quantity > 0),
            unit_price      REAL NOT NULL CHECK (unit_price >= 0),
            FOREIGN KEY (order_id) REFERENCES orders (order_id)
                ON DELETE CASCADE,
            FOREIGN KEY (product_id) REFERENCES products (product_id)
        );

        -- Helpful indexes for later lookups / joins (see 05_indexing_performance_demo.py
        -- for a dedicated deep-dive on index performance).
        CREATE INDEX idx_orders_customer_id ON orders (customer_id);
        CREATE INDEX idx_order_items_order_id ON order_items (order_id);
        CREATE INDEX idx_order_items_product_id ON order_items (product_id);
        """
    )
    conn.commit()


def seed_customers(conn: sqlite3.Connection) -> None:
    first_names = [
        "Alice", "Bob", "Charlie", "Diana", "Ethan", "Fiona", "George", "Hannah",
        "Ivan", "Julia", "Kevin", "Laura", "Mohan", "Nina", "Oscar", "Priya",
        "Quentin", "Riya", "Sam", "Tara",
    ]
    last_names = [
        "Smith", "Johnson", "Lee", "Brown", "Garcia", "Davis", "Miller", "Wilson",
        "Kapoor", "Chen", "Patel", "Anderson", "Rao", "Clark", "Lewis", "Nair",
        "Walker", "Young", "King", "Wright",
    ]
    cities = [
        ("New York", "USA"), ("Chicago", "USA"), ("Austin", "USA"), ("Toronto", "Canada"),
        ("Vancouver", "Canada"), ("London", "UK"), ("Manchester", "UK"), ("Mumbai", "India"),
        ("Bengaluru", "India"), ("Berlin", "Germany"),
    ]

    rows = []
    start = date(2023, 1, 1)
    for i in range(20):
        first = first_names[i]
        last = last_names[i]
        city, country = cities[i % len(cities)]
        email = f"{first.lower()}.{last.lower()}@example.com"
        signup_date = (start + timedelta(days=i * 11)).isoformat()
        rows.append((first, last, email, city, country, signup_date))

    conn.executemany(
        """
        INSERT INTO customers (first_name, last_name, email, city, country, signup_date)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        rows,
    )
    conn.commit()


def seed_products(conn: sqlite3.Connection) -> None:
    rows = [
        ("Wireless Mouse", "Electronics", 19.99, 150),
        ("Mechanical Keyboard", "Electronics", 59.99, 90),
        ("USB-C Hub", "Electronics", 24.50, 200),
        ("27in Monitor", "Electronics", 179.00, 40),
        ("Noise Cancelling Headphones", "Electronics", 89.99, 75),
        ("Standing Desk", "Furniture", 249.00, 25),
        ("Ergonomic Chair", "Furniture", 189.50, 30),
        ("Bookshelf", "Furniture", 75.00, 45),
        ("Desk Lamp", "Furniture", 29.99, 120),
        ("Filing Cabinet", "Furniture", 110.00, 20),
        ("Notebook Pack", "Stationery", 8.99, 300),
        ("Gel Pens (12pk)", "Stationery", 6.49, 400),
        ("Sticky Notes", "Stationery", 3.99, 500),
        ("Whiteboard", "Stationery", 34.99, 60),
        ("Highlighter Set", "Stationery", 5.99, 250),
        ("Coffee Mug", "Kitchen", 12.99, 180),
        ("Electric Kettle", "Kitchen", 39.99, 70),
        ("Blender", "Kitchen", 54.99, 55),
        ("Water Bottle", "Kitchen", 15.99, 220),
        ("Lunch Box", "Kitchen", 18.50, 160),
        ("Yoga Mat", "Fitness", 22.99, 130),
        ("Dumbbell Set", "Fitness", 89.00, 40),
        ("Resistance Bands", "Fitness", 14.99, 200),
        ("Running Shoes", "Fitness", 74.99, 65),
        ("Fitness Tracker", "Fitness", 49.99, 85),
    ]
    conn.executemany(
        """
        INSERT INTO products (product_name, category, unit_price, stock_quantity)
        VALUES (?, ?, ?, ?)
        """,
        rows,
    )
    conn.commit()


def seed_orders_and_items(conn: sqlite3.Connection) -> None:
    cur = conn.cursor()
    cur.execute("SELECT customer_id FROM customers")
    customer_ids = [r[0] for r in cur.fetchall()]

    cur.execute("SELECT product_id, unit_price FROM products")
    products = cur.fetchall()  # list of (product_id, unit_price)

    statuses = ["pending", "shipped", "delivered", "delivered", "cancelled"]
    start = date(2023, 2, 1)

    order_id_counter = 0
    for i in range(28):  # 28 orders spread across the 20 customers
        customer_id = customer_ids[i % len(customer_ids)]
        order_date = (start + timedelta(days=i * 5)).isoformat()
        status = statuses[i % len(statuses)]

        cur.execute(
            "INSERT INTO orders (customer_id, order_date, status) VALUES (?, ?, ?)",
            (customer_id, order_date, status),
        )
        order_id_counter = cur.lastrowid

        # Each order gets 1-3 line items, cycling deterministically through products.
        num_items = (i % 3) + 1
        for j in range(num_items):
            product_id, unit_price = products[(i + j * 7) % len(products)]
            quantity = ((i + j) % 4) + 1
            cur.execute(
                """
                INSERT INTO order_items (order_id, product_id, quantity, unit_price)
                VALUES (?, ?, ?, ?)
                """,
                (order_id_counter, product_id, quantity, unit_price),
            )

    conn.commit()


def main() -> None:
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)

    conn = sqlite3.connect(DB_PATH)
    try:
        conn.execute("PRAGMA foreign_keys = ON")
        create_schema(conn)
        seed_customers(conn)
        seed_products(conn)
        seed_orders_and_items(conn)

        # Quick sanity summary.
        cur = conn.cursor()
        for table in ("customers", "products", "orders", "order_items"):
            count = cur.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
            print(f"{table:15s}: {count} rows")

        print(f"\nCreated {DB_PATH}")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
