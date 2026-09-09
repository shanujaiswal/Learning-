"""
11_tries_union_find_advanced_structures.py

Implements and tests:
    1. A Trie -- insert/search/starts_with, demonstrating fast prefix lookups
    2. Union-Find with path compression + union by rank -- friend-circle grouping
    3. A Fenwick Tree (Binary Indexed Tree) for O(log n) prefix-sum queries and point updates

Covers Theory chapter:
    2) DSA/Theory/11 Tries Union-Find and Advanced Data Structures.md

Run:  python 11_tries_union_find_advanced_structures.py
"""


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


# ---------------------------------------------------------------------------
# 1) Trie
# ---------------------------------------------------------------------------

class TrieNode:
    def __init__(self):
        self.children = {}
        self.is_end_of_word = False


class Trie:
    def __init__(self):
        self.root = TrieNode()

    def insert(self, word):
        node = self.root
        for char in word:
            node = node.children.setdefault(char, TrieNode())
        node.is_end_of_word = True

    def search(self, word):
        node = self._walk(word)
        return node is not None and node.is_end_of_word

    def starts_with(self, prefix):
        return self._walk(prefix) is not None

    def _walk(self, s):
        node = self.root
        for char in s:
            if char not in node.children:
                return None
            node = node.children[char]
        return node


def demo_trie() -> None:
    print_section("1) Trie -- prefix-based lookups")
    trie = Trie()
    words = ["cat", "car", "card", "care", "dog", "do"]
    for w in words:
        trie.insert(w)

    print(f"Inserted words: {words}")
    for word in ["cat", "ca", "care", "cart", "do", "dogs"]:
        exact = trie.search(word)
        prefix = trie.starts_with(word)
        print(f"  '{word}': exact match={exact}, is a valid prefix={prefix}")

    assert trie.search("cat") is True
    assert trie.search("ca") is False          # "ca" was never inserted as a complete word
    assert trie.starts_with("ca") is True        # but plenty of words start with "ca"
    assert trie.search("cart") is False
    assert trie.starts_with("cart") is False
    print("Assertions passed.")


# ---------------------------------------------------------------------------
# 2) Union-Find -- friend circle grouping
# ---------------------------------------------------------------------------

class UnionFind:
    def __init__(self, n):
        self.parent = list(range(n))
        self.rank = [0] * n

    def find(self, x):
        if self.parent[x] != x:
            self.parent[x] = self.find(self.parent[x])
        return self.parent[x]

    def union(self, x, y):
        root_x, root_y = self.find(x), self.find(y)
        if root_x == root_y:
            return False
        if self.rank[root_x] < self.rank[root_y]:
            root_x, root_y = root_y, root_x
        self.parent[root_y] = root_x
        if self.rank[root_x] == self.rank[root_y]:
            self.rank[root_x] += 1
        return True

    def count_groups(self):
        return len({self.find(i) for i in range(len(self.parent))})


def demo_union_find_friend_circles() -> None:
    print_section("2) Union-Find -- grouping people into friend circles")
    people = ["Alice", "Bob", "Carol", "Dave", "Eve", "Frank"]
    index = {name: i for i, name in enumerate(people)}
    uf = UnionFind(len(people))

    friendships = [("Alice", "Bob"), ("Bob", "Carol"), ("Dave", "Eve")]
    print(f"People: {people}")
    print(f"Friendships (union operations): {friendships}")
    for a, b in friendships:
        uf.union(index[a], index[b])

    print(f"Number of distinct friend circles: {uf.count_groups()}")
    assert uf.find(index["Alice"]) == uf.find(index["Carol"])     # connected via Bob
    assert uf.find(index["Alice"]) != uf.find(index["Dave"])       # separate circles
    assert uf.count_groups() == 3    # {Alice,Bob,Carol}, {Dave,Eve}, {Frank} alone
    print("Assertions passed -- Alice and Carol are in the same circle (via Bob); "
          "Frank remains alone in his own circle.")


# ---------------------------------------------------------------------------
# 3) Fenwick Tree (Binary Indexed Tree)
# ---------------------------------------------------------------------------

class FenwickTree:
    def __init__(self, size):
        self.size = size
        self.tree = [0] * (size + 1)

    def update(self, index, delta):        # O(log n) -- add `delta` to the value at position `index`
        index += 1
        while index <= self.size:
            self.tree[index] += delta
            index += index & (-index)

    def prefix_sum(self, index):             # O(log n) -- sum of all elements from 0 to index, inclusive
        index += 1
        total = 0
        while index > 0:
            total += self.tree[index]
            index -= index & (-index)
        return total

    def range_sum(self, left, right):
        return self.prefix_sum(right) - (self.prefix_sum(left - 1) if left > 0 else 0)


def demo_fenwick_tree() -> None:
    print_section("3) Fenwick Tree -- O(log n) prefix sums and point updates")
    arr = [3, 2, 7, 5, 1, 9, 4, 6]
    fenwick = FenwickTree(len(arr))
    for i, val in enumerate(arr):
        fenwick.update(i, val)

    print(f"Array: {arr}")
    sum_0_3 = fenwick.range_sum(0, 3)
    print(f"Sum of indices [0..3] ({arr[0:4]}): {sum_0_3}")
    assert sum_0_3 == sum(arr[0:4])

    print("Updating index 2 (+10)...")
    fenwick.update(2, 10)
    arr[2] += 10
    sum_0_3_after = fenwick.range_sum(0, 3)
    print(f"Sum of indices [0..3] after update: {sum_0_3_after}")
    assert sum_0_3_after == sum(arr[0:4])
    print("Assertions passed -- range sums stay correct after a point update, both O(log n).")


if __name__ == "__main__":
    demo_trie()
    demo_union_find_friend_circles()
    demo_fenwick_tree()
    print("\nAll Tries/Union-Find/Advanced-Structures demos completed.")
