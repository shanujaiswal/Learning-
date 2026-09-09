## SQL INSERT INTO Statement

# The SQL INSERT INTO Statement

==> The INSERT INTO statement is used to **insert new records** in a table.

insert  --> values

# INSERT INTO Syntax

--> It is possible to write the INSERT INTO statement in two ways:

1. Specify both the column names and the values to be inserted:

INSERT INTO table_name (column1, column2, column3, ...)
VALUES (value1, value2, value3, ...);

2. If you are adding values for all the columns of the table, you do not need to specify the column names in the SQL query. However, make sure the order of the values is in the same order as the columns in the table. Here, the INSERT INTO syntax would be as follows:

INSERT INTO table_name
VALUES (value1, value2, value3, ...);

# INSERT INTO Example

==> The following SQL statement inserts a new record in the "Customers" table:

==> Example
INSERT INTO Customers (CustomerName, ContactName, Address, City, PostalCode, Country)
VALUES ('Cardinal', 'Tom B. Erichsen', 'Skagen 21', 'Stavanger', '4006', 'Norway');

# Insert Data Only in Specified Columns

--> It is also possible to only insert data in specific columns.

--> The following SQL statement will insert a new record, but only insert data in the "CustomerName", "City", and "Country" columns (CustomerID will be updated automatically):

==> Example
INSERT INTO Customers (CustomerName, City, Country)
VALUES ('Cardinal', 'Stavanger', 'Norway');

# Insert Multiple Rows

--> It is also possible to insert multiple rows in one statement.
--> To insert multiple rows of data, we use the same INSERT INTO statement, but with multiple values:

==> Example
INSERT INTO Customers (CustomerName, ContactName, Address, City, PostalCode, Country)
VALUES
('Cardinal', 'Tom B. Erichsen', 'Skagen 21', 'Stavanger', '4006', 'Norway'),
('Greasy Burger', 'Per Olsen', 'Gateveien 15', 'Sandnes', '4306', 'Norway'),
('Tasty Tee', 'Finn Egan', 'Streetroad 19B', 'Liverpool', 'L1 0AA', 'UK');

==> Make sure you separate each set of values with a comma ,.

## SQL NULL Values

# What is a NULL Value?

==> A field with a NULL value is a field with no value.

==> If a field in a table is optional, it is possible to insert a new record or update a record without adding a value to this field. Then, the field will be saved with a NULL value.

**Note** : A NULL value is different from a zero value or a field that contains spaces. A field with a NULL value is one that has been left blank during record creation!

# How to Test for NULL Values?

--> It is not possible to test for NULL values with comparison operators, such as =, <, or <>.

--> We will have to use the IS NULL and IS NOT NULL operators instead.

==>  IS NULL Syntax

SELECT column_names
FROM table_name
WHERE column_name IS NULL;

==> IS NOT NULL Syntax

SELECT column_names
FROM table_name
WHERE column_name IS NOT NULL;

# The IS NULL Operator

==> The IS NULL operator is used to test for empty values (NULL values).
==> The following SQL lists all customers with a NULL value in the "Address" field:

==> Example

SELECT CustomerName, ContactName, Address
FROM Customers
WHERE Address IS NULL;

# The IS NOT NULL Operator

-->  The IS NOT NULL operator is used to test for non-empty values (NOT NULL values).
-->  The following SQL lists all customers with a value in the "Address" field:

==> Example

SELECT CustomerName, ContactName, Address
FROM Customers
WHERE Address IS NOT NULL;

## SQL UPDATE Statement

# The SQL UPDATE Statement

--> The UPDATE statement is **used to modify the existing records** in a table.

update --> set

==> UPDATE Syntax
UPDATE table_name
SET column1 = value1, column2 = value2, ...
WHERE condition;

**Note** : Be careful when updating records in a table! Notice the WHERE clause in the UPDATE statement. The WHERE clause specifies which record(s) that should be updated. If you omit the WHERE clause, all records in the table will be updated!

# UPDATE Table

The following SQL statement updates the first customer (CustomerID = 1) with a new contact person and a new city.
Example
UPDATE Customers
SET ContactName = 'Alfred Schmidt', City= 'Frankfurt'
WHERE CustomerID = 1;

# UPDATE Multiple Records

