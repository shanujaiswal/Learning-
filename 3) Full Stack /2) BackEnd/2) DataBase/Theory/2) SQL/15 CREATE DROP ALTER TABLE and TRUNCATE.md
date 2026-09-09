## SQL CREATE TABLE Statement

# The SQL CREATE TABLE Statement

--> The CREATE TABLE statement is used to create a new table in a database.

==> Syntax
CREATE TABLE table_name (
    column1 datatype,
    column2 datatype,
    column3 datatype,
   ....
);

--> The column parameters specify the names of the columns of the table.

--> The datatype parameter specifies the type of data the column can hold (e.g. varchar, integer, date, etc.).

SQL CREATE TABLE Example
The following example creates a table called "Persons" that contains five columns: PersonID, LastName, FirstName, Address, and City:

ExampleGet your own SQL Server
CREATE TABLE Persons (
    PersonID int,
    LastName varchar(255),
    FirstName varchar(255),
    Address varchar(255),
    City varchar(255)
);

The PersonID column is of type int and will hold an integer.

The LastName, FirstName, Address, and City columns are of type varchar and will hold characters, and the maximum length for these fields is 255 characters.

The empty "Persons" table will now look like this:
![Person Table ](15-01_Person_Table_Empty.png)

**Note**: The empty "Persons" table can now be filled with data with the SQL INSERT INTO statement.

# Create Table Using Another Table

--> A copy of an existing table can also be created using CREATE TABLE.

--> The new table gets the same column definitions. All columns or specific columns can be selected.

--> If you create a new table using an existing table, the new table will be filled with the existing values from the old table.

==> Syntax
CREATE TABLE new_table_name AS
    SELECT column1, column2,...
    FROM existing_table_name
    WHERE ....;

--> The following SQL creates a new table called "TestTable" (which is a copy of the "Customers" table):

==> Example
CREATE TABLE TestTable AS
SELECT customername, contactname
FROM customers;

## SQL DROP TABLE Statement

# The SQL DROP TABLE Statement

--> The DROP TABLE statement is used to drop an existing table in a database.

==> Syntax
DROP TABLE table_name;

**Note**: Be careful before dropping a table. Deleting a table will result in loss of complete information stored in the table!

# SQL DROP TABLE Example

--> The following SQL statement drops the existing table "Shippers":

Example
DROP TABLE Shippers;

# SQL TRUNCATE TABLE

--> The TRUNCATE TABLE statement is used to delete the data inside a table, but not the table itself.

==> Syntax
TRUNCATE TABLE table_name;

## SQL ALTER TABLE Statement

# SQL ALTER TABLE Statement

--> The ALTER TABLE statement is used to add, delete, or modify columns in an existing table.

--> The ALTER TABLE statement is also used to add and drop various constraints on an existing table.

# ALTER TABLE - ADD Column

--> To add a column in a table, use the following syntax:

ALTER TABLE table_name
ADD column_name datatype;

--> The following SQL adds an "Email" column to the "Customers" table:

==> Example
ALTER TABLE Customers
ADD Email varchar(255);

# ALTER TABLE - DROP COLUMN

