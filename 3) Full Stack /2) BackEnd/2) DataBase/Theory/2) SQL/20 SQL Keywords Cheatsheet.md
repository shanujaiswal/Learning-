## SQL Keywords

# Keyword Description

1. ADD --> Adds a column in an existing table
2. ADD CONSTRAINT  -->   Adds a constraint after a table is already created
3. ALL -->  Returns  true if all of the subquery values meet the condition
4. ALTER --> Adds, deletes, or modifies columns in a table, or changes the data type of a column in a table
5. ALTER COLUMN --> Changes the data type of a column in a table
6. ALTER TABLE --> Adds, deletes, or modifies columns in a table
7. AND --> Only includes rows where both conditions is true
8. ANY --> Returns true if any of the subquery values meet the condition
9. AS --> Renames a column or table with an alias
10. ASC --> Sorts the result set in ascending order
11. BACKUP DATABASE --> Creates a back up of an existing database
12. BETWEEN --> Selects values within a given range
13. CASE --> Creates different outputs based on conditions
14. CHECK --> A constraint that limits the value that can be placed in a column
15. COLUMN --> Changes the data type of a column or deletes a column in a table
16. CONSTRAINT -->  Adds or deletes a constraint
17. CREATE --> Creates a database, index, view, table, or procedure
18. CREATE DATABASE --> Creates a new SQL database
19. CREATE INDEX --> Creates an index on a table (allows duplicate values)
20. CREATE OR REPLACE VIEW --> Updates a view
21. CREATE TABLE --> Creates a new table in the database
22. CREATE PROCEDURE --> Creates a stored procedure
23. CREATE UNIQUE INDEX --> Creates a unique index on a table (no duplicate values)
24. CREATE VIEW --> Creates a view based on the result set of a SELECT statement
25. DATABASE --> Creates or deletes an SQL database
26. DEFAULT --> A constraint that provides a default value for a column
27. DELETE --> Deletes rows from a table
28. DESC --> Sorts the result set in descending order
29. DISTINCT --> Selects only distinct (different) values
30. DROP --> Deletes a column, constraint, database, index, table, or view
31. DROP COLUMN --> Deletes a column in a table
32. DROP CONSTRAINT --> Deletes a UNIQUE, PRIMARY KEY, FOREIGN KEY, or CHECK constraint
33. DROP DATABASE --> Deletes an existing SQL database
34. DROP DEFAULT --> Deletes a DEFAULT constraint
35. DROP INDEX --> Deletes an index in a table
36. DROP TABLE --> Deletes an existing table in the database
37. DROP VIEW --> Deletes a view
38. EXEC --> Executes a stored procedure
39. EXISTS --> Tests for the existence of any record in a subquery
40. FOREIGN KEY --> A constraint that is a key used to link two tables together
41. FROM --> Specifies which table to select or delete data from
42. FULL OUTER JOIN --> Returns all rows when there is a match in either left table or right table
43. GROUP BY --> Groups the result set (used with aggregate functions: COUNT, MAX, MIN, SUM, AVG)
44. HAVING --> Used instead of WHERE with aggregate functions
45. IN --> Allows you to specify multiple values in a WHERE clause
46. INDEX --> Creates or deletes an index in a table
47. INNER JOIN --> Returns rows that have matching values in both tables
48. INSERT INTO --> Inserts new rows in a table
49. INSERT INTO SELECT --> Copies data from one table into another table
50. IS NULL --> Tests for empty values
51. IS NOT NULL --> Tests for non-empty values
52. JOIN --> Joins tables
53. LEFT JOIN --> Returns all rows from the left table, and the matching rows from the right table
54. LIKE --> Searches for a specified pattern in a column
55. LIMIT --> Specifies the number of records to return in the result set
56. NOT --> Only includes rows where a condition is not true
57. NOT NULL --> A constraint that enforces a column to not accept NULL values
58. OR --> Includes rows where either condition is true
59. ORDER BY --> Sorts the result set in ascending or descending order
60. OUTER JOIN --> Returns all rows when there is a match in either left table or right table
61. PRIMARY KEY --> A constraint that uniquely identifies each record in a database table
62. PROCEDURE --> A stored procedure
63. RIGHT JOIN --> Returns all rows from the right table, and the matching rows from the left table
64. ROWNUM --> Specifies the number of records to return in the result set
65. SELECT --> Selects data from a database
66. SELECT DISTINCT --> Selects only distinct (different) values
67. SELECT INTO --> Copies data from one table into a new table
68. SELECT TOP --> Specifies the number of records to return in the result set
69. SET --> Specifies which columns and values that should be updated in a table
70. TABLE --> Creates a table, or adds, deletes, or modifies columns in a table, or deletes a table or data inside a table
71. TOP --> Specifies the number of records to return in the result set
72. TRUNCATE TABLE --> Deletes the data inside a table, but not the table itself
73. UNION --> Combines the result set of two or more SELECT statements (only distinct values)
74. UNION ALL --> Combines the result set of two or more SELECT statements (allows duplicate values)
75. UNIQUE --> A constraint that ensures that all values in a column are unique
76. UPDATE --> Updates existing rows in a table
77. VALUES --> Specifies the values of an INSERT INTO statement
78. VIEW --> Creates, updates, or deletes a view
79. WHERE --> Filters a result set to include only records that fulfill a specified condition

