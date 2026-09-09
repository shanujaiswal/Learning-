# Classes with Type Annotations

```typescript
class Animal {
  name: string;
  private age: number;       // Only accessible within this class
  protected species: string; // Accessible within this class AND subclasses
  readonly id: number;       // Can only be set once, in the constructor

  constructor(name: string, age: number, species: string, id: number) {
    this.name = name;
    this.age = age;
    this.species = species;
    this.id = id;
  }

  makeSound(): void {
    console.log(`${this.name} makes a sound`);
  }
}
```

# Access Modifiers

--> public (default) -- accessible from anywhere.
--> private -- accessible only within the same class, not subclasses or outside code.
--> protected -- accessible within the class and any subclass, but not from outside.
--> readonly -- can be assigned once (in the constructor or at declaration), then never reassigned.

# Parameter Properties -- Shorthand Constructor Syntax

--> TypeScript lets you declare AND assign class properties directly in the constructor parameter list, removing boilerplate.

```typescript
class User {
  constructor(
    public name: string,
    private email: string,
    readonly id: number
  ) {} // No need to manually write this.name = name, etc. -- TypeScript generates it

  getEmail(): string {
    return this.email;
  }
}
const user = new User("Alice", "alice@example.com", 1);
```

# Interfaces with Classes -- implements

--> A class can implement an interface, guaranteeing it provides all the properties/methods the interface requires.

```typescript
interface Shape {
  area(): number;
}

class Circle implements Shape {
  constructor(private radius: number) {}
  area(): number {
    return Math.PI * this.radius ** 2;
  }
}
```

# Abstract Classes

--> An abstract class cannot be instantiated directly -- it's meant to be extended, and can declare abstract methods that subclasses MUST implement.

```typescript
abstract class Shape {
  abstract area(): number; // No implementation here -- subclasses must provide one

  describe(): string {
    return `This shape has an area of ${this.area()}`; // Regular method, usable as-is
  }
}

class Square extends Shape {
  constructor(private side: number) { super(); }
  area(): number { return this.side ** 2; } // Required implementation
}

new Shape(); // Error -- cannot instantiate an abstract class directly
```

# Function Types and Overloads

```typescript
// Function type as a variable/parameter annotation
let calculate: (a: number, b: number) => number;
calculate = (a, b) => a + b;

// Function overloads -- multiple call signatures for the same function name
function format(value: string): string;
function format(value: number): string;
function format(value: string | number): string {
  if (typeof value === "number") return value.toFixed(2);
  return value.trim();
}
```

--> Overloads let callers get specific, accurate autocomplete/type-checking for each way a function can be called, even though there's one underlying implementation.

# Generics with Classes

```typescript
class Stack<T> {
  private items: T[] = [];

  push(item: T): void { this.items.push(item); }
  pop(): T | undefined { return this.items.pop(); }
  peek(): T | undefined { return this.items[this.items.length - 1]; }
}

const numberStack = new Stack<number>();
numberStack.push(1);
numberStack.push(2);

const stringStack = new Stack<string>();
stringStack.push("a");
```

# Decorators (Brief Overview)

--> Decorators are a special declaration attached to classes/methods/properties that can modify or annotate them at design time -- heavily used in frameworks like Angular and NestJS.
--> Requires "experimentalDecorators": true in tsconfig.json (legacy decorators) -- TypeScript 5+ also supports the newer standardized decorator proposal.

```typescript
function Log(target: any, propertyKey: string) {
  console.log(`Property decorated: ${propertyKey}`);
}

class Product {
  @Log
  name: string = "";
}
```

--> Not commonly needed in plain React/Express projects -- mainly relevant if working with Angular or NestJS, which build their entire architecture around decorators.

# Deep Dive -- Interfaces and Multiple Inheritance-Like Behavior

--> TypeScript classes can only `extend` ONE base class (matching JavaScript's single-inheritance model, covered in the OOP and Prototypes file), but a class can `implements` MULTIPLE interfaces simultaneously -- letting a class guarantee it satisfies several independent contracts at once, without the complications real multiple inheritance would introduce.

```typescript
interface Flyable { fly(): void; }
interface Swimmable { swim(): void; }

class Duck implements Flyable, Swimmable {
  fly() { console.log("Duck flying"); }
  swim() { console.log("Duck swimming"); }
}
```

--> This is TypeScript's answer to the "FlyingFish" problem raised in the Composition Over Inheritance discussion in the OOP file -- rather than forcing a rigid single-parent class hierarchy, interfaces let a class declare and be checked against MULTIPLE independent capability contracts.

# Deep Dive -- Mixins -- Composing Class Behavior

--> TypeScript supports a "mixin" pattern for composing reusable behavior into classes, directly mirroring the composition-over-inheritance idea covered in the OOP and Prototypes file, but expressed with TypeScript's class/function syntax.

```typescript
type Constructor<T = {}> = new (...args: any[]) => T;

function Timestamped<TBase extends Constructor>(Base: TBase) {
  return class extends Base {
    timestamp = Date.now();
  };
}

class User { constructor(public name: string) {} }

const TimestampedUser = Timestamped(User);
const instance = new TimestampedUser("Alice");
console.log(instance.name, instance.timestamp);   // "Alice", 1735689600000
```

--> `Timestamped` is a function that takes a class and RETURNS a new class extending it with additional behavior -- multiple mixins can be layered (`Timestamped(Serializable(User))`), letting behavior be composed flexibly rather than baked into one rigid inheritance chain.

# Deep Dive -- Generic Constraints With Default Types

```typescript
class ApiResponse<T = unknown> {   // Default generic type -- ApiResponse without <T> falls back to "unknown"
  constructor(public data: T, public status: number) {}
}

const response1 = new ApiResponse({ id: 1 }, 200);   // T inferred as { id: number }
const response2: ApiResponse = new ApiResponse("raw text", 200);   // Uses the default "unknown" since no <T> was given explicitly
```

--> Providing a sensible default (as covered more generally in the Generics Deep Dive file) means consumers aren't FORCED to always specify a type parameter, while still getting full type safety whenever they do.
