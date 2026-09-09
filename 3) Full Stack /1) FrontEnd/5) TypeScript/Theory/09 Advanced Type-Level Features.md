# Decorators (TS 5+ Standard Decorators)

--> A decorator is a function that intercepts and can modify a class, method, property, or accessor at definition time -- written as `@decoratorName` directly above the thing it decorates. TypeScript 5.0 switched to implementing the TC39 STANDARD decorators proposal (the same feature now landing in plain JavaScript), replacing the older experimental/`--experimentalDecorators` implementation that frameworks like Angular and NestJS historically relied on.
--> A method decorator receives the original method as its target plus a `context` object describing what's being decorated (`kind`, `name`, etc.), and returns either nothing (keep the original method) or a replacement function.

```typescript
function logCall(originalMethod: any, context: ClassMethodDecoratorContext) {
  const methodName = String(context.name);
  return function (this: any, ...args: any[]) {
    console.log(`Calling ${methodName} with`, args);
    return originalMethod.call(this, ...args);
  };
}

class Calculator {
  @logCall
  add(a: number, b: number) {
    return a + b;
  }
}

new Calculator().add(2, 3);   // Logs "Calling add with [2, 3]", then returns 5
```

--> A class decorator receives the class itself and can return a new class that replaces it entirely -- commonly used for registering a class somewhere, or wrapping it to add functionality without touching its actual definition.

```typescript
function sealed(target: any) {
  Object.seal(target);
  Object.seal(target.prototype);
  return target;
}

@sealed
class Config {
  apiUrl = "https://api.example.com";
}
```

--> Standard decorators (TS 5+) are notably DIFFERENT from the old experimental ones -- no `emitDecoratorMetadata`/`reflect-metadata` dependency required, and the signature/behavior is aligned with the actual JS proposal rather than a TypeScript-specific extension. Older codebases (or libraries like older TypeORM/NestJS versions) may still be on `experimentalDecorators: true` -- mixing the two styles in one project is not supported, so check which mode a codebase uses in its `tsconfig.json` before writing new decorators (see file 07 tsconfig Deep Dive).

# Module Augmentation -- declare module

--> `declare module` lets you ADD to an existing module's types from outside that module -- most commonly used to extend a third-party library's types with properties/methods your app actually attaches to them, without forking the library itself.

```typescript
// express-augmentation.d.ts -- adding a custom property to Express's Request type
declare module "express" {
  interface Request {
    user?: { id: number; role: string };   // Now every Express Request has an optional .user
  }
}

// Anywhere else in the project, once this file is included in compilation:
app.get("/profile", (req, res) => {
  console.log(req.user?.id);   // TypeScript now knows about .user without modifying express's own types
});
```

--> This works specifically because TypeScript MERGES multiple `interface` declarations with the same name inside the same `declare module` target (declaration merging, referenced in file 02 Interfaces Types and Advanced Types for regular interfaces too) -- augmenting `express`'s `Request` interface adds to it rather than replacing it.

# declare global -- Extending the Global Scope

--> `declare global` (usually inside a `.d.ts` file, or a regular `.ts` file that also has at least one `import`/`export` to make it a module) adds properties to GLOBAL objects like `window` or `globalThis` -- necessary whenever code legitimately attaches something to the global scope (an analytics snippet, a feature flag object injected by the server) that TypeScript doesn't know about by default.

```typescript
// globals.d.ts
declare global {
  interface Window {
    __APP_CONFIG__: { apiUrl: string; env: "dev" | "prod" };
  }
}
export {};   // An empty export makes this file a MODULE, which is required for `declare global` to work correctly

// Anywhere else in the project:
console.log(window.__APP_CONFIG__.apiUrl);   // No longer a type error
```

--> A `.d.ts` file with no `import`/`export` at all is treated as a global SCRIPT (its declarations apply everywhere automatically) -- one that DOES have imports/exports is treated as a module, whose declarations only apply where explicitly imported, UNLESS wrapped in `declare global` as shown above to intentionally punch back out to the global scope.

# Authoring Your Own .d.ts Declaration Files

