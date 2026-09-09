### Trusted Execution Environments and Confidential Computing

--> Notes 20 and 21 covered protecting KEYS at rest and in transit. A different problem remains: even with perfect key management, the moment data is DECRYPTED to be computed on, it exists as plaintext in RAM — visible in principle to a compromised OS, hypervisor, or a cloud provider's own privileged infrastructure. A Trusted Execution Environment (TEE) shrinks the trust boundary down to just the CPU package itself, so that plaintext computation can happen without trusting the OS, hypervisor, or even the physical operator around it.

## What a TEE Is

--> A TEE (also called a "secure enclave") is an isolated execution region, enforced by the CPU itself, where code and data are protected from every other piece of software on the machine — including the OS kernel and hypervisor, both of which normally have unrestricted access to everything.
--> Two properties make this possible:
--> 1. **Isolated execution** — the CPU enforces, at the hardware level, that no instruction outside the enclave can read or write enclave memory or step through enclave code, regardless of privilege ring. Even a fully compromised kernel cannot inspect it directly.
--> 2. **Memory encryption** — enclave memory pages are encrypted on the fly by a dedicated hardware engine as they leave the CPU cache for DRAM, so even physically probing the RAM bus (a real attack surface for a malicious cloud operator or a stolen server) yields ciphertext, not plaintext.
--> **Remote attestation** ties this all together: a TEE can produce a cryptographically signed statement — "code with this exact hash is running inside a genuine enclave of this type, in this state" — signed by a key that traces back to the chip manufacturer. A remote party can verify that statement BEFORE sending any secrets to the enclave, closing the loop on "why should I trust code running on hardware I don't own."

## Intel SGX (Software Guard Extensions)

--> SGX lets an application carve out an **enclave** — a protected memory region created by special CPU instructions (`ECREATE`, `EADD`, `EINIT`) — into which sensitive code and data are loaded. Once initialized, code inside the enclave runs with full CPU speed, but nothing outside (including `ring 0` kernel code) can read its memory.
--> The **Memory Encryption Engine (MEE)** transparently encrypts and integrity-protects the enclave's pages as they're evicted from cache into the Enclave Page Cache in DRAM — this is the "protect even a physical RAM probe" half of the guarantee.
--> **Attestation flow (SGX, classic form via the Intel Attestation Service, IAS)**:
--> 1. The enclave generates a local attestation report (a `REPORT` structure) containing a measurement (hash) of its own code/data and any application-supplied data.
--> 2. This report is signed using a hardware-derived key tied to the specific CPU (via the Quoting Enclave, a special Intel-provided enclave whose job is exactly this).
--> 3. The signed "quote" is sent to a remote verifier, who forwards it to Intel's Attestation Service (or, in newer deployments, Intel's Data Center Attestation Primitives / DCAP running the verification locally without calling out to Intel).
--> 4. IAS/DCAP confirms the quote came from genuine, unrevoked Intel SGX hardware and returns a verification result.
--> 5. Only after successful attestation does the remote party provision secrets (e.g. a decryption key) into the enclave — this is the exact point where a key from an HSM/KMS (note 20) could be released specifically to an attested enclave and nowhere else.
--> **Side-channel attacks damaged trust badly**: SGX's isolation is at the memory-access level, but shares CPU microarchitectural structures (caches, branch predictors, speculative execution buffers) with the rest of the system, which turned out to leak information anyway.
--> 1. **Foreshadow / L1TF (2018)** — exploited speculative execution (in the same family as Meltdown) to read L1 cache contents that included SGX enclave secrets, defeating the isolation guarantee entirely for unpatched systems — a devastating result specifically because SGX's entire value proposition was "protected even from a compromised OS," and this attack achieved exactly that from a compromised OS.
--> 2. Numerous follow-on cache-timing and branch-prediction side channels (Plundervolt, SGAxe, and others) kept eroding confidence through the following years, to the point that Intel discontinued SGX on newer consumer-facing client CPU lines while continuing it for server/Xeon parts with mitigations.

## ARM TrustZone

--> TrustZone splits the entire system (not just enclave-sized memory regions) into two "worlds": the **Secure World** (trusted OS, trusted applications, protected memory/peripherals) and the **Normal World** (the regular OS — Android, Linux — and all normal apps).
--> A special CPU mode called **Monitor Mode** handles the actual context switch between worlds, similar in spirit to a hypervisor switching between VMs but implemented as a dedicated hardware-enforced privilege level rather than software virtualization.
--> Unlike SGX's per-enclave granularity, TrustZone security is coarse-grained and system-wide — an entire trusted OS runs in the Secure World, and Normal World code cannot access Secure World memory, peripherals, or interrupts at all, enforced by bus-level access control (the TrustZone-aware memory controller/interconnect, not just the CPU core).
--> This is the dominant TEE architecture in mobile: it's what backs the hardware-isolated portion of Android's **Keystore** (private keys generated/used inside the Secure World, never exported to the Normal World Android OS) and similar "secure element" style storage on countless embedded and IoT devices — conceptually the same "keys unusable outside the boundary" guarantee as an HSM (note 20), just implemented as an ARM CPU mode instead of a discrete physical device.

## Apple Secure Enclave

