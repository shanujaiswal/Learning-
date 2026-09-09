# Additional Media Features

--> Beyond `prefers-color-scheme` and viewport-width queries (covered in the Responsive Design and Media Queries file), several newer media features let CSS respond to accessibility/system-level user preferences rather than just screen geometry.

```css
/* User has an OS-level "high contrast" / forced colors mode enabled (e.g. Windows High Contrast) */
@media (forced-colors: active) {
  .card {
    border: 1px solid CanvasText;   /* Use system color keywords, not arbitrary hex values */
  }
}

/* User has requested reduced transparency effects */
@media (prefers-reduced-transparency: reduce) {
  .frosted-panel {
    backdrop-filter: none;
    background: white;   /* Solid fallback instead of a translucent blur */
  }
}

/* User is on a constrained/metered connection and prefers less data usage */
@media (prefers-reduced-data: reduce) {
  .hero-video {
    display: none;   /* Skip the autoplay video background entirely */
  }
  .hero-fallback-image {
    display: block;
  }
}
```

--> `forced-colors: active` -- fires when the OS is overriding the page's own colors with a limited, user-chosen palette (a Windows accessibility feature) -- in this mode you should generally rely on CSS system color keywords (`Canvas`, `CanvasText`, `LinkText`, `ButtonFace`, etc.) rather than fighting the override with arbitrary colors, since the OS deliberately ignores most author colors in this mode anyway.
--> `prefers-reduced-transparency` -- mirrors `prefers-reduced-motion`'s intent (covered in the Responsive Design file) but for transparency/blur effects, which can reduce readability for some users with low vision.
--> `prefers-reduced-data` -- lets a page voluntarily skip heavy assets (autoplay video, large background images, custom web fonts) for users who have signaled a general preference for lower data usage, independent of actual detected connection speed.

# Less-Common Length Units

--> Beyond `px`/`%`/`rem`/`em` (covered in the Colors, Units and Typography file), several length units are defined relative to the CURRENT FONT's actual glyph metrics rather than a fixed reference size -- genuinely useful for typography that should scale precisely with character shapes, not just font-size.

```css
.terminal-input {
  width: 40ch;   /* Roughly 40 characters wide in the current monospace font -- ideal for code inputs */
}

.tight-underline {
  text-decoration-line: underline;
  text-underline-offset: 0.1ex;   /* Offset relative to lowercase letter height */
}

.small-cap-label {
  font-size: 3cap;   /* Relative to the font's capital-letter height */
}

.icon-sized-text {
  font-size: 2ic;   /* Relative to a full-width (CJK) character's advance -- useful in CJK-aware layouts */
}
```

--> `ch` -- the advance width of the `"0"` (zero) glyph in the current font -- widely supported and genuinely useful for sizing text inputs/code blocks to a target character count (`width: 60ch` for a comfortable reading line length).
--> `ex` -- roughly the x-height (lowercase letter height, e.g. the height of "x") of the current font -- historically inconsistent across browsers/fonts, used sparingly.
--> `cap` -- the height of a capital letter in the current font -- newer and less broadly supported than `ch`/`ex`, useful for precisely vertically-centering capital text against other elements.
--> `ic` -- the advance measure of the "水" (CJK water ideograph) reference character, useful for sizing layouts intended to hold full-width CJK text predictably.
--> All four are FONT-RELATIVE rather than viewport-relative (`vw`/`vh`) or root-relative (`rem`) -- they change automatically if the font itself changes, which is exactly the point for typography-sensitive sizing.

# `:target`, `:empty`, `:only-child`, `:nth-last-child()`

--> Rounding out the structural/state pseudo-classes beyond the `:nth-child()` family (covered in the Advanced CSS Variables and Pseudo Selectors file):

```css
/* Highlight whichever section the URL's #hash currently points to */
section:target {
  background: #fffbcc;
}
```

```html
<a href="#section-2">Jump to Section 2</a>
<section id="section-2">...</section>
```

