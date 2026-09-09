"""
05 - Attention Mechanism From Scratch (pure NumPy)
=====================================================

Companion to: Theory/04 Transformers and Attention Mechanism.md

Goal: implement scaled dot-product self-attention with nothing but NumPy,
mirroring the "it" / "cat" worked example from the theory chapter, so every
moving part - query, key, value, scores, softmax, weighted sum - is visible
and printable.

Toy sequence: 4 tokens, each represented by a small hand-picked embedding.
    tokens = ["The", "cat", "sat", "down"]

We deliberately make "cat" and "sat" similar in embedding space (both about
the cat's action) so the printed attention matrix shows a clear, explainable
pattern: which positions attend most to which.

Self-attention recipe, for input embeddings X (shape: seq_len x d_model):
    Q = X @ W_q          queries  - "what am I looking for?"
    K = X @ W_k          keys     - "what do I contain?"
    V = X @ W_v          values   - "what do I actually offer?"

    scores = Q @ K.T / sqrt(d_k)         raw compatibility between every
                                          query and every key
    weights = softmax(scores, axis=-1)   normalise each row to a probability
                                          distribution over positions
    output = weights @ V                 weighted sum of values

Run:
    python 05_attention_mechanism_from_scratch.py
"""

import numpy as np

np.random.seed(42)


# ---------------------------------------------------------------------------
# 1. Toy sequence and embeddings
# ---------------------------------------------------------------------------
tokens = ["The", "cat", "sat", "down"]
d_model = 4  # embedding dimension - kept tiny so everything prints cleanly

# Hand-picked (not learned) embeddings, one row per token. "cat" and "sat"
# are made deliberately similar so the resulting attention pattern is easy
# to read: we'd expect "sat" to attend a fair bit to "cat" (its subject).
X = np.array([
    [1.0, 0.0, 1.0, 0.0],   # "The"
    [0.0, 1.0, 1.0, 0.0],   # "cat"
    [0.0, 1.0, 0.0, 1.0],   # "sat"
    [1.0, 0.0, 0.0, 1.0],   # "down"
])
seq_len, d_model = X.shape
print(f"tokens: {tokens}")
print(f"embeddings X (shape {X.shape}):\n{X}\n")


# ---------------------------------------------------------------------------
# 2. Learned projection matrices: Q, K, V weights
# ---------------------------------------------------------------------------
# In a real transformer these are learned by backprop. Here they're just
# small fixed random matrices so we can run attention end-to-end without
# a training loop - the mechanic is the point, not the learning.
d_k = d_model  # keep query/key/value dimension same as embedding dimension

W_q = np.random.randn(d_model, d_k) * 0.5
W_k = np.random.randn(d_model, d_k) * 0.5
W_v = np.random.randn(d_model, d_k) * 0.5


def softmax(z, axis=-1):
    """Numerically stable softmax: subtract the row max before exponentiating
    so we never overflow, then normalise each row to sum to 1.
    """
    z_shifted = z - np.max(z, axis=axis, keepdims=True)
    exp_z = np.exp(z_shifted)
    return exp_z / np.sum(exp_z, axis=axis, keepdims=True)


# ---------------------------------------------------------------------------
# 3. Scaled dot-product self-attention
# ---------------------------------------------------------------------------
def self_attention(X, W_q, W_k, W_v):
    Q = X @ W_q  # (seq_len, d_k) - "what each position is looking for"
    K = X @ W_k  # (seq_len, d_k) - "what each position contains"
    V = X @ W_v  # (seq_len, d_k) - "what each position offers if attended to"

    # Raw compatibility scores between every query and every key.
    # Scaling by sqrt(d_k) keeps the dot products from growing too large as
    # d_k increases, which would otherwise push softmax into saturated,
    # near-one-hot regions and stall gradients.
    scores = Q @ K.T / np.sqrt(d_k)  # (seq_len, seq_len)

    # Row i of `weights` is a probability distribution over which positions
    # position i attends to; rows sum to 1.
    weights = softmax(scores, axis=-1)  # (seq_len, seq_len)

    # Each output row is a weighted blend of ALL value vectors, weighted by
    # how much that position attends to each other position.
    output = weights @ V  # (seq_len, d_k)

    return Q, K, V, scores, weights, output


Q, K, V, scores, weights, output = self_attention(X, W_q, W_k, W_v)


# ---------------------------------------------------------------------------
# 4. Print everything so the mechanic is fully visible
# ---------------------------------------------------------------------------
np.set_printoptions(precision=3, suppress=True)

print(f"Q (queries):\n{Q}\n")
print(f"K (keys):\n{K}\n")
print(f"V (values):\n{V}\n")
print(f"raw scores (Q @ K.T / sqrt(d_k)):\n{scores}\n")

print("Attention weight matrix (rows = query position, cols = key position, "
      "each row sums to 1):")
header = "        " + "  ".join(f"{tok:>6s}" for tok in tokens)
print(header)
for i, row in enumerate(weights):
    row_str = "  ".join(f"{w:6.3f}" for w in row)
    print(f"{tokens[i]:>6s}  {row_str}")

print("\nInterpretation: for each row (the token doing the 'looking'), the "
      "column with the highest weight is the token it attends to most.")
for i, row in enumerate(weights):
    top_j = int(np.argmax(row))
    print(f"  '{tokens[i]}' attends most to '{tokens[top_j]}' "
          f"(weight={row[top_j]:.3f})")

print(f"\nOutput (weights @ V) - each row is a context-aware blend of the "
      f"value vectors, shape {output.shape}:\n{output}")
