"""
subdomain_enumerator.py

Simulates the recon step real hunters run with Subfinder/Amass/crt.sh
(Theory note 16, "Subdomain enumeration") -- WITHOUT making any real
network or DNS calls. This is a deterministic (fixed-seed) stand-in for
a passive-recon merge-and-dedupe pass over multiple data sources.

Deliberately realistic detail: enumeration finds subdomains regardless
of whether the program actually wants them tested. Out-of-scope hosts
(the marketing blog, the status page, a partner's HR portal, internal
staging boxes) show up in the candidate list right alongside legitimate
in-scope targets, exactly as they would from a real crt.sh/Amass sweep
over a domain. Filtering against the published scope has to happen
*before* a single probe is sent -- that's `scope_filter.py`'s job.
"""

from __future__ import annotations

import random

SEED = 1337  # fixed seed -> fully reproducible "discovery" every run


def enumerate_subdomains(domain: str = "acmecorp.com") -> list[str]:
    """
    Return a simulated list of candidate hosts discovered for `domain`.

    In a real toolkit this would shell out to `subfinder -d domain -silent`
    / query crt.sh / query Amass's passive sources and merge+dedupe the
    results. Here we just deterministically shuffle a fixed candidate
    pool so the pipeline is 100% offline and reproducible.
    """
    candidates = [
        # --- legitimate in-scope assets ---
        "www.acmecorp.com",
        "api.acmecorp.com",
        "dev.acmecorp.com",
        "shop.acmecorp.com",
        "portal.acmecorp.com",
        "mail.acmecorp.com",
        "cdn.acmecorp.com",
        # --- out-of-scope, but realistically discovered anyway ---
        "blog.acmecorp.com",               # excluded: third-party CMS
        "status.acmecorp.com",             # excluded: third-party status page
        "partner-hr.acmecorp.com",         # excluded: acquired subsidiary
        "build.internal.acmecorp.com",     # excluded: internal wildcard
        "vpn.internal.acmecorp.com",       # excluded: internal wildcard
        # --- unrelated domain that just happens to show up in a broad
        #     crt.sh sweep (shared cert, similar name, etc.) ---
        "acmecorp-notreal.net",
    ]

    rng = random.Random(SEED)
    shuffled = candidates.copy()
    rng.shuffle(shuffled)
    return shuffled


if __name__ == "__main__":
    for host in enumerate_subdomains():
        print(host)
