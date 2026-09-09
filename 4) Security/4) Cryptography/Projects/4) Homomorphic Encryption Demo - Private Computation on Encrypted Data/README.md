# Homomorphic Encryption Demo — Private Computation on Encrypted Data

A from-scratch, stdlib-only implementation of the **Paillier partially
homomorphic cryptosystem** (Pascal Paillier, 1999) — a real, historically
significant public-key scheme, not a toy substitution cipher. It demonstrates
the one property that makes Paillier famous:

```
Enc(a) * Enc(b)  mod n^2   ==   Enc(a + b)
```

Multiplying two ciphertexts is equivalent to adding their plaintexts — and
this holds with **no decryption key involved at any point**.

## Real-world scenario

Several clinics each hold one private patient reading (e.g. a lab value in
mg/dL). A cloud analytics vendor needs the **total across all clinics** for
a public-health statistic, but must never see any individual clinic's raw
number — that would be a serious privacy/compliance violation (HIPAA-style).

The solution:

1. A trusted **auditor** (key authority) generates a Paillier keypair and
   publishes only the *public* key.
2. Each clinic (client) encrypts its own private reading **locally**, using
   only the public key, and sends only the ciphertext to the cloud.
3. The cloud **homomorphically sums the ciphertexts** — plain modular
   multiplication — without ever decrypting anything, and without being
   able to.
4. Only the auditor, holding the private key, decrypts the **single final
   aggregate** — never any individual clinic's value.
5. The decrypted aggregate is checked, by assertion, against the real sum
   of the original plaintexts.

This is the textbook real-world use case for homomorphic encryption: a
single untrusted party (the cloud) computes blindly on data it can never
read, in contrast to secure multiparty computation, which instead spreads
trust across multiple interacting parties.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `paillier_cryptosystem.py` | From-scratch Paillier key generation (Miller-Rabin primality testing, prime generation, lambda/mu), encryption, decryption, homomorphic addition (ciphertext × ciphertext mod n²), homomorphic add-plaintext, and homomorphic scalar multiplication (ciphertext^k mod n²) | The core math underlying real PHE/FHE research and libraries such as IBM HELib or Microsoft SEAL — genuine applied cryptography, not a substitution cipher |
| `key_authority.py` | Generates the Paillier keypair once; keeps the private key locked away; hands out only the public key | A compliance auditor / statistics bureau / HSM-backed key custodian who is the sole party ever allowed to decrypt |
| `client_simulator.py` | Simulates multiple clients, each encrypting its own private numeric value locally before it ever leaves the client | Hospitals, bank branches, or IoT sensor endpoints that must never expose their raw readings upstream |
| `cloud_aggregator.py` | The untrusted "cloud" — receives only ciphertexts, combines them homomorphically into one ciphertext representing the sum, and is shown explicitly failing to extract any plaintext from what it holds | A privacy-preserving analytics cloud service (AWS/Azure/GCP-hosted pipeline) that computes on data it is contractually/technically unable to read |
| `main.py` | Orchestrates the full story end-to-end and asserts the decrypted homomorphic sum exactly equals the real plaintext sum | The overall protocol/workflow tying every party together |

## Run it

Requires only the Python standard library (`random`, `math`, `dataclasses`) — no `pip install` needed.

```bash
python main.py
```

Runtime is a few seconds — key generation uses 512-bit primes (≈1024-bit
modulus `n`), a genuine, non-trivial key size, kept modest purely so the
from-scratch Miller-Rabin prime search finishes quickly for a demo.

## Verified result

Actual output from a real run (`python main.py`), five clinics' private
readings — `118, 95, 142, 87, 133` — encrypted independently, aggregated
homomorphically by the cloud, then decrypted only by the key authority:

```
--- Step 5: Key Authority decrypts ONLY the final aggregated result ---
[KeyAuthority] Decrypted aggregate sum = 575

--- Step 6: Verification ---
Real sum of original private values (known here only for verification purposes; the cloud never had access to this): 575
Decrypted result of homomorphic aggregation:                          575

*** VERIFIED: decrypted homomorphic sum EXACTLY matches the real plaintext sum. ***

--- Step 7: Confirming the cloud's stored ciphertexts reveal nothing ---
  Clinic-A: real value =  118 | ciphertext (as int) has 616 digits | ciphertext == real value? False
  Clinic-B: real value =   95 | ciphertext (as int) has 616 digits | ciphertext == real value? False
  Clinic-C: real value =  142 | ciphertext (as int) has 616 digits | ciphertext == real value? False
  Clinic-D: real value =   87 | ciphertext (as int) has 615 digits | ciphertext == real value? False
  Clinic-E: real value =  133 | ciphertext (as int) has 616 digits | ciphertext == real value? False

All assertions passed. The cloud performed a real computation on real ciphertexts and never saw a single plaintext value, yet the final decrypted answer is exactly correct.
```

`118 + 95 + 142 + 87 + 133 = 575` — matches the decrypted homomorphic
aggregate exactly. `main.py` asserts this equality (and asserts that no
ciphertext ever equals its corresponding plaintext) rather than just
printing numbers that look right, so any future regression breaks the run
loudly instead of silently.

The cloud's `attempt_to_snoop()` step is also run every time: it shows the
cloud, holding only the public key `(n, g)` and the raw ciphertext integers,
trying a naive `ciphertext mod n` reduction and a re-encrypt-and-compare
brute-force guess — both fail to recover anything, both because the private
key `(lambda, mu)` is never available to it and because Paillier encryption
is randomized (a fresh blinding factor `r` per encryption means even a
correct guess wouldn't reproduce the same ciphertext bit-for-bit).

## Things to try changing

- **Different aggregate**: swap `private_readings` in `main.py` for
  salaries, sensor readings, vote tallies, or any other numeric scenario —
  the sum will still verify exactly.
- **Homomorphic scalar multiplication**: use
  `homomorphic_scalar_multiply(public_key, ciphertext, k)` from
  `paillier_cryptosystem.py` to compute an encrypted *average* by first
  summing, then "multiplying" by a constant `1/count` worked out via modular
  inverse — or simply demonstrate `Dec(c^3) == 3 * Dec(c)`.
- **Homomorphic add-plaintext**: use `homomorphic_add_plaintext` to add a
  known public constant (e.g. a processing fee, or a correction offset) to
  an encrypted value without ever decrypting it.
- **Key size**: change `key_bit_length` in `KeyAuthority(...)` (in
  `main.py`) — try 256 for a near-instant run, or 1024 for a much more
  realistic (but slower to generate) key size, and watch prime generation
  time scale.
- **More clients**: add many more entries to `private_readings` and confirm
  the homomorphic sum still matches — Paillier's ciphertext-multiplication
  trick composes over any number of ciphertexts, not just two.
- **Break the security property (educational only)**: try modifying
  `attempt_to_snoop` to actually factor `n` for a very small `key_bit_length`
  (e.g. 8 bits) and watch how, once `p` and `q` are recoverable, the whole
  scheme collapses — a hands-on feel for why key size matters.
