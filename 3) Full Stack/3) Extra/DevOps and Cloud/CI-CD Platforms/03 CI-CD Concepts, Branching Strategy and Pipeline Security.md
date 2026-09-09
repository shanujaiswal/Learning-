# Why Concepts Matter More Than Any One Tool

--> Jenkins, GitLab CI/CD, GitHub Actions, and AWS's native pipeline (CodePipeline/CodeBuild) all implement the same underlying practices with different syntax -- the concepts in this file (branching strategy, release safety, pipeline security, artifact promotion) transfer across every tool covered so far.

# Trunk-Based Development vs GitFlow

--> GitFlow -- a branching model with long-lived `develop` and `main` branches, plus dedicated `feature/*`, `release/*`, and `hotfix/*` branches merged through a defined sequence -- gives very explicit release structure, at the cost of long-lived branches that drift from `main` and produce large, risky merges.
--> Trunk-Based Development -- all developers commit small, frequent changes directly to a single shared branch (`main`/`trunk`), with feature branches (if used at all) living for at most a day or two before merging -- keeps integration continuous and merge conflicts small, but requires strong automated testing and feature flags (below) to keep incomplete work from breaking `main`.
--> Why trunk-based development has become the default recommendation for teams practicing real CI/CD -- GitFlow's long-lived branches are fundamentally in tension with "continuous integration," since code isn't actually integrated until the long branch finally merges, often weeks after it diverged.
--> GitFlow still earns its place for software with genuinely scheduled, versioned releases (e.g. a library with semantic versioning and multiple maintained release branches) rather than a continuously-deployed web service.

# Feature-Flag-Driven Release

--> A feature flag (a.k.a. feature toggle) is a runtime switch (usually backed by a config service or a dedicated flag platform) that turns a code path on/off WITHOUT a new deployment -- merged code can stay dark in production until the flag is flipped.
--> This is what makes trunk-based development safe -- an incomplete feature can be merged to `main` and deployed continuously alongside finished code, as long as it stays behind a flag that's off, decoupling "deploy" from "release" as two separate events.

```javascript
// Simplified feature flag check
if (featureFlags.isEnabled('new-checkout-flow', { userId })) {
  return renderNewCheckout();
}
return renderLegacyCheckout();
```

--> Flags also enable canary/percentage rollouts (mentioned as a deployment strategy in the AWS CI/CD file) at the application level rather than the infrastructure level -- e.g. enable a flag for 5% of users, watch error rates, ramp up.
--> Flag debt -- old flags left in code long after a feature is fully rolled out accumulate as dead conditional branches -- disciplined teams treat "delete the flag" as part of the definition of done, not an optional cleanup.

# Pipeline Security -- SAST/DAST Integration

--> Shifting security checks into the pipeline itself ("shifting left") catches vulnerabilities before merge/deploy instead of relying on a separate, later security review -- the pipeline becomes a security gate, not just a build/test gate.
--> SAST (Static Application Security Testing) -- scans source code itself (without running it) for known vulnerable patterns -- SQL injection-prone string concatenation, hardcoded secrets, insecure deserialization. Runs early and fast, typically as its own pipeline stage on every PR.
--> DAST (Dynamic Application Security Testing) -- tests a RUNNING instance of the application from the outside, the way an attacker would -- sending malicious requests to a deployed staging environment and checking for actual exploitable behavior. Catches issues SAST can't (misconfigurations, runtime-only behavior) but runs later and slower, usually against a staging deploy rather than every PR.
--> Dependency/SCA (Software Composition Analysis) scanning -- checks third-party packages (`package.json`, `requirements.txt`) against known-vulnerability databases (CVEs) -- distinct from SAST (which scans your own code) but run alongside it in the same pipeline stage in practice.

```yaml
# GitHub Actions example -- SAST + dependency scanning as a required stage
jobs:
  security-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run SAST scan
        uses: github/codeql-action/analyze@v3
      - name: Dependency vulnerability scan
        run: npm audit --audit-level=high
```

--> Secrets scanning -- a related, often-separate check that scans commits/diffs for accidentally committed credentials (API keys, private keys) before they reach a shared branch -- pairs with the "never commit secrets, use Secrets Manager/Parameter Store" practice already covered in the AWS security file.
--> Failing the pipeline (rather than just warning) on high-severity findings is what actually enforces the gate -- a scan step that only reports without blocking the merge tends to get ignored over time.

# Artifact Promotion Between Environments

--> Artifact promotion -- build ONE artifact (a Docker image, a compiled binary) once, then promote that exact same artifact through dev → staging → production, rather than rebuilding from source at each stage.
--> Why this matters -- rebuilding per environment risks a subtle difference (a dependency resolving to a different version, a different build-time environment variable baking in different behavior) between what was tested in staging and what actually runs in production -- "build once, deploy everywhere" eliminates that class of bug entirely.

```yaml
# Conceptual promotion flow -- same image tag/digest moves forward, never rebuilt
docker build -t myorg/my-app:1.4.2 .
docker push myorg/my-app:1.4.2

# staging deploy references the exact same tag
kubectl set image deployment/my-app my-app=myorg/my-app:1.4.2

# after staging verification passes, production references the SAME tag -- no rebuild
kubectl set image deployment/my-app my-app=myorg/my-app:1.4.2 --context=prod-cluster
```

--> An artifact repository (a container registry like ECR/GHCR/Docker Hub, or a general-purpose one like Nexus/Artifactory) is what makes promotion possible -- it's the durable, addressable store that both the staging and production deploy steps pull the identical artifact from.
--> Immutable tags/digests -- promoting by a mutable tag like `latest` defeats the purpose (it can point to a different image tomorrow); promoting by an immutable version tag or content digest (`sha256:...`) guarantees staging and production really did run the exact same bits.
--> Manual approval gates (mentioned in the AWS CI/CD file for CodePipeline) commonly sit between the staging-verified and production-promotion steps -- automated tests clear the artifact for promotion, but a human still approves the final production push for high-risk changes.
