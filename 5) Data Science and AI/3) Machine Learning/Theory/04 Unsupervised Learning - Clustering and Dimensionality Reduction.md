# No Labels -- Finding Structure on Its Own

--> Unlike Regression/Classification (both requiring labeled training data), Unsupervised Learning works with data that has NO predefined "correct answer" -- the algorithm's job is to discover inherent structure or patterns purely from the data's own characteristics.

# Clustering -- Grouping Similar Data Points

--> Clustering algorithms group data points into clusters such that points within a cluster are more similar to each other than to points in other clusters -- with no predefined labels telling the algorithm what those groups should be.

# K-Means Clustering -- The Standard Starting Algorithm

--> Partitions data into exactly `k` clusters (a number chosen in advance), by iteratively: (1) assigning each point to its nearest cluster center, (2) recalculating each cluster center as the average of its assigned points, and repeating until the assignments stop changing.

```python
from sklearn.cluster import KMeans

kmeans = KMeans(n_clusters=4, random_state=42)
kmeans.fit(X)
cluster_labels = kmeans.labels_          # Which cluster each data point was assigned to
```

--> **Choosing k** -- since K-Means requires `k` to be specified upfront, the "Elbow Method" plots the model's error (inertia) against different values of `k`, looking for the point where adding more clusters stops meaningfully reducing error -- a visual heuristic, not an exact formula.

```python
inertias = []
for k in range(1, 10):
    km = KMeans(n_clusters=k, random_state=42).fit(X)
    inertias.append(km.inertia_)
# Plot k vs inertias -- look for the "elbow" bend in the curve
```

# Real-World Use -- Customer Segmentation

--> A classic business application, directly connecting to the Data Analyst folder's discipline -- clustering customers by purchasing behavior into groups (e.g. "frequent big spenders," "occasional bargain hunters") that marketing can target with different, tailored strategies, discovered from the data rather than defined by arbitrary business assumptions upfront.

# Hierarchical Clustering

--> Builds a tree of clusters (a "dendrogram") by either progressively merging the closest pairs of points/clusters (agglomerative) or progressively splitting one big cluster (divisive) -- unlike K-Means, it doesn't require choosing `k` in advance; you can cut the resulting tree at whatever level produces the desired number of clusters afterward.

# DBSCAN -- Density-Based Clustering

--> Groups points that are densely packed together, automatically treating sparse, isolated points as "noise" rather than forcing them into a cluster -- unlike K-Means, it doesn't assume clusters are roughly round/similarly sized, and naturally handles outliers rather than being distorted by them.

# Dimensionality Reduction -- Simplifying Without Losing the Story

--> Real datasets often have many features (dozens, hundreds, or more) -- Dimensionality Reduction compresses this down to fewer dimensions while preserving as much of the meaningful structure/variance as possible, useful for visualization (humans can only really see 2-3 dimensions) and for speeding up/improving downstream modeling.

# PCA -- Principal Component Analysis

--> Finds new, artificial axes ("principal components") that are combinations of the original features, ordered by how much of the data's total variance each one captures -- keeping just the first 2-3 components often preserves most of the meaningful signal while discarding the rest.

```python
from sklearn.decomposition import PCA

pca = PCA(n_components=2)
X_reduced = pca.fit_transform(X)      # Now visualizable on a simple 2D scatter plot

print(pca.explained_variance_ratio_)   # How much of the original variance each component captures
```

--> A common, practical workflow -- reduce a high-dimensional dataset to 2 dimensions with PCA purely to visually inspect it for clusters/patterns via a scatter plot, before deciding which modeling approach (clustering, classification) to actually apply.

# Why Both Techniques Matter Together

--> Clustering and dimensionality reduction are frequently combined -- reducing dimensions first (removing noise, easing computation) THEN clustering the reduced data often produces cleaner, more meaningful clusters than clustering the full, high-dimensional original data directly.
