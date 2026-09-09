# Scope of This File

--> The Vue.js Fundamentals and Angular and Svelte Fundamentals files cover the core mental model of each framework -- this file goes one level deeper into each one's more advanced, current-day tooling: Vue's built-in directives and its Nuxt meta-framework, Angular's modern Standalone Components/Signals APIs and NgRx, and Svelte 5's rewritten reactivity model (runes) plus SvelteKit.

# Vue Directives Beyond the Basics

--> The Vue.js Fundamentals file introduces `@click`/`v-on` and Vue's overall templating approach -- Vue ships several other built-in directives that handle the same conditional-rendering/list-rendering/two-way-binding problems React solves with plain JavaScript (`&&`, `.map()`, controlled inputs, all covered in the React folder).

```vue
<template>
  <!-- v-if / v-else-if / v-else -- conditional rendering, directly parallel to a React ternary or && -->
  <p v-if="status === 'loading'">Loading...</p>
  <p v-else-if="status === 'error'">Something went wrong</p>
  <p v-else>{{ data }}</p>

  <!-- v-show -- toggles CSS display:none instead of adding/removing the element from the DOM -->
  <p v-show="isVisible">Toggled via CSS, not mounted/unmounted</p>

  <!-- v-for -- list rendering, parallel to React's .map(), :key plays the same role as React's key prop -->
  <ul>
    <li v-for="item in items" :key="item.id">{{ item.name }}</li>
  </ul>

  <!-- v-bind (shorthand ":") -- binds an attribute to a JS expression, parallel to JSX's {expression} in an attribute -->
  <img :src="imageUrl" :alt="imageAlt" />

  <!-- v-model -- two-way binding on form inputs, condensing what React needs value+onChange for into one directive -->
  <input v-model="username" />
</template>
```

--> **`v-if` vs `v-show`** -- the one Vue-specific decision with no direct React parallel: `v-if` actually adds/removes the element from the DOM (higher toggle cost, but zero cost while hidden), while `v-show` always keeps the element in the DOM and just flips its CSS `display` (cheaper to toggle frequently, but it still exists in the DOM/layout tree while hidden). Rule of thumb -- `v-show` for something toggled often (a tab panel), `v-if` for something rarely shown or expensive to keep mounted.
--> **`v-model` under the hood** -- syntactic sugar that expands to `:value="username"` plus `@input="username = $event.target.value"` -- it is doing exactly what a React controlled input (covered in the Forms file) does explicitly, just condensed into one directive since Vue's reactivity system (covered in the Vue.js Fundamentals file) doesn't need an explicit setter call the way `useState` does.
--> `v-model` also works on custom components (a component can opt into being usable with `v-model` by accepting a `modelValue` prop and emitting an `update:modelValue` event), letting custom form controls (a `<StarRating v-model="rating" />`) support the same two-way-binding syntax as native inputs.

# Nuxt -- Vue's Meta-Framework

--> Nuxt is to Vue what Next.js (covered in the Next.js and SSR file) is to React -- a meta-framework adding file-based routing, SSR/SSG rendering strategies, and a cohesive project structure on top of plain Vue, rather than leaving those decisions to the developer.

```vue
<!-- pages/blog/[slug].vue -- file-based routing, directly parallel to Next.js App Router's app/blog/[slug]/page.js -->
<script setup>
const { slug } = useRoute().params;         // Nuxt's auto-imported route helper, parallel to Next's params prop
const { data: post } = await useFetch(`/api/posts/${slug}`);   // Runs server-side during SSR, then client-side on navigation
</script>

<template>
  <article>
    <h1>{{ post.title }}</h1>
    <p>{{ post.body }}</p>
  </article>
</template>
```

--> `useFetch` is Nuxt's built-in data-fetching composable -- it automatically handles SSR (fetching on the server for the initial request, avoiding a second client-side fetch after hydration) and caching/de-duplication, conceptually similar in spirit to what `fetch()` inside a Next.js Server Component (covered in the Next.js and SSR file) or React Query (covered in the Data Fetching file) each provide, just as Vue's own built-in answer to the same problem.
--> Nuxt supports the same rendering-strategy spectrum as Next.js -- SSR by default, `nitro`-powered API routes (`server/api/*.js`, parallel to Next's `app/api/*/route.js`), and static generation (`nuxt generate`) for SSG -- the underlying rendering-strategy CONCEPTS (covered in the Next.js and SSR file's Rendering Strategies Overview) are framework-agnostic; Nuxt is simply Vue's implementation of the same ideas.
--> Auto-imports are a distinctive Nuxt convenience -- composables, components, and Vue APIs (`ref`, `computed`) are usable in any `.vue` file WITHOUT explicit `import` statements, since Nuxt's build step scans the project structure and injects the imports automatically -- a deliberate trade of slightly more "magic" for less boilerplate.

# Angular Standalone Components

