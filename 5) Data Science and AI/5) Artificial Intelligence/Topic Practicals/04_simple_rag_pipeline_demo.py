"""
04 - Simple RAG Pipeline Demo (Generative AI / LLMs / AI Ethics chapter)
==========================================================================
A genuinely self-contained Retrieval-Augmented Generation (RAG) demo.
No API keys, no network calls, no external embedding service required.

Pipeline stages:
    1. Document store   -> a small in-memory list of short text "documents"
    2. Retrieval         -> TF-IDF vectorization + cosine similarity to find
                             the document(s) most relevant to a user query
                             (scikit-learn TfidfVectorizer, purely local math)
    3. Augmentation      -> build a prompt that stuffs the retrieved
                             document(s) as "context" alongside the question
    4. Generation        -> a MOCK generate_answer() function stands in for
                             a real LLM API call (e.g. Anthropic's Claude).
                             This is the ONLY spot where you would swap in a
                             real API call -- clearly marked below.

This demonstrates the full RAG concept end-to-end: retrieve -> augment ->
generate, using nothing but scikit-learn and numpy.

Install:
    pip install scikit-learn numpy

Run:
    python 04_simple_rag_pipeline_demo.py
"""

from __future__ import annotations

from typing import List, Tuple

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

# ---------------------------------------------------------------------------
# 1. Document store (in-memory "knowledge base")
# ---------------------------------------------------------------------------
# In a real system this would be a vector database (e.g. Pinecone, Chroma,
# FAISS) holding thousands/millions of chunks. Here it's just a Python list
# of short strings so the whole demo runs with zero external dependencies.
DOCUMENT_STORE: List[str] = [
    "The Eiffel Tower is a wrought-iron lattice tower in Paris, France. "
    "It was completed in 1889 and was the tallest man-made structure in the "
    "world for 41 years.",

    "Python is a high-level, interpreted programming language known for its "
    "readability. It was created by Guido van Rossum and first released in 1991.",

    "Photosynthesis is the process by which green plants and some other "
    "organisms use sunlight to synthesize nutrients from carbon dioxide and water.",

    "The Great Wall of China is a series of fortifications built across the "
    "historical northern borders of China to protect against invasions.",

    "Retrieval-Augmented Generation (RAG) is a technique that combines an "
    "information retrieval step with a text generation model, grounding the "
    "model's output in retrieved facts rather than relying purely on its "
    "parametric memory.",

    "Mount Everest, located in the Mahalangur Himal sub-range of the "
    "Himalayas, is Earth's highest mountain above sea level.",

    "A transformer is a deep learning architecture introduced in the paper "
    "'Attention Is All You Need'. It relies on self-attention and underlies "
    "most modern large language models.",

    "The mitochondrion is a double-membrane-bound organelle found in most "
    "eukaryotic cells, often called the powerhouse of the cell because it "
    "generates most of the cell's ATP.",
]


# ---------------------------------------------------------------------------
# 2. Retrieval: TF-IDF vectorization + cosine similarity
# ---------------------------------------------------------------------------
class SimpleRetriever:
    """A minimal local retriever: TF-IDF vectors + cosine similarity.

    This plays the role of an "embedding model + vector search" in a real
    RAG system, except everything happens locally with classic bag-of-words
    statistics instead of a neural embedding API.
    """

    def __init__(self, documents: List[str]) -> None:
        self.documents = documents
        self.vectorizer = TfidfVectorizer(stop_words="english")
        # Fit the vectorizer on the whole document store and cache the matrix.
        self.doc_matrix = self.vectorizer.fit_transform(documents)

    def retrieve(self, query: str, top_k: int = 2) -> List[Tuple[str, float]]:
        """Return the top_k (document, similarity_score) pairs for a query."""
        query_vec = self.vectorizer.transform([query])
        similarities = cosine_similarity(query_vec, self.doc_matrix).flatten()

        ranked_indices = np.argsort(similarities)[::-1][:top_k]
        return [(self.documents[i], float(similarities[i])) for i in ranked_indices]


