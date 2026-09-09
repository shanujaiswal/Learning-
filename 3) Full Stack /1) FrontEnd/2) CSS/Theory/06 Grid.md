# What is CSS Grid

--> Grid is a two-dimensional layout system — arranges items in rows AND columns simultaneously.
--> Activated with `display: grid;` on the parent (the "grid container").

# Defining the Grid

--> `grid-template-columns` — defines the number/size of columns, e.g. `grid-template-columns: 1fr 1fr 1fr;` (3 equal columns).
--> `grid-template-rows` — defines the number/size of rows.
--> `fr` unit — a fraction of the remaining available space (flexible, grid-specific unit).
--> `repeat(3, 1fr)` — shorthand for repeating the same track definition, e.g. equivalent to `1fr 1fr 1fr`.
--> `minmax(100px, 1fr)` — track sizes between a minimum and maximum.
--> `gap` (or `row-gap` / `column-gap`) — spacing between grid tracks.
--> `grid-template` — shorthand for `grid-template-rows` / `grid-template-columns` / `grid-template-areas` together.
--> `grid-template-columns: subgrid` — lets a nested grid item inherit its parent's track sizing instead of defining its own, keeping nested grids aligned to the outer grid.

# Placing Items

--> `grid-column: 1 / 3` — item spans from column line 1 to column line 3.
--> `grid-row: 2 / 4` — item spans from row line 2 to row line 4.
--> `grid-column: span 2` — item spans 2 columns starting from its auto-placed position.
--> `grid-area` — shorthand for row-start / column-start / row-end / column-end, or a named area.

# Named Template Areas

```css
.container {
  display: grid;
  grid-template-columns: 200px 1fr;
  grid-template-areas:
    "sidebar header"
    "sidebar main"
    "sidebar footer";
}
.sidebar { grid-area: sidebar; }
.header  { grid-area: header; }
.main    { grid-area: main; }
.footer  { grid-area: footer; }
```

# Auto-placement

--> `grid-auto-flow: row | column` — direction new items are auto-placed when not explicitly positioned.
--> `grid-auto-rows` / `grid-auto-columns` — size of implicitly created tracks.
--> `auto-fill` / `auto-fit` (with `repeat()`) — automatically fit as many tracks as possible into the available space; common for responsive card grids: `grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));`
--> Explicit grid — the tracks you deliberately define with `grid-template-columns`/`grid-template-rows`. Implicit grid — extra tracks the browser auto-generates (sized via `grid-auto-rows`/`grid-auto-columns`) when items are placed outside the explicit grid.

# Alignment in Grid

--> `justify-items` / `align-items` — aligns items within their own grid cell (horizontal/vertical).
--> `justify-content` / `align-content` — aligns the whole grid within the container when it's smaller than the container.
--> `justify-self` / `align-self` — overrides alignment for a single item.

# Flexbox vs Grid — When to Use Which

--> Flexbox — best for one-dimensional layouts (a single row or column) like navbars, toolbars, button groups.
--> Grid — best for two-dimensional layouts (rows and columns together) like page layouts, image galleries, dashboards.

# Deep Dive — auto-fill vs auto-fit

--> A genuinely subtle but important difference. `auto-fill` keeps EMPTY track slots if there's more room than content needs (items stay their `minmax` size, with visible empty tracks after them). `auto-fit` COLLAPSES those empty tracks, letting the actual content items stretch to fill the full available width instead.

```css
.gallery {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));   /* Cards stretch to fill the row when there aren't enough to complete it */
}
```

--> For a card gallery where you want cards to stretch and fill the row when there aren't enough to complete it, `auto-fit` is almost always the desired behavior; `auto-fill` is more appropriate when you specifically want to preserve consistent card sizing even with empty trailing space.

# Deep Dive — Grid Line Numbering

--> Grid lines are numbered starting from 1 (not 0) — a 3-column grid has 4 vertical grid lines (before column 1, between 1-2, between 2-3, and after column 3). `grid-column: 1 / 3` spans from the FIRST line to the THIRD line, covering the first two columns — a detail that trips up developers expecting 0-indexed or purely count-based semantics.

# Deep Dive — Responsive Layout via Redefining Named Areas

--> Because `grid-template-areas` is just a property value, an entirely different layout (e.g. stacking everything vertically on mobile) can be defined inside a media query by simply redefining the SAME named areas in a new arrangement, without touching the HTML or the named `grid-area` assignments on individual elements at all.

```css
@media (max-width: 600px) {
  .page-layout {
    grid-template-columns: 1fr;
    grid-template-areas:
      "header"
      "main"
      "sidebar"
      "footer";
  }
}
```

# Deep Dive — Grid and Flexbox Working Together

--> A real page layout commonly uses BOTH simultaneously, each for what it does best — Grid for the overall page/component structure, and Flexbox WITHIN individual grid areas for arranging their internal one-dimensional content (a row of buttons inside the header area, a vertical stack of nav links inside the sidebar area).

```css
.header {
  grid-area: header;
  display: flex;              /* Flexbox WITHIN a grid area, for the header's own internal layout */
  justify-content: space-between;
  align-items: center;
}
```
