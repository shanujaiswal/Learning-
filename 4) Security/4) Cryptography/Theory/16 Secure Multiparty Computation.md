# The Problem SMPC Solves

--> Normally, computing a function over data from multiple parties requires at least one party to SEE all the input data (a server receiving everyone's raw values to compute an average, for example). Secure Multiparty Computation (SMPC/MPC) lets multiple parties jointly compute a function over their combined private inputs WITHOUT any party ever revealing their individual input to anyone else -- only the final agreed-upon result is learned by anyone.

# The Classic Illustrative Example -- Average Salary

--> Three coworkers want to know their average salary, without revealing their individual salary to each other or to any third party. MPC makes this genuinely possible -- not through trusting a "neutral" fourth party, but through cryptographic protocols where no single party's data is ever exposed in the clear, even during the computation itself.

# Secret Sharing -- The Foundational Building Block

--> Shamir's Secret Sharing splits a secret value into multiple "shares" distributed among parties, such that no individual share (or even most shares, below some threshold) reveals ANYTHING about the original secret -- but combining enough shares together reconstructs it exactly.

```python
# Conceptual illustration -- splitting a secret salary value into 3 shares
# using a (3-of-3) additive secret sharing scheme
import random

def split_secret(secret, num_shares=3):
    shares = [random.randint(-10**9, 10**9) for _ in range(num_shares - 1)]
    last_share = secret - sum(shares)   # Ensures all shares sum back to the original secret
    shares.append(last_share)
    return shares   # Each party gets ONE share -- individually meaningless, reveals nothing alone

def reconstruct_secret(shares):
    return sum(shares)   # Only works once ALL shares are combined together
```

--> Critically, computations (like addition) can often be performed DIRECTLY on the shares themselves, without ever reconstructing anyone's actual secret value -- each party locally computes on their own share, and only the FINAL combined result (e.g. the sum) gets reconstructed, never any individual input.

# Garbled Circuits -- Another MPC Technique

--> An alternative MPC approach encodes a computation as a Boolean circuit, then "garbles" (encrypts) its logic gates such that two parties can jointly evaluate the circuit on their combined inputs, each learning only the final output bit, never any intermediate wire value that would reveal information about the other party's input.
--> Commonly used for specific two-party comparison problems (e.g. "who has the higher salary, without revealing either salary" -- the classically cited "Millionaires' Problem" that originally motivated this whole field).

# Real-World Applications

--> Privacy-preserving data analysis -- multiple hospitals jointly computing aggregate statistics across their patient data (for medical research) without any hospital exposing individual patient records to the others.
--> Private set intersection -- two companies determining which customers they have IN COMMON (for fraud detection or targeted advertising audience overlap) without either company revealing their full customer list to the other.
--> Threshold cryptography -- splitting a private key (e.g. for a cryptocurrency wallet or a certificate authority's signing key) across multiple parties/servers, so signing/decrypting requires a threshold number of them to cooperate -- no single compromised party can act alone, directly relevant to the PKI concepts covered earlier in this track.

# Why MPC Is Still a Specialized, Emerging Tool

--> MPC protocols are computationally and communication-intensive compared to a plain computation performed by one trusted party -- meaningful overhead that limits its practical use to scenarios where the privacy guarantee is worth that cost (which is growing as privacy regulation, covered in the Cyber Security track's GRC file, increasingly makes "just trust one party with all the raw data" a genuine legal and reputational liability).
