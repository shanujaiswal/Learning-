# Beyond "It Caches Things" -- Operating a CDN in Production

--> The previous file establishes WHAT a CDN does (serve cached content from nearby edge locations) and how `Cache-Control` headers set an INITIAL caching policy. This file covers what happens AFTER that initial policy is set -- how to actually get stale content out of caches you don't directly control, how a CDN's edge network doubles as a genuine security layer, and why and how organizations run MORE than one CDN at once.

# Cache Invalidation and Purging Strategies

--> The previous file's filename-hashing pattern (`app.a1b2c3.js`) elegantly SIDESTEPS invalidation for build-versioned static assets -- but it only works when a client naturally requests the new URL anyway. It does nothing for content that must be updated at the SAME URL (a CMS page edit, a product's price, a corrected legal document) -- for that, you need to actively tell the CDN's edge locations, wherever they're geographically scattered, that their cached copy is now stale.

## TTL Expiry -- The Passive Default

```http
Cache-Control: public, max-age=3600
```

--> The simplest approach -- just wait. Every edge location's cached copy naturally expires after `max-age` seconds and gets refetched from the origin on the next request past that point. Works fine for content where a short window of staleness (users seeing the old version for up to an hour after an update) is genuinely acceptable, and requires zero active intervention -- but it's fundamentally a passive, "eventually" guarantee, not an immediate one.

## Active Purging -- Forcing Immediate Invalidation

```bash
# Cloudflare API -- purge a specific URL from EVERY edge location immediately
curl -X POST "https://api.cloudflare.com/client/v4/zones/{zone_id}/purge_cache" \
  -H "Authorization: Bearer $CF_API_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"files":["https://example.com/pricing"]}'
```

