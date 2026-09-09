"""
05_indexing_performance_demo.py

Creates a standalone larger table (events, 5,000+ rows) inside practice.db,
runs the SAME filtering query before and after adding an index on the
filtered column, and prints both EXPLAIN QUERY PLAN outputs side by side so
the SCAN -> SEARCH change is visible. Also times both runs.

Covers Theory chapters:
    2) SQL/18 CREATE INDEX AUTO INCREMENT and Dates.md
    2) SQL/25 Query Execution Plans EXPLAIN.md
    3) Advanced/05 Indexing and Performance Tuning.md

Run:  python 01_setup_schema_and_seed_data.py   (once, to create practice.db)
      python 05_indexing_performance_demo.py

All queries use parameterized placeholders ("?") even where no user input is
involved, to model the habit of never building SQL via string formatting.
"""

import os
import sqlite3
import time
import random

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "practice.db")

NUM_ROWS = 8000
TARGET_USER_ID = 4242  # a specific user_id we will filter on


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


def create_events_table(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        DROP TABLE IF EXISTS events;
        CREATE TABLE events (
            event_id    INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id     INTEGER NOT NULL,
            event_type  TEXT NOT NULL,
            event_ts    TEXT NOT NULL
        );
        """
    )
    conn.commit()


def seed_events_table(conn: sqlite3.Connection) -> None:
    rng = random.Random(42)  # deterministic seed for reproducible results
    event_types = ["login", "logout", "purchase", "click", "view", "signup", "error"]

    rows = []
    for i in range(NUM_ROWS):
        # Sprinkle in our TARGET_USER_ID roughly every ~200 rows so it has a
        # realistic, non-trivial number of matching rows (~40 out of 8000).
        if i % 200 == 0:
            user_id = TARGET_USER_ID
        else:
            user_id = rng.randint(1, 50000)
        event_type = rng.choice(event_types)
        day = 1 + (i % 28)
        event_ts = f"2023-{1 + (i % 12):02d}-{day:02d} {i % 24:02d}:00:00"
        rows.append((user_id, event_type, event_ts))

    conn.executemany(
        "INSERT INTO events (user_id, event_type, event_ts) VALUES (?, ?, ?)",
        rows,
    )
    conn.commit()
    print(f"Seeded {NUM_ROWS} rows into 'events' (target user_id={TARGET_USER_ID} appears ~{NUM_ROWS // 200} times)")


QUERY = "SELECT event_id, event_type, event_ts FROM events WHERE user_id = ?"


def explain_query_plan(conn: sqlite3.Connection) -> list:
    return conn.execute(f"EXPLAIN QUERY PLAN {QUERY}", (TARGET_USER_ID,)).fetchall()


def timed_run(conn: sqlite3.Connection, repeats: int = 200) -> float:
    start = time.perf_counter()
    for _ in range(repeats):
        conn.execute(QUERY, (TARGET_USER_ID,)).fetchall()
    elapsed = time.perf_counter() - start
    return elapsed


def main() -> None:
    if not os.path.exists(DB_PATH):
        raise SystemExit(
            "practice.db not found. Run 01_setup_schema_and_seed_data.py first."
        )

    conn = sqlite3.connect(DB_PATH)
    try:
        print_section(f"Setting up 'events' table with {NUM_ROWS} rows")
        create_events_table(conn)
        seed_events_table(conn)

        print_section("BEFORE INDEX — EXPLAIN QUERY PLAN")
        print(f"Query: {QUERY}  (param = {TARGET_USER_ID})")
        plan_before = explain_query_plan(conn)
        for row in plan_before:
            print(row)
        time_before = timed_run(conn)
        print(f"\n200x execution time WITHOUT index: {time_before:.4f}s")

        print_section("Creating index: CREATE INDEX idx_events_user_id ON events (user_id)")
        conn.execute("CREATE INDEX idx_events_user_id ON events (user_id)")
        conn.commit()

        print_section("AFTER INDEX — EXPLAIN QUERY PLAN")
        print(f"Query: {QUERY}  (param = {TARGET_USER_ID})")
        plan_after = explain_query_plan(conn)
        for row in plan_after:
            print(row)
        time_after = timed_run(conn)
        print(f"\n200x execution time WITH index: {time_after:.4f}s")

        print_section("SIDE BY SIDE COMPARISON")
        print(f"{'BEFORE (no index)':40s} | {'AFTER (with index)':40s}")
        print("-" * 85)
        max_len = max(len(plan_before), len(plan_after))
        for i in range(max_len):
            before_row = str(plan_before[i]) if i < len(plan_before) else ""
            after_row = str(plan_after[i]) if i < len(plan_after) else ""
            print(f"{before_row:40s} | {after_row:40s}")

        print(
            "\nExpect BEFORE to show 'SCAN events' (full table scan) and AFTER to\n"
            "show 'SEARCH events USING INDEX idx_events_user_id (user_id=?)'.\n"
            f"Timing: {time_before:.4f}s -> {time_after:.4f}s "
            f"({'faster' if time_after < time_before else 'not faster on this small demo'} with the index)."
        )
    finally:
        conn.close()


if __name__ == "__main__":
    main()
