"""
secure_boot_verifier.py

Walks a boot chain (list[BootStage]) stage by stage and applies one of two
policies, mirroring a real UEFI firmware setting:

  * Secure Boot ENABLED  -- verify() every stage; the INSTANT one stage
    fails, halt immediately. Nothing after a broken link in the chain of
    trust is measured or executed, exactly like real Secure Boot: firmware
    won't hand off to an unverified bootloader, and a verified bootloader
    won't hand off to an unverified kernel.

  * Secure Boot DISABLED -- verify() every stage purely for reporting, but
    load/continue regardless of the result. This models a real machine
    with Secure Boot turned off (or bypassed): a tampered bootloader still
    gets executed, and it can then hand off to whatever kernel/drivers it
    wants, unsigned or not.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from boot_chain_simulator import BootStage, StageResult


@dataclass
class BootRunResult:
    enabled: bool
    stage_results: list  # list[StageResult] measured, in order attempted
    halted_at: Optional[str]  # stage name where boot stopped, if any
    booted_fully: bool


def run_secure_boot_enabled(chain: list[BootStage]) -> BootRunResult:
    """Chain-of-trust enforcement: verify each stage; stop at first failure.

    This is the real Secure Boot behavior: each stage is only measured/run
    because the previous stage already passed. A failure here means the
    chain of trust is broken and everything downstream is untrustworthy,
    so we never even look at the remaining stages.
    """
    results: list[StageResult] = []
    for stage in chain:
        result = stage.verify()
        results.append(result)
        if not result.ok:
            return BootRunResult(
                enabled=True,
                stage_results=results,
                halted_at=stage.name,
                booted_fully=False,
            )
    return BootRunResult(
        enabled=True,
        stage_results=results,
        halted_at=None,
        booted_fully=True,
    )


def run_secure_boot_disabled(chain: list[BootStage]) -> BootRunResult:
    """No enforcement: every stage is measured (so we can still report
    pass/fail for comparison purposes) but execution proceeds regardless
    of the outcome -- modeling Secure Boot turned off / bypassed, where a
    tampered or unsigned stage is allowed to run anyway.
    """
    results: list[StageResult] = [stage.verify() for stage in chain]
    # booted_fully is defined here as "every stage was allowed to load",
    # which with Secure Boot disabled is always true regardless of results.
    return BootRunResult(
        enabled=False,
        stage_results=results,
        halted_at=None,
        booted_fully=True,
    )


def print_run(run: BootRunResult) -> None:
    mode = "ENABLED" if run.enabled else "DISABLED"
    print(f"--- Secure Boot: {mode} ---")
    for result in run.stage_results:
        print(result)
        if run.enabled and not result.ok:
            print(f"         >>> HALT: chain of trust broken at '{result.stage}'. "
                  f"Boot stopped -- no further stages are measured or executed.")
    if run.enabled:
        if run.booted_fully:
            print("Result: system booted -- every stage verified against its "
                  "signed/expected hash.")
        else:
            print(f"Result: BOOT HALTED at '{run.halted_at}'. System does not start.")
    else:
        failed = [r.stage for r in run.stage_results if not r.ok]
        if failed:
            print(f"Result: system booted anyway (Secure Boot disabled) despite "
                  f"verification failures at: {', '.join(failed)}.")
        else:
            print("Result: system booted -- all stages happened to verify cleanly.")
    print()
