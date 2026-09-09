# The Descriptor Protocol

--> A descriptor is any object whose class defines `__get__`, `__set__`, or `__delete__` -- when such an object is stored as a CLASS attribute, accessing it through an instance is intercepted by these methods instead of returning the object directly. This is the actual mechanism underlying `@property`, `@staticmethod`, `@classmethod`, and bound methods themselves.
--> A "data descriptor" defines `__set__` and/or `__delete__` (and usually `__get__`) -- it takes priority over the instance's own `__dict__`. A "non-data descriptor" defines only `__get__` -- the instance `__dict__` takes priority over it if a same-named key exists there.

```python
class Positive:
    """A reusable descriptor that validates a value is positive."""

    def __set_name__(self, owner, name):
        # Called automatically when the class body finishes -- tells the descriptor
        # what attribute name it was assigned to, so it can store the real value
        # under a private key without needing it passed in manually.
        self.private_name = "_" + name

    def __get__(self, instance, owner):
        # instance -- the object being accessed (None if accessed via the class itself)
        # owner    -- the class the descriptor is defined on
        if instance is None:
            return self
        return getattr(instance, self.private_name)

    def __set__(self, instance, value):
        if value <= 0:
            raise ValueError("Value must be positive")
        setattr(instance, self.private_name, value)


class Product:
    price = Positive()      # __set_name__ runs here with name="price"
    quantity = Positive()

    def __init__(self, price, quantity):
        self.price = price        # triggers Positive.__set__
        self.quantity = quantity

p = Product(10, 5)
print(p.price)          # 10 -- triggers Positive.__get__
# p.price = -5           # ValueError: Value must be positive
```

==> How @property Actually Works
--> `property` (used throughout the OOP Concepts file) is just a built-in data descriptor class -- `@property` wraps the getter function into a `property` object, and `.setter`/`.deleter` return NEW `property` objects with the corresponding slot filled in. Writing a descriptor by hand (above) is exactly what `property` does generically under the hood.

```python
class Circle:
    def __init__(self, radius):
        self._radius = radius

    @property
    def radius(self):
        return self._radius

    @radius.setter
    def radius(self, value):
        if value < 0:
            raise ValueError("Radius cannot be negative")
        self._radius = value

# Roughly equivalent, hand-written with the raw descriptor protocol:
class CircleManual:
    def __init__(self, radius):
        self._radius = radius

    def _get_radius(self):
        return self._radius

    def _set_radius(self, value):
        if value < 0:
            raise ValueError("Radius cannot be negative")
        self._radius = value

    radius = property(fget=_get_radius, fset=_set_radius)
```

--> **Real-world relevance** -- Django model fields, SQLAlchemy's `Column` mapped attributes, and dataclasses' internals all lean on descriptors to intercept attribute access; recognizing the pattern demystifies "how does assigning to `model.name` actually run validation/DB logic" in every ORM in the Web Frameworks and DB Connectors files.

---

# Async Context Managers (`async with`)

--> Just as `__enter__`/`__exit__` (Custom Context Managers, OOP Concepts file) define a synchronous context manager, `__aenter__`/`__aexit__` (both coroutines, both awaited) define an ASYNC context manager -- needed whenever setup/teardown itself involves an `await` (e.g. opening a network connection, releasing an async lock).

```python
import asyncio

class AsyncConnection:
    async def __aenter__(self):
        print("Opening connection...")
        await asyncio.sleep(0.1)   # simulates an async handshake
        return self

    async def __aexit__(self, exc_type, exc_value, traceback):
        print("Closing connection...")
        await asyncio.sleep(0.1)

    async def query(self, sql):
        return f"Result of: {sql}"

async def main():
    async with AsyncConnection() as conn:
        result = await conn.query("SELECT * FROM users")
        print(result)

asyncio.run(main())
```

--> `contextlib.asynccontextmanager` is the async counterpart to `@contextmanager` -- turns an `async def` generator function (one `yield`) into an async context manager without writing a class.

```python
from contextlib import asynccontextmanager

@asynccontextmanager
async def async_connection():
    print("Opening connection...")
    await asyncio.sleep(0.1)
    try:
        yield "connection-object"
    finally:
        print("Closing connection...")

async def main2():
    async with async_connection() as conn:
        print(conn)
```

==> Async Iterators (`async for`)
--> `__aiter__` (returns self) and `__anext__` (a coroutine, raises `StopAsyncIteration` when exhausted) define an async iterator -- consumed with `async for`, which awaits each `__anext__` call instead of calling `__next__` synchronously. Useful for streaming results (paginated API responses, rows from an async DB driver) where each "next item" itself requires an await.

