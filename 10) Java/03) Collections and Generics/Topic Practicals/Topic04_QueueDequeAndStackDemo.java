/*
 * Topic04_QueueDequeAndStackDemo.java
 *
 * Demonstrates:
 *   1. Queue interface -- throwing methods vs special-value methods (add/offer, remove/poll, element/peek)
 *   2. PriorityQueue -- min-heap default ordering, max-heap via Comparator, custom object ordering
 *   3. PriorityQueue's iterator does NOT return sorted order -- only repeated poll() does
 *   4. Deque -- double-ended insertion/removal, used as both stack and queue
 *   5. ArrayDeque as the modern replacement for both Stack and LinkedList-as-Queue
 *   6. Legacy java.util.Stack and its List-inherited encapsulation flaw
 *   7. Real use cases: balanced-parentheses check (stack), simple BFS layer processing (queue),
 *      "K smallest elements" (PriorityQueue), sliding-window maximum (monotonic Deque)
 *
 * Covers Theory chapter:
 *   10) Java/03) Collections and Generics/Theory/04 Queue Deque and Stack.md
 *
 * Compile & run:
 *   javac Topic04_QueueDequeAndStackDemo.java
 *   java Topic04_QueueDequeAndStackDemo
 */

import java.util.*;

public class Topic04_QueueDequeAndStackDemo {

    public static void main(String[] args) {
        demoQueueThrowingVsSpecialValueMethods();
        demoPriorityQueueOrdering();
        demoPriorityQueueIteratorIsNotSorted();
        demoDequeBothEnds();
        demoArrayDequeAsStackAndQueue();
        demoLegacyStackFlaw();
        demoRealUseCases();
        System.out.println("\nAll Queue, Deque and Stack demos completed.");
    }

    // -----------------------------------------------------------------
    // 1) Queue -- throwing methods vs special-value methods
    // -----------------------------------------------------------------
    private static void demoQueueThrowingVsSpecialValueMethods() {
        printSection("1) Queue -- throws-on-failure vs returns-special-value");

        Queue<Integer> queue = new LinkedList<>();
        System.out.println("offer(1), offer(2), offer(3): " + queue.offer(1) + ", " + queue.offer(2) + ", " + queue.offer(3));
        System.out.println("Queue now: " + queue);
        System.out.println("peek() [no removal]: " + queue.peek());
        System.out.println("poll() [removes head]: " + queue.poll());
        System.out.println("Queue now: " + queue);

        // Draining with special-value methods -- no exception handling needed
        while (!queue.isEmpty()) {
            System.out.println("  drained via poll(): " + queue.poll());
        }
        System.out.println("poll() on empty queue returns: " + queue.poll());   // null, no exception

        try {
            queue.element();          // throws on empty
        } catch (NoSuchElementException e) {
            System.out.println("element() on empty queue threw NoSuchElementException as expected");
        }
        try {
            queue.remove();           // throws on empty
        } catch (NoSuchElementException e) {
            System.out.println("remove() on empty queue threw NoSuchElementException as expected");
        }
    }

