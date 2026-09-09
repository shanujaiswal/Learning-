# View Transitions API

--> `document.startViewTransition(callback)` -- lets the browser automatically animate between two DOM states (old vs. new) with a smooth cross-fade/morph, instead of a hard cut -- no manual FLIP-technique JavaScript animation code required.

```javascript
function updateContent(newHTML) {
  if (!document.startViewTransition) {
    document.body.innerHTML = newHTML;   // Fallback for browsers without support
    return;
  }
  document.startViewTransition(() => {
    document.body.innerHTML = newHTML;   // The actual DOM mutation
  });
}
```

--> The browser snapshots the page BEFORE the callback runs, lets the callback mutate the DOM, then snapshots the page AFTER -- and cross-fades between the two snapshots automatically.

# ::view-transition-* Pseudo-Elements

--> The transition is exposed as a small pseudo-element tree that CSS can target directly, enabling custom animations rather than the default cross-fade:

```css
::view-transition-old(root),
::view-transition-new(root) {
  animation-duration: 0.4s;
}

/* Give an element its own named transition so it animates independently of the rest of the page */
.hero-image {
  view-transition-name: hero;
}

::view-transition-old(hero),
::view-transition-new(hero) {
  animation: none;
  mix-blend-mode: normal;
}
```

--> `view-transition-name` (a CSS property set on an element) opts that specific element into its OWN named transition -- e.g. a product thumbnail that visually "flies" and morphs into the full product image on a detail page, a pattern popularized by native app transitions and now achievable in plain CSS/JS.
--> Same-document transitions (SPA-style, shown above) are broadly supported; cross-document transitions (a full MPA navigation between two separate pages) use the same pseudo-elements but are opted into via `@view-transition { navigation: auto; }` in each page's CSS.

# Resource Hints

--> `<link rel="...">` values that tell the browser to do networking work AHEAD of when a resource is actually needed, trading a little bandwidth for a faster perceived load.

```html
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="dns-prefetch" href="https://fonts.gstatic.com">
<link rel="preload" href="/fonts/main.woff2" as="font" type="font/woff2" crossorigin>
<link rel="prefetch" href="/next-page.html">
<link rel="modulepreload" href="/scripts/app.js">
```

--> `preconnect` -- opens the DNS lookup + TCP handshake + TLS negotiation for a third-party origin early, so the actual request (e.g. a font file, an API call) that follows doesn't pay that latency cost.
--> `dns-prefetch` -- a cheaper, older fallback that resolves ONLY the DNS lookup (not the full connection) -- useful as a broader safety net for many origins since it costs almost nothing, whereas `preconnect` should be reserved for a handful of origins you're sure you'll use (each open connection has a real cost).
--> `preload` -- forces the browser to fetch a resource this page WILL definitely need very soon (e.g. a hero font or above-the-fold image) at a high priority, without waiting for the CSS/JS that would normally trigger the request. Requires a correct `as` value (`font`, `style`, `script`, `image`...) so the browser applies the right priority and request headers.
--> `prefetch` -- a low-priority hint for a resource the NEXT navigation will likely need (e.g. the page a "Next" button links to) -- fetched opportunistically when the browser is idle, unlike `preload`'s urgency.
--> `modulepreload` -- like `preload` but specifically for ES modules, additionally letting the browser parse and fetch the module's own `import` dependency graph ahead of time.
--> **Overusing hints backfires** -- preloading/preconnecting to resources the page doesn't actually need soon competes for the same limited early bandwidth/connection budget as the resources that genuinely matter, slowing the page down instead of speeding it up.

# hreflang and International SEO Link Annotations

--> `hreflang` on a `<link rel="alternate">` tells search engines "this URL is the equivalent of this page, but in language/region X" -- letting a search engine serve a French visitor the French version of a page directly in results, instead of the version that happened to rank.

```html
<link rel="alternate" hreflang="en-us" href="https://example.com/en-us/pricing">
<link rel="alternate" hreflang="en-gb" href="https://example.com/en-gb/pricing">
<link rel="alternate" hreflang="fr" href="https://example.com/fr/pricing">
<link rel="alternate" hreflang="x-default" href="https://example.com/pricing">
```

