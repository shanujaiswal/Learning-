"""
07_type_hints_and_dataclasses.py

Maps to Theory chapter:
    21 Type Hints and the typing Module.md

Demonstrates:
    - Functions with proper type hints, including Optional and Union (via |)
    - Generics like list[int], dict[str, int]
    - A @dataclass example with defaults, a computed method, and typed fields

No external dependencies. Run directly:
    python "07_type_hints_and_dataclasses.py"

Note: uses PEP 604 `X | Y` union syntax and builtin generics (list[int]),
both natively supported at runtime on Python 3.10+. If you're on Python 3.9,
either upgrade or replace `X | Y` with `Union[X, Y]` and `list[int]` with
`List[int]` from `typing`.
"""

from __future__ import annotations
from dataclasses import dataclass, field
from typing import Optional, Union


# ---------------------------------------------------------------------------
# Functions with type hints: Optional, Union / |, generics
# ---------------------------------------------------------------------------
def find_student(name: str, roster: list[str]) -> Optional[int]:
    """Returns the index of `name` in `roster`, or None if not found."""
    if name in roster:
        return roster.index(name)
    return None


def parse_score(raw: str | int | float) -> float:
    """Accepts a score as a string, int, or float (Union via the | syntax)."""
    if isinstance(raw, str):
        return float(raw)
    return float(raw)


def summarize_scores(scores: list[int]) -> dict[str, float]:
    """Generic collection types: takes a list[int], returns dict[str, float]."""
    if not scores:
        return {"count": 0, "average": 0.0, "max": 0.0, "min": 0.0}
    return {
        "count": len(scores),
        "average": round(sum(scores) / len(scores), 2),
        "max": float(max(scores)),
        "min": float(min(scores)),
    }


def merge_tags(*tag_groups: list[str]) -> set[str]:
    """Variadic args typed as list[str]; returns a set[str]."""
    merged: set[str] = set()
    for group in tag_groups:
        merged.update(group)
    return merged


# ---------------------------------------------------------------------------
# @dataclass example
# ---------------------------------------------------------------------------
@dataclass
class Student:
    name: str
    subject: str
    scores: list[int] = field(default_factory=list)
    email: Optional[str] = None
    active: bool = True

    def average(self) -> float:
        if not self.scores:
            return 0.0
        return round(sum(self.scores) / len(self.scores), 2)

    def add_score(self, score: int) -> None:
        self.scores.append(score)


@dataclass(frozen=True)
class Point:
    """An immutable dataclass -- frozen=True makes instances hashable & read-only."""
    x: float
    y: float

    def distance_to(self, other: "Point") -> float:
        return ((self.x - other.x) ** 2 + (self.y - other.y) ** 2) ** 0.5


def main() -> None:
    print("=== Optional / Union / generics in plain functions ===")
    roster = ["Asha", "Ravi", "Meera"]
    print("find_student('Ravi', roster):", find_student("Ravi", roster))
    print("find_student('Kabir', roster):", find_student("Kabir", roster))

    print("parse_score('88.5'):", parse_score("88.5"))
    print("parse_score(90):", parse_score(90))

    stats = summarize_scores([88, 76, 95, 69, 82])
    print("summarize_scores(...):", stats)

    tags = merge_tags(["python", "backend"], ["backend", "api"], ["python"])
    print("merge_tags(...):", tags)

    print("\n=== @dataclass: Student ===")
    student = Student(name="Asha", subject="Math", email="asha@example.com")
    student.add_score(88)
    student.add_score(94)
    print(student)
    print("Average:", student.average())

    print("\n=== @dataclass(frozen=True): Point ===")
    p1 = Point(0, 0)
    p2 = Point(3, 4)
    print(f"{p1} to {p2} distance:", p1.distance_to(p2))
    try:
        p1.x = 100  # type: ignore[misc]  # frozen dataclass -- should raise
    except Exception as exc:  # dataclasses.FrozenInstanceError
        print(f"Expected error mutating frozen dataclass: {type(exc).__name__}: {exc}")


if __name__ == "__main__":
    main()
