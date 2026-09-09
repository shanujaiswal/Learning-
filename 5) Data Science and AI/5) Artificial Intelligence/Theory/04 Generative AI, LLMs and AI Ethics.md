# Generative vs Discriminative AI

--> Everything covered so far in the Machine Learning/Deep Learning folders is largely DISCRIMINATIVE -- classifying or predicting a label/value FROM input data (is this email spam? what's this house worth?). Generative AI instead CREATES new content -- text, images, audio, code -- that resembles its training data, without simply copying any single training example.

# Large Language Models (LLMs)

--> Built on the Transformer architecture (covered in the Deep Learning folder), trained on massive text datasets with a deceptively simple objective -- predict the next word (or "token") given everything before it -- repeated at a scale of billions of parameters and vast amounts of text.
--> At sufficient scale, this simple next-token-prediction objective produces models capable of far more than simple text completion -- summarization, translation, question-answering, and code generation all emerge from the same underlying training process, without being explicitly, separately trained for each task.

```python
from transformers import pipeline

generator = pipeline("text-generation", model="gpt2")
output = generator("The future of artificial intelligence is", max_length=50)
```

# How LLMs Are Actually Built -- Pretraining and Fine-Tuning

--> **Pretraining** -- the massive, expensive phase of training on a huge, general corpus of text, learning broad language patterns and world knowledge.
--> **Fine-tuning** -- a much smaller, cheaper additional training phase adapting the pretrained model to a specific task or desired behavior (e.g. following instructions helpfully, adopting a specific tone) -- most usable, deployed LLMs are fine-tuned versions of a larger pretrained base model, not the raw pretrained model itself.
--> **RLHF (Reinforcement Learning from Human Feedback)** -- a fine-tuning technique where human raters rank different model outputs, and that feedback trains the model to produce outputs humans actually prefer -- a major factor in why modern assistant-style LLMs behave noticeably more helpfully/appropriately than earlier raw language models.

# Prompt Engineering

--> Because LLMs are so general-purpose, HOW you phrase a request ("prompt") significantly affects the quality of the output -- providing clear context, examples, and explicit instructions ("few-shot prompting" -- showing the model a couple of examples of the desired input/output pattern before the actual request) reliably improves results without any model retraining at all.

# Retrieval-Augmented Generation (RAG)

--> LLMs' knowledge is frozen at whenever their training data was collected, and they can "hallucinate" (state incorrect information confidently) -- RAG addresses this by first RETRIEVING relevant, up-to-date, or private documents (often using vector similarity search over embeddings, connecting to the NLP file's embedding concepts) and feeding them into the model's context alongside the actual question, grounding its answer in real, current, verifiable source material.

# Generative Models for Images

--> **GANs (Generative Adversarial Networks)** -- two networks compete -- a Generator tries to create convincing fake images, a Discriminator tries to distinguish fake from real, and both improve together through this adversarial process.
--> **Diffusion Models** -- the architecture behind most modern image generation tools (Stable Diffusion, DALL-E) -- trained to progressively remove noise from a random starting image, guided by a text description, gradually "revealing" a coherent image matching the prompt.

# AI Ethics -- The Responsibility Layer

--> **Hallucination and misinformation** -- an LLM can generate confident-sounding but factually WRONG content -- a genuine risk requiring human verification for any consequential use, especially without the grounding RAG provides.
--> **Bias** -- models trained on real-world data (containing real-world human biases) can learn and reproduce those biases -- e.g. a hiring-screening model trained on historically biased hiring data can perpetuate that same bias at scale, and this exact risk is why fairness auditing of training data and model outputs is an active, essential part of responsible model development.
--> **Data privacy** -- training data may include personal or copyrighted content scraped from the internet, raising both privacy concerns (connecting to the Privacy Engineering file in the Cyber Security track) and ongoing legal/copyright questions the field is still actively working through.
--> **Deepfakes and misuse** -- generative image/video/audio models can create convincing fake content of real people -- a direct, serious extension of the social engineering risks covered in the Ethical Hacking track, now amplified by generative capability.
--> **Prompt injection** -- a security-specific AI risk (directly connecting to the injection-attack concepts covered in the OWASP Top 10 file in the Ethical Hacking track) where malicious instructions hidden in input data (a document, a webpage an AI agent reads) can hijack an LLM's intended behavior.
--> **Explainability** -- deep learning models are frequently "black boxes" -- for high-stakes decisions (loan approvals, medical diagnoses), understanding WHY a model made a specific decision matters as much as the decision's accuracy, an active research area (explainable AI / XAI) without a fully solved general answer yet.

# Responsible Deployment as an Ongoing Discipline

--> None of these ethical concerns are solved once and forgotten -- responsible AI deployment requires ongoing monitoring, bias auditing, clear disclosure to users that they're interacting with AI, and human oversight for consequential decisions, treated as a continuous discipline rather than a one-time checklist, echoing the continuous-monitoring philosophy already established in the Cyber Security track's Security Program Management file.
