## SQL Aliases

# SQL Aliases

--> SQL aliases are used to give a table, or a column in a table, a temporary name.
--> Aliases are often used to make column names more readable.
--> An alias only exists for the duration of that query.
--> An alias is created with the AS keyword.

==> Example

SELECT CustomerID AS ID
FROM Customers;

# AS is Optional

Actually, in most database languages, you can skip the AS keyword and get the same result:

==> Example
SELECT CustomerID ID
FROM Customers;

==> Syntax
i) When alias is used on column:

SELECT column_name AS alias_name
FROM table_name;

ii) When alias is used on table:

SELECT column_name(s)
FROM table_name AS alias_name;

==> Alias for Columns
--> The following SQL statement creates two aliases, one for the CustomerID column and one for the CustomerName column:

==> Example
SELECT CustomerID AS ID, CustomerName AS Customer
FROM Customers;

# Using Aliases With a Space Character

If you want your alias to contain one or more spaces, like "My Great Products", surround your alias with square brackets or double quotes.

==> Example
--> Using [square brackets] for aliases with space characters:

SELECT ProductName AS [My Great Products]
FROM Products;

==> Example
--> Using "double quotes" for aliases with space characters:

SELECT ProductName AS "My Great Products"
FROM Products;

**Note**: Some database systems allows both [] and "", and some only allows one of them.

# Concatenate Columns

--> The following SQL statement creates an alias named "Address" that combine four columns (Address, PostalCode, City and Country):

==> Example
SELECT CustomerName, Address + ', ' + PostalCode + ' ' + City + ', ' + Country AS Address
FROM Customers;

**Note**: To get the SQL statement above to work in MySQL use the following:

==> MySQL Example
SELECT CustomerName, CONCAT(Address,', ',PostalCode,', ',City,', ',Country) AS Address
FROM Customers;

**Note**: To get the SQL statement above to work in Oracle use the following:

==> Oracle Example
SELECT CustomerName, (Address || ', ' || PostalCode || ' ' || City || ', ' || Country) AS Address
FROM Customers;

# Alias for Tables

--> The same rules applies when you want to use an alias for a table.

==> Example
--> Refer to the Customers table as Persons instead:

SELECT * FROM Customers AS Persons;

**Notes**
==> It might seem useless to use aliases on tables, but when you are using more than one table in your queries, it can make the SQL statements shorter.

==> The following SQL statement selects all the orders from the customer with CustomerID=4 (Around the Horn). We use the "Customers" and "Orders" tables, and give them the table aliases of "c" and "o" respectively (Here we use aliases to make the SQL shorter):

==> Example
SELECT o.OrderID, o.OrderDate, c.CustomerName
FROM Customers AS c, Orders AS o
WHERE c.CustomerName='Around the Horn' AND c.CustomerID=o.CustomerID;

--> The following SQL statement is the same as above, but without aliases:

==> Example
SELECT Orders.OrderID, Orders.OrderDate, Customers.CustomerName
FROM Customers, Orders
WHERE Customers.CustomerName='Around the Horn' AND Customers.CustomerID=Orders.CustomerID;
Aliases can be useful when:

==> There are more than one table involved in a query
i) Functions are used in the query
ii) Column names are big or not very readable
iii) Two or more columns are combined together

## Deep Dive -- Why a Column Alias Can't Be Used in WHERE

--> Directly connecting to the Logical Order of SQL Query Execution deep dive in the UNION/GROUP BY/HAVING file -- `SELECT` (where aliases are defined) executes AFTER `WHERE` (and even after `GROUP BY`/`HAVING`) -- so a `WHERE` clause referencing a `SELECT`-defined alias is referencing something that doesn't exist yet at the point `WHERE` actually runs.

```sql
-- Error in most databases -- "taxed_price" doesn't exist yet when WHERE executes
SELECT price * 1.1 AS taxed_price FROM products WHERE taxed_price > 100;

-- Fix 1: repeat the full expression
SELECT price * 1.1 AS taxed_price FROM products WHERE price * 1.1 > 100;

-- Fix 2: wrap in a subquery/CTE so the alias is fully computed and materialized first
SELECT * FROM (SELECT price * 1.1 AS taxed_price FROM products) AS sub WHERE taxed_price > 100;
```

--> Interestingly, MOST databases (though not strictly per the SQL standard) DO allow referencing a `SELECT` alias inside `GROUP BY`, `HAVING`, and `ORDER BY` -- because those clauses execute at or after the point the alias becomes meaningful in the actual execution order -- but `WHERE` specifically cannot, precisely because of where it sits in that execution sequence.

## Deep Dive -- Table Aliases Are Mandatory for Self Joins and Correlated Subqueries

--> Beyond making queries shorter (as the file notes), table aliases are structurally REQUIRED whenever the same table needs to be referenced more than once in a single query -- without an alias, the database has no way to distinguish which "instance" of the table a column reference belongs to.

```sql
-- Required, not optional -- directly connecting to the Self Join example in the SQL Joins file
SELECT A.CustomerName, B.CustomerName, A.City
FROM Customers A, Customers B
WHERE A.CustomerID <> B.CustomerID AND A.City = B.City;

-- Also required in a correlated subquery referencing the OUTER query's table
SELECT * FROM Products p
WHERE p.Price > (SELECT AVG(p2.Price) FROM Products p2 WHERE p2.CategoryID = p.CategoryID);
-- "p" and "p2" both refer to the SAME Products table, but must be distinguished to correlate correctly
```

--> This is precisely why the `A`/`B` aliasing convention appears throughout self-join and correlated-subquery examples elsewhere in these SQL notes -- it isn't a style preference in those specific cases, it's the only way the query can be written at all.
