# Regression -- Predicting a Number

--> Regression algorithms predict a continuous numeric value -- house prices from square footage, sales from ad spend, temperature from time of day -- covered as a category in the Fundamentals file.

# Linear Regression -- The Foundational Algorithm

--> Fits a straight line (or, with multiple features, a flat plane/hyperplane) through the data, finding the line that minimizes the total distance between the line and every actual data point.

```
y = m*x + b        (simple linear regression, one feature)
y = w1*x1 + w2*x2 + ... + b     (multiple linear regression, many features)
```

```python
from sklearn.linear_model import LinearRegression

model = LinearRegression()
model.fit(X_train, y_train)
predictions = model.predict(X_test)

print(model.coef_)        # The learned weight for each feature
print(model.intercept_)     # The learned "b" -- baseline value when all features are 0
```

--> The learned coefficients are directly interpretable -- "each additional bedroom adds $15,000 to predicted price" -- a major advantage of linear models over more complex "black box" algorithms when interpretability matters as much as raw predictive accuracy.

# How Training Actually Works -- Minimizing Loss

--> **Mean Squared Error (MSE)** -- the standard loss function for regression -- the average of the squared differences between predicted and actual values. Squaring penalizes large errors disproportionately more than small ones, and keeps the value always positive.

```
MSE = (1/n) * Σ(actual - predicted)²
```

--> Training finds the coefficients that MINIMIZE this loss -- via a closed-form mathematical solution for simple linear regression, or iteratively via Gradient Descent (covered in depth once Deep Learning training is introduced, since the exact same optimization idea scales up to neural networks).

# Polynomial and Non-Linear Regression

--> When the true relationship isn't a straight line (e.g. diminishing returns on ad spend), Polynomial Regression fits a curve by adding polynomial terms (x², x³) as additional features -- still technically "linear" in its coefficients, just fit to curved input features.

```python
from sklearn.preprocessing import PolynomialFeatures

poly = PolynomialFeatures(degree=2)
X_poly = poly.fit_transform(X_train)   # Adds x² terms, then a normal LinearRegression fits on top
```

--> Higher-degree polynomials fit training data increasingly closely, but risk overfitting badly (covered in depth in the Overfitting file) -- a curve so flexible it wiggles to match every training point's noise won't generalize to new data at all.

# Regression Trees -- A Non-Linear Alternative

--> Decision Trees (also usable for classification, covered in the next file) can perform regression by splitting the data into regions based on feature thresholds, predicting the average target value within each resulting region -- captures non-linear relationships naturally, without needing to manually engineer polynomial features.

```python
from sklearn.tree import DecisionTreeRegressor

tree_model = DecisionTreeRegressor(max_depth=5)
tree_model.fit(X_train, y_train)
```

--> `max_depth` directly controls model complexity -- an unbounded tree can grow deep enough to perfectly memorize the training data (a severe overfitting risk revisited in the Overfitting file).

# Evaluating a Regression Model

--> **R² (R-squared)** -- the proportion of variance in the target variable explained by the model, ranging roughly 0 to 1 -- a quick, common first-glance metric, though not sufficient alone (covered in more depth in the Model Evaluation file).
--> **RMSE (Root Mean Squared Error)** -- the square root of MSE, expressed in the SAME units as the original target variable (e.g. dollars), making it more directly interpretable than raw MSE.

```python
from sklearn.metrics import r2_score, mean_squared_error
import numpy as np

r2 = r2_score(y_test, predictions)
rmse = np.sqrt(mean_squared_error(y_test, predictions))
```
