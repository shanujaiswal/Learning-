# Why This File Exists

--> The Generative AI file covered prompting a model for a single response and RAG for grounding that response in retrieved documents -- but it never covered what happens when an LLM needs to take MULTIPLE steps, use TOOLS, and decide its own next action -- that's the subject of AI agents, covered first below. This file then covers how LLMs get evaluated rigorously (beyond just "the output looks reasonable"), the mechanics of tokenization and context windows that quietly constrain everything an LLM does, how a large pretrained model gets efficiently adapted without retraining it entirely, and closes with a brief look at speech-based AI.

# AI Agents -- From Single Responses to Multi-Step Action

--> Everything in the Generative AI file was fundamentally REACTIVE -- give the model a prompt, get back one response. An AI Agent instead uses an LLM as a decision-making CONTROLLER in a loop -- given a goal, it decides what ACTION to take next (which might be calling a tool, searching for information, or writing code), observes the RESULT of that action, and decides the next action based on that result, repeating until the goal is achieved.

```
              ┌────────────────────────────────────────────┐
              │                                              │
              v                                              │
     ┌─────────────┐    action    ┌──────────────┐    result │
     │  LLM (the    │ -----------> │ Tool / API /  │ ---------┘
     │  "reasoning" │              │ Search / Code  │
     │  loop)       │ <----------- │ Execution       │
     └─────────────┘   observation └──────────────┘

This loop closely echoes the Reinforcement Learning agent-environment loop from
the Machine Learning folder's RL file -- an agent, an action, an observation/reward,
repeated -- except here the "policy" deciding each action is the LLM's reasoning itself,
not a learned Q-function or policy network.
```

--> **Tool Use** -- rather than answering purely from its own trained knowledge, an agent can be given access to external tools (a calculator, a web search API, a code interpreter, a database query function) that it decides to invoke when its own knowledge is insufficient or a real, current, precise answer is needed -- directly addressing the exact hallucination/frozen-knowledge concerns the Generative AI file raised, in a way that's more flexible than RAG's fixed retrieve-then-generate pattern, since the agent decides WHICH tool to use and WHEN, rather than always retrieving the same way.

```python
# Conceptual illustration of a tool definition an LLM agent can choose to call
tools = [
    {
        "name": "get_current_weather",
        "description": "Get the current weather for a given city",
        "parameters": {"city": "string"},
    }
]

# The LLM, given a user question like "should I bring an umbrella in Chicago today?",
# decides on its own that it needs current data it doesn't have, and outputs a
# structured request to call get_current_weather(city="Chicago") rather than guessing
```

--> **The ReAct Pattern (Reason + Act)** -- a widely used prompting/agent pattern where the model is explicitly prompted to alternate between writing out its REASONING ("I need to find X before I can answer this") and taking an ACTION (calling a tool), observing the result, and reasoning again -- making the model's intermediate decision-making explicit and inspectable, rather than a single opaque leap straight to a final answer, similar in spirit to how showing worked steps makes a human's reasoning checkable.

```
Thought: The user is asking for the population of a city I don't have memorized reliably. I should search for it.
Action: search("current population of Austin, Texas")
Observation: "Austin's population is approximately 980,000 as of the most recent estimate."
Thought: I now have the information needed to answer.
Answer: Austin's population is approximately 980,000.
```

--> **Multi-step planning** -- more complex goals ("plan a 3-day trip itinerary") require an agent to break the goal into SUB-TASKS, work through them (possibly using different tools for each), and assemble the results into a final coherent output -- a genuinely harder problem than single-tool-call agents, since errors or bad assumptions early in a long plan can compound across every later step.
--> **Agent frameworks (LangChain, and similar)** -- rather than every developer hand-writing this reasoning-observation-action loop, act-tool-definition boilerplate, and conversation memory management from scratch, frameworks like LangChain provide these as reusable building blocks -- conceptually the same value proposition as a web framework (covered in the Full Stack track) providing reusable routing/middleware instead of everyone hand-rolling their own HTTP server logic, just applied to LLM-orchestration instead of web requests.
--> **The security angle** -- an agent that can execute code or call arbitrary tools based on text it reads is directly exposed to the Prompt Injection risk the Generative AI file's Ethics section raised -- malicious instructions hidden in a retrieved webpage or document can hijack an agent's next ACTION, not just its next sentence, making agent security a meaningfully higher-stakes concern than a simple chatbot's.

