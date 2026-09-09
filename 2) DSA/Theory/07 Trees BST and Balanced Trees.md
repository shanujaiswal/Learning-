# Tree Terminology

--> A tree is a HIERARCHICAL structure of nodes -- one ROOT node at the top, with each node having zero or more CHILDREN, and every node except the root has exactly one PARENT. A node with no children is a LEAF.
--> **Height** of a node -- the number of edges on the longest downward path from that node to a leaf. **Depth** of a node -- the number of edges from the root down to that node. The root has depth 0; a tree's overall height is the height of its root.
--> A **binary tree** restricts every node to at most TWO children, conventionally called `left` and `right` -- the specific shape nearly every tree algorithm in this file assumes.

```python
class TreeNode:
    def __init__(self, value):
        self.value = value
        self.left = None
        self.right = None
```

# Tree Traversals

--> **Depth-first traversals** (go as deep as possible before backtracking) -- three orderings, differing only in WHEN the current node itself is visited relative to its children.

```python
def preorder(node, result):    # Node, then Left, then Right
    if node:
        result.append(node.value)
        preorder(node.left, result)
        preorder(node.right, result)

def inorder(node, result):     # Left, then Node, then Right
    if node:
        inorder(node.left, result)
        result.append(node.value)
        inorder(node.right, result)

def postorder(node, result):   # Left, then Right, then Node
    if node:
        postorder(node.left, result)
        postorder(node.right, result)
        result.append(node.value)
```

```text
       4
      / \
     2   6
    / \ / \
   1  3 5  7

Preorder  (Node,L,R): 4, 2, 1, 3, 6, 5, 7    -- useful for COPYING a tree (recreate parent before children)
Inorder   (L,Node,R):  1, 2, 3, 4, 5, 6, 7    -- for a BST specifically, this always yields SORTED order
Postorder (L,R,Node):  1, 3, 2, 5, 7, 6, 4    -- useful for DELETING a tree (free children before the parent)
```

--> **Breadth-first traversal (level-order)** -- visits all nodes at depth 0, then all at depth 1, then depth 2, and so on -- implemented with a QUEUE (directly connecting to the Queues section of the Linked Lists/Stacks/Queues file) rather than recursion, since it needs to track "what's next in line" rather than "go as deep as possible."

```python
from collections import deque

def level_order(root):
    if not root:
        return []
    result, queue = [], deque([root])
    while queue:
        node = queue.popleft()
        result.append(node.value)
        if node.left:
            queue.append(node.left)
        if node.right:
            queue.append(node.right)
    return result
```

# Binary Search Trees (BST)

--> A BST adds one ordering rule on top of a plain binary tree: for every node, everything in its LEFT subtree is SMALLER, and everything in its RIGHT subtree is LARGER -- this single rule is what makes search, insert, and delete all achievable in `O(log n)` (on a BALANCED tree) instead of needing to check every node.

```python
def bst_insert(root, value):
    if root is None:
        return TreeNode(value)
    if value < root.value:
        root.left = bst_insert(root.left, value)
    else:
        root.right = bst_insert(root.right, value)
    return root

def bst_search(root, target):
    if root is None or root.value == target:
        return root
    if target < root.value:
        return bst_search(root.left, target)
    return bst_search(root.right, target)
```

--> Each comparison eliminates an ENTIRE subtree from consideration -- exactly the same halving idea as binary search on a sorted array (covered in the Searching file), just expressed as a tree structure instead of index arithmetic.

# The Balance Problem

--> A BST's `O(log n)` guarantee depends entirely on the tree being roughly BALANCED (each subtree's height staying proportional to `log n`) -- inserting already-sorted data into a naive BST (as shown above, with no rebalancing) produces a completely lopsided tree that degenerates into a plain linked list.

```text
Inserting 1, 2, 3, 4, 5 in that order into a naive BST:

1
 \
  2
   \
    3
     \
      4
       \
        5

-- every node has only a right child -- this is now O(n) to search, exactly like a linked list, not O(log n)
```

--> This is exactly the motivation for SELF-BALANCING trees -- they automatically restructure themselves during insertion/deletion to guarantee the tree never degenerates like this.

# AVL Trees -- Strict Height Balancing

--> An AVL tree tracks a BALANCE FACTOR at every node (the height of its left subtree minus the height of its right subtree) and enforces that this factor must always stay within `{-1, 0, 1}` -- whenever an insertion/deletion would push it outside that range, a ROTATION restructures the tree locally to restore balance.

```text
Right rotation (fixes a left-heavy imbalance):

      z                       y
     /                       / \
    y            -->        x   z
   /
  x

-- y becomes the new local root, z becomes y's right child, preserving BST ordering throughout
```

--> **Why this guarantees O(log n)** -- keeping every subtree's balance factor within `{-1,0,1}` mathematically guarantees the tree's height stays `O(log n)` even in the worst case -- unlike the naive BST above, AVL trees CANNOT degenerate into a linked-list shape, no matter what order elements are inserted in.
--> **The trade-off** -- rotations add real overhead to every insert/delete (checking and potentially rebalancing at multiple levels on the way back up) -- AVL trees favor LOOKUP-heavy workloads (strict balance means the fastest possible guaranteed search) over write-heavy ones.

# Red-Black Trees -- Looser Balancing, Faster Writes

--> A red-black tree colors every node RED or BLACK and enforces a looser set of rules (no two red nodes in a row, every path from root to a leaf passes through the same number of black nodes) that still GUARANTEE `O(log n)` height, but require FEWER rotations on average than AVL's strict balance factor rule.
--> **Why this matters practically** -- red-black trees favor WRITE-heavy workloads (fewer rebalancing operations per insert/delete) at the cost of slightly less tight balance (and thus slightly slower worst-case lookups) than AVL -- this exact trade-off is why red-black trees are the standard choice inside many real-world library implementations: Java's `TreeMap`/`TreeSet`, C++'s `std::map`, and the Linux kernel's internal scheduler data structures all use red-black trees rather than AVL trees.

# B-Trees -- Balancing for Disk, Not Just Memory

--> Directly connects to the Indexing files in the Database notes -- a B-Tree generalizes the balanced-tree idea to allow MANY children per node (not just 2), specifically to minimize the NUMBER of disk reads needed to reach a target value, since each disk read can be relatively slow but can fetch an entire large node's worth of data at once.
--> This is exactly why B-Trees (and their common variant, B+ Trees) are the standard structure underlying database indexes rather than a binary AVL/red-black tree -- a wide, shallow B-Tree needs far fewer disk-level node reads to reach any given key than an in-memory-oriented binary tree would.

# Deep Dive -- When to Reach for a Tree at All

--> **Use a BST/balanced tree when** -- you need SORTED order maintained continuously while still supporting `O(log n)` insert/search/delete (a plain sorted array gives fast search but `O(n)` insert; an unsorted structure gives fast insert but slow search -- a balanced tree gives both).
--> **Prefer a hash table instead (covered in the Hashing file) when** -- you don't need sorted order or range queries ("give me everything between X and Y") at all, just fast individual lookups -- a hash table's `O(1)` average lookup beats a tree's `O(log n)` whenever ordering genuinely doesn't matter.
--> **Prefer a heap instead (covered in the Heaps file) when** -- you only ever need the MINIMUM or MAXIMUM element repeatedly, not arbitrary sorted access to every element -- a heap's simpler structure achieves this with less overhead than maintaining full sort order in a balanced tree.
