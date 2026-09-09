# Game AI, Behavior Trees and Pathfinding

- Introduction to game artificial intelligence
- Decision-making systems for NPCs and agents
- Finite state machines, behavior trees, and utility AI
- Pathfinding algorithms: A\*, Dijkstra, navigation meshes
- Steering behaviors and local movement control
- Adaptive difficulty and procedural behavior generation
- AI debugging, balancing, and player interaction considerations

## Introduction to Game Artificial Intelligence

Game AI is the discipline of making non-player characters (NPCs), enemies, and interactive systems behave in believable, fun, and challenging ways. It is not about making perfectly intelligent agents, but about creating systems that enhance gameplay through predictable, responsive, and varied behavior.

## Decision-Making Systems for NPCs and Agents

NPC decision-making can be implemented through simple rule-based systems, finite state machines, or more advanced architectures like goal-oriented action planning. The goal is to choose actions based on the game state, player behavior, and environmental context.

## Finite State Machines, Behavior Trees, and Utility AI

Finite state machines (FSMs) model behavior as a set of discrete states and transitions. Behavior trees extend FSMs with hierarchical control flow, allowing reusable and modular behavior composition. Utility AI scores possible actions and selects the highest-value option, enabling more dynamic and context-sensitive decisions.

## Pathfinding Algorithms: A\*, Dijkstra, Navigation Meshes

Pathfinding is the process of finding a traversable route through a game world. A\* is the most common algorithm for grid- and graph-based navigation. Dijkstra is useful for uniform-cost pathfinding and precomputing distances. Navigation meshes (navmeshes) represent walkable surfaces in continuous space and are critical for efficient and realistic movement in 3D environments.

## Steering Behaviors and Local Movement Control

Steering behaviors control how agents move locally, producing natural motion like seeking, fleeing, arriving, and avoidance. Combining steering with global pathfinding yields smooth navigation across dynamic worlds while responding to obstacles and other agents.

## Adaptive Difficulty and Procedural Behavior Generation

Adaptive difficulty adjusts game challenge based on player skill and performance. Procedural behavior generation uses rules, randomness, and combinatorial systems to create varied encounters, enemy tactics, and NPC schedules without hand-authoring every scenario.

## AI Debugging, Balancing, and Player Interaction Considerations

Debugging game AI requires visualization tools, logging, and state inspection to understand why agents choose actions. Balancing AI ensures challenge without frustration; designers should tune response times, sensing ranges, and decision thresholds. Player interaction considerations include fairness, telegraphing actions, and avoiding behavior that feels artificial or unpredictable in a bad way.

--> This is a depth-companion to `07 Game AI, Behavior Trees and Pathfinding.md` -- that chapter lists the concepts; this one works through the actual data structures, code, and trade-offs.

# From Finite State Machines to Behavior Trees

--> Chapter 3's FSMs ([[03 Game Architecture -- ECS, State Machines and Object Pooling]]) model an NPC as exactly one state at a time, with hand-wired transitions. That works fine for a handful of states, but every new state multiplies the number of possible transitions you have to reason about -- a boss with 8 states and situational transitions between all of them becomes an unmaintainable tangle of `if` statements before long.
--> A **behavior tree (BT)** fixes this by replacing a flat set of states with a **tree of nodes** evaluated top-to-bottom, every tick, starting from the root. Each node returns one of three statuses: `SUCCESS`, `FAILURE`, or `RUNNING` (still in progress, e.g. mid-animation). There is no persistent "current state" to track -- the tree just re-evaluates itself from the root each tick, which is what makes behavior trees compose so much more cleanly than FSMs as complexity grows.
--> Two composite node types do essentially all the structural work:
--> **Selector ("OR" node)** -- tries each child in order, stops and returns `SUCCESS` at the first child that succeeds (or returns `RUNNING` if a child is still running). Returns `FAILURE` only if every child fails. Used to express "try this, and if it doesn't apply, fall back to that."
--> **Sequence ("AND" node)** -- runs each child in order, stops and returns `FAILURE` at the first child that fails. Returns `SUCCESS` only if every child succeeds. Used to express "do this, then this, then this, aborting if any step fails."
--> **Leaf nodes** are the actual work: **conditions** (`IsPlayerVisible?`) that just check something and return SUCCESS/FAILURE, and **actions** (`ChaseTarget`) that do something over one or more ticks and can return `RUNNING`.

