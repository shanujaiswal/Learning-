# What GitOps Actually Changes

--> Every CI/CD pipeline covered so far (AWS CodePipeline, Jenkins, GitLab CI/CD, GitHub Actions) is PUSH-based -- the pipeline itself holds credentials to your cluster/cloud and actively pushes changes out (`kubectl apply`, `terraform apply`, `aws deploy`) when a job runs.
--> GitOps flips this to PULL-based -- a Git repository is the single source of truth for desired state, and an agent running INSIDE the target cluster continuously watches that repo and pulls changes to itself, rather than an external pipeline pushing in.
--> Why this matters -- no external CI system ever needs cluster-admin credentials to your production Kubernetes cluster; the only thing that needs access to the cluster is the in-cluster agent, and the only thing that needs access to it is Git. This significantly shrinks the blast radius of a compromised CI pipeline.

# The Core Loop -- Desired State, Actual State, Reconciliation

--> Desired state -- what's declared in Git (a set of Kubernetes YAML manifests, Helm charts, or Kustomize overlays in a repo) -- this is identical in spirit to a Terraform `.tf` config (covered in the Terraform file) declaring desired infrastructure, just applied continuously to a running cluster instead of run on-demand.
--> Actual state -- what's really running in the cluster right now.
--> Reconciliation loop -- the GitOps agent continuously (e.g. every few minutes, or via a webhook) diffs desired vs actual state and applies whatever changes are needed to make actual state match desired state -- the same "declared desired state, tool figures out the diff" philosophy that underlies Kubernetes Deployments themselves (covered in the Kubernetes fundamentals file), just extended one level up to the whole cluster's configuration.

# Drift Detection

--> Drift -- when actual cluster state no longer matches what's declared in Git, typically because someone ran `kubectl edit` or `kubectl apply` by hand directly against the cluster, bypassing Git entirely.
--> A GitOps agent detects this drift on its next reconciliation pass and, depending on configuration, either automatically reverts the manual change back to match Git (self-healing) or just flags it as OutOfSync for a human to review.
--> This is the enforcement mechanism behind "Git is the source of truth" as a practice, not just a slogan -- without a reconciling agent, nothing stops configuration from silently diverging from what's documented in the repo over time.

# ArgoCD

--> ArgoCD is a GitOps controller for Kubernetes -- you point it at a Git repo and a target cluster/namespace via an `Application` resource, and it continuously syncs the two.

```yaml
# argocd-application.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: my-app
  namespace: argocd
spec:
  source:
    repoURL: https://github.com/my-org/my-app-manifests.git
    targetRevision: main
    path: k8s/production
  destination:
    server: https://kubernetes.default.svc
    namespace: production
  syncPolicy:
    automated:
      selfHeal: true      # Automatically revert manual drift back to match Git
      prune: true          # Delete resources removed from Git
```

--> ArgoCD ships a web UI/CLI that visualizes exactly what's in sync vs OutOfSync per Application, and can show a live diff before syncing -- similar in spirit to `terraform plan` (covered in the Terraform file) showing what would change before it happens, except continuously rather than on a single invocation.
--> A promoted change (e.g. from the artifact-promotion flow covered in the CI/CD Concepts file) in GitOps terms means updating the image tag in the Git repo ArgoCD watches for the target environment -- the actual deployment then happens via ArgoCD's pull, not via the CI pipeline pushing.

# FluxCD

--> FluxCD (Flux) is ArgoCD's main alternative -- same pull-based reconciliation model, built as a set of Kubernetes controllers (`GitRepository`, `Kustomization`, `HelmRelease` custom resources) rather than a standalone UI-first application -- generally considered more lightweight/composable, while ArgoCD is generally considered more approachable via its UI.

```yaml
# flux-kustomization.yaml
apiVersion: kustomize.toolkit.fluxcd.io/v1
kind: Kustomization
metadata:
  name: my-app
  namespace: flux-system
spec:
  interval: 5m                      # How often to reconcile
  sourceRef:
    kind: GitRepository
    name: my-app-manifests
  path: "./k8s/production"
  prune: true
  targetNamespace: production
```

--> Both tools support the same essential capabilities (multi-cluster management, Helm chart deployment, automated image update detection) -- choice between them in practice often comes down to whether the team wants ArgoCD's opinionated UI-centric workflow or Flux's more Kubernetes-native, controller-composition approach.

# GitOps vs a Traditional CD Pipeline Stage

--> A traditional pipeline's deploy stage (e.g. CodeDeploy, or a GitHub Actions job running `kubectl apply`) is push-based and stops existing once the job finishes -- nothing is left continuously watching to correct drift.
--> GitOps only handles the CD (continuous deployment) half -- CI (build, test, produce an artifact) still runs in Jenkins/GitLab CI/GitHub Actions/CodeBuild exactly as covered in the CI-CD Platforms folder; GitOps tools pick up right where CI leaves off, watching for the artifact reference to change in Git and reconciling the cluster to match.
--> Typical combined flow -- CI pipeline builds and pushes an image, then updates an image tag in a separate "manifests" Git repo (or the same repo) -- ArgoCD/Flux detects that commit and reconciles the cluster, completing the deploy without the CI pipeline itself ever touching the cluster directly.
