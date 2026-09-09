# CSS Filters

--> `filter` applies graphical effects (blur, color adjustment) directly to an element, without needing image-editing software or extra markup.

```css
.photo {
  filter: grayscale(100%);
  filter: blur(5px);
  filter: brightness(1.2) contrast(1.1);
  filter: drop-shadow(2px 4px 6px rgba(0,0,0,0.3));   /* Follows the element's actual shape, unlike box-shadow */
}

.frosted-panel {
  backdrop-filter: blur(10px);   /* Blurs whatever is BEHIND this element, not the element itself */
  background: rgba(255, 255, 255, 0.2);
}
```

--> Multiple filter functions can be chained in one declaration, applied in the order written.
--> `backdrop-filter` is what powers the common "frosted glass" UI effect (blurring content behind a semi-transparent panel) seen in modern app design.

# Blend Modes

--> `mix-blend-mode` controls how an element's content blends with whatever is BEHIND it (similar to blend modes in Photoshop).
--> `background-blend-mode` -- same idea, but blends an element's own background image/color layers with each other.

```css
.overlay-text {
  mix-blend-mode: difference;   /* Common trick for text that stays readable over any background image */
}

.duotone-image {
  background-blend-mode: multiply;
}
```

--> Common values: `multiply` (darkens), `screen` (lightens), `overlay` (contrast-dependent mix), `difference` (inverts based on contrast, often used for guaranteed-readable overlaid text).

# clip-path -- Non-Rectangular Shapes

--> By default, every element is a rectangle. `clip-path` clips an element to a custom shape -- only the clipped region is visible; content outside it is simply hidden (not just visually covered).

```css
.hexagon {
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
}

.circle-avatar {
  clip-path: circle(50%);
}

.diagonal-banner {
  clip-path: polygon(0 0, 100% 0, 100% 85%, 0 100%);
}
```

--> Common shape functions: `circle()`, `ellipse()`, `polygon()`, `inset()`.
--> Unlike `border-radius` (which only rounds corners of a rectangle), `clip-path` can create arbitrary geometric shapes -- hexagons, diagonal cuts, stars -- entirely in CSS, no image assets needed.
--> `clip-path` can also be animated/transitioned (covered in the Transitions/Animations file), enabling shape-morphing hover effects without JavaScript.
