# CSS Gradients

--> Gradients generate smooth color transitions directly in CSS, as a `background-image` value -- no image file needed, fully scalable, and easy to tweak/animate.

```css
.linear {
  background: linear-gradient(to right, #ff7e5f, #feb47b);
}

.radial {
  background: radial-gradient(circle at top left, #ffffff, #cccccc 70%, #999999);
}

.conic {
  background: conic-gradient(from 90deg, red, yellow, lime, aqua, blue, magenta, red);
}

.repeating-stripes {
  background: repeating-linear-gradient(45deg, #333 0 10px, #555 10px 20px);
}
```

--> `linear-gradient(direction, color-stops...)` -- transitions along a straight line; direction can be a keyword (`to right`, `to bottom left`) or an angle (`90deg`).
--> `radial-gradient(shape at position, color-stops...)` -- transitions outward from a center point, shape defaults to an ellipse matching the box, `circle` forces a perfect circle.
--> `conic-gradient(from angle, color-stops...)` -- transitions AROUND a center point like a color wheel/pie chart, rather than outward -- the basis for pie-chart effects and color-picker UIs done in pure CSS.
--> Color stops can carry explicit positions (`red 0%, yellow 50%`) to control exactly where each color sits, rather than spacing evenly.
--> `repeating-linear-gradient()` / `repeating-radial-gradient()` / `repeating-conic-gradient()` -- the stop list repeats infinitely across the element, useful for stripes, checkerboards, and other patterns without an image asset.
--> Multiple gradients (and gradients mixed with images) can be layered in one `background` declaration, comma-separated, the first listed rendering on top.

# `@supports` -- Feature Queries

--> `@supports` lets a stylesheet check whether the browser actually understands a given CSS feature BEFORE applying rules that depend on it -- a graceful-degradation mechanism analogous to `@media`, but querying feature support instead of viewport/device characteristics.

```css
.card {
  display: block;   /* Fallback for browsers without grid */
}

@supports (display: grid) {
  .card {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
  }
}

@supports not (backdrop-filter: blur(10px)) {
  .frosted-panel {
    background: rgba(255, 255, 255, 0.9);   /* Solid fallback instead of a blur effect */
  }
}

@supports (display: grid) and (gap: 1rem) {
  .layout { display: grid; gap: 1rem; }
}
```

--> The condition is a real CSS property/value pair, not just a property name -- `@supports (display: grid)` checks that `grid` specifically is a valid value for `display`, not merely that `display` exists (which would be true of every browser).
--> Supports `and`, `or`, and `not` for combining conditions, exactly like `@media`.
--> Write the FALLBACK styles as plain, unwrapped rules first, then progressively enhance inside `@supports` -- this way, a browser that doesn't understand `@supports` itself (extremely rare today) still gets a reasonable baseline rather than nothing.

# `@layer` -- Cascade Layers

--> Ordinarily, which of two conflicting rules wins is decided by specificity, then source order (covered in the Selectors and Specificity file) -- a system that gets unpredictable in large codebases mixing resets, a component library, utility classes, and overrides. `@layer` lets you declare explicit, named layers whose ORDER OF DECLARATION decides priority, overriding specificity entirely between layers.

```css
/* Declare the layer order up front -- later layers win over earlier ones, regardless of specificity */
@layer reset, base, components, utilities;

@layer reset {
  * { margin: 0; padding: 0; box-sizing: border-box; }
}

@layer base {
  body { font-family: system-ui, sans-serif; line-height: 1.5; }
  h1 { font-size: 2rem; }
}

@layer components {
  .card { padding: 1rem; border: 1px solid #ddd; }
}

@layer utilities {
  .p-0 { padding: 0 !important; }
  .text-center { text-align: center; }
}
```

--> **Why this solves a real problem** -- without layers, a highly-specific `.card h1 { font-size: 1.5rem; }` in `components` would normally beat the simple `h1 { font-size: 2rem; }` in `base`, EVEN IF you conceptually want `utilities` to always win over `components`, and `components` to always win over `base`. With the layers declared in that order above, `utilities` rules beat `components` rules, which beat `base` rules, which beat `reset` rules -- REGARDLESS of how specific any individual selector inside each layer is. Specificity still matters WITHIN a single layer, just not ACROSS layers.
--> Any rule NOT inside an `@layer` block is treated as belonging to one final, implicit layer that always wins over every named layer -- meaning ordinary un-layered CSS (e.g. an inline `<style>` block or a quick override file) still overrides layered styles, which is exactly why third-party libraries increasingly ship their own CSS wrapped in `@layer` -- so consuming projects can easily override them without a specificity fight.
--> Layers can be declared upfront (as a name list, establishing order before any rules exist) and then filled in later, in any order, across multiple files/`<link>` tags -- the DECLARATION order fixes the priority, not the order the content is actually written.

