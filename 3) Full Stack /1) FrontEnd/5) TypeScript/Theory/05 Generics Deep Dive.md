# Why Generics Exist

--> Without generics, you'd either write duplicate functions for every type (`getFirstString`, `getFirstNumber`) or use `any` (which throws away all type safety). Generics let a function/class/interface work with MULTIPLE types while still preserving full type-checking for whichever specific type is actually used.

```typescript
function getFirst<T>(arr: T[]): T {
  return arr[0];
}

const num = getFirst([1, 2, 3]);        // T is inferred as number -- num: number
const str = getFirst(["a", "b"]);        // T is inferred as string -- str: string
```

--> `T` is a type parameter -- a placeholder filled in with the ACTUAL type either explicitly (`getFirst<number>([1,2,3])`) or, more commonly, inferred automatically from the arguments passed.

# Generic Constraints

--> `extends` restricts a generic to types that satisfy a certain shape -- without a constraint, TypeScript can't assume the type parameter has any particular properties/methods.

```typescript
function getLength<T extends { length: number }>(item: T): number {
  return item.length;
}

getLength("hello");        // OK -- strings have .length
getLength([1, 2, 3]);       // OK -- arrays have .length
// getLength(42);           // Error -- number has no .length property
```

# Multiple Type Parameters

```typescript
function merge<T, U>(a: T, b: U): T & U {
  return { ...a, ...b };
}

const merged = merge({ name: "Alice" }, { age: 30 });
// merged: { name: string } & { age: number }
```

# Generic Interfaces and Classes

```typescript
interface ApiResponse<T> {
  data: T;
  status: number;
  error?: string;
}

const userResponse: ApiResponse<{ name: string }> = {
  data: { name: "Alice" },
  status: 200,
};

class Box<T> {
  constructor(private contents: T) {}
  getContents(): T {
    return this.contents;
  }
}

const numberBox = new Box<number>(42);
```

# Default Type Parameters

```typescript
interface Container<T = string> {
  value: T;
}

const c1: Container = { value: "hello" };          // Uses the default, T = string
const c2: Container<number> = { value: 42 };         // Explicitly overridden to number
```

# keyof and Generic Constraints Together

--> A very common real-world generic pattern -- a function that safely accesses a property on an object by key, constrained so the key MUST actually exist on that object type (catching typos at compile time).

```typescript
function getProperty<T, K extends keyof T>(obj: T, key: K): T[K] {
  return obj[key];
}

const user = { name: "Alice", age: 30 };
getProperty(user, "name");    // OK, returns string
// getProperty(user, "email"); // Error -- "email" is not a key of user
```

# Why Generics Matter for Reusable, Type-Safe Code

--> Generics are what let library code (array methods, Promise, React's `useState<T>`) stay fully type-safe while working with whatever specific type the CALLER passes in -- without generics, every reusable utility would need to sacrifice either reusability or type safety.
