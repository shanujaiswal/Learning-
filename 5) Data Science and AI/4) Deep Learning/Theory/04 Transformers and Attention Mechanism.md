# The Limitation Transformers Solve

--> RNNs/LSTMs (covered in the previous file) process a sequence strictly one step at a time -- inherently sequential, hard to parallelize across modern GPU hardware, and even with LSTM's gating, still imperfect at connecting very distant parts of a long sequence. Transformers (introduced in the 2017 paper "Attention Is All You Need") process an ENTIRE sequence at once, using a mechanism called "attention" instead of step-by-step recurrence.

# Self-Attention -- The Core Innovation

--> Self-attention lets every position in a sequence directly look at and weigh the relevance of EVERY OTHER position, all at once, regardless of distance -- rather than information having to flow step-by-step through many intermediate hidden states as in an RNN.
--> For each word, the mechanism computes a "query," "key," and "value" vector, then determines how much attention that word should pay to every other word by comparing queries against keys -- words that are more relevant to understanding the current word get weighted more heavily when computing its final representation.

```
Sentence: "The cat sat on the mat because it was tired"

When processing "it," self-attention learns to assign HIGH attention weight to "cat"
(correctly resolving that "it" refers to the cat, not the mat) -- learned entirely
from data, not programmed with explicit grammar rules.
```

# Multi-Head Attention

--> Rather than computing attention just once, Transformers compute it several times in parallel ("heads"), each potentially learning to focus on different TYPES of relationships (one head might track grammatical dependencies, another might track topical similarity) -- their results are then combined, giving the model a richer, multi-faceted understanding of the sequence.

# Positional Encoding -- Restoring Order Information

--> Because self-attention looks at all positions simultaneously (unlike an RNN's inherently ordered, step-by-step processing), the model needs an explicit way to know WHERE each word sits in the sequence -- positional encodings are added to each word's representation specifically to inject this order information back in.

# The Encoder-Decoder Structure

--> The original Transformer architecture has an Encoder (processes the input sequence into a rich internal representation) and a Decoder (generates an output sequence, e.g. a translation, using that representation) -- useful for tasks explicitly mapping one sequence to another (translation, summarization).
--> Many modern large language models use a decoder-only variant -- trained simply to predict the next word given everything before it, repeated at massive scale, which turns out to be sufficient for a remarkably broad range of language tasks without needing a separate encoder at all.

```python
# Using a pretrained Transformer via the Hugging Face library -- the standard practical entry point,
# since training a Transformer from scratch requires massive data/compute few individuals have access to
from transformers import pipeline

classifier = pipeline("sentiment-analysis")
result = classifier("This movie was absolutely wonderful!")
# [{'label': 'POSITIVE', 'score': 0.999}]
```

# Why Transformers Scaled So Well

--> Because self-attention processes a whole sequence in parallel (rather than sequentially like an RNN), Transformers are far more efficiently parallelizable on GPU/TPU hardware -- this is a major practical reason it became feasible to train models on vastly larger datasets and with vastly more parameters than RNN-based architectures ever practically achieved, directly enabling the rise of today's large language models.

# Where This Leads

--> Every concept in this file -- self-attention, the decoder-only next-word-prediction training objective, and the value of scaling both data and parameters -- is exactly the foundation the Generative AI and Large Language Models file in the Artificial Intelligence folder builds on, applying these same mechanics at the scale of models trained on huge portions of the internet's text.
