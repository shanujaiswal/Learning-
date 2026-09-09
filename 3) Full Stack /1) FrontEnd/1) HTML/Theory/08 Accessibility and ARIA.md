# Why Accessibility Matters

--> Accessibility (a11y) means a website works for users with disabilities -- visual (blind/low-vision, using screen readers), motor (can't use a mouse, navigating by keyboard only), auditory, or cognitive.
--> It's not a niche concern -- screen reader users, keyboard-only users, and users relying on browser zoom/high-contrast modes all depend on markup being written correctly, not just visually "looking right."

# Semantic HTML Is the First Accessibility Layer

--> Using the right element (`<button>` instead of a styled `<div>`, `<nav>` instead of a generic `<div>`) gives screen readers and keyboard navigation correct behavior for free -- a real `<button>` is automatically focusable and announced as "button" by a screen reader; a `<div>` styled to look like one is not, unless you manually re-implement all of that behavior yourself.

# alt Text for Images

--> Every meaningful `<img>` needs a descriptive `alt` attribute -- a screen reader reads this aloud in place of the image.
--> Decorative images (that add no information) should use `alt=""` (empty, not omitted) -- this tells screen readers to skip it entirely rather than announcing the filename.

```html
<img src="chart.png" alt="Quarterly revenue chart showing 20% growth in Q3">
<img src="decorative-divider.png" alt="">
```

# ARIA -- Filling the Gaps Semantic HTML Can't Cover

--> ARIA (Accessible Rich Internet Applications) attributes add accessibility information when semantic HTML alone isn't enough -- most commonly needed for custom interactive widgets (a custom dropdown, a modal, a tab panel) that don't have a native HTML equivalent.
--> First rule of ARIA -- don't use ARIA if a native HTML element already does the job; ARIA only describes behavior, it doesn't implement it (adding `role="button"` to a `<div>` doesn't make it keyboard-clickable, YOU still have to wire that up).

```html
<div role="tablist">
  <button role="tab" aria-selected="true" id="tab1">Profile</button>
  <button role="tab" aria-selected="false" id="tab2">Settings</button>
</div>
<div role="tabpanel" aria-labelledby="tab1">Profile content...</div>
```

--> `aria-label` -- provides an accessible name when there's no visible text (an icon-only button).
--> `aria-hidden="true"` -- hides purely decorative content from screen readers while keeping it visually visible.
--> `aria-live="polite"` -- announces dynamically updated content (a toast notification, a live search result count) to screen reader users without needing a page reload.

# Keyboard Navigation

--> Every interactive element must be reachable and operable using only the Tab key (to move focus) and Enter/Space (to activate) -- mouse-only interactions (e.g. a click handler with no keyboard equivalent) lock out keyboard-only users entirely.
--> `tabindex="0"` -- makes a non-interactive element (like a `<div>`) focusable in natural tab order. `tabindex="-1"` -- removes it from tab order while still allowing it to be focused programmatically. Positive values (`tabindex="1"`) are almost always a mistake -- they override natural document order and create confusing navigation.
--> Visible focus indicators (the outline that appears around a focused element) should never be removed with `outline: none` without providing a clear visual replacement -- doing so makes keyboard navigation unusable, even though it looks "cleaner."

# Color Contrast

--> WCAG (Web Content Accessibility Guidelines) defines minimum contrast ratios between text and background (commonly 4.5:1 for normal text) so low-vision users can actually read content.
--> Tools like the browser's DevTools contrast checker or WebAIM's Contrast Checker validate a color pairing against these thresholds before shipping a design.

# Testing Accessibility

--> Automated tools (axe, Lighthouse's Accessibility audit) catch a meaningful subset of issues (missing alt text, insufficient contrast, missing form labels) but can't catch everything -- logical reading order and genuine usability still need manual keyboard-only and screen-reader testing.
