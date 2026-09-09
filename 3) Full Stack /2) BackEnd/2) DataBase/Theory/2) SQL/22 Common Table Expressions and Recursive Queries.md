# What a CTE Is

--> A Common Table Expression (CTE), defined with `WITH`, creates a named, temporary result set that can be referenced later in the same query -- like defining a readable, reusable variable for a subquery, instead of nesting subqueries or repeating the same subquery multiple times.

```sql
WITH high_earners AS (
    SELECT employee_name, department, salary
    FROM employees
    WHERE salary > 80000
)
SELECT department, COUNT(*) AS high_earner_count
FROM high_earners
GROUP BY department;
```

--> The CTE only exists for the duration of that one query -- it isn't a permanent object like a View (which persists across queries/sessions).

# CTEs vs Subqueries -- Why Prefer Them

--> Functionally, a CTE and an equivalent nested subquery often produce the same result -- the real benefit is READABILITY: a CTE gives a meaningful name to an intermediate result and reads top-to-bottom, instead of nesting logic several levels deep inside parentheses.

```sql
-- Without a CTE -- nested subquery, harder to read
SELECT department, COUNT(*)
FROM (SELECT employee_name, department, salary FROM employees WHERE salary > 80000) AS high_earners
GROUP BY department;
```

# Multiple CTEs in One Query

--> A single `WITH` clause can define several CTEs, and later ones can even reference earlier ones -- letting a complex query be broken into clearly named, sequential logical steps.

```sql
WITH regional_sales AS (
    SELECT region, SUM(amount) AS total_sales
    FROM orders
    GROUP BY region
),
top_regions AS (
    SELECT region FROM regional_sales WHERE total_sales > 100000
)
SELECT * FROM orders WHERE region IN (SELECT region FROM top_regions);
```

# Recursive CTEs -- Querying Hierarchical Data

--> A recursive CTE references ITSELF, letting you query hierarchical/tree-shaped data (an org chart, a category tree, a bill-of-materials) that a normal `JOIN` can't traverse an unknown number of levels deep.
--> Structure -- an "anchor" query (the starting point) `UNION ALL`'d with a "recursive" query (which references the CTE's own name, repeating until it produces no more new rows).

```sql
WITH RECURSIVE employee_hierarchy AS (
    -- Anchor: start with the top-level manager (no manager_id)
    SELECT employee_id, employee_name, manager_id, 1 AS level
    FROM employees
    WHERE manager_id IS NULL

    UNION ALL

    -- Recursive: find each next level's direct reports
    SELECT e.employee_id, e.employee_name, e.manager_id, eh.level + 1
    FROM employees e
    JOIN employee_hierarchy eh ON e.manager_id = eh.employee_id
)
SELECT * FROM employee_hierarchy ORDER BY level;
```

--> This single query returns the ENTIRE org chart, correctly labeled with each person's depth level, regardless of how many management layers deep the hierarchy actually goes -- something impossible to express with a fixed number of `JOIN`s since the depth isn't known in advance.

# Common Recursive CTE Use Cases

--> Org charts / employee-manager hierarchies.
--> Category trees (a category containing subcategories containing sub-subcategories).
--> Generating a sequence of numbers or dates without a source table.

```sql
WITH RECURSIVE numbers AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM numbers WHERE n < 10
)
SELECT * FROM numbers;
```

--> Always include a terminating condition (`WHERE n < 10` above) in the recursive part -- without one, the recursion never stops and the query runs indefinitely.
