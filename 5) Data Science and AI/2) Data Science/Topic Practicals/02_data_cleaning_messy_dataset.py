"""
02 - Data Cleaning / Wrangling a Messy Dataset
================================================
Companion script for: "Data Cleaning/Wrangling".

Builds a deliberately messy small DataFrame (missing values, duplicate
rows, inconsistent string casing/whitespace, wrong dtypes) and walks
through cleaning it step by step, printing before/after state at each
step and explaining *why* each choice was made.
"""

import numpy as np
import pandas as pd

SEP = "=" * 70


def section(title: str) -> None:
    print(f"\n{SEP}\n{title}\n{SEP}")


# ---------------------------------------------------------------------------
# STEP 0 -- Build a deliberately messy dataset
# ---------------------------------------------------------------------------

section("STEP 0: The messy raw data")

raw = pd.DataFrame({
    "customer_id": [1, 2, 3, 4, 4, 5, 6, 7, 8],
    "name": [" Alice ", "bob", "CHARLIE", "Diana", "Diana",
              "eve ", "Frank", None, "  Grace"],
    "city": ["Mumbai", "delhi ", "BANGALORE", "Mumbai", "Mumbai",
             "Chennai", None, "Delhi", "bangalore"],
    # age stored as strings, with a missing value and a stray space
    "age": ["29", "34", "41", "25", "25", "N/A", "38", "30", " 45"],
    # purchase_amount has NaN and one value stored as a string with a $
    "purchase_amount": [250.0, np.nan, 480.5, 125.0, 125.0,
                         300.0, np.nan, "$210", 610.0],
    "signup_date": ["2023-01-15", "2023-02-20", "2023-01-30", "2023-03-05",
                     "2023-03-05", "2023-04-01", "2023-04-18", "2023-05-02",
                     "2023-05-20"],
})
print(raw)
print("\ndtypes:\n", raw.dtypes)

print(
    "\nProblems visible above:\n"
    "  1. 'name' has leading/trailing whitespace and inconsistent casing.\n"
    "  2. 'city' has inconsistent casing/whitespace ('delhi ', 'BANGALORE').\n"
    "  3. 'age' is stored as text (dtype=object), including 'N/A' for missing.\n"
    "  4. 'purchase_amount' mixes float and a currency-formatted string.\n"
    "  5. Row for customer_id=4 (Diana) is duplicated exactly.\n"
    "  6. 'name' has a missing value (customer_id=7).\n"
    "  7. 'city' has a missing value (customer_id=6).\n"
)


# ---------------------------------------------------------------------------
# STEP 1 -- Fix inconsistent string casing / whitespace
# ---------------------------------------------------------------------------

section("STEP 1: Normalize string columns (strip + lowercase, then title-case)")

df = raw.copy()

# .str.strip() removes leading/trailing whitespace; .str.lower() makes
# comparisons/grouping consistent (e.g. "delhi " vs "Delhi" vs "DELHI"
# would otherwise be treated as three different cities).
for col in ["name", "city"]:
    before_unique = df[col].dropna().unique()
    df[col] = df[col].str.strip().str.lower()
    print(f"Column '{col}' unique values BEFORE: {list(before_unique)}")
    print(f"Column '{col}' unique values AFTER : {list(df[col].dropna().unique())}")

# Title-case for readability once values are normalized (purely cosmetic,
# safe now that casing/whitespace no longer affects equality/grouping).
df["name"] = df["name"].str.title()
df["city"] = df["city"].str.title()
print("\nAfter title-casing for display:\n", df[["name", "city"]])


# ---------------------------------------------------------------------------
# STEP 2 -- Fix wrong dtypes
# ---------------------------------------------------------------------------

section("STEP 2: Fix dtypes (age -> numeric, purchase_amount -> numeric)")

print("age BEFORE:", df["age"].tolist(), "| dtype:", df["age"].dtype)
# "N/A" is not a number -> pd.to_numeric with errors='coerce' turns it (and
# any other unparsable text) into NaN instead of raising, so we can decide
# how to handle it explicitly in the missing-values step below.
df["age"] = pd.to_numeric(df["age"].str.strip(), errors="coerce")
print("age AFTER :", df["age"].tolist(), "| dtype:", df["age"].dtype)

