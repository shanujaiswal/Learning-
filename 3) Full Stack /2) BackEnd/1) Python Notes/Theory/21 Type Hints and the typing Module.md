# Why Type Hints -- Python Is Still Dynamically Typed

--> Type hints are OPTIONAL annotations -- Python itself never enforces them at runtime (no error is raised for violating one) -- they exist purely to help tools (IDEs, static type checkers like `mypy`, other developers reading the code) catch mistakes before running the program and provide better autocomplete.

```python
def greet(name: str) -> str:
    return f"Hello, {name}"

greet(42)   # No runtime error at all -- type hints alone don't enforce anything
```

--> Running a static type checker (`mypy your_file.py`) is what actually surfaces a violation like the one above as an error, before the code ever runs.

# Basic Type Hints

```python
age: int = 30
name: str = "Alice"
price: float = 19.99
is_active: bool = True

def add(a: int, b: int) -> int:
    return a + b

def log(message: str) -> None:   # "-> None" means the function returns nothing
    print(message)
```

# Typing Collections

```python
from typing import List, Dict, Tuple, Set

names: List[str] = ["Alice", "Bob"]
scores: Dict[str, int] = {"Alice": 90, "Bob": 85}
point: Tuple[float, float] = (1.5, 2.5)
unique_ids: Set[int] = {1, 2, 3}

# Modern Python (3.9+) allows using built-in generics directly, without importing from typing:
names2: list[str] = ["Alice", "Bob"]
scores2: dict[str, int] = {"Alice": 90}
```

# Optional and Union

--> `Optional[X]` means "X or None" -- shorthand for `Union[X, None]`. Essential for parameters/return values that might legitimately be absent.

```python
from typing import Optional, Union

def find_user(user_id: int) -> Optional[dict]:
    # Returns a dict if found, or None if not
    ...

def process(value: Union[int, str]) -> str:
    # Accepts EITHER an int or a str
    return str(value)

# Modern shorthand (Python 3.10+):
def find_user2(user_id: int) -> dict | None:
    ...
```

# Callable -- Typing Functions as Arguments

```python
from typing import Callable

def apply_twice(func: Callable[[int], int], value: int) -> int:
    return func(func(value))

apply_twice(lambda x: x + 1, 5)   # 7
```

--> `Callable[[int], int]` means "a function taking one int argument and returning an int."

# TypedDict -- Typing Dictionary Shapes

--> For dictionaries with a known, fixed set of keys (e.g. parsed JSON with a predictable shape), `TypedDict` gives dict-literal syntax proper static type checking, without needing a full class.

```python
from typing import TypedDict

class UserDict(TypedDict):
    name: str
    age: int

def create_user(data: UserDict) -> None:
    print(data["name"])   # Type-checked -- "name" must exist and be a str
```

# dataclasses -- Type-Hinted Classes With Less Boilerplate

--> `@dataclass` auto-generates `__init__`, `__repr__`, and `__eq__` from type-hinted class attributes, removing the repetitive boilerplate of a hand-written class constructor.

```python
from dataclasses import dataclass

@dataclass
class User:
    name: str
    age: int
    email: str = ""   # Default value

user = User(name="Alice", age=30)
print(user)   # User(name='Alice', age=30, email='')
```

# Why Adopt Type Hints in a Real Project

--> Biggest practical payoff on any codebase beyond a small script -- catches an entire category of bugs (passing the wrong type, calling a function with missing/extra arguments) before runtime, and dramatically improves IDE autocomplete/refactoring safety as a project and its contributors grow.
