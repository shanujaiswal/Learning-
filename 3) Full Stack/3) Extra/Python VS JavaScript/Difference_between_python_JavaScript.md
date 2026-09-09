# 🧠 JavaScript vs Python — Complete Cheat Sheet

---

## 📘 Basic Syntax Comparison

| 🔧 Feature               | 🟦 JavaScript                        | 🟨 Python                             |
|--------------------------|--------------------------------------|----------------------------------------|
| **Variable Declaration** | `let x = 10;`<br>`const y = 20;`     | `x = 10`<br>`y = 20`                   |
| **Output**               | `console.log("Hello")`              | `print("Hello")`                      |
| **Comments**             | `// single-line`<br>`/* multi */`   | `# single-line`<br>`''' multi '''`    |
| **Data Types**           | `Number, String, Boolean, Object`   | `int, str, bool, list, dict`          |

---

## 🎯 Scope and Variable Behavior

| Feature           | JavaScript                        | Python                           |
|-------------------|-----------------------------------|----------------------------------|
| **Global Scope**  | `var x = 10;` (function scope)   | `x = 10` (module scope)          |
| **Block Scope**   | `let x = 10;` (block scope)       | `x = 10` (same as global)        |
| **Constant**      | `const x = 10;` (immutable ref)   | `X = 10` (convention only)       |
| **Hoisting**      | `var` hoisted, `let/const` not    | No hoisting                      |
| **Global Object** | `window` (browser), `global` (Node) | `globals()`                    |

```javascript
// Hoisting example
console.log(x); // undefined (not error)
var x = 5;

console.log(y); // ReferenceError
let y = 10;
```

```python
# No hoisting - order matters
print(x)  # NameError: name 'x' is not defined
x = 5
```

---

## 📚 Data Structures

### Arrays / Lists

| Feature      | JavaScript                | Python                   |
|--------------|---------------------------|---------------------------|
| Declare      | `let nums = [1, 2, 3];`   | `nums = [1, 2, 3]`       |
| Access       | `nums[0]`                 | `nums[0]`                |
| Add Element  | `nums.push(4);`           | `nums.append(4)`         |

### Objects / Dictionaries

| Feature      | JavaScript                                | Python                            |
|--------------|-------------------------------------------|-----------------------------------|
| Declare      | `let user = {name: "Ram", age: 25};`      | `user = {"name": "Ram", "age": 25}` |
| Access       | `user.name` or `user["name"]`             | `user["name"]`                     |

---

## 🔁 Conditional Statements

| Condition Type | JavaScript | Python |
|----------------|------------|--------|
| If             | `if (x > 5) {}`     | `if x > 5:`            |
| Else If        | `else if (x < 10)`  | `elif x < 10:`         |
| Else           | `else {}`           | `else:`                |

```javascript
if (age >= 18) {
  console.log("Adult");
} else {
  console.log("Minor");
}
```

```python
if age >= 18:
    print("Adult")
else:
    print("Minor")
```

---

## 🔄 Loops

| Loop Type | JavaScript                      | Python                    |
|-----------|----------------------------------|----------------------------|
| For Loop  | `for (let i=0; i<5; i++)`       | `for i in range(5):`       |
| While     | `while (x < 10)`               | `while x < 10:`            |

---

## 🔄 Advanced Loop Constructs

| Feature           | JavaScript                        | Python                           |
|-------------------|-----------------------------------|----------------------------------|
| **For...in**      | `for (let key in obj) {}`         | `for key in dict:`               |
| **For...of**      | `for (let val of array) {}`       | `for val in list:`               |
| **Enumerate**     | `arr.forEach((val, i) => {})`     | `for i, val in enumerate(list):` |
| **Break/Continue**| `break;` / `continue;`            | `break` / `continue`             |
| **Zip**           | `arr1.map((v, i) => [v, arr2[i]])` | `for a, b in zip(list1, list2):` |

```javascript
// For...of with index
for (let [index, value] of arr.entries()) {
  console.log(index, value);
}
```

```python
# Enumerate with start
for i, value in enumerate(arr, start=1):
    print(i, value)
```

---

## 🔧 Functions

| Feature       | JavaScript                    | Python                      |
|---------------|-------------------------------|-----------------------------|
| Define        | `function add(a, b) {}`       | `def add(a, b):`            |
| Return        | `return x + y;`               | `return x + y`              |
| Arrow/Lambda  | `const sum = (a,b) => a + b;` | `sum = lambda a,b: a + b`   |

