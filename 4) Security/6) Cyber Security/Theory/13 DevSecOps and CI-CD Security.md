### DevSecOps and CI/CD Security

--> This chapter covers embedding security INTO the software delivery pipeline itself, rather than treating security as a separate gate that happens only after development finishes — directly relevant to the container/Kubernetes pipeline covered in the previous chapter, since that's typically what a CI/CD pipeline is building and deploying.

## Shift Left Security

--> "Shift left" means moving security activities EARLIER in the software development lifecycle (SDLC) — visualized as a left-to-right timeline (design → code → build → test → release → deploy → operate), security work shifts from the right side (a pen test right before/after release, or worse, an incident discovered in production) toward the left side (secure design review, secure coding practices, automated scanning on every single commit).
--> Why it matters, concretely: a vulnerability caught by a linter/SAST tool in a developer's IDE or a PR check costs minutes to fix. The exact same vulnerability, if it survives all the way to a live production incident, can cost the org an actual breach, incident response effort (Chapter 5's full IR lifecycle), regulatory exposure, and reputational damage — the COST of a fix compounds enormously the later it's found, and the FREQUENCY of finding it drops the later it's checked, since fewer eyes/tools are looking that late in the process.
--> DevSecOps is the organizational/cultural embodiment of shift-left: security is a shared responsibility woven into every stage the DevOps pipeline already has (plan, code, build, test, release, deploy, operate, monitor), automated wherever possible, rather than a separate team that only shows up at the very end holding up a release.

## SAST vs DAST vs SCA

--> These three testing categories are complementary, not competing — a mature pipeline runs all three, because each catches a fundamentally different CLASS of issue that the others structurally cannot see.

1. SAST (Static Application Security Testing)
   --> Analyzes SOURCE CODE (or bytecode/binaries) WITHOUT executing it, looking for known-insecure coding patterns: SQL injection-prone string concatenation into queries, hardcoded credentials, use of deprecated/unsafe crypto functions, missing input validation, etc.
   --> Strength: can be run on every single commit/PR, extremely early (the leftmost possible "shift"), and can point to the EXACT line of vulnerable code.
   --> Limitation: analyzes code in isolation — it doesn't know how the app actually behaves at runtime, how components interact under real conditions, or catch configuration/environment-level issues, and it has a well-known reputation for noisy false positives if not tuned.
   --> Example tools: Semgrep (fast, highly customizable rule-based scanning, popular for its low noise relative to older tools), SonarQube (broader code-quality + security), Checkmarx, Bandit (Python-specific).

2. DAST (Dynamic Application Security Testing)
   --> Tests a RUNNING application from the OUTSIDE, the same way an actual attacker would — sending crafted HTTP requests, probing for reflected XSS, SQL injection, broken authentication, misconfigured headers — without any access to or knowledge of the source code (black-box testing).
   --> Strength: catches real, exploitable runtime behavior, including issues that only manifest through actual request/response interaction, and issues in third-party/compiled components SAST can't see into.
   --> Limitation: needs a running, reasonably realistic deployed instance of the app to test against (so it runs later in the pipeline, typically against a staging environment, not on every single commit), and generally can't point to the specific line of code causing the finding — the fix still has to be traced back manually.
   --> Example tools: OWASP ZAP (free, widely used, easily automatable in CI), Burp Suite (industry-standard, more manual/interactive workflow, also covered from the offensive side in Ethical Hacking).

3. SCA (Software Composition Analysis) / Dependency Scanning
   --> Scans a project's THIRD-PARTY dependencies (npm packages, Python packages, Maven artifacts, OS packages inside a container image) against known vulnerability databases (the CVE database, GitHub Advisory Database) to flag known-vulnerable versions being pulled in — this is the same underlying technique as the Trivy image scanning covered in the container security chapter, but applied more broadly to any dependency manifest, not just container images.
   --> Why it matters disproportionately: the overwhelming majority of a typical modern application's code is actually third-party dependency code, not code the team itself wrote — SAST scanning only your OWN code while ignoring the dependency tree is scanning a small minority of what's actually running in production.
   --> Example tools: Dependabot (built into GitHub, auto-opens PRs bumping vulnerable dependencies), Snyk, OWASP Dependency-Check, `npm audit` / `pip-audit` for language-specific quick checks.

