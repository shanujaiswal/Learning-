### SSH Protocol Cryptography

--> SSH (Secure Shell) is what TLS is to browsers, but for remote shell access, file transfer, and port forwarding. It layers cleanly into two protocols: a **transport layer protocol** (encryption, integrity, server authentication — analogous to note 06's TLS handshake) and a **connection protocol** (multiplexed logical channels running on top, once the transport is secure).
--> Same underlying primitives as everywhere else in this track — key exchange (note 06), signatures (note 05), symmetric encryption (note 03) — SSH is mostly a specific arrangement of those primitives plus its own authentication and trust model.

## Transport Layer Phases

--> 1. **Version exchange** — each side sends a plaintext line like `SSH-2.0-OpenSSH_9.6`, establishing protocol version and implementation before anything is encrypted.
--> 2. **Algorithm negotiation (KEXINIT)** — both sides send an ordered list of supported algorithms (key exchange method, host key type, ciphers, MACs, compression). Each side picks the first mutually supported option from the client's preference order. This is conceptually identical to TLS cipher-suite negotiation in note 06, just SSH-specific naming (e.g. `curve25519-sha256`, `rsa-sha2-512`, `chacha20-poly1305@openssh.com`).
--> 3. **Key exchange** — runs the negotiated KEX method to derive a shared secret, then a symmetric session key and MAC key via a hash-based derivation (conceptually the same role HKDF plays in note 07). This also authenticates the SERVER via its host key signature over the exchange hash — the client's identity is NOT yet established at this point.
--> 4. **Connection layer** — once the transport is encrypted and integrity-protected, the connection protocol opens **channels** multiplexed over the single encrypted transport connection: a shell session, an SFTP subsystem, a forwarded TCP port, or a forwarded agent socket can all coexist on one underlying connection.

```
Client                                   Server
  |--- SSH-2.0-OpenSSH_9.6 ------------->|   version strings (plaintext)
  |<-------------- SSH-2.0-OpenSSH_9.6 --|
  |--- KEXINIT (algo lists) ------------>|
  |<------------------------- KEXINIT ---|
  |--- ECDH client public value -------->|   curve25519-sha256 typically
  |<--- ECDH server public value + ------|
  |     host key signature over exchange |
  |     hash (server authenticated here) |
  |======== encrypted from here ========|
  |--- user auth requests -------------->|   (password / pubkey / cert)
  |======== channels multiplexed ========|   shell, sftp, port-forward, agent
```

## Key Exchange — Classic DH Groups vs Curve25519

--> Older SSH KEX methods (`diffie-hellman-group14-sha256`, etc.) use classic finite-field Diffie-Hellman over fixed, named prime groups — the same modular-exponentiation math worked through by hand in note 06's DH walkthrough.
--> Fixed, well-known DH groups became a liability: **Logjam**-class attacks showed that precomputation against small/shared groups makes breaking individual handshakes cheaper than expected, and smaller legacy groups (1024-bit) are within reach of well-funded attackers.
--> Modern SSH defaults to `curve25519-sha256` — the same X25519 elliptic-curve Diffie-Hellman primitive covered in note 08 — for the same reasons TLS 1.3 moved to X25519: smaller public values, faster computation, no risk of weak/backdoored group parameters, and no small-subgroup pitfalls because Curve25519 clears the cofactor by construction.
--> This mirrors exactly the RSA-transport-to-ECDHE move in TLS (note 06) and the general RSA-to-ECC size/speed argument in note 08 — SSH just took the same path a few years apart, under KEX method names instead of TLS cipher suites.

## Host Key Verification and Trust-On-First-Use

--> Unlike TLS, SSH generally has **no CA-signed certificate chain by default** (note 09's PKI model is optional in SSH via an SSH CA, covered below) — the server just presents a long-lived **host key**, and the client decides whether to trust it.
--> On first connection, the client has no prior record of the server's host key. It shows the key's **fingerprint** (a hash of the public key, e.g. SHA256) and asks the user to confirm it out-of-band. This is **Trust On First Use (TOFU)**: whatever key is accepted now becomes the trusted key for that host going forward.
--> The fingerprint only proves "this is the SAME key you saw and accepted last time" — it does NOT prove the key belongs to the legitimate server unless the fingerprint was verified through a genuinely independent channel (e.g. the server admin reading it aloud on a phone call, or a provisioning system publishing it out-of-band).
--> `~/.ssh/known_hosts` stores, per host, the host key type and the public key itself (not a certificate, not a fingerprint alone) — every subsequent connection compares the presented key byte-for-byte against this stored value.
--> **The narrow but real MITM window**: an attacker positioned on the network path during that FIRST connection can present their own host key, and the client — having nothing to compare against yet — may accept it, silently pinning trust to the attacker's key for all future sessions. After that first accepted connection, the attack surface for a passive MITM effectively disappears, because any later mismatch trips a loud warning.
--> `StrictHostKeyChecking` controls this behavior:

```
# ~/.ssh/config

# "accept-new": silently trust an UNSEEN host's key (TOFU with no prompt),
# but hard-fail if a PREVIOUSLY KNOWN host's key ever changes — a
# reasonable default for automation that still catches real MITM/rotation
# surprises after the first contact.
Host *
    StrictHostKeyChecking accept-new

# "yes": refuse to connect to any host not already in known_hosts at all —
# strongest posture, requires host keys to be provisioned out-of-band
# (e.g. baked into a VM image or pushed by a config management tool).
Host prod-*
    StrictHostKeyChecking yes
```

--> **Host key rotation in practice is a recurring operational headache**: when a server is rebuilt, migrated, or simply regenerates its host key, every client that previously connected now gets a scary "REMOTE HOST IDENTIFICATION HAS CHANGED" warning and refuses to connect — correctly, since this is indistinguishable from an actual MITM from the client's point of view. Fixes are either manually removing the stale `known_hosts` entry (risky habit to normalize) or proactively rotating keys via signed `SSHFP` DNS records or an SSH CA so clients have a real signal to trust the new key.

## User Authentication Methods

--> 1. **Password** — simplest, weakest; vulnerable to brute force and password reuse (see note 04's password-storage failures on the server side too).
--> 2. **Public key** — client proves possession of a private key whose matching public key the server already trusts (listed in `authorized_keys`). No secret ever crosses the wire.
--> 3. **Keyboard-interactive** — a generic challenge/response mechanism used for things like OTP or PAM-based multi-factor prompts; flexible but essentially a password-like exchange dressed up.
--> 4. **Certificate-based (SSH CA)** — instead of trusting individual raw public keys one by one, the server trusts a single **SSH CA public key**, and each user/host key is signed by that CA into a short-lived certificate. This solves the exact `known_hosts`/`authorized_keys` sprawl and revocation problem that plain TOFU key trust has, the same way note 09's PKI solves it for TLS — except SSH certificates are typically far shorter-lived (minutes to hours) and issued on demand, rather than the multi-month/year lifetimes typical of TLS certs.

## How Public-Key Authentication Actually Proves Possession

--> The mechanics are the same challenge-sign-verify pattern used for the RSA/ECDSA signature verification walked through in note 05 — but with one SSH-specific twist that matters.
--> During user auth, the server does not hand the client an arbitrary nonce to sign in isolation. The client instead signs a blob that includes the **session identifier** derived from the transport-layer key exchange (unique to this specific connection) plus the auth request fields.
--> **Why sign a session-specific blob instead of an arbitrary challenge**: if the signed message were just a generic random challenge, nothing would stop a malicious server from replaying a signature it collected from you against a DIFFERENT server session (or reusing it to fake authenticating elsewhere). Binding the signature to this session's unique identifier makes a captured signature useless outside the exact session it was produced for — an SSH-specific defense against signature replay that note 05's generic signature description doesn't need to cover, because SSH signatures are single-use authentication proofs rather than durable statements about a message.
--> Client-side flow: server sends a public-key auth request naming an offered key -> server (or client, depending on direction of the flow) constructs the session-bound blob -> client's private key signs it -> server verifies using the public key already listed in `authorized_keys` -> success only if both the signature verifies AND the key is authorized.

```bash
# Client offers an Ed25519 key (see note 08) instead of RSA for auth —
# smaller, faster, and immune to the RSA/ECDSA nonce and padding pitfalls
# discussed in notes 05 and 08.
ssh-keygen -t ed25519 -C "vanisha@warpx.ai" -f ~/.ssh/id_ed25519

# Public half goes on the server, private half never leaves the client
cat ~/.ssh/id_ed25519.pub >> ~/.ssh/authorized_keys   # on the SERVER
```

## The SSH Agent and Agent Forwarding

--> The **ssh-agent** is a background process holding decrypted private keys in memory, so you unlock a passphrase-protected key once per login session instead of on every connection. Client tools talk to it over a local Unix socket (`SSH_AUTH_SOCK`), asking it to sign challenges — the private key material itself never leaves the agent process.
--> **Agent forwarding** (`ForwardAgent yes` / `ssh -A`) extends that same socket over the SSH connection to a REMOTE host: a program running on the remote host can ask — through the forwarded socket — YOUR local agent to sign an authentication challenge, letting you hop from that remote host to a THIRD host using your local key, without ever copying the private key to the remote host.

```
# ~/.ssh/config
Host jump-only-agent
    HostName bastion.example.com
    ForwardAgent yes   # remote host can now relay signing requests to YOUR agent
```

--> **The well-known danger is not key theft — it's live key USE.** The private key material genuinely never leaves your laptop. But while your session on the intermediate host is open, *anything* with access to that host's forwarded agent socket (a root user, another process, an attacker who has compromised that intermediate host) can ask your agent to sign a challenge for ANY host your key is authorized on, and your agent will oblige — it has no idea the request came from a compromised host rather than a legitimate hop you initiated. A compromised bastion effectively becomes able to impersonate you, live, for as long as your forwarded session stays open — without ever exfiltrating a single byte of key material.
--> **Mitigation**: scope `ForwardAgent` narrowly (only to hosts you genuinely trust as the immediate hop, never blanket-enabled via `Host *`), and prefer `ProxyJump` for multi-hop access instead — `ProxyJump` tunnels the connection THROUGH an intermediate host without exposing your agent socket to it at all, since the intermediate host is only relaying encrypted bytes, not participating in any signing.

```
# ~/.ssh/config — safer multi-hop pattern: no agent exposed to the bastion
Host target-server
    HostName 10.0.5.12
    User deploy
    ProxyJump bastion.example.com   # bastion only relays the TCP stream;
                                     # your agent is never reachable from it
```

## Method Comparison

| Method | Secret crosses network? | Revocation | Typical use |
|---|---|---|---|
| Password | Hash of it, effectively | Change password | Discouraged; brute-forceable |
| Public key (TOFU-trusted) | Never | Remove line from `authorized_keys` | Default for most SSH access |
| Certificate-based (SSH CA) | Never | Short cert lifetime / CA revocation list | Fleet-scale, ephemeral infra |
| Keyboard-interactive/MFA | Depends on backend | Backend-specific | Compliance-driven added factor |

## Pitfalls

--> 1. **Blind TOFU acceptance** — clicking "yes" through an unfamiliar host key prompt without any out-of-band verification defeats the entire point of host key checking; it only protects you if the FIRST trust decision was actually verified.
--> 2. **Stale `known_hosts` "fixes"** — habitually deleting the offending line whenever a host key mismatch warning appears trains you (and any script doing this automatically) to ignore the exact signal that would catch a real MITM.
--> 3. **Blanket agent forwarding** — `ForwardAgent yes` under `Host *` in a config file forwards your agent to every host you ever connect to, including throwaway or shared boxes; scope it per-host or replace it with `ProxyJump` entirely.
--> 4. **Legacy KEX/host key algorithms left enabled** — servers still permitting `diffie-hellman-group1-sha1` or `ssh-rsa` (SHA-1) host keys for backward compatibility reopen exactly the weak-group and weak-hash classes of attack that curve25519-sha256 and `rsa-sha2-*`/Ed25519 were adopted to close.