--> A `.d.ts` file contains ONLY type information -- no actual runtime code, no function bodies -- describing the shape of JavaScript that TypeScript otherwise has no type information for (an untyped npm package, a global script loaded via a `<script>` tag, a `.json`/`.css` import your bundler handles specially).

```typescript
// legacy-lib.d.ts -- describing an untyped JS library
declare module "legacy-lib" {
  export function initialize(config: { debug?: boolean }): void;
  export const VERSION: string;
  export default class Widget {
    constructor(elementId: string);
    render(): void;
  }
}
```

```typescript
import Widget, { initialize, VERSION } from "legacy-lib";   // Now fully typed, despite the library shipping no types itself
```

--> Publishing types for a real package -- the `"types"` (or `"typings"`) field in `package.json` points consumers to your `.d.ts` entry file; for packages you don't control, the community-maintained `@types/package-name` packages (DefinitelyTyped) fill this gap, and `npm install --save-dev @types/lodash` (for example) is the usual way to get types for an untyped package without writing your own.
--> `declare` on its own (without `module`/`global`) describes a single value's type without providing an implementation -- used for ambient values that exist at runtime through some other mechanism (a global injected by a bundler's `define` plugin, a variable a script tag creates).

```typescript
declare const __BUILD_VERSION__: string;   // Injected by the build tool at compile time, not defined anywhere in source
console.log(`Build: ${__BUILD_VERSION__}`);   // Type-checks correctly, even though nothing in the codebase defines it
```

# Branded (Nominal) Types

--> TypeScript's type system is STRUCTURAL by default -- two types with the same shape are considered interchangeable, even if they represent conceptually different things (a `UserId` and a `ProductId` are both just `number` structurally, so nothing stops you from passing one where the other belongs).

```typescript
type UserId = number;
type ProductId = number;

function getUser(id: UserId) { /* ... */ }
const productId: ProductId = 42;
getUser(productId);   // No error at all -- structurally, both are just "number"
```

--> A branded type fakes NOMINAL typing (distinct types are only interchangeable if explicitly declared so, as in Java/C#) by attaching a unique, unused "brand" property that only exists at the type level -- it's never actually present on the real runtime value, but it's enough to make TypeScript reject a plain `number` where a specifically-branded one is required.

```typescript
type UserId = number & { readonly __brand: "UserId" };
type ProductId = number & { readonly __brand: "ProductId" };

function toUserId(id: number): UserId {
  return id as UserId;   // The one place the cast is allowed -- a deliberate, explicit conversion boundary
}

function getUser(id: UserId) { /* ... */ }

const rawId = 42;
getUser(rawId);                 // Error -- plain number isn't a UserId
getUser(toUserId(rawId));        // OK -- explicitly converted through the sanctioned constructor function
```

--> This pattern is exactly how libraries like Zod's `.brand()` and many "type-safe ID" utilities work under the hood -- it costs nothing at runtime (the `__brand` property never actually exists on the object; it's a pure compile-time fiction) while catching an entire category of "passed the wrong kind of ID" bugs before the code ever runs.

# Template Literal Types

--> Template literal types build new STRING LITERAL types out of other literal/union types, using the exact same `${}` syntax as a JS template literal, but operating on types rather than runtime string values.

```typescript
type Direction = "top" | "right" | "bottom" | "left";
type Margin = `margin-${Direction}`;   // "margin-top" | "margin-right" | "margin-bottom" | "margin-left"

function setMargin(prop: Margin, value: string) { /* ... */ }
setMargin("margin-top", "10px");    // OK
setMargin("margin-diagonal", "10px");   // Error -- not a valid combination
```

--> Combining multiple unions inside one template literal type produces the full CARTESIAN PRODUCT of every combination -- useful for generating exhaustive, type-safe string unions like CSS-in-JS property names or event name patterns, without hand-listing every combination.

```typescript
type Size = "sm" | "md" | "lg";
type Color = "red" | "blue";
type ClassName = `btn-${Size}-${Color}`;
// "btn-sm-red" | "btn-sm-blue" | "btn-md-red" | "btn-md-blue" | "btn-lg-red" | "btn-lg-blue"
```

