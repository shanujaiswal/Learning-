# Data Science — Practical

Runnable Python companion scripts for the theory chapters in
`4) Data Science and AI/2) Data Science/Theory/`. Each script is
self-contained, generates its own synthetic data (no external datasets
needed), and can be run directly with `python <script>.py`.

## Setup

```bash
pip install numpy pandas matplotlib scipy statsmodels
```

Tested with Python 3.10+. All scripts write any output images (PNG) into
this same folder, next to the script.

## Chapter mapping

| # | Script | Theory chapter |
|---|--------|-----------------|
| 1 | `01_numpy_pandas_fundamentals.py` | 3. Python for DS (NumPy/Pandas) |
| 2 | `02_data_cleaning_messy_dataset.py` | 4. Data Cleaning/Wrangling |
| 3 | `03_eda_and_visualization.py` | 5. EDA/Visualization |
| 4 | `04_probability_and_statistics.py` | 6. Probability/Statistics for DS |
| 5 | `05_linear_algebra_for_ml.py` | 7. Linear Algebra for DS/AI |
| 6 | `06_gradient_descent_from_scratch.py` | 8. Calculus/Optimization for ML |
| 7 | `07_time_series_forecasting_demo.py` | 9. Time Series Forecasting |

(Chapters 1 "Roadmap" and 2 "Data Science Lifecycle" in the Theory folder
are conceptual/overview chapters and have no dedicated practical script —
they're covered indirectly by the workflow used across all scripts below.)

## What each script demonstrates

1. **`01_numpy_pandas_fundamentals.py`**
   Vectorized NumPy: boolean indexing, broadcasting, matrix multiplication.
   Pandas: building a DataFrame, filtering, `groupby` aggregation, and
   merging two tables — all on small synthetic "employees/departments" data.

2. **`02_data_cleaning_messy_dataset.py`**
   Builds a deliberately messy DataFrame (NaNs, duplicate rows, inconsistent
   casing/whitespace in strings, wrong dtypes) and cleans it step by step
   with before/after prints and inline explanations of each choice.

3. **`03_eda_and_visualization.py`**
   Synthetic dataset + `describe()`-style summary statistics, then three
   saved PNG charts: a histogram, a scatter plot with a fitted trend line,
   and a box plot used for outlier detection.

4. **`04_probability_and_statistics.py`**
   Simulates binomial and normal distributions with NumPy, visualizes them,
   and runs a real two-sample t-test with `scipy.stats` to compare two
   groups, reporting the p-value and conclusion.

5. **`05_linear_algebra_for_ml.py`**
   Dot products, matrix multiplication, transpose, inverse, determinant,
   and eigenvalues/eigenvectors via `numpy.linalg`, each annotated with its
   ML application (e.g. eigenvectors/eigenvalues → PCA).

6. **`06_gradient_descent_from_scratch.py`**
   Linear regression fit purely by hand-rolled gradient descent (no
   scikit-learn) on synthetic data, printing the MSE loss shrinking over
   iterations and plotting the fitted line.

7. **`07_time_series_forecasting_demo.py`**
   Synthetic trend + seasonality + noise series, seasonal decomposition via
   `statsmodels`, an Augmented Dickey-Fuller stationarity test, and a fitted
   ARIMA model with a forecast plot.

## Output files

Running scripts 3, 4, 6, and 7 will create PNG files in this folder
(e.g. `03_histogram.png`, `03_scatter_trend.png`, `03_boxplot.png`,
`04_distributions.png`, `06_gradient_descent_fit.png`,
`07_decomposition.png`, `07_forecast.png`). These are generated artifacts —
safe to delete and regenerate at any time.
