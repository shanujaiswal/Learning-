# The Problem -- Distance Adds Latency

--> If your server is in Virginia and a user is browsing from Singapore, every request physically travels that entire distance -- adding real, unavoidable network latency no amount of server-side optimization can remove, since it's bounded by the speed of light over that distance.
--> A CDN (Content Delivery Network) solves this by caching content at Edge Locations distributed around the world, so a user's request is served from the nearest edge location instead of always reaching all the way back to the origin server.

# How a CDN Request Flow Works

```
User (Singapore) --> Nearest Edge Location (Singapore) --> [Cache HIT: serve immediately]
                                                          --> [Cache MISS: fetch from Origin Server (Virginia), cache it, then serve]
```

--> First request from a region -- cache miss, the edge location fetches from the origin (the real latency hit happens once).
--> Every subsequent request from nearby users -- cache hit, served entirely from the nearby edge location, with dramatically lower latency and reduced load on the origin server itself.

# What Gets Cached -- Static vs Dynamic Content

--> Ideal CDN candidates -- static assets that rarely change: images, CSS, JavaScript bundles, videos, downloadable files.
--> Dynamic, personalized content (a logged-in user's dashboard, real-time data) is generally NOT cached at the edge by default, since caching it could serve one user's private data to another -- though modern CDNs increasingly support more sophisticated edge caching/compute for specific dynamic use cases too.

# Cache Control Headers -- Telling the CDN What to Do

```http
Cache-Control: public, max-age=31536000, immutable
```

--> `Cache-Control` headers (set by the origin server) tell both the CDN and the browser how long content can be cached and whether it's safe to cache at all -- `max-age` in seconds, `public` (cacheable by shared caches like a CDN, not just the browser), `immutable` (this exact URL's content will never change, common when filenames include a content hash).
--> Cache-busting via filename hashing (`app.a1b2c3.js` instead of `app.js`) is the standard pattern for safely setting a very long cache duration on static assets -- when the file's content changes, its hashed filename changes too, so it's automatically treated as a brand-new, uncached resource rather than requiring the cache to somehow know the old one is now stale.

# CDN Providers

--> Cloudflare, AWS CloudFront (covered in the AWS notes), Akamai, Fastly -- similar core function, differing in edge network size, additional features (Cloudflare's WAF/DDoS protection bundled in, Fastly's fast cache purging for near-real-time updates).

# Beyond Static Caching -- Additional CDN Capabilities

--> TLS/SSL termination at the edge -- the CDN handles the HTTPS handshake close to the user, rather than every connection needing to reach the origin server for encryption setup.
--> DDoS mitigation -- a CDN's distributed edge network absorbs and filters malicious traffic before it ever reaches the origin server, directly connecting to the DDoS protection concepts covered in the Cyber Security and AWS Security Hardening notes.
--> Edge computing -- some CDNs (Cloudflare Workers, AWS CloudFront Functions/Lambda@Edge) let you run actual code at the edge location itself, close to the user, for tasks like request routing, A/B testing, or lightweight authentication checks, without a round trip to the origin at all.

# Why Every Full-Stack Deployment Touches This

--> Nearly every production deployment pattern covered elsewhere in this folder (the AWS "Deploying a Simple Full-Stack App" pattern, Kubernetes Ingress) assumes a CDN sits in front of static assets at minimum -- this file is the conceptual foundation those deployment patterns were already relying on.
