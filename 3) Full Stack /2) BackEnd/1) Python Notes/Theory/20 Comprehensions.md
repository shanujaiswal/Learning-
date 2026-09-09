# Why Comprehensions

--> Comprehensions build a new list/dict/set from an existing iterable in a single, readable expression -- doing the same thing with a manual `for` loop + `.append()` takes more lines and is generally considered less idiomatic Python for simple transformations.

# List Comprehensions

```python
numbers = [1, 2, 3, 4, 5]

squares = [n ** 2 for n in numbers]
# [1, 4, 9, 16, 25]

evens = [n for n in numbers if n % 2 == 0]
# [2, 4]

labeled = [f"even:{n}" if n % 2 == 0 else f"odd:{n}" for n in numbers]
# ['odd:1', 'even:2', 'odd:3', 'even:4', 'odd:5']
```

--> General shape: `[expression for item in iterable if condition]` -- the `if` clause is optional and filters which items are included; the expression at the front transforms each included item.

# Nested Loops in a Comprehension

```python
pairs = [(x, y) for x in range(3) for y in range(2)]
# [(0,0),(0,1),(1,0),(1,1),(2,0),(2,1)]

matrix = [[1, 2], [3, 4], [5, 6]]
flattened = [num for row in matrix for num in row]
# [1, 2, 3, 4, 5, 6]
```

--> Reads left to right in the same order as the equivalent nested `for` loops would be written.

# Dictionary Comprehensions

```python
names = ["alice", "bob", "carol"]

name_lengths = {name: len(name) for name in names}
# {'alice': 5, 'bob': 3, 'carol': 5}

squared_dict = {n: n**2 for n in range(5)}
# {0: 0, 1: 1, 2: 4, 3: 9, 4: 16}

# Inverting a dictionary
original = {"a": 1, "b": 2}
inverted = {value: key for key, value in original.items()}
# {1: 'a', 2: 'b'}
```

# Set Comprehensions

```python
words = ["apple", "banana", "apple", "cherry"]
unique_lengths = {len(word) for word in words}
# {5, 6}  -- duplicates automatically removed, like any set
```

# Generator Expressions -- Lazy Comprehensions

--> Same syntax as a list comprehension but with `()` instead of `[]` -- produces a generator that yields values ONE AT A TIME, on demand, instead of building the entire list in memory upfront.

```python
squares_gen = (n ** 2 for n in range(1_000_000))   # No memory used yet -- nothing computed until iterated
total = sum(squares_gen)                             # Values generated one at a time as sum() consumes them
```

--> Use a generator expression instead of a list comprehension whenever you're only iterating once and don't need the full list held in memory -- especially important for very large or infinite sequences.

# When NOT to Use a Comprehension

--> If the expression or condition logic gets complex enough that the comprehension becomes hard to read on one line, a regular `for` loop is the more maintainable, more "Pythonic" (readability counts) choice -- comprehensions are a readability tool, not a rule to force everything into.

```python
# Getting hard to read -- a plain loop would be clearer here
result = [transform(x) for x in data if condition_one(x) and condition_two(x) if not condition_three(x)]
```
