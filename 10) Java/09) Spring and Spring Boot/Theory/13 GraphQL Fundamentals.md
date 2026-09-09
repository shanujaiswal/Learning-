# Why GraphQL Exists -- The Problem It Solves

--> REST organizes an API around RESOURCES (`/products`, `/products/{id}/reviews`) -- each endpoint returns a FIXED shape decided by the server. A mobile screen that needs a product's name and price gets the SAME full payload (description, images, dimensions, seller info...) as a desktop screen that needs everything, because the server can't know in advance what each client actually wants.
--> **GraphQL** flips this around: it's a QUERY LANGUAGE for APIs where the CLIENT describes the exact shape of data it needs in the request itself, and the server returns EXACTLY that shape -- no more, no less. It was created at Facebook (2012 internally, open-sourced 2015) specifically to solve the mismatch between rapidly changing mobile UI needs and rigid REST endpoints.
--> GraphQL is NOT a database, NOT tied to any particular storage technology, and NOT a replacement for HTTP -- it's a specification for a query language, a type system, and an execution model. It typically runs over a SINGLE HTTP endpoint (commonly `POST /graphql`), in contrast to REST's many endpoints spread across many URLs.

# GraphQL vs REST -- The Core Comparison

| Aspect | REST | GraphQL |
|---|---|---|
| Endpoints | Many (one per resource/action) | Usually one (`/graphql`) |
| Response shape | Fixed by the server per endpoint | Chosen by the client per request |
| Over-fetching | Common (server sends the full resource) | Avoided (client asks for exact fields) |
| Under-fetching | Common (need multiple round trips to assemble a screen) | Avoided (one query can span multiple related resources) |
| Versioning | Often `/v1/`, `/v2/` URL prefixes | Typically NONE -- schema evolves by adding fields, deprecating old ones |
| Type system | Not enforced by the protocol itself (OpenAPI/Swagger is optional, bolted on) | BUILT IN -- the schema is the contract, strongly typed |
| Caching | Easy -- HTTP caching works naturally per URL | Harder -- one endpoint, POST requests aren't cached by browsers/CDNs by default |
| Discoverability | Requires separate docs (OpenAPI spec, Postman collection) | Self-documenting via INTROSPECTION -- the schema describes itself |
| File uploads / simple GETs | Natural fit | Needs extensions/conventions (GraphQL isn't built for binary payloads) |

--> **Over-fetching** -- a REST `GET /products/42` might return 20 fields when the UI only displays 3 of them. The client downloads and discards the rest, wasting bandwidth -- more painful on mobile networks.
--> **Under-fetching** -- a screen showing a product AND its reviews AND the reviewer's profile picture might need THREE separate REST calls (`/products/42`, `/products/42/reviews`, `/users/{id}`), each a full network round trip. GraphQL expresses all of that as ONE query, resolved server-side in a single request.
--> **Neither replaces the other outright** -- REST remains simpler for CRUD-shaped services, file transfer, and cases where HTTP-level caching matters a lot (e.g. public, rarely-changing content). GraphQL shines when clients have DIVERSE and EVOLVING data needs (multiple frontends: web, iOS, Android, each wanting a different slice of the same backend data) or when a screen naturally needs data assembled from many sources.

# The Schema Definition Language (SDL) -- GraphQL's Type System

--> Every GraphQL API is described by a **schema**, written in **SDL (Schema Definition Language)** -- a small, readable syntax for declaring types, fields, and their relationships. The schema is the CONTRACT between client and server, analogous to an OpenAPI spec for REST, except it's a first-class, mandatory part of GraphQL itself (not optional tooling bolted on afterward).

```graphql
type Product {
    id: ID!
    name: String!
    price: Float!
    category: String
    inStock: Boolean!
    reviews: [Review!]!
}

type Review {
    id: ID!
    rating: Int!
    comment: String
    author: User!
}

type User {
    id: ID!
    username: String!
}
```

--> **Scalar types** -- GraphQL ships with five built-in scalars: `Int`, `Float`, `String`, `Boolean`, and `ID` (a unique identifier, serialized as a `String` but semantically distinct -- signals "this is an opaque identifier, don't do math on it"). Custom scalars (e.g. `DateTime`, `BigDecimal`) can be added but need custom serialization logic wired in on the server.
--> **`!` means NON-NULL** -- `name: String!` means this field can NEVER return `null`; `category: String` (no `!`) means it CAN be `null`. This is enforced by the GraphQL execution engine itself, not just documentation -- a resolver that returns `null` for a non-null field triggers a runtime error that propagates up.
--> **`[Review!]!`** reads inside-out: a non-null LIST, of non-null `Review` items -- i.e., `reviews` is guaranteed to be an array (never `null`, though it CAN be empty `[]`), and every element in that array is guaranteed to be a non-null `Review`.
--> **Object types reference each other** -- `Product.reviews: [Review!]!` and `Review.author: User!` let a single query traverse from a product, through its reviews, to each reviewer -- this GRAPH of connected types is where GraphQL gets its name.

# Queries, Mutations, and Subscriptions -- The Three Root Operation Types

--> Every GraphQL schema defines up to three special ROOT types, each an entry point into the graph:

```graphql
type Query {
    product(id: ID!): Product
    products(category: String): [Product!]!
}

type Mutation {
    createProduct(input: ProductInput!): Product!
    deleteProduct(id: ID!): Boolean!
}

type Subscription {
    productPriceChanged(productId: ID!): Product!
}
```

--> **`Query`** -- read operations, conceptually equivalent to REST's `GET`. Queries are expected to be SIDE-EFFECT FREE (reading shouldn't change server state) and, because of that, GraphQL servers/clients are free to execute independent query fields IN PARALLEL.
--> **`Mutation`** -- write operations (create/update/delete), conceptually equivalent to REST's `POST`/`PUT`/`PATCH`/`DELETE`. Unlike queries, top-level mutation fields in a single request are executed SEQUENTIALLY, one after another -- this matters if one mutation's side effect could affect another's outcome.
--> **`Subscription`** -- a long-lived operation that pushes updates to the client over time (typically over WebSockets), conceptually similar to a REST server-sent-events stream or a WebSocket channel. The client subscribes once and receives a new payload every time the underlying event fires (e.g. `productPriceChanged` fires an update every time that product's price changes in the database).
--> **A client sends a QUERY DOCUMENT, not a URL** -- the request body (usually `POST /graphql` with a JSON payload) contains the actual GraphQL query text plus any variables, and the single endpoint interprets which root type/field is being invoked based on the query's syntax (`query { ... }`, `mutation { ... }`, `subscription { ... }`).

```graphql
# A client query, asking for exactly the fields it needs
query {
    product(id: "42") {
        name
        price
        reviews {
            rating
            author {
                username
            }
        }
    }
}
```

```json
// Server response -- shape mirrors the query exactly
{
  "data": {
    "product": {
      "name": "Mechanical Keyboard",
      "price": 89.99,
      "reviews": [
        { "rating": 5, "author": { "username": "alice" } },
        { "rating": 4, "author": { "username": "bob" } }
      ]
    }
  }
}
```

--> **Variables** avoid string-concatenating user input directly into the query text (a GraphQL-level analog of SQL injection risk otherwise) -- the query declares typed variable placeholders, and the client sends variable values as a separate JSON object alongside the query:

```graphql
query GetProduct($id: ID!) {
    product(id: $id) {
        name
        price
    }
}
```

```json
{ "id": "42" }
```

# Resolvers -- How Fields Actually Get Their Data

--> A **resolver** is a function attached to ONE FIELD in the schema, responsible for producing that field's value. When a query comes in, the GraphQL execution engine walks the query's SHAPE and, for every field the client asked for, calls that field's resolver -- this is fundamentally different from REST, where one handler method produces the entire response body at once.
--> **Default resolvers** -- if a field's name matches a property on the underlying data object (e.g. `Product.name` matching a Java `Product.getName()`), most GraphQL server frameworks (including Spring for GraphQL, covered in the next file) provide a TRIVIAL default resolver automatically -- you only write explicit resolver code for fields that need real logic (a root `Query`/`Mutation` field, a computed field, or a field that requires fetching related data from elsewhere).
--> **Resolver execution is naturally NESTED and RECURSIVE** -- resolving `product(id: "42") { reviews { author { username } } }` means: call the `product` resolver to get a `Product` object, then for `reviews`, call a resolver that fetches that product's reviews, then for EACH review, call a resolver for `author`, then for `username` on that author. Each resolver only needs to know how to get ITS OWN field's data from its PARENT object -- it doesn't need to know about the whole query tree.

```text
product(id: "42")                     <- root Query resolver: fetch the Product
  |-- name                            <- default resolver: product.getName()
  |-- price                           <- default resolver: product.getPrice()
  |-- reviews                         <- resolver: fetch reviews for this product's id
        |-- rating                    <- default resolver: review.getRating()
        |-- author                    <- resolver: fetch the User for this review's authorId
              |-- username            <- default resolver: user.getUsername()
```

--> **Resolvers can be async / return promises / futures** -- because each field's resolver is independent, a GraphQL execution engine can resolve SIBLING fields concurrently (e.g. fetching `reviews` and some other unrelated field of `product` at the same time), which is one of GraphQL's efficiency advantages over sequential REST calls -- though this concurrency benefit is only fully realized when resolvers are written to support it (non-blocking I/O, async data fetching).

# Introspection -- The Schema Describing Itself

--> GraphQL schemas are **introspectable** -- a client can QUERY THE SCHEMA ITSELF using special meta-fields (`__schema`, `__type`), asking "what types exist, what fields does `Product` have, what arguments does `createProduct` accept." This is how tooling like GraphQL Playground, GraphiQL, and Apollo Studio can auto-generate interactive documentation and autocomplete WITHOUT any separate hand-maintained spec file.

```graphql
query {
    __type(name: "Product") {
        name
        fields {
            name
            type { name }
        }
    }
}
```

--> This is a meaningful contrast with REST, where documentation (OpenAPI/Swagger) is a SEPARATE artifact that can drift out of sync with the actual implementation -- a GraphQL schema and its introspection result are, by construction, always accurate, because they ARE the live server's contract.

# Common Gotchas

--> **Thinking GraphQL replaces REST outright** -- it doesn't automatically make an API "better"; it trades REST's simplicity and HTTP-native caching for flexibility and precision in fetched data. A simple CRUD service with one client rarely benefits enough to justify the added complexity.
--> **Forgetting non-null (`!`) is enforced at RUNTIME** -- declaring a field non-null in the schema is a promise the resolver MUST keep; if the underlying data can legitimately be missing, either make the field nullable or handle the missing case explicitly in the resolver (returning `null` from a resolver bound to a `!` field is a schema-contract violation, not just an inconvenience).
--> **The N+1 problem is worse in GraphQL than REST by default** -- because every nested field can have its OWN resolver called once per parent item, a naive implementation of `Product.reviews { author { username } }` across 50 reviews can trigger 50 separate "fetch this author" calls. This is significant enough to warrant its own coverage (see the next file, DataLoader pattern).
--> **All requests going through ONE endpoint breaks naive HTTP caching** -- browsers and CDNs cache based on URL + method, and GraphQL typically uses `POST /graphql` for everything, which most HTTP caches treat as non-cacheable by default. Solving this requires GraphQL-aware caching layers (e.g. persisted queries, Apollo's cache, or application-level caching), not something you get for free the way REST's `GET` endpoints do.
--> **Errors don't always mean HTTP failure** -- a GraphQL response can return HTTP `200 OK` even when part of the query failed, with a top-level `"errors"` array alongside a partially-populated `"data"` object. Client code needs to check the `errors` field explicitly, not just rely on the HTTP status code the way REST clients typically do.

# Best Practices Summary

--> Design the SCHEMA first, as a genuine contract -- treat it with the same care as an OpenAPI spec, since GraphQL clients depend on it directly for both correctness and tooling.
--> Prefer EVOLVING the schema by adding new nullable fields and deprecating old ones (`@deprecated(reason: "...")`) over introducing versioned endpoints -- this is one of GraphQL's central design philosophies.
--> Keep `Query` fields side-effect-free and let `Mutation` be the only place state changes happen -- this preserves the engine's ability to parallelize query resolution safely.
--> Use variables for any client-supplied values in a query -- never string-concatenate user input into query text.
--> Be deliberate about nullability -- mark a field non-null (`!`) only when the resolver can GENUINELY guarantee a value every time; over-promising non-null leads to confusing runtime errors later.
--> Plan for the N+1 problem from the start on any schema with nested/relational fields -- it's a "when," not "if," concern the moment a schema has one type referencing a list of another.