# LLM Evaluation Methods

--> Evaluating an LLM is genuinely harder than evaluating the Classification/Regression models in the Machine Learning folder, since there's rarely one single "correct" output to compare against -- an LLM's response can be one of many reasonable phrasings, making simple accuracy-style metrics insufficient on their own.

--> **Perplexity** -- a measure of how "surprised" a language model is by the actual next word in a piece of text, based on the probability it assigned to that word -- lower perplexity means the model consistently assigned higher probability to the words that actually appeared, a rough proxy for how well the model has learned the statistical patterns of language, though it doesn't directly measure whether outputs are actually HELPFUL, correct, or safe.

```
perplexity = 2^(average negative log-probability the model assigned to each actual next token)

(A model assigning high probability to what actually came next has LOW perplexity --
it wasn't very "surprised"; a model that keeps assigning low probability to what
actually appears has HIGH perplexity -- it was frequently "surprised")
```

--> **Benchmark suites** -- standardized sets of questions/tasks with known correct answers (e.g. MMLU for broad academic knowledge, HumanEval for code generation correctness) that let different models be compared on the SAME fixed tasks -- directly analogous to running the same test set (Model Evaluation file, Machine Learning folder) across different classifiers, just with tasks designed for the very different, more open-ended nature of language generation.
--> **LLM-as-Judge** -- rather than relying purely on rigid benchmark scoring, this approach uses ANOTHER (often more capable) LLM to read and score a candidate model's output against criteria like helpfulness, correctness, or tone -- practical at a scale human evaluation can't match, but it inherits its own risks (a judge model can share the same biases/blind spots as the model being judged, and can itself be gamed by outputs that superficially "look" high-quality to an LLM without actually being correct) -- typically used alongside, not as a full replacement for, targeted human evaluation for genuinely high-stakes conclusions.
--> **Human evaluation** -- despite the above automated methods, direct human judgment (rating outputs, comparing two candidate responses side by side) remains the ultimate ground truth for subjective qualities like tone, helpfulness, and genuine usefulness that benchmarks and even LLM-judges only approximate.

# Context Windows and Tokenization

--> An LLM does not process raw characters or whole words directly -- text is first broken into **tokens**, and the model has a fixed maximum number of tokens (the "context window") it can consider at once, both for the input prompt and its own generated output combined.

--> **Byte Pair Encoding (BPE)** -- the tokenization algorithm underlying most modern LLMs -- starts with individual characters as the smallest units, then repeatedly merges the MOST FREQUENTLY occurring adjacent pair of units in a huge training corpus into a single new unit, building up a vocabulary of common sub-word chunks (whole common words often become a single token, while rarer/longer words get split into a few sub-word pieces).

```
Starting point: individual characters -- "l", "o", "w", "e", "r"
Step 1: merge the most frequent adjacent pair across the training corpus, e.g. "e" + "r" --> "er"
Step 2: merge the next most frequent pair, e.g. "l" + "o" --> "lo"
... repeated thousands of times, until a fixed-size vocabulary of common sub-word chunks is built

Result: "lower" might tokenize as ["low", "er"] -- two tokens, not five characters
        and not necessarily one single "lower" token either, depending on the trained vocabulary
```

--> **Why this matters practically** -- token counts, not word or character counts, are what actually consume a model's context window and (for hosted APIs) determine cost -- a technical term or a word in a less-common language can silently consume several tokens where a common English word consumes one, and the roughly 3-4 characters-per-token rule of thumb for English is only a rough approximation, not exact.
--> **Context window limitations** -- once a conversation or a RAG-retrieved document set (Generative AI file) exceeds the context window, older content must be dropped, summarized, or otherwise managed -- directly explaining why long-running agent conversations and RAG systems over large document sets need explicit strategies (summarizing older turns, retrieving only the most relevant document chunks rather than entire documents) rather than simply feeding everything in.

```python
import tiktoken

encoding = tiktoken.get_encoding("cl100k_base")
tokens = encoding.encode("Tokenization mechanics matter more than people expect")
print(len(tokens))   # The actual token count, which is what counts against the context window -- not len(the string)
```

