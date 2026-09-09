## unittest -- Built-in Testing Framework

--> unittest is Python's built-in testing framework, modeled after JUnit -- tests are written as methods inside a class that inherits from unittest.TestCase.
--> Test method names must start with "test" for the test runner to discover them automatically.

```python
import unittest

def add(a, b):
    return a + b

class TestMathFunctions(unittest.TestCase):
    def test_add_positive_numbers(self):
        self.assertEqual(add(2, 3), 5)

    def test_add_negative_numbers(self):
        self.assertEqual(add(-1, -1), -2)

    def setUp(self):
        print("Runs before EACH test method")

    def tearDown(self):
        print("Runs after EACH test method")

if __name__ == "__main__":
    unittest.main()
```

==> Common Assertion Methods
--> assertEqual(a, b) / assertNotEqual(a, b)
--> assertTrue(x) / assertFalse(x)
--> assertIs(a, b) -- checks identity (a is b), not just equality
--> assertIn(item, container) -- checks membership
--> assertRaises(ExceptionType) -- checks that a block of code raises a specific exception

```python
def test_divide_by_zero_raises():
    with self.assertRaises(ZeroDivisionError):
        1 / 0
```

==> setUp / tearDown
--> setUp() runs before every test method -- used to prepare fresh test data/objects so tests don't affect each other.
--> tearDown() runs after every test method -- used to clean up (close files, reset state).
--> setUpClass() / tearDownClass() (classmethods) run once for the whole class instead of once per test.

---

## pytest -- The Popular Third-Party Framework

--> pytest is not built-in (pip install pytest) but is the de facto standard in the Python ecosystem -- less boilerplate than unittest, plain functions instead of classes, plain assert statements instead of assertEqual/assertTrue.

```python
# test_math.py -- pytest auto-discovers files named test_*.py or *_test.py
def add(a, b):
    return a + b

def test_add_positive_numbers():
    assert add(2, 3) == 5  # plain assert -- pytest rewrites it to show a detailed failure diff

def test_divide_by_zero_raises():
    import pytest
    with pytest.raises(ZeroDivisionError):
        1 / 0
```

--> Run with: pytest -- automatically finds and runs all test_*.py files and test_* functions in the current directory.
--> pytest can still run unittest-style TestCase classes -- it's largely backward compatible.

==> Fixtures
--> Fixtures replace setUp/tearDown -- a function decorated with @pytest.fixture provides reusable setup (and optional teardown via yield) that test functions request as an argument.

```python
import pytest

@pytest.fixture
def sample_data():
    data = {"name": "Alice"}
    yield data  # provided to the test
    print("Cleanup after test")  # runs after the test finishes

def test_uses_fixture(sample_data):
    assert sample_data["name"] == "Alice"
```

==> Parametrize -- Running One Test with Multiple Inputs
--> @pytest.mark.parametrize lets a single test function run multiple times with different input/expected-output pairs, avoiding repetitive near-duplicate tests.

```python
import pytest

@pytest.mark.parametrize("a, b, expected", [
    (2, 3, 5),
    (-1, -1, -2),
    (0, 0, 0),
])
def test_add(a, b, expected):
    assert add(a, b) == expected
```

==> Mocking
--> unittest.mock (built-in, usable from either framework) replaces real objects/functions with fake ones during a test -- useful to avoid making real API calls, DB queries, or file I/O in tests.

```python
from unittest.mock import patch

@patch("requests.get")
def test_fetch_user(mock_get):
    mock_get.return_value.json.return_value = {"id": 1, "name": "Alice"}
    result = fetch_user(1)  # internally calls requests.get, which is now faked
    assert result["name"] == "Alice"
```

==> pytest vs unittest
--> unittest -- built-in (no install needed), class-based, more verbose assertion methods.
--> pytest -- richer features (fixtures, parametrize, plugins), plain assert with better failure output, generally preferred for new projects.
