==> Shift + Alt + F ---> code beautifier
==> /* comment */ ---> not rendered, used for notes in the stylesheet
==> ../ ---> To get file location (relative path, same as HTML)

# .a ---> class ---> 1 page ---> Unlimited Repeat
# #a ---> ID ---> 1 page ---> 1 time Repeat, higher specificity than class

==> * ---> Universal selector, selects everything
==> !important ---> forces a declaration to win regardless of specificity (use sparingly)

==> px ---> fixed/absolute unit
==> % ---> relative to parent
==> em ---> relative to current element's font-size
==> rem ---> relative to root (html) font-size ---> more predictable than em
==> vw / vh ---> 1% of viewport width / height

==> box-sizing: border-box ---> width/height include padding + border (use this almost always)
==> margin ---> outside the border (space between elements)
==> padding ---> inside the border (space between content and border)

==> display: none ---> removes from layout completely
==> visibility: hidden ---> hides but still takes up space

==> flexbox ---> one-dimensional layout (row OR column)
==> grid ---> two-dimensional layout (rows AND columns)
==> justify-content ---> main axis alignment
==> align-items ---> cross axis alignment

==> position: relative ---> shifts element but keeps its space in flow
==> position: absolute ---> removed from flow, positioned relative to nearest positioned ancestor
==> position: fixed ---> positioned relative to viewport, stays on scroll
==> position: sticky ---> relative until scroll threshold, then fixed

==> transition ---> smooth animation between two states (e.g. on :hover)
==> @keyframes ---> multi-step animation sequence
==> transform + opacity ---> cheapest properties to animate (GPU-accelerated)

==> :root ---> where global CSS variables (--name: value) are usually defined
==> var(--name, fallback) ---> reads a custom property, with an optional fallback

==> @media (min-width: ...) ---> mobile-first responsive breakpoint
==> clamp(min, preferred, max) ---> one value that scales fluidly between bounds

==> BEM ---> block__element--modifier naming convention to avoid specificity clashes

==> Common gotcha ---> vertical margins between block elements collapse (take the larger, not the sum)
==> Common gotcha ---> z-index only works on positioned elements (non-static)
==> Common gotcha ---> inline elements ignore width/height/vertical margin — use inline-block or block instead
