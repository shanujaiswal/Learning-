# Bellman-Ford -- Shortest Path with Negative Weights

--> Dijkstra's (covered in the Graphs file) greedily finalizes a node's distance the moment it's popped and cannot handle negative edge weights -- Bellman-Ford instead RELAXES every edge repeatedly, `V - 1` times, tolerating negative weights at the cost of `O(V*E)` instead of Dijkstra's `O(E log V)`.
--> **Relaxation** -- for an edge `(u, v, weight)`, if `distance[u] + weight < distance[v]`, then a shorter path to `v` was just discovered through `u`, so `distance[v]` is updated -- this single operation, repeated enough times over enough edges, is the entire algorithm.
--> **Why exactly `V - 1` passes** -- the shortest path between any two nodes in a graph with `V` nodes uses at most `V - 1` edges (a path revisiting a node would contain a cycle, which is never beneficial to include unless the cycle is negative -- see below) -- so after `V - 1` full passes relaxing every edge, every shortest path has necessarily been found, since each pass guarantees the shortest path using at most one additional edge has been captured.

```python
def bellman_ford(vertices, edges, start):   # edges: [(u, v, weight), ...]
    distances = {v: float('inf') for v in vertices}
    distances[start] = 0

    for _ in range(len(vertices) - 1):        # relax every edge, V-1 times
        for u, v, weight in edges:
            if distances[u] != float('inf') and distances[u] + weight < distances[v]:
                distances[v] = distances[u] + weight

    # one extra pass -- if anything STILL improves, a negative cycle exists
    for u, v, weight in edges:
        if distances[u] != float('inf') and distances[u] + weight < distances[v]:
            return None, True    # (distances, has_negative_cycle)

    return distances, False
```

--> **Negative-cycle detection** -- a negative cycle (a cycle whose total weight sums to less than zero) means there's NO well-defined shortest path at all, since looping the cycle infinitely keeps reducing the total cost further and further -- the extra `V`-th pass exploits the `V - 1` guarantee above: if a distance can STILL be improved after `V - 1` passes should have already found every true shortest path, the only explanation is a negative cycle feeding it fresh (illegitimate) improvements forever.
--> **Practical guidance** -- use Dijkstra's whenever weights are guaranteed non-negative (it's faster); reach for Bellman-Ford specifically when negative weights are possible (e.g. currency arbitrage detection, where a negative cycle literally represents a profitable trading loop) or when negative-cycle detection itself is the goal.

# Floyd-Warshall -- All-Pairs Shortest Path

--> Bellman-Ford and Dijkstra's both solve shortest path from a SINGLE source -- Floyd-Warshall solves shortest path between EVERY pair of nodes simultaneously, in `O(V^3)`, using dynamic programming (directly connecting to the DP file's core idea of building answers from smaller subproblems).
--> **The state and recurrence** -- `dp[i][j]` is refined by asking, for every possible intermediate node `k`: "is going from `i` to `j` VIA `k` shorter than the best path found so far?" -- if `dp[i][k] + dp[k][j] < dp[i][j]`, then routing through `k` improves it.

```python
def floyd_warshall(n, edges):    # n nodes labeled 0..n-1, edges: [(u, v, weight), ...]
    INF = float('inf')
    dist = [[INF] * n for _ in range(n)]
    for i in range(n):
        dist[i][i] = 0
    for u, v, weight in edges:
        dist[u][v] = weight

    for k in range(n):              # try every node as an intermediate "waypoint"
        for i in range(n):
            for j in range(n):
                if dist[i][k] + dist[k][j] < dist[i][j]:
                    dist[i][j] = dist[i][k] + dist[k][j]
    return dist       # dist[i][j] = shortest distance from i to j, for ALL pairs
```

--> **Why the loop order matters** -- `k` (the intermediate node being tried) MUST be the outermost loop -- by the time the algorithm considers routing through `k`, every shorter path that itself routes through nodes `0..k-1` has already been folded into `dist`, which is exactly the optimal-substructure property the DP file describes.
--> **Floyd-Warshall vs running Bellman-Ford/Dijkstra's from every node** -- running a single-source algorithm from all `V` sources costs `O(V^2 * E)` (Bellman-Ford) or `O(V * E log V)` (Dijkstra's); Floyd-Warshall's flat `O(V^3)` is simpler to implement and actually wins on DENSE graphs (`E` close to `V^2`) precisely because it has no dependency on `E` at all, but loses badly on sparse graphs where `V^3` vastly exceeds `V * E log V`.

