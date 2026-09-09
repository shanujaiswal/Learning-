# What Domain-Driven Design Is, and Why "Strategic" First

--> **Domain-Driven Design (DDD)**, from Eric Evans's book of the same name, is a set of practices for building software whose structure mirrors a deep, shared understanding of the business domain it serves -- rather than mirroring database tables, framework conventions, or whatever shape was easiest to code first. DDD splits into two halves: **strategic** patterns, covered in this file, operate at the scale of an entire organization or system -- how to carve a large, messy problem space into coherent pieces, and how those pieces relate to each other. **Tactical** patterns (file 04) operate at the scale of code inside one of those pieces -- entities, value objects, aggregates. Strategic design should always come first: it decides WHERE a piece of logic belongs and WHO owns it, before any class gets written to hold it.
--> DDD's strategic patterns are also the conceptual foundation most microservices decompositions actually rest on, whether or not a team explicitly names DDD as the reason -- "one service per bounded context" is close to the most defensible service-boundary heuristic in wide use today.

# The Problem: One Model Cannot Serve Everyone

--> In a sufficiently large system, a single word means DIFFERENT things to different parts of the business. Consider "Customer" at an e-commerce company:

```text
In Sales:           a Customer has a sales rep, a lead score, a pipeline stage
In Billing:         a Customer has a payment method, an invoice history, a credit limit
In Support:         a Customer has a ticket history, an SLA tier, a satisfaction score
In Shipping:        a Customer has a delivery address, preferred carrier, delivery instructions
```

--> A single, shared `Customer` class trying to serve all four of these becomes bloated, is edited by four unrelated teams for four unrelated reasons (an SRP violation at the architecture scale), and every team ends up half-using, half-ignoring fields that belong to a different concern. DDD's answer is not to find one perfect unified model -- it's to accept that MULTIPLE models, each valid and precise within its own boundary, is the correct outcome.

# Bounded Contexts

--> A **bounded context** is an explicit boundary within which a particular domain model is defined, consistent, and unambiguous. Inside a bounded context, a term like "Customer" has exactly one meaning and one shape. Outside that boundary, the same word may mean something else entirely, modeled by a different class, in a different bounded context -- and that is by design, not a mistake to be reconciled.

```text
┌─────────────────────────┐   ┌─────────────────────────┐
│   Sales Bounded Context      │   │   Billing Bounded Context     │
│                             │   │                             │
│   class Customer {           │   │   class Customer {           │
│     String salesRepId;       │   │     String paymentMethodId;   │
│     int leadScore;            │   │     BigDecimal creditLimit;    │
│     String pipelineStage;      │   │     List<Invoice> invoices;    │
│   }                          │   │   }                          │
└─────────────────────────┘   └─────────────────────────┘
      Same word, same real-world person, DELIBERATELY different models --
      each is complete and correct for its own context's purposes.
```

```java
// Two classes, same name, living in two different packages/bounded contexts.
// Neither is "wrong" -- each is the right shape for ITS context's concerns.
package com.example.sales;
class Customer {
    String customerId;
    String salesRepId;
    int leadScore;
}

package com.example.billing;
class Customer {
    String customerId;
    String paymentMethodId;
    java.math.BigDecimal creditLimit;
}
```

--> **A bounded context is not just a data model boundary -- it's a boundary of LANGUAGE, TEAM ownership, and (very often) deployment.** In a microservices system, a bounded context typically maps to one service, owned by one team, with its own database, deployed independently. This is why bounded contexts matter as much to system architecture as to domain modeling: getting a bounded context boundary wrong produces either a service that is too chatty with its neighbors (boundary drawn in the wrong place, splitting something that should be one concept) or a service that has quietly become a second dumping ground for unrelated concerns (boundary not drawn at all).

# Ubiquitous Language

--> Within a bounded context, DDD insists on a **ubiquitous language** -- a vocabulary shared, without translation, between domain experts (the people who understand the business) and the code itself. If the business calls something a "Chargeback", the code should have a class, method, or variable literally named `Chargeback` -- not `PaymentReversalRecord` or `refund_v2`, and not a generic `Transaction` with a `type` field distinguishing it after the fact.

