# Artificial Intelligence -- Practical Demos

Index of the runnable Python scripts in this folder, how each maps to a
Theory chapter, and what to `pip install` before running them.

## Index

| # | File | Theory chapter it maps to | Needs external API? |
|---|------|---------------------------|----------------------|
| 01 | `01_search_algorithms_pathfinding.py` | `Theory/00 Artificial Intelligence Roadmap.md` + `Theory/01 AI Fundamentals, History and Search Algorithms.md` | No |
| 02 | `02_nlp_text_processing_pipeline.py` | `Theory/02 Natural Language Processing.md` | No |
| 03 | `03_computer_vision_basics.py` | `Theory/03 Computer Vision.md` | No |
| 04 | `04_simple_rag_pipeline_demo.py` | `Theory/04 Generative AI, LLMs and AI Ethics.md` | No (real LLM call is mocked -- see below) |

(Theory folder referenced above: `4) Data Science and AI\5) Artificial Intelligence\Theory\`)

## What each demo does

- **01 -- Search Algorithms & Pathfinding**: Implements BFS, DFS, and A* from
  scratch on a 2D grid maze, comparing path length and nodes explored. Pure
  Python + numpy.
- **02 -- NLP Text Processing Pipeline**: Tokenization, stopword removal,
  Bag-of-Words vectorization (scikit-learn `CountVectorizer`), and a tiny
  inline-trained Naive Bayes sentiment classifier.
- **03 -- Computer Vision Basics**: Generates a synthetic test image
  (Pillow), converts it to grayscale, runs a manually-implemented Sobel edge
  detector, and applies binary thresholding. No OpenCV needed.
- **04 -- Simple RAG Pipeline Demo**: A small in-memory document store,
  TF-IDF + cosine-similarity retrieval (scikit-learn `TfidfVectorizer`), a
  prompt-augmentation step, and a clearly-marked MOCK `generate_answer()`
  function standing in for a real LLM API call -- demonstrates the full
  retrieve -> augment -> generate RAG loop with zero API keys or network
  access.

## Pip installs needed

```bash
pip install numpy pillow scikit-learn
```

(`nltk` is optional for script 02 -- it will auto-detect and use nltk's
stopword corpus if already downloaded, otherwise it falls back to a small
built-in stopword list, so `nltk` is not required.)

## No external API access required

**All four scripts run fully offline with no API keys, no network calls,
and no paid services.** Specifically:

- Scripts 01-03 use only local, deterministic algorithms (search, classic
  NLP preprocessing + a locally-trained tiny classifier, and pixel-level
  image processing) -- there was never an external API involved.
- Script 04 demonstrates the RAG *pattern* end-to-end (retrieval via local
  TF-IDF/cosine-similarity math, then prompt augmentation) but swaps in a
  mock `generate_answer()` function in place of a real LLM call, with an
  inline comment showing exactly where a real API call (e.g. to Anthropic's
  Claude) would go.

## Running any script

```bash
python 0N_<script_name>.py
```

Run from inside this `Practical` folder (or use the full path) with your
Python environment activated after installing the packages above.
