# Graph Terminology and Representation

--> A graph is a set of NODES (vertices) connected by EDGES -- more general than a tree (covered in the Trees file), since a graph allows cycles, disconnected components, and nodes with multiple parents, none of which a tree permits.
--> **Directed vs undirected** -- a directed edge only allows travel one way (`A -> B` doesn't imply `B -> A`, e.g. a one-way street, a "follows" relationship); an undirected edge allows travel both ways (e.g. a friendship, a physical road).
--> **Weighted vs unweighted** -- a weighted edge carries a numeric cost (distance, time, price); an unweighted edge is just a plain connection, effectively a weight of 1 everywhere.

# Adjacency List vs Adjacency Matrix

```python
# Adjacency list -- a dict mapping each node to a list of its neighbors
graph_list = {
    "A": ["B", "C"],
    "B": ["A", "D"],
    "C": ["A", "D"],
    "D": ["B", "C"]
}

# Adjacency matrix -- an n x n grid, 1 if an edge exists between row-node and column-node, else 0
#      A  B  C  D
# A  [ 0, 1, 1, 0 ]
# B  [ 1, 0, 0, 1 ]
# C  [ 1, 0, 0, 1 ]
# D  [ 0, 1, 1, 0 ]
```

```text
                  Adjacency List              Adjacency Matrix
Space               O(V + E)                     O(V^2)
Check if edge exists  O(degree of node)            O(1)
Iterate all neighbors  O(degree of node)            O(V) -- must scan the whole row
```

--> **Practical guidance** -- an adjacency list is the standard default for most real-world graphs, since graphs are usually SPARSE (far fewer edges than the `V^2` maximum possible) -- an adjacency matrix wastes enormous space storing mostly zeros on a sparse graph, but wins when the graph is DENSE (edges close to the `V^2` maximum) or when "does this specific edge exist" needs to be checked extremely frequently in `O(1)`.

# Breadth-First Search (BFS)

--> Explores the graph LEVEL BY LEVEL outward from a starting node, using a QUEUE (directly connecting to the Queues section of the Linked Lists/Stacks/Queues file) -- guarantees finding the SHORTEST PATH in terms of number of edges on an UNWEIGHTED graph, since it fully explores every node at distance 1 before moving on to distance 2, and so on.

```python
from collections import deque

def bfs(graph, start):
    visited = {start}
    queue = deque([start])
    order = []
    while queue:
        node = queue.popleft()
        order.append(node)
        for neighbor in graph[node]:
            if neighbor not in visited:
                visited.add(neighbor)
                queue.append(neighbor)
    return order
```

--> **Why the visited set is non-negotiable** -- without it, a graph with a cycle would cause the traversal to loop forever, re-visiting the same nodes indefinitely -- unlike a tree (which has no cycles by definition), a general graph traversal ALWAYS needs explicit cycle protection.

# Depth-First Search (DFS)

--> Explores as DEEP as possible down one path before backtracking -- implemented either with explicit recursion (using the language's own call stack, directly connecting to the Recursion file) or an explicit stack to simulate the same behavior iteratively.

```python
def dfs_recursive(graph, node, visited=None):
    if visited is None:
        visited = set()
    visited.add(node)
    order = [node]
    for neighbor in graph[node]:
        if neighbor not in visited:
            order.extend(dfs_recursive(graph, neighbor, visited))
    return order

def dfs_iterative(graph, start):
    visited = {start}
    stack = [start]
    order = []
    while stack:
        node = stack.pop()
        order.append(node)
        for neighbor in graph[node]:
            if neighbor not in visited:
                visited.add(neighbor)
                stack.append(neighbor)
    return order
```

--> **BFS vs DFS -- when each is the right tool** -- BFS for shortest path on unweighted graphs, or anything genuinely "level by level" (e.g. finding everyone within 2 degrees of connection in a social network); DFS for exploring every possibility down a path before giving up on it (cycle detection, topological sort below, maze-solving, and any problem with the "explore fully, backtrack if stuck" shape shared with the Backtracking file).

# Topological Sort -- Ordering a Directed Acyclic Graph (DAG)

--> Produces a linear ordering of nodes such that for every directed edge `A -> B`, `A` comes BEFORE `B` in the ordering -- only possible on a DAG (a directed graph with NO cycles; a cycle would create a contradiction -- something needing to come both before and after itself).

```python
def topological_sort(graph):
    visited, order = set(), []

    def dfs(node):
        visited.add(node)
        for neighbor in graph[node]:
            if neighbor not in visited:
                dfs(neighbor)
        order.append(node)          # append AFTER exploring all neighbors -- this is the key step

    for node in graph:
        if node not in visited:
            dfs(node)
    return order[::-1]                # reverse -- nodes finished last actually belong first in the order
```

--> **Canonical real-world use cases** -- course prerequisites (can't take Course B before Course A if A is a prerequisite), build systems / task scheduling (compile module A before module B if B depends on A), and the Database Migration Tooling concept covered in the Database notes (migrations must apply in an order respecting their dependencies).

# Dijkstra's Algorithm -- Shortest Path on Weighted Graphs (Non-Negative Weights)

--> BFS finds the shortest path by EDGE COUNT on an unweighted graph -- Dijkstra's generalizes this to find the shortest path by TOTAL WEIGHT on a weighted graph, using a priority queue/min-heap (covered in the Heaps file) to always process the closest known unvisited node next.

```python
import heapq

def dijkstra(graph, start):        # graph: {node: [(neighbor, weight), ...]}
    distances = {node: float('inf') for node in graph}
    distances[start] = 0
    pq = [(0, start)]                # (distance, node)
    while pq:
        current_dist, node = heapq.heappop(pq)
        if current_dist > distances[node]:
            continue                  # a shorter path to this node was already found -- skip stale entry
        for neighbor, weight in graph[node]:
            new_dist = current_dist + weight
            if new_dist < distances[neighbor]:
                distances[neighbor] = new_dist
                heapq.heappush(pq, (new_dist, neighbor))
    return distances
```

--> **Why it requires non-negative weights** -- Dijkstra's greedily FINALIZES a node's shortest distance the moment it's popped from the priority queue, assuming no later-discovered path could ever be shorter -- a negative edge weight could violate that assumption (a path that looks longer right now might become shorter later by using a negative edge), which is exactly why Dijkstra's gives WRONG answers on graphs with negative weights, and why the Bellman-Ford algorithm (which tolerates negative weights, at the cost of `O(V*E)` instead of Dijkstra's `O(E log V)`) exists as the alternative when negative weights are possible.

# Minimum Spanning Tree (MST) -- Prim's and Kruskal's

--> A spanning tree connects EVERY node in a graph using the FEWEST possible edges (exactly `V - 1` edges for `V` nodes) with no cycles -- a MINIMUM spanning tree is the spanning tree with the smallest possible TOTAL edge weight, among all possible spanning trees of a weighted graph.
--> **Canonical real-world use case** -- designing a network (roads, pipelines, network cabling) connecting every required location while minimizing total cost, directly connecting to the Database Replication and Sharding file's network topology concerns at a conceptual level.

--> **Prim's algorithm** -- grows the MST one node at a time, always adding the CHEAPEST edge that connects a node already in the tree to a node not yet in it -- structurally very similar to Dijkstra's (uses a priority queue, greedily picks the locally cheapest option), but optimizes for cheapest EDGE rather than shortest cumulative PATH.
--> **Kruskal's algorithm** -- sorts ALL edges by weight, then greedily adds each edge (cheapest first) as long as it doesn't create a cycle -- cycle detection here is efficiently handled with a Union-Find/Disjoint Set structure (covered in the Tries and Advanced Data Structures file).
--> **Both are Greedy algorithms** (covered in the Dynamic Programming and Greedy file) -- each makes the locally cheapest choice at every step, and it's a genuinely non-obvious, provable fact (not something that holds for greedy strategies in general) that this local greediness happens to produce the GLOBALLY optimal minimum spanning tree for this specific problem.

# Deep Dive -- Cycle Detection, Directed vs Undirected

--> **Undirected graph** -- a cycle exists if, during DFS, you encounter an already-visited node that ISN'T the node you just came from (its direct parent in the traversal) -- revisiting your own immediate parent is normal in an undirected graph (the edge goes both ways) and doesn't indicate a cycle.
--> **Directed graph** -- requires tracking nodes currently "in progress" on the CURRENT recursion path specifically (not just ever-visited) -- encountering a node that's still in-progress on the current path indicates a genuine cycle; encountering a node that was fully finished and popped off the path earlier does NOT indicate a cycle, since directed edges only go one way.
--> This distinction is exactly why topological sort (above) can only exist for a directed graph with NO cycles -- the "in-progress path" cycle check is precisely the mechanism that would catch and reject an attempt to topologically sort a cyclic directed graph.
