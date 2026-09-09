## ORM -- Database-Side Perspective

--> An ORM (Object-Relational Mapping) generates the actual SQL behind the scenes from Python model classes -- from a database standpoint, it's still creating tables, indexes, and constraints, just declared in Python instead of raw DDL.
--> Full code usage of SQLAlchemy and Django ORM is covered in Python Notes -- "18 DB Connectors and ORM.md". This note focuses on the database-design side: what the ORM is actually doing to the schema, and where it can go wrong.

# What an ORM Generates

--> A model class becomes a CREATE TABLE statement -- fields become columns with types, constraints (nullable, unique, primary key), and defaults.
--> A ForeignKey field becomes an actual foreign key constraint in the database, enforcing referential integrity at the DB level, not just in application code.
--> Migrations are the ORM's version-controlled diff of the schema over time -- each migration is essentially an ALTER TABLE (or CREATE/DROP) generated from comparing model definitions.

# The N+1 Query Problem

--> The most common ORM-caused performance issue -- fetching a list of N parent rows, then looping over them and querying for each one's related rows separately, resulting in 1 + N queries instead of 1 or 2.

```python
# N+1 problem -- 1 query for users, then 1 more PER user for their posts
users = User.objects.all()
for user in users:
    print(user.posts.all())  # separate query every iteration
```

--> Fix -- eager loading: fetch the related rows up front in one extra query (or one combined JOIN query) instead of one-per-row.
--> Django -- select_related() (SQL JOIN, for ForeignKey/OneToOne) and prefetch_related() (separate query + Python-side matching, for ManyToMany/reverse FK).
--> SQLAlchemy -- joinedload() or selectinload() passed to a query, with the same underlying idea.

```python
users = User.objects.prefetch_related("posts").all()  # 2 queries total, not N+1
```

# Lazy vs Eager Evaluation

--> Both ORMs build queries lazily by default -- User.objects.filter(...) or session.query(User) doesn't hit the database until the result is actually iterated/evaluated.
--> This is usually good (lets you chain filters cheaply) but can surprise you -- logging a QuerySet's length twice can trigger two separate database round trips unless you force evaluation once (e.g. list(queryset)) and reuse that.

# Schema Design Considerations When Using an ORM

--> Indexes still need to be added deliberately -- an ORM will NOT automatically index every field you query on. db_index=True (Django) / Column(index=True) (SQLAlchemy) must be set explicitly on frequently-filtered/sorted columns.
--> Cascading deletes (on_delete=models.CASCADE, ondelete="CASCADE") must be chosen intentionally per relationship -- the wrong default can silently delete more data than intended, or leave orphaned rows if set to nothing.
--> An ORM makes it easy to write inefficient queries without realizing it (e.g. fetching entire rows/relations when only one field is needed) -- .values()/.only() (Django) or .with_entities()/load_only() (SQLAlchemy) let you select just the needed columns.

# When to Drop to Raw SQL

--> Complex aggregate reports, window functions, or highly-tuned queries are sometimes clearer and faster written as raw SQL than forced through the ORM's query API.
--> Both frameworks support raw SQL escape hatches -- Model.objects.raw() / connection.cursor() in Django, session.execute(text(...)) in SQLAlchemy -- useful when the ORM abstraction gets in the way rather than helping.