```java
// WITHOUT ubiquitous language -- code and business conversation use different words;
// every discussion requires a mental translation step, and translation drift causes bugs.
class Transaction {
    String type;   // "CHARGE", "REFUND", "CHARGEBACK" -- the domain concept is buried in a string
    BigDecimal amount;
}

// WITH ubiquitous language -- the business's own vocabulary IS the type system.
// A domain expert reading this code recognizes their own words directly.
interface PaymentEvent { }
class Charge implements PaymentEvent { BigDecimal amount; }
class Refund implements PaymentEvent { BigDecimal amount; String reason; }
class Chargeback implements PaymentEvent { BigDecimal amount; String disputeId; }
```

--> **Why this matters beyond tidiness**: when code and conversation share exactly one vocabulary, a domain expert can read a method signature and correct a misunderstanding immediately, rather than a developer silently encoding their OWN (possibly wrong) interpretation of a business rule into a variable name nobody outside engineering recognizes. Ubiquitous language is also scoped PER bounded context -- "Order" in a Sales context and "Order" in a Fulfillment context are allowed to mean different things, exactly as with `Customer` above, because each is its own language living inside its own boundary.

# Context Mapping

--> Real systems have MULTIPLE bounded contexts that must communicate. **Context mapping** is the practice of explicitly documenting how bounded contexts relate to and integrate with each other -- because an undocumented, informal relationship between two contexts is exactly where implicit coupling and translation bugs creep in. Evans and later DDD literature name several recurring relationship patterns:

| Pattern | Meaning |
|---|---|
| **Shared Kernel** | two contexts deliberately share a small, jointly-owned subset of the model (e.g. a common `Money` value object); changes to it require both teams' agreement |
| **Customer/Supplier** | one context (supplier) provides data/functionality that a downstream context (customer) depends on; the supplier team plans around the customer's needs |
| **Conformist** | a downstream context has no negotiating power and simply conforms to the upstream context's model as-is, with no translation layer |
| **Anticorruption Layer (ACL)** | a downstream context builds a translation layer that converts an upstream model into its OWN model, so upstream changes/messiness never leak in directly |
| **Open Host Service** | an upstream context publishes a well-defined, stable public API/protocol intended for many downstream consumers, rather than a bespoke integration per consumer |
| **Published Language** | a shared, well-documented interchange format (e.g. a versioned event schema) used for communication between contexts, independent of either context's internal model |
| **Separate Ways** | two contexts have no meaningful integration need; deliberately keep them fully independent rather than forcing an integration |

```java
// Anticorruption Layer -- the Billing context depends on a third-party payment
// provider's model, but never lets that model leak past this one translation class.
// Everything else in Billing works only with Billing's OWN Chargeback/Charge types.
class StripeAntiCorruptionLayer {
    // Stripe's raw event shape -- messy, provider-specific, subject to change without our consent.
    static class StripeRawEvent { String type; long amountCents; String disputeReason; }

    // Translates Stripe's model into Billing's OWN ubiquitous language.
    PaymentEvent translate(StripeRawEvent raw) {
        return switch (raw.type) {
            case "charge.succeeded" -> new Charge(java.math.BigDecimal.valueOf(raw.amountCents, 2));
            case "charge.dispute.created" ->
                    new Chargeback(java.math.BigDecimal.valueOf(raw.amountCents, 2), raw.disputeReason);
            default -> throw new IllegalArgumentException("Unhandled Stripe event: " + raw.type);
        };
    }
}
```