# A Worked Example -- Patrol / Chase / Attack

--> A classic enemy AI is exactly a Selector choosing between three behaviors, each gated by a Sequence of conditions:

```python
# --- Node status ---
SUCCESS, FAILURE, RUNNING = "SUCCESS", "FAILURE", "RUNNING"

class Node:
    def tick(self, agent):
        raise NotImplementedError

class Selector(Node):
    """Tries children in order; succeeds as soon as one succeeds."""
    def __init__(self, children):
        self.children = children

    def tick(self, agent):
        for child in self.children:
            status = child.tick(agent)
            if status != FAILURE:
                return status          # SUCCESS or RUNNING -- stop here
        return FAILURE

class Sequence(Node):
    """Runs children in order; fails as soon as one fails."""
    def __init__(self, children):
        self.children = children

    def tick(self, agent):
        for child in self.children:
            status = child.tick(agent)
            if status != SUCCESS:
                return status          # FAILURE or RUNNING -- stop here
        return SUCCESS

# --- Leaf conditions ---
class IsPlayerInAttackRange(Node):
    def tick(self, agent):
        return SUCCESS if agent.distance_to_player() <= agent.attack_range else FAILURE

class IsPlayerVisible(Node):
    def tick(self, agent):
        return SUCCESS if agent.can_see_player() else FAILURE

# --- Leaf actions ---
class AttackPlayer(Node):
    def tick(self, agent):
        agent.play_attack_animation()
        return RUNNING if not agent.attack_animation_finished() else SUCCESS

class ChasePlayer(Node):
    def tick(self, agent):
        agent.move_toward(agent.player_position(), speed=agent.chase_speed)
        return RUNNING

class Patrol(Node):
    def tick(self, agent):
        agent.move_toward(agent.next_patrol_point(), speed=agent.patrol_speed)
        return RUNNING

# --- The tree itself ---
enemy_ai = Selector([
    Sequence([IsPlayerInAttackRange(), AttackPlayer()]),   # try attack first
    Sequence([IsPlayerVisible(),       ChasePlayer()]),    # else chase if seen
    Patrol(),                                              # else fall back to patrol
])

# Game loop calls this once per frame, same as the update() step in chapter 1
def update_enemy(agent):
    enemy_ai.tick(agent)
```

--> Read the Selector top-down: attack is tried first (it's the most specific, highest-priority behavior); if the player isn't in range, `Sequence` fails fast on `IsPlayerInAttackRange`, and the Selector moves on to try chasing; if the player isn't visible either, it falls through to `Patrol`, which never fails, so the Selector always terminates in *some* behavior every tick.
--> Note this needs zero explicit "current state" variable and no hand-written transition table -- reordering priorities is just reordering the Selector's children list, and adding a new behavior (e.g. `Flee` when health is low) is just inserting one more `Sequence` before `Patrol`. This is the concrete version of the "generalizes FSMs" line chapter 7 mentions only in passing.
--> `RUNNING` is what makes multi-tick actions (playing an attack animation, walking toward a point) work correctly inside a tree that's re-evaluated every frame -- a naive re-check-from-scratch design would restart the attack animation every tick; returning `RUNNING` lets a node "hold" control across frames until it's actually done.

# Pathfinding -- A* Worked Example

--> `ChasePlayer` and `Patrol` above call `move_toward()` as if the destination were reachable in a straight line, which is only true in open terrain. Real levels have walls, so the agent needs an actual **path** through the world first -- this is where A* comes in, exactly as introduced conceptually in [[../../8) Robotics/Theory/05 Robotics Perception and SLAM]]'s "Path Planning: A* Basics" section. The robotics chapter and this one are solving the literal same algorithm; a game's navmesh/grid plays the same role as a robot's occupancy grid map.
--> A* keeps a priority queue of nodes to explore, always expanding the node with the lowest `f(n) = g(n) + h(n)`: `g(n)` is the exact cost accumulated so far to reach node `n`, and `h(n)` is a heuristic *estimate* of the remaining cost to the goal. As long as `h` never overestimates the true remaining cost (an "admissible" heuristic -- Manhattan or Euclidean distance both qualify on a grid), the first path A* finds to the goal is guaranteed optimal.

