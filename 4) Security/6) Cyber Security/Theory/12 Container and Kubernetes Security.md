### Container and Kubernetes Security

--> Modern infrastructure runs mostly as containers orchestrated by Kubernetes rather than as long-lived VMs. The security model changes significantly, and misconfigurations here are one of the most common real-world breach causes in cloud-native environments.

## Container Isolation Fundamentals: Why Containers Are NOT VMs

--> A common beginner misconception is treating a container as "a lightweight VM." Security-wise this is a dangerous simplification — the isolation BOUNDARY is fundamentally different, and that difference has direct consequences for how much you should trust container isolation alone.

--> A VM virtualizes HARDWARE. Each VM runs its own complete kernel, on top of a hypervisor that mediates access to the real underlying hardware. Two VMs on the same host share nothing at the kernel level — the isolation boundary is the hypervisor, a small, heavily-scrutinized, purpose-built piece of software.
--> A container virtualizes the OPERATING SYSTEM, not the hardware. All containers on a given host SHARE the same underlying host kernel. A container is really just a regular Linux process, with the kernel using a set of features to make that process THINK it has its own isolated filesystem, network stack, and process tree — but underneath, it's still directly running on the same kernel as every other container and the host itself.

The two Linux kernel primitives that make this illusion of isolation possible:

