# Why Containers Need Their Own Pentesting Approach

--> Standard host-based enumeration and privilege escalation techniques (covered in the Linux Privilege Escalation file) apply INSIDE a container too, but containers/Kubernetes add entirely new layers -- container runtime boundaries, the Kubernetes API itself, and cluster-level RBAC (covered defensively in the DevOps folder's Kubernetes notes) -- each with its own distinct misconfiguration risks.

# Container Escape -- Breaking Out to the Host

--> A container is meant to isolate a process from the underlying host -- a container escape breaks that isolation, giving an attacker access to the host machine (and potentially every OTHER container running on it) from inside what should have been a sandboxed environment.
--> Privileged containers (`--privileged` flag, or `securityContext: privileged: true` in Kubernetes) essentially disable most container isolation entirely -- a compromised privileged container is nearly equivalent to root access on the host itself, making this one of the single most impactful misconfigurations to check for.

```bash
# Inside a privileged container -- mounting the host's filesystem directly
mkdir /mnt/host
mount /dev/sda1 /mnt/host    # If this succeeds, you now have direct host filesystem access
```

--> Exposed Docker socket (`/var/run/docker.sock` mounted into a container) lets any process inside that container issue commands to the HOST's Docker daemon -- effectively equivalent to root on the host, since you can simply ask the daemon to launch a new, deliberately privileged container.

```bash
docker -H unix:///var/run/docker.sock run -v /:/host -it alpine chroot /host sh
```

# Enumerating a Kubernetes Cluster From the Inside

--> Every pod, by default, has a Service Account token mounted at a well-known path -- the very first thing to check after landing inside a compromised pod.

```bash
cat /var/run/secrets/kubernetes.io/serviceaccount/token
cat /var/run/secrets/kubernetes.io/serviceaccount/namespace

# Using that token to query the Kubernetes API directly
curl -k -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
  https://kubernetes.default.svc/api/v1/namespaces/default/pods
```

--> If the RBAC (covered in the DevOps Kubernetes notes) attached to that Service Account is overly permissive, an attacker can enumerate secrets, other pods, and potentially create NEW pods with attacker-chosen (privileged) configurations -- directly escalating from "compromised one application" to "control of the cluster."

# Exploiting the Kubernetes API Server Directly

--> An exposed, unauthenticated `kubelet` API (historically a common misconfiguration) can allow direct command execution on a node without needing to compromise an application pod first at all.
--> Overly permissive RBAC bindings -- a service account with `create pods` permission cluster-wide, for instance, can create a new pod mounting the HOST's filesystem and running privileged, achieving host compromise from pure API access, without ever touching a container escape technique directly.

# Kubernetes-Specific Enumeration Tools

--> `kubeaudit` and `kube-hunter` automate scanning a cluster for common misconfigurations (privileged pods, exposed dashboards, overly permissive RBAC, missing network policies) -- the Kubernetes-specific equivalent of running a general vulnerability scanner against a traditional network.

```bash
kube-hunter --remote <cluster-ip>
kubeaudit all -f cluster-config.yaml
```

# Network Policies -- The Missing Segmentation

--> Without explicit Kubernetes NetworkPolicies, every pod can reach every other pod on the cluster by default -- a compromised low-value pod can freely reach a high-value database pod with zero additional lateral movement effort, directly connecting to the lateral movement concepts covered in the Post-Exploitation file, but with Kubernetes' flat-by-default networking making it even easier than in a traditionally segmented network.

# Reporting Cluster Findings

--> As with cloud findings (covered in the Cloud Penetration Testing file), report the SPECIFIC resource, namespace, and exact RBAC rule/misconfiguration responsible -- "the cluster has bad security" isn't actionable; "the `ci-runner` service account in namespace `default` has `create` on `pods` cluster-wide, enabling privilege escalation to node compromise" is.
