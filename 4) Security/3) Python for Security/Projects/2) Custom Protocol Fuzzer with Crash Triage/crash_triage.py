"""
crash_triage.py

Deduplicates raw crash records from a fuzzing campaign into unique crash
CLASSES, then ranks those classes by how quickly/easily the fuzzer found
them (lower first-seen iteration = easier to find). This is the same basic
job a real crash-triage tool does -- e.g. `afl-collect` grouping AFL crash
files by a stack-hash signature, or CERT's Failure Observation Engine (FOE)
bucketing crashes by faulting instruction + call stack -- just simplified to
work off (exception type, source location) instead of a real stack trace.

Deduplication key: "<ExceptionType>@<function:lineno>" when we have a
server-side log entry, or "network:<signal>" when we only have a network-
level signal and no matching log line. Two crashes with the same key are
considered the SAME underlying bug, even if the exact bytes that triggered
them differ.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from fuzzer_harness import CrashRecord


@dataclass
class CrashClass:
    signature: str
    first_seen_iteration: int
    found_by_mutator: str
    example_input: bytes
    example_seed: bytes
    exception_type: str | None
    location: str | None
    exception_msg: str | None
    occurrences: int = 0
    mutators_that_found_it: set = field(default_factory=set)


def deduplicate(crashes: list[CrashRecord]) -> list[CrashClass]:
    """Collapse raw crash records into unique crash classes keyed by
    signature, keeping the earliest occurrence of each as the representative
    example."""
    classes: dict[str, CrashClass] = {}

    for c in crashes:
        if c.signature not in classes:
            classes[c.signature] = CrashClass(
                signature=c.signature,
                first_seen_iteration=c.iteration,
                found_by_mutator=c.mutator,
                example_input=c.mutated_input,
                example_seed=c.seed,
                exception_type=c.exception_type,
                location=c.location,
                exception_msg=c.exception_msg,
            )
        cls = classes[c.signature]
        cls.occurrences += 1
        cls.mutators_that_found_it.add(c.mutator)

    return sorted(classes.values(), key=lambda cls: cls.first_seen_iteration)


def format_report(crashes: list[CrashRecord], iterations_run: int) -> str:
    """Build the human-readable triage report string."""
    classes = deduplicate(crashes)

    lines = []
    lines.append("=" * 78)
    lines.append("CRASH TRIAGE REPORT")
    lines.append("=" * 78)
    lines.append(f"Iterations run:        {iterations_run}")
    lines.append(f"Total crashing inputs: {len(crashes)}")
    lines.append(f"Unique crash classes:  {len(classes)}")
    lines.append("")

    if not classes:
        lines.append("No crashes found. Try increasing iteration count or widening the seed corpus.")
        return "\n".join(lines)

    for rank, cls in enumerate(classes, start=1):
        lines.append("-" * 78)
        lines.append(f"#{rank}  signature: {cls.signature}")
        lines.append(f"     first found at iteration: {cls.first_seen_iteration}  "
                      f"(lower = fuzzer found this bug faster/more easily)")
        lines.append(f"     times seen this run:       {cls.occurrences}")
        lines.append(f"     found by mutator(s):       {', '.join(sorted(cls.mutators_that_found_it))}")
        if cls.exception_type:
            lines.append(f"     exception type:            {cls.exception_type}")
            lines.append(f"     source location:           {cls.location}")
            lines.append(f"     exception message:         {cls.exception_msg}")
        else:
            lines.append("     (no server-side log entry matched -- network-only signal)")
        lines.append(f"     example seed command:      {cls.example_seed!r}")
        lines.append(f"     example crashing input:    {cls.example_input[:120]!r}")

    lines.append("-" * 78)
    return "\n".join(lines)


def print_report(crashes: list[CrashRecord], iterations_run: int) -> None:
    print(format_report(crashes, iterations_run))