```css
/* Hide a container entirely if it has no content at all (e.g. an empty comments list) */
.comment-list:empty::before {
  content: "No comments yet.";
  color: #888;
}

/* Style an item differently only when it's literally the ONLY child -- no siblings at all */
li:only-child {
  font-weight: bold;
}

/* Count from the END of the sibling list instead of the start */
li:nth-last-child(1) {
  border-bottom: none;   /* The actual last item, regardless of total count */
}
li:nth-last-child(-n+3) {
  color: dodgerblue;   /* The last three items */
}
```

--> `:target` -- matches the element whose `id` matches the current URL fragment (`#section-2`) -- enables pure-CSS "highlight the linked section" or even pure-CSS tab/accordion patterns (toggling `display` based on which target is active) without any JavaScript.
--> `:empty` -- matches an element with absolutely no children, including no whitespace text nodes -- a stray space or newline between tags in the markup will actually prevent `:empty` from matching, a common gotcha.
--> `:only-child` -- matches an element that is the sole child of its parent (equivalent to `:first-child:last-child` combined).
--> `:nth-last-child(n)` -- identical math to `:nth-child()` but counting position from the END of the sibling list backward -- particularly useful for "style the last N items" patterns where the total item count isn't known/fixed ahead of time.

# CSS Shapes -- `shape-outside`

--> `shape-outside` lets INLINE content (text) wrap around a custom geometric shape rather than just the rectangular bounding box of a floated element -- e.g. text flowing naturally around a circular avatar or an irregular illustration instead of leaving an awkward rectangular gap.

```css
.avatar {
  float: left;
  width: 150px;
  height: 150px;
  shape-outside: circle(50%);
  shape-margin: 12px;   /* Extra breathing room between the shape's edge and the wrapping text */
  border-radius: 50%;
}
```

--> Requires the shaped element to be `float`ed -- `shape-outside` has no effect on a non-floated element, since it's fundamentally about how surrounding inline content flows around a float.
--> Accepts the same shape functions as `clip-path` (`circle()`, `ellipse()`, `polygon()`, `inset()`), plus `shape-outside: url(image.png)` to derive the wrap shape from an image's actual alpha channel (e.g. wrapping text tightly around an irregular PNG illustration's silhouette).
--> `shape-margin` -- adds spacing between the computed shape boundary and the text that wraps around it, avoiding text crowding right up against the shape's edge.

# CSS Grid Masonry Layout

--> Standard Grid (covered in the Grid file) aligns items into a strict row-and-column grid, meaning items of different heights leave gaps to keep every row aligned -- exactly what a Pinterest-style masonry layout intentionally avoids. Masonry lets items pack into the next available column at whatever height fits best, closing those gaps.

```css
.masonry-gallery {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: masonry;
  gap: 12px;
}
```

--> Setting `grid-template-rows: masonry` (while columns remain a normal explicit track list) tells the grid to place items into whichever column currently has the least content height next, rather than forcing every item in a row to align to the same row-track boundaries.
--> This directly replaces the common older workaround of faking masonry with `column-count`/`column-width` (the multi-column layout module) or a JavaScript layout library (e.g. Masonry.js) -- both of which have their own drawbacks (multi-column fills top-to-bottom in reading-order-breaking ways; JS libraries add a dependency and a layout-thrash cost).
--> As a newer addition to the Grid spec, masonry has narrower browser support than the rest of Grid -- treat it as a progressive enhancement layered over a working non-masonry Grid/Flexbox fallback rather than a layout to depend on unconditionally.

# `initial`, `inherit`, `unset`, `revert` -- CSS-Wide Keywords

--> Every CSS property accepts these four special keyword values instead of an ordinary value, each resetting the property in a distinctly different way -- the differences are easy to blur together but matter for predictable overrides.

