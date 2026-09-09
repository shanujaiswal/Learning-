"""
mutators.py

Mutation strategies over seed inputs for the protocol fuzzer.

Each mutator is a pure function: bytes -> bytes. It takes one "seed" command
(a single line of the target's text protocol, WITHOUT the trailing newline)
and returns a mutated version of it. None of these functions touch the
network -- they are the "input generation" half of the fuzzer, kept separate
from fuzzer_harness.py (which is the "send it and watch what happens" half).

This mirrors, in miniature, what a real mutation-based fuzzer (AFL, honggfuzz,
boofuzz) does: take a corpus of known-good/interesting inputs and apply cheap,
randomized transformations that are individually dumb but collectively good at
stumbling into edge cases a developer never thought to test.
"""

from __future__ import annotations

import random
import string

# Integers chosen specifically to sit right on or just past classic fixed-width
# integer boundaries (signed/unsigned 32-bit and 64-bit). These are the values
# most likely to trip an integer-overflow-style bug in code that packs a
# length field into a fixed-width type without range-checking it first.
BOUNDARY_INTEGERS = [
    -1,
    0,
    1,
    127,
    128,
    255,
    256,
    2**15 - 1,
    2**15,
    2**16,
    -(2**31),
    -(2**31) - 1,
    2**31 - 1,
    2**31,
    2**32,
    2**63 - 1,
    2**63,
    2**64,
    10**18,
    -(10**18),
]

PRINTABLE_BYTES = string.printable.encode("ascii")


def _rand_bytes(n: int) -> bytes:
    return bytes(random.randint(0, 255) for _ in range(n))


def bit_flip(seed: bytes) -> bytes:
    """Flip a small number of random bits at random byte positions."""
    if not seed:
        return seed
    data = bytearray(seed)
    flips = random.randint(1, max(1, len(data) // 4) or 1)
    for _ in range(flips):
        pos = random.randrange(len(data))
        bit = 1 << random.randint(0, 7)
        data[pos] ^= bit
    return bytes(data)


def byte_insert(seed: bytes) -> bytes:
    """Insert 1-8 random bytes at a random position."""
    data = bytearray(seed)
    count = random.randint(1, 8)
    pos = random.randint(0, len(data))
    data[pos:pos] = _rand_bytes(count)
    return bytes(data)


def byte_delete(seed: bytes) -> bytes:
    """Delete a small random slice of bytes. This is what most reliably
    produces the 'malformed structure / missing token' class of bug -- e.g.
    deleting the space between a key and a value collapses two protocol
    tokens into one."""
    if len(seed) < 2:
        return seed
    data = bytearray(seed)
    count = random.randint(1, max(1, len(data) // 3))
    pos = random.randint(0, len(data) - count)
    del data[pos : pos + count]
    return bytes(data)


def boundary_value_integer(seed: bytes) -> bytes:
    """Replace a whitespace-separated token that looks numeric (or, failing
    that, the second token) with a boundary-value integer. This is the
    mutator most likely to trigger the length-prefix integer-overflow-style
    bug in the LEN command."""
    tokens = seed.split(b" ")
    value = str(random.choice(BOUNDARY_INTEGERS)).encode("ascii")

    numeric_positions = [i for i, tok in enumerate(tokens) if tok.lstrip(b"-").isdigit()]
    if numeric_positions:
        idx = random.choice(numeric_positions)
    elif len(tokens) > 1:
        idx = 1
    else:
        tokens.append(value)
        return b" ".join(tokens)

    tokens[idx] = value
    return b" ".join(tokens)


def null_byte_injection(seed: bytes) -> bytes:
    """Insert one or more NUL (0x00) bytes at random positions. Real-world
    parsers frequently assume text protocol fields never contain embedded
    NULs; this is the mutator aimed squarely at that assumption."""
    data = bytearray(seed)
    count = random.randint(1, 3)
    for _ in range(count):
        pos = random.randint(0, len(data))
        data.insert(pos, 0x00)
    return bytes(data)


def oversized_string(seed: bytes) -> bytes:
    """Append or replace a token with a very large chunk of data, to probe
    for missing length/size validation."""
    size = random.choice([1_000, 10_000, 65_536, 200_000])
    filler = bytes(random.choice(PRINTABLE_BYTES) for _ in range(size))
    if random.random() < 0.5:
        return seed + b" " + filler
    tokens = seed.split(b" ")
    if len(tokens) > 1:
        tokens[-1] = filler
        return b" ".join(tokens)
    return filler


# Registry used by fuzzer_harness.py so every generated input can be traced
# back to the exact strategy that produced it -- this is what lets the triage
# report later say "mutator X found bug Y".
MUTATORS = {
    "bit_flip": bit_flip,
    "byte_insert": byte_insert,
    "byte_delete": byte_delete,
    "boundary_value_integer": boundary_value_integer,
    "null_byte_injection": null_byte_injection,
    "oversized_string": oversized_string,
}


def mutate(seed: bytes, mutator_name: str | None = None) -> tuple[str, bytes]:
    """Apply one randomly chosen (or explicitly named) mutator to `seed`.
    Returns (mutator_name, mutated_bytes)."""
    name = mutator_name or random.choice(list(MUTATORS))
    return name, MUTATORS[name](seed)