--> Format is `language` (ISO 639-1, e.g. `fr`) optionally combined with `-REGION` (ISO 3166-1, e.g. `en-GB`) -- region narrows a language variant, it isn't valid alone (no region-only value like `-us`).
--> `x-default` -- a special value marking the fallback URL to show when none of the other listed languages/regions match the visitor.
--> **Must be reciprocal** -- every page in the set should link to every other page in the set (including itself) with matching `hreflang` values, or search engines may ignore the annotations entirely. This sits alongside the JSON-LD/Open Graph metadata (covered in the SEO Meta Tags file) as part of a page's full metadata surface.

# Declarative Shadow DOM

--> Ordinarily, Shadow DOM (covered in the Web Components file) can only be attached via JavaScript's `attachShadow()` -- meaning server-rendered HTML has no shadow content until JS runs, hurting first-paint content and breaking JS-disabled scenarios. Declarative Shadow DOM lets a `<template>` attach itself as shadow root directly from static HTML/server-rendered markup, no JavaScript required.

```html
<user-card>
  <template shadowrootmode="open">
    <style>.card { border: 1px solid #ccc; padding: 8px; }</style>
    <div class="card"><slot></slot></div>
  </template>
  <span slot="name">Alice</span>
</user-card>
```

--> The browser's HTML parser recognizes `<template shadowrootmode="open">` as a special instruction and converts it into a real, attached shadow root the moment it's parsed -- streamed server-rendered HTML gets working encapsulated components before any JavaScript executes at all.
--> `shadowrootmode` accepts `open` or `closed`, mirroring the two modes of the JS `attachShadow({ mode })` call.
--> A custom element's own JavaScript class (if defined) still runs its lifecycle callbacks afterward -- Declarative Shadow DOM handles the initial markup/encapsulation, custom element JS still handles behavior.

# Customized Built-in Elements (`is="..."`)

--> Alongside "autonomous" custom elements (`<user-card>`, an entirely new tag), the Custom Elements spec also supports extending an EXISTING built-in element's behavior while keeping its native tag and all its built-in semantics/accessibility.

```javascript
class FancyButton extends HTMLButtonElement {
  connectedCallback() {
    this.addEventListener("click", () => this.classList.add("clicked"));
  }
}
customElements.define("fancy-button", FancyButton, { extends: "button" });
```

```html
<button is="fancy-button">Click me</button>
```

--> The class must extend the specific native element's interface (`HTMLButtonElement`, not the generic `HTMLElement`) and the `is` attribute is what actually upgrades the element in HTML.
--> **Why this matters** -- a `<button is="fancy-button">` remains a REAL `<button>` for every purpose that matters: forms submit it correctly, screen readers announce it as a button, keyboard activation (Space/Enter) works automatically. An autonomous `<fancy-button>` custom element gets none of that native behavior for free -- you'd have to reimplement focus handling, keyboard activation, and ARIA roles manually.
--> Browser support is notably uneven -- Safari has never implemented customized built-ins, which has limited real-world adoption despite the spec advantage over autonomous elements for extending native semantics.

# The Missing `adoptedCallback` in Custom Elements Lifecycle

--> The Custom Elements lifecycle (covered in the Web Components file) actually has FOUR callbacks, not just the commonly-shown three:

--> `connectedCallback()` -- runs each time the element is inserted into a document.
--> `disconnectedCallback()` -- runs each time the element is removed from a document.
--> `attributeChangedCallback()` -- runs when an observed attribute changes.
--> `adoptedCallback(oldDocument, newDocument)` -- runs when the element is moved into a DIFFERENT document via `document.adoptNode()`, e.g. moving a node between the main document and an `<iframe>`'s document, or into content created by `document.implementation.createHTMLDocument()`.

```javascript
class UserCard extends HTMLElement {
  adoptedCallback(oldDocument, newDocument) {
    // Re-attach document-specific resources (e.g. stylesheets scoped to newDocument)
    console.log("Moved to a new document context");
  }
}
```

--> This is by far the rarest of the four to actually fire in real applications -- most apps never move nodes across documents -- but it exists precisely because `connectedCallback`/`disconnectedCallback` alone can't distinguish "removed and immediately re-inserted in the SAME document" from "moved to a genuinely different document," which matters if a component holds document-specific state (event listeners bound to `document`, stylesheet references).

# Popover API

