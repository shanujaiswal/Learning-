# DNS Security Monitor -- Tunneling and Spoofing Detection

## Real-world scenario

A SOC analyst (or an appliance like Cisco Umbrella, Zeek, or a Suricata DNS
rule set) watches a continuous stream of DNS query/response logs off a
corporate resolver. Buried in thousands of routine lookups, three very
different attack shapes can appear:

1. **DNS tunneling / data exfiltration.** Malware on an infected host can't
   reach the internet directly past the firewall, but DNS (port 53) is
   almost always allowed out. So it encodes stolen data into subdomain
   labels (`d0f8a3b9c1...long-random-string.attacker-domain.com`) and
   "asks" the attacker's own authoritative nameserver to resolve them --
   the query itself carries the payload. The tell: abnormally long,
   high-entropy (near-random) labels, and a lot of them, from one host to
   one domain, in a short window.

2. **DNS cache poisoning / spoofing.** An attacker races the legitimate
   authoritative server, flooding a resolver with forged responses that
   guess at the transaction ID (and often the source port) of a query it
   never sent an answer for yet. If a forged response's guessed ID happens
   to match before the real answer arrives, the resolver caches a
   malicious IP for a real, trusted domain name. The tell: a burst of
   responses to the *same* query name, arriving close together, with
   transaction IDs that overwhelmingly disagree with the ID the client
   actually used -- and usually disagreeing answer IPs too.

3. **Typosquatting / lookalike domains.** Phishing infrastructure registers
   domains that look almost identical to a real brand at a glance --
   `paypa1.com` (digit `1` for letter `l`), `gooogle.com` (extra `o`),
   `micosoft.com` (dropped letter). A user mistypes a URL, or clicks a
   phishing link, and the resolver dutifully looks it up. The tell: the
   queried domain is one or two character edits away from a known,
   high-value brand domain -- but isn't an exact match.

This project builds a fully offline, synthetic DNS log (fixed random seed,
no real network calls) containing normal background traffic plus one
injected example of each attack, then runs three independent detectors over
it and reports what each one finds.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `dns_log_generator.py` | Generates the synthetic, time-ordered DNS query/response log (normal traffic + 3 injected attacks), fixed seed for reproducibility | A packet capture / resolver query log (e.g. `dns.log` from a Zeek sensor, or a Wireshark DNS capture) |
| `entropy_analysis.py` | Shannon entropy + label-length scoring, escalated to an alert by per-host/per-domain query volume in a time window | DNS tunneling detection the way Cisco Umbrella, Zeek's `dns` analyzer, or Palo Alto DNS Security score query names for exfiltration/C2 |
| `spoofing_detector.py` | Groups responses by query name, clusters them into time-bounded bursts, flags bursts where transaction IDs mostly mismatch the client's real query ID | Anti-spoofing / cache-poisoning detection the way resolver hardening (0x20 encoding, source-port randomization checks) and IDS signatures (Suricata `dns_event` rules) work |
| `typosquat_detector.py` | Levenshtein edit-distance check of every queried domain against a small known-brand list | Brand-protection / lookalike-domain monitoring (Netcraft typosquat feeds, browser "Did you mean google.com?" warnings) |
| `main.py` | Runs the log through all three detectors, prints a merged time-ordered alert feed, a summary (counts per attack type + false-positive check), and saves a query-volume chart with flagged windows highlighted | A SIEM dashboard correlating multiple detection engines into one alert feed |

## Run it

Requires Python 3.9+, `numpy`, and `matplotlib` (stdlib otherwise).

```bash
python main.py
```

Each module is also independently runnable for isolated testing:

```bash
python dns_log_generator.py     # inspect the raw synthetic log
python entropy_analysis.py      # tunneling detector only
python spoofing_detector.py     # spoofing detector only
python typosquat_detector.py    # typosquat detector only
```

## Verified result (actual output of `python main.py`)

```
==============================================================================
DNS SECURITY MONITOR -- ALERT FEED
==============================================================================
[t=  24.96s] [TYPOSQUAT ] host 10.0.0.9 queried 'paypa1.com' (edit distance 1 from known brand 'paypal.com')
[t=  29.13s] [TYPOSQUAT ] host 10.0.0.4 queried 'gooogle.com' (edit distance 1 from known brand 'google.com')
[t=  37.19s] [TYPOSQUAT ] host 10.0.0.15 queried 'linkedln.com' (edit distance 1 from known brand 'linkedin.com')
[t=  40.00s] [TUNNELING ] host 10.0.0.77 -> datax-relay.net: 8 high-entropy/long labels within 2.7s (e.g. rfaufeitiskde0adh3c9u695...)
[t=  53.42s] [TYPOSQUAT ] host 10.0.0.6 queried 'netfliix.com' (edit distance 1 from known brand 'netflix.com')
[t=  58.98s] [TYPOSQUAT ] host 10.0.0.11 queried 'micosoft.com' (edit distance 1 from known brand 'microsoft.com')
[t=  75.01s] [SPOOFING  ] query bank-secure-login.com from 10.0.0.13: 26 responses in 0.15s, 26 distinct txn IDs, 26 distinct answer IPs (mismatch ratio 0.96)

==============================================================================
SUMMARY
==============================================================================
Total DNS records processed : 562
Tunneling alerts            : 1
Spoofing alerts             : 1
Typosquat alerts            : 5
False positives on normal traffic : 0
  (none -- all alerts trace back to the injected attack patterns)

Saved chart -> dns_monitor_result.png
```

All three injected attack types were caught (1 tunneling alert on the exact
attacker host/domain, 1 spoofing burst on the exact victim query, and 5/5
typosquat lookups against the exact lookalike domains injected), with **zero
false positives** on the 220 records of normal background traffic.

`dns_monitor_result.png` plots DNS query volume in 2-second buckets across
the full 120-second capture, with the tunneling window shaded orange, the
spoofing burst shaded red, and each typosquat query marked with a dashed
purple line -- so the three attack shapes are visually distinguishable from
ordinary traffic even before reading the alert feed.

## Things to try changing

- **Raise `LABEL_LENGTH_THRESHOLD` / `ENTROPY_THRESHOLD`** in
  `entropy_analysis.py` and watch the tunneling alert disappear once the
  injected labels no longer clear the bar -- then loosen `VOLUME_THRESHOLD`
  to see how few queries it takes before a normal high-entropy CDN/DKIM
  label (there are some in the normal traffic pool) starts to false-positive.
- **Lower `MISMATCH_RATIO_THRESHOLD`** in `spoofing_detector.py` towards 0 --
  a legitimate resolver retry (same query, new transaction ID after a
  timeout) can then start looking like spoofing; this is the real-world
  tradeoff behind tuning anti-spoofing heuristics.
- **Increase `MAX_EDIT_DISTANCE`** in `typosquat_detector.py` to 3 -- see
  which additional normal domains from `NORMAL_DOMAINS` start getting
  flagged as accidental "lookalikes" of a brand, illustrating why real
  brand-protection systems combine edit distance with visual/homoglyph
  similarity rather than edit distance alone.
- **Change `SEED` in `dns_log_generator.py`** to get a different random
  layout of normal traffic (attack injections stay structurally the same)
  and confirm the detectors still fire correctly and without new false
  positives.
- **Add a second tunneling host** or a second spoofing victim query in
  `dns_log_generator.py` and confirm `main.py`'s alert feed and summary
  correctly report multiple independent incidents of the same attack type.