--> To delete a column in a table, use the following syntax (notice that some database systems don't allow deleting a column):

ALTER TABLE table_name
DROP COLUMN column_name;

--> The following SQL deletes the "Email" column from the "Customers" table:

==> Example
ALTER TABLE Customers
DROP COLUMN Email;

# ALTER TABLE - RENAME COLUMN

--> To rename a column in a table, use the following syntax:

ALTER TABLE table_name
RENAME COLUMN old_name to new_name;

--> To rename a column in a table in SQL Server, use the following syntax:

==> SQL Server:

EXEC sp_rename 'table_name.old_name',  'new_name', 'COLUMN';

# ALTER TABLE - ALTER/MODIFY DATATYPE

--> To change the data type of a column in a table, use the following syntax:

==> SQL Server / MS Access:

ALTER TABLE table_name
ALTER COLUMN column_name datatype;

==> My SQL / Oracle (prior version 10G):

ALTER TABLE table_name
MODIFY COLUMN column_name datatype;

==> Oracle 10G and later:

ALTER TABLE table_name
MODIFY column_name datatype;

# SQL ALTER TABLE Example

Look at the "Persons" table:

![Peraons Table](15-02_Persons_Table.png)

--> Now we want to add a column named "DateOfBirth" in the "Persons" table.

--> We use the following SQL statement:

ALTER TABLE Persons
ADD DateOfBirth date;

**Note**: that the new column, "DateOfBirth", is of type date and is going to hold a date. The data type specifies what type of data the column can hold

# Change Data Type Example

--> Now we want to change the data type of the column named "DateOfBirth" in the "Persons" table.
--> We use the following SQL statement:

ALTER TABLE Persons
ALTER COLUMN DateOfBirth year;

**Note** that the "DateOfBirth" column is now of type year and is going to hold a year in a two- or four-digit format.

# DROP COLUMN Example

--> Next, we want to delete the column named "DateOfBirth" in the "Persons" table.

--> We use the following SQL statement:

ALTER TABLE Persons
DROP COLUMN DateOfBirth;

## Deep Dive -- DELETE vs TRUNCATE vs DROP -- Three Genuinely Different Operations

--> These three are often lumped together as "ways to remove data," but they differ in scope, performance, and recoverability in ways that matter a great deal in practice.
--> **DELETE** (covered in the INSERT/UPDATE/DELETE file) -- removes rows one at a time, logging each individual row deletion -- can be filtered with `WHERE`, can be rolled back within a transaction, and FIRES any `DELETE` triggers (covered in the Triggers file) for each removed row. Slowest of the three for removing a large number of rows, precisely because of this per-row logging.
--> **TRUNCATE TABLE** -- removes ALL rows at once, typically by deallocating the data pages directly rather than logging each row -- dramatically faster than `DELETE` for clearing an entire table, but usually CANNOT be filtered with `WHERE` (it's all-or-nothing), and in most databases does NOT fire row-level `DELETE` triggers. Resets auto-increment counters back to their starting value in most databases (unlike `DELETE`, which leaves the counter wherever it was).
--> **DROP TABLE** -- removes the table STRUCTURE itself, not just its data -- columns, constraints, indexes, and all associated data are gone entirely; the table no longer exists at all and would need to be recreated with `CREATE TABLE` to use again.

```sql
DELETE FROM Orders WHERE OrderDate < '2020-01-01';   -- Removes only matching rows, can be rolled back, fires triggers
TRUNCATE TABLE OldLogs;                                -- Removes ALL rows instantly, table structure remains, resets auto-increment
DROP TABLE DeprecatedTable;                             -- Removes the table AND its structure entirely -- nothing left to query
```

--> **Practical guidance** -- use `DELETE` when you need to remove a SUBSET of rows or need trigger/transaction-rollback safety; use `TRUNCATE` when clearing an ENTIRE table quickly (e.g. resetting a staging/temp table between batch jobs) and don't need row-level triggers; use `DROP` only when the table itself should cease to exist.

## Deep Dive -- ALTER TABLE Locking on Large Production Tables

--> A seemingly simple `ALTER TABLE ... ADD COLUMN` on a table with millions of rows can, depending on the database engine and version, require rewriting the ENTIRE table on disk -- during which the table may be LOCKED, blocking reads and/or writes from every other query for the duration, potentially minutes or hours on a genuinely large table.
--> Modern versions of most major databases (PostgreSQL, MySQL 8+) have improved this significantly for simple operations (adding a nullable column with no default is often now a fast, near-instant metadata-only change) -- but adding a column WITH a default value, changing a column's data type, or adding certain constraints can still trigger a full table rewrite depending on the specific database and version.
--> **Practical guidance for production systems** -- schema changes on large tables are typically run during low-traffic maintenance windows, or using specialized "online schema change" tools (e.g. `gh-ost` or `pt-online-schema-change` for MySQL) that perform the rewrite in the background on a shadow copy of the table, swapping it in atomically at the end to minimize locking -- directly connecting to the Database Migration Tooling concepts referenced in the Database Fundamentals file, since this exact concern is precisely why migration tools and careful migration review matter so much more on a live production database than on a fresh development database with no real traffic to disrupt.

