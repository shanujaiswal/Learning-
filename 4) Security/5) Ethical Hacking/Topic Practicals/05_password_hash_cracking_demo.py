"""
05_password_hash_cracking_demo.py -- Offline Dictionary Attack Demo
=================================================================================
(Ch.07: Password Attacks - Hydra, John the Ripper and Hashcat)

LEGAL / ETHICAL SCOPE
----------------------
Only test systems you own or are authorized to test. This script performs
NO network activity whatsoever -- it hashes a small local wordlist in memory
and compares the results against a handful of intentionally weak, unsalted
hashes that this script itself generates. It never touches any real
credential store, and it is not a substitute for actual tools like John the
Ripper or Hashcat -- it exists purely to demonstrate the *concept* of a
dictionary attack against unsalted hashes in a fully self-contained way,
since installing external cracking tools is out of scope for a single
Python file.

WHAT THIS DEMONSTRATES
------------------------
1. Why unsalted hashes are crackable: identical passwords always produce
   identical hashes, and a small wordlist can recover any password that
   appears in it, instantly, with no brute force needed.
2. A basic dictionary attack: hash every wordlist entry and compare against
   the target hash (this is conceptually what Hydra/John/Hashcat automate
   at scale, with GPU acceleration, rule-based mutation, salting-awareness,
   etc. -- all out of scope here).
3. Why salting + slow hash functions (bcrypt/scrypt/argon2) defeat this
   simple approach.

Run:
    python 05_password_hash_cracking_demo.py
"""

import hashlib
from typing import Optional

# A tiny local wordlist -- deliberately small and obviously weak passwords,
# for demo purposes only. Real wordlists (e.g. rockyou.txt) have millions
# of entries; this file is intentionally minimal so the demo stays
# self-contained and fast.
WORDLIST = [
    "password",
    "123456",
    "admin",
    "letmein",
    "qwerty",
    "alicepass123",
    "bobpassword",
    "SuperSecret1",
    "dragon",
    "monkey",
]

# Simulated "leaked" unsalted MD5 hash database -- these correspond 1:1 to
# users.password values in target_app.py's in-memory table, hashed here
# with plain unsalted MD5 to illustrate a common real-world mistake.
def md5(text: str) -> str:
    return hashlib.md5(text.encode("utf-8")).hexdigest()


LEAKED_HASHES = {
    "alice": md5("alicepass123"),
    "bob": md5("bobpassword"),
    "admin": md5("SuperSecret1"),
    "carol": md5("dragon"),  # not in target_app.py, just an extra example
}


def dictionary_attack(username: str, target_hash: str, wordlist) -> Optional[str]:
    """
    Classic offline dictionary attack: hash every candidate password from
    the wordlist with the same algorithm and compare against the target
    hash. No network calls, no brute force -- just a linear search, which
    is exactly why unsalted+unstretched hashing is unsafe: it makes this
    search essentially free.
    """
    for candidate in wordlist:
        if md5(candidate) == target_hash:
            return candidate
    return None


def run_demo():
    print("[*] Simulated leaked (unsalted MD5) password hash database:")
    for user, h in LEAKED_HASHES.items():
        print(f"    {user}: {h}")
    print()

    print("[*] Running dictionary attack using local wordlist "
          f"({len(WORDLIST)} candidate passwords)...\n")

    for user, target_hash in LEAKED_HASHES.items():
        cracked = dictionary_attack(user, target_hash, WORDLIST)
        if cracked:
            print(f"    [!] CRACKED  {user:<10} hash={target_hash}  password='{cracked}'")
        else:
            print(f"    [ ] not found in wordlist: {user:<10} hash={target_hash}")
    print()

    print("[i] Why this worked so easily:")
    print("    - The hashes are unsalted, so identical passwords -> identical hashes")
    print("      across every account (and across every system that made this mistake).")
    print("    - MD5 is extremely fast to compute, so hashing an entire wordlist takes")
    print("      microseconds -- this is exactly what tools like Hashcat/John do at a")
    print("      vastly larger scale, with GPUs and rule-based mutations.")
    print()
    print("[+] THE FIX:")
    print("    - Use a slow, purpose-built password hashing algorithm: bcrypt, scrypt,")
    print("      or argon2 (NOT md5/sha1/sha256 alone).")
    print("    - Always use a unique, random salt per password, so identical passwords")
    print("      produce different stored hashes and precomputed tables (rainbow")
    print("      tables) become useless.")
    print("    - Consider adding a rate limit / account lockout for the *online* login")
    print("      form as a separate defense (this demo is about *offline* cracking of")
    print("      a leaked hash database, which salting+slow-hashing directly defeats).")


if __name__ == "__main__":
    run_demo()
