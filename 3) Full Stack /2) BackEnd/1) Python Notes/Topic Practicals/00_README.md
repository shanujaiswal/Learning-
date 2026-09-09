# Python Practical -- Index

This folder replaces the old, single, messy `1) Python Practice.py` file
(hundreds of commented-out dead exercises plus a genuine crash bug in a
`get_marks()` function). It has been fully deleted and replaced with the
focused, runnable, verified files below. Each file was executed locally and
confirmed to run cleanly (or, for `08_fastapi_mini_app.py`, confirmed to be
syntactically correct and ready to run once its extra dependency is
installed).

## File index

| File | Theory chapter(s) it maps to | Pip installs needed to run |
|---|---|---|
| `00_README.md` | -- (this index) | none |
| `01_data_structures_practical.py` | `05 Lists and Tuples.md`, `06 Sets and Dictionaries.md` | none (stdlib only) |
| `02_functions_and_comprehensions.py` | `08 Functions and Lambda.md`, `20 Comprehensions.md` | none (stdlib only) |
| `03_oop_bank_account.py` | `09 OOP Concepts.md` | none (stdlib only) |
| `04_decorators_and_error_handling.py` | `19 Decorators.md`, `12 Error Handling Virtual Env and File IO.md` | none (stdlib only) |
| `05_file_io_and_json.py` | `12 Error Handling Virtual Env and File IO.md`, `11 Math JSON and RegEx.md` | none (stdlib only: `json`, `os`, `re`, `tempfile`) |
| `06_concurrency_demo.py` | `15 Concurrency Threading Multiprocessing and Asyncio.md` | none (stdlib only: `multiprocessing`, `threading`, `asyncio`) |
| `07_type_hints_and_dataclasses.py` | `21 Type Hints and the typing Module.md` | none (stdlib only, Python 3.10+ for `X \| Y` union syntax and builtin generics like `list[int]`) |
| `08_fastapi_mini_app.py` | `16 Web Frameworks Flask Django and FastAPI.md` | `pip install fastapi uvicorn` |
| `test_09_pytest_examples.py` | `17 Testing Unittest and Pytest.md` | `pip install pytest` |

## How to run each file

Most files are plain scripts:

```bash
python "01_data_structures_practical.py"
python "02_functions_and_comprehensions.py"
python "03_oop_bank_account.py"
python "04_decorators_and_error_handling.py"
python "05_file_io_and_json.py"
python "06_concurrency_demo.py"
python "07_type_hints_and_dataclasses.py"
```

`08_fastapi_mini_app.py` is an ASGI app, not a plain script -- run it through
uvicorn, not `python file.py`:

```bash
pip install fastapi uvicorn
uvicorn "08_fastapi_mini_app:app" --reload
```

Then open `http://127.0.0.1:8000/docs` for the interactive Swagger UI.

`test_09_pytest_examples.py` is a test module -- run it through pytest, not
plain `python`:

```bash
pip install pytest
pytest "test_09_pytest_examples.py" -v
```

## Naming convention note (file 09)

pytest's default discovery rule looks for files matching `test_*.py` or
`*_test.py`, and functions inside them named `test_*` (or classes named
`Test*`). A file named `09_pytest_examples.py` would **not** be
auto-discovered by plain `pytest` with default settings, so this file is
named `test_09_pytest_examples.py` instead -- correct pytest convention wins
over strict numeric-prefix consistency here. It still sorts near the other
numbered files alphabetically enough to be found easily, and it also has a
small `if __name__ == "__main__":` smoke-test block so `python
test_09_pytest_examples.py` still prints something useful even without
pytest installed.

## Notes on what changed vs. the old file

- The old `1) Python Practice.py` mixed together many unrelated, half-finished,
  commented-out practice snippets from different learning sessions.
- It also contained a real bug around line 983: a `get_marks()` function with
  an unclosed f-string, a reference to an undefined `subject_name` variable,
  and a missing `import re` -- meaning the function would raise a
  `SyntaxError`/`NameError` if ever called.
- That file has been deleted entirely. Every file above was freshly written,
  compiled with `python -m py_compile`, and executed (where runnable without
  extra installs) to confirm real, correct output before being left in this
  folder.