--> **Why an Anticorruption Layer matters architecturally**: without one, a third-party (or another team's) model shape leaks directly into a context's domain code -- every field the upstream system adds, renames, or restructures now forces a change deep inside the downstream context, and the downstream context's own ubiquitous language gets contaminated with vocabulary that isn't really its own. This is the Dependency Rule from files 01-02 applied to INTER-CONTEXT boundaries rather than intra-application layers: the downstream context's stable, valuable domain model should not depend directly on an upstream context's volatile, foreign one.

# Subdomains: Core, Supporting, and Generic

--> Not every part of a business deserves the same investment of design effort. DDD classifies subdomains into three tiers, which should directly drive where a team spends its best engineering time.

```text
Core Subdomain          -- the thing that makes THIS business win/differentiate.
                            Deserves the most design investment, the best engineers,
                            custom-built software, and deep DDD tactical modeling (file 04).

Supporting Subdomain     -- necessary for the core to function, but not itself a
                            competitive differentiator. Worth building in-house,
                            but with a lighter touch (simpler CRUD, fewer patterns).

Generic Subdomain        -- a solved problem every company in every industry needs
                            (authentication, sending email, invoicing, file storage).
                            Buy or use an existing library/SaaS; building it from
                            scratch is usually wasted effort.
```

--> **Example**: for a company whose business IS algorithmic trading, the trade-matching and risk-calculation engine is the CORE subdomain -- it deserves the richest domain model, the most experienced engineers, and the heaviest DDD tactical investment. Sending trade-confirmation emails is a SUPPORTING subdomain -- necessary, but not what makes the company money. User authentication is a GENERIC subdomain -- nearly identical at every company, and best handled by an off-the-shelf identity provider rather than hand-rolled.
--> **The common mistake this classification guards against**: spending elite engineering effort building a bespoke authentication system (generic) while the actual trade-matching logic (core) is left as an under-designed, poorly-tested afterthought. Subdomain classification is a resource-allocation tool as much as a modeling tool.

# How This Relates to Microservices Boundaries

--> A **bounded context is the natural, defensible unit for a microservice boundary** -- far more defensible than splitting services by technical layer (a "database service", a "business-logic service") or by arbitrary team org-chart lines. Each bounded context already has its own consistent model, its own vocabulary, and (ideally) its own team; wrapping that in a service boundary with its own database and deployment pipeline is a natural, low-friction step, not an additional design decision.

```text
Bounded Context  ──maps to──>  Microservice (typical, not universal)

  Sales context           ──>  sales-service        (owns its own Customer model, own DB)
  Billing context          ──>  billing-service       (owns its own Customer model, own DB)
  Shipping context          ──>  shipping-service       (owns its own Customer model, own DB)

  Context mapping patterns (Customer/Supplier, ACL, Open Host Service, Published
  Language) become the ACTUAL inter-service integration patterns -- an event schema
  published by billing-service for others to consume IS a Published Language;
  a translation layer inside shipping-service that guards against billing-service's
  raw event shape IS an Anticorruption Layer.
```

--> **Getting the bounded-context boundary wrong is the single most common root cause of a painful microservices decomposition.** Splitting a bounded context across two services forces constant chatty synchronous calls and shared-transaction headaches between services that were never really independent to begin with (a "distributed monolith"); conversely, cramming multiple unrelated bounded contexts into one service recreates the original bloated, multi-team-owned model problem this whole file opened with, just now hidden inside a single deployable instead of a single class. Strategic DDD is, in this sense, service-boundary design performed BEFORE any infrastructure decision is made.

# Common Gotchas and Best Practices

--> **Skipping strategic design and jumping straight to tactical patterns (file 04).** Aggregates, entities, and value objects (file 04) are powerful, but applying them inside a badly-drawn bounded context just produces a well-engineered wrong boundary. Strategic design (where do the boundaries go, who owns what) should be settled, at least provisionally, before investing heavily in tactical modeling inside any one context.
--> **Trying to build one unified, canonical model for the whole organization.** This is the single most common DDD mistake -- resisting a shared "Customer" table with a `type` column is uncomfortable at first, but a truly universal model tends to satisfy no one context well and becomes a bottleneck every team has to negotiate through for every change.
--> **Confusing "bounded context" with "database" or "microservice".** A bounded context is fundamentally a boundary of MODEL and LANGUAGE; a database and a service are common (and often good) ways to enforce that boundary technically, but the concept exists and is useful even in a single-deployable monolith organized into clearly separated modules/packages -- this is sometimes called a "modular monolith", and it is a perfectly legitimate way to apply bounded contexts without committing to microservices at all.
--> **Building an integration between two contexts without deciding which context mapping pattern applies.** An ad hoc, undiscussed integration tends to silently become a Conformist relationship (the downstream team just adapts to whatever the upstream team ships) even when a Customer/Supplier or Anticorruption Layer relationship would have served both teams far better -- naming the intended pattern up front makes the trade-off (and who bears the translation cost) an explicit decision instead of an accident.
--> **Misclassifying a core subdomain as generic (or vice versa) out of habit.** "Everyone needs a notifications system" makes notification delivery SOUND generic, but if personalized, timing-optimized notifications are genuinely how a company drives retention and revenue, that subdomain may actually be core for THAT company specifically -- subdomain classification depends on what differentiates THIS business, not on how common the general problem shape is elsewhere.
