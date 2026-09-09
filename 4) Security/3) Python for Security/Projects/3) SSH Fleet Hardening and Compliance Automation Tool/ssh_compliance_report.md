# SSH Fleet Compliance Report

Generated: 2026-08-17 05:21 UTC

Fleet size: 15 hosts | Average compliance score: **83.3/100** | Fully compliant hosts: 7/15

## Worst Offenders

| Rank | Host | Score | Failed Rules |
|------|------|-------|---------------|
| 1 | bastion-07.fleet.internal | 0.0 | CIS-5.2.8, CIS-5.2.10, CIS-5.2.13, CIS-5.2.2, CIS-5.2.20 |
| 2 | monitor-08.fleet.internal | 66.7 | CIS-5.2.8, CIS-5.2.20 |
| 3 | db-02.fleet.internal | 75.0 | CIS-5.2.8 |

## Per-Host Findings and Remediation

### web-01.fleet.internal (10.2.84.200) — score 100.0/100

All benchmark rules passed. No remediation required.


### db-02.fleet.internal (10.2.196.164) — score 75.0/100

**Failed checks:**

- `CIS-5.2.8` [CRITICAL] Ensure SSH root login is disabled
  - Finding: PermitRootLogin is 'yes' — root login is not fully disabled.

**Remediation — apply these lines in `/etc/ssh/sshd_config`:**

```
PermitRootLogin no
```

**Commands to apply and reload:**

```bash
# On db-02.fleet.internal, back up first:
sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak.$(date +%s)
sudo sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo sshd -t   # validate config syntax before reloading
sudo systemctl reload sshd
```

### cache-03.fleet.internal (10.2.157.102) — score 75.0/100

**Failed checks:**

- `CIS-5.2.10` [CRITICAL] Ensure SSH PasswordAuthentication is disabled
  - Finding: PasswordAuthentication is 'yes' — passwords are accepted alongside/instead of keys.

**Remediation — apply these lines in `/etc/ssh/sshd_config`:**

```
PasswordAuthentication no
```

**Commands to apply and reload:**

```bash
# On cache-03.fleet.internal, back up first:
sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak.$(date +%s)
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sshd -t   # validate config syntax before reloading
sudo systemctl reload sshd
```

### queue-04.fleet.internal (10.1.186.29) — score 83.3/100

**Failed checks:**

- `CIS-5.2.13` [HIGH] Ensure only strong ciphers are used
  - Finding: Weak/legacy cipher(s) configured: aes256-cbc, 3des-cbc.

**Remediation — apply these lines in `/etc/ssh/sshd_config`:**

```
Ciphers aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com
```

**Commands to apply and reload:**

```bash
# On queue-04.fleet.internal, back up first:
sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak.$(date +%s)
sudo sed -i 's/^#\?Ciphers.*/Ciphers aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com/' /etc/ssh/sshd_config
sudo sshd -t   # validate config syntax before reloading
sudo systemctl reload sshd
```

### app-05.fleet.internal (10.3.204.232) — score 75.0/100

**Failed checks:**

- `CIS-5.2.2` [CRITICAL] Ensure SSH Protocol is not set to 1
  - Finding: Protocol 1 configured — SSH-1 is cryptographically broken and must not be used.

**Remediation — apply these lines in `/etc/ssh/sshd_config`:**

```
Protocol 2
```

**Commands to apply and reload:**

```bash
# On app-05.fleet.internal, back up first:
sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak.$(date +%s)
sudo sed -i 's/^#\?Protocol.*/Protocol 2/' /etc/ssh/sshd_config
sudo sshd -t   # validate config syntax before reloading
sudo systemctl reload sshd
```

### lb-06.fleet.internal (10.0.178.229) — score 91.7/100

**Failed checks:**

- `CIS-5.2.20` [MEDIUM] Ensure SSH access is limited via AllowUsers/AllowGroups
  - Finding: No AllowUsers or AllowGroups configured — any account on the host can attempt SSH login.

**Remediation — apply these lines in `/etc/ssh/sshd_config`:**

```
AllowUsers <explicit list of usernames, no wildcards>
```

**Commands to apply and reload:**

```bash
# On lb-06.fleet.internal, back up first:
sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak.$(date +%s)
sudo sed -i 's/^#\?AllowUsers.*/AllowUsers <explicit list of usernames, no wildcards>/' /etc/ssh/sshd_config
sudo sshd -t   # validate config syntax before reloading
sudo systemctl reload sshd
```

### bastion-07.fleet.internal (10.3.206.207) — score 0.0/100

**Failed checks:**

- `CIS-5.2.8` [CRITICAL] Ensure SSH root login is disabled
  - Finding: PermitRootLogin is 'yes' — root login is not fully disabled.
- `CIS-5.2.10` [CRITICAL] Ensure SSH PasswordAuthentication is disabled
  - Finding: PasswordAuthentication is 'yes' — passwords are accepted alongside/instead of keys.
- `CIS-5.2.13` [HIGH] Ensure only strong ciphers are used
  - Finding: Weak/legacy cipher(s) configured: arcfour, blowfish-cbc.
- `CIS-5.2.2` [CRITICAL] Ensure SSH Protocol is not set to 1
  - Finding: Protocol 1 configured — SSH-1 is cryptographically broken and must not be used.
- `CIS-5.2.20` [MEDIUM] Ensure SSH access is limited via AllowUsers/AllowGroups
  - Finding: No AllowUsers or AllowGroups configured — any account on the host can attempt SSH login.

**Remediation — apply these lines in `/etc/ssh/sshd_config`:**

