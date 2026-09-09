# Why This File Exists

--> The earlier files in this folder cover the fundamentals -- Excel basics (file 02), visualization tools (file 03), core statistics and hypothesis testing (file 04), and randomized experimentation (file 05). In practice, a working analyst spends most days one level deeper than any of that: writing SQL with window functions instead of single-table `SELECT`s, building Excel models that do more than `SUMIF`, translating a business question into a defensible metric BEFORE ever running a test, and reaching for a different statistical test the moment the tidy assumptions from file 04 don't hold. This file fills in that practical depth.

# SQL for Analysts -- Window Functions

--> A window function computes a value ACROSS a set of rows related to the current row, WITHOUT collapsing them into a single output row the way `GROUP BY` does -- you keep every original row and simply add a calculated column alongside it.

```sql
SELECT
    customer_id,
    order_date,
    amount,
    ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY order_date) AS order_sequence,
    RANK()       OVER (PARTITION BY customer_id ORDER BY amount DESC) AS amount_rank,
    LAG(amount)  OVER (PARTITION BY customer_id ORDER BY order_date) AS previous_order_amount
FROM orders;
```

--> `PARTITION BY` resets the calculation within each group (each customer here) -- conceptually similar to a `GROUP BY`, except the underlying rows survive instead of collapsing.
--> `ROW_NUMBER()` -- a unique, gapless sequence per partition -- useful for "give me each customer's first order" (`WHERE order_sequence = 1`). `RANK()` -- like `ROW_NUMBER()`, but ties share the same rank (two orders of the exact same amount both rank `1`, and the next distinct amount jumps to rank `3`).
--> `LAG()`/`LEAD()` -- pull a value from a PREVIOUS or NEXT row within the same partition, without a self-join -- the standard way to compute "change from last order," "days since previous purchase," or a month-over-month delta directly in SQL.

# Running Totals -- the Analyst's Most Common Window Function Use Case

```sql
SELECT
    order_date,
    daily_revenue,
    SUM(daily_revenue) OVER (ORDER BY order_date) AS running_total_revenue,
    AVG(daily_revenue) OVER (ORDER BY order_date ROWS BETWEEN 6 PRECEDING AND CURRENT ROW) AS rolling_7day_avg
FROM daily_sales
ORDER BY order_date;
```

--> `SUM(...) OVER (ORDER BY order_date)` with no `PARTITION BY` treats the entire result set as one window, accumulating every prior row's value into the current row -- exactly a running/cumulative total, the SQL equivalent of Excel's `=SUM($A$1:A1)` absolute-reference trick.
--> The explicit frame `ROWS BETWEEN 6 PRECEDING AND CURRENT ROW` restricts the window to the current row plus the 6 before it (7 rows total) -- a rolling 7-day average, directly analogous to the `.rolling(7)` pandas method covered in the Data Science folder's Time Series file, just computed in the warehouse instead of in Python.
--> Without an explicit frame, `ORDER BY` inside `OVER()` defaults to `RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW` -- everything up to and including the current row -- which is exactly what makes the plain running-total version above work with no extra frame clause needed.

# CTEs -- Common Table Expressions

--> A CTE (`WITH ... AS (...)`) names a subquery's result so it can be referenced later in the same statement -- turning a deeply nested, hard-to-read query into a readable sequence of named, logical steps.

```sql
WITH monthly_revenue AS (
    SELECT
        DATE_TRUNC('month', order_date) AS month,
        customer_id,
        SUM(amount) AS revenue
    FROM orders
    GROUP BY 1, 2
),
customer_totals AS (
    SELECT customer_id, SUM(revenue) AS lifetime_revenue
    FROM monthly_revenue
    GROUP BY customer_id
)
SELECT c.customer_id, c.lifetime_revenue, m.month, m.revenue
FROM customer_totals c
JOIN monthly_revenue m ON c.customer_id = m.customer_id
WHERE c.lifetime_revenue > 1000
ORDER BY c.lifetime_revenue DESC;
```

