"""
typosquat_detector.py
-----------------------
Typosquatting / lookalike-domain detector: real-world equivalent of brand-
protection feeds and browser lookalike-URL warnings (Chrome's "Did you mean
google.com?", Netcraft's typosquat monitoring) -- compare every queried
registered domain against a short list of known, high-value brand domains
using Levenshtein edit distance, and flag anything close-but-not-equal.

Edit distance (Levenshtein) counts the minimum number of single-character
insertions, deletions, or substitutions needed to turn one string into
another -- exactly the kind of near-miss a typo ("gooogle.com") or a
deliberately crafted lookalike ("paypa1.com", using the digit 1 for the
letter l) produces relative to the real brand domain.
"""

KNOWN_BRAND_DOMAINS = [
    "google.com", "paypal.com", "microsoft.com", "netflix.com",
    "linkedin.com", "amazon.com", "apple.com", "facebook.com",
    "github.com", "wikipedia.org",
]

# Domains within this edit distance (inclusive) of a known brand -- but not
# an exact match -- are flagged as likely lookalikes.
MAX_EDIT_DISTANCE = 2


def levenshtein_distance(a: str, b: str) -> int:
    """Classic O(len(a) * len(b)) dynamic-programming edit distance."""
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)

    prev_row = list(range(len(b) + 1))
    for i, ch_a in enumerate(a, start=1):
        curr_row = [i] + [0] * len(b)
        for j, ch_b in enumerate(b, start=1):
            insert_cost = curr_row[j - 1] + 1
            delete_cost = prev_row[j] + 1
            substitute_cost = prev_row[j - 1] + (0 if ch_a == ch_b else 1)
            curr_row[j] = min(insert_cost, delete_cost, substitute_cost)
        prev_row = curr_row
    return prev_row[-1]


def registered_domain(query: str) -> str:
    """Strip subdomain labels down to the registrable 'domain.tld' part,
    e.g. 'www.paypa1.com' -> 'paypa1.com'."""
    parts = query.split(".")
    if len(parts) <= 2:
        return query
    return ".".join(parts[-2:])


def closest_brand(domain: str):
    """Return (brand, distance) for the known-brand domain nearest to `domain`."""
    best_brand, best_distance = None, None
    for brand in KNOWN_BRAND_DOMAINS:
        distance = levenshtein_distance(domain, brand)
        if best_distance is None or distance < best_distance:
            best_brand, best_distance = brand, distance
    return best_brand, best_distance


def analyze_queries(records: list) -> dict:
    """
    Check every distinct queried domain against the known-brand list.

    Returns:
        {"alerts": [ {"query", "domain", "closest_brand", "edit_distance",
                       "src_ip", "timestamp"}, ... ]}
    """
    alerts = []
    seen_domains_flagged = set()

    for r in records:
        if r["is_response"]:
            continue
        domain = registered_domain(r["query"])

        if domain in KNOWN_BRAND_DOMAINS:
            continue  # exact match to a real brand domain -- not a typosquat

        brand, distance = closest_brand(domain)
        if distance is not None and 0 < distance <= MAX_EDIT_DISTANCE:
            key = (r["src_ip"], domain)
            if key in seen_domains_flagged:
                continue
            seen_domains_flagged.add(key)
            alerts.append({
                "query": r["query"], "domain": domain, "closest_brand": brand,
                "edit_distance": distance, "src_ip": r["src_ip"],
                "timestamp": r["timestamp"],
            })

    return {"alerts": alerts}


if __name__ == "__main__":
    from dns_log_generator import generate_log

    log = generate_log()
    result = analyze_queries(log)
    print(f"Typosquat alerts: {len(result['alerts'])}")
    for a in result["alerts"]:
        print(f"  t={a['timestamp']:7.3f}  host={a['src_ip']:12}  "
              f"query={a['domain']:18}  closest_brand={a['closest_brand']:15}  "
              f"edit_distance={a['edit_distance']}")
