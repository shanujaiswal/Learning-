# Beyond the Kubernetes-Specific Observability Overview

--> The Kubernetes Observability and Service Mesh file already introduces Prometheus/Grafana, the EFK stack, and distributed tracing at a high level, scoped to "why observability is harder in Kubernetes." This file goes deeper into each stack itself and adds the major commercial platforms (Datadog, New Relic, Splunk) as points of comparison -- the same three pillars (metrics, logs, traces) apply whether or not Kubernetes is involved at all.

# Prometheus in Depth

--> Prometheus pulls (scrapes) metrics rather than having applications push them -- it periodically hits each configured target's `/metrics` HTTP endpoint and stores every sample as a time series, labeled by key/value pairs (`method="GET"`, `status="500"`) for later filtering.
--> The four Prometheus metric TYPES -- Counter (a value that only ever goes up, e.g. total requests served, reset only on restart), Gauge (a value that can go up or down, e.g. current memory usage), Histogram (buckets observations into ranges, e.g. request durations, allowing later calculation of percentiles), Summary (similar to a histogram but calculates percentiles client-side rather than server-side).

```yaml
# prometheus.yml -- what to scrape and how often
scrape_configs:
  - job_name: 'my-app'
    scrape_interval: 15s
    static_configs:
      - targets: ['my-app:3000']
```

## PromQL -- Prometheus's Query Language

--> PromQL is how you turn raw scraped time series into an actual answer -- a dashboard panel, an alert condition -- by selecting, filtering, and aggregating labeled metrics.

```promql
# Current value of a counter, filtered by label
http_requests_total{status="500"}

# Rate of requests per second over the last 5 minutes (turns a cumulative counter into a rate)
rate(http_requests_total[5m])

# 95th percentile request latency from a histogram
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))

# Alert-style expression -- error rate above 5% over 5 minutes
sum(rate(http_requests_total{status=~"5.."}[5m]))
  /
sum(rate(http_requests_total[5m])) > 0.05
```

--> `rate()` is one of the most-used PromQL functions specifically because counters reset on restart -- querying the raw counter value directly is rarely useful; the RATE of change over a window is almost always what you actually want (requests/sec, errors/sec).

## Alertmanager

--> Prometheus itself only evaluates alerting rules and fires alerts -- Alertmanager is the separate component that receives those firing alerts and handles what happens next: deduplication, grouping related alerts together, routing to the right notification channel, and silencing.

```yaml
# alert-rule.yml
groups:
  - name: my-app-alerts
    rules:
      - alert: HighErrorRate
        expr: sum(rate(http_requests_total{status=~"5.."}[5m])) / sum(rate(http_requests_total[5m])) > 0.05
        for: 5m                    # Must stay true for 5 minutes before firing (avoids noisy flapping)
        annotations:
          summary: "Error rate above 5% for 5+ minutes"
```

```yaml
# alertmanager.yml -- routing fired alerts to a notification channel
route:
  receiver: slack-oncall
receivers:
  - name: slack-oncall
    slack_configs:
      - channel: '#oncall-alerts'
```

--> Grouping and deduplication matter enormously at scale -- without Alertmanager, 50 pods all failing the same health check would page on-call with 50 separate alerts instead of one grouped "50 pods failing this check" notification.

# Grafana Dashboard Design

--> Grafana itself is just the visualization layer (mentioned in the Kubernetes Observability file) -- it can query Prometheus, Loki, Elasticsearch, and most other observability backends from one place, but dashboard DESIGN is a separate skill from just having data available.
--> The four golden signals (from Google's SRE practice, directly relevant to the SRE discipline file) that a good service dashboard leads with -- Latency (how long requests take), Traffic (how much demand, e.g. requests/sec), Errors (rate of failing requests), Saturation (how "full" the system is -- CPU, memory, queue depth, connection pool usage).
--> Practical dashboard design habits -- put the golden signals at the top of the dashboard where they're seen first during an incident; use consistent time ranges/color thresholds across panels so red always means the same severity; avoid cramming every available metric onto one screen -- a dashboard someone has to hunt through during an outage has already failed its purpose.
--> Dashboards-as-code -- Grafana dashboards are just JSON under the hood, so they're commonly checked into the same Git repo as the application (provisioned via a `kube-prometheus-stack` Helm values file or Grafana's own provisioning API) rather than built by hand through the UI and never version-controlled -- the same "config as code, reviewed like code" philosophy already covered for Terraform, Helm, and CI/CD pipelines throughout this folder.

# The ELK/EFK Stack in Depth

--> ELK = Elasticsearch (storage/search), Logstash (ingestion/parsing pipeline), Kibana (visualization). EFK swaps Logstash for Fluentd/Fluent Bit -- a lighter-weight log forwarder, especially common as a Kubernetes DaemonSet (already mentioned in the Kubernetes Observability file) because it has a much smaller resource footprint than Logstash on every node.
--> Elasticsearch stores logs as indexed, structured documents (typically one index per day, e.g. `logs-2026.08.20`) -- this indexing is what makes full-text search across huge log volumes fast, at the cost of real infrastructure to run and tune (it is not a lightweight thing to operate at scale).
--> Kibana provides the query/dashboard UI on top -- both free-text search ("show me every log line containing this error string") and structured filtering on indexed fields (`status:500 AND service:checkout`).

```
# A Fluent Bit config fragment -- tail container logs and ship to Elasticsearch
[INPUT]
    Name              tail
    Path              /var/log/containers/*.log

[OUTPUT]
    Name              es
    Host              elasticsearch.logging.svc
    Port              9200
    Index             app-logs
```

--> Operational cost is the main reason lighter alternatives exist -- Grafana Loki (mentioned briefly in the Kubernetes Observability file) indexes only labels rather than full log text, which is dramatically cheaper to run at high log volume, at the cost of slower free-text search compared to Elasticsearch.

# Commercial Observability Platforms -- Datadog, New Relic, Splunk

--> Datadog -- a unified SaaS observability platform covering metrics, logs, and traces (APM) in one product, plus infrastructure monitoring, security monitoring, and synthetic testing -- its core value proposition is NOT having to run and integrate Prometheus + Grafana + an EFK stack + a tracing backend yourself; you install an agent and get all three pillars correlated in one UI. Priced per host/data volume, which becomes a real cost line item at scale (directly relevant to the FinOps/cost governance discussion elsewhere in this folder).
--> New Relic -- similar unified SaaS positioning to Datadog (metrics/logs/traces/APM in one platform), historically strongest in application performance monitoring (APM) specifically -- deep code-level transaction tracing to pinpoint which exact function/query is slow, not just which service.
--> Splunk -- originally and still primarily a log-analysis platform (its SPL query language plays a similar role to Kibana's queries or PromQL, but over log/event data specifically) -- historically dominant in large enterprises and security/SIEM (Security Information and Event Management) use cases, now also offers metrics and observability features, but its core reputation and strength remains log search at very large scale.
--> The real tradeoff across the open-source stack (Prometheus/Grafana/EFK/Jaeger) vs. commercial platforms (Datadog/New Relic/Splunk) -- self-hosted open-source tooling is cheaper at the infrastructure-bill level but costs real engineering time to deploy, integrate, and keep operating correctly; commercial platforms cost real money (often significant at scale) but arrive pre-integrated across all three observability pillars with a support contract behind them. Many organizations end up running a hybrid -- Prometheus for Kubernetes-native metrics, a commercial platform for company-wide log search and APM.
