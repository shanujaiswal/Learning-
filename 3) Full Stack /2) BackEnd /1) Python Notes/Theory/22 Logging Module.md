# Why Not Just Use print()

--> `print()` has no severity levels, can't be easily redirected/filtered, and is either always on or manually removed -- fine for a quick throwaway script, but unsuitable for any real application that needs to distinguish routine info from actual errors, or needs those messages searchable/persisted in production.
--> Python's built-in `logging` module solves all of this -- severity levels, configurable output destinations, and structured, timestamped messages, without needing a third-party dependency.

# Log Levels -- In Increasing Severity

--> `DEBUG` -- detailed diagnostic info, useful only during active development/troubleshooting.
--> `INFO` -- confirmation that things are working as expected (a request was handled, a job started).
--> `WARNING` -- something unexpected happened, but the program can still continue.
--> `ERROR` -- a real problem occurred; some functionality failed.
--> `CRITICAL` -- a serious error -- the program itself may not be able to continue running.

```python
import logging

logging.basicConfig(level=logging.INFO)   # Only INFO and above will actually be output

logging.debug("This won't show -- below the configured level")
logging.info("Server started on port 8000")
logging.warning("Disk usage above 80%")
logging.error("Failed to connect to database")
logging.critical("Out of memory -- shutting down")
```

--> Setting the level acts as a filter -- setting it to `WARNING` in production silences routine `DEBUG`/`INFO` noise while still capturing anything that actually needs attention.

# Loggers, Handlers and Formatters

--> Logger -- the entry point your code actually calls (`logger.info(...)`) -- best practice is one logger per module, named after the module (`logging.getLogger(__name__)`), rather than always using the root logger.
--> Handler -- decides WHERE log messages go (console, a file, a remote logging service) -- one logger can have multiple handlers attached simultaneously.
--> Formatter -- decides what each log message actually looks like (timestamp format, included fields).

```python
logger = logging.getLogger(__name__)
logger.setLevel(logging.DEBUG)

console_handler = logging.StreamHandler()
file_handler = logging.FileHandler("app.log")

formatter = logging.Formatter("%(asctime)s - %(name)s - %(levelname)s - %(message)s")
console_handler.setFormatter(formatter)
file_handler.setFormatter(formatter)

logger.addHandler(console_handler)
logger.addHandler(file_handler)

logger.info("User logged in")
# Output: 2026-08-07 10:30:00 - myapp.auth - INFO - User logged in
```

# Logging Exceptions With Full Tracebacks

```python
try:
    result = 10 / 0
except ZeroDivisionError:
    logger.error("Division failed", exc_info=True)   # Includes the full traceback in the log output
```

--> `logger.exception(...)` is shorthand for `logger.error(..., exc_info=True)`, specifically meant to be called from inside an `except` block.

# Rotating Log Files

--> `RotatingFileHandler` / `TimedRotatingFileHandler` automatically start a new log file once the current one hits a size limit or a time interval, preventing a single log file from growing indefinitely and filling up disk space.

```python
from logging.handlers import RotatingFileHandler

handler = RotatingFileHandler("app.log", maxBytes=5_000_000, backupCount=3)
```

# Why This Connects to the Cyber Security Track

--> Well-structured application logging (this file) is the raw material that feeds SIEM systems and incident response investigations (covered in the Security folder's Incident Response file) -- an application with no meaningful logging is far harder to investigate after a security incident, regardless of how good the SIEM tooling around it is.
