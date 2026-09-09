### Steganography, Blockchain, and Post-Quantum Cryptography

--> This note covers three forward-looking areas that build on everything already covered: hiding data's EXISTENCE rather than just its content (steganography), how hash functions and signatures compose into the trust model behind cryptocurrencies (blockchain), and what happens to today's cryptography once large quantum computers exist (post-quantum crypto), closing with two "computing on secrets" frontiers - Zero-Knowledge Proofs and Homomorphic Encryption.

## Steganography

--> Cryptography hides the CONTENT of a message (an eavesdropper sees ciphertext and knows a secret exists, just not what it says). Steganography hides the EXISTENCE of a message (an eavesdropper sees what looks like an ordinary image/audio file/text and doesn't even know a secret is being communicated).
--> The two are complementary, not competing: best practice is to ENCRYPT the message first, then hide the resulting ciphertext inside a cover file - so even if the hidden data is discovered, it's still protected by encryption.

# LSB (Least Significant Bit) Steganography

--> Every pixel in an image is typically stored as bytes for Red, Green, and Blue (0-255 each). Changing only the LEAST significant bit of a color value changes it by at most 1 out of 255 - a change so small it's imperceptible to the human eye, but it means every pixel channel can secretly carry exactly 1 bit of hidden data.
--> To hide a message: convert it to bits, and overwrite the LSB of successive pixel color bytes with those bits, one bit per channel. To extract: read the LSBs back in the same order and reassemble them into bytes.

```python
def message_to_bits(message: str) -> list[int]:
    data = message.encode("utf-8")
    bits = []
    for byte in data:
        bits.extend([(byte >> i) & 1 for i in range(7, -1, -1)])   # MSB-first per byte
    return bits

def bits_to_message(bits: list[int]) -> str:
    byte_chunks = [bits[i:i+8] for i in range(0, len(bits), 8)]
    byte_values = []
    for chunk in byte_chunks:
        value = 0
        for bit in chunk:
            value = (value << 1) | bit
        byte_values.append(value)
    return bytes(byte_values).decode("utf-8", errors="ignore")

# A 32-bit length header is embedded first so the extractor knows exactly how many
# bits belong to the real message, versus the "random-looking" LSB noise that follows.
def hide_message(pixels: list[list[int]], message: str) -> list[list[int]]:
    payload_bits = message_to_bits(message)
    length_bits = [(len(payload_bits) >> i) & 1 for i in range(31, -1, -1)]
    all_bits = length_bits + payload_bits

    if len(all_bits) > len(pixels) * 3:
        raise ValueError("Cover image too small to hide this message")

    stego = [row[:] for row in pixels]
    bit_index = 0
    for pixel in stego:
        for channel in range(3):   # R, G, B
            if bit_index >= len(all_bits):
                break
            pixel[channel] = (pixel[channel] & 0xFE) | all_bits[bit_index]   # clear LSB, set new bit
            bit_index += 1
    return stego

def extract_message(pixels: list[list[int]]) -> str:
    flat_bits = []
    for pixel in pixels:
        for channel in range(3):
            flat_bits.append(pixel[channel] & 1)

    length = 0
    for bit in flat_bits[:32]:
        length = (length << 1) | bit

    payload_bits = flat_bits[32:32 + length]
    return bits_to_message(payload_bits)


# Simulate a small 4x4 "image" as a flat list of [R, G, B] pixels (a real project would
# load this via Pillow: `from PIL import Image; img = Image.open("cover.png")`)
cover_image = [[100, 150, 200] for _ in range(16)]

secret = "Meet at dawn"
stego_image = hide_message(cover_image, secret)

print(cover_image[0], "->", stego_image[0])
# [100, 150, 200] -> [100, 150, 200]   <- LSB changes are at most +/-1, visually identical

recovered = extract_message(stego_image)
print(recovered)
# Meet at dawn
```

--> Detecting steganography (steganalysis) works by statistical analysis of LSB patterns - natural image noise has certain statistical properties, and LSB-embedded data (especially if it was encrypted first, making it look like uniform random noise) has subtly different statistical properties detectable with tools like chi-square attacks on the LSB plane.
--> Real-world use: legitimate uses include digital watermarking (embedding ownership/tracking info) and censorship-resistant communication; malicious uses include malware hiding payloads inside seemingly innocuous images to evade network content filters (a technique observed in real malware campaigns exfiltrating data via image uploads to legitimate-looking cloud storage).

## Blockchain Cryptography Fundamentals

--> A blockchain is fundamentally a cryptographic data structure: a chain of blocks where each block cryptographically commits to all previous blocks via hashing, combined with digital signatures to authorize transactions and a distributed consensus mechanism to agree on the single valid chain.

# Hash Chaining

--> Each block contains: a set of transactions, a timestamp, and the HASH of the previous block. Because each block's hash depends on the previous block's hash (which depends on the one before it, all the way back), changing ANY historical block changes its hash, which changes the NEXT block's "previous hash" field, which changes ITS hash, cascading forward through every subsequent block.

```python
import hashlib
import json
import time

class Block:
    def __init__(self, index: int, transactions: list, previous_hash: str):
        self.index = index
        self.timestamp = time.time()
        self.transactions = transactions
        self.previous_hash = previous_hash
        self.nonce = 0
        self.hash = self.compute_hash()

    def compute_hash(self) -> str:
        block_data = {
            "index": self.index,
            "timestamp": self.timestamp,
            "transactions": self.transactions,
            "previous_hash": self.previous_hash,
            "nonce": self.nonce,
        }
        block_string = json.dumps(block_data, sort_keys=True).encode()
        return hashlib.sha256(block_string).hexdigest()

genesis = Block(0, ["genesis block"], "0" * 64)
block1 = Block(1, ["Alice pays Bob 5 BTC"], genesis.hash)
block2 = Block(2, ["Bob pays Carol 2 BTC"], block1.hash)

print(block1.previous_hash == genesis.hash)   # True - the chain link
print(block2.previous_hash == block1.hash)    # True

# Tampering demonstration: modify block1's transaction data after the fact
block1.transactions = ["Alice pays Bob 500 BTC"]   # attacker tries to rewrite history
print(block1.compute_hash() == block1.hash)   # False - the recomputed hash no longer
                                               # matches the ORIGINAL hash, and critically,
                                               # block2.previous_hash still points to the
                                               # OLD (now invalid) hash, breaking the chain
                                               # visibly at every block built on top of it
```

--> This is exactly why blockchains are described as "immutable" - not because editing is physically impossible, but because editing breaks a verifiable hash chain that every participant in the network independently checks, and because rewriting history convincingly would require redoing the computational work (mining) for every subsequent block faster than the rest of the honest network combined.

# Merkle Trees

--> A block doesn't hash its transaction list directly (which would require re-hashing the ENTIRE list to verify even a single transaction's inclusion). Instead it uses a Merkle tree: pairs of transactions are hashed together, those hashes are paired and hashed again, repeating until a single "Merkle root" hash remains, which is the only thing stored in the block header.