--> Combined with `infer` (see file 02 Interfaces Types and Advanced Types for the conditional-type basics of `infer`), template literal types can also PARSE apart a string literal type at the type level.

```typescript
type ExtractRouteParam<T extends string> = T extends `${string}/:${infer Param}`
  ? Param
  : never;

type Param = ExtractRouteParam<"/users/:id">;   // "id"
```

# The satisfies Operator (TS 4.9+)

--> `satisfies` checks that a value matches a given type WITHOUT widening the value's own inferred type to that type -- solving a genuine gap between annotating a variable (`: Type`, which widens/loses literal specificity) and not annotating it at all (which loses the safety check entirely).

```typescript
type Colors = Record<string, [number, number, number] | string>;

// Using a type annotation -- loses the specific literal/tuple type information
const palette1: Colors = {
  red: [255, 0, 0],
  blue: "#0000ff",
};
palette1.red[0];   // Error-prone -- TypeScript now widens `.red` to `[number,number,number] | string`, losing the fact it's specifically a tuple

// Using satisfies -- still checked against Colors, but palette2's OWN inferred type is preserved
const palette2 = {
  red: [255, 0, 0],
  blue: "#0000ff",
} satisfies Colors;
palette2.red[0];   // OK -- TypeScript still knows `.red` is specifically the tuple [255, 0, 0], so this is a number
```

--> `satisfies` also catches typos/excess properties the same way a type annotation would (unlike having no check at all), while additionally rejecting a value that DOESN'T match the target type -- it gives you both compile-time validation AND the most specific inferred type simultaneously, which neither plain annotation nor no annotation could do alone.

# Index Signatures

--> An index signature describes the type of ALL properties an object can have when the exact set of keys isn't known ahead of time -- a dictionary/map-like shape, distinct from listing specific named properties.

```typescript
interface StringDictionary {
  [key: string]: number;   // Any string key maps to a number value
}

const scores: StringDictionary = { alice: 95, bob: 87 };
scores.carol = 100;         // OK -- any new string key is allowed
scores.dave = "high";        // Error -- value must be a number
```

--> An index signature can coexist with specific named properties, but every named property's type must be ASSIGNABLE to the index signature's value type, since the named property is really just a special case the index signature also covers.

```typescript
interface Config {
  [key: string]: string | number;
  name: string;    // OK -- string is assignable to (string | number)
  version: number;  // OK -- number is assignable to (string | number)
}
```

--> `Record<string, T>` (from file 06 Utility Types) is, under the hood, essentially a convenient built-in ALIAS for exactly this pattern -- `Record<string, number>` and the `StringDictionary` interface above describe the same shape.

# Distributive Conditional Types Over Unions

--> A conditional type applied to a NAKED type parameter (the type parameter appears alone, not wrapped in something else like `T[]` or `Box<T>`) automatically DISTRIBUTES over each member of a union individually, rather than treating the whole union as one thing.

```typescript
type ToArray<T> = T extends any ? T[] : never;

type Result = ToArray<string | number>;   // string[] | number[] -- distributed over EACH union member separately
// NOT (string | number)[], which is what you might naively expect
```

--> Wrapping the type parameter in a tuple (`[T]`) suppresses this automatic distribution, forcing the conditional to evaluate against the WHOLE union at once instead -- a deliberate escape hatch for the rarer cases where distribution isn't what you want.

```typescript
type ToArrayNonDistributive<T> = [T] extends [any] ? T[] : never;
type Result2 = ToArrayNonDistributive<string | number>;   // (string | number)[] -- NOT distributed
```

--> This distributive behavior is exactly why `Exclude<T, U>` and `Extract<T, U>` (file 06 Utility Types) correctly filter INDIVIDUAL union members rather than evaluating the union as one opaque blob -- `Exclude<T, U> = T extends U ? never : T` only works as a per-member filter because of this distribution rule.

# Recursive Conditional Types

--> A conditional type can reference ITSELF, letting TypeScript express operations over arbitrarily nested/deep structures -- unions, tuples, or object trees -- entirely at the type level, though the compiler enforces a recursion depth limit to prevent runaway type-checking.