---

## 🚀 Advanced Function Features

| Feature            | JavaScript                           | Python                              |
|--------------------|--------------------------------------|-------------------------------------|
| **Default Params** | `function greet(name = "World") {}`  | `def greet(name="World"):`          |
| **Rest/Spread**    | `function sum(...nums) {}`           | `def sum(*args):`                   |
| **Keyword Args**   | `function config({x, y}) {}`         | `def config(**kwargs):`             |
| **Destructuring**  | `const [a, b] = [1, 2];`             | `a, b = 1, 2`                       |

```javascript
// Default + Rest parameters
function calculate(op = "add", ...nums) {
  return nums.reduce((a, b) => a + b, 0);
}
```

```python
# Default + Variable arguments
def calculate(op="add", *nums):
    return sum(nums)
```

---

## 🔤 String Operations

| Operation         | JavaScript                        | Python                           |
|-------------------|-----------------------------------|----------------------------------|
| **Length**        | `str.length`                      | `len(str)`                       |
| **Concatenation** | `str1 + str2`                     | `str1 + str2`                    |
| **Template/f-string** | `` `Hello ${name}` ``         | `f"Hello {name}"`                |
| **Split**         | `str.split(",")`                  | `str.split(",")`                 |
| **Join**          | `arr.join(",")`                   | `",".join(arr)`                  |
| **Replace**       | `str.replace("old", "new")`       | `str.replace("old", "new")`      |
| **Slice**         | `str.slice(0, 5)`                 | `str[0:5]`                       |
| **Upper/Lower**   | `str.toUpperCase()`               | `str.upper()`                    |
| **Trim**          | `str.trim()`                      | `str.strip()`                    |

```javascript
// Template literals
const name = "John";
const age = 30;
const message = `Hello ${name}, you are ${age} years old`;
```

```python
# f-strings
name = "John"
age = 30
message = f"Hello {name}, you are {age} years old"
```

---

## 📊 Advanced Array/List Operations

| Operation         | JavaScript                        | Python                           |
|-------------------|-----------------------------------|----------------------------------|
| **Map**           | `arr.map(x => x * 2)`             | `[x * 2 for x in arr]`           |
| **Filter**        | `arr.filter(x => x > 5)`          | `[x for x in arr if x > 5]`      |
| **Reduce**        | `arr.reduce((a, b) => a + b, 0)`  | `from functools import reduce`   |
| **Sort**          | `arr.sort((a, b) => a - b)`       | `arr.sort()` or `sorted(arr)`    |
| **Find**          | `arr.find(x => x > 5)`            | `next(x for x in arr if x > 5)`  |
| **Some/Any**      | `arr.some(x => x > 5)`            | `any(x > 5 for x in arr)`        |
| **Every/All**     | `arr.every(x => x > 0)`           | `all(x > 0 for x in arr)`        |

```javascript
// Method chaining
const result = arr
  .filter(x => x > 0)
  .map(x => x * 2)
  .reduce((a, b) => a + b, 0);
```

```python
# List comprehension with conditions
result = sum(x * 2 for x in arr if x > 0)
```

---

## 🧱 Classes and OOP

| Feature       | JavaScript                                      | Python                                        |
|---------------|-------------------------------------------------|-----------------------------------------------|
| Define Class  | `class Car { constructor() {} }`               | `class Car:`<br>`    def __init__(self):` |
| Create Object | `let c = new Car();`                           | `c = Car()`                                   |

```javascript
class Car {
  constructor(brand) {
    this.brand = brand;
  }
}
```

```python
class Car:
    def __init__(self, brand):
        self.brand = brand
```

---

## 🏗️ Advanced OOP Features

| Feature            | JavaScript                        | Python                           |
|--------------------|-----------------------------------|----------------------------------|
| **Inheritance**    | `class Dog extends Animal {}`     | `class Dog(Animal):`             |
| **Super Method**   | `super()`                         | `super()`                        |
| **Static Method**  | `static method() {}`              | `@staticmethod`                  |
| **Private Fields** | `#privateField`                   | `_private` or `__private`        |
| **Getter/Setter**  | `get name() {}` / `set name() {}` | `@property` / `@name.setter`     |

