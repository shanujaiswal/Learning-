# What OSINT Is

--> Open Source Intelligence (OSINT) is information gathering using only publicly available sources -- no hacking or unauthorized access involved, just systematically collecting and correlating what's already public (social media, DNS records, breach databases, search engines, public code repositories).
--> The first phase of nearly every real engagement referenced elsewhere in this Security folder -- the Ethical Hacking track's Reconnaissance file covers this from a methodology angle; this file covers the Python automation side of actually doing it at scale.

# Querying Shodan -- The "Search Engine for Devices"

--> Shodan continuously scans the internet and indexes exposed devices/services (servers, webcams, industrial control systems, databases left open) -- letting you search for specific software versions, open ports, or misconfigurations across the ENTIRE internet, rather than one target at a time.

```python
import shodan

api = shodan.Shodan("YOUR_API_KEY")

results = api.search("apache country:US port:8080")
for result in results["matches"]:
    print(f"{result['ip_str']}:{result['port']} -- {result.get('org', 'Unknown')}")
```

--> Real defensive use case -- an organization running the same query against its OWN IP ranges to discover forgotten, exposed services before an attacker does (a legitimate, common blue-team application of the exact same tool).

# Querying VirusTotal -- Checking Files, Hashes and URLs

--> VirusTotal aggregates results from dozens of antivirus engines and URL/domain reputation services -- useful for quickly checking whether a suspicious file hash, URL, or domain has already been flagged as malicious by the security community.

```python
import requests

API_KEY = "YOUR_API_KEY"
file_hash = "44d88612fea8a8f36de82e1278abb02f"   # SHA256/MD5 of a file

response = requests.get(
    f"https://www.virustotal.com/api/v3/files/{file_hash}",
    headers={"x-apikey": API_KEY}
)
data = response.json()
malicious_count = data["data"]["attributes"]["last_analysis_stats"]["malicious"]
print(f"Flagged as malicious by {malicious_count} engines")
```

# Automating theHarvester-Style Domain Recon

--> Gathering subdomains, email addresses, and associated names for a target domain from public sources (search engines, certificate transparency logs) is a standard early recon step.

```python
import requests

def get_subdomains_from_crtsh(domain):
    # crt.sh indexes publicly logged SSL certificates -- a certificate for "mail.example.com"
    # reveals that subdomain exists, whether or not it's linked from anywhere public
    response = requests.get(f"https://crt.sh/?q=%25.{domain}&output=json")
    if response.status_code == 200:
        entries = response.json()
        return set(entry["name_value"] for entry in entries)
    return set()

subdomains = get_subdomains_from_crtsh("example.com")
```

# Web Scraping for OSINT with BeautifulSoup

--> For sources without a clean API, scraping publicly rendered HTML directly is a common fallback -- extracting employee names/roles from a public "About Us" page, for example, which can later feed into a password-spraying or social-engineering-awareness assessment (covered in the Cyber Security and Ethical Hacking tracks).

```python
from bs4 import BeautifulSoup
import requests

response = requests.get("https://example.com/about-us")
soup = BeautifulSoup(response.text, "html.parser")

employee_names = [tag.get_text() for tag in soup.select(".employee-name")]
```

# Correlating Results

--> The real value of OSINT automation isn't any single tool's output -- it's CORRELATING results across multiple sources (a name found on LinkedIn + an email format pattern from public breach data + a subdomain from crt.sh) into a cohesive picture, exactly the kind of repetitive cross-referencing work Python scripting is well suited to automate rather than doing by hand.

# Ethical and Legal Boundary

--> OSINT itself only touches PUBLIC information and is generally legal -- but using it to enable an actual attack (phishing, credential stuffing) against a target without authorization is not. This tooling is intended for authorized engagements, your own organization's exposure assessment, or CTF/lab practice.