## SQL ADD Keyword

# ADD

The ADD command is used to add a column in an existing table.

## Deep Dive -- Grouping These Keywords by Category (A Capstone View)

--> With the full keyword list above, it's worth explicitly grouping them into the four SQL sub-language categories referenced in the Database Fundamentals file -- seeing WHICH category each keyword belongs to reveals the underlying structure this entire SQL file series has been teaching piece by piece.

```
DDL (Data Definition Language) -- defines/modifies STRUCTURE:
  CREATE, ALTER, DROP, TRUNCATE, CONSTRAINT, INDEX

DML (Data Manipulation Language) -- reads/writes DATA:
  SELECT, INSERT INTO, UPDATE, DELETE, INSERT INTO SELECT, SELECT INTO

DCL (Data Control Language) -- manages PERMISSIONS (covered in the Access Control GRANT/REVOKE file):
  GRANT, REVOKE

TCL (Transaction Control Language) -- manages TRANSACTIONS (covered in the Transactions and ACID file):
  COMMIT, ROLLBACK, BEGIN TRANSACTION

Clauses -- modify/filter/shape a DML statement's behavior, not standalone statements on their own:
  WHERE, GROUP BY, HAVING, ORDER BY, JOIN, LIMIT/TOP, UNION
```

--> Recognizing which category an unfamiliar keyword belongs to is a genuinely useful mental shortcut when encountering new SQL syntax -- a keyword you've never seen that clearly modifies table STRUCTURE (something ending in a schema change) behaves very differently (usually auto-committing immediately, not participating in a `ROLLBACK` the way DML does in many databases) than one that manipulates data.

## Deep Dive -- Why DDL Statements Often Can't Be Rolled Back

--> A genuinely important, database-specific caveat -- in MySQL, DDL statements (`CREATE`, `ALTER`, `DROP`, `TRUNCATE`) cause an IMPLICIT COMMIT, meaning they cannot be rolled back as part of a transaction, even if wrapped in `BEGIN TRANSACTION` -- once a `DROP TABLE` executes, it's permanent immediately, regardless of any surrounding transaction block. PostgreSQL, by contrast, DOES support transactional DDL -- a `DROP TABLE` inside a transaction CAN be rolled back there.
--> This is exactly the kind of "the SQL standard says X, but check your specific database" caveat raised throughout this SQL file series (the SELECT INTO support gap, the historical MySQL CHECK constraint gap) -- transactional DDL support is a genuine, consequential difference between major databases that affects how safely you can experiment with schema changes inside a transaction before committing to them.
