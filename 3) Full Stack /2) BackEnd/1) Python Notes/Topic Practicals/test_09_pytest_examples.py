"""
test_09_pytest_examples.py

Maps to Theory chapter:
    17 Testing Unittest and Pytest.md

Contains a small module (a few functions) PLUS real pytest test functions
in the same file, including a fixture and a parametrized test.

Naming convention note (see 00_README.md for the full explanation):
    pytest's default discovery looks for files matching `test_*.py` or
    `*_test.py`, and test functions named `test_*`. That's why this file
    is named `test_09_pytest_examples.py` rather than `09_pytest_examples.py`
    -- pytest would NOT discover the latter automatically.

Pip installs needed to RUN the tests:
    pip install pytest

How to run:
    pytest "test_09_pytest_examples.py" -v
"""

from __future__ import annotations
import pytest


# ---------------------------------------------------------------------------
# The "module under test": a few small, real functions.
# ---------------------------------------------------------------------------
def add(a: float, b: float) -> float:
    return a + b


def is_prime(n: int) -> bool:
    if n < 2:
        return False
    for divisor in range(2, int(n ** 0.5) + 1):
        if n % divisor == 0:
            return False
    return True


def clamp(value: float, low: float, high: float) -> float:
    """Clamp `value` into the inclusive range [low, high]."""
    if low > high:
        raise ValueError("low must be <= high")
    return max(low, min(value, high))


# ---------------------------------------------------------------------------
# Fixture: provides reusable test data/setup.
# ---------------------------------------------------------------------------
@pytest.fixture
def sample_numbers() -> list[int]:
    """A fixture providing a fixed list of numbers to multiple tests."""
    return [2, 3, 4, 5, 6, 7, 10, 11, 13, 17, 20]


# ---------------------------------------------------------------------------
# Plain tests
# ---------------------------------------------------------------------------
def test_add_positive_numbers() -> None:
    assert add(2, 3) == 5


def test_add_negative_numbers() -> None:
    assert add(-2, -3) == -5


def test_add_float_precision() -> None:
    assert add(0.1, 0.2) == pytest.approx(0.3)


def test_clamp_within_range() -> None:
    assert clamp(5, 0, 10) == 5


def test_clamp_below_range() -> None:
    assert clamp(-5, 0, 10) == 0


def test_clamp_above_range() -> None:
    assert clamp(50, 0, 10) == 10


def test_clamp_invalid_bounds_raises() -> None:
    with pytest.raises(ValueError):
        clamp(5, 10, 0)


# ---------------------------------------------------------------------------
# Test using the fixture
# ---------------------------------------------------------------------------
def test_primes_from_fixture(sample_numbers: list[int]) -> None:
    primes = [n for n in sample_numbers if is_prime(n)]
    assert primes == [2, 3, 5, 7, 11, 13, 17]


def test_fixture_has_expected_length(sample_numbers: list[int]) -> None:
    assert len(sample_numbers) == 11


# ---------------------------------------------------------------------------
# Parametrized test: runs once per (input, expected) pair.
# ---------------------------------------------------------------------------
@pytest.mark.parametrize(
    "number, expected",
    [
        (1, False),
        (2, True),
        (3, True),
        (4, False),
        (17, True),
        (18, False),
        (97, True),
    ],
)
def test_is_prime_parametrized(number: int, expected: bool) -> None:
    assert is_prime(number) is expected


if __name__ == "__main__":
    # Allow `python test_09_pytest_examples.py` to at least run the plain
    # module functions as a smoke test, even though `pytest` is the
    # intended/normal way to execute the test functions above.
    print("add(2, 3) =", add(2, 3))
    print("is_prime(17) =", is_prime(17))
    print("clamp(50, 0, 10) =", clamp(50, 0, 10))
    print("\nRun with `pytest test_09_pytest_examples.py -v` to execute the actual tests.")
