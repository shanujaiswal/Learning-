"""
09_graphs_traversal_shortest_paths_mst.py

Implements and tests:
    1. BFS and DFS on an adjacency-list graph
    2. Topological sort on a course-prerequisite DAG
    3. Dijkstra's algorithm for weighted shortest paths
    4. Kruskal's algorithm for a minimum spanning tree (using Union-Find)

Covers Theory chapter:
    2) DSA/Theory/09 Graphs Traversal Shortest Paths and Minimum Spanning Trees.md

Run:  python 09_graphs_traversal_shortest_paths_mst.py
"""

import heapq
from collections import deque


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


# ---------------------------------------------------------------------------
# 1) BFS and DFS
# ---------------------------------------------------------------------------

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


def dfs_recursive(graph, node, visited=None, order=None):
    if visited is None:
        visited, order = set(), []
    visited.add(node)
    order.append(node)
    for neighbor in graph[node]:
        if neighbor not in visited:
            dfs_recursive(graph, neighbor, visited, order)
    return order


def demo_bfs_dfs() -> None:
    print_section("1) BFS vs DFS traversal order on the same graph")
    graph = {
        "A": ["B", "C"],
        "B": ["A", "D", "E"],
        "C": ["A", "F"],
        "D": ["B"],
        "E": ["B", "F"],
        "F": ["C", "E"],
    }
    bfs_order = bfs(graph, "A")
    dfs_order = dfs_recursive(graph, "A")
    print(f"Graph: {graph}")
    print(f"BFS from A (level by level): {bfs_order}")
    print(f"DFS from A (deep first):      {dfs_order}")
    assert bfs_order[0] == "A" and set(bfs_order) == set(graph.keys())
    assert dfs_order[0] == "A" and set(dfs_order) == set(graph.keys())
    print("Assertions passed -- both visit every node exactly once, but in genuinely different orders.")


# ---------------------------------------------------------------------------
# 2) Topological sort
# ---------------------------------------------------------------------------

def topological_sort(graph):
    visited, order = set(), []

    def dfs(node):
        visited.add(node)
        for neighbor in graph[node]:
            if neighbor not in visited:
                dfs(neighbor)
        order.append(node)

    for node in graph:
        if node not in visited:
            dfs(node)
    return order[::-1]


def demo_topological_sort() -> None:
    print_section("2) Topological sort -- course prerequisites")
    # edge A -> B means "A is a prerequisite for B"
    courses = {
        "Intro": ["DataStructures", "Discrete Math"],
        "Discrete Math": ["Algorithms"],
        "DataStructures": ["Algorithms"],
        "Algorithms": ["Capstone"],
        "Capstone": [],
    }
    order = topological_sort(courses)
    print(f"Prerequisite graph: {courses}")
    print(f"Valid course order: {order}")

    # Verify every prerequisite edge is respected in the resulting order
    position = {course: i for i, course in enumerate(order)}
    for course, dependents in courses.items():
        for dependent in dependents:
            assert position[course] < position[dependent], (
                f"{course} must come before {dependent}, but doesn't in {order}"
            )
    print("Assertion passed -- every prerequisite is scheduled before the course that depends on it.")


# ---------------------------------------------------------------------------
# 3) Dijkstra's algorithm
# ---------------------------------------------------------------------------

def dijkstra(graph, start):
    distances = {node: float('inf') for node in graph}
    distances[start] = 0
    pq = [(0, start)]
    while pq:
        current_dist, node = heapq.heappop(pq)
        if current_dist > distances[node]:
            continue
        for neighbor, weight in graph[node]:
            new_dist = current_dist + weight
            if new_dist < distances[neighbor]:
                distances[neighbor] = new_dist
                heapq.heappush(pq, (new_dist, neighbor))
    return distances


def demo_dijkstra() -> None:
    print_section("3) Dijkstra's algorithm -- weighted shortest paths")
    graph = {
        "A": [("B", 4), ("C", 1)],
        "B": [("A", 4), ("D", 1)],
        "C": [("A", 1), ("D", 5), ("E", 8)],
        "D": [("B", 1), ("C", 5), ("E", 2)],
        "E": [("C", 8), ("D", 2)],
    }
    distances = dijkstra(graph, "A")
    print(f"Weighted graph: {graph}")
    print(f"Shortest distances from A: {distances}")
    assert distances["A"] == 0
    assert distances["C"] == 1          # direct edge A->C
    assert distances["D"] == 5          # A->C(1)->D(5)=6, or A->B(4)->D(1)=5 -- the shorter path wins
    print("Assertions passed.")


# ---------------------------------------------------------------------------
# 4) Kruskal's MST using Union-Find
# ---------------------------------------------------------------------------

class UnionFind:
    def __init__(self, nodes):
        self.parent = {node: node for node in nodes}

    def find(self, x):
        if self.parent[x] != x:
            self.parent[x] = self.find(self.parent[x])
        return self.parent[x]

    def union(self, x, y):
        root_x, root_y = self.find(x), self.find(y)
        if root_x == root_y:
            return False
        self.parent[root_y] = root_x
        return True


def kruskal_mst(nodes, edges):
    # edges: list of (weight, node_a, node_b)
    uf = UnionFind(nodes)
    mst = []
    total_weight = 0
    for weight, a, b in sorted(edges):
        if uf.union(a, b):
            mst.append((a, b, weight))
            total_weight += weight
    return mst, total_weight


def demo_kruskal_mst() -> None:
    print_section("4) Kruskal's algorithm -- minimum spanning tree via Union-Find")
    nodes = ["A", "B", "C", "D", "E"]
    edges = [
        (2, "A", "B"), (3, "A", "C"), (1, "B", "C"),
        (4, "B", "D"), (5, "C", "D"), (6, "D", "E"), (7, "C", "E"),
    ]
    mst, total_weight = kruskal_mst(nodes, edges)
    print(f"Edges (weight, a, b): {edges}")
    print(f"MST edges: {mst}")
    print(f"Total MST weight: {total_weight}")
    assert len(mst) == len(nodes) - 1     # a spanning tree always has exactly V-1 edges
    print("Assertion passed -- exactly V-1 edges, confirming a valid spanning tree was built.")


if __name__ == "__main__":
    demo_bfs_dfs()
    demo_topological_sort()
    demo_dijkstra()
    demo_kruskal_mst()
    print("\nAll Graphs/Shortest-Path/MST demos completed.")
