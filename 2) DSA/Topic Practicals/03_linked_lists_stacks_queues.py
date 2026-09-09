"""
03_linked_lists_stacks_queues.py

Implements and tests:
    1. Singly linked list with append/prepend/to_list
    2. Reversing a linked list in-place
    3. Floyd's cycle detection (fast/slow pointers)
    4. Balanced-brackets validation using a stack
    5. Level-order-style FIFO processing using a queue (collections.deque)

Covers Theory chapter:
    2) DSA/Theory/03 Linked Lists Stacks and Queues.md

Run:  python 03_linked_lists_stacks_queues.py
"""

from collections import deque


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


# ---------------------------------------------------------------------------
# 1) Singly linked list
# ---------------------------------------------------------------------------

class Node:
    def __init__(self, value):
        self.value = value
        self.next = None


class LinkedList:
    def __init__(self):
        self.head = None

    def append(self, value):
        new_node = Node(value)
        if not self.head:
            self.head = new_node
            return
        current = self.head
        while current.next:
            current = current.next
        current.next = new_node

    def prepend(self, value):
        new_node = Node(value)
        new_node.next = self.head
        self.head = new_node

    def to_list(self):
        result = []
        current = self.head
        while current:
            result.append(current.value)
            current = current.next
        return result


def reverse_linked_list(head):
    prev = None
    current = head
    while current:
        next_node = current.next     # save before overwriting
        current.next = prev           # flip the pointer
        prev = current
        current = next_node
    return prev                        # new head


def demo_linked_list_basics() -> None:
    print_section("1) & 2) Linked list append/prepend, then in-place reversal")
    ll = LinkedList()
    for v in [1, 2, 3]:
        ll.append(v)
    ll.prepend(0)
    print(f"After append(1,2,3) then prepend(0): {ll.to_list()}")
    assert ll.to_list() == [0, 1, 2, 3]

    ll.head = reverse_linked_list(ll.head)
    print(f"After reversing in-place:            {ll.to_list()}")
    assert ll.to_list() == [3, 2, 1, 0]
    print("Assertions passed.")


# ---------------------------------------------------------------------------
# 3) Floyd's cycle detection
# ---------------------------------------------------------------------------

def has_cycle(head):
    slow, fast = head, head
    while fast and fast.next:
        slow = slow.next
        fast = fast.next.next
        if slow == fast:
            return True
    return False


def demo_cycle_detection() -> None:
    print_section("3) Floyd's cycle detection (fast/slow pointers)")
    # Build a clean list: 1 -> 2 -> 3 -> None
    n1, n2, n3 = Node(1), Node(2), Node(3)
    n1.next, n2.next = n2, n3
    print(f"Acyclic list 1->2->3: has_cycle = {has_cycle(n1)}")
    assert has_cycle(n1) is False

    # Now make it cyclic: 1 -> 2 -> 3 -> back to 2
    n3.next = n2
    print(f"After linking 3 back to 2 (cycle): has_cycle = {has_cycle(n1)}")
    assert has_cycle(n1) is True
    print("Assertions passed.")


# ---------------------------------------------------------------------------
# 4) Stack -- balanced brackets
# ---------------------------------------------------------------------------

def is_balanced(s):
    stack = []
    pairs = {')': '(', ']': '[', '}': '{'}
    for char in s:
        if char in '([{':
            stack.append(char)
        elif char in ')]}':
            if not stack or stack.pop() != pairs[char]:
                return False
    return not stack


def demo_balanced_brackets() -> None:
    print_section("4) Balanced brackets validation using a stack")
    cases = ["({[]})", "([)]", "((()))", "(()", ""]
    for case in cases:
        print(f"  '{case}'  -> balanced = {is_balanced(case)}")
    assert is_balanced("({[]})") is True
    assert is_balanced("([)]") is False
    assert is_balanced("(()") is False
    assert is_balanced("") is True
    print("Assertions passed.")


# ---------------------------------------------------------------------------
# 5) Queue -- FIFO task processing with deque
# ---------------------------------------------------------------------------

def demo_queue_fifo_processing() -> None:
    print_section("5) FIFO task processing with collections.deque")
    task_queue = deque(["task-A", "task-B", "task-C"])
    processed = []
    while task_queue:
        task = task_queue.popleft()      # O(1) -- dequeue from the front
        processed.append(task)
        print(f"  Processing {task}...")
    print(f"Processed order: {processed}")
    assert processed == ["task-A", "task-B", "task-C"]     # FIFO -- first added, first processed
    print("Assertion passed -- confirms FIFO order.")


if __name__ == "__main__":
    demo_linked_list_basics()
    demo_cycle_detection()
    demo_balanced_brackets()
    demo_queue_fifo_processing()
    print("\nAll Linked List/Stack/Queue demos completed.")
