# Custom Protocol Fuzzer with Crash Triage

**AUTHORIZED USE ONLY.** Everything here targets `127.0.0.1` — a small,
deliberately buggy TCP server included in this project. Never repoint any
part of this at a host you do not own or are not explicitly authorized to
test. This is a defensive/educational exercise for learning how mutation-based
fuzzing and crash deduplication actually work.

## Real-world scenario

A QA/security engineer has been handed a small custom TCP service that speaks
a homegrown line-based text protocol (`SET key value`, `GET key`, `DEL key`,
a length-prefixed `LEN n payload`, and `PING`). Nobody has time to hand-write
every edge case, so instead they build a fuzzer: take a handful of known-good
"seed" commands, mutate them in a bunch of cheap, randomized ways, fire the
mutated commands at the server as fast as possible, and watch for the
connection dying. Every input that kills the connection gets recorded. Because
raw crash logs from a real fuzzing run are usually **hundreds of near-duplicate
inputs that all trip the same underlying bug**, the last step is triage:
group crashes by *which code path actually failed*, not by the exact bytes
that happened to trigger it, and report which few bugs are actually unique —
which is exactly the workflow behind real tools like `afl-collect` or a crash
management system on top of a fuzzer's output corpus.

This project deliberately ships two known bugs in the target so you can watch
the whole pipeline — mutation, crash detection, log correlation, and
deduplication — work end to end and verify it finds *exactly* the bugs that
were planted.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `target_server.py` | Threaded TCP server implementing the toy protocol; contains 2 intentional bugs; logs every unhandled exception (type, source location, triggering bytes) to `server_crashes.log` before dropping the connection | The buggy service under test — plus the crash/core-dump logging a real OS or crash-reporter would provide |
| `mutators.py` | Pure `bytes -> bytes` mutation strategies: bit flips, byte insert/delete, boundary-value integers, NUL-byte injection, oversized strings | AFL's/honggfuzz's mutation engine (radically simplified — no coverage feedback, just blind mutation of a seed corpus) |
| `fuzzer_harness.py` | Drives the campaign: picks a seed + mutator each iteration, sends the mutated command over a fresh TCP connection, classifies the network outcome (`ok` / `connection_closed` / `connection_reset` / `timeout`), and cross-references `server_crashes.log` to attach an exact exception signature to each crash | A fuzzing harness/driver — e.g. boofuzz's `Session`, or the network-facing half of AFL's `afl-fuzz` when fuzzing a network service via a proxy |
| `crash_triage.py` | Deduplicates raw crash records by signature `<ExceptionType>@<function:lineno>`, ranks unique classes by the iteration each was first found (a proxy for "how easy/quick" it was to hit), and reports which mutator(s) found each one | A crash-deduplication tool like `afl-collect`, or CERT's crash-bucketing step in the Failure Observation Engine (FOE) / CERT Triage Tools (CERT-BFF) |
| `main.py` | Orchestrator: starts the target server in-process, runs the campaign for N iterations with live progress, prints the final triage report | The "run everything and give me a report" wrapper script/CI job around a fuzzing tool |

### The two intentional bugs

1. **Integer-overflow-style bug in `LEN`** (`target_server.py::_cmd_len`) —
   the declared length `n` in `LEN <n> <payload>` is packed into a signed
   32-bit struct field (`struct.pack("!i", n)`) with **no range check**. Any
   `n` outside `[-2**31, 2**31 - 1]` raises `struct.error: argument out of
   range` — a direct analogue of a C length field silently wrapping or
   overflowing a fixed-width integer.
2. **Unhandled exception on malformed structure** (`target_server.py::_cmd_set`) —
   `SET <key> <value>` assumes exactly 3 whitespace-separated tokens and that
   keys never contain a NUL byte. Deleting the separator between tokens (or
   any mutation that collapses/removes a token) raises an uncaught
   `IndexError: list index out of range`.

Neither is caught near the parsing code. The per-connection handler in
`target_server.py` catches the exception one layer up, appends a JSON crash
record to `server_crashes.log`, and closes the socket **without responding** —
from the client/fuzzer's point of view, that's indistinguishable from "the
server just crashed."

(In practice, the mutators also stumble into several *incidental*
`UnicodeDecodeError`s from feeding invalid UTF-8 bytes into `.decode("utf-8")`
calls throughout the dispatcher — those are real, distinct bugs too, and the
triage report below shows them getting their own crash classes exactly as
intended: dedup by exception type *and* source location, not just type.)

## Run it

Requires only the Python standard library (`socket`, `threading`, `struct`,
`json`, `dataclasses`) — no third-party packages.