--> One-line summary to memorize: SAST reads your code, DAST attacks your running app from outside, SCA checks whether the code you DIDN'T write (your dependencies) is secretly vulnerable.

## Secrets Scanning in CI/CD

--> A hardcoded secret is a real credential (API key, database password, private key, cloud access token) committed directly into source code or a config file, instead of being injected at runtime from a secrets manager/environment variable.
--> This is a genuinely recurring, real-world incident category, not a theoretical risk — once a secret is committed to git, it exists in the repository's HISTORY forever (even if the file is later deleted or the secret is "removed" in a subsequent commit), because git history preserves every prior version of every file by design. Anyone who ever clones the repo, or who gains access to it later, can walk back through history and recover the original secret from an old commit — deleting it in a NEW commit does not delete it from the OLD one.
--> Compounding factor: a public GitHub repo accidentally containing a live AWS key is actively, automatically scraped by botnets within minutes of being pushed — this isn't a hypothetical "someone might stumble across it," it is routinely and rapidly exploited (frequently for cryptomining using the victim's own cloud billing account) faster than most humans would even notice the leak, let alone rotate the key.

Secrets scanning tools detect this pattern (recognizable formats like AWS `AKIA...` key prefixes, private key PEM headers, generic high-entropy strings that look like tokens) either as a pre-commit hook (blocking the commit locally before it ever reaches the remote) or as a CI pipeline step (scanning the full commit history/diff on every push and failing the build if something is found):

--> gitleaks – open-source, extremely fast, widely used both as a pre-commit hook and a CI step; ships with a large library of regex/entropy detectors for common secret formats out of the box.
--> truffleHog – also open-source; notably, in addition to pattern matching, it can actively VERIFY many detected secrets by testing them live against the relevant provider's API (e.g., actually attempting a lightweight authenticated call to AWS with a found key) to confirm whether it's a currently-live, exploitable credential versus an already-revoked/dead one — dramatically cutting down on triage noise from expired test keys.

--> The correct remediation when a secret is found is never just "delete the line and commit again" — the secret must be treated as fully compromised and ROTATED (revoked at the provider and reissued) regardless of whether it's still in current history, because it may have already been scraped, and old commits containing it may still be reachable via forks, cached clones, or CI logs even after a history rewrite.

## Worked Example: GitHub Actions Pipeline with Security Scanning

```yaml
name: CI Security Pipeline

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

jobs:
  security-scan:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code (full history needed for secrets scan)
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      # Secrets scanning — catch hardcoded credentials before they merge
      - name: Run gitleaks
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      # SAST — static analysis of our own application source code
      - name: Run Semgrep SAST scan
        uses: semgrep/semgrep-action@v1
        with:
          config: p/owasp-top-ten

      # SCA — scan dependency manifests for known-vulnerable packages
      - name: Run dependency vulnerability scan
        run: |
          pip install pip-audit
          pip-audit -r requirements.txt --fail-on-vuln

      # Container image scanning — same category as SCA, applied to the built image
      - name: Build image
        run: docker build -t myapp:${{ github.sha }} .

      - name: Scan image with Trivy
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: myapp:${{ github.sha }}
          severity: HIGH,CRITICAL
          exit-code: 1        # fail the pipeline — do not push/deploy a vulnerable image

      # Only reaches this step if every prior security gate passed
      - name: Push image to registry
        if: github.ref == 'refs/heads/main'
        run: docker push myapp:${{ github.sha }}
```
--> Note the ordering logic: fast, cheap checks (secrets scan, SAST) run first and fail fast; the image is only built and pushed to a registry AFTER all scans pass — this is the concrete, literal implementation of "shift left" as actual pipeline YAML, not just a slogan. DAST would typically run as a separate, later job against a deployed staging environment, since it needs something live to attack.

