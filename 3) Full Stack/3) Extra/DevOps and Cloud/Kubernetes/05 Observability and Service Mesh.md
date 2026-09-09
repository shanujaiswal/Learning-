# Why Observability Is Harder in Kubernetes

--> A traditional server has one predictable place to SSH in and check logs/CPU. A Kubernetes app might have dozens of ephemeral pods spread across many nodes, rescheduled constantly -- you can't "just look at the server," you need centralized, cluster-aware tooling.

# Metrics -- Prometheus and Grafana

--> Prometheus -- the de-facto standard metrics system for Kubernetes -- periodically "scrapes" (pulls) metrics from applications and cluster components over HTTP, storing them as time-series data.
--> Applications expose a `/metrics` endpoint (via a client library) in a text format Prometheus understands; Kubernetes components (kubelet, API server) expose their own automatically.
--> Grafana -- the visualization layer on top -- dashboards, graphs, and alerting rules built from Prometheus data (or other data sources).
--> `kube-prometheus-stack` -- a widely used Helm chart that installs Prometheus + Grafana + sensible default dashboards/alerts for a cluster in one shot.

```yaml
# Example metric exposed by an app, scraped by Prometheus
# http_requests_total{method="GET", status="200"} 15423
```

# Logging -- Centralizing Ephemeral Pod Logs

--> `kubectl logs` only works while a pod exists -- once it's deleted/rescheduled, those logs are gone unless shipped somewhere durable.
--> EFK stack (Elasticsearch, Fluentd/Fluent Bit, Kibana) -- a log collector (Fluent Bit) runs as a DaemonSet on every node, forwarding all container logs to Elasticsearch for storage/search, visualized in Kibana.
--> Loki (Grafana Labs) -- a lighter-weight alternative designed to integrate directly with Grafana, indexing logs by label rather than full-text (cheaper to run than the EFK stack for many use cases).

# Distributed Tracing

--> In a microservices architecture, a single user request might pass through 5+ services -- when it's slow or fails, metrics/logs alone often can't show WHERE in that chain the problem occurred.
--> Distributed tracing (OpenTelemetry, Jaeger, Zipkin) -- tags a request with a trace ID that follows it across every service hop, letting you visualize the full request path and see exactly which service added the latency.

# Service Mesh -- Istio and Linkerd

--> A service mesh injects a lightweight proxy ("sidecar") alongside every pod, transparently intercepting all network traffic between services -- without any application code changes.
--> What it buys you: automatic mutual TLS (mTLS) encryption between every service, fine-grained traffic control (canary releases, A/B routing by percentage), automatic retries/circuit-breaking, and built-in observability (every request between services is automatically traced/measured).
--> Istio -- the most feature-rich mesh, but operationally heavier. Linkerd -- deliberately simpler and lighter-weight, often recommended as the pragmatic default unless Istio's advanced traffic-shaping features are specifically needed.
--> When to actually adopt one -- a service mesh earns its complexity once you have enough interdependent services that manually securing/observing each service-to-service connection becomes unmanageable; it's not a day-one requirement for a small app.

```yaml
# Example Istio VirtualService: send 90% of traffic to v1, 10% to a canary v2
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: my-app
spec:
  hosts:
    - my-app
  http:
    - route:
        - destination:
            host: my-app
            subset: v1
          weight: 90
        - destination:
            host: my-app
            subset: v2
          weight: 10
```