```python
import hashlib

def sha256(data: str) -> str:
    return hashlib.sha256(data.encode()).hexdigest()

def build_merkle_tree(transactions: list[str]) -> list[list[str]]:
    layer = [sha256(tx) for tx in transactions]
    tree = [layer]
    while len(layer) > 1:
        if len(layer) % 2 == 1:
            layer.append(layer[-1])   # duplicate the last hash if odd count
        next_layer = [sha256(layer[i] + layer[i+1]) for i in range(0, len(layer), 2)]
        tree.append(next_layer)
        layer = next_layer
    return tree

transactions = ["Alice->Bob:5", "Bob->Carol:2", "Carol->Dave:1", "Dave->Alice:3"]
tree = build_merkle_tree(transactions)

for depth, layer in enumerate(tree):
    print(f"layer {depth}: {[h[:8] for h in layer]}")   # showing truncated hashes for readability
# layer 0: ['a1b2c3d4', '5e6f7a8b', '9c0d1e2f', '3a4b5c6d']   <- leaf hashes of each transaction
# layer 1: ['7f8e9d0c', '1b2a3c4d']                             <- pairwise combined hashes
# layer 2: ['f0e1d2c3']                                         <- the Merkle root

merkle_root = tree[-1][0]
print(f"Merkle root: {merkle_root}")
```

--> Merkle proof of inclusion: to prove transaction #2 ("Carol->Dave:1") is in the block WITHOUT downloading all transactions, you only need `log2(n)` hashes - the sibling hash at each level of the tree - which the verifier combines step by step with the known transaction hash to recompute the Merkle root, and compares against the trusted root. This is exactly how lightweight/mobile Bitcoin wallets (SPV - Simplified Payment Verification) verify a transaction happened without downloading the entire blockchain.
1. Efficiency – verifying one transaction's inclusion in a block of millions costs O(log n) hashes, not O(n).
2. Tamper detection – changing ANY transaction changes its leaf hash, which propagates up and changes the Merkle root, invalidating the block, same principle as the hash chain but applied within a single block's transaction set.

# Digital Signatures Securing Bitcoin Transactions

--> Every Bitcoin address is derived from an ECDSA public key (secp256k1 curve specifically). Spending funds from an address requires producing a valid ECDSA SIGNATURE over the transaction data using the corresponding private key - proving ownership without ever revealing the private key itself, using exactly the signature verification math already covered for RSA/ECDSA elsewhere in this series.
--> A transaction says, in effect: "the owner of private key X authorizes moving these funds to address Y," signed with X's private key, and every node in the network independently verifies that signature against the sender's known public key before accepting the transaction into a block.
--> This is why nonce reuse in ECDSA (see the Android Bitcoin wallet case study) is catastrophic specifically FOR Bitcoin - the private key IS the funds; recovering it means an attacker can sign arbitrary transactions moving the victim's coins anywhere, with no recovery mechanism, ever.

