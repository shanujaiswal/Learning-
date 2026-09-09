"""
04_decorators_and_error_handling.py

Maps to Theory chapters:
    19 Decorators.md
    12 Error Handling Virtual Env and File IO.md

Demonstrates:
    - A working @timer decorator
    - A @retry(times=N) decorator that actually retries on exception
    - Custom Exception subclasses
    - Proper try/except/else/finally usage

No external dependencies. Run directly:
    python "04_decorators_and_error_handling.py"
"""

from __future__ import annotations
import functools
import random
import time


# ---------------------------------------------------------------------------
# Custom exceptions
# ---------------------------------------------------------------------------
class TransientServiceError(Exception):
    """Simulates a flaky external call that sometimes fails."""


class ValidationError(Exception):
    """Raised when input data fails validation."""


# ---------------------------------------------------------------------------
# @timer decorator
# ---------------------------------------------------------------------------
def timer(func):
    """Decorator that prints how long the wrapped function took to run."""

    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        start = time.perf_counter()
        try:
            return func(*args, **kwargs)
        finally:
            elapsed = time.perf_counter() - start
            print(f"[timer] {func.__name__} took {elapsed * 1000:.2f} ms")

    return wrapper


# ---------------------------------------------------------------------------
# @retry(times=N) decorator -- a parametrized decorator (decorator factory)
# ---------------------------------------------------------------------------
def retry(times: int = 3, delay_seconds: float = 0.0):
    """Decorator factory: retries the wrapped function up to `times` attempts
    if it raises an exception, re-raising the last exception if all fail."""

    def decorator(func):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            last_exc: Exception | None = None
            for attempt in range(1, times + 1):
                try:
                    return func(*args, **kwargs)
                except Exception as exc:  # noqa: BLE001 - intentional, demo decorator
                    last_exc = exc
                    print(f"[retry] attempt {attempt}/{times} failed: {exc}")
                    if attempt < times and delay_seconds:
                        time.sleep(delay_seconds)
            assert last_exc is not None
            raise last_exc

        return wrapper

    return decorator


# ---------------------------------------------------------------------------
# Demo functions using the decorators
# ---------------------------------------------------------------------------
@timer
def slow_sum(n: int) -> int:
    return sum(range(n))


_attempt_counter = {"count": 0}


@retry(times=4, delay_seconds=0)
def flaky_network_call() -> str:
    """Fails the first two times it's called, then succeeds -- proving retries work."""
    _attempt_counter["count"] += 1
    if _attempt_counter["count"] < 3:
        raise TransientServiceError(f"Service unavailable (attempt {_attempt_counter['count']})")
    return "response-ok"


def validate_age(age: int) -> int:
    if not isinstance(age, int) or age < 0 or age > 150:
        raise ValidationError(f"Invalid age: {age!r}")
    return age


def demo_try_except_else_finally() -> None:
    ages_to_check = [25, -5, "thirty", 200, 42]

    for raw_age in ages_to_check:
        try:
            valid_age = validate_age(raw_age)  # type: ignore[arg-type]
        except ValidationError as exc:
            print(f"  REJECTED {raw_age!r}: {exc}")
        except TypeError as exc:
            print(f"  REJECTED {raw_age!r} (type error): {exc}")
        else:
            # else runs only if no exception was raised in the try block
            print(f"  ACCEPTED {raw_age!r} -> valid age {valid_age}")
        finally:
            # finally always runs, success or failure
            print(f"    (finished checking {raw_age!r})")


def main() -> None:
    print("=== @timer decorator ===")
    total = slow_sum(2_000_000)
    print("Sum result:", total)

    print("\n=== @retry(times=N) decorator ===")
    result = flaky_network_call()
    print("Final result:", result)

    print("\n=== Custom exceptions + try/except/else/finally ===")
    demo_try_except_else_finally()

    print("\n=== retry exhausting all attempts (always fails) ===")
    @retry(times=2, delay_seconds=0)
    def always_fails():
        raise TransientServiceError("this service never recovers")

    try:
        always_fails()
    except TransientServiceError as exc:
        print(f"Gave up after retries, as expected: {exc}")


if __name__ == "__main__":
    main()
