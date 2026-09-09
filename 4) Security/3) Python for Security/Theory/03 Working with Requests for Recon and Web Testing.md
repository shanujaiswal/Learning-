### Working with Requests for Recon and Web Testing

--> `requests` is the standard Python library for making HTTP calls. For web app security work — recon, header auditing, login-flow testing, proxying through an intercepting proxy — it's the tool you reach for before anything heavier like Burp's own scripting.
--> Install with `pip install requests`.

## GET and POST basics

```python
import requests

# GET request
resp = requests.get("https://httpbin.org/get", params={"q": "test"})
print(resp.status_code)     # 200
print(resp.url)             # https://httpbin.org/get?q=test
print(resp.json())          # parsed JSON body as a dict

# POST request with a JSON body
resp = requests.post(
    "https://httpbin.org/post",
    json={"username": "admin", "password": "hunter2"},
)
print(resp.status_code)     # 200
print(resp.request.headers) # inspect what was actually sent
```

--> `resp.text` gives the raw body as a decoded string, `resp.content` gives raw bytes, `resp.json()` parses JSON (raises if the body isn't valid JSON).

## Headers, cookies, and sessions

--> Custom headers are essential for recon — setting a realistic `User-Agent`, sending auth tokens, or replaying specific values seen in Burp.

```python
import requests

headers = {
    "User-Agent": "Mozilla/5.0 (compatible; recon-script/1.0)",
    "X-Custom-Header": "test",
}
resp = requests.get("https://httpbin.org/headers", headers=headers)
print(resp.json()["headers"]["X-Custom-Header"])   # test
```

--> Cookies can be sent directly or read from a response:

```python
import requests

resp = requests.get("https://httpbin.org/cookies/set/session_id/abc123")
print(resp.cookies.get("session_id"))   # abc123

# Send cookies manually on a later request
resp2 = requests.get("https://httpbin.org/cookies", cookies={"session_id": "abc123"})
print(resp2.json())   # {'cookies': {'session_id': 'abc123'}}
```

--> A `Session` object persists cookies and headers across multiple requests automatically — critical when testing anything that requires being logged in (the session cookie set at login is reused on every subsequent request without you doing it manually).

```python
import requests

session = requests.Session()
session.headers.update({"User-Agent": "recon-script/1.0"})

# Login once — session cookie gets stored automatically
session.post("https://target-test-app.local/login", data={"user": "admin", "pass": "hunter2"})

# Subsequent requests reuse the stored cookies
resp = session.get("https://target-test-app.local/dashboard")
print(resp.status_code)   # 200 if the session cookie carried through
```

## Timeouts

--> Just like raw sockets, `requests` calls block forever by default if the server hangs. Always pass `timeout`.

```python
import requests

try:
    resp = requests.get("https://target-test-app.local/", timeout=5)
except requests.exceptions.Timeout:
    print("Request timed out after 5 seconds")
except requests.exceptions.ConnectionError:
    print("Could not connect (host down / refused / DNS failure)")
```

--> `timeout` can be a single number (applies to both connect and read) or a tuple `(connect_timeout, read_timeout)` for finer control.

## Redirects

--> By default `requests` follows redirects automatically. For security testing you often want to see the *chain* — open redirects, redirect-based auth bypass, and mixed HTTP/HTTPS hops are common findings.

```python
import requests

resp = requests.get("https://httpbin.org/redirect/3", allow_redirects=True)
print(resp.status_code)          # 200 (final page after following all redirects)
print(len(resp.history))         # 3 -> number of redirects followed

for hop in resp.history:
    print(hop.status_code, hop.url)   # each intermediate 302/301 and its Location

# Inspect without following, to catch open-redirect style issues
resp_no_follow = requests.get("https://httpbin.org/redirect/1", allow_redirects=False)
print(resp_no_follow.status_code)                  # 302
print(resp_no_follow.headers.get("Location"))       # where it would have gone
```

## Basic auth

```python
import requests
from requests.auth import HTTPBasicAuth

resp = requests.get(
    "https://httpbin.org/basic-auth/admin/hunter2",
    auth=HTTPBasicAuth("admin", "hunter2"),
)
print(resp.status_code)   # 200
```

## Proxies — routing through Burp Suite or mitmproxy

--> Intercepting proxies (Burp, mitmproxy) let you watch, replay, and tamper with requests your script sends. Point `requests` at them with the `proxies` dict.

```python
import requests

proxies = {
    "http": "http://127.0.0.1:8080",
    "https": "http://127.0.0.1:8080",
}

# verify=False is often needed because Burp's CA cert isn't trusted by default —
# for real testing, install Burp's CA cert instead of disabling verification globally.
resp = requests.get(
    "https://target-test-app.local/",
    proxies=proxies,
    verify=False,
    timeout=10,
)
print(resp.status_code)
```

--> Disabling TLS verification (`verify=False`) suppresses a security warning every request. It's acceptable for local lab testing through a proxy whose cert you understand, but never do this against production traffic or code you ship.

## Mini example: checking HTTP security headers of a site

--> A quick, common recon task — auditing whether a target sends recommended security headers.

```python
import requests

SECURITY_HEADERS = [
    "Strict-Transport-Security",
    "Content-Security-Policy",
    "X-Content-Type-Options",
    "X-Frame-Options",
    "Referrer-Policy",
    "Permissions-Policy",
]

def audit_headers(url):
    resp = requests.get(url, timeout=10)
    print(f"[*] {url} -> {resp.status_code}")
    for header in SECURITY_HEADERS:
        value = resp.headers.get(header)
        if value:
            print(f"    [+] {header}: {value}")
        else:
            print(f"    [-] MISSING: {header}")

audit_headers("https://example.com")
```

--> Sample output shape:

```
[*] https://example.com -> 200
    [+] Strict-Transport-Security: max-age=31536000
    [-] MISSING: Content-Security-Policy
    [+] X-Content-Type-Options: nosniff
    [-] MISSING: X-Frame-Options
```

## Mini example: rate-limited login brute-force testing structure

--> IMPORTANT: only run this against an application you own or are explicitly authorized to test (e.g. a local DVWA/juice-shop instance). Brute-forcing a real, third-party login endpoint is illegal and will likely trip account lockouts or WAF bans.

```python
import time
import requests

LOGIN_URL = "http://localhost:3000/rest/user/login"   # example: local test app only
USERNAME = "admin@test.local"
CANDIDATE_PASSWORDS = ["password123", "admin123", "letmein", "qwerty123"]

def attempt_login(session, username, password):
    resp = session.post(
        LOGIN_URL,
        json={"email": username, "password": password},
        timeout=5,
    )
    return resp.status_code == 200

def brute_force(username, passwords, delay=1.0):
    session = requests.Session()
    for pwd in passwords:
        success = attempt_login(session, username, pwd)
        print(f"[*] Trying '{pwd}' -> {'SUCCESS' if success else 'failed'}")
        if success:
            print(f"[+] Valid credentials found: {username}:{pwd}")
            return pwd
        time.sleep(delay)   # rate-limit ourselves to avoid hammering the target
    print("[-] No valid password found in list")
    return None

# brute_force(USERNAME, CANDIDATE_PASSWORDS, delay=1.5)
```

--> Key structural points worth internalizing from that example:

1. Reuse a `Session` so cookies/anti-CSRF tokens carry across attempts the way a real browser would.
2. Always add a `delay` between attempts — both to avoid triggering defensive lockouts prematurely during authorized testing, and because hammering an endpoint with zero delay is indistinguishable from a DoS.
3. Stop immediately on success rather than continuing to try every remaining candidate.
4. Log every attempt so the test is auditable afterward.
