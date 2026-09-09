# Profiling -- Finding Where Time Actually Goes

--> Optimizing before measuring is guesswork -- profiling tools show exactly which function calls consume the most time/memory, so effort goes to the real bottleneck instead of a guessed one.

==> timeit -- Micro-benchmarking Small Snippets
--> Runs a snippet many times (to average out noise) and reports total/per-call time -- appropriate for comparing two small alternative implementations, not for profiling a whole application.

```python
import timeit

# Compare list comprehension vs a plain for-loop append (Comprehensions file)
comp_time = timeit.timeit("[x**2 for x in range(1000)]", number=10000)
loop_time = timeit.timeit(
    "result = []\nfor x in range(1000): result.append(x**2)", number=10000
)
print(comp_time, loop_time)   # comprehension is typically noticeably faster
```

==> cProfile -- Whole-Program/Function Profiling
--> Instruments every function call in a run and reports call counts and cumulative/per-call time per function -- the standard first tool to reach for when a program is "slow" but it's unclear WHERE.

```python
import cProfile
import pstats

def slow_function():
    total = 0
    for i in range(1_000_000):
        total += i ** 2
    return total

cProfile.run("slow_function()", filename="profile_output.prof")

stats = pstats.Stats("profile_output.prof")
stats.sort_stats("cumulative").print_stats(10)   # top 10 slowest calls by cumulative time
```

```bash
python -m cProfile -o profile_output.prof app.py
snakeviz profile_output.prof     # third-party -- visualizes the .prof file as an interactive flame graph
```

==> line_profiler -- Line-by-Line Timing (Conceptual)
--> `cProfile` attributes time to whole FUNCTIONS -- `line_profiler` (`pip install line_profiler`, third-party) goes one level deeper, timing each individual LINE inside a function decorated with `@profile`, which pinpoints the exact line inside an already-identified slow function that's the actual cost.

```python
@profile   # only meaningful when run through the kernprof CLI tool, not plain python
def process(items):
    result = []
    for item in items:
        result.append(item.upper())   # line_profiler would show this line's cumulative time/hit count
    return result
```

```bash
kernprof -l -v script.py   # runs the script, then prints a per-line timing breakdown
```

==> memory_profiler -- Line-by-Line Memory Usage (Conceptual)
--> Same idea as `line_profiler` but for memory instead of time -- decorate a function with `@profile` and run via `mprof`/the module's CLI to see memory consumption after each line, useful for tracking down where a program's memory footprint balloons (e.g. accidentally holding a full dataset in memory when streaming would do).

```python
from memory_profiler import profile

@profile
def load_data():
    data = [i for i in range(10_000_000)]   # memory_profiler would flag this line's memory jump
    return sum(data)
```

--> **Practical workflow** -- reach for `timeit` to compare two snippets, `cProfile` to find which FUNCTION is slow across a real program run, `line_profiler`/`memory_profiler` to zoom into which LINE inside that already-identified function is the actual cost.

---

# pytest Fixture Depth

--> The Testing file covers basic `@pytest.fixture` usage. Real test suites lean heavily on fixture SCOPE, `conftest.py` sharing, and finalization order to keep setup/teardown correct and fast across hundreds of tests.

==> conftest.py -- Sharing Fixtures Across Files
--> A file named `conftest.py` in a test directory makes its fixtures automatically available to every test file in that directory (and subdirectories), with no import needed -- pytest discovers and loads it implicitly.

```python
# tests/conftest.py
import pytest

@pytest.fixture
def db_connection():
    conn = create_test_db_connection()
    yield conn
    conn.close()
```

```python
# tests/test_users.py -- no import of db_connection needed at all
def test_create_user(db_connection):
    ...
```

==> Fixture Scopes
--> `scope` controls how often a fixture's setup/teardown actually runs -- reusing an expensive fixture (e.g. spinning up a real DB) across many tests instead of recreating it for each one.

```python
import pytest

@pytest.fixture(scope="function")   # default -- runs fresh for EVERY test function
def fresh_list():
    return []

@pytest.fixture(scope="class")      # once per test CLASS
def class_resource():
    ...

@pytest.fixture(scope="module")     # once per test FILE
def module_resource():
    ...

@pytest.fixture(scope="session")    # once for the ENTIRE test run, across all files
def db_engine():
    engine = create_engine("postgresql://test")
    yield engine
    engine.dispose()
```

