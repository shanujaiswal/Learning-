# Why CSS Needs a Naming Methodology

--> Plain CSS has no built-in scoping -- every class name lives in one global namespace. On a large project, this leads to accidental collisions, deeply nested overrides, and specificity wars where nobody's sure which rule will actually win.
--> CSS methodologies are naming/organizational CONVENTIONS (not new CSS features) that keep large stylesheets predictable and maintainable.

# BEM -- Block, Element, Modifier

--> Block -- a standalone, reusable component (`card`, `menu`, `button`).
--> Element -- a part of a block that has no meaning on its own (`card__title`, `menu__item`) -- written as `block__element`.
--> Modifier -- a variant/state of a block or element (`button--primary`, `card--highlighted`) -- written as `block--modifier`.

```html
<div class="card card--highlighted">
  <h2 class="card__title">Title</h2>
  <p class="card__description">Description text</p>
  <button class="card__button card__button--disabled">Buy</button>
</div>
```

```css
.card { border: 1px solid #ddd; }
.card--highlighted { border-color: gold; }
.card__title { font-size: 1.2rem; }
.card__button--disabled { opacity: 0.5; pointer-events: none; }
```

--> Benefit -- flat specificity (every selector is a single class, so there's no nesting-order ambiguity), and the class name alone tells you exactly what a component is and which part of it you're looking at, without reading the surrounding HTML structure.

# SMACSS -- Scalable and Modular Architecture for CSS

--> Categorizes every CSS rule into one of five types, each with a naming/organizational convention:
--> **Base** -- default element styles with no class needed (`body`, `a`, `h1`).
--> **Layout** -- major page structure (`.l-header`, `.l-sidebar`) -- macro-level positioning.
--> **Module** -- reusable, self-contained components (`.card`, `.button`) -- the bulk of most stylesheets.
--> **State** -- describes how a module looks in a particular state (`.is-active`, `.is-collapsed`, `.is-disabled`) -- often applied/removed via JavaScript.
--> **Theme** -- visual variations (color schemes, branding) that can be swapped without touching structural/module rules.

```css
/* State example -- toggled via JS, not tied to one specific module */
.is-hidden { display: none; }
.is-loading { opacity: 0.5; pointer-events: none; }
```

# BEM vs SMACSS vs Utility-First (Tailwind)

--> BEM/SMACSS are naming/organization conventions applied to hand-written CSS -- they solve maintainability but still involve writing and maintaining a separate stylesheet.
--> Utility-first frameworks (Tailwind CSS) take the opposite approach -- compose small, single-purpose utility classes directly in markup (`class="flex p-4 text-lg"`) instead of writing custom component classes at all -- trading "reading a component's own stylesheet" for "reading the classes right there in the HTML."
--> Neither approach is universally "correct" -- BEM/SMACSS suit teams wanting clean separation between markup and styling logic; utility-first suits teams prioritizing development speed and avoiding CSS file sprawl. Modern component frameworks (React, Vue) also enable CSS Modules/scoped styles as another alternative to solving the same global-namespace problem.
