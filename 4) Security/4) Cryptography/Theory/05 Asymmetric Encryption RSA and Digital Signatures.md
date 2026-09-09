### Asymmetric Encryption - RSA and Digital Signatures

--> Asymmetric (public-key) cryptography uses a mathematically linked PAIR of keys instead of one shared secret. This single idea solves two huge problems that symmetric encryption alone cannot: securely exchanging keys over an insecure channel, and proving WHO sent a message.

## Public/Private Key Pair Concept

--> A public key is meant to be handed out to literally anyone — post it on your website, print it on a business card, it doesn't matter.
--> A private key must NEVER leave your possession. The security of the entire system rests on this one key staying secret.
--> The two keys are mathematically related in a special way: anything encrypted with the public key can ONLY be decrypted with the matching private key, and anything signed with the private key can be verified by anyone using the matching public key.

--> Two very different use cases fall out of this:
1. Encryption for confidentiality – Anyone can ENCRYPT a message to you using your public key; only YOU can decrypt it with your private key.
2. Digital signatures for authenticity – Only YOU can SIGN a message using your private key; anyone can VERIFY that signature using your public key.

## RSA at a Conceptual Level

--> RSA's security is built on a simple asymmetry: multiplying two large prime numbers together is fast, but factoring the resulting large number back into its original two primes is (as far as anyone currently knows) extremely slow for sufficiently large numbers.
--> The public key consists of a modulus `n` (the product of two huge secret primes `p` and `q`) and a public exponent `e` (almost always the small, fixed number 65537 in practice).
--> The private key consists of that same modulus `n` and a private exponent `d`, which is mathematically derived from `p`, `q`, and `e` in such a way that encrypting with `(n, e)` and decrypting with `(n, d)` perfectly undo each other.
--> Anyone who could factor `n` back into `p` and `q` could reconstruct the private exponent `d` and break the whole scheme — this is why key sizes matter so much (2048-bit or 4096-bit `n` values, made from correspondingly huge primes, are currently considered infeasible to factor).
--> You never construct RSA keys by hand in practice — a library handles the prime generation and math; understanding the "why it's secure" (factoring is hard) is enough at this level.

## RSA Key Generation

```python
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization

private_key = rsa.generate_private_key(
    public_exponent=65537,   # the near-universal standard choice for `e`
    key_size=2048,           # size of the modulus `n`, in bits
)
public_key = private_key.public_key()

# serialize to PEM so keys can be stored in files / sent over the network
private_pem = private_key.private_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PrivateFormat.PKCS8,
    encryption_algorithm=serialization.NoEncryption(),
)
public_pem = public_key.public_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PublicFormat.SubjectPublicKeyInfo,
)

print(private_pem.decode()[:32], "...")   # -----BEGIN PRIVATE KEY----- ...
print(public_pem.decode()[:32], "...")    # -----BEGIN PUBLIC KEY----- ...
```

## Encrypt / Decrypt with RSA

--> RSA can only directly encrypt data SMALLER than its key size (roughly 190 bytes with a 2048-bit key and OAEP padding) — this is why in practice RSA is used to encrypt a small symmetric AES key, not entire messages/files (this pattern is called "hybrid encryption" and is exactly what TLS does, see file 06).

```python
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import hashes

message = b"the launch code is 4821"

ciphertext = public_key.encrypt(
    message,
    padding.OAEP(
        mgf=padding.MGF1(algorithm=hashes.SHA256()),
        algorithm=hashes.SHA256(),
        label=None,
    ),
)
print(ciphertext[:16])   # b'\xa1\x9c\x02...' - unreadable, only decryptable with the matching private key

plaintext = private_key.decrypt(
    ciphertext,
    padding.OAEP(
        mgf=padding.MGF1(algorithm=hashes.SHA256()),
        algorithm=hashes.SHA256(),
        label=None,
    ),
)
print(plaintext)   # b'the launch code is 4821'
```

--> OAEP (Optimal Asymmetric Encryption Padding) adds randomness into the padding scheme so encrypting the SAME message twice produces DIFFERENT ciphertext each time — necessary because raw/"textbook" RSA without proper padding is deterministic and insecure (identical plaintexts would produce identical ciphertexts, leaking information, similar in spirit to the ECB problem in file 03).

## Digital Signatures — Authenticity and Integrity

--> A digital signature answers two questions at once: "did this message really come from the claimed sender?" (authenticity) and "was this message altered in transit?" (integrity). Neither plain encryption nor plain hashing alone gives you both.
--> The process: the sender hashes the message, then encrypts (signs) that hash using their PRIVATE key. Anyone can then hash the message themselves, decrypt (verify) the signature using the sender's PUBLIC key, and check the two hashes match.
--> Why this proves authenticity: only the private key holder could have produced a signature that the matching public key successfully verifies. Why it proves integrity: if even one byte of the message changes, the recomputed hash won't match the one embedded in the signature, and verification fails.
--> Note the key usage is FLIPPED compared to encryption: for confidentiality you encrypt with the PUBLIC key (only the private key owner can read it); for signatures you sign with the PRIVATE key (anyone with the public key can confirm it was really you).

```python
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.exceptions import InvalidSignature

message = b"I authorize this $10,000 wire transfer"

# sign with the PRIVATE key - only the real owner of this key pair can produce a valid signature
signature = private_key.sign(
    message,
    padding.PSS(
        mgf=padding.MGF1(hashes.SHA256()),
        salt_length=padding.PSS.MAX_LENGTH,
    ),
    hashes.SHA256(),
)
print(signature[:16])   # b'\x4f\x9d...' - bound to both this exact message and this exact private key

# verify with the PUBLIC key - anyone can do this, no secret needed
try:
    public_key.verify(
        signature,
        message,
        padding.PSS(
            mgf=padding.MGF1(hashes.SHA256()),
            salt_length=padding.PSS.MAX_LENGTH,
        ),
        hashes.SHA256(),
    )
    print("Signature valid - message is authentic and untampered")
except InvalidSignature:
    print("Signature invalid - reject this message")


# tampering proof: change even one character and verification fails
tampered_message = b"I authorize this $99,999 wire transfer"
try:
    public_key.verify(
        signature,
        tampered_message,
        padding.PSS(mgf=padding.MGF1(hashes.SHA256()), salt_length=padding.PSS.MAX_LENGTH),
        hashes.SHA256(),
    )
    print("Signature valid")
except InvalidSignature:
    print("Signature invalid - message was altered")   # this branch runs
```

--> Real-world usage of this exact pattern: software update signing (so your OS only installs updates signed by the real vendor), TLS certificates (the CA signs the certificate with ITS private key, see file 06), JWT tokens signed with RS256, and code commit signing (`git commit -S`).
--> Digital signatures are NOT encryption — the message itself in the example above was sent in plaintext; only the hash of it got signed. If you also need confidentiality, you encrypt separately (or use a combined scheme) on top of signing.