# Scroll-Driven Animations

--> Traditionally, tying an animation's progress to scroll position required a `scroll` event listener recalculating styles on every frame in JavaScript -- expensive and prone to jank. `animation-timeline` lets the browser drive a CSS animation directly from scroll position, entirely off the main thread.

```css
@keyframes fade-in {
  from { opacity: 0; transform: translateY(40px); }
  to { opacity: 1; transform: translateY(0); }
}

.progress-bar {
  animation: grow-bar linear;
  animation-timeline: scroll(root);   /* Progress tied to scrolling the whole page */
}

@keyframes grow-bar {
  from { width: 0%; }
  to { width: 100%; }
}

.reveal-on-scroll {
  animation: fade-in linear both;
  animation-timeline: view();   /* Progress tied to this element's own visibility in the viewport */
  animation-range: entry 0% cover 40%;
}
```

--> `scroll()` -- ties the animation's timeline to the scroll position of a scroll container (`root` for the whole document, or the nearest scrollable ancestor by default) -- good for page-wide progress indicators.
--> `view()` -- ties the animation's timeline to the ELEMENT'S OWN position as it travels through the viewport -- 0% progress when it enters, 100% when it exits -- the mechanism behind "fade/slide in as you scroll to this section" effects.
--> `animation-range` -- narrows exactly which portion of the scroll/view journey the animation plays across (e.g. only animate during the element's entry, not its entire time on screen).
--> Because these run on the browser's compositor rather than via repeated JavaScript recalculation, scroll-driven animations stay smooth even on a busy main thread -- a direct performance win over the older scroll-listener approach.

# CSS Houdini `@property`

--> Plain custom properties (`--gap: 16px;`, covered in the Advanced CSS Variables file) are untyped strings as far as the engine is concerned -- the browser has no idea `--angle: 45deg` is an ANGLE, which is exactly why you can't smoothly `transition`/`animate` a custom property by default (the engine can't interpolate between two arbitrary strings). `@property` registers a custom property with an explicit syntax/type, making it animatable.

```css
@property --gradient-angle {
  syntax: "<angle>";
  inherits: false;
  initial-value: 0deg;
}

.animated-gradient {
  background: conic-gradient(from var(--gradient-angle), red, orange, yellow, red);
  transition: --gradient-angle 1s ease;
}

.animated-gradient:hover {
  --gradient-angle: 360deg;
}
```

--> `syntax` -- declares the property's TYPE (`<color>`, `<length>`, `<angle>`, `<number>`, `<percentage>`, etc.) so the browser knows how to interpolate between values during a transition/animation.
--> `inherits` -- explicitly controls whether child elements inherit the value, rather than the implicit "yes" behavior of ordinary custom properties.
--> `initial-value` -- required for most syntax types; the fallback value used before anything sets the property.
--> This is genuinely the mechanism that makes rotating conic-gradient effects, animated custom shadows, and other previously-JavaScript-only interpolations possible in pure CSS.

# `color-scheme` Property

--> `color-scheme` tells the browser which light/dark palette(s) an element/page supports, so the browser can adjust its OWN default UI (form controls, scrollbars, focus outlines) to match, even before any of your own dark-mode CSS runs.

```css
:root {
  color-scheme: light dark;   /* Page supports both; browser picks based on user/OS preference */
}

.always-dark-widget {
  color-scheme: dark;   /* This specific component always renders with dark-mode native UI */
}
```

--> Without `color-scheme` set, a page with custom dark-mode CSS (via `prefers-color-scheme`, covered in the Responsive Design file) can end up with a dark page background but browser-default WHITE checkboxes/dropdowns/scrollbars -- `color-scheme` fixes exactly that mismatch by telling native form controls which palette to render themselves in.
--> Listing `light dark` (both) lets the browser choose based on the user's OS-level preference; listing only one forces that scheme for native UI regardless of OS preference.

# `accent-color` Property

--> `accent-color` recolors the browser's own native accent color used on checkboxes, radio buttons, range sliders, and progress bars -- without needing to fully rebuild these controls from scratch with custom markup/CSS just to match a brand color.

```css
:root {
  accent-color: #6366f1;
}

input[type="checkbox"].danger {
  accent-color: #dc2626;
}
```

