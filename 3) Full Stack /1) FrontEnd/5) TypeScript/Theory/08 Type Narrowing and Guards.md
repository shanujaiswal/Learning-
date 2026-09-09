# What Type Narrowing Is

--> When a variable's type is a union (`string | number`) or otherwise broad, TypeScript can't let you use type-specific operations until it's NARROWED down to a single specific type within a given code path -- narrowing is how you prove to the compiler which branch handles which type.

# typeof Narrowing

```typescript
function formatValue(value: string | number) {
  if (typeof value === "string") {
    return value.toUpperCase();   // TypeScript knows value is a string here
  }
  return value.toFixed(2);         // In this branch, TypeScript knows it's a number
}
```

# Truthiness and Equality Narrowing

```typescript
function printName(name: string | null) {
  if (name) {
    console.log(name.toUpperCase());   // Narrowed -- null/empty-string/undefined cases excluded
  }
}

function compare(a: string | number, b: string | number) {
  if (typeof a === typeof b && typeof a === "string") {
    // Narrowed to string on both sides
  }
}
```

# instanceof Narrowing

```typescript
class Dog { bark() {} }
class Cat { meow() {} }

function speak(animal: Dog | Cat) {
  if (animal instanceof Dog) {
    animal.bark();   // Narrowed to Dog
  } else {
    animal.meow();    // Narrowed to Cat
  }
}
```

# in Narrowing -- Checking for a Property's Existence

```typescript
interface Bird { fly(): void; }
interface Fish { swim(): void; }

function move(animal: Bird | Fish) {
  if ("fly" in animal) {
    animal.fly();   // Narrowed to Bird
  } else {
    animal.swim();   // Narrowed to Fish
  }
}
```

# Discriminated Unions -- The Most Robust Pattern

--> A discriminated union gives every variant a shared, literal-typed "tag" property, letting TypeScript narrow precisely based on that single field -- the recommended pattern for modeling "one of several distinct shapes" (API responses, state machines, Redux actions).

```typescript
interface LoadingState { status: "loading"; }
interface SuccessState { status: "success"; data: string; }
interface ErrorState { status: "error"; message: string; }

type FetchState = LoadingState | SuccessState | ErrorState;

function render(state: FetchState) {
  switch (state.status) {
    case "loading": return "Loading...";
    case "success": return state.data;        // Narrowed -- .data only exists on SuccessState
    case "error": return state.message;         // Narrowed -- .message only exists on ErrorState
  }
}
```

# Custom Type Guards -- User-Defined Narrowing Functions

--> A function whose return type is `arg is SpecificType` tells TypeScript "if this function returns true, narrow the argument to this type" -- useful for narrowing logic too complex for a single `typeof`/`instanceof` check.

```typescript
interface Cat { meow(): void; }
interface Dog { bark(): void; }

function isCat(animal: Cat | Dog): animal is Cat {
  return (animal as Cat).meow !== undefined;
}

function speak(animal: Cat | Dog) {
  if (isCat(animal)) {
    animal.meow();   // Narrowed via the custom type guard
  } else {
    animal.bark();
  }
}
```

# The never Type -- Exhaustiveness Checking

--> Assigning a narrowed value to a variable typed `never` in a final `else`/`default` branch causes a compile error if a new union member is ever added and forgotten in a switch/if chain -- a powerful safety net ensuring every case is always handled.

```typescript
function assertNever(x: never): never {
  throw new Error("Unexpected value: " + x);
}

function render(state: FetchState) {
  switch (state.status) {
    case "loading": return "Loading...";
    case "success": return state.data;
    case "error": return state.message;
    default: return assertNever(state);   // Errors at compile time if a new status is added but unhandled
  }
}
```
