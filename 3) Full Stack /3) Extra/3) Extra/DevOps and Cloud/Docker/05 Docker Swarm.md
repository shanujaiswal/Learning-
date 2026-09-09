# Docker Swarm -- Docker's Own Orchestrator

--> Swarm is Docker's built-in orchestration mode -- turns a group of Docker hosts into a single cluster, without needing a separate tool like Kubernetes. Enabled with one command on existing Docker installations.

```bash
docker swarm init                              # Turn this machine into a Swarm manager
docker swarm join --token <token> <manager-ip>:2377   # Run on other machines to join as workers
```

# Manager and Worker Nodes

--> Manager nodes -- maintain the cluster state, schedule services, and handle the Raft consensus that keeps multiple managers in sync (running 3 or 5 managers, an odd number, gives fault tolerance if one goes down).
--> Worker nodes -- run the actual containers ("tasks") assigned to them by managers, but don't participate in cluster management decisions.

# Services -- The Swarm Equivalent of a Deployment

--> A Service describes a desired state (which image, how many replicas) -- conceptually the same idea as a Kubernetes Deployment, just simpler.

```bash
docker service create --name web --replicas 3 -p 80:80 nginx

docker service ls                          # List running services
docker service scale web=5                  # Scale up/down
docker service update --image nginx:1.25 web   # Rolling update to a new image
```

--> Swarm distributes the 3 (or 5) replicas across available worker nodes automatically, and reschedules a replica elsewhere if the node running it goes down -- the same self-healing idea Kubernetes offers, with a smaller feature set.

# docker stack deploy -- Reusing Compose Syntax

--> Swarm can deploy a multi-service application directly from a `docker-compose.yml`-style file (a "stack file"), reusing the same syntax already covered in the Docker Compose file -- a major practical advantage: no separate YAML dialect to learn between local Compose and Swarm production deployment.

```yaml
# docker-stack.yml
version: "3.9"
services:
  web:
    image: my-app:1.0
    deploy:
      replicas: 3
      restart_policy:
        condition: on-failure
    ports:
      - "80:80"
```

```bash
docker stack deploy -c docker-stack.yml my-app
docker stack services my-app
docker stack rm my-app
```

# Swarm's Built-In Load Balancing and Overlay Networks

--> Swarm automatically load-balances requests across all replicas of a service, and overlay networks let containers on DIFFERENT physical hosts communicate as if they were on the same local network -- both handled transparently, without the manual network setup Kubernetes' Services/Ingress require configuring explicitly.

# Swarm vs Kubernetes -- Why Kubernetes Still Dominates

--> Swarm's biggest selling point is simplicity -- far less to learn and operate than Kubernetes for a small-to-medium deployment.
--> Kubernetes wins on ecosystem and feature depth -- Helm charts, a vastly larger set of third-party integrations, finer-grained scheduling/scaling controls (HPA, custom resource definitions), and it's what nearly every managed cloud container offering (EKS/GKE/AKS) is actually built on.
--> Practical takeaway -- Swarm is a legitimate, much lower-overhead choice for smaller teams/deployments that don't need Kubernetes' full feature set; most large-scale production shops still default to Kubernetes given its ecosystem dominance and hiring pool.
