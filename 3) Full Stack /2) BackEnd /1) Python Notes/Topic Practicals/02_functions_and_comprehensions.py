"""
02_functions_and_comprehensions.py

Maps to Theory chapters:
    08 Functions and Lambda.md
    20 Comprehensions.md

Demonstrates:
    - lambda + map/filter
    - list / dict / set comprehensions solving real small problems
      (word frequency counter, flattening nested lists)
    - a recursive function with a clear base case

No external dependencies. Run directly:
    python "02_functions_and_comprehensions.py"
"""

from __future__ import annotations
import re


# ---------------------------------------------------------------------------
# 1. lambda + map/filter
# ---------------------------------------------------------------------------
def demo_lambda_map_filter() -> None:
    numbers = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

    squared = list(map(lambda n: n ** 2, numbers))
    evens = list(filter(lambda n: n % 2 == 0, numbers))
    even_squares = list(map(lambda n: n ** 2, filter(lambda n: n % 2 == 0, numbers)))

    print("Numbers:      ", numbers)
    print("Squared:      ", squared)
    print("Evens only:   ", evens)
    print("Even squares: ", even_squares)

    # lambda as a sort key
    words = ["banana", "kiwi", "apple", "fig", "watermelon"]
    by_length = sorted(words, key=lambda w: len(w))
    print("Words sorted by length:", by_length)


# ---------------------------------------------------------------------------
# 2. Word frequency counter using a dict comprehension + regex tokenizing
# ---------------------------------------------------------------------------
def word_frequency(text: str) -> dict[str, int]:
    """Return a mapping of lowercase word -> occurrence count."""
    words = re.findall(r"[a-zA-Z']+", text.lower())
    unique_words = set(words)
    # Dict comprehension: one line, real-world useful.
    return {word: words.count(word) for word in unique_words}


def demo_word_frequency() -> None:
    paragraph = (
        "Python is great. Python is readable. "
        "I love Python because Python is simple and Python is powerful."
    )
    freq = word_frequency(paragraph)
    print("Word frequency (sorted by count desc):")
    for word, count in sorted(freq.items(), key=lambda item: item[1], reverse=True):
        print(f"  {word!r}: {count}")


# ---------------------------------------------------------------------------
# 3. Flattening nested lists with a nested list comprehension
# ---------------------------------------------------------------------------
def flatten(nested: list[list[int]]) -> list[int]:
    return [item for sublist in nested for item in sublist]


def flatten_arbitrary_depth(nested: list) -> list:
    """Recursively flattens a list of arbitrary nesting depth."""
    flat: list = []
    for item in nested:
        if isinstance(item, list):
            flat.extend(flatten_arbitrary_depth(item))
        else:
            flat.append(item)
    return flat


def demo_flatten() -> None:
    matrix = [[1, 2, 3], [4, 5], [6, 7, 8, 9]]
    print("Matrix:", matrix)
    print("Flattened (one level):", flatten(matrix))

    deeply_nested = [1, [2, 3, [4, 5, [6, 7]], 8], 9, [10]]
    print("Deeply nested:", deeply_nested)
    print("Flattened (arbitrary depth):", flatten_arbitrary_depth(deeply_nested))

    # Set comprehension bonus: unique squares from a nested structure
    unique_squares = {n ** 2 for n in flatten(matrix)}
    print("Unique squares (set comprehension):", unique_squares)


# ---------------------------------------------------------------------------
# 4. Recursion with a clear base case: factorial and Fibonacci
# ---------------------------------------------------------------------------
def factorial(n: int) -> int:
    """Recursive factorial. Base case: 0! == 1! == 1."""
    if n < 0:
        raise ValueError("factorial is not defined for negative numbers")
    if n in (0, 1):  # base case
        return 1
    return n * factorial(n - 1)  # recursive case


def fibonacci(n: int) -> int:
    """Recursive Fibonacci. Base case: fib(0) == 0, fib(1) == 1."""
    if n < 0:
        raise ValueError("fibonacci is not defined for negative numbers")
    if n in (0, 1):  # base case
        return n
    return fibonacci(n - 1) + fibonacci(n - 2)  # recursive case


def demo_recursion() -> None:
    for i in range(6):
        print(f"factorial({i}) = {factorial(i)}")

    fib_sequence = [fibonacci(i) for i in range(10)]  # comprehension over recursion
    print("First 10 Fibonacci numbers:", fib_sequence)


def main() -> None:
    print("=== 1. lambda + map/filter ===")
    demo_lambda_map_filter()

    print("\n=== 2. Word frequency counter ===")
    demo_word_frequency()

    print("\n=== 3. Flattening nested lists ===")
    demo_flatten()

    print("\n=== 4. Recursion: factorial & fibonacci ===")
    demo_recursion()


if __name__ == "__main__":
    main()
