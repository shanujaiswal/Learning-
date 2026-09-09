# Interfaces -- Defining Object Shapes

--> An interface describes the shape (properties and types) an object must have -- purely a compile-time construct, produces no JavaScript output.

```typescript
interface User {
  id: number;
  name: string;
  email: string;
  isAdmin?: boolean; // Optional property -- may or may not be present
  readonly createdAt: Date; // Cannot be reassigned after initial creation
}

const user: User = {
  id: 1,
  name: "Alice",
  email: "alice@example.com",
  createdAt: new Date(),
};

user.createdAt = new Date(); // Error -- readonly property cannot be reassigned
```

# Type Aliases

--> The `type` keyword creates a named alias for any type -- objects, unions, primitives, tuples, function signatures.

```typescript
type ID = string | number;
type Point = { x: number; y: number };
type Callback = (error: Error | null, data?: string) => void;
```

# Interface vs Type Alias -- When to Use Which

--> Interfaces can be extended and merged (declaration merging) -- multiple declarations of the same interface name combine automatically. Type aliases cannot be re-opened this way.
--> Type aliases can represent unions, tuples, and primitives directly; interfaces can only describe object/function shapes.
--> Common convention: use `interface` for object shapes (especially ones meant to be extended, like component props), use `type` for unions, tuples, and utility compositions.

```typescript
interface Animal { name: string; }
interface Dog extends Animal { breed: string; } // Interface extension

type Status = "active" | "inactive" | "pending"; // Union -- must use type, not interface
```

# Generics -- Reusable Type-Safe Components

--> Generics let a function, interface, or class work with multiple types while preserving type safety, instead of using `any`.

```typescript
function identity<T>(value: T): T {
  return value;
}
identity<string>("hello"); // T is explicitly string
identity(42);               // T inferred as number

interface Box<T> {
  contents: T;
}
const stringBox: Box<string> = { contents: "hello" };
const numberBox: Box<number> = { contents: 42 };

function firstElement<T>(arr: T[]): T | undefined {
  return arr[0];
}
firstElement([1, 2, 3]);        // number | undefined
firstElement(["a", "b", "c"]);  // string | undefined
```

--> Generic constraints -- restrict what types T can be using `extends`.

```typescript
function getLength<T extends { length: number }>(item: T): number {
  return item.length; // Safe -- T is guaranteed to have a .length property
}
getLength("hello");      // OK -- strings have .length
getLength([1, 2, 3]);    // OK -- arrays have .length
getLength(42);           // Error -- number has no .length
```

# Utility Types

--> TypeScript ships several built-in utility types that transform existing types.

```typescript
interface User { id: number; name: string; email: string; }

type PartialUser = Partial<User>;   // All properties become optional -- { id?, name?, email? }
type ReadonlyUser = Readonly<User>; // All properties become readonly
type UserPreview = Pick<User, "id" | "name">;    // Only keeps specified properties
type UserWithoutEmail = Omit<User, "email">;      // Removes specified properties
type UserRecord = Record<string, User>;           // Object type with string keys, User values
```

--> Common real-world use -- Partial<T> is frequently used for "update" functions where only some fields are being changed.

```typescript
function updateUser(id: number, changes: Partial<User>) {
  // changes might only include { name: "New Name" } -- other fields optional
}
```

# Intersection Types

--> Combines multiple types into one that must satisfy ALL of them, using &.

```typescript
type Person = { name: string };
type Employee = { salary: number };
type StaffMember = Person & Employee; // Must have both name AND salary

const staff: StaffMember = { name: "Alice", salary: 50000 };
```

# Type Guards and Narrowing

--> A type guard is a check that lets TypeScript narrow a broader type down to a more specific one within a conditional block.

```typescript
function isString(value: unknown): value is string { // Custom type guard -- "value is string" is the type predicate
  return typeof value === "string";
}

function process(input: unknown) {
  if (isString(input)) {
    console.log(input.toUpperCase()); // TypeScript knows input is string here
  }
}
```

--> `in` operator narrowing -- checks if a property exists to distinguish between union members.

```typescript
interface Cat { meow: () => void; }
interface Dog { bark: () => void; }

function makeSound(animal: Cat | Dog) {
  if ("meow" in animal) {
    animal.meow(); // Narrowed to Cat
  } else {
    animal.bark(); // Narrowed to Dog
  }
}
```

# Discriminated Unions

--> A pattern where each variant of a union has a common literal property (a "discriminant") that TypeScript uses to narrow the type automatically.

```typescript
type Shape =
  | { kind: "circle"; radius: number }
  | { kind: "rectangle"; width: number; height: number };

function area(shape: Shape): number {
  switch (shape.kind) {
    case "circle":
      return Math.PI * shape.radius ** 2; // TypeScript knows this variant has 'radius'
    case "rectangle":
      return shape.width * shape.height;   // And this one has 'width'/'height'
  }
}
```

# Deep Dive -- Mapped Types

--> A mapped type builds a NEW type by transforming every property of an EXISTING type -- this is literally how built-in utility types like `Partial<T>` and `Readonly<T>` (shown above) are actually implemented internally.

```typescript
type MyPartial<T> = { [K in keyof T]?: T[K] };      // Reimplementing Partial<T> from scratch
type MyReadonly<T> = { readonly [K in keyof T]: T[K] };   // Reimplementing Readonly<T> from scratch

interface User { id: number; name: string; }
type OptionalUser = MyPartial<User>;   // { id?: number; name?: string }
```

--> `keyof T` produces a union of all of `T`'s property names as string literal types (`"id" | "name"` for the `User` interface above) -- combined with `in`, this lets you iterate over every key at the TYPE level, the same way a `for...in` loop iterates over keys at the VALUE level.
--> Custom mapped types are useful when the built-in utilities (`Partial`, `Pick`, `Omit`) don't quite express what's needed -- e.g. making every property NULLABLE instead of optional:

```typescript
type Nullable<T> = { [K in keyof T]: T[K] | null };
type NullableUser = Nullable<User>;   // { id: number | null; name: string | null }
```

# Deep Dive -- Conditional Types

--> A conditional type selects between two types based on a type-level condition, using syntax that mirrors JavaScript's ternary operator but operates entirely on TYPES rather than values.

```typescript
type IsString<T> = T extends string ? "yes" : "no";

type A = IsString<string>;   // "yes"
type B = IsString<number>;    // "no"
```

--> **`infer`** -- extracts and captures a type from within a conditional type's structure, letting you "pull out" a piece of a more complex type.

```typescript
type ElementType<T> = T extends (infer U)[] ? U : never;

type Numbers = ElementType<number[]>;   // number -- extracted the array's element type
type Strings = ElementType<string[]>;    // string
```

--> This exact mechanism is how the built-in `ReturnType<F>` and `Parameters<F>` utility types work -- `ReturnType<F> = F extends (...args: any[]) => infer R ? R : never` captures whatever type a function actually returns, without you needing to write it out manually and risk it drifting out of sync if the function's implementation changes later.
