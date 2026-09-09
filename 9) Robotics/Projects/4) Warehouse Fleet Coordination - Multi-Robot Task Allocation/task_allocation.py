"""Auction-based multi-robot task allocation -- the genuinely new content
this project teaches on top of Project 1's single-robot navigation stack.

This is the real Bertsekas auction algorithm (Bertsekas, 1979/1988) for the
assignment problem, the same family of algorithm real fleet-management
systems (and the broader multi-robot task allocation / MRTA literature,
Gerkey & Mataric's "instantaneous assignment" class of problem) use to
match idle robots to open work items: every idle robot "bids" its own
travel cost for every unassigned task, and instead of a single central
optimizer solving the whole assignment problem at once (Hungarian
algorithm, O(n^3), and it needs the FULL matrix up front), the auction
algorithm runs as a sequence of local bids and price rises that converges
to a (near-)optimal assignment and, crucially, extends naturally to the
"robots become available/tasks arrive over time" setting a real fleet
actually runs in -- exactly why `FleetManagerNode` (`nodes.py`) just calls
`auction_allocate` again on whatever idle robots + unassigned tasks exist
at the moment, every time the roster changes, instead of needing to
re-solve one big static assignment problem.

Bid model: each robot's bid for a task is its Euclidean travel distance to
that task's location. Real fleets often bid actual path distance (A* cost)
instead of straight-line distance for a tighter estimate, but that means
running a full path plan for every (robot, task) pair just to VALUE a bid
-- expensive to do every time the task queue changes. Euclidean distance is
the standard fast admissible proxy (it never overestimates true path cost
in this floorplan's open aisles) used for the bidding round; the actual
route is planned properly (via `world.astar`) only once a bid wins.
"""

import numpy as np


def _bertsekas_auction(cost_matrix, epsilon=0.01, max_iterations=20000):
    """Classic auction algorithm for the (possibly rectangular) linear
    assignment problem: assigns each of `n_bidders` rows to at most one of
    `n_items` columns, minimizing total assigned cost. Returns an array of
    length `n_bidders` giving each bidder's assigned item index (-1 if
    left unassigned, which only happens when there are more bidders than
    items).

    Every bidder repeatedly bids on whichever item currently gives it the
    best (value - price); prices only ever go up, which is what guarantees
    the process terminates instead of two bidders fighting over the same
    item forever. `epsilon` is the minimum bid increment -- without it the
    algorithm can cycle without making progress; with it, the algorithm is
    guaranteed to terminate at an assignment within `n_items * epsilon` of
    the true optimum (the standard epsilon-scaling correctness result).
    """
    n_bidders, n_items = cost_matrix.shape
    value = -cost_matrix  # bidders maximize value; minimizing cost = maximizing -cost
    prices = np.zeros(n_items)
    assignment = -np.ones(n_bidders, dtype=int)   # bidder -> item
    owner = -np.ones(n_items, dtype=int)          # item -> bidder
    unassigned = list(range(n_bidders))

    it = 0
    while unassigned and it < max_iterations:
        it += 1
        i = unassigned.pop(0)
        net_values = value[i] - prices
        best_j = int(np.argmax(net_values))
        best_val = net_values[best_j]
        if n_items > 1:
            net_values_wo_best = net_values.copy()
            net_values_wo_best[best_j] = -np.inf
            second_val = np.max(net_values_wo_best)
        else:
            second_val = best_val - epsilon
        bid_increment = (best_val - second_val) + epsilon
        prices[best_j] += bid_increment

        prev_owner = owner[best_j]
        if prev_owner != -1:
            assignment[prev_owner] = -1
            unassigned.append(prev_owner)
        owner[best_j] = i
        assignment[i] = best_j

    return assignment


def auction_allocate(robot_positions, task_positions, epsilon=0.01):
    """robot_positions: dict[robot_id -> (x, y)] of currently IDLE robots.
    task_positions: dict[task_id -> (x, y)] of currently UNASSIGNED tasks.

    Runs one round of the auction algorithm over the current idle-robot /
    open-task roster and returns dict[robot_id -> task_id] for however many
    pairs can be matched this round (min(len(robots), len(tasks)) pairs --
    if there are more robots than tasks, the extra robots are left
    unassigned this round and simply bid again next time `FleetManagerNode`
    calls this with a fresh, smaller task list).

    Cost model: Euclidean travel distance from robot to task, in meters --
    see module docstring for why this proxy is used instead of full A* cost
    at bid time.
    """
    if not robot_positions or not task_positions:
        return {}

    robot_ids = list(robot_positions.keys())
    task_ids = list(task_positions.keys())
    robot_xy = np.array([robot_positions[r] for r in robot_ids])   # (n_r, 2)
    task_xy = np.array([task_positions[t] for t in task_ids])      # (n_t, 2)

    # Vectorized Euclidean bid/cost matrix: cost[r, t] = ||robot_r - task_t||
    cost_matrix = np.linalg.norm(robot_xy[:, None, :] - task_xy[None, :, :], axis=2)

    # The auction algorithm assigns each ROW to at most one COLUMN, so
    # whichever side is larger should be columns (items) so every bidder on
    # the smaller side gets a match this round; if there are more tasks
    # than robots we auction with robots-as-bidders (every robot matched,
    # tasks left over); if more robots than tasks, tasks-as-bidders
    # (every task matched, robots left over) -- either way nobody on the
    # "item" side is starved of a possible bidder.
    if len(robot_ids) <= len(task_ids):
        assignment = _bertsekas_auction(cost_matrix, epsilon=epsilon)
        return {robot_ids[r]: task_ids[t] for r, t in enumerate(assignment) if t != -1}
    else:
        assignment = _bertsekas_auction(cost_matrix.T, epsilon=epsilon)
        return {robot_ids[r]: task_ids[t] for t, r in enumerate(assignment) if r != -1}
