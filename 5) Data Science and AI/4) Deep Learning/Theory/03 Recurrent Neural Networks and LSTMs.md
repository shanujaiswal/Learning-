# Why Sequential Data Needs a Different Architecture

--> CNNs (covered in the previous file) exploit SPATIAL structure in images. Text, speech, and time-series data have a different kind of structure -- SEQUENTIAL/temporal order matters enormously ("dog bites man" vs "man bites dog" use identical words in a different, meaning-changing order) -- a fixed-size, order-blind network architecture doesn't naturally capture this.

# The Core RNN Idea -- A Memory That Persists

--> An RNN processes a sequence one element at a time (one word, one time step), maintaining a "hidden state" that gets updated at each step and carries forward information from everything seen SO FAR in the sequence -- effectively giving the network a form of memory across the sequence.

```
hidden_state(t) = activation(W_input * input(t) + W_hidden * hidden_state(t-1) + bias)
```

--> The SAME weights (`W_input`, `W_hidden`) are reused at every time step -- another instance of parameter sharing, similar in spirit to how a CNN's filter is reused across every spatial position, just applied across time instead.

```python
import tensorflow as tf
from tensorflow.keras import layers, models

model = models.Sequential([
    layers.SimpleRNN(64, input_shape=(sequence_length, num_features)),
    layers.Dense(1, activation="sigmoid")
])
```

# The Vanishing Gradient Problem -- Why Plain RNNs Struggle With Long Sequences

--> When backpropagation (covered in the Neural Network Fundamentals file) works backward through many time steps, the repeated multiplication of gradients across each step can cause them to shrink toward zero (vanish) or grow uncontrollably (explode) -- in practice, this means plain/"vanilla" RNNs struggle to learn dependencies between elements that are far apart in a long sequence (e.g. connecting a pronoun to a noun mentioned many sentences earlier).

# LSTM -- Long Short-Term Memory

--> LSTMs were specifically designed to solve the vanishing gradient problem, using an internal "cell state" and three learned "gates" that control information flow:
--> **Forget gate** -- decides what information from the past to discard.
--> **Input gate** -- decides what new information from the current input to add.
--> **Output gate** -- decides what part of the current cell state to actually output as the hidden state.

```python
model = models.Sequential([
    layers.LSTM(64, input_shape=(sequence_length, num_features)),
    layers.Dense(1, activation="sigmoid")
])
```

--> These gates let an LSTM learn to selectively REMEMBER important information across many time steps while forgetting irrelevant details -- dramatically better than plain RNNs at capturing long-range dependencies in a sequence.

# GRU -- A Simplified Alternative

--> Gated Recurrent Units combine LSTM's forget and input gates into a single "update gate," resulting in a simpler architecture with fewer parameters -- often achieves comparable performance to LSTM with faster training, though which performs better varies by specific task and dataset.

# Bidirectional RNNs

--> A standard RNN/LSTM only sees a sequence in one direction (left to right, for text) -- a Bidirectional RNN runs TWO separate networks, one processing the sequence forward and one backward, then combines both -- useful whenever context from BOTH before and after a given point matters (common in text understanding tasks).

# Practical Use Cases

--> Time series forecasting (stock prices, sensor readings), sentiment analysis, machine translation (translating a full sentence, where word order/context matters enormously), speech recognition.

# Why This Architecture Was Eventually Superseded for Many NLP Tasks

--> RNNs/LSTMs process sequences strictly one step at a time, which is inherently slow to parallelize and still struggles with VERY long sequences despite LSTM's improvements -- the Transformers file that follows covers the architecture that has largely replaced RNNs for most modern large-scale language tasks, precisely by addressing these remaining limitations.
