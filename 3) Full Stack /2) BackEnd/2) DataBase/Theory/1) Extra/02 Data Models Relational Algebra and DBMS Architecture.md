## Structured, Semi-Structured, and Unstructured Data

--> **Structured data** -- fits neatly into rows and columns with a predefined schema known in advance (a fixed set of columns, each with a declared data type). Every record has the same shape. Example: a `Customers` table in MySQL/PostgreSQL, an Excel sheet with consistent columns.

--> **Semi-structured data** -- has SOME organizational structure (tags, keys, nesting) but no fixed schema -- different records can have different fields, and the structure is embedded in the data itself rather than declared separately. Example: JSON documents, XML files, a MongoDB document, an email (structured headers like To/From, but a free-text body).

--> **Unstructured data** -- has no predefined structure or schema at all -- just raw content. Example: images, video, audio, PDFs, free-text documents, chat logs, social media posts.

```text
Structured:      | CustomerID | Name  | City   |
                  | 1          | Alice | Berlin |

Semi-structured:  { "name": "Alice", "city": "Berlin", "tags": ["vip", "returning"] }
                  -- fields can vary between documents; nesting is allowed

Unstructured:     a photo, a video file, a raw .txt transcript of a phone call
```

--> **Why the distinction matters** -- it drives which storage technology fits: structured data fits a relational database naturally; semi-structured data fits a document store (MongoDB) or a JSON column inside a relational database; unstructured data is usually stored as files/blobs (e.g. S3, covered in the AWS notes) with only METADATA about it (a filename, a URL, a timestamp) kept inside a database, since a database engine isn't built to search "inside" an image or video the way it searches inside rows.
--> Real systems usually hold all three at once -- an e-commerce app might keep orders in PostgreSQL (structured), a product's flexible custom-attributes in a JSON column or MongoDB (semi-structured), and product photos in blob storage with just a URL reference in the structured table (unstructured, referenced not stored inline).

## Relational Algebra -- The Formal Theory Behind SQL

--> Relational algebra is the formal mathematical foundation SQL is built on -- every SQL query is, underneath, a composition of a small set of relational algebra operators applied to tables (treated as mathematical "relations," i.e. sets of tuples/rows).

--> **Selection (σ)** -- picks rows matching a condition -- this is exactly what SQL's `WHERE` does.
```text
σ(Price > 20)(Products)   -- relational algebra
SELECT * FROM Products WHERE Price > 20;   -- the SQL equivalent
```

--> **Projection (π)** -- picks specific columns, discarding the rest -- this is exactly what naming columns in `SELECT` does.
```text
π(ProductName, Price)(Products)
SELECT ProductName, Price FROM Products;
```

--> **Union (∪), Intersection (∩), Difference (−)** -- the set operations -- directly correspond to SQL's `UNION`, `INTERSECT`, and `EXCEPT`.

--> **Join (⋈)** -- combines rows from two relations based on a common condition -- the formal ancestor of SQL's `JOIN`.

--> **Division (÷)** -- a less commonly taught operator with no single direct SQL keyword -- answers "which X are related to ALL Y" (e.g. "which students have taken EVERY course in the Computer Science department"), typically expressed in SQL using `NOT EXISTS` combined with a correlated subquery rather than a single operator.

--> **Why this matters practically** -- recognizing SQL clauses as compositions of these underlying operators explains WHY the SQL query planner can freely reorder/rewrite a query (pushing a `WHERE` filter earlier, choosing a different join order) while still guaranteeing the same result -- the relational algebra expression is what's actually semantically equivalent, not the specific SQL syntax used to write it. This is the theoretical foundation the Query Execution Plans file's optimizer discussion sits on top of.

## Key Taxonomy -- Beyond Just "Primary Key" and "Foreign Key"

