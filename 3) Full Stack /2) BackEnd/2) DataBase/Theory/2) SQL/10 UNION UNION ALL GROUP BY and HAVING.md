## SQL UNION Operator

# The SQL UNION Operator

--> The UNION operator is used to combine the result-set of two or more SELECT statements.

--> The UNION operator automatically removes duplicate rows from the result set.

--> Requirements for UNION:

1) Every SELECT statement within UNION must have the same number of columns
2) The columns must also have similar data types
3) The columns in every SELECT statement must also be in the same order

==> UNION Syntax
SELECT column_name(s) FROM table1
UNION
SELECT column_name(s) FROM table2;

**Note**: The column names in the result-set are usually equal to the column names in the first SELECT statement.

# SQL UNION Example

The following SQL statement returns the cities (only distinct values) from both the "Customers" and the "Suppliers" table:

==> Example
SELECT City FROM Customers
UNION
SELECT City FROM Suppliers
ORDER BY City;

**Note**: If some customers or suppliers have the same city, each city will only be listed once, because UNION selects only distinct values. Use UNION ALL to also select duplicate values!

# SQL UNION With WHERE

The following SQL statement returns the German cities (only distinct values) from both the "Customers" and the "Suppliers" table:

==> Example
SELECT City, Country FROM Customers
WHERE Country='Germany'
UNION
SELECT City, Country FROM Suppliers
WHERE Country='Germany'
ORDER BY City;

# Another UNION Example

--> The following SQL statement lists all customers and suppliers:

==> Example
SELECT 'Customer' AS Type, ContactName, City, Country
FROM Customers
UNION
SELECT 'Supplier', ContactName, City, Country
FROM Suppliers;

**Note**: the "AS Type" above - it is an alias. SQL Aliases are used to give a table or a column a temporary name. An alias only exists for the duration of the query. So, here we have created a temporary column named "Type", that list whether the contact person is a "Customer" or a "Supplier".

## SQL UNION ALL Operator

# The SQL UNION ALL Operator

--> The UNION ALL operator is used to combine the result-set of two or more SELECT statements.
--> The UNION ALL operator includes all rows from each statement, including any duplicates.
--> Requirements for UNION ALL:

1) Every SELECT statement within UNION ALL must have the same number of columns
2) The columns must also have similar data types
3) The columns in every SELECT statement must also be in the same order

# UNION ALL Syntax

--> While the UNION operator removes duplicate values by default, the UNION ALL includes duplicate values:

SELECT column_name(s) FROM table1
UNION ALL
SELECT column_name(s) FROM table2;

**Note**: The column names in the result-set are usually equal to the column names in the first SELECT statement.

# SQL UNION ALL Example

--> The following SQL statement returns the cities (duplicate values also) from both the "Customers" and the "Suppliers" table:

==> Example
SELECT City FROM Customers
UNION ALL
SELECT City FROM Suppliers
ORDER BY City;

# SQL UNION ALL With WHERE

--> The following SQL statement returns the German cities (duplicate values also) from both the "Customers" and the "Suppliers" table:

==> Example
SELECT City, Country FROM Customers
WHERE Country='Germany'
UNION ALL
SELECT City, Country FROM Suppliers
WHERE Country='Germany'
ORDER BY City;

## SQL GROUP BY Statement

# The SQL GROUP BY Statement

--> The GROUP BY statement groups rows that have the same values into summary rows, like "find the number of customers in each country".

--> The GROUP BY statement is often used with aggregate functions (COUNT(), MAX(), MIN(), SUM(), AVG()) to group the result-set by one or more columns.

==> GROUP BY Syntax
SELECT column_name(s)
FROM table_name
WHERE condition
GROUP BY column_name(s)
ORDER BY column_name(s);

# SQL GROUP BY Examples

--> The following SQL statement lists the number of customers in each country:

==> Example
SELECT COUNT(CustomerID), Country
FROM Customers
GROUP BY Country;
ORDER BY COUNT(CustomerID) DESC;

# GROUP BY With JOIN Example

--> The following SQL statement lists the number of orders sent by each shipper:

==> Example
SELECT Shippers.ShipperName, COUNT(Orders.OrderID) AS NumberOfOrders FROM Orders
LEFT JOIN Shippers ON Orders.ShipperID = Shippers.ShipperID
GROUP BY ShipperName;

## SQL HAVING Clause

# The SQL HAVING Clause

