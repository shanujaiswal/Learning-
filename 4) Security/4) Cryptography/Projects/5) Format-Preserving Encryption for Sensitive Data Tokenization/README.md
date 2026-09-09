# Format-Preserving Encryption for Sensitive Data Tokenization

## Real-world scenario

A payments company stores credit card numbers (PANs) and Social Security
Numbers in its database. Dozens of downstream systems -- analytics, fraud
scoring, customer support tools, legacy `CHAR(16)`/`CHAR(9)` database columns
-- all expect these fields to *look* like a real 16-digit card number or a
real 9-digit SSN. If you just AES-encrypt the value, you get a fixed-size
blob of random-looking bytes that breaks every one of those systems' format
validation, column widths, and display logic.

**Format-Preserving Encryption (FPE)** solves this: it encrypts the value
into *another value of the exact same shape* -- still 16 digits, still
"looks like" a valid card number -- while being just as cryptographically
reversible as normal encryption, but only for someone holding the key. This
is exactly what real PCI-DSS-compliant payment systems do (NIST standardized
two such constructions, **FF1** and **FF3-1**, both built on AES) to shrink
the number of systems that ever need to touch a *real* card number, without
rewriting every downstream system's schema or validation code.

This project builds that construction from scratch (an HMAC-SHA256-driven
Feistel network, architecturally identical to how FF1/FF3-1 use AES), wraps
it in a small tokenization service, and simulates a token vault -- then
proves the whole pipeline is exactly reversible with the key and reveals
nothing without it.

```
Original card number:  4532015112830366
FPE token (fake):      7830776117781328    <-- still 16 digits, still digit-only
Detokenized back:      4532015112830366    <-- EXACT match, only possible with the key
```

## Architecture

| Module | Role in this project | Real-world equivalent |
|---|---|---|
| `feistel_fpe.py` | From-scratch Feistel-network FPE primitive: splits digits into two halves, runs HMAC-SHA256-driven rounds, produces a same-length/same-format ciphertext with an exact inverse. | NIST **FF1 / FF3-1** format-preserving encryption modes, as used in real PCI-DSS card/SSN tokenization -- same Feistel-over-a-PRF architecture, AES swapped for HMAC-SHA256 here. |
| `tokenization_service.py` | Wraps the raw FPE primitive with real data shapes: `tokenize_card_number`/`detokenize_card_number` (16 digits), `tokenize_ssn`/`detokenize_ssn` (9 digits); holds a securely generated key. | A payment processor's internal **tokenization/encryption service** (the component behind, e.g., Stripe's or Braintree's "token vault" APIs) that everything else in the company calls instead of ever handling raw card data. |
| `token_vault.py` | Tracks which tokens have been issued (audit trail) without ever storing a reverse mapping or the FPE key; demonstrates that the stored token list alone can't be reversed. | The **token vault / audit ledger** a PCI-DSS scope-reduction architecture keeps -- narrowly scoped, key-free, purely for issuance tracking and compliance logging. |
| `main.py` | Runs the end-to-end story: tokenize several cards/SSNs, show token vs. original, detokenize back, assert exact equality, simulate a keyless attacker. | A payment flow's **tokenize-on-ingest / detokenize-on-authorize** pipeline, plus an incident-response tabletop exercise ("attacker steals the token database -- what do they actually get?"). |

## How the FPE construction works

1. Split the digit string into two halves (`left`, `right`), with fixed
   lengths for the whole run. Odd-length inputs (like a 9-digit SSN) give
   the extra digit to the left half.
2. Run 10 Feistel rounds. On even rounds, update `right` using a PRF of
   `left`; on odd rounds, update `left` using a PRF of `right`:
   ```
   round i even:  right <- (right + F(i, left))  mod 10**len(right)
   round i odd:   left  <- (left  + F(i, right))  mod 10**len(left)
   F(i, x) = HMAC-SHA256(key, i || x)  mod 10**k
   ```
3. Concatenate the final `left + right` -- same length, digits only.

Decryption replays the rounds **in reverse order**, subtracting the same PRF
outputs instead of adding them. Because the PRF output for each round only
ever depends on the *untouched* half (which is still sitting, unmodified, in
the ciphertext when you walk backwards), this is exactly invertible without
needing the round function itself to be invertible -- the same reason any
Feistel cipher (including DES, and including FF1/FF3-1) is invertible
regardless of how complex its round function is.

## Run it

```bash
python main.py
```

No dependencies beyond the Python standard library (`hmac`, `hashlib`,
`secrets`, `dataclasses`, `datetime`).

## Verified result (actual output)

