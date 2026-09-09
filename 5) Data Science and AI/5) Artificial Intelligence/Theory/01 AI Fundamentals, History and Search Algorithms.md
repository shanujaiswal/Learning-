# What "Artificial Intelligence" Actually Means

--> AI is the broad field of building systems that perform tasks normally requiring human intelligence -- reasoning, learning, perception, decision-making. Machine Learning and Deep Learning (covered in their own folders) are the DOMINANT current approach, but AI as a field is older and broader than "training models on data."

# A Brief History -- Two Competing Approaches

--> **Symbolic AI ("Good Old-Fashioned AI")** -- the dominant approach from the 1950s through the 1980s -- explicitly encoding human knowledge as logical rules and facts, then having the system REASON over them (e.g. expert systems that encoded a doctor's diagnostic rules directly as if/then logic).
--> **Connectionist AI** -- inspired by the brain's neurons, this approach (culminating in today's Deep Learning) LEARNS patterns from data rather than being explicitly programmed with rules -- largely dormant for decades due to insufficient data/compute, until both became available at scale starting roughly in the 2010s, at which point it became the dominant paradigm.
--> Today's AI field blends both -- pure symbolic reasoning still underlies areas like automated theorem proving and some planning systems, while learned/connectionist approaches dominate perception and pattern-recognition-heavy tasks (covered throughout the ML/Deep Learning folders).

# Search Algorithms -- AI's Classical Toolkit

--> Before (and alongside) modern ML, many AI problems were framed as SEARCH -- finding a path from a starting state to a goal state through a space of possible states/actions.

# Uninformed Search

--> **Breadth-First Search (BFS)** -- explores all neighbors at the current depth before going deeper -- guarantees the shortest path (in terms of number of steps) but can use a lot of memory.
--> **Depth-First Search (DFS)** -- explores as far as possible down one path before backtracking -- memory-efficient but doesn't guarantee the shortest path.
--> Both are "uninformed" -- they have no sense of which direction is actually promising, just a systematic way of exploring every possibility.

# Informed Search -- Using a Heuristic

--> A heuristic is a rule-of-thumb estimate of how close a given state is to the goal -- informed search algorithms use this to explore more PROMISING paths first, rather than blindly exploring everything.
--> **A\* Search** -- combines the actual cost traveled so far with a heuristic estimate of remaining cost, and expands the most promising path first -- the standard algorithm behind pathfinding in GPS navigation and many video games.

```
A* evaluates each candidate path by: f(n) = g(n) + h(n)
  g(n) = actual cost so far to reach state n
  h(n) = heuristic estimate of remaining cost from n to the goal
```

# Adversarial Search -- Game Playing

--> **Minimax algorithm** -- used in two-player games (chess, tic-tac-toe) -- assumes the opponent will always play optimally against you, and searches for the move that minimizes your worst-case outcome (maximizing your minimum guaranteed result).
--> **Alpha-Beta Pruning** -- an optimization to Minimax that skips exploring branches that can't possibly influence the final decision, dramatically reducing the search space without changing the final result.
--> Modern game-playing AI (like AlphaGo, referenced in the Reinforcement Learning discussion in the ML Fundamentals file) COMBINES this classical search approach with deep learning -- a neural network evaluates board positions (replacing hand-crafted heuristics), while search still explores possible future moves, illustrating exactly how classical and learned AI approaches genuinely complement each other in practice.

# Knowledge Representation and Reasoning

--> Beyond search, classical AI also developed formal ways to represent facts and relationships (semantic networks, ontologies, formal logic) and reason over them -- this lineage still underlies modern knowledge graphs (used by search engines and some recommendation systems) even as most day-to-day AI applications today rely primarily on the learned approaches covered in the following files.
