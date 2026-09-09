# Deep Learning — Practical

Companion code for the `Theory` chapters. Each script is standalone, runnable,
and deliberately small so you can read it top to bottom in one sitting.

## Setup

```bash
pip install torch numpy matplotlib
```

Notes:
- `torch` (PyTorch) CPU-only build is plenty for every demo here — nothing in
  this folder needs a GPU. The pip package is a few hundred MB; that's normal
  for a deep learning framework and a one-time cost.
- `matplotlib` is only used by the RNN/LSTM script (04) to plot predictions
  vs. actual values.
- Script 03 downloads FashionMNIST (~30 MB) into a local `./data` folder the
  first time you run it. Subsequent runs reuse the cached copy.

## Chapter mapping

| # | Script | Theory chapter |
|---|--------|-----------------|
| — | `00 Deep Learning Roadmap.md` | (orientation, no code) |
| 1 | `01_neural_network_from_scratch.py` | `01 Neural Network Fundamentals.md` |
| 2 | `02_pytorch_basics_and_training_loop.py` | `01 Neural Network Fundamentals.md` (practical bridge) |
| 3 | `03_cnn_image_classifier.py` | `02 Convolutional Neural Networks CNNs.md` |
| 4 | `04_rnn_lstm_sequence_demo.py` | `03 Recurrent Neural Networks and LSTMs.md` |
| 5 | `05_attention_mechanism_from_scratch.py` | `04 Transformers and Attention Mechanism.md` |

## Suggested order

Run them in numeric order. 1 and 2 solve the *same* XOR-style problem two
ways — first with nothing but NumPy and hand-written calculus (so you see
exactly what a forward pass, backprop, and a gradient step actually are),
then with idiomatic PyTorch (so you see how the same math is expressed once
you let the framework track gradients for you). 3 and 4 apply that same
training-loop pattern to real architectures (CNN, LSTM). 5 drops back down
to from-scratch NumPy, the same way script 1 did, but for the attention
mechanism instead of a plain feedforward layer — deliberately mirroring the
"it" / "cat" worked example from the Transformers theory chapter.

## Files

- `01_neural_network_from_scratch.py` — 1-hidden-layer MLP, pure NumPy,
  manual forward pass + backprop + gradient descent, trained on XOR.
- `02_pytorch_basics_and_training_loop.py` — the same XOR-style problem
  (plus a slightly richer variant) via `nn.Module`, `nn.Linear`, an
  optimizer, and a real `loss.backward()` / `optimizer.step()` loop.
- `03_cnn_image_classifier.py` — small CNN (3 conv layers) trained on
  FashionMNIST, prints train/test accuracy per epoch.
- `04_rnn_lstm_sequence_demo.py` — LSTM trained to predict the next point
  of a sine wave; plots prediction vs. actual.
- `05_attention_mechanism_from_scratch.py` — scaled dot-product
  self-attention from scratch in NumPy on a tiny toy sequence, prints the
  attention weight matrix.

## Note — Why This Folder Is PyTorch But the Theory Chapters Are TensorFlow/Keras

The Theory chapters (`01` through `04`) illustrate concepts with
TensorFlow/Keras code snippets (`tf.keras.layers`, `model.fit(...)`), while
every script here is PyTorch. This is intentional, not an inconsistency to
fix: the Theory snippets favor Keras' concise, high-level syntax for
explaining a concept clearly, while this folder favors PyTorch's more
explicit `nn.Module` + manual training loop (`loss.backward()` /
`optimizer.step()`) specifically to make the training mechanics visible.
Both frameworks are current and widely used in production; expect
differences when translating between them — e.g. Keras hides the training
loop behind `model.fit(...)`, PyTorch requires writing it by hand, and the
two frameworks default to different tensor axis orders for image data
(channels-last in Keras vs. channels-first in PyTorch).
