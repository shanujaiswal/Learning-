# What a Decorator Is

--> A decorator is a function that wraps ANOTHER function, adding behavior before/after it runs, without modifying the original function's own code. Possible because Python functions are first-class objects -- they can be passed around, returned, and wrapped just like any other value.

```python
def my_decorator(func):
    def wrapper(*args, **kwargs):
        print("Before the function runs")
        result = func(*args, **kwargs)
        print("After the function runs")
        return result
    return wrapper

@my_decorator
def greet(name):
    print(f"Hello, {name}")

greet("Alice")
# Before the function runs
# Hello, Alice
# After the function runs
```

--> `@my_decorator` above `def greet` is exactly equivalent to writing `greet = my_decorator(greet)` -- the decorator syntax is just cleaner sugar for reassigning a function to its wrapped version.

# Preserving Function Metadata with functools.wraps

--> Without it, the wrapped function's `__name__`/`__doc__` get replaced by the wrapper's -- confusing for debugging/introspection. `functools.wraps` copies that metadata over automatically.

```python
from functools import wraps

def my_decorator(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        return func(*args, **kwargs)
    return wrapper

@my_decorator
def greet(name):
    """Greets a person by name."""
    print(f"Hello, {name}")

print(greet.__name__)   # "greet" -- without @wraps, this would print "wrapper"
```

# Decorators With Arguments

--> To let a decorator itself accept configuration arguments, add another layer of function nesting -- the outer function takes the decorator's arguments and returns the actual decorator.

```python
def repeat(times):
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            for _ in range(times):
                func(*args, **kwargs)
        return wrapper
    return decorator

@repeat(times=3)
def say_hi():
    print("Hi!")

say_hi()   # Prints "Hi!" three times
```

# Practical, Real-World Decorators

```python
import time
from functools import wraps

def timer(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        start = time.perf_counter()
        result = func(*args, **kwargs)
        print(f"{func.__name__} took {time.perf_counter() - start:.4f}s")
        return result
    return wrapper

def require_login(func):
    @wraps(func)
    def wrapper(user, *args, **kwargs):
        if not user.get("logged_in"):
            raise PermissionError("Login required")
        return func(user, *args, **kwargs)
    return wrapper
```

--> This exact pattern is what powers Flask/FastAPI route decorators (`@app.route("/users")`, `@app.get("/users")`) covered in the Web Frameworks file -- registering a function as a handler for a specific URL is, under the hood, just a decorator wrapping/registering the view function.

# Built-In Decorators You Already Use

--> `@staticmethod`, `@classmethod`, `@property` (from the OOP Concepts file) are all decorators too -- the exact same mechanism shown here, just provided built-in for common class-related use cases.

# Stacking Multiple Decorators

--> Decorators apply bottom-to-top -- the one closest to the function runs first (wraps it first), then each decorator above wraps the result of the one below it.

```python
@timer
@require_login
def get_dashboard(user):
    ...
# Equivalent to: get_dashboard = timer(require_login(get_dashboard))
```