--> When content genuinely cannot wait for a TTL to naturally expire (an incorrect price that must be fixed everywhere immediately, a legal/compliance-driven takedown), an explicit purge call tells the CDN provider to immediately evict that URL's cached copy from EVERY edge location worldwide, forcing the next request anywhere to go back to the origin and refetch a fresh copy.
--> **The real cost of purging widely and often** -- a purge, by definition, temporarily increases origin load (every edge location's next request after a purge is a guaranteed cache MISS, all needing to hit the origin at once) and defeats the entire latency benefit of caching for that content until it's re-cached -- exactly why purging is reserved for genuine "this must change NOW" situations, not used as a routine substitute for correctly setting `max-age` in the first place.

## Tag-Based (Surrogate Key) Purging

```http
Surrogate-Key: product-142 category-electronics homepage-featured
```

```bash
# Purge every cached response tagged with "product-142" -- could be several
# different URLs/pages (the product page, category listing, homepage feature
# slot) that all happen to display that one product's data
curl -X POST "https://api.fastly.com/service/{service_id}/purge/product-142" \
  -H "Fastly-Key: $FASTLY_API_TOKEN"
```

--> **The problem this solves** -- a single piece of underlying data (one product's price) often appears across MULTIPLE distinct cached URLs (its own page, a category listing, a homepage banner) -- purging by exact URL one at a time is error-prone (easy to forget one) and doesn't scale. Tagging every cached response that includes a given piece of data with a shared "surrogate key" lets ONE purge call invalidate every affected URL simultaneously, regardless of how many different pages happen to display that data.
--> **Why this matters specifically for a CMS or e-commerce platform** -- this pattern (native to Fastly, and available via similar mechanisms on other providers) is what makes CDN caching genuinely practical for dynamic-but-cacheable content with data that fans out across many pages, rather than forcing a choice between "cache aggressively but risk stale data scattered across forgotten pages" and "don't cache this content at all."

## Stale-While-Revalidate -- Avoiding a Latency Cliff on Expiry

```http
Cache-Control: public, max-age=60, stale-while-revalidate=3600
```

--> Rather than the FIRST request after `max-age` expires being forced to wait for a full origin round-trip (a real, user-visible latency spike hitting whichever unlucky request lands right after expiry), `stale-while-revalidate` lets the CDN serve the (now slightly stale) cached copy IMMEDIATELY to that request, while fetching a fresh copy from the origin in the BACKGROUND to serve to subsequent requests -- trading a bounded, deliberate extra window of staleness for eliminating the latency cliff at expiry entirely.

# CDN-Based Rate Limiting and Edge Security

--> The previous file's DDoS Mitigation section names the CDN's distributed edge network absorbing malicious traffic; this section makes explicit exactly how, and why enforcing this AT THE EDGE (rather than only back at the origin/API Gateway layer covered in the API Design file) is genuinely more effective, not merely redundant with it.

## Why Edge-Level Enforcement Is Different From Gateway-Level

--> Directly connecting to the API Design file's "Where Enforcement Happens -- Gateway vs Per-Service" section -- a CDN's edge is a THIRD, even earlier layer, geographically closest to the actual traffic source, before a request has traveled all the way back to the origin infrastructure at all.

```
Malicious traffic origin --> Nearest EDGE location --> (blocked/rate-limited HERE,
                                                          never travels any further)
                          --> [if it got through] --> Origin's own API Gateway
                                                          rate limiting
                          --> [if it got through] --> Origin server itself
```

--> **The concrete advantage of stopping abuse at the edge** -- malicious/excessive traffic is absorbed and discarded at a location geographically close to its source, consuming the CDN provider's distributed edge capacity (which is typically enormous, and specifically built to absorb exactly this kind of volumetric abuse) rather than ever consuming YOUR origin infrastructure's bandwidth, compute, or your own gateway's rate-limit bookkeeping at all -- a volumetric DDoS flood that would meaningfully strain a single origin data center barely registers against a CDN's globally distributed edge capacity.

```
# Example: a Cloudflare/Fastly-style edge rate-limiting rule
Rule: if requests from a single IP to /api/login exceed 10 per minute,
      respond with 429 directly at the edge -- the request never reaches
      the origin's own login endpoint OR its own application-level
      rate limiter at all.
```

## Edge Security Capabilities Beyond Basic Rate Limiting

--> **WAF (Web Application Firewall) rules at the edge** -- inspecting and blocking requests matching known attack signatures (SQL injection patterns, cross-site scripting payloads, covered conceptually in the Cyber Security track) before they ever reach application code, directly connecting to the AWS WAF concepts referenced in the previous file's provider comparison.
--> **Bot management** -- distinguishing genuine human/browser traffic from automated scraping/credential-stuffing bots using signals only visible at the edge (TLS fingerprinting, request timing patterns, JavaScript challenge responses) -- a layer of defense that's structurally hard to replicate purely at an origin-side API Gateway, since the edge sees the RAW connection characteristics of the request before any application-level processing normalizes them away.
--> **Geo-blocking/geo-fencing** -- blocking or challenging traffic from specific countries/regions entirely at the edge, useful both for abuse mitigation (blocking traffic from a region generating disproportionate attack volume) and for genuine legal/licensing compliance (content only licensed for distribution in specific territories).
--> **The layered takeaway** -- edge-level rate limiting/WAF/bot management, gateway-level rate limiting, and per-service rate limiting (from the API Design file) together form a genuine defense-in-depth stack, each layer catching what got past the one before it, with progressively MORE specific/precise enforcement the closer you get to the actual origin service -- exactly the same "cheap and broad first, precise and expensive last" logic that recurs throughout the security and rate-limiting material across this track.

# Multi-CDN Strategies

--> A single CDN provider, however large, is still a single point of failure and a single performance profile -- a genuine outage at one provider (these do happen, occasionally at real scale) takes down every site relying SOLELY on it, and any one provider's edge network is inevitably stronger in some regions than others.

## Why Run More Than One CDN

```
Single-CDN risk:
  Provider X has a regional outage --> every user in that region served
  by Provider X loses access entirely, with no fallback.

Multi-CDN:
  Traffic is split/routed across Provider X AND Provider Y --> if X has
  an outage, traffic fails over to Y, and the site stays up for
  those users instead of going fully down.
```

--> **Resilience** -- the primary driver -- eliminating a single provider's outage as a single point of total failure for the whole site. **Performance optimization by region** -- different CDN providers genuinely have different edge network strengths in different parts of the world; routing traffic to whichever provider performs best for a GIVEN user's specific region (rather than committing entirely to one provider's global average performance) can measurably improve real-world latency. **Negotiating leverage/cost** -- having genuine, provable ability to shift meaningful traffic volume to a competing provider gives real negotiating leverage on pricing that sole reliance on one vendor doesn't.

## How Traffic Is Actually Split

```
DNS-based steering:
  A DNS provider (or a dedicated multi-CDN traffic manager) resolves a
  domain to Provider X's edge IPs for some users, and Provider Y's for
  others -- based on real-time health checks (routing AWAY from a
  provider currently having issues) and/or measured performance data
  per region.

Client-side/RUM-based steering:
  Real User Monitoring data (actual measured load times different real
  users experienced against each provider, from real traffic) feeds
  back into the routing decision, continuously refining which provider
  is actually fastest for which specific region/network, rather than
  relying on a static, unchanging assumption about which provider is
  "generally best."
```

--> **The real cost, and why not every deployment needs this** -- running multiple CDN providers means paying and integrating with (and correctly configuring cache purging logic, from the sections above, consistently across) more than one vendor, plus building or buying the traffic-steering layer that decides which provider serves which request -- genuine added operational complexity that only pays for itself once a site's traffic/revenue/uptime requirements are large enough that a single provider's occasional regional hiccup or full outage represents a real, costly risk worth actively engineering around -- the same "don't reach for this until a concrete, proven pain point justifies the added complexity" judgment that recurs throughout the Software Architecture files' own framework for when to adopt any additional architectural complexity at all.
