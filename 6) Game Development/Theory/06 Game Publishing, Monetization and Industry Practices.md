# Distribution Platforms

--> **Steam** (PC) -- dominant PC storefront, takes a revenue cut (historically 30%, tiering down for very high-revenue titles), requires going through Steamworks integration (achievements, cloud saves, workshop) and a review process before release.
--> **App Store (iOS) / Google Play (Android)** -- mobile distribution, also historically ~30% cuts (with reduced rates for smaller developers under certain thresholds), each with its own review process and, notably, its own strict content and monetization disclosure rules.
--> **Console certification** (PlayStation, Xbox, Switch) -- the strictest gate: consoles require formal "certification" (cert) passes checking crash stability, compliance with platform UX guidelines, save data handling, and more, before a build can ship -- a process that can take weeks and reject a submission over relatively minor issues.
--> Across all platforms, "ship day" is really the end of a much longer pipeline: build submission, automated and manual review, and (for consoles especially) fixing certification failures and resubmitting -- worth planning for as a project timeline risk, not an afterthought.

# Monetization Models

--> **Premium (pay-once)** -- simplest model: player pays up front, gets the full game. Predictable revenue per copy, but ongoing revenue depends entirely on new sales, so games either need a sequel/DLC cadence or accept a front-loaded revenue curve.
--> **Free-to-play (F2P)** -- the game itself is free, revenue comes from in-game purchases. Removes the price barrier to entry (much larger player base) but requires designing purchasable value into the game itself, and typically needs a much larger, more consistent player base to be profitable per-player.
--> **In-app purchases (IAP)** -- cosmetics (skins, emotes -- doesn't affect gameplay balance), consumables (currency, boosts), or "pay-to-win" items that directly affect power -- the last of these is the most controversial since it can undermine competitive fairness.
--> **Battle passes** -- a paid, time-limited track of unlockable rewards earned by playing during a season; effective at driving both a one-time purchase AND daily/weekly engagement (players return to "not waste" pass progress), now a dominant model in live-service games.
--> **Subscriptions** -- recurring payment for ongoing content/services (Xbox Game Pass, World of Warcraft) or ad-free/premium access -- gives predictable recurring revenue but requires continuously proving ongoing value to avoid cancellations.
--> Trade-offs across all of these mirror the general SaaS pricing trade-offs touched on in the Software Architecture notes: one-time payments are simple and predictable but cap revenue per user, while recurring/microtransaction models scale revenue with engagement but require sustained content investment and are far more sensitive to player trust.

# Live-Ops and Post-Launch Content

--> **Live-ops** ("live operations") is the ongoing work of running a game AFTER launch: new content drops, balance patches, timed events, seasonal battle passes, and monitoring player behaviour to decide what to build next.
--> This turns a "finished" game into something closer to a continuously operated service -- which pulls directly on infrastructure and monitoring concepts from the MLOps and Big Data folder (telemetry pipelines, A/B testing balance changes, anomaly detection for exploits or server issues) rather than being purely a game-design activity.
--> Player retention metrics (Day 1/7/30 retention, DAU/MAU ratios) become central business metrics for live-service games in a way they simply aren't for a one-time premium release -- closely related to the general analytics/metrics concepts covered in the Data Analyst folder, just applied to play sessions instead of business transactions.

# Manipulative Monetization Patterns -- An Honest Look

--> **Loot boxes** -- randomized reward mechanics purchased with real or in-game currency -- draw regulatory scrutiny specifically because the "pay for a random chance at value" structure closely resembles gambling mechanics, especially for players who can't reliably judge odds (a concern that's been particularly acute regarding minors). Several countries (Belgium, and various others with partial restrictions) have restricted or banned loot boxes in games available to minors; many platforms now require odds disclosure.
--> **Dark patterns in monetization** -- deliberately confusing currency conversions (buying an oddly-priced in-game currency bundle that doesn't map cleanly to purchase prices, obscuring the real-money cost of an item), artificially inflated "limited time!" urgency, and friction-free spending flows paired with deliberately difficult refund/cancellation flows.
--> **Pay-to-win in competitive contexts** specifically erodes trust because it breaks the implicit fairness contract of competition -- this is a design and business risk distinct from the ethical concerns above, since it can hollow out a game's core audience even where no regulation applies.
--> None of this makes monetization inherently exploitative -- cosmetic-only IAP and well-structured battle passes are broadly considered healthy models by both players and regulators. The distinction that keeps drawing scrutiny is specifically randomized-chance mechanics tied to real money, and mechanics designed to obscure real spending -- worth understanding clearly if you're ever the one designing a monetization system, rather than treating "make more money" as monetization design's only success metric.

# Deep Dive -- Why "Ship It and Move On" Doesn't Work Anymore

--> The industry's shift from primarily premium, one-time releases toward live-service models has fundamentally changed what "shipping a game" even means: launch is now often treated as closer to a beta than a finish line, with the majority of a title's total content, balance, and revenue arriving over months or years afterward.
--> This has a real engineering consequence: live games need infrastructure that supports frequent, low-risk deployment of patches and content (server-side config changes, feature flags, staged rollouts) -- essentially the same continuous-deployment discipline covered in the GitHub folder's CI/CD file and the Node and Express folder's Production Deployment file, just applied to a real-time, latency-sensitive service instead of a typical web backend.
