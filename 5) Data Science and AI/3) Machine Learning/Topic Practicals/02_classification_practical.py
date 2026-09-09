"""
02 - Classification Practical
==============================
Demonstrates: Supervised Classification + Model Evaluation/Metrics/Validation.

We generate a small synthetic binary classification dataset, train two
models (Logistic Regression and a Decision Tree), evaluate with
accuracy / precision / recall / F1 / confusion matrix, and print a full
classification report for each.
"""

from sklearn.datasets import make_classification
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.tree import DecisionTreeClassifier
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    confusion_matrix,
    classification_report,
)


def evaluate(name, model, X_train, X_test, y_train, y_test):
    model.fit(X_train, y_train)
    preds = model.predict(X_test)

    acc = accuracy_score(y_test, preds)
    prec = precision_score(y_test, preds)
    rec = recall_score(y_test, preds)
    f1 = f1_score(y_test, preds)
    cm = confusion_matrix(y_test, preds)

    print(f"\n--- {name} ---")
    print(f"Accuracy  : {acc:.3f}")
    print(f"Precision : {prec:.3f}")
    print(f"Recall    : {rec:.3f}")
    print(f"F1 score  : {f1:.3f}")
    print("Confusion matrix:")
    print(cm)
    print("Classification report:")
    print(classification_report(y_test, preds))

    return {"name": name, "acc": acc, "prec": prec, "rec": rec, "f1": f1}


def main():
    # 1. Generate a small synthetic binary classification dataset.
    X, y = make_classification(
        n_samples=300,
        n_features=6,
        n_informative=4,
        n_redundant=1,
        n_classes=2,
        weights=[0.55, 0.45],
        random_state=42,
    )

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.25, random_state=42, stratify=y
    )

    print("Dataset: 300 samples, 6 features, binary target")
    print(f"Train size: {len(X_train)}, Test size: {len(X_test)}")

    # 2. Train and evaluate Logistic Regression.
    log_result = evaluate(
        "Logistic Regression",
        LogisticRegression(max_iter=1000),
        X_train,
        X_test,
        y_train,
        y_test,
    )

    # 3. Train and evaluate Decision Tree.
    tree_result = evaluate(
        "Decision Tree",
        DecisionTreeClassifier(max_depth=4, random_state=42),
        X_train,
        X_test,
        y_train,
        y_test,
    )

    # 4. Comparison summary.
    print("\n=== Comparison Summary ===")
    header = f"{'Model':<25}{'Accuracy':>10}{'Precision':>11}{'Recall':>9}{'F1':>8}"
    print(header)
    print("-" * len(header))
    for r in (log_result, tree_result):
        print(
            f"{r['name']:<25}{r['acc']:>10.3f}{r['prec']:>11.3f}"
            f"{r['rec']:>9.3f}{r['f1']:>8.3f}"
        )


if __name__ == "__main__":
    main()
