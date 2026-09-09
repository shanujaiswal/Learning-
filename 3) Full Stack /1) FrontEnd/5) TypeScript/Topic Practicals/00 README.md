# TypeScript Practical

Hands-on `.ts` files that pair with the `Theory` chapters in the sibling
`TypeScript/Theory` folder. Every file here is real, compilable TypeScript
that has been verified with `tsc --strict` (no errors, exit code 0).

## Files

| File | Covers (Theory chapter) | What it demonstrates |
|---|---|---|
| `01-basic-types-and-narrowing.ts` | 01 Fundamentals and Basic Types, 08 Type Narrowing and Guards | Primitives, tuples, literal types, union types, a discriminated union (`Shape`) handled exhaustively with a `switch` and a `never`-typed `assertNever` check, plus a custom `pet is Dog` type predicate and `in`-operator narrowing. |
| `02-interfaces-and-utility-types.ts` | 02 Interfaces Types and Advanced Types, 06 Utility Types | An `Entity -> Person -> Employee` interface hierarchy, then `Partial`, `Pick`, `Omit`, `Record`, and `Readonly` applied to it, each annotated with what changes about the resulting type. |
| `03-generics-practical.ts` | 05 Generics Deep Dive | A generic in-memory `Repository<T extends { id: string }>` CRUD store, a generic `pluck<T, K extends keyof T>` helper with a precisely inferred return type, and a constrained `merge<A extends object, B extends object>` example. |
| `04-classes-and-decorators.ts` | 03 Classes and Functions in TypeScript | An `abstract class Employee` with `private`/`protected`/`readonly` members and an abstract method, two concrete subclasses using constructor parameter property shorthand, and a second-level subclass (`Manager`) that overrides a method with `override`. |
| `05-type-safe-api-client.ts` | 05 Generics Deep Dive, 06 Utility Types, 08 Type Narrowing and Guards (and conceptually ties to the vault's Node/Express and Full Stack API notes) | A generic `apiGet<T>` / `apiPost<TBody, TResponse>` typed `fetch` wrapper, `User`/`Post`/`ApiEnvelope<T>` response-shape interfaces, a runtime `isApiErrorBody` type guard, a typed `ApiError` class, and a usage example showing how typed responses catch mistakes (e.g. a typo'd property) at compile time instead of at runtime. |

## How to run / type-check

Each file is standalone and can be checked individually. From this folder:

```bash
# Type-check only (recommended -- verifies correctness without running anything)
npx tsc --noEmit "01-basic-types-and-narrowing.ts"
npx tsc --noEmit "02-interfaces-and-utility-types.ts"
npx tsc --noEmit "03-generics-practical.ts"
npx tsc --noEmit "04-classes-and-decorators.ts"
npx tsc --noEmit "05-type-safe-api-client.ts"

# Or check them all at once
npx tsc --noEmit --strict *.ts

# To actually execute a file's console.log output (needs ts-node):
npx ts-node "01-basic-types-and-narrowing.ts"
```

Notes:

- If you don't have `typescript` installed anywhere on the machine yet, run
  `npm install typescript ts-node --no-save` in this folder first so `npx`
  can resolve the packages.
- `05-type-safe-api-client.ts` type-checks standalone but its `main()` isn't
  called automatically -- it talks to a real `/api/...` backend, so only run
  it (via `ts-node`) against an actual server if you want to see it execute.
- These files intentionally have no `tsconfig.json` of their own; they were
  verified against `--strict` directly on the command line so they will also
  pass under any project's `tsconfig.json` that has `"strict": true`.
- Nothing under any `Theory` folder was modified to produce these files.