--> Each CTE reads top-to-bottom like a plan: "first compute monthly revenue per customer, then roll that up to lifetime revenue, then join the two back together and filter to high-value customers" -- the same query written as one nested subquery would bury that logic several parentheses deep and be far harder for a reviewer (or future you) to follow.
--> CTEs are NOT automatically materialized/cached in every engine -- some warehouses re-run a CTE referenced multiple times, so for a genuinely expensive intermediate result, an actual temp table may still outperform a CTE at scale; but for readability and one-time use, CTEs are almost always the right default over nested subqueries.

# Subqueries -- Correlated vs Uncorrelated

--> An **uncorrelated subquery** runs completely independently of the outer query and could be executed on its own.

```sql
-- Uncorrelated: the inner query doesn't reference anything from the outer query
SELECT customer_id, amount
FROM orders
WHERE amount > (SELECT AVG(amount) FROM orders);   -- orders above the overall average
```

--> A **correlated subquery** references a column from the OUTER query -- it effectively re-runs once per outer row, which makes it powerful but potentially slow on large tables.

```sql
-- Correlated: "AVG(amount)" here is recomputed per customer_id, referencing the outer row
SELECT o.customer_id, o.amount
FROM orders o
WHERE o.amount > (
    SELECT AVG(o2.amount) FROM orders o2 WHERE o2.customer_id = o.customer_id
);   -- orders above THAT SAME CUSTOMER's own average, not the overall average
```

--> The window function version of the correlated-subquery example above is almost always preferable in a modern warehouse -- `amount - AVG(amount) OVER (PARTITION BY customer_id)` computes the identical "above own average" comparison in a single pass, without the per-row re-execution cost a correlated subquery implies.

# A Real Cohort Retention Table in SQL

--> Cohort analysis groups users by the period they FIRST did something (signed up, made a first purchase) and tracks a behavior across the periods that follow -- the standard way product and growth teams answer "do users who joined in a given month keep coming back."

```sql
WITH first_purchase AS (
    SELECT customer_id, DATE_TRUNC('month', MIN(order_date)) AS cohort_month
    FROM orders
    GROUP BY customer_id
),
activity AS (
    SELECT
        f.customer_id,
        f.cohort_month,
        DATE_TRUNC('month', o.order_date) AS activity_month,
        DATE_PART('month', AGE(DATE_TRUNC('month', o.order_date), f.cohort_month)) AS months_since_first
    FROM orders o
    JOIN first_purchase f ON o.customer_id = f.customer_id
)
SELECT
    cohort_month,
    months_since_first,
    COUNT(DISTINCT customer_id) AS active_customers
FROM activity
GROUP BY 1, 2
ORDER BY 1, 2;
```

--> Pivoting this long-format result into a wide cohort grid (cohort month as rows, `months_since_first` as columns, active-customer count as values) -- typically done in a BI tool or a spreadsheet Pivot Table (file 02) rather than in SQL itself -- produces the classic triangular retention table where each row's counts shrink moving right, visually exposing exactly how fast each signup cohort decays.
--> Dividing every column by the cohort's own `months_since_first = 0` count converts raw counts into a retention PERCENTAGE, which is what actually makes different cohorts (a January cohort of 500 users vs a July cohort of 3,000) comparable to each other.

# Excel Depth -- Power Pivot and the Data Model

--> Power Pivot lets Excel hold MULTIPLE related tables in memory (a "Data Model") and build relationships between them by key column -- exactly the same relational idea as a SQL `JOIN`, but expressed by drawing a relationship line between two tables' key columns instead of writing a `JOIN` clause.
--> This solves the single biggest limitation of plain `VLOOKUP`-based workbooks (file 02) -- rather than one flat, wide, VLOOKUP-stitched sheet, Power Pivot keeps `orders`, `customers`, and `products` as separate, properly related tables and lets a Pivot Table pull fields from all of them at once, the spreadsheet equivalent of the star schema fact/dimension design covered in the Data Engineering folder's Data Modeling file.
--> **DAX (Data Analysis Expressions)** is Power Pivot's formula language for defining calculated "measures" across the data model.

