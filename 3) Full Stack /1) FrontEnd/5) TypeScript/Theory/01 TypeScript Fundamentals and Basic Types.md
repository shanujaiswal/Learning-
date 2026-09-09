# What Is TypeScript

--> TypeScript is a superset of JavaScript that adds static typing -- any valid JavaScript is valid TypeScript, but TypeScript adds type annotations checked at COMPILE time, before the code ever runs.
--> TypeScript compiles ("transpiles") down to plain JavaScript -- browsers and Node never run TypeScript directly, the tsc compiler (or a bundler like Vite/webpack with a TS loader) converts .ts files to .js first.
--> Main benefit: catches type-related bugs (passing a string where a number is expected, calling a method that doesn't exist on an object) during development, instead of at runtime in production.

# Setting Up TypeScript

```bash
npm install -D typescript
npx tsc --init   # Generates a tsconfig.json with compiler options
npx tsc          # Compiles all .ts files according to tsconfig.json
```

--> tsconfig.json -- configures how TypeScript compiles -- key options: "target" (which JS version to output), "strict" (enables all strict type-checking rules -- recommended), "outDir" (where compiled JS goes).

# Basic Types

```typescript
let age: number = 25;
let name: string = "Alice";
let isActive: boolean = true;
let tags: string[] = ["admin", "user"];       // Array of strings
let scores: Array<number> = [90, 85, 100];    // Alternative array syntax
let anything: any = "avoid this";              // Opts OUT of type checking -- last resort only
let notSure: unknown = fetchData();            // Safer than 'any' -- must narrow the type before using it
```

--> any vs unknown -- any disables type checking entirely (dangerous, defeats the purpose of TypeScript); unknown also accepts any value but FORCES a type check/narrowing before you can use it, making it much safer.

```typescript
let val: unknown = "hello";
val.toUpperCase(); // Error -- TypeScript won't let you call methods on unknown without narrowing first
if (typeof val === "string") {
  val.toUpperCase(); // OK now -- TypeScript knows val is a string within this block
}
```

# Tuples

--> A tuple is a fixed-length array where each position has a specific, known type -- useful for representing a small, structured group of values.

```typescript
let point: [number, number] = [10, 20];
let entry: [string, number] = ["age", 25];
// entry[0] is always string, entry[1] is always number
```

# Enums

--> Enums define a set of named constants -- useful for representing a fixed set of options (status codes, roles, directions).

```typescript
enum Role {
  Admin,     // 0
  Editor,    // 1
  Viewer,    // 2
}
let userRole: Role = Role.Admin;

enum Status {
  Success = "SUCCESS",  // String enums are often preferred -- more readable in logs/debugging than numbers
  Failure = "FAILURE",
}
```

# Type Annotations for Functions

```typescript
function add(a: number, b: number): number {  // Parameter types + return type
  return a + b;
}

function greet(name: string): void {  // void -- function returns nothing
  console.log(`Hello, ${name}`);
}

function multiply(a: number, b: number = 1): number { // Default parameter
  return a * b;
}

function sum(...numbers: number[]): number { // Rest parameter -- all must be the same type
  return numbers.reduce((total, n) => total + n, 0);
}
```

# Type Inference

--> TypeScript can often infer types automatically without explicit annotations -- it's good practice to let inference work for simple cases and only annotate where it adds clarity or where inference can't determine the type.

```typescript
let count = 5;         // Inferred as number -- no need to write ": number"
let items = ["a", "b"]; // Inferred as string[]

function double(n: number) { // Return type inferred as number automatically
  return n * 2;
}
```

# Union and Literal Types

--> A union type allows a value to be one of several specified types, using |.

```typescript
let id: string | number;
id = "abc123"; // OK
id = 42;        // Also OK
id = true;      // Error -- boolean is not part of the union

function printId(id: string | number) {
  if (typeof id === "string") {
    console.log(id.toUpperCase()); // TypeScript narrows the type inside this branch
  } else {
    console.log(id.toFixed(2));
  }
}
```

--> Literal types restrict a value to specific exact values (not just a type, but specific allowed values).

```typescript
let direction: "up" | "down" | "left" | "right";
direction = "up";     // OK
direction = "sideways"; // Error -- not one of the allowed literal values
```

# The null and undefined Types

--> With strictNullChecks enabled (part of "strict" mode), null and undefined are NOT automatically assignable to other types -- must be explicitly included in a union.

```typescript
let username: string | null = null; // Must explicitly allow null
username = "Alice"; // OK later

function findUser(id: number): string | undefined {
  return id === 1 ? "Alice" : undefined; // Explicit that this function might not find a result
}
```

--> Optional chaining (?.) and nullish coalescing (??) -- both work identically to plain JavaScript, and TypeScript understands their narrowing effect.

```typescript
const city = user?.address?.city ?? "Unknown"; // Safely access nested optional properties, fall back if null/undefined
```

# Deep Dive -- const Assertions

--> Normally, TypeScript widens a literal value's type to its general category when inferring (a string literal `"admin"` infers as the general `string` type, not the specific literal `"admin"`). The `as const` assertion tells TypeScript to infer the NARROWEST possible type instead, treating the value as an immutable, exact literal.

```typescript
let role1 = "admin";          // Inferred as type: string (widened -- could be reassigned to any string)
let role2 = "admin" as const;   // Inferred as type: "admin" (the exact literal, cannot be reassigned to anything else)

const config = { mode: "production", version: 2 };
// config.mode is inferred as type: string (widened)

const configConst = { mode: "production", version: 2 } as const;
// configConst.mode is inferred as type: "production" (exact literal) -- and every property becomes readonly too
```

--> This is particularly useful for tuples and discriminated-union-style literal values -- without `as const`, an array literal like `[1, 2, 3]` infers as `number[]` (a general, resizable array type) rather than the exact tuple `[1, 2, 3]`, which matters when passing values to functions expecting a specific tuple shape (like the Tuples section above).

# Deep Dive -- Type Assertions vs Type Casting

--> TypeScript's `as` keyword (or the older `<Type>value` angle-bracket syntax) tells the compiler "trust me, treat this value as this specific type" -- it's a compile-time-only instruction with ZERO runtime effect, unlike a real type conversion (like `Number("42")`, which actually transforms the value).

```typescript
const input = document.getElementById("username") as HTMLInputElement;
input.value = "Alice";   // TypeScript now allows .value since it trusts the assertion -- but this is NOT verified at runtime

const wrongAssertion = "hello" as unknown as number;   // Compiles fine, but "hello" is still a string at runtime -- a lie to the compiler
console.log(typeof wrongAssertion);   // "string" -- the assertion changed nothing about the actual runtime value
```

--> **Why this matters** -- an incorrect type assertion doesn't cause a compile error (TypeScript trusts your explicit claim), but it CAN cause a genuine runtime bug later, since the actual value never changed, only what the compiler believes about it. Assertions should be used sparingly, specifically in situations where you genuinely know more about a value's type than TypeScript can infer on its own (like the DOM query above, where `getElementById` can only return the general `HTMLElement | null` type).
