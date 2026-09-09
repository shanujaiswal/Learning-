# Why More Features Isn't Automatically Better

--> After the Feature Engineering Fundamentals and Encoding files, it's tempting to create as many features as possible and let the model "figure out" which ones matter -- but this instinct is often wrong. Irrelevant or redundant features can hurt model performance, slow down training significantly, and make a model harder to interpret -- Feature Selection is the discipline of deliberately choosing which features actually deserve to be included.

# The Curse of Dimensionality

--> As the number of features grows, the amount of DATA needed to reliably learn patterns across that feature space grows exponentially, not linearly -- with too many features relative to the number of training examples, the feature space becomes so sparse that "nearby" points (critical for distance-based algorithms like KNN, covered in the Classification file) become meaningless, and models become far more prone to overfitting (covered in depth in its own file), since there's enough dimensional "room" to fit noise rather than genuine signal.
--> This is precisely the practical motivation behind Dimensionality Reduction techniques like PCA, covered in the Unsupervised Learning file -- reducing dimensionality isn't just about visualization, it directly combats this curse.

# Filter Methods -- Selecting Features Independently of Any Model

## Correlation With the Target

--> A quick first-pass technique -- compute each feature's correlation with the target variable (echoing the correlation concept from the Statistics Fundamentals file), and drop features with near-zero correlation, on the reasoning that a feature barely related to the target is unlikely to help predict it.

```python
correlations = df.corr(numeric_only=True)["target"].abs().sort_values(ascending=False)
print(correlations)
# Features with correlation near 0 are candidates for removal
```

--> **Important caveat** -- this only catches LINEAR relationships. A feature with a strong but non-linear relationship to the target (e.g. a U-shaped relationship) can show near-zero linear correlation despite being genuinely predictive -- this filter method should inform, not automatically dictate, feature removal decisions.

## Removing Highly Correlated Features With EACH OTHER

--> Two features that are highly correlated WITH EACH OTHER (not with the target) carry largely redundant information -- keeping both adds complexity without adding much genuine new signal, and can specifically destabilize linear models (a problem called "multicollinearity," where the model can't reliably distinguish each correlated feature's individual contribution).

```python
correlation_matrix = df.corr(numeric_only=True).abs()
# Identify pairs of features with correlation above a threshold (e.g. 0.9) and consider dropping one from each pair
high_corr_pairs = [(col1, col2) for col1 in correlation_matrix.columns
                    for col2 in correlation_matrix.columns
                    if col1 != col2 and correlation_matrix.loc[col1, col2] > 0.9]
```

## Variance Threshold

--> A feature with near-zero variance (nearly the same value for almost every record) carries almost no discriminating information -- trivially removable regardless of any relationship to the target, since a feature that doesn't vary can't possibly help distinguish between outcomes.

```python
from sklearn.feature_selection import VarianceThreshold

selector = VarianceThreshold(threshold=0.01)
X_reduced = selector.fit_transform(X)
```

# Wrapper Methods -- Selecting Features Using Model Performance Directly

--> Unlike filter methods (which evaluate features independently of any specific model), wrapper methods repeatedly TRAIN a model with different feature subsets and use actual model performance (connecting to the Model Evaluation file's metrics) to decide which features to keep -- more computationally expensive, but directly optimized for the specific model being used.

## Recursive Feature Elimination (RFE)

--> Starts with ALL features, trains a model, removes the least important feature (based on the model's own internal importance ranking), and repeats -- progressively narrowing down to the most predictive subset.

```python
from sklearn.feature_selection import RFE
from sklearn.ensemble import RandomForestClassifier

model = RandomForestClassifier()
selector = RFE(model, n_features_to_select=10)
selector.fit(X_train, y_train)

selected_features = X_train.columns[selector.support_]
print(selected_features)
```

## Forward and Backward Selection

--> **Forward selection** -- starts with NO features, repeatedly adds whichever single feature improves model performance the most, until adding more features stops helping.
--> **Backward selection** -- starts with ALL features, repeatedly removes whichever single feature hurts performance the LEAST when removed, until removing more starts hurting meaningfully.
--> Both are conceptually simple but computationally expensive for datasets with many features, since they require training many models to evaluate each candidate addition/removal.

# Embedded Methods -- Selection Built Into the Model Itself

## L1 Regularization (Lasso) -- Automatic Feature Selection

--> As covered in the Overfitting/Regularization file, L1 regularization can shrink some feature coefficients all the way to EXACTLY zero -- effectively performing feature selection as a natural side effect of training the model, rather than as a separate preprocessing step.

```python
from sklearn.linear_model import Lasso

lasso = Lasso(alpha=0.1)
lasso.fit(X_train, y_train)

selected_features = X_train.columns[lasso.coef_ != 0]   # Features Lasso didn't shrink to zero
```

## Tree-Based Feature Importance

--> Random Forests and Gradient Boosting models (covered in the Overfitting/Ensemble Methods file) naturally compute a "feature importance" score for every feature during training, based on how much each feature actually contributed to reducing prediction error across the ensemble's many trees.

```python
from sklearn.ensemble import RandomForestClassifier
import pandas as pd

model = RandomForestClassifier()
model.fit(X_train, y_train)

importance_df = pd.DataFrame({
    "feature": X_train.columns,
    "importance": model.feature_importances_
}).sort_values("importance", ascending=False)

print(importance_df.head(10))   # The 10 most influential features, ranked by this model's own internal metric
```

--> This is one of the most commonly used, practically convenient feature selection techniques precisely because it comes "for free" from training a model you were likely going to build anyway -- no separate selection process required.

# Practical Guidance -- Balancing Selection Effort Against Payoff

--> For a small number of features (dozens), aggressive feature selection often matters less -- most modern algorithms (especially tree-based ensembles) handle some irrelevant features reasonably gracefully on their own.
--> For a large number of features (hundreds or thousands, common in text/genomic/sensor data), feature selection becomes genuinely essential -- both for fighting the curse of dimensionality directly and for keeping models interpretable and computationally tractable.
--> As with feature ENGINEERING (covered in that earlier file), feature SELECTION is iterative -- start broad, measure impact using the Model Evaluation file's rigorous validation methodology, and narrow down based on what actually, measurably helps, rather than by intuition alone.