## Post-Quantum Cryptography

--> Quantum computers, if built at sufficient scale and quality (many thousands of stable logical qubits, far beyond what exists as of 2026), run fundamentally different algorithms that break the specific hard-math problems underlying today's most common asymmetric cryptography.

# Why RSA and ECC Break Under Shor's Algorithm

--> RSA's security rests on integer factorization being computationally hard classically (no known classical algorithm factors large numbers in polynomial time). ECC's security rests on the elliptic curve discrete logarithm problem being similarly hard classically.
--> Shor's algorithm (1994) is a quantum algorithm that solves BOTH integer factorization AND discrete logarithm problems in polynomial time, given a sufficiently large, low-error quantum computer. This isn't a minor speedup - it changes the problem from "computationally infeasible" to "efficiently solvable," collapsing the entire security foundation of RSA, DH, and ECC/ECDSA/ECDH simultaneously.
--> Critically, symmetric cryptography (AES) and hash functions (SHA-256) are NOT broken the same way - Grover's algorithm gives only a quadratic speedup against them (effectively halving the key-length security margin, e.g. AES-256 degrades to roughly AES-128-equivalent security against a quantum attacker), which is comfortably handled by simply using larger key sizes (AES-256 instead of AES-128). The crisis is specifically for PUBLIC-KEY cryptography.
--> "Harvest now, decrypt later" is the practical threat model driving urgency today: an adversary can record encrypted traffic NOW (e.g. TLS handshakes using RSA/ECDH key exchange) and decrypt it retroactively once a capable quantum computer exists years from now - meaning long-lived confidential data is at risk even before quantum computers are actually built, which is why migration is happening well ahead of the actual quantum threat materializing.

# Lattice-Based Cryptography

--> The leading replacement family relies on LATTICE problems - geometric problems in high-dimensional space believed to be hard for BOTH classical AND quantum computers, with no known Shor-style shortcut.
--> Core hard problem intuition: a lattice is an infinite regular grid of points in n-dimensional space, defined by a set of "basis vectors." The Shortest Vector Problem (SVP) asks for the shortest non-zero vector in the lattice, and the Closest Vector Problem (CVP) asks for the lattice point closest to some arbitrary target point - both become extremely hard to solve efficiently as the dimension grows, even though verifying a proposed solution is easy.
--> Learning With Errors (LWE) is the specific hard problem most standardized lattice schemes build on: given many noisy linear equations (`A*s + e ≈ b`, where `e` is small random "error"/noise), recovering the secret vector `s` is computationally hard - conceptually similar to modular arithmetic public-key systems, but the ADDED NOISE is what defeats both classical and known quantum solving techniques.

# NIST Post-Quantum Standardization

--> NIST ran a multi-year public competition (2016-2024) evaluating dozens of candidate algorithms for cryptanalytic resistance, performance, and implementation safety, ultimately standardizing:
1. Kyber (standardized as ML-KEM, FIPS 203) – a lattice-based Key Encapsulation Mechanism, the PQC replacement for RSA/ECDH key exchange. Used to establish a shared symmetric key between two parties, which then encrypts the actual traffic with AES/ChaCha20 as before.
2. Dilithium (standardized as ML-DSA, FIPS 204) – a lattice-based digital signature scheme, the PQC replacement for RSA/ECDSA/EdDSA signatures.
3. SPHINCS+ (standardized as SLH-DSA, FIPS 205) – a hash-based (not lattice-based) signature scheme, included as a structurally DIFFERENT backup approach in case unexpected weaknesses are later found in lattice-based math specifically - deliberately diversifying the mathematical foundation NIST is betting on.
4. Falcon – another lattice-based signature scheme, standardized later, offering smaller signatures than Dilithium at the cost of a more complex implementation (harder to implement in constant-time, a real side-channel consideration circling back to the previous note in this series).
--> Migration approach in practice: "hybrid" key exchange (e.g. X25519 combined with Kyber in the same TLS handshake, already deployed by major browsers/CDNs) - the connection stays secure as long as EITHER the classical OR the post-quantum algorithm holds, hedging against both an unexpected classical break AND the possibility that these newer PQC algorithms haven't yet had as many decades of cryptanalytic scrutiny as RSA/ECC.

## Zero-Knowledge Proofs (Forward-Looking)

