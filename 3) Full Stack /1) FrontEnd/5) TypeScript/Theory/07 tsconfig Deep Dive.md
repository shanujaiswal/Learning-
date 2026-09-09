# What tsconfig.json Controls

--> `tsconfig.json` configures how the TypeScript compiler (`tsc`) checks and compiles a project -- which files to include, how strict type-checking should be, and what JavaScript output to target.

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "strict": true,
    "outDir": "./dist",
    "rootDir": "./src",
    "esModuleInterop": true,
    "skipLibCheck": true,
    "moduleResolution": "node"
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist"]
}
```

# Key Compiler Options

--> `target` -- which JavaScript version the compiled output uses (`ES5`, `ES2020`, etc.) -- affects whether modern syntax (optional chaining, etc.) is compiled down to older equivalents or left as-is.
--> `module` -- the module system used in the output (`CommonJS` for older Node.js, `ESNext`/`ES2020` for modern bundlers and native ESM).
--> `outDir` / `rootDir` -- where compiled JS output goes vs where the TypeScript source lives.
--> `strict` -- a single flag that enables an entire bundle of stricter checks at once (see below) -- the single most impactful setting in the whole file.

# The strict Flag -- What It Actually Enables

--> `strict: true` turns on several individually-toggleable checks together:
--> `noImplicitAny` -- errors on any value TypeScript can't infer a type for and would otherwise silently treat as `any`.
--> `strictNullChecks` -- `null`/`undefined` are NOT automatically assignable to every other type -- you must explicitly handle the possibility of them.
--> `strictFunctionTypes`, `strictBindCallApply`, `alwaysStrict`, and a few others -- each tightens a specific category of type inference.
--> Turning `strict` on for a brand-new project is nearly always the right call; retrofitting it onto a large existing loosely-typed codebase can surface a large number of pre-existing type errors all at once, which is why some teams enable individual strict sub-flags incrementally instead.

# Path Aliases

--> `paths` lets you define import shortcuts, avoiding long relative import chains (`../../../../components/Button`).

```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@components/*": ["src/components/*"],
      "@utils/*": ["src/utils/*"]
    }
  }
}
```

```typescript
import Button from "@components/Button";   // Instead of "../../../components/Button"
```

--> Note -- `tsconfig` path aliases only affect TYPE CHECKING; the actual bundler/runtime (Webpack, Vite, Node) needs its OWN matching alias configuration for this to work at runtime too, not just for `tsc`.

# skipLibCheck and esModuleInterop

--> `skipLibCheck` -- skips type-checking of `.d.ts` declaration files (mostly from `node_modules`) -- speeds up compilation and avoids being blocked by type errors in third-party libraries you don't control.
--> `esModuleInterop` -- smooths over interoperability between CommonJS and ES Module import styles, avoiding awkward `import * as React from "react"` in favor of the more natural `import React from "react"`.

# Project References -- Multi-Package Setups

--> For monorepos/multi-package projects, `references` lets one `tsconfig.json` depend on another, so TypeScript can incrementally build only what actually changed rather than re-checking the entire combined codebase every time.
