### DSA with Python

--> Data Structures is about how data can be stored in different structures.
--> Algorithms is about how to solve different problems, often by searching through and manipulating data structures.
--> DSA helps to find the best combination of Data Structures and Algorithms to create more efficient code.

# Data Structures

--> Data Structures are a way of storing and organizing data in a computer.
--> Python has built-in support for several data structures, such as lists, dictionaries, and sets.
--> Other data structures can be implemented using Python classes and objects, such as linked lists, stacks, queues, trees, and graphs.

1. Lists and Arrays
2. Stacks
3. Queues
4. Linked Lists
5. Hash Tables
6. Trees
   --> Binary Trees
   --> Binary Search Trees
   --> AVL Trees
7. Graphs

# Algorithms

--> Algorithms are a way of working with data in a computer and solving problems like sorting, searching, etc.

1. Linear Search
2. Binary Search
3. Bubble Sort
4. Selection Sort
5. Insertion Sort
6. Quick Sort
7. Counting Sort
8. Radix Sort
9. Merge Sort

==> Why Learn DSA with Python
--> Python has a clean readable syntax
--> DSA allows you to improve problem-solving skills
--> DSA and Python helps you write more efficient code
--> DSA gives you a better understanding of memory storage
--> DSA helps you handle complex programming challenges
--> Python is widely used in Data Science and Machine Learning

## Python Lists and Arrays

--> In Python, lists are the built-in data structure that serves as a dynamic array.
--> Lists are ordered, mutable, and can contain elements of different types.

# Lists

--> A list is a built-in data structure in Python, used to store multiple elements.
--> Lists are used by many algorithms.

==> Creating Lists
--> Lists are created using square brackets []:

```python
# Empty list
x = []

# List with initial values
y = [1, 2, 3, 4, 5]

# List with mixed types
z = [1, "hello", 3.14, True]
```

==> List Methods
--> Python lists come with several built-in algorithms (called methods), to perform common operations like appending, sorting, and more.

```python
x = [9, 12, 7, 4, 11]

# Add element:
x.append(8)

# Sort list ascending:
x.sort()
```

# Create Algorithms

Sometimes we want to perform actions that are not built into Python.Then we can create our own algorithms.

==> Example
Create an algorithm to find the lowest value in a list:

```python
my_array = [7, 12, 9, 4, 11, 8]
minVal = my_array[0]

for i in my_array:
  if i < minVal:
    minVal = i

print('Lowest value:', minVal)
```

--> The algorithm above is very simple, and fast enough for small data sets, but if the data is big enough, any algorithm will take time to run.
--> This is where optimization comes in.
--> Optimization is an important part of algorithm development, and of course, an important part of DSA programming.
--> In the example above, the time the algorithm needs to run is proportional, or linear, to the size of the data set. It is because the algorithm must visit every array element one time to find the lowest value. The loop must run 5 times since there are 5 values in the array. And if the array had 1000 values, the loop would have to run 1000 times.

## Stacks

--> A Stack is a linear data structure that follows the LIFO (Last In, First Out) principle -- the last element added is the first one removed.
--> Real-world analogy: a stack of plates -- you add/remove plates only from the top.
--> Common operations: push (add to top), pop (remove from top), peek (view top without removing), is_empty.
--> In Python, a stack can be implemented simply using a list, using append() for push and pop() for pop.

```python
stack = []

# Push
stack.append(10)
stack.append(20)
stack.append(30)
print(stack)          # [10, 20, 30]

# Peek (top item)
print(stack[-1])      # 30

# Pop
print(stack.pop())    # 30 -- removes and returns the last item
print(stack)          # [10, 20]

# is_empty
print(len(stack) == 0)   # False
```

==> Implementing a Stack as a Class

```python
class Stack:
    def __init__(self):
        self.items = []

    def push(self, item):
        self.items.append(item)

    def pop(self):
        if not self.is_empty():
            return self.items.pop()
        return None

    def peek(self):
        if not self.is_empty():
            return self.items[-1]
        return None

    def is_empty(self):
        return len(self.items) == 0

s = Stack()
s.push(1)
s.push(2)
print(s.pop())    # 2
print(s.peek())   # 1
```

## Queues

