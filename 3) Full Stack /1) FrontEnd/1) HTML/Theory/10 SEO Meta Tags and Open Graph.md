# Why Meta Tags Matter

--> Meta tags don't render visibly on the page, but they tell search engines and social platforms how to understand, rank, and display a page -- getting them wrong doesn't break the site visually, but it silently hurts discoverability.

# Core SEO Meta Tags

```html
<title>Product Name - Short, Descriptive Page Title</title>
<meta name="description" content="A concise 150-160 character summary shown under the title in search results.">
<meta name="robots" content="index, follow">
<link rel="canonical" href="https://example.com/products/widget">
```

--> `<title>` -- the single most important on-page SEO element -- shown as the clickable headline in search results and the browser tab.
--> `meta description` -- doesn't directly affect ranking, but strongly affects click-through rate since it's the summary text shown in search results.
--> `robots` -- controls whether search engines should index this page and follow its links (`noindex` is used for pages you don't want appearing in search, like an internal admin page).
--> `canonical` -- tells search engines which URL is the "real" one when the same content is reachable via multiple URLs (with/without trailing slash, with tracking parameters) -- prevents duplicate-content SEO penalties.

# Open Graph -- Controlling Social Media Previews

--> Open Graph (OG) tags control how a link looks when shared on Facebook, LinkedIn, Discord, WhatsApp, etc. -- without them, platforms guess (often poorly) what image/title/description to show.

```html
<meta property="og:title" content="Product Name">
<meta property="og:description" content="Short description shown in the link preview card.">
<meta property="og:image" content="https://example.com/preview-image.jpg">
<meta property="og:url" content="https://example.com/products/widget">
<meta property="og:type" content="website">
```

--> `og:image` should be a specific, reasonably sized image (commonly 1200x630px) -- platforms often reject or badly crop images that don't roughly match their expected aspect ratio.

# Twitter/X Cards

--> A separate but overlapping tag set specifically for how links preview on Twitter/X -- falls back to Open Graph tags if these are absent, but explicit Twitter Card tags give more control there.

```html
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="Product Name">
<meta name="twitter:image" content="https://example.com/preview-image.jpg">
```

# Structured Data (Schema.org / JSON-LD)

--> Structured data embeds machine-readable metadata directly in the page describing what the content actually IS (a product, a recipe, an article, an FAQ) -- this is what powers "rich results" in search (star ratings, price, breadcrumbs shown directly in search listings).

```html
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "Product",
  "name": "Widget",
  "offers": {
    "@type": "Offer",
    "price": "29.99",
    "priceCurrency": "USD"
  }
}
</script>
```

# Viewport and Mobile SEO

--> `<meta name="viewport" content="width=device-width, initial-scale=1">` -- without this, mobile browsers render the page at a fixed desktop-like width and shrink it, producing a broken zoomed-out experience. Google explicitly factors mobile-friendliness into ranking.
