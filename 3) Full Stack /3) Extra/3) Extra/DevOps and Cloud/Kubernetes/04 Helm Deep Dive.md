# Recap -- What Helm Solves

--> Raw Kubernetes YAML doesn't parameterize well -- deploying the same app to dev/staging/prod means either duplicating YAML files with small differences, or hand-editing them each time. Helm turns a set of manifests into a templated, configurable, versioned package.

# Chart Structure

```
my-chart/
├── Chart.yaml            # Chart metadata: name, version, description
├── values.yaml            # Default configuration values
├── charts/                 # Sub-charts (dependencies) bundled inside
└── templates/
    ├── deployment.yaml
    ├── service.yaml
    ├── ingress.yaml
    └── _helpers.tpl        # Reusable named template snippets
```

# Templating with values.yaml

--> Templates use Go template syntax (`{{ }}`) to inject values instead of hardcoding them -- the same template produces different output for dev vs prod just by swapping the values file.

```yaml
# values.yaml
replicaCount: 3
image:
  repository: my-app
  tag: "1.0"
service:
  port: 80
```

```yaml
# templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Release.Name }}-app
spec:
  replicas: {{ .Values.replicaCount }}
  template:
    spec:
      containers:
        - name: app
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          ports:
            - containerPort: {{ .Values.service.port }}
```

```bash
helm install my-release ./my-chart -f values-prod.yaml     # Override defaults per environment
helm template ./my-chart                                    # Render final YAML locally without deploying (great for debugging)
```

# Releases and Versioning

--> Every `helm install`/`upgrade` creates a new REVISION of a release -- Helm keeps history, which is what makes `helm rollback my-release 1` possible even several upgrades later.
--> `helm diff upgrade` (a plugin) -- shows exactly what would change before actually applying an upgrade, similar in spirit to `terraform plan`.

# Chart Dependencies

--> A chart can declare dependencies on other charts (e.g. your app chart depends on the official `postgresql` chart) in `Chart.yaml`'s `dependencies` section -- `helm dependency update` pulls them into `charts/`, so one `helm install` stands up the whole stack.

```yaml
# Chart.yaml
dependencies:
  - name: postgresql
    version: "12.x.x"
    repository: "https://charts.bitnami.com/bitnami"
```

# Helm Repositories and Publishing

--> Public chart repositories (Bitnami, Artifact Hub) host pre-built charts for common software (databases, message queues, monitoring stacks) -- often faster to adopt a well-maintained community chart than hand-writing manifests for a well-known piece of infrastructure.

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
helm install my-db bitnami/postgresql
```

# Hooks -- Running Jobs Around a Release Lifecycle

--> Helm Hooks let a chart run a Job at a specific point in a release's lifecycle -- e.g. `pre-install` (run DB migrations before the app starts), `post-upgrade` (smoke-test after an upgrade completes).

```yaml
metadata:
  annotations:
    "helm.sh/hook": pre-install
```
