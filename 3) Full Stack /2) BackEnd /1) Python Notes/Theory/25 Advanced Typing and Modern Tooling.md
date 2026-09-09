# Generics -- TypeVar and Generic[T]

--> The Type Hints file covers concrete, fixed types (`list[str]`, `dict[str, int]`). A GENERIC type hint is one parameterized by a placeholder type that gets filled in per use -- e.g. "a list of SOMETHING" where the something varies by call, but stays internally consistent (a function that takes a `list[T]` and returns `T` should be checked as returning the SAME type that was in the list, not `Any`).

```python
from typing import TypeVar, Generic

T = TypeVar("T")

def first_item(items: list[T]) -> T:
    return items[0]

first_item([1, 2, 3])         # mypy infers T=int, return type int
first_item(["a", "b"])        # mypy infers T=str, return type str
```

==> Generic Classes
--> `Generic[T]` lets a CLASS itself be parameterized by a type -- e.g. a `Stack[int]` vs a `Stack[str]`, where every method's types stay consistent with whichever `T` was chosen at use.

```python
class Stack(Generic[T]):
    def __init__(self) -> None:
        self._items: list[T] = []

    def push(self, item: T) -> None:
        self._items.append(item)

    def pop(self) -> T:
        return self._items.pop()

int_stack: Stack[int] = Stack()
int_stack.push(5)
# int_stack.push("x")   # mypy error -- "x" is not consistent with Stack[int]
```

--> **Modern syntax (Python 3.12+, PEP 695)** -- generics can be declared without importing `TypeVar` at all, using new native syntax:

```python
def first_item2[T](items: list[T]) -> T:
    return items[0]

class Stack2[T]:
    def __init__(self) -> None:
        self._items: list[T] = []
```

==> Bounded and Constrained TypeVars
--> `TypeVar("T", bound=X)` restricts `T` to `X` or any subclass of `X`. `TypeVar("T", int, str)` restricts `T` to EXACTLY one of a fixed set of types (no subclasses considered "the same" T).

```python
from typing import TypeVar

Numeric = TypeVar("Numeric", bound=int | float)

def double(value: Numeric) -> Numeric:
    return value * 2   # mypy knows value supports * because it's bounded to int | float
```

---

# Protocol -- Structural Typing ("Duck Typing" That mypy Can Check)

--> Every type hint seen so far (including `Generic`) is NOMINAL -- it cares about the actual declared type/class hierarchy (inheriting from `Shape`, from the Abstraction section of the OOP Concepts file, via `abc.ABC`). `Protocol` (from `typing`) instead defines STRUCTURAL typing -- "any object with THESE methods/attributes qualifies," regardless of its class hierarchy, matching Python's traditional duck-typing philosophy but now statically checkable.

```python
from typing import Protocol

class Drawable(Protocol):
    def draw(self) -> str: ...

class Circle:                 # note: does NOT inherit from Drawable at all
    def draw(self) -> str:
        return "Drawing a circle"

class Square:
    def draw(self) -> str:
        return "Drawing a square"

def render(shape: Drawable) -> None:
    print(shape.draw())

render(Circle())   # Type-checks fine -- Circle happens to have a matching draw() method
render(Square())   # Type-checks fine too -- no inheritance from Drawable needed anywhere
```

--> **When to prefer Protocol over abc.ABC** -- use `abc.ABC` (Abstraction section, OOP Concepts file) when you control all the implementing classes and want to ENFORCE the contract at runtime (instantiating a subclass that skips an abstract method raises `TypeError` immediately). Use `Protocol` when you're describing a shape that third-party or pre-existing classes might already satisfy without modification -- very common when typing a function parameter that should accept "anything file-like" or "anything with a `.close()` method," without forcing every caller's class through a specific base class.
--> `@runtime_checkable` lets a Protocol additionally be used with `isinstance()` at runtime (checking only that the named methods exist, not their signatures) -- most Protocols are for static checking only and skip this.

---

# @overload -- Multiple Signatures for One Function

--> Some functions genuinely return a DIFFERENT type depending on an argument's value or type, in a way a single type hint line can't express precisely. `@overload` lets you declare several precise signatures for type-checking purposes, followed by one real implementation.

```python
from typing import overload

@overload
def process(value: int) -> str: ...
@overload
def process(value: str) -> list[str]: ...
def process(value):
    if isinstance(value, int):
        return str(value)
    return list(value)

x = process(5)      # mypy knows x: str
y = process("ab")   # mypy knows y: list[str]
```

---

# Literal, Final, and ParamSpec

==> Literal -- Restricting to Specific Values
--> `Literal[...]` narrows a type hint down to a fixed, specific SET of allowed values (not just "any str") -- e.g. modeling an argument that's only ever meant to be one of a few known strings.

```python
from typing import Literal

def set_status(status: Literal["pending", "active", "closed"]) -> None:
    print(status)

set_status("active")     # OK
# set_status("unknown")  # mypy error -- "unknown" isn't one of the allowed literals
```

