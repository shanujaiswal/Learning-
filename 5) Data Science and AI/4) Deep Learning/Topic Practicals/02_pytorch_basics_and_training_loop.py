"""
02 - PyTorch Basics and the Standard Training Loop
====================================================

Companion to: Theory/01 Neural Network Fundamentals.md (practical bridge)

This solves the SAME kind of problem as 01_neural_network_from_scratch.py
(XOR), then a slightly richer synthetic dataset, but the idiomatic PyTorch
way:

  - model defined as an nn.Module subclass, built from nn.Linear layers
  - loss computed with a built-in loss function
  - gradients computed automatically via autograd (loss.backward())
  - weights updated by an optimizer (optimizer.step())

Compare this file line-by-line with script 01: every hand-written line
there (matrix multiply, sigmoid derivative, manual weight update) has a
one-line equivalent here. That's the whole point of a framework.

Run:
    python 02_pytorch_basics_and_training_loop.py
"""

import torch
import torch.nn as nn

torch.manual_seed(42)


# ---------------------------------------------------------------------------
# Part A: XOR, the same 4-point dataset as script 01
# ---------------------------------------------------------------------------
X = torch.tensor([
    [0.0, 0.0],
    [0.0, 1.0],
    [1.0, 0.0],
    [1.0, 1.0],
])
y = torch.tensor([[0.0], [1.0], [1.0], [0.0]])


class XORNet(nn.Module):
    """Same architecture as script 01: 2 -> 4 (sigmoid) -> 1 (sigmoid)."""

    def __init__(self, n_input=2, n_hidden=4, n_output=1):
        super().__init__()
        self.hidden = nn.Linear(n_input, n_hidden)
        self.output = nn.Linear(n_hidden, n_output)
        self.sigmoid = nn.Sigmoid()

    def forward(self, x):
        x = self.sigmoid(self.hidden(x))
        x = self.sigmoid(self.output(x))
        return x


def train_xor():
    print("=" * 60)
    print("Part A: XOR with nn.Module + autograd + optimizer")
    print("=" * 60)

    model = XORNet()
    criterion = nn.BCELoss()  # binary cross-entropy, same loss as script 01
    optimizer = torch.optim.SGD(model.parameters(), lr=0.5)

    n_epochs = 5000
    for epoch in range(n_epochs):
        optimizer.zero_grad()          # clear gradients from the previous step
        predictions = model(X)          # forward pass
        loss = criterion(predictions, y)
        loss.backward()                 # autograd computes ALL gradients for us
        optimizer.step()                 # gradient descent update

        if epoch % 500 == 0 or epoch == n_epochs - 1:
            print(f"epoch {epoch:5d}  loss {loss.item():.4f}")

    print("\nFinal predictions:")
    with torch.no_grad():  # no need to track gradients for inference
        preds = model(X)
        for xi, pi, yi in zip(X, preds, y):
            print(f"  input={xi.tolist()}  predicted={pi.item():.4f}  "
                  f"rounded={round(pi.item())}  actual={int(yi.item())}")


# ---------------------------------------------------------------------------
# Part B: a slightly richer synthetic dataset — two interleaving "moons"
# (200 points, 2D, binary label). Same recipe, more data, deeper network.
# ---------------------------------------------------------------------------
def make_moons(n_samples=200, noise=0.15):
    """Small self-contained two-moons generator (no sklearn dependency)."""
    n_per_class = n_samples // 2
    theta1 = torch.linspace(0, torch.pi, n_per_class)
    theta2 = torch.linspace(0, torch.pi, n_per_class)

    x1 = torch.stack([torch.cos(theta1), torch.sin(theta1)], dim=1)
    x2 = torch.stack([1 - torch.cos(theta2), 1 - torch.sin(theta2) - 0.5], dim=1)

    x1 += noise * torch.randn_like(x1)
    x2 += noise * torch.randn_like(x2)

    X = torch.cat([x1, x2], dim=0)
    y = torch.cat([torch.zeros(n_per_class, 1), torch.ones(n_per_class, 1)], dim=0)
    return X, y


class MoonsNet(nn.Module):
    """A slightly deeper MLP: 2 -> 16 -> 8 -> 1, ReLU hidden activations."""

    def __init__(self):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(2, 16),
            nn.ReLU(),
            nn.Linear(16, 8),
            nn.ReLU(),
            nn.Linear(8, 1),
            nn.Sigmoid(),
        )

    def forward(self, x):
        return self.net(x)


def train_moons():
    print("\n" + "=" * 60)
    print("Part B: richer synthetic dataset (two moons)")
    print("=" * 60)

    X, y = make_moons(n_samples=200)
    model = MoonsNet()
    criterion = nn.BCELoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=0.01)

    n_epochs = 300
    for epoch in range(n_epochs):
        optimizer.zero_grad()
        predictions = model(X)
        loss = criterion(predictions, y)
        loss.backward()
        optimizer.step()

        if epoch % 30 == 0 or epoch == n_epochs - 1:
            with torch.no_grad():
                accuracy = ((predictions > 0.5).float() == y).float().mean().item()
            print(f"epoch {epoch:4d}  loss {loss.item():.4f}  accuracy {accuracy:.2%}")


if __name__ == "__main__":
    train_xor()
    train_moons()
