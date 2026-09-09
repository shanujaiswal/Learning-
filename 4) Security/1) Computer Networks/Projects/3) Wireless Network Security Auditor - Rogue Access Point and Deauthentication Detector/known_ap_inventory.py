"""
known_ap_inventory.py
----------------------
The "ground truth" corporate Wireless Intrusion Detection System (WIDS) baseline.

In a real WIDS/WIPS product (Cisco aWIPS, Aruba RFProtect, Kismet's known-network
list, etc.) a security team manually enrolls every AP they own -- its SSID, its
BSSID (the AP radio's MAC address), and the minimum encryption policy required
for that network. Anything seen over the air that doesn't match this inventory
is, by definition, either unauthorized hardware or a policy violation.

This file is that inventory. It never changes at runtime -- it is the trusted
reference the detectors compare live beacon frames against.
"""

from dataclasses import dataclass, field
from typing import Dict, List


@dataclass(frozen=True)
class ApprovedNetwork:
    ssid: str
    approved_bssids: List[str]
    required_encryption: str  # minimum acceptable encryption standard


# Rank encryption strength so "at least this strong" comparisons are easy.
ENCRYPTION_STRENGTH = {
    "OPEN": 0,   # no encryption at all
    "WEP": 1,    # broken, crackable in minutes
    "WPA": 2,    # interim, superseded
    "WPA2": 3,   # modern standard baseline
    "WPA3": 4,   # current best practice
}


# ---------------------------------------------------------------------------
# The corporate baseline, as it would appear in a WIDS enrollment database.
# ---------------------------------------------------------------------------
APPROVED_NETWORKS: Dict[str, ApprovedNetwork] = {
    "CorpNet-WiFi": ApprovedNetwork(
        ssid="CorpNet-WiFi",
        approved_bssids=[
            "AA:BB:CC:00:01:01",  # Floor 1 AP
            "AA:BB:CC:00:01:02",  # Floor 2 AP
            "AA:BB:CC:00:01:03",  # Floor 3 AP
        ],
        required_encryption="WPA2",
    ),
    "CorpNet-Guest": ApprovedNetwork(
        ssid="CorpNet-Guest",
        approved_bssids=[
            "AA:BB:CC:00:02:01",  # Lobby guest AP
        ],
        required_encryption="WPA2",
    ),
}


def all_approved_bssids() -> List[str]:
    """Flat list of every BSSID this company owns."""
    bssids: List[str] = []
    for net in APPROVED_NETWORKS.values():
        bssids.extend(net.approved_bssids)
    return bssids


def is_approved_bssid(ssid: str, bssid: str) -> bool:
    """True only if this exact BSSID is enrolled for this exact SSID."""
    net = APPROVED_NETWORKS.get(ssid)
    return net is not None and bssid in net.approved_bssids


def required_encryption_for(ssid: str) -> str:
    net = APPROVED_NETWORKS.get(ssid)
    return net.required_encryption if net else "WPA2"  # default policy


def meets_policy(ssid: str, encryption: str) -> bool:
    """Does the observed encryption meet or exceed the required policy?"""
    required = required_encryption_for(ssid)
    return ENCRYPTION_STRENGTH.get(encryption, 0) >= ENCRYPTION_STRENGTH.get(required, 3)