```python
import heapq

def heuristic(a, b):
    # Manhattan distance -- admissible for 4-directional grid movement
    return abs(a[0] - b[0]) + abs(a[1] - b[1])

def a_star(grid, start, goal):
    """grid: 2D list, 0 = walkable, 1 = wall. start/goal: (row, col) tuples."""
    rows, cols = len(grid), len(grid[0])
    open_set = [(0, start)]                 # (f_score, node) min-heap
    came_from = {}
    g_score = {start: 0}

    while open_set:
        _, current = heapq.heappop(open_set)
        if current == goal:
            path = [current]
            while current in came_from:
                current = came_from[current]
                path.append(current)
            return path[::-1]                # reverse: start -> goal

        r, c = current
        for dr, dc in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            neighbor = (r + dr, c + dc)
            nr, nc = neighbor
            if not (0 <= nr < rows and 0 <= nc < cols) or grid[nr][nc] == 1:
                continue                     # off-grid or blocked by a wall

            tentative_g = g_score[current] + 1     # uniform edge cost of 1
            if tentative_g < g_score.get(neighbor, float("inf")):
                came_from[neighbor] = current
                g_score[neighbor] = tentative_g
                f_score = tentative_g + heuristic(neighbor, goal)
                heapq.heappush(open_set, (f_score, neighbor))

    return None   # no path exists
```

--> This returns a list of grid cells from start to goal; in the enemy AI above, `ChasePlayer` would call `a_star(level_grid, agent.grid_pos(), player.grid_pos())` once (or periodically, if the player moves) and then feed the returned waypoints one at a time into `move_toward()`, rather than moving straight at the player's raw position and getting stuck on walls.
--> **Dijkstra** is the special case of A* where `h(n) = 0` always -- it explores uniformly outward with no goal-directed guidance, which is why chapter 7 correctly notes it's better suited to precomputing distances FROM one source TO everywhere (useful for e.g. flow-field pathfinding for large crowds) rather than a single point-to-point query, where A*'s heuristic saves a large amount of wasted exploration.
--> **Navigation meshes (navmeshes)** replace the grid with a set of walkable convex polygons covering the actual 3D level geometry; A* runs over the navmesh's polygon-adjacency graph instead of grid cells, giving paths that hug real geometry instead of being staircase-shaped along grid axis lines. The algorithm above is identical in structure -- only what counts as a "node" and a "neighbor" changes.

# Deep Dive -- Behavior Trees vs Utility AI vs GOAP

