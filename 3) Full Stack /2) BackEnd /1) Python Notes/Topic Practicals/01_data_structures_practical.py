"""
01_data_structures_practical.py

Maps to Theory chapters:
    05 Lists and Tuples.md
    06 Sets and Dictionaries.md

Demonstrates realistic, combined use of lists, tuples, sets, and dictionaries:
    - Deduplicating and grouping a list of student records into a dict-of-lists
    - Using tuples as immutable records / dict keys
    - Set operations to find common / unique elements across groups

No external dependencies. Run directly:
    python "01_data_structures_practical.py"
"""

from __future__ import annotations


# ---------------------------------------------------------------------------
# Sample data: list of dicts, each representing a student record.
# Notice there are duplicate entries (same name + subject) that we must
# deduplicate before grouping.
# ---------------------------------------------------------------------------
raw_students: list[dict[str, object]] = [
    {"name": "Asha", "subject": "Math", "score": 88},
    {"name": "Asha", "subject": "Math", "score": 88},  # duplicate row
    {"name": "Asha", "subject": "Science", "score": 91},
    {"name": "Ravi", "subject": "Math", "score": 76},
    {"name": "Ravi", "subject": "English", "score": 82},
    {"name": "Meera", "subject": "Science", "score": 95},
    {"name": "Meera", "subject": "Math", "score": 69},
    {"name": "Kabir", "subject": "English", "score": 73},
    {"name": "Kabir", "subject": "English", "score": 73},  # duplicate row
]


def deduplicate_records(records: list[dict[str, object]]) -> list[tuple]:
    """Convert each dict record to a hashable tuple and dedupe via a set,
    then return a list of tuples (order not guaranteed, so we sort)."""
    as_tuples = {(r["name"], r["subject"], r["score"]) for r in records}
    return sorted(as_tuples)


def group_by_student(records: list[tuple]) -> dict[str, list[tuple[str, int]]]:
    """Group (subject, score) pairs under each student's name."""
    grouped: dict[str, list[tuple[str, int]]] = {}
    for name, subject, score in records:
        grouped.setdefault(name, []).append((subject, score))
    return grouped


def group_by_subject(records: list[tuple]) -> dict[str, set[str]]:
    """For each subject, collect the set of student names taking it."""
    subjects: dict[str, set[str]] = {}
    for name, subject, _score in records:
        subjects.setdefault(subject, set()).add(name)
    return subjects


def average_score_per_student(grouped: dict[str, list[tuple[str, int]]]) -> dict[str, float]:
    return {
        name: round(sum(score for _subject, score in entries) / len(entries), 2)
        for name, entries in grouped.items()
    }


def main() -> None:
    print("=== Step 1: Deduplicate raw records ===")
    deduped = deduplicate_records(raw_students)
    print(f"Raw count: {len(raw_students)} -> Deduped count: {len(deduped)}")
    for row in deduped:
        print("  ", row)

    print("\n=== Step 2: Group by student (dict of lists) ===")
    by_student = group_by_student(deduped)
    for name, entries in by_student.items():
        print(f"  {name}: {entries}")

    print("\n=== Step 3: Group by subject (dict of sets) ===")
    by_subject = group_by_subject(deduped)
    for subject, names in by_subject.items():
        print(f"  {subject}: {sorted(names)}")

    print("\n=== Step 4: Set operations across subjects ===")
    math_students = by_subject.get("Math", set())
    science_students = by_subject.get("Science", set())
    english_students = by_subject.get("English", set())

    print("Students in Math AND Science:", math_students & science_students)
    print("Students in Math OR Science:", math_students | science_students)
    print("Students in Math but NOT English:", math_students - english_students)
    print("Students in exactly one of Math/Science (symmetric diff):",
          math_students ^ science_students)

    print("\n=== Step 5: Average score per student ===")
    averages = average_score_per_student(by_student)
    for name, avg in sorted(averages.items(), key=lambda item: item[1], reverse=True):
        print(f"  {name}: {avg}")

    print("\n=== Step 6: Tuple as an immutable dict key (composite key) ===")
    # Tuples are hashable, so they make excellent composite dict keys.
    score_lookup: dict[tuple[str, str], int] = {
        (name, subject): score for name, subject, score in deduped
    }
    key = ("Meera", "Science")
    print(f"Score lookup for {key}: {score_lookup[key]}")


if __name__ == "__main__":
    main()
