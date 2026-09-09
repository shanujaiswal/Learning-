"""
01 - NumPy & Pandas Fundamentals
=================================
Companion script for: "Python for Data Science (NumPy/Pandas)".

Covers:
  - NumPy: array creation, broadcasting, boolean (mask) indexing,
    vectorized math, matrix multiplication.
  - Pandas: DataFrame construction, filtering, groupby aggregation,
    and merging two related tables.

All data is small, synthetic, and generated inline -- no files needed.
"""

import numpy as np
import pandas as pd

SEP = "=" * 70


def section(title: str) -> None:
    print(f"\n{SEP}\n{title}\n{SEP}")


# ---------------------------------------------------------------------------
# PART 1 -- NumPy fundamentals
# ---------------------------------------------------------------------------

section("PART 1: NumPy — vectorized operations")

rng = np.random.default_rng(seed=42)

# A small "sensor readings" matrix: 5 sensors x 6 hourly readings.
readings = rng.normal(loc=25.0, scale=3.0, size=(5, 6)).round(2)
print("Raw sensor readings (5 sensors x 6 hours):\n", readings)

# --- Vectorized math: no Python loops needed ---------------------------
readings_fahrenheit = readings * 9 / 5 + 32
print("\nConverted to Fahrenheit (vectorized, no loop):\n",
      readings_fahrenheit.round(1))

# --- Boolean indexing / masks -------------------------------------------
hot_mask = readings > 27.0
print("\nBoolean mask where reading > 27C:\n", hot_mask)
print("Values where reading > 27C:", readings[hot_mask])
print("Count of 'hot' readings:", hot_mask.sum())

# Replace all values below freezing-equivalent threshold with NaN-like flag
# (demonstrates conditional vectorized assignment)
flagged = readings.copy()
flagged[readings < 22.0] = -1  # sentinel for "too cold, sensor check needed"
print("\nReadings with values < 22C flagged as -1:\n", flagged)

# --- Broadcasting: subtract each sensor's own mean (row-wise) ----------
sensor_means = readings.mean(axis=1, keepdims=True)  # shape (5, 1)
deviations = readings - sensor_means  # broadcasts (5,6) - (5,1) -> (5,6)
print("\nPer-sensor mean (broadcast subtracted):\n", sensor_means.round(2))
print("Deviations from each sensor's own mean:\n", deviations.round(2))

# --- Matrix multiplication ------------------------------------------------
# Suppose each hour has a different "weight" (importance) for a daily index.
hour_weights = np.array([0.1, 0.15, 0.2, 0.25, 0.2, 0.1])  # sums to 1.0
# (5,6) @ (6,) -> (5,) : weighted daily index per sensor
daily_index = readings @ hour_weights
print("\nHour weights:", hour_weights, "(sum =", hour_weights.sum(), ")")
print("Weighted daily index per sensor (matrix-vector product):\n",
      daily_index.round(2))

# A full matrix-matrix multiply: correlate sensors via covariance-like matrix
# (5,6) @ (6,5) -> (5,5) "similarity" matrix between sensors
similarity = readings @ readings.T
print("\nSensor-to-sensor similarity matrix (readings @ readings.T):\n",
      similarity.round(1))


# ---------------------------------------------------------------------------
# PART 2 -- Pandas fundamentals
# ---------------------------------------------------------------------------

section("PART 2: Pandas — DataFrame operations")

# Small synthetic "employees" table
employees = pd.DataFrame({
    "emp_id": range(1, 11),
    "name": ["Asha", "Ben", "Chen", "Dana", "Eli",
             "Farah", "Gus", "Hana", "Ivo", "Jia"],
    "dept_id": [1, 2, 1, 3, 2, 1, 3, 2, 1, 3],
    "salary": [72000, 65000, 81000, 59000, 77000,
               68000, 91000, 73000, 62000, 84000],
    "years_experience": [3, 1, 6, 2, 4, 2, 8, 5, 1, 7],
})
print("Employees table:\n", employees)

# Small synthetic "departments" table (for the merge demo below)
departments = pd.DataFrame({
    "dept_id": [1, 2, 3],
    "dept_name": ["Engineering", "Marketing", "Data Science"],
})
print("\nDepartments table:\n", departments)

# --- Filtering -------------------------------------------------------------
senior_high_earners = employees[
    (employees["years_experience"] >= 4) & (employees["salary"] > 70000)
]
print("\nFilter: senior (>=4 yrs) AND salary > 70000:\n", senior_high_earners)

# --- Groupby aggregation ----------------------------------------------------
dept_stats = (
    employees.groupby("dept_id")
    .agg(
        avg_salary=("salary", "mean"),
        max_salary=("salary", "max"),
        headcount=("emp_id", "count"),
        avg_experience=("years_experience", "mean"),
    )
    .round(1)
    .reset_index()
)
print("\nGroupby dept_id -> aggregated stats:\n", dept_stats)

# --- Merge -------------------------------------------------------------------
merged = employees.merge(departments, on="dept_id", how="left")
print("\nMerged employees + departments (left join on dept_id):\n",
      merged[["name", "dept_name", "salary"]])

# Merge the aggregated stats too, to get readable department names
dept_stats_named = dept_stats.merge(departments, on="dept_id", how="left")
print("\nDepartment stats with readable names:\n",
      dept_stats_named[["dept_name", "headcount", "avg_salary",
                         "max_salary", "avg_experience"]])

section("Done. NumPy demonstrated vectorization/masks/matmul; "
        "Pandas demonstrated filter/groupby/merge.")