```css
.reset-example {
  color: initial;    /* Resets to the property's spec-defined default (usually black for `color`) */
  color: inherit;    /* Forces it to take the parent element's computed value, even if not normally inherited */
  color: unset;      /* Acts like `inherit` for naturally-inherited properties, `initial` for non-inherited ones */
  color: revert;     /* Rolls back to what the USER-AGENT stylesheet would have set, ignoring author/user styles */
}
```

--> `initial` -- resets the property to its DEFAULT value as defined by the CSS specification for that property, regardless of what the browser's own default stylesheet says or what any parent element has. For `color` that's `black`; for `display` that's `inline` -- NOT necessarily what looks visually "unstyled" in a browser.
--> `inherit` -- explicitly forces inheritance from the parent's COMPUTED value, useful for properties that don't inherit by default (like `border` or `padding`) when you specifically want a child to match its parent anyway.
--> `unset` -- the property-aware smart default: behaves as `inherit` for properties that normally inherit (`color`, `font-family`, `line-height`), and as `initial` for properties that normally don't (`margin`, `border`, `display`) -- generally the most intuitive "just reset this" choice of the four.
--> `revert` -- rolls the property back to whatever the BROWSER's built-in user-agent stylesheet would have set for that element (e.g. `revert` on a `<button>`'s `all` property restores native button chrome), rather than the spec's generic `initial` default -- the only one of the four that's element-and-browser aware rather than a single fixed value.
--> All four also work with the `all` shorthand (`all: unset;`) to reset EVERY property on an element at once -- a heavier-handed alternative to a manual reset ruleset (covered in the Advanced CSS Variables and Pseudo Selectors file) for cases like fully de-styling a `<button>` back to plain content.

# CSS-in-JS vs. Traditional CSS -- Trade-off Discussion

--> CSS-in-JS (styled-components, Emotion, and similar libraries) writes component styles as JavaScript, generating scoped class names and injecting `<style>` tags at runtime (or at build time for "zero-runtime" variants) -- an alternative to writing `.css` files, BEM naming (covered in the CSS Methodologies file), or CSS Modules.

```javascript
// CSS-in-JS example (styled-components)
const Card = styled.div`
  padding: 1rem;
  border: 1px solid ${(props) => props.theme.borderColor};
  &:hover { border-color: dodgerblue; }
`;
```

```css
/* Equivalent traditional CSS, scoped by naming convention instead of tooling */
.card {
  padding: 1rem;
  border: 1px solid var(--border-color);
}
.card:hover {
  border-color: dodgerblue;
}
```