--> Older Angular required every component to be declared inside an `NgModule` (a grouping/configuration unit) before it could be used anywhere -- **Standalone Components** (the default since Angular 15+, and now Angular's officially recommended style) let a component declare its own dependencies directly, removing the NgModule requirement entirely for most apps.

```typescript
import { Component } from "@angular/core";
import { CommonModule } from "@angular/common";

@Component({
  selector: "app-user-card",
  standalone: true,                          // No NgModule needed to use this component
  imports: [CommonModule],                   // Dependencies declared directly on the component itself
  template: `<div>{{ user.name }}</div>`
})
export class UserCardComponent {
  user = { name: "Alice" };
}
```

--> This directly parallels the broader front-end trend AWAY from centralized, ceremony-heavy registration steps and TOWARD components that own/declare their own dependencies -- conceptually similar to how modern React function components just `import` whatever they need directly, without a separate registration step, rather than the older HOC-heavy patterns covered in the Advanced React Patterns file.
--> Standalone components can be bootstrapped directly (`bootstrapApplication(RootComponent)`) without a root `AppModule` at all, meaningfully simplifying a new Angular app's starting boilerplate compared to older Angular tutorials still showing full NgModule setup.

# Angular Signals

--> Angular introduced its own **Signals** API (stable from Angular 17+) as a new, more fine-grained reactivity primitive, existing alongside (not fully replacing, at least initially) Angular's RxJS/Observable-based approach covered in the Angular and Svelte Fundamentals file.

```typescript
import { Component, signal, computed } from "@angular/core";

@Component({
  selector: "app-counter",
  standalone: true,
  template: `
    <button (click)="increment()">Count: {{ count() }} (doubled: {{ doubled() }})</button>
  `
})
export class CounterComponent {
  count = signal(0);                          // A Signal -- read by CALLING it, count()
  doubled = computed(() => this.count() * 2); // Derived signal, recalculates only when count() changes

  increment() {
    this.count.update((c) => c + 1);          // update() takes the current value and returns the new one
  }
}
```

--> Notice the strong parallel to SolidJS's signal model (covered in the React Theory folder's file 21, Concurrent React) -- a Signal is read by CALLING it (`count()`), not by accessing a plain property, which is what lets Angular's change-detection system track exactly which template expressions depend on which signal, without needing Zone.js's older, broader "patch every async API and re-check everything" strategy.
--> Angular's motivation for adding Signals mirrors Vue's Proxy-based reactivity (covered in the Vue.js Fundamentals file) and Svelte's runes (below) -- all three frameworks have converged, independently, on fine-grained, dependency-tracked reactive primitives as a more predictable and often more performant alternative to broader "re-check everything" or "re-run the whole component" strategies.
--> `signal()` for standalone state, `computed()` for derived values (directly comparable to Vue's `computed()` or React's `useMemo`), and `effect()` for running side effects when a signal changes (comparable to `useEffect`, though Angular's effects run outside the template rendering cycle) form the three core primitives.

# NgRx -- Redux's Pattern, Applied to Angular

--> NgRx is Angular's most widely used state management library, deliberately modeled on Redux's core principles (covered in the Redux and Zustand file) -- a single store, plain-object actions, pure reducer functions -- but built on top of RxJS Observables rather than plain callback-based subscriptions.

```typescript
// actions
import { createAction, props } from "@ngrx/store";
export const addItem = createAction("[Cart] Add Item", props<{ item: CartItem }>());

// reducer
import { createReducer, on } from "@ngrx/store";
export const cartReducer = createReducer(
  [] as CartItem[],
  on(addItem, (state, { item }) => [...state, item])   // Same immutable-update principle as a Redux reducer
);

// component
import { Store } from "@ngrx/store";

@Component({ selector: "app-cart", standalone: true, template: `...` })
export class CartComponent {
  items$ = this.store.select((state) => state.cart);   // An Observable of the cart slice, not a plain value

  constructor(private store: Store) {}
  addToCart(item: CartItem) {
    this.store.dispatch(addItem({ item }));
  }
}
```

--> The `$` suffix convention (`items$`) is an RxJS idiom flagging "this is an Observable, not a plain value" -- consumed in the template with Angular's `async` pipe (`{{ items$ | async }}`), which subscribes/unsubscribes automatically, avoiding the manual `.subscribe()`/memory-leak management RxJS otherwise requires.
--> NgRx Effects handle async side effects (API calls triggered by a dispatched action) similarly in spirit to Redux Toolkit's `createAsyncThunk` (covered in the Redux and Zustand file), but expressed as RxJS streams reacting to an Observable-of-actions rather than an async function.
--> Because Angular now has Signals as a lighter-weight built-in alternative, NgRx's own `@ngrx/signals` package has emerged as a signals-based alternative to the full Observable-based NgRx store -- mirroring the exact same "full Redux-style library vs a simpler primitive-based approach" choice React developers face between Redux and Zustand/Jotai (covered in the Redux and Zustand file and the React Theory folder's file 21).