--> Broader scopes (`session`) are faster (setup runs once) but riskier -- shared mutable state between tests can leak and cause order-dependent failures; narrower scopes (`function`) are slower but isolate tests fully. A common pattern is a `session`-scoped DB engine/connection, combined with a `function`-scoped fixture that opens a fresh transaction and ROLLS IT BACK after each test, getting both speed and isolation together.

==> Finalization Order
--> Teardown code (after `yield` in a fixture) runs in the REVERSE order of setup, and nested/dependent fixtures tear down innermost-first -- mirrors how nested `with` blocks unwind, and matters when one fixture's teardown depends on another still being alive.

```python
@pytest.fixture
def outer():
    print("setup outer")
    yield "outer"
    print("teardown outer")

@pytest.fixture
def inner(outer):              # depends on outer -- outer's setup runs first
    print("setup inner")
    yield "inner"
    print("teardown inner")    # inner tears down BEFORE outer, even though outer was requested first

def test_order(inner):
    print("running test")

# Output order:
# setup outer -> setup inner -> running test -> teardown inner -> teardown outer
```

--> `yield_fixture`-style fixtures with `try`/`finally` around the `yield` guarantee teardown runs even if the test itself raises -- same principle as `finally` in ordinary exception handling (Error Handling file).

==> pytest-cov -- Coverage Integrated Into pytest
--> `pytest-cov` (a plugin, `pip install pytest-cov`) wraps `coverage.py` so a single `pytest` invocation both runs tests AND reports which lines/branches of the source were actually executed by them.

```bash
pytest --cov=myapp --cov-report=term-missing   # shows % covered per file, plus exact missing line numbers
pytest --cov=myapp --cov-report=html            # generates an interactive HTML report
pytest --cov=myapp --cov-fail-under=80          # fails the run (useful in CI) if coverage drops below 80%
```

--> High coverage is a floor, not a guarantee of correctness -- a line being EXECUTED by a test says nothing about whether the test actually asserted the right behavior for it. Still a genuinely useful CI signal (alongside mypy, from the Typing file) for catching code paths with zero test attention at all.

---

# Property-Based Testing With Hypothesis (Conceptual)

--> Every test so far (Testing file) is EXAMPLE-based -- the developer picks specific input/output pairs by hand. `hypothesis` (`pip install hypothesis`, third-party) instead lets you describe the SHAPE of valid inputs, and it automatically generates hundreds of varied (including deliberately adversarial edge-case) examples per test run, looking for ANY input that breaks a stated property.

```python
from hypothesis import given, strategies as st

def add(a, b):
    return a + b

@given(st.integers(), st.integers())
def test_add_is_commutative(a, b):
    assert add(a, b) == add(b, a)   # Hypothesis tries many (a, b) pairs, including 0, negatives, huge ints

@given(st.lists(st.integers()))
def test_sorted_list_is_ordered(numbers):
    result = sorted(numbers)
    assert all(result[i] <= result[i + 1] for i in range(len(result) - 1))
```

--> When Hypothesis finds a failing example, it automatically "shrinks" it down to the smallest/simplest input that still reproduces the failure (e.g. reducing a 50-item failing list down to the 2 items actually responsible) -- makes the eventual bug report far easier to debug than a single hand-picked example ever would surface on its own.
--> Best suited for testing genuine INVARIANTS/PROPERTIES (commutativity, round-tripping through serialize/deserialize, sorted output being ordered) rather than one-off business logic with a single expected literal output, where a plain example-based `assert` is simpler and clearer.

---

# Testing Async Code With pytest-asyncio

--> Plain `pytest` (Testing file) has no idea how to run an `async def` test function -- it would just receive an unawaited coroutine object and silently pass without running any of its body. `pytest-asyncio` (`pip install pytest-asyncio`) bridges this, actually running the coroutine on an event loop.

```python
import pytest

@pytest.mark.asyncio
async def test_async_fetch():
    result = await fetch_data_async()
    assert result == "expected"

# In pyproject.toml or pytest.ini, setting asyncio_mode = "auto" removes the need
# for the @pytest.mark.asyncio marker on every single async test function.
```

