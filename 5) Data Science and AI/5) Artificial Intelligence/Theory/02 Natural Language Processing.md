# What NLP Is

--> Natural Language Processing (NLP) is the field focused on enabling computers to understand, interpret, and generate human language -- text and speech -- one of the hardest AI problems precisely because human language is ambiguous, context-dependent, and full of exceptions to any rule you try to write down.

# Text Preprocessing -- Preparing Raw Text

--> **Tokenization** -- splitting text into individual units (words, subwords, or characters) -- the first step in almost any NLP pipeline, since a model needs discrete units to work with rather than one long string.
--> **Stop word removal** -- filtering out extremely common, low-information words ("the," "is," "a") -- useful for some traditional techniques, though modern deep learning approaches (covered below) often skip this, since these words can still carry subtle grammatical/contextual signal.
--> **Stemming/Lemmatization** -- reducing words to a common root form ("running," "ran," "runs" → "run") so a model treats them as related rather than entirely distinct tokens.

```python
from nltk.tokenize import word_tokenize
from nltk.stem import WordNetLemmatizer

tokens = word_tokenize("The cats were running quickly")
# ['The', 'cats', 'were', 'running', 'quickly']

lemmatizer = WordNetLemmatizer()
lemmatizer.lemmatize("running", pos="v")   # 'run'
```

# Representing Text Numerically

--> Machine learning models (covered in the ML folder) need NUMBERS, not raw text -- turning words into numeric representations is a foundational NLP problem.
--> **Bag of Words / TF-IDF** -- represents a document as a vector of word counts (or weighted counts, in TF-IDF's case, down-weighting words common across many documents) -- simple, interpretable, but ignores word order and any sense of meaning/similarity between different words.
--> **Word Embeddings (Word2Vec, GloVe)** -- represent each word as a dense vector of numbers, LEARNED such that words with similar meanings end up close together in that vector space -- captures semantic similarity in a way Bag of Words fundamentally can't (e.g. "king" and "queen" end up numerically close, "king" and "banana" end up far apart).

```python
# Conceptual illustration of embedding arithmetic -- a famous, often-cited example
king_vector - man_vector + woman_vector ≈ queen_vector
# The learned vector space captures a genuine, meaningful semantic relationship
```

# Modern NLP -- Transformer-Based Language Models

--> The Transformers and Attention Mechanism file in the Deep Learning folder covers the architecture now underlying essentially all state-of-the-art NLP -- modern language models produce CONTEXTUAL embeddings, where the same word gets a different numeric representation depending on its surrounding context (unlike Word2Vec's fixed, single vector per word), directly solving a major limitation of the earlier embedding approaches above.

```python
from transformers import pipeline

summarizer = pipeline("summarization")
summary = summarizer("Long article text here...", max_length=50)

ner = pipeline("ner")   # Named Entity Recognition -- identifying people, places, organizations in text
entities = ner("Apple was founded by Steve Jobs in Cupertino")
```

# Core NLP Tasks

--> **Sentiment Analysis** -- determining whether text expresses positive/negative/neutral sentiment -- widely used for analyzing customer reviews/social media (connecting to the Data Analyst folder's business-analysis focus).
--> **Named Entity Recognition (NER)** -- identifying and categorizing real-world entities (people, organizations, locations, dates) mentioned in text.
--> **Machine Translation** -- translating text between languages, a task that directly motivated the original Transformer encoder-decoder architecture.
--> **Text Summarization and Question Answering** -- condensing long text, or extracting/generating answers to questions from a body of text -- core capabilities behind the large language model applications covered in the Generative AI file.

# Why NLP Is Genuinely Hard

--> Ambiguity is everywhere -- "I saw her duck" could mean watching a bird she owns, or watching her lower her head -- resolving this correctly requires broader context a purely word-level analysis can't capture, which is precisely the kind of long-range, contextual understanding that Transformer-based attention (covered in the Deep Learning folder) was specifically designed to capture better than earlier approaches.