```python
class AsyncCounter:
    def __init__(self, limit):
        self.limit = limit
        self.current = 0

    def __aiter__(self):
        return self

    async def __anext__(self):
        if self.current >= self.limit:
            raise StopAsyncIteration
        await asyncio.sleep(0.05)   # simulates an async fetch of the next item
        self.current += 1
        return self.current

async def main3():
    async for number in AsyncCounter(3):
        print(number)   # 1, 2, 3 -- each with a real await between items
```

--> An "async generator" (an `async def` function containing `yield`) is the shorthand for the same thing -- no manual `__aiter__`/`__anext__` needed.

```python
async def async_counter(limit):
    for i in range(1, limit + 1):
        await asyncio.sleep(0.05)
        yield i

async def main4():
    async for number in async_counter(3):
        print(number)
```

---

# asyncio Synchronization Primitives

--> Mirror the threading primitives (Concurrency file) but designed for the single-threaded event loop -- they coordinate between CONCURRENT TASKS, not OS threads, and must be awaited.

==> asyncio.Lock
--> Same purpose as `threading.Lock` -- ensures only one task at a time enters a critical section, even though only one task ever truly "runs" at once; still needed because a task can be suspended at an `await` mid-critical-section, letting another task interleave.

```python
import asyncio

balance = 100
lock = asyncio.Lock()

async def withdraw(amount):
    global balance
    async with lock:              # only one task inside at a time
        if balance >= amount:
            await asyncio.sleep(0.01)   # simulates an awaited DB write
            balance -= amount
```

==> asyncio.Semaphore
--> Limits how many tasks may run a section CONCURRENTLY (not just one, like Lock) -- classic use case is capping concurrent outbound HTTP requests to avoid overwhelming a server.

```python
semaphore = asyncio.Semaphore(3)   # at most 3 concurrent requests

async def fetch(url):
    async with semaphore:
        print(f"Fetching {url}")
        await asyncio.sleep(1)
        return f"data from {url}"

async def main5():
    urls = [f"url{i}" for i in range(10)]
    results = await asyncio.gather(*(fetch(u) for u in urls))
```

==> asyncio.Queue
--> A producer/consumer queue for tasks -- one or more producer tasks `put()` items, one or more consumer tasks `get()` them, decoupling how fast work is produced from how fast it's processed.

```python
async def producer(queue):
    for i in range(5):
        await queue.put(i)
        print(f"Produced {i}")
    await queue.put(None)   # sentinel to signal "no more items"

async def consumer(queue):
    while True:
        item = await queue.get()
        if item is None:
            break
        print(f"Consumed {item}")

async def main6():
    queue = asyncio.Queue()
    await asyncio.gather(producer(queue), consumer(queue))
```

---

# Cancellation, Timeouts, and Structured Concurrency

--> Every `asyncio.Task` can be cancelled -- `task.cancel()` schedules a `asyncio.CancelledError` to be raised INSIDE that task at its next `await` point. Well-written coroutines should let `CancelledError` propagate (or clean up in a `finally` and re-raise) rather than swallowing it silently.

```python
async def long_running():
    try:
        await asyncio.sleep(10)
    except asyncio.CancelledError:
        print("Cancelled -- cleaning up")
        raise   # re-raise so the caller correctly sees this task was cancelled

async def main7():
    task = asyncio.create_task(long_running())
    await asyncio.sleep(1)
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        print("Task was cancelled")
```

==> asyncio.wait_for -- Timeouts
--> Wraps an awaitable with a deadline -- if it doesn't finish in time, it's cancelled and `asyncio.TimeoutError` (`TimeoutError` as of 3.11) is raised instead.

```python
async def slow_operation():
    await asyncio.sleep(5)
    return "done"

async def main8():
    try:
        result = await asyncio.wait_for(slow_operation(), timeout=2)
    except asyncio.TimeoutError:
        print("Timed out after 2 seconds")
```

==> Structured Concurrency with asyncio.TaskGroup (Python 3.11+)
--> `asyncio.gather` has a sharp edge -- if one coroutine raises, the others keep running unmanaged in the background unless you handle cancellation yourself. `TaskGroup` fixes this -- it's an async context manager that tracks every task created inside it, and if ANY task fails, it automatically cancels all the sibling tasks and re-raises (wrapped in an `ExceptionGroup`) once they've all actually finished -- no orphaned background tasks, no silent leaks.

```python
async def fetch_user(user_id):
    if user_id == 2:
        raise ValueError(f"User {user_id} not found")
    await asyncio.sleep(1)
    return f"user-{user_id}"

async def main9():
    try:
        async with asyncio.TaskGroup() as tg:
            tasks = [tg.create_task(fetch_user(i)) for i in range(1, 4)]
        # Only reached if ALL tasks succeeded
        print([t.result() for t in tasks])
    except* ValueError as eg:
        # "except*" (3.11+) unpacks an ExceptionGroup -- catches every ValueError raised by any child task
        for exc in eg.exceptions:
            print("Failed:", exc)
```

