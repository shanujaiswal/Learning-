# What Machine Learning Actually Is

--> Traditional programming: a human writes explicit rules, the computer follows them exactly. Machine Learning: a computer LEARNS the rules itself from data/examples, then applies what it learned to new, unseen data -- instead of hand-coding "if income > X and age > Y, approve the loan," you show the algorithm thousands of past loan outcomes and let it discover the pattern itself.

# The Three Major Categories

--> **Supervised Learning** -- the training data includes the correct answer ("label") for every example -- the algorithm learns to map inputs to known outputs, then predicts labels for new, unlabeled data. Covered in depth across the next two files (Regression and Classification).
--> **Unsupervised Learning** -- the training data has NO labels at all -- the algorithm finds structure/patterns in the data on its own (grouping similar items, reducing dimensionality). Covered in its own file.
--> **Reinforcement Learning** -- an agent learns by taking actions in an environment and receiving rewards/penalties, gradually learning a strategy ("policy") that maximizes cumulative reward over time -- the paradigm behind game-playing AI (AlphaGo) and robotics control, distinct enough from the other two that it's covered separately, in the Artificial Intelligence folder.

# Supervised Learning -- The Two Sub-Types

--> **Regression** -- predicting a CONTINUOUS numeric value (house price, temperature, revenue) -- covered in its own file.
--> **Classification** -- predicting a CATEGORY/label from a fixed set of options (spam/not spam, which of 3 customer segments) -- covered in its own file.
--> The distinction matters because it determines which algorithms and evaluation metrics are appropriate -- using a regression metric on a classification problem (or vice versa) simply doesn't make sense.

# The Core Machine Learning Workflow

```
1. Collect and prepare data (Data Science folder's territory)
2. Split into Training set and Test set (NEVER let the model see test data during training)
3. Choose a model/algorithm appropriate for the problem type
4. Train ("fit") the model on the training data
5. Evaluate performance on the test set (covered in the Model Evaluation file)
6. Tune and iterate
7. Deploy (covered in the MLOps folder)
```

--> The train/test split is the single most important discipline in this entire workflow -- evaluating a model on data it already learned from tells you almost nothing about how it will perform on genuinely new data, directly setting up the Overfitting file's central concern.

```python
from sklearn.model_selection import train_test_split

X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
# X = features (inputs), y = the target/label being predicted
```

# Features and Labels -- The Basic Vocabulary

--> **Features** (also called inputs/predictors/independent variables) -- the columns of data used to make a prediction (e.g. square footage, number of bedrooms).
--> **Label/Target** (also called output/dependent variable) -- the thing being predicted (e.g. house price).
--> This vocabulary is used consistently across every subsequent file in this folder, and connects directly to feature engineering, covered as part of the Data Science folder's lifecycle and revisited practically throughout this folder.

# Why "Learning" Really Means "Optimization"

--> Under the hood, training a model means adjusting its internal parameters to minimize a "loss function" -- a mathematical measure of how wrong the model's predictions currently are -- covered concretely once Linear Regression is introduced in the next file, and central to how Deep Learning training works in the following folder.
