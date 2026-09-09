# Responsive Web Design

--> Responsive design makes a page look good and function well on all screen sizes (mobile, tablet, desktop).
--> Requires the viewport meta tag in HTML: `<meta name="viewport" content="width=device-width, initial-scale=1.0">`.

# Media Queries

--> `@media` — applies CSS rules only when certain conditions (usually screen width) are met.

```css
/* Mobile-first: base styles apply to all sizes, then override for larger screens */
.container {
  display: block;
}

@media (min-width: 768px) {
  .container {
    display: flex;
  }
}
```

--> `min-width` — applies styles from that width upward (mobile-first approach — recommended).
--> `max-width` — applies styles up to that width (desktop-first approach).
--> Can combine conditions: `@media (min-width: 600px) and (max-width: 900px) { ... }`
--> `orientation: portrait | landscape` — target based on device orientation.
--> `prefers-color-scheme: dark | light` — detect the user's OS-level theme preference.
--> `prefers-reduced-motion: reduce` — detect when the user has asked the OS to minimize animations; wrap non-essential animations/transitions in this query.
--> `prefers-contrast: more | less | no-preference` — detect the user's OS-level contrast preference.
--> Note: media queries only respond to the viewport; for styling based on a containing element's own size, see Container Queries (`@container`) in file 10.

# Common Breakpoints (general convention, not fixed rules)

--> Mobile: up to ~576px
--> Tablet: ~768px
--> Laptop/Desktop: ~992px
--> Large desktop: ~1200px+

# Fluid/Flexible Units for Responsiveness

--> Use `%`, `vw`/`vh`, `rem`/`em` instead of fixed `px` where layout should scale.
--> `max-width: 100%;` on images prevents them from overflowing their container.
--> `clamp(min, preferred, max)` — a single value that scales fluidly between a min and max bound, e.g. `font-size: clamp(1rem, 2vw, 2rem);`
--> `aspect-ratio` — sets a preferred width-to-height ratio so the box maintains proportions without JS, e.g. `aspect-ratio: 16 / 9;` on a video/image container.

# Responsive Layout Techniques

--> Flexbox with `flex-wrap: wrap;` — items reflow onto new lines as space shrinks.
--> Grid with `repeat(auto-fit, minmax(200px, 1fr))` — automatically responsive column count.
--> Hide/show elements at breakpoints using `display: none;` inside media queries.

# Mobile-First vs Desktop-First

--> Mobile-first — write base styles for small screens, then add complexity with `min-width` media queries as screens grow. Generally preferred (simpler base case, progressive enhancement).
--> Desktop-first — write base styles for large screens, then simplify with `max-width` media queries as screens shrink.

# Print Styles

--> `@media print { ... }` — applies styles only when the page is printed (or exported to PDF via print).
--> Common pattern: hide navigation/buttons/ads that make no sense on paper — `nav, .no-print { display: none; }`.
--> Can also force elements to always show for print, e.g. printing a link's URL after it: `a::after { content: " (" attr(href) ")"; }`.

# Accessibility (a11y) in CSS

--> Accessibility isn't only a JS/HTML concern — several CSS techniques directly affect whether the site is usable for people relying on screen readers, keyboards, or assistive tech.

==> Visually Hidden (but Screen-Reader Accessible) Content
--> `display: none;` / `visibility: hidden;` hide content from EVERYONE, including screen readers.
--> To hide something visually while keeping it announced by screen readers (e.g. extra context for a link, a skip-link before it becomes focused), use the "visually-hidden" pattern instead:

```css
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
```

==> Skip Links
--> A "Skip to main content" link is placed first in the DOM, hidden by default, and becomes visible (using `:focus`) when a keyboard user tabs to it — letting them bypass repeated navigation.

```css
.skip-link {
  position: absolute;
  top: -40px;
  left: 0;
}
.skip-link:focus {
  top: 0;   /* Becomes visible when focused via Tab key */
}
```

==> Focus Indicators
--> Never remove `outline` on `:focus` without providing an equally visible replacement — see `:focus-visible` in file 02 for the modern approach that keeps a clean look for mouse users while preserving the indicator for keyboard users.

==> Color Contrast
--> Text should meet a minimum contrast ratio against its background (WCAG AA: 4.5:1 for normal text, 3:1 for large text) — don't rely on color alone to convey meaning (e.g. error states should also use an icon/text label, not just red text).

==> Reduced Motion
--> Already covered via `prefers-reduced-motion` above — always wrap non-essential animations in this query so users who get motion sickness/vestibular issues aren't forced to experience them.
