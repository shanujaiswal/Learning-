## SQL IN Operator

# The SQL IN Operator

--> The IN operator allows you to specify multiple values in a WHERE clause.
--> The IN operator is a shorthand for multiple OR conditions.

==> Example
--> Return all customers from 'Germany', 'France', or 'UK'

SELECT * FROM Customers
WHERE Country IN ('Germany', 'France', 'UK');

==> Syntax
SELECT column_name(s)
FROM table_name
WHERE column_name IN (value1, value2, ...);

# NOT IN

--> By using the NOT keyword in front of the IN operator, you return all records that are NOT any of the values in the list.

==> Example
--> Return all customers that are NOT from 'Germany', 'France', or 'UK':

SELECT * FROM Customers
WHERE Country NOT IN ('Germany', 'France', 'UK');

# IN (SELECT)

--> You can also use IN with a subquery in the WHERE clause.

--> With a subquery you can return all records from the main query that are present in the result of the subquery.

==> Example
--> Return all customers that have an order in the Orders table:

SELECT * FROM Customers
WHERE CustomerID IN (SELECT CustomerID FROM Orders);

# NOT IN (SELECT)

--> The result in the example above returned 74 records, that means that there are 17 customers that haven't placed any orders.

Let us check if that is correct, by using the NOT IN operator.

Example
Return all customers that have NOT placed any orders in the Orders table:

SELECT * FROM Customers
WHERE CustomerID NOT IN (SELECT CustomerID FROM Orders);

# SQL BETWEEN Operator

# The SQL BETWEEN Operator

--> The BETWEEN operator selects values within a given range. The values can be numbers, text, or dates.

--> The BETWEEN operator is inclusive: begin and end values are included.

==> Example
--> Selects all products with a price between 10 and 20:

SELECT * FROM Products
WHERE Price BETWEEN 10 AND 20;

==>Syntax
SELECT column_name(s)
FROM table_name
WHERE column_name BETWEEN value1 AND value2;

# NOT BETWEEN

--> To display the products outside the range of the previous example, use NOT BETWEEN:

==> Example
SELECT * FROM Products
WHERE Price NOT BETWEEN 10 AND 20;

# BETWEEN with IN

--> The following SQL statement selects all products with a price between 10 and 20. In addition, the CategoryID must be either 1,2, or 3:

==> Example

SELECT * FROM Products
WHERE Price BETWEEN 10 AND 20
AND CategoryID IN (1,2,3);

# BETWEEN Text Values

--> The following SQL statement selects all products with a ProductName alphabetically between Carnarvon Tigers and Mozzarella di Giovanni:

==> Example
SELECT * FROM Products
WHERE ProductName BETWEEN 'Carnarvon Tigers' AND 'Mozzarella di Giovanni'
ORDER BY ProductName;

--> The following SQL statement selects all products with a ProductName between Carnarvon Tigers and Chef Anton's Cajun Seasoning:

==> Example
SELECT * FROM Products
WHERE ProductName BETWEEN "Carnarvon Tigers" AND "Chef Anton's Cajun Seasoning"
ORDER BY ProductName;

# NOT BETWEEN Text Values

--> The following SQL statement selects all products with a ProductName not between Carnarvon Tigers and Mozzarella di Giovanni:

==> Example
SELECT * FROM Products
WHERE ProductName NOT BETWEEN 'Carnarvon Tigers' AND 'Mozzarella di Giovanni'
ORDER BY ProductName;

# BETWEEN Dates

--> The following SQL statement selects all orders with an OrderDate between '01-July-1996' and '31-July-1996':

==> Example
SELECT * FROM Orders
WHERE OrderDate BETWEEN #07/01/1996# AND #07/31/1996#;

OR:

==> Example
SELECT * FROM Orders
WHERE OrderDate BETWEEN '1996-07-01' AND '1996-07-31';

## Deep Dive -- The Infamous NOT IN + NULL Trap

