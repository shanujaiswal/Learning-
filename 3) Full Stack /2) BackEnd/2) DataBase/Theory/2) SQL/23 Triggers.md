# What a Trigger Is

--> A trigger is a stored piece of SQL logic that automatically executes in response to a specific event (INSERT, UPDATE, DELETE) on a table -- unlike a Stored Procedure, which only runs when explicitly called, a trigger fires automatically, without the application needing to know it exists.

# BEFORE vs AFTER Triggers

--> `BEFORE` trigger -- runs before the triggering statement actually modifies data -- commonly used to VALIDATE or MODIFY the incoming data before it's saved.
--> `AFTER` trigger -- runs after the change has already been committed -- commonly used to take a follow-up action based on data that has now definitely changed (logging, updating a related table).

```sql
CREATE TRIGGER before_employee_insert
BEFORE INSERT ON employees
FOR EACH ROW
BEGIN
    IF NEW.salary < 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Salary cannot be negative';
    END IF;
END;
```

--> `NEW` refers to the row's incoming/updated values; `OLD` refers to the row's values before the change (available in `UPDATE`/`DELETE` triggers) -- syntax for referencing these varies slightly between database systems (MySQL/PostgreSQL/SQL Server).

# A Common Real-World Pattern -- Audit Logging

```sql
CREATE TRIGGER log_salary_change
AFTER UPDATE ON employees
FOR EACH ROW
BEGIN
    IF OLD.salary <> NEW.salary THEN
        INSERT INTO salary_audit_log (employee_id, old_salary, new_salary, changed_at)
        VALUES (NEW.employee_id, OLD.salary, NEW.salary, NOW());
    END IF;
END;
```

--> This automatically records every salary change with zero extra application code -- even a change made directly via a raw SQL client (not through the application) still gets logged, which is exactly the point: the guarantee lives at the database level, not the application level.

# Maintaining Derived/Denormalized Data

```sql
CREATE TRIGGER update_order_total
AFTER INSERT ON order_items
FOR EACH ROW
BEGIN
    UPDATE orders
    SET total_amount = (SELECT SUM(price * quantity) FROM order_items WHERE order_id = NEW.order_id)
    WHERE order_id = NEW.order_id;
END;
```

--> Keeps a denormalized `total_amount` column automatically in sync whenever line items change, instead of relying on every piece of application code that inserts an order item to remember to also update the total.

# Trade-offs and When to Avoid Triggers

--> Hidden logic -- a trigger fires silently, invisible to anyone just reading application code -- this can make debugging harder ("why did this value change? there's no code that did that... oh, it's a trigger").
--> Performance -- triggers add overhead to every single INSERT/UPDATE/DELETE they're attached to, even for the common case where their logic doesn't need to do much.
--> Cascading triggers (a trigger causing another change that fires ANOTHER trigger) can become genuinely hard to reason about in a complex schema.
--> Reasonable guidance -- triggers are well-suited for cross-cutting, safety-critical guarantees (audit logs, data integrity checks) that MUST hold regardless of which application code path modifies the data; ordinary business logic is usually clearer living in the application layer instead.