```typescript
type DeepReadonly<T> = T extends object
  ? { readonly [K in keyof T]: DeepReadonly<T[K]> }
  : T;

interface Nested { a: { b: { c: number } } }
type FrozenNested = DeepReadonly<Nested>;
// { readonly a: { readonly b: { readonly c: number } } } -- recursion applies readonly at EVERY nesting level, not just the top
```

--> Flattening a nested array type is another common recursive pattern -- notice the base case (`T` on its own, not an array) is what stops the recursion, exactly like a base case in a recursive JS function.

```typescript
type Flatten<T> = T extends (infer U)[] ? Flatten<U> : T;

type Deep = Flatten<number[][][]>;   // number -- recurses through each array layer until it hits a non-array
```

# Variadic Tuple Types and Labeled Tuple Elements

--> Variadic tuple types let a tuple type include a SPREAD of another tuple/array type parameter, letting generic functions describe exactly how their argument tuple relates to their return tuple -- particularly useful for typing utilities like a generic `concat`/`curry`.

```typescript
type Concat<T extends unknown[], U extends unknown[]> = [...T, ...U];

type Combined = Concat<[string, number], [boolean]>;   // [string, number, boolean]

function concat<T extends unknown[], U extends unknown[]>(a: [...T], b: [...U]): [...T, ...U] {
  return [...a, ...b];
}
const result = concat([1, "a"], [true]);   // [number, string, boolean] -- exact tuple shape preserved, not widened to any[]
```

--> Labeled tuple elements attach a descriptive NAME to each position purely for readability/tooling (autocomplete, hover hints) -- they carry no runtime meaning at all, exactly like parameter names in a function type.

```typescript
type RGB = [red: number, green: number, blue: number];   // Same as [number, number, number] at runtime,
                                                            // but editor tooltips now show meaningful names per position

function setColor(...[red, green, blue]: RGB) { /* ... */ }
```

# this Parameter Typing in Functions

--> A function can declare an explicit `this` parameter as its FIRST parameter -- it's erased entirely at runtime (never actually passed as a real argument) but lets TypeScript type-check what `this` must be when the function is called, catching a whole category of "called with the wrong context" bugs.

```typescript
interface Button {
  label: string;
  onClick: (this: Button, event: MouseEvent) => void;
}

const button: Button = {
  label: "Submit",
  onClick(event) {
    console.log(this.label);   // TypeScript knows `this` is a Button here, because of the `this: Button` parameter type
  },
};

function standalone(this: Button, event: MouseEvent) {
  console.log(this.label);
}
standalone.call(button, new MouseEvent("click"));   // OK -- this is bound to a matching Button
// standalone(new MouseEvent("click"));               // Error -- called without the required `this` context (with noImplicitThis)
```

--> This is most useful for typing callback-style APIs (older jQuery-style plugins, DOM event handlers assigned via `element.onclick = function() { ... }` where `this` is the element) where `this` genuinely varies by how the function is invoked, rather than always being `undefined`/the enclosing scope the way an arrow function's `this` would be (see file 03 Classes and Functions in TypeScript for arrow-function `this` binding).

# Deep Dive -- Combining Branded Types With satisfies for Safer Config Objects

--> Branded types and `satisfies` solve different halves of the same overall problem -- `satisfies` ensures an object's shape/values are validated against a type while preserving specific literal inference, and a branded type ensures a value's ORIGIN was validated somewhere specific, rather than an arbitrary same-shaped value slipping in unchecked.

```typescript
type ValidatedEmail = string & { readonly __brand: "ValidatedEmail" };

function validateEmail(input: string): ValidatedEmail {
  if (!input.includes("@")) throw new Error("Invalid email");
  return input as ValidatedEmail;
}

const config = {
  adminEmail: validateEmail("admin@example.com"),
  retries: 3,
} satisfies { adminEmail: ValidatedEmail; retries: number };

// config.adminEmail is guaranteed, at the type level, to have gone through validateEmail() at some point --
// a plain string literal could never be assigned there directly, and satisfies still checked the whole shape
```