```ini
# pytest.ini
[pytest]
asyncio_mode = auto
```

--> Async fixtures work the same way -- an `async def` fixture function, `await`ing setup/teardown, requested by an async test exactly like a sync fixture is requested by a sync test.

```python
@pytest.fixture
async def async_client():
    client = await create_async_http_client()
    yield client
    await client.close()

@pytest.mark.asyncio
async def test_with_async_client(async_client):
    response = await async_client.get("/health")
    assert response.status_code == 200
```

---

# Authentication Patterns in Python Web Apps

--> The Web Frameworks file covers routing/views; this section covers actually verifying WHO is making a request, which every real backend needs.

==> Password Hashing -- Never Store Plaintext Passwords
--> Passwords must never be stored in plaintext OR with a fast general-purpose hash (MD5, SHA-256 alone) -- those are too fast to compute, making brute-force/rainbow-table attacks on a leaked database feasible. Purpose-built password hashing algorithms (`bcrypt`, `argon2`) are deliberately slow and incorporate a random salt automatically, so identical passwords produce different hashes and cracking one requires brute-forcing each hash individually.

```python
# bcrypt
import bcrypt

password = b"correct horse battery staple"
hashed = bcrypt.hashpw(password, bcrypt.gensalt())   # salt is generated and embedded in the result
print(hashed)   # b'$2b$12$....' -- safe to store this in a database column

# Verifying a login attempt later:
attempt = b"correct horse battery staple"
print(bcrypt.checkpw(attempt, hashed))   # True
print(bcrypt.checkpw(b"wrong guess", hashed))   # False
```

```python
# argon2 -- winner of the 2015 Password Hashing Competition, generally the current best-practice choice
from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError

ph = PasswordHasher()
hashed = ph.hash("correct horse battery staple")

try:
    ph.verify(hashed, "correct horse battery staple")
    print("Password correct")
except VerifyMismatchError:
    print("Password incorrect")
```

==> JWT (JSON Web Tokens) -- Stateless Authentication
--> A JWT is a signed (not necessarily encrypted) token encoding claims (user ID, expiry, roles) as JSON -- the server issues it after login, the client sends it back on subsequent requests (typically an `Authorization: Bearer <token>` header), and the server verifies its signature to trust the claims WITHOUT needing a server-side session store lookup on every request.

```python
import jwt   # PyJWT package
import datetime

SECRET_KEY = "use-a-real-secret-from-env-in-production"

def create_token(user_id: int) -> str:
    payload = {
        "sub": str(user_id),
        "exp": datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=1),
        "iat": datetime.datetime.now(datetime.timezone.utc),
    }
    return jwt.encode(payload, SECRET_KEY, algorithm="HS256")

def verify_token(token: str) -> dict:
    try:
        return jwt.decode(token, SECRET_KEY, algorithms=["HS256"])
    except jwt.ExpiredSignatureError:
        raise ValueError("Token has expired")
    except jwt.InvalidTokenError:
        raise ValueError("Invalid token")

token = create_token(user_id=42)
payload = verify_token(token)
print(payload["sub"])   # "42"
```

```python
# FastAPI dependency using a JWT (ties into FastAPI's dependency injection, Web Frameworks file)
from fastapi import Depends, HTTPException, Header

async def get_current_user(authorization: str = Header(...)):
    token = authorization.removeprefix("Bearer ")
    try:
        payload = verify_token(token)
    except ValueError:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    return payload["sub"]

@app.get("/me")
async def read_current_user(user_id: str = Depends(get_current_user)):
    return {"user_id": user_id}
```

==> Session-Based Authentication
--> Alternative to JWT -- the server creates a session record (in memory, Redis, or a DB table) on login and sends the client only an opaque random session ID (usually via a cookie); every subsequent request looks that ID up server-side to retrieve the actual user. Unlike JWT, revoking access is instant (delete the session record) since the server holds the source of truth, at the cost of a lookup per request and needing shared session storage across multiple server instances.
--> **JWT vs Sessions** -- JWT is stateless (no server-side lookup, scales horizontally trivially, but hard to revoke before expiry without an extra denylist mechanism); sessions are stateful (trivial to revoke instantly, but need shared storage like Redis, covered below, when running multiple server processes/instances).

---

# Background and Async Tasks

