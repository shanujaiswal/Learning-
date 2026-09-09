### Dynamic Web Security Testing with Selenium and Playwright

--> `03 Working with Requests for Recon and Web Testing.md` covered `requests` -- fast, but it only ever sees the raw HTTP response, never anything JavaScript renders/modifies afterward. Modern web apps often build most of their actual content, forms, and behavior client-side, invisible to a plain `requests.get()`. Selenium and Playwright drive a REAL browser instead, so they see the page exactly as a human visitor's browser would, including JS-rendered DOM changes, client-side validation, and dynamically injected content.

## Ethical note

--> The same authorization rule as every other file in this folder applies here without exception -- only automate browser interaction against applications you own or are explicitly authorized to test. Automated form submission, login attempts, or content scraping against a site you don't have permission for can violate its terms of service and, in cases involving authentication bypass attempts or scale, computer misuse law.

## Selenium basics

```python
# pip install selenium
# Also requires a matching WebDriver binary (e.g. chromedriver) on PATH, or Selenium Manager
# (bundled in modern Selenium) will fetch one automatically for the installed browser version.
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

options = webdriver.ChromeOptions()
options.add_argument("--headless=new")   # run without a visible window -- standard for automated tooling

driver = webdriver.Chrome(options=options)
driver.get("http://testphp.vulnweb.com/")   # a deliberately vulnerable practice site meant for this purpose

# Waiting explicitly for an element is essential -- the page may still be rendering when
# get() returns, since get() only waits for the initial HTML load event, not JS-driven content
search_box = WebDriverWait(driver, 10).until(
    EC.presence_of_element_located((By.NAME, "searchFor"))
)
search_box.send_keys("test")
search_box.submit()

print(driver.title)
print(driver.page_source[:200])   # the FULLY RENDERED HTML, after JS execution -- unlike requests.get()

driver.quit()
```

--> `--headless` is what makes this practical for automated scripts/CI pipelines -- no window is drawn, but the browser engine still fully renders and executes JavaScript exactly as it would with a visible window.
--> Explicit waits (`WebDriverWait` + `expected_conditions`) are the correct pattern over `time.sleep(n)` -- a fixed sleep either wastes time waiting longer than necessary or, on a slow-loading page, isn't long enough and the script fails intermittently; waiting for a specific CONDITION handles both cases correctly regardless of actual load time.

## Playwright -- a more modern alternative

```python
# pip install playwright
# playwright install    <- downloads the actual browser binaries Playwright drives
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    page.goto("http://testphp.vulnweb.com/")

    # Playwright auto-waits for elements to be actionable by default -- no separate
    # explicit-wait boilerplate needed for most straightforward interactions
    page.fill('input[name="searchFor"]', "test")
    page.click('input[type="submit"]')

    print(page.title())
    print(page.content()[:200])

    browser.close()
```

--> Playwright's built-in auto-waiting (checking that an element is visible, enabled, and stable before interacting with it) removes much of the manual `WebDriverWait` boilerplate Selenium requires -- one of the main practical reasons newer tooling has shifted toward it, alongside first-class support for intercepting network requests/responses directly (below), which Selenium requires additional tooling (e.g. a proxy) to achieve.

## Intercepting network traffic -- Playwright's request/response hooks

```python
from playwright.sync_api import sync_playwright

captured_requests = []

def log_request(request):
    captured_requests.append({"method": request.method, "url": request.url})

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    page.on("request", log_request)      # fires for EVERY request the page makes, including
                                          # background XHR/fetch calls invisible in the raw HTML
    page.goto("https://example.com")

    for req in captured_requests:
        print(req["method"], req["url"])
    browser.close()
```

--> This is directly useful for security recon on a JS-heavy single-page app -- discovering the actual backend API endpoints a page calls (often not visible anywhere in the static HTML/JS source, only triggered at runtime) is exactly the kind of attack-surface mapping that a plain `requests`-based crawl of `03`'s style cannot see at all.

## Detecting reflected content and DOM-based issues

```python
def check_reflected_xss_dynamic(page, url, param, payload="<script>window.__xss_test=true</script>"):
    """Loads a URL with a test payload injected into a query parameter, then checks whether the
    browser actually EXECUTED it (a DOM-based/reflected XSS indicator), not just whether the
    raw string appears back in the HTML (which requests-based checking in file 03 already covers)."""
    test_url = f"{url}?{param}={payload}"
    page.goto(test_url)
    executed = page.evaluate("() => window.__xss_test === true")
    return executed

# executed == True means the browser actually ran injected script -- a stronger, more specific
# signal than merely finding the payload string reflected in the response body, since some
# reflected strings never actually execute (e.g. if properly HTML-escaped by the app).
```

--> This distinction matters -- checking whether a payload string appears unescaped in a raw HTTP response (as in the static-analysis style of `03`) can produce false positives/negatives relative to whether a REAL browser would actually execute it (which depends on exact injection context, escaping, and CSP). Driving a real browser and checking for actual JS execution, as above, gives a much more reliable positive signal specifically for this class of finding.

## Practical considerations for security tooling specifically

--> Browser automation is much heavier and slower than `requests` -- launching a real browser engine per test, rendering full pages, and waiting for JS execution costs orders of magnitude more time/memory than a plain HTTP request. The practical pattern in real tools: use `requests` for broad, fast reconnaissance and simple checks (as in `03`), and reserve Selenium/Playwright specifically for the subset of checks that genuinely require JS execution/DOM inspection to detect at all.
--> Both tools can also capture screenshots (`driver.save_screenshot(path)` / `page.screenshot(path=path)`) -- useful for evidence capture in a penetration test report, documenting exactly what a vulnerable page/form looked like at the time of testing.

## Cross-references

--> Extends `03 Working with Requests for Recon and Web Testing.md`'s recon/testing patterns into JS-rendered content; the vulnerability-detection logic here feeds directly into the SQLi/XSS checker patterns built in `14 Building SQLi and XSS Vulnerability-Checking Scripts.md`, which covers the request-crafting/response-analysis side of the same class of bugs in more depth.
