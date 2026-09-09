"""
07_trees_bst_balanced_trees.py

Implements and tests:
    1. Binary tree traversals -- preorder, inorder, postorder, level-order
    2. Binary Search Tree -- insert, search, and the "inorder gives sorted order" property
    3. A demonstration of BST degeneration on sorted input (the motivation for balancing)
    4. Computing tree height, to show the degenerate case's height is O(n), not O(log n)

Covers Theory chapter:
    2) DSA/Theory/07 Trees BST and Balanced Trees.md

Run:  python 07_trees_bst_balanced_trees.py
"""

from collections import deque


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


class TreeNode:
    def __init__(self, value):
        self.value = value
        self.left = None
        self.right = None


# ---------------------------------------------------------------------------
# 1) Traversals
# ---------------------------------------------------------------------------

def preorder(node, result=None):
    if result is None:
        result = []
    if node:
        result.append(node.value)
        preorder(node.left, result)
        preorder(node.right, result)
    return result


def inorder(node, result=None):
    if result is None:
        result = []
    if node:
        inorder(node.left, result)
        result.append(node.value)
        inorder(node.right, result)
    return result


def postorder(node, result=None):
    if result is None:
        result = []
    if node:
        postorder(node.left, result)
        postorder(node.right, result)
        result.append(node.value)
    return result


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


def build_sample_tree():
    #        4
    #       / \
    #      2   6
    #     / \ / \
    #    1  3 5  7
    root = TreeNode(4)
    root.left = TreeNode(2)
    root.right = TreeNode(6)
    root.left.left = TreeNode(1)
    root.left.right = TreeNode(3)
    root.right.left = TreeNode(5)
    root.right.right = TreeNode(7)
    return root


def demo_traversals() -> None:
    print_section("1) Tree traversals -- preorder, inorder, postorder, level-order")
    root = build_sample_tree()
    pre, ino, post, level = preorder(root), inorder(root), postorder(root), level_order(root)
    print(f"Preorder  (Node,L,R): {pre}")
    print(f"Inorder   (L,Node,R): {ino}")
    print(f"Postorder (L,R,Node): {post}")
    print(f"Level-order (BFS):    {level}")
    assert pre == [4, 2, 1, 3, 6, 5, 7]
    assert ino == [1, 2, 3, 4, 5, 6, 7]
    assert post == [1, 3, 2, 5, 7, 6, 4]
    assert level == [4, 2, 6, 1, 3, 5, 7]
    print("Assertions passed.")


# ---------------------------------------------------------------------------
# 2) BST insert/search + inorder-gives-sorted-order property
# ---------------------------------------------------------------------------

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


def tree_height(root):
    if root is None:
        return -1
    return 1 + max(tree_height(root.left), tree_height(root.right))


def demo_bst_operations() -> None:
    print_section("2) BST insert/search, and inorder traversal always yields sorted order")
    values = [50, 30, 70, 20, 40, 60, 80]
    root = None
    for v in values:
        root = bst_insert(root, v)

    print(f"Inserted (in this order): {values}")
    print(f"Inorder traversal:        {inorder(root)}  <- always sorted for a BST, regardless of insert order")
    assert inorder(root) == sorted(values)

    found = bst_search(root, 60)
    not_found = bst_search(root, 999)
    print(f"search(60)  -> node found with value {found.value if found else None}")
    print(f"search(999) -> {'found' if not_found else 'not found'}")
    assert found is not None and found.value == 60
    assert not_found is None

    print(f"Tree height with this reasonably balanced insert order: {tree_height(root)}")
    print("Assertions passed.")


# ---------------------------------------------------------------------------
# 3) BST degeneration on sorted input -- the motivation for AVL/red-black trees
# ---------------------------------------------------------------------------

def demo_bst_degeneration() -> None:
    print_section("3) BST degeneration -- inserting already-sorted data with no rebalancing")
    sorted_values = [1, 2, 3, 4, 5, 6, 7]
    root = None
    for v in sorted_values:
        root = bst_insert(root, v)

    height = tree_height(root)
    print(f"Inserted already-sorted values {sorted_values} into a naive (non-self-balancing) BST.")
    print(f"Resulting tree height: {height}  (a balanced tree with {len(sorted_values)} nodes would have height "
          f"~{len(sorted_values).bit_length() - 1})")
    assert height == len(sorted_values) - 1   # degenerated into a linked-list shape -- O(n) height, not O(log n)
    print("Confirmed: height == n-1, i.e. the tree degenerated into a linked list -- "
          "exactly the problem AVL/red-black trees exist to prevent.")


if __name__ == "__main__":
    demo_traversals()
    demo_bst_operations()
    demo_bst_degeneration()
    print("\nAll Trees/BST/Balanced-Tree demos completed.")
