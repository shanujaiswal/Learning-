### Kerberos Delegation Attacks

--> LEGAL/ETHICAL REMINDER: everything below is for authorized environments only - your own lab (e.g. a self-hosted AD lab, GOAD, HackTheBox/TryHackMe AD boxes) or engagements with signed written permission. Abusing delegation on a real domain without authorization is a serious crime (Computer Misuse Act, CFAA, equivalents). Always have written scope/rules of engagement before touching a real domain.

--> This note assumes you already understand the Kerberos AS/TGS flow from note 10. It builds on that toward delegation - a legitimate AD feature that, misconfigured, becomes one of the cleanest privilege-escalation paths in modern AD.

## The Double-Hop Problem Delegation Solves

--> A user authenticates to a front-end service (e.g. a web app on IIS) with Kerberos. That front-end now needs to act AS the user against a back-end service (e.g. a SQL Server) - but the front-end only received a service ticket for ITSELF, not a general-purpose credential it can reuse elsewhere. Without delegation, the back-end call would have to happen under the front-end's OWN identity, breaking auditing/access-control expectations (the SQL server sees "IIS_service_account" instead of the actual end user).
--> Delegation lets an administrator explicitly configure a service account to be trusted to act on behalf of users who authenticate to it, solving this "double hop."

## Unconstrained Delegation

--> When a computer account is marked "Trusted for Delegation" (unconstrained), something powerful and dangerous happens under the hood: whenever ANY user's ticket is presented to that machine with the ticket's "forwardable" flag set (the default), the KDC actually embeds a COPY OF THE USER'S FULL TGT inside the service ticket sent to that machine, and the machine caches it.
--> Practical impact: if an attacker gets local admin (or SYSTEM) on a host configured for unconstrained delegation, they can extract cached TGTs from LSASS for EVERY user who has authenticated to that host since it started - including, if a Domain Admin happens to browse a file share or print to that host even once, the DA's full TGT. That TGT can be replayed directly (Pass-the-Ticket, note 10) to become that user.
--> This is why unconstrained delegation on ANY machine other than domain controllers themselves (which need it for their own operation) is considered a serious misconfiguration - it's an implicit invitation for high-privilege accounts to "walk into" a compromised low-value host.

```bash
# Find computers configured for unconstrained delegation
Get-DomainComputer -Unconstrained  # PowerView
# or with Impacket/ldapsearch, filter on userAccountControl bit
# TRUSTED_FOR_DELEGATION (0x80000)

# Coerce a high-value account to authenticate to the compromised unconstrained
# host (e.g. force the DC itself to connect back, via PetitPotam/PrinterBug),
# then harvest the resulting TGT from LSASS
Rubeus.exe monitor /interval:5 /filteruser:DC01$
```

## Constrained Delegation - S4U2Self and S4U2Proxy

--> Constrained delegation restricts WHICH services an account may delegate to (`msDS-AllowedToDelegateTo` on the account), removing the "cache everyone's TGT" danger of unconstrained delegation - but introduces its own abuse surface via two S4U (Service-for-User) extensions:
1. S4U2Self - lets a service request a ticket to ITSELF on behalf of an arbitrary user, without needing that user's TGT at all (this is how the front-end "becomes" the user in the first place without the user handing over credentials).
2. S4U2Proxy - takes the ticket from S4U2Self and exchanges it for a service ticket to one of the ALLOWED target services listed in `msDS-AllowedToDelegateTo`, impersonating the original user against that target.
--> Abuse case: if an attacker compromises an account that's permitted constrained delegation to Service A, and "protocol transition" isn't explicitly restricted (`TRUSTED_TO_AUTH_FOR_DELEGATION` UAC flag being present vs a resource-based-only trust), the attacker can call S4U2Self for ANY user (even a Domain Admin, since S4U2Self doesn't validate that the target user actually authenticated), then S4U2Proxy to obtain a service ticket to the allowed target AS that Domain Admin - full impersonation without ever touching the DA's credentials.

```bash
# Impacket getST.py performs the full S4U2Self + S4U2Proxy chain
getST.py -spn 'CIFS/fileserver.corp.local' -impersonate administrator \
    'corp.local/svc_web:Password123'

# Rubeus equivalent, single command for the whole chain
Rubeus.exe s4u /user:svc_web /rc4:<ntlm_hash> /impersonateuser:administrator \
    /msdsspn:CIFS/fileserver.corp.local /ptt
```

## Resource-Based Constrained Delegation (RBCD)

--> RBCD flips WHERE the delegation trust is configured: instead of the source account listing what it may delegate TO, the TARGET object lists who is allowed to delegate TO IT, via the attribute `msDS-AllowedToActOnBehalfOfOtherIdentity`.
--> The abuse chain that made RBCD famous: writing to that attribute only requires WRITE access on the target computer object - and by default, every domain user can create up to 10 new computer objects (`MachineAccountQuota`, often left at its default of 10). An attacker with zero special privileges can:
1. Create a new computer account they fully control (via `MachineAccountQuota`).
2. Find (or be granted, via a separate misconfiguration) write access to a valuable target computer object's `msDS-AllowedToActOnBehalfOfOtherIdentity` attribute.
3. Configure that attribute so their own newly created computer account is trusted to delegate to the target.
4. Run the S4U2Self/S4U2Proxy chain FROM their controlled computer account, impersonating any user (including a Domain Admin) against the target - full takeover of the target host's services.

```bash
# Create a controlled computer account (uses default MachineAccountQuota=10)
addcomputer.py -computer-name 'PWNED$' -computer-pass 'Passw0rd!' corp.local/alice:'Password123'

# Configure RBCD on the target so PWNED$ can delegate to it
rbcd.py -delegate-to 'TARGET$' -delegate-from 'PWNED$' -action write \
    corp.local/alice:'Password123'

# Now perform the S4U chain from the controlled account, impersonating a DA
getST.py -spn 'CIFS/target.corp.local' -impersonate administrator \
    -hashes :<pwned_ntlm_hash> corp.local/PWNED\$
```

## Mitigations

--> 1. Audit for accounts/computers with unconstrained delegation (`Get-DomainComputer -Unconstrained`) and remove the flag unless there's a hard operational requirement - migrate to constrained or resource-based delegation instead.
--> 2. Place sensitive/privileged accounts in the built-in `Protected Users` security group - members of this group cannot have their credentials delegated via any of the mechanisms above (their TGTs are non-forwardable, non-delegatable by design).
--> 3. Set `MachineAccountQuota` to 0 domain-wide unless there's a specific need for self-service computer joins, closing off the cheapest step of the RBCD chain.
--> 4. Audit write access on computer objects' `msDS-AllowedToActOnBehalfOfOtherIdentity` and on `msDS-AllowedToDelegateTo` regularly with BloodHound, treating unexpected write edges into either as a high-priority finding.

--> Putting it together: delegation abuse is the natural "what next" after gaining any foothold that grants object-write rights or local admin on a poorly-scoped host - it converts a narrow compromise into full impersonation of arbitrary users without ever cracking a password or forging a Golden Ticket. See note 34 for DCSync as the technique typically chained AFTER reaching DA-equivalent access via delegation abuse, and note 32 for the certificate-based route to the same outcome.
