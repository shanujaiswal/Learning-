# Why Design Patterns

--> Design patterns are proven, reusable SOLUTIONS to recurring software design problems -- not framework features, but general structural ideas that show up across many codebases and languages.

# Module Pattern

--> Uses a closure (covered earlier) to create private state and expose only a specific public interface -- the original way to achieve encapsulation in JavaScript, before ES Modules existed.

```javascript
const CounterModule = (function () {
  let count = 0;   // Private -- inaccessible from outside

  return {
    increment() { return ++count; },
    reset() { count = 0; },
  };
})();

CounterModule.increment();   // 1
// CounterModule.count -- undefined, truly private
```

--> Native ES Modules (`import`/`export`, covered in the JSON/Storage/Modules file) now provide this same encapsulation at the file level, making the manual IIFE-based Module Pattern less necessary for new code -- but it still appears throughout older/existing codebases and libraries.

# Singleton Pattern

--> Ensures a class/object has only ONE instance, shared across the whole application -- useful for things like a single shared configuration object, a single database connection pool, or a single global app state store.

```javascript
class Database {
  constructor() {
    if (Database.instance) {
      return Database.instance;   // Return the existing instance instead of creating a new one
    }
    this.connection = "connected";
    Database.instance = this;
  }
}

const db1 = new Database();
const db2 = new Database();
console.log(db1 === db2);   // true -- both variables point to the exact same instance
```

--> In modern JS module systems, a Singleton is often achieved more simply -- a module's top-level state is naturally shared by every file that imports it, since a module is only ever evaluated once.

# Observer Pattern

--> An object (the "subject") maintains a list of dependents ("observers") and notifies all of them automatically when its state changes -- the foundational pattern behind DOM events, React's `useState` re-renders, and Vue's reactivity system.

```javascript
class EventEmitter {
  constructor() {
    this.listeners = {};
  }

  on(event, callback) {
    (this.listeners[event] ??= []).push(callback);
  }

  emit(event, data) {
    (this.listeners[event] || []).forEach(cb => cb(data));
  }
}

const emitter = new EventEmitter();
emitter.on("userLoggedIn", (user) => console.log(`Welcome, ${user.name}`));
emitter.emit("userLoggedIn", { name: "Alice" });
```

--> Node.js's built-in `EventEmitter` (covered in the Node.js Async Patterns file) is a direct, production-ready implementation of exactly this pattern.

# Factory Pattern

--> Centralizes object creation behind a function/method, hiding the specific logic of WHICH type of object to create -- useful when object creation involves conditional logic that shouldn't be duplicated everywhere an object is needed.

```javascript
function createShape(type) {
  switch (type) {
    case "circle": return { type, area: (r) => Math.PI * r * r };
    case "square": return { type, area: (s) => s * s };
    default: throw new Error("Unknown shape type");
  }
}

const shape = createShape("circle");
```

# Strategy Pattern

--> Defines a family of interchangeable algorithms/behaviors and selects one at runtime -- avoids a large `if/else` or `switch` block scattered through business logic by injecting the desired behavior as a parameter instead.

```javascript
const paymentStrategies = {
  creditCard: (amount) => `Charging $${amount} to credit card`,
  paypal: (amount) => `Charging $${amount} via PayPal`,
};

function processPayment(method, amount) {
  return paymentStrategies[method](amount);
}
```
