# Why This Is Most of the Real Work

--> As mentioned in the Data Analyst Workflow file, cleaning consumes the majority of most real projects' time -- real-world data is virtually never as clean as a tutorial dataset: missing values, duplicate records, inconsistent formatting, and outright errors are the norm, not the exception.

# Handling Missing Data

--> First, understand WHY data is missing -- Missing Completely at Random (no pattern), Missing at Random (related to other observed variables), or Missing Not at Random (related to the missing value itself, e.g. high earners disproportionately skipping an income survey question) -- the right handling strategy depends heavily on which case applies.

```python
df.isnull().sum()                        # Identify how much is missing, per column

df.dropna(subset=["critical_column"])      # Drop rows missing a truly essential field
df["age"].fillna(df["age"].median())        # Impute with median -- robust to outliers, per the Statistics file
df["category"].fillna("Unknown")             # For categorical data, an explicit "Unknown" label is often clearer than imputing a guess
```

--> Simple imputation (filling with mean/median) is easy but can distort the data's true variance -- more advanced approaches use other columns to predict a reasonable fill value (e.g. K-Nearest-Neighbors imputation), covered as a technique once the Machine Learning folder's KNN algorithm is introduced.

# Handling Duplicates

```python
df.duplicated().sum()          # Count exact duplicate rows
df.drop_duplicates()             # Remove them

df.duplicated(subset=["email"])   # Duplicates based on a specific key, even if other columns differ
```

--> "Fuzzy" duplicates (the same real-world entity recorded slightly differently -- "John Smith" vs "Jon Smith") require more sophisticated matching (string similarity algorithms) than exact duplicate detection can catch.

# Standardizing Formats and Types

```python
df["date"] = pd.to_datetime(df["date"], errors="coerce")   # Parse inconsistent date strings into a real datetime type
df["state"] = df["state"].str.strip().str.upper()             # Normalize whitespace and casing
df["price"] = df["price"].astype(float)                        # Ensure the correct data type -- a "price" column stored as text can't be summed
```

# Detecting and Handling Outliers

--> The IQR (Interquartile Range) method -- flag values falling far outside the range between the 25th and 75th percentile as potential outliers, a robust rule of thumb that doesn't assume a normal distribution.

```python
Q1 = df["revenue"].quantile(0.25)
Q3 = df["revenue"].quantile(0.75)
IQR = Q3 - Q1
outliers = df[(df["revenue"] < Q1 - 1.5*IQR) | (df["revenue"] > Q3 + 1.5*IQR)]
```

--> An outlier isn't automatically an ERROR to remove -- it might be a genuine, important data point (a legitimately huge sale) -- always investigate WHY a value is extreme before deciding whether to remove, cap, or keep it as-is.

# Data Validation -- Catching Errors Structurally

--> Beyond ad hoc checks, defining explicit validation rules (a percentage column must be between 0-100, a date can't be in the future) and systematically checking every record against them catches errors far more reliably than eyeballing the data.

# Documenting the Cleaning Process

--> Every cleaning decision (why a column was dropped, why a specific imputation method was chosen) should be documented in code/comments, not just applied silently -- a colleague (or your future self) revisiting the analysis needs to understand exactly what transformations were applied and why, directly connecting to the reproducibility principle covered in the Data Science Lifecycle file.
