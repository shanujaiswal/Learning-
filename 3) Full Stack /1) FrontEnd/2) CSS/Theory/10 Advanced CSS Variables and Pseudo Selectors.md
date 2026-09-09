# CSS Custom Properties (Variables) Recap

--> Defined as `--name: value;`, typically on `:root` for global access, or on any element to scope it locally.
--> Read with `var(--name)`, with an optional fallback: `var(--name, fallback-value)`.
--> Can be overridden per-component/media-query for theming (e.g. dark mode).

```css
:root {
  --gap: 16px;
}
@media (prefers-color-scheme: dark) {
  :root { --bg: #111; --text: #eee; }
}
```

# nth-child / nth-of-type Patterns

--> `:nth-child(2n)` — every even child (also `:nth-child(even)`).
--> `:nth-child(2n+1)` — every odd child (also `:nth-child(odd)`).
--> `:nth-child(3)` — exactly the 3rd child.
--> `:nth-of-type(n)` — like `nth-child` but only counts siblings of the same tag type.
--> Common use: zebra-striping table rows — `tr:nth-child(even) { background: #f2f2f2; }`

# Combining Pseudo-classes

--> `a:hover:not(.disabled)` — hover style applied only when the element does not have the `disabled` class.
--> `input:focus:invalid` — style applied when a focused input fails its validation constraints.

# :has(), :is(), :where() — Relational and Grouping Selectors

--> `:has(selector)` — the "parent selector"; matches an element only if it contains something matching the given selector. Enables selecting a parent/ancestor based on its descendants, which plain CSS couldn't do before.
```css
/* Style a card only if it contains an image */
.card:has(img) {
  border: 1px solid #ccc;
}
/* Style a form-group when its input is invalid */
.form-group:has(input:invalid) {
  outline: 1px solid red;
}
```
--> `:is(selector-list)` — matches any selector in the list, shortens repetitive grouped selectors, e.g. `:is(h1, h2, h3) { margin-top: 0; }`.
--> `:where(selector-list)` — same as `:is()` but always contributes zero specificity, handy for low-priority base/reset styles that are easy to override later.

# CSS Functions

--> `calc()` — perform math combining different units, e.g. `width: calc(100% - 40px);`
--> `min()` / `max()` — pick the smallest/largest of a list of values.
--> `clamp(min, preferred, max)` — a fluid value bounded between a min and max.
--> `var()` — reads a custom property.

# Layering and Cascade Layers

--> `@layer` — groups CSS rules into named layers, controlling cascade order explicitly regardless of specificity/source order.

# Container Queries (modern CSS)

--> `@container` — applies styles based on the size of a containing element (not just the viewport), enabling truly component-level responsiveness.
--> Requires setting `container-type: inline-size;` on the parent element first.

# CSS Resets / Normalization

--> A reset removes default browser styling (margins, list bullets, etc.) for a consistent starting point across browsers.
--> Common minimal reset:
```css
*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}
```
--> A genuinely modern reset goes further than just margin/padding — it also normalizes `line-height` (e.g. `body { line-height: 1.5; }`) and removes default `list-style` on `ul`/`ol` used for navigation, since these also vary/cause inconsistency across browsers and default user-agent stylesheets.

# Preprocessors (brief mention)

--> Sass/SCSS/LESS — add variables, nesting, mixins, and functions on top of CSS, compiled down to plain CSS before shipping.
--> Modern native CSS (custom properties, nesting, `@layer`) has closed much of the historical gap with preprocessors.

# Native CSS Nesting

--> Native CSS Nesting (no preprocessor required) lets you write child/related selectors directly inside a parent rule, similar to Sass, using standard `.css` files.
--> A nested selector that starts with a bare element/pseudo-class must be prefixed with `&` (ampersand, referring to the parent selector) in some cases — but nesting a class/id selector directly works without it.

```css
.card {
  padding: 1rem;
  border: 1px solid #ddd;

  &:hover {
    border-color: dodgerblue;
  }

  .card__title {
    font-size: 1.25rem;
    font-weight: bold;
  }

  @media (min-width: 768px) {
    padding: 2rem;
  }
}
```

--> Equivalent compiled CSS (what the above expands to conceptually):

```css
.card { padding: 1rem; border: 1px solid #ddd; }
.card:hover { border-color: dodgerblue; }
.card .card__title { font-size: 1.25rem; font-weight: bold; }
@media (min-width: 768px) { .card { padding: 2rem; } }
```

--> Supported in all modern evergreen browsers (Chrome/Edge/Firefox/Safari) as of 2023-2024 — check caniuse.com if targeting older browsers, and fall back to a preprocessor if needed.

# CSS Logical Properties

--> Logical properties describe direction/position in terms of writing-mode flow (`inline` = the direction text flows, e.g. left-to-right; `block` = the direction blocks stack, e.g. top-to-bottom) instead of fixed physical directions (`left`/`right`/`top`/`bottom`).
--> This means a single set of styles automatically adapts correctly for right-to-left languages (Arabic, Hebrew) or vertical writing modes, without needing separate RTL overrides.

--> Common mappings (in the default left-to-right, top-to-bottom mode):
    - `margin-inline-start` / `margin-inline-end` --> `margin-left` / `margin-right`
    - `margin-block-start` / `margin-block-end` --> `margin-top` / `margin-bottom`
    - `padding-inline` (shorthand for start+end) --> `padding-left` + `padding-right`
    - `padding-block` (shorthand for start+end) --> `padding-top` + `padding-bottom`
    - `inset-inline-start` / `inset-inline-end` --> `left` / `right` (for positioned elements)
    - `border-inline-start` --> `border-left`
    - `inline-size` / `block-size` --> `width` / `height`

```css
/* Physical (doesn't flip for RTL languages) */
.box {
  margin-left: 1rem;
  padding-top: 0.5rem;
  width: 200px;
}

/* Logical (automatically flips direction for RTL) */
.box {
  margin-inline-start: 1rem;
  padding-block-start: 0.5rem;
  inline-size: 200px;
}
```

--> Recommended for any component that might need to support multiple languages/writing directions — increasingly the default recommendation in modern CSS style guides.