--> Chapter 7 lists FSMs, behavior trees, and utility AI side by side without saying when to reach for which. The real differentiator is how the "which action next" decision is made:
--> **Behavior trees** encode priority explicitly and structurally -- the Selector's child ORDER is the designer's stated priority (attack beats chase beats patrol). This makes BTs easy to author, debug, and reason about by reading the tree top to bottom, but that same rigidity means an agent can behave "obviously scripted" once players learn the priority order, and adding many overlapping behaviors makes the tree wide and harder to keep readable.
--> **Utility AI** scores every candidate action with a numeric function of the current game state (e.g. `attack_score = threat_level * 0.6 + ammo_remaining * 0.3 - distance_to_cover * 0.1`) and picks the highest-scoring action each tick. This produces more organic, less obviously-scripted behavior because small changes in game state can smoothly shift which action wins, but it's harder to author (tuning weights is trial-and-error) and harder to debug (there's no explicit priority list to read -- you have to reconstruct WHY an action won from several competing numeric scores).
--> **GOAP (Goal-Oriented Action Planning)** goes a level more abstract still: instead of authoring behavior directly, you give the agent a goal (e.g. `KillPlayer`) and a library of actions each tagged with preconditions and effects (`Reload`: requires `has_ammo_in_reserve`, produces `has_ammo_in_gun`), and a planner (typically A* over the space of possible action sequences -- the same algorithm as the pathfinding section above, just searching a space of actions instead of a space of grid cells) finds a sequence of actions that satisfies the goal from the current world state. This is the most flexible and can produce genuinely emergent-looking behavior (an agent that goes to fetch cover, THEN reloads, THEN attacks, without anyone hand-authoring that exact sequence), at the cost of being the hardest to control, tune, and debug of the three -- a bad precondition/effect definition can make the planner produce bizarre or degenerate plans that are hard to trace back to a root cause.
--> In practice, most shipped games use behavior trees for the bulk of enemy AI (Halo popularized this pattern, and it's the default in Unreal's Behavior Tree editor) precisely because designers can read, author, and debug a tree directly; utility AI and GOAP get reserved for agents where more organic or more emergent decision-making is worth the added tuning cost (The Sims' GOAP-driven Sims, F.E.A.R.'s widely-cited GOAP-driven soldiers).

# Deep Dive -- Pathfinding Performance in Real-Time Games

--> Chapter 1's frame budget ([[01 Game Development Fundamentals and the Game Loop]]) applies just as hard to AI as to physics and rendering: A* over a large navmesh or grid is not free, and running a full search for every one of hundreds of agents every single frame will blow the 16.6ms budget on AI alone.
--> The standard mitigations, all trading some staleness/optimality for speed, exactly mirror the broad-phase/narrow-phase and spatial-partitioning trade-offs in [[02 Game Physics and Collision Detection]]:
--> **Time-slicing / staggering** -- don't repath every agent every frame; spread repathing across several frames (agent 1 repaths this frame, agent 2 next frame, etc.), or only repath when the target has moved meaningfully far from the last-planned destination.
--> **Hierarchical pathfinding** -- precompute a coarse graph over large regions of the level, find a rough region-to-region route first, then run fine-grained A* only within the one or two regions actually being traversed right now, rather than over the whole level's full-resolution graph.
--> **Shared/cached paths** -- if many agents are chasing the same target, compute one flow field (Dijkstra run once, outward from the target, giving every cell a "which direction gets me closer" vector) instead of running A* separately per agent -- this is precisely the Dijkstra-vs-A* trade-off from the worked example above, now applied to many-agent crowds instead of one query.
--> **Off-thread pathfinding** -- since a path result is only needed a frame or two later (not instantly, unlike physics response), many engines run pathfinding requests on a worker thread and hand back results asynchronously, keeping the main thread's frame budget free of pathfinding's spikier cost.
--> This is the same recurring theme as chapters 1-3: the "textbook" version of an algorithm (A* run fresh, every agent, every frame) is usually not what ships; what ships is the textbook algorithm plus a scheduling/caching layer that keeps its cost inside a hard real-time budget.

# See Also

--> [[07 Game AI, Behavior Trees and Pathfinding]] -- the overview chapter this deep dive extends.
--> [[03 Game Architecture -- ECS, State Machines and Object Pooling]] -- FSMs, which behavior trees generalize, plus the object pooling pattern that applies equally well to reusing agent/pathfinding-request objects.
--> [[02 Game Physics and Collision Detection]] -- broad-phase/narrow-phase and spatial partitioning, the physics-side analogue of the pathfinding performance trade-offs above.
--> [[../../8) Robotics/Theory/05 Robotics Perception and SLAM]] -- the same A* algorithm applied to real robot path planning over an occupancy-grid map built by SLAM, plus a discussion of why a good planner still needs an honest motion model.
