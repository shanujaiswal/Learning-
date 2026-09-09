"""
06 - Gradient Descent From Scratch
=====================================
Companion script for: "Calculus/Optimization for ML".

Implements batch gradient descent completely from scratch (no scikit-learn)
to fit a simple linear regression line (y = w*x + b) to synthetic data.
Prints the Mean Squared Error loss shrinking over iterations and saves a
plot of the final fitted line against the data, plus the loss curve.

The gradients used here are the textbook calculus result of differentiating
the MSE loss with respect to w and b:
    L(w, b) = (1/n) * sum((w*x_i + b - y_i)^2)
    dL/dw   = (2/n) * sum((w*x_i + b - y_i) * x_i)
    dL/db   = (2/n) * sum(w*x_i + b - y_i)
"""

from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np

OUT_DIR = Path(__file__).parent
SEP = "=" * 70

INK = "#1a1a1a"
MUTED = "#6b6b6b"
PRIMARY = "#2b6cb0"
ACCENT = "#c05621"
GRID = "#d9d9d9"


def section(title: str) -> None:
    print(f"\n{SEP}\n{title}\n{SEP}")


plt.rcParams.update({
    "figure.facecolor": "white",
    "axes.facecolor": "white",
    "axes.edgecolor": MUTED,
    "axes.labelcolor": INK,
    "text.color": INK,
    "xtick.color": INK,
    "ytick.color": INK,
    "axes.grid": True,
    "grid.color": GRID,
    "grid.linewidth": 0.6,
    "axes.spines.top": False,
    "axes.spines.right": False,
    "font.size": 11,
})


# ---------------------------------------------------------------------------
# STEP 1 -- Generate synthetic linear data with noise
# ---------------------------------------------------------------------------

section("STEP 1: Generate synthetic linear data (true y = 4x + 7 + noise)")

rng = np.random.default_rng(seed=21)
n = 100
true_w, true_b = 4.0, 7.0

x = rng.uniform(0, 10, size=n)
noise = rng.normal(scale=2.5, size=n)
y = true_w * x + true_b + noise

print(f"Generated {n} points. True weight={true_w}, true bias={true_b}")
print(f"First 5 (x, y): {list(zip(x[:5].round(2), y[:5].round(2)))}")


# ---------------------------------------------------------------------------
# STEP 2 -- Gradient descent implemented from scratch
# ---------------------------------------------------------------------------

section("STEP 2: Batch gradient descent (from scratch)")


def compute_loss(w: float, b: float, x: np.ndarray, y: np.ndarray) -> float:
    """Mean squared error loss."""
    y_pred = w * x + b
    return np.mean((y_pred - y) ** 2)


def compute_gradients(w: float, b: float, x: np.ndarray, y: np.ndarray):
    """Partial derivatives of MSE loss w.r.t. w and b."""
    n = len(x)
    y_pred = w * x + b
    error = y_pred - y
    dw = (2.0 / n) * np.sum(error * x)
    db = (2.0 / n) * np.sum(error)
    return dw, db


# Hyperparameters
learning_rate = 0.01
n_iterations = 1000

# Initialize parameters at an arbitrary (deliberately "wrong") starting point.
w, b = 0.0, 0.0

loss_history = []
w_history = []
b_history = []

for it in range(1, n_iterations + 1):
    loss = compute_loss(w, b, x, y)
    dw, db = compute_gradients(w, b, x, y)

    # The core gradient descent update rule: step opposite the gradient.
    w -= learning_rate * dw
    b -= learning_rate * db

    loss_history.append(loss)
    w_history.append(w)
    b_history.append(b)

    if it in (1, 10, 50, 100, 200, 500, 1000):
        print(f"Iteration {it:5d} | loss (MSE) = {loss:9.4f} | "
              f"w = {w:.4f} | b = {b:.4f}")

final_loss = compute_loss(w, b, x, y)
print(f"\nFinal parameters after {n_iterations} iterations: "
      f"w = {w:.4f} (true = {true_w}), b = {b:.4f} (true = {true_b})")
print(f"Final loss (MSE): {final_loss:.4f}")
print(f"Loss decreased from {loss_history[0]:.4f} to {loss_history[-1]:.4f} "
      f"across training.")


# ---------------------------------------------------------------------------
# CHART -- Loss curve + fitted line
# ---------------------------------------------------------------------------

section("CHART: Loss curve + fitted line -> 06_gradient_descent_fit.png")

fig, axes = plt.subplots(1, 2, figsize=(11, 4.5))

ax = axes[0]
ax.plot(range(1, n_iterations + 1), loss_history, color=PRIMARY, linewidth=2)
ax.set_title("Loss (MSE) vs. Iteration", fontsize=12, fontweight="bold")
ax.set_xlabel("Iteration")
ax.set_ylabel("MSE loss")
ax.set_yscale("log")

ax = axes[1]
ax.scatter(x, y, color=PRIMARY, alpha=0.6, s=25, edgecolor="white",
           linewidth=0.4, label="Data")
x_line = np.linspace(x.min(), x.max(), 100)
y_line = w * x_line + b
ax.plot(x_line, y_line, color=ACCENT, linewidth=2.5,
        label=f"Fitted: y = {w:.2f}x + {b:.2f}")
ax.set_title("Fitted Line (gradient descent result)", fontsize=12,
             fontweight="bold")
ax.set_xlabel("x")
ax.set_ylabel("y")
ax.legend(frameon=False, loc="upper left")

fig.tight_layout()
fig.savefig(OUT_DIR / "06_gradient_descent_fit.png", dpi=150)
plt.close(fig)
print("Saved 06_gradient_descent_fit.png")

section("Done. Gradient descent converged from scratch, no scikit-learn used.")
