"""
01 - Search Algorithms & Pathfinding (AI Fundamentals / Search Algorithms chapter)
====================================================================================
Pure Python + numpy. No ML framework required.

Implements, from scratch, three classic search algorithms used to find a path
through a grid with obstacles:

    - BFS  (Breadth-First Search)   -> guarantees shortest path (unweighted)
    - DFS  (Depth-First Search)     -> explores deep first, no shortest-path guarantee
    - A*   (A-star)                 -> informed search using a heuristic, efficient +
                                        guarantees shortest path if heuristic is admissible

The grid is a simple 2D maze: 0 = free cell, 1 = obstacle/wall.
We compare the path each algorithm finds and how many nodes each explored,
which is exactly the kind of "search strategy" comparison covered in the
AI Fundamentals / History / Search Algorithms theory chapter.

Run:
    python 01_search_algorithms_pathfinding.py
"""

from __future__ import annotations

import heapq
from collections import deque
from typing import Callable, Dict, List, Optional, Tuple

import numpy as np

Cell = Tuple[int, int]

# ---------------------------------------------------------------------------
# 1. Define the maze
# ---------------------------------------------------------------------------
# 0 = walkable, 1 = wall/obstacle
MAZE_RAW = [
    [0, 0, 0, 0, 0, 1, 0, 0, 0, 0],
    [0, 1, 1, 0, 1, 1, 0, 1, 1, 0],
    [0, 1, 0, 0, 0, 1, 0, 0, 1, 0],
    [0, 1, 0, 1, 1, 1, 0, 1, 1, 0],
    [0, 0, 0, 1, 0, 0, 0, 0, 0, 0],
    [1, 1, 0, 1, 0, 1, 1, 1, 1, 0],
    [0, 0, 0, 0, 0, 1, 0, 0, 0, 0],
    [0, 1, 1, 1, 0, 0, 0, 1, 1, 0],
    [0, 0, 0, 1, 0, 1, 0, 0, 1, 0],
    [0, 1, 0, 0, 0, 1, 0, 1, 0, 0],
]

MAZE = np.array(MAZE_RAW, dtype=int)
ROWS, COLS = MAZE.shape

START: Cell = (0, 0)
GOAL: Cell = (9, 9)

# 4-directional movement: up, down, left, right
DIRECTIONS: List[Cell] = [(-1, 0), (1, 0), (0, -1), (0, 1)]


def neighbors(cell: Cell) -> List[Cell]:
    """Return walkable, in-bounds neighboring cells (4-connected)."""
    r, c = cell
    result = []
    for dr, dc in DIRECTIONS:
        nr, nc = r + dr, c + dc
        if 0 <= nr < ROWS and 0 <= nc < COLS and MAZE[nr, nc] == 0:
            result.append((nr, nc))
    return result


def reconstruct_path(came_from: Dict[Cell, Cell], start: Cell, goal: Cell) -> Optional[List[Cell]]:
    if goal not in came_from and goal != start:
        return None
    path = [goal]
    current = goal
    while current != start:
        current = came_from[current]
        path.append(current)
    path.reverse()
    return path


# ---------------------------------------------------------------------------
# 2. Breadth-First Search
# ---------------------------------------------------------------------------
def bfs(start: Cell, goal: Cell) -> Tuple[Optional[List[Cell]], int]:
    frontier = deque([start])
    came_from: Dict[Cell, Cell] = {}
    visited = {start}
    explored = 0

    while frontier:
        current = frontier.popleft()
        explored += 1
        if current == goal:
            break
        for nxt in neighbors(current):
            if nxt not in visited:
                visited.add(nxt)
                came_from[nxt] = current
                frontier.append(nxt)

    return reconstruct_path(came_from, start, goal), explored


