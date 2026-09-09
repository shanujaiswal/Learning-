## SQL EXISTS Operator

# The SQL EXISTS Operator

--> The EXISTS operator is used to test for the existence of any record in a subquery.
--> The EXISTS operator returns TRUE if the subquery returns one or more records.

==> EXISTS Syntax
SELECT column_name(s)
FROM table_name
WHERE EXISTS
(SELECT column_name FROM table_name WHERE condition);

# SQL EXISTS Examples

The following SQL statement returns TRUE and lists the suppliers with a product price less than 20:

==> Example
SELECT SupplierName
FROM Suppliers
WHERE EXISTS (SELECT ProductName FROM Products WHERE Products.SupplierID = Suppliers.supplierID AND Price < 20);

## SQL ANY and ALL Operators

# The SQL ANY and ALL Operators

--> The ANY and ALL operators allow you to perform a comparison between a single column value and a range of other values.

# The SQL ANY Operator

--> The ANY operator:

1) returns a boolean value as a result
2) returns TRUE if ANY of the subquery values meet the condition

--> ANY means that the condition will be true if the operation is true for any of the values in the range.

# ANY Syntax

SELECT column_name(s)
FROM table_name
WHERE column_name operator ANY
  (SELECT column_name
  FROM table_name
  WHERE condition);

**Note**: The operator must be a standard comparison operator (=, <>, !=, >, >=, <, or <=).

# The SQL ALL Operator

--> The ALL operator:

1) returns a boolean value as a result
2) returns TRUE if ALL of the subquery values meet the condition
3) is used with SELECT, WHERE and HAVING statements
--> ALL means that the condition will be true only if the operation is true for all values in the range.

# ALL Syntax With SELECT

SELECT ALL column_name(s)
FROM table_name
WHERE condition;

# ALL Syntax With WHERE or HAVING

SELECT column_name(s)
FROM table_name
WHERE column_name operator ALL
  (SELECT column_name
  FROM table_name
  WHERE condition);

**Note**: The operator must be a standard comparison operator (=, <>, !=, >, >=, <, or <=).

# SQL ANY Examples

--> The following SQL statement lists the ProductName if it finds ANY records in the OrderDetails table has Quantity equal to 10 (this will return TRUE because the Quantity column has some values of 10):

==> Example

SELECT ProductName
FROM Products
WHERE ProductID = ANY
  (SELECT ProductID
  FROM OrderDetails
  WHERE Quantity = 10);

--> The following SQL statement lists the ProductName if it finds ANY records in the OrderDetails table has Quantity larger than 99 (this will return TRUE because the Quantity column has some values larger than 99):

==> Example

SELECT ProductName
FROM Products
WHERE ProductID = ANY
  (SELECT ProductID
  FROM OrderDetails
  WHERE Quantity > 99);

--> The following SQL statement lists the ProductName if it finds ANY records in the OrderDetails table has Quantity larger than 1000 (this will return FALSE because the Quantity column has no values larger than 1000):

==> Example
SELECT ProductName
FROM Products
WHERE ProductID = ANY
  (SELECT ProductID
  FROM OrderDetails
  WHERE Quantity > 1000);

# SQL ALL Examples

The following SQL statement lists ALL the product names:

==> Example
SELECT ALL ProductName
FROM Products
WHERE TRUE;

--> The following SQL statement lists the ProductName if ALL the records in the OrderDetails table has Quantity equal to 10. This will of course return FALSE because the Quantity column has many different values (not only the value of 10):

==> Example
SELECT ProductName
FROM Products
WHERE ProductID = ALL
  (SELECT ProductID
  FROM OrderDetails
  WHERE Quantity = 10);

## SQL SELECT INTO Statement

# The SQL SELECT INTO Statement

-->  The SELECT INTO statement copies data from one table into a new table.

# SELECT INTO Syntax

Copy all columns into a new table:
SELECT *
INTO newtable [IN externaldb]
FROM oldtable
WHERE condition;
Copy only some columns into a new table:

SELECT column1, column2, column3, ...
INTO newtable [IN externaldb]
FROM oldtable
WHERE condition;
The new table will be created with the column-names and types as defined in the old table. You can create new column names using the AS clause.

# SQL SELECT INTO Examples

--> The following SQL statement creates a backup copy of Customers:

SELECT * INTO CustomersBackup2017
FROM Customers;

--> The following SQL statement uses the IN clause to copy the table into a new table in another database:

