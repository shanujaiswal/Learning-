# Href Attribute / Links

--> The `<a>` tag defines a hyperlink. The `href` attribute specifies the URL of the page the link should go to.
--> `target="_blank"` opens the link in a new tab.
--> `<a href="#section-id">` links to an element on the same page (anchor link).
--> `<a href="mailto:someone@example.com">` opens the default email client.
--> `<a href="tel:+1234567890">` opens the phone dialer on mobile.
--> Relative paths (`../folder/file.html`) point to files relative to the current file's location.
--> Absolute paths/URLs (`https://example.com`) point to a location regardless of the current file.
--> Security: when using `target="_blank"`, also add `rel="noopener noreferrer"` — prevents the new page from accessing `window.opener` (protects against tabnabbing) and stops the referrer header being sent.

# Images

--> `<img src="url" alt="description">` — embeds an image. It is an empty element (no closing tag).
--> `src` — path/URL to the image file.
--> `alt` — alternative text shown if the image fails to load; also used by screen readers (accessibility) and SEO.
--> `width` / `height` — set the display dimensions (helps prevent layout shift while loading).
--> `loading="lazy"` — defers loading offscreen images until the user scrolls near them, improving page load performance.

# SVG

--> SVG (Scalable Vector Graphics) is used to add icons/graphics that scale without losing quality.
--> Can be embedded via `<img src="icon.svg">`, inline as `<svg>...</svg>`, or as a CSS background.

# Audio and Video

--> `<audio controls>` — embeds sound content; `controls` shows play/pause/volume UI.
--> `<video controls>` — embeds video content.
--> `<source src="file.mp4" type="video/mp4">` — nested inside `<audio>`/`<video>` to provide multiple format fallbacks.
--> `autoplay`, `loop`, `muted` — additional playback attributes.

# iframe

--> `<iframe src="url">` — embeds another HTML page/document inside the current page (e.g. embedding a YouTube video or Google Map).

# Figure and Caption

--> `<figure>` — wraps self-contained content like an image, diagram, or code snippet.
--> `<figcaption>` — provides a caption for the content inside `<figure>`.

# Favicon

--> `<link rel="icon" href="favicon.ico">` — sets the small icon shown in the browser tab.

# The track Element (Captions and Subtitles)

--> `<track>` — nested inside `<audio>`/`<video>` to add timed text tracks such as subtitles, captions, or descriptions, without needing any JavaScript.
--> `kind` — type of track: `subtitles`, `captions`, `descriptions`, `chapters`, or `metadata`.
--> `src` — path to the track file (usually a `.vtt` WebVTT file).
--> `srclang` — language of the track text (e.g. `en`, `es`).
--> `label` — a user-readable title shown in the player's track-selection menu.
--> `default` — marks this track as the one enabled by default if the user hasn't chosen one.
--> Important for accessibility — captions/subtitles let deaf/hard-of-hearing users and non-native speakers follow along with video/audio content.

```html
<video controls>
  <source src="movie.mp4" type="video/mp4">
  <track kind="subtitles" src="subtitles_en.vtt" srclang="en" label="English" default>
  <track kind="subtitles" src="subtitles_es.vtt" srclang="es" label="Español">
</video>
```
