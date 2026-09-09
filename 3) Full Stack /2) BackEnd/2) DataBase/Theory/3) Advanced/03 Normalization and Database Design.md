## Why Normalization

--> Normalization is the process of organizing tables to reduce data redundancy and avoid update anomalies -- the same fact should ideally live in exactly one place.
--> Without it, the same value (e.g. a customer's address) gets duplicated across many rows -- update it in one place and forget another, and the data becomes inconsistent.

# First Normal Form (1NF)

--> Every column holds a single (atomic) value -- no comma-separated lists or repeating groups inside one cell.
--> Every row must be uniquely identifiable (a primary key).

```text
-- Violates 1NF: multiple phone numbers crammed into one column
CustomerID | Name  | Phones
1          | Alice | "555-1111, 555-2222"

-- Fixed: separate row per phone number (or a separate related table)
CustomerID | Name  | Phone
1          | Alice | 555-1111
1          | Alice | 555-2222
```

# Second Normal Form (2NF)

--> Must already be in 1NF, PLUS every non-key column must depend on the WHOLE primary key, not just part of it -- relevant only for tables with a composite (multi-column) primary key.

```text
-- Violates 2NF: composite key (OrderID, ProductID), but ProductName depends only on ProductID, not the full key
OrderID | ProductID | ProductName | Quantity

-- Fixed: split ProductName into its own Products table, keyed by ProductID alone
Orders: OrderID | ProductID | Quantity
Products: ProductID | ProductName
```

# Third Normal Form (3NF)

--> Must already be in 2NF, PLUS no non-key column should depend on another non-key column (no "transitive dependency").

```text
-- Violates 3NF: ZipCode determines City, but City isn't determined by the primary key (CustomerID) directly
CustomerID | Name | ZipCode | City

-- Fixed: move ZipCode -> City mapping into its own table
Customers: CustomerID | Name | ZipCode
ZipCodes: ZipCode | City
```

--> Most real-world application databases are designed to 3NF -- it's usually the practical sweet spot between eliminating redundancy and keeping queries simple.

# Denormalization -- The Deliberate Trade-off

--> Denormalization means intentionally reintroducing some redundancy (duplicated or precomputed data) to improve read performance, at the cost of extra work to keep it consistent on writes.
--> Common example -- storing a denormalized order_total column on an Orders table instead of recalculating SUM(price*quantity) from OrderItems on every read.
--> Reasonable when reads vastly outnumber writes and the join/calculation cost is a proven bottleneck -- not a default starting point, normalize first and denormalize only where profiling shows it's needed.

# Entity-Relationship (ER) Modeling

--> Before creating tables, database design is typically sketched as an ER diagram -- entities (things, e.g. Customer, Order) become tables, attributes become columns, relationships become foreign keys.
--> Relationship types:
--> One-to-Many -- one Customer has many Orders -- foreign key (CustomerID) goes on the "many" side (Orders table).
--> Many-to-Many -- many Students enroll in many Courses -- requires a junction/bridge table (e.g. Enrollments) holding a foreign key to each side.
--> One-to-One -- one User has one Profile -- foreign key on either side, usually with a unique constraint.

```text
Students <-- Enrollments --> Courses
(Enrollments: StudentID, CourseID, EnrollDate -- StudentID+CourseID together often form the primary key)
```

# Deep Dive -- Boyce-Codd Normal Form (BCNF)

--> BCNF is a stricter version of 3NF, closing a subtle gap 3NF can miss -- it requires that for every functional dependency (`A determines B`), `A` must be a "candidate key" (a column or set of columns that could serve as the primary key) -- 3NF only requires this when `B` is a non-key column, leaving a narrow edge case involving overlapping composite candidate keys unaddressed.
--> In practice, most real-world schemas that satisfy 3NF also satisfy BCNF -- the distinction matters mainly in specific, somewhat rare scenarios with multiple overlapping composite keys, which is exactly why 3NF (not BCNF) is cited as "the practical sweet spot" above -- BCNF is worth knowing exists, but rarely the deciding design factor in everyday schema design.

# Deep Dive -- A Concrete Normalization Walkthrough

--> Applying all three normal forms together to a single realistic example makes the abstract rules concrete.

```text
Starting (unnormalized) table:
OrderID | CustomerName | CustomerEmail | Product1, Product2, Product3 | ProductPrices

-- Step 1: Achieve 1NF -- eliminate repeating groups (Product1/2/3), one row per order-item
Orders: OrderID, CustomerName, CustomerEmail
OrderItems: OrderID, ProductName, Price

-- Step 2: Achieve 2NF -- OrderItems has no composite key issue here since OrderID+ProductName
-- together already form a fine natural key, and Price genuinely depends on that whole pair --
-- no change needed at this step for this particular example

-- Step 3: Achieve 3NF -- CustomerEmail depends on CustomerName, not directly on OrderID
-- (a transitive dependency) -- split Customers into their own table
Orders: OrderID, CustomerID
Customers: CustomerID, CustomerName, CustomerEmail
OrderItems: OrderID, ProductName, Price
```

--> Each step directly removes a SPECIFIC kind of redundancy/anomaly -- 1NF removes repeating groups, 2NF removes partial-key dependencies, 3NF removes transitive dependencies -- and the final three-table design is exactly the kind of schema the JOIN techniques covered in the SQL Joins file are designed to recombine into a single, complete view when actually querying the data.

# Deep Dive -- Normalization's Real Trade-off in Practice

--> A fully normalized schema minimizes redundancy and update anomalies, but often requires MORE joins to answer a typical query, since related data is spread across more tables -- directly connecting to the Join Performance discussion in the SQL Joins file, since every additional join is another opportunity for a missing index to cause a real performance problem.
--> This is exactly why the Denormalization section above frames it as a DELIBERATE, informed trade-off rather than a mistake -- a high-read, low-write analytics table (or the Materialized Views concept covered in the SQL Views file) intentionally accepts some redundancy specifically to avoid paying the join cost on every single read, once profiling (connecting to the Query Execution Plans file) has actually confirmed that cost is a genuine bottleneck worth trading correctness-by-design for.