--> A Zero-Knowledge Proof (ZKP) lets a "prover" convince a "verifier" that a statement is TRUE (e.g. "I know a password," "I am over 18," "this transaction is valid") WITHOUT revealing any information beyond the fact that the statement is true - not the password, not the birthdate, not the transaction details.
--> A proof system must satisfy three properties: completeness (a true statement can always be proven), soundness (a false statement can't be proven, except with negligible probability), and zero-knowledge (the verifier learns literally nothing beyond "yes, this is true").

# The Ali Baba Cave Analogy

--> Picture a circular cave with a single entrance splitting into two paths (A and B) that meet at a secret door deep inside, openable only with a magic word. Peggy (prover) claims to know the magic word; Victor (verifier) wants proof without learning the word itself.
1. Victor waits outside while Peggy enters and randomly walks down EITHER path A or B (Victor doesn't see which).
2. Victor then walks to the entrance and shouts which path he wants Peggy to EXIT from - chosen randomly, AFTER Peggy has already committed to a path.
3. If Peggy genuinely knows the magic word, she can always exit from whichever path Victor names (opening the door to switch paths if she entered the "wrong" one for his request). If she DOESN'T know the word, she only has a 50% chance of having entered the path Victor happens to name.
4. Repeating this many times (say 20 rounds) makes Peggy's odds of "faking it" by pure luck vanishingly small (1 in 2^20), while Victor NEVER learns the magic word itself - he only ever sees which path she emerged from, never inside the actual secret door.
--> This is the essence of an interactive zero-knowledge proof: repeated probabilistic challenges that make cheating exponentially unlikely while leaking zero information about the secret itself.

# Real-World Relevance

--> zk-SNARKs and zk-STARKs (non-interactive variants, generating a single compact proof rather than requiring back-and-forth rounds) are used today in privacy-preserving cryptocurrencies (Zcash uses zk-SNARKs to prove a transaction is valid - correct amounts, no double-spending - without revealing the sender, receiver, or amount), and in blockchain "rollups" that prove an entire batch of transactions was processed correctly without every node re-executing them, purely by verifying one small proof.

## Homomorphic Encryption (Forward-Looking)

--> Homomorphic encryption allows performing COMPUTATIONS directly on ciphertext, producing an encrypted result that, when decrypted, matches what you'd get from doing the same computation on the plaintext - without the party doing the computation ever seeing the actual data.
1. Partially Homomorphic Encryption (PHE) – supports only ONE operation repeatedly (e.g. RSA is homomorphic under multiplication only: `Encrypt(a) * Encrypt(b) = Encrypt(a*b)`, which is incidentally also WHY RSA needs proper padding, since this same property enables certain forgery/oracle attacks if left unmitigated, as covered in the padding oracle note).
2. Somewhat Homomorphic Encryption (SHE) – supports a LIMITED number of both addition and multiplication operations before accumulated "noise" in the ciphertext makes decryption fail.
3. Fully Homomorphic Encryption (FHE) – supports UNLIMITED arbitrary computation (any circuit, any number of operations) on encrypted data, made practical by Craig Gentry's 2009 breakthrough technique called "bootstrapping" (periodically refreshing/re-encrypting the ciphertext mid-computation to reduce accumulated noise before it grows too large to decrypt correctly).

```python
# Conceptual illustration only (toy, insecure) of the ADDITIVE homomorphic property,
# just to make the idea concrete - NOT a real FHE scheme.
def toy_encrypt(value: int, key: int) -> int:
    return value + key            # trivially insecure "encryption", for illustration only

def toy_decrypt(ciphertext: int, key: int) -> int:
    return ciphertext - key

key = 42
enc_a = toy_encrypt(5, key)
enc_b = toy_encrypt(7, key)

# A party holding ONLY enc_a and enc_b (no key) can still compute an encrypted SUM:
enc_sum = enc_a + enc_b - key      # homomorphic addition, structured so decrypting once works
print(toy_decrypt(enc_sum, key))   # 12 - matches 5 + 7, computed without ever seeing 5 or 7 in the clear
```

--> Real-world relevance: FHE enables computing statistics or running ML inference on encrypted medical/financial records at a cloud provider that never sees the underlying data in plaintext, private set intersection (e.g. contact-discovery in messaging apps without uploading your contact list), and encrypted database queries. The current practical barrier is performance - FHE operations are still orders of magnitude slower than plaintext computation, an active and fast-moving research area (Microsoft SEAL, IBM HELib, Zama's Concrete are current real-world FHE libraries), making it the cryptographic frontier most likely to become mainstream-practical over the next decade.

## Closing Note

--> Steganography, blockchain cryptography, post-quantum algorithms, ZKPs, and homomorphic encryption all build on the exact same primitives covered earlier in this series - hashing, symmetric encryption, and digital signatures - just recombined toward new goals: hiding existence, decentralizing trust, surviving a new class of adversary (quantum computers), and computing on secrets directly. Understanding the fundamentals deeply is what makes each of these frontiers legible rather than magical.
