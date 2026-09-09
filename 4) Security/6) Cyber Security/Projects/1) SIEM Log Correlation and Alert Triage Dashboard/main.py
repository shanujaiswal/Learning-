"""
main.py
-------
Runs the full pipeline end to end for one synthetic analyst shift:

    log_sources.generate_shift_logs()
        -> alert_triage_engine.triage()   (which itself calls into
           correlation_rules.group_events_by_ip_window / evaluate_group)
        -> dashboard.render_dashboard()   (printed to console)
        -> a matplotlib PNG summarizing alerts by severity and by source

Real-world equivalent: this script is standing in for the always-on
pipeline a SIEM runs continuously -- ingest -> correlation searches ->
notable events -> the analyst's triage screen -- compressed into a single
reproducible run over one shift's worth of logs.
"""

from __future__ import annotations

import matplotlib
matplotlib.use("Agg")  # headless: just save a PNG, don't try to open a window
import matplotlib.pyplot as plt

from log_sources import generate_shift_logs
from alert_triage_engine import triage
from correlation_rules import DEFAULT_GROUP_WINDOW
import dashboard

OUTPUT_PNG = "siem_dashboard_result.png"

SEVERITY_COLORS = {
    "CRITICAL": "#b71c1c",
    "HIGH": "#e65100",
    "MEDIUM": "#f9a825",
    "LOW": "#2e7d32",
}
SOURCE_COLORS = {
    "firewall": "#1565c0",
    "ids": "#6a1b9a",
    "auth": "#00838f",
}


def save_summary_png(alerts, stats, path: str = OUTPUT_PNG) -> None:
    sev_counts = dashboard.severity_breakdown(alerts)
    src_counts = dashboard.source_breakdown(alerts)

    n_alerts = max(len(alerts), 1)
    fig_height = max(5, 0.22 * n_alerts)
    fig = plt.figure(figsize=(15, fig_height))
    gs = fig.add_gridspec(1, 3, width_ratios=[1, 1, 1.4])
    axes = [fig.add_subplot(gs[0, i]) for i in range(3)]

    # Panel 1: alerts by severity
    labels = dashboard.SEVERITY_ORDER
    values = [sev_counts[l] for l in labels]
    colors = [SEVERITY_COLORS[l] for l in labels]
    axes[0].bar(labels, values, color=colors)
    axes[0].set_title("Alerts by Severity")
    axes[0].set_ylabel("Alert count")
    for i, v in enumerate(values):
        axes[0].text(i, v + 0.05, str(v), ha="center", va="bottom")

    # Panel 2: alerts touching each log source
    src_labels = ["firewall", "ids", "auth"]
    src_values = [src_counts.get(s, 0) for s in src_labels]
    src_colors = [SOURCE_COLORS[s] for s in src_labels]
    axes[1].bar(src_labels, src_values, color=src_colors)
    axes[1].set_title("Alerts by Log Source")
    axes[1].set_ylabel("Alert count (source present in alert)")
    for i, v in enumerate(src_values):
        axes[1].text(i, v + 0.05, str(v), ha="center", va="bottom")

    # Panel 3: simulated per-alert triage time, colored by severity
    if alerts:
        ids_sorted = [a.alert_id for a in alerts]
        times_min = [stats.per_alert_seconds[aid] / 60.0 for aid in ids_sorted]
        bar_colors = [SEVERITY_COLORS[a.severity_label] for a in alerts]
        axes[2].barh(ids_sorted, times_min, color=bar_colors)
        axes[2].invert_yaxis()
        axes[2].tick_params(axis="y", labelsize=max(5, min(9, 300 // n_alerts)))
        axes[2].set_xlabel("Simulated triage time (minutes)")
        axes[2].set_title(f"Per-Alert Triage Time\n(mean = {stats.mean_seconds/60:.1f} min)")
    else:
        axes[2].text(0.5, 0.5, "No alerts", ha="center", va="center")
        axes[2].set_title("Per-Alert Triage Time")

    fig.suptitle("SIEM Log Correlation & Alert Triage -- Shift Summary", fontsize=14)
    fig.tight_layout(rect=[0, 0, 1, 0.94])
    fig.savefig(path, dpi=150)
    plt.close(fig)


def main() -> None:
    print("Ingesting logs from firewall / IDS / auth sources for one 4-hour shift...")
    events = generate_shift_logs()
    print(f"  -> {len(events)} raw log events ingested.\n")

    print(f"Running correlation rules (grouping window = "
          f"{DEFAULT_GROUP_WINDOW.total_seconds():.0f}s)...")
    alerts = triage(events, window=DEFAULT_GROUP_WINDOW)
    print(f"  -> {len(alerts)} alert(s) produced after correlation + triage.\n")

    stats = dashboard.compute_triage_time_stats(alerts)
    print(dashboard.render_dashboard(alerts))

    save_summary_png(alerts, stats, OUTPUT_PNG)
    print(f"\nSaved shift summary chart to: {OUTPUT_PNG}")


if __name__ == "__main__":
    main()
