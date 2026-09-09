"""
01 - Train and Serialize a Model
=================================
Chapter: MLOps Fundamentals / Model Deployment (start of the model lifecycle)

This is the very first step of the "model lifecycle": train a model, then
persist ("serialize") it to disk so it can be loaded later by a serving
process without retraining. The next script, 02_fastapi_model_serving.py,
loads the model.pkl artifact produced here.

Install:
    pip install scikit-learn joblib pandas

Run:
    python 01_train_and_serialize_model.py
"""

import joblib
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score

RANDOM_SEED = 42
MODEL_PATH = "model.pkl"


def make_synthetic_dataset(n_samples: int = 2000):
    """Create a small, reproducible synthetic binary-classification dataset.

    Two numeric features. The label is 1 when feature_1 + feature_2 is
    above a noisy threshold, which gives the logistic regression something
    real (if simple) to learn.
    """
    rng = np.random.default_rng(RANDOM_SEED)
    feature_1 = rng.normal(loc=50, scale=10, size=n_samples)
    feature_2 = rng.normal(loc=20, scale=5, size=n_samples)
    noise = rng.normal(loc=0, scale=8, size=n_samples)

    score = feature_1 + feature_2 + noise
    label = (score > np.median(score)).astype(int)

    df = pd.DataFrame(
        {
            "feature_1": feature_1,
            "feature_2": feature_2,
            "label": label,
        }
    )
    return df


def train_model(df: pd.DataFrame) -> LogisticRegression:
    X = df[["feature_1", "feature_2"]]
    y = df["label"]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=RANDOM_SEED
    )

    model = LogisticRegression()
    model.fit(X_train, y_train)

    predictions = model.predict(X_test)
    accuracy = accuracy_score(y_test, predictions)
    print(f"Held-out test accuracy: {accuracy:.3f}")

    return model


def main():
    df = make_synthetic_dataset()
    print("Sample of training data:")
    print(df.head())

    model = train_model(df)

    joblib.dump(model, MODEL_PATH)
    print(f"\nSaved trained model to '{MODEL_PATH}'")
    print("This artifact is the input for 02_fastapi_model_serving.py")


if __name__ == "__main__":
    main()