```
==============================================================================
STEP 2 -- Tokenize credit card numbers (16-digit PAN -> 16-digit token)
==============================================================================
  original: 4532015112830366   token: 7830776117781328   [same length & digits-only format: True]
  original: 5500005555555559   token: 7450762035297253   [same length & digits-only format: True]
  original: 4111111111111111   token: 9961442812313116   [same length & digits-only format: True]
  original: 4000000000000002   token: 3983618487835985   [same length & digits-only format: True]

==============================================================================
STEP 3 -- Tokenize SSNs (9-digit SSN -> 9-digit token, incl. leading zeros)
==============================================================================
  original: 078051120   token: 344788534   [same length & digits-only format: True]
  original: 219099999   token: 866103665   [same length & digits-only format: True]
  original: 001010001   token: 143228542   [same length & digits-only format: True]
  original: 000000001   token: 760559189   [same length & digits-only format: True]

==============================================================================
STEP 4 -- Detokenize everything back and verify EXACT equality
==============================================================================
  token: 7830776117781328   detokenized: 4532015112830366   original: 4532015112830366   [exact match: True]
  token: 7450762035297253   detokenized: 5500005555555559   original: 5500005555555559   [exact match: True]
  token: 9961442812313116   detokenized: 4111111111111111   original: 4111111111111111   [exact match: True]
  token: 3983618487835985   detokenized: 4000000000000002   original: 4000000000000002   [exact match: True]
  token: 344788534   detokenized: 078051120   original: 078051120   [exact match: True]
  token: 866103665   detokenized: 219099999   original: 219099999   [exact match: True]
  token: 143228542   detokenized: 001010001   original: 001010001   [exact match: True]
  token: 760559189   detokenized: 000000001   original: 000000001   [exact match: True]

All assertions passed -- every detokenized value is EXACTLY equal to its original,
including the leading-zero SSN edge cases ('001010001', '000000001').

==============================================================================
STEP 5 -- Simulate an attacker who steals ONLY the vault's token list
==============================================================================
Attacker exfiltrates the vault's `tokens_issued` table: 8 tokens.
The attacker does NOT have the FPE key (the vault never stored it).

  Token '7830776117781328' is a well-formed 16-digit value, but it cannot be decrypted without the FPE key. No original digits can be recovered from the token alone.
  Token '7450762035297253' is a well-formed 16-digit value, but it cannot be decrypted without the FPE key. No original digits can be recovered from the token alone.
  Token '9961442812313116' is a well-formed 16-digit value, but it cannot be decrypted without the FPE key. No original digits can be recovered from the token alone.
  ...

vault repr() for logging safety check -> TokenVault(tokens_issued=8, key=<not stored in vault>)
(Notice: no key material, no plaintext originals, ever appear in that repr.)
```

Additionally verified separately (not part of `main.py`'s default run): tokenizing
the same PAN under two different, independently generated keys and detokenizing
with the *wrong* key produces a different 16-digit garbage value, never the
original -- confirming reversal genuinely depends on possessing the exact key:

```
token: 1315419352254583
wrong-key detokenize:   8035992613163384   (should NOT equal original)
correct-key detokenize: 4532015112830366
```

## Things to try changing

- **Break the leading-zero handling**: change `_add_digits`/`_sub_digits` in
  `feistel_fpe.py` to use plain `str(int(...))` without `.zfill(len(a))`, and
  watch the `001010001` / `000000001` SSN test cases start failing their
  round-trip assertions -- a good illustration of why real FPE
  implementations are so careful about fixed-width, zero-padded arithmetic.
- **Lower `DEFAULT_ROUNDS`** in `feistel_fpe.py` to 1 or 2 and observe (by
  eye) that the ciphertext still changes only a couple of digits relative to
  a 1-round or single-active-half change in the input -- a concrete way to
  see why NIST recommends a minimum of 8 rounds for full diffusion.
  (The round-trip will still succeed at any round count >= 1 -- reversibility
  doesn't depend on round count, only *security/diffusion* does.)
- **Swap the round PRF** from HMAC-SHA256 to HMAC-SHA1 or HMAC-SHA3-256 (all
  in stdlib `hashlib`) and confirm the whole thing still round-trips exactly
  -- demonstrating the Feistel network's invertibility is independent of
  which PRF drives it.
- **Try a same-key collision check**: tokenize two *different* 16-digit PANs
  and print whether their tokens share any digit in the same position more
  often than chance would suggest -- an intuition-builder for why a single
  input-space brute-force isn't practical here (10^16 possible PANs) even
  though the theory file's caveat about *small* input spaces (e.g. a
  1-digit field) still applies.
- **Point it at a different field shape**: add a `tokenize_expiry_mm_yy`
  helper for a 4-digit `MMYY` field, and see the same primitive handle it
  with no changes to `feistel_fpe.py` at all -- proving the Feistel core is
  genuinely alphabet/length-agnostic, only the wrapper in
  `tokenization_service.py` is data-shape-specific.

## Honest caveats (read before treating this as production-grade)

- This is a **teaching/demo implementation** of the FF1/FF3-1 *architecture*
  (Feistel network + PRF round function, radix-10 alphabet), not a
  byte-for-byte, NIST SP 800-38G-certified implementation. Real regulated
  deployments should use a vetted, certified FPE library, not this code.
  Notably, this implementation does not include FF1's tweak/domain-separation
  parameter handling, exact bit-level PRF construction, or the specific
  numeric encoding NIST mandates -- it captures the same *shape* of
  construction with a simpler, from-stdlib round function.
- As the theory file notes: FPE's reversibility is itself an attack surface
  -- if the key leaks, EVERY token ever issued becomes reversible instantly.
  Compare this to a classic random-token vault, where a leaked *token* alone
  is cryptographically meaningless without the vault's mapping table.
- Small input spaces (e.g. a 1-digit or 2-digit field) are inherently
  vulnerable to brute-force guessing of the entire space, no matter how
  strong the underlying round function is -- format preservation sometimes
  means preserving a *small* space. 16-digit PANs (10^16 possibilities) and
  9-digit SSNs (10^9 possibilities) are large enough that this isn't a
  practical concern here, but a tokenized single "yes/no" flag would be.