--> A Queue is a linear data structure that follows the FIFO (First In, First Out) principle -- the first element added is the first one removed.
--> Real-world analogy: a queue/line at a ticket counter -- first person in line is served first.
--> Common operations: enqueue (add to the back), dequeue (remove from the front), peek, is_empty.
--> Using a plain list for dequeue (pop(0)) is inefficient (O(n)) because all remaining elements must shift -- collections.deque is the recommended way, since it supports O(1) additions/removals from both ends.

```python
from collections import deque

queue = deque()

# Enqueue
queue.append(10)
queue.append(20)
queue.append(30)
print(queue)              # deque([10, 20, 30])

# Dequeue
print(queue.popleft())    # 10 -- removes and returns the first item
print(queue)              # deque([20, 30])
```

==> Implementing a Queue as a Class

```python
from collections import deque

class Queue:
    def __init__(self):
        self.items = deque()

    def enqueue(self, item):
        self.items.append(item)

    def dequeue(self):
        if not self.is_empty():
            return self.items.popleft()
        return None

    def is_empty(self):
        return len(self.items) == 0

q = Queue()
q.enqueue(1)
q.enqueue(2)
print(q.dequeue())    # 1
```

## Linked Lists

--> A Linked List is a linear data structure where each element (called a node) contains data and a reference (pointer) to the next node in the sequence.
--> Unlike a list/array, elements are NOT stored in contiguous memory -- this makes insertion/deletion in the middle faster (no shifting), but random access is slower (must traverse from the head).
--> A Singly Linked List has nodes that only point forward (to the next node).

```python
class Node:
    def __init__(self, data):
        self.data = data
        self.next = None      # Pointer to the next node

class LinkedList:
    def __init__(self):
        self.head = None      # Start of the list

    def append(self, data):
        new_node = Node(data)
        if self.head is None:
            self.head = new_node
            return
        current = self.head
        while current.next:
            current = current.next
        current.next = new_node

    def print_list(self):
        current = self.head
        while current:
            print(current.data, end=" -> ")
            current = current.next
        print("None")

ll = LinkedList()
ll.append(10)
ll.append(20)
ll.append(30)
ll.print_list()   # 10 -> 20 -> 30 -> None
```

## Hash Tables

--> A Hash Table stores key-value pairs, using a hash function to compute an index (called a hash) into an array of buckets, from which the desired value can be found very quickly.
--> Python's built-in dict is itself a hash table implementation -- lookups, insertions, and deletions are (on average) O(1).

```python
# Python's dict IS a hash table
hash_table = {}
hash_table["apple"] = 10
hash_table["banana"] = 20

print(hash_table["apple"])     # 10 -- O(1) average lookup
print("banana" in hash_table)   # True
```

==> Why Hash Tables Are Fast
--> The key is passed through a hash function (Python uses hash()) which converts it into an index.
--> That index tells the hash table exactly which "bucket" to look in, avoiding the need to scan every item like a list would (O(n)).

```python
print(hash("apple"))   # Some integer -- used internally to locate the bucket
```

## Trees

--> A Tree is a hierarchical (non-linear) data structure consisting of nodes connected by edges, starting from a single root node, where each node can have zero or more child nodes.
--> Terminology: root (top node), parent/child, leaf (node with no children), height/depth.

# Binary Trees
--> A Binary Tree is a tree where each node has AT MOST two children, commonly referred to as the left child and right child.

```python
class TreeNode:
    def __init__(self, data):
        self.data = data
        self.left = None
        self.right = None

# Manually building a small binary tree
root = TreeNode(1)
root.left = TreeNode(2)
root.right = TreeNode(3)
root.left.left = TreeNode(4)
root.left.right = TreeNode(5)

# Simple in-order traversal (Left -> Root -> Right)
def inorder(node):
    if node:
        inorder(node.left)
        print(node.data, end=" ")
        inorder(node.right)

inorder(root)   # 4 2 5 1 3
```

# Binary Search Trees (BST)
--> A Binary Search Tree is a special binary tree where, for every node: all values in the LEFT subtree are smaller, and all values in the RIGHT subtree are larger.
--> This ordering property makes searching, inserting, and deleting very efficient -- O(log n) on average.

