"""
08 - Simple Recommender System
=================================
Demonstrates: Recommender Systems.

A small collaborative-filtering-style recommender built from scratch
with numpy + scikit-learn's cosine_similarity -- no specialized
recommender library needed. We build a synthetic user-item ratings
matrix (0 = not rated), compute user-user cosine similarity, and use
the ratings of similar users to predict scores for items the target
user has not rated yet, then recommend the top-N items.
"""

import numpy as np
from sklearn.metrics.pairwise import cosine_similarity

ITEM_NAMES = [
    "The Matrix",
    "Inception",
    "Titanic",
    "The Notebook",
    "Interstellar",
    "La La Land",
    "Avengers",
    "Pride and Prejudice",
]


def build_synthetic_ratings():
    """Build a synthetic user x item ratings matrix (0 = not rated).

    Ratings are on a 1-5 scale. Users are constructed with clear taste
    clusters (sci-fi fans vs romance fans) so the similarity-based
    recommendations are easy to sanity-check.
    """
    # Rows = users, columns = items (order matches ITEM_NAMES).
    ratings = np.array(
        [
            # Matrix Incep Titan Note  Inter LaLa  Aveng Pride
            [5, 5, 1, 0, 5, 0, 4, 0],  # User0: sci-fi/action fan
            [4, 5, 0, 1, 5, 0, 5, 0],  # User1: sci-fi/action fan
            [0, 1, 5, 5, 0, 4, 0, 5],  # User2: romance/drama fan
            [1, 0, 4, 5, 0, 5, 0, 4],  # User3: romance/drama fan
            [5, 4, 0, 0, 4, 0, 5, 0],  # User4: sci-fi/action fan
            [0, 0, 5, 4, 1, 5, 0, 4],  # User5: romance/drama fan
            [4, 5, 2, 0, 5, 0, 4, 0],  # User6 (target): mostly sci-fi, a couple unrated
        ]
    )
    return ratings


def predict_ratings_for_user(ratings, target_user_idx, k_neighbors=3):
    """Predict ratings for all unrated items of the target user using
    a similarity-weighted average of other users' ratings (user-based
    collaborative filtering)."""
    similarities = cosine_similarity(ratings)[target_user_idx]

    # Exclude the user itself from its own neighbor list.
    neighbor_order = np.argsort(similarities)[::-1]
    neighbor_order = [i for i in neighbor_order if i != target_user_idx]
    top_neighbors = neighbor_order[:k_neighbors]

    n_items = ratings.shape[1]
    predicted = np.zeros(n_items)

    for item_idx in range(n_items):
        if ratings[target_user_idx, item_idx] != 0:
            continue  # Already rated -- skip, we only predict unrated items.

        weighted_sum = 0.0
        weight_total = 0.0
        for neighbor_idx in top_neighbors:
            neighbor_rating = ratings[neighbor_idx, item_idx]
            if neighbor_rating == 0:
                continue  # Neighbor hasn't rated this item either.
            weight = similarities[neighbor_idx]
            weighted_sum += weight * neighbor_rating
            weight_total += weight

        predicted[item_idx] = weighted_sum / weight_total if weight_total > 0 else 0.0

    return predicted, similarities, top_neighbors


def recommend_top_n(predicted_ratings, ratings_row, n=3):
    """Return the top-N recommended item indices (must be unrated and
    have a non-zero predicted score)."""
    candidates = [
        (idx, score)
        for idx, score in enumerate(predicted_ratings)
        if ratings_row[idx] == 0 and score > 0
    ]
    candidates.sort(key=lambda pair: pair[1], reverse=True)
    return candidates[:n]


def main():
    ratings = build_synthetic_ratings()
    n_users, n_items = ratings.shape
    target_user_idx = n_users - 1  # User6, our sample user.

    print(f"Synthetic ratings matrix: {n_users} users x {n_items} items (0 = not rated)")
    print(f"\nTarget user: User{target_user_idx}")
    print("Ratings so far:")
    for item_idx, item_name in enumerate(ITEM_NAMES):
        rating = ratings[target_user_idx, item_idx]
        status = f"{rating}/5" if rating != 0 else "not rated"
        print(f"  {item_name:<22} {status}")

    predicted, similarities, top_neighbors = predict_ratings_for_user(
        ratings, target_user_idx, k_neighbors=3
    )

    print(f"\n=== User-user cosine similarity to User{target_user_idx} ===")
    for user_idx in range(n_users):
        if user_idx == target_user_idx:
            continue
        marker = " (neighbor used)" if user_idx in top_neighbors else ""
        print(f"  User{user_idx}: similarity = {similarities[user_idx]:.3f}{marker}")

    print("\n=== Predicted ratings for unrated items ===")
    for item_idx, item_name in enumerate(ITEM_NAMES):
        if ratings[target_user_idx, item_idx] == 0:
            print(f"  {item_name:<22} predicted score = {predicted[item_idx]:.2f}")

    top_recs = recommend_top_n(predicted, ratings[target_user_idx], n=3)

    print(f"\n=== Top {len(top_recs)} recommendations for User{target_user_idx} ===")
    for rank, (item_idx, score) in enumerate(top_recs, start=1):
        print(f"  {rank}. {ITEM_NAMES[item_idx]} (predicted score: {score:.2f})")

    print(
        "\nHow it works: users with similar rating patterns (high cosine "
        "similarity) are treated as neighbors. Items the target user hasn't "
        "rated get a predicted score as a similarity-weighted average of "
        "how the neighbors rated those same items -- classic user-based "
        "collaborative filtering."
    )


if __name__ == "__main__":
    main()
