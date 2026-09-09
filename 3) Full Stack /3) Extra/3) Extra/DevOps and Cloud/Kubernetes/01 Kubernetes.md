# What Is Kubernetes (K8s)

--> Kubernetes is a container orchestration platform -- automates deploying, scaling, networking, and managing containerized applications (like the ones built with Docker) across a cluster of machines.
--> Solves what Docker alone doesn't: running many containers reliably at scale, restarting failed ones automatically, distributing load, and rolling out updates without downtime.
--> "K8s" is a numeronym -- K, 8 letters, S.

# Why Kubernetes on Top of Docker

--> Docker packages and runs a single container on a single machine well -- it doesn't natively handle: automatically restarting a crashed container, spreading containers across multiple servers, load balancing between replicas, or zero-downtime deployments.
--> Kubernetes manages a CLUSTER (many machines) running many containers, handling all of the above automatically based on a declared desired state.

# Core Concepts

--> Cluster -- a set of machines (nodes) running Kubernetes, managed as one unit.
--> Node -- a single machine (VM or physical) in the cluster that runs containers.
--> Pod -- the smallest deployable unit in Kubernetes -- wraps one or more tightly-coupled containers that share network/storage. Most commonly, one pod = one container.
--> Deployment -- describes the DESIRED state for a set of pods (which image, how many replicas) -- Kubernetes continuously works to match reality to this desired state, restarting/recreating pods as needed.
--> Service -- provides a stable network endpoint (IP/DNS name) for a set of pods, even as individual pods are created/destroyed/rescheduled.
--> Namespace -- a way to divide a cluster into virtual sub-clusters, useful for separating environments (dev/staging/prod) or teams.

# A Basic Deployment

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
spec:
  replicas: 3               # Run 3 identical copies (pods) of this container
  selector:
    matchLabels:
      app: my-app
  template:
    metadata:
      labels:
        app: my-app
    spec:
      containers:
        - name: my-app
          image: my-app:1.0
          ports:
            - containerPort: 3000
```

```yaml
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: my-app-service
spec:
  selector:
    app: my-app        # Routes traffic to any pod matching this label
  ports:
    - port: 80
      targetPort: 3000
  type: LoadBalancer     # Exposes the service externally via a cloud load balancer
```

```bash
kubectl apply -f deployment.yaml   # Create/update resources from a YAML file
kubectl apply -f service.yaml
kubectl get pods                    # List running pods
kubectl get deployments             # List deployments and their replica status
kubectl get services                # List services and their exposed IPs
kubectl logs <pod-name>             # View a pod's container logs
kubectl describe pod <pod-name>     # Detailed info/events for debugging
kubectl delete -f deployment.yaml   # Remove resources
```

# Self-Healing and Scaling

--> If a pod crashes or fails a health check, Kubernetes automatically restarts or replaces it to match the declared replica count -- no manual intervention needed.
--> Horizontal scaling -- increase/decrease the number of pod replicas to handle more/less load.

```bash
kubectl scale deployment my-app --replicas=5   # Manually scale to 5 pods
```

--> Horizontal Pod Autoscaler (HPA) -- automatically adjusts replica count based on observed CPU/memory usage or custom metrics, without manual scaling commands.

# Rolling Updates and Rollbacks

--> Updating a Deployment's image triggers a ROLLING UPDATE by default -- new pods are started and old ones terminated gradually, keeping the app available throughout (zero-downtime deployment).

```bash
kubectl set image deployment/my-app my-app=my-app:2.0   # Trigger a rolling update to a new image
kubectl rollout status deployment/my-app                  # Watch the rollout progress
kubectl rollout undo deployment/my-app                     # Roll back to the previous version if something's wrong
```

# ConfigMaps and Secrets

--> ConfigMap -- stores non-sensitive configuration data (feature flags, URLs) separately from container images, injectable as environment variables or files.
--> Secret -- same idea but for sensitive data (passwords, API keys, tokens) -- base64-encoded at rest (not full encryption by default, but kept separate from ConfigMaps for access-control purposes).

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  API_URL: "https://api.example.com"
```

```yaml
env:
  - name: API_URL
    valueFrom:
      configMapKeyRef:
        name: app-config
        key: API_URL
```

# Ingress -- Routing External Traffic

--> An Ingress resource manages external HTTP/HTTPS access to services within the cluster, typically with rules based on hostname/path -- like a reverse proxy for the whole cluster.

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-app-ingress
spec:
  rules:
    - host: myapp.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: my-app-service
                port:
                  number: 80
```

# Helm -- The Kubernetes Package Manager

--> Helm packages a set of Kubernetes YAML files (Deployment, Service, ConfigMap, etc.) into a reusable, configurable "chart" -- similar to how npm packages JS code.
--> Lets you install/upgrade a complex multi-resource application with one command instead of applying many YAML files manually.

```bash
helm install my-release ./my-chart      # Install a chart
helm upgrade my-release ./my-chart       # Upgrade with new config/version
helm rollback my-release 1                # Roll back to a previous release revision
```

# Minikube and Local Development

--> Minikube / kind (Kubernetes in Docker) -- run a single-node Kubernetes cluster locally for development/testing, without needing real cloud infrastructure.

```bash
minikube start        # Spin up a local cluster
kubectl get nodes      # Confirm it's running
minikube dashboard      # Open a web UI for the cluster
```

# When You Actually Need Kubernetes

--> Small apps/single-server deployments -- Docker Compose is usually enough; Kubernetes adds real operational complexity that isn't justified.
--> Kubernetes earns its complexity when you need: multi-server scaling, automatic failover, zero-downtime deployments, or managing many interdependent microservices.
--> Managed Kubernetes services (EKS on AWS, GKE on Google Cloud, AKS on Azure) offload cluster management (control plane, upgrades) to the cloud provider, reducing operational burden significantly.
