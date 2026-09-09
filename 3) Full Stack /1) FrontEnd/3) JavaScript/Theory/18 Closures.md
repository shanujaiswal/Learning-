# What a Closure Is

--> A closure is a function that "remembers" the variables from the scope it was created in, even after that outer scope has finished executing.
--> Every function in JavaScript forms a closure over its surrounding scope -- this isn't an opt-in feature, it's how scoping fundamentally works; closures are simply what becomes visible/useful when an inner function outlives its outer function's execution.

```javascript
function makeCounter() {
  let count = 0;              // "count" lives in makeCounter's scope

  return function increment() {
    count++;                   // This inner function "closes over" count
    return count;
  };
}

const counter = makeCounter();
counter();   // 1
counter();   // 2 -- count persisted between calls, even though makeCounter() already returned
```

--> Each call to `makeCounter()` creates a brand new, independent `count` variable -- closures capture a reference to the variable itself, not a snapshot of its value, and each function invocation gets its own separate scope to close over.

```javascript
const counterA = makeCounter();
const counterB = makeCounter();
counterA();   // 1
counterA();   // 2
counterB();   // 1 -- completely independent from counterA
```

# Practical Use -- Private State (Data Encapsulation)

--> Before ES2022 private class fields existed, closures were THE way to create genuinely private variables in JavaScript -- a variable in an outer function's scope is completely inaccessible from outside, reachable only through functions deliberately returned from that scope.

```javascript
function createBankAccount(initialBalance) {
  let balance = initialBalance;   // Not accessible from outside at all

  return {
    deposit(amount) { balance += amount; return balance; },
    withdraw(amount) {
      if (amount > balance) throw new Error("Insufficient funds");
      balance -= amount;
      return balance;
    },
    getBalance() { return balance; }
  };
}

const account = createBankAccount(100);
account.deposit(50);        // 150
// account.balance -- undefined, no direct access possible
```

# Practical Use -- Function Factories

--> Closures let you generate specialized functions from a general template by "baking in" some arguments at creation time.

```javascript
function multiplyBy(factor) {
  return function (number) {
    return number * factor;
  };
}

const double = multiplyBy(2);
const triple = multiplyBy(3);
double(5);   // 10
triple(5);   // 15
```

# The Classic Loop-and-Closure Pitfall

--> A famous historical gotcha: using `var` (function-scoped) in a loop meant every closure created inside shared the SAME variable, so they'd all see its final value.

```javascript
for (var i = 0; i < 3; i++) {
  setTimeout(() => console.log(i), 100);
}
// Logs: 3, 3, 3  -- every callback closed over the SAME "i", which had already finished looping to 3

for (let j = 0; j < 3; j++) {
  setTimeout(() => console.log(j), 100);
}
// Logs: 0, 1, 2  -- "let" creates a NEW binding of "j" for each loop iteration
```

--> This is precisely why `let`/`const` (block-scoped) replaced `var` (function-scoped) as the default choice -- it makes closures inside loops behave the way most developers intuitively expect.

# Memory Consideration

--> A variable captured by a closure can't be garbage-collected as long as the closure itself is still reachable -- this is usually fine and intentional, but holding onto large objects/DOM nodes inside a long-lived closure (e.g. an event listener that's never removed) is a real, common source of memory leaks in long-running applications.
