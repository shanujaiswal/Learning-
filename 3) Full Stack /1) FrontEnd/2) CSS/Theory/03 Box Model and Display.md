# The CSS Box Model

--> Every element is a rectangular box made of four layers (outside in): margin, border, padding, content.
--> `content` — the actual text/image/inner content.
--> `padding` — space between the content and the border (inside the element, part of its background).
--> `border` — the edge/outline around the padding.
--> `margin` — space outside the border, between this element and neighboring elements.

# Box Sizing

--> `box-sizing: content-box` (default) — `width`/`height` apply only to the content; padding/border add to the total size.
--> `box-sizing: border-box` — `width`/`height` include padding and border, making sizing far more predictable.
--> Common reset: `* { box-sizing: border-box; }`

# Width, Height, and Overflow

--> `width` / `height` — set explicit dimensions; can use `px`, `%`, `vw`/`vh`, `auto`.
--> `min-width` / `max-width` / `min-height` / `max-height` — constrain size within a range.
--> `overflow: visible | hidden | scroll | auto` — controls what happens when content exceeds its box.

# Display Property

--> `display: block` — takes up the full width available, starts on a new line (e.g. `div`, `p`, `h1`).
--> `display: inline` — takes only as much width as needed, does not start a new line (e.g. `span`, `a`); ignores width/height/vertical margin.
--> `display: inline-block` — flows inline but respects width/height/margin/padding like a block.
--> `display: none` — removes the element from the layout entirely (not just hidden visually).
--> `visibility: hidden` — hides the element but still reserves its space in the layout.
--> `display: flex` / `display: grid` — enables flexbox/grid layout for the element's children.
--> `display: inline-flex` / `display: inline-grid` — same as flex/grid, but the container itself flows inline like `inline-block` instead of taking the full width.
--> `display: contents` — the element itself disappears from the box tree (no box of its own), but its children render as if they were direct children of its parent; useful for unwrapping a wrapper element for layout purposes.

# Margin Collapsing

--> Vertical margins between two adjacent block elements collapse into a single margin (the larger of the two), rather than adding together.
--> Does not happen with horizontal margins, or when flex/grid is involved.
--> Also doesn't happen when `overflow: hidden` (or anything else that establishes a new Block Formatting Context, e.g. `display: flow-root`) is set on an element — this contains margins inside instead of letting them collapse through.

# Borders and Outlines

--> `border: 1px solid black;` — shorthand for width, style, color.
--> `border-radius` — rounds the corners of an element.
--> `outline` — drawn outside the border, does not affect layout/box size (commonly used for `:focus` states).

# Background

--> `background-color`, `background-image`, `background-size`, `background-position`, `background-repeat`.
--> `background: url(image.jpg) no-repeat center / cover;` — common shorthand pattern for full-cover background images.

# object-fit and object-position

--> Control how a replaced element's content (`<img>`, `<video>`) fills its box, when the box's aspect ratio differs from the content's natural size.
--> `object-fit: cover` — fills the box completely, cropping overflow, preserving aspect ratio (most common for thumbnails/avatars).
--> `object-fit: contain` — scales content to fit entirely inside the box, preserving aspect ratio (may leave empty space).
--> `object-fit: fill` (default) — stretches content to fill the box exactly, ignoring aspect ratio.
--> `object-position` — like `background-position`, controls which part of the content is visible/anchored when cropped, e.g. `object-position: top center;`
