# Data Analyst — Practical

Runnable code companions to the `Theory` chapters in
`4) Data Science and AI\1) Data Analyst\Theory\`. Each numbered script
demonstrates one or more chapters end to end using a shared synthetic sales
dataset (or its own small synthetic dataset, for 04/05).

## Setup

```
pip install pandas numpy matplotlib scipy
```

Python 3.9+ recommended (scripts use `X | Y` union type hints and dict
generics in signatures).

## Index

| # | File | Theory chapter(s) it demonstrates |
|---|------|-----------------------------------|
| 00 | `00 README.md` | This index — see also `00 Data Analyst Roadmap.md` |
| 01 | `01_generate_sample_sales_data.csv` | Sample data underlying 02 and 03 — `01 Role of a Data Analyst and the Analytics Workflow.md`, `02 Excel and Spreadsheet Analysis.md` |
| 02 | `02_analytics_workflow_eda.py` | `01 Role of a Data Analyst and the Analytics Workflow.md`, `02 Excel and Spreadsheet Analysis.md`, `03 Data Visualization with Tableau and Power BI.md` (pandas groupby/pivot as the PivotTable equivalent, matplotlib charts as the Tableau/Power BI equivalent) |
| 03 | `03_statistics_fundamentals_demo.py` | `04 Statistics Fundamentals for Analysts.md` (mean/median/variance by hand vs numpy, correlation, one- and two-sample t-tests) |
| 04 | `04_ab_test_analysis.py` | `05 A-B Testing and Experimentation.md` (synthetic control/treatment conversion data, two-proportion z-test, chi-square cross-check, effect size, significance/recommendation summary) |
| 05 | `05_causal_inference_intro.py` | `06 Causal Inference Beyond A-B Testing.md` (Difference-in-Differences on a synthetic before/after, treatment/control dataset, parallel-trends caveat) |

## Mapping notes

- Chapter `00 Data Analyst Roadmap.md` has no dedicated script — it's the
  overview chapter that this whole folder operationalizes in order (02 → 05
  roughly follows the roadmap's progression from workflow basics to
  statistics to experimentation to causal inference).
- Chapter `03 Data Visualization with Tableau and Power BI.md` doesn't get
  its own script because 02 already covers the pandas/matplotlib equivalent
  of the pivot-and-chart workflow those tools provide; there's no local
  substitute for the Tableau/Power BI GUIs themselves.

## Running

Run from inside this folder so relative paths resolve correctly:

```
python 02_analytics_workflow_eda.py
python 03_statistics_fundamentals_demo.py
python 04_ab_test_analysis.py
python 05_causal_inference_intro.py
```

Scripts `02` and `03` read `01_generate_sample_sales_data.csv`. Scripts `04`
and `05` are self-contained — they generate their own synthetic data with a
fixed random seed so output is reproducible.
