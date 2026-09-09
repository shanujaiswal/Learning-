"""
target_server.py

AUTHORIZED USE ONLY. This is a deliberately buggy practice target that only
ever binds to 127.0.0.1. Never repoint the fuzzer harness at a host you do
not own or are not explicitly authorized to test.

A tiny threaded TCP server speaking a simple line-based text protocol:

    SET <key> <value>   -- store a value
    GET <key>            -- retrieve a value
    DEL <key>            -- delete a value
    LEN <n> <payload...> -- a length-prefixed command (declares a payload
                            length `n` before the actual echo/ack logic)
    PING                 -- health check, replies PONG

Commands are newline-terminated ASCII/UTF-8 text. This file contains TWO
INTENTIONAL bugs for the fuzzer in this project to find:

  1. Integer-overflow-style bug in the LEN command (see `_cmd_len`): the
     attacker-controlled declared length is packed into a fixed-width signed
     32-bit struct field with no range check first. Any length outside
     [-2**31, 2**31 - 1] raises `struct.error`, mirroring the classic C bug
     where a length field wraps/overflows a fixed-width integer.

  2. Unhandled-exception bug on malformed structure / embedded NUL bytes in
     SET (see `_cmd_set`): the handler assumes every SET command has exactly
     3 whitespace-separated tokens and that keys never contain NUL bytes.
     A NUL byte with nothing after it, or a command missing the value token
     entirely, blows up with an uncaught IndexError.

Neither bug is caught anywhere near the parsing code -- the exception
propagates up to the per-connection handler, which logs it (with a
timestamp, exception type, source location, and the exact bytes that
triggered it) to `server_crashes.log` as one JSON object per line, then
closes the connection WITHOUT sending a response. From the fuzzer's point of
view that looks exactly like a real crash: the connection just goes away.
"""

from __future__ import annotations

import json
import os
import socket
import struct
import sys
import threading
import time
import traceback
from datetime import datetime, timezone

HOST = "127.0.0.1"
DEFAULT_PORT = 9999
RECV_BUFSIZE = 4096
CONN_TIMEOUT_SECONDS = 2.0

CRASH_LOG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "server_crashes.log")

_log_lock = threading.Lock()


def log_crash(command_bytes: bytes, exc: BaseException) -> dict:
    """Append one JSON-line crash record and return it. This file is the
    'server process exception logged' crash signal the harness/triage tool
    correlates against network-level crash detection (connection drop)."""
    tb = traceback.extract_tb(exc.__traceback__)
    frame = tb[-1] if tb else None
    location = f"{frame.name}:{frame.lineno}" if frame else "unknown"
    exc_type = type(exc)
    # Qualify with the module for anything that isn't a builtin (e.g.
    # struct.error's __name__ is just "error", which is meaningless on its
    # own in a triage report).
    type_name = exc_type.__name__ if exc_type.__module__ in ("builtins",) else f"{exc_type.__module__}.{exc_type.__name__}"
    record = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "exception_type": type_name,
        "exception_msg": str(exc),
        "location": location,
        "command_hex": command_bytes.hex(),
        "command_repr": repr(command_bytes[:200]),
    }
    with _log_lock:
        with open(CRASH_LOG_PATH, "a", encoding="utf-8") as f:
            f.write(json.dumps(record) + "\n")
    return record


