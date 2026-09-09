### HTTP Request Smuggling

--> ⚠️ LEGAL / ETHICAL REMINDER: Only test the techniques below against DVWA, PortSwigger Web Security Academy's dedicated request-smuggling labs, TryHackMe/HackTheBox web boxes, or an authorized client app explicitly in scope. Request smuggling deliberately desyncs shared infrastructure — firing it at a real-world site you don't own can corrupt other real users' sessions, which is both illegal and genuinely harmful.

--> Request smuggling exploits ambiguity in where one HTTP request ENDS and the next BEGINS on a connection that is shared between a front-end component (load balancer, reverse proxy, CDN) and a back-end application server. Most production stacks reuse a small pool of TCP connections between front-end and back-end for performance — many different users' requests get pipelined one after another down the SAME back-end connection. If the front-end and back-end disagree about a single request's boundary, an attacker can smuggle a hidden, second "request" inside their own request body. The back-end treats the leftover bytes as the START of the NEXT request on that connection — which really belongs to whatever legitimate user's request follows. That victim's request gets concatenated with attacker-controlled bytes, and the response meant for the victim can be redirected to the attacker, or the victim's request can be rewritten before the back-end ever sees it.

--> The mechanical root cause: HTTP/1.1 allows a request body's length to be declared TWO different ways, and if both are present and the front-end/back-end trust DIFFERENT ones, they disagree about the body's end.
1. `Content-Length: N` — an exact byte count for the body.
2. `Transfer-Encoding: chunked` — no upfront length; the body is a series of `<hex-length>\r\n<chunk-data>\r\n` chunks, terminated by a zero-length chunk (`0\r\n\r\n`).

--> The HTTP spec says `Transfer-Encoding` should take priority when both headers are present, but not every server implements this correctly, and some legacy/older systems don't fully support chunked encoding at all — that inconsistency is exactly what an attacker exploits.

## CL.TE — front-end trusts Content-Length, back-end trusts Transfer-Encoding

--> Front-end reads `Content-Length` and forwards exactly that many bytes as "the request." Back-end instead parses the body as chunked and stops as soon as it sees the terminating `0` chunk — which the attacker places EARLIER than the `Content-Length` claims, leaving trailing bytes unconsumed by the back-end.
```text
POST /search HTTP/1.1
Host: vulnerable-site.com
Content-Length: 13
Transfer-Encoding: chunked

0

SMUGGLED
```
--> Byte-by-byte: `Content-Length: 13` tells the front-end "read exactly 13 bytes of body," which covers `0\r\n\r\nSMUGGLED` in full — front-end forwards the whole thing as ONE request. The back-end, trusting `Transfer-Encoding: chunked` instead, reads the chunk `0\r\n\r\n` and immediately considers the body FINISHED (a zero-length chunk is the chunked-encoding terminator) — it never treats `SMUGGLED` as part of THIS request's body at all. Those leftover bytes (`SMUGGLED`) sit in the TCP stream and get prepended to whatever request arrives next on that reused connection, silently corrupting it.
--> A real attack replaces the literal word `SMUGGLED` with a second, fully-formed HTTP request line, e.g.:
```text
POST /search HTTP/1.1
Host: vulnerable-site.com
Content-Length: 53
Transfer-Encoding: chunked

0

GET /admin HTTP/1.1
Host: vulnerable-site.com
```
--> When the NEXT real user's request lands right after this on the shared connection, the back-end sees the attacker's `GET /admin` line already sitting there and can end up merging the victim's headers/cookies onto the attacker's smuggled request, or serving the attacker's smuggled response first — either way, the connection's shared state has been poisoned.

## TE.CL — front-end trusts Transfer-Encoding, back-end trusts Content-Length

--> The reverse mismatch: front-end parses the body as chunked and forwards the fully-reassembled request; back-end ignores `Transfer-Encoding` and instead reads only `Content-Length` bytes, leaving the attacker's real chunked terminator UNPROCESSED as leftover data.
```text
POST /search HTTP/1.1
Host: vulnerable-site.com
Content-Length: 4
Transfer-Encoding: chunked

5c
GET /admin HTTP/1.1
Host: vulnerable-site.com

0

```
--> The front-end honors `Transfer-Encoding: chunked`, reads the `5c`-length chunk (which contains a whole embedded `GET /admin` request) followed by the `0` terminator, and forwards the ENTIRE reconstructed body to the back-end as one request. The back-end instead trusts `Content-Length: 4`, reads only the first 4 bytes of the body (`5c\r\n` — the chunk-size line), and considers the request finished right there. Everything after those 4 bytes — the smuggled `GET /admin HTTP/1.1...` block — is left sitting unconsumed on the connection and gets interpreted as the start of the next request.

