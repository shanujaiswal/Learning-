# Why Print Styles Are Still Relevant

--> Invoices, receipts, tickets, resumes, and reports are still commonly printed directly from a browser -- without a dedicated print stylesheet, the printed page looks exactly like the screen (navigation bars, ads, buttons, dark backgrounds included), wasting ink and looking unprofessional.

# The print Media Type

--> `@media print` scopes CSS rules to apply only when the page is being printed (or exported to PDF via "Print to PDF") -- completely separate from the screen styles.

```css
@media print {
  nav, footer, .sidebar, .no-print, button {
    display: none;   /* Hide navigation/interactive elements irrelevant on paper */
  }

  body {
    color: #000;
    background: #fff;   /* Dark themes should never print dark backgrounds -- wastes ink, hurts legibility */
    font-size: 12pt;
  }

  a {
    color: #000;
    text-decoration: underline;
  }

  a[href]::after {
    content: " (" attr(href) ")";   /* Show the actual URL since a printed link isn't clickable */
  }
}
```

# Page Setup Properties

--> `@page` controls page-level properties like margins and size, specific to print/PDF output.

```css
@page {
  size: A4;
  margin: 2cm;
}
```

# Controlling Page Breaks

--> `break-inside: avoid` -- prevents an element (a table row, a card, a heading with its paragraph) from being awkwardly split across two printed pages.
--> `break-before: page` / `break-after: page` -- forces content to start on a new page -- useful for ensuring each invoice/section starts cleanly.

```css
.invoice-section {
  break-inside: avoid;
}

.chapter-title {
  break-before: page;
}
```

# Print-Specific Units

--> `pt` (points), `cm`, `mm`, `in` -- physical units that make more sense for print than screen-relative units like `vw`/`vh` (which have no meaningful physical size on paper).

# Testing Print Styles

--> Browser DevTools can emulate print media directly (Chrome DevTools → Rendering tab → "Emulate CSS media type: print") without actually printing, letting you iterate quickly.
--> Always verify the ACTUAL "Print Preview" too -- some print-specific browser behaviors (page breaks, header/footer injection) only show accurately there.
