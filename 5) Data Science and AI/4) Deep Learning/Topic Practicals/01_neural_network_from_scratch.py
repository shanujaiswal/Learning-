"""
01 - Neural Network From Scratch (pure NumPy)
==============================================

Companion to: Theory/01 Neural Network Fundamentals.md

Goal: build the smallest possible neural network that still shows every
moving part explicitly:

    input -> [Linear -> sigmoid] hidden layer -> [Linear -> sigmoid] output

No autograd, no framework. We derive and code the forward pass, the loss,
the backward pass (manual chain rule / backpropagation), and the gradient
descent weight update ourselves.

Problem: XOR. It's the classic "why do we even need a hidden layer" demo -
XOR is NOT linearly separable, so a single-layer perceptron cannot solve it,
but one hidden layer with a nonlinearity can.

    x1  x2  |  y (XOR)
    0   0   |  0
    0   1   |  1
    1   0   |  1
    1   1   |  0

Run:
    python 01_neural_network_from_scratch.py
"""

import numpy as np

# Reproducibility
np.random.seed(42)


# ---------------------------------------------------------------------------
# 1. Data
# ---------------------------------------------------------------------------
# 4 examples, 2 features each.
X = np.array([
    [0.0, 0.0],
    [0.0, 1.0],
    [1.0, 0.0],
    [1.0, 1.0],
])  # shape (4, 2)

y = np.array([[0.0], [1.0], [1.0], [0.0]])  # shape (4, 1)


# ---------------------------------------------------------------------------
# 2. Activation functions and their derivatives
# ---------------------------------------------------------------------------
def sigmoid(z):
    return 1.0 / (1.0 + np.exp(-z))


def sigmoid_derivative(a):
    """Derivative of sigmoid, expressed in terms of the sigmoid OUTPUT `a`
    (a = sigmoid(z)) rather than the raw input z. This is the standard trick
    that makes backprop code short: d/dz sigmoid(z) = sigmoid(z)*(1-sigmoid(z)) = a*(1-a)
    """
    return a * (1.0 - a)


# ---------------------------------------------------------------------------
# 3. Network architecture / parameter initialisation
# ---------------------------------------------------------------------------
n_input = 2       # x1, x2
n_hidden = 4       # size of the (only) hidden layer
n_output = 1       # single probability output

# Small random weights, zero biases - standard starting point.
W1 = np.random.randn(n_input, n_hidden) * 0.5   # (2, 4)
b1 = np.zeros((1, n_hidden))                     # (1, 4)
W2 = np.random.randn(n_hidden, n_output) * 0.5  # (4, 1)
b2 = np.zeros((1, n_output))                     # (1, 1)

learning_rate = 0.5
n_epochs = 10_000


def forward(X):
    """Forward pass. Returns intermediate activations because backprop
    needs them.

    z1 = X @ W1 + b1     (linear combination into the hidden layer)
    a1 = sigmoid(z1)     (hidden layer activations)
    z2 = a1 @ W2 + b2    (linear combination into the output layer)
    a2 = sigmoid(z2)     (predicted probability)
    """
    z1 = X @ W1 + b1
    a1 = sigmoid(z1)
    z2 = a1 @ W2 + b2
    a2 = sigmoid(z2)
    return z1, a1, z2, a2


def binary_cross_entropy(y_true, y_pred, eps=1e-8):
    """Standard loss for binary classification.
    L = -mean( y*log(p) + (1-y)*log(1-p) )
    """
    y_pred = np.clip(y_pred, eps, 1 - eps)  # avoid log(0)
    return -np.mean(y_true * np.log(y_pred) + (1 - y_true) * np.log(1 - y_pred))


# ---------------------------------------------------------------------------
# 4. Training loop: forward -> loss -> backward (manual backprop) -> update
# ---------------------------------------------------------------------------
print("Training a 2-4-1 feedforward network on XOR, from scratch...\n")

for epoch in range(n_epochs):
    m = X.shape[0]  # number of training examples, used to average gradients

    # ---- forward pass ----
    z1, a1, z2, a2 = forward(X)
    loss = binary_cross_entropy(y, a2)

    # ---- backward pass (manual chain rule) ----
    # dL/da2 combined with da2/dz2 (sigmoid derivative) gives a clean form
    # for binary cross-entropy + sigmoid output:
    #     dL/dz2 = (a2 - y) / m
    dz2 = (a2 - y) / m                       # (4, 1)
    dW2 = a1.T @ dz2                         # (4, 1)
    db2 = np.sum(dz2, axis=0, keepdims=True)  # (1, 1)

    # Propagate the error back through W2 into the hidden layer.
    da1 = dz2 @ W2.T                         # (4, 4)
    dz1 = da1 * sigmoid_derivative(a1)       # chain rule through sigmoid
    dW1 = X.T @ dz1                          # (2, 4)
    db1 = np.sum(dz1, axis=0, keepdims=True)  # (1, 4)

    # ---- gradient descent update ----
    W2 -= learning_rate * dW2
    b2 -= learning_rate * db2
    W1 -= learning_rate * dW1
    b1 -= learning_rate * db1

    if epoch % 1000 == 0 or epoch == n_epochs - 1:
        print(f"epoch {epoch:5d}  loss {loss:.4f}")


# ---------------------------------------------------------------------------
# 5. Final predictions
# ---------------------------------------------------------------------------
_, _, _, predictions = forward(X)
print("\nFinal predictions (probability of class 1) vs. ground truth:")
for xi, pi, yi in zip(X, predictions, y):
    print(f"  input={xi.tolist()}  predicted={pi[0]:.4f}  "
          f"rounded={round(pi[0])}  actual={int(yi[0])}")