```javascript
class Animal {
  constructor(name) {
    this.name = name;
  }
  
  static getSpecies() {
    return "Unknown";
  }
}

class Dog extends Animal {
  constructor(name, breed) {
    super(name);
    this.breed = breed;
  }
}
```

```python
class Animal:
    def __init__(self, name):
        self.name = name
    
    @staticmethod
    def get_species():
        return "Unknown"

class Dog(Animal):
    def __init__(self, name, breed):
        super().__init__(name)
        self.breed = breed
```

---

## ⚡ Asynchronous Programming

| Feature           | JavaScript                        | Python                           |
|-------------------|-----------------------------------|----------------------------------|
| **Promise**       | `new Promise((resolve, reject) => {})` | `asyncio.Future()`          |
| **Async Function** | `async function getData() {}`    | `async def get_data():`          |
| **Await**         | `await fetch(url)`                | `await fetch(url)`               |
| **Promise.all**   | `Promise.all([p1, p2])`           | `asyncio.gather(p1, p2)`         |
| **Timeout**       | `setTimeout(fn, 1000)`            | `await asyncio.sleep(1)`         |

> **Note:** The table above maps `Promise` to `asyncio.Future()`, which is technically valid but not the closest everyday equivalent. In practice, `asyncio.Task` (or an awaited coroutine, created via `asyncio.create_task(...)`) is the more precise match for how JS `Promise` is actually used in `async`/`await` code -- it's the "pending result" object application code awaits. `asyncio.Future` is a lower-level primitive (Task is actually a subclass of Future) that most async Python code doesn't construct directly; it's mainly used internally by asyncio or by library authors bridging callback-based APIs via `loop.create_future()`.

```javascript
// Async/await in JavaScript
async function fetchData() {
  try {
    const response = await fetch('api/data');
    const data = await response.json();
    return data;
  } catch (error) {
    console.error(error);
  }
}
```

```python
# Async/await in Python
import asyncio
import aiohttp

async def fetch_data():
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get('api/data') as response:
                data = await response.json()
                return data
    except Exception as error:
        print(error)
```

---

## ❌ Error Handling

| Feature       | JavaScript                        | Python                       |
|---------------|-----------------------------------|-------------------------------|
| Try/Catch     | `try { ... } catch(e) { ... }`    | `try:`<br>`    ...`<br>`except e:` |
| Finally       | `finally { ... }`                 | `finally:`                    |

---

## 🛡️ Advanced Error Handling

| Feature              | JavaScript                        | Python                           |
|----------------------|-----------------------------------|----------------------------------|
| **Custom Error**     | `class MyError extends Error {}`  | `class MyError(Exception):`      |
| **Multiple Catch**   | `catch(e) { if (e instanceof...) }` | `except ValueError:` / `except TypeError:` |
| **Throw/Raise**      | `throw new Error("message")`      | `raise ValueError("message")`    |
| **Error Types**      | `Error`, `TypeError`, `ReferenceError` | `ValueError`, `TypeError`, `KeyError` |

```javascript
// Custom error with multiple catch simulation
class ValidationError extends Error {
  constructor(message) {
    super(message);
    this.name = "ValidationError";
  }
}

try {
  throw new ValidationError("Invalid input");
} catch (error) {
  if (error instanceof ValidationError) {
    console.log("Validation error:", error.message);
  } else {
    console.log("Other error:", error.message);
  }
}
```

```python
# Custom error with multiple except
class ValidationError(Exception):
    pass

try:
    raise ValidationError("Invalid input")
except ValidationError as e:
    print(f"Validation error: {e}")
except Exception as e:
    print(f"Other error: {e}")
```

---

## 🔍 Regular Expressions

| Feature           | JavaScript                        | Python                           |
|-------------------|-----------------------------------|----------------------------------|
| **Create Regex**  | `/pattern/flags` or `new RegExp()` | `re.compile(r'pattern')`        |
| **Test Match**    | `regex.test(string)`              | `re.search(pattern, string)`     |
| **Find All**      | `string.match(/pattern/g)`        | `re.findall(pattern, string)`    |
| **Replace**       | `string.replace(/pattern/g, 'new')` | `re.sub(pattern, 'new', string)` |
| **Split**         | `string.split(/pattern/)`         | `re.split(pattern, string)`      |

```javascript
// Email validation
const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const isValid = emailRegex.test("user@example.com");
```