--> This is one of the most notorious, easy-to-miss SQL gotchas -- if the subquery/list used with `NOT IN` contains even a SINGLE `NULL` value, the ENTIRE `NOT IN` condition returns no rows at all, silently, with no error.

```sql
-- If any order has a NULL CustomerID, this returns ZERO rows -- not "customers without orders"!
SELECT * FROM Customers
WHERE CustomerID NOT IN (SELECT CustomerID FROM Orders);
```

--> Why this happens -- `NOT IN (1, 2, NULL)` is logically evaluated as `<> 1 AND <> 2 AND <> NULL` -- and as covered in the SQL Introduction file, any comparison to `NULL` evaluates to UNKNOWN, not TRUE or FALSE, which poisons the entire `AND` chain to UNKNOWN, causing the whole condition to fail to match anything.
--> **The safe fix** -- either filter out NULLs explicitly in the subquery, or use `NOT EXISTS` instead, which doesn't have this trap at all.

```sql
-- Fix 1: filter NULLs out of the subquery explicitly
SELECT * FROM Customers
WHERE CustomerID NOT IN (SELECT CustomerID FROM Orders WHERE CustomerID IS NOT NULL);

-- Fix 2 (generally preferred) -- NOT EXISTS doesn't suffer from this NULL trap at all
SELECT * FROM Customers c
WHERE NOT EXISTS (SELECT 1 FROM Orders o WHERE o.CustomerID = c.CustomerID);
```

--> **Practical guidance** -- given this trap, many experienced SQL developers default to `NOT EXISTS` over `NOT IN` specifically for subqueries where the referenced column's NULL-ability isn't 100% certain, since `NOT EXISTS` is immune to this issue entirely and behaves the way most people initially expect `NOT IN` to.

## Deep Dive -- IN vs EXISTS Performance for Subqueries

--> For a NON-negated `IN`/`EXISTS`, both are usually optimized similarly by modern query planners, but `EXISTS` can have an edge on large subqueries specifically because it can STOP as soon as it finds ONE matching row, while `IN` (depending on the database and plan) may need to first materialize the full list of subquery results before comparing.

```sql
-- Functionally equivalent, but EXISTS can short-circuit as soon as one match is found
SELECT * FROM Customers WHERE CustomerID IN (SELECT CustomerID FROM Orders);
SELECT * FROM Customers c WHERE EXISTS (SELECT 1 FROM Orders o WHERE o.CustomerID = c.CustomerID);
```

--> As always with performance questions, checking the actual `EXPLAIN` plan (covered in the Query Execution Plans file) for your specific database and data is more reliable than assuming one form is always faster -- modern optimizers (especially PostgreSQL's) often rewrite `IN` and `EXISTS` into the same underlying plan anyway.

## Deep Dive -- BETWEEN and Dates -- A Time-of-Day Gotcha

--> `BETWEEN` is inclusive on both ends (as the file states) -- but for `DATETIME`/`TIMESTAMP` columns, this creates a subtle trap: `BETWEEN '2026-01-01' AND '2026-01-31'` actually means `BETWEEN '2026-01-01 00:00:00' AND '2026-01-31 00:00:00'`, SILENTLY EXCLUDING any records timestamped later than midnight on the 31st -- effectively missing almost the entire last day.

```sql
-- BUG: excludes most of January 31st (anything after midnight)
SELECT * FROM Orders WHERE OrderDate BETWEEN '2026-01-01' AND '2026-01-31';

-- Fix: use an explicit half-open range instead of BETWEEN for datetime columns
SELECT * FROM Orders WHERE OrderDate >= '2026-01-01' AND OrderDate < '2026-02-01';
```

--> This exact pattern -- using `>=` and `<` (a "half-open" range) instead of `BETWEEN` -- is the standard, recommended way to query date RANGES that include full days, specifically to sidestep this time-component trap entirely.