==> Final -- Preventing Reassignment/Overriding
--> `Final` marks a variable as never meant to be reassigned after its first assignment, or a method/class as never meant to be overridden/subclassed -- enforced by mypy, not at runtime.

```python
from typing import Final

MAX_RETRIES: Final = 3
# MAX_RETRIES = 5   # mypy error -- Final variables cannot be reassigned

class Base:
    @final       # (imported from typing) -- subclasses cannot override this method
    def core_logic(self) -> None: ...
```

==> ParamSpec -- Typing Decorators That Preserve Signatures
--> A plain `Callable[..., Any]` return type on a decorator (Decorators file) loses the wrapped function's exact parameter list for type-checking purposes -- callers of the decorated function no longer get argument-mismatch errors. `ParamSpec` captures "whatever parameters the wrapped function had" and forwards that shape to the wrapper's type, closing that gap.

```python
from typing import ParamSpec, TypeVar, Callable
from functools import wraps

P = ParamSpec("P")
R = TypeVar("R")

def timer(func: Callable[P, R]) -> Callable[P, R]:
    @wraps(func)
    def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
        return func(*args, **kwargs)
    return wrapper

@timer
def add(a: int, b: int) -> int:
    return a + b

add(1, 2)        # mypy still checks this against add's real (int, int) -> int signature
# add("1", "2")  # mypy error -- preserved through the decorator, unlike a plain Callable[..., Any]
```

---

# mypy -- Configuration, Strictness, and Running in CI

--> Type hints alone (Type Hints file) enforce nothing at runtime -- `mypy` is the tool that actually reads them and reports violations, and a real project needs it configured and wired into CI so violations are caught automatically rather than relying on someone manually running it.

```ini
# mypy.ini (or a [tool.mypy] table inside pyproject.toml)
[mypy]
python_version = 3.12
disallow_untyped_defs = true      # every function must have a full type signature
disallow_any_generics = true      # bans bare "list" / "dict" without a type parameter
warn_return_any = true            # flags functions that return "Any" implicitly
warn_unused_ignores = true        # flags "# type: ignore" comments that are no longer needed
no_implicit_optional = true       # "def f(x: int = None)" must be "x: int | None = None"
strict = true                     # shorthand that enables most of the above (and more) at once
```

==> Strictness Levels in Practice
--> Adopting `strict = true` on a large existing untyped codebase all at once usually produces hundreds of errors -- the practical rollout path is per-module: start with `disallow_untyped_defs` on new/rewritten modules only, using `[[tool.mypy.overrides]]` blocks to apply strict settings to specific module paths while leaving legacy code looser, then widen the strict set over time.

```toml
# pyproject.toml
[[tool.mypy.overrides]]
module = "myapp.legacy.*"
disallow_untyped_defs = false
ignore_errors = true

[[tool.mypy.overrides]]
module = "myapp.new_feature.*"
strict = true
```

==> Running mypy in CI
--> The same principle as running pytest in CI (Testing file) -- a failing type check should block a merge, not just be advisory.

```yaml
# .github/workflows/typecheck.yml (excerpt)
- name: Run mypy
  run: mypy src/ --config-file pyproject.toml
```

--> Pair with `pytest` (Testing file) and coverage (Performance/Testing Depth file) as one of several automated gates -- type errors, test failures, and coverage drops are each a distinct, independently useful signal in a CI pipeline.

---

# Pydantic -- Runtime Validation Built on Type Hints

--> Type hints as described so far are purely static (`mypy`-only, zero runtime effect). Pydantic is the most widely used library that takes the SAME type-hint syntax and actually enforces/parses/converts it at RUNTIME -- turning annotated class attributes into real validation logic, executed the moment an object is constructed. FastAPI (Web Frameworks file) uses Pydantic models internally for request/response validation.

```python
from pydantic import BaseModel, Field, field_validator

class User(BaseModel):
    id: int
    name: str
    email: str
    age: int = Field(default=18, ge=0, le=120)   # ge/le -- runtime-enforced numeric bounds

    @field_validator("email")
    @classmethod
    def email_must_contain_at(cls, value: str) -> str:
        if "@" not in value:
            raise ValueError("Invalid email address")
        return value

user = User(id=1, name="Alice", email="alice@example.com")
print(user)                # id=1 name='Alice' email='alice@example.com' age=18

# user_bad = User(id=1, name="Alice", email="not-an-email")
# Raises pydantic.ValidationError -- unlike a bare type hint, this is enforced at runtime

user2 = User(id="2", name="Bob", email="bob@example.com")   # "2" (str) -- coerced to int automatically
print(user2.id, type(user2.id))   # 2 <class 'int'>
```

==> Field() -- Constraints and Metadata
--> `Field()` attaches extra validation rules and metadata beyond the bare type hint -- defaults, numeric bounds (`ge`/`le`/`gt`/`lt`), string length (`min_length`/`max_length`), regex patterns, and descriptions (useful for the auto-generated API docs FastAPI builds from these models).

