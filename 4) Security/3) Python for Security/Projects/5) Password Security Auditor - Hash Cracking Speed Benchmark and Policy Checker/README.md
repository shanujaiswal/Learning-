# Password Security Auditor — Hash-Cracking Speed Benchmark & Policy Checker

> **AUTHORIZED USE ONLY.** Every hash in this project is generated locally, from
> passwords this project itself created, purely to measure and demonstrate a
> real speed difference. Never point this kind of tool at credentials you do
> not own or are not explicitly authorized to test.

## Real-world scenario

You're a security engineer asked to evaluate a company's password storage
scheme and password policy before a compliance review. Two questions come up
constantly in this job, and both deserve a *measured* answer instead of a
hand-wave:

1. **"We hash our passwords, so we're fine, right?"** — Not necessarily. A raw
   `SHA-256(password)` is technically "hashing," but it's a **fast**,
   general-purpose hash designed to process gigabytes per second. If the
   password database leaks, an attacker can try enormous numbers of guesses
   per second against it. A **slow**, purpose-built KDF like PBKDF2 (or
   bcrypt/scrypt/Argon2) with a high work factor turns every single guess into
   hundreds of thousands of hash rounds, throttling the attacker to a handful
   of guesses per second on the same hardware. This project hashes the *same*
   test passwords both ways and dictionary-attacks both, so the difference is
   an actual measured number, not a claim.

2. **"Are our users' passwords actually any good?"** — A policy audit checks
   real (synthetic, here) account passwords against length, character-class,
   common-password-blocklist, and username-derivation rules, and reports
   which accounts would fail a real compliance check and why.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `hash_schemes.py` | Implements a fast/weak scheme (raw, unsalted SHA-256) and a slow/proper scheme (salted PBKDF2-HMAC-SHA256, high iteration count) with matching hash/verify interfaces | Why real systems use bcrypt/scrypt/Argon2 (or PBKDF2 with a high work factor) instead of a raw fast hash for password storage |
| `common_password_list.py` | A small list of common/breached passwords, used both as dictionary-attack ammunition and as a policy blocklist | A trimmed-down `rockyou.txt` / the "Pwned Passwords" list behind Have I Been Pwned |
| `cracking_benchmark.py` | Hashes the same target password with both schemes, then times a real dictionary attack against each, reporting elapsed time and guesses/sec | A hashcat / John the Ripper speed benchmark for a given hash mode |
| `password_policy_checker.py` | Checks each account's password against length, character-class, blocklist, and username-derivation rules; reports pass/fail with reasons | An automated password-policy compliance scanner (the kind IT/security runs against an AD or IdP export) |
| `main.py` | Runs the cracking benchmark across several target passwords, runs the policy audit across sample accounts, then prints a combined risk summary | The final report a security engineer hands to management after an audit |

## Run it

```bash
python main.py
```

No third-party dependencies — only the standard library (`hashlib`, `hmac`,
`secrets`). Individual modules are also runnable directly for a focused demo:

```bash
python hash_schemes.py
python cracking_benchmark.py
python password_policy_checker.py
```

## Verified result

The numbers below are from an **actual run** of `python main.py` on the
development machine (`PBKDF2_ITERATIONS = 400,000`, wordlist size = 2,020
candidates: 30 common passwords + 2,000 padding entries).

### Part 1 — Cracking speed benchmark

| Target password | Fast/Weak SHA-256 (guesses/sec) | Slow/Proper PBKDF2 (guesses/sec) | Speed advantage (fast/slow) |
|---|---:|---:|---:|
| `trustno1` | 411,522.5 | 5.8 | ~71,031x |
| `monkey` | 1,016,951.4 | 5.7 | ~179,804x |
| `shadow` | 810,811.9 | 5.6 | ~144,012x |
| **Average** | **~746,429** | **~5.7** | **~131,109x** |

All three target passwords were found in the dictionary in well under a
handful of milliseconds against the fast/weak scheme, and in 2-4 seconds
against the slow/proper scheme (only 12-21 guesses each, but each guess costs
400,000 PBKDF2 rounds). Scale that gap to a real 10-million-entry wordlist or
a GPU attacker, and the fast/weak scheme falls in seconds while the slow
scheme remains impractical to brute-force — exactly the property real
password storage design is built around.

### Part 2 — Password policy audit

Policy: minimum length 12, must contain uppercase + lowercase + digit +
symbol, must not be on the common-password blocklist, must not be derived
from the username.

```
[FAIL] vanisha      - too short (10 chars), no uppercase, no symbol, derived from username
[PASS] j.smith
[FAIL] admin        - too short (8 chars), no uppercase/digit/symbol, on common-password list
[FAIL] r.patel      - no uppercase/digit/symbol (a passphrase with no complexity)
[PASS] k.lee
[FAIL] m.chen       - too short (10 chars), derived from username
[PASS] s.ahmed

Compliance: 3/7 accounts pass policy (43%)
```

### Combined summary (as printed by `main.py`)

```
Hashing scheme risk : fast/weak SHA-256 sustained ~746,429 guesses/sec
                      slow/proper PBKDF2 sustained ~6 guesses/sec
                      -> 131,109x throughput advantage for the attacker on the weak scheme
Policy compliance   : 3/7 accounts pass (43%)

Overall posture: HIGH RISK. The storage scheme itself would let an
attacker who steals the database try guesses orders of magnitude faster
than necessary, AND a meaningful fraction of real accounts use passwords
that fail policy -- either issue alone is exploitable; together they
compound.
```

## Things to try changing

- **Raise `PBKDF2_ITERATIONS` in `hash_schemes.py`** (e.g. from 400,000 to
  800,000 or 1,200,000) and re-run — crack time for the slow scheme should
  grow roughly proportionally, since each guess is a fixed number of extra
  HMAC rounds. This is the "work factor" knob real systems tune based on
  available hardware and acceptable login latency.
- **Swap `weak_hash`'s single SHA-256 round for something even weaker** (e.g.
  MD5) to see it barely changes the fast side — the vulnerability isn't the
  specific algorithm, it's the *lack of iteration and salt*.
- **Grow `common_password_list.py`** with a real breached-password list (e.g.
  download a trimmed rockyou.txt) and watch both the dictionary attack and the
  policy blocklist become far more effective.
- **Tighten `password_policy_checker.py`'s `MIN_LENGTH`** to 16 (NIST's
  suggested minimum for high-assurance systems) and see how many of the
  sample accounts newly fail.
- **Add a per-account lockout/rate-limit simulation**: even the slow PBKDF2
  scheme can eventually be brute-forced given enough time — a real system
  layers rate-limiting and account lockout on top of slow hashing, it doesn't
  rely on hashing speed alone.
