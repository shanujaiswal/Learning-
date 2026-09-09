# Beyond the strict Bundle -- Individually Toggleable Flags

--> `strict: true` (file 07 tsconfig Deep Dive) bundles many checks together, but several genuinely useful checks are NOT part of `strict` at all and must be opted into individually -- each catches a specific, common class of bug that `strict` alone still allows through.

# noUncheckedIndexedAccess

--> By default, accessing an object/array via an index signature or numeric index returns the VALUE type directly, even though the key/index might not actually exist -- TypeScript otherwise just trusts you.

```typescript
interface Scores {
  [name: string]: number;
}

function getScore(scores: Scores, name: string) {
  const score = scores[name];   // Without the flag: typed as `number` -- but "name" might not actually be a key at all
  return score.toFixed(2);        // Runtime crash if scores[name] is actually undefined -- TypeScript saw no problem
}
```

--> With `noUncheckedIndexedAccess: true`, every index access is typed as `T | undefined` instead, forcing an explicit check before use -- the same reasoning as `strictNullChecks`, applied specifically to indexed access rather than every value in general.

```typescript
function getScore(scores: Scores, name: string) {
  const score = scores[name];   // Now typed as `number | undefined`
  if (score === undefined) return 0;
  return score.toFixed(2);        // OK -- narrowed to number
}
```

--> This also affects plain array indexing (`arr[i]`), which is easy to forget -- looping with a manually-tracked index and no bounds guarantee is exactly the case this flag is designed to catch.

# exactOptionalPropertyTypes

--> Normally, an optional property (`name?: string`) is treated as `string | undefined` for assignment purposes -- meaning explicitly assigning `undefined` to it looks identical to simply omitting the property, even though some code cares about that difference (e.g. distinguishing "field not provided" from "field explicitly cleared").

```typescript
interface Settings {
  theme?: "light" | "dark";
}

const s: Settings = { theme: undefined };   // Allowed by default -- treated the same as omitting theme entirely
```

--> With `exactOptionalPropertyTypes: true`, an optional property means "present with that type, OR entirely absent" -- explicitly assigning `undefined` to it becomes a type error unless the property's type explicitly includes `undefined` itself.

```typescript
interface Settings {
  theme?: "light" | "dark";
}
const s: Settings = { theme: undefined };   // Error, with the flag on -- "undefined" is not assignable, theme must be omitted or a real value

interface SettingsExplicit {
  theme?: "light" | "dark" | undefined;   // Explicitly allows undefined as a distinct assigned value
}
const s2: SettingsExplicit = { theme: undefined };   // OK now -- the type says this is intentional
```

--> Matters most for APIs (often ones interacting with JSON payloads or React props) where "key omitted" and "key present but `undefined`" are meant to trigger genuinely different behavior downstream, and silently conflating the two would be a real bug rather than a stylistic nitpick.

# noImplicitOverride

--> Without this flag, overriding a base class method in a subclass requires no special syntax at all -- a typo in the method name (meant to override, but actually just defines an unrelated new method) fails completely silently, since TypeScript has no way to know the intent was an override.

```typescript
class Animal {
  makeSound() { console.log("..."); }
}

class Dog extends Animal {
  makeSond() { console.log("Woof!"); }   // Typo -- meant to override makeSound, but defines a NEW unrelated method instead
}

new Dog().makeSound();   // Logs "..." -- the base class version, since the override never actually happened. No error anywhere.
```

--> With `noImplicitOverride: true`, TypeScript requires the explicit `override` keyword on any method that overrides a base class method -- and, crucially, ERRORS if `override` is used but no matching base method actually exists, catching exactly the typo above.

```typescript
class Dog extends Animal {
  override makeSond() { console.log("Woof!"); }   // Error -- "makeSond" does not override any base class method
}

class Cat extends Animal {
  override makeSound() { console.log("Meow!"); }   // OK -- genuinely overrides Animal.makeSound
}
```

--> Also flags the opposite mistake -- forgetting `override` on a method that actually DOES shadow a base class method, forcing every override in the codebase to be an explicit, intentional statement rather than something that happens to line up by name.

# Assertion Functions vs Type Predicates

