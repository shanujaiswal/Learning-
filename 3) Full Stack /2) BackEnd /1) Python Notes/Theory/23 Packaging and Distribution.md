# Why Packaging Matters

--> Beyond a single script, real Python projects need a standard way to declare dependencies, be installed by others, and be published/shared -- packaging is the set of conventions and tools that make that possible.

# pip and requirements.txt -- The Basics

--> `pip` installs packages from PyPI (the Python Package Index). `requirements.txt` lists a project's dependencies so anyone can recreate the same environment.

```bash
pip install requests
pip freeze > requirements.txt     # Captures exact installed versions
pip install -r requirements.txt   # Installs everything listed, matching versions
```

--> `pip freeze` captures EVERYTHING currently installed in the environment, including transitive dependencies -- this is exactly why virtual environments (covered in the Error Handling/Venv/File IO file) matter, so `requirements.txt` reflects only this project's actual dependencies, not your entire system's Python packages.

# pyproject.toml -- The Modern Standard

--> `pyproject.toml` has replaced the older `setup.py`/`setup.cfg` as the standard, tool-agnostic way to declare a Python project's metadata, dependencies, and build configuration.

```toml
[project]
name = "my-package"
version = "1.0.0"
description = "A short description of the package"
authors = [{ name = "Alice", email = "alice@example.com" }]
dependencies = [
    "requests>=2.28.0",
    "flask>=2.0.0",
]

[project.optional-dependencies]
dev = ["pytest", "black", "mypy"]

[build-system]
requires = ["setuptools>=61.0"]
build-backend = "setuptools.build_meta"
```

# Poetry -- Dependency Management and Packaging Together

--> Poetry wraps dependency resolution, virtual environment management, and packaging into one tool with a single `pyproject.toml`, replacing the separate pip + venv + setup.py workflow with one consistent interface.

```bash
poetry init                  # Create a new pyproject.toml interactively
poetry add requests          # Add a dependency (updates pyproject.toml AND installs it)
poetry add --group dev pytest   # Add a dev-only dependency
poetry install                # Install everything, using a lockfile for exact reproducibility
poetry run python app.py      # Run a command inside the project's managed virtual environment
```

--> `poetry.lock` pins EXACT resolved versions of every dependency (including transitive ones) -- guarantees that everyone on a team, and CI, installs the identical dependency tree, not just "a version that satisfies the constraints."

# Publishing a Package to PyPI

```bash
python -m build              # Builds the package into a distributable format (wheel + sdist)
python -m twine upload dist/*   # Uploads the built package to PyPI
```

--> Once published, anyone can `pip install your-package-name` -- this is exactly how every third-party library used throughout these notes (Flask, requests, pytest) got distributed in the first place.

# Semantic Versioning

--> The near-universal version numbering convention: `MAJOR.MINOR.PATCH` (e.g. `2.4.1`).
--> MAJOR -- incremented for breaking changes. MINOR -- incremented for new backward-compatible features. PATCH -- incremented for backward-compatible bug fixes.
--> Dependency constraints (`requests>=2.28.0`, `flask^2.0.0` in Poetry's caret syntax) rely on consumers trusting this convention to safely auto-upgrade within a compatible range.