--> It is the WHERE clause that determines how many records will be updated.
--> The following SQL statement will update the ContactName to "Juan" for all records where country is "Mexico"

==> Example
UPDATE Customers
SET ContactName='Juan'
WHERE Country='Mexico';

# Update Warning

==> Be careful when updating records. If you omit the WHERE clause, ALL records will be updated!

==> Example
UPDATE Customers
SET ContactName='Juan';

## SQL DELETE Statement

# The SQL DELETE Statement

The DELETE statement is used to delete existing records in a table.

==> DELETE Syntax
DELETE FROM table_name WHERE condition;

**Note**: Be careful when deleting records in a table! Notice the WHERE clause in the DELETE statement. The WHERE clause specifies which record(s) should be deleted. If you omit the WHERE clause, all records in the table will be deleted!

# SQL DELETE Example

--> The following SQL statement deletes the customer "Alfreds Futterkiste" from the "Customers" table:
DELETE FROM Customers WHERE CustomerName='Alfreds Futterkiste';

# Delete All Records

--> It is possible to delete all rows in a table without deleting the table. This means that the table structure, attributes, and indexes will be intact:

DELETE FROM table_name;

--> The following SQL statement deletes all rows in the "Customers" table, without deleting the table:

==> Example
DELETE FROM Customers;

# Delete a Table

To delete the table completely, use the DROP TABLE statement:

Example
Remove the Customers table:

DROP TABLE Customers;

## Deep Dive -- The "SELECT First" Safety Habit for UPDATE/DELETE

--> Given the explicit warnings above about omitting `WHERE` accidentally affecting every row, the single most effective practical habit is to write and run the EQUIVALENT `SELECT` statement FIRST, verify it returns exactly the rows you intend to change, and only then swap `SELECT *` for the actual `UPDATE`/`DELETE`.

```sql
-- Step 1: Verify exactly which rows this WHERE clause matches
SELECT * FROM Customers WHERE Country = 'Mexico' AND City = 'Guadalajara';

-- Step 2: Only after confirming the result looks correct, run the real statement with the SAME WHERE clause
UPDATE Customers SET ContactName = 'Juan' WHERE Country = 'Mexico' AND City = 'Guadalajara';
```

--> This costs almost nothing and catches the exact class of mistake (a subtly wrong `WHERE` condition, or a missing one entirely) that has caused genuinely well-known, costly real-world incidents where an engineer ran an `UPDATE`/`DELETE` against a production database without this verification step first.

## Deep Dive -- Wrapping Risky Statements in a Transaction

--> Directly connecting to the Transactions and ACID file -- wrapping an `UPDATE`/`DELETE` in an explicit transaction lets you inspect the result and `ROLLBACK` if something looks wrong, BEFORE the change becomes permanent.

```sql
BEGIN TRANSACTION;

DELETE FROM Customers WHERE Country = 'Mexico';

-- Check the result / row count here before deciding
SELECT COUNT(*) FROM Customers;   -- Does this number look right?

-- If it looks correct:
COMMIT;
-- If something looks wrong instead:
-- ROLLBACK;
```

--> This is a standard, low-cost safety net specifically for exploratory or one-off administrative changes -- for automated application code, the `WHERE`-clause discipline and proper testing matter more, since a human isn't watching each individual statement's result in real time to decide whether to commit or roll back.

## Deep Dive -- The RETURNING Clause (PostgreSQL/SQLite)

--> Normally, `INSERT`/`UPDATE`/`DELETE` don't return any of the affected DATA, only a count of affected rows -- `RETURNING` lets you get back the actual row values in the same statement, avoiding a separate follow-up `SELECT`.

```sql
INSERT INTO Customers (CustomerName, City) VALUES ('Cardinal', 'Stavanger')
RETURNING CustomerID, CustomerName;   -- Immediately get back the auto-generated CustomerID, no second query needed

UPDATE Customers SET City = 'Oslo' WHERE CustomerID = 5
RETURNING CustomerName, City;   -- Confirm exactly what changed, in the same round trip
```

--> Particularly valuable in application code needing the newly-generated primary key immediately after an insert (e.g. to use it right away in a related record) -- without `RETURNING`, this typically requires a separate `SELECT LAST_INSERT_ID()`-style follow-up query (the MySQL/SQL Server equivalent, which lacks a direct `RETURNING` clause in the same way).