    // -----------------------------------------------------------------
    // 2) PriorityQueue -- min-heap, max-heap, and custom object ordering
    // -----------------------------------------------------------------
    private static void demoPriorityQueueOrdering() {
        printSection("2) PriorityQueue -- min-heap default, max-heap, and custom ordering");

        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        minHeap.offer(5);
        minHeap.offer(1);
        minHeap.offer(3);
        minHeap.offer(2);
        System.out.print("Min-heap poll order: ");
        while (!minHeap.isEmpty()) {
            System.out.print(minHeap.poll() + " ");
        }
        System.out.println("(always smallest first)");

        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
        maxHeap.offer(5);
        maxHeap.offer(1);
        maxHeap.offer(3);
        System.out.print("Max-heap poll order: ");
        while (!maxHeap.isEmpty()) {
            System.out.print(maxHeap.poll() + " ");
        }
        System.out.println("(always largest first)");

        // Custom object ordering via Comparator
        PriorityQueue<Task> tasks = new PriorityQueue<>(Comparator.comparingInt(t -> t.priority));
        tasks.offer(new Task("low", 5));
        tasks.offer(new Task("urgent", 1));
        tasks.offer(new Task("medium", 3));
        System.out.print("Task queue poll order (lowest priority number = most urgent): ");
        while (!tasks.isEmpty()) {
            System.out.print(tasks.poll().name + " ");
        }
        System.out.println();

        // peek() is O(1) -- always the array's first slot, no removal
        PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(9, 2, 7));
        System.out.println("peek() without removing: " + pq.peek() + ", size still: " + pq.size());
    }

    // -----------------------------------------------------------------
    // 3) PriorityQueue's iterator does NOT give sorted order
    // -----------------------------------------------------------------
    private static void demoPriorityQueueIteratorIsNotSorted() {
        printSection("3) PriorityQueue iterator vs poll() -- NOT the same order");

        PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(5, 1, 4, 2, 3, 9, 0));
        System.out.print("for-each over the queue directly (internal heap array order, NOT sorted): ");
        for (int n : pq) {
            System.out.print(n + " ");
        }
        System.out.println();

        System.out.print("Repeated poll() on a copy (this IS priority order): ");
        PriorityQueue<Integer> copy = new PriorityQueue<>(pq);
        while (!copy.isEmpty()) {
            System.out.print(copy.poll() + " ");
        }
        System.out.println();
        System.out.println("-> Only poll() guarantees priority order; iterating just walks heap storage order.");
    }

    // -----------------------------------------------------------------
    // 4) Deque -- double-ended insertion/removal
    // -----------------------------------------------------------------
    private static void demoDequeBothEnds() {
        printSection("4) Deque -- insertion/removal at both ends");

        Deque<Integer> deque = new ArrayDeque<>();
        deque.addFirst(1);        // [1]
        deque.addLast(2);         // [1, 2]
        deque.addFirst(0);        // [0, 1, 2]
        System.out.println("Deque after addFirst(1), addLast(2), addFirst(0): " + deque);
        System.out.println("peekFirst(): " + deque.peekFirst() + ", peekLast(): " + deque.peekLast());
        deque.pollFirst();
        deque.pollLast();
        System.out.println("After pollFirst() and pollLast(): " + deque);
    }

    // -----------------------------------------------------------------
    // 5) ArrayDeque as both Stack (LIFO) and Queue (FIFO)
    // -----------------------------------------------------------------
    private static void demoArrayDequeAsStackAndQueue() {
        printSection("5) ArrayDeque -- modern default for stack AND queue");

        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(1);        // push/pop alias addFirst/removeFirst -- LIFO
        stack.push(2);
        stack.push(3);
        System.out.println("Stack usage -- pop(): " + stack.pop() + " (LIFO -- last pushed comes out first)");
        System.out.println("Stack usage -- peek() after one pop: " + stack.peek());

        Deque<Integer> asQueue = new ArrayDeque<>();
        asQueue.offer(1);      // offer/poll alias addLast/removeFirst -- FIFO
        asQueue.offer(2);
        asQueue.offer(3);
        System.out.println("Queue usage -- poll(): " + asQueue.poll() + " (FIFO -- first offered comes out first)");

        // ArrayDeque disallows null -- avoids ambiguity with poll()'s "empty means null" signal
        try {
            Deque<String> noNulls = new ArrayDeque<>();
            noNulls.add(null);
        } catch (NullPointerException e) {
            System.out.println("ArrayDeque.add(null) threw NullPointerException as expected (unlike LinkedList)");
        }

        // ArrayDeque has no get(index) -- it's not a List, purely a deque/stack/queue contract
        System.out.println("ArrayDeque does not implement List -- no get(index) method exists at all.");
    }

    // -----------------------------------------------------------------
    // 6) Legacy java.util.Stack -- extends Vector, breaks encapsulation
    // -----------------------------------------------------------------
    @SuppressWarnings("deprecation")
    private static void demoLegacyStackFlaw() {
        printSection("6) Legacy Stack class -- extends Vector, encapsulation flaw");

        Stack<Integer> legacyStack = new Stack<>();
        legacyStack.push(1);
        legacyStack.push(2);
        System.out.println("Legacy Stack after push(1), push(2): " + legacyStack + ", pop(): " + legacyStack.pop());

        // The core design flaw: Stack IS-A Vector, so all List methods are still legally callable
        legacyStack.add(0, 99);      // legal! inserts at the BOTTOM, silently corrupting LIFO order
        System.out.println("After illegally calling add(0, 99) [a List method]: " + legacyStack
                + "  <- bottom of the \"stack\" was corrupted, nothing prevented this");

        // Modern replacement -- no List methods to misuse
        Deque<Integer> modernStack = new ArrayDeque<>();
        modernStack.push(1);
        modernStack.push(2);
        modernStack.pop();
        System.out.println("Modern replacement (ArrayDeque) has no add(index, e) to accidentally call.");
    }

    // -----------------------------------------------------------------
    // 7) Real-world use cases
    // -----------------------------------------------------------------
    private static void demoRealUseCases() {
        printSection("7) Real use cases");

        // 7a) Stack -- balanced parentheses checker
        System.out.println("7a) Balanced parentheses check using a Deque as a stack:");
        System.out.println("  \"(a(b)[c]{d})\" balanced? " + isBalanced("(a(b)[c]{d})"));
        System.out.println("  \"(a(b][c)\" balanced?     " + isBalanced("(a(b][c)"));

        // 7b) Queue -- simple BFS-style level processing on a small graph
        System.out.println("7b) BFS traversal order using ArrayDeque as a FIFO queue:");
        Map<Integer, List<Integer>> graph = Map.of(
                1, List.of(2, 3),
                2, List.of(4),
                3, List.of(4),
                4, List.of());
        System.out.println("  BFS from node 1: " + bfs(graph, 1));

        // 7c) PriorityQueue -- K smallest elements
        System.out.println("7c) K smallest elements via a max-heap of size K:");
        int[] data = {7, 10, 4, 3, 20, 15};
        System.out.println("  3 smallest of " + Arrays.toString(data) + " -> " + kSmallest(data, 3));

        // 7d) Deque -- sliding window maximum (monotonic deque pattern)
        System.out.println("7d) Sliding window maximum via a monotonic Deque:");
        int[] window = {1, 3, -1, -3, 5, 3, 6, 7};
        System.out.println("  window=3 over " + Arrays.toString(window) + " -> " + slidingWindowMax(window, 3));
    }

    private static boolean isBalanced(String s) {
        Deque<Character> stack = new ArrayDeque<>();
        Map<Character, Character> pairs = Map.of(')', '(', ']', '[', '}', '{');
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                stack.push(c);
            } else if (pairs.containsKey(c)) {
                if (stack.isEmpty() || stack.pop() != pairs.get(c)) {
                    return false;
                }
            }
        }
        return stack.isEmpty();
    }

    private static List<Integer> bfs(Map<Integer, List<Integer>> graph, int start) {
        List<Integer> order = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new ArrayDeque<>();
        queue.offer(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            int node = queue.poll();
            order.add(node);
            for (int neighbor : graph.getOrDefault(node, List.of())) {
                if (visited.add(neighbor)) {       // Set.add() returns false if already present
                    queue.offer(neighbor);
                }
            }
        }
        return order;
    }

    private static List<Integer> kSmallest(int[] data, int k) {
        // Max-heap of size k -- keeps the k smallest seen so far, evicting the current largest when full
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
        for (int n : data) {
            maxHeap.offer(n);
            if (maxHeap.size() > k) {
                maxHeap.poll();                    // discard the largest -- keeps only k smallest
            }
        }
        List<Integer> result = new ArrayList<>(maxHeap);
        Collections.sort(result);
        return result;
    }

    private static List<Integer> slidingWindowMax(int[] nums, int windowSize) {
        // Monotonic deque holds INDICES, kept in decreasing order of their values
        Deque<Integer> monoDeque = new ArrayDeque<>();
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < nums.length; i++) {
            // Drop indices that fall out of the current window from the front
            while (!monoDeque.isEmpty() && monoDeque.peekFirst() <= i - windowSize) {
                monoDeque.pollFirst();
            }
            // Drop smaller values from the back -- they can never be the max while nums[i] is in the window
            while (!monoDeque.isEmpty() && nums[monoDeque.peekLast()] < nums[i]) {
                monoDeque.pollLast();
            }
            monoDeque.addLast(i);
            if (i >= windowSize - 1) {
                result.add(nums[monoDeque.peekFirst()]);   // front of deque is always the current max's index
            }
        }
        return result;
    }

    private static class Task {
        final String name;
        final int priority;
        Task(String name, int priority) { this.name = name; this.priority = priority; }
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