==> FastAPI BackgroundTasks -- Simple In-Process Background Work
--> For lightweight work that should happen AFTER a response is already sent (e.g. sending a confirmation email after signup) but doesn't need a separate worker infrastructure, FastAPI's `BackgroundTasks` runs a function after the response, in the same process.

```python
from fastapi import BackgroundTasks, FastAPI

app = FastAPI()

def send_email(email: str, message: str):
    print(f"Sending '{message}' to {email}")   # simulates an actual email send

@app.post("/signup")
async def signup(email: str, background_tasks: BackgroundTasks):
    # ... create the user in the DB ...
    background_tasks.add_task(send_email, email, "Welcome!")
    return {"message": "Signup successful"}   # response returns immediately -- email sends after
```

--> Limitation -- this work still runs in the SAME process as the web server; a crash of that process loses any pending background task, and heavy/long-running work here still competes with the server for resources. Fine for quick, non-critical side effects; not a substitute for a real task queue.

==> Celery / RQ -- Dedicated Task Queues (Conceptual)
--> For work that must survive process restarts, be retried on failure, be distributed across multiple worker machines, or be scheduled for later, a real message-queue-backed task system is used instead. The pattern -- the web process enqueues a job description onto a broker (commonly Redis or RabbitMQ); one or more separate WORKER processes continuously pull jobs off that queue and execute them, entirely decoupled from request/response timing.

```python
# Celery (conceptual) -- tasks.py
from celery import Celery

celery_app = Celery("tasks", broker="redis://localhost:6379/0")

@celery_app.task
def send_email_task(email, message):
    ...   # runs in a separate worker process, not the web server

# In the web view:
send_email_task.delay(email, "Welcome!")   # enqueues the job, returns immediately
```

```python
# RQ (Redis Queue) (conceptual) -- simpler, Redis-only alternative to Celery
from redis import Redis
from rq import Queue

queue = Queue(connection=Redis())
job = queue.enqueue(send_email, "user@example.com", "Welcome!")
```

--> **When to pick which** -- `BackgroundTasks` for quick fire-and-forget side effects within one request's lifecycle; Celery/RQ once tasks need retries, scheduling, distributed workers, or must survive the web server restarting.

---

# WebSockets, Server-Sent Events, and Streaming Responses

==> WebSockets -- Full-Duplex, Persistent Connection
--> Unlike ordinary request/response HTTP, a WebSocket upgrades one TCP connection into a persistent, bidirectional channel -- either side can send messages at any time without a new request each time. Used for chat apps, live dashboards, real-time collaborative editing.

```python
# FastAPI WebSocket endpoint
from fastapi import FastAPI, WebSocket

app = FastAPI()

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    try:
        while True:
            data = await websocket.receive_text()
            await websocket.send_text(f"Echo: {data}")
    except Exception:
        await websocket.close()
```

==> Server-Sent Events (SSE) -- One-Way Streaming Over Plain HTTP
--> Simpler than WebSockets when data only needs to flow SERVER -> CLIENT (e.g. live progress updates, log tailing) -- SSE is just a long-lived HTTP response that keeps sending `data: ...` chunks, using plain HTTP infrastructure (works through normal proxies/load balancers more easily than WebSockets often do).

```python
from fastapi.responses import StreamingResponse
import asyncio

async def event_stream():
    for i in range(5):
        yield f"data: progress {i}\n\n"     # SSE wire format -- each event ends with a blank line
        await asyncio.sleep(1)

@app.get("/progress")
async def progress():
    return StreamingResponse(event_stream(), media_type="text/event-stream")
```

==> Streaming Responses -- Sending Data Incrementally
--> Not every "streaming" need is real-time push -- `StreamingResponse` over a plain (async) generator is also the right tool for sending a LARGE response (a big file, a large generated CSV) without buffering the entire thing in memory first.

```python
async def generate_large_csv():
    yield "id,name\n"
    for i in range(1_000_000):
        yield f"{i},item-{i}\n"     # each row streamed out immediately, never held fully in memory

@app.get("/export")
async def export_csv():
    return StreamingResponse(generate_large_csv(), media_type="text/csv")
```

---

# Database Transactions, Connection Pooling, and Alembic Migrations

