-- =====================================================================
-- 06_normalization_before_after.sql
--
-- Covers Theory chapter:
--   3) Advanced/03 Normalization and Database Design.md
--
-- This is a REFERENCE SQL file, not a runnable Python script. It is plain
-- ANSI-ish SQL (SQLite-flavored) meant to be read, or optionally pasted into
-- `sqlite3 :memory:` / any SQL client to see it execute. It shows:
--   Part A: a deliberately UNNORMALIZED ("flat") orders table (0NF/1NF-ish,
--           full of repeating groups and redundancy)
--   Part B: the same information normalized step-by-step to 3NF
--
-- =====================================================================


-- =====================================================================
-- PART A: UNNORMALIZED DESIGN  ("orders_flat")
-- =====================================================================
-- Problems with this single denormalized table, deliberately baked in:
--   1. Repeating groups: item_1_*, item_2_*, item_3_* columns hard-cap
--      an order at 3 line items and waste space/NULLs for orders with fewer.
--   2. Customer data (name/email/city) is duplicated on every single order
--      row -> UPDATE anomaly (change a customer's email and you must update
--      every order row for that customer, or they go inconsistent).
--   3. Product data (name/price) is duplicated inside each item_n_* column
--      set -> the same anomaly for products, plus wasted storage.
--   4. DELETE anomaly: deleting the one order a new customer ever placed
--      deletes all knowledge that the customer exists.
--   5. INSERT anomaly: cannot record a new product until someone orders it,
--      because product info only exists embedded inside order rows.

DROP TABLE IF EXISTS orders_flat;

CREATE TABLE orders_flat (
    order_id            INTEGER PRIMARY KEY,
    order_date          TEXT,

    -- customer info repeated on every row for this customer
    customer_name       TEXT,
    customer_email      TEXT,
    customer_city       TEXT,

    -- repeating group #1 (item 1 of up to 3)
    item_1_product_name TEXT,
    item_1_unit_price   REAL,
    item_1_quantity     INTEGER,

    -- repeating group #2 (item 2 of up to 3)
    item_2_product_name TEXT,
    item_2_unit_price   REAL,
    item_2_quantity     INTEGER,

    -- repeating group #3 (item 3 of up to 3) -- what happens with a 4th item?
    item_3_product_name TEXT,
    item_3_unit_price   REAL,
    item_3_quantity     INTEGER
);

-- Sample rows showing the redundancy in action: "Alice Smith" and her email
-- and city are repeated verbatim across two separate order rows below.
INSERT INTO orders_flat VALUES
    (1, '2023-02-01', 'Alice Smith', 'alice.smith@example.com', 'New York',
        'Wireless Mouse', 19.99, 2,
        'USB-C Hub', 24.50, 1,
        NULL, NULL, NULL),
    (2, '2023-02-10', 'Alice Smith', 'alice.smith@example.com', 'New York',
        'Mechanical Keyboard', 59.99, 1,
        NULL, NULL, NULL,
        NULL, NULL, NULL);


-- =====================================================================
-- PART B: NORMALIZATION TO 3NF
-- =====================================================================

-- ---------------------------------------------------------------------
-- Step 1 -> 1NF: eliminate repeating groups (item_1_*, item_2_*, item_3_*).
-- Every column must hold a single atomic value, and each order can now have
-- an arbitrary number of items -- no more hard-coded "up to 3" limit.
-- We do this by moving each item into its OWN ROW in a separate table,
-- keyed by (order_id) as a foreign key back to the order.
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS orders_1nf;
DROP TABLE IF EXISTS order_items_1nf;

CREATE TABLE orders_1nf (
    order_id        INTEGER PRIMARY KEY,
    order_date      TEXT,
    customer_name   TEXT,
    customer_email  TEXT,
    customer_city   TEXT
);

CREATE TABLE order_items_1nf (
    order_item_id   INTEGER PRIMARY KEY,
    order_id        INTEGER NOT NULL,
    product_name    TEXT,
    unit_price      REAL,
    quantity        INTEGER,
    FOREIGN KEY (order_id) REFERENCES orders_1nf (order_id)
);

-- Removed by this step: the repeating-group anomaly (no more wasted NULL
-- columns for orders with fewer items, and no artificial max-item limit).
-- Customer redundancy (name/email/city repeated per order) still remains.


