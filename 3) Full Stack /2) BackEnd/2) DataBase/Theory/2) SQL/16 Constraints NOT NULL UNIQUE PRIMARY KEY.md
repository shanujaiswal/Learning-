## SQL Constraints

--> SQL constraints are used to specify rules for data in a table.

# SQL Create Constraints

--> Constraints can be specified when the table is created with the CREATE TABLE statement, or after the table is created with the ALTER TABLE statement.

==> Syntax
CREATE TABLE table_name (
    column1 datatype constraint,
    column2 datatype constraint,
    column3 datatype constraint,
    ....
);

# SQL Constraints

--> SQL constraints are used to specify rules for the data in a table.

--> Constraints are used to limit the type of data that can go into a table. This ensures the accuracy and reliability of the data in the table. If there is any violation between the constraint and the data action, the action is aborted.

--> Constraints can be column level or table level. Column level constraints apply to a column, and table level constraints apply to the whole table.

--> The following constraints are commonly used in SQL:

1) NOT NULL - Ensures that a column cannot have a NULL value
2) UNIQUE - Ensures that all values in a column are different
3) PRIMARY KEY - A combination of a NOT NULL and UNIQUE. Uniquely identifies each row in a table
4) FOREIGN KEY - Prevents actions that would destroy links between tables
5) CHECK - Ensures that the values in a column satisfies a specific condition
6) DEFAULT - Sets a default value for a column if no value is specified
7) CREATE INDEX - Used to create and retrieve data from the database very quickly

## SQL NOT NULL Constraint

# SQL NOT NULL Constraint

--> By default, a column can hold NULL values.

--> The NOT NULL constraint enforces a column to NOT accept NULL values.

--> This enforces a field to always contain a value, which means that you cannot insert a new record, or update a record without adding a value to this field.

# SQL NOT NULL on CREATE TABLE

--> The following SQL ensures that the "ID", "LastName", and "FirstName" columns will NOT accept NULL values when the "Persons" table is created:

==> Example
CREATE TABLE Persons (
    ID int NOT NULL,
    LastName varchar(255) NOT NULL,
    FirstName varchar(255) NOT NULL,
    Age int
);

# SQL NOT NULL on ALTER TABLE

--> To create a NOT NULL constraint on the "Age" column when the "Persons" table is already created, use the following SQL:

==> SQL Server / MS Access:

ALTER TABLE Persons
ALTER COLUMN Age int NOT NULL;

==> My SQL / Oracle (prior version 10G):

ALTER TABLE Persons
MODIFY COLUMN Age int NOT NULL;

==> Oracle 10G and later:

ALTER TABLE Persons
MODIFY Age int NOT NULL;

## SQL UNIQUE Constraint

# SQL UNIQUE Constraint

--> The UNIQUE constraint ensures that all values in a column are different.

--> Both the UNIQUE and PRIMARY KEY constraints provide a guarantee for uniqueness for a column or set of columns.

--> A PRIMARY KEY constraint automatically has a UNIQUE constraint.

However, you can have many UNIQUE constraints per table, but only one PRIMARY KEY constraint per table.

# SQL UNIQUE Constraint on CREATE TABLE

--> The following SQL creates a UNIQUE constraint on the "ID" column when the "Persons" table is created:

==> SQL Server / Oracle / MS Access:

CREATE TABLE Persons (
    ID int NOT NULL UNIQUE,
    LastName varchar(255) NOT NULL,
    FirstName varchar(255),
    Age int
);

==> MySQL:

CREATE TABLE Persons (
    ID int NOT NULL,
    LastName varchar(255) NOT NULL,
    FirstName varchar(255),
    Age int,
    UNIQUE (ID)
);

--> To name a UNIQUE constraint, and to define a UNIQUE constraint on multiple columns, use the following SQL syntax:

==> MySQL / SQL Server / Oracle / MS Access:

CREATE TABLE Persons (
    ID int NOT NULL,
    LastName varchar(255) NOT NULL,
    FirstName varchar(255),
    Age int,
    CONSTRAINT UC_Person UNIQUE (ID,LastName)
);

# SQL UNIQUE Constraint on ALTER TABLE

--> To create a UNIQUE constraint on the "ID" column when the table is already created, use the following SQL:

==> MySQL / SQL Server / Oracle / MS Access:

ALTER TABLE Persons
ADD UNIQUE (ID);

--> To name a UNIQUE constraint, and to define a UNIQUE constraint on multiple columns, use the following SQL syntax:

==> MySQL / SQL Server / Oracle / MS Access:

ALTER TABLE Persons
ADD CONSTRAINT UC_Person UNIQUE (ID,LastName);

# DROP a UNIQUE Constraint

--> To drop a UNIQUE constraint, use the following SQL:

==> MySQL:

ALTER TABLE Persons
DROP INDEX UC_Person;

==> SQL Server / Oracle / MS Access:

ALTER TABLE Persons
DROP CONSTRAINT UC_Person;

## SQL PRIMARY KEY Constraint

# SQL PRIMARY KEY Constraint

--> The PRIMARY KEY constraint is used to uniquely identify each record in a table.

--> Primary keys must contain unique values, and cannot contain NULL values.

