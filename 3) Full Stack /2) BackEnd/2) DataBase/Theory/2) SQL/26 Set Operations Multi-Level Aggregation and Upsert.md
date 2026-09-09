## SQL INTERSECT and EXCEPT Operators

# The SQL INTERSECT Operator

--> The INTERSECT operator returns only the rows that appear in BOTH SELECT statements' result sets -- the opposite mindset from UNION (which combines everything).

==> Syntax
SELECT column_name(s) FROM table1
INTERSECT
SELECT column_name(s) FROM table2;

==> Example
--> Return cities that have BOTH a customer and a supplier:

SELECT City FROM Customers
INTERSECT
SELECT City FROM Suppliers;

**Note**: Same requirements as UNION -- same number of columns, compatible data types, same column order. MySQL added native `INTERSECT` support only in version 8.0.31 -- older MySQL requires an `INNER JOIN`-based or `IN`-based workaround instead.

# The SQL EXCEPT Operator (MINUS in Oracle)

--> The EXCEPT operator returns rows from the FIRST SELECT statement that do NOT appear in the second -- a set difference.

==> Syntax
SELECT column_name(s) FROM table1
EXCEPT
SELECT column_name(s) FROM table2;

==> Example
--> Return cities that have a customer but NO supplier:

SELECT City FROM Customers
EXCEPT
SELECT City FROM Suppliers;

**Note**: Oracle uses `MINUS` instead of `EXCEPT` for the exact same operation -- another instance of the "SQL is a standard, BUT" caveat raised in the SQL Introduction file.

```sql
-- Oracle equivalent of the same query:
SELECT City FROM Customers
MINUS
SELECT City FROM Suppliers;
```

## Deep Dive -- INTERSECT/EXCEPT vs Their JOIN/NOT EXISTS Equivalents

--> Both operators can always be rewritten using `JOIN`/`NOT EXISTS`, and older MySQL versions without native support REQUIRE this rewrite:

```sql
-- INTERSECT rewritten with EXISTS:
SELECT City FROM Customers c
WHERE EXISTS (SELECT 1 FROM Suppliers s WHERE s.City = c.City);

-- EXCEPT rewritten with NOT EXISTS:
SELECT City FROM Customers c
WHERE NOT EXISTS (SELECT 1 FROM Suppliers s WHERE s.City = c.City);
```

--> `INTERSECT`/`EXCEPT` are usually more READABLE for genuinely set-oriented questions ("what's common," "what's missing from one side"), while `EXISTS`/`NOT EXISTS` tends to perform comparably or better on large tables since the query planner can often short-circuit as soon as one match is found -- directly connecting to the IN vs EXISTS performance discussion in the IN and BETWEEN Operators file.

## SQL GROUPING SETS, ROLLUP, and CUBE

# Why These Exist

--> Plain `GROUP BY` produces exactly ONE level of grouping per query. Getting multiple related subtotals (by category, by region, AND a grand total) normally requires either several separate queries `UNION`'d together, or these dedicated multi-level aggregation extensions that compute them all in a SINGLE pass over the data.

# GROUPING SETS -- Multiple Groupings in One Query

```sql
SELECT Category, Region, SUM(Sales) AS TotalSales
FROM Orders
GROUP BY GROUPING SETS (
    (Category, Region),   -- subtotal per category+region combination
    (Category),            -- subtotal per category alone
    ()                      -- the grand total across everything
);
```

--> Produces exactly the rows a manual `UNION ALL` of three separate `GROUP BY` queries would produce, but in one query, over one pass of the data -- generally more efficient than three separate scans.

# ROLLUP -- Hierarchical Subtotals

--> `ROLLUP` is shorthand for a specific, common pattern of `GROUPING SETS` -- producing subtotals at each level of a HIERARCHY, from most detailed up to the grand total.

```sql
SELECT Region, Category, SUM(Sales) AS TotalSales
FROM Orders
GROUP BY ROLLUP (Region, Category);
-- Produces: (Region, Category) subtotals, then (Region) subtotals, then the grand total ()
-- Equivalent to: GROUPING SETS ((Region, Category), (Region), ())
```

--> Useful for reports with a natural drill-down hierarchy (Country → State → City totals, or Year → Quarter → Month totals).

# CUBE -- Every Possible Combination

--> `CUBE` generates subtotals for EVERY possible combination of the grouping columns, not just a hierarchical rollup.

```sql
SELECT Region, Category, SUM(Sales) AS TotalSales
FROM Orders
GROUP BY CUBE (Region, Category);
-- Produces: (Region, Category), (Region) alone, (Category) alone, AND the grand total ()
-- CUBE with 2 columns produces 2^2 = 4 grouping combinations; ROLLUP only produces 3 (the hierarchical subset)
```

