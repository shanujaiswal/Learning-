# What is Flexbox

--> Flexbox is a one-dimensional layout system — arranges items in a row OR a column.
--> Activated with `display: flex;` on the parent (the "flex container"); its direct children become "flex items".

# Container Properties

--> `flex-direction: row | row-reverse | column | column-reverse` — the main axis direction.
--> `justify-content` — alignment along the main axis: `flex-start`, `flex-end`, `center`, `space-between`, `space-around`, `space-evenly`.
--> `align-items` — alignment along the cross axis: `flex-start`, `flex-end`, `center`, `stretch`, `baseline`.
--> `align-content` — aligns multiple lines (only relevant when wrapping and there's extra space in the cross axis).
--> `flex-wrap: nowrap | wrap | wrap-reverse` — whether items stay on one line or wrap to multiple lines.
--> `gap` — space between flex items (row-gap / column-gap individually).

# Item Properties

--> `flex-grow` — how much an item grows relative to siblings when there's extra space (default `0`).
--> `flex-shrink` — how much an item shrinks relative to siblings when space is tight (default `1`).
--> `flex-basis` — the item's initial size before growing/shrinking is applied.
--> `flex: 1` — shorthand for `flex-grow: 1; flex-shrink: 1; flex-basis: 0;` (item grows/shrinks to fill space equally). Note: the basis here is unitless `0`, treated as a length (not `0%`) — in practice this rarely matters, but it's the technically correct value.
--> `align-self` — overrides `align-items` for a single item.
--> `order` — changes the visual order of an item without changing the HTML order (default `0`).

# Common Flexbox Patterns

--> Center anything: `display: flex; justify-content: center; align-items: center;`
--> Navbar with logo left, links right: `display: flex; justify-content: space-between; align-items: center;`
--> Equal-width columns: give each child `flex: 1;`
--> Sticky footer: wrap page in `display: flex; flex-direction: column; min-height: 100vh;` and give the main content `flex: 1;`

# Main Axis vs Cross Axis

--> Main axis — the direction set by `flex-direction` (row = horizontal, column = vertical).
--> Cross axis — perpendicular to the main axis.
--> `justify-content` always works on the main axis; `align-items`/`align-content` always work on the cross axis.

# flex-wrap + align-content Example

```css
.container {
  display: flex;
  flex-wrap: wrap;
  align-content: space-between; /* spreads wrapped lines apart in the cross axis */
}
```

# Deep Dive — The Proportional Growth Mechanic

--> If three items have `flex-grow: 1`, `flex-grow: 1`, and `flex-grow: 2` respectively, any EXTRA available space is distributed in a 1:1:2 ratio — the third item gets TWICE as much of the leftover space as each of the other two, not twice its total final size.

# Deep Dive — `flex: 1` vs `flex: 1 1 auto`

--> This is a genuinely common, subtle point of confusion. `flex: 1` (shorthand for `1 1 0%`) makes every item's STARTING size effectively zero before growing, meaning items end up EQUAL width regardless of their content's natural size. `flex: 1 1 auto` starts from each item's natural content size THEN distributes any extra space proportionally on top — items with more natural content can end up wider even with the same `flex-grow` value.

```css
.item-a { flex: 1; }         /* Ends up equal width to siblings, ignoring content size */
.item-b { flex: 1 1 auto; }    /* Starts from natural content width, then shares leftover space */
```

# Deep Dive — `order` and Accessibility

--> **Important accessibility caveat** — `order` changes only the VISUAL order, not the underlying DOM/HTML source order. Screen readers and keyboard tab navigation generally still follow DOM order, not visual `order` — using `order` to create a significant mismatch between visual layout and DOM order can create a confusing experience for keyboard/screen-reader users, who navigate in an order that doesn't match what they see on screen.

# Deep Dive — `gap` Replacing the Old Margin Hack

--> `gap` is the modern replacement for the older technique of adding margin to every item and then subtracting a compensating negative margin from the container to avoid unwanted extra space at the edges — a real, previously-necessary hack that `gap` eliminates entirely, now widely supported across all modern browsers.

```css
.container {
  display: flex;
  gap: 16px 8px;   /* row-gap column-gap, specified separately */
}
```

# Deep Dive — Real-World Pattern: Responsive Card Row

```css
.card-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.card {
  flex: 1 1 250px;   /* Grow/shrink, but never smaller than 250px — wraps to a new row once that's not achievable */
}
```
