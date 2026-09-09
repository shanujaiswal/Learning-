# Health Checks -- Liveness, Readiness and Startup Probes

--> Kubernetes doesn't know if your app is actually healthy just because the container process is running -- probes tell it how to check.
--> Liveness probe -- "is this container still alive?" If it fails repeatedly, Kubernetes kills and restarts the pod.
--> Readiness probe -- "is this container ready to receive traffic?" If it fails, the pod is removed from the Service's routing (but NOT restarted) -- useful during slow startup or temporary overload.
--> Startup probe -- gives slow-starting apps extra time before liveness checks kick in, avoiding a restart loop on legitimately slow boots.

```yaml
containers:
  - name: my-app
    image: my-app:1.0
    livenessProbe:
      httpGet:
        path: /health
        port: 3000
      initialDelaySeconds: 10
      periodSeconds: 15
    readinessProbe:
      httpGet:
        path: /ready
        port: 3000
      periodSeconds: 5
```

# Resource Requests and Limits

--> Requests -- the minimum CPU/memory Kubernetes guarantees a container; used for scheduling (which node has room).
--> Limits -- the maximum a container is allowed to use -- exceeding a memory limit gets the container killed (OOMKilled); exceeding a CPU limit just throttles it.
--> Not setting these lets one misbehaving pod starve every other pod on the same node -- setting sane requests/limits is a baseline production practice, not an optimization.

```yaml
resources:
  requests:
    cpu: "250m"       # 0.25 of a CPU core
    memory: "256Mi"
  limits:
    cpu: "500m"
    memory: "512Mi"
```

# Jobs and CronJobs -- Running Tasks, Not Long-Lived Services

--> Job -- runs a pod to completion for a one-off task (e.g. a database migration, a batch export) and stops -- unlike a Deployment, it isn't meant to run forever.
--> CronJob -- runs a Job on a schedule (same syntax as Linux cron) -- e.g. nightly backups, periodic report generation.

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: nightly-backup
spec:
  schedule: "0 2 * * *"           # 2 AM every day
  jobTemplate:
    spec:
      template:
        spec:
          containers:
            - name: backup
              image: my-backup-tool
          restartPolicy: OnFailure
```

# StatefulSets and DaemonSets

--> StatefulSet -- like a Deployment, but for pods that need a stable identity and persistent storage per replica (e.g. a database cluster where each node needs to keep its own data and a predictable name like `db-0`, `db-1`). Regular Deployments treat all replicas as interchangeable; StatefulSets don't.
--> DaemonSet -- ensures exactly one copy of a pod runs on EVERY node in the cluster -- used for node-level agents (log collectors, monitoring agents, network plugins) rather than application replicas.

# RBAC -- Role-Based Access Control

--> Controls WHO (users, service accounts) can do WHAT (get/list/create/delete) on WHICH resources (pods, secrets, deployments) within the cluster -- the same least-privilege idea as IAM, applied to the cluster itself.
--> Role -- a set of permissions scoped to a Namespace. ClusterRole -- the same, but cluster-wide. RoleBinding/ClusterRoleBinding -- attaches a Role to a specific user or service account.

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: default
  name: pod-reader
rules:
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list"]
```

--> A common real-world mistake is giving a CI/CD pipeline's service account `cluster-admin` out of convenience -- scope it down to only the namespaces/verbs it actually needs.
