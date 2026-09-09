# Privacy vs Security -- Related but Distinct Goals

--> Security is about protecting data from unauthorized access. Privacy is about respecting what data is collected, how it's used, and who it's shared with in the first place -- a system can be perfectly SECURE (no breach, no unauthorized access) while still being a PRIVACY problem (collecting far more personal data than needed, or using it in ways users never actually agreed to).
--> This file builds directly on the GRC file's coverage of GDPR/HIPAA/PCI-DSS as legal frameworks -- here covering the actual ENGINEERING techniques used to satisfy those requirements in practice, not just the compliance obligations themselves.

# Privacy by Design -- Building It In, Not Bolting It On

--> A foundational principle (embedded directly into GDPR) that privacy protections should be considered from the START of a system's design, not retrofitted after the fact -- a data model, database schema, and logging strategy are all far easier to make privacy-respecting from day one than to fix after years of collecting more than necessary.

# Data Minimization

--> Collect ONLY the personal data actually necessary for a specific, defined purpose -- not "it might be useful someday." Every additional field of personal data collected is additional risk (a bigger breach impact, more compliance obligation) for often marginal benefit.
--> A practical discipline -- for every new personal data field a product wants to collect, ask what SPECIFIC feature/purpose requires it, and whether that purpose can be achieved with less identifying information.

# Anonymization vs Pseudonymization

--> **Anonymization** -- data is altered so it can NEVER be linked back to a specific individual, even in combination with other data -- once properly and irreversibly anonymized, data generally falls outside many privacy regulations' scope entirely, since it's no longer "personal data" about anyone.
--> **Pseudonymization** -- direct identifiers (name, email) are replaced with a pseudonym/token (echoing the Tokenization concept from the Cryptography track), but the data CAN still be re-linked to the individual using a separate, protected mapping -- weaker protection than true anonymization, since re-identification remains possible if that mapping is compromised, but still a meaningful risk reduction and often required/recommended by GDPR specifically.

```python
# Pseudonymization example -- replacing a direct identifier with a token
def pseudonymize_user_record(record, mapping_vault):
    token = mapping_vault.get_or_create_token(record["email"])
    record["email"] = token   # The real email now lives only in the protected vault
    return record
```

--> True anonymization is genuinely harder to achieve correctly than it sounds -- famous re-identification research has repeatedly shown that combining several supposedly "anonymized" fields (zip code, birthdate, gender) can uniquely re-identify a specific individual even without any direct identifier present at all.

# Differential Privacy

--> A more rigorous, mathematically-grounded approach to anonymization -- deliberately injecting calibrated statistical noise into aggregate data/query results, so individual records can't be reliably distinguished from the noise, while aggregate patterns across the whole dataset remain accurate and useful.
--> Used by organizations doing large-scale data analysis (Apple and the US Census Bureau are notable real-world adopters) where genuinely rigorous privacy guarantees for individuals matter alongside still needing useful aggregate statistics.

# Consent Management

--> GDPR and similar regulations require tracking WHAT a user actually consented to, WHEN, and providing a real mechanism to withdraw that consent -- consent management platforms handle this as a structured, auditable record rather than an assumed, undocumented "they clicked accept once."

# The Right to Erasure -- Engineering Implications

--> GDPR's "right to be forgotten" sounds simple in policy but is genuinely hard to implement correctly in a real system -- personal data often exists in the primary database, backups, logs, analytics pipelines, and third-party vendor systems (connecting to the Vendor Risk Management file) simultaneously, and a proper erasure request needs to actually reach ALL of those locations, not just the obvious primary record.

# Privacy Impact Assessments (PIAs / DPIAs)

--> A structured process (required under GDPR for higher-risk processing activities) evaluating a NEW system or feature's privacy implications BEFORE it launches -- essentially privacy's equivalent of a security threat model, assessing what personal data will be processed, why, and what risks that introduces, early enough to actually change the design if needed.
