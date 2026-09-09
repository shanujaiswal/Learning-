# What is CSS

--> CSS (Cascading Style Sheets) describes how HTML elements are displayed — layout, color, spacing, fonts, etc.
--> Three ways to add CSS: inline (`style="..."` attribute), internal (`<style>` in `<head>`), external (`<link rel="stylesheet" href="file.css">`).
--> External CSS is preferred — separates content (HTML) from presentation (CSS), reusable across pages.

# Basic Selectors

--> `element` — selects all elements of that tag, e.g. `p { }` selects all `<p>` tags.
--> `.classname` — selects all elements with that class → class → 1 page → unlimited repeat.
--> `#idname` — selects the element with that id → id → 1 page → 1 time repeat.
--> `*` — universal selector, selects everything.
--> `element1, element2` — group selector, applies the same rule to multiple selectors.

# Combinators

--> `A B` (descendant) — selects all `B` elements inside `A`, at any depth.
--> `A > B` (child) — selects `B` elements that are direct children of `A`.
--> `A + B` (adjacent sibling) — selects the `B` immediately after `A`.
--> `A ~ B` (general sibling) — selects all `B` siblings that come after `A`.

# Attribute Selectors

--> `[attr]` — has the attribute.
--> `[attr="value"]` — attribute equals value exactly.
--> `[attr^="value"]` — attribute starts with value.
--> `[attr$="value"]` — attribute ends with value.
--> `[attr*="value"]` — attribute contains value.

# Pseudo-classes

--> `:hover` — when the mouse is over the element.
--> `:focus` — when the element has focus (e.g. an input being typed in).
--> `:active` — while the element is being clicked/pressed.
--> `:first-child` / `:last-child` — first/last child of its parent.
--> `:nth-child(n)` — selects elements based on their position among siblings.
--> `:not(selector)` — selects everything that does NOT match the selector.
--> `:checked`, `:disabled`, `:required` — state-based selectors for form elements.
--> `:focus-visible` — like `:focus`, but only matches when the browser determines the focus indicator should be shown (e.g. keyboard Tab navigation) and NOT for a mouse click. Lets you keep a clean look on mouse click while still showing a clear outline for keyboard users — the modern, accessible replacement for blanket `:focus { outline: none; }`.

```css
/* Bad for accessibility: removes the indicator for everyone, including keyboard users */
button:focus { outline: none; }

/* Good: only suppress the outline for mouse users, keep it for keyboard users */
button:focus:not(:focus-visible) { outline: none; }
button:focus-visible { outline: 2px solid dodgerblue; }
```

# Pseudo-elements

--> `::before` / `::after` — insert generated content before/after an element's content (used with the `content` property).
--> `::first-letter` / `::first-line` — style the first letter/line of text.
--> `::placeholder` — styles placeholder text inside inputs.

# Modern Selectors

--> `:is(selector-list)` — matches if any selector in the list matches; shortens repetitive grouped selectors, e.g. `:is(header, footer) p`.
--> `:where(selector-list)` — same matching as `:is()`, but always has zero specificity, useful for overridable base styles.
--> `:has(selector)` — matches an element if it contains something matching the given selector (a "parent selector"), e.g. `div:has(> img)`.

# Specificity (which rule wins)

--> Specificity order (lowest to highest): element/pseudo-element < class/attribute/pseudo-class < id < inline style < `!important`.
--> Technically specificity is a 3-part tuple: (id count, class/attribute/pseudo-class count, type/pseudo-element count), compared column by column left to right — not a single linear score. A single id always beats any number of classes, and a single class always beats any number of elements, because the comparison stops at the first column where the counts differ. The "id > class > element" mental model above is a simplified shortcut for this same rule.
--> `!important` --> forces a declaration to override normal specificity rules (used to indicate the important — use sparingly).
--> When specificity is equal, the rule that appears later in the stylesheet wins.

# Comments

--> `/* comment text */` — not rendered, used for notes in the stylesheet.

# BEM Naming Convention

--> BEM (Block, Element, Modifier) is a class-naming methodology that keeps CSS predictable and avoids specificity wars by relying on flat, single-class selectors instead of nesting/combinators.
--> Block --> a standalone component, e.g. `.card`.
--> Element --> a part of a block, written as `block__element`, e.g. `.card__title`, `.card__image`.
--> Modifier --> a variant/state of a block or element, written as `block--modifier`, e.g. `.card--featured`, `.card__title--large`.
--> Benefit --> every class is a single, low-specificity selector (`.card__title` not `.card .title`), so styles rarely fight each other and it's obvious from the class name alone where a rule belongs.
