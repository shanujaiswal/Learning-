### argparse and logging for Production-Grade Security Tools

--> Every script across this folder so far has used `print()` and hardcoded target values at the bottom under `if __name__ == "__main__":`. That's fine for a one-off demo, but a REUSABLE tool -- something you or a teammate runs repeatedly against different targets, with different options, and needs to actually audit afterward -- needs a real command-line interface and real logging instead. This file covers both, and is the natural finishing layer for turning any script earlier in this folder into an actual tool.

## `argparse` basics

```python
import argparse

parser = argparse.ArgumentParser(
    description="Scan a target for open TCP ports."
)
parser.add_argument("host", help="Target hostname or IP address")
parser.add_argument("-p", "--ports", default="1-1000",
                     help="Port range to scan, e.g. '1-1000' or '22,80,443' (default: 1-1000)")
parser.add_argument("-t", "--timeout", type=float, default=1.0,
                     help="Per-port connection timeout in seconds (default: 1.0)")
parser.add_argument("-w", "--workers", type=int, default=200,
                     help="Number of concurrent worker threads (default: 200)")
parser.add_argument("-v", "--verbose", action="store_true",
                     help="Enable verbose (DEBUG-level) logging")

args = parser.parse_args()
print(args.host, args.ports, args.timeout, args.workers, args.verbose)
```

