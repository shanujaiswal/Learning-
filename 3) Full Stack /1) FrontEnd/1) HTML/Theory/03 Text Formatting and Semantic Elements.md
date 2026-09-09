# Headings and Paragraphs

--> `<h1>` to `<h6>` define headings, `<h1>` being the most important/largest.
--> `<p>` defines a paragraph of text.
--> Browsers automatically add some margin before/after headings and paragraphs.
--> Don't skip heading levels (e.g. `<h1>` straight to `<h3>`) — keep the outline sequential for accessibility/screen readers and a proper document outline.

# Text Formatting Tags

--> `<b>` — bold text (no extra importance, purely visual).
--> `<strong>` — bold text with semantic importance (screen readers emphasize it).
--> `<i>` — italic text (visual only).
--> `<em>` — italic text with semantic emphasis.
--> `<mark>` — highlighted/marked text.
--> `<small>` — smaller text.
--> `<del>` — strikethrough (deleted) text.
--> `<ins>` — underlined (inserted) text.
--> `<sub>` / `<sup>` — subscript / superscript text.
--> `<br>` — line break (empty element).
--> `<hr>` — horizontal rule / thematic break (empty element).
--> `<time datetime="2026-08-05">` — represents a date/time in a machine-readable format, human-readable text shown to the user.
--> `<code>` — inline snippet of computer code (monospace font).
--> `<kbd>` — represents keyboard input (e.g. `<kbd>Ctrl</kbd>+<kbd>C</kbd>`).
--> `<samp>` — represents sample output from a program/system.
--> `<address>` — represents contact information for the author/owner of a document or article (physical address, email, phone, social links); browsers typically render its content in italics.

# Semantic Elements

--> Semantic elements clearly describe their meaning to both the browser and the developer (unlike `<div>`/`<span>` which are generic containers).
--> `<header>` — introductory content/navigation for a page or section.
--> `<nav>` — a block of navigation links.
--> `<section>` — defines a section in a document; a semantic element that helps with SEO and accessibility.
    - SEO --> Search Engine Optimization.
--> `<article>` — independent, self-contained content (blog post, news article, card).
--> `<aside>` — content aside from the main content (sidebar, related links).
--> `<footer>` — footer for a page or section (author, copyright, links).
--> `<figure>` + `<figcaption>` — self-contained media (image/diagram) with a caption.

# Why Use Semantic HTML

--> Improves accessibility — screen readers use these tags to navigate the page structure.
--> Improves SEO — search engines weigh semantic structure when indexing content.
--> Improves readability/maintainability of the code for other developers.

# Generic Containers

--> `<div>` — block-level generic container, used for grouping/styling.
--> `<span>` — inline generic container, used for styling small pieces of text within a line.

# Quotes and Citations

--> `<blockquote cite="url">` — a long quotation (browsers usually indent it).
--> `<q>` — a short inline quotation (browsers add quotation marks automatically).
--> `<cite>` — the title of a creative work being referenced.
--> `<abbr title="...">` — an abbreviation/acronym; the `title` shows a tooltip with the full form.

# Lists as Text Structuring

--> `<ul>` — unordered (bulleted) list.
--> `<ol>` — ordered (numbered) list.
--> `<li>` — list item, used inside `<ul>` or `<ol>`.
--> `<dl>`, `<dt>`, `<dd>` — description list, term, and description.

# Semantic Elements Deep Dive — What Each One Actually Means

--> A semantic element communicates its MEANING/PURPOSE to both the browser and any tool parsing the page (a screen reader, a search engine crawler, another developer reading the code) — `<article>` says "this is a self-contained piece of content," while a generic `<div>` says nothing at all beyond "this is a box." The visual rendering can be made IDENTICAL with CSS regardless of which tag you choose — semantics is entirely about the underlying MEANING, independent of appearance.