```python
class BSTNode:
    def __init__(self, data):
        self.data = data
        self.left = None
        self.right = None

def insert(root, data):
    if root is None:
        return BSTNode(data)
    if data < root.data:
        root.left = insert(root.left, data)
    else:
        root.right = insert(root.right, data)
    return root

def search(root, target):
    if root is None:
        return False
    if root.data == target:
        return True
    elif target < root.data:
        return search(root.left, target)
    else:
        return search(root.right, target)

root = None
for value in [50, 30, 70, 20, 40, 60, 80]:
    root = insert(root, value)

print(search(root, 40))   # True
print(search(root, 90))   # False
```

# AVL Trees
--> An AVL Tree is a self-balancing Binary Search Tree, where the height difference (balance factor) between the left and right subtrees of any node is at most 1.
--> Whenever an insertion/deletion breaks this balance, the tree performs rotations (left rotation / right rotation) to restore it.
--> This guarantees O(log n) operations even in the worst case (a plain unbalanced BST can degrade to O(n) if data is inserted in sorted order).
--> AVL Trees are more complex to implement (require tracking node heights and performing rotations) and are typically used via library implementations rather than written from scratch in everyday code.

## Graphs

--> A Graph is a non-linear data structure consisting of a set of nodes (called vertices) connected by edges. Unlike a tree, a graph can have cycles and does not need a single root.
--> Graphs can be Directed (edges have a direction, A -> B) or Undirected (edges go both ways).
--> A common way to represent a graph in Python is an adjacency list -- a dictionary mapping each node to a list of its connected neighbors.

```python
graph = {
    "A": ["B", "C"],
    "B": ["A", "D"],
    "C": ["A", "D"],
    "D": ["B", "C"]
}

# Breadth-First Search (BFS) -- explores neighbors level by level, using a queue
from collections import deque

def bfs(graph, start):
    visited = set([start])
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

print(bfs(graph, "A"))   # ['A', 'B', 'C', 'D']

# Depth-First Search (DFS) -- explores as far as possible along each branch, using recursion/a stack
def dfs(graph, start, visited=None, order=None):
    if visited is None:
        visited = set()
        order = []
    visited.add(start)
    order.append(start)
    for neighbor in graph[start]:
        if neighbor not in visited:
            dfs(graph, neighbor, visited, order)
    return order

print(dfs(graph, "A"))   # ['A', 'B', 'D', 'C']
```

## Searching Algorithms

# Linear Search
--> Checks every element of a list one by one until the target value is found (or the list ends).
--> Time complexity: O(n) -- works on both sorted and unsorted data.

```python
def linear_search(arr, target):
    for i in range(len(arr)):
        if arr[i] == target:
            return i     # Return the index where found
    return -1            # Not found

nums = [4, 2, 7, 1, 9, 3]
print(linear_search(nums, 7))   # 2
print(linear_search(nums, 5))   # -1
```

# Binary Search
--> Repeatedly divides a SORTED list in half, comparing the target with the middle element, and discarding the half that cannot contain it.
--> Time complexity: O(log n) -- much faster than linear search, but REQUIRES the data to already be sorted.

```python
def binary_search(arr, target):
    low, high = 0, len(arr) - 1
    while low <= high:
        mid = (low + high) // 2
        if arr[mid] == target:
            return mid
        elif arr[mid] < target:
            low = mid + 1
        else:
            high = mid - 1
    return -1

nums = [1, 3, 4, 7, 9, 12, 15]   # Must be sorted
print(binary_search(nums, 9))    # 4
print(binary_search(nums, 5))    # -1
```

## Sorting Algorithms

# Bubble Sort
--> Repeatedly steps through the list, compares adjacent elements, and swaps them if they are in the wrong order. The largest values "bubble up" to the end with each pass.
--> Time complexity: O(n^2) -- simple but inefficient for large datasets.

```python
def bubble_sort(arr):
    n = len(arr)
    for i in range(n):
        for j in range(0, n - i - 1):
            if arr[j] > arr[j + 1]:
                arr[j], arr[j + 1] = arr[j + 1], arr[j]
    return arr

print(bubble_sort([5, 2, 9, 1, 5, 6]))   # [1, 2, 5, 5, 6, 9]
```

# Selection Sort
--> Repeatedly finds the minimum element from the unsorted portion and swaps it into its correct position at the front.
--> Time complexity: O(n^2).

```python
def selection_sort(arr):
    n = len(arr)
    for i in range(n):
        min_idx = i
        for j in range(i + 1, n):
            if arr[j] < arr[min_idx]:
                min_idx = j
        arr[i], arr[min_idx] = arr[min_idx], arr[i]
    return arr

print(selection_sort([64, 25, 12, 22, 11]))   # [11, 12, 22, 25, 64]
```