# Svelte 5 Runes -- A Rewritten Reactivity Model

--> The Angular and Svelte Fundamentals file's Svelte example (`let count = 0` implicitly reactive, `$: doubled = count * 2`) describes Svelte 4 and earlier -- Svelte 5 introduced **runes**, a deliberate rewrite of Svelte's reactivity syntax to be more explicit and to work correctly outside `.svelte` files (in plain `.js`/`.ts` files too, which the old implicit-reactivity compiler magic could not support).

```svelte
<script>
  let count = $state(0);                      // Explicit reactive state -- replaces plain `let count = 0`
  let doubled = $derived(count * 2);           // Replaces the old `$: doubled = count * 2` label syntax

  $effect(() => {                              // Replaces `$:` used for side effects (e.g. console.log)
    console.log(`count is now ${count}`);
  });

  function increment() {
    count += 1;                                // Still looks like a plain assignment -- the compiler makes it reactive
  }
</script>

<button onclick={increment}>
  Count: {count} (doubled: {doubled})
</button>
```

--> `$state`, `$derived`, and `$effect` are RUNES -- special compiler-recognized symbols (not real JavaScript functions, similar in spirit to how Svelte's old `$:` was a repurposed JS label, covered in the Angular and Svelte Fundamentals file) that make what used to be IMPLICIT (Svelte's compiler guessing that `count` is reactive because it's declared with `let` at the top level) fully EXPLICIT.
--> Why this changed -- Svelte 4's implicit reactivity only worked for top-level `let` declarations inside a `.svelte` file's `<script>` block, so reactive logic couldn't be extracted into a plain, reusable `.js` file the way a React custom Hook or a Vue composable (both covered in their respective files) can. Runes work the same way inside plain `.js`/`.ts` files, finally enabling genuinely reusable, file-agnostic reactive logic.
--> `onclick={increment}` (no colon) replacing `on:click={increment}` is another Svelte 5 change -- event handlers are now plain HTML-standard attribute-like props rather than a Svelte-specific directive syntax, simplifying the mental model (and enabling passing event handlers as regular props to components, the way React does).
--> `$props()` replaces the old `export let propName` syntax for declaring what a component accepts:
```svelte
<script>
  let { label, count = 0 } = $props();   // Destructured props with a default, replaces `export let label; export let count = 0;`
</script>
```

# SvelteKit -- Svelte's Meta-Framework

--> SvelteKit plays the exact same role for Svelte that Next.js plays for React and Nuxt plays for Vue -- file-based routing, SSR/SSG, and a full application structure built around Svelte components.

```javascript
// src/routes/blog/[slug]/+page.server.js -- runs ONLY on the server, parallel to getServerSideProps/a Remix loader
export async function load({ params }) {
  const post = await db.post.findUnique({ where: { slug: params.slug } });
  return { post };                          // Available in the corresponding +page.svelte as a `data` prop
}
```

```svelte
<!-- src/routes/blog/[slug]/+page.svelte -->
<script>
  let { data } = $props();     // `data.post` -- exactly what the load function above returned
</script>

<article>
  <h1>{data.post.title}</h1>
</article>
```

--> SvelteKit's `+page.server.js` / `load` function convention is directly comparable to Remix's `loader` (covered in the React Theory folder's file 21) and Next.js's `getServerSideProps`/Server Component data fetching (covered in the Next.js and SSR file) -- all three are the same underlying idea (fetch data server-side, before the page renders) expressed through each framework's own file-based convention.
--> `+page.js` (without `.server`) runs on BOTH server and client (useful for data-fetching logic that's safe to expose, e.g. hitting a public API), while `+page.server.js` is guaranteed server-only -- directly parallel to the Server Component vs Client Component split covered in the React Server Components and Suspense file, just organized by file suffix rather than a `"use client"` directive.
--> Because SvelteKit inherits Svelte's compile-away-the-runtime philosophy (covered in the Angular and Svelte Fundamentals file), a SvelteKit app's shipped JS bundle tends to stay smaller than an equivalent Next.js/Nuxt app's, for the same underlying reason Svelte's bundle sizes are smaller than React/Vue's in general.

# The Recurring Pattern Across All Three

--> Vue's Composition API/reactivity, Angular's Signals, and Svelte's runes have all independently converged on the SAME underlying idea in the last few years -- fine-grained, explicitly-declared reactive primitives (`ref`/`computed`, `signal`/`computed`, `$state`/`$derived`) that track dependencies precisely, rather than either React's "re-run the whole function" model or the frameworks' own older, coarser reactivity approaches.
--> Similarly, Nuxt, Next.js, Remix, and SvelteKit have all converged on the same meta-framework shape -- file-based routing, a server-side data-loading convention tied to that file structure, and a clear seam between server-only and client-shipped code -- confirming the same "different syntax, converging problems" lesson the Angular and Svelte Fundamentals file closes on, now visible one layer up at the meta-framework level too.
