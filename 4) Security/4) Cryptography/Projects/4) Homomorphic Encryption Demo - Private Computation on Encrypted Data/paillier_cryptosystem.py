"""
paillier_cryptosystem.py

A real, from-scratch implementation of the Paillier partially-homomorphic
cryptosystem (Pascal Paillier, 1999).

Paillier is a genuine, historically real public-key cryptosystem whose
security rests on the "Decisional Composite Residuosity" assumption (related
in spirit to RSA's hardness of factoring n = p * q). It has one special
algebraic property that makes it famous:

    Enc(a) * Enc(b)  mod n^2   ==   Enc(a + b mod n)

Multiplying two ciphertexts (mod n^2) is equivalent to adding their
plaintexts (mod n). This is "additive homomorphism". No decryption key is
ever needed to perform that addition -- an untrusted party can do it.

This file implements, all from stdlib (random, math only):
    1. Miller-Rabin primality testing (from scratch)
    2. Safe-ish random prime generation of a given bit length
    3. Paillier key generation (p, q, n, lambda, mu, g)
    4. Encryption / Decryption
    5. Homomorphic addition of two ciphertexts (ciphertext * ciphertext mod n^2)
    6. Homomorphic addition of a ciphertext and a known plaintext constant
    7. Homomorphic scalar multiplication (ciphertext ** k mod n^2)

No third-party crypto libraries are used anywhere in this project.
"""

from __future__ import annotations

import math
import random
from dataclasses import dataclass


# --------------------------------------------------------------------------
# 1. Miller-Rabin primality test (from scratch)
# --------------------------------------------------------------------------

def is_probable_prime(n: int, rounds: int = 40) -> bool:
    """Miller-Rabin probabilistic primality test.

    Returns True if `n` is *probably* prime with error probability at most
    4^-rounds (with rounds=40 that's astronomically small, ~1e-24).
    """
    if n < 2:
        return False
    # Quick trial division against small primes to reject obvious composites fast.
    small_primes = (2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47)
    for p in small_primes:
        if n == p:
            return True
        if n % p == 0:
            return False

    # Write n - 1 = 2^r * d with d odd.
    r, d = 0, n - 1
    while d % 2 == 0:
        d //= 2
        r += 1

    for _ in range(rounds):
        a = random.randrange(2, n - 1)
        x = pow(a, d, n)
        if x == 1 or x == n - 1:
            continue
        for _ in range(r - 1):
            x = pow(x, 2, n)
            if x == n - 1:
                break
        else:
            return False  # definitely composite
    return True  # probably prime


def generate_prime(bit_length: int) -> int:
    """Generate a random probable prime of exactly `bit_length` bits."""
    if bit_length < 2:
        raise ValueError("bit_length must be >= 2")
    while True:
        # Force the top bit (so the number has exactly bit_length bits) and
        # the bottom bit (so it's odd -- primes > 2 are always odd).
        candidate = random.getrandbits(bit_length)
        candidate |= (1 << (bit_length - 1)) | 1
        if is_probable_prime(candidate):
            return candidate


# --------------------------------------------------------------------------
# 2. Small number-theory helpers
# --------------------------------------------------------------------------

def lcm(a: int, b: int) -> int:
    """Least common multiple, via gcd (math.gcd is stdlib arithmetic, not crypto)."""
    return a // math.gcd(a, b) * b


def mod_inverse(a: int, m: int) -> int:
    """Modular multiplicative inverse of a mod m, via the extended Euclidean
    algorithm (implemented from scratch -- no library call for this)."""
    old_r, r = a % m, m
    old_s, s = 1, 0
    while r != 0:
        quotient = old_r // r
        old_r, r = r, old_r - quotient * r
        old_s, s = s, old_s - quotient * s
    if old_r != 1:
        raise ValueError(f"{a} has no inverse modulo {m}")
    return old_s % m


def l_function(x: int, n: int) -> int:
    """The Paillier "L" function: L(x) = (x - 1) / n, integer division.

    Only ever called with x that is congruent to 1 mod n, so the division
    is exact.
    """
    return (x - 1) // n


# --------------------------------------------------------------------------
# 3. Key containers
# --------------------------------------------------------------------------

@dataclass(frozen=True)
class PaillierPublicKey:
    """The public key -- safe to hand to every client and to the cloud.

    Knowing (n, g) lets anyone ENCRYPT and homomorphically COMBINE
    ciphertexts, but gives no practical way to decrypt them.
    """
    n: int
    g: int

    @property
    def n_sq(self) -> int:
        return self.n * self.n


@dataclass(frozen=True)
class PaillierPrivateKey:
    """The private key -- held ONLY by the key authority / auditor.

    (lambda, mu) are the only pieces of information capable of turning a
    ciphertext back into a plaintext.
    """
    lam: int   # lambda = lcm(p-1, q-1)
    mu: int    # mu = (L(g^lambda mod n^2))^-1 mod n
    public_key: PaillierPublicKey


