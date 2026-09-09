## SQL Aggregate Functions

-->  An aggregate function is a function that performs a calculation on a set of values, and returns a single value.
-->  Aggregate functions are often used with the GROUP BY clause of the SELECT statement. The GROUP BY clause splits the result-set into groups of values and the aggregate function can be used to return a single value for each group.
-->  The most commonly used SQL aggregate functions are:

MIN() - returns the smallest value within the selected column
MAX() - returns the largest value within the selected column
COUNT() - returns the number of rows in a set
SUM() - returns the total sum of a numerical column
AVG() - returns the average value of a numerical column

--> Aggregate functions ignore null values (except for COUNT(*)).

## SQL MIN() and MAX() Functions

# The SQL MIN() and MAX() Functions

--> The MIN() function returns the smallest value of the selected column.

--> The MAX() function returns the largest value of the selected column.

==> MIN Example
--> Find the lowest price in the Price column:

SELECT MIN(Price)
FROM Products;

==> MAX Example
--> Find the highest price in the Price column:

SELECT MAX(Price)
FROM Products;

==> Syntax
SELECT MIN(column_name)
FROM table_name
WHERE condition;

SELECT MAX(column_name)
FROM table_name
WHERE condition;

# Set Column Name (Alias)

==> When you use MIN() or MAX(), the returned column will not have a descriptive name. To give the column a descriptive name, use the AS keyword:

Example
SELECT MIN(Price) AS SmallestPrice
FROM Products;

# Use MIN() with GROUP BY

--> Here we use the MIN() function and the GROUP BY clause, to return the smallest price for each category in the Products table:

==> Example
SELECT MIN(Price) AS SmallestPrice, CategoryID
FROM Products
GROUP BY CategoryID;

## SQL COUNT() Function

# The SQL COUNT() Function

--> The COUNT() function returns the number of rows that matches a specified criterion.

==> Example
--> Find the total number of rows in the Products table:

SELECT COUNT(*)
FROM Products;

==> Syntax
SELECT COUNT(column_name)
FROM table_name
WHERE condition;

# Specify Column

--> You can specify a column name instead of the asterix symbol (*).
--> If you specify a column name instead of (*), NULL values will not be counted.

==> Example
--> Find the number of products where the ProductName is not null:

SELECT COUNT(ProductName)
FROM Products;

# Add a WHERE Clause

You can add a WHERE clause to specify conditions:

==> Example
--> Find the number of products where Price is higher than 20:

SELECT COUNT(ProductID)
FROM Products
WHERE Price > 20;

# Ignore Duplicates

--> You can ignore duplicates by using the DISTINCT keyword in the COUNT() function.
--> If DISTINCT is specified, rows with the same value for the specified column will be counted as one.

==> Example
--> How many different prices are there in the Products table:

SELECT COUNT(DISTINCT Price)
FROM Products;

# Use an Alias

--> Give the counted column a name by using the AS keyword.

==> Example
--> Name the column "Number of records":

SELECT COUNT(*) AS [Number of records]
FROM Products;

# Use COUNT() with GROUP BY

--> Here we use the COUNT() function and the GROUP BY clause, to return the number of records for each category in the Products table:

==> Example
SELECT COUNT(*) AS [Number of records], CategoryID
FROM Products
GROUP BY CategoryID;

## SQL SUM() Function

# The SQL SUM() Function

--> The SUM() function returns the total sum of a numeric column.

==> Example
--> Return the sum of all Quantity fields in the OrderDetails table:

SELECT SUM(Quantity)
FROM OrderDetails;

==> Syntax
SELECT SUM(column_name)
FROM table_name
WHERE condition;

# Add a WHERE Clause

--> You can add a WHERE clause to specify conditions:

==> Example
--> Return the sum of the Quantity field for the product with ProductID 11:

SELECT SUM(Quantity)
FROM OrderDetails
WHERE ProductId = 11;

# Use an Alias

--> Give the summarized column a name by using the AS keyword.

==> Example
--> Name the column "total":

SELECT SUM(Quantity) AS total
FROM OrderDetails;

# Use SUM() with GROUP BY

--> Here we use the SUM() function and the GROUP BY clause, to return the Quantity for each OrderID in the OrderDetails table:

==> Example
SELECT OrderID, SUM(Quantity) AS [Total Quantity]
FROM OrderDetails
GROUP BY OrderID;

# SUM() With an Expression

--> The parameter inside the SUM() function can also be an expression.
--> If we assume that each product in the OrderDetails column costs 10 dollars, we can find the total earnings in dollars by multiply each quantity with 10:

