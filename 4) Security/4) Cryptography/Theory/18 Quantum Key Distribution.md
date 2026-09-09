# QKD vs Post-Quantum Cryptography -- Two Different Answers to Quantum Threats

--> The Post-Quantum Cryptography content covered earlier in this track (Steganography/Blockchain/Post-Quantum file) is about designing NEW MATHEMATICAL algorithms (Kyber, Dilithium) that remain hard even for a quantum computer to break -- still classical computation and classical communication, just quantum-resistant math.
--> Quantum Key Distribution (QKD) is a completely different approach -- it uses the actual PHYSICS of quantum mechanics itself to distribute an encryption key, with security guaranteed by the laws of physics rather than by a math problem being computationally hard.

# The Physics QKD Relies On

--> Photon polarization -- QKD (most commonly via the BB84 protocol) encodes key bits onto the polarization state of individual photons sent between two parties.
--> The No-Cloning Theorem -- a fundamental law of quantum mechanics stating that an unknown quantum state CANNOT be perfectly copied. An eavesdropper physically cannot intercept a photon, copy its state, and pass along an identical copy undetected.
--> The Observer Effect -- measuring a quantum system in the wrong "basis" disturbs it. An eavesdropper attempting to read the photons in transit inevitably introduces detectable errors into the key exchange.

# The BB84 Protocol -- Conceptual Walkthrough

--> The sender (traditionally "Alice") encodes each key bit as a photon's polarization, using one of two randomly chosen measurement "bases."
--> The receiver ("Bob") measures each photon using his own randomly chosen basis, without knowing in advance which basis Alice used for that particular photon.
--> Afterward, Alice and Bob publicly compare (over an ordinary, unsecured channel) WHICH basis they each used for each photon -- discarding results where their bases didn't match, and keeping only the bits where they happened to match, forming the actual shared secret key.
--> Crucially, if an eavesdropper ("Eve") tried to intercept and measure the photons in transit, her measurements would introduce detectable errors -- Alice and Bob can statistically verify the key exchange by comparing a small sample of their supposedly-matching bits; a suspiciously high error rate reveals that eavesdropping occurred, and they simply discard that key exchange and try again.

# What QKD Actually Provides -- Detecting, Not Preventing, Eavesdropping

--> QKD doesn't make eavesdropping impossible -- it makes it DETECTABLE, with the guarantee coming from physics rather than computational difficulty. If Eve intercepts the channel, Alice and Bob will know (via the elevated error rate) and can abort before ever using a compromised key.
--> This is a fundamentally different security model from every other technique covered in this track -- RSA/ECC/AES all rely on a problem being computationally infeasible to solve in reasonable time; QKD's guarantee holds even against an attacker with UNLIMITED computational power, quantum or otherwise, because the security comes from the physical act of measurement, not from a math problem at all.

# Practical Limitations

--> Distance -- photons degrade over fiber-optic distance; practical QKD links are currently limited to roughly a few hundred kilometers without "quantum repeaters" (still an active area of research), unlike classical internet traffic which can travel globally without this constraint.
--> Specialized hardware -- QKD requires dedicated photon-transmission equipment on both ends, not standard networking gear -- it's not something you can simply enable in software like adopting a new algorithm.
--> QKD secures the KEY EXCHANGE step only -- the actual bulk data encryption afterward still uses conventional symmetric encryption (AES, covered earlier), with QKD supplying that symmetric key via an unconditionally secure channel instead of via classical Diffie-Hellman/RSA-based key exchange.

# Where QKD Is Actually Deployed Today

--> Currently used in specialized, high-value contexts -- government/military communications, financial institutions' most sensitive links, and a handful of metropolitan "quantum networks" (notably in China and parts of Europe) -- not yet a mainstream internet technology, given the distance and hardware constraints above.