--> Applies to `<input type="checkbox">`, `<input type="radio">`, `<input type="range">`, and `<progress>` -- each renders its native "on/filled" indicator in the given color while keeping every bit of built-in accessibility/keyboard behavior a custom-built replacement would have to reimplement.

# CSS Counters

--> CSS can maintain its own running numeric counters purely in the stylesheet, generating numbering (or bullet-like text) without any HTML list markup or JavaScript.

```css
.chapters {
  counter-reset: chapter;   /* Initialize/reset the counter to 0 on this container */
}

.chapters h2::before {
  counter-increment: chapter;   /* Increment by 1 each time an h2 is encountered */
  content: "Chapter " counter(chapter) ": ";
}

/* Nested counters -- e.g. "1.1", "1.2", "2.1" style outline numbering */
ol.outline {
  counter-reset: section;
  list-style: none;
}
ol.outline li::before {
  counter-increment: section;
  content: counter(section) ". ";
}
ol.outline li ol {
  counter-reset: subsection;
}
ol.outline li ol li::before {
  content: counter(section) "." counter(subsection) " ";
  counter-increment: subsection;
}
```

--> `counter-reset: name` -- creates/resets a named counter to zero (or a given starting number) on the element it's set on.
--> `counter-increment: name` -- increases that counter by 1 (or a given amount) each time the rule matches an element.
--> `content: counter(name)` -- inserts the counter's current value as generated text, typically inside a `::before`/`::after` pseudo-element.
--> This is exactly how browsers implement native `<ol>` numbering internally, and it's why author-defined counters can produce the same effect with full control over formatting/nesting that plain `<ol>` numbering doesn't offer.

# `@counter-style`

--> `@counter-style` defines an entirely custom numbering SCHEME (beyond the built-in `decimal`, `upper-roman`, `disc`, etc.) usable anywhere a counter style is expected -- `list-style-type` or the `counter()` function's second argument.

```css
@counter-style thumbs {
  system: cyclic;
  symbols: "👍";
  suffix: " ";
}

@counter-style custom-decimal {
  system: numeric;
  symbols: "0" "1" "2" "3" "4" "5" "6" "7" "8" "9";
  suffix: ") ";
}

ul.reactions {
  list-style: thumbs;
}

ol.custom-numbered li::before {
  content: counter(item, custom-decimal);
}
```

--> `system` -- how values map to symbols: `cyclic` (repeats the symbol list), `numeric` (place-value, like Arabic numerals), `alphabetic`, `fixed` (a finite list, falls back after running out), among others.
--> `symbols` -- the actual glyphs/characters used to represent counter values.
--> `suffix`/`prefix` -- text automatically appended/prepended to each generated counter value.
--> This is the standards-based, non-JavaScript way to get numbering styles (custom bullets, non-Latin numbering systems, emoji lists) that the built-in `list-style-type` keyword set doesn't cover.

# `text-wrap: balance` and `text-wrap: pretty`

--> Browsers have always wrapped text using a simple greedy line-breaking algorithm, which can leave an awkward single short word alone on the last line of a heading/paragraph ("orphans"). `text-wrap` lets CSS ask for smarter line-breaking, no manual `<br>` placement needed.

```css
h1, h2, h3 {
  text-wrap: balance;   /* Distributes text evenly across lines -- ideal for short headings */
}

p {
  text-wrap: pretty;   /* Avoids orphans/ragged last lines -- ideal for longer body paragraphs */
}
```

--> `balance` -- recalculates line breaks so that all lines in the block are as close to equal width as possible, best suited to short blocks of text like headings, pull-quotes, and card titles where a lopsided wrap looks visibly bad. Browsers typically cap `balance` to a small number of lines for performance reasons, so it's not intended for long-form paragraphs.
--> `pretty` -- applies a slower, higher-quality line-breaking algorithm aimed specifically at avoiding a single orphaned word on the final line, suited to longer paragraphs where full `balance` would be too expensive to compute.
--> Both are purely presentational, opt-in enhancements -- unsupported browsers simply fall back to normal greedy wrapping, no layout breakage either way.

# `content-visibility` and `will-change` -- Rendering Performance

--> `content-visibility: auto` -- tells the browser it's allowed to entirely SKIP rendering (layout, paint) an element's contents when that element is off-screen, re-rendering it only once it's about to become visible -- a massive win for very long pages (e.g. long articles, huge lists) where most content is never on screen at once.

```css
.article-section {
  content-visibility: auto;
  contain-intrinsic-size: 0 500px;   /* Placeholder size so scrollbar/layout doesn't jump once real size is known */
}
```

