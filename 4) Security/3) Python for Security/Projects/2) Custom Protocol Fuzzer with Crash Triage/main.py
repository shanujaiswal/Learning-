"""
main.py

AUTHORIZED USE ONLY -- everything in this project targets 127.0.0.1 only.

Entry point that ties the whole campaign together:

  1. Starts target_server.py's ProtocolServer in a background thread.
  2. Runs the fuzzing campaign (fuzzer_harness.py) for N iterations,
     printing live progress.
  3. Feeds every crash it found into crash_triage.py, which deduplicates
     them into unique crash classes and ranks them by how quickly they
     were found.

Usage:
    python main.py [iterations] [port]

Defaults: iterations=4000, port=9999.
"""

from __future__ import annotations

import os
import sys
import time

from crash_triage import print_report
from fuzzer_harness import CRASH_LOG_PATH, run_campaign
from target_server import HOST, ProtocolServer


def reset_crash_log() -> None:
    """Start with a clean crash log so the triage report reflects only this
    run, not leftovers from a previous one."""
    if os.path.exists(CRASH_LOG_PATH):
        os.remove(CRASH_LOG_PATH)


def wait_until_up(host: str, port: int, timeout_seconds: float = 5.0) -> bool:
    import socket

    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                s.settimeout(0.5)
                s.connect((host, port))
                return True
        except OSError:
            time.sleep(0.1)
    return False


def main() -> None:
    iterations = int(sys.argv[1]) if len(sys.argv) > 1 else 4000
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 9999

    print("=" * 78)
    print("Custom Protocol Fuzzer with Crash Triage")
    print(f"Target: {HOST}:{port}  (local practice target only)")
    print(f"Iterations requested: {iterations}")
    print("=" * 78)

    reset_crash_log()

    server = ProtocolServer(HOST, port)
    server.start()
    print(f"[main] target_server started on {HOST}:{port}")

    if not wait_until_up(HOST, port):
        print("[main] ERROR: target server did not come up in time.")
        sys.exit(1)

    print("[main] target server is up. Starting fuzz campaign...\n")

    start = time.time()
    result = run_campaign(HOST, port, iterations=iterations, progress_every=max(1, iterations // 20))
    elapsed = time.time() - start

    print(f"\n[main] campaign finished in {elapsed:.1f}s -- "
          f"{result.iterations_run} iterations, {len(result.crashes)} crashing inputs recorded.\n")

    print_report(result.crashes, result.iterations_run)

    server.stop()


if __name__ == "__main__":
    main()