## Deep Dive -- Identifying Which Subtotal Level a Row Belongs To

--> Since all these grouping levels come back mixed together in one result set, the standard `GROUPING()` function tells you whether a given column was "rolled up" (aggregated away) for that specific row -- returns `1` if the column is part of the current subtotal (its value is NULL because it was summarized away), `0` if it's a real, non-aggregated value.

```sql
SELECT
    Region, Category, SUM(Sales) AS TotalSales,
    GROUPING(Region) AS IsRegionSubtotal,
    GROUPING(Category) AS IsCategorySubtotal
FROM Orders
GROUP BY ROLLUP (Region, Category);
-- A row with GROUPING(Category)=1 means that row's Category is NULL because it's a Region-level subtotal, not a real "NULL category"
```

--> This solves a genuinely common confusion -- a plain NULL in the Category column of the result could otherwise mean either "this row IS a subtotal" or "there genuinely are uncategorized orders with a NULL Category" -- `GROUPING()` disambiguates the two.

## SQL PIVOT and UNPIVOT

# PIVOT -- Rows Into Columns

--> `PIVOT` rotates unique values from one column into multiple output COLUMNS, turning "long" data into "wide" data -- useful for report-style layouts (e.g. one column per month, instead of one row per month).

```sql
-- SQL Server syntax -- turn a Quarter column's values into separate columns
SELECT Product, [Q1], [Q2], [Q3], [Q4]
FROM (SELECT Product, Quarter, Sales FROM SalesData) AS src
PIVOT (SUM(Sales) FOR Quarter IN ([Q1], [Q2], [Q3], [Q4])) AS pvt;
```

--> **Not universally supported** -- native `PIVOT` syntax exists in SQL Server and Oracle; PostgreSQL and MySQL lack it directly, and typically fake the same result using a `CASE`-inside-`SUM` pattern instead:

```sql
-- PostgreSQL/MySQL equivalent, using conditional aggregation
SELECT Product,
    SUM(CASE WHEN Quarter = 'Q1' THEN Sales ELSE 0 END) AS Q1,
    SUM(CASE WHEN Quarter = 'Q2' THEN Sales ELSE 0 END) AS Q2
FROM SalesData
GROUP BY Product;
```

# UNPIVOT -- Columns Into Rows

--> `UNPIVOT` does the reverse -- takes several columns and rotates them back into rows, turning "wide" data back into "long" data (often needed to normalize a poorly-designed spreadsheet-style import back into a proper relational shape).

```sql
-- SQL Server syntax
SELECT Product, Quarter, Sales
FROM SalesWide
UNPIVOT (Sales FOR Quarter IN (Q1, Q2, Q3, Q4)) AS unpvt;
```

## SQL MERGE / Upsert Syntax

# The Upsert Problem

--> A very common need -- "insert this row, but if a row with this key already exists, update it instead" -- doing this safely with separate `SELECT` + `INSERT`/`UPDATE` statements has a race condition (another transaction could insert the same row in between the check and the insert) -- `MERGE`/upsert syntax handles it as a single ATOMIC operation.

# MERGE (SQL Server / Oracle / PostgreSQL 15+)

```sql
MERGE INTO Inventory AS target
USING (SELECT 101 AS ProductID, 50 AS Quantity) AS source
ON target.ProductID = source.ProductID
WHEN MATCHED THEN
    UPDATE SET target.Quantity = target.Quantity + source.Quantity
WHEN NOT MATCHED THEN
    INSERT (ProductID, Quantity) VALUES (source.ProductID, source.Quantity);
```

# INSERT ... ON DUPLICATE KEY UPDATE (MySQL)

```sql
INSERT INTO Inventory (ProductID, Quantity)
VALUES (101, 50)
ON DUPLICATE KEY UPDATE Quantity = Quantity + 50;
-- If ProductID 101 already exists (violates a UNIQUE/PRIMARY KEY), update instead of erroring
```

# INSERT ... ON CONFLICT DO UPDATE (PostgreSQL, SQLite)

```sql
INSERT INTO Inventory (ProductID, Quantity)
VALUES (101, 50)
ON CONFLICT (ProductID) DO UPDATE
SET Quantity = Inventory.Quantity + EXCLUDED.Quantity;
-- EXCLUDED refers to the row that WOULD have been inserted, letting you reference its values in the UPDATE
```

--> **Why this matters practically** -- this exact pattern is the backbone of idempotent data-loading jobs (an ETL pipeline that can safely re-run without creating duplicates), caching/counter tables (incrementing a view count that may or may not already have a row for today), and sync operations pulling data from an external API where the same record might arrive more than once.