print("\npurchase_amount BEFORE:", raw["purchase_amount"].tolist())
# Strip a leading '$' if present, then coerce to numeric. Using .astype(str)
# first so the mix of float/str doesn't break .str accessor calls.
df["purchase_amount"] = (
    df["purchase_amount"].astype(str).str.replace("$", "", regex=False)
)
df["purchase_amount"] = pd.to_numeric(df["purchase_amount"], errors="coerce")
print("purchase_amount AFTER :", df["purchase_amount"].tolist(),
      "| dtype:", df["purchase_amount"].dtype)

# signup_date -> real datetime dtype instead of plain strings, enabling
# date arithmetic / resampling later.
df["signup_date"] = pd.to_datetime(df["signup_date"])
print("\nsignup_date dtype AFTER conversion:", df["signup_date"].dtype)


# ---------------------------------------------------------------------------
# STEP 3 -- Handle missing values
# ---------------------------------------------------------------------------

section("STEP 3: Handle missing values (dropna vs fillna, decided per column)")

print("Missing-value counts BEFORE handling:\n", df.isna().sum())

# 'name' is an identifying field -- there's no sensible way to *guess* a
# missing name, and losing one row out of a handful is an acceptable cost
# to keep the data trustworthy. Decision: drop rows with missing name.
before_rows = len(df)
df = df.dropna(subset=["name"])
print(f"\nDropped rows with missing 'name': {before_rows} -> {len(df)} rows.")

# 'city' is categorical and missing at random for one customer. Rather than
# drop a whole row over one field, we fill with an explicit "Unknown"
# sentinel so it stays visible in groupby/analysis instead of silently
# vanishing or being confused with a real city.
df["city"] = df["city"].fillna("Unknown")
print("'city' missing values filled with 'Unknown'.")

# 'age' and 'purchase_amount' are numeric. Dropping rows would lose otherwise
# valid records, so instead we impute with the column median -- more robust
# to outliers than the mean, and preserves the row for other analyses.
for col in ["age", "purchase_amount"]:
    median_val = df[col].median()
    n_missing = df[col].isna().sum()
    df[col] = df[col].fillna(median_val)
    print(f"'{col}': filled {n_missing} missing value(s) with median "
          f"({median_val:.1f}).")

print("\nMissing-value counts AFTER handling:\n", df.isna().sum())


# ---------------------------------------------------------------------------
# STEP 4 -- Remove duplicate rows
# ---------------------------------------------------------------------------

section("STEP 4: Remove duplicate rows")

print("Rows that are exact duplicates:\n", df[df.duplicated(keep=False)])

before_rows = len(df)
df = df.drop_duplicates()
print(f"\nDropped exact duplicate rows: {before_rows} -> {len(df)} rows.")


# ---------------------------------------------------------------------------
# STEP 5 -- Final dtype tidy-up + result
# ---------------------------------------------------------------------------

section("STEP 5: Final cleaned dataset")

df["age"] = df["age"].astype(int)
df = df.reset_index(drop=True)

print("Cleaned DataFrame:\n", df)
print("\nFinal dtypes:\n", df.dtypes)

section("Summary of cleaning steps applied")
print(
    "1. Stripped whitespace + normalized casing on 'name' and 'city'.\n"
    "2. Converted 'age' and 'purchase_amount' from text/mixed to numeric,\n"
    "   coercing unparsable values ('N/A', '$210') safely.\n"
    "3. Converted 'signup_date' strings to real datetime64 dtype.\n"
    "4. Dropped rows missing an identifying field ('name').\n"
    "5. Filled missing categorical 'city' with an explicit 'Unknown' label.\n"
    "6. Filled missing numeric fields with the column median (robust to\n"
    "   outliers, preserves row count).\n"
    "7. Removed exact duplicate rows.\n"
)