--> Running `python scanner.py --help` automatically prints a full usage message generated from the arguments above -- for free, with zero extra code -- exactly the kind of self-documenting behavior real tools (nmap, sqlmap, every CLI you've ever used) provide, and users of any tool you build will expect the same.
--> `type=float` / `type=int` convert and validate in one step -- if a user passes `--timeout abc`, argparse itself exits with a clear error before your script logic ever runs, rather than your code crashing later with a confusing type error deep inside a function.
--> `action="store_true"` gives a clean boolean flag (`--verbose` present -> `True`, absent -> `False`) without needing to parse a value at all.

## Parsing a port specification into an actual list

```python
def parse_ports(port_spec):
    """Accepts '22,80,443' or '1-1000' or a mix like '22,80,1000-1010'."""
    ports = set()
    for part in port_spec.split(","):
        if "-" in part:
            start, end = part.split("-")
            ports.update(range(int(start), int(end) + 1))
        else:
            ports.add(int(part))
    return sorted(ports)

parse_ports("22,80,1000-1010")   # [22, 80, 1000, 1001, ..., 1010]
```

--> Custom parsing logic like this (versus baking every possible format into argparse's own `type=` machinery) keeps argument definitions simple and puts validation logic somewhere it's easy to unit test independently of the CLI layer.

## Subcommands for a multi-purpose tool

--> A tool that does several distinct things (scan ports, check TLS certs, run the SQLi/XSS checks from `14`) reads much more cleanly as SUBCOMMANDS rather than one giant flat pile of optional flags that only make sense in certain combinations.

```python
parser = argparse.ArgumentParser(prog="sectool", description="Multi-purpose security scanning tool")
subparsers = parser.add_subparsers(dest="command", required=True)

scan_parser = subparsers.add_parser("scan", help="Port scan a target")
scan_parser.add_argument("host")
scan_parser.add_argument("-p", "--ports", default="1-1000")

cert_parser = subparsers.add_parser("cert", help="Inspect a host's TLS certificate")
cert_parser.add_argument("host")
cert_parser.add_argument("--warn-days", type=int, default=30)

inject_parser = subparsers.add_parser("inject", help="Check a URL for SQLi/XSS")
inject_parser.add_argument("url")

args = parser.parse_args()

if args.command == "scan":
    print(f"Scanning {args.host} on ports {args.ports}")
elif args.command == "cert":
    print(f"Checking certificate for {args.host}, warn under {args.warn_days} days")
elif args.command == "inject":
    print(f"Checking {args.url} for injection vulnerabilities")

# Usage: python sectool.py scan example.com -p 1-1000
#        python sectool.py cert example.com --warn-days 14
#        python sectool.py inject "http://target/search.php?q=1"
```

--> This is exactly the pattern real multi-function CLI tools use (`git commit`, `git push`, `docker run`, `docker build`) -- each subcommand gets its OWN set of relevant arguments, and `--help` scoped to a subcommand (`sectool.py scan --help`) shows only what's relevant to that operation.

## The `logging` module instead of `print()`

--> `print()` has no levels (you can't selectively silence routine info while keeping errors visible), no timestamps, and no easy way to redirect output to a file WHILE still showing it on screen -- `logging` solves all three, and is the standard expected in any tool meant to be run unattended, in CI, or by someone other than the person who wrote it.

```python
import logging

def setup_logging(verbose=False, log_file=None):
    level = logging.DEBUG if verbose else logging.INFO
    handlers = [logging.StreamHandler()]        # always log to the console
    if log_file:
        handlers.append(logging.FileHandler(log_file))   # optionally ALSO log to a file

    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(message)s",
        handlers=handlers,
    )

setup_logging(verbose=True, log_file="scan_results.log")
logger = logging.getLogger(__name__)

logger.debug("Starting scan of 1000 ports")        # only shown if verbose=True (DEBUG level)
logger.info("Found open port: 22/tcp")              # shown normally
logger.warning("Connection to port 8080 timed out")  # shown normally, visually distinct level
logger.error("Failed to resolve hostname")           # shown normally, and typically the level
                                                       # you'd alert on in a monitored pipeline
```

--> Output with the format string above looks like:
```
2026-08-20 14:03:11 [INFO] Found open port: 22/tcp
2026-08-20 14:03:12 [WARNING] Connection to port 8080 timed out
2026-08-20 14:03:12 [ERROR] Failed to resolve hostname
```
--> The five standard levels, in increasing severity: `DEBUG` (verbose internal detail, off by default), `INFO` (normal operational messages), `WARNING` (something unexpected but not fatal), `ERROR` (an operation failed), `CRITICAL` (the whole tool/process cannot continue). Setting the logger's level to `INFO` automatically suppresses all `DEBUG` messages without deleting a single line of code -- exactly the selective-silencing `print()` can't do without manually wrapping every call in an `if verbose:` check.
--> `logger.exception("message")`, called from inside an `except:` block, logs the message AND the full traceback automatically -- the correct way to record an unexpected failure in a long-running scan without crashing the entire tool over one bad target.

```python
import logging
logger = logging.getLogger(__name__)

def scan_host_safe(host):
    try:
        return scan_port(host, 80)
    except Exception:
        logger.exception(f"Unexpected error scanning {host}")   # logs message + full traceback
        return None   # continue on to the next host instead of crashing the whole batch
```

## Putting it together -- a small reusable tool skeleton

```python
import argparse
import logging

def build_parser():
    parser = argparse.ArgumentParser(description="Reusable security scanning tool skeleton")
    parser.add_argument("host")
    parser.add_argument("-v", "--verbose", action="store_true")
    parser.add_argument("--log-file", default=None)
    return parser

def main():
    args = build_parser().parse_args()
    setup_logging(verbose=args.verbose, log_file=args.log_file)
    logger = logging.getLogger(__name__)

    logger.info(f"Starting scan against {args.host}")
    try:
        # ... actual scanning logic here, e.g. the threaded scanner from file 09 ...
        logger.info("Scan complete")
    except KeyboardInterrupt:
        logger.warning("Scan interrupted by user")
    except Exception:
        logger.exception("Scan failed with an unexpected error")

if __name__ == "__main__":
    main()
```

--> This `build_parser()` / `setup_logging()` / `main()` shape -- CLI definition, logging setup, then a guarded main body -- is the reusable skeleton worth applying to every script in this folder that's meant to be run more than once by more than one person: the port scanner from `01`/`09`, the certificate checker from `12`, and the injection checker from `14` all drop into this same structure with only the middle logic swapped out.

## Cross-references

--> This file is the natural "production-ization" layer for `01 Networking Basics for Python Security Scripts.md`, `09 Concurrent Port Scanning with asyncio and Threading.md`, `12 TLS-SSL Certificate Inspection and Validation with the ssl Module.md`, and `14 Building SQLi and XSS Vulnerability-Checking Scripts.md` -- each of those files' `if __name__ == "__main__":` demo block is exactly what `build_parser()`/`main()` here is meant to replace in a real, shareable tool.