1. Namespaces – provide isolated VIEWS of system resources
   --> `PID` namespace: a container sees its own process tree starting at PID 1, unaware of any other processes running on the host or in other containers.
   --> `NET` namespace: a container gets its own network stack — its own interfaces, IP address, routing table, iptables rules — separate from the host's.
   --> `MNT` namespace: a container sees its own isolated filesystem/mount tree (this is why a container's filesystem looks like a fresh, minimal OS even though it's really just a directory tree on the host, usually built from image layers).
   --> Other namespaces: `UTS` (hostname), `IPC` (inter-process communication), `USER` (UID/GID mapping — a process can be "root" INSIDE the container's user namespace while mapping to an unprivileged UID on the host, when configured).

2. cgroups (control groups) – limit and account for RESOURCE USAGE
   --> Enforce limits on CPU, memory, disk I/O, and network bandwidth a container's processes are allowed to consume, and prevent one noisy/malicious container from starving the whole host of resources (a form of denial-of-service prevention at the host level).

--> The critical security consequence: because the KERNEL is shared, a kernel-level vulnerability (a container escape / privilege escalation bug in the kernel itself) can potentially let a process break out of its namespace isolation and directly touch the host — and from there, every other container on that same host, since they all share that one kernel. This class of attack has no VM equivalent, because a VM's hypervisor boundary doesn't share a kernel with the guest at all.
--> Practical implication: never treat container isolation as equivalent to VM isolation for genuinely hostile multi-tenant workloads. Running containers from different trust levels (e.g., your own trusted code alongside an untrusted third party's code) on the exact same shared kernel/host is a materially weaker security boundary than running them in separate VMs — this is precisely why sandboxed container runtimes like gVisor and Kata Containers exist: they add an extra isolation layer (a userspace kernel proxy, or a lightweight VM per container respectively) specifically to close this gap for higher-trust-boundary workloads.

## Image Security

--> A container image is the packaged, static blueprint (layers of filesystem changes) that a container is instantiated from. Since an image can be pulled and run by anyone with access to it, securing the IMAGE itself is a distinct discipline from securing the running container.

1. Vulnerability scanning
   --> Container images are built from base images (e.g., `python:3.11`, `node:20`, `ubuntu:22.04`) plus whatever packages/dependencies you install on top — any of these layers can contain known-vulnerable software (an outdated OpenSSL version with a published CVE, for example).
   --> Trivy (open-source, by Aqua Security) is one of the most widely used scanners — it inspects an image's OS packages and language dependencies against vulnerability databases and reports known CVEs by severity.
   ```bash
   # Scan an image for OS + dependency vulnerabilities, fail the build on HIGH/CRITICAL findings
   trivy image --severity HIGH,CRITICAL --exit-code 1 myapp:1.4.2
   ```
   --> Other common scanners: Grype (Anchore), Clair, and cloud-native scanners built into registries (Amazon ECR image scanning, Google Artifact Registry scanning).

2. Minimize the base image
   --> Every package, library, and shell binary present in an image is additional attack surface — if an attacker achieves code execution inside a container, a fuller-featured base image (full Ubuntu/Debian with `bash`, `curl`, `wget`, package managers) gives them far more tools to work with for follow-on attacks (downloading a second-stage payload, establishing a reverse shell) than a minimal image would.
   --> Preferred minimal bases: `distroless` images (Google's project — contain ONLY the application and its runtime dependencies, no shell, no package manager, no unnecessary OS utilities at all) and Alpine Linux (a genuinely small, musl-libc-based distro, though it trades off some compatibility and DNS-resolution edge cases for its size).
   --> Practical effect: if an attacker exploits an app running in a distroless image, they typically can't even get an interactive shell to explore further — there isn't one in the image to get.

3. Never use the `:latest` tag in production
   --> `:latest` is a MUTABLE, floating tag — it points to whatever the most recently pushed image happens to be at any given moment, which changes over time without any change in your own deployment manifest.
   --> This breaks reproducibility (you can't be certain what code is actually running, or roll back to a known-good state with confidence) and is a security risk specifically because a compromised upstream base image, or an accidental bad push, can silently propagate into your deployments the next time anything pulls or restarts.
   --> Always pin to an immutable, specific version — ideally by digest (SHA256 content hash), which cannot be reassigned to different content even by the image's own publisher, rather than just a version tag (which technically still CAN be overwritten/retagged upstream):
   ```dockerfile
   # Bad — floating tag, could silently change to different content over time
   FROM python:3.11
   
   # Better — pinned version tag
   FROM python:3.11.7-slim
   
   # Best — pinned by immutable content digest, cannot silently change at all
   FROM python@sha256:2e3f...c9a1
   ```

4. Don't run as root inside the container
   --> By default, if a Dockerfile specifies no `USER` instruction, the container's main process runs as `root` (UID 0) INSIDE the container. Combined with the shared-kernel reality above, if an attacker escapes the container while running as root inside it, they have a meaningfully easier path to also acquiring elevated privileges on the host (especially if any container escape/misconfiguration exists), compared to escaping from an already-unprivileged process.
   --> Fix: create and switch to a dedicated non-root user in the Dockerfile.
   ```dockerfile
   FROM python:3.11.7-slim

   # Create a dedicated non-root user/group for the app
   RUN groupadd -r appgroup && useradd -r -g appgroup appuser

   WORKDIR /app
   COPY --chown=appuser:appgroup . .
   RUN pip install --no-cache-dir -r requirements.txt

   # Switch to the non-root user for all subsequent instructions AND at runtime
   USER appuser

   CMD ["python", "app.py"]
   ```
   --> This should be paired with the Kubernetes-level `securityContext.runAsNonRoot: true` (covered below) so the cluster actively REFUSES to start a pod that tries to run as root, rather than just relying on the Dockerfile's good intentions alone.

## Kubernetes RBAC (Role-Based Access Control)

--> Kubernetes RBAC controls WHO (users, groups, or ServiceAccounts — the identity a POD itself uses to talk to the Kubernetes API) can perform WHAT ACTIONS (verbs like get/list/create/delete/watch) on WHICH RESOURCES (pods, secrets, deployments, etc.), scoped either to a single namespace or cluster-wide.
--> Four core RBAC objects: `Role` (defines permissions within ONE namespace), `ClusterRole` (defines permissions cluster-wide, or reusable across namespaces), `RoleBinding` (grants a Role to a user/group/ServiceAccount, within one namespace), `ClusterRoleBinding` (grants a ClusterRole cluster-wide).

Worked example: a CI/CD ServiceAccount that should be able to view and restart deployments in the `staging` namespace, but explicitly CANNOT touch Secrets or delete anything.

```yaml
# Role: defines the exact permission set, scoped to the "staging" namespace only
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: deployment-viewer-restarter
  namespace: staging
rules:
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["get", "list", "watch", "patch"]   # patch is needed to trigger a rolling restart
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list", "watch"]            # read-only visibility into pod status
  # Note: no "secrets" resource listed at all — this identity has ZERO access to Secrets,
  # not even read access. Omission is the default-deny; RBAC is allow-list only.
---
# RoleBinding: attaches the Role above to a specific ServiceAccount
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: ci-cd-binding
  namespace: staging
subjects:
  - kind: ServiceAccount
    name: ci-cd-deployer
    namespace: staging
roleRef:
  kind: Role
  name: deployment-viewer-restarter
  apiGroup: rbac.authorization.k8s.io
```

--> Key RBAC principles to internalize: RBAC is deny-by-default and purely additive — a ServiceAccount with no Role bound to it can do NOTHING against the API server, and there is no way to write an explicit "deny" rule; you achieve least-privilege purely by never granting more than what's listed. Always prefer namespaced `Role`/`RoleBinding` over `ClusterRole`/`ClusterRoleBinding` unless cluster-wide scope is a genuine, deliberate requirement — this is the direct Kubernetes-native application of the least-privilege principle from Chapter 1 and the least-privilege pillar from the Zero Trust chapter.
--> A very common real-world misconfiguration is binding the built-in `cluster-admin` ClusterRole to a ServiceAccount "just to make something work" during development and never revisiting it — this single mistake gives that ServiceAccount (and therefore anything that can impersonate or use it, including a compromised pod) full control of the entire cluster.

## Kubernetes Secrets: Not Actually Encrypted at Rest by Default

--> Kubernetes `Secret` objects are the built-in mechanism for storing sensitive values (passwords, API keys, TLS certs) separately from plain `ConfigMap`s, and mounting them into pods as environment variables or files.
--> The critical, frequently-misunderstood catch: by default, Secret data is stored in `etcd` (Kubernetes' backing key-value store) only Base64-ENCODED, not encrypted. Base64 is a reversible ENCODING, not encryption at all — anyone with direct read access to `etcd`, or anyone with RBAC permission to `get` that Secret via the API, can trivially recover the plaintext value.
```bash
kubectl get secret db-password -o jsonpath='{.data.password}' | base64 -d
# prints the real plaintext password — Base64 provides zero confidentiality
```
--> To actually get encryption at rest, a cluster administrator must explicitly configure an `EncryptionConfiguration` resource for the API server (encrypting Secret data with a provider such as AES-CBC/AES-GCM, or better, integrating with an external KMS like AWS KMS/HashiCorp Vault/Azure Key Vault for envelope encryption) — this is NOT the out-of-the-box default on most self-managed clusters, and teams frequently assume it's already handled when it isn't.
--> Additional real-world Secrets hardening beyond "turn on encryption at rest": restrict RBAC access to Secrets as narrowly as possible (as shown in the RBAC example above — omit the resource entirely for identities that don't need it), enable audit logging specifically for Secret `get`/`list` API calls so unusual access is visible, and consider an external secrets manager (Vault, AWS Secrets Manager, External Secrets Operator) that injects secrets at runtime rather than storing long-lived sensitive values as native K8s objects at all.

## Common Kubernetes Misconfigurations

1. Exposed Kubernetes Dashboard
   --> The Kubernetes Dashboard is a web UI for managing cluster resources. Historically, several real-world breaches (most famously a Tesla cloud-mining/cryptojacking incident) occurred because a Dashboard instance was exposed to the public internet with no authentication at all, or with an overly permissive default service account bound to it — anyone who found the IP had full or near-full cluster control through a point-and-click UI.
   --> Fix: never expose the Dashboard externally; if it must be used, put it behind strict RBAC, require authentication, and access it only via `kubectl proxy`/port-forwarding from an already-authenticated admin session, not a public LoadBalancer/Ingress.

2. Overly permissive ServiceAccounts
   --> Every pod, unless told otherwise, is automatically assigned the `default` ServiceAccount in its namespace and that ServiceAccount's token is auto-mounted into the pod's filesystem. If that namespace's `default` ServiceAccount has been (even accidentally) granted broad permissions — or even if it hasn't, but `automountServiceAccountToken` was left enabled unnecessarily — a compromised application pod can use that mounted token to talk to the Kubernetes API itself as that identity, turning "we popped one container" into "we can now query/manipulate the cluster."
   --> Fix: set `automountServiceAccountToken: false` on pods/ServiceAccounts that never need to call the K8s API at all (most application workloads don't), and create dedicated, narrowly-scoped ServiceAccounts (as in the RBAC example) for the ones that do.

3. Privileged pods / containers
   --> A pod running with `securityContext.privileged: true` essentially disables most of the container isolation boundary described earlier — it gets access to ALL host devices and kernel capabilities, roughly equivalent to root access directly on the host node itself. This is sometimes used legitimately for specific infrastructure workloads (CNI plugins, storage drivers) but is drastically overused in practice, often just to work around a permission error during development without understanding what was actually granted.
   --> Related, narrower risky settings worth knowing individually: `allowPrivilegeEscalation: true` (lets a process gain more privileges than its parent, e.g. via setuid binaries), `hostPID`/`hostNetwork`/`hostIPC: true` (shares the HOST's process tree/network namespace/IPC namespace with the container, defeating the namespace isolation described earlier by deliberate configuration), and mounting sensitive host paths like `/var/run/docker.sock` into a container (giving that container control over the Docker daemon itself, which is close to root-equivalent on the host).
   --> Hardened baseline `securityContext` for a typical application pod that needs none of the above:
   ```yaml
   securityContext:
     runAsNonRoot: true          # refuses to start if the image tries to run as root
     runAsUser: 1000
     privileged: false
     allowPrivilegeEscalation: false
     readOnlyRootFilesystem: true
     capabilities:
       drop:
         - ALL                   # drop all Linux capabilities, then add back only what's truly needed
   ```
   --> Kubernetes Pod Security Standards (the built-in `Restricted`, `Baseline`, `Privileged` profiles, enforced via Pod Security Admission at the namespace level) exist specifically to enforce hardened defaults like these cluster-wide, rather than relying on every single deployment YAML author remembering to set them individually.
