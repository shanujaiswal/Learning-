# Image Vulnerability Scanning

--> Every base image (and every dependency layered on top) can carry known CVEs -- shipping an unscanned image to production is shipping unknown risk.
--> Trivy (open-source, most widely used) -- scans an image's OS packages AND application dependencies (npm, pip, etc.) against public CVE databases.
--> Snyk / Docker Scout -- commercial/integrated alternatives with the same goal, often wired directly into CI or `docker` CLI (`docker scout cves`).

```bash
trivy image my-app:1.0                       # Scan a built image
trivy image --severity HIGH,CRITICAL my-app  # Fail CI only on serious findings
```

--> Standard practice: run a scan as a CI gate BEFORE pushing to a registry -- block the pipeline on CRITICAL/HIGH findings rather than discovering them after deployment.

# Minimal and Distroless Base Images

--> Every package in a base image is both attack surface and bloat. Smaller base images mean fewer CVEs to patch and faster pulls/deploys.
--> `alpine` variants -- much smaller than full Debian/Ubuntu-based images (`node:20` vs `node:20-alpine`), at the cost of using musl libc instead of glibc (occasionally causes subtle native-dependency issues).
--> Distroless images (Google's `gcr.io/distroless/*`) -- contain ONLY the application and its runtime dependencies, no shell, no package manager, no OS utilities at all -- drastically reduces what an attacker can do even if they gain code execution inside the container.

```dockerfile
FROM node:20 AS build
WORKDIR /app
COPY . .
RUN npm ci && npm run build

FROM gcr.io/distroless/nodejs20-debian12
COPY --from=build /app/dist /app
WORKDIR /app
CMD ["server.js"]
```

# Running as a Non-Root User

--> By default, many images run processes as `root` inside the container -- if an attacker escapes the application into the container's OS layer, root privileges make that far more dangerous.
--> Best practice -- create and switch to an unprivileged user in the Dockerfile.

```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```

# Image Signing and Provenance

--> Docker Content Trust / Sigstore Cosign -- cryptographically sign images at build/push time so consumers (a Kubernetes cluster, a teammate) can verify an image genuinely came from your pipeline and wasn't tampered with in the registry.
--> SBOM (Software Bill of Materials) -- a manifest listing every package/library inside an image, generated at build time (`docker sbom my-app`) -- used to quickly answer "are we affected by this newly disclosed CVE?" across every image in the org without re-scanning everything from scratch.

# Private Registries and Access Control

--> Public Docker Hub images are fine for base OS/runtime layers, but application images with proprietary code belong in a PRIVATE registry (AWS ECR, GitHub Container Registry, Google Artifact Registry, self-hosted Harbor).
--> Registry authentication -- CI/CD pipelines authenticate via short-lived tokens or IAM roles (e.g. `aws ecr get-login-password`), never long-lived static credentials baked into config.
--> Image tagging discipline -- avoid deploying `:latest` in production; tag immutably (git SHA or semantic version) so a running deployment always maps back to an exact, reproducible build.

```bash
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account>.dkr.ecr.us-east-1.amazonaws.com
docker tag my-app:latest <account>.dkr.ecr.us-east-1.amazonaws.com/my-app:a1b2c3d
docker push <account>.dkr.ecr.us-east-1.amazonaws.com/my-app:a1b2c3d
```

# Orchestration Alternatives to Kubernetes

--> Docker Swarm -- Docker's own built-in orchestrator -- far simpler setup/mental model than Kubernetes (reuses `docker-compose.yml`-style syntax via `docker stack deploy`), but with a much smaller feature set and ecosystem. Reasonable choice for small teams wanting multi-node orchestration without Kubernetes' operational overhead.
--> Nomad (HashiCorp) -- a more general-purpose scheduler (can orchestrate containers AND non-containerized workloads/VMs), simpler ops model than Kubernetes, often paired with Consul (service discovery) and Vault (secrets).
--> Reality check -- Kubernetes' dominant ecosystem (Helm charts, operators, cloud-managed offerings, hiring pool) is why it wins by default in most production shops today, even where Swarm/Nomad would technically suffice.
