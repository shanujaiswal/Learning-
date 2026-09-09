"""
vendor_registry.py

The "system of record" for third-party vendors under assessment: for each
vendor, a security-questionnaire response (self-attested by the vendor) plus
the data-access scope our own systems actually grant them.

Real-world equivalent: a Vendor Risk Management (VRM) intake system / GRC
tool (e.g. OneTrust, ServiceNow VRM, Vanta) that stores questionnaire
responses alongside a data-flow map of what each vendor integration touches.
"""

from dataclasses import dataclass

from data_sensitivity_model import DataAccessScope, DataType
from questionnaire_scorer import QuestionnaireResponse


@dataclass(frozen=True)
class Vendor:
    vendor_id: str
    name: str
    category: str                          # what kind of service they provide
    questionnaire: QuestionnaireResponse
    data_scope: DataAccessScope


VENDORS = [
    Vendor(
        vendor_id="V001",
        name="PayStream Payroll Processing",
        category="Payroll & benefits administration",
        questionnaire=QuestionnaireResponse(
            encryption_at_rest=True,
            mfa_enforced=True,
            has_soc2=False,
            breach_in_last_2_years=False,
            subprocessor_count=3,
        ),
        # Payroll vendor: SSNs, bank account numbers, AND health-benefits data.
        data_scope=DataAccessScope(data_type=DataType.PCI_PHI, record_count=42_000),
    ),
    Vendor(
        vendor_id="V002",
        name="BrightLeaf Newsletter Co",
        category="Email marketing / newsletter platform",
        questionnaire=QuestionnaireResponse(
            encryption_at_rest=True,
            mfa_enforced=True,
            has_soc2=False,
            breach_in_last_2_years=False,
            subprocessor_count=3,
        ),
        # IDENTICAL questionnaire answers to V001, but only ever sees email
        # addresses -- deliberately set up for the side-by-side demo in main.py.
        data_scope=DataAccessScope(data_type=DataType.PII, record_count=42_000),
    ),
    Vendor(
        vendor_id="V003",
        name="ClearRoute Cloud Hosting",
        category="Infrastructure-as-a-Service provider",
        questionnaire=QuestionnaireResponse(
            encryption_at_rest=True,
            mfa_enforced=True,
            has_soc2=True,
            breach_in_last_2_years=False,
            subprocessor_count=2,
        ),
        data_scope=DataAccessScope(data_type=DataType.PCI, record_count=2_500_000),
    ),
    Vendor(
        vendor_id="V004",
        name="QuickBooks-Alike Invoicing Startup",
        category="Small-business invoicing SaaS",
        questionnaire=QuestionnaireResponse(
            encryption_at_rest=False,
            mfa_enforced=False,
            has_soc2=False,
            breach_in_last_2_years=True,
            subprocessor_count=9,
        ),
        data_scope=DataAccessScope(data_type=DataType.PCI, record_count=8_000),
    ),
    Vendor(
        vendor_id="V005",
        name="MedSync Health Records API",
        category="Patient records integration vendor",
        questionnaire=QuestionnaireResponse(
            encryption_at_rest=True,
            mfa_enforced=False,
            has_soc2=False,
            breach_in_last_2_years=True,
            subprocessor_count=4,
        ),
        data_scope=DataAccessScope(data_type=DataType.PHI, record_count=310_000),
    ),
    Vendor(
        vendor_id="V006",
        name="OfficeSupplyDirect",
        category="Office supplies ordering portal",
        questionnaire=QuestionnaireResponse(
            encryption_at_rest=False,
            mfa_enforced=False,
            has_soc2=False,
            breach_in_last_2_years=False,
            subprocessor_count=1,
        ),
        data_scope=DataAccessScope(data_type=DataType.NONE, record_count=0),
    ),
    Vendor(
        vendor_id="V007",
        name="AnalyticsAggregate Insights",
        category="Aggregated product-usage analytics",
        questionnaire=QuestionnaireResponse(
            encryption_at_rest=True,
            mfa_enforced=True,
            has_soc2=False,
            breach_in_last_2_years=False,
            subprocessor_count=5,
        ),
        data_scope=DataAccessScope(data_type=DataType.ANONYMIZED, record_count=5_000_000),
    ),
]


def get_vendor(vendor_id: str) -> Vendor:
    for vendor in VENDORS:
        if vendor.vendor_id == vendor_id:
            return vendor
    raise KeyError(f"No vendor registered with id {vendor_id!r}")
