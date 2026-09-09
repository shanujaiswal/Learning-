"""
config_auditor.py

Applies the CIS-style benchmark (cis_ssh_benchmark.py) to every host's sshd_config
record, producing a per-host findings list and a 0-100 weighted compliance score.

The score is severity-weighted rather than a plain pass-count: failing a
"critical" rule (root login, password auth, Protocol 1) costs more than failing
a "medium" one (access restriction), which mirrors how a real CIS scoring
tool / Lynis hardening index would treat these differently.
"""

from dataclasses import dataclass

from cis_ssh_benchmark import SEVERITY_WEIGHT, RuleResult, run_benchmark


@dataclass
class HostAudit:
    hostname: str
    ip: str
    results: list[RuleResult]
    score: float

    @property
    def failed_results(self) -> list[RuleResult]:
        return [r for r in self.results if not r.passed]

    @property
    def passed_count(self) -> int:
        return sum(1 for r in self.results if r.passed)

    @property
    def total_count(self) -> int:
        return len(self.results)


def _weighted_score(results: list[RuleResult]) -> float:
    total_weight = sum(SEVERITY_WEIGHT[r.severity] for r in results)
    earned_weight = sum(SEVERITY_WEIGHT[r.severity] for r in results if r.passed)
    if total_weight == 0:
        return 100.0
    return round((earned_weight / total_weight) * 100, 1)


def audit_host(host: dict) -> HostAudit:
    """Run the benchmark against one host and compute its compliance score."""
    results = run_benchmark(host)
    score = _weighted_score(results)
    return HostAudit(hostname=host["hostname"], ip=host["ip"], results=results, score=score)


def audit_fleet(fleet: list[dict]) -> list[HostAudit]:
    """Run the benchmark against every host in the fleet."""
    return [audit_host(host) for host in fleet]


def fleet_average_score(audits: list[HostAudit]) -> float:
    if not audits:
        return 0.0
    return round(sum(a.score for a in audits) / len(audits), 1)


def worst_offenders(audits: list[HostAudit], limit: int = 3) -> list[HostAudit]:
    """Return the `limit` lowest-scoring hosts, worst first."""
    return sorted(audits, key=lambda a: a.score)[:limit]


if __name__ == "__main__":
    from fleet_inventory import generate_fleet

    fleet = generate_fleet()
    audits = audit_fleet(fleet)
    for audit in audits:
        status = "PASS" if audit.score == 100.0 else "FAIL"
        print(f"[{status}] {audit.hostname:24s} score={audit.score:5.1f}  "
              f"({audit.passed_count}/{audit.total_count} rules passed)")
    print(f"\nFleet average score: {fleet_average_score(audits)}")
