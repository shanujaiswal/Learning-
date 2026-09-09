# The Limitation of Media Queries

--> `@media` queries (covered earlier) respond to the VIEWPORT's size -- but a component (a card, a sidebar widget) often needs to adapt based on the size of its own CONTAINER, not the whole browser window. A card might render in a wide main area or a narrow sidebar on the exact same page at the exact same viewport width -- a media query can't tell the difference.

# Container Queries -- Component-Level Responsiveness

--> Container queries let an element style itself based on the size of a named ancestor container, independent of the viewport.

```css
.card-container {
  container-type: inline-size;   /* Opt this element in as a queryable container */
  container-name: card-container;
}

@container card-container (min-width: 400px) {
  .card {
    display: flex;
    flex-direction: row;
  }
}

@container card-container (max-width: 399px) {
  .card {
    display: flex;
    flex-direction: column;
  }
}
```

--> `container-type: inline-size` -- makes the element queryable based on its inline-axis (width, in a normal horizontal writing mode) size.
--> The same `.card` component can now automatically switch layout depending on whether it's dropped into a wide main content area or a narrow sidebar, without any JavaScript and without needing separate CSS classes per context.

# Container Query Units

--> New length units relative to the queried container rather than the viewport: `cqw` (container query width, 1% of the container's width), `cqh` (container query height), `cqi`/`cqb` (inline/block-size equivalents).

```css
.card-title {
  font-size: 5cqw;   /* Scales with the container's width, not the viewport's */
}
```

# Why This Matters for Component-Based Design

--> Modern frontend development (React, Vue) is built around reusable components dropped into many different layout contexts -- container queries finally let CSS express "how should THIS component look given the space it's actually been given," matching how component-based architecture already thinks, rather than forcing every responsive decision back up to the global viewport.
