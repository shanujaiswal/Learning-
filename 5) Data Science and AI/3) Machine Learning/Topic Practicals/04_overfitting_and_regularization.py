"""
04 - Overfitting and Regularization
=====================================
Demonstrates: Overfitting / Regularization / Ensemble Methods (concretely).

We deliberately overfit a high-degree polynomial regression on a small
noisy dataset (showing a huge train/test score gap), then fix it with
Ridge and Lasso regularization applied to the SAME high-degree features.
Train vs test scores are printed side by side, before and after.
"""

import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LinearRegression, Ridge, Lasso
from sklearn.preprocessing import PolynomialFeatures, StandardScaler
from sklearn.pipeline import make_pipeline
from sklearn.metrics import r2_score, mean_squared_error


def make_small_noisy_dataset(n=25, seed=7):
    rng = np.random.RandomState(seed)
    X = np.sort(rng.uniform(-3, 3, size=n)).reshape(-1, 1)
    true_y = 0.5 * X.ravel() ** 2 - X.ravel()
    noise = rng.normal(scale=3.0, size=n)
    y = true_y + noise
    return X, y


def report(name, model, X_train, X_test, y_train, y_test):
    model.fit(X_train, y_train)
    train_pred = model.predict(X_train)
    test_pred = model.predict(X_test)

    train_r2 = r2_score(y_train, train_pred)
    test_r2 = r2_score(y_test, test_pred)
    train_rmse = np.sqrt(mean_squared_error(y_train, train_pred))
    test_rmse = np.sqrt(mean_squared_error(y_test, test_pred))

    print(f"\n--- {name} ---")
    print(f"Train R^2  : {train_r2:8.3f}   Test R^2  : {test_r2:8.3f}")
    print(f"Train RMSE : {train_rmse:8.3f}   Test RMSE : {test_rmse:8.3f}")
    gap = train_r2 - test_r2
    print(f"Train/Test R^2 gap: {gap:.3f}  {'<-- large overfit gap!' if gap > 0.3 else ''}")

    return {
        "name": name,
        "train_r2": train_r2,
        "test_r2": test_r2,
        "train_rmse": train_rmse,
        "test_rmse": test_rmse,
    }


def main():
    X, y = make_small_noisy_dataset(n=25, seed=7)
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.3, random_state=7
    )

    print("Dataset: 25 small noisy samples, quadratic-ish true relationship")
    print(f"Train size: {len(X_train)}, Test size: {len(X_test)}")

    degree = 12  # deliberately way too high for 25 points -> overfitting

    # --- BEFORE: plain Linear Regression on degree-12 polynomial features. ---
    overfit_model = make_pipeline(
        PolynomialFeatures(degree=degree),
        StandardScaler(),
        LinearRegression(),
    )
    overfit_result = report(
        f"OVERFIT: Degree-{degree} Polynomial + Linear Regression (no regularization)",
        overfit_model,
        X_train,
        X_test,
        y_train,
        y_test,
    )

    # --- AFTER: same degree-12 features, but Ridge (L2) regularization. ---
    ridge_model = make_pipeline(
        PolynomialFeatures(degree=degree),
        StandardScaler(),
        Ridge(alpha=5.0),
    )
    ridge_result = report(
        f"FIXED: Degree-{degree} Polynomial + Ridge (L2, alpha=5.0)",
        ridge_model,
        X_train,
        X_test,
        y_train,
        y_test,
    )

    # --- AFTER: same degree-12 features, but Lasso (L1) regularization. ---
    lasso_model = make_pipeline(
        PolynomialFeatures(degree=degree),
        StandardScaler(),
        Lasso(alpha=0.5, max_iter=20000),
    )
    lasso_result = report(
        f"FIXED: Degree-{degree} Polynomial + Lasso (L1, alpha=0.5)",
        lasso_model,
        X_train,
        X_test,
        y_train,
        y_test,
    )

    # --- Side-by-side before/after summary. ---
    print("\n=== Before vs After Regularization ===")
    header = f"{'Model':<45}{'Train R^2':>12}{'Test R^2':>10}{'Gap':>8}"
    print(header)
    print("-" * len(header))
    for r in (overfit_result, ridge_result, lasso_result):
        gap = r["train_r2"] - r["test_r2"]
        print(f"{r['name']:<45}{r['train_r2']:>12.3f}{r['test_r2']:>10.3f}{gap:>8.3f}")

    print(
        "\nTakeaway: the unregularized degree-12 model fits training noise almost"
        " perfectly (high train R^2) but fails on the test set (low/negative test"
        " R^2). Ridge and Lasso shrink the polynomial coefficients, closing the"
        " train/test gap and generalizing much better."
    )


if __name__ == "__main__":
    main()
