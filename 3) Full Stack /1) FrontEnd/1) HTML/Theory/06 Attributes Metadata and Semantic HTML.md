# HTML Attributes

--> Attributes provide additional information about an HTML element.
--> Usually come in a name/value pair like `name="value"`.
--> Attributes are always set in the start/opening tag.

# Global Attributes (usable on almost any element)

--> `id` — a unique identifier for one element (1 page, 1 time repeat).
--> `class` — assigns one or more class names for CSS/JS targeting (1 page, unlimited repeat).
--> `style` — inline CSS applied directly to the element.
--> `title` — extra info shown as a tooltip on hover.
--> `data-*` — custom data attributes to store extra information (`data-id="123"`), readable via JS `dataset`.
--> `tabindex` — controls the tab order for keyboard navigation.
--> `contenteditable` — makes an element's content directly editable by the user.
--> `draggable` — makes an element draggable (used with the HTML Drag and Drop API).
--> `hidden` — hides the element (roughly equivalent to `display: none`, but not always exact — a CSS rule with higher specificity, e.g. `display: block`, can override it, and focus/edge-case behavior can differ from `display: none`).
--> `lang` — declares the language of the element's content.

# CSS Selectors Recap (class vs id)

--> `.a` → class → can repeat unlimited times on a page → targeted with `.classname` in CSS.
--> `#a` → id → should appear only once per page → targeted with `#idname` in CSS.

# Metadata (head-level attributes/tags)

--> `<meta name="description" content="...">` — page description used by search engines.
--> `<meta name="keywords" content="...">` — keywords for SEO (largely ignored by modern search engines).
--> `<meta name="author" content="...">` — author of the document.
--> `<meta http-equiv="refresh" content="30">` — auto-refresh the page every N seconds.
--> `<meta charset="UTF-8">` — character encoding.
--> `<meta name="viewport" content="width=device-width, initial-scale=1.0">` — responsive scaling on mobile.

# Accessibility Attributes (ARIA)

--> `aria-label` — provides an accessible name for an element when visible text isn't enough.
--> `aria-hidden="true"` — hides an element from assistive technology (but not visually).
--> `role` — defines the role of an element for assistive technology (e.g. `role="button"`).
--> `aria-expanded="true|false"` — tells assistive tech whether a collapsible element (dropdown, accordion) is currently expanded.
--> `aria-live="polite|assertive"` — marks a region whose content updates dynamically, so screen readers announce changes.
--> Semantic tags (`<nav>`, `<header>`, `<main>`, etc.) reduce the need for extra ARIA roles.

# ARIA Landmark Roles

--> Landmark roles mark out the major regions of a page so screen-reader users can jump directly between them (similar to how sighted users visually scan a layout).
--> Most are already implied by semantic HTML5 tags, so explicit `role` attributes are only needed on generic `<div>`s or for older markup:
    - `role="banner"` — implied by `<header>` (site header, only one per page).
    - `role="navigation"` — implied by `<nav>`.
    - `role="main"` — implied by `<main>`.
    - `role="complementary"` — implied by `<aside>`.
    - `role="contentinfo"` — implied by `<footer>`.
    - `role="search"` — for a search form region (no implicit HTML5 equivalent, so needs to be set explicitly).
    - `role="dialog"` / `role="alertdialog"` — for custom modal implementations (native `<dialog>` handles this automatically).

# Keyboard Navigation and Focus Management

--> Every interactive element (links, buttons, inputs) must be reachable and operable using ONLY the keyboard (Tab, Shift+Tab, Enter, Space, Arrow keys) — a core accessibility requirement.
--> `tabindex="0"` — adds a normally non-focusable element (like a `<div>`) into the natural tab order.
--> `tabindex="-1"` — makes an element focusable programmatically (via JS `.focus()`) but removes it from the natural Tab-key order — useful for moving focus to an error message or newly opened panel.
--> `tabindex` with a positive number (e.g. `tabindex="1"`) forces a custom tab order — generally discouraged, since it's easy to create a confusing, hard-to-maintain order.
--> A visible focus indicator (outline) must never be removed (`outline: none`) without providing an equally visible custom replacement — keyboard users rely on it to know where they are.
--> "Skip to main content" links (a hidden link that becomes visible on focus, jumping past repeated navigation) are a common pattern for keyboard/screen-reader users.

# Structured Data (JSON-LD / schema.org)

--> Structured data is machine-readable metadata embedded in a page that helps search engines understand its content precisely (e.g. this is a Recipe, this is a Product with a price, this is an Article with an author) — can enable rich search results (star ratings, prices, breadcrumbs).
--> JSON-LD (JSON for Linked Data) is the format Google recommends: a `<script type="application/ld+json">` block placed anywhere in the page (usually `<head>`), following the vocabulary defined at schema.org.

```html
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "Article",
  "headline": "How to Learn HTML",
  "author": {
    "@type": "Person",
    "name": "Shanu Jaiswal"
  },
  "datePublished": "2026-01-15"
}
</script>
```

# Boolean Attributes

--> Attributes like `required`, `disabled`, `checked`, `readonly`, `autoplay`, `multiple` don't need a value — their mere presence sets them to true.

# SEO and Social Sharing Meta Tags

--> `<link rel="canonical" href="url">` — tells search engines the "master" URL when the same content is reachable at multiple URLs, avoiding duplicate-content penalties.
--> Open Graph tags (used by Facebook, LinkedIn, WhatsApp, etc. to render link previews):
    - `<meta property="og:title" content="...">` — title shown in the preview card.
    - `<meta property="og:description" content="...">` — description shown in the preview card.
    - `<meta property="og:image" content="url">` — thumbnail image shown in the preview card.
    - `<meta property="og:url" content="url">` — canonical URL for the shared content.
--> Twitter Card tags (`<meta name="twitter:card" content="summary_large_image">`) — similar purpose, specific to X/Twitter's preview rendering.
--> `<link rel="icon">` / `<link rel="apple-touch-icon">` — favicon and iOS home-screen icon.