# Insertion Sort
--> Builds the sorted list one item at a time, taking each new element and inserting it into its correct position among the already-sorted elements (similar to sorting playing cards in your hand).
--> Time complexity: O(n^2), but fast in practice for small or nearly-sorted datasets.

```python
def insertion_sort(arr):
    for i in range(1, len(arr)):
        key = arr[i]
        j = i - 1
        while j >= 0 and arr[j] > key:
            arr[j + 1] = arr[j]
            j -= 1
        arr[j + 1] = key
    return arr

print(insertion_sort([9, 5, 1, 4, 3]))   # [1, 3, 4, 5, 9]
```

# Quick Sort
--> A "divide and conquer" algorithm -- picks a "pivot" element, partitions the array so smaller elements go left and larger go right, then recursively sorts each side.
--> Time complexity: O(n log n) on average (O(n^2) worst case for a poorly chosen pivot), generally very fast in practice.

```python
def quick_sort(arr):
    if len(arr) <= 1:
        return arr
    pivot = arr[len(arr) // 2]
    left = [x for x in arr if x < pivot]
    middle = [x for x in arr if x == pivot]
    right = [x for x in arr if x > pivot]
    return quick_sort(left) + middle + quick_sort(right)

print(quick_sort([10, 7, 8, 9, 1, 5]))   # [1, 5, 7, 8, 9, 10]
```

# Merge Sort
--> Another "divide and conquer" algorithm -- recursively splits the array into halves until each piece has one element, then merges the sorted halves back together.
--> Time complexity: O(n log n) in all cases, making it more predictable than Quick Sort, at the cost of needing extra memory for merging.

```python
def merge_sort(arr):
    if len(arr) <= 1:
        return arr

    mid = len(arr) // 2
    left = merge_sort(arr[:mid])
    right = merge_sort(arr[mid:])

    return merge(left, right)

def merge(left, right):
    result = []
    i = j = 0
    while i < len(left) and j < len(right):
        if left[i] <= right[j]:
            result.append(left[i])
            i += 1
        else:
            result.append(right[j])
            j += 1
    result.extend(left[i:])
    result.extend(right[j:])
    return result

print(merge_sort([38, 27, 43, 3, 9, 82, 10]))   # [3, 9, 10, 27, 38, 43, 82]
```

# Counting Sort
--> Works only on non-negative integers (or data that can map to a limited range) -- counts how many times each value occurs, then rebuilds the sorted array from those counts.
--> Time complexity: O(n + k), where k is the range of input values -- very fast, but only suitable when k is not much larger than n.

```python
def counting_sort(arr):
    if not arr:
        return arr
    max_val = max(arr)
    count = [0] * (max_val + 1)

    for num in arr:
        count[num] += 1

    sorted_arr = []
    for num, freq in enumerate(count):
        sorted_arr.extend([num] * freq)
    return sorted_arr

print(counting_sort([4, 2, 2, 8, 3, 3, 1]))   # [1, 2, 2, 3, 3, 4, 8]
```

# Radix Sort
--> Sorts integers digit by digit, starting from the least significant digit (rightmost) to the most significant digit (leftmost), using a stable sort (like Counting Sort) at each digit position.
--> Time complexity: O(d * (n + k)), where d is the number of digits -- efficient for sorting large lists of integers.

```python
def counting_sort_by_digit(arr, exp):
    n = len(arr)
    output = [0] * n
    count = [0] * 10

    for num in arr:
        index = (num // exp) % 10
        count[index] += 1

    for i in range(1, 10):
        count[i] += count[i - 1]

    for i in range(n - 1, -1, -1):
        index = (arr[i] // exp) % 10
        output[count[index] - 1] = arr[i]
        count[index] -= 1

    for i in range(n):
        arr[i] = output[i]

def radix_sort(arr):
    if not arr:
        return arr
    max_val = max(arr)
    exp = 1
    while max_val // exp > 0:
        counting_sort_by_digit(arr, exp)
        exp *= 10
    return arr

print(radix_sort([170, 45, 75, 90, 802, 24, 2, 66]))   # [2, 24, 45, 66, 75, 90, 170, 802]
```

