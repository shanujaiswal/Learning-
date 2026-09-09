"""
02_analytics_workflow_eda.py

Demonstrates a real analytics workflow (see Theory: "Role of a Data Analyst /
Analytics Workflow" and "Excel and Spreadsheet Analysis") using pandas instead
of a spreadsheet:

    1. Load data
    2. Understand shape / dtypes (describe)
    3. Check data quality (missing values)
    4. Summarize with groupby (the pandas equivalent of a PivotTable)
    5. Visualize (the pandas/matplotlib equivalent of Excel/Tableau charts)

Run:
    python 02_analytics_workflow_eda.py

Outputs (written next to this script):
    revenue_by_region.png
    revenue_over_time.png
"""

from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd

HERE = Path(__file__).resolve().parent
CSV_PATH = HERE / "01_generate_sample_sales_data.csv"


def load_data() -> pd.DataFrame:
    df = pd.read_csv(CSV_PATH, parse_dates=["date"])
    return df


def step_1_first_look(df: pd.DataFrame) -> None:
    print("=" * 70)
    print("STEP 1: First look at the data")
    print("=" * 70)
    print(f"\nShape: {df.shape[0]} rows x {df.shape[1]} columns")
    print("\nDtypes:")
    print(df.dtypes)
    print("\nFirst 5 rows:")
    print(df.head())


def step_2_describe(df: pd.DataFrame) -> None:
    print("\n" + "=" * 70)
    print("STEP 2: describe() - the pandas equivalent of Excel's summary stats")
    print("=" * 70)
    print("\nNumeric columns:")
    print(df.describe())
    print("\nCategorical columns:")
    print(df.describe(include="object"))


def step_3_missing_values(df: pd.DataFrame) -> None:
    print("\n" + "=" * 70)
    print("STEP 3: Data quality check - missing values")
    print("=" * 70)
    missing = df.isna().sum()
    pct = (missing / len(df) * 100).round(1)
    report = pd.DataFrame({"missing_count": missing, "missing_pct": pct})
    print(report)

    if missing.sum() > 0:
        print(
            "\nDecision: 'units' has a small number of missing values. "
            "For this demo we fill them with the column median so downstream "
            "aggregations (SUM, MEAN) aren't skewed by NaNs, but in a real "
            "analysis you'd first ask WHY the data is missing before choosing "
            "a fix (drop, fill, or flag for the data source owner)."
        )
        df["units"] = df["units"].fillna(df["units"].median())
    else:
        print("\nNo missing values found.")


def step_4_groupby_summaries(df: pd.DataFrame) -> None:
    print("\n" + "=" * 70)
    print("STEP 4: Groupby summaries - the pandas equivalent of a PivotTable")
    print("=" * 70)

    by_region = (
        df.groupby("region")
        .agg(total_revenue=("revenue", "sum"), total_units=("units", "sum"), avg_order=("revenue", "mean"))
        .round(2)
        .sort_values("total_revenue", ascending=False)
    )
    print("\nRevenue by region:")
    print(by_region)

    by_product = (
        df.groupby("product")
        .agg(total_revenue=("revenue", "sum"), total_units=("units", "sum"), avg_order=("revenue", "mean"))
        .round(2)
        .sort_values("total_revenue", ascending=False)
    )
    print("\nRevenue by product:")
    print(by_product)

    # Cross-tab: region x product, like a 2D PivotTable
    pivot = df.pivot_table(
        values="revenue", index="region", columns="product", aggfunc="sum", fill_value=0
    ).round(2)
    print("\nRevenue by region x product (pivot table):")
    print(pivot)

    return by_region


def step_5_visualize(df: pd.DataFrame, by_region: pd.DataFrame) -> None:
    print("\n" + "=" * 70)
    print("STEP 5: Visualization (matplotlib equivalent of Excel/Tableau charts)")
    print("=" * 70)

    # --- Chart 1: bar chart of revenue by region ---
    fig, ax = plt.subplots(figsize=(7, 4.5))
    ax.bar(by_region.index, by_region["total_revenue"], color="#3b6fa0")
    ax.set_title("Total Revenue by Region")
    ax.set_xlabel("Region")
    ax.set_ylabel("Total Revenue ($)")
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.grid(axis="y", linestyle="--", alpha=0.4)
    fig.tight_layout()
    out1 = HERE / "revenue_by_region.png"
    fig.savefig(out1, dpi=150)
    plt.close(fig)
    print(f"Saved: {out1}")

    # --- Chart 2: line chart of revenue over time (daily total, weekly rolling avg) ---
    daily = df.groupby("date")["revenue"].sum().sort_index()
    rolling = daily.rolling(window=7, min_periods=1).mean()

    fig, ax = plt.subplots(figsize=(9, 4.5))
    ax.plot(daily.index, daily.values, color="#a0a8b3", linewidth=1, label="Daily revenue")
    ax.plot(rolling.index, rolling.values, color="#3b6fa0", linewidth=2, label="7-day rolling avg")
    ax.set_title("Revenue Over Time")
    ax.set_xlabel("Date")
    ax.set_ylabel("Revenue ($)")
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.grid(axis="y", linestyle="--", alpha=0.4)
    ax.legend(frameon=False)
    fig.autofmt_xdate()
    fig.tight_layout()
    out2 = HERE / "revenue_over_time.png"
    fig.savefig(out2, dpi=150)
    plt.close(fig)
    print(f"Saved: {out2}")


def main() -> None:
    df = load_data()
    step_1_first_look(df)
    step_2_describe(df)
    step_3_missing_values(df)
    by_region = step_4_groupby_summaries(df)
    step_5_visualize(df, by_region)
    print("\nDone. This mirrors the standard analytics workflow: "
          "collect -> clean -> explore -> summarize -> visualize -> communicate.")


if __name__ == "__main__":
    main()
