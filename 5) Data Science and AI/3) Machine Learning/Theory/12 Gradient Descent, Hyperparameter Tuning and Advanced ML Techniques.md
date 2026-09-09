# Why This File Exists

--> Several practical techniques got referenced but never fully explained across the earlier files in this folder -- gradient descent was mentioned in passing in the Fundamentals file ("learning really means optimization") and properly detailed only in the Deep Learning folder's Neural Network Fundamentals file, even though it's the optimization engine behind plain Linear/Logistic Regression too. This file closes that gap, then covers a handful of specialized-but-common real-world ML techniques (systematic hyperparameter search, imbalanced data, nonlinear dimensionality reduction, time series, anomaly detection) that come up constantly in practice but didn't fit neatly into any earlier file's scope.

# Gradient Descent -- How "Learning" Actually Minimizes the Loss

--> Gradient Descent is the optimization algorithm that adjusts a model's parameters (weights) step by step to minimize its loss function -- at every step, it computes the GRADIENT (the direction of steepest increase of the loss, with respect to each parameter) and moves the parameters a small step in the OPPOSITE direction, since that's the direction the loss decreases fastest.

```
new_weight = old_weight - learning_rate * gradient

(Repeated over and over -- each step nudges the weight slightly toward
a value that produces lower loss, until the loss stops meaningfully improving)
```

