"""
05 - Linear Algebra for Machine Learning
===========================================
Companion script for: "Linear Algebra for DS/AI".

Demonstrates core `numpy.linalg` operations, each annotated with the ML
concept it underpins:
  - Vectors & dot product        -> similarity / cosine similarity, attention scores
  - Matrix multiplication        -> forward pass of a neural network layer
  - Transpose                    -> switching between row/column conventions,
                                     computing Gram matrices (X^T X)
  - Inverse & determinant        -> solving normal equations in linear regression
  - Eigenvalues / eigenvectors   -> Principal Component Analysis (PCA)
"""

import numpy as np

SEP = "=" * 70


def section(title: str) -> None:
    print(f"\n{SEP}\n{title}\n{SEP}")


rng = np.random.default_rng(seed=3)


# ---------------------------------------------------------------------------
# PART 1 -- Vectors & dot product
# ---------------------------------------------------------------------------

section("PART 1: Vectors & dot product -> similarity between data points")

# Two "feature vectors" e.g. TF-IDF vectors of two short documents.
v1 = np.array([2.0, 0.0, 1.0, 3.0])
v2 = np.array([1.0, 1.0, 0.0, 2.0])

dot = np.dot(v1, v2)
norm1, norm2 = np.linalg.norm(v1), np.linalg.norm(v2)
cosine_sim = dot / (norm1 * norm2)

print(f"v1 = {v1}")
print(f"v2 = {v2}")
print(f"dot(v1, v2)   = {dot:.3f}")
print(f"||v1||        = {norm1:.3f}")
print(f"||v2||        = {norm2:.3f}")
print(f"cosine sim    = {cosine_sim:.3f}  "
      f"(ML use: measures how 'similar' two vectors/embeddings are, "
      f"e.g. document/word embeddings, recommender systems)")


# ---------------------------------------------------------------------------
# PART 2 -- Matrix multiplication -> neural network forward pass
# ---------------------------------------------------------------------------

section("PART 2: Matrix multiplication -> NN layer forward pass")

# Toy "mini-batch" of 3 samples, each with 4 input features.
X = rng.normal(size=(3, 4)).round(2)
# A toy "dense layer": weight matrix (4 inputs -> 2 outputs) + bias
W = rng.normal(scale=0.5, size=(4, 2)).round(2)
b = np.array([0.1, -0.2])

print("X (3 samples x 4 features):\n", X)
print("\nW (4 inputs x 2 outputs):\n", W)
print("\nb (bias):", b)

Z = X @ W + b  # (3,4) @ (4,2) -> (3,2), then broadcast-add bias
print("\nZ = X @ W + b  (linear part of a dense layer's forward pass):\n",
      Z.round(3))

relu_out = np.maximum(0, Z)
print("\nAfter ReLU activation (elementwise max(0, Z)):\n", relu_out.round(3))
print("(ML use: every dense/linear layer in a neural network is literally "
      "a matrix multiplication plus a bias vector.)")


# ---------------------------------------------------------------------------
# PART 3 -- Transpose & Gram matrix
# ---------------------------------------------------------------------------

section("PART 3: Transpose -> Gram matrix (X^T X)")

print("X.T (4 features x 3 samples):\n", X.T)

gram = X.T @ X  # (4,3) @ (3,4) -> (4,4): feature-feature "covariance-like" matrix
print("\nGram matrix X^T @ X (4x4, feature-feature relationships):\n",
      gram.round(3))
print("(ML use: X^T X appears directly in the normal equations for linear "
      "regression, and in computing covariance matrices for PCA below.)")


# ---------------------------------------------------------------------------
# PART 4 -- Inverse & determinant -> solving linear regression normal equations
# ---------------------------------------------------------------------------

section("PART 4: Inverse & determinant -> linear regression normal equations")

# Synthetic regression problem: y = 3*x1 + 2*x2 + noise
n = 50
X_reg = rng.normal(size=(n, 2))
true_w = np.array([3.0, 2.0])
y_reg = X_reg @ true_w + rng.normal(scale=0.3, size=n)

XtX = X_reg.T @ X_reg
det = np.linalg.det(XtX)
print(f"X^T X (2x2):\n{XtX.round(3)}")
print(f"\ndeterminant(X^T X) = {det:.4f}  "
      f"(non-zero -> matrix is invertible -> unique regression solution exists)")

XtX_inv = np.linalg.inv(XtX)
w_hat = XtX_inv @ X_reg.T @ y_reg  # closed-form OLS solution: w = (X^T X)^-1 X^T y
print(f"\nInverse (X^T X)^-1:\n{XtX_inv.round(4)}")
print(f"\nEstimated weights via normal equation: {w_hat.round(3)} "
      f"(true weights were {true_w})")
print("(ML use: the closed-form OLS solution for linear regression is "
      "w = (X^T X)^-1 X^T y -- requires computing a matrix inverse.)")


# ---------------------------------------------------------------------------
# PART 5 -- Eigenvalues / eigenvectors -> PCA
# ---------------------------------------------------------------------------

section("PART 5: Eigenvalues/eigenvectors -> Principal Component Analysis")

# Build a correlated 2D dataset (so PCA has an interesting principal axis).
n_pts = 200
angle = np.pi / 6
rot = np.array([[np.cos(angle), -np.sin(angle)],
                [np.sin(angle), np.cos(angle)]])
raw = rng.normal(size=(n_pts, 2)) * np.array([3.0, 0.5])  # elongated along x
data = raw @ rot.T  # rotate to correlate the two features

data_centered = data - data.mean(axis=0)
cov_matrix = np.cov(data_centered, rowvar=False)  # 2x2 covariance matrix
print("Covariance matrix of the (centered) 2D dataset:\n", cov_matrix.round(3))

eigenvalues, eigenvectors = np.linalg.eig(cov_matrix)
# Sort descending by eigenvalue (explained variance)
order = np.argsort(eigenvalues)[::-1]
eigenvalues = eigenvalues[order]
eigenvectors = eigenvectors[:, order]

print(f"\nEigenvalues (variance explained along each principal axis): "
      f"{eigenvalues.round(3)}")
print(f"Eigenvectors (principal axes / directions), as columns:\n",
      eigenvectors.round(3))

explained_ratio = eigenvalues / eigenvalues.sum()
print(f"\nExplained variance ratio: {explained_ratio.round(3)} "
      f"-> PC1 explains {explained_ratio[0]*100:.1f}% of the variance")

# Project data onto the top principal component (dimensionality reduction).
pc1 = eigenvectors[:, 0]
projected = data_centered @ pc1
print(f"\nFirst 5 points projected onto PC1 (2D -> 1D): "
      f"{projected[:5].round(3)}")
print("(ML use: PCA finds the eigenvectors of the covariance matrix -- "
      "the directions of maximum variance -- and projects data onto the "
      "top few for dimensionality reduction.)")

section("Done. All linear-algebra-to-ML mappings printed above.")
