# REST vs GraphQL

--> A REST API (covered in the Building REST APIs file) exposes multiple fixed-shape endpoints (`/users/1`, `/users/1/posts`) -- a client often needs several round trips to gather related data, or gets back MORE data than it actually needs from a broad endpoint (over-fetching).
--> GraphQL exposes a SINGLE endpoint where the client describes exactly what data it wants, in one request -- the server returns precisely that shape, no more, no less, potentially resolving data from multiple underlying sources in one round trip.

# The Schema -- Defining What's Queryable

--> A GraphQL API is defined by a strongly-typed Schema (using GraphQL's own Schema Definition Language) describing every type, and every Query/Mutation clients are allowed to perform.

```graphql
type User {
  id: ID!
  name: String!
  email: String!
  posts: [Post!]!
}

type Post {
  id: ID!
  title: String!
  content: String!
  author: User!
}

type Query {
  user(id: ID!): User
  posts: [Post!]!
}

type Mutation {
  createPost(title: String!, content: String!, authorId: ID!): Post!
}
```

--> `!` marks a field as non-nullable -- the server guarantees it will never return `null` for that field.

# Querying -- The Client Asks for Exactly What It Needs

```graphql
query {
  user(id: "1") {
    name
    posts {
      title
    }
  }
}
```

```json
{
  "data": {
    "user": {
      "name": "Alice",
      "posts": [{ "title": "My First Post" }]
    }
  }
}
```

--> Notice the response shape exactly mirrors the query shape -- the client got the user's name and their posts' titles in ONE request, without over-fetching the user's email or the posts' full content, and without a separate round trip to fetch posts after fetching the user.

# Setting Up Apollo Server with Express

```javascript
const { ApolloServer } = require("@apollo/server");
const { expressMiddleware } = require("@apollo/server/express4");
const express = require("express");

const typeDefs = `
  type Query {
    hello: String
  }
`;

const resolvers = {
  Query: {
    hello: () => "Hello, GraphQL!",
  },
};

const server = new ApolloServer({ typeDefs, resolvers });
await server.start();

const app = express();
app.use("/graphql", expressMiddleware(server));
app.listen(4000);
```

# Resolvers -- Where Data Actually Comes From

--> A resolver is a function that fetches the actual data for a specific field -- this is where a GraphQL server connects its schema to real data sources (a SQL database, a REST API, another microservice).

```javascript
const resolvers = {
  Query: {
    user: (parent, args) => db.findUserById(args.id),
  },
  User: {
    posts: (parent) => db.findPostsByAuthorId(parent.id),   // Resolves the "posts" field on a User
  },
};
```

# The N+1 Query Problem and DataLoader

--> A naive resolver setup can accidentally issue one database query PER item in a list (fetching 50 posts' authors triggers 50 separate author lookups) -- known as the N+1 problem.
--> DataLoader batches and caches those individual lookups within a single request, turning 50 separate queries into one batched query -- a standard, near-mandatory tool in any non-trivial GraphQL server.

# When GraphQL Is (and Isn't) the Right Choice

--> Good fit -- complex, nested, client-varying data needs (a mobile app and a web app needing different subsets of the same data), reducing round trips for deeply related data.
--> Less necessary -- simple CRUD APIs with few relationships, where REST's simplicity and ease of caching (HTTP caching works naturally with REST's per-resource URLs, less naturally with GraphQL's single endpoint) outweigh GraphQL's flexibility benefits.
