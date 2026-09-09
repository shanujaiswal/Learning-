# What DLP Protects Against

--> Data Loss Prevention (DLP) is a category of tools/policies specifically focused on preventing sensitive data from leaving an organization's control -- whether through malicious exfiltration (an insider stealing data, or an attacker who's gained access), or simple, unintentional employee mistakes (accidentally emailing a spreadsheet of customer data to the wrong recipient).

# The Three DLP Enforcement Points

--> **Data in Motion** -- monitors network traffic (email, web uploads, file transfers) for sensitive data leaving the network, and can block it in real time.
--> **Data at Rest** -- scans stored data (file servers, cloud storage, databases) to discover WHERE sensitive data actually lives across the organization -- you can't protect what you don't know exists, and this discovery step is often the most revealing part of a first DLP rollout.
--> **Data in Use** -- monitors/restricts actions on an endpoint itself -- blocking a user from copying sensitive data to a USB drive, printing it, or pasting it into a personal webmail client.

# How DLP Actually Identifies "Sensitive" Data

--> Pattern matching -- regular expressions detecting structured sensitive data formats (credit card numbers, Social Security Numbers, specific ID formats).
--> Keyword/dictionary matching -- flagging documents containing specific terms ("Confidential," "Internal Only," project codenames).
--> Data fingerprinting -- registering exact known sensitive documents/database exports, so the system can detect that EXACT content leaving, even if renamed or reformatted.
--> Machine learning-based classification -- increasingly used to catch sensitive data that doesn't match a rigid pattern (e.g. identifying an unstructured document as likely containing legal/HR-sensitive content based on its content and context, not just keyword matches).

# A Practical DLP Policy Example

```
Policy: Block outbound email containing credit card number patterns
  IF email attachment or body matches: \d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}
  AND recipient domain is NOT in the approved partner list
  THEN: Block the email, notify the sender, alert the security team
```

--> This connects directly to the Format-Preserving Encryption/Tokenization content in the Cryptography track -- an organization that TOKENIZES card numbers throughout its systems (rather than storing/transmitting real ones) dramatically shrinks what a DLP policy like this even needs to catch in the first place, since real card numbers rarely exist in most systems to begin with.

# Endpoint DLP -- Controlling Data in Use

--> Endpoint DLP agents can enforce granular controls directly on a user's device -- disabling USB mass storage devices entirely, blocking copy-paste from a sensitive application into an unapproved one, or watermarking documents when printed to trace a leak back to whoever printed it.

# DLP and Insider Threats

--> DLP is one of the primary technical controls specifically relevant to the Insider Threat Management content covered elsewhere in this track -- most DLP tools can flag unusual data-access/exfiltration PATTERNS (a user suddenly downloading far more data than their normal baseline, especially shortly before a resignation) as a behavioral anomaly worth investigating, not just matching a fixed sensitive-data pattern.

# Balancing DLP Against Usability and Privacy

--> Overly aggressive DLP policies generate significant false positives, disrupting legitimate business workflows and training employees to find workarounds that undermine the control entirely -- a well-tuned DLP program requires ongoing policy refinement based on real observed false-positive/false-negative rates, not a "set it once and forget it" deployment.
--> Endpoint DLP monitoring also raises legitimate employee privacy considerations -- most organizations pair DLP deployment with a clear, disclosed acceptable-use policy so monitoring isn't a surprise, connecting to the privacy engineering and compliance considerations covered elsewhere in this track.
