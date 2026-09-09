# Why Database-Level Access Control Matters

--> Application-level permission checks (covered in the Cyber Security IAM file) control what a USER can do through the app -- but the database itself also needs its own access control, controlling what each DATABASE ACCOUNT (often a service account used by an application, or a human DBA) is allowed to do directly against the data.
--> This is a second, independent layer of defense -- even if application-level authorization has a bug, database-level permissions limit the actual damage a compromised or misconfigured connection can do.

# Users and Privileges

```sql
CREATE USER 'app_user'@'localhost' IDENTIFIED BY 'strong_password';
CREATE USER 'readonly_analyst'@'%' IDENTIFIED BY 'another_password';
```

# GRANT -- Assigning Permissions

```sql
GRANT SELECT, INSERT, UPDATE ON myapp.orders TO 'app_user'@'localhost';
GRANT SELECT ON myapp.* TO 'readonly_analyst'@'%';
GRANT ALL PRIVILEGES ON myapp.* TO 'admin_user'@'localhost';
```

--> Common privilege types: `SELECT`, `INSERT`, `UPDATE`, `DELETE` (data-level), `CREATE`, `DROP`, `ALTER` (schema-level), `EXECUTE` (running stored procedures).
--> Scope can be as broad as an entire server (`*.*`), a whole database (`myapp.*`), a single table (`myapp.orders`), or in some database systems, even individual columns.

# REVOKE -- Removing Permissions

```sql
REVOKE INSERT, UPDATE ON myapp.orders FROM 'app_user'@'localhost';
REVOKE ALL PRIVILEGES ON myapp.* FROM 'former_employee'@'%';
```

--> Revoking permissions promptly when an account no longer needs them (a role change, an employee departure) is a basic, frequently-overlooked hygiene practice.

# Roles -- Grouping Permissions

--> Rather than granting the same set of permissions individually to every user who needs them, most modern database systems support Roles -- a named bundle of permissions that can be granted/revoked as a single unit, then assigned to many users.

```sql
CREATE ROLE 'read_only_role';
GRANT SELECT ON myapp.* TO 'read_only_role';

GRANT 'read_only_role' TO 'analyst_1'@'%', 'analyst_2'@'%';
```

--> Changing what "read_only_role" can do updates every user assigned that role at once, instead of having to individually re-grant/revoke permissions for each person.

# The Principle of Least Privilege, Applied to Database Accounts

--> An application's database connection should have EXACTLY the permissions it needs (typically `SELECT`/`INSERT`/`UPDATE`/`DELETE` on its own tables) and nothing more -- it almost never needs `DROP TABLE` or `CREATE USER` privileges, and granting them anyway needlessly expands the blast radius of a SQL injection vulnerability (covered in the SQL Views/Injection file) or a leaked credential.
--> Separate accounts for separate purposes -- an application's runtime connection, a reporting/analytics tool, and a human DBA should each use DIFFERENT accounts with only the permissions relevant to that specific purpose, rather than one shared, overly-permissive account used everywhere.

# Auditing Who Can Do What

```sql
SHOW GRANTS FOR 'app_user'@'localhost';   -- MySQL: lists everything this user is currently allowed to do
```

--> Periodically auditing granted permissions (who has access to what, and whether they still need it) is a standard part of both good database hygiene and compliance requirements (referenced in the GRC/ISO 27001 file in the Security folder).
