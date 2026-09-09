## Window Function Frame Clauses

# Why Frames Matter

--> The Window Functions file covers `PARTITION BY` and `ORDER BY`, but every window function ALSO has an implicit or explicit FRAME -- the exact subset of rows, within the current partition, that the function actually looks at for the current row. Understanding the frame explains subtle behavior differences (why a running total sometimes includes "future" rows and sometimes doesn't).

# The Default Frame

--> When `ORDER BY` is present inside `OVER (...)` but no frame is specified, most databases default to:
`RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW` -- everything from the partition's start up to (and including) rows tied with the current row.

# ROWS vs RANGE

--> `ROWS` -- counts a fixed number of physical ROWS before/after the current one, regardless of whether their values are tied.
--> `RANGE` -- counts based on VALUE, not row position -- rows with the SAME `ORDER BY` value as the current row are treated as part of the same "peer group" and included/excluded together.

```sql
-- ROWS: exactly the 2 rows before, the current row, and the 2 after -- always 5 rows (or fewer at the edges)
SELECT order_date, amount,
    AVG(amount) OVER (ORDER BY order_date ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING) AS moving_avg
FROM orders;

-- RANGE: if two rows share the exact same order_date, RANGE treats them as tied and includes both together
SELECT order_date, amount,
    SUM(amount) OVER (ORDER BY order_date RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total
FROM orders;
```

# Common Frame Patterns

```sql
ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW      -- running total from the start up to now
ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING       -- reverse running total, now to the end
ROWS BETWEEN 6 PRECEDING AND CURRENT ROW               -- a 7-row moving average/sum
ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING -- the entire partition, same value repeated on every row
```

--> **Practical guidance** -- for a genuine "moving average over N periods," `ROWS` is almost always what's actually intended -- `RANGE` on a mostly-unique `ORDER BY` column (like a timestamp with second precision) behaves the same as `ROWS` in practice, but silently diverges the moment duplicate values appear, which is a subtle, easy-to-miss source of wrong results if the two are used interchangeably without understanding the difference.

## Native JSON/Document Support in SQL Databases

# Why This Exists

--> Modern relational databases have absorbed part of what document databases (covered in the NoSQL file) offer -- storing and querying semi-structured JSON data directly inside an otherwise ordinary relational table, without needing a separate MongoDB-style database just for that one flexible field.

# PostgreSQL -- JSONB

```sql
CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    attributes JSONB   -- JSONB stores JSON in a parsed, indexable binary format (faster to query than plain JSON/text)
);

INSERT INTO products (name, attributes)
VALUES ('Laptop', '{"brand": "Dell", "specs": {"ram_gb": 16, "storage_gb": 512}}');

-- -> extracts a JSON value (keeping it JSON); ->> extracts it AS TEXT
SELECT name, attributes->>'brand' AS brand FROM products;
SELECT name FROM products WHERE attributes->'specs'->>'ram_gb' = '16';

-- A GIN index lets JSONB queries use an actual index instead of scanning every row's JSON
CREATE INDEX idx_attributes ON products USING GIN (attributes);
```

# MySQL -- JSON Type

```sql
CREATE TABLE products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    attributes JSON
);

INSERT INTO products (name, attributes)
VALUES ('Laptop', '{"brand": "Dell", "specs": {"ram_gb": 16}}');

SELECT name, JSON_EXTRACT(attributes, '$.brand') AS brand FROM products;
SELECT name FROM products WHERE JSON_EXTRACT(attributes, '$.specs.ram_gb') = 16;

-- JSON_TABLE (MySQL 8+) reshapes JSON into a normal relational row/column result
SELECT * FROM JSON_TABLE(
    '[{"id":1,"name":"A"},{"id":2,"name":"B"}]',
    '$[*]' COLUMNS (id INT PATH '$.id', name VARCHAR(50) PATH '$.name')
) AS jt;
```

--> **When to reach for a JSON column vs a separate table** -- a JSON column fits genuinely variable, sparse, or deeply nested attributes that don't need to be individually queried/joined often (product-specific specs that vary wildly by category); a normal column/table fits data that's queried, filtered, joined, or constrained frequently -- cramming everything into JSON just to "avoid migrations" sacrifices the type-checking, indexing, and constraint enforcement a proper relational column gives for free.

## Cursors

# What a Cursor Is

--> A cursor lets procedural SQL code (inside a stored procedure/function) iterate over a query's result set ROW BY ROW, imperatively -- the direct opposite of SQL's normal declarative, set-based style ("describe what you want," not "loop through it one row at a time").

```sql
-- SQL Server example
DECLARE @ProductName VARCHAR(255);
DECLARE product_cursor CURSOR FOR SELECT ProductName FROM Products WHERE Price > 100;

OPEN product_cursor;
FETCH NEXT FROM product_cursor INTO @ProductName;

WHILE @@FETCH_STATUS = 0
BEGIN
    PRINT @ProductName;   -- process one row at a time
    FETCH NEXT FROM product_cursor INTO @ProductName;
END;

CLOSE product_cursor;
DEALLOCATE product_cursor;
```

--> **Why cursors are usually a last resort, not a default tool** -- a set-based query (a single `UPDATE`/`SELECT` operating on all matching rows at once) is almost always dramatically faster than a cursor looping row by row and issuing repeated individual statements -- this is precisely the same underlying performance gap as the N+1 query problem covered in the ORM and Indexing files, just written directly inside the database instead of application code. Cursors are reserved for genuinely row-dependent procedural logic that truly cannot be expressed as a single set-based statement (e.g. a calculation where each row's result depends on the PREVIOUS row's already-computed result in a way a window function can't express).

## Sequences as a General-Purpose SQL Object

--> The CREATE INDEX/AUTO INCREMENT file only introduces `CREATE SEQUENCE` as Oracle's specific workaround for auto-incrementing primary keys -- but a sequence is actually a general-purpose database OBJECT in its own right, supported natively by PostgreSQL, Oracle, and SQL Server, useful for far more than just standing in for `AUTO_INCREMENT`.

```sql
-- PostgreSQL -- a sequence is a standalone object, independent of any specific table/column
CREATE SEQUENCE order_number_seq START WITH 1000 INCREMENT BY 1;

SELECT nextval('order_number_seq');   -- 1000, advances the sequence and returns the new value
SELECT nextval('order_number_seq');   -- 1001
SELECT currval('order_number_seq');   -- 1001 -- the current value, without advancing it again
```

--> **Why a sequence is more general than a column's auto-increment property** -- an `AUTO_INCREMENT`/`IDENTITY` column ties the counter to ONE specific column on ONE specific table -- a standalone sequence object can be shared across MULTIPLE tables (e.g. one global `invoice_number_seq` used by both an `Invoices` and a `CreditNotes` table, so invoice and credit-note numbers are guaranteed never to collide even though they live in different tables), or used to generate values that never even get stored as a primary key at all (a display-facing order number, a batch job ID).
--> **Sequences are NOT transactional in the way row data is** -- directly connecting to the Auto-Increment Gaps deep dive in the CREATE INDEX file -- a sequence's `nextval()` call is NOT rolled back even if the surrounding transaction is, since sequences are specifically designed to never hand out the same value twice to two different callers, even under heavy concurrent access, which requires the counter itself to advance immediately and permanently, independent of whether the transaction that requested it ultimately commits.
--> **`CACHE`** -- as the CREATE INDEX file's Oracle example already shows, a sequence can pre-allocate and cache a batch of values in memory for faster repeated access -- at the cost of those cached-but-unused values being lost (leaving a small permanent gap) if the database restarts before they're actually consumed.

## CREATE SCHEMA -- Namespacing Within a Database

--> A SCHEMA is a namespace WITHIN a database that groups related tables/views/objects together -- distinct from an entire separate database, and distinct from the informal use of "schema" to mean "the overall table structure" (as used elsewhere in these notes).

```sql
CREATE SCHEMA sales;
CREATE SCHEMA inventory;

CREATE TABLE sales.orders (order_id INT PRIMARY KEY, amount DECIMAL);
CREATE TABLE inventory.products (product_id INT PRIMARY KEY, name VARCHAR(255));

-- Fully-qualified reference, disambiguating which schema's table is meant
SELECT * FROM sales.orders;
```

--> Useful for organizing a large database with many tables into logical groups (by team, by subsystem), and for letting two different schemas each have a table with the SAME name without conflict (`sales.customers` vs `support.customers`) -- directly analogous to how folders organize files, or packages organize code modules.

## Savepoints -- Partial Rollback Within a Transaction

--> A `SAVEPOINT` marks a point WITHIN an ongoing transaction that a later `ROLLBACK` can return to, WITHOUT undoing the entire transaction -- useful when a transaction has several independent steps and only a later one fails.

```sql
BEGIN TRANSACTION;

INSERT INTO Orders (CustomerID, Total) VALUES (1, 100);
SAVEPOINT after_order;

INSERT INTO OrderItems (OrderID, ProductID) VALUES (999, 5);  -- suppose this fails (invalid OrderID)

ROLLBACK TO SAVEPOINT after_order;   -- undoes only the failed OrderItems insert, keeps the Orders insert intact

COMMIT;   -- commits with the Orders row saved, the failed OrderItems attempt discarded
```

--> Directly extends the "wrapping risky statements in a transaction" pattern covered in the INSERT/UPDATE/DELETE file -- a savepoint gives fine-grained recovery WITHIN a single transaction, instead of an all-or-nothing choice between committing everything or rolling back everything.

## Temporary Tables

--> A temporary table behaves like a normal table (can be queried, joined, indexed) but is automatically dropped at the end of the SESSION (or transaction, depending on the database) that created it -- and is invisible to other sessions/connections, even ones querying the exact same table name.

```sql
CREATE TEMPORARY TABLE temp_high_value_customers AS
SELECT CustomerID, SUM(amount) AS total_spent
FROM orders
GROUP BY CustomerID
HAVING SUM(amount) > 10000;

SELECT * FROM temp_high_value_customers WHERE total_spent > 50000;
-- Automatically gone once this session/connection ends -- no manual DROP TABLE needed, though it's still allowed
```

--> **Temporary table vs CTE vs View, when to use which** -- a CTE (covered in its own file) exists only for the duration of ONE query and can't be indexed or reused across multiple separate queries; a VIEW is a permanent, named, always-fresh definition visible to everyone with access; a TEMPORARY TABLE sits in between -- it persists across MULTIPLE queries within one session/transaction (letting you build up and reuse an intermediate result set, even add your own index to it) but disappears automatically and is never visible to other users, which a real table would be.

## Collations and Character Sets

--> A **character set** determines WHICH characters can be stored (ASCII, UTF-8, etc.) -- a **collation** determines HOW those characters are COMPARED and SORTED (case sensitivity, accent sensitivity, alphabetical ordering rules that differ between languages).

```sql
-- MySQL example -- a case-insensitive, accent-insensitive collation vs a case-sensitive binary one
CREATE TABLE products (
    name VARCHAR(255) COLLATE utf8mb4_general_ci   -- ci = case-insensitive: 'Shirt' = 'shirt' in comparisons/sorting
);

CREATE TABLE usernames (
    name VARCHAR(255) COLLATE utf8mb4_bin           -- bin = binary/case-sensitive: 'Alice' != 'alice'
);
```

--> **Why this matters practically** -- a case-insensitive collation means `WHERE Name = 'alice'` matches a row stored as `'Alice'`, and `ORDER BY Name` sorts case-blind ("apple" and "Apple" sort adjacent to each other, not by strict ASCII value which would put all-uppercase letters before all-lowercase ones). Mismatched collations between two columns being compared/joined can cause an error or silently prevent an index from being used -- a genuinely real, occasionally confusing production issue especially after migrating data between database versions/systems with different collation defaults.

## Regular Expression Matching

--> Beyond `LIKE`'s `%`/`_` wildcards (covered in the LIKE Operator file), most databases also support full REGULAR EXPRESSION matching for genuinely complex pattern needs `LIKE` simply can't express (alternation, repetition counts, character classes).

```sql
-- MySQL / PostgreSQL (~ operator) -- match a valid-looking email pattern
SELECT * FROM Customers WHERE Email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$';   -- MySQL
SELECT * FROM Customers WHERE Email ~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$';           -- PostgreSQL

-- Matching one of several alternatives -- LIKE can't express "OR" inside one pattern; REGEXP can
SELECT * FROM Products WHERE ProductName REGEXP 'Book|Pen|Notebook';
```

--> **Practical guidance** -- reach for `REGEXP`/`~` only when `LIKE`'s wildcards genuinely can't express the pattern -- regex matching is typically SLOWER than `LIKE` and, like a leading-wildcard `LIKE` pattern (covered in the LIKE Operator file's Deep Dive), generally can't use a standard index either, making it a poor choice for filtering large tables at scale without a dedicated full-text index.

## Prepared Statements as a General Performance Pattern

--> The SQL Views/Injection file introduces parameterized queries purely as an injection DEFENSE -- but preparing a statement has a genuine PERFORMANCE benefit too, independent of security, when the same query shape runs repeatedly with different values.

```python
# Without preparation -- the database must re-parse and re-plan the SQL text on every single call
for user_id in user_ids:
    cursor.execute(f"SELECT * FROM users WHERE id = {user_id}")   # also injectable -- shown only for contrast

# With a prepared statement -- parsed and planned ONCE, then reused with different bound values
stmt = connection.prepare("SELECT * FROM users WHERE id = ?")
for user_id in user_ids:
    stmt.execute(user_id)   # skips re-parsing/re-planning -- just substitutes the value and runs
```

--> The database can cache the PARSED query plan and reuse it across executions instead of redoing that work every time -- for a query executed thousands of times with only the bound values changing, this parsing/planning overhead saved genuinely adds up, on top of the injection protection already covered elsewhere -- a rare case where the secure approach and the performant approach happen to be the exact same thing.