SELECT * INTO CustomersBackup2017 IN 'Backup.mdb'
FROM Customers;

--> The following SQL statement copies only a few columns into a new table:

SELECT CustomerName, ContactName INTO CustomersBackup2017
FROM Customers;

--> The following SQL statement copies only the German customers into a new table:

SELECT * INTO CustomersGermany
FROM Customers
WHERE Country = 'Germany';

--> The following SQL statement copies data from more than one table into a new table:

SELECT Customers.CustomerName, Orders.OrderID
INTO CustomersOrderBackup2017
FROM Customers
LEFT JOIN Orders ON Customers.CustomerID = Orders.CustomerID;

**Notes**: SELECT INTO can also be used to create a new, empty table using the schema of another. Just add a WHERE clause that causes the query to return no data:

SELECT * INTO newtable
FROM oldtable
WHERE 1 = 0;

## SQL INSERT INTO SELECT Statement

# The SQL INSERT INTO SELECT Statement

--> The INSERT INTO SELECT statement copies data from one table and inserts it into another table.

--> The INSERT INTO SELECT statement requires that the data types in source and target tables match.

**Note**: The existing records in the target table are unaffected.

# INSERT INTO SELECT Syntax

--> Copy all columns from one table to another table:

INSERT INTO table2
SELECT * FROM table1
WHERE condition;

--> Copy only some columns from one table into another table:

INSERT INTO table2 (column1, column2, column3, ...)
SELECT column1, column2, column3, ...
FROM table1
WHERE condition;

# SQL INSERT INTO SELECT Examples

==>  Example
--> Copy "Suppliers" into "Customers" (the columns that are not filled with data, will contain NULL):

INSERT INTO Customers (CustomerName, City, Country)
SELECT SupplierName, City, Country FROM Suppliers;

==> Example
--> Copy "Suppliers" into "Customers" (fill all columns):

INSERT INTO Customers (CustomerName, ContactName, Address, City, PostalCode, Country)
SELECT SupplierName, ContactName, Address, City, PostalCode, Country FROM Suppliers;

==> Example
--> Copy only the German suppliers into "Customers":

INSERT INTO Customers (CustomerName, City, Country)
SELECT SupplierName, City, Country FROM Suppliers
WHERE Country='Germany';

## Deep Dive -- SELECT INTO Is Not Universally Supported

--> A genuinely important caveat missing from the syntax above -- `SELECT ... INTO ...` (to create a NEW table from a query) is supported by SQL Server and MS Access, but **NOT by MySQL**, where `INTO` after `SELECT` is instead used for a completely different purpose (assigning selected values into variables inside a stored procedure). PostgreSQL supports a similar but differently-named `SELECT INTO` / `CREATE TABLE AS` distinction.

```sql
-- MySQL / PostgreSQL equivalent of "SELECT INTO" for creating a new table from a query:
CREATE TABLE CustomersBackup2017 AS
SELECT * FROM Customers;
```

--> This is a good, concrete reminder of the "SQL is a standard, BUT" caveat raised in the very first SQL file -- syntax that works perfectly in one database can be entirely unavailable, or mean something different, in another, and checking your SPECIFIC database's documentation before assuming a query pattern transfers directly is a necessary habit, not just a theoretical concern.

## Deep Dive -- ANY/SOME Are Interchangeable, and How = ANY Relates to IN

--> `SOME` is an exact synonym for `ANY` in standard SQL (PostgreSQL and a few other databases support both keywords identically) -- purely a stylistic/readability choice with zero functional difference.
--> `= ANY (subquery)` is functionally equivalent to `IN (subquery)` -- both check whether a value matches ANY row returned by the subquery. `<> ALL (subquery)` is functionally equivalent to `NOT IN (subquery)` -- and inherits the EXACT same NULL trap covered in the IN and BETWEEN Operators file, since the underlying comparison logic is identical.

```sql
-- These two are functionally equivalent:
WHERE ProductID = ANY (SELECT ProductID FROM OrderDetails WHERE Quantity = 10)
WHERE ProductID IN (SELECT ProductID FROM OrderDetails WHERE Quantity = 10)
```

--> Where `ANY`/`ALL` genuinely add something `IN`/`NOT IN` can't express is with comparison operators OTHER than equality -- `WHERE Price > ALL (subquery)` ("greater than every single value returned") or `WHERE Price > ANY (subquery)` ("greater than at least one value returned") have no direct `IN`-based equivalent, since `IN` only ever expresses equality-based membership.
