# Why Elasticsearch -- The Problem With SQL Search

--> A SQL `LIKE '%keyword%'` query (covered in the SQL notes) is slow at scale and can't rank results by relevance, handle typos, or search across many fields intelligently -- it's a basic substring match, not real search.
--> Elasticsearch is a distributed search and analytics engine, built on Apache Lucene, purpose-built for fast, relevance-ranked full-text search across large volumes of data -- the technology behind "search" features in countless real products (e-commerce product search, log search, autocomplete).

# Core Concepts

--> Index -- roughly analogous to a database (or a table, depending on how you model it) -- a collection of related documents.
--> Document -- a single JSON record, roughly analogous to a row -- schema-flexible, unlike a rigid SQL table.
--> Field -- roughly analogous to a column.
--> Elasticsearch is distributed by default -- an index is automatically split into Shards spread across nodes, and can have Replicas for fault tolerance, echoing the sharding/replication concepts covered in the Database Advanced notes but built into Elasticsearch natively from the ground up.

# Indexing a Document

```bash
PUT /products/_doc/1
{
  "name": "Wireless Bluetooth Headphones",
  "description": "Noise-cancelling over-ear headphones with 30-hour battery life",
  "price": 79.99,
  "category": "electronics"
}
```

# Full-Text Search Queries

```json
GET /products/_search
{
  "query": {
    "match": {
      "description": "wireless noise cancelling"
    }
  }
}
```

--> Unlike SQL's exact substring match, `match` tokenizes the search text, finds documents containing any/all of those tokens, and returns results RANKED by relevance score (based on term frequency, field length, and other factors) -- the best match appears first, not just "any match, unordered."

# Analyzers -- How Text Gets Tokenized

--> An Analyzer breaks text into searchable tokens -- typically lowercasing, splitting on whitespace/punctuation, and removing common "stop words" (the, a, is) -- this is what lets a search for "Running" also match a document containing "run" or "runs" depending on the analyzer's configured stemming.

# Filtering vs Full-Text Querying

--> `match`/`multi_match` queries -- for relevance-ranked, fuzzy, full-text search.
--> `term`/`range` filters -- for exact-match/structured filtering (a specific category, a price range) -- typically combined with a full-text query in a `bool` query.

```json
GET /products/_search
{
  "query": {
    "bool": {
      "must": { "match": { "description": "headphones" } },
      "filter": { "range": { "price": { "lte": 100 } } }
    }
  }
}
```

# Common Real-World Use Cases

--> Product search on e-commerce sites (relevance ranking, typo tolerance, faceted filtering by category/price).
--> Log aggregation and analysis -- the "E" in the EFK stack covered in the Kubernetes Observability file, storing and searching massive volumes of application/infrastructure logs.
--> Autocomplete/typeahead suggestions as a user types.

# Elasticsearch vs a Traditional Database

--> Elasticsearch is NOT a replacement for a primary transactional database (PostgreSQL, MySQL) -- it's typically used ALONGSIDE one, indexing a searchable copy of data that lives authoritatively in the primary database, kept in sync via the application or a change-data-capture pipeline.
--> It trades strict ACID transactional guarantees (covered in the Transactions and ACID file) for search speed and relevance -- the right tool specifically for "find the most relevant matches," not for "the single source of truth for this data."