--> `contain-intrinsic-size` -- provides an estimated placeholder size for skipped content so the page doesn't jump around/mis-size its scrollbar before the browser has actually measured the real content.
--> `will-change: transform;` / `will-change: opacity;` -- a hint telling the browser a property is ABOUT to change repeatedly (e.g. right before a drag or complex animation begins), letting it proactively promote the element to its own compositor layer ahead of time rather than reacting mid-animation.
--> **`will-change` is not a general performance switch** -- applying it broadly (e.g. on many elements "just in case") consumes extra GPU memory for each promoted layer and can make performance WORSE than doing nothing; it should be set briefly, right before an expected change, and removed afterward (often via a class toggled in JavaScript) rather than left on permanently in a stylesheet.

# Scrollbar Styling

--> Historically scrollbar appearance was OS/browser-controlled with only vendor-prefixed, non-standard hooks (`::-webkit-scrollbar`). The `scrollbar-width` and `scrollbar-color` standard properties now offer basic cross-browser control, though full custom scrollbar UI still leans on the WebKit pseudo-elements.

```css
/* Standard properties -- supported in Firefox and Chromium */
.scroll-box {
  scrollbar-width: thin;              /* auto | thin | none */
  scrollbar-color: #888 #eee;         /* thumb color, track color */
}

/* WebKit-specific pseudo-elements -- needed for finer visual control in Chrome/Safari/Edge */
.scroll-box::-webkit-scrollbar {
  width: 10px;
}
.scroll-box::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 5px;
}
.scroll-box::-webkit-scrollbar-track {
  background: #eee;
}
```

--> `scrollbar-width: none` fully hides the scrollbar while scrolling still works -- use sparingly, since a hidden scrollbar removes an important visual affordance that content is scrollable.
--> Because coverage differs, a realistic cross-browser scrollbar theme sets BOTH the standard properties AND the `::-webkit-scrollbar*` pseudo-elements together, one styling the Firefox path, the other the Chromium/Safari path.

# CSS Masks vs. `clip-path`

--> `clip-path` (covered in the Filters, Blend Modes and Clip-Path file) hides everything OUTSIDE a hard-edged geometric shape -- there's no partial transparency, a pixel is either fully shown or fully hidden. `mask-image` instead uses the LUMINANCE/ALPHA of a separate image (or gradient) to control opacity pixel-by-pixel, enabling soft edges, fades, and arbitrarily complex shapes a `polygon()` could never express.

```css
.fade-out-edge {
  mask-image: linear-gradient(to bottom, black 70%, transparent 100%);
  /* Bottom 30% of the element smoothly fades to fully transparent */
}

.custom-shape-mask {
  mask-image: url("star-shape.svg");
  mask-size: contain;
  mask-repeat: no-repeat;
}
```

--> In a mask image, opaque/white areas show the underlying element's content, transparent/black areas hide it, and anything in between produces PARTIAL transparency -- something `clip-path`'s binary in/out shapes structurally cannot do.
--> **Choosing between them** -- reach for `clip-path` for crisp geometric cuts (hexagons, diagonal banners, circular avatars) where a hard edge is exactly what's wanted; reach for `mask-image` for soft fades, gradient-based reveals, or masking with an arbitrarily detailed image/SVG shape.

# `env()` for Safe-Area Insets

--> On devices with notches, rounded corners, or a home-indicator bar (modern phones), naive edge-to-edge layout can place real content behind hardware cutouts. `env()` exposes the OS-reported safe area as CSS values so layout can pad around them.

```css
.fullscreen-header {
  padding-top: env(safe-area-inset-top, 20px);
  padding-left: env(safe-area-inset-left, 0px);
  padding-right: env(safe-area-inset-right, 0px);
  padding-bottom: env(safe-area-inset-bottom, 0px);
}
```

--> `env(safe-area-inset-top|right|bottom|left, fallback)` -- each resolves to the exact inset needed to clear a hardware cutout on the current device, falling back to the given value on devices that don't report one (e.g. desktop browsers, older phones).
--> Requires `<meta name="viewport" content="viewport-fit=cover">` in the page's `<head>` -- without it, the browser doesn't extend the page content under the cutout area in the first place, so the safe-area values have nothing to compensate for.
--> Most relevant for installed PWAs and full-screen web apps (covered in the HTML5 APIs and Advanced Elements file's Service Worker/PWA section) where the page genuinely spans the entire physical screen, rather than sitting inside a browser chrome that already accounts for the notch itself.