# A* Search -- Informed Pathfinding

--> A* generalizes Dijkstra's by adding a HEURISTIC -- an estimate of remaining distance to the goal -- so the search prioritizes nodes that seem likely to be on the way to the destination, rather than exploring uniformly outward in every direction like plain Dijkstra's.
--> **The scoring formula** -- each node is prioritized by `f(n) = g(n) + h(n)`, where `g(n)` is the actual cost from the start to `n` (exactly what Dijkstra's tracks), and `h(n)` is the heuristic's ESTIMATED cost from `n` to the goal (e.g. straight-line/Euclidean distance on a grid).

```python
import heapq

def a_star(graph, start, goal, heuristic):   # graph: {node: [(neighbor, weight), ...]}, heuristic: fn(node) -> estimate
    g_score = {start: 0}
    pq = [(heuristic(start), start)]           # (f_score, node)
    came_from = {}

    while pq:
        _, node = heapq.heappop(pq)
        if node == goal:
            path = [node]
            while node in came_from:
                node = came_from[node]
                path.append(node)
            return path[::-1]

        for neighbor, weight in graph[node]:
            tentative_g = g_score[node] + weight
            if tentative_g < g_score.get(neighbor, float('inf')):
                came_from[neighbor] = node
                g_score[neighbor] = tentative_g
                f_score = tentative_g + heuristic(neighbor)
                heapq.heappush(pq, (f_score, neighbor))
    return None    # no path found
```

--> **Admissibility** -- a heuristic must NEVER overestimate the true remaining distance for A* to guarantee the optimal path -- Euclidean distance is admissible for grid movement (the straight line is always the shortest possible path, so any real path is at least that long), while an overestimating heuristic could cause A* to skip over the actually-optimal path in favor of one that only looked cheaper.
--> **Why A* usually beats Dijkstra's in practice** -- Dijkstra's has no notion of "direction toward the goal" and explores equally in every direction (directly connecting to the Graphs file's BFS "level by level" framing, just weighted); A*'s heuristic actively steers the search toward the goal, often visiting far fewer nodes for the same correctness guarantee -- setting `h(n) = 0` for every node collapses A* back into exactly plain Dijkstra's, showing Dijkstra's is really just a special case of A* with no heuristic information at all.

# Network Flow -- Ford-Fulkerson and Edmonds-Karp

--> Models a graph as a network of PIPES -- each edge has a CAPACITY (maximum flow it can carry), and the goal is to push the maximum possible total flow from a SOURCE node to a SINK node without exceeding any edge's capacity.
--> **The core idea -- augmenting paths** -- repeatedly find any path from source to sink that still has spare capacity along every edge (an "augmenting path"), push as much flow as that path's bottleneck edge allows, then repeat until no augmenting path exists at all.
--> **The residual graph** -- after pushing flow along an edge, a "residual" reverse edge is added (or increased) representing the ability to UNDO that flow later -- this is what lets the algorithm correct an earlier suboptimal choice by effectively rerouting flow backward through a path that wouldn't otherwise exist in the original graph.

```python
from collections import deque, defaultdict

def bfs_find_path(capacity, source, sink):    # capacity: {u: {v: remaining_capacity}}
    parent = {source: None}
    queue = deque([source])
    while queue:
        u = queue.popleft()
        for v, cap in capacity[u].items():
            if v not in parent and cap > 0:
                parent[v] = u
                if v == sink:
                    path = []
                    node = sink
                    while node != source:
                        path.append((parent[node], node))
                        node = parent[node]
                    return path[::-1]
                queue.append(v)
    return None

def edmonds_karp(graph, source, sink):    # graph: {u: {v: capacity}}
    capacity = defaultdict(dict)
    for u in graph:
        for v, cap in graph[u].items():
            capacity[u][v] = capacity[u].get(v, 0) + cap
            capacity[v].setdefault(u, 0)          # residual reverse edge, starts at 0

    max_flow = 0
    path = bfs_find_path(capacity, source, sink)
    while path:
        bottleneck = min(capacity[u][v] for u, v in path)
        for u, v in path:
            capacity[u][v] -= bottleneck           # use up forward capacity
            capacity[v][u] += bottleneck            # grow the ability to undo this flow later
        max_flow += bottleneck
        path = bfs_find_path(capacity, source, sink)
    return max_flow
```

--> **Ford-Fulkerson vs Edmonds-Karp** -- "Ford-Fulkerson" is the general METHOD (find any augmenting path, push flow, repeat) and its complexity depends entirely on HOW the augmenting path is found; Edmonds-Karp is the specific, standard IMPLEMENTATION that always finds the augmenting path via BFS (directly connecting to the Graphs file's BFS, since using BFS specifically guarantees the SHORTEST augmenting path each time) -- this specific choice guarantees `O(V * E^2)`, whereas a poorly-chosen path-finding strategy in generic Ford-Fulkerson can pathologically require far more iterations.
--> **Max-flow min-cut theorem** -- the maximum possible flow from source to sink EXACTLY equals the minimum total capacity of any "cut" that separates source from sink (a cut being a set of edges whose removal disconnects the two) -- a genuinely non-obvious, proven duality, and the reason Ford-Fulkerson terminating with no augmenting path left is proof the flow found is truly maximum, not just locally stuck.
--> **Canonical real-world use cases** -- bipartite matching (assigning workers to jobs, students to schools) modeled as a flow problem, network bandwidth/traffic capacity planning, and image segmentation in computer vision.

# Articulation Points and Bridges

--> An ARTICULATION POINT (cut vertex) is a node whose removal disconnects the graph (or increases the number of connected components); a BRIDGE is the edge equivalent -- an edge whose removal disconnects the graph -- both identify the structurally "load-bearing" parts of a network.

```python
def find_articulation_points(graph, n):
    visited = [False] * n
    disc = [0] * n              # discovery time -- when DFS first visits this node
    low = [0] * n                # lowest discovery time reachable from this node's subtree
    ap = set()
    timer = [0]

    def dfs(u, parent):
        visited[u] = True
        disc[u] = low[u] = timer[0]
        timer[0] += 1
        child_count = 0

        for v in graph[u]:
            if v == parent:
                continue
            if visited[v]:
                low[u] = min(low[u], disc[v])       # back edge -- v was already reached another way
            else:
                child_count += 1
                dfs(v, u)
                low[u] = min(low[u], low[v])
                # u is an articulation point if some child subtree can't reach above u without u
                if parent is not None and low[v] >= disc[u]:
                    ap.add(u)

        if parent is None and child_count > 1:      # root is an articulation point only with 2+ subtrees
            ap.add(u)

    for node in range(n):
        if not visited[node]:
            dfs(node, None)
    return ap
```

--> **The `disc`/`low` mechanism, intuitively** -- `disc[u]` timestamps when a node was first reached; `low[u]` tracks the earliest-discovered node reachable from `u`'s entire subtree, including via "back edges" that jump up to an ancestor -- if a child's subtree has NO way to reach anything discovered before `u` itself (`low[v] >= disc[u]`), that subtree is only connected to the rest of the graph THROUGH `u`, making `u` load-bearing.
--> **Bridges use the identical `disc`/`low` DFS**, just checking `low[v] > disc[u]` (strictly greater, since a bridge is about the EDGE, not needing an alternate route back to `u` itself specifically) instead of the articulation point's `>=`.
--> **Canonical real-world use case** -- identifying single points of failure in a physical network (which single router or cable, if it went down, would partition the network) -- directly connecting to the Database Replication and Sharding file's concerns about avoiding single points of failure.

# Strongly Connected Components -- Tarjan's and Kosaraju's

--> A Strongly Connected Component (SCC) is a maximal set of nodes in a DIRECTED graph where every node can reach every other node in the set via directed edges -- undirected graphs don't need this concept, since "connected" already implies mutual reachability there.

--> **Kosaraju's algorithm** -- (1) run DFS on the original graph, recording finish order (identical idea to topological sort's finish-time trick in the Graphs file), (2) reverse every edge in the graph, (3) run DFS again on the REVERSED graph, processing nodes in REVERSE finish order from step 1 -- each DFS tree produced in step 3 is exactly one SCC.

```python
def kosaraju_scc(graph, n):
    def dfs1(u, visited, order):
        visited.add(u)
        for v in graph[u]:
            if v not in visited:
                dfs1(v, visited, order)
        order.append(u)                     # record finish order, same trick as topological sort

    def reverse_graph(graph):
        reversed_g = {u: [] for u in graph}
        for u in graph:
            for v in graph[u]:
                reversed_g[v].append(u)
        return reversed_g

    def dfs2(u, visited, component, reversed_g):
        visited.add(u)
        component.append(u)
        for v in reversed_g[u]:
            if v not in visited:
                dfs2(v, visited, component, reversed_g)

    visited, order = set(), []
    for node in graph:
        if node not in visited:
            dfs1(node, visited, order)

    reversed_g = reverse_graph(graph)
    visited = set()
    sccs = []
    for node in reversed(order):
        if node not in visited:
            component = []
            dfs2(node, visited, component, reversed_g)
            sccs.append(component)
    return sccs
```

--> **Why reversing the graph and using finish order works, intuitively** -- processing nodes in reverse finish order guarantees a node that can reach many other components gets processed FIRST; running DFS on the reversed graph from it then only reaches nodes that could ALSO reach it in the original graph (mutual reachability), which is exactly the SCC definition -- nodes in a different SCC that it could only reach one-way get cut off because the edges are now reversed.
--> **Tarjan's algorithm** -- a single-pass alternative using the same `disc`/`low` machinery as articulation points above, plus an explicit stack of "currently on the stack" nodes -- an SCC is identified in one DFS pass whenever a node's `low` value equals its own `disc` value, avoiding Kosaraju's need for a second full graph traversal, at the cost of a somewhat trickier implementation.
--> **Canonical real-world use case** -- condensing a directed graph into its SCCs collapses each SCC into a single node, producing a DAG -- exactly the graph shape topological sort (Graphs file) requires -- useful for analyzing dependency cycles among software modules or compiler optimization passes.

# Bipartite Graph Checking

--> A graph is BIPARTITE if its nodes can be split into two groups such that every edge connects a node in one group to a node in the OTHER group -- no edge ever connects two nodes within the same group.

```python
from collections import deque

def is_bipartite(graph):
    color = {}
    for start in graph:
        if start in color:
            continue
        color[start] = 0
        queue = deque([start])
        while queue:
            node = queue.popleft()
            for neighbor in graph[node]:
                if neighbor not in color:
                    color[neighbor] = 1 - color[node]     # opposite color from current node
                    queue.append(neighbor)
                elif color[neighbor] == color[node]:
                    return False                            # same color on both ends of an edge -- not bipartite
    return True
```

--> **Equivalent characterization** -- a graph is bipartite if and only if it contains NO odd-length cycle -- the BFS 2-coloring above is really just detecting exactly that, since alternating colors around any cycle only stays consistent if the cycle has even length.
--> **Canonical real-world use case** -- exactly the matching structure network flow's bipartite-matching use case relies on (workers vs jobs, students vs schools) -- checking bipartiteness is usually the first validation step before applying a matching algorithm to such a problem.

# Euler Paths/Circuits and Hamiltonian Paths (Briefly)

--> An EULER PATH visits every EDGE exactly once (nodes can repeat); an EULER CIRCUIT is an Euler path that also returns to its starting node -- existence is fully characterized: an undirected graph has an Euler circuit if and only if every node has EVEN degree and the graph is connected, and has an Euler path (not circuit) if and only if EXACTLY two nodes have odd degree.
--> **The Seven Bridges of Königsberg** -- the historical origin of graph theory itself -- Euler proved no walking route could cross each of the city's seven bridges exactly once, precisely because more than two landmasses had an odd number of bridges touching them.
--> A HAMILTONIAN PATH visits every NODE exactly once (edges may go unused) -- unlike Euler paths, there is NO known efficient characterization for when a Hamiltonian path exists, and determining one is NP-complete (previewed conceptually in the Math and Complexity Theory file) -- brute-force search or backtracking (Recursion file) is the practical fallback for small inputs, since no polynomial-time general algorithm is known to exist.

# Deep Dive -- Choosing Among These Algorithms

```text
Need                                                          Algorithm
Single-source shortest path, non-negative weights                Dijkstra's (Graphs file)
Single-source shortest path, negative weights allowed             Bellman-Ford
All-pairs shortest path                                            Floyd-Warshall
Single-source shortest path with a known "direction" to a goal     A* search
Maximum flow through a capacitated network                         Ford-Fulkerson / Edmonds-Karp
Single points of failure (nodes/edges)                              Articulation points / bridges
Groups of mutually-reachable nodes in a directed graph               Tarjan's / Kosaraju's SCC
"Can these nodes be split into two non-conflicting sides"            Bipartite check
Visit every edge exactly once                                        Euler path/circuit
Visit every node exactly once (NP-complete in general)               Hamiltonian path
```

--> **The general lesson** -- plain BFS/DFS/Dijkstra's from the Graphs file solve the common cases; this file's algorithms exist because real graphs come with extra constraints (negative weights, capacities, a need for ALL pairs, a need to find weak points) that the basic traversals were never designed to handle -- recognizing WHICH constraint is in play is most of the work of picking the right tool here.