--> **`<header>`** — introductory content for its nearest ancestor sectioning element. A page can have ONE top-level `<header>` (site branding/nav) AND additional `<header>` elements nested inside individual `<article>`/`<section>` elements (e.g. a blog post's own title/date header) — a genuinely common point of confusion, since `<header>` is NOT restricted to appearing only once per page.
--> **`<nav>`** — specifically marks a block of NAVIGATION links. Not every group of links needs `<nav>` (a list of tags in a blog post's footer usually doesn't warrant it), reserved for MAJOR navigation blocks (primary site nav, a table of contents, pagination controls) — screen readers let users jump directly between `<nav>` landmarks, so overusing it dilutes that navigation aid.
--> **`<main>`** — wraps the page's DOMINANT, unique content — exactly ONE per page, excluding repeated boilerplate like site navigation/sidebars/footers. Screen reader users can jump directly to `<main>` to skip past repetitive navigation on every page load.
--> **`<article>`** — content that would make sense distributed/syndicated INDEPENDENTLY (a blog post, a news story, a product card). The test: "would this still make sense pulled out and shown on its own, elsewhere?" If yes, `<article>` is appropriate.
--> **`<section>`** — a thematic grouping of content, generally expected to have its OWN heading. Less strict than `<article>`'s "makes sense standalone" test, but still meant for a genuinely distinct thematic block, not simply any grouping better served by a plain `<div>` used purely for styling.
--> **`<aside>`** — content tangentially related to the surrounding content — a sidebar, a pull quote, a related-links widget — content that could be removed without losing the main content's core meaning.

# Heading Hierarchy — A Frequently Misused Structural Signal

--> Headings aren't just "bigger/smaller text" (a purely visual effect achievable with CSS `font-size` regardless of tag) — they express a STRICT document OUTLINE, and skipping levels breaks that outline's logical structure for screen reader users navigating by heading level, and can confuse search engines' understanding of the page's content hierarchy.

```html
<!-- Correct hierarchy -->
<h1>Blog Post Title</h1>
  <h2>Introduction</h2>
  <h2>Main Argument</h2>
    <h3>Supporting Point 1</h3>
    <h3>Supporting Point 2</h3>
  <h2>Conclusion</h2>
```

--> A common misuse to avoid — choosing a heading tag purely because its DEFAULT visual size looks right, rather than because it genuinely represents that position in the document's logical outline. The fix is always to use the semantically CORRECT heading level and adjust its font-size with CSS separately, never the reverse.

# The `<div>` Isn't "Wrong" — It's a Deliberate Fallback

--> `<div>`/`<span>` remain entirely appropriate whenever you need a purely STYLING/SCRIPTING container with NO inherent semantic meaning of its own — a wrapper purely for applying a CSS Grid layout to group unrelated elements genuinely has no semantic meaning to express, and forcing a semantic tag onto it just to avoid `<div>` would actively misrepresent the content's structure to assistive technology and search engines.

# Microdata and Structured Data

--> Beyond the built-in semantic ELEMENTS, HTML supports embedding additional MACHINE-READABLE metadata directly in markup via `itemscope`/`itemtype`/`itemprop` attributes (Microdata), or more commonly today, JSON-LD (covered in the SEO Meta Tags file) — both let you explicitly tell a search engine "this specific text is a product's PRICE," beyond what semantic elements alone can express.

```html
<div itemscope itemtype="https://schema.org/Product">
  <span itemprop="name">Wireless Headphones</span>
  <span itemprop="price">79.99</span>
</div>
```

# Why the Document Outline Algorithm Doesn't Matter in Practice

--> The HTML specification originally defined an "outline algorithm" where nested `<section>`/`<article>` elements would each contribute their own heading level to an overall document outline, regardless of which literal `<h1>`-`<h6>` tag was used inside them. In practice, NO major browser or screen reader ever fully implemented this, and the specification has since deprecated the approach — the safe, practical guidance remains: use explicit, correctly-nested `<h1>`-`<h6>` levels yourself, rather than relying on sectioning elements to automatically adjust heading levels.

# Validating Semantic Structure

--> The W3C Markup Validator and browser accessibility auditing tools (Lighthouse's Accessibility panel) flag structural issues — multiple `<main>` elements, skipped heading levels, `<article>`/`<section>` elements missing an expected heading — semantic correctness should be actively VALIDATED, not just written carefully once and assumed to remain correct as a page evolves.
