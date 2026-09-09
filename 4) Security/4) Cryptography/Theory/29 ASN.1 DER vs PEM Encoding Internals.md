### ASN.1, DER, and PEM Encoding Internals

--> An X.509 certificate (note 09) is structured data — issuer name, subject name, public key, validity dates, extensions, a signature — and a CA signs it, then every client in the world needs to hash and parse that exact same structure identically, byte for byte. That requires an unambiguous, formally defined way to turn "a set of structured fields" into "a specific sequence of bytes," with zero room for two implementations to disagree.
--> Three layers do this job, and they are commonly confused because they're always seen stacked together: **ASN.1** is the grammar (what fields exist and their types), **DER** is the binary serialization (the actual bytes), and **PEM** is a text wrapper around DER for contexts where raw binary is inconvenient (email, config files, terminals).

## ASN.1 — The Schema Language

--> ASN.1 (Abstract Syntax Notation One) is a language for describing the STRUCTURE of data, independent of how it's eventually encoded into bytes — similar in spirit to how a Protobuf `.proto` file or a JSON Schema describes shape without dictating the wire format.
--> Core building blocks relevant to certificates:
--> 1. **SEQUENCE** — an ordered list of fields, like a struct/record (a certificate itself is one big nested SEQUENCE).
--> 2. **SET** — an unordered collection (used rarely in certs; e.g. `RelativeDistinguishedName` components).
--> 3. **INTEGER** — arbitrary-precision signed integer (used for the serial number, and for RSA's `n`/`e`/`d` when embedded in a key structure).
--> 4. **OCTET STRING** — a raw byte blob with no further ASN.1 structure implied (e.g. a raw signature value, or a key wrapped inside another structure).
--> 5. **BIT STRING** — a sequence of bits, used specifically for the certificate's `subjectPublicKey` field and the final signature value.
--> 6. **OBJECT IDENTIFIER (OID)** — a globally unique dotted-number identifier for "what algorithm/attribute/extension is this."
--> The X.509 certificate's ENTIRE shape (every field in note 09's structure list) is literally just an ASN.1 module definition published in RFC 5280 — ASN.1 is the grammar, X.509 is one particular schema written in that grammar.

### OIDs — Why a Dotted Number Instead of a String

--> An OID like `1.2.840.113549.1.1.11` looks arbitrary but is a globally administered, hierarchical namespace — each dot descends one level of a tree registered by international standards bodies. Decoded: `1.2.840.113549` is RSA Data Security Inc.'s registered arc, `.1.1` is PKCS#1, and `.11` is `sha256WithRSAEncryption` specifically.
--> Why OIDs instead of just a string like `"sha256WithRSA"`: an OID is a permanent, collision-free, language/encoding-neutral identifier that never needs redefinition or namespacing — any organization can request its own arc from an assigning body and mint identifiers under it without coordinating with every other user of ASN.1 on Earth.
--> This is exactly what gives certificates **algorithm agility**: the `signatureAlgorithm` field is just an OID lookup, so a client that doesn't recognize a particular OID can cleanly reject/ignore it, and adding a new algorithm (e.g. `ecdsa-with-SHA384`, or a future post-quantum scheme per note 13) never requires changing the certificate FORMAT — only registering a new OID and teaching parsers to recognize it.

## DER — The Actual Bytes (Tag-Length-Value)

--> ASN.1 defines structure; it does NOT by itself define bytes. Multiple encoding rule sets exist — **BER** (Basic Encoding Rules, flexible, allows several valid encodings of the same logical value) and **DER** (Distinguished Encoding Rules, a strict subset of BER that allows EXACTLY ONE valid encoding per value).
--> Every DER-encoded element follows **TLV**: **T**ag (what type this is), **L**ength (how many bytes the value occupies), **V**alue (the actual content bytes, which may themselves be nested TLV elements for composite types like SEQUENCE).

```
Worked example: DER encoding of a small ASN.1 structure —
    SEQUENCE { INTEGER 5 }

Raw bytes: 30 03 02 01 05

Byte-by-byte breakdown:
  30          <- Tag byte for SEQUENCE (universal class, constructed,
                 tag number 0x10). The 0x20 bit being set marks
                 "constructed" (contains nested TLVs, not raw data).
  03          <- Length byte: the VALUE of this SEQUENCE is 3 bytes long
                 (everything that follows, up through the end of the
                 nested INTEGER's own TLV).
  02 01 05    <- This 3-byte blob IS the nested INTEGER TLV:
       02     <-   Tag byte for INTEGER (universal class, primitive,
                    tag number 0x02)
       01     <-   Length byte: this INTEGER's value is 1 byte long
       05     <-   Value byte: the integer 5 itself

So: outer SEQUENCE tag+length wraps one inner INTEGER tag+length+value.
A real certificate is exactly this pattern nested dozens of levels deep —
SEQUENCE containing SEQUENCEs containing INTEGERs, OIDs, BIT STRINGs, etc.
```

