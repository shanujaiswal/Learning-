# Why "Accuracy" Alone Can Be Deeply Misleading

--> Imagine a dataset where 99% of transactions are legitimate and 1% are fraudulent -- a model that lazily predicts "legitimate" for EVERY transaction achieves 99% accuracy while being completely useless at its actual job (catching fraud). This single example is why classification evaluation needs metrics beyond simple accuracy, especially for imbalanced data.

# The Confusion Matrix -- The Foundation of Classification Metrics

--> A table breaking predictions into four outcomes, comparing predicted vs actual class:

```
                  Predicted Positive    Predicted Negative
Actual Positive    True Positive (TP)     False Negative (FN)
Actual Negative    False Positive (FP)     True Negative (TN)
```

```python
from sklearn.metrics import confusion_matrix
cm = confusion_matrix(y_test, predictions)
```

# Precision, Recall and F1-Score

--> **Precision** -- of everything the model predicted as positive, what fraction actually WAS positive? `TP / (TP + FP)`. Matters most when false positives are costly (e.g. flagging a legitimate transaction as fraud and blocking a real customer).
--> **Recall** -- of everything that was ACTUALLY positive, what fraction did the model correctly catch? `TP / (TP + FN)`. Matters most when false negatives are costly (e.g. missing an actual fraudulent transaction, or missing an actual disease case in the medical diagnosis scenario from the Bayes' Theorem file).
--> **F1-Score** -- the harmonic mean of precision and recall, useful as a single balanced metric when both false positives and false negatives matter and neither should be optimized at the total expense of the other.

```python
from sklearn.metrics import precision_score, recall_score, f1_score, classification_report

print(classification_report(y_test, predictions))   # Prints precision, recall, F1 for every class at once
```

--> There's an inherent trade-off between precision and recall -- a model can trivially achieve 100% recall by predicting "positive" for everything (catching every real case, but with terrible precision), or near-100% precision by predicting "positive" only when extremely confident (very few false alarms, but missing many real cases). The right balance depends entirely on which type of error is more costly for the specific business problem.

# ROC Curve and AUC

--> The ROC curve plots the True Positive Rate against the False Positive Rate at every possible classification threshold, visualizing the precision/recall trade-off across the full range of possible decision thresholds rather than just one fixed cutoff.
--> AUC (Area Under the Curve) -- a single number summarizing that whole curve, ranging from 0.5 (no better than random guessing) to 1.0 (a perfect classifier) -- a commonly cited overall metric for comparing classifiers independent of any single chosen threshold.

# Cross-Validation -- Getting a Reliable Performance Estimate

--> A single train/test split's result can be somewhat lucky or unlucky depending on exactly which data ended up in the test set. K-Fold Cross-Validation splits the data into `k` folds, trains/evaluates the model `k` times (each time using a different fold as the test set and the rest as training), and averages the results -- a far more reliable performance estimate than one single split.

```python
from sklearn.model_selection import cross_val_score

scores = cross_val_score(model, X, y, cv=5)     # 5-fold cross-validation
print(f"Average accuracy: {scores.mean():.3f} (+/- {scores.std():.3f})")
```

# The Critical Rule -- Never Touch the Test Set During Development

--> The test set exists to simulate genuinely unseen, future data -- if it's used repeatedly during model development/tuning (checking performance, adjusting hyperparameters, checking again), it stops honestly representing unseen data and information effectively "leaks" from it into the model choices, producing an optimistic, unreliable final estimate.
--> The standard fix -- a THIRD split, the "validation set," is used for all tuning/comparison during development; the test set is touched exactly once, at the very end, to report a final, honest performance estimate.

```
Training set    -- used to fit the model's parameters
Validation set    -- used to tune hyperparameters and choose between models
Test set            -- touched ONCE, at the very end, for a final honest evaluation
```