--> A native, browser-managed way to show "popover" UI (tooltips, menus, non-modal dialogs) that automatically gets top-layer rendering (no z-index fights), light-dismiss (clicking outside closes it), and Escape-to-close -- all without any JavaScript positioning/state logic.

```html
<button popovertarget="info-popover">More info</button>
<div id="info-popover" popover>
  This is extra information shown as a native popover.
</div>
```

--> `popover` attribute -- marks an element as a popover; it's hidden by default (`display: none` equivalent) until explicitly shown.
--> `popovertarget="id"` on a button -- wires the button up to toggle that popover open/closed with zero JavaScript.
--> `popovertargetaction="show" | "hide" | "toggle"` -- optionally forces a specific action instead of the default toggle behavior.
--> `popover="auto"` (the default) -- only one auto popover can be open at a time, and light-dismiss/Escape work automatically. `popover="manual"` -- multiple can be open simultaneously, and the page's own JavaScript is responsible for closing them (via `.showPopover()`/`.hidePopover()`/`.togglePopover()`).
--> Popovers render in the browser's "top layer," the same rendering layer used by `<dialog>`'s `showModal()` -- guaranteeing they visually sit above everything else on the page regardless of `z-index` or `overflow: hidden` on ancestors, a problem CSS alone has never fully solved.

# `<colgroup>` / `<col>` -- Styling Table Columns

--> `<colgroup>`/`<col>` let you apply styles to an entire table COLUMN without repeating a class on every `<td>` in that column -- something plain CSS selectors can't target directly (there is no `nth-column` combinator).

```html
<table>
  <colgroup>
    <col style="background: #f5f5f5;">
    <col span="2" style="background: #fff;">
  </colgroup>
  <tr><th>Name</th><th>Q1</th><th>Q2</th></tr>
  <tr><td>Revenue</td><td>10k</td><td>12k</td></tr>
</table>
```

--> `span` attribute on `<col>` -- applies the same styling to multiple consecutive columns without writing one `<col>` per column.
--> Only a small subset of CSS properties actually apply to `<col>`/`<colgroup>` in practice (mainly `background`, `border`, `visibility: collapse`, and width-related properties) -- most other properties are simply ignored on these elements, a common source of confusion.

# `<select>` optgroup and Multiple Select

--> `<optgroup label="...">` -- visually groups related `<option>`s under a bold, non-selectable label inside a dropdown, purely for organization.

```html
<select name="animal">
  <optgroup label="Mammals">
    <option value="dog">Dog</option>
    <option value="cat">Cat</option>
  </optgroup>
  <optgroup label="Birds">
    <option value="parrot">Parrot</option>
  </optgroup>
</select>
```

--> `<select multiple>` -- turns the control into a scrolling list allowing more than one selection at once (Ctrl/Cmd+click, or Shift+click for a range), instead of the default single-choice dropdown.

```html
<select name="toppings" multiple size="4">
  <option value="cheese">Cheese</option>
  <option value="olives">Olives</option>
  <option value="mushrooms">Mushrooms</option>
</select>
```

--> With `multiple`, the submitted form data includes one name/value pair per selected option -- server-side code must read it as an array/list (e.g. `toppings[]` conventions in some frameworks), not a single value.
--> `size` attribute -- controls how many options are visible at once without scrolling; without `multiple`, `size` greater than 1 turns even a single-choice select into a visible scrolling list rather than a dropdown.

# Per-Button Form Overrides

--> A single `<form>` can have multiple submit buttons that each override the form's own submission behavior -- useful for "Save as Draft" vs. "Publish" buttons that must hit different endpoints from the same form.

```html
<form action="/submit-normal" method="post">
  <input type="text" name="title">
  <button type="submit">Save (default endpoint)</button>
  <button type="submit" formaction="/save-draft" formmethod="post">Save as Draft</button>
  <button type="submit" formaction="/upload" formenctype="multipart/form-data">Upload</button>
  <button type="submit" formnovalidate>Save Without Validating</button>
</form>
```

--> `formaction` -- overrides the form's `action` URL for just this button's click.
--> `formmethod` -- overrides the form's `method` (`get`/`post`) for just this button.
--> `formenctype` -- overrides the form's encoding type (e.g. switching to `multipart/form-data` for a button that triggers a file upload flow, while the form's default stays `application/x-www-form-urlencoded`).
--> `formnovalidate` -- skips the browser's native validation attributes (covered in the Advanced Form Validation file) for just this button's click, e.g. letting a "Save as Draft" button bypass `required` fields that only matter for final "Publish."
--> `formtarget` -- overrides the form's `target` (e.g. opening just this submission's result in a new tab).