```
PermitRootLogin no
PasswordAuthentication no
Ciphers aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com
Protocol 2
AllowUsers <explicit list of usernames, no wildcards>
```

**Commands to apply and reload:**

```bash
# On bastion-07.fleet.internal, back up first:
sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak.$(date +%s)
sudo sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?Ciphers.*/Ciphers aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?Protocol.*/Protocol 2/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?AllowUsers.*/AllowUsers <explicit list of usernames, no wildcards>/' /etc/ssh/sshd_config
sudo sshd -t   # validate config syntax before reloading
sudo systemctl reload sshd
```

### monitor-08.fleet.internal (10.2.9.215) — score 66.7/100

**Failed checks:**

- `CIS-5.2.8` [CRITICAL] Ensure SSH root login is disabled
  - Finding: PermitRootLogin is 'prohibit-password' — root login is not fully disabled.
- `CIS-5.2.20` [MEDIUM] Ensure SSH access is limited via AllowUsers/AllowGroups
  - Finding: AllowUsers/AllowGroups is set to a wildcard ('*') — equivalent to no restriction at all.

**Remediation — apply these lines in `/etc/ssh/sshd_config`:**

```
PermitRootLogin no
AllowUsers <explicit list of usernames, no wildcards>
```

**Commands to apply and reload:**

```bash
# On monitor-08.fleet.internal, back up first:
sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak.$(date +%s)
sudo sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?AllowUsers.*/AllowUsers <explicit list of usernames, no wildcards>/' /etc/ssh/sshd_config
sudo sshd -t   # validate config syntax before reloading
sudo systemctl reload sshd
```

### build-09.fleet.internal (10.2.60.119) — score 83.3/100

**Failed checks:**

- `CIS-5.2.13` [HIGH] Ensure only strong ciphers are used
  - Finding: Weak/legacy cipher(s) configured: cast128-cbc.

**Remediation — apply these lines in `/etc/ssh/sshd_config`:**

```
Ciphers aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com
```

**Commands to apply and reload:**

```bash
# On build-09.fleet.internal, back up first:
sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak.$(date +%s)
sudo sed -i 's/^#\?Ciphers.*/Ciphers aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com/' /etc/ssh/sshd_config
sudo sshd -t   # validate config syntax before reloading
sudo systemctl reload sshd
```

### vpn-10.fleet.internal (10.1.87.205) — score 100.0/100

All benchmark rules passed. No remediation required.


### mail-11.fleet.internal (10.2.234.156) — score 100.0/100

All benchmark rules passed. No remediation required.


### storage-12.fleet.internal (10.2.102.142) — score 100.0/100

All benchmark rules passed. No remediation required.


### auth-13.fleet.internal (10.0.22.20) — score 100.0/100

All benchmark rules passed. No remediation required.


### backup-14.fleet.internal (10.3.27.174) — score 100.0/100

All benchmark rules passed. No remediation required.


### dns-15.fleet.internal (10.0.100.52) — score 100.0/100

All benchmark rules passed. No remediation required.


## Key Hygiene Findings

Scanned **51** authorized_keys entries across the fleet.

### Duplicate keys reused across accounts (1)

- **ssh-rsa oK1H252MCZUA...jed6Ck** installed on 3 accounts: `svc-backup@queue-04.fleet.internal`, `operator@vpn-10.fleet.internal`, `deploy@web-01.fleet.internal`

**Remediation:** generate a distinct key per account/host, distribute the new public keys, then revoke the shared key everywhere it appears:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/svc-backup_queue-04_ed25519 -C 'svc-backup@queue-04.fleet.internal'
ssh-keygen -t ed25519 -f ~/.ssh/operator_vpn-10_ed25519 -C 'operator@vpn-10.fleet.internal'
ssh-keygen -t ed25519 -f ~/.ssh/deploy_web-01_ed25519 -C 'deploy@web-01.fleet.internal'
# then remove the old shared key's line from each account's authorized_keys:
sudo sed -i '/oK1H252MCZUA4f9E/d' /home/svc-backup/.ssh/authorized_keys  # on queue-04.fleet.internal
sudo sed -i '/oK1H252MCZUA4f9E/d' /home/operator/.ssh/authorized_keys  # on vpn-10.fleet.internal
sudo sed -i '/oK1H252MCZUA4f9E/d' /home/deploy/.ssh/authorized_keys  # on web-01.fleet.internal
```

### Unlabeled keys — no owner identification (2)

- `admin@lb-06.fleet.internal` — ecdsa-sha2-nistp256 JMCivtAiunTV...6gCKAr (empty comment field)
- `ec2-user@mail-11.fleet.internal` — ssh-rsa n2G+1bD9bBGa...NqiBJ3 (empty comment field)

**Remediation:** identify the owner (check deployment/onboarding records), then append an identifying comment or remove the key if the owner cannot be confirmed:

```bash
# on lb-06.fleet.internal: confirm the owner, then either label it —
sudo sed -i 's|JMCivtAiunTV9vCl.*|& owner-confirmed@lb-06.fleet.internal|' /home/admin/.ssh/authorized_keys
# — or remove it if unowned:
sudo sed -i '/JMCivtAiunTV9vCl/d' /home/admin/.ssh/authorized_keys
# on mail-11.fleet.internal: confirm the owner, then either label it —
sudo sed -i 's|n2G+1bD9bBGaa3qG.*|& owner-confirmed@mail-11.fleet.internal|' /home/ec2-user/.ssh/authorized_keys
# — or remove it if unowned:
sudo sed -i '/n2G+1bD9bBGaa3qG/d' /home/ec2-user/.ssh/authorized_keys
```