# ---------------------------------------------------------------------------
# 3. Depth-First Search
# ---------------------------------------------------------------------------
def dfs(start: Cell, goal: Cell) -> Tuple[Optional[List[Cell]], int]:
    frontier = [start]  # stack
    came_from: Dict[Cell, Cell] = {}
    visited = {start}
    explored = 0

    while frontier:
        current = frontier.pop()
        explored += 1
        if current == goal:
            break
        for nxt in neighbors(current):
            if nxt not in visited:
                visited.add(nxt)
                came_from[nxt] = current
                frontier.append(nxt)

    return reconstruct_path(came_from, start, goal), explored


# ---------------------------------------------------------------------------
# 4. A* Search
# ---------------------------------------------------------------------------
def manhattan(a: Cell, b: Cell) -> int:
    return abs(a[0] - b[0]) + abs(a[1] - b[1])


def a_star(start: Cell, goal: Cell, heuristic: Callable[[Cell, Cell], float] = manhattan) -> Tuple[Optional[List[Cell]], int]:
    counter = 0  # tie-breaker for heap ordering
    open_heap: List[Tuple[float, int, Cell]] = [(0, counter, start)]
    came_from: Dict[Cell, Cell] = {}
    g_score: Dict[Cell, float] = {start: 0}
    visited = set()
    explored = 0

    while open_heap:
        _, _, current = heapq.heappop(open_heap)
        if current in visited:
            continue
        visited.add(current)
        explored += 1

        if current == goal:
            break

        for nxt in neighbors(current):
            tentative_g = g_score[current] + 1  # uniform cost per step
            if tentative_g < g_score.get(nxt, float("inf")):
                g_score[nxt] = tentative_g
                f_score = tentative_g + heuristic(nxt, goal)
                came_from[nxt] = current
                counter += 1
                heapq.heappush(open_heap, (f_score, counter, nxt))

    return reconstruct_path(came_from, start, goal), explored


# ---------------------------------------------------------------------------
# 5. Visualization helper
# ---------------------------------------------------------------------------
def render_path(maze: np.ndarray, path: Optional[List[Cell]], start: Cell, goal: Cell) -> str:
    if path is None:
        return "  (no path found)"
    path_set = set(path)
    lines = []
    for r in range(maze.shape[0]):
        row_chars = []
        for c in range(maze.shape[1]):
            cell = (r, c)
            if cell == start:
                row_chars.append("S")
            elif cell == goal:
                row_chars.append("G")
            elif cell in path_set:
                row_chars.append("*")
            elif maze[r, c] == 1:
                row_chars.append("#")
            else:
                row_chars.append(".")
        lines.append(" ".join(row_chars))
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# 6. Run and compare
# ---------------------------------------------------------------------------
def main() -> None:
    print("Maze legend: # = wall, . = free cell, S = start, G = goal, * = path\n")
    print("Raw maze:")
    print(render_path(MAZE, None, START, GOAL))
    print()

    algorithms = {
        "BFS (Breadth-First Search)": bfs,
        "DFS (Depth-First Search)": dfs,
        "A* (A-star, Manhattan heuristic)": a_star,
    }

    summary = []
    for name, algo in algorithms.items():
        path, explored = algo(START, GOAL)
        path_len = len(path) - 1 if path else None  # number of steps
        summary.append((name, path_len, explored))

        print(f"--- {name} ---")
        print(render_path(MAZE, path, START, GOAL))
        if path:
            print(f"Path length (steps): {path_len}")
        print(f"Nodes explored: {explored}")
        print()

    print("=== Comparison Summary ===")
    print(f"{'Algorithm':<38}{'Path length':<14}{'Nodes explored':<16}")
    for name, path_len, explored in summary:
        print(f"{name:<38}{str(path_len):<14}{explored:<16}")

    print(
        "\nObservation: BFS and A* both find the SHORTEST path on this uniform-cost "
        "grid, but A* typically explores fewer nodes because its heuristic "
        "(Manhattan distance) guides the search toward the goal. DFS finds *a* "
        "path (not necessarily shortest) and its node count depends heavily on "
        "the order neighbors are pushed onto the stack."
    )


if __name__ == "__main__":
    main()