# Fine-Tuning Techniques -- LoRA, PEFT and Quantized Fine-Tuning

--> The Generative AI file distinguished pretraining from fine-tuning conceptually -- **full fine-tuning** (updating every single one of a model's billions of parameters) is accurate but extremely expensive in compute and memory, motivating a family of PARAMETER-EFFICIENT approaches that adapt a model's behavior while touching only a small fraction of its total parameters.

--> **PEFT (Parameter-Efficient Fine-Tuning)** -- the umbrella term for techniques that freeze the vast majority of a pretrained model's original weights and train only a small number of ADDITIONAL parameters layered on top -- dramatically cheaper in compute/memory/storage than full fine-tuning, and because the original weights stay frozen and untouched, the base model's broad general capability is far less likely to be damaged/forgotten in the process.
--> **LoRA (Low-Rank Adaptation)** -- the most widely used PEFT technique -- instead of updating a layer's full weight matrix directly, LoRA learns a much smaller pair of "low-rank" matrices whose product approximates the needed UPDATE to that weight matrix, then adds that small update to the frozen original weights at inference time -- since the low-rank matrices are far smaller than the full weight matrix they're adapting, training touches a tiny fraction of the total parameter count while still meaningfully changing the model's behavior for a specific task.

```python
from peft import LoraConfig, get_peft_model

lora_config = LoraConfig(
    r=8,                 # The "rank" -- controls how many trainable parameters LoRA adds; smaller = cheaper, less expressive
    lora_alpha=16,
    target_modules=["q_proj", "v_proj"],   # Which of the Transformer's attention weight matrices to adapt (Transformers file)
    lora_dropout=0.05,
)

model = get_peft_model(base_model, lora_config)
model.print_trainable_parameters()   # Typically well under 1% of the base model's total parameters
```

--> **Quantized Fine-Tuning (QLoRA)** -- combines LoRA with quantization (loading the huge frozen base model in reduced precision, e.g. 4-bit, directly connecting to the Model Compression concepts in the Deep Learning folder) -- makes it practical to fine-tune models with tens of billions of parameters on a single consumer-grade GPU, since the frozen base model's memory footprint shrinks dramatically while the small trainable LoRA matrices still train at higher precision for stability.
--> **When fine-tuning genuinely beats RAG/prompting alone** -- fine-tuning is the right tool specifically when a model needs to reliably adopt a particular STYLE, format, or specialized skill across many interactions (not just recall specific facts, which RAG already handles well) -- teams increasingly reach for RAG first (cheaper, easier to update, no retraining needed when facts change) and fine-tuning only once prompting and retrieval alone genuinely can't achieve the needed behavior.

# Speech Recognition and Synthesis

--> **ASR (Automatic Speech Recognition)** -- converts spoken audio into text -- modern systems (e.g. Whisper) are typically built on the same Transformer/attention architecture (Deep Learning folder) underlying text-based LLMs, just with audio waveforms (or their spectrogram representation) as input instead of text tokens.
--> **TTS (Text-to-Speech)** -- the reverse direction -- converts text into natural-sounding spoken audio, increasingly using the same Diffusion or Transformer-based generative approaches (Generative AI file) that power image/text generation, rather than the older, more robotic-sounding rule-based/concatenative speech synthesis approaches.
--> **Why this matters for agents** -- voice assistants and voice-driven agents chain ASR (converting the user's spoken request into text), the agent reasoning loop covered above (deciding what action to take), and TTS (speaking the final response back) together into one pipeline -- each stage inherits its own quality limitations (misheard words from ASR, unnatural-sounding output from TTS) on top of whatever limitations the underlying LLM reasoning itself has.

# Where This Leaves the Artificial Intelligence Folder

--> Agents, rigorous evaluation, and efficient fine-tuning are precisely the practical layer that turns a general-purpose pretrained LLM (Generative AI file) into a reliable, task-specific, deployable system -- and every one of these techniques still inherits the exact same Ethics concerns (hallucination, bias, prompt injection) that file raised, now operating across multiple autonomous steps and tool calls instead of a single response, which is exactly why the MLOps folder's monitoring and deployment discipline applies just as much to agentic AI systems as to the more traditional ML models covered earlier in this section.
