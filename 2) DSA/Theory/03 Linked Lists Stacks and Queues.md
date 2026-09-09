# Linked Lists -- Trading Index Access for Flexible Insertion

--> Unlike an array's contiguous memory (covered in the Arrays file), a linked list stores elements as separate NODES scattered anywhere in memory, each holding a value and a POINTER to the next node -- there's no single contiguous block, so there's no way to jump directly to "element 5" the way array indexing does.

```text
Array:        [ 10 | 20 | 30 | 40 ]   -- contiguous, index i = direct memory offset

Linked List:   [10|next] --> [20|next] --> [30|next] --> [40|None]
               (nodes can live anywhere in memory, connected only by pointers)
```

```python
class Node:
    def __init__(self, value):
        self.value = value
        self.next = None

class LinkedList:
    def __init__(self):
        self.head = None

    def append(self, value):          # O(n) without a tail pointer -- must walk to the end first
        new_node = Node(value)
        if not self.head:
            self.head = new_node
            return
        current = self.head
        while current.next:
            current = current.next
        current.next = new_node

    def prepend(self, value):          # O(1) -- no walking needed, just relink the head
        new_node = Node(value)
        new_node.next = self.head
        self.head = new_node
```

# Singly vs Doubly vs Circular Linked Lists

--> **Singly linked list** -- each node points only FORWARD to the next node -- shown above. Traversing backward requires restarting from the head.
--> **Doubly linked list** -- each node ALSO holds a `prev` pointer back to the previous node -- allows O(1) backward traversal and O(1) removal of a node once you already have a reference to it (a singly linked list needs to walk from the head to find the PREVIOUS node before it can unlink the target).
--> **Circular linked list** -- the last node's `next` points back to the FIRST node instead of `None` -- useful for representing a naturally cyclic sequence (a round-robin turn order, a fixed-size circular buffer).

```text
Doubly linked list:   None <-- [10] <--> [20] <--> [30] --> None
                                 prev/next pointers in both directions

Circular linked list: [10] --> [20] --> [30] --+
                        ^-----------------------+   (30's next points back to 10)
```

# Array vs Linked List -- The Real Trade-off

```text
Operation                Array              Linked List
Access by index           O(1)               O(n)
Insert/delete at start    O(n)               O(1)
Insert/delete at end      O(1) amortized     O(1) with tail pointer, else O(n)
Insert/delete in middle   O(n)               O(n) to FIND the position, O(1) to actually link once there
Memory overhead           low (just values)  higher (each node also stores pointer(s))
Cache locality            good (contiguous)  poor (nodes scattered across memory)
```

--> **Why linked lists lose in practice more often than the Big-O table alone suggests** -- modern CPUs are heavily optimized around cache locality (accessing nearby memory addresses is dramatically faster than jumping around) -- an array's contiguous layout plays to this strength; a linked list's scattered nodes cause frequent cache misses, which is exactly why array-backed dynamic arrays (Python lists, `ArrayList`) are the default choice in most real-world code even for operations where a linked list's Big-O looks theoretically better.
--> **Where linked lists genuinely win** -- when you already hold a reference to a specific node and need to insert/delete AT that exact point repeatedly (an LRU cache's internal structure, certain graph adjacency representations), or when the data structure needs to avoid ever reallocating/copying (a linked list never needs the "resize and copy everything" step a dynamic array occasionally pays).

# The Fast/Slow Pointer Technique (Floyd's Cycle Detection)

```python
def has_cycle(head):
    slow, fast = head, head
    while fast and fast.next:
        slow = slow.next          # moves 1 step
        fast = fast.next.next      # moves 2 steps
        if slow == fast:            # they've met -- there's a cycle
            return True
    return False                    # fast reached the end -- no cycle
```

--> If there's a cycle, `fast` (moving twice as fast) is guaranteed to eventually LAP `slow` and land on the same node -- if there's no cycle, `fast` simply reaches the end (`None`) first. This runs in O(n) time and O(1) extra space, compared to the O(n) SPACE a hash-set-based "have I seen this node before" approach would need -- a classic example of a clever pointer technique beating a straightforward one on space.

# Stacks -- Last In, First Out (LIFO)

--> A stack only allows access to the TOP element -- push adds to the top, pop removes from the top, both O(1). Think of a physical stack of plates: you can only add or remove from the top.

```python
stack = []
stack.append(10)     # push -- O(1)
stack.append(20)
stack.append(30)
top = stack.pop()      # pop -- O(1), removes and returns 30 (the most recently added)
```

--> **Canonical use cases** -- undo/redo functionality (each action pushed, undo pops the most recent), function call tracking (the call stack itself, directly connecting to the Recursion file's stack-space discussion), balanced-parentheses/bracket validation, and depth-first traversal (covered in the Trees and Graphs files, where DFS is either implemented with explicit recursion -- using the language's own call stack -- or an explicit stack data structure to simulate it iteratively).

```python
def is_balanced(s):
    stack = []
    pairs = {')': '(', ']': '[', '}': '{'}
    for char in s:
        if char in '([{':
            stack.append(char)
        elif char in ')]}':
            if not stack or stack.pop() != pairs[char]:
                return False
    return not stack        # must be empty -- every opening bracket found its match
```

# Queues -- First In, First Out (FIFO)

--> A queue only allows adding at the BACK (enqueue) and removing from the FRONT (dequeue) -- like a real-world line/queue of people, whoever arrived first leaves first.

```python
from collections import deque

queue = deque()
queue.append(10)        # enqueue -- O(1)
queue.append(20)
queue.append(30)
front = queue.popleft()   # dequeue -- O(1), removes and returns 10 (the earliest added)
```

--> **Why a plain Python `list` is a poor queue** -- `list.pop(0)` (removing from the front) is `O(n)`, since every remaining element must shift left by one -- `collections.deque` is specifically implemented as a doubly linked list of blocks internally, giving true `O(1)` operations at BOTH ends, which is exactly why it's the standard choice for queue behavior in Python.
--> **Canonical use cases** -- breadth-first traversal (covered in the Trees and Graphs files -- BFS visits nodes in the order they were discovered, which is precisely FIFO order), task scheduling (a print queue, a job queue processed in arrival order), and buffering data between a fast producer and a slower consumer.

# Deque (Double-Ended Queue)

--> A generalization allowing O(1) insertion/removal at BOTH ends -- a deque can act as a stack, a queue, or both simultaneously, which is exactly why `collections.deque` (shown above) is used for both patterns in Python rather than having two separate specialized structures.

```python
dq = deque([10, 20, 30])
dq.appendleft(5)     # O(1) -- add to the front
dq.append(40)          # O(1) -- add to the back
dq.popleft()            # O(1) -- remove from the front
dq.pop()                 # O(1) -- remove from the back
```

--> **A key sliding-window pattern -- the monotonic deque** -- maintaining a deque where values are kept in sorted (increasing or decreasing) order by discarding elements from the back that can never be useful again, used to solve "maximum in every sliding window" in O(n) total instead of the naive O(n*k) of scanning every window from scratch (directly connecting to the Sliding Window discussion in the Arrays file).
