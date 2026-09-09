# The Neuron -- The Basic Building Block

--> A single artificial neuron takes several numeric inputs, multiplies each by a learned "weight," sums them together plus a "bias" term, then passes that sum through an "activation function" to produce an output -- conceptually, this is exactly Logistic Regression (covered in the Machine Learning folder) with one extra step; a neural network is essentially many of these connected together in layers.

```
output = activation(w1*x1 + w2*x2 + ... + b)
```

# Layers -- Stacking Neurons Together

--> **Input layer** -- the raw features going in (e.g. pixel values, word representations) -- no computation happens here, just data entry.
--> **Hidden layer(s)** -- layers of neurons between input and output, where the actual learned transformations happen -- "deep" learning refers specifically to networks with multiple hidden layers stacked together.
--> **Output layer** -- produces the final prediction -- one neuron for regression, one neuron per class (with a softmax activation) for multi-class classification.

```python
import tensorflow as tf
from tensorflow import keras

model = keras.Sequential([
    keras.layers.Dense(64, activation="relu", input_shape=(10,)),   # Hidden layer, 64 neurons
    keras.layers.Dense(32, activation="relu"),                        # Another hidden layer
    keras.layers.Dense(1, activation="sigmoid")                        # Output layer, binary classification
])
```

# Activation Functions -- Why They're Necessary

--> Without an activation function, stacking layers would just be equivalent to one big linear operation (linear combinations of linear combinations are still linear) -- activation functions introduce NON-LINEARITY, which is what actually lets a network learn complex, curved decision boundaries and relationships.
--> **ReLU** (`max(0, x)`) -- the most common choice for hidden layers today -- computationally cheap and avoids a problem older activation functions had with very small gradients during training.
--> **Sigmoid** -- squashes output to between 0 and 1 -- used in the output layer for binary classification, directly connecting to how Logistic Regression works in the Machine Learning folder.
--> **Softmax** -- generalizes sigmoid to multiple classes, producing a probability distribution across all classes that sums to exactly 1.

# Forward Propagation -- Making a Prediction

--> Data flows through the network layer by layer, left to right, each layer's output becoming the next layer's input, until the final output layer produces a prediction -- this is "forward propagation," the process of actually USING a trained (or untrained) network to make a prediction.

# Backpropagation -- How a Network Actually Learns

--> After a forward pass produces a prediction, the loss function (echoing MSE from the Regression file, or cross-entropy for classification) measures how wrong that prediction was.
--> Backpropagation then calculates exactly how much EACH weight in the network contributed to that error, working BACKWARD from the output layer to the input layer, using calculus (the chain rule) to efficiently compute these contributions layer by layer.
--> Gradient Descent then uses those calculated contributions ("gradients") to nudge every weight slightly in the direction that would have reduced the error -- repeated over many training examples and many passes ("epochs") through the data, the network's weights gradually converge toward values that make good predictions.

```python
model.compile(optimizer="adam", loss="binary_crossentropy", metrics=["accuracy"])
model.fit(X_train, y_train, epochs=20, batch_size=32, validation_data=(X_val, y_val))
```

--> `optimizer="adam"` -- a specific, widely-used variant of gradient descent that adapts its step size automatically during training, generally converging faster and more reliably than plain gradient descent.
--> `batch_size` -- rather than updating weights after every single example (slow) or the entire dataset at once (memory-intensive, less frequent updates), training processes data in small batches -- a practical middle ground.

# Why GPUs Matter for This Field

--> The matrix multiplications underlying forward/backward propagation are exactly the kind of massively parallel computation GPUs are built for (originally designed for rendering graphics) -- this hardware fit is a major practical reason deep learning became computationally feasible at scale, not just a theoretical convenience.