--> A type predicate (`value is T`, covered in file 08 Type Narrowing and Guards) narrows a type only within the branch of an `if` that CALLS the guard function and checks its boolean return value -- the function returns `true`/`false`, and narrowing happens on the caller's conditional.

```typescript
function isString(value: unknown): value is string {
  return typeof value === "string";
}

function example(input: unknown) {
  if (isString(input)) {
    input.toUpperCase();   // Narrowed only inside this if-block
  }
}
```

--> An assertion function (`asserts value is T`, or the more general `asserts condition`) instead THROWS if the condition is false, and narrows the type for all code AFTER the call, with no `if` block needed at all -- a completely different control-flow shape, closer to a runtime `assert()`.

```typescript
function assertIsString(value: unknown): asserts value is string {
  if (typeof value !== "string") {
    throw new TypeError("Expected a string");
  }
}

function example(input: unknown) {
  assertIsString(input);
  input.toUpperCase();   // Narrowed here, with NO enclosing if-block -- the function call itself performs the narrowing
}
```

--> `asserts condition` (without `is Type`) is the more general form, useful for asserting an invariant that isn't specifically about narrowing a type but should still stop execution if violated (e.g. `assert(value !== null)`), similarly narrowing away `null`/`undefined` for everything that follows the call.

```typescript
function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function process(value: string | null) {
  assert(value !== null, "value must not be null");
  console.log(value.toUpperCase());   // Narrowed to string here, past the assert() call
}
```

--> Choose a type predicate when the caller needs to branch on the result (do one thing if true, another if false); choose an assertion function when an invalid value should genuinely halt execution rather than be handled as a normal alternate path -- they solve the same underlying "prove this to the compiler" problem with different control-flow shapes.

# const enum Pitfalls

--> A `const enum` is meant purely as a compile-time optimization over a regular `enum` -- rather than generating a lookup object at runtime, every reference to a `const enum` member is INLINED directly as its literal value wherever it's used, producing zero runtime footprint.

```typescript
const enum Direction { Up, Down, Left, Right }
const d = Direction.Up;
// Compiles down to: const d = 0;  -- no Direction object exists in the output JS at all
```

--> The inlining is exactly what causes the pitfalls. It requires the enum's DECLARATION to be visible to the compiler at every usage site -- this breaks down across certain module boundaries (notably with `isolatedModules: true`, required by single-file transpilers like Babel/esbuild/swc that process files independently without full cross-file type information) since a tool transpiling one file at a time has no way to inline a value declared in another file it isn't type-checking.
--> It also means a `const enum` published as part of a LIBRARY's public API is fragile -- a consumer's bundler may have compiled the enum's numeric value directly into their code at build time, so upgrading the library and changing enum member ORDER (which changes the auto-assigned numeric values) can silently produce wrong behavior in already-compiled consumer code without any error at all.

```typescript
// If a library reorders these between versions...
const enum Status { Pending, Active, Closed }   // Was Pending=0, Active=1, Closed=2
// ...to this:
const enum Status { Active, Pending, Closed }   // Now Active=0, Pending=1 -- any code that inlined the OLD values is now wrong
```

# Why a Union of String Literals Is Often Preferred

--> A plain union of string literal types avoids every pitfall above -- no separate runtime object needed at all (a literal union is a pure compile-time construct, same as any other type), no `isolatedModules` incompatibility, and no "reordering breaks consumers" risk since string literals don't have an implicit numeric ordering to reorder.

```typescript
type Status = "pending" | "active" | "closed";   // No runtime object, no inlining fragility, no isolatedModules conflict

function handle(status: Status) {
  if (status === "pending") { /* ... */ }
}
```

--> The literal-union values are also more readable in logs/debugging/network payloads (`"pending"` vs a bare `0`), and serialize to JSON exactly as written, whereas a numeric enum serializes as a meaningless number unless you specifically use a STRING enum (`enum Status { Pending = "pending" }`) to get similar readability -- at which point a literal union does the same job with less machinery and no runtime cost. Regular (non-const) `enum` is still occasionally preferred when you specifically want a genuine runtime object to iterate over (`Object.values(Status)`) or reverse-map a value to its name, which a literal union type cannot provide since it doesn't exist at runtime at all.