# The `autofocus` Attribute

--> `autofocus` -- automatically moves keyboard focus to an element as soon as the page (or a newly shown `<dialog>`) finishes loading, without JavaScript.

```html
<input type="text" name="search" autofocus>
```

--> **Accessibility caveat** -- autofocus is disorienting for screen reader users who expect to start at the top of the page/document structure, and it can cause an unexpected page jump/scroll on load. It should be used sparingly -- typically only in a genuinely single-purpose view like a search page or a freshly opened `<dialog>`/modal, never on a page with substantial content above the focused field.
--> Only ONE element with `autofocus` should exist per page load -- if multiple elements have it, only the first one in document order actually receives focus.

# ValidityState API in Detail

--> Every validity-constrained form field exposes `element.validity`, a `ValidityState` object with multiple boolean flags describing EXACTLY which constraint failed -- letting you show a specific, tailored error message instead of a generic "invalid" state.

```javascript
const field = document.getElementById("username");

field.addEventListener("invalid", () => {
  const v = field.validity;
  if (v.valueMissing) field.setCustomValidity("Username is required.");
  else if (v.tooShort) field.setCustomValidity("Username is too short.");
  else if (v.patternMismatch) field.setCustomValidity("Only letters, numbers, and underscores allowed.");
  else field.setCustomValidity("");
});
```

--> `valueMissing` -- a `required` field is empty.
--> `patternMismatch` -- the value doesn't match the `pattern` regular expression.
--> `rangeOverflow` / `rangeUnderflow` -- a numeric/date value is above `max` / below `min`.
--> `tooLong` / `tooShort` -- the value violates `maxlength` / `minlength`.
--> `typeMismatch` -- the value doesn't match the expected format for its `type` (e.g. not a valid email for `type="email"`).
--> `stepMismatch` -- the value doesn't align with the `step` increment.
--> `badInput` -- the browser couldn't even parse the input into the expected data type (e.g. non-numeric text typed into a `type="number"` field that the browser couldn't sanitize).
--> `valid` -- `true` only when every other flag above is `false`, i.e. the field currently satisfies all of its constraints.
--> This is strictly more granular than the plain `:invalid` CSS pseudo-class (covered in the Advanced Form Validation file) -- `:invalid` only tells you SOMETHING is wrong, `ValidityState` tells you exactly WHAT.

# Content-Security-Policy via `<meta>` Tag

--> A Content-Security-Policy can be delivered either as an HTTP response header OR embedded directly in the HTML via a `<meta>` tag -- useful when you don't control server headers (e.g. static hosting) but still want to restrict where scripts/styles/images can load from.

```html
<meta http-equiv="Content-Security-Policy"
      content="default-src 'self'; script-src 'self' https://trusted-cdn.example.com; img-src * data:; style-src 'self' 'unsafe-inline';">
```

--> `default-src 'self'` -- baseline: only allow resources from the page's own origin unless a more specific directive overrides it.
--> `script-src` / `style-src` / `img-src` -- narrow the allowed sources per resource type.
--> **Meta-tag CSP limitations** -- it cannot use `frame-ancestors`, `report-uri`, or `sandbox` directives (these only work as an HTTP header), and it only takes effect once the parser reaches that `<meta>` tag -- any resource requested by markup ABOVE it in the document is not covered. A real HTTP header applies from the very first byte and supports the full directive set, so the header is preferred whenever the server can set it.

# Deeper `<picture>`/`srcset`/`sizes` Coverage

--> Responsive images (briefly introduced in the HTML5 APIs file) actually solve two DIFFERENT problems, and mixing them up is the most common source of confusion.

--> **Resolution switching** -- the same IMAGE, at different pixel densities/sizes, letting the browser pick the most efficient file for the current screen. Uses `srcset` + `sizes` on a plain `<img>`, no `<picture>` needed.

```html
<img
  src="photo-800.jpg"
  srcset="photo-400.jpg 400w, photo-800.jpg 800w, photo-1200.jpg 1200w"
  sizes="(min-width: 900px) 800px, 100vw"
  alt="A mountain landscape">
```

