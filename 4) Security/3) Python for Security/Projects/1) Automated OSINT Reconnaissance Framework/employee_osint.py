"""
employee_osint.py

Cross-references the (mock) employee directory scrape against the (mock)
breach-corpus lookup table, flagging any employee whose corporate email
address appears in a known credential-exposure breach.

Real-world equivalent: scraping employee names/emails from LinkedIn or a
company "About Us" page (theory: BeautifulSoup scraping), then checking each
address against a breach-database check such as Have I Been Pwned's API.

AUTHORIZED USE ONLY: operates purely on local, simulated data
(mock_data_sources.py) -- no real scraping or breach-API calls are made.
"""

from __future__ import annotations

from mock_data_sources import Employee, get_breach_corpus, get_employee_directory


def scrape_employees() -> list[Employee]:
    """Return the (mock) employee directory."""
    return get_employee_directory()


def check_breach_exposure(employees: list[Employee]) -> list[dict]:
    """Cross-reference each employee's email against the mock breach corpus.

    Returns a list of findings, one per employee whose email was found in the
    corpus, including which breach(es) it appeared in.
    """
    corpus = get_breach_corpus()
    findings = []

    for employee in employees:
        record = corpus.get(employee.email)
        if record is not None:
            findings.append(
                {
                    "name": employee.name,
                    "title": employee.title,
                    "email": employee.email,
                    "breaches": record["breaches"],
                    "exposed_password_hint": record["exposed_password_hint"],
                    "first_seen": record["first_seen"],
                }
            )

    return findings


def run() -> dict:
    """Run the full employee OSINT step and return a summary dict."""
    employees = scrape_employees()
    findings = check_breach_exposure(employees)

    return {
        "employees": employees,
        "breached_employees": findings,
    }


if __name__ == "__main__":
    result = run()

    print("=== Employee OSINT: directory scrape + breach cross-reference ===")
    print(f"\n[+] Employees found ({len(result['employees'])}):")
    for employee in result["employees"]:
        print(f"    {employee.name:<16} {employee.title:<28} {employee.email}")

    print("\n[+] Breach exposure check:")
    if result["breached_employees"]:
        for finding in result["breached_employees"]:
            breach_list = ", ".join(finding["breaches"])
            print(
                f"    [RISK] {finding['name']} <{finding['email']}> found in: {breach_list} "
                f"(first seen {finding['first_seen']}, password hint: "
                f"{finding['exposed_password_hint']})"
            )
    else:
        print("    No employee credentials found in breach corpus.")