--> **When to reach for TaskGroup vs gather** -- prefer `TaskGroup` for new code needing multiple concurrent tasks that should live and die together (the "structured concurrency" principle: a group of tasks should have one clear point where they're all guaranteed to be done, success or failure). `gather(..., return_exceptions=True)` remains useful when you deliberately want every coroutine to run to completion regardless of individual failures, collecting results/exceptions side by side.

---

# weakref -- References That Don't Keep Objects Alive

--> A normal reference to an object increments its reference count (see Garbage Collection below), keeping it alive. A `weakref` lets you refer to an object WITHOUT contributing to its reference count -- the object can still be garbage collected even while a weak reference to it exists; dereferencing a dead weak reference gives `None` (or raises, depending on the API used).
--> Common use case -- caches, or parent/child object graphs (a child holding a "weak" back-reference to its parent) that would otherwise create a reference cycle keeping both alive forever.

```python
import weakref

class Node:
    def __init__(self, name):
        self.name = name

n = Node("A")
r = weakref.ref(n)          # a weak reference -- does NOT keep n alive
print(r())                  # <Node object> -- call it to get the real object (or None if dead)

del n                       # n's only strong reference is gone -- it's collected immediately (refcounting)
print(r())                  # None -- the weak reference now dereferences to nothing
```

--> `weakref.WeakValueDictionary` / `WeakKeyDictionary` are dict variants whose entries disappear automatically once the referenced object is garbage collected -- ideal for a cache that shouldn't itself be the reason an object stays alive.

```python
cache = weakref.WeakValueDictionary()
cache["a"] = Node("A")
# If nothing else references this Node, it's collected and "a" silently disappears from cache
```

---

# The gc Module and How Python Actually Frees Memory

--> CPython's PRIMARY memory management mechanism is reference counting -- every object tracks how many references point to it (`sys.getrefcount(obj)`); the moment that count hits zero, the object is freed immediately, deterministically, with no separate "garbage collector pass" needed. This is why `__exit__`/`__del__`-based cleanup in CPython tends to run predictably and promptly compared to garbage-collected languages like Java.
--> Reference counting alone cannot free REFERENCE CYCLES -- e.g. two objects that reference each other (`a.other = b; b.other = a`) never naturally hit a refcount of zero even after both become unreachable from the rest of the program. The `gc` module's generational cycle collector exists specifically to find and free these cycles.

```python
import gc

class Node:
    def __init__(self):
        self.other = None

a, b = Node(), Node()
a.other = b
b.other = a          # a reference cycle -- each keeps the other's refcount above 0
del a, b              # both are now unreachable, but refcounts never hit 0 due to the cycle

gc.collect()          # forces a generational collection pass -- finds and frees the cycle
```

==> Generational Collection
--> The cycle collector groups objects into 3 "generations" (0, 1, 2) based on how long they've survived collection passes -- new objects start in generation 0. The intuition (the "generational hypothesis") is that most objects die young, so generation 0 is scanned far more often than generation 2; an object that survives a scan of its generation is promoted to the next one.
--> `gc.collect()` -- forces an immediate full collection. `gc.disable()` / `gc.enable()` -- turns the automatic cyclic collector off/on (reference counting itself is NEVER disabled -- it's not optional). `gc.get_stats()` -- inspect per-generation collection counts, useful when diagnosing GC-related latency in a long-running server process.
--> In practice, most application code never touches `gc` directly -- relevant mainly when profiling memory (Performance file) or diagnosing why a large object graph isn't being freed as expected (usually: check for a reference cycle, or an unexpected reference held in a cache/closure).

---

# Generators, Revisited -- .send(), .throw(), and yield from

--> Comprehensions and basic generator functions are covered in the Comprehensions file -- this section covers the two-way communication a generator supports, which is the actual foundation `async def`/`await` was built on top of historically.
--> `yield` doesn't just produce values outward -- it's also an EXPRESSION that can receive a value sent INTO the generator via `.send(value)`, resuming the generator with that value as the result of the `yield` expression.

```python
def running_average():
    total = 0
    count = 0
    average = None
    while True:
        value = yield average     # yields the current average, receives the next value via .send()
        total += value
        count += 1
        average = total / count

gen = running_average()
next(gen)                 # "primes" the generator -- runs it to the first yield (average=None)
print(gen.send(10))       # 10.0
print(gen.send(20))       # 15.0
print(gen.send(30))       # 20.0
```

--> `.throw(exc)` raises an exception INSIDE the generator at the point it's currently paused (at its `yield`) -- lets a caller signal an error condition into a running generator rather than just stopping it. `.close()` raises `GeneratorExit` inside it, the conventional way to ask a generator to clean up (e.g. release a resource held across a `try`/`finally` wrapped around the `yield`) and stop.

```python
def resource_generator():
    try:
        while True:
            yield "resource"
    except GeneratorExit:
        print("Cleaning up resource")
    except ValueError as e:
        print(f"Handling injected error: {e}")
        yield "recovered"

gen2 = resource_generator()
print(next(gen2))               # "resource"
print(gen2.throw(ValueError("bad state")))   # Handling injected error: bad state -> "recovered"
gen2.close()                    # Cleaning up resource
```

==> yield from -- Delegating to a Sub-Generator
--> `yield from other_generator` delegates iteration to another generator (or any iterable) -- every value the inner generator yields is yielded outward transparently, AND `.send()`/`.throw()` calls on the outer generator are forwarded down to the inner one too. Without it, manually looping over a sub-generator with a plain `for` loop only forwards values OUT, not `.send()`/`.throw()` calls IN.

```python
def inner():
    yield 1
    yield 2
    return "inner done"

def outer():
    result = yield from inner()   # yields 1, then 2, then binds "inner done" to result
    print(f"inner returned: {result}")
    yield 3

print(list(outer()))   # prints "inner returned: inner done" as a side effect, returns [1, 2, 3]
```

--> **Historical note** -- before native `async`/`await` syntax (Python 3.5), coroutines were written as `@asyncio.coroutine`-decorated generator functions using exactly this `yield from` mechanism to await other coroutines -- understanding `yield from` here explains why `await` behaves the way it does (suspends, forwards exceptions, propagates a final return value) at a mechanical level.

---

# functools Deep Dive

==> functools.singledispatch -- Generic Functions by Argument Type
--> Lets a single function name have different implementations selected by the TYPE of its first argument -- a lightweight, Pythonic alternative to a chain of `isinstance` checks, similar in spirit to method overloading (mentioned as "not directly supported in Python" in the OOP Concepts file) but done via a registry instead of parameter signatures.

```python
from functools import singledispatch

@singledispatch
def describe(value):
    return f"Some value: {value}"

@describe.register
def _(value: int):
    return f"An integer: {value}"

@describe.register
def _(value: list):
    return f"A list with {len(value)} items"

print(describe(42))         # An integer: 42
print(describe([1, 2, 3]))  # A list with 3 items
print(describe(3.14))       # Some value: 3.14 -- falls back to the base implementation
```

==> functools.cached_property
--> Like `@property` (OOP Concepts file), but computes the value ONCE on first access and then stores it directly in the instance's `__dict__` under the same name -- every access after the first is a plain, fast attribute lookup with no recomputation. Only appropriate for values that shouldn't change across the object's lifetime (or where staleness is acceptable), since invalidating it means manually `del`-ing the cached attribute.

```python
from functools import cached_property
import time

class Report:
    def __init__(self, rows):
        self.rows = rows

    @cached_property
    def total(self):
        print("Computing total...")   # only ever prints once
        time.sleep(1)                 # simulates an expensive computation
        return sum(self.rows)

r = Report([1, 2, 3, 4])
print(r.total)   # "Computing total..." then 10
print(r.total)   # 10 -- instant, no recomputation
```

--> **Mechanically**, `cached_property` is a non-data descriptor (it defines only `__get__`) -- that's precisely why it's able to fall out of the picture on subsequent access once the instance's own `__dict__` gets a same-named entry written into it: non-data descriptors lose priority to the instance `__dict__`, unlike `property`, which as a data descriptor would win every time and so must compute -- or explicitly cache to a different private name -- on every access.

==> functools.total_ordering
--> Writing all six comparison dunders (`__lt__`, `__le__`, `__gt__`, `__ge__`, `__eq__`, `__ne__`) by hand for a custom class (Operator Overloading, OOP Concepts file) is repetitive -- `@total_ordering` fills in the rest automatically, given just `__eq__` and ONE of `__lt__`/`__le__`/`__gt__`/`__ge__`.

```python
from functools import total_ordering

@total_ordering
class Money:
    def __init__(self, cents):
        self.cents = cents

    def __eq__(self, other):
        return self.cents == other.cents

    def __lt__(self, other):
        return self.cents < other.cents

m1, m2 = Money(500), Money(750)
print(m1 < m2)    # True  -- defined directly
print(m1 > m2)    # False -- derived automatically from __lt__ and __eq__
print(m1 <= m2)   # True  -- derived automatically
```