```
Total Revenue := SUM(Orders[amount])
Revenue Last Month := CALCULATE([Total Revenue], DATESINPERIOD(Calendar[date], LASTDATE(Calendar[date]), -1, MONTH))
% of Total := DIVIDE([Total Revenue], CALCULATE([Total Revenue], ALL(Orders)))
```

--> `CALCULATE` re-evaluates a measure under a modified filter context (a different date range, ignoring a filter with `ALL(...)`) -- the single most important DAX function, and the direct analog of adding/removing a `WHERE` clause in SQL.

# Array Formulas

--> An array formula performs a calculation across an entire range/array of values in one formula, rather than one cell at a time -- letting a single formula do what would otherwise require a helper column.

```
{=SUM((A2:A100="East")*(B2:B100>100)*C2:C100)}
-- Sums column C where region is "East" AND the value in B exceeds 100 -- a two-condition
-- SUMIF that plain SUMIFS also handles, but array formulas generalize to conditions SUMIFS can't express.

=SUMPRODUCT((A2:A100="East")*(B2:B100="Closed")*C2:C100)
-- SUMPRODUCT achieves the same multi-condition arithmetic without needing Ctrl+Shift+Enter,
-- and is the more commonly recommended modern approach for this exact pattern.
```

--> Modern Excel (with "dynamic arrays") spills a formula's array result automatically across adjacent cells without the legacy `{Ctrl+Shift+Enter}` syntax -- functions like `UNIQUE()`, `FILTER()`, and `SORT()` return whole arrays natively, which is gradually replacing older array-formula idioms for many use cases.

# Goal Seek, Scenario Manager and Solver -- "What-If" Analysis

--> **Goal Seek** works a formula BACKWARD -- given a target output value, it finds the single input value that produces it. Example: "what price would this product need to sell at to hit exactly $50,000 in monthly revenue, given my current unit-volume forecast?" -- Goal Seek iterates the price input until the revenue formula lands on $50,000.
--> **Scenario Manager** stores several named sets of input assumptions (e.g. "Best Case," "Base Case," "Worst Case," each with its own growth rate, churn rate, and cost inputs) and lets you flip between them, instantly recalculating every dependent formula -- the spreadsheet-native way to present a range of business outcomes rather than a single fragile point estimate.
--> **Solver** generalizes Goal Seek to MULTIPLE changing inputs and actual constraints, optimizing an objective (maximize profit, minimize cost) subject to limits (a marketing budget cap, a maximum headcount) -- e.g. finding the ad-spend allocation across five channels that maximizes total conversions without exceeding a fixed total budget, which Goal Seek's single-input-single-target design can't handle on its own.
--> All three sit squarely in the "prototyping tool" role described in file 02 -- genuinely useful for sketching a financial model's behavior quickly, but the same what-if logic (iterating an input to satisfy a target, or optimizing subject to constraints) is exactly what a Python `scipy.optimize` call or a dedicated planning tool would do at scale once the model outgrows a spreadsheet.

# KPI and Metric Design -- Choosing What to Measure Before Measuring It

--> Before any SQL query or A/B test (file 05) is worth running, an analyst has to answer a prior question: which metric actually reflects success? Getting this wrong means rigorously, correctly measuring the wrong thing.

## The North Star Metric

--> A single metric that best captures the core value a product delivers to its users, chosen so that when it goes up, the business is genuinely healthier -- not just a vanity number that can rise while the actual business gets worse.
--> Example: a marketplace's North Star might be "weekly transacting buyers," not "total signups" -- signups can be inflated by a promo with no lasting value, while transacting buyers reflects people who actually got value and came back to act on it.
--> A North Star Metric is deliberately singular and simple specifically so it can align an entire company around one shared definition of progress, rather than each team optimizing a different, possibly conflicting local metric.