--> **Batch Gradient Descent** -- computes the gradient using the ENTIRE training dataset before taking a single step -- produces a smooth, stable path toward the minimum, but is slow and memory-heavy for large datasets, since nothing updates until every example has been processed once.
--> **Stochastic Gradient Descent (SGD)** -- computes the gradient using just ONE randomly chosen training example per step -- extremely fast per step and can escape shallow local minima due to its inherent noisiness, but that same noise makes its path toward the minimum erratic rather than smooth.
--> **Mini-Batch Gradient Descent** -- the practical middle ground used almost universally in real training (directly connecting to the `batch_size` parameter in the Deep Learning folder's `model.fit()` calls) -- computes the gradient over a small batch (e.g. 32-256 examples) per step, balancing Batch's stability against SGD's speed and scalability.

```python
from sklearn.linear_model import SGDRegressor

# scikit-learn's own SGD-based linear model, useful for datasets too large to
# comfortably fit the closed-form/batch solution used by plain LinearRegression
sgd_model = SGDRegressor(learning_rate="constant", eta0=0.01, max_iter=1000)
sgd_model.fit(X_train, y_train)
```

# The Learning Rate -- The Single Most Sensitive Hyperparameter

--> The learning rate controls the SIZE of each step taken during gradient descent -- arguably the single hyperparameter most likely to make or break training.

```
Learning rate too small:  Training crawls forward extremely slowly, may not converge within a reasonable number of steps at all.
Learning rate too large:  Steps overshoot the minimum entirely, causing loss to oscillate wildly or even diverge (increase toward infinity).
Learning rate well-tuned:  Loss decreases steadily and settles near the minimum in a reasonable number of steps.
```

--> **Learning rate schedules** -- rather than a single fixed value for the entire training run, many practical setups start with a larger learning rate (fast initial progress) and gradually shrink it over time (fine-tuning precision as training approaches the minimum) -- directly related to why `optimizer="adam"` (covered in the Deep Learning folder) is so widely preferred over plain SGD in practice, since Adam adapts its effective step size per-parameter automatically rather than requiring a hand-tuned fixed rate or manual schedule.

# Hyperparameter Tuning Methodology

--> A **hyperparameter** (`alpha` in Ridge/Lasso from the Overfitting file, `k` in K-Means, `max_depth` in Random Forests) is a setting chosen BEFORE training rather than learned FROM the data during training -- picking good hyperparameter values matters as much as picking a good algorithm, and doing it systematically (rather than by hand-guessing) is its own discipline.

## Grid Search -- Exhaustive but Expensive

--> `GridSearchCV` tries EVERY combination of a specified set of hyperparameter values, evaluating each combination with cross-validation (covered in the Model Evaluation file), and returns whichever combination scored best -- guaranteed to find the best combination within the specified grid, but the number of combinations (and therefore the compute cost) grows multiplicatively with every additional hyperparameter or value tried.

```python
from sklearn.model_selection import GridSearchCV
from sklearn.ensemble import RandomForestClassifier

param_grid = {
    "n_estimators": [50, 100, 200],
    "max_depth": [4, 8, 12],
}

grid_search = GridSearchCV(RandomForestClassifier(), param_grid, cv=5, scoring="f1")
grid_search.fit(X_train, y_train)

print(grid_search.best_params_)   # The combination that scored highest across the 5 folds
```

## Randomized Search -- Sampling Instead of Exhausting

--> `RandomizedSearchCV` samples a fixed NUMBER of random combinations from the specified hyperparameter ranges, rather than trying every single one -- in practice, it often finds a comparably good combination to Grid Search in a small fraction of the compute time, since not every hyperparameter matters equally, and randomly sampling covers the important ones more efficiently than exhaustively covering every unimportant combination too.

```python
from sklearn.model_selection import RandomizedSearchCV
from scipy.stats import randint

param_distributions = {
    "n_estimators": randint(50, 300),
    "max_depth": randint(3, 15),
}

random_search = RandomizedSearchCV(
    RandomForestClassifier(), param_distributions, n_iter=20, cv=5, scoring="f1", random_state=42
)
random_search.fit(X_train, y_train)
```

## Bayesian Optimization -- Searching Smartly, Not Blindly

--> Both Grid and Randomized Search treat every trial as independent -- they never learn from PAST trials to inform which combination to try NEXT. Bayesian Optimization instead builds a probabilistic model of "which hyperparameter regions tend to score well," based on every trial run so far, and uses that model to intelligently choose the most PROMISING next combination to try -- conceptually similar to how Informed Search (the AI Fundamentals file's A* algorithm) uses a heuristic to explore promising paths first rather than exploring blindly like Breadth/Depth-First Search does.
--> In practice, this means Bayesian Optimization (implemented by libraries like `Optuna` or `Hyperopt`) typically finds a strong hyperparameter combination in far fewer trials than Grid or Randomized Search, which matters enormously when each individual trial (a full model training run) is itself expensive/slow, as is often the case for the Deep Learning folder's neural networks.

# Handling Imbalanced Data

--> Directly extending the "accuracy is misleading" fraud-detection scenario opening the Model Evaluation file -- when one class vastly outnumbers another (99% legitimate, 1% fraud), a model trained naively tends to simply learn to predict the majority class most of the time, since doing so already minimizes average loss even while being useless at the actual task of catching the rare, important class.

--> **Class Weighting** -- tells the algorithm to treat mistakes on the minority class as more COSTLY than mistakes on the majority class during training, without touching the data itself at all.

```python
from sklearn.ensemble import RandomForestClassifier

# "balanced" automatically weights each class inversely proportional to its frequency --
# rarer classes get proportionally larger weight, penalizing the model more for misclassifying them
rf = RandomForestClassifier(class_weight="balanced")
rf.fit(X_train, y_train)
```

--> **Random Oversampling** -- duplicates existing minority-class examples until the classes are more balanced -- simple, but risks overfitting to those exact duplicated examples since no genuinely new information is added.
--> **Random Undersampling** -- removes majority-class examples until the classes are more balanced -- simple, but risks throwing away potentially useful majority-class information, especially when the majority class isn't enormous to begin with.
--> **SMOTE (Synthetic Minority Oversampling Technique)** -- rather than simply duplicating existing minority examples, SMOTE generates entirely NEW synthetic minority-class examples by interpolating between existing minority examples and their nearest neighbors (directly connecting to the distance/similarity concepts underlying KNN in the Classification file) -- adds genuinely new, plausible minority-class data points rather than exact duplicates, generally producing better-generalizing models than plain oversampling.

```python
from imblearn.over_sampling import SMOTE

smote = SMOTE(random_state=42)
X_resampled, y_resampled = smote.fit_resample(X_train, y_train)
# X_resampled/y_resampled now contain synthetic minority-class examples --
# critically, SMOTE must be applied ONLY to the training set, never to the test set,
# to avoid leaking synthetic information into the supposedly "unseen" evaluation data
```

# t-SNE and UMAP -- Nonlinear Dimensionality Reduction

--> PCA (covered in the Unsupervised Learning file) finds LINEAR combinations of features -- excellent for capturing overall variance, but it can badly distort or entirely miss complex, curved/nonlinear structure in high-dimensional data (e.g. data that genuinely lies along a twisted, curved manifold rather than a flat plane).
--> **t-SNE (t-Distributed Stochastic Neighbor Embedding)** -- specifically optimized for VISUALIZATION, it tries to preserve LOCAL structure -- points that are close together in the original high-dimensional space stay close together in the resulting 2D/3D plot -- at the cost of not necessarily preserving global distances/structure faithfully (the overall spacing between distant clusters in a t-SNE plot isn't reliably meaningful, only the local groupings are).

```python
from sklearn.manifold import TSNE

tsne = TSNE(n_components=2, perplexity=30, random_state=42)
X_embedded = tsne.fit_transform(X)   # Purely for visualization -- rarely used as input to further modeling
```

--> **UMAP (Uniform Manifold Approximation and Projection)** -- solves a similar visualization problem to t-SNE, but runs significantly faster on large datasets and tends to preserve more of the GLOBAL structure (relative distances between distant clusters) alongside the local structure -- increasingly preferred over t-SNE for large modern datasets for that reason, though both remain purely exploratory/visualization tools rather than general-purpose preprocessing steps for downstream modeling, unlike PCA.

```python
import umap

reducer = umap.UMAP(n_components=2, random_state=42)
X_embedded = reducer.fit_transform(X)
```

--> **When to reach for which** -- PCA first, always, since it's fast and its components are directly interpretable (explained variance ratio) -- reach for t-SNE/UMAP specifically when PCA's 2D projection still looks like a shapeless blob, suggesting the real structure is nonlinear and a manifold-aware technique is needed to actually see it.

# Time-Series-Specific Machine Learning

--> Time series data (values recorded sequentially over time -- stock prices, sensor readings, monthly sales) breaks a core assumption most ML techniques rely on -- that training examples are independent of each other -- since consecutive time points are typically highly correlated with their neighbors, and the standard random train/test split from the Fundamentals file would leak future information into the training set.

--> **Stationarity** -- a stationary time series has statistical properties (mean, variance) that stay roughly constant over time -- many classical time-series models assume stationarity, so a genuinely trending or seasonal series usually needs to be transformed (differencing -- subtracting each value from the previous one) before those models apply cleanly.

```python
from statsmodels.tsa.stattools import adfuller

result = adfuller(time_series)
print(f"p-value: {result[1]}")   # A small p-value (typically < 0.05) suggests the series is already stationary
```

--> **ARIMA (AutoRegressive Integrated Moving Average)** -- a classical, still widely used time-series forecasting model combining three components: an AutoRegressive term (predicting the next value from a weighted combination of previous values), an Integrated term (differencing, to handle non-stationarity), and a Moving Average term (predicting from past forecast errors).

```python
from statsmodels.tsa.arima.model import ARIMA

model = ARIMA(time_series, order=(1, 1, 1))   # (p, d, q) -- AR terms, differencing order, MA terms
fitted_model = model.fit()
forecast = fitted_model.forecast(steps=10)   # Predict the next 10 time points
```

--> **Time-Series Cross-Validation / Walk-Forward Validation** -- standard K-Fold Cross-Validation (Model Evaluation file) shuffles data across folds, which would let a model "see the future" when evaluated on a fold that's chronologically earlier than some of its training data -- walk-forward validation instead always trains on a chronologically EARLIER window and tests on the immediately FOLLOWING window, sliding that window forward through time, exactly mimicking how the model would actually be used in production (only ever predicting forward from what's already happened).

```python
from sklearn.model_selection import TimeSeriesSplit

tscv = TimeSeriesSplit(n_splits=5)
for train_index, test_index in tscv.split(X):
    X_train, X_test = X[train_index], X[test_index]
    # Each successive fold's training window grows to include more of the past,
    # and its test window is always chronologically AFTER its own training window
```

# Anomaly and Outlier Detection

--> Related to, but distinct from, standard classification -- anomaly detection identifies rare, unusual data points WITHOUT necessarily having labeled examples of what "anomalous" looks like in advance (fraud detection, manufacturing defect detection, network intrusion detection), often because anomalies are too rare and too varied to have been comprehensively labeled ahead of time.

--> **Isolation Forest** -- builds many random decision trees (echoing the Random Forest concept from the Overfitting/Ensemble file, but used here for a different purpose), each repeatedly splitting data on random features/thresholds -- the key insight is that anomalies, being rare and different, tend to get ISOLATED into their own tiny partition in FEWER splits than normal points require, so a data point's average path length across all the trees becomes its anomaly score.

```python
from sklearn.ensemble import IsolationForest

iso_forest = IsolationForest(contamination=0.05, random_state=42)   # Assume ~5% of data is anomalous
predictions = iso_forest.fit_predict(X)   # Returns -1 for anomalies, 1 for normal points
```

--> **One-Class SVM** -- learns a boundary that encloses the "normal" data as tightly as possible, treating any point falling OUTSIDE that boundary as an anomaly -- conceptually related to the standard SVM's margin-maximizing boundary between classes, except here there's only one class of (mostly) normal data to fit a boundary around, rather than a boundary separating two known classes.

```python
from sklearn.svm import OneClassSVM

oc_svm = OneClassSVM(nu=0.05)   # nu approximates the expected fraction of outliers
predictions = oc_svm.fit_predict(X)   # Also returns -1 for anomalies, 1 for normal points
```

# How These Techniques Fit Together in Practice

--> None of these are exotic edge cases -- systematic hyperparameter tuning, class imbalance, and anomaly detection specifically come up in the exact same fraud-detection/medical-diagnosis scenarios already referenced throughout the Model Evaluation and Classification files, just requiring these additional tools once a real, messy, imbalanced production dataset replaces a clean textbook example. The MLOps folder's monitoring discipline (data drift, silent model degradation) is precisely where anomaly detection and careful validation techniques like walk-forward validation matter most in an ongoing, deployed system, not just during one-off model development.
