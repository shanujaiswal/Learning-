# Color Values

--> Named colors — `red`, `blue`, `tomato`, etc.
--> Hex — `#ff0000` (or shorthand `#f00`); `#rrggbbaa` adds alpha (transparency).
--> `rgb(255, 0, 0)` / `rgba(255, 0, 0, 0.5)` — red/green/blue (+ alpha).
--> `hsl(0, 100%, 50%)` / `hsla(...)` — hue, saturation, lightness (+ alpha); more intuitive for adjusting shades.
--> `currentColor` — reuses the element's current text color in another property (e.g. `border-color`).
--> `hwb(200 30% 10%)` — hue, whiteness, blackness; another intuitive alternative to hsl.
--> `oklch(60% 0.15 250)` / `lch(...)` — perceptually uniform color spaces (lightness, chroma, hue); adjusting lightness looks visually consistent across hues, wider gamut than sRGB.
--> `color-mix(in srgb, red 50%, blue 50%)` — mixes two colors by a given percentage directly in CSS, no preprocessor needed.

# Units — Absolute vs Relative

--> `px` — absolute pixel unit, fixed size regardless of context.
--> `%` — relative to the parent element's corresponding property.
--> `em` — relative to the font-size of the current element (or its parent if font-size isn't set).
--> `rem` — relative to the font-size of the root (`<html>`) element; more predictable than `em` for consistent scaling.
--> `vw` / `vh` — 1% of the viewport's width/height.
--> `vmin` / `vmax` — 1% of the viewport's smaller/larger dimension.
--> `auto` — browser calculates the value automatically.

# Typography Basics

--> `font-family` — list of fonts in order of preference, ending with a generic fallback (`serif`, `sans-serif`, `monospace`).
--> `font-size` — size of the text.
--> `font-weight` — thickness of text (`normal`, `bold`, or `100`–`900`).
--> `font-style` — `normal`, `italic`, `oblique`.
--> `line-height` — vertical space between lines of text; unitless values scale with font-size.
--> `text-align` — `left`, `right`, `center`, `justify`.
--> `text-decoration` — `underline`, `line-through`, `none`.
--> `text-transform` — `uppercase`, `lowercase`, `capitalize`.
--> `letter-spacing` / `word-spacing` — space between letters/words.
--> `white-space: nowrap` — prevents text from wrapping to a new line.
--> `text-overflow: ellipsis` (with `overflow: hidden; white-space: nowrap;`) — truncates overflowing text with `...`.

# Web Fonts

--> `@font-face` — defines a custom font to load and use by name.
--> Google Fonts / CDN links — imported via `<link>` in HTML or `@import` in CSS.
--> `font-display: swap | block | fallback | optional` — controls how a page renders text while a `@font-face` font is still loading (`swap` shows fallback text immediately, then swaps in the custom font once ready).
--> Variable fonts — a single font file that contains a continuous range of weights/widths/styles; adjust with `font-variation-settings: "wght" 550;` instead of loading separate font files per weight.

# Color Theory Basics

--> Color theory is choosing colors that work well together, based on their relationships on the color wheel (a circular arrangement of hues) rather than picking colors randomly.
--> Complementary colors — sit opposite each other on the color wheel (e.g. blue and orange); high contrast, good for making an element (like a call-to-action button) stand out against the rest of the design.
--> Analogous colors — sit next to each other on the color wheel (e.g. blue, teal, green); low contrast, naturally harmonious, good for calm/cohesive-looking UIs.
--> Triadic colors — three colors evenly spaced around the wheel (e.g. red, yellow, blue); vibrant and balanced, but needs one dominant color with the other two as accents to avoid looking too busy.
--> 60-30-10 rule — a practical UI guideline: ~60% of the design uses a dominant/neutral color, ~30% a secondary color, ~10% an accent color (e.g. for buttons/links) — keeps a palette from feeling chaotic.
--> Warm colors (red, orange, yellow) feel energetic/urgent; cool colors (blue, green, purple) feel calm/trustworthy — a factor when choosing a brand palette, not just a visual preference.

# CSS Variables (Custom Properties)

--> Defined with `--name: value;`, usually on `:root` for global scope.
--> Used with `var(--name)`, optionally with a fallback: `var(--name, default-value)`.

```css
:root {
  --primary-color: #3b82f6;
}
.button {
  background-color: var(--primary-color);
}
```
