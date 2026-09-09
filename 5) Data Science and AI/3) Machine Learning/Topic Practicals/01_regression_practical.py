"""
01 - Regression Practical
=========================
Demonstrates: Supervised Regression + Model Evaluation/Metrics/Validation.

We generate a small synthetic regression dataset, train two models
(Linear Regression and a Random Forest Regressor), evaluate both with
MAE / RMSE / R^2, and print a side-by-side comparison.
"""

import numpy as np
from sklearn.datasets import make_regression
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LinearRegression
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score


def evaluate(name, model, X_train, X_test, y_train, y_test):
    model.fit(X_train, y_train)
    preds = model.predict(X_test)

    mae = mean_absolute_error(y_test, preds)
    rmse = np.sqrt(mean_squared_error(y_test, preds))
    r2 = r2_score(y_test, preds)

    print(f"\n--- {name} ---")
    print(f"MAE  : {mae:.3f}")
    print(f"RMSE : {rmse:.3f}")
    print(f"R^2  : {r2:.3f}")
    return {"name": name, "mae": mae, "rmse": rmse, "r2": r2}


def main():
    # 1. Generate a small synthetic regression dataset.
    X, y = make_regression(
        n_samples=200,
        n_features=5,
        n_informative=3,
        noise=15.0,
        random_state=42,
    )

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.25, random_state=42
    )

    print("Dataset: 200 samples, 5 features (3 informative), noise=15.0")
    print(f"Train size: {len(X_train)}, Test size: {len(X_test)}")

    # 2. Train and evaluate Linear Regression.
    lin_result = evaluate(
        "Linear Regression", LinearRegression(), X_train, X_test, y_train, y_test
    )

    # 3. Train and evaluate Random Forest Regressor.
    rf_result = evaluate(
        "Random Forest Regressor",
        RandomForestRegressor(n_estimators=100, random_state=42),
        X_train,
        X_test,
        y_train,
        y_test,
    )

    # 4. Comparison summary.
    print("\n=== Comparison Summary ===")
    header = f"{'Model':<25}{'MAE':>10}{'RMSE':>10}{'R^2':>10}"
    print(header)
    print("-" * len(header))
    for r in (lin_result, rf_result):
        print(f"{r['name']:<25}{r['mae']:>10.3f}{r['rmse']:>10.3f}{r['r2']:>10.3f}")

    better = min((lin_result, rf_result), key=lambda r: r["rmse"])
    print(f"\nLower RMSE wins on this dataset: {better['name']}")


if __name__ == "__main__":
    main()
