"""
03 - Clustering and PCA
========================
Demonstrates: Unsupervised Learning (Clustering + Dimensionality Reduction).

We generate synthetic clustered data, run K-Means and print cluster
assignments and inertia, then run PCA to reduce dimensionality and show
the explained variance ratio.
"""

import numpy as np
from sklearn.datasets import make_blobs
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA
from sklearn.preprocessing import StandardScaler


def main():
    # 1. Generate synthetic clustered data: 4 clusters, 6 features.
    X, y_true = make_blobs(
        n_samples=120,
        n_features=6,
        centers=4,
        cluster_std=1.8,
        random_state=42,
    )

    print("Dataset: 120 samples, 6 features, generated from 4 true blobs")

    # Standardize before clustering (distance-based algorithm).
    X_scaled = StandardScaler().fit_transform(X)

    # 2. K-Means clustering.
    kmeans = KMeans(n_clusters=4, random_state=42, n_init=10)
    cluster_labels = kmeans.fit_predict(X_scaled)

    print("\n--- K-Means Clustering ---")
    print(f"Inertia (within-cluster sum of squares): {kmeans.inertia_:.3f}")
    print("Cluster assignments (first 30 samples):")
    print(cluster_labels[:30])

    unique, counts = np.unique(cluster_labels, return_counts=True)
    print("\nCluster sizes:")
    for u, c in zip(unique, counts):
        print(f"  Cluster {u}: {c} samples")

    # 3. PCA dimensionality reduction.
    pca = PCA(n_components=2)
    X_pca = pca.fit_transform(X_scaled)

    print("\n--- PCA (6 features -> 2 components) ---")
    print(f"Explained variance ratio: {pca.explained_variance_ratio_}")
    print(
        f"Total variance retained by 2 components: "
        f"{pca.explained_variance_ratio_.sum() * 100:.2f}%"
    )
    print("First 5 samples projected onto principal components:")
    print(X_pca[:5])


if __name__ == "__main__":
    main()
