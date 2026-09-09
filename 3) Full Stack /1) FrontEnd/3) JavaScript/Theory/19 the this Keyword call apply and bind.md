# What this Refers To

--> `this` is a special keyword whose value is determined by HOW a function is called, not where it's defined -- this is the single most common source of confusion in JavaScript, especially compared to languages where `this`/`self` is always bound to the instance a method belongs to.

# The Four Ways this Gets Determined

--> **Default binding** -- a plain function call: `this` is `undefined` in strict mode (or the global object in non-strict mode).
--> **Implicit binding** -- called as a method on an object: `this` is that object.
--> **Explicit binding** -- using `call`/`apply`/`bind` (below) to force `this` to a specific value.
--> **`new` binding** -- called with `new`: `this` is the newly created object instance.

```javascript
function show() { console.log(this); }
show();                           // undefined (strict mode) -- default binding

const obj = { name: "Alice", show() { console.log(this.name); } };
obj.show();                        // "Alice" -- implicit binding, "this" is "obj"

const detached = obj.show;
detached();                        // TypeError / undefined -- lost its "this" once detached from obj!
```

--> That last example is the classic bug -- passing a method as a callback (`setTimeout(obj.show, 100)`, an event handler) DETACHES it from its object, and `this` is no longer what you expect.

# call and apply -- Explicit, One-Time Binding

--> Both immediately INVOKE the function with a specific `this` value -- the only difference is how arguments are passed.

```javascript
function greet(greeting) {
  console.log(`${greeting}, ${this.name}`);
}

const person = { name: "Bob" };

greet.call(person, "Hello");        // call: arguments passed individually -- "Hello, Bob"
greet.apply(person, ["Hello"]);     // apply: arguments passed as an array -- "Hello, Bob"
```

--> Mnemonic: "**A**pply takes an **A**rray."

# bind -- Creating a Permanently Bound Copy

--> `bind` does NOT invoke the function immediately -- it returns a NEW function with `this` permanently locked to the given value, regardless of how that new function is later called.

```javascript
const boundGreet = greet.bind(person);
boundGreet("Hi");                    // "Hi, Bob" -- works correctly even called standalone

setTimeout(obj.show.bind(obj), 100);  // Fixes the earlier detached-callback problem
```

--> Common real-world use -- binding event handlers/callbacks in class-based code so `this` reliably refers to the class instance no matter how the handler gets invoked.

# Arrow Functions -- Lexical this

--> Arrow functions don't have their own `this` at all -- they capture `this` from the enclosing (lexical) scope at the time they're DEFINED, exactly like closures capture variables. This is why arrow functions are now the default choice for callbacks where you want `this` to "just work" without manual binding.

```javascript
class Timer {
  constructor() {
    this.seconds = 0;
  }
  start() {
    setInterval(() => {
      this.seconds++;   // Arrow function -- "this" is inherited from start(), i.e. the Timer instance
    }, 1000);
  }
}
```

--> If that callback had been written with `function () {}` instead of an arrow function, `this` inside it would NOT be the Timer instance -- another version of the exact detached-`this` problem shown above.
