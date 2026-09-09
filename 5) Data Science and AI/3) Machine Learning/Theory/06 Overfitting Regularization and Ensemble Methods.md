# Overfitting -- Memorizing Instead of Learning

--> A model that's TOO flexible/complex can essentially memorize the training data (including its noise and quirks) rather than learning the genuine underlying pattern -- performing excellently on training data but poorly on new, unseen data, directly explaining why the train/test split discipline from the Fundamentals file is non-negotiable.
--> **Underfitting** -- the opposite problem -- a model too simple to capture the real underlying pattern at all, performing poorly even on the training data itself.

```
Underfitting:  Training accuracy: 60%   Test accuracy: 58%    (model too simple, both scores low)
Good fit:        Training accuracy: 90%   Test accuracy: 88%    (small, healthy gap)
Overfitting:    Training accuracy: 99%   Test accuracy: 65%    (huge gap -- memorized training data, doesn't generalize)
```

# The Bias-Variance Tradeoff

--> **Bias** -- error from an overly simplistic model that can't capture the true underlying pattern (underfitting).
--> **Variance** -- error from a model that's overly sensitive to the specific training data it happened to see, producing wildly different results if trained on a slightly different sample (overfitting).
--> Every modeling choice (algorithm complexity, hyperparameters) shifts this balance -- the goal is finding the sweet spot minimizing TOTAL error, not eliminating bias or variance entirely (which is generally impossible at the same time).

# Regularization -- Penalizing Complexity Directly

--> Regularization adds a penalty term to a model's loss function that discourages unnecessarily large/complex parameter values, directly combating overfitting by keeping the model simpler than it would otherwise become.
--> **L1 Regularization (Lasso)** -- penalizes the absolute size of coefficients, and can shrink some coefficients all the way to exactly zero -- effectively performing automatic feature selection by eliminating less useful features entirely.
--> **L2 Regularization (Ridge)** -- penalizes the squared size of coefficients -- shrinks all coefficients toward zero somewhat, but rarely to exactly zero, keeping every feature but reducing each one's influence.

```python
from sklearn.linear_model import Ridge, Lasso

ridge_model = Ridge(alpha=1.0)   # alpha controls how strongly the penalty is applied
lasso_model = Lasso(alpha=1.0)
```

--> The `alpha` hyperparameter directly controls the bias-variance tradeoff -- too low, and regularization does nothing (risk of overfitting); too high, and the model becomes too constrained to fit even the real pattern (risk of underfitting).

# Ensemble Methods -- Combining Multiple Models

--> Rather than relying on one single model, Ensemble methods combine PREDICTIONS from multiple models, generally producing more accurate and more robust results than any single model alone.

# Bagging -- Random Forests

--> Trains many Decision Trees (covered in the Regression/Classification files), each on a randomly resampled subset of the training data, and averages their predictions (for regression) or takes a majority vote (for classification).
--> Individual Decision Trees are prone to overfitting badly (they can grow deep enough to memorize training data perfectly) -- averaging across many DIFFERENT trees, each seeing slightly different data, smooths out each individual tree's overfitting tendencies.

```python
from sklearn.ensemble import RandomForestClassifier

rf = RandomForestClassifier(n_estimators=100, max_depth=8)
rf.fit(X_train, y_train)
```

# Boosting -- Gradient Boosting and XGBoost

--> Rather than training many independent trees in parallel (bagging), Boosting trains trees SEQUENTIALLY, where each new tree specifically focuses on correcting the mistakes the previous trees made -- often achieves higher accuracy than Random Forests, at the cost of being more prone to overfitting if not carefully tuned, and being more sensitive to hyperparameter choices.
--> XGBoost, LightGBM, and CatBoost are optimized, widely-used implementations of gradient boosting, consistently among the top-performing algorithms in structured-data machine learning competitions.

```python
from xgboost import XGBClassifier

xgb_model = XGBClassifier(n_estimators=100, learning_rate=0.1, max_depth=4)
xgb_model.fit(X_train, y_train)
```

# Why This File Closes Out the Core ML Folder

--> Every technique covered here directly addresses the SAME fundamental challenge -- building a model that generalizes to new data rather than memorizing old data -- tying together the train/test discipline (Fundamentals file), the evaluation rigor (Model Evaluation file), and now the actual algorithmic/mathematical tools for fighting overfitting directly. The Deep Learning folder ahead revisits every one of these same concepts (train/test splits, regularization, ensembling) at the scale of neural networks.
