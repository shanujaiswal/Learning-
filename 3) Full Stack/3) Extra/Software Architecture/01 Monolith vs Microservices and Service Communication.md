# What "Architecture" Means Beyond Individual Technologies

--> Everything covered elsewhere in the Full Stack track (React, Node/Express, SQL/Postgres, Docker/Kubernetes) is a TECHNOLOGY choice. Architecture is a separate, higher-level layer of decisions -- how you STRUCTURE an entire system's components and their relationships to each other -- and it matters independently of which specific technologies you pick underneath it. Two teams could build the exact same feature set with the exact same tech stack and end up with radically different systems purely based on architectural choices.

# The Monolith -- One Deployable Unit

--> A monolithic architecture packages an entire application's functionality (user management, orders, payments, notifications) into ONE codebase, deployed and scaled as a single unit -- most applications, including the practical projects referenced throughout the React/Node folders, start this way, and for good reason.

```
Monolith:
┌─────────────────────────────────┐
│         Single Application        │
│  ┌────────┐ ┌────────┐ ┌────────┐│
│  │  Users  │ │ Orders  │ │Payments││
│  └────────┘ └────────┘ └────────┘│
│         One Database              │
└─────────────────────────────────┘
```

## Advantages of a Monolith

--> **Simplicity of development** -- one codebase, one repository (connecting to the Git/GitHub concepts covered in the Full Stack Extra notes), one deployment pipeline -- a new developer can run the ENTIRE application locally with one command, understanding the full system without needing to coordinate across multiple separate services.
--> **Simplicity of operations** -- one thing to deploy, one thing to monitor, no network communication between internal components (avoiding an entire category of distributed-systems bugs covered further below), and transactions across different "modules" (an order affecting both inventory and billing) can use a single database transaction (covered in the Transactions and ACID file) instead of needing complex cross-service coordination.
--> **Easier debugging** -- a single stack trace shows the ENTIRE request's journey through the system, rather than needing to piece together logs from several separate services (directly connecting to the distributed tracing concept covered in the Kubernetes Observability file, which exists specifically to solve this exact problem for microservices).

## Disadvantages That Emerge at Scale

--> **Scaling is all-or-nothing** -- if only the "Orders" part of the application is under heavy load, you still have to scale the ENTIRE monolith (every instance carries the full application, including parts that don't need extra capacity), wasting resources compared to scaling just the busy part independently.
--> **Deployment risk grows with codebase size** -- every deployment ships changes to the WHOLE application at once; a bug in an unrelated feature can block or risk an entirely unrelated team's release, and the blast radius of any single bug is the entire application.
--> **Technology lock-in** -- the whole application is built in one language/framework; adopting a new technology for one specific new feature isn't really practical without either accepting the mismatch or committing to it application-wide.
--> **Team coordination overhead grows** -- as more engineers/teams work in the SAME codebase, merge conflicts, coordination meetings, and "who owns this part of the code" ambiguity all increase, a problem sometimes called "organizational scaling" as distinct from technical scaling.

# Microservices -- Splitting Into Independent Services

--> A microservices architecture splits an application into multiple SMALL, independently deployable services, each typically owning its own specific business capability AND its own database -- communicating with each other over a network (typically HTTP/REST or message queues, both covered in the Full Stack Node/Express and Extra Message Queues files).

```
Microservices:
┌──────────┐   ┌──────────┐   ┌───────────┐
│  Users    │   │  Orders   │   │ Payments   │
│  Service  │──▶│  Service  │──▶│  Service    │
│ (own DB)  │   │ (own DB)  │   │  (own DB)   │
└──────────┘   └──────────┘   └───────────┘
```

## Advantages of Microservices

--> **Independent scaling** -- if the Orders service is under heavy load, scale JUST that service (adding more instances, covered via Kubernetes Horizontal Pod Autoscaling in the DevOps notes) without touching Users or Payments at all.
--> **Independent deployment** -- each team can deploy their own service on their own schedule, without needing to coordinate a single application-wide release -- directly enabling the kind of fast, frequent CI/CD deployment cadence covered in the GitHub CI/CD and AWS CI/CD files at genuinely large organizational scale.
--> **Technology flexibility** -- each service can use whichever language/database/framework best fits ITS specific job (e.g. a recommendation service written in Python to use the ML ecosystem covered in the Data Science and AI folder, alongside a Node.js-based Orders service) -- a monolith can't offer this at all.
--> **Fault isolation** -- if the Payments service crashes, Users and Orders can (ideally, with proper design, covered below) continue functioning -- a failure is contained rather than taking down the entire application.

## The Real Costs Microservices Introduce

--> **Distributed systems complexity** -- network calls between services can fail, time out, or arrive out of order in ways that a single monolith's in-process function calls simply never do -- this isn't a minor inconvenience, it's an entirely new category of failure mode every microservices team must design around from day one.
--> **Data consistency across services** -- since each service owns its own database, a single business operation touching multiple services (placing an order affects Orders, Inventory, AND Payments) can no longer use one simple database transaction -- this directly motivates the Saga pattern and eventual consistency, covered next.
--> **Operational overhead** -- many more things to deploy, monitor, and secure (each service needs its own CI/CD pipeline, its own logging/monitoring setup, covered in the Kubernetes Observability file) -- genuinely justifying Kubernetes' complexity (referenced in the DevOps notes' "when do you actually need Kubernetes" discussion) far more than a monolith would.
--> **Testing complexity** -- integration testing across many independently-deployed services is significantly harder than testing one cohesive codebase, since a bug might only manifest from the specific interaction between two services' current versions.