# --------------------------------------------------------------------------
# 4. Key generation
# --------------------------------------------------------------------------

def generate_keypair(bit_length: int = 512) -> tuple[PaillierPublicKey, PaillierPrivateKey]:
    """Generate a genuine Paillier keypair.

    bit_length is the size of each of the two primes p and q, so n = p*q
    ends up roughly 2 * bit_length bits -- e.g. bit_length=512 gives a
    ~1024-bit modulus n, a real (if modest, for classroom runtime speed)
    key size, not a toy substitution-cipher scale.
    """
    # 1. Pick two distinct large primes p, q of the requested bit length.
    p = generate_prime(bit_length)
    q = generate_prime(bit_length)
    while p == q:
        q = generate_prime(bit_length)

    n = p * q
    n_sq = n * n

    # 2. lambda = lcm(p-1, q-1)  -- the Carmichael function of n.
    lam = lcm(p - 1, q - 1)

    # 3. Standard simplified generator choice g = n + 1.
    #    This is the well-known simplification (valid whenever gcd(n, p) and
    #    gcd(n, q) hold as they do here) that makes mu's computation trivial
    #    and is used in essentially every textbook/real Paillier implementation.
    g = n + 1

    # 4. mu = ( L(g^lambda mod n^2) )^-1 mod n
    #    With g = n + 1, g^lambda mod n^2 = 1 + lambda*n mod n^2, so
    #    L(...) = lambda mod n, and mu = (lambda mod n)^-1 mod n.
    x = pow(g, lam, n_sq)
    mu = mod_inverse(l_function(x, n), n)

    public_key = PaillierPublicKey(n=n, g=g)
    private_key = PaillierPrivateKey(lam=lam, mu=mu, public_key=public_key)
    return public_key, private_key


# --------------------------------------------------------------------------
# 5. Encryption / Decryption
# --------------------------------------------------------------------------

def encrypt(public_key: PaillierPublicKey, plaintext: int, r: int | None = None) -> int:
    """Encrypt an integer plaintext (0 <= plaintext < n) under the public key.

    A random blinding factor r (coprime to n) is chosen so the same
    plaintext encrypts to a different ciphertext every time -- this
    randomization is essential: without it, the cloud could recognize
    repeated values by comparing ciphertexts directly.
    """
    n = public_key.n
    n_sq = public_key.n_sq

    if not (0 <= plaintext < n):
        raise ValueError("plaintext must satisfy 0 <= plaintext < n")

    if r is None:
        while True:
            r = random.randrange(1, n)
            if math.gcd(r, n) == 1:
                break

    # ciphertext = g^plaintext * r^n mod n^2
    c = (pow(public_key.g, plaintext, n_sq) * pow(r, n, n_sq)) % n_sq
    return c


def decrypt(private_key: PaillierPrivateKey, ciphertext: int) -> int:
    """Decrypt a ciphertext using the private key. Only the key authority
    (the only party holding a PaillierPrivateKey) can ever call this."""
    n = private_key.public_key.n
    n_sq = private_key.public_key.n_sq

    x = pow(ciphertext, private_key.lam, n_sq)
    plaintext = (l_function(x, n) * private_key.mu) % n
    return plaintext


# --------------------------------------------------------------------------
# 6. Homomorphic operations (no private key involved anywhere below)
# --------------------------------------------------------------------------

def homomorphic_add(public_key: PaillierPublicKey, c1: int, c2: int) -> int:
    """Combine two ciphertexts so that decrypting the result yields the SUM
    of the two original plaintexts -- performed by simple modular
    multiplication of the ciphertexts, with no visibility into either
    plaintext, and no private key required.

        Dec( c1 * c2 mod n^2 ) == Dec(c1) + Dec(c2)  (mod n)
    """
    n_sq = public_key.n_sq
    return (c1 * c2) % n_sq


def homomorphic_add_plaintext(public_key: PaillierPublicKey, c: int, plain_constant: int) -> int:
    """Add a known plaintext constant `k` to an encrypted value, without
    decrypting: Dec( c * g^k mod n^2 ) == Dec(c) + k."""
    n_sq = public_key.n_sq
    return (c * pow(public_key.g, plain_constant, n_sq)) % n_sq


def homomorphic_scalar_multiply(public_key: PaillierPublicKey, c: int, k: int) -> int:
    """Multiply an encrypted value by a known plaintext scalar `k`, without
    decrypting: Dec( c^k mod n^2 ) == k * Dec(c) (mod n)."""
    n_sq = public_key.n_sq
    return pow(c, k, n_sq)
