"""
phishing_templates.py

Simulated phishing email templates used only for internal awareness-training
campaigns. These are never sent anywhere -- they exist purely as text/metadata
consumed by campaign_simulator.py to model how convincing a lure is.

Each template has a `difficulty` weight in [0, 1]: how convincing/realistic
it is. Higher difficulty = more employees are expected to fall for it.
This mirrors the tiered template libraries in real products like KnowBe4 or
Proofpoint Security Awareness Training, which offer templates ranging from
obviously-fake to highly-targeted spear-phishing lookalikes.
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class PhishingTemplate:
    template_id: str
    name: str
    subject: str
    difficulty: float          # 0.0 (obvious) .. 1.0 (highly convincing)
    lure_type: str             # category of social-engineering pretext
    red_flags: list            # what SHOULD have tipped off a careful reader
    body_preview: str


TEMPLATES: list[PhishingTemplate] = [
    PhishingTemplate(
        template_id="TPL-01",
        name="You Won a Prize",
        subject="CONGRATULATIONS!!! You have WON a $1000 Gift Card!!",
        difficulty=0.10,
        lure_type="prize / too-good-to-be-true",
        red_flags=[
            "Excessive punctuation and urgency",
            "Unsolicited prize you never entered for",
            "Generic greeting ('Dear Winner')",
            "Suspicious shortened link domain",
        ],
        body_preview=(
            "Dear Winner, You have been randomly selected to receive a "
            "$1000 gift card! Click here within 24 hours to claim your "
            "prize before it expires!!"
        ),
    ),
    PhishingTemplate(
        template_id="TPL-02",
        name="Package Delivery Failed",
        subject="Delivery Attempt Failed - Action Required",
        difficulty=0.35,
        lure_type="delivery notification",
        red_flags=[
            "No tracking number referenced",
            "Generic courier name, mismatched sender domain",
            "Pressure to 'reschedule' within a short window",
        ],
        body_preview=(
            "We attempted to deliver your package today but no one was "
            "available. Please confirm your delivery address to "
            "reschedule within 48 hours."
        ),
    ),
    PhishingTemplate(
        template_id="TPL-03",
        name="Shared Document Notification",
        subject="A file has been shared with you: 'Q3_Budget_Review.xlsx'",
        difficulty=0.55,
        lure_type="fake document share",
        red_flags=[
            "Sender is not someone you normally collaborate with",
            "Login page requests full credentials, not SSO",
            "Hover-over link doesn't match the claimed file-sharing service",
        ],
        body_preview=(
            "Finance has shared 'Q3_Budget_Review.xlsx' with you. "
            "Click below to view the document. This link expires in 24 hours."
        ),
    ),
    PhishingTemplate(
        template_id="TPL-04",
        name="IT Password Reset Required",
        subject="[Action Required] Your password expires today - reset now",
        difficulty=0.80,
        lure_type="IT / helpdesk impersonation",
        red_flags=[
            "Creates urgency around account lockout",
            "Spoofed 'IT Helpdesk' sender display name",
            "Login page URL is a lookalike domain, not the real SSO portal",
            "Real IT never asks for your current password by email",
        ],
        body_preview=(
            "Your network password will expire today at 5:00 PM. To avoid "
            "losing access to email and shared drives, please verify your "
            "identity and reset your password immediately using the secure "
            "link below."
        ),
    ),
    PhishingTemplate(
        template_id="TPL-05",
        name="Executive Wire Transfer Request",
        subject="Quick favor - are you at your desk?",
        difficulty=0.90,
        lure_type="CEO fraud / business email compromise",
        red_flags=[
            "Impersonates an executive using a lookalike personal email",
            "Requests urgent, confidential wire transfer",
            "Explicitly asks to bypass normal verification ('don't call me, "
            "I'm in meetings all day')",
            "Atypical request channel for this type of transaction",
        ],
        body_preview=(
            "Hi, I'm stuck in back-to-back meetings and need you to process "
            "an urgent wire transfer to a new vendor before end of day. "
            "This is time-sensitive and confidential -- please handle it "
            "directly and let me know once it's done."
        ),
    ),
]


def get_template(template_id: str) -> PhishingTemplate:
    for t in TEMPLATES:
        if t.template_id == template_id:
            return t
    raise KeyError(f"Unknown template id: {template_id}")


if __name__ == "__main__":
    print("Available simulated phishing templates:\n")
    for t in TEMPLATES:
        print(f"{t.template_id}  {t.name:<32} difficulty={t.difficulty:.2f}  ({t.lure_type})")