```python
from pydantic import Field

class Product(BaseModel):
    name: str = Field(..., min_length=1, max_length=100)   # "..." means required, no default
    price: float = Field(..., gt=0, description="Price in USD")
    sku: str = Field(..., pattern=r"^[A-Z]{3}-\d{4}$")
```

==> Nested Models
--> Pydantic models compose naturally -- a field typed as another `BaseModel` is validated recursively, and a malformed nested field's error path shows exactly where inside the nested structure it failed.

```python
class Address(BaseModel):
    city: str
    zip_code: str

class Customer(BaseModel):
    name: str
    address: Address        # nested model -- validated recursively

customer = Customer(name="Alice", address={"city": "Delhi", "zip_code": "110001"})
print(customer.address.city)   # Delhi -- the raw dict was parsed into a real Address instance
```

==> model_config -- Controlling Model Behavior
--> Pydantic v2 configures per-model behavior via a `model_config` class attribute (a `ConfigDict`) -- common settings include forbidding unknown fields, making models immutable, and stripping whitespace automatically.

```python
from pydantic import ConfigDict

class StrictUser(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, str_strip_whitespace=True)

    id: int
    name: str

# StrictUser(id=1, name="Alice", unexpected="oops")   # raises -- extra="forbid" rejects unknown fields
```

==> BaseSettings -- Configuration From Environment Variables
--> `pydantic-settings` (a separate installable companion package in Pydantic v2) provides `BaseSettings`, a `BaseModel` variant that automatically reads its field values from environment variables (or a `.env` file) instead of/in addition to constructor arguments -- the standard, type-checked way to load application configuration (database URLs, secret keys, feature flags) in a production backend.

```python
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    database_url: str
    debug: bool = False
    secret_key: str

    class Config:
        env_file = ".env"   # also loads values from a .env file if present

settings = Settings()   # reads DATABASE_URL, DEBUG, SECRET_KEY from the environment automatically
print(settings.database_url)
```

---

# uv -- The Modern Package and Virtual Environment Manager

--> The Packaging file covers `pip`/`venv` and Poetry. `uv` (from Astral, the makers of `ruff`) is a newer, drastically faster (written in Rust) tool that replaces `pip`, `pip-tools`, `venv`, and much of Poetry's workflow with one single binary -- increasingly the default recommendation for new projects as of 2025-2026.

```bash
uv venv                       # create a virtual environment (equivalent to python -m venv .venv)
uv pip install requests       # pip-compatible install interface, but far faster dependency resolution
uv add requests               # Poetry-style -- adds to pyproject.toml AND installs, using a uv.lock file
uv add --dev pytest mypy      # dev-only dependency group
uv sync                       # installs exactly what's pinned in uv.lock -- reproducible, like poetry install
uv run python app.py          # runs a command inside the project's managed environment, no manual activate
uv run pytest                 # same idea -- no need to activate the venv first
```

--> `uv` also manages Python interpreter VERSIONS themselves (`uv python install 3.12`), similar to `pyenv` -- one tool covering interpreter installation, virtual environments, and dependency management, which is why teams are consolidating onto it instead of separate tools for each concern.

---

# Building and Distributing a CLI-Installable Package

--> The Packaging file covers publishing a library to PyPI. A CLI TOOL additionally needs an "entry point" -- a mapping from a command name typed in a terminal to a Python function to run -- declared in `pyproject.toml`.

```toml
# pyproject.toml
[project]
name = "mycli"
version = "1.0.0"
dependencies = ["click>=8.0"]

[project.scripts]
mycli = "mycli.main:cli"   # "mycli" command runs the `cli` function/object in mycli/main.py

[build-system]
requires = ["setuptools>=61.0"]
build-backend = "setuptools.build_meta"
```

```python
# mycli/main.py
import click

@click.command()
@click.option("--name", default="World")
def cli(name):
    click.echo(f"Hello, {name}!")
```

--> Once installed (`pip install mycli` or `uv pip install mycli`), typing `mycli --name Alice` in ANY terminal runs that function directly -- no `python -m` prefix, no knowing the package's internal file layout. This is exactly how tools like `pytest`, `black`, `mypy`, and `uv` itself are runnable as bare commands after installation.

==> Editable Installs -- Developing a Package Locally
--> While actively developing the package itself (not just using it), an editable install links the installed package directly to the local source directory instead of copying files -- edits to the source take effect immediately, without reinstalling.

```bash
pip install -e .          # editable install -- traditional pip
uv pip install -e .       # editable install -- uv
uv sync                    # in a uv-managed project, dependencies listed as {path = "...", editable = true}
                            # or the project's own package (declared under [project]) are editable by default
```

--> Essential during development of any package that also gets consumed by another project in the same workspace (a shared internal library, a monorepo's own packages) -- without `-e`, every source change would require a full reinstall to be picked up by the consuming project.
