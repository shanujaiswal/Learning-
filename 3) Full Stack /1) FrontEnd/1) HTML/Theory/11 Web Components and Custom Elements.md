# What Web Components Are

--> Web Components are a set of native browser APIs (not a framework) for building reusable, encapsulated custom HTML elements -- e.g. `<user-card>` behaving like a real built-in tag with its own internal markup, styles, and behavior, usable in any framework or no framework at all.
--> Three core technologies work together: Custom Elements (define new tags), Shadow DOM (encapsulate internal markup/styles), and HTML Templates (reusable inert markup fragments).

# Custom Elements

--> Register a new HTML tag backed by a JavaScript class extending `HTMLElement`, with lifecycle callbacks similar in spirit to React's component lifecycle.

```javascript
class UserCard extends HTMLElement {
  connectedCallback() {
    // Runs when the element is inserted into the DOM
    this.innerHTML = `<div class="card">${this.getAttribute("name")}</div>`;
  }

  disconnectedCallback() {
    // Runs when the element is removed from the DOM -- good place for cleanup
  }

  static get observedAttributes() {
    return ["name"];
  }

  attributeChangedCallback(attrName, oldValue, newValue) {
    // Runs when a watched attribute changes
  }
}

customElements.define("user-card", UserCard);
```

```html
<user-card name="Alice"></user-card>
```

--> Custom element names MUST contain a hyphen (`user-card`, not `usercard`) -- this is a deliberate spec requirement, guaranteeing no collision with any current or future native HTML tag.

# Shadow DOM -- True Encapsulation

--> Shadow DOM attaches a separate, encapsulated DOM subtree to an element -- styles defined inside it don't leak out to the rest of the page, and page-level CSS doesn't leak in either (unlike a normal `<div>`, where global CSS rules can accidentally affect its contents).

```javascript
class UserCard extends HTMLElement {
  connectedCallback() {
    const shadow = this.attachShadow({ mode: "open" });
    shadow.innerHTML = `
      <style>
        .card { border: 1px solid #ccc; padding: 8px; }
      </style>
      <div class="card">${this.getAttribute("name")}</div>
    `;
  }
}
```

--> This solves a real, common problem in large apps -- CSS class name collisions between unrelated components -- without needing a CSS-in-JS library or naming convention discipline (like BEM) to avoid it.

# HTML Templates

--> `<template>` defines inert markup that isn't rendered until explicitly cloned into the document via JavaScript -- useful for defining a reusable structure once and stamping out multiple instances.

```html
<template id="card-template">
  <div class="card"><slot></slot></div>
</template>
```

```javascript
const template = document.getElementById("card-template");
const clone = template.content.cloneNode(true);
document.body.appendChild(clone);
```

# Slots -- Content Projection

--> `<slot>` inside a custom element's Shadow DOM defines where content passed BETWEEN the element's opening/closing tags should be rendered -- conceptually similar to React's `children` prop or Vue's slots.

```html
<user-card>
  <span slot="name">Alice</span>
</user-card>
```

# Where Web Components Fit vs React/Vue

--> Web Components are framework-agnostic and browser-native -- useful for building a shared component library usable across projects regardless of which framework (or none) each one uses, and for cases where long-term framework independence matters.
--> Frameworks like React still generally offer a richer developer experience for building an entire application (state management, routing, ecosystem) -- Web Components are often used for specific shareable widgets rather than replacing a framework outright.