==> Example
--> Use an expression inside the SUM() function:

SELECT SUM(Quantity * 10)
FROM OrderDetails;

--> We can also join the OrderDetails table to the Products table to find the actual amount, instead of assuming it is 10 dollars:

==> Example
--> Join OrderDetails with Products, and use SUM() to find the total amount:

SELECT SUM(Price * Quantity)
FROM OrderDetails
LEFT JOIN Products ON OrderDetails.ProductID = Products.ProductID;

## SQL AVG() Function

# The SQL AVG() Function

--> The AVG() function returns the average value of a numeric column.

==> Example
--> Find the average price of all products:

SELECT AVG(Price)
FROM Products;

**Note** : NULL values are ignored.

==> Syntax
SELECT AVG(column_name)
FROM table_name
WHERE condition;

# Add a WHERE Clause

--> You can add a WHERE clause to specify conditions:

==> Example
--> Return the average price of products in category 1:

SELECT AVG(Price)
FROM Products
WHERE CategoryID = 1;

# Use an Alias

--> Give the AVG column a name by using the AS keyword.

==> Example
--> Name the column "average price":

SELECT AVG(Price) AS [average price]
FROM Products;

# Higher Than Average

--> To list all records with a higher price than average, we can use the AVG() function in a sub query:

==> Example
--> Return all products with a higher price than the average price:

SELECT * FROM Products
WHERE price > (SELECT AVG(price) FROM Products);

Use AVG() with GROUP BY
Here we use the AVG() function and the GROUP BY clause, to return the average price for each category in the Products table:

==> Example
SELECT AVG(Price) AS AveragePrice, CategoryID
FROM Products
GROUP BY CategoryID;

## Deep Dive -- COUNT(*) vs COUNT(column) vs COUNT(1)

--> These three look interchangeable but have a real semantic difference worth knowing precisely:
--> `COUNT(*)` -- counts every ROW, regardless of whether any column is NULL.
--> `COUNT(column_name)` -- counts only rows where THAT SPECIFIC column is NOT NULL -- as the file already notes, this is why `COUNT(ProductName)` can return a smaller number than `COUNT(*)` if some products have a NULL name.
--> `COUNT(1)` -- functionally equivalent to `COUNT(*)` in every modern database (counts every row) -- the old belief that `COUNT(1)` is meaningfully faster than `COUNT(*)` is an outdated myth from older database engines; modern query optimizers treat them identically.

## Deep Dive -- Aggregate Functions Silently Ignore NULLs (Except COUNT(*))

--> This is stated in this file for `AVG()`, but it applies to EVERY aggregate function (`SUM`, `MIN`, `MAX`, `AVG`) -- NULL values are excluded from the calculation entirely, not treated as zero.

```sql
-- If the Discount column has values [10, NULL, 20, NULL, 30]:
SELECT AVG(Discount) FROM Products;   -- (10+20+30)/3 = 20 -- NOT (10+0+20+0+30)/5 = 12
SELECT COUNT(Discount) FROM Products;  -- 3 -- only counts the non-NULL values
SELECT COUNT(*) FROM Products;          -- 5 -- counts every row regardless of NULL
```

--> This is frequently a source of subtle bugs when a developer EXPECTS a NULL to be treated as zero for an average/sum calculation -- if that's genuinely the desired behavior, wrap the column in `COALESCE(Discount, 0)` (covered in the CASE Expression and NULL Functions file) to explicitly convert NULLs to zero BEFORE aggregating.

## Deep Dive -- The FILTER Clause (PostgreSQL) -- Conditional Aggregation

--> Rather than running multiple separate queries (or a single query with a `CASE` expression, covered in its own file) to compute different aggregates under different conditions, PostgreSQL's `FILTER` clause lets you apply a condition to a SPECIFIC aggregate function within one query.

```sql
SELECT
    COUNT(*) AS total_orders,
    COUNT(*) FILTER (WHERE status = 'completed') AS completed_orders,
    COUNT(*) FILTER (WHERE status = 'cancelled') AS cancelled_orders,
    SUM(amount) FILTER (WHERE status = 'completed') AS completed_revenue
FROM orders;
```

--> This produces multiple differently-filtered aggregate results side by side, in ONE pass over the table, instead of running three or four separate queries (or a more verbose `CASE WHEN ... THEN ... END` inside `SUM`) -- a genuinely convenient, readable pattern for building analytics/dashboard-style queries directly connecting to the Data Analyst folder's dashboard-building use cases.