--> The DB Connectors file covers basic SQLAlchemy usage. Production backends additionally need transactional correctness, efficient connection reuse, and a controlled way to evolve the schema over time.

==> Transactions -- All or Nothing
--> A transaction groups multiple statements so they either ALL commit together or ALL roll back together -- essential whenever related writes must stay consistent (e.g. debiting one account and crediting another must never happen only halfway).

```python
from sqlalchemy.orm import Session

def transfer_funds(session: Session, from_id: int, to_id: int, amount: float):
    try:
        from_account = session.get(Account, from_id)
        to_account = session.get(Account, to_id)
        from_account.balance -= amount
        to_account.balance += amount
        session.commit()          # both updates persist together
    except Exception:
        session.rollback()        # either update failing rolls back BOTH -- no half-applied transfer
        raise
```

--> A `with session.begin():` block achieves the same commit/rollback-on-exception behavior implicitly, mirroring the context-manager cleanup guarantee already seen with files (Error Handling file) and custom context managers (OOP Concepts file).

==> Connection Pooling
--> Opening a fresh database connection per request is expensive (TCP handshake, auth, connection setup) -- a connection pool keeps a set of already-open connections ready to be checked out/returned, reused across many requests instead of reconnecting each time.

```python
from sqlalchemy import create_engine

engine = create_engine(
    "postgresql://user:pass@localhost/mydb",
    pool_size=10,        # normal number of persistent connections kept open
    max_overflow=5,       # extra temporary connections allowed under a traffic spike
    pool_timeout=30,      # seconds to wait for a connection before erroring out
    pool_recycle=1800,    # recycle (close and reopen) connections older than this, avoiding stale ones
)
```