## OKRs -- Objectives and Key Results

--> **Objective** -- a qualitative, ambitious statement of WHERE you want to go ("become the fastest checkout experience in the industry"). **Key Results** -- 2-4 specific, measurable, time-bound numbers that would prove the objective was actually achieved ("reduce median checkout time from 45s to 20s by Q3," "increase checkout completion rate from 68% to 80%").
--> The discipline OKRs enforce is precisely the "define the metric before acting" principle from file 05's experiment design section, applied at a quarterly planning level rather than to a single test -- an Objective without measurable Key Results is just an aspiration nobody can be held accountable for.

## Metric Trees -- Decomposing a Top-Line Number

--> A metric tree breaks one high-level metric down into the smaller, more directly actionable metrics that mathematically drive it -- turning "why did revenue drop?" from a vague question into a structured investigation.

```
Revenue
  = Visitors × Conversion Rate × Average Order Value
        |             |                  |
   (traffic/SEO   (checkout UX,    (upsells, pricing,
    marketing        pricing)       bundle offers)
    spend)
```

--> With the tree laid out, a revenue drop can be diagnosed by checking which BRANCH actually moved -- if Visitors and AOV are flat but Conversion Rate dropped, the investigation narrows immediately to the checkout experience, rather than an analyst guessing across the entire business.

## Leading vs Lagging Indicators

--> **Lagging indicators** measure an outcome AFTER it has already happened (monthly revenue, quarterly churn) -- accurate, but too late to act on for the period they describe.
--> **Leading indicators** predict a lagging outcome BEFORE it fully materializes (weekly active usage as a leading indicator of next month's churn; demo requests as a leading indicator of next quarter's sales) -- genuinely actionable, because there's still time to intervene.
--> A well-designed metric tree and dashboard combines both -- lagging indicators to report what happened, leading indicators to give early warning and a lever to actually pull before the lagging number is locked in.

# Non-Parametric Statistical Tests -- When file 04's Assumptions Don't Hold

--> The hypothesis tests introduced in file 04 (and the t-test used implicitly in A/B testing, file 05) generally assume the underlying data is roughly normally distributed. When that assumption clearly doesn't hold -- heavily skewed data, small samples, ordinal/ranked data, or outliers a mean-based test would be distorted by -- a **non-parametric test** answers the same underlying question using ranks or category counts instead of assuming a specific distribution shape.

## Test-Selection Guide

```
Comparing...                                  Parametric test        Non-parametric alternative
Two independent groups' means/medians          Independent t-test      Mann-Whitney U test
Two paired/matched groups (before vs after)    Paired t-test           Wilcoxon signed-rank test
Three or more independent groups                One-way ANOVA           Kruskal-Wallis test
Association between two categorical variables   (no direct parametric)  Chi-square test of independence
```

## Chi-Square Test -- Are Two Categorical Variables Related?

```python
from scipy.stats import chi2_contingency
import numpy as np

# Observed counts: rows = marketing channel, columns = converted (Yes/No)
observed = np.array([
    [180, 620],   # Email
    [95,  405],   # Social
    [140, 260],   # Search
])

chi2, p_value, dof, expected = chi2_contingency(observed)
print(f"chi2={chi2:.2f}, p={p_value:.4f}")
# A small p-value suggests conversion rate genuinely DIFFERS by channel,
# rather than the observed differences being explainable by chance alone
```

--> Chi-square compares OBSERVED counts in each category combination against what would be EXPECTED if the two variables were truly independent -- a large gap between observed and expected, relative to sample size, produces a large chi-square statistic and a small p-value.

## Mann-Whitney U Test -- Comparing Two Groups Without Assuming Normality

