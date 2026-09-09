## Flask -- Micro Framework

--> Flask is a lightweight, unopinionated web framework -- gives you routing and request/response handling, but leaves choices like ORM, project structure, and auth up to you.
--> Good for small apps, APIs, and prototypes where you want full control without built-in conventions.

```python
from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route("/users/<int:user_id>", methods=["GET"])
def get_user(user_id):
    return jsonify({"id": user_id, "name": "Alice"})

@app.route("/users", methods=["POST"])
def create_user():
    data = request.get_json()  # parses the JSON body of the request
    return jsonify({"created": data}), 201

if __name__ == "__main__":
    app.run(debug=True)
```

==> Key Concepts
--> @app.route() decorator maps a URL path (and HTTP method) to a view function.
--> request object holds incoming data -- request.args (query string), request.get_json() (JSON body), request.form (form data).
--> jsonify() converts a Python dict/list into a proper JSON HTTP response.
--> Extensions add functionality piece by piece -- Flask-SQLAlchemy (ORM), Flask-Migrate (migrations), Flask-Login (auth).

---

## Django -- Full Batteries-Included Framework

--> Django is a full-featured framework that ships with an ORM, admin panel, authentication, forms, and templating out of the box -- follows "convention over configuration".
--> Best for larger applications where you want a standard, well-supported structure rather than assembling pieces yourself.

==> Project Structure
--> django-admin startproject mysite -- creates the project (settings, URL config).
--> python manage.py startapp blog -- creates an "app" (a self-contained feature module) inside the project.

```python
# models.py -- defines a database table via the built-in ORM
from django.db import models

class Post(models.Model):
    title = models.CharField(max_length=200)
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)

# views.py -- handles a request and returns a response
from django.http import JsonResponse

def post_list(request):
    posts = Post.objects.all()  # ORM query
    return JsonResponse({"posts": [p.title for p in posts]})

# urls.py -- maps URL patterns to views
from django.urls import path
from . import views

urlpatterns = [path("posts/", views.post_list)]
```

==> Key Concepts
--> Models -- Python classes representing database tables, using Django's built-in ORM (no raw SQL needed for common operations).
--> Migrations -- python manage.py makemigrations / migrate -- generate and apply database schema changes based on model definitions.
--> Admin Panel -- automatically generated CRUD interface for any registered model, available at /admin.
--> Templates -- Django's built-in templating language for rendering HTML with dynamic data (if not building a pure API).
--> Django REST Framework (DRF) -- the standard add-on for building REST APIs with Django (serializers, viewsets, browsable API).

---

## FastAPI -- Modern Async Framework

--> FastAPI is built for speed (both runtime performance and developer speed) -- built on Starlette (ASGI) and Pydantic, with native async support and automatic API docs.
--> Type hints define request/response schemas AND provide automatic validation, serialization, and interactive documentation (Swagger UI / ReDoc) for free.

```python
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

class User(BaseModel):
    name: str
    age: int

@app.get("/users/{user_id}")
async def get_user(user_id: int):  # type hint -> automatic validation + docs
    return {"id": user_id, "name": "Alice"}

@app.post("/users")
async def create_user(user: User):  # request body auto-parsed and validated against the User model
    return {"created": user}
```

==> Key Concepts
--> Pydantic models -- define the shape of request bodies/responses -- invalid data automatically returns a 422 error with details, no manual validation code needed.
--> Native async def support -- built for asyncio from the ground up, well-suited to I/O-heavy APIs.
--> Automatic interactive docs -- visiting /docs gives a full Swagger UI generated from your route definitions and Pydantic models, with zero extra code.
--> Dependency Injection -- Depends() lets you cleanly share logic (auth checks, DB sessions) across multiple routes.

---

## Choosing a Framework

--> Flask -- small APIs/prototypes, full control, minimal opinions.
--> Django -- large apps needing a complete, batteries-included structure (admin, ORM, auth) fast.
--> FastAPI -- modern APIs prioritizing performance, async I/O, and automatic validation/docs.

## Deep Dive -- WSGI vs ASGI

--> **WSGI** (Web Server Gateway Interface) is the traditional, long-standing standard interface between a Python web application and a web server -- it's fundamentally SYNCHRONOUS, handling one request at a time per worker, blocking until that request completes before the worker is free again. Flask and traditional Django both run on WSGI by default (via servers like Gunicorn).
--> **ASGI** (Asynchronous Server Gateway Interface) is the modern successor, designed specifically to support `async`/`await` (covered in the Concurrency file) -- a single ASGI worker can handle MANY concurrent requests, especially I/O-bound ones, without needing a separate OS thread/process per request. FastAPI is built natively on ASGI (via Starlette/Uvicorn); Django has also added ASGI support for its async views.
--> **Why this matters practically** -- an ASGI-based app can serve significantly more CONCURRENT I/O-bound requests (waiting on a database, an external API) per worker process than an equivalent WSGI app, since it doesn't dedicate a whole blocked worker to each waiting request -- directly connecting to the asyncio concurrency model covered in the Concurrency file, just applied at the web-server-integration layer rather than within application code alone.

```bash
# WSGI server running a Flask app
gunicorn app:app

# ASGI server running a FastAPI app
uvicorn app:app --reload
```

## Deep Dive -- Middleware Across Frameworks

--> All three frameworks support "middleware" -- code that runs on EVERY request/response, before/after the actual route handler -- directly parallel to the Express Middleware Architecture concept covered in the Full Stack Node/Express notes, just implemented in each Python framework's own idiom.

```python
# Flask -- using a before_request hook
@app.before_request
def log_request():
    print(f"Incoming request: {request.path}")

# FastAPI -- using proper ASGI middleware
@app.middleware("http")
async def add_process_time_header(request, call_next):
    start = time.time()
    response = await call_next(request)
    response.headers["X-Process-Time"] = str(time.time() - start)
    return response

# Django -- a middleware class in settings.py's MIDDLEWARE list
class SimpleLoggingMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        print(f"Incoming request: {request.path}")
        return self.get_response(request)
```

--> Common middleware use cases across all three -- authentication checks, CORS headers, request logging, rate limiting -- the exact same cross-cutting-concerns philosophy covered in the API Design Patterns file's API Gateway discussion, just applied at the application-framework level instead of an infrastructure layer in front of multiple services.
