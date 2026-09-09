# Python for Security — Practical

> **Legal / ethical scope, restated:** Every script in this folder is written to run **only against
> `127.0.0.1` / `localhost`, your own machine, or infrastructure you personally own and are explicitly
> authorized to test.** Do not point any of these tools at a domain, IP address, network, or system you
> do not own or do not have written authorization to test. Unauthorized scanning, fuzzing, credential
> attacks, or packet sniffing against third-party systems is illegal in most jurisdictions (e.g. under
> the U.S. Computer Fraud and Abuse Act and equivalent laws elsewhere) and violates the ethics already
> established throughout the `Theory` folder. Treat every script here as a lab exercise for a sandbox
> you control.

## Purpose

The `Theory` folder already contains isolated, chapter-by-chapter code snippets. This `Practical` folder
is deliberately small: **a handful of integrated, end-to-end runnable mini-tools** that stitch together
ideas from *multiple* theory chapters into one coherent program, so you can see how the pieces combine
in a realistic (but safe, localhost-scoped) workflow.

## Setup

```bash
pip install requests scapy paramiko flask
```

- `requests`, `paramiko` — third-party, required for scripts 2 and 4.
- `scapy` — only required if you extend script 3 to raw packet crafting/sniffing (see Theory Ch.4); the
  port scanner itself uses only the standard-library `socket` module so it runs without extra privileges.
- `flask` — required only for the local fuzzing target in script 5.
- `hashlib`, `hmac`, `secrets`, `socket`, `subprocess` (used indirectly by `ssh` in script 4's comments)
  are all standard library — no install needed.

## Files and chapter mapping

| File | Theory chapter(s) it integrates | What it does |
|---|---|---|
| `01_password_hashing_toolkit.py` | Ch.2 — Hashing / HMAC / Secure Randomness | Salted `hashlib` password hashing + `hmac.compare_digest` constant-time check + a mini "cracker" demo against a weak unsalted MD5 hash to prove *why* salting/slow-hashing matters. |
| `02_recon_toolkit.py` | Ch.3 — Requests for Recon/Web Testing (+ Ch.1 DNS resolution) | Resolves a domain, fetches HTTP headers and flags missing security headers, probes a few common well-known paths. |
| `03_local_port_scanner_and_banner_grab.py` | Ch.1 — Networking Basics | TCP connect-scan of a port range on `127.0.0.1` plus a banner grab on any open port. |
| `04_ssh_automation_demo.py` | Ch.5 — Automating SSH with Paramiko | Connects to a local SSH server on `localhost`, runs a command, handles connection failures gracefully. |
| `05_fuzzer_against_local_flask_target.py` (+ `target_app.py`) | Ch.7 — Exploit Development / Fuzzing (+ Ch.3 requests) | A tiny local-only Flask app with a deliberately fragile endpoint, and a fuzzer that hammers it with malformed input and reports which payloads broke it. |

## Suggested order to run them

1. `01_password_hashing_toolkit.py` — no setup needed, pure standard library.
2. `02_recon_toolkit.py` — edit the `TARGET_DOMAIN` placeholder at the top before running.
3. `03_local_port_scanner_and_banner_grab.py` — safe by default, only touches `127.0.0.1`.
4. `04_ssh_automation_demo.py` — only works if you've enabled OpenSSH Server locally (see comment in file).
5. `target_app.py` then `05_fuzzer_against_local_flask_target.py` — run the Flask app first in one
   terminal, then run the fuzzer in another.