class ProtocolServer:
    """Threaded TCP server: one thread accepting connections, one thread per
    open connection. Deliberately minimal -- this is a practice target, not
    production code."""

    def __init__(self, host: str = HOST, port: int = DEFAULT_PORT):
        self.host = host
        self.port = port
        self.store: dict[str, str] = {}
        self.store_lock = threading.Lock()
        self._sock: socket.socket | None = None
        self._stop = threading.Event()
        self._accept_thread: threading.Thread | None = None

    def start(self) -> None:
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._sock.bind((self.host, self.port))
        self._sock.listen(64)
        self._sock.settimeout(1.0)
        self._accept_thread = threading.Thread(target=self._accept_loop, daemon=True)
        self._accept_thread.start()

    def stop(self) -> None:
        self._stop.set()
        try:
            if self._sock is not None:
                self._sock.close()
        except OSError:
            pass

    def _accept_loop(self) -> None:
        while not self._stop.is_set():
            try:
                conn, _addr = self._sock.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            threading.Thread(target=self._handle_conn, args=(conn,), daemon=True).start()

    def _handle_conn(self, conn: socket.socket) -> None:
        conn.settimeout(CONN_TIMEOUT_SECONDS)
        buf = b""
        try:
            with conn:
                while not self._stop.is_set():
                    try:
                        chunk = conn.recv(RECV_BUFSIZE)
                    except socket.timeout:
                        break
                    except OSError:
                        break
                    if not chunk:
                        break
                    buf += chunk
                    while b"\n" in buf:
                        line, buf = buf.split(b"\n", 1)
                        line = line.rstrip(b"\r")
                        if not line:
                            continue
                        try:
                            response = self._dispatch(line)
                        except Exception as exc:  # noqa: BLE001 -- this IS the crash boundary
                            log_crash(line, exc)
                            # Simulate a crashed handler: drop the connection,
                            # send nothing back. This is what "the server
                            # stopped responding" looks like to a client.
                            return
                        conn.sendall(response + b"\n")
        except OSError:
            return

    def _dispatch(self, line: bytes) -> bytes:
        parts = line.split(b" ")
        cmd = parts[0].decode("ascii", errors="replace").upper()

        if cmd == "LEN":
            return self._cmd_len(parts)
        if cmd == "SET":
            return self._cmd_set(parts)
        if cmd == "GET":
            return self._cmd_get(parts)
        if cmd == "DEL":
            return self._cmd_del(parts)
        if cmd == "PING":
            return b"PONG"
        return b"ERR unknown command"

    def _cmd_len(self, parts: list[bytes]) -> bytes:
        """Protocol: LEN <n> <payload...>

        A real length-prefixed protocol packs the declared length into a
        fixed-width header field before doing anything else with it.
        INTENTIONAL BUG: `n` is attacker-controlled and never range-checked
        before being packed into a signed 32-bit struct field -- classic
        integer-overflow-style parsing bug. struct.pack("!i", n) raises
        struct.error for any n outside [-2**31, 2**31 - 1].
        """
        n = int(parts[1])
        header = struct.pack("!i", n)
        return b"LEN-OK " + header.hex().encode("ascii")

    def _cmd_set(self, parts: list[bytes]) -> bytes:
        """Protocol: SET <key> <value>

        INTENTIONAL BUG: assumes the command always has exactly 3
        whitespace-separated tokens, and that keys never contain an embedded
        NUL byte. A missing token raises an uncaught IndexError. A key that
        contains a NUL byte with nothing after it also raises IndexError, in
        a legacy 'revision suffix' normalization step that blindly assumes a
        NUL is always followed by more bytes.
        """
        key = parts[1].decode("utf-8", errors="strict")
        value = parts[2].decode("utf-8", errors="strict")

        normalized = key.encode("utf-8")
        if b"\x00" in normalized:
            _revision = normalized.split(b"\x00")[1]  # IndexError if nothing follows the NUL

        with self.store_lock:
            self.store[key] = value
        return b"OK"

    def _cmd_get(self, parts: list[bytes]) -> bytes:
        key = parts[1].decode("utf-8", errors="strict")
        with self.store_lock:
            value = self.store.get(key)
        return (b"OK " + value.encode("utf-8")) if value is not None else b"NOTFOUND"

    def _cmd_del(self, parts: list[bytes]) -> bytes:
        key = parts[1].decode("utf-8", errors="strict")
        with self.store_lock:
            self.store.pop(key, None)
        return b"OK"


def main() -> None:
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT
    server = ProtocolServer(HOST, port)
    server.start()
    print(f"[target_server] listening on {HOST}:{port} (crash log: {CRASH_LOG_PATH})")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        server.stop()


if __name__ == "__main__":
    main()
