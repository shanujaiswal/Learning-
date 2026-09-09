"""
02 - NLP Text Processing Pipeline (Natural Language Processing chapter)
=========================================================================
A real, runnable NLP pipeline using lightweight techniques -- no heavy model
downloads required.

Pipeline stages:
    1. Tokenization            (simple regex-based word tokenizer)
    2. Lowercasing & cleaning  (strip punctuation)
    3. Stopword removal        (small built-in English stopword list --
                                 works even without downloading nltk's corpora;
                                 will use nltk's list automatically if available)
    4. Bag-of-Words vectorization (scikit-learn CountVectorizer)
    5. Sentiment classification   (scikit-learn Naive Bayes trained on a tiny
                                    inline labeled dataset)

Install:
    pip install scikit-learn
    (optional) pip install nltk   -- pipeline works fine without it too

Run:
    python 02_nlp_text_processing_pipeline.py
"""

from __future__ import annotations

import re
from typing import List

from sklearn.feature_extraction.text import CountVectorizer
from sklearn.model_selection import train_test_split
from sklearn.naive_bayes import MultinomialNB
from sklearn.metrics import accuracy_score, classification_report

# ---------------------------------------------------------------------------
# 1. Stopwords (fallback list; use nltk's if the corpus is already downloaded)
# ---------------------------------------------------------------------------
FALLBACK_STOPWORDS = {
    "a", "an", "the", "is", "are", "was", "were", "am", "be", "been", "being",
    "this", "that", "these", "those", "it", "its", "i", "you", "he", "she",
    "we", "they", "to", "of", "in", "on", "at", "for", "with", "as", "by",
    "and", "or", "but", "if", "so", "not", "no", "do", "does", "did", "very",
    "just", "than", "then", "there", "here", "my", "your", "his", "her",
}

try:
    import nltk
    from nltk.corpus import stopwords as nltk_stopwords

    try:
        STOPWORDS = set(nltk_stopwords.words("english"))
    except LookupError:
        # Corpus not downloaded -- fall back gracefully instead of crashing.
        STOPWORDS = FALLBACK_STOPWORDS
except ImportError:
    STOPWORDS = FALLBACK_STOPWORDS


# ---------------------------------------------------------------------------
# 2. Tokenizer
# ---------------------------------------------------------------------------
TOKEN_PATTERN = re.compile(r"[a-zA-Z']+")


def tokenize(text: str) -> List[str]:
    """Lowercase + extract word tokens (letters and apostrophes only)."""
    return TOKEN_PATTERN.findall(text.lower())


def remove_stopwords(tokens: List[str]) -> List[str]:
    return [t for t in tokens if t not in STOPWORDS]


def preprocess(text: str) -> str:
    """Full text-cleaning pipeline -> returns a cleaned string (for vectorizers)."""
    tokens = tokenize(text)
    tokens = remove_stopwords(tokens)
    return " ".join(tokens)


# ---------------------------------------------------------------------------
# 3. Demonstrate tokenization + stopword removal on a sample sentence
# ---------------------------------------------------------------------------
def demo_tokenization() -> None:
    sample = "This movie was absolutely fantastic! I really loved the acting and the story."
    tokens = tokenize(sample)
    filtered = remove_stopwords(tokens)

    print("=== Tokenization & Stopword Removal Demo ===")
    print(f"Original text : {sample}")
    print(f"Tokens        : {tokens}")
    print(f"After removing stopwords: {filtered}")
    print()


# ---------------------------------------------------------------------------
# 4. Tiny labeled dataset for sentiment classification (built inline)
# ---------------------------------------------------------------------------
TRAIN_TEXTS = [
    "I love this movie, it was fantastic and amazing",
    "What a great film, absolutely wonderful acting",
    "Best movie I have seen this year, brilliant",
    "This was an excellent and delightful experience",
    "I really enjoyed this, so much fun and joy",
    "Amazing story with a happy and satisfying ending",
    "I hate this movie, it was terrible and boring",
    "What an awful film, the acting was horrible",
    "Worst movie I have seen this year, disappointing",
    "This was a dreadful and painful experience",
    "I really disliked this, so much frustration",
    "Terrible story with a sad and unsatisfying ending",
]
TRAIN_LABELS = [
    "positive", "positive", "positive", "positive", "positive", "positive",
    "negative", "negative", "negative", "negative", "negative", "negative",
]

TEST_TEXTS = [
    "I loved the wonderful acting, what a fantastic experience",
    "This was a horrible and boring film, I hated it",
    "Such a delightful and amazing story, brilliant!",
    "Dreadful movie, disappointing and terrible ending",
]
TEST_LABELS = ["positive", "negative", "positive", "negative"]


# ---------------------------------------------------------------------------
# 5. Bag-of-Words vectorization + Naive Bayes sentiment classifier
# ---------------------------------------------------------------------------
def run_sentiment_pipeline() -> None:
    print("=== Bag-of-Words Vectorization + Sentiment Classifier Demo ===")

    # Preprocess (tokenize + remove stopwords) before vectorizing.
    cleaned_train = [preprocess(t) for t in TRAIN_TEXTS]
    cleaned_test = [preprocess(t) for t in TEST_TEXTS]

    vectorizer = CountVectorizer()
    X_train = vectorizer.fit_transform(cleaned_train)
    X_test = vectorizer.transform(cleaned_test)

    vocab = vectorizer.get_feature_names_out()
    print(f"Vocabulary size: {len(vocab)}")
    print(f"Sample vocabulary: {sorted(vocab)[:15]} ...")
    print(f"Bag-of-Words matrix shape (train): {X_train.shape}")
    print()

    clf = MultinomialNB()
    clf.fit(X_train, TRAIN_LABELS)

    predictions = clf.predict(X_test)

    print("Predictions on held-out test sentences:")
    for text, true_label, pred_label in zip(TEST_TEXTS, TEST_LABELS, predictions):
        mark = "OK" if true_label == pred_label else "WRONG"
        print(f"  [{mark}] '{text}'")
        print(f"        true={true_label}  predicted={pred_label}")

    acc = accuracy_score(TEST_LABELS, predictions)
    print(f"\nTest accuracy: {acc:.2f}")
    print("\nFull classification report:")
    print(classification_report(TEST_LABELS, predictions, zero_division=0))

    # Try it on a brand-new custom sentence
    custom = "The plot was fantastic but the ending felt a bit disappointing"
    custom_vec = vectorizer.transform([preprocess(custom)])
    custom_pred = clf.predict(custom_vec)[0]
    print(f"Custom sentence: '{custom}'")
    print(f"Predicted sentiment: {custom_pred}")


def main() -> None:
    demo_tokenization()
    run_sentiment_pipeline()


if __name__ == "__main__":
    main()
