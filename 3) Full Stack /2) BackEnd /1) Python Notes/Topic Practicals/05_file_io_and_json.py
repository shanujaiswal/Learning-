"""
05_file_io_and_json.py

Maps to Theory chapters:
    12 Error Handling Virtual Env and File IO.md
    11 Math JSON and RegEx.md

Demonstrates:
    - Writing/reading a JSON file
    - Writing/reading a CSV-like text file
    - Proper `with` context-manager usage throughout
    - Cleanup of any files this script creates

No external dependencies (uses only the standard library: json, os, re, tempfile).
Run directly:
    python "05_file_io_and_json.py"
"""

from __future__ import annotations
import json
import os
import re
import tempfile


def demo_json_roundtrip(work_dir: str) -> None:
    print("--- JSON round-trip ---")
    json_path = os.path.join(work_dir, "students.json")

    students = [
        {"name": "Asha", "subject": "Math", "score": 88},
        {"name": "Ravi", "subject": "English", "score": 82},
    ]

    # Write JSON using a context manager.
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(students, f, indent=2)
    print(f"Wrote JSON to {json_path}")

    # Read it back.
    with open(json_path, "r", encoding="utf-8") as f:
        loaded = json.load(f)

    print("Loaded JSON content:")
    for row in loaded:
        print("  ", row)

    assert loaded == students, "Round-tripped JSON should match the original data"
    print("JSON round-trip verified OK.")


def demo_csv_like_text_file(work_dir: str) -> None:
    print("\n--- CSV-like text file ---")
    csv_path = os.path.join(work_dir, "students.csv")

    header = ["name", "subject", "score"]
    rows = [
        ["Asha", "Math", "88"],
        ["Ravi", "English", "82"],
        ["Meera", "Science", "95"],
    ]

    with open(csv_path, "w", encoding="utf-8", newline="") as f:
        f.write(",".join(header) + "\n")
        for row in rows:
            f.write(",".join(row) + "\n")
    print(f"Wrote CSV-like file to {csv_path}")

    parsed_rows: list[dict[str, str]] = []
    with open(csv_path, "r", encoding="utf-8") as f:
        lines = [line.strip() for line in f if line.strip()]

    columns = lines[0].split(",")
    for line in lines[1:]:
        values = line.split(",")
        parsed_rows.append(dict(zip(columns, values)))

    print("Parsed rows:")
    for row in parsed_rows:
        print("  ", row)


def demo_regex_on_file_content(work_dir: str) -> None:
    print("\n--- RegEx over file content ---")
    log_path = os.path.join(work_dir, "app.log")

    log_lines = [
        "2026-08-10 10:00:01 INFO Application started",
        "2026-08-10 10:00:05 ERROR Failed to connect to db: timeout",
        "2026-08-10 10:00:07 WARNING Retry attempt 1",
        "2026-08-10 10:00:09 ERROR Failed to connect to db: refused",
        "2026-08-10 10:00:12 INFO Connected successfully",
    ]

    with open(log_path, "w", encoding="utf-8") as f:
        f.write("\n".join(log_lines) + "\n")

    error_pattern = re.compile(r"^(?P<timestamp>[\d-]+ [\d:]+) ERROR (?P<message>.+)$")

    errors_found: list[tuple[str, str]] = []
    with open(log_path, "r", encoding="utf-8") as f:
        for line in f:
            match = error_pattern.match(line.strip())
            if match:
                errors_found.append((match.group("timestamp"), match.group("message")))

    print(f"Found {len(errors_found)} ERROR line(s):")
    for timestamp, message in errors_found:
        print(f"  [{timestamp}] {message}")


def cleanup(work_dir: str, filenames: list[str]) -> None:
    print("\n--- Cleanup ---")
    for name in filenames:
        path = os.path.join(work_dir, name)
        try:
            os.remove(path)
            print(f"Removed {path}")
        except FileNotFoundError:
            print(f"Already removed (or never created): {path}")


def main() -> None:
    # Use a temporary directory so this script never litters the repo,
    # and always cleans up after itself even if something goes wrong.
    with tempfile.TemporaryDirectory(prefix="python_practical_") as work_dir:
        print(f"Working in temporary directory: {work_dir}\n")
        try:
            demo_json_roundtrip(work_dir)
            demo_csv_like_text_file(work_dir)
            demo_regex_on_file_content(work_dir)
        finally:
            # TemporaryDirectory removes itself automatically on context exit,
            # but we also demonstrate explicit cleanup of individual files
            # for clarity, since real-world code often manages files manually.
            cleanup(work_dir, ["students.json", "students.csv", "app.log"])


if __name__ == "__main__":
    main()