## Supply Chain Security

--> Supply chain security concerns the risk that the software you SHIP was compromised somewhere upstream of your own code — in a dependency, a build tool, a CI runner, or a vendor's product you trusted and integrated — rather than through a vulnerability you personally introduced.

1. SBOM (Software Bill of Materials)
   --> A structured, machine-readable inventory listing every component (direct AND transitive dependencies, down to specific versions) that makes up a piece of software — conceptually similar to an ingredients list on packaged food.
   --> Why it matters: when a new critical CVE is disclosed in some widely-used library, an org with SBOMs already generated for all its software can immediately, precisely query "which of our applications actually contain this exact vulnerable component and version," instead of scrambling for days trying to manually figure out exposure across potentially hundreds of applications and their deep transitive dependency trees.
   --> Standard formats: CycloneDX and SPDX are the two dominant, widely-adopted SBOM formats; tools like Syft (by Anchore, often paired with Grype for the actual vulnerability check against the generated SBOM) generate them automatically from source code or a built container image.

2. Dependency pinning
   --> Locking dependencies to EXACT versions (via lockfiles: `package-lock.json`, `poetry.lock`, `Pipfile.lock`, or a container image pinned by digest as covered in the previous chapter) rather than loose version ranges (`^1.2.0`, `~1.2.0`, or an unpinned `requests` with no version at all).
   --> Without pinning, the exact same source code can pull in DIFFERENT dependency code on different days/machines as new versions get published upstream — this is a security risk (a newly published malicious/compromised version of a dependency could be silently pulled into your next build with zero code change on your end) as well as a reproducibility problem.

3. Case study: SolarWinds (2020)
   --> Attackers compromised SolarWinds' own SOFTWARE BUILD SYSTEM (not just a single dependency) and injected a malicious backdoor (later named SUNBURST) directly into the build process of its Orion IT-monitoring product. The tampered, backdoored update was then digitally signed with SolarWinds' own LEGITIMATE code-signing certificate and distributed through their normal, trusted, official update channel to roughly 18,000 customers, including numerous US government agencies.
   --> Conceptual lesson: this was a compromise of the TRUSTED BUILD/DISTRIBUTION PIPELINE itself, not a vulnerability in the shipped product's own logic — no amount of SAST/DAST scanning of SolarWinds' own source code would necessarily have caught a backdoor injected into the BUILD process after the code was written but before it was signed and shipped. This is precisely why build-pipeline integrity (restricting who/what can modify build systems, verifying build provenance/attestation — frameworks like SLSA exist specifically to formalize this) is now treated as its own distinct supply-chain security concern, separate from application code security.

4. Case study: Log4Shell (2021)
   --> A critical remote-code-execution vulnerability (CVE-2021-44228) was discovered in Log4j, an extremely widely-used Java logging LIBRARY embedded as a transitive dependency inside an enormous number of applications and products worldwide — many organizations didn't even know they were running vulnerable Log4j versions at all, because it was buried several layers deep in their dependency trees, pulled in indirectly by some OTHER library they had explicitly chosen, rather than a library they'd deliberately added themselves.
   --> Conceptual lesson: this is the textbook illustration of why SCA/dependency scanning and SBOMs matter so much — organizations that already had accurate SBOMs generated for their software could immediately, precisely query which of their applications actually contained the vulnerable Log4j version, while organizations without that visibility had to scramble for weeks doing manual, error-prone dependency-tree archaeology across their entire portfolio just to figure out their actual exposure, all while the vulnerability was being mass-exploited in the wild in real time.

--> Both case studies point to the same underlying theme this whole chapter builds toward: modern software security cannot stop at "is MY code secure" — it has to extend to "can I trust every step of how this software was built, what it's made of, and how it got to me," which is exactly the gap DevSecOps practices (shift-left scanning, SBOMs, pinned/verified dependencies, build-pipeline integrity) exist to close.