--> `srcset` lists candidate files with their intrinsic width in `w` units (not a viewport breakpoint). `sizes` tells the browser how wide the image will actually be DISPLAYED at various viewport widths, so it can combine that with device pixel ratio to pick the best-matching candidate from `srcset` -- the browser decides, the page never forces a specific file.

--> **Art direction** -- genuinely DIFFERENT crops/compositions for different contexts (e.g. a tightly-cropped square image on mobile vs. a wide landscape crop on desktop) -- this requires `<picture>` with multiple `<source media="...">` elements, because the goal isn't just efficiency, it's showing different content.

```html
<picture>
  <source media="(max-width: 600px)" srcset="portrait-crop.jpg">
  <source media="(min-width: 601px)" srcset="landscape-crop.jpg">
  <img src="landscape-crop.jpg" alt="Team photo">
</picture>
```

--> **The rule of thumb** -- if the image content is conceptually the SAME picture just served more/less efficiently, use `srcset`/`sizes` on `<img>` (resolution switching). If the image needs to show a DIFFERENT crop/composition/subject depending on context, use `<picture>` with `<source media>` (art direction). The two techniques can combine: each `<source>` inside a `<picture>` can itself carry its own `srcset`/`sizes` for resolution switching within that art-directed variant.
--> The final `<img>` inside `<picture>` is mandatory and acts as the fallback for browsers that don't support `<picture>`/`<source>` at all -- it's also what actually renders and gets its ARIA/`alt` treatment; the `<source>` elements themselves are invisible to accessibility tooling.

# Named Slots, Fallback Content, `::slotted()`, `:host`, `part`/`exportparts`

--> Building on the basic single `<slot>` covered in the Web Components file, real components typically need MULTIPLE distinct insertion points, default content when nothing is provided, and a way for outside CSS to reach in past the Shadow DOM boundary in a controlled way.

```html
<template id="card-template">
  <style>
    :host { display: block; border: 1px solid #ccc; padding: 12px; }
    :host([featured]) { border-color: gold; }
    ::slotted(img) { border-radius: 50%; }
    .title-slot::slotted(*) { font-weight: bold; }
  </style>
  <slot name="avatar"></slot>
  <slot name="title" class="title-slot">Untitled Card</slot>
  <slot part="body"></slot>
</template>
```

```html
<user-card featured>
  <img slot="avatar" src="alice.jpg">
  <span slot="title">Alice</span>
  <p>Some body content projected into the default (unnamed) slot.</p>
</user-card>
```

--> **Named slots** -- a `<slot name="avatar">` inside Shadow DOM only receives light-DOM children marked with a matching `slot="avatar"` attribute; an unnamed `<slot>` catches everything else (the "default slot"). This lets one component define several distinct content regions instead of just one blob of projected children.
--> **Fallback content** -- any markup written directly INSIDE a `<slot>` tag (e.g. `Untitled Card` above) renders only when no light-DOM content is actually assigned to that slot -- a built-in default, no JavaScript check required.
--> **`::slotted(selector)`** -- the only way Shadow DOM CSS can style projected (light-DOM) content, and only top-level slotted elements are reachable, not their descendants (`::slotted(img)` works, `::slotted(div img)` does not).
--> **`:host`** -- inside Shadow DOM CSS, selects the custom element itself from the inside (styling the host element's own box, e.g. its border/display) -- `:host([featured])` further scopes that to only when the host carries a given attribute, letting a component style itself differently based on attributes the CONSUMER sets on the outer tag.
--> **`part`/`exportparts`** -- an escape hatch for the OUTSIDE page to style specific internal shadow elements, deliberately, without breaking full encapsulation. The component author opts an internal element in with `part="body"`; the consuming page can then target it from ordinary page CSS with `::part(body) { ... }`. `exportparts` lets a wrapper component re-expose an inner nested component's parts as if they were its own, so styling hooks can pass through multiple layers of composed custom elements.

```css
/* From the OUTER page's stylesheet, not inside the component's Shadow DOM */
user-card::part(body) {
  color: #333;
}
```

--> Together, `::slotted()`, `:host`, and `part`/`exportparts` are the deliberate, narrow set of holes the Shadow DOM encapsulation model (covered in the Web Components file) allows -- full styling control stays inside the component, while a small, author-approved surface remains stylable from outside.