```python
import re

# Email validation
email_pattern = r'^[^\s@]+@[^\s@]+\.[^\s@]+$'
is_valid = bool(re.match(email_pattern, "user@example.com"))
```

---

## 🔧 Built-in Functions & Global Objects

| Category          | JavaScript                        | Python                           |
|-------------------|-----------------------------------|----------------------------------|
| **Math**          | `Math.max()`, `Math.random()`     | `max()`, `random.random()`       |
| **Length/Size**   | `array.length`, `string.length`   | `len(list)`, `len(string)`       |
| **Type Check**    | `typeof x`, `instanceof`          | `type(x)`, `isinstance()`        |
| **Parse Numbers** | `parseInt()`, `parseFloat()`      | `int()`, `float()`               |
| **Range**         | `Array.from({length: 5}, (_, i) => i)` | `range(5)`, `list(range(5))` |

```javascript
// Global functions and objects
const nums = Array.from({length: 10}, (_, i) => i);
const randomNum = Math.floor(Math.random() * 100);
const maxNum = Math.max(...nums);
```

```python
# Built-in functions
nums = list(range(10))
import random
random_num = random.randint(0, 99)
max_num = max(nums)
```

---

## ✅ Comparison Operators

| Operator         | JavaScript | Python |
|------------------|------------|--------|
| Equal            | `===`      | `==`   |
| Not Equal        | `!==`      | `!=`   |
| Greater Than     | `>`        | `>`    |
| Less Than        | `<`        | `<`    |
| Greater or Equal | `>=`       | `>=`   |
| Less or Equal    | `<=`       | `<=`   |

---

## 🔐 Boolean Logic

| Logic | JavaScript | Python |
|-------|------------|--------|
| AND   | `&&`       | `and`  |
| OR    | `||`       | `or`   |
| NOT   | `!`        | `not`  |

---

## 🧩 Null and None

| Concept     | JavaScript | Python |
|-------------|------------|--------|
| Null Value  | `null`     | `None` |

---

## 📦 Modules and Imports

| Task            | JavaScript                        | Python                          |
|-----------------|------------------------------------|----------------------------------|
| Import Module   | `import pkg from 'lib';`          | `import lib` or `from lib import pkg` |

---

## 📂 File I/O

| Task               | JavaScript (Node.js)                   | Python                             |
|--------------------|----------------------------------------|------------------------------------|
| Read File          | `fs.readFileSync("file.txt", "utf8")` | `open("file.txt").read()`          |
| Write File         | `fs.writeFileSync("file.txt", data)`  | `open("file.txt", "w").write(data)`|

---

## 🔃 JSON Handling

| Task             | JavaScript               | Python                  |
|------------------|--------------------------|--------------------------|
| Parse JSON       | `JSON.parse(jsonStr)`    | `json.loads(jsonStr)`   |
| Convert to JSON  | `JSON.stringify(obj)`    | `json.dumps(obj)`       |

---

## ⏰ Date and Time

| Task             | JavaScript                      | Python                              |
|------------------|----------------------------------|--------------------------------------|
| Current Date     | `new Date()`                    | `import datetime`<br>`datetime.datetime.now()` |
| Format Date      | `date.toISOString()`            | `date.strftime("%Y-%m-%d")`         |

---

## 🔄 Type Conversion

| Conversion       | JavaScript         | Python            |
|------------------|--------------------|--------------------|
| To Number        | `Number("5")`      | `int("5")`         |
| To String        | `String(123)`      | `str(123)`         |
| To Boolean       | `Boolean(0)`       | `bool(0)`          |

---

## ✅ Sample Comparison Function

```javascript
function isAdult(age) {
  return age >= 18 ? "Adult" : "Minor";
}
```

```python
def is_adult(age):
    return "Adult" if age >= 18 else "Minor"
```

---

## 📝 Key Takeaways

**JavaScript**:
- Uses **curly braces `{}`** and semicolons `;`
- **Hoisting** behavior with `var`
- **Prototype-based** inheritance
- **Event-driven** and **asynchronous** by nature
- **Flexible** typing with type coercion

**Python**:
- Uses **indentation** and **no semicolons**
- **No hoisting** - order matters
- **Class-based** inheritance
- **Synchronous** by default (async available)
- **Duck typing** with strong dynamic typing

---
