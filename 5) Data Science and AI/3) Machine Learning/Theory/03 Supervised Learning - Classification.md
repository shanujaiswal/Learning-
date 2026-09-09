# Classification -- Predicting a Category

--> Instead of a continuous number (covered in the Regression file), classification predicts which of a fixed set of categories an example belongs to -- spam/not spam, which of 3 customer segments, malignant/benign (echoing the medical diagnosis example from the Bayes' Theorem discussion in the Data Science folder).
--> **Binary classification** -- exactly two possible classes. **Multi-class classification** -- more than two, mutually exclusive classes.

# Logistic Regression -- Despite the Name, a Classifier

--> Predicts the PROBABILITY that an example belongs to a class, by passing a linear combination of features through a sigmoid function that squashes any input into a range between 0 and 1.

```python
from sklearn.linear_model import LogisticRegression

model = LogisticRegression()
model.fit(X_train, y_train)

probabilities = model.predict_proba(X_test)   # e.g. [0.85, 0.15] -- 85% class 0, 15% class 1
predictions = model.predict(X_test)             # The final class label, thresholded at 0.5 by default
```

--> Despite its name and its use of a linear combination of features internally, Logistic Regression is a classification algorithm, not a regression one -- a very common point of confusion for newcomers.

# K-Nearest Neighbors (KNN)

--> A simple, intuitive algorithm -- to classify a new point, look at its `k` closest neighbors in the training data (by distance) and predict whichever class is most common among them.

```python
from sklearn.neighbors import KNeighborsClassifier

knn = KNeighborsClassifier(n_neighbors=5)
knn.fit(X_train, y_train)
```

--> KNN does no real "training" beyond storing the data -- all the actual work happens at prediction time, calculating distances to every stored point, which makes it slow on large datasets despite its conceptual simplicity.
--> Feature scaling (covered in the Feature Engineering discussion in the next files) matters enormously for KNN -- a feature measured in the thousands (income) will completely dominate the distance calculation over a feature measured in single digits (age) unless both are scaled to comparable ranges first.

# Decision Trees for Classification

--> The same Decision Tree structure covered for Regression, but each leaf predicts a CLASS label instead of a numeric average -- splits are chosen to make each resulting group as "pure" (dominated by one class) as possible, typically measured via Gini impurity or entropy.

```python
from sklearn.tree import DecisionTreeClassifier

tree = DecisionTreeClassifier(max_depth=4)
tree.fit(X_train, y_train)
```

--> A major advantage -- fully interpretable, visualizable as an actual flowchart of decisions, unlike many other algorithms.

# Naive Bayes -- Direct Application of Bayes' Theorem

--> Uses Bayes' Theorem (covered in the Data Science folder's Probability file) directly, calculating the probability of each class given the observed features, assuming (often unrealistically, but effectively in practice) that all features are independent of each other -- hence "naive."
--> Particularly effective and computationally cheap for text classification (spam filtering, sentiment analysis) despite the independence assumption rarely being strictly true for real text data.

```python
from sklearn.naive_bayes import MultinomialNB

nb = MultinomialNB()
nb.fit(X_train, y_train)
```

# Support Vector Machines (SVM)

--> Finds the decision boundary that maximizes the margin (the distance) between the boundary and the closest points of each class -- generally strong performance on smaller, well-structured datasets, with the "kernel trick" allowing it to find non-linear boundaries by implicitly mapping data into a higher-dimensional space.

```python
from sklearn.svm import SVC

svm = SVC(kernel="rbf")
svm.fit(X_train, y_train)
```

# Multi-Class Strategies

--> Some algorithms (Decision Trees, Naive Bayes) handle multi-class problems natively. Others (basic Logistic Regression, SVM) are inherently binary and use strategies like "one-vs-rest" (training one binary classifier per class, distinguishing that class from all others) to handle multiple classes -- scikit-learn handles this automatically in most cases, but it's worth understanding what's happening underneath.
