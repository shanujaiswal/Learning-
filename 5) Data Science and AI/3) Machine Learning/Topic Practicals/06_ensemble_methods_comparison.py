"""
06 - Ensemble Methods Comparison
==================================
Demonstrates: Overfitting / Ensemble Methods.

We train a single Decision Tree, a Random Forest (bagging ensemble),
and a Gradient Boosting Classifier (boosting ensemble) on the SAME
dataset, compare their accuracy, and explain which ensemble strategy
is used and why it helps reduce overfitting / improve generalization.
"""

from sklearn.datasets import make_classification
from sklearn.model_selection import train_test_split
from sklearn.tree import DecisionTreeClassifier
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.metrics import accuracy_score


def evaluate(name, strategy_note, model, X_train, X_test, y_train, y_test):
    model.fit(X_train, y_train)
    train_acc = accuracy_score(y_train, model.predict(X_train))
    test_acc = accuracy_score(y_test, model.predict(X_test))

    print(f"\n--- {name} ---")
    print(f"Strategy: {strategy_note}")
    print(f"Train accuracy: {train_acc:.3f}")
    print(f"Test accuracy : {test_acc:.3f}")
    print(f"Train/Test gap: {train_acc - test_acc:.3f}")

    return {"name": name, "train_acc": train_acc, "test_acc": test_acc}


def main():
    X, y = make_classification(
        n_samples=400,
        n_features=10,
        n_informative=6,
        n_redundant=2,
        flip_y=0.03,
        random_state=42,
    )

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.3, random_state=42, stratify=y
    )

    print("Dataset: 400 samples, 10 features, binary target, some label noise")
    print(f"Train size: {len(X_train)}, Test size: {len(X_test)}")

    results = []

    # 1. Single Decision Tree (deep, unregularized -> prone to overfitting).
    results.append(
        evaluate(
            "Single Decision Tree",
            "No ensembling: one deep tree that memorizes training noise, "
            "so it tends to overfit (high train accuracy, weaker test accuracy).",
            DecisionTreeClassifier(random_state=42),
            X_train,
            X_test,
            y_train,
            y_test,
        )
    )

    # 2. Random Forest (Bagging).
    results.append(
        evaluate(
            "Random Forest (Bagging)",
            "BAGGING: trains many trees in parallel on bootstrapped samples with "
            "random feature subsets, then averages their votes. Averaging "
            "independent, high-variance trees cancels out overfitting noise.",
            RandomForestClassifier(n_estimators=150, random_state=42),
            X_train,
            X_test,
            y_train,
            y_test,
        )
    )

    # 3. Gradient Boosting (Boosting).
    results.append(
        evaluate(
            "Gradient Boosting (Boosting)",
            "BOOSTING: trains trees sequentially, where each new tree focuses on "
            "correcting the residual errors of the previous ensemble. This reduces "
            "bias and often reaches higher accuracy, at some risk of overfitting if "
            "too many stages/too high learning rate are used.",
            GradientBoostingClassifier(
                n_estimators=150, learning_rate=0.1, max_depth=3, random_state=42
            ),
            X_train,
            X_test,
            y_train,
            y_test,
        )
    )

    print("\n=== Comparison Summary ===")
    header = f"{'Model':<32}{'Train Acc':>12}{'Test Acc':>10}{'Gap':>8}"
    print(header)
    print("-" * len(header))
    for r in results:
        gap = r["train_acc"] - r["test_acc"]
        print(f"{r['name']:<32}{r['train_acc']:>12.3f}{r['test_acc']:>10.3f}{gap:>8.3f}")

    best = max(results, key=lambda r: r["test_acc"])
    print(f"\nBest test accuracy: {best['name']} ({best['test_acc']:.3f})")
    print(
        "\nWhy ensembles help: a single decision tree has high variance and easily "
        "overfits. Bagging (Random Forest) reduces variance by averaging many "
        "decorrelated trees. Boosting (Gradient Boosting) reduces bias by "
        "iteratively focusing on hard-to-predict examples. Both usually "
        "generalize better than a single unconstrained tree."
    )


if __name__ == "__main__":
    main()
