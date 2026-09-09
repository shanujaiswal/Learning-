## SQL TOP, LIMIT, FETCH FIRST or ROWNUM Clause

# The SQL SELECT TOP Clause

--> The SELECT TOP clause is used to specify the number of records to return.
--> The SELECT TOP clause is useful on large tables with thousands of records. Returning a large number of records can impact performance.

==> Example
Select only the first 3 records of the Customers table:

SELECT TOP 3 * FROM Customers;

**Note**: Not all database systems support the SELECT TOP clause. MySQL supports the LIMIT clause to select a limited number of records, while Oracle uses FETCH FIRST n ROWS ONLY and ROWNUM.

==> SQL Server / MS Access Syntax:

SELECT TOP number|percent column_name(s)
FROM table_name
WHERE condition;

==> MySQL Syntax:

SELECT column_name(s)
FROM table_name
WHERE condition
LIMIT number;

==> Oracle 12 Syntax:

SELECT column_name(s)
FROM table_name
ORDER BY column_name(s)
FETCH FIRST number ROWS ONLY;

==> Older Oracle Syntax:

SELECT column_name(s)
FROM table_name
WHERE ROWNUM <= number;

==> Older Oracle Syntax (with ORDER BY):

SELECT *
FROM (SELECT column_name(s) FROM table_name ORDER BY column_name(s))
WHERE ROWNUM <= number;

# LIMIT

--> The following SQL statement shows the equivalent example for MySQL:

==> Example
--> Select the first 3 records of the Customers table:

SELECT * FROM Customers
LIMIT 3;

# FETCH FIRST

--> The following SQL statement shows the equivalent example for Oracle:

==> Example
--> Select the first 3 records of the Customers table:

SELECT * FROM Customers
FETCH FIRST 3 ROWS ONLY;

# SQL TOP PERCENT Example

--> The following SQL statement selects the first 50% of the records from the "Customers" table (for SQL Server/MS Access):

==> Example
SELECT TOP 50 PERCENT * FROM Customers;

--> The following SQL statement shows the equivalent example for Oracle:

==> Example
SELECT * FROM Customers
FETCH FIRST 50 PERCENT ROWS ONLY;

# ADD a WHERE CLAUSE

--> The following SQL statement selects the first three records from the "Customers" table, where the country is "Germany" (for SQL Server/MS Access):

==> Example
SELECT TOP 3 * FROM Customers
WHERE Country='Germany';

The following SQL statement shows the equivalent example for MySQL:

==> Example
SELECT * FROM Customers
WHERE Country='Germany'
LIMIT 3;

--> The following SQL statement shows the equivalent example for Oracle:
==> Example
SELECT * FROM Customers
WHERE Country='Germany'
FETCH FIRST 3 ROWS ONLY;

# ADD the ORDER BY Keyword

--> Add the ORDER BY keyword when you want to sort the result, and return the first 3 records of the sorted result.

For SQL Server and MS Access:

==> Example
--> Sort the result reverse alphabetically by CustomerName, and return the first 3 records:

SELECT TOP 3 * FROM Customers
ORDER BY CustomerName DESC;

--> The following SQL statement shows the equivalent example for MySQL:

==> Example
SELECT * FROM Customers
ORDER BY CustomerName DESC
LIMIT 3;

--> The following SQL statement shows the equivalent example for Oracle:

==> Example
SELECT * FROM Customers
ORDER BY CustomerName DESC
FETCH FIRST 3 ROWS ONLY;

## Deep Dive -- LIMIT/TOP Without ORDER BY Is Non-Deterministic

--> A genuinely important, easy-to-miss caveat -- without an `ORDER BY`, a database is FREE to return rows in ANY order it finds convenient (often related to physical storage order, which can change after an update, a vacuum/reorganize operation, or even between two runs of the exact same query). `LIMIT 3` without `ORDER BY` might return a different 3 rows tomorrow than it does today, even with unchanged data.

```sql
-- Unreliable -- "first 3 records" is not a well-defined concept without an explicit sort
SELECT * FROM Customers LIMIT 3;

-- Reliable -- explicitly defines what "first" means
SELECT * FROM Customers ORDER BY CustomerID LIMIT 3;
```

--> Always pair `LIMIT`/`TOP`/`FETCH FIRST` with an `ORDER BY` clause whenever the specific rows returned matter (which is nearly always) -- this is especially critical for pagination, where inconsistent ordering between page requests can cause the same row to appear on two different pages, or never appear at all.

## Deep Dive -- OFFSET Pagination Performance at Scale

--> Directly connecting to the Pagination section of the API Design Patterns file -- combining `LIMIT` with `OFFSET` for pagination (`LIMIT 20 OFFSET 10000`) requires the database to still SCAN AND DISCARD all 10,000 skipped rows before returning the next 20 -- a genuinely real performance problem for deep pagination on large tables, since the cost keeps growing the further into the result set a user pages.

```sql
-- Gets progressively SLOWER for higher page numbers, since more rows must be scanned and discarded
SELECT * FROM Orders ORDER BY OrderID LIMIT 20 OFFSET 100000;

-- Cursor-based alternative -- consistently fast regardless of "page depth," since it jumps directly
-- to the right starting point using an index, rather than scanning and discarding
SELECT * FROM Orders WHERE OrderID > 100000 ORDER BY OrderID LIMIT 20;
```

--> This is exactly the concrete SQL mechanics behind the API Design Patterns file's recommendation to prefer cursor-based pagination over offset-based pagination for large, high-traffic datasets -- the cursor version can use an index to jump directly to the starting point (an index seek) rather than scanning past every preceding row.

