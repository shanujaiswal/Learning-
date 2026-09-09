"""
fuzzer_harness.py

Sends mutated protocol commands at target_server.py (127.0.0.1 only),
detects crashes, and records exactly which input caused each one.

A "crash" is detected purely from the network side -- no direct access to
the target process is assumed, matching how you'd fuzz a real remote-ish
service:

  - the server closes the connection without sending a response (our target
    does this deliberately when a handler raises), or
  - the connection is reset / send fails outright, or
  - the server stops responding within the timeout window.

Because we also control the practice target, we additionally cross-reference
`server_crashes.log` (written by target_server.py) to recover the *exact*
exception type + source location for each crash, which is what gives
crash_triage.py a real signature to deduplicate on instead of just "it broke
somehow". A production fuzzer against a real black-box target would only have
the network-level signal; the log correlation here is the "you happen to
also own the target in this lab" shortcut.
"""

from __future__ import annotations

import json
import os
import random
import socket
import time
from dataclasses import dataclass, field

from mutators import MUTATORS, mutate

HOST = "127.0.0.1"
CONNECT_TIMEOUT_SECONDS = 1.0
RECV_TIMEOUT_SECONDS = 1.0

CRASH_LOG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "server_crashes.log")

# Seed corpus: a handful of valid/near-valid protocol commands. Mutators
# chew on these rather than generating input completely from scratch --
# mutation-based fuzzing starts from *plausible* inputs and perturbs them,
# which finds parser-deep bugs far faster than pure random generation.
SEED_COMMANDS: list[bytes] = [
    b"SET key value",
    b"GET key",
    b"DEL key",
    b"LEN 5 hello",
    b"PING",
    b"SET user1 alice",
    b"LEN 10 0123456789",
]


@dataclass
class CrashRecord:
    iteration: int
    mutator: str
    seed: bytes
    mutated_input: bytes
    network_signal: str
    signature: str
    exception_type: str | None = None
    location: str | None = None
    exception_msg: str | None = None


@dataclass
class CampaignResult:
    iterations_run: int = 0
    crashes: list[CrashRecord] = field(default_factory=list)


def _tail_new_crash_log_entries(offset: int) -> tuple[list[dict], int]:
    """Read any JSON-line crash records appended to the log since `offset`
    bytes into the file. Returns (records, new_offset)."""
    if not os.path.exists(CRASH_LOG_PATH):
        return [], offset
    with open(CRASH_LOG_PATH, "rb") as f:
        f.seek(offset)
        new_bytes = f.read()
        new_offset = f.tell()
    records = []
    for line in new_bytes.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            records.append(json.loads(line.decode("utf-8")))
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue
    return records, new_offset


def _crash_log_offset() -> int:
    if not os.path.exists(CRASH_LOG_PATH):
        return 0
    return os.path.getsize(CRASH_LOG_PATH)


def send_one(host: str, port: int, payload: bytes) -> str:
    """Send one payload (a single protocol line) at the target and classify
    the outcome purely from network behaviour. Returns a short network
    signal string: 'ok', 'connection_closed', 'connection_reset', 'timeout',
    or 'connect_failed'."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.settimeout(CONNECT_TIMEOUT_SECONDS)
            sock.connect((host, port))
            sock.settimeout(RECV_TIMEOUT_SECONDS)
            try:
                sock.sendall(payload + b"\n")
            except (ConnectionResetError, BrokenPipeError, OSError):
                return "connection_reset"
            try:
                response = sock.recv(4096)
            except socket.timeout:
                return "timeout"
            except (ConnectionResetError, OSError):
                return "connection_reset"
            if response == b"":
                return "connection_closed"
            return "ok"
    except (ConnectionRefusedError, socket.timeout, OSError):
        return "connect_failed"


def run_campaign(host: str, port: int, iterations: int, progress_every: int = 200,
                  seed: int | None = None) -> CampaignResult:
    """Run the fuzzing campaign for `iterations` mutated inputs and return
    every crash found, in the order discovered."""
    if seed is not None:
        random.seed(seed)

    result = CampaignResult()
    log_offset = _crash_log_offset()

    for i in range(1, iterations + 1):
        seed_cmd = random.choice(SEED_COMMANDS)
        mutator_name, mutated = mutate(seed_cmd)

        signal = send_one(host, port, mutated)
        result.iterations_run = i

        if signal in ("connection_closed", "connection_reset", "timeout"):
            # Give the log write (which happens just before the server closes
            # the socket) a brief moment to land on disk, then read anything
            # new since the last crash.
            time.sleep(0.02)
            new_entries, log_offset = _tail_new_crash_log_entries(log_offset)

            if new_entries:
                entry = new_entries[-1]
                signature = f"{entry['exception_type']}@{entry['location']}"
                record = CrashRecord(
                    iteration=i,
                    mutator=mutator_name,
                    seed=seed_cmd,
                    mutated_input=mutated,
                    network_signal=signal,
                    signature=signature,
                    exception_type=entry["exception_type"],
                    location=entry["location"],
                    exception_msg=entry["exception_msg"],
                )
            else:
                # No matching server-side log entry -- fall back to a
                # network-only signature (this is the realistic black-box
                # case: you don't always get to see the target's internals).
                signature = f"network:{signal}"
                record = CrashRecord(
                    iteration=i,
                    mutator=mutator_name,
                    seed=seed_cmd,
                    mutated_input=mutated,
                    network_signal=signal,
                    signature=signature,
                )
            result.crashes.append(record)
        else:
            # Keep the offset in sync even when nothing crashed, in case the
            # server ever logs something asynchronously.
            _, log_offset = _tail_new_crash_log_entries(log_offset)

        if progress_every and i % progress_every == 0:
            print(f"[fuzzer] iteration {i}/{iterations} -- crashes so far: {len(result.crashes)}")

    return result


if __name__ == "__main__":
    # Standalone smoke test: assumes target_server.py is already running on
    # the default port.
    res = run_campaign(HOST, 9999, iterations=500)
    print(f"Done. {len(res.crashes)} crashes out of {res.iterations_run} iterations.")
    for c in res.crashes[:10]:
        print(f"  #{c.iteration} [{c.mutator}] {c.signature} input={c.mutated_input[:60]!r}")