-- ---------------------------------------------------------------------
-- Step 2 -> 2NF: remove partial dependencies on part of a composite key.
-- In 1NF, order_items_1nf's non-key columns (product_name, unit_price)
-- depend only on the PRODUCT, not on the (order_id, product) pair as a
-- whole -- that is a partial dependency once you consider order_id +
-- product together as identifying a line item. We split product
-- attributes into their own table keyed by product_id.
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS products_2nf;
DROP TABLE IF EXISTS order_items_2nf;

CREATE TABLE products_2nf (
    product_id      INTEGER PRIMARY KEY,
    product_name    TEXT NOT NULL,
    unit_price      REAL NOT NULL
);

CREATE TABLE order_items_2nf (
    order_item_id   INTEGER PRIMARY KEY,
    order_id        INTEGER NOT NULL,
    product_id      INTEGER NOT NULL,
    quantity        INTEGER NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders_1nf (order_id),
    FOREIGN KEY (product_id) REFERENCES products_2nf (product_id)
);

-- Removed by this step: product_name/unit_price redundancy across every
-- order_item row that sells the same product. Now a product's price lives
-- in exactly one row (products_2nf), so a price correction is a single
-- UPDATE instead of an update-every-matching-row anomaly.
-- Customer redundancy on orders_1nf still remains.


-- ---------------------------------------------------------------------
-- Step 3 -> 3NF: remove transitive dependencies (non-key column depending
-- on another non-key column, not directly on the primary key).
-- In orders_1nf, customer_email and customer_city depend on customer_name,
-- not directly on order_id -- a transitive dependency
-- (order_id -> customer_name -> customer_email/city). We split customer
-- attributes into their own table keyed by customer_id.
-- ---------------------------------------------------------------------

DROP TABLE IF EXISTS customers_3nf;
DROP TABLE IF EXISTS orders_3nf;
DROP TABLE IF EXISTS order_items_3nf;
DROP TABLE IF EXISTS products_3nf;

CREATE TABLE customers_3nf (
    customer_id     INTEGER PRIMARY KEY,
    customer_name   TEXT NOT NULL,
    customer_email  TEXT NOT NULL UNIQUE,
    customer_city   TEXT NOT NULL
);

CREATE TABLE products_3nf (
    product_id      INTEGER PRIMARY KEY,
    product_name    TEXT NOT NULL,
    unit_price      REAL NOT NULL CHECK (unit_price >= 0)
);

CREATE TABLE orders_3nf (
    order_id        INTEGER PRIMARY KEY,
    customer_id     INTEGER NOT NULL,
    order_date      TEXT NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers_3nf (customer_id)
);

CREATE TABLE order_items_3nf (
    order_item_id   INTEGER PRIMARY KEY,
    order_id        INTEGER NOT NULL,
    product_id      INTEGER NOT NULL,
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    unit_price      REAL NOT NULL,  -- price AT TIME OF ORDER (historical snapshot,
                                     -- intentionally kept here, not a normalization bug --
                                     -- see note below)
    FOREIGN KEY (order_id) REFERENCES orders_3nf (order_id),
    FOREIGN KEY (product_id) REFERENCES products_3nf (product_id)
);

-- Removed by this step: customer redundancy across every order row for the
-- same customer. Now:
--   - Update anomaly gone: changing a customer's email is ONE UPDATE to
--     customers_3nf, not N updates across every historical order.
--   - Delete anomaly gone: deleting an order no longer risks losing all
--     record of the customer, because customer data lives independently.
--   - Insert anomaly gone: a new product can be added to products_3nf
--     before anyone has ever ordered it.
--
-- Note on order_items_3nf.unit_price: this is NOT leftover redundancy --
-- it is a deliberate denormalization to preserve HISTORY. Product prices
-- change over time; storing the price at the moment of purchase means a
-- past invoice total does not silently change when today's price changes.
-- This mirrors 01_setup_schema_and_seed_data.py's real schema.

-- This final shape (customers_3nf, products_3nf, orders_3nf,
-- order_items_3nf) is exactly the pattern used by
-- 01_setup_schema_and_seed_data.py's customers/products/orders/order_items
-- schema, which the other Practical scripts run all their JOIN, GROUP BY,
-- window function, transaction, and indexing demos against.
