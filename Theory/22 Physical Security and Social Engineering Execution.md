# Why Physical/Social Testing Is Part of a Real Assessment

--> Every technical control covered elsewhere in this track (firewalls, WAFs, patched software) can be irrelevant if an attacker simply walks into the building and plugs a device into an open network port, or convinces an employee to hand over a password on the phone. The Cyber Security track's Malware Types and Social Engineering file covers these attacks CONCEPTUALLY, from the defensive/awareness angle -- this file covers how an authorized red team engagement actually EXECUTES them.

# Rules of Engagement -- Even More Critical Here

--> Physical and social engineering testing carries real risk of alarming employees, involving actual law enforcement, or crossing into genuinely illegal territory if not scoped with extreme care -- the Rules of Engagement document (referenced in the Fundamentals and Methodology file) must explicitly define: which buildings/locations are in scope, what happens if testers are caught (a "get out of jail free" letter carried at all times, with client contact info to verify authorization on the spot), and firm ethical boundaries (never threatening anyone, never causing actual property damage).

# Physical Entry Techniques

--> Tailgating -- following an authorized employee through a badge-controlled door without your own valid badge, relying on social politeness ("holding the door") rather than a technical bypass.
--> Badge cloning -- many RFID access badges (particularly older, unencrypted formats like basic 125kHz proximity cards) can be read and cloned with a handheld device (like a Flipper Zero or Proxmark) simply by getting it near an employee's badge briefly, without them ever noticing.
--> Lock picking / bypass -- for physical locks specifically included in scope, testing whether they resist reasonably skilled manual manipulation -- generally a smaller focus than badge/access-control weaknesses in a modern corporate environment, but still relevant for server rooms, cabinets, and secure storage.
--> Pretexting entry -- posing as a delivery person, contractor, or new employee to be let in or given access by staff who have no reason to be suspicious of a plausible cover story.

# Social Engineering Execution -- Phishing Campaigns

--> A red team phishing campaign mirrors real attacker tradecraft, but under authorization and with careful containment -- typically culminating in either credential capture (a fake login page) or execution tracking (whether a user opened an attachment/clicked a link), never actually delivering real malware.

```
Typical campaign structure:
1. Target list built from OSINT (covered in the Python for Security track's OSINT file)
2. A believable pretext email crafted, often referencing real internal context gathered during recon
3. A cloned/lookalike login page hosted on a similar-looking domain
4. Metrics tracked: open rate, click rate, credential-submission rate
5. Results reported in aggregate to the client -- never used to publicly shame individual employees
```

--> Domain typo-squatting (`micros0ft-support.com` instead of `microsoft-support.com`) and homograph attacks (using visually similar Unicode characters) are common techniques for making a phishing domain look legitimate at a glance.

# Vishing -- Voice-Based Social Engineering

--> Phone-based pretexting -- e.g. calling the IT helpdesk impersonating an employee who's "locked out" and needs a password reset, or calling an employee directly claiming to be IT support needing their credentials to "fix an issue."
--> Caller ID spoofing tools can make the call appear to originate from a legitimate internal extension, significantly increasing the pretext's credibility.

# USB Drop Attacks

--> Leaving USB drives (loaded with a benign, tracking-only payload for testing purposes -- not real malware) in parking lots, break rooms, or lobbies to test whether employees plug in and run unknown found devices, directly testing the security awareness training covered in the Cyber Security track.

# Reporting Physical/Social Findings Responsibly

--> Results should focus on PROCESS and TRAINING gaps (which controls failed, which training moments would have prevented it) rather than singling out individual employees who fell for a well-crafted pretext -- the point is improving organizational resilience, not creating a culture of blame that discourages people from reporting suspicious activity in the future.