==> Alembic -- Schema Migrations for SQLAlchemy
--> `Alembic` tracks incremental, version-controlled changes to a database schema (adding a column, a new table, an index) as ordered migration scripts -- so a schema change is applied consistently and repeatably across every environment (a developer's machine, staging, production) instead of manually running ad hoc SQL.

```bash
alembic init migrations                              # sets up the migrations directory + config
alembic revision --autogenerate -m "add user email"   # diffs current models vs DB, generates a script
alembic upgrade head                                   # applies all pending migrations
alembic downgrade -1                                   # rolls back the most recent migration
```

```python
# migrations/versions/xxxx_add_user_email.py (auto-generated, then reviewed)
def upgrade():
    op.add_column("users", sa.Column("email", sa.String(255), nullable=True))

def downgrade():
    op.drop_column("users", "email")
```

--> Every migration script pairs an `upgrade()` with a `downgrade()` -- migrations should be treated like code, reviewed in the same pull request as the model change that prompted them, and run automatically as a deployment step (see Dockerizing below) rather than by hand on a production server.

---

# Caching With Redis

--> Redis is an in-memory key-value store -- extremely fast (data lives in RAM) reads/writes, commonly used as a cache in front of a slower database or expensive computation, and also as the broker for Celery/RQ (above) or the shared store behind session-based auth (above).

```python
import redis
import json

r = redis.Redis(host="localhost", port=6379, decode_responses=True)

def get_user(user_id: int) -> dict:
    cache_key = f"user:{user_id}"
    cached = r.get(cache_key)
    if cached is not None:
        return json.loads(cached)          # cache hit -- skip the database entirely

    user = query_user_from_db(user_id)      # cache miss -- fall back to the real (slow) source
    r.setex(cache_key, 300, json.dumps(user))   # cache for 300 seconds (5 minutes)
    return user
```

--> `setex(key, seconds, value)` sets a value with an automatic expiry (TTL) -- avoids permanently serving stale data after the underlying record changes; the alternative is EXPLICITLY invalidating (`r.delete(cache_key)`) the cache entry whenever the underlying record is updated, which is more precise but requires remembering to do it at every write path that touches that data.
--> Redis is also commonly used for rate limiting (an incrementing counter per user/IP with a TTL) and distributed locks (`SET key value NX EX seconds` -- only succeeds if the key doesn't already exist, giving a simple cross-process mutex, conceptually parallel to `asyncio.Lock`/`threading.Lock` but across separate processes/machines).

---

# CLI Tools -- argparse, click, and typer

--> The Packaging file's entry-point example used `click`; here's the comparison across all three common ways to build a command-line interface.

==> argparse -- Built-in, No Install Needed
```python
import argparse

parser = argparse.ArgumentParser(description="Greet someone")
parser.add_argument("--name", default="World", help="Name to greet")
parser.add_argument("--times", type=int, default=1)
args = parser.parse_args()

for _ in range(args.times):
    print(f"Hello, {args.name}!")
```

```bash
python greet.py --name Alice --times 3
```

==> click -- Decorator-Based, Third-Party
```python
import click

@click.command()
@click.option("--name", default="World", help="Name to greet")
@click.option("--times", default=1, type=int)
def greet(name, times):
    for _ in range(times):
        click.echo(f"Hello, {name}!")

if __name__ == "__main__":
    greet()
```

==> typer -- Type-Hint-Driven, Built on click
--> `typer` infers the CLI's argument types and help text directly from ordinary Python type hints (Type Hints file) and function signatures -- noticeably less boilerplate than either `argparse` or raw `click` for the common case.

```python
import typer

app = typer.Typer()

@app.command()
def greet(name: str = "World", times: int = 1):
    for _ in range(times):
        typer.echo(f"Hello, {name}!")

if __name__ == "__main__":
    app()
```

--> **Choosing between them** -- `argparse` when zero dependencies is a hard requirement (small scripts, stdlib-only environments); `click` for mature, widely-used CLI tools needing fine control over command groups/plugins (many real-world tools, including `flask`'s own CLI, are built on it); `typer` for the fastest path from a type-hinted function to a polished CLI, especially in a codebase that already leans on type hints throughout.

---

# Deployment Mechanics

==> Gunicorn/Uvicorn Worker Tuning
--> A single Python process handles one request at a time for CPU-bound work (GIL, Concurrency file) -- production deployments run MULTIPLE WORKER PROCESSES behind a process manager, so incoming requests are distributed across them.
--> `gunicorn` is a mature WSGI (sync frameworks -- Flask/Django) process manager; for ASGI (async frameworks -- FastAPI), it's commonly paired with `uvicorn` WORKERS via `uvicorn.workers.UvicornWorker`, combining gunicorn's process management with uvicorn's async request handling.

```bash
# Flask/Django (WSGI) -- gunicorn directly
gunicorn myapp:app --workers 4 --bind 0.0.0.0:8000

# FastAPI (ASGI) -- gunicorn managing uvicorn workers
gunicorn myapp:app --workers 4 --worker-class uvicorn.workers.UvicornWorker --bind 0.0.0.0:8000

# Or uvicorn directly, with its own multi-worker flag (simpler, common for smaller deployments)
uvicorn myapp:app --workers 4 --host 0.0.0.0 --port 8000
```

--> **Sizing the worker count** -- a common starting formula for CPU-bound-per-request sync workloads is `(2 x CPU cores) + 1`; for I/O-bound async (FastAPI/uvicorn) workloads, fewer workers are often needed since a single async worker already handles many concurrent connections cooperatively (asyncio, Concurrency file) -- actual tuning should be validated with real load testing rather than the formula alone.

==> Dockerizing a Python App
--> A container packages the application together with its exact interpreter version and dependencies, so it runs identically across a developer's machine, CI, and production -- eliminating "works on my machine" drift.

```dockerfile
FROM python:3.12-slim

WORKDIR /app

# Copy only dependency files first -- Docker caches this layer, so re-builds after only
# changing application code skip reinstalling dependencies entirely.
COPY pyproject.toml uv.lock ./
RUN pip install uv && uv sync --frozen --no-dev

COPY . .

EXPOSE 8000
CMD ["uvicorn", "myapp:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]
```

```bash
docker build -t myapp:latest .
docker run -p 8000:8000 --env-file .env myapp:latest
```

--> Layer ordering matters for build speed -- dependency installation (rarely changes) is copied and installed BEFORE the application source (changes constantly), so Docker's build cache reuses the expensive dependency-install layer on almost every rebuild, only re-running the fast final steps.
--> A production Dockerfile typically also runs as a non-root user, uses a `.dockerignore` (excluding `.venv`, `__pycache__`, test files, `.git`) to keep the image small, and runs the Alembic migration step (above) as a separate deployment step or entrypoint script rather than baking a specific migration state into the image itself.
