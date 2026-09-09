"""
fleet_inventory.py

Generates a synthetic fleet of `sshd_config`-style records and an authorized_keys
inventory across hosts/users, entirely in memory / offline. No real network calls,
no real SSH connections — this stands in for the "connect to N hosts and pull their
config" step that a real Paramiko-based tool would perform (see Theory Ch.5).

Everything is seeded (`random.seed(FLEET_SEED)`) so the fleet is reproducible: the
same hosts, the same violations, every run. A handful of violations are injected
deliberately (commented "INJECTED VIOLATION" below) so the rest of the pipeline has
known-bad data to catch; the remaining hosts are filled in as mostly-compliant with
small seeded variation so the fleet doesn't look artificially uniform.
"""

import random

FLEET_SEED = 1337

# --- Building blocks for synthetic sshd_config records -----------------------

STRONG_CIPHERS = [
    "chacha20-poly1305@openssh.com",
    "aes256-gcm@openssh.com",
    "aes128-gcm@openssh.com",
]

WEAK_CIPHERS = [
    "3des-cbc",
    "arcfour",
    "arcfour128",
    "blowfish-cbc",
    "cast128-cbc",
    "aes128-cbc",
    "aes192-cbc",
    "aes256-cbc",
]

HOST_ROLES = [
    "web", "db", "cache", "queue", "app", "lb", "bastion", "monitor",
    "build", "vpn", "mail", "storage", "auth", "backup", "dns",
]

USERNAMES = [
    "root", "deploy", "svc-backup", "jdoe", "asmith", "svc-monitor",
    "operator", "ci-runner", "ec2-user", "admin",
]

KEY_TYPES = ["ssh-rsa", "ssh-ed25519", "ecdsa-sha2-nistp256"]


def _fake_key_material(rng: random.Random, length: int = 68) -> str:
    """Produce a base64-alphabet-looking blob standing in for real key material."""
    alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    return "".join(rng.choice(alphabet) for _ in range(length))


def _make_host(rng: random.Random, index: int, role: str) -> dict:
    hostname = f"{role}-{index:02d}.fleet.internal"
    return {
        "hostname": hostname,
        "ip": f"10.{rng.randint(0, 3)}.{rng.randint(0, 255)}.{rng.randint(2, 254)}",
        "PermitRootLogin": "no",
        "PasswordAuthentication": "no",
        "Ciphers": list(STRONG_CIPHERS),
        "Protocol": 2,
        "AllowUsers": ["deploy", "ci-runner"],
        "AllowGroups": [],
    }


def generate_fleet() -> list[dict]:
    """Return a list of per-host sshd_config-style records, fixed seed."""
    rng = random.Random(FLEET_SEED)
    fleet = []

    for i, role in enumerate(HOST_ROLES, start=1):
        fleet.append(_make_host(rng, i, role))

    # --- INJECTED VIOLATIONS: deliberately non-compliant hosts ---------------

    # 1) Root login wide open, no password-only excuse.
    fleet[1]["PermitRootLogin"] = "yes"                                  # web -> db-02
    # 2) Password auth still enabled (should be key-only).
    fleet[2]["PasswordAuthentication"] = "yes"                            # cache-03
    # 3) Weak/legacy ciphers present alongside strong ones.
    fleet[3]["Ciphers"] = ["aes256-cbc", "3des-cbc", "chacha20-poly1305@openssh.com"]  # queue-04
    # 4) Ancient Protocol 1 still configured.
    fleet[4]["Protocol"] = 1                                             # app-05
    # 5) No AllowUsers/AllowGroups restriction at all (anyone with an account can log in).
    fleet[5]["AllowUsers"] = []                                          # lb-06
    fleet[5]["AllowGroups"] = []
    # 6) Everything wrong at once — the worst offender in the fleet.
    fleet[6]["PermitRootLogin"] = "yes"                                   # bastion-07 (ironic)
    fleet[6]["PasswordAuthentication"] = "yes"
    fleet[6]["Ciphers"] = ["arcfour", "blowfish-cbc"]
    fleet[6]["Protocol"] = 1
    fleet[6]["AllowUsers"] = []
    fleet[6]["AllowGroups"] = []
    # 7) A softer, partially-compliant violation: PermitRootLogin restricted but not
    #    fully off, and a wildcard AllowUsers ("*") that defeats the point of the rule.
    fleet[7]["PermitRootLogin"] = "prohibit-password"                    # monitor-08
    fleet[7]["AllowUsers"] = ["*"]
    # 8) Single weak cipher slipped in among otherwise strong ones (easy to miss by eye).
    fleet[8]["Ciphers"] = ["chacha20-poly1305@openssh.com", "aes256-gcm@openssh.com", "cast128-cbc"]  # build-09

    return fleet


# --- authorized_keys inventory -------------------------------------------------

def generate_authorized_keys_inventory(fleet: list[dict]) -> list[dict]:
    """Return a synthetic authorized_keys inventory: one or more keys per user per host.

    Each entry: {host, user, key_type, key_material, comment}
    `comment` mirrors the trailing comment field of a real authorized_keys line
    (e.g. "jdoe@laptop"), used in practice to identify who owns a key.
    """
    rng = random.Random(FLEET_SEED + 1)
    inventory = []

    # A single "shared deploy key" that ends up duplicated on multiple accounts/hosts —
    # this is the real-world red flag key_hygiene_auditor.py is meant to catch.
    shared_deploy_key = _fake_key_material(random.Random(FLEET_SEED + 99))

    for host in fleet:
        hostname = host["hostname"]
        num_users = rng.randint(2, 4)
        users = rng.sample(USERNAMES, k=num_users)

        for user in users:
            key_type = rng.choice(KEY_TYPES)
            key_material = _fake_key_material(rng)
            comment = f"{user}@{rng.choice(['laptop', 'workstation', 'yubikey'])}"
            inventory.append({
                "host": hostname,
                "user": user,
                "key_type": key_type,
                "key_material": key_material,
                "comment": comment,
            })

    # --- INJECTED VIOLATIONS: key hygiene issues -----------------------------

    # A) The same private/public key pair reused across three different
    #    host/account combinations — a real key-reuse red flag (one leaked
    #    laptop compromises three accounts at once).
    reuse_targets = [
        (fleet[0]["hostname"], "deploy"),
        (fleet[3]["hostname"], "svc-backup"),
        (fleet[9]["hostname"], "operator"),
    ]
    for host_name, user in reuse_targets:
        inventory.append({
            "host": host_name,
            "user": user,
            "key_type": "ssh-rsa",
            "key_material": shared_deploy_key,
            "comment": "deploy-automation",
        })

    # B) Keys installed with no identifying comment at all — impossible to
    #    tell whose key it is or whether it should still be trusted.
    unlabeled_targets = [
        (fleet[5]["hostname"], "admin"),
        (fleet[10]["hostname"], "ec2-user"),
    ]
    for host_name, user in unlabeled_targets:
        inventory.append({
            "host": host_name,
            "user": user,
            "key_type": rng.choice(KEY_TYPES),
            "key_material": _fake_key_material(rng),
            "comment": "",
        })

    return inventory


if __name__ == "__main__":
    fleet = generate_fleet()
    keys = generate_authorized_keys_inventory(fleet)
    print(f"Generated {len(fleet)} hosts and {len(keys)} authorized_keys entries.")
    for host in fleet:
        print(f"  {host['hostname']:24s} root={host['PermitRootLogin']:16s} "
              f"pwauth={host['PasswordAuthentication']:4s} proto={host['Protocol']}")