```
Longer-length example: if a value's content is >= 128 bytes, the length
byte can't hold it in 7 bits, so DER switches to "long form":

  82 01 F4    <- length bytes meaning:
    82        <-   high bit set = long form; low 7 bits (0x02) =
                   "2 more bytes follow encoding the actual length"
       01 F4  <-   those 2 bytes, read as an integer: 0x01F4 = 500
                   -> this value is 500 bytes long

This is why a small integer costs 3 bytes of overhead (tag+len+value)
but a 2048-bit RSA modulus (256 bytes) costs a tag byte, a "long form,
2 length bytes follow" marker, 2 length bytes, then the 256 value bytes.
```

### Why DER Specifically (Not General BER) for Certificates

--> BER allows multiple valid encodings of the same logical value — e.g. a length can be encoded in short form OR an equivalent long form even when unnecessary, and constructed types can use an "indefinite length" marker instead of stating a length upfront. This flexibility is fine for general-purpose data interchange but is a disaster for anything that gets HASHED and SIGNED.
--> A digital signature (note 05) is computed over a HASH of the exact bytes of the "to-be-signed" structure. If the same logical certificate content could be validly represented as two different byte strings (as BER permits), then the CA's signature would only verify against the ONE specific byte string it actually signed — and worse, an attacker could potentially re-encode a certificate into an alternate valid-but-different byte form and ask "does this hash the same?" opening the door to encoding-confusion attacks.
--> DER eliminates this entirely by mandating exactly one canonical byte representation per logical value: shortest-possible length encoding, no indefinite lengths, SET elements sorted into a defined order, boolean `TRUE` encoded as exactly `0xFF` (not any other nonzero byte BER would permit). "Canonical" here means: parse a DER structure, re-encode it, and you get byte-for-byte the same output — a property BER does not guarantee.
--> This is precisely why certificates, and the signature computed over their `tbsCertificate` ("to be signed" portion, referenced in note 09's worked example), MUST use DER — canonical encoding is a prerequisite for hash-then-sign to even be a coherent operation.

## PEM — Base64-Wrapped DER for Text Transport

--> PEM (Privacy-Enhanced Mail) is, despite the name, unrelated to modern secure email — it originated from a 1990s IETF effort to secure email that never gained traction for that purpose, but its text-safe encoding convention outlived the original use case entirely and is now the near-universal way certs/keys are stored and pasted.
--> A PEM file is simply: take the raw DER bytes, base64-encode them, wrap in a `-----BEGIN <LABEL>-----` / `-----END <LABEL>-----` header/footer pair, and line-wrap at 64 characters. That's the entire format — no additional structure beyond the base64 blob itself.
--> Why this exists at all: raw binary DER breaks in contexts designed for text — 7-bit-only mail transports historically, copy-pasting into a terminal or a YAML/JSON config value, version control diffing. Base64 makes the payload safe to embed anywhere plain ASCII text is safe.

```
PEM (what you paste/store)              DER (what's actually hashed & signed)
------------------------------          --------------------------------------
-----BEGIN CERTIFICATE-----             30 82 02 3A 30 82 01 A2 A0 03 02 01 02
MIICOjCCAaKgAwIBAgIJAJ...    <----->    02 09 00 A1 B2 C3 D4 E5 F6 30 0D 06 09
...(base64 of the DER bytes)...         2A 86 48 86 F7 0D 01 01 0B 05 00 30 ...
-----END CERTIFICATE-----               (the base64 above is literally just
                                          this hex, run through base64 —
                                          decode the PEM body and you get
                                          exactly these bytes back)
```

```bash
# Practical debugging workflow: PEM is what you paste, DER is what's
# actually hashed and signed, ASN.1 is the grammar defining what's inside.

# 1. Strip the PEM wrapper and base64-decode to get raw DER bytes
openssl base64 -d -in cert.pem -out cert.der
# (or: openssl x509 -in cert.pem -outform DER -out cert.der)

# 2. Parse the ASN.1 grammar of those DER bytes directly — shows every
#    tag/length/value node in the structure, useful when a cert is
#    misbehaving in a way -text doesn't surface (e.g. a malformed
#    extension, or unexpected trailing bytes some parsers accept and
#    others reject)
openssl asn1parse -in cert.der -inform DER

# 3. The friendlier human-readable decode most people reach for day to
#    day — internally openssl is doing exactly the DER parse above, then
#    pretty-printing the OIDs and fields it recognizes
openssl x509 -in cert.pem -text -noout

# 4. Confirm the OID for the signature algorithm by eye
openssl x509 -in cert.pem -noout -text | grep -A1 "Signature Algorithm"
#   Signature Algorithm: sha256WithRSAEncryption
#   -> internally this line IS just OID 1.2.840.113549.1.1.11, resolved
#      to a human-readable name from openssl's built-in OID table
```

--> This three-layer mental model resolves a huge share of real debugging confusion: "why doesn't this signature verify" is almost always an ENCODING question (was DER re-serialized non-canonically somewhere, e.g. by a library that re-wrapped a cert as BER), not a math question — always drop down to `openssl asn1parse` to see the literal TLV structure before assuming the cryptography itself is wrong.

## Common PEM Header Types

| PEM Header | ASN.1 / DER structure it wraps | Typical use |
|---|---|---|
| `-----BEGIN CERTIFICATE-----` | X.509 `Certificate` SEQUENCE (per RFC 5280) | A signed cert — leaf, intermediate, or root (note 09) |
| `-----BEGIN CERTIFICATE REQUEST-----` | PKCS#10 `CertificationRequest` SEQUENCE | A CSR — unsigned-by-a-CA subject info + public key, submitted for signing |
| `-----BEGIN PRIVATE KEY-----` | PKCS#8 `PrivateKeyInfo` SEQUENCE (algorithm-agnostic wrapper) | An unencrypted private key (RSA, EC, etc. — the OID inside says which) |
| `-----BEGIN RSA PRIVATE KEY-----` | PKCS#1 `RSAPrivateKey` SEQUENCE (RSA-specific, older format) | Legacy RSA-only private key encoding, predates PKCS#8's generic wrapper |
| `-----BEGIN PUBLIC KEY-----` | `SubjectPublicKeyInfo` SEQUENCE | A standalone public key (algorithm OID + BIT STRING key data) |
| `-----BEGIN ENCRYPTED PRIVATE KEY-----` | PKCS#8 `EncryptedPrivateKeyInfo` SEQUENCE | A password-encrypted private key — the DER payload is ciphertext, not a plain key structure, until decrypted |

## Pitfalls

--> 1. **Assuming PEM headers guarantee content** — a `-----BEGIN CERTIFICATE-----` block is just a label; nothing stops a file from being mislabeled, and some tools happily attempt to parse the DER regardless of the header text. Always confirm with `openssl asn1parse`/`-text` rather than trusting the header string alone.
--> 2. **BER vs DER confusion in hand-rolled parsers** — a parser that's lenient about BER's flexible length encodings when generating (not just reading) certificate data can produce non-canonical bytes that some strict verifiers reject outright — this is a real historical source of cross-implementation interop bugs.
--> 3. **Concatenated PEM blocks** — a single `.pem` file commonly contains MULTIPLE `BEGIN/END` blocks back to back (e.g. leaf + intermediate chain, as in note 09's worked example) — each block is independently base64-decoded to its own separate DER structure; don't assume one file equals one certificate.
--> 4. **Forgetting line-ending/whitespace sensitivity** — base64 itself tolerates padding and line wrap variations, but a PEM file with the wrong line endings (CRLF vs LF) pasted into some strict parsers can fail decode; when in doubt, re-canonicalize with `openssl x509 -in cert.pem -out cert.pem` to normalize.
--> 5. **Treating the OID name as authoritative without checking the actual dotted number** — OID-to-name tables are just local lookup conventions; two different tools can print different human-readable labels for algorithms, or a not-yet-registered OID a tool doesn't recognize, so when in doubt read the raw dotted OID via `asn1parse`, not the pretty-printed name.
--> Next logical step: once a certificate's raw ASN.1/DER structure is comfortable, the natural follow-on is how these encoded structures get bundled and transported at scale in real handshakes and revocation flows (OCSP responses, CRLs, and CMS/PKCS#7 signed-data envelopes) — all of which are, unsurprisingly, more ASN.1/DER structures wrapped in the same TLV discipline covered here.
