# Beyond Search -- The Analytics Half and the Operational Reality

--> The previous file covers Elasticsearch as a full-text SEARCH engine -- finding and ranking the most RELEVANT documents. This file covers the other major half of what Elasticsearch does (aggregations -- computing analytics ACROSS documents, not just finding individual ones), plus the operational depth needed to run it at real scale: how data ages out of an index over time, how a field's mapping actually shapes what queries are even possible against it, what a cluster's nodes are each individually responsible for, and how Elasticsearch supports the newer category of vector/semantic search alongside its original text-matching roots.

# Aggregations -- The Analytics Half of Elasticsearch

--> The previous file's queries answer "which documents match, ranked by relevance." Aggregations answer a fundamentally different kind of question -- "summarize/group these matching documents" -- the same conceptual job as SQL's `GROUP BY` and aggregate functions (`COUNT`, `AVG`, `SUM`, covered in the SQL notes), but computed across a potentially massive, distributed dataset, and combinable directly alongside a relevance query in the same request.

## Metric Aggregations -- Computing a Single Number

```json
GET /products/_search
{
  "size": 0,
  "aggs": {
    "average_price": { "avg": { "field": "price" } },
    "price_stats":    { "stats": { "field": "price" } }
  }
}
```

```json
Response (relevant part):
{
  "aggregations": {
    "average_price": { "value": 47.32 },
    "price_stats": { "min": 5.99, "max": 199.99, "avg": 47.32, "sum": 47320.0, "count": 1000 }
  }
}
```