--> **CSS-in-JS advantages** -- styles can directly reference JavaScript values/props (theme objects, component state) without a separate custom-property bridge; class name collisions become structurally impossible since names are auto-generated per component; dead CSS for unused components is easier to eliminate since styles live and die with the component file.
--> **CSS-in-JS costs** -- runtime variants add actual JavaScript execution cost to style computation (parsing template literals, generating class names, injecting stylesheets) that plain CSS never pays, since a `.css` file is parsed once by the browser's native CSS engine; it also couples styling to a specific JS framework/library, undermining the framework-agnostic reuse that Web Components/Shadow DOM (covered in the Web Components file) are built around.
--> **Where native CSS has closed the gap** -- `@layer` (this file's earlier section) now offers native, framework-agnostic control over override order that CSS-in-JS's automatic specificity previously had an edge on; native custom properties + `@property` cover most of the "styles driven by dynamic values" use case that originally motivated template-literal interpolation; native CSS Nesting (covered in the Advanced CSS Variables and Pseudo Selectors file) closes most of the syntactic-convenience gap with the `&`-nesting styled-components popularized.
--> **Practical takeaway** -- CSS-in-JS remains a reasonable choice inside a single, already-JS-framework-committed app that wants tight prop-driven theming; plain CSS (optionally with Cascade Layers, custom properties, and a naming convention like BEM) remains the better choice for anything intended to be framework-agnostic, for genuinely large stylesheets where runtime cost adds up, or for teams that want styling debuggable in browser devtools without a JS build step in the way.

# `@font-face` `unicode-range` and Font Subsetting

--> A single web font file, especially one covering a large writing system (CJK fonts routinely exceed several megabytes), forces every visitor to download the WHOLE file even if a page only ever uses a handful of its characters. `unicode-range` lets one `@font-face` declaration be split into several files, each covering only a specific range of Unicode code points, downloaded ONLY if the page's actual text needs a character in that range.

```css
@font-face {
  font-family: "Noto Sans";
  src: url("noto-sans-latin.woff2") format("woff2");
  unicode-range: U+0000-00FF, U+0131, U+0152-0153;   /* Basic Latin + a few extras */
}

@font-face {
  font-family: "Noto Sans";
  src: url("noto-sans-cyrillic.woff2") format("woff2");
  unicode-range: U+0400-04FF;   /* Cyrillic block */
}

@font-face {
  font-family: "Noto Sans";
  src: url("noto-sans-cjk.woff2") format("woff2");
  unicode-range: U+4E00-9FFF;   /* CJK Unified Ideographs */
}
```

--> All three `@font-face` rules share the SAME `font-family` name -- the browser treats them as one logical font split into subsets, and downloads only whichever subset file(s) actually contain a character present in the rendered page's text.
--> This is exactly what tools like Google Fonts do automatically behind the scenes (serving a different, smaller subset file depending on the requesting page's language/`Accept-Language`) -- `unicode-range` is the underlying CSS mechanism that makes that optimization possible, and self-hosted fonts can replicate it manually with subsetting tools (e.g. `fonttools subset`, glyphhanger).
--> **Font subsetting** (generating a font file containing only the specific glyphs a project actually uses, e.g. a logo font that only ever renders a company name) shrinks file size dramatically further, at the cost of that file being unusable for any other text -- appropriate for decorative/limited-use fonts, not for a general body-text font.

# OpenType `font-feature-settings`

--> Many professional/OpenType fonts bundle optional typographic features (ligatures, alternate numeral styles, small caps, stylistic swashes) that aren't active by default -- `font-feature-settings` (and the friendlier high-level properties layered over it) turns them on.

```css
.old-style-figures {
  font-feature-settings: "onum" 1;   /* Old-style (lowercase-height) numerals, often nicer inline in prose */
}

.tabular-numbers-table {
  font-feature-settings: "tnum" 1;   /* Tabular (fixed-width) numerals -- keeps numbers aligned in a data table */
}

.discretionary-ligatures {
  font-feature-settings: "dlig" 1, "liga" 1;
}

/* Preferred modern shorthand for the common cases, where supported */
.modern-numeric-table {
  font-variant-numeric: tabular-nums oldstyle-nums;
}
```

--> `font-feature-settings` takes one or more 4-letter OpenType feature tags with a value (`1` to enable, `0` to disable, or a number for features with multiple variants) -- it's a low-level escape hatch that works for ANY OpenType feature a font happens to define, including obscure/font-specific ones.
--> Common tags: `liga`/`dlig` (standard/discretionary ligatures), `smcp` (small caps), `tnum`/`pnum` (tabular vs. proportional numeral widths), `onum`/`lnum` (old-style vs. lining numerals), `zero` (slashed zero).
--> **Prefer the dedicated high-level properties when they exist** -- `font-variant-numeric`, `font-variant-ligatures`, `font-variant-caps` express the SAME common features with self-documenting keyword syntax instead of opaque 4-letter codes, and they're the recommended modern approach; `font-feature-settings` remains necessary only for a feature that has no dedicated high-level property, since not every OpenType feature has been given one.
--> Whether any of this has a visible effect depends entirely on whether the LOADED FONT FILE itself actually implements the given feature -- these properties enable a feature IF the font supports it, they cannot fabricate small caps or alternate numerals in a font that never designed them.