# ---------------------------------------------------------------------------
# 3. Augmentation: build the prompt that would be sent to an LLM
# ---------------------------------------------------------------------------
def build_augmented_prompt(query: str, retrieved_docs: List[Tuple[str, float]]) -> str:
    """Stuff retrieved context + the original question into a single prompt,
    exactly the "augmentation" step in Retrieval-Augmented Generation."""
    context_block = "\n\n".join(
        f"[Context {i + 1} | similarity={score:.3f}]\n{doc}"
        for i, (doc, score) in enumerate(retrieved_docs)
    )

    prompt = (
        "You are a helpful assistant. Answer the question using ONLY the "
        "context below. If the context does not contain the answer, say "
        "you don't know.\n\n"
        f"{context_block}\n\n"
        f"Question: {query}\n"
        "Answer:"
    )
    return prompt


# ---------------------------------------------------------------------------
# 4. Generation: MOCK LLM call
# ---------------------------------------------------------------------------
def generate_answer(prompt: str) -> str:
    """MOCK stand-in for a real generative model call.

    ------------------------------------------------------------------
    THIS is the exact spot where a real RAG pipeline would call an LLM.
    A real implementation would replace the body of this function with,
    e.g., a call to Anthropic's Claude API:

        import anthropic
        client = anthropic.Anthropic(api_key="...")
        response = client.messages.create(
            model="claude-sonnet-4-5",
            max_tokens=300,
            messages=[{"role": "user", "content": prompt}],
        )
        return response.content[0].text

    No API key or network access is used here -- instead we return a
    canned, clearly-labeled placeholder string so the whole pipeline stays
    runnable offline and demonstrates the RAG *structure*, not real
    generation quality.
    ------------------------------------------------------------------
    """
    return (
        "[MOCK LLM OUTPUT -- replace generate_answer() with a real API call]\n"
        "Based on the provided context, here is a placeholder answer. "
        "In a real system, the text above the 'Question:' line would be sent "
        "to a language model, which would generate a grounded answer using "
        "only the retrieved context."
    )


# ---------------------------------------------------------------------------
# 5. Run the full RAG pipeline on a few example queries
# ---------------------------------------------------------------------------
def run_rag_query(retriever: SimpleRetriever, query: str, top_k: int = 2) -> None:
    print(f"Query: {query}")

    retrieved = retriever.retrieve(query, top_k=top_k)
    print("\nRetrieved context (most relevant first):")
    for rank, (doc, score) in enumerate(retrieved, start=1):
        print(f"  {rank}. (similarity={score:.3f}) {doc[:90]}...")

    prompt = build_augmented_prompt(query, retrieved)
    print("\n--- Augmented prompt sent to the (mock) LLM ---")
    print(prompt)

    answer = generate_answer(prompt)
    print("\n--- Generated answer ---")
    print(answer)
    print("\n" + "=" * 78 + "\n")


def main() -> None:
    print("=== Simple RAG (Retrieval-Augmented Generation) Pipeline Demo ===\n")
    print(f"Document store contains {len(DOCUMENT_STORE)} short documents.\n")

    retriever = SimpleRetriever(DOCUMENT_STORE)

    queries = [
        "How tall is the Eiffel Tower and when was it built?",
        "What is a transformer model used for in AI?",
        "How do plants make energy from sunlight?",
    ]

    for query in queries:
        run_rag_query(retriever, query, top_k=2)

    print(
        "Pipeline summary:\n"
        "  1. Retrieval  -> TF-IDF + cosine similarity picks the most relevant\n"
        "                   document(s) from the in-memory store for each query.\n"
        "  2. Augmentation -> retrieved documents are concatenated with the\n"
        "                     question into a single prompt.\n"
        "  3. Generation -> generate_answer() is a MOCK function; swap its body\n"
        "                   for a real LLM API call (e.g. Anthropic Claude,\n"
        "                   OpenAI, etc.) to get real generated answers.\n"
        "\nThis is the core RAG idea: ground an LLM's output in retrieved facts\n"
        "instead of relying purely on what it memorized during training."
    )


if __name__ == "__main__":
    main()