--> Each table can have only ONE primary key. The primary key can be a single column or a combination of columns.

# SQL PRIMARY KEY on CREATE TABLE

--> The following SQL creates a PRIMARY KEY on the "ID" column when the "Persons" table is created:

==> MySQL:

CREATE TABLE Persons (
    ID int NOT NULL,
    LastName varchar(255) NOT NULL,
    FirstName varchar(255),
    Age int,
    PRIMARY KEY (ID)
);

==> SQL Server / Oracle / MS Access:

CREATE TABLE Persons (
    ID int NOT NULL PRIMARY KEY,
    LastName varchar(255) NOT NULL,
    FirstName varchar(255),
    Age int
);

--> To define a PRIMARY KEY constraint on multiple columns, use the following SQL syntax:

==> MySQL / SQL Server / Oracle / MS Access:

CREATE TABLE Persons (
    ID int NOT NULL,
    LastName varchar(255) NOT NULL,
    FirstName varchar(255),
    Age int,
    CONSTRAINT PK_Person PRIMARY KEY (ID,LastName)
);
**Note**: In the example above there is one PRIMARY KEY (PK_Person). However, the value of the primary key is made up of two columns (ID + LastName).

# SQL PRIMARY KEY on ALTER TABLE

--> To create a PRIMARY KEY constraint on the "ID" column when the table is already created, use the following SQL:

==> MySQL / SQL Server / Oracle / MS Access:

ALTER TABLE Persons
ADD PRIMARY KEY (ID);

--> To define a PRIMARY KEY constraint on multiple columns, use the following SQL syntax:

==> MySQL / SQL Server / Oracle / MS Access:

ALTER TABLE Persons
ADD CONSTRAINT PK_Person PRIMARY KEY (ID,LastName);
**Note** : If you use ALTER TABLE to add a primary key, the primary key column(s) must have been declared with NOT NULL, when the table was first created.

# DROP a PRIMARY KEY Constraint

To drop a PRIMARY KEY constraint, use the following SQL:

==> MySQL:

ALTER TABLE Persons
DROP PRIMARY KEY;
==> SQL Server / Oracle / MS Access:

ALTER TABLE Persons
DROP CONSTRAINT PK_Person;

## Deep Dive -- Why Naming Constraints Explicitly Matters

--> Notice the `DROP CONSTRAINT` examples above require knowing the constraint's NAME (`PK_Person`, `UC_Person`) -- if a constraint is created WITHOUT an explicit name (as in the simpler `PRIMARY KEY (ID)` examples), the database auto-generates an often cryptic, system-specific name (like `PK__Persons__3214EC077F60ED59` in SQL Server) that's genuinely painful to reference later when you need to modify or drop it.
--> **Practical guidance** -- always explicitly name constraints in production schema definitions (`CONSTRAINT PK_Person PRIMARY KEY (ID)`), even when it feels like unnecessary verbosity for a simple case -- this single habit saves real, recurring pain during future schema migrations (directly connecting to the Database Migration Tooling concepts referenced in the Full Stack DevOps notes), where a migration script needs to reliably reference a specific constraint by name.

## Deep Dive -- Composite Primary Keys and Auto-Increment Interaction

--> A table can't have TWO separate auto-incrementing columns, and a composite primary key (multiple columns together forming the key, shown above with `PK_Person (ID, LastName)`) generally can't have auto-increment on more than one of its component columns either, since auto-increment logic inherently needs a single, well-defined "next value" sequence.
--> A very common composite-key pattern instead is a JUNCTION/BRIDGE table for many-to-many relationships (directly connecting to the Normalization file's Entity-Relationship coverage) -- e.g. an `Enrollments` table with a composite primary key of `(StudentID, CourseID)`, where NEITHER column is auto-incrementing; the combination of two foreign keys IS the natural, meaningful identity of each enrollment record, with no need for a separate auto-incrementing surrogate ID column at all.

```sql
CREATE TABLE Enrollments (
    StudentID INT NOT NULL,
    CourseID INT NOT NULL,
    EnrollDate DATE,
    PRIMARY KEY (StudentID, CourseID),   -- Composite key -- the SAME student can't enroll in the SAME course twice
    FOREIGN KEY (StudentID) REFERENCES Students(StudentID),
    FOREIGN KEY (CourseID) REFERENCES Courses(CourseID)
);
```

## Deep Dive -- Constraint Violations at Runtime

--> When application code attempts an operation violating a constraint (inserting a duplicate value into a `UNIQUE` column, inserting `NULL` into a `NOT NULL` column), the database REJECTS the entire statement and raises an error -- the application must catch and handle this gracefully rather than assuming every insert/update will succeed.

```python
# Python example using a try/except around a constraint violation, directly connecting to the
# Error Handling file's exception-handling patterns in the Full Stack Backend Python notes
try:
    cursor.execute("INSERT INTO Persons (ID, Email) VALUES (%s, %s)", (1, "existing@example.com"))
except IntegrityError as e:
    print("This email is already registered:", e)
```

--> This is precisely why constraints are considered a critical LAST LINE of defense for data integrity -- even if application-level validation has a bug or is bypassed entirely (a direct database script, a different application sharing the same database), the database itself refuses to store data that violates its declared rules.
