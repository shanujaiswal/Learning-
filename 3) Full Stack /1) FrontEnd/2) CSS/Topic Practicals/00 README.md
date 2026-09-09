# CSS Practical Examples — Index

Each file below is a **single, self-contained `.html`** file with an embedded `<style>` block.
Just double-click / open any file in a browser — no build step, no server, no dependencies.
Inline comments inside each `<style>` block call out exactly which CSS concept each rule demonstrates.

These map to the chapters in `Theory/` as follows:

| File | Theory Chapter(s) Covered | What It Demonstrates |
|---|---|---|
| `01-flexbox-patterns.html` | 05 Flexbox | Centering with flex, a navbar (logo left / links right via `space-between`), equal-width columns (`flex: 1`), and a responsive wrapping card row (`flex: 1 1 250px`). |
| `02-grid-layout-and-areas.html` | 06 Grid | Full page layout (header/sidebar/main/footer) via named `grid-template-areas`, a responsive image gallery with `repeat(auto-fit, minmax(200px,1fr))`, and a mobile media query that redefines the grid areas into a stacked layout. |
| `03-responsive-and-fluid-units.html` | 08 Responsive Design and Media Queries (+ 04 Colors Units and Typography for fluid units) | Fluid typography with `clamp()`, `aspect-ratio` boxes, mobile-first `@media (min-width: ...)` breakpoints, `prefers-color-scheme` dark mode with a manual toggle, and `prefers-reduced-motion` handling. |
| `04-transitions-animations-transforms.html` | 09 Transitions Animations and Transforms | Hover `transition`s on a button and card, `@keyframes` spinner + pulsing dot, and a CSS-only flip-card using `perspective` / `transform-style: preserve-3d` / `backface-visibility`. |
| `05-modern-css-features.html` | 10 Advanced CSS Variables and Pseudo Selectors, 12 Container Queries | `:has()` parent-selection on form fields and a card list, `:is()` / `:where()` selector grouping, native CSS nesting (`&`), `@container` with `container-type: inline-size`, and CSS custom properties driving a themeable button component. |
| `06-bem-naming-example.html` | 11 CSS Methodologies BEM and SMACSS | A product-card component built strictly with BEM naming (`card`, `card__title`, `card--featured`, `card__badge--sale`, etc.), with an explanatory comment block on the naming rules followed. |

## Chapters not given a dedicated practical file

A few Theory chapters are either pure reference material or are already woven into the files above rather than needing a standalone demo:

- **01 Quick Notes** — a summary/cheat-sheet chapter; no separate practical needed.
- **02 Selectors and Specificity** — specificity concepts are exercised throughout every file above (e.g. `:where()` zero-specificity vs `:is()` in file 05, BEM's flat class-only specificity in file 06).
- **03 Box Model and Display** — box-sizing, padding, borders and display values are used as the structural foundation of every card/section in files 01–06.
- **04 Colors Units and Typography** — color values, gradients, `ch`/`vw`/`rem` units and `clamp()` typography are demonstrated concretely in file 03.
- **07 Positioning and Layout** — `position: relative/absolute` is used for badges/overlays in files 02, 04 and 06 (e.g. the BEM `card__badge`).
- **13 Filters Blend Modes and Clip-Path** and **14 Print Stylesheets** — narrower, situational topics; if you want dedicated demo files for these, ask and they can be added as `07-filters-blend-clip-path.html` and `08-print-stylesheet.html`.

## How to use these files

1. Open any `.html` file directly in a browser (Chrome/Edge/Firefox, evergreen versions).
2. Resize the browser window to see the responsive/media-query behavior.
3. Read the `<style>` block in each file's source (View Source / DevTools) — every rule that demonstrates a specific concept has an inline comment explaining it.
4. All examples use current, well-supported 2024/2025-era CSS (nesting, `:has()`, container queries, logical properties like `inset`/`padding-inline`) which work in current evergreen browsers.