--> The HAVING clause was added to SQL because the WHERE keyword cannot be used with aggregate functions.

==> HAVING Syntax
SELECT column_name(s)
FROM table_name
WHERE condition
GROUP BY column_name(s)
HAVING condition
ORDER BY column_name(s);

# SQL HAVING Examples

The following SQL statement lists the number of customers in each country. Only include countries with more than 5 customers:

==> Example
SELECT COUNT(CustomerID), Country
FROM Customers
GROUP BY Country
HAVING COUNT(CustomerID) > 5;

--> The following SQL statement lists the number of customers in each country, sorted high to low (Only include countries with more than 5 customers):

==> Example
SELECT COUNT(CustomerID), Country
FROM Customers
GROUP BY Country
HAVING COUNT(CustomerID) > 5
ORDER BY COUNT(CustomerID) DESC;

--> The following SQL statement lists if the employees "Davolio" or "Fuller" have registered more than 25 orders:

==> Example
SELECT Employees.LastName, COUNT(Orders.OrderID) AS NumberOfOrders
FROM Orders
INNER JOIN Employees ON Orders.EmployeeID = Employees.EmployeeID
WHERE LastName = 'Davolio' OR LastName = 'Fuller'
GROUP BY LastName
HAVING COUNT(Orders.OrderID) > 25;

**Note**: The difference between the WHERE and HAVING clauses is The WHERE clause filters rows; the HAVING clause filters groups

## Deep Dive -- Why UNION ALL Is Faster Than UNION

--> Plain `UNION` must remove duplicate rows from the combined result -- to do this, the database has to compare every row against every other row (typically via a sort or a hash-based deduplication step), an inherently expensive operation on large result sets. `UNION ALL` skips this entirely, simply concatenating the results together.
--> **Practical guidance** -- if you already KNOW the two result sets can't overlap (e.g. combining "orders from 2024" with "orders from 2025" -- a date range guarantees no duplicate rows between them), always use `UNION ALL` instead of `UNION` -- there's no correctness reason to pay for deduplication that can never actually find anything to deduplicate.

## Deep Dive -- The Logical Order of SQL Query Execution

--> SQL is WRITTEN in the order `SELECT, FROM, WHERE, GROUP BY, HAVING, ORDER BY` -- but it does NOT EXECUTE in that order. Understanding the actual logical execution order explains exactly why `HAVING` can use aggregate functions while `WHERE` cannot, and why you can't reference a `SELECT`-defined alias inside a `WHERE` clause.

```
Actual logical execution order:
1. FROM       -- identify the source table(s), including JOINs
2. WHERE       -- filter individual ROWS (before any grouping happens)
3. GROUP BY     -- group the remaining rows
4. HAVING        -- filter GROUPS (aggregate functions are available here, since grouping already happened)
5. SELECT          -- compute the actual output columns/expressions
6. ORDER BY         -- sort the final result
```

--> This ordering is precisely WHY `WHERE` can't use `COUNT()`/`SUM()` -- at the point `WHERE` executes, grouping hasn't happened yet, so there's no aggregate value to filter on. By the time `HAVING` executes, groups already exist, so aggregate functions are meaningful there.
--> It's also why this is an ERROR in most databases: `SELECT price * 1.1 AS taxed_price FROM products WHERE taxed_price > 100` -- `WHERE` executes BEFORE `SELECT` computes the `taxed_price` alias, so `WHERE` has no idea what `taxed_price` even is yet. The fix is to either repeat the full expression in `WHERE`, or wrap the query in a subquery/CTE (covered in the Common Table Expressions file) that computes it first.

## Deep Dive -- GROUP BY With Multiple Columns

--> Grouping by more than one column creates a separate group for every UNIQUE COMBINATION of those columns' values, not a separate group per column.

```sql
SELECT Country, City, COUNT(CustomerID) AS CustomerCount
FROM Customers
GROUP BY Country, City;
-- Produces one row per unique (Country, City) pair -- e.g. a separate count for "Germany, Berlin"
-- AND a separate count for "Germany, Munich", rather than one combined "Germany" total
```

--> **A common mistake** -- every column in the `SELECT` list that ISN'T inside an aggregate function must appear in the `GROUP BY` clause (most databases enforce this and will error otherwise) -- selecting a column the database can't guarantee is consistent within a group (since a group might span multiple different values of that column) is not logically well-defined, which is exactly why this restriction exists.