# Service Communication Patterns

## Synchronous Communication -- REST and gRPC

--> The simplest pattern -- one service calls another directly over HTTP (the REST concepts covered in the Node/Express and REST API files) and WAITS for a response before continuing.

```
Order Service --HTTP POST /charge--> Payment Service
              (waits for response before confirming the order)
```

--> **gRPC** -- an alternative to REST for service-to-service communication specifically, using Protocol Buffers (a compact binary format) instead of JSON, and HTTP/2 instead of HTTP/1.1 -- generally faster and more efficient than REST/JSON for internal service-to-service calls, though less universally supported by browsers/external clients than REST, which is why REST/GraphQL (covered in their own files) remain the default choice for public-facing APIs while gRPC is common specifically for internal microservice-to-microservice traffic.
--> **Risk** -- a synchronous call chain (Service A calls B, which calls C, which calls D) means A's response time is the SUM of every downstream service's response time, and if any one link in that chain is down, the entire chain fails -- a cascading failure risk directly motivating the Circuit Breaker pattern.

## Circuit Breakers -- Preventing Cascading Failures

--> A Circuit Breaker wraps a call to another service, and if that service starts failing repeatedly, the breaker "opens" -- immediately failing FUTURE calls without even attempting the network request, for a cooldown period -- preventing a struggling downstream service from being overwhelmed further, and preventing the calling service from wasting time/resources on calls likely to fail anyway.

```
Normal (Closed):     Calls pass through normally to the downstream service
Failing repeatedly:   Breaker "Opens" -- calls fail FAST, immediately, without even trying the network call
After a cooldown:      Breaker goes "Half-Open" -- allows a few test calls through to see if the service has recovered
Recovered:              Breaker "Closes" again -- normal traffic resumes
```

## Asynchronous Communication -- Message Queues and Events

--> Rather than calling another service directly and waiting, a service publishes a MESSAGE/EVENT (using the Kafka/RabbitMQ concepts covered in the Full Stack Message Queues file) and moves on immediately -- the receiving service processes it whenever it's ready, fully decoupling the two services' availability and timing from each other.

```
Order Service publishes: "OrderPlaced" event
     |
     +--> Inventory Service consumes it, decrements stock (independently, whenever ready)
     +--> Notification Service consumes it, sends confirmation email (independently, whenever ready)
```

--> This directly avoids the cascading-failure risk of synchronous chains -- if the Notification Service is temporarily down, the order still completes successfully; the notification simply gets sent once that service recovers and catches up on its queued messages.

# Choosing Between Them -- A Practical Framework

--> **Start with a monolith.** This is now widely accepted, well-established practical wisdom in the industry -- the complexity of microservices is rarely justified for a new product/team still figuring out its actual domain boundaries, and a monolith is dramatically faster to build, deploy, and iterate on early.
--> **Split into microservices when specific, concrete pain points emerge** -- a genuinely proven need for independent scaling of one specific component, organizational scaling pain (too many teams stepping on each other in one codebase), or a genuine need for technology diversity for a specific new capability -- not simply because microservices sound more sophisticated or modern.
--> **The "Modular Monolith"** -- a middle-ground approach, structuring a monolith's INTERNAL code into cleanly-separated modules (with well-defined boundaries and minimal cross-module coupling) without the network-communication and independent-deployment overhead of true microservices -- often the pragmatic sweet spot, keeping the door open to eventually splitting a specific module into its own service later, once (and only once) that split is actually justified by real, observed pain.