--> **Super key** -- ANY set of columns that uniquely identifies a row, including redundant ones (e.g. `(CustomerID, Email)` together is a super key, even though `CustomerID` alone would already be enough).
--> **Candidate key** -- a MINIMAL super key -- no column can be removed from it without losing uniqueness. A table can have multiple candidate keys (e.g. both `CustomerID` and `Email` might independently and uniquely identify a customer).
--> **Primary key** -- the ONE candidate key actually chosen to be the table's official unique identifier (covered in the Constraints file). Every other candidate key that wasn't chosen becomes an...
--> **Alternate key** -- a candidate key that exists but was NOT selected as the primary key (e.g. if `CustomerID` is the primary key, `Email` -- still unique -- is an alternate key, typically enforced with a `UNIQUE` constraint instead).
--> **Natural key** -- a key made of data that has real-world, business meaning (an email address, a national ID number, a product's SKU).
--> **Surrogate key** -- an artificial, meaningless identifier generated purely for the database's own use (an `AUTO_INCREMENT` integer, a UUID) -- carries no business meaning at all.

```sql
-- CustomerID is a surrogate key (meaningless, auto-generated)
-- Email is a natural key (has real-world meaning) AND a candidate/alternate key (unique, but not chosen as primary)
CREATE TABLE Customers (
    CustomerID INT AUTO_INCREMENT PRIMARY KEY,   -- surrogate key, chosen as the primary key
    Email VARCHAR(255) UNIQUE NOT NULL           -- natural key, alternate key
);
```

--> **Practical guidance** -- surrogate keys are generally preferred as the PRIMARY key in production schemas, even when a natural key exists, because natural keys can occasionally need to CHANGE (an email address gets updated, a SKU gets reassigned) -- and a primary key that other tables reference via foreign keys should ideally never need to change. The natural key still gets a `UNIQUE` constraint to enforce its own real-world uniqueness rule, just not as the primary key other tables link to.

## Functional Dependencies

--> A functional dependency, written `A -> B` ("A determines B"), means that knowing the value of column(s) `A` always tells you EXACTLY ONE corresponding value of column `B` -- for every row sharing the same `A`, `B` must also be the same. This is the formal concept the Normalization file's normal forms (1NF/2NF/3NF/BCNF) are actually built on, even though it's only named explicitly in that file's BCNF aside.

```text
CustomerID | Name  | ZipCode | City
1          | Alice | 10001   | New York
2          | Bob   | 10001   | New York

-- ZipCode -> City is a functional dependency: every row with ZipCode=10001 has City=New York, no exceptions.
-- CustomerID -> Name, ZipCode, City is also a functional dependency (CustomerID is the primary key, so it
-- determines every other column BY DEFINITION -- this is exactly why a primary key is a "trivial" dependency).
```

--> **Full vs. partial dependency** -- with a COMPOSITE key `(A, B)`, a column `C` has a FULL dependency on the key if it depends on BOTH `A` and `B` together, but only a PARTIAL dependency if it actually depends on just `A` alone (or just `B` alone) -- exactly the situation 2NF (covered in the Normalization file) is designed to eliminate.
--> **Transitive dependency** -- when `A -> B` and `B -> C`, which together imply `A -> C`, but `C` doesn't depend on `A` DIRECTLY -- it depends on `A` only THROUGH `B`. `ZipCode -> City` above is transitive through `CustomerID -> ZipCode -> City` -- exactly the pattern 3NF is designed to eliminate.
--> **Why naming this explicitly matters** -- every one of the Normalization file's normal forms is really just a rule about which KINDS of functional dependency are and aren't allowed to exist in a properly designed table -- 1NF is about atomic values, but 2NF outlaws partial dependencies and 3NF outlaws transitive ones, both defined entirely in terms of functional dependencies. Recognizing a functional dependency by inspection (does this column's value ever differ for two rows sharing the same key?) is the actual practical skill normalization design comes down to, underneath the numbered-form terminology.

## Types of Data Integrity

--> "Data integrity" and "referential integrity" appear throughout the Constraints, Foreign Key, and Triggers files as general phrases -- formally, integrity rules fall into three named categories, each enforced by a different mechanism already covered elsewhere in these notes.

--> **Domain integrity** -- every value in a column must fall within that column's allowed set of values (its data TYPE, plus any `CHECK` constraint) -- e.g. an `Age` column can't hold the text `"twelve"`, and a `CHECK (Age >= 18)` constraint further restricts it beyond just "must be an integer." Enforced by column data types and `CHECK`/`NOT NULL` constraints (covered in the Constraints file).
--> **Entity integrity** -- every row must be UNIQUELY and UNAMBIGUOUSLY identifiable, and that identifying key can never be NULL -- enforced by the `PRIMARY KEY` constraint specifically (a `UNIQUE` constraint alone doesn't fully guarantee this, since unlike a primary key it can still allow NULLs in most databases).
--> **Referential integrity** -- a value in a FOREIGN KEY column must either be NULL or match an EXISTING value in the referenced table's primary key -- no "dangling" reference to a row that doesn't exist. Enforced by the `FOREIGN KEY` constraint, including its `ON DELETE`/`ON UPDATE` behavior (covered in the FOREIGN KEY Constraints file).

```sql
CREATE TABLE Orders (
    OrderID INT PRIMARY KEY,                    -- entity integrity: OrderID uniquely, non-null identifies each row
    Quantity INT CHECK (Quantity > 0),           -- domain integrity: Quantity must be a positive number
    CustomerID INT,
    FOREIGN KEY (CustomerID) REFERENCES Customers(CustomerID)   -- referential integrity: CustomerID must exist, or be NULL
);
```

--> **Why naming these three separately is useful** -- when a database rejects an operation, recognizing WHICH category of integrity was violated points directly at the fix -- a domain integrity error usually means the application sent a badly-validated value; a referential integrity error usually means a parent row was deleted (or never created) before a child row tried to reference it; an entity integrity error usually means a duplicate-key insert was attempted. All three are the same "last line of defense" the Constraints file describes -- this is simply the formal vocabulary for classifying which specific defense caught the problem.

## The Three-Schema Architecture

--> A standard way of describing how a database system separates WHAT users see from HOW data is actually stored, in three distinct layers:

--> **External (view) schema** -- what an individual user or application actually sees -- often a subset or a reshaped version of the full data (directly connects to the SQL Views file -- a `CREATE VIEW` is a concrete example of an external schema layer).
--> **Conceptual (logical) schema** -- the overall structure of the ENTIRE database as designed -- all the tables, columns, relationships, and constraints, independent of how any specific application uses them or how they're physically stored on disk.
--> **Internal (physical) schema** -- how the data is ACTUALLY stored on disk -- file organization, indexes, storage engine details (this is the layer the Indexing and Performance Tuning file's B-Tree discussion lives at).

```text
External:    "Brazil Customers" view -- CustomerName, ContactName only
                        |
Conceptual:  full Customers table -- every column, every constraint, every relationship
                        |
Internal:    B-Tree index files, page layout on disk, storage engine internals
```

--> **Why this separation matters -- Data Independence** -- because these layers are separate, a change at one layer doesn't necessarily force a change at the others. Adding a new INDEX (internal layer) doesn't change any application's queries. Adding a new COLUMN to a table (conceptual layer) doesn't break an existing VIEW that doesn't reference it (external layer). This insulation between layers is precisely why a DBA can reorganize physical storage, or a schema can evolve, without every single application needing to be rewritten in lockstep.

## ER Diagram Notation -- Beyond One-to-Many

--> The Normalization file introduces One-to-Many, Many-to-Many, and One-to-One relationships -- a fuller ER modeling vocabulary adds a few more formal concepts used when a design gets more complex:

--> **Weak entity** -- an entity that CANNOT be uniquely identified by its own attributes alone -- it depends on a "strong" entity's key as part of its own identity. Example -- a `Room` might only be unique WITHIN a specific `Building` (`Room 101` exists in many buildings) -- `Room`'s effective identity is `(BuildingID, RoomNumber)` together, not `RoomNumber` alone.
--> **Generalization / Specialization (ISA hierarchies)** -- modeling a general entity with several specialized sub-types that share common attributes but also each have their own unique ones. Example -- a `Vehicle` entity generalizing `Car` and `Truck`, where `Car` has a `NumberOfDoors` attribute and `Truck` has a `CargoCapacity` attribute, but both share `VIN` and `Manufacturer`.
```sql
-- One common way to implement an ISA hierarchy relationally -- a shared parent table plus per-subtype tables
CREATE TABLE Vehicles (VIN VARCHAR(17) PRIMARY KEY, Manufacturer VARCHAR(100));
CREATE TABLE Cars (VIN VARCHAR(17) PRIMARY KEY REFERENCES Vehicles(VIN), NumberOfDoors INT);
CREATE TABLE Trucks (VIN VARCHAR(17) PRIMARY KEY REFERENCES Vehicles(VIN), CargoCapacity DECIMAL);
```
--> **Aggregation** -- treating an entire relationship itself as a higher-level entity that can participate in further relationships. Example -- the relationship "Employee WORKS_ON Project" might itself need to be related to "Manager EVALUATES" -- aggregation lets the WORKS_ON relationship be treated as its own entity for that purpose.
--> **Cardinality and participation constraints** -- beyond just "one" or "many," formal ER notation also specifies whether participation is MANDATORY (every entity instance must participate in the relationship, drawn with a double line) or OPTIONAL (drawn with a single line) -- e.g. every `Order` must have a `Customer` (mandatory), but a `Customer` doesn't need to have placed any `Order` yet (optional).

## Legacy and Alternative Data Models

--> Beyond the relational model (covered throughout this SQL series) and the NoSQL models covered in their own file (document, key-value, column-family, graph), a few older/historical data models are worth recognizing by name:

--> **Hierarchical model** -- data organized as a strict tree, where each child record has exactly ONE parent (like a filesystem directory tree). Predates the relational model (IBM's IMS, still used in some legacy mainframe systems) -- naturally fits strictly hierarchical data, but struggles to represent many-to-many relationships cleanly.
--> **Network model** -- a generalization of the hierarchical model where a record can have MULTIPLE parents, represented as a graph rather than a strict tree (CODASYL databases) -- more flexible than hierarchical, but more complex to query, which is a large part of why the relational model displaced both.
--> **Object-oriented model** -- data stored as objects (with attributes AND behavior/methods bundled together), closely mirroring object-oriented programming language structures -- used by some specialized databases, though most applications today instead bridge the gap between OO application code and relational storage using an ORM (covered in its own file) rather than an object database directly.

--> **Why the relational model won out for most general-purpose use** -- it offers a strong mathematical foundation (relational algebra, above), a declarative query language (SQL) that doesn't require the programmer to specify HOW to traverse the data structure, and mature tooling -- hierarchical/network models require the application to know the exact physical navigation path to the data it wants, which the relational model's declarative approach specifically frees the developer from.

## Database Architecture Patterns

--> **1-tier** -- the database and the application live on the SAME machine (e.g. a desktop app with an embedded SQLite file) -- simplest, but doesn't scale beyond a single user/machine.
--> **2-tier (client-server)** -- a client application connects directly to a separate database server over a network -- the classic early web/desktop-app pattern, still common for small internal tools.
--> **3-tier** -- a client talks to an application/business-logic SERVER, which in turn talks to the database -- the standard modern web application shape (browser → backend API server → database), directly connecting to the layered architecture covered throughout the Full Stack Backend notes. Separating the business-logic tier from the database tier means the application server can enforce validation/authorization/caching BEFORE ever reaching the database, and multiple application server instances can share one database.
--> **Distributed database architecture** -- the database itself is spread across multiple physical servers (directly connecting to the Replication and Sharding file) -- a further evolution beyond a single centralized database server, needed once a single server's capacity is no longer enough regardless of how many application-tier servers sit in front of it.

```text
1-tier:  [ App + DB on one machine ]

2-tier:  [ Client ] <---network---> [ Database Server ]

3-tier:  [ Client ] <---> [ App Server ] <---> [ Database Server ]
                            (business logic,
                             validation, auth)
```
