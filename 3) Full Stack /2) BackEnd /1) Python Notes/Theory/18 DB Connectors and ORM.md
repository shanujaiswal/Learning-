## sqlite3 -- Built-in Database Connector

--> sqlite3 is Python's built-in module for working with SQLite, a lightweight file-based database -- no separate server process needed, good for small apps and prototypes.

```python
import sqlite3

conn = sqlite3.connect("app.db")  # creates the file if it doesn't exist
cursor = conn.cursor()

cursor.execute("""
    CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY,
        name TEXT NOT NULL
    )
""")

# Always use placeholders (?) instead of f-strings/string formatting -- prevents SQL injection
cursor.execute("INSERT INTO users (name) VALUES (?)", ("Alice",))
conn.commit()  # writes changes to disk -- required for INSERT/UPDATE/DELETE

cursor.execute("SELECT * FROM users WHERE name = ?", ("Alice",))
rows = cursor.fetchall()  # fetchone() for a single row, fetchall() for all matching rows
print(rows)

conn.close()
```

==> Key Points
--> Never build SQL with string concatenation/f-strings using user input -- always use parameterized queries (? placeholders) to prevent SQL injection.
--> with sqlite3.connect(...) as conn: -- auto-commits on success, auto-rolls-back on exception (still need to close the connection manually).
--> Connectors for other databases follow a similar cursor-based pattern -- psycopg2 (PostgreSQL), mysql-connector-python (MySQL), pymongo (MongoDB, but document-based, not cursor/SQL-based).

---

## ORM -- Object-Relational Mapping

--> An ORM lets you interact with database tables using Python classes and objects instead of writing raw SQL -- a class maps to a table, an instance maps to a row, attributes map to columns.
--> Benefits -- less boilerplate SQL, database-agnostic code (can switch SQLite -> PostgreSQL with minimal changes), built-in protection against SQL injection, easier to reason about relationships.
--> Trade-offs -- an extra abstraction layer, can hide inefficient queries (e.g. the N+1 query problem) if not used carefully, slightly more overhead than raw SQL for very performance-critical paths.

---

## SQLAlchemy -- Standalone Python ORM

--> SQLAlchemy is the most widely used ORM in the Python ecosystem, usable with any framework (Flask, FastAPI) or standalone.

```python
from sqlalchemy import create_engine, Column, Integer, String
from sqlalchemy.orm import declarative_base, sessionmaker

engine = create_engine("sqlite:///app.db")
Base = declarative_base()

class User(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True)
    name = Column(String, nullable=False)

Base.metadata.create_all(engine)  # creates tables from model definitions

Session = sessionmaker(bind=engine)
session = Session()

# Create
new_user = User(name="Alice")
session.add(new_user)
session.commit()

# Read
user = session.query(User).filter_by(name="Alice").first()
print(user.id, user.name)

# Update
user.name = "Alice Smith"
session.commit()

# Delete
session.delete(user)
session.commit()
```

==> Key Concepts
--> declarative_base() -- base class that model classes inherit from, connecting Python classes to database tables.
--> Session -- manages the "unit of work" -- tracks changes to objects and commits them as a transaction.
--> Relationships -- relationship() and ForeignKey define one-to-many/many-to-many links between tables, letting you access related rows as Python attributes (e.g. user.posts).

---

## Django ORM -- Built into the Django Framework

--> Django's ORM is tightly integrated with the framework -- models double as the schema definition and the query interface, with migrations generated automatically.

```python
from django.db import models

class User(models.Model):
    name = models.CharField(max_length=100)

class Post(models.Model):
    title = models.CharField(max_length=200)
    author = models.ForeignKey(User, on_delete=models.CASCADE, related_name="posts")

# Create
user = User.objects.create(name="Alice")

# Read
users = User.objects.filter(name="Alice")     # returns a QuerySet (lazy -- SQL runs only when iterated/evaluated)
user = User.objects.get(id=1)                 # raises DoesNotExist if not found

# Update
user.name = "Alice Smith"
user.save()

# Delete
user.delete()

# Relationships
alice_posts = user.posts.all()  # reverse relation via related_name
```

==> Key Concepts
--> QuerySets are lazy -- User.objects.filter(...) builds a query but doesn't hit the database until you iterate it, call list(), or otherwise force evaluation.
--> select_related() (for ForeignKey/OneToOne) and prefetch_related() (for ManyToMany/reverse FK) avoid the N+1 query problem by fetching related rows in fewer queries.
--> Migrations (makemigrations, migrate) track schema changes over time -- generated automatically by comparing model definitions to the last migration state.

==> SQLAlchemy vs Django ORM
--> SQLAlchemy -- framework-agnostic, more explicit/flexible, steeper learning curve, preferred with Flask/FastAPI.
--> Django ORM -- simpler for common cases, tightly coupled to Django, less flexible for complex/custom SQL but faster to get started with.
