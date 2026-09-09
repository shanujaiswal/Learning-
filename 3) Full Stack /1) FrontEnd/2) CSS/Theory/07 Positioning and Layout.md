# Position Property

--> `position: static` (default) — element follows the normal document flow; `top`/`left`/etc. have no effect.
--> `position: relative` — element stays in normal flow but can be shifted using `top`/`right`/`bottom`/`left`, relative to its own original position.
--> `position: absolute` — element is removed from normal flow and positioned relative to its nearest ancestor with a `position` other than `static`.
--> `position: fixed` — element is removed from normal flow and positioned relative to the viewport; stays in place when scrolling.
--> `position: sticky` — behaves like `relative` until a scroll threshold is met, then behaves like `fixed` (e.g. sticky headers).

# Positioning Offsets

--> `top`, `right`, `bottom`, `left` — set the offset distance from the respective edge (used with any position except `static`).
--> `z-index` — controls stacking order (which element appears on top); only works on positioned elements (non-`static`).

# Float (older layout technique)

--> `float: left | right` — takes an element out of normal flow and shifts it to one side; other content wraps around it.
--> `clear: left | right | both` — prevents an element from wrapping next to floated elements.
--> Mostly replaced by flexbox/grid today, but still used for text-wrapping around images.

# Centering Techniques

--> Horizontally center a block element: `margin: 0 auto;` (with a defined `width`).
--> Center with flexbox: `display: flex; justify-content: center; align-items: center;`
--> Center with grid: `display: grid; place-items: center;`
--> Center an absolutely positioned element:
```css
.child {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
```

# Stacking Context

--> A new stacking context is created by elements with `position` + `z-index`, `opacity < 1`, `transform`, `filter`, `will-change` (when set to a property that would create a stacking context, e.g. `transform`/`opacity`), `isolation: isolate`, among others.
--> `z-index` values only compare within the same stacking context.

# Overflow and Scrolling

--> `overflow: auto` — adds scrollbars only when content overflows.
--> `overflow-x` / `overflow-y` — control horizontal/vertical overflow independently.
--> `scroll-behavior: smooth` — animates scrolling triggered by anchor links or JS.

# Scroll Snap

--> Scroll Snap makes a scrollable container "snap" to specific positions as the user scrolls, instead of stopping at an arbitrary spot — commonly used for image carousels/galleries and full-page sections.
--> `scroll-snap-type` (on the SCROLLING CONTAINER) — sets the axis and strictness: `x mandatory` (always snaps), `y proximity` (snaps only if close enough), `both mandatory`.
--> `scroll-snap-align` (on the CHILD items) — where each item should snap to: `start`, `center`, or `end`.
--> `scroll-padding` (on the container) — offsets the snap position, useful when a sticky header would otherwise cover the snapped item.

```css
.gallery {
  display: flex;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
}
.gallery img {
  scroll-snap-align: center;
}
```

# Multi-Column Layout

--> The Multi-Column Layout module flows a single block of content across multiple newspaper-style columns automatically — different from Grid/Flexbox, which position discrete items rather than reflow one continuous stream of text.
--> `column-count: 3;` — splits the content into (up to) 3 columns.
--> `column-width: 200px;` — instead of a fixed count, creates as many columns as fit at ~200px each (responsive by default).
--> `column-gap: 2rem;` — spacing between columns.
--> `column-rule: 1px solid #ccc;` — draws a dividing line between columns (shorthand for width/style/color, like `border`).
--> `break-inside: avoid;` (on a child element) — prevents that element (e.g. a card or image) from being split awkwardly across two columns.

```css
.article {
  column-count: 3;
  column-gap: 2rem;
  column-rule: 1px solid #ddd;
}
```
