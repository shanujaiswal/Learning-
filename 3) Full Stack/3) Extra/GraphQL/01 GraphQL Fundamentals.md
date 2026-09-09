# GraphQL as a General Concept

--> GraphQL is a query language and runtime for APIs, created by Facebook -- rather than exposing many fixed-shape REST endpoints, it exposes ONE endpoint where clients describe exactly what data they want in a single request, and get back exactly that shape.
--> This file covers GraphQL conceptually and framework-independently; a hands-on Node/Express + Apollo Server implementation walkthrough lives in the Node and Express folder's own GraphQL file.

# The Three Root Operation Types

--> **Query** -- read data, analogous to a REST `GET`.
--> **Mutation** -- write/modify data, analogous to REST `POST`/`PUT`/`DELETE`.
--> **Subscription** -- a long-lived connection (typically over WebSockets) that pushes data to the client whenever a specific event occurs -- GraphQL's answer to the real-time push problem also solved by Socket.io/WebSockets.

```graphql
subscription {
  newMessage(channelId: "general") {
    id
    text
    author
  }
}
```

# Strong Typing -- The Schema Is a Contract

--> Every field, argument, and return type is explicitly declared in the Schema -- the API is fully self-describing, which is exactly what makes tools like GraphiQL/Apollo Studio able to auto-generate interactive API documentation and autocomplete, without needing separately-maintained docs (a common drift problem with REST APIs and Swagger/OpenAPI specs).

# Fragments -- Reusing Query Pieces

--> A Fragment defines a reusable set of fields, avoiding repetition when the same fields are needed in multiple places within a query.

```graphql
fragment UserBasicInfo on User {
  id
  name
  email
}

query {
  currentUser {
    ...UserBasicInfo
    posts { title }
  }
  recommendedUser {
    ...UserBasicInfo
  }
}
```

# Variables -- Parameterizing Queries

--> Rather than string-concatenating values into a query (a real injection risk, echoing the SQL Injection concerns covered in the Database notes), GraphQL queries accept typed variables, keeping the query structure static and safely separating it from user-supplied values.

```graphql
query GetUser($userId: ID!) {
  user(id: $userId) {
    name
  }
}
```

```json
{ "userId": "42" }
```

# Client-Side Libraries

--> Apollo Client and urql are the most common client-side libraries for consuming a GraphQL API from a frontend framework -- they handle caching, re-fetching, and loading/error state in a way conceptually similar to React Query/SWR (covered in the React notes) but purpose-built around GraphQL's query/mutation model specifically.

# GraphQL-Specific Security Considerations

--> Because a client can construct arbitrarily deep/nested queries, a malicious or careless client could request an extremely expensive query (deeply nested relationships, huge result sets) -- production GraphQL servers typically implement query depth limiting, complexity/cost analysis, and rate limiting specifically tuned for this risk, on top of the general API security practices covered in the Ethical Hacking track's API security file.

# GraphQL vs REST -- Restated Simply

--> REST -- many endpoints, fixed shapes, naturally cacheable via HTTP caching, simpler mental model.
--> GraphQL -- one endpoint, client-defined shapes, avoids over/under-fetching, better suited to complex/nested data needs and multiple client types (web + mobile) with differing data requirements from the same backend.