--> `"size": 0` -- explicitly tells Elasticsearch not to bother returning any actual matched DOCUMENTS, since this request only cares about the aggregated numbers, saving the cost of fetching and returning documents nobody's going to look at.
--> Metric aggregations (`avg`, `sum`, `min`, `max`, `stats`, `cardinality` for a distinct-count) compute ONE summary number (or a small fixed set of them) across every document matching the query -- directly analogous to SQL's aggregate functions, just computed across Elasticsearch's distributed shards (from the previous file's Core Concepts) and combined into one final result.

## Bucket Aggregations -- Grouping Documents

```json
GET /products/_search
{
  "size": 0,
  "aggs": {
    "by_category": {
      "terms": { "field": "category" },
      "aggs": {
        "average_price_in_category": { "avg": { "field": "price" } }
      }
    }
  }
}
```

```json
Response (relevant part):
{
  "aggregations": {
    "by_category": {
      "buckets": [
        { "key": "electronics", "doc_count": 340, "average_price_in_category": { "value": 89.50 } },
        { "key": "books",       "doc_count": 210, "average_price_in_category": { "value": 14.20 } }
      ]
    }
  }
}
```

--> A bucket aggregation splits matching documents into GROUPS ("buckets"), each defined by some criteria (`terms` -- one bucket per distinct field value, `range` -- one bucket per numeric range, `date_histogram` -- one bucket per time interval) -- directly analogous to SQL's `GROUP BY`.
--> **Nested aggregations -- the genuinely powerful part** -- a metric aggregation can be nested INSIDE a bucket aggregation (as shown above -- average price COMPUTED SEPARATELY within each category bucket), letting one single request answer "break these results down by category, AND tell me the average price within EACH category" -- exactly the kind of faceted-analytics query that powers a typical e-commerce search page's sidebar filters (showing category counts and price ranges alongside search results) in one combined round trip, rather than needing separate queries per facet.

```json
GET /logs/_search
{
  "size": 0,
  "aggs": {
    "requests_per_hour": {
      "date_histogram": { "field": "@timestamp", "calendar_interval": "hour" }
    }
  }
}
```

--> `date_histogram` -- buckets documents into fixed TIME intervals, the core mechanism behind the time-series dashboards (requests per hour, errors per day) built on top of the log-aggregation use case named in the previous file, directly connecting to the Kubernetes Observability file's EFK/ELK stack, where Kibana visualizes exactly this kind of aggregation as a chart.

# Index Lifecycle Management (ILM)

--> A log/metrics index (the previous file's "log aggregation" use case) grows continuously and forever unless something actively manages it -- ILM automates moving an index through defined stages as it ages, without a human manually intervening on a schedule.

```json
PUT _ilm/policy/logs_policy
{
  "policy": {
    "phases": {
      "hot":    { "actions": { "rollover": { "max_size": "50gb", "max_age": "1d" } } },
      "warm":   { "min_age": "7d",  "actions": { "shrink": { "number_of_shards": 1 } } },
      "cold":   { "min_age": "30d", "actions": { "freeze": {} } },
      "delete": { "min_age": "90d", "actions": { "delete": {} } }
    }
  }
}
```

--> **Hot** -- actively written to and queried, kept on fast storage/nodes, and periodically "rolled over" into a brand new index once it hits a size/age threshold (rather than one single index growing unboundedly forever). **Warm** -- no longer written to, occasionally queried, moved to cheaper storage and shrunk (fewer shards, since it no longer needs write-optimized parallelism). **Cold** -- rarely queried, moved to the cheapest storage, "frozen" to minimize its resource footprint while still technically searchable if needed. **Delete** -- past its useful retention period, removed entirely.
--> **Why this matters at any real log/metrics volume** -- without ILM, an ever-growing single index eventually degrades query performance (more data to scan per search) and consumes ever more expensive storage for data nobody queries anymore; ILM directly implements the same "hot data needs speed, cold data needs cost efficiency, ancient data needs deleting" tiering logic that shows up generally in the AWS S3 storage-class notes, just automated specifically around Elasticsearch's own index rollover/shrink/delete primitives.

# Mapping Types in Depth

--> The previous file mentions a Document is "schema-flexible, unlike a rigid SQL table," but that flexibility has real limits -- a field's MAPPING (its declared type) determines exactly what queries can be run against it, and getting it wrong (or letting Elasticsearch guess it via "dynamic mapping") is a common, hard-to-fix-later source of production search bugs.

```json
PUT /products
{
  "mappings": {
    "properties": {
      "name":        { "type": "text" },
      "name_exact":  { "type": "keyword" },
      "price":       { "type": "float" },
      "created_at":  { "type": "date" },
      "in_stock":    { "type": "boolean" },
      "location":    { "type": "geo_point" }
    }
  }
}
```

--> **`text` vs `keyword` -- the single most consequential mapping decision** -- `text` fields are ANALYZED (tokenized, lowercased, per the previous file's Analyzers section) specifically to support fuzzy, relevance-ranked full-text search (`match` queries); `keyword` fields are stored EXACTLY as-is, with no analysis at all, and support only exact-match filtering, sorting, and aggregations (the `terms` bucket aggregation above genuinely requires a `keyword` field -- running it against an analyzed `text` field buckets by individual TOKEN, not by the original whole value, producing a nonsensical result).

```json
// A common real-world pattern -- map the SAME underlying value both ways,
// so both use cases work correctly against the field they actually need
{
  "name": {
    "type": "text",
    "fields": {
      "keyword": { "type": "keyword" }
    }
  }
}
// Query: match on "name" for full-text search
// Aggregate/sort/exact-filter on "name.keyword" for that instead
```

--> **Dynamic mapping's real danger** -- by default, Elasticsearch GUESSES a new field's type from the first document that introduces it (a numeric-looking string might get mapped as a number, an object gets an auto-inferred nested structure) -- convenient for quick prototyping, but a genuine production risk, since a LATER document with a differently-shaped value for that same field can be silently rejected or mis-indexed, and a field guessed as the WRONG type (e.g. a `keyword` where `text` full-text search was actually needed) usually can't be fixed by simply updating the mapping -- typically requires reindexing the entire index from scratch into a new one with the corrected mapping. Explicitly defining a mapping upfront (as shown above), the same discipline as a SQL schema, avoids this entirely for anything beyond quick experimentation.

# Cluster Health and Node Roles

--> The previous file names shards and replicas as Elasticsearch's built-in distribution mechanism; this section covers how a CLUSTER of nodes is actually organized to serve that distribution, and how to read its health at a glance.

```bash
GET /_cluster/health

{
  "status": "green",
  "number_of_nodes": 6,
  "active_shards": 45,
  "unassigned_shards": 0
}
```

--> **Green** -- every primary AND replica shard is allocated and healthy. **Yellow** -- every primary shard is healthy, but at least one REPLICA isn't (e.g. a node briefly down) -- data is NOT lost, but redundancy is currently reduced, a real "fix this soon" warning, not an emergency. **Red** -- at least one PRIMARY shard is unavailable, meaning some data is genuinely inaccessible right now -- an active incident requiring immediate attention.

## Node Roles

```
Master-eligible nodes  -- manage cluster-wide state: which nodes exist, which
                          shard lives on which node, index creation/deletion --
                          the cluster ELECTS one active master among them at a time,
                          conceptually similar in spirit to the leader-election idea
                          behind Kubernetes' own control-plane HA.

Data nodes              -- actually STORE shards and execute search/aggregation
                            queries against them -- where the real CPU/memory/disk
                            work of Elasticsearch happens.

Ingest nodes             -- pre-process a document (parsing, enriching, transforming
                             fields) BEFORE it's actually indexed, via an "ingest pipeline" --
                             offloading transformation logic that would otherwise need
                             to happen in application code before ever sending the
                             document to Elasticsearch at all.

Coordinating nodes (implicit -- any node can act as one) -- receive a client's
                             request, fan it out to every relevant data node holding
                             a relevant shard, and merge their partial results into
                             one final response -- the node a client actually talks
                             to isn't necessarily the one holding the data.
```

--> **Why separating these roles matters at real production scale** -- a small/dev cluster typically runs every role on the same few nodes, but a large deployment dedicates SPECIFIC nodes to specific roles (e.g. beefy, disk-heavy machines as data nodes; smaller, network-focused machines as dedicated master-eligible nodes) so that a spike in query load (hitting data nodes hard) doesn't risk starving the cluster's own master-election/coordination machinery of resources it needs to keep the cluster itself stable -- the same "don't let one concern's load starve an unrelated but critical concern" logic that motivates separating concerns generally throughout distributed systems design.

# Vector/Semantic Search -- kNN

--> Everything in the previous file and in this file's Aggregations/Mapping sections is fundamentally KEYWORD-based -- a `match` query finds documents containing the actual words searched for (even accounting for stemming/synonyms via the Analyzer). Vector/semantic search answers a genuinely different question -- "find documents whose MEANING is similar to this query," even if they don't share any of the same literal words at all.

```
Traditional keyword search for "affordable laptop for students":
  Matches documents literally containing words like "affordable," "laptop," "students."
  A genuinely relevant listing titled "Budget-Friendly Notebook Computer for College"
  might be RANKED LOW or missed entirely -- it shares almost no literal keywords.

Semantic/vector search:
  Both the query and every document are converted into an EMBEDDING -- a dense
  numeric vector (from a machine learning model, connecting to the embeddings
  concept in the Data Science and AI folder) capturing MEANING, not literal words --
  and Elasticsearch finds the documents whose vectors are numerically CLOSEST
  to the query's vector, regardless of literal word overlap.
```

```json
PUT /products
{
  "mappings": {
    "properties": {
      "description_embedding": {
        "type": "dense_vector",
        "dims": 384,
        "index": true,
        "similarity": "cosine"
      }
    }
  }
}

GET /products/_search
{
  "knn": {
    "field": "description_embedding",
    "query_vector": [0.021, -0.14, 0.098, ...],
    "k": 10,
    "num_candidates": 100
  }
}
```

--> `dense_vector` -- a mapping type specifically for storing an embedding (a fixed-length array of floating-point numbers, generated OUTSIDE Elasticsearch by an embedding model and supplied at index time). `knn` (k-Nearest-Neighbors) search finds the `k` documents whose stored vectors are mathematically closest to the query's vector, using a similarity metric like cosine similarity.
--> **Hybrid search -- combining both, in practice** -- most real production semantic-search systems don't replace keyword search with vector search outright; they run BOTH a traditional `match` query and a `knn` query in the SAME request and blend their scores, since pure keyword matching is often still better for exact terms (a specific product SKU, an exact model number) that a semantic embedding might blur past, while vector search catches genuinely related results keyword matching would miss entirely -- getting the strengths of both rather than picking one exclusively.
--> **Why this doesn't replace the fundamentals covered elsewhere** -- vector search still depends on Elasticsearch's underlying distributed shard/replica architecture (previous file) for scale, still benefits from ILM (above) for managing embedding-heavy indices' storage costs over time, and still needs a correctly-defined mapping (above) for the vector field itself -- it's an additional QUERY capability layered on the same operational foundation, not a separate system.
