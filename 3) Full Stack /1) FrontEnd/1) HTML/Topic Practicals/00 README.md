# HTML Practical Examples — Index

This folder contains clean, self-contained HTML5 practicals that map to the
chapters in `../Theory`. Every `.html` file here is a complete, standalone
document — open it directly in a browser, no build step or server required.

| File | Demonstrates | Maps to Theory Chapter(s) |
|---|---|---|
| `01-semantic-page-layout.html` | Full semantic page skeleton — `header`/`nav`/`main`/`section`/`article`/`aside`/`footer`, a correct single-`h1` heading hierarchy, and text-level semantic tags (`strong`, `em`, `mark`, `time`, `abbr`, `blockquote`, `cite`, `code`). | 02 Document Structure and Basics, 03 Text Formatting and Semantic Elements |
| `02-media-and-forms.html` | Responsive images (`srcset`/`sizes`, `loading="lazy"`, `decoding="async"`), a `<video>` with multiple `<source>` and a caption `<track>`, plus a multi-field form using `email`/`date`/`range`/`color`/`tel`/`url` inputs, `<datalist>`, and `<fieldset>`/`<legend>` grouping. Also includes a simple `<table>` with `<caption>`/`scope`. | 04 Links Images and Media, 05 Lists Tables and Forms |
| `03-accessible-and-validated-form.html` | A signup form using `required`/`pattern`/`minlength` constraints, the Constraint Validation API (`setCustomValidity()`) for a custom cross-field rule (password confirmation match), `<label for>` on every control, `aria-describedby` hints/errors, and visible `:valid`/`:invalid` styling. | 09 Advanced Form Validation, 08 Accessibility and ARIA |
| `04-html5-apis-demo.html` | Four native browser APIs, each in its own section: `<dialog>` (`showModal()`/`close()`), `<details>`/`<summary>` disclosure widgets, `localStorage` read/write persistence, and a small `<canvas>` freehand drawing pad. | 07 HTML5 APIs and Advanced Elements |
| `05-web-component-counter.html` | A real `<counter-widget>` custom element built with `customElements.define()` and encapsulated Shadow DOM, observed attributes, lifecycle callbacks, and a custom `counter-change` event bubbling up to the page — no framework involved. | 11 Web Components and Custom Elements |
| `06-seo-meta-tags-example.html` | A realistic `<head>` for a sample product page: tuned `<title>`/meta description, `rel="canonical"`, `robots` directive, full Open Graph (`og:*`) tags, Twitter Card (`twitter:*`) tags, and a JSON-LD `Product` structured-data block. | 10 SEO Meta Tags and Open Graph |

## Notes on conventions used throughout

- All external links that open a new tab use `target="_blank" rel="noopener noreferrer"`.
- Every page defines a light/dark palette via CSS custom properties and
  `prefers-color-scheme`, and uses `:focus-visible` instead of removing
  outlines outright.
- No deprecated tags/attributes (`<font>`, `<center>`, `align=`, `<marquee>`,
  etc.) are used anywhere in this folder.
- Files are numbered in a suggested reading order, but each one is fully
  independent and can be opened on its own.
