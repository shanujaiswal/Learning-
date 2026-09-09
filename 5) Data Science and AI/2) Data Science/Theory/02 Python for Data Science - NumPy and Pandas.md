# Why Not Just Use Plain Python

--> Plain Python lists/loops (covered in the Full Stack Python Notes) are flexible but slow for large-scale numeric work -- NumPy and Pandas are built specifically to make data manipulation both fast (backed by optimized, compiled C code under the hood) and expressive (operating on entire datasets at once, without explicit loops).

# NumPy -- The Foundation

--> NumPy's core data structure, the `ndarray`, stores data of a SINGLE type in a contiguous memory block -- this is precisely what makes it fast, unlike a Python list which can hold mixed types and pays overhead for that flexibility.

```python
import numpy as np

arr = np.array([1, 2, 3, 4, 5])
arr * 2                    # array([2, 4, 6, 8, 10]) -- applied to every element at once, no loop
arr[arr > 2]                # array([3, 4, 5]) -- boolean filtering, called "boolean indexing"

matrix = np.array([[1, 2], [3, 4]])
matrix.T                    # Transpose
np.dot(matrix, matrix)       # Matrix multiplication -- the core operation underlying most ML math
```

--> Vectorization -- applying an operation to a whole array at once (`arr * 2`) instead of looping element-by-element -- both far faster AND more readable, and the standard idiom throughout the entire NumPy/Pandas/ML ecosystem.

# Pandas -- Structured, Labeled Data

--> Built on top of NumPy, Pandas introduces the `DataFrame` -- a labeled, 2-dimensional table (rows and named columns), conceptually similar to a spreadsheet or a SQL table (covered extensively in the Full Stack Database track), but manipulable entirely in code.

```python
import pandas as pd

df = pd.read_csv("sales_data.csv")
df.head()                     # First 5 rows -- quick sanity check after loading
df.info()                      # Column types, non-null counts
df.describe()                   # Quick descriptive statistics per numeric column

df["revenue"].mean()
df[df["region"] == "East"]        # Filtering rows -- similar to a SQL WHERE clause
df.groupby("region")["revenue"].sum()   # Similar to a SQL GROUP BY
```

# Selecting and Filtering Data

```python
df.loc[0:5, ["name", "revenue"]]     # Select by label -- rows 0-5, specific columns
df.iloc[0:5, 0:2]                     # Select by integer position

df[(df["revenue"] > 1000) & (df["region"] == "East")]   # Combining multiple filter conditions
```

# Handling Missing Data

```python
df.isnull().sum()               # Count missing values per column -- the first check on any new dataset
df.dropna()                      # Remove rows with any missing values
df.fillna(df["revenue"].mean())   # Fill missing values with a computed value (covered further in Data Cleaning)
```

# Merging and Joining -- Pandas' Equivalent of SQL Joins

```python
merged = pd.merge(orders_df, customers_df, on="customer_id", how="left")
# Directly parallels the SQL JOIN concepts covered in the Full Stack Database Joins file
```

# Why This Pairing Is the Universal Starting Point

--> Nearly every subsequent file in this Data Science and Machine Learning section assumes data is being manipulated through Pandas DataFrames and NumPy arrays -- this is the shared vocabulary/toolkit connecting data cleaning, EDA, feature engineering, and model training into one consistent workflow, and the reason it's covered before any of those topics.