```bash
cd "Projects/2) Custom Protocol Fuzzer with Crash Triage"
python main.py                 # 4000 iterations against 127.0.0.1:9999 (defaults)
python main.py 3000 9998       # or: python main.py <iterations> <port>
```

`main.py` starts the target server itself (as a background thread inside the
same process), runs the fuzz campaign with live progress printed every ~5% of
iterations, then prints the full crash-triage report. Nothing needs to be
started manually, and nothing ever leaves `127.0.0.1`.

Each run resets `server_crashes.log` first, so the report always reflects
only that run's campaign.

You can also run the pieces independently:

```bash
python target_server.py 9999        # just the target, in one terminal
python fuzzer_harness.py            # smoke-tests a 500-iteration campaign against it
```

## Verified result

Actually run via `python main.py 3000 9998`:

```
[main] campaign finished in 67.2s -- 3000 iterations, 350 crashing inputs recorded.

==============================================================================
CRASH TRIAGE REPORT
==============================================================================
Iterations run:        3000
Total crashing inputs: 350
Unique crash classes:  7

------------------------------------------------------------------------------
#1  signature: UnicodeDecodeError@_cmd_del:219
     first found at iteration: 1     times seen: 28   found by: bit_flip, byte_insert
------------------------------------------------------------------------------
#2  signature: IndexError@_cmd_set:202                       <-- planted bug #2
     first found at iteration: 8     times seen: 47   found by: bit_flip, byte_delete
     example seed command:      b'SET user1 alice'
     example crashing input:    b'SET useice'
------------------------------------------------------------------------------
#3  signature: ValueError@_cmd_len:187
     first found at iteration: 13    times seen: 91   found by: bit_flip, byte_delete, byte_insert, null_byte_injection
------------------------------------------------------------------------------
#4  signature: UnicodeDecodeError@_cmd_get:213
     first found at iteration: 26    times seen: 32   found by: bit_flip, byte_insert
------------------------------------------------------------------------------
#5  signature: struct.error@_cmd_len:188                      <-- planted bug #1
     first found at iteration: 27    times seen: 45   found by: boundary_value_integer, byte_delete
     example seed command:      b'LEN 10 0123456789'
     example crashing input:    b'LEN 9223372036854775807 0123456789'
------------------------------------------------------------------------------
#6  signature: UnicodeDecodeError@_cmd_set:202
     first found at iteration: 70    times seen: 65   found by: bit_flip, byte_insert
------------------------------------------------------------------------------
#7  signature: UnicodeDecodeError@_cmd_set:201
     first found at iteration: 85    times seen: 42   found by: bit_flip, byte_insert
------------------------------------------------------------------------------
```

Both planted bugs were found and correctly deduplicated into their own unique
classes: **350 raw crashing inputs collapsed down to 7 unique crash
classes**, with the intentional `struct.error` (integer-overflow-style, found
by `boundary_value_integer` — exactly the mutator built for this) and
`IndexError` (malformed-structure, found by `byte_delete`/`bit_flip`)
appearing as classes #5 and #2 respectively. The `boundary_value_integer`
mutator's very first boundary value tried against `LEN` (`2**63 - 1`) was
enough to trip the overflow bug, confirming targeted mutation finds
structure-aware bugs far faster than pure randomness would.

Re-running produces the same 7 crash classes with different example bytes and
slightly different first-seen iterations (mutation is randomized), which is
itself a good demonstration of why deduplication-by-signature — not by exact
input — is what makes a triage report actually readable.

## Things to try changing

- **Add coverage feedback.** Right now this is a "dumb" blind mutator with no
  idea which code paths it has already exercised. Track which `_cmd_*`
  branch each input reached (the server already knows) and bias mutation
  toward inputs that reach less-explored branches — a tiny step toward what
  AFL's coverage-guided fuzzing actually does.
- **Add a mutator that composes seeds** (splice two different seed commands
  together) instead of only mutating one at a time — often finds bugs no
  single-seed mutation would.
- **Make the crash signature include a call-stack hash**, not just the
  deepest frame, to see how much that changes deduplication once the target
  has more layers of function calls between the socket read and the bug.
- **Fix one of the two planted bugs** (e.g. range-check `n` before
  `struct.pack`) and re-run — confirm that crash class disappears from the
  report while the other one still shows up.
- **Increase `iterations`** in `main.py` and watch `occurrences` climb for
  existing classes while `unique crash classes` stays flat — a good visual
  for why raw crash counts are a misleading fuzzing metric compared to unique
  bugs found.