# Type-Level Testing -- tsd (Conceptually)

--> Ordinary unit tests (file 28 Testing JavaScript in the JavaScript track) verify runtime BEHAVIOR -- they run compiled code and assert on its output. They cannot verify that a TYPE itself is correct, since by the time a test runs, TypeScript's type checking has already finished and been erased.
--> `tsd` (and similar tools like `expect-type`, or Vitest's built-in `expectTypeOf`) let you write assertions that run during TYPE CHECKING itself, failing `tsc` (or the test tool's type-check step) if a type doesn't match what's expected -- a test for your TYPES, not your runtime values.

```typescript
// index.test-d.ts -- a tsd test file (convention: .test-d.ts)
import { expectType, expectError } from "tsd";
import { merge } from "./index";

expectType<{ name: string; age: number }>(merge({ name: "Alice" }, { age: 30 }));
expectError(merge({ name: "Alice" }, "not an object"));   // Asserts this call should fail to type-check at all
```

--> Most valuable for library authors, where the actual DELIVERABLE consumers depend on is largely the TYPES (a generic utility function, a complex conditional-type helper like the ones in file 09 Advanced Type-Level Features) -- a runtime unit test could easily pass while the exported type signature has quietly regressed (lost precision, stopped narrowing correctly, started allowing an invalid input), which only a type-level test would actually catch.

# ts-node vs tsc vs Babel -- Transpilation Trade-offs

--> `tsc` (the TypeScript compiler itself) is the reference implementation -- it both TYPE-CHECKS and emits JavaScript. Running `tsc` on a project is the only path that's guaranteed to catch every type error the language actually defines, since every other tool below either skips checking entirely or reimplements a subset of it.
--> `ts-node` runs a `.ts` file directly, on the fly, without a separate manual compile step first -- convenient for scripts/dev servers, but it's still fundamentally invoking the full TypeScript compiler API under the hood per file/run, which is measurably slower per-execution than running already-compiled plain JS.
--> Babel's TypeScript preset (and similarly, esbuild/swc) STRIPS type annotations to produce JavaScript, but does NOT type-check at all -- it's a fast, purely syntactic transform that assumes the code is already valid, deliberately trading type safety for speed and single-file-at-a-time processing (relevant to the `isolatedModules` constraint mentioned above with `const enum`).

```typescript
function add(a: number, b: number): number {
  return a + b;
}
// Babel/esbuild/swc: strips `: number` annotations and emits the function, WITHOUT checking whether
// callers actually pass numbers -- add("2", "3") would transpile and run without any warning from these tools
// tsc: would actually ERROR on such a call elsewhere in the project, since it performs real type checking
```

--> The common, recommended real-world setup runs BOTH kinds of tool for different jobs -- a fast transpiler (Babel/esbuild/swc, or Vite's dev server built on esbuild) for the actual build/dev-server speed, paired with `tsc --noEmit` run separately (in CI, a pre-commit hook, or the editor's language service) purely for type-checking, so you get fast builds without silently losing type safety altogether.
--> `ts-node` remains genuinely useful for ad hoc scripts, seed files, and small CLI tools where spinning up a separate build step is more overhead than the script is worth -- but for anything performance-sensitive (a dev server rebuilding on every file save) the split "fast transpile + separate type-check" approach above is standard practice specifically because `ts-node`/`tsc`'s full type-checking on every run doesn't scale to that use case.

# Deep Dive -- Combining These Flags for Maximum Practical Rigor

--> `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, and `noImplicitOverride` all share the same underlying philosophy as `strict` itself -- each closes a SPECIFIC gap where TypeScript would otherwise silently trust code that later turns out to be wrong at runtime, in situations `strict` alone still lets through.

```json
{
  "compilerOptions": {
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true,
    "noImplicitOverride": true
  }
}
```

--> Enabling all of them on a brand-new project costs little (the same "nearly always the right call" reasoning `strict` itself gets in file 07 tsconfig Deep Dive) -- but retrofitting them onto a large, already-loosely-typed codebase can surface a genuinely large number of pre-existing issues at once, which is exactly why teams often enable one flag at a time, fix what it surfaces, then move to the next, rather than flipping every strictness flag simultaneously.
