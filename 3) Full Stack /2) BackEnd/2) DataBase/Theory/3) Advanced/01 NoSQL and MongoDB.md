## NoSQL Overview

--> NoSQL ("Not Only SQL") databases store data in formats other than relational tables -- built for flexible schemas, horizontal scaling, and specific access patterns SQL databases handle less naturally.
--> Four main categories:
1. Document -- stores JSON-like documents (MongoDB, CouchDB)
2. Key-Value -- simple key -> value lookups (Redis, DynamoDB)
3. Column-family -- optimized for reading/writing columns across huge datasets (Cassandra, HBase)
4. Graph -- stores nodes/edges for highly connected data (Neo4j)

==> SQL vs NoSQL
--> SQL -- fixed schema, tables with rows/columns, relationships via foreign keys, strong consistency (ACID), best for structured data with complex relationships.
--> NoSQL -- flexible/dynamic schema, denormalized data (related info often duplicated/nested instead of joined), horizontally scalable, best for large-scale, rapidly-changing, or loosely-structured data.
--> Not a strict "better/worse" -- many real systems use both (polyglot persistence), e.g. PostgreSQL for orders + Redis for caching + MongoDB for logs.

---

## MongoDB -- Document Database

--> MongoDB stores data as BSON (binary JSON) documents grouped into collections -- roughly equivalent to a "row" and a "table" in SQL terms, but a document can have nested objects/arrays instead of needing separate joined tables.

```javascript
// A MongoDB document -- no fixed schema, fields can vary between documents in the same collection
{
  _id: ObjectId("64f1a2..."),
  name: "Alice",
  email: "alice@example.com",
  orders: [                      // embedded array, no separate "orders" table needed
    { product: "Book", price: 15 },
    { product: "Pen", price: 2 }
  ]
}
```

==> Basic CRUD Operations
```javascript
db.users.insertOne({ name: "Alice", age: 30 });          // Create
db.users.find({ age: { $gte: 18 } });                     // Read -- $gte, $lt, $in, etc. are query operators
db.users.updateOne({ name: "Alice" }, { $set: { age: 31 } }); // Update
db.users.deleteOne({ name: "Alice" });                    // Delete
```

==> Embedding vs Referencing
--> Embedding -- nest related data directly inside a document (like the orders array above) -- fast reads (one query gets everything), but can duplicate data and grow documents large.
--> Referencing -- store just an ID and look up the related document separately (similar to a SQL foreign key) -- keeps documents smaller, but requires an extra query (or $lookup aggregation) to join data.
--> Rule of thumb: embed data that's always read together and rarely changes independently; reference data that's large, shared across many documents, or updated independently.

```javascript
// Referencing example
{ _id: 1, name: "Alice", authorId: ObjectId("...") }
db.users.aggregate([
  { $lookup: { from: "profiles", localField: "authorId", foreignField: "_id", as: "profile" } }
]); // MongoDB's rough equivalent of a SQL JOIN
```

==> Indexing in MongoDB
--> db.users.createIndex({ email: 1 }) -- speeds up queries filtering/sorting on email, same purpose as an index in a SQL database.
--> Without an index, MongoDB scans every document in the collection (a "collection scan") to find matches -- slow at scale.

==> When to Choose MongoDB
--> Rapidly evolving schemas (fields vary or change often across the app's lifetime)
--> Data that's naturally hierarchical/nested (a user profile with nested settings, a product with variable attributes)
--> High write throughput and horizontal scaling needs (sharding built in)
--> Less ideal when the data is highly relational with many cross-references needing strong consistency and complex joins -- a relational database fits better there.

## Deep Dive -- The CAP Theorem

--> The CAP Theorem states that a distributed database system can only guarantee TWO of the following three properties AT THE SAME TIME, not all three simultaneously, during a network partition (a communication failure between nodes):
--> **Consistency** -- every read receives the most recent write, or an error -- every node sees the same data at the same time.
--> **Availability** -- every request receives a (non-error) response, even if it might not reflect the latest write.
--> **Partition Tolerance** -- the system continues operating despite network failures between nodes.
--> Since network partitions are an unavoidable real-world possibility in any distributed system, partition tolerance is essentially mandatory -- the REAL practical choice most distributed databases face is between Consistency and Availability when a partition actually occurs.

```
During a network partition, a distributed database must choose:

CP (Consistency + Partition Tolerance) -- refuses to serve some requests to guarantee
    every response is up-to-date -- e.g. traditional relational databases in a clustered
    setup, MongoDB's default configuration (favors consistency for a given piece of data).

AP (Availability + Partition Tolerance) -- keeps responding to every request, but different
    nodes might temporarily return different, "stale" answers until they resync -- e.g.
    Cassandra, DynamoDB, in their typical/default configurations.
```

--> This directly explains why choosing a NoSQL database isn't just about document vs relational schema shape -- it's also implicitly a choice about this fundamental distributed-systems trade-off, and different NoSQL databases (and even different configuration modes within the SAME database) make different choices here, directly relevant to whether "eventual consistency" (a delay before all nodes agree on the latest value) is an acceptable trade-off for a given application's needs.

## Deep Dive -- Eventual Consistency in Practice

--> Many NoSQL systems (particularly AP-leaning ones like Cassandra/DynamoDB) default to "eventual consistency" -- a write is accepted immediately and propagated to other nodes in the BACKGROUND, meaning a read immediately after a write, routed to a different node, might briefly return the OLD value before that propagation completes.
--> This is a genuinely different mental model from a traditional relational database's strong consistency (covered in the Transactions and ACID file) -- application code interacting with an eventually-consistent store needs to be explicitly designed to tolerate brief staleness (e.g. showing "your comment is posting..." rather than assuming it's instantly visible everywhere), a real design constraint, not just an implementation detail to ignore.

## Deep Dive -- ACID vs BASE -- Naming the Two Philosophies Against Each Other

--> The Transactions and ACID file covers ACID in full -- BASE is the informal acronym coined specifically as its NoSQL/distributed-systems counterpart, describing the alternative philosophy most AP-leaning systems (Cassandra, DynamoDB) actually optimize for, directly connecting the CAP Theorem and Eventual Consistency deep dives above back to ACID by name.

--> **B**asically **A**vailable -- the system prioritizes always responding to a request (covered as the "A" in CAP) even if that means occasionally returning a response that doesn't reflect the very latest write, rather than refusing to answer at all.
--> **S**oft state -- the system's state can change over time even WITHOUT new writes, purely as a side effect of eventual-consistency background replication catching up between nodes -- there isn't one single, immediately-settled "current" state the way a committed ACID transaction guarantees.
--> **E**ventually consistent -- given enough time with no new writes, all replicas WILL converge to the same value -- but with no strict guarantee of exactly WHEN that convergence completes (directly connecting to the Eventual Consistency deep dive above).

```text
ACID (traditional relational):                BASE (typical AP-leaning NoSQL):
  Atomicity, Consistency,                       Basically Available,
  Isolation, Durability                         Soft state,
                                                 Eventually consistent

  Strong guarantees, enforced immediately       Weaker guarantees, enforced eventually
  May sacrifice availability to stay correct    May sacrifice strict correctness to stay available
```

--> **Why this isn't just "ACID = good, BASE = bad"** -- BASE is a deliberate, informed trade-off for workloads where availability and horizontal scale matter more than every single read being perfectly up-to-the-millisecond current (a social media like-count, a product view counter) -- exactly the same "choose the trade-off deliberately, based on the actual workload" framing the CAP Theorem deep dive already establishes, just given its own memorable name here as the direct rhetorical counterpart to ACID.
