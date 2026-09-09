# Why Raw Data Needs Encoding and Scaling

--> Machine learning algorithms operate on NUMBERS -- but real data frequently arrives as categories ("red," "blue," "green") or as numeric values on wildly different scales (age: 0-100, income: 0-500,000). Both situations need addressing before most algorithms can use the data correctly, and getting this step wrong is a common, subtle source of poor model performance that has nothing to do with the choice of algorithm at all.

# Encoding Categorical Variables

## One-Hot Encoding -- The Standard Default

--> Converts a categorical column into MULTIPLE binary (0/1) columns, one per category -- avoids implying any false numeric ORDER or magnitude relationship between categories that don't actually have one.

```python
import pandas as pd

df = pd.DataFrame({"color": ["red", "blue", "green", "red"]})
pd.get_dummies(df, columns=["color"])

#    color_blue  color_green  color_red
# 0       0            0           1
# 1       1            0           0
# 2       0            1           0
# 3       0            0           1
```

--> Why not just assign red=1, blue=2, green=3? Because that would falsely imply green > blue > red numerically, and that "blue is twice red" -- a meaningless, misleading relationship for a model (especially a linear one) to pick up on, since color categories have no inherent numeric order.

## Label Encoding -- When Order Actually IS Meaningful

--> For ORDINAL categories (ones with a genuine, meaningful rank), simple integer encoding is appropriate and often preferable to one-hot encoding, since it preserves the real ordering information rather than discarding it.

```python
from sklearn.preprocessing import OrdinalEncoder

education_order = [["High School", "Bachelor's", "Master's", "PhD"]]
encoder = OrdinalEncoder(categories=education_order)
df["education_encoded"] = encoder.fit_transform(df[["education"]])
# High School -> 0, Bachelor's -> 1, Master's -> 2, PhD -> 3 -- the numeric order now genuinely reflects reality
```

--> Using Label Encoding on a NON-ordinal variable (like color) is a common mistake -- it silently introduces a false numeric ordering that a model (especially a distance-based one like KNN, covered in the Classification file) will incorrectly treat as meaningful.

## Handling High-Cardinality Categorical Variables

--> One-hot encoding a column with hundreds or thousands of unique categories (a "zip code" or "product SKU" column) creates an enormous number of new columns, most of which are extremely sparse (almost entirely zeros) -- a genuine practical problem, not just an inefficiency.
--> **Frequency Encoding** -- replace each category with how often it appears in the dataset, condensing high-cardinality data into a single informative numeric column.
--> **Target Encoding** -- replace each category with the average target value for that category (e.g. replace each zip code with the average house price in that zip code) -- powerful, but must be computed CAREFULLY using only training data (never leaking test-set target values into the encoding), directly connecting to the data leakage warning covered in the Feature Engineering Fundamentals file.

```python
freq_map = df["zip_code"].value_counts(normalize=True)
df["zip_code_frequency"] = df["zip_code"].map(freq_map)

# Target encoding -- computed ONLY on training data, then applied to both train and test
target_means = train_df.groupby("zip_code")["house_price"].mean()
train_df["zip_code_target_enc"] = train_df["zip_code"].map(target_means)
test_df["zip_code_target_enc"] = test_df["zip_code"].map(target_means)   # Uses train-derived means, never recomputed on test data
```

# Scaling Numeric Features

## Why Scale Matters -- Algorithm-Dependent

--> **Distance-based algorithms** (K-Nearest Neighbors, K-Means clustering, both covered in earlier ML files) are directly, severely affected by feature scale -- a feature ranging 0-500,000 (income) will completely dominate a distance calculation over a feature ranging 0-100 (age), even if age is actually the more predictive feature.
--> **Gradient-descent-based algorithms** (Linear/Logistic Regression, and especially Neural Networks in the Deep Learning folder) converge far more reliably and quickly when features are on comparable scales -- unscaled data can cause training to be slow or numerically unstable.
--> **Tree-based algorithms** (Decision Trees, Random Forests, Gradient Boosting) are generally UNAFFECTED by scale -- a tree simply asks "is this value above or below a threshold," which works identically regardless of the feature's numeric range, making scaling unnecessary (though harmless) for these specific algorithms.

## Standardization (Z-Score Scaling)

--> Rescales a feature to have a mean of 0 and a standard deviation of 1 -- directly connecting to the Standard Deviation concept covered in the Statistics Fundamentals file in the Data Analyst folder.

```python
from sklearn.preprocessing import StandardScaler

scaler = StandardScaler()
X_train_scaled = scaler.fit_transform(X_train)     # Learns the mean/std FROM the training data
X_test_scaled = scaler.transform(X_test)              # Applies that SAME learned mean/std to test data -- never re-fit on test data
```

--> **Critical rule** -- always `fit` the scaler ONLY on training data, then `transform` both train and test with those same learned parameters. Fitting separately on the test set (or on the full combined dataset before splitting) leaks information about the test set's distribution into the training process -- another specific, easy-to-miss instance of the data leakage problem.

## Min-Max Normalization

--> Rescales a feature to a fixed range, typically 0 to 1 -- useful when an algorithm specifically expects bounded input (some neural network activation functions, covered in the Deep Learning folder, work best with inputs in a known range).

```python
from sklearn.preprocessing import MinMaxScaler

scaler = MinMaxScaler()
X_train_scaled = scaler.fit_transform(X_train)
```

--> Downside compared to Standardization -- Min-Max scaling is highly sensitive to outliers, since a single extreme value stretches the entire scale, compressing every other "normal" value into a narrow sub-range near 0.

## Robust Scaling -- For Data With Outliers

--> Uses the median and interquartile range (IQR, covered in the Data Cleaning file's outlier-detection discussion) instead of mean/standard deviation -- far less distorted by extreme outlier values than either Standardization or Min-Max scaling.

```python
from sklearn.preprocessing import RobustScaler

scaler = RobustScaler()
X_train_scaled = scaler.fit_transform(X_train)
```

# Putting It Together -- Pipelines

--> In practice, encoding and scaling steps should be bundled together with the model itself into a single scikit-learn `Pipeline`, ensuring the exact same preprocessing steps are applied consistently and correctly to any new data, and specifically preventing the leakage mistakes described above from creeping in accidentally.

```python
from sklearn.pipeline import Pipeline
from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.linear_model import LogisticRegression

preprocessor = ColumnTransformer([
    ("num", StandardScaler(), ["age", "income"]),
    ("cat", OneHotEncoder(), ["color", "region"])
])

pipeline = Pipeline([
    ("preprocessing", preprocessor),
    ("model", LogisticRegression())
])

pipeline.fit(X_train, y_train)         # Fits preprocessing AND the model together, correctly, in one step
pipeline.predict(X_test)                 # Applies the SAME fitted preprocessing to new data automatically
```
