"""
05_fuzzer_against_local_flask_target.py

AUTHORIZED USE ONLY. This fuzzer is hardcoded to target http://127.0.0.1:5000, the companion
target_app.py Flask app running on your own machine. Never repoint this at a host you do not own
or are not explicitly authorized to test — fuzzing is an active attack technique.

HOW TO RUN THIS DEMO:
  1. In one terminal:  python target_app.py
  2. In another terminal: python 05_fuzzer_against_local_flask_target.py

Integrates Theory Ch.7 (Exploit Development / Fuzzing) with Ch.3 (requests):
  - Generates a mix of random and deliberately malformed payloads for the /divide?a=&b= endpoint.
  - Sends each payload with `requests`.
  - Reports which payloads produced a non-200 response (i.e. "crashed" the naive endpoint),
    demonstrating the core fuzzing workflow: automated input generation + response monitoring +
    triage of interesting ("crashing") inputs.
"""

import random
import string

import requests

TARGET_URL = "http://127.0.0.1:5000/divide"
REQUEST_TIMEOUT_SECONDS = 3
NUM_RANDOM_CASES = 30

# A handful of deliberately malformed/edge-case payloads we KNOW should break the naive
# int()-based parsing or division logic in target_app.py.
KNOWN_EDGE_CASES = [
    {"a": "10", "b": "0"},        # ZeroDivisionError
    {"a": "abc", "b": "2"},       # ValueError: invalid literal for int()
    {"a": "10", "b": "xyz"},      # ValueError
    {"a": "", "b": "2"},          # ValueError: empty string
    {"a": "10"},                  # missing 'b' entirely -> TypeError on int(None)
    {"b": "2"},                   # missing 'a' entirely
    {"a": "9" * 500, "b": "1"},   # very large integer string
    {"a": "10", "b": "-0"},       # negative zero -> still ZeroDivisionError
    {"a": "1e10", "b": "2"},      # scientific notation, not valid for int()
    {"a": "0x10", "b": "2"},      # hex-looking string, not valid for plain int()
]


def random_value() -> str:
    """Produce a random string that is sometimes numeric, sometimes garbage — mimics a
    real fuzzer's mix of "plausible" and "nonsense" inputs.
    """
    choice = random.random()
    if choice < 0.4:
        return str(random.randint(-10_000, 10_000))
    elif choice < 0.7:
        length = random.randint(0, 20)
        return "".join(random.choices(string.printable, k=length))
    else:
        return random.choice(["", "null", "NaN", "Infinity", " ", "\n", "%00", "../../etc/passwd"])


def generate_random_cases(count: int) -> list[dict]:
    cases = []
    for _ in range(count):
        params = {}
        if random.random() > 0.05:  # occasionally omit 'a' entirely
            params["a"] = random_value()
        if random.random() > 0.05:  # occasionally omit 'b' entirely
            params["b"] = random_value()
        cases.append(params)
    return cases


def send_case(params: dict) -> tuple[int | None, str]:
    """Send one fuzz case and return (status_code, short response snippet)."""
    try:
        response = requests.get(TARGET_URL, params=params, timeout=REQUEST_TIMEOUT_SECONDS)
        return response.status_code, response.text[:150]
    except requests.RequestException as exc:
        return None, f"<request failed: {exc}>"


def main() -> None:
    print(f"=== Fuzzing {TARGET_URL} (target_app.py must already be running) ===\n")

    all_cases = KNOWN_EDGE_CASES + generate_random_cases(NUM_RANDOM_CASES)
    crashing_cases = []

    for i, params in enumerate(all_cases, start=1):
        status, snippet = send_case(params)
        marker = "OK" if status == 200 else "!!"
        print(f"[{marker}] case {i:>2}: params={params!r} -> status={status} resp={snippet!r}")

        if status != 200:
            crashing_cases.append((params, status, snippet))

    print(f"\n=== Summary: {len(crashing_cases)} / {len(all_cases)} cases produced a non-200 response ===")
    for params, status, snippet in crashing_cases:
        print(f"  status={status}  params={params!r}")

    if crashing_cases:
        print(
            "\nThese are exactly the kind of unhandled-exception bugs (missing validation, "
            "division by zero, missing parameters) that a fuzzer is designed to surface "
            "automatically instead of requiring a human to guess every edge case by hand."
        )
    else:
        print("\nNo crashes found — try increasing NUM_RANDOM_CASES or adding more edge cases.")


if __name__ == "__main__":
    main()
