# What Window Functions Do

--> A window function performs a calculation ACROSS a set of related rows (a "window"), while still returning one output row PER INPUT ROW -- unlike `GROUP BY`, which collapses multiple rows down into one summary row per group, a window function keeps every original row intact and just adds a calculated value alongside it.

# The OVER Clause -- Defining the Window

--> Every window function is followed by `OVER (...)`, which defines the set of rows it operates over.

```sql
SELECT
    employee_name,
    department,
    salary,
    AVG(salary) OVER (PARTITION BY department) AS dept_avg_salary
FROM employees;
```

--> `PARTITION BY department` -- splits rows into groups (partitions) by department; the window function is calculated separately WITHIN each partition, but every individual employee row is still returned (unlike `GROUP BY department`, which would collapse this down to one row per department).

# ROW_NUMBER, RANK and DENSE_RANK

--> `ROW_NUMBER()` -- assigns a unique, sequential number to each row within its partition, in the order specified by `ORDER BY` -- ties get arbitrarily different numbers.
--> `RANK()` -- same idea, but ties get the SAME rank, and the next rank after a tie skips ahead (1, 2, 2, 4).
--> `DENSE_RANK()` -- like `RANK()`, but doesn't leave gaps after ties (1, 2, 2, 3).

```sql
SELECT
    employee_name,
    department,
    salary,
    ROW_NUMBER() OVER (PARTITION BY department ORDER BY salary DESC) AS row_num,
    RANK()       OVER (PARTITION BY department ORDER BY salary DESC) AS salary_rank,
    DENSE_RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS dense_rank
FROM employees;
```

--> Classic real-world use -- "find the top 3 highest-paid employees per department":

```sql
SELECT * FROM (
    SELECT
        employee_name, department, salary,
        RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS rnk
    FROM employees
) ranked
WHERE rnk <= 3;
```

# LAG and LEAD -- Comparing to Other Rows

--> `LAG(column, n)` -- looks at the value of `column` from `n` rows BEFORE the current row (within the same partition/order).
--> `LEAD(column, n)` -- the same, but looking `n` rows AHEAD.
--> Extremely common for time-series comparisons -- "how does this month's sales compare to last month's."

```sql
SELECT
    month,
    revenue,
    LAG(revenue, 1) OVER (ORDER BY month) AS previous_month_revenue,
    revenue - LAG(revenue, 1) OVER (ORDER BY month) AS revenue_change
FROM monthly_sales;
```

# Running Totals with SUM OVER

```sql
SELECT
    order_date,
    amount,
    SUM(amount) OVER (ORDER BY order_date) AS running_total
FROM orders;
```

--> Without a `PARTITION BY`, and with an `ORDER BY` inside the window, `SUM() OVER` accumulates a running total row by row, instead of one grand total across the whole table.

# FIRST_VALUE and NTILE

--> `FIRST_VALUE(column) OVER (...)` -- returns the first value in the ordered window for every row in it (e.g. showing each employee's department's top salary alongside their own row).
--> `NTILE(n)` -- divides rows into `n` roughly equal-sized buckets, useful for building percentile/quartile groupings (e.g. splitting customers into 4 spending quartiles).

# Why This Matters

--> Before window functions, "top N per group" or running totals required awkward self-joins or subqueries -- window functions express these extremely common analytical queries far more simply and efficiently, and are a frequent focus in SQL technical interviews.
