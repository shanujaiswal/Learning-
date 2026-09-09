"""
04 - RNN / LSTM Sequence Demo (PyTorch)
==========================================

Companion to: Theory/03 Recurrent Neural Networks and LSTMs.md

Goal: show an LSTM doing what RNNs/LSTMs are for - modelling a sequence
where the current value depends on previous values.

Problem: predict the next point of a noisy sine wave from a short window
of preceding points.

    given [sin(t-19), sin(t-18), ..., sin(t)]  ->  predict sin(t+1)

This is the sequence-model equivalent of script 01/02's XOR: small,
synthetic, and easy to sanity-check by eye (plot prediction vs. actual).

Architecture:

    input (window of scalars) -> LSTM(hidden_size) -> take last hidden
    state -> Linear -> single scalar prediction

Output: saves a PNG (`04_rnn_lstm_prediction.png`, next to this script)
plotting predicted vs. actual values on a held-out stretch of the wave.

Run:
    python 04_rnn_lstm_sequence_demo.py
"""

import os

import numpy as np
import torch
import torch.nn as nn
import matplotlib
matplotlib.use("Agg")  # write straight to a file, no GUI window needed
import matplotlib.pyplot as plt

torch.manual_seed(42)
np.random.seed(42)

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Using device: {DEVICE}")


# ---------------------------------------------------------------------------
# 1. Data: a noisy sine wave, sliced into (window -> next value) pairs
# ---------------------------------------------------------------------------
SEQ_LEN = 20        # how many past points the model gets to see
N_POINTS = 1000      # total points along the wave
NOISE_STD = 0.05

t = np.linspace(0, 60 * np.pi, N_POINTS)
series = np.sin(t) + NOISE_STD * np.random.randn(N_POINTS)
series = series.astype(np.float32)


def make_windows(data, seq_len):
    """Slide a window of length `seq_len` over `data`, pairing each window
    with the single value that comes right after it.

    Returns X of shape (n_windows, seq_len, 1) and y of shape (n_windows, 1).
    """
    xs, ys = [], []
    for i in range(len(data) - seq_len):
        xs.append(data[i:i + seq_len])
        ys.append(data[i + seq_len])
    X = np.array(xs, dtype=np.float32).reshape(-1, seq_len, 1)
    y = np.array(ys, dtype=np.float32).reshape(-1, 1)
    return X, y


X, y = make_windows(series, SEQ_LEN)

# Chronological split: train on the first 80%, test on the last 20%
# (no shuffling - shuffling a time series would leak the future into training).
split = int(0.8 * len(X))
X_train, y_train = X[:split], y[:split]
X_test, y_test = X[split:], y[split:]

X_train = torch.from_numpy(X_train).to(DEVICE)
y_train = torch.from_numpy(y_train).to(DEVICE)
X_test = torch.from_numpy(X_test).to(DEVICE)
y_test = torch.from_numpy(y_test).to(DEVICE)

print(f"train windows: {X_train.shape[0]}   test windows: {X_test.shape[0]}")


# ---------------------------------------------------------------------------
# 2. Model: a small LSTM followed by a linear projection to one scalar
# ---------------------------------------------------------------------------
class LSTMPredictor(nn.Module):
    def __init__(self, input_size=1, hidden_size=32, num_layers=1):
        super().__init__()
        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,  # so input shape is (batch, seq, features)
        )
        self.fc = nn.Linear(hidden_size, 1)

    def forward(self, x):
        # x: (batch, seq_len, 1)
        # lstm_out: (batch, seq_len, hidden_size) - hidden state at every step
        # h_n: (num_layers, batch, hidden_size) - final hidden state
        lstm_out, (h_n, c_n) = self.lstm(x)
        last_hidden = lstm_out[:, -1, :]  # hidden state after the LAST time step
        return self.fc(last_hidden)        # -> (batch, 1) predicted next value


model = LSTMPredictor().to(DEVICE)
criterion = nn.MSELoss()
optimizer = torch.optim.Adam(model.parameters(), lr=1e-2)


# ---------------------------------------------------------------------------
# 3. Training loop
# ---------------------------------------------------------------------------
def train():
    N_EPOCHS = 150
    print(f"\nTraining LSTM on next-step sine-wave prediction for {N_EPOCHS} epochs...\n")

    for epoch in range(1, N_EPOCHS + 1):
        model.train()
        optimizer.zero_grad()
        preds = model(X_train)
        loss = criterion(preds, y_train)
        loss.backward()
        optimizer.step()

        if epoch % 15 == 0 or epoch == N_EPOCHS:
            model.eval()
            with torch.no_grad():
                test_loss = criterion(model(X_test), y_test).item()
            print(f"epoch {epoch:3d}/{N_EPOCHS}  train_loss={loss.item():.5f}  "
                  f"test_loss={test_loss:.5f}")


# ---------------------------------------------------------------------------
# 4. Evaluate + plot prediction vs. actual on the held-out stretch
# ---------------------------------------------------------------------------
@torch.no_grad()
def plot_predictions():
    model.eval()
    preds = model(X_test).cpu().numpy().flatten()
    actual = y_test.cpu().numpy().flatten()

    plt.figure(figsize=(10, 4))
    plt.plot(actual, label="actual", linewidth=2)
    plt.plot(preds, label="predicted", linestyle="--")
    plt.title("LSTM: predicted vs. actual next value (held-out test stretch)")
    plt.xlabel("test window index")
    plt.ylabel("value")
    plt.legend()
    plt.tight_layout()

    out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             "04_rnn_lstm_prediction.png")
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to: {out_path}")


if __name__ == "__main__":
    train()
    plot_predictions()