## TE.TE — obfuscating the Transfer-Encoding header

--> Both front-end and back-end support chunked encoding, but one of them can be tricked into NOT recognizing the `Transfer-Encoding` header via subtle obfuscation, so effectively it falls back to CL.TE or TE.CL behavior against just that one component.
```text
Transfer-Encoding: chunked
Transfer-Encoding: cow
```
```text
Transfer-Encoding : chunked      (extra space before the colon)
Transfer-Encoding: chunked\r\n\t (trailing/injected whitespace)
```
--> One server might normalize this and still see valid chunked encoding, while the other treats the header line as malformed and ignores it entirely, falling back to `Content-Length` — reintroducing the same CL/TE disagreement seen above. Testing every combination of casing, duplicate headers, and whitespace against both front-end and back-end is standard smuggling recon.

## Real-world impact

--> ==> Bypassing front-end security controls: a WAF or auth check sitting at the front-end only inspects the "outer," well-formed request — the smuggled inner request never passes through it, letting an attacker reach back-end-only endpoints or filters that would otherwise block the payload.
--> ==> Hijacking another user's request: by carefully crafting the smuggled prefix to end mid-request, the attacker's payload gets APPENDED to the front of the next real user's request, letting the attacker capture that user's response (including session tokens) or inject headers into their session.
--> ==> Cache poisoning: smuggling a request that manipulates a cache key or an unkeyed input can poison the shared cache for every subsequent visitor — this is the desync-based route into the cache poisoning attacks covered in note 37.

## Detection methodology

--> Burp Suite ships a built-in HTTP Request Smuggling scanner (and the standalone "HTTP Request Smuggler" extension) that automates sending probe requests with conflicting `Content-Length`/`Transfer-Encoding` combinations and flags anomalous responses — this is the fastest first pass and should be run before manual work.
--> Manual differential/timing-based technique: send a deliberately ambiguous request where the back-end, if it is waiting for more body bytes than the front-end forwarded, will simply HANG until a timeout. A classic CL.TE timing probe:
```text
POST / HTTP/1.1
Host: vulnerable-site.com
Content-Length: 4
Transfer-Encoding: chunked

1
A
X
```
--> If the front-end forwards only the 4 declared bytes (`1\r\nA\r\n`) but the back-end is parsing chunked and expects a terminating `0` chunk that never arrives, the back-end will sit waiting for the rest of the body — a noticeably delayed response (versus an instant one) is strong evidence of a CL.TE desync, without needing a second connection or a victim at all.

## Mitigations

| Mitigation | Why it works |
|---|---|
| Normalize/reject ambiguous requests at the edge | If the front-end rejects any request containing BOTH `Content-Length` and `Transfer-Encoding`, the ambiguity that enables smuggling never reaches the back-end. |
| Use HTTP/2 end-to-end | HTTP/2 frames length explicitly at the protocol level (no `Content-Length`/chunked duality) — no boundary ambiguity to exploit, provided both hops actually speak HTTP/2 rather than downgrading to HTTP/1.1 internally. |
| Disable connection reuse between front-end and back-end | If every back-end request gets its own fresh connection, there is no "next request" for smuggled bytes to poison — this costs performance but eliminates the entire attack class. |
| Keep front-end and back-end HTTP parsers on the same implementation/config | Most real-world smuggling bugs come from mismatched parser behavior between two different products (e.g. an nginx front-end and a different app server) — consistent parsing removes the disagreement. |

--> This is a desync-class vulnerability at heart, and note 37 covers its closest sibling — web cache poisoning — including how smuggling itself can be used as a cache-poisoning delivery mechanism; also cross-reference note 04 (Security Misconfiguration/Broken Access Control, since smuggling is often used to bypass front-end access controls) and note 14 (Burp Suite, for the Repeater/Intruder workflow used to manually test these payloads).

--> With request smuggling and cache poisoning covered as the two big desync/caching-layer attack classes, note 38 moves to a completely different exploitation surface — Server-Side Template Injection.
