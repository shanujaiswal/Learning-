# Why Recommender Systems Deserve Their Own File

--> "What should we show this user next" is one of the most commercially significant, widely-deployed applications of machine learning in the real world -- powering Netflix's movie suggestions, Amazon's "customers also bought," and Spotify's playlists. It draws on ideas from several earlier files (similarity/distance from Classification's KNN, matrix operations from the Linear Algebra file) combined into approaches specific to this one problem.

# The Core Problem -- Predicting Preference for Unseen Items

--> A recommender system's fundamental job is estimating how much a user would like an item they HAVEN'T interacted with yet, based on patterns in what they (and other users) HAVE interacted with -- framed as filling in the missing values of a giant, mostly-empty user-item matrix.

```
              Movie A   Movie B   Movie C   Movie D
User 1:          5         ?         3         ?
User 2:          ?         4         ?         2
User 3:          4         ?         ?         5

(The "?" cells are what a recommender system tries to predict --
almost every real user-item matrix is overwhelmingly empty/"sparse,"
since any one user has only interacted with a tiny fraction of all items)
```

# Collaborative Filtering -- Learning From Collective Behavior

--> Collaborative Filtering's central idea -- users who agreed in the PAST (rated/purchased similar items similarly) will likely agree again in the FUTURE -- it uses ONLY the user-item interaction matrix itself, without needing any information about the items' actual content (genre, description) at all.

## User-Based Collaborative Filtering

--> Find users SIMILAR to the target user (based on their past ratings/behavior, using a similarity measure directly connecting to the distance concepts covered in the K-Nearest Neighbors section of the Classification file), then recommend items THOSE similar users liked that the target user hasn't seen yet.

```python
from sklearn.metrics.pairwise import cosine_similarity
import numpy as np

# Rows = users, columns = movies, values = ratings (0 = not yet rated)
ratings_matrix = np.array([
    [5, 0, 3, 0],
    [0, 4, 0, 2],
    [4, 0, 0, 5]
])

user_similarity = cosine_similarity(ratings_matrix)
# For User 1, find the most similar OTHER users, then recommend items those similar users rated highly
```

--> **Cosine Similarity** measures the angle between two vectors (each user's rating pattern, treated as a vector, directly connecting to the Vectors concept in the Linear Algebra file) rather than their raw magnitude -- two users who both rate everything either very high or very low but in the SAME relative pattern are considered highly similar, even if one user tends to give consistently higher numeric scores overall than the other.

## Item-Based Collaborative Filtering

--> Instead of finding similar USERS, find similar ITEMS -- "users who liked Movie A also tended to like Movie C" -- then recommend items similar to what a specific user has ALREADY liked.
--> **Why item-based is often preferred in practice** -- item-item similarity relationships tend to be more STABLE over time than user-user similarity (a movie's relationship to other movies rarely changes; a user's taste can shift), and there are typically far fewer items than users in large-scale systems, making item-based similarity computation more efficient at real-world scale -- this is precisely the approach Amazon popularized with its well-known "customers who bought this also bought" feature.

# Matrix Factorization -- The Technique Behind the Netflix Prize

--> Rather than directly computing user-user or item-item similarity, Matrix Factorization decomposes the giant, sparse user-item matrix into TWO smaller matrices -- a "user factors" matrix and an "item factors" matrix -- such that multiplying them back together approximately reconstructs the original ratings, AND fills in the previously-missing values.

```
Original sparse matrix (users x items) ≈ User Factors (users x k) @ Item Factors (k x items)ᵀ

Where "k" is a small number of learned "latent factors" -- abstract dimensions the
model discovers on its own (which might loosely correspond to things like "action-ness"
or "critical acclaim" for movies, though the model never explicitly labels them that way)
```

```python
from sklearn.decomposition import NMF   # Non-negative Matrix Factorization

model = NMF(n_components=10)   # Discover 10 latent factors
user_factors = model.fit_transform(ratings_matrix)
item_factors = model.components_

predicted_ratings = user_factors @ item_factors   # Reconstructs the FULL matrix, including previously-missing values
```

--> This is directly analogous to PCA and Dimensionality Reduction (covered in the Unsupervised Learning file) -- both techniques discover a smaller number of "latent" dimensions that explain the patterns in high-dimensional data, just applied here specifically to a user-item ratings matrix rather than a general feature matrix, and used for PREDICTION (filling missing values) rather than only for visualization/dimensionality reduction.
--> This exact technique, refined and scaled up considerably, is what won the famous 2009 Netflix Prize competition -- a million-dollar prize for whichever team could most improve Netflix's own recommendation accuracy, a major historical milestone that popularized matrix factorization as the standard serious approach to large-scale recommendation.

# Content-Based Filtering -- Using Item Attributes Directly

--> Unlike Collaborative Filtering (which ignores item content entirely), Content-Based Filtering recommends items SIMILAR IN CONTENT to what a user has previously liked -- directly using item features (a movie's genre, actors, description -- potentially processed with the TF-IDF/embedding techniques covered in the Natural Language Processing file for text-based item descriptions).

```python
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

movie_descriptions = ["A thrilling action movie with car chases", "A romantic comedy about love", "An action-packed superhero film"]

vectorizer = TfidfVectorizer()
tfidf_matrix = vectorizer.fit_transform(movie_descriptions)

item_similarity = cosine_similarity(tfidf_matrix)
# Movies 1 and 3, both about "action," would show high similarity based purely on their text content
```

--> **The Cold Start Problem, and how Content-Based Filtering helps solve it** -- Collaborative Filtering fundamentally CANNOT recommend a brand-new item that no user has rated yet (there's no interaction data to learn from at all), and struggles similarly with brand-new users who have no rating history -- Content-Based Filtering sidesteps this specific problem, since a new item can be recommended based purely on its CONTENT similarity to items a user already likes, without needing any prior interaction data for that specific new item at all.

# Hybrid Recommender Systems -- Combining Both Approaches

--> Most real, large-scale production recommender systems (Netflix, YouTube, Spotify) COMBINE Collaborative Filtering and Content-Based Filtering, along with additional signals, specifically to get the strengths of each approach while covering for each other's individual weaknesses (collaborative filtering's cold-start problem, content-based filtering's tendency to over-recommend items too similar to what's already been consumed, sometimes called an "echo chamber" or filter-bubble effect).

# Evaluating Recommender Systems

--> Standard regression/classification metrics (RMSE, covered in the Model Evaluation file) can measure raw RATING PREDICTION accuracy -- but real-world recommender evaluation increasingly focuses on RANKING quality instead (is the BEST item actually shown near the TOP of the recommendation list, not just "is the predicted rating numerically close to the true rating").
--> **Precision@K and Recall@K** -- of the top K recommended items shown to a user, what fraction did they actually engage with (Precision@K), and of all items the user would have actually liked, what fraction appeared in the top K recommendations (Recall@K) -- directly adapting the Precision/Recall concepts from the Model Evaluation file specifically to the ranked-list nature of recommendation output.
--> **A/B testing recommender changes in production** -- directly connecting back to the A/B Testing and Experimentation file in the Data Analyst folder -- ultimately, the real measure of a recommender system's success is whether it actually improves a genuine BUSINESS metric (engagement time, purchase conversion) when tested against real users, not just offline accuracy metrics computed against historical data alone.
