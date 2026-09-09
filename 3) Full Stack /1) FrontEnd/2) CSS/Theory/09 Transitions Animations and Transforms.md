# Transform

--> `transform` changes an element's shape/position visually without affecting the document flow.
--> `translate(x, y)` — moves an element from its current position.
--> `scale(x, y)` — resizes an element.
--> `rotate(deg)` — rotates an element.
--> `skew(x, y)` — slants/distorts an element.
--> Multiple transforms can be combined: `transform: translateX(20px) rotate(10deg);`
--> `transform-origin` — sets the point around which transforms (like rotate/scale) are applied (default is center).

# 3D Transforms

--> `rotate3d(x, y, z, angle)` — rotates around an arbitrary 3D vector.
--> `perspective(px)` — sets the distance from the viewer to the z=0 plane, giving 3D transforms depth/foreshortening.
--> `translateZ(px)` — moves an element toward/away from the viewer along the z-axis (needs `perspective` on the parent to be visible).
--> `backface-visibility: hidden` — hides an element when its rotated face is turned away from the viewer (common for flip-card effects).

# Transitions

--> `transition` animates a property smoothly from one value to another over time (usually on state change like `:hover`).
--> `transition-property` — which property to animate (or `all`).
--> `transition-duration` — how long the animation takes (e.g. `0.3s`).
--> `transition-timing-function` — the pacing curve: `ease`, `linear`, `ease-in`, `ease-out`, `ease-in-out`, `cubic-bezier(...)`.
--> `transition-delay` — waits before starting the animation.
--> Shorthand: `transition: background-color 0.3s ease-in-out;`

```css
.button {
  background-color: blue;
  transition: background-color 0.3s ease;
}
.button:hover {
  background-color: darkblue;
}
```

# Animations (@keyframes)

--> `@keyframes` defines a sequence of styles over the course of an animation, using percentages (or `from`/`to`).
--> `animation-name` — links an element to a `@keyframes` block.
--> `animation-duration` — how long one cycle takes.
--> `animation-timing-function` — pacing curve, same values as transitions.
--> `animation-iteration-count` — number of times to repeat, or `infinite`.
--> `animation-direction` — `normal`, `reverse`, `alternate`.
--> `animation-fill-mode` — what styles apply before/after the animation runs (`forwards`, `backwards`, `both`).
--> Shorthand: `animation: fadeIn 1s ease-in forwards;` — combines name, duration, timing-function, delay, iteration-count, direction, fill-mode, play-state in one property.

```css
@keyframes fadeIn {
  from { opacity: 0; }
  to   { opacity: 1; }
}
.box {
  animation: fadeIn 1s ease-in forwards;
}
```

# Performance Notes

--> Animating `transform` and `opacity` is the most performant (GPU-accelerated), since they don't trigger layout/reflow.
--> Avoid animating properties like `width`, `height`, `top`/`left` for smoother animations — prefer `transform: translate()`/`scale()` instead.