--> Apple's Secure Enclave (present in iPhones since the A7 chip, and in Apple silicon Macs) is a physically separate coprocessor on the same package as the main CPU, with its OWN dedicated boot ROM, its own encrypted memory, and its own hardware random number generator — an even stronger physical separation than TrustZone's mode-switch model, closer in spirit to a miniaturized, always-on HSM baked directly into the consumer chip.
--> It's what makes Face ID/Touch ID data and the device's file-encryption keys practically un-exfiltratable even by Apple itself in normal operation: biometric templates and the hardware UID-derived keys never leave the Secure Enclave, and the main OS (iOS) only ever receives a yes/no match result or a key-unwrap result, never the underlying secret.
--> This is the same "handle-based" interaction pattern PKCS#11 enforces for HSMs (note 20) — the host OS gets capability, not key material.

## Confidential Computing — The Cloud Industry Term

--> "Confidential computing" is the umbrella term the cloud industry (and the Linux Foundation's Confidential Computing Consortium) uses for running a workload such that even the cloud PROVIDER's own hypervisor, host OS, and privileged infrastructure staff cannot see the guest's memory contents — TEEs are the enabling hardware technology, applied at VM or container scale rather than the fine-grained enclave scale of raw SGX.
--> 1. **AWS Nitro Enclaves** — carve out an isolated, attested compute environment from an EC2 instance, with no persistent storage, no interactive shell access, and no network access except a restricted local channel to its parent instance — deliberately minimal, so the attestation covers a small, auditable surface. Used for things like processing decrypted payment data or private keys that must be provably inaccessible to the rest of the instance, including its own root user.
--> 2. **Azure confidential VMs** — full VMs backed by AMD SEV-SNP or Intel TDX, encrypting entire VM memory such that the Azure hypervisor itself cannot read guest memory, with attestation available via Microsoft Azure Attestation — a much larger trusted computing base than a single SGX enclave (a whole guest OS, not just one enclave's code), trading attestation granularity for compatibility (existing VM workloads can often run largely unmodified).
--> 3. **Google Confidential Computing** (Confidential VMs/GKE) — similarly built on AMD SEV / Intel TDX, extending the same "encrypt guest memory, keep hypervisor blind" guarantee to Compute Engine VMs and GKE nodes.
--> The common thread: whichever mechanism, the promise is "run this workload on infrastructure you don't own/control, with cryptographic (attested) proof that the infrastructure owner cannot see the data" — the cloud-scale generalization of what a single SGX enclave or Secure Enclave chip does at device scale.

## TEE vs MPC vs Homomorphic Encryption

--> All three of these (TEEs here, Secure Multiparty Computation in note 16, Homomorphic Encryption in note 17) solve variations of the same problem — "compute on data without exposing its plaintext to the party doing the computing" — but via fundamentally different mechanisms and with very different cost/trust profiles.

| Approach | Trust model | Performance overhead | Typical fit |
|---|---|---|---|
| TEE (SGX, TrustZone, Nitro Enclaves, confidential VMs) | Trusts the CHIP MANUFACTURER's hardware and firmware correctness and freedom from side-channels; does NOT require trusting the cloud provider's software stack | Low — near-native CPU speed once inside the enclave; main cost is attestation setup and I/O crossing the enclave boundary | Cloud workloads needing near-native performance on sensitive data: payment processing, ML inference on private data, secrets management |
| MPC (note 16) | Trusts NO single party — security holds as long as fewer than the threshold number of participants collude; no special hardware trust required at all | High — significant communication and computation overhead scales with the protocol and number of parties | Cross-organizational computation where no party is willing to run the trusted-hardware role at all (e.g. competing banks computing joint fraud signals) |
| Fully Homomorphic Encryption (note 17) | Trusts NOTHING about the computing party — the server literally never sees plaintext or any decryption capability at any point | Very high — orders of magnitude slower than plaintext computation, though improving | Cases where even trusting a specific piece of hardware (TEE) is unacceptable, or where the compute party must be provably unable to ever access plaintext even under legal compulsion |

--> Practically: pick a TEE when you need speed and can accept "trust this chip vendor's hardware," pick MPC when multiple mutually distrusting organizations must jointly compute something and none will accept a single trusted execution root, and reach for FHE only when the performance cost is acceptable and even hardware-rooted trust is unacceptable — which today usually means narrow, high-value, low-volume computations rather than general workloads.
--> These aren't mutually exclusive in practice — a system can combine them: an FHE ciphertext might be processed with MPC across servers that also happen to run inside TEEs for defense in depth, layering weaker-assumption guarantees on top of a fast baseline rather than picking exactly one.

## Pitfalls

--> 1. **Treating TEE isolation as immune to side channels** — Foreshadow/L1TF (above) proved memory isolation and microarchitectural isolation are different guarantees; an attested, isolated enclave can still leak through caches, timing, or speculative execution unless the CPU generation and microcode specifically mitigate the known channel.
--> 2. **Skipping attestation verification** — provisioning secrets into an enclave without actually validating the attestation quote against the manufacturer's root of trust means you're trusting "some enclave, allegedly," which defeats the entire model; attestation must be checked by the relying party, not assumed.
--> 3. **Confusing "confidential computing" marketing with a specific hardware guarantee** — the term spans SGX-style fine-grained enclaves, whole-VM memory encryption (SEV-SNP/TDX), and container-level isolation (Nitro Enclaves), each with a different trusted computing base size and different side-channel exposure; check which specific technology and CPU generation backs a given cloud offering before relying on its claims.
--> 4. **Assuming a TEE removes the need for key management discipline** — an enclave still needs keys provisioned into it via the same lifecycle discipline as note 21 (rotation, revocation, destruction on enclave teardown); "it's in an enclave" is not a substitute for those controls, only a stronger boundary around where they execute.
