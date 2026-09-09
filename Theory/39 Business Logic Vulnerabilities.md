### Business Logic Vulnerabilities

--> LEGAL/ETHICAL REMINDER: everything below is for authorized environments only — your own lab, deliberately vulnerable practice apps (OWASP Juice Shop's pricing/coupon challenges are built for exactly this), or engagements/bug bounty programs explicitly in scope with written permission. Manipulating a real checkout's price, redeeming a real coupon via a race condition, or farming a real referral system on production is fraud, not just "testing," even if the underlying bug is genuine. Always have written scope/rules of engagement before touching a real target's live commerce or account systems.

--> This note assumes you already understand the OWASP Top 10 (note 04), the injection/broken-access-control deep dive (note 25), and XSS/CSRF/SSRF (note 26). It builds on those toward a category that doesn't fit any of them cleanly.

## Why Business Logic Bugs Are a Distinct Category

--> Every vulnerability class in notes 04/25/26 — SQLi, XSS, IDOR, SSRF — is fundamentally the application failing to handle input SAFELY: a string breaks out of its intended context (SQL, HTML, a URL fetch) because of a missing sanitization/encoding/validation step. There's always a technical defect in the code you can point to.
--> A business logic vulnerability has none of that. The code runs exactly as written, with no memory-safety bug, no injection point, no missing output-encoding. The flaw lives one layer up — in the WORKFLOW or RULES the application encodes, which have a gap or an unchecked assumption an attacker can walk straight through. The request is perfectly well-formed; it's just a request the designer never expected (or never re-validated) a legitimate-looking user to make.
--> This is precisely why automated scanners (Burp's active scanner, Nessus, sqlmap) essentially never find these: a scanner detects vulnerabilities by pattern-matching syntax anomalies (an SQL error string, a reflected `<script>` tag, a timing delta). There is no malformed syntax here to detect — `price: 0.01` is syntactically valid JSON, `quantity: -1` is a syntactically valid integer. Finding business logic bugs requires a human who understands what the application is SUPPOSED to do end-to-end, and who then deliberately breaks that intended sequence, sign, or scope — which is also exactly why they consistently rank among the highest-paying, hardest-to-scan-for findings in bug bounty programs (see note 16).

## Price and Parameter Manipulation

--> The classic case: a checkout flow where the CLIENT (browser JS, mobile app) computes and sends the final price/quantity/discount, and the server simply trusts and charges that value instead of re-deriving it itself from the product ID and the authoritative price table.
--> Worked example — an intercepted checkout request (Burp Repeater, per note 04/14's proxy workflow):

```http
POST /api/checkout HTTP/1.1
Host: shop.example.com
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...

{
  "productId": "SKU-4471",
  "quantity": 1,
  "price": 49.99,
  "discountPercent": 0
}
```

--> If the server takes `price` straight from the request body and charges that amount (rather than looking up `SKU-4471`'s current price server-side and ignoring the client's number entirely), the fix is trivial to abuse in Repeater:

```http
POST /api/checkout HTTP/1.1
Host: shop.example.com
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...

{
  "productId": "SKU-4471",
  "quantity": 1,
  "price": 0.01,
  "discountPercent": 0
}
```

--> If this returns a `200 OK` and the order is placed at $0.01, the checkout implicitly trusted client-supplied pricing data. Some variants are subtler — a `discountPercent` field the client can set beyond the intended coupon range, or a `quantity: -5` that inverts a total (see the negative-value section below).
--> Why this is fundamentally an access-control/trust-boundary issue at its root, even though it's neither IDOR nor SQLi: the server is trusting client-controlled data to make an authorization-adjacent decision (how much this user owes) instead of re-deriving that value from a source it controls. It's the exact same underlying failure as an IDOR (client input determines something the server should decide) — the vulnerability just surfaces as a financial-logic bug instead of a data-access bug. The trust boundary between "client" and "server" is violated identically in both; only the consequence differs.

--> Mitigation: never accept price, discount, tax, or total fields from the client at all — the server should accept only `productId` + `quantity`, look up current price and any applicable, server-validated discount/coupon server-side, and compute the total itself, on every request, with no client-supplied override path.

## Workflow / Sequence Bypass

--> Multi-step processes (checkout: cart to shipping to payment to confirmation; onboarding: signup to email-verify to KYC-approve to account-active) are usually built as a sequence of endpoints, with the UI enforcing the order by only showing the "next" button once a prior step completes. The server-side bug is when each endpoint doesn't independently re-verify that the PRIOR required step actually happened — it just assumes the UI enforced the order, and quietly serves any authenticated caller who hits it directly.
--> Worked example — skipping payment entirely by calling the confirmation endpoint directly:

```http
POST /api/orders/8841/confirm HTTP/1.1
Host: shop.example.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...

{}
```

--> If the server marks order `8841` as `PAID`/`CONFIRMED` and releases it for fulfillment without independently checking a payment-gateway callback/webhook actually landed for that order ID, the entire payment step was purely a UI convention, not an enforced gate.
--> A second common shape: an admin-approval workflow where the intended flow is `submit-for-review` (sets status `PENDING`) then a separate admin action `approve` (sets status `APPROVED`), but the `approve` endpoint itself never re-checks that the CALLER holds the admin role — it was only ever reachable via an admin-only UI link, and the link being hidden was mistaken for the endpoint being protected.

```http
POST /api/documents/552/approve HTTP/1.1
Host: portal.example.com
Authorization: Bearer <regular_user_token>
```

--> If this succeeds for a non-admin token, the "gate" was cosmetic. This is a vertical-privilege-escalation flavor of the same root cause as note 25's admin-URL example, but framed as a WORKFLOW step rather than a static endpoint.

--> Mitigation: every step-dependent endpoint must independently verify, server-side, that the required prior state actually exists in the data model (`order.payment_status == PAID`, `document.submitted_by != caller` and `caller.role == admin`) — never infer that a prior step happened just because this endpoint was reached at all.

## Race Conditions as a Business-Logic Class

--> Distinct from the memory-level race conditions covered in notes 12/28 (TOCTOU on a file descriptor, a signal handler, shared memory) — this is the exact same TOCTOU (time-of-check-to-time-of-use) shape, just at the APPLICATION layer: check a balance/usage-count, then later update it, with a window in between where multiple concurrent requests can all pass the check before any of them commits the update.
--> Classic target: a single-use discount code, or a gift-card/wallet balance.

1. Application logic (pseudocode): `if (coupon.uses_remaining > 0) { apply_discount(); coupon.uses_remaining -= 1; save(); }`.
2. If 50 redemption requests for the SAME coupon arrive within milliseconds of each other, all 50 can read `uses_remaining = 1` (true at that instant) BEFORE any of the 50 writes back `0` — every one of them passes the check and applies the discount, even though it was meant to be single-use.
3. Same pattern for a gift-card balance: check `balance >= amount`, then debit — fire enough concurrent requests and the debits can collectively exceed the balance because each check ran against the still-not-yet-decremented value.

--> Testing this requires actual CONCURRENCY, not sequential requests — Burp's standard Intruder sends requests with enough network jitter between them that the race window usually closes before the second request arrives. Two practical approaches:
- Burp Intruder with the "null payloads" trick: set the payload type to "Null payloads," generate e.g. 50 empty payloads, and set the number of threads high — this fires requests back-to-back with minimal spacing, sometimes enough to win a race on a slow endpoint.
- **Turbo Intruder** (Burp extension, scriptable in Python): purpose-built for this — it opens all connections and sends every request's headers first, then releases all the request BODIES in the same fractional-millisecond window (the "last-byte sync" technique), reliably landing dozens of requests within the same race window regardless of network jitter.

```python
# Turbo Intruder script skeleton — race a single-use coupon redemption
def queueRequests(target, wordlists):
    engine = RequestEngine(endpoint=target.endpoint,
                            concurrentConnections=30,
                            engine=Engine.BURP2)
    for i in range(30):
        engine.queue(target.req, gate='race1')   # queue on a gate, don't send yet
    engine.openGate('race1')                     # release all 30 simultaneously

def handleResponse(req, interesting):
    table.add(req)
```

--> Mitigation: enforce the check-and-decrement as a single ATOMIC database operation (e.g. `UPDATE coupons SET uses_remaining = uses_remaining - 1 WHERE code = ? AND uses_remaining > 0`, checking the affected-row count rather than a separate read-then-write) or use a proper distributed lock/transaction isolation level around the whole check-then-update sequence, so concurrent requests serialize instead of all reading stale state.

## Insufficient Anti-Automation

--> Many endpoints assume a human is clicking, one attempt at a time, and never add rate-limiting or CAPTCHA to enforce that assumption — leaving them trivially scriptable.
--> Coupon-code guessing: if coupon codes follow a guessable pattern (`SAVE10-XXXX` with a 4-digit numeric suffix) and the redemption endpoint has no rate limit or lockout, a script can brute-force all 10,000 combinations in minutes.
--> Referral/bonus farming: a "refer a friend, both get $10 credit" system that only checks "is this a new, valid-looking email/account" can be scripted to mass-create throwaway accounts (disposable email aliases, sequential test emails) purely to farm the referral bonus at scale, turning a marketing feature into a direct financial-abuse vector.
--> Testing approach: identify any endpoint with a limited-value payoff (coupon apply, referral claim, OTP verify) and simply script a burst of requests (varying only the guessed value) — no rate-limit response (`429`) or CAPTCHA challenge after a reasonable threshold is the finding itself.

--> Mitigation: rate-limit and eventually lock the specific endpoint (not just login), require a CAPTCHA after a handful of failed coupon attempts, and validate referral eligibility against durable signals (verified phone/payment method, device fingerprint) rather than just "is this a syntactically new email."

## Negative-Value / Integer-Boundary Logic Abuse

--> Occurs when an application accepts a numeric field without validating its SIGN or range, and the downstream logic was only ever designed with positive values in mind.
--> Worked example: a "return quantity" field on an order-return form that, on submission, CREDITS the account (or the customer's wallet) proportionally to quantity returned.

```http
POST /api/orders/8841/return HTTP/1.1
Host: shop.example.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...

{
  "orderId": 8841,
  "returnQuantity": -1
}
```

--> If the server computes `refund_amount = unit_price * returnQuantity` without ever checking `returnQuantity > 0`, a negative quantity flips the sign of the calculation — instead of debiting for a returned item, it CREDITS the account for a phantom "negative return," or in some observed real-world variants, decrements the order's shipped-quantity counter below zero, corrupting downstream inventory logic.
--> The same class shows up as integer-boundary abuse elsewhere: a "gift $X to a friend" field accepting a negative number to pull money the other direction, or a loop counter/array index driven by unvalidated user input hitting an unexpected boundary the developer never tested (0, -1, `INT_MAX`).

--> Mitigation: validate the SIGN and RANGE of every numeric field server-side against what the business rule actually permits (`returnQuantity` must be a positive integer no greater than the quantity originally ordered) — never assume a form's client-side `min="0"` HTML attribute is enforced anywhere except in a compliant browser UI, which an attacker simply isn't using.

## Methodology for Finding Business Logic Bugs

--> Unlike injection hunting (throw a payload list at every parameter), business logic testing is inherently manual and requires building a mental model of the app first.

1. **Map the entire intended happy-path workflow** before touching anything adversarial — every step of checkout, onboarding, a support-ticket lifecycle, a review-and-approval flow — including every field involved at each step and what value/state it's supposed to hold at that point.
2. For every step in that map, systematically ask:
   - **Skip** — can I call a LATER step's endpoint directly, without ever completing the steps before it?
   - **Repeat / reorder** — can I call this step twice, or call steps out of their intended order?
   - **Boundary/negative** — what happens at 0, negative numbers, decimals where an integer is expected, or an absurdly large value?
   - **Concurrency** — what happens if I fire many identical or near-identical requests for this exact step simultaneously?
   - **Substitute identity** — what happens if I use another (real or test) user's ID/token at this exact step, rather than my own?
3. Treat every "the UI won't let you do that" moment as a hypothesis to test directly against the API, not as a real restriction — the UI enforcing something is never evidence the server does too.
4. Prioritize workflows involving money, credits/points, or a limited/scarce resource (coupons, inventory, referral bonuses) — these are where a logic gap converts directly into quantifiable, easy-to-articulate impact, which is exactly what makes them attractive, high-severity bug bounty findings (see note 16's report-writing guidance on making impact concrete).

## Business Logic vs Technical Vulnerability Classes — Quick Comparison

| Aspect | Injection/XSS/IDOR (notes 04/25/26) | Business Logic |
|---|---|---|
| Root cause | Unsafe handling of input syntax/identity | Gap in workflow rules/assumptions |
| Code correctness | Contains an actual coding defect | Code runs exactly as designed |
| Automated scanner detection | Often detectable (error strings, reflected payloads, timing) | Essentially never detectable automatically |
| Finding it requires | Payload lists, fuzzing | Understanding the intended workflow deeply |
| Typical examples | `' OR 1=1`, `<script>`, `/orders/1002` | $0.01 checkout, skip-payment, coupon race |

--> With injection/access-control (notes 04/25) and the methodology/reporting layer (note 16) covered, business logic testing is the piece that turns "I ran the standard checklist" into genuinely novel, high-severity findings on a target that's already been scanned to death by everyone else.
