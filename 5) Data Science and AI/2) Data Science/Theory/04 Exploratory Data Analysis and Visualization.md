# What EDA Is For

--> Exploratory Data Analysis (EDA) means getting to know a dataset BEFORE modeling it -- understanding its shape, spotting quality issues the Data Cleaning file's techniques might have missed, and forming hypotheses about which variables actually matter, rather than blindly throwing raw data at a model and hoping for the best.

# Univariate Analysis -- Understanding One Variable at a Time

```python
import matplotlib.pyplot as plt
import seaborn as sns

df["revenue"].describe()               # Count, mean, std, min/max, quartiles -- the quick numeric summary

sns.histplot(df["revenue"], bins=30)     # Shape of the distribution -- normal? skewed? multiple peaks (bimodal)?
plt.show()

sns.boxplot(x=df["revenue"])              # Visualizes median, quartiles, and outliers in one compact chart
```

--> A histogram immediately reveals things a table of numbers hides -- a bimodal distribution (two distinct peaks) often means the data actually represents two different underlying populations mixed together (e.g. new customers vs returning customers spending very differently), a genuinely important finding a mean/median alone would completely mask.

# Bivariate Analysis -- Relationships Between Two Variables

```python
sns.scatterplot(x="ad_spend", y="conversions", data=df)   # Visualize the relationship between two numeric variables

correlation_matrix = df.corr(numeric_only=True)             # Correlation between every pair of numeric columns
sns.heatmap(correlation_matrix, annot=True, cmap="coolwarm")
```

--> A correlation heatmap gives a fast overview of which variables move together across an entire dataset at once -- directly connecting to the correlation concept from the Statistics Fundamentals file, and its important "correlation isn't causation" caveat still fully applies here.

# Categorical Data Analysis

```python
df["region"].value_counts()                       # How many records per category
sns.barplot(x="region", y="revenue", data=df, estimator="mean")   # Average revenue per region, visually compared
sns.countplot(x="region", data=df)                   # Simple frequency count per category
```

# Time Series Exploration

```python
df.set_index("date")["revenue"].plot()      # Visualize revenue over time -- trend, seasonality, and anomalies become visible
```

--> Immediately reveals patterns statistics alone would miss -- a clear weekly cycle (weekend dips), an overall upward trend, or a sudden one-time spike/drop worth investigating separately before drawing conclusions from the aggregate numbers.

# Pair Plots -- A Fast Multivariate Overview

```python
sns.pairplot(df[["revenue", "ad_spend", "customer_age"]])
# Generates a grid of scatter plots for every pair of listed variables at once -- a fast first look at multiple relationships simultaneously
```

# What to Look For During EDA

--> Distribution shape (normal, skewed, bimodal) -- informs which statistical/modeling assumptions are actually valid for this specific data.
--> Relationships between features and the target variable you eventually want to predict -- an early hint at which features might matter for the Machine Learning folder's feature engineering and modeling steps.
--> Data quality red flags missed during cleaning -- an EDA chart often reveals a lingering issue (an impossible negative age, a suspicious spike of exactly-zero values suggesting a placeholder rather than real data) that summary statistics alone didn't surface.

# EDA Is Iterative With Cleaning, Not a Separate Phase

--> Discovering an issue during EDA routinely sends you back to the Data Cleaning file's techniques -- in practice, cleaning and EDA are tightly interleaved, exactly as the Data Science Lifecycle file described the overall process as iterative rather than strictly linear.
