# Machine Learning -- Practical

Companion code for the `Theory` chapters. Every script is standalone,
runnable, and uses only scikit-learn / numpy / pandas (and matplotlib
if you want to add plots yourself -- none of the scripts require a
display, they only print results to the console).

## Setup

```bash
pip install scikit-learn pandas numpy matplotlib
```

Run any script directly, e.g.:

```bash
python 01_regression_practical.py
```

## Chapter Mapping

| Script | Theory Chapter(s) |
|---|---|
| `01_regression_practical.py` | Supervised-Regression; Model Evaluation/Metrics/Validation |
| `02_classification_practical.py` | Supervised-Classification; Model Evaluation/Metrics/Validation |
| `03_clustering_and_pca.py` | Unsupervised-Clustering/Dimensionality Reduction |
| `04_overfitting_and_regularization.py` | Overfitting/Regularization/Ensemble Methods |
| `05_feature_engineering_and_encoding.py` | Feature Engineering; Encoding/Scaling; Feature Selection/Dimensionality |
| `06_ensemble_methods_comparison.py` | Overfitting/Regularization/Ensemble Methods |
| `07_simple_reinforcement_learning_qlearning.py` | Reinforcement Learning Fundamentals |
| `08_simple_recommender_system.py` | Recommender Systems |

## Files

- `00 README.md` -- this index.
- `01_regression_practical.py` -- Linear Regression vs Random Forest Regressor on a synthetic regression dataset, evaluated with MAE/RMSE/R^2.
- `02_classification_practical.py` -- Logistic Regression vs Decision Tree on a synthetic binary classification dataset, evaluated with accuracy/precision/recall/F1/confusion matrix.
- `03_clustering_and_pca.py` -- K-Means clustering plus PCA dimensionality reduction on synthetic blob data.
- `04_overfitting_and_regularization.py` -- High-degree polynomial regression overfitting demo, fixed with Ridge/Lasso regularization.
- `05_feature_engineering_and_encoding.py` -- One-hot/label encoding, StandardScaler/MinMaxScaler, and a derived day-of-week feature on a small DataFrame.
- `06_ensemble_methods_comparison.py` -- Decision Tree vs Random Forest (bagging) vs Gradient Boosting (boosting) accuracy comparison.
- `07_simple_reinforcement_learning_qlearning.py` -- From-scratch tabular Q-learning on a tiny grid world (no gym needed).
- `08_simple_recommender_system.py` -- Cosine-similarity collaborative-filtering recommender on a synthetic ratings matrix.