```python
from scipy.stats import mannwhitneyu

group_a_session_minutes = [2, 3, 3, 4, 5, 45]     # heavily right-skewed by one outlier session
group_b_session_minutes = [6, 7, 8, 9, 10, 11]

stat, p_value = mannwhitneyu(group_a_session_minutes, group_b_session_minutes, alternative="two-sided")
print(f"p={p_value:.4f}")
```

--> Rather than comparing means (which the `45`-minute outlier in group A would badly distort), Mann-Whitney ranks ALL the values together across both groups and checks whether one group's ranks tend to be systematically higher or lower than the other's -- exactly the kind of skew-robust comparison the median-over-mean guidance in file 04 was pointing toward, now formalized as an actual significance test.

## One-Way ANOVA -- Comparing Three or More Group Means at Once

```python
from scipy.stats import f_oneway

region_a = [102, 98, 110, 95]
region_b = [88, 91, 85, 90]
region_c = [120, 115, 130, 118]

f_stat, p_value = f_oneway(region_a, region_b, region_c)
print(f"F={f_stat:.2f}, p={p_value:.4f}")
# A small p-value says AT LEAST ONE region's mean differs from the others --
# it does NOT say which one(s) -- a follow-up "post-hoc" test (e.g. Tukey's HSD)
# is needed to identify specifically which pairs of regions actually differ
```

--> Running a separate t-test on every PAIR of groups instead of one ANOVA reintroduces exactly the multiple-comparisons problem flagged in file 05 -- each additional pairwise test inflates the overall false-positive rate, which is precisely why ANOVA exists: one test, one p-value, testing "is there ANY difference among these groups" before ever drilling into which pair.

# Retention and Funnel Analysis -- Turning Cohorts Into Action

--> **Retention analysis** (built directly on the cohort SQL pattern above) tracks what fraction of a cohort remains active over successive periods -- the shape of the retention curve matters as much as any single number: a curve that keeps declining toward zero signals the product hasn't found lasting value for that cohort, while a curve that FLATTENS after an initial drop (a "smile curve") signals a stable core of retained users worth building on.
--> **Funnel analysis** breaks a multi-step process (view product -> add to cart -> start checkout -> complete purchase) into stage-by-stage conversion rates, exposing exactly WHERE users drop off rather than only reporting the final end-to-end conversion rate.

```sql
WITH funnel AS (
    SELECT
        session_id,
        MAX(CASE WHEN event = 'view_product'    THEN 1 ELSE 0 END) AS viewed,
        MAX(CASE WHEN event = 'add_to_cart'      THEN 1 ELSE 0 END) AS added_to_cart,
        MAX(CASE WHEN event = 'start_checkout'   THEN 1 ELSE 0 END) AS started_checkout,
        MAX(CASE WHEN event = 'purchase'         THEN 1 ELSE 0 END) AS purchased
    FROM events
    GROUP BY session_id
)
SELECT
    SUM(viewed)            AS step1_viewed,
    SUM(added_to_cart)      AS step2_added_to_cart,
    SUM(started_checkout)   AS step3_started_checkout,
    SUM(purchased)          AS step4_purchased,
    ROUND(100.0 * SUM(added_to_cart)    / SUM(viewed), 1)          AS pct_view_to_cart,
    ROUND(100.0 * SUM(started_checkout) / SUM(added_to_cart), 1)   AS pct_cart_to_checkout,
    ROUND(100.0 * SUM(purchased)        / SUM(started_checkout), 1) AS pct_checkout_to_purchase
FROM funnel;
```

--> The step with the largest percentage drop-off is where a fix would have the biggest leverage on the final conversion number -- and it's exactly the kind of specific, actionable finding a metric tree (above) or an A/B test (file 05) can then be pointed at directly, closing the loop from "diagnose with SQL" to "design a metric-driven fix" to "validate the fix with a proper experiment."
