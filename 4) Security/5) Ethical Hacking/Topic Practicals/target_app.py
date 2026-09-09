"""
target_app.py -- Deliberately Vulnerable Local Lab Target
============================================================

LEGAL / ETHICAL SCOPE
----------------------
Only test systems you own or are authorized to test. This application is a
self-contained, intentionally-vulnerable Flask app built purely for local,
offline learning. It binds ONLY to 127.0.0.1 (localhost) and must never be
exposed to a network, the internet, or any host you do not own. Do not
deploy this code anywhere except your own local machine for practice.

WHAT THIS IS
-------------
A miniature "DVWA-style" training target with three deliberately vulnerable
endpoints, each mapped to an OWASP Top 10 / Theory chapter concept:

  1. /login        -> SQL Injection (Injection / OWASP A03)
  2. /search        -> Reflected XSS (Cross-Site Scripting / OWASP A03, Ch.26)
  3. /account        -> Broken Access Control / IDOR (OWASP A01, Ch.25)

Run this file first, in its own terminal:
    python target_app.py

Then, in a second terminal, run the attack-demo scripts in this same folder
against http://127.0.0.1:5000.

Do NOT use this code as a template for real applications -- every "VULNERABLE
ON PURPOSE" section below shows the *wrong* way to do things on purpose, so
you can see the flaw and then study the fix.
"""

import sqlite3
from flask import Flask, request

app = Flask(__name__)

# ----------------------------------------------------------------------------
# In-memory SQLite database, rebuilt fresh every time the app starts.
# check_same_thread=False is fine here only because this is a tiny,
# single-purpose local training toy, not production code.
# ----------------------------------------------------------------------------
DB = sqlite3.connect(":memory:", check_same_thread=False)
DB.execute("""
    CREATE TABLE users (
        id INTEGER PRIMARY KEY,
        username TEXT NOT NULL,
        password TEXT NOT NULL,
        email TEXT NOT NULL,
        is_admin INTEGER NOT NULL DEFAULT 0
    )
""")
DB.executemany(
    "INSERT INTO users (id, username, password, email, is_admin) VALUES (?,?,?,?,?)",
    [
        (1, "alice", "alicepass123", "alice@lab.local", 0),
        (2, "bob",   "bobpassword",  "bob@lab.local",   0),
        (3, "admin", "SuperSecret1", "admin@lab.local", 1),
    ],
)
DB.commit()


@app.route("/")
def index():
    return (
        "<h1>Vulnerable Training Lab</h1>"
        "<p>Endpoints: /login (SQLi), /search (XSS), /account?user_id=1 (IDOR)</p>"
    )


# ----------------------------------------------------------------------------
# 1) SQL INJECTION -- VULNERABLE ON PURPOSE
#
# The query is built with raw Python string formatting instead of
# parameterized placeholders. An attacker-supplied username/password can
# break out of the intended string literal and alter the query logic, e.g.
# username = "' OR '1'='1" bypasses the password check entirely.
#
# GET/POST /login?username=...&password=...
# ----------------------------------------------------------------------------
@app.route("/login", methods=["GET", "POST"])
def login():
    username = request.values.get("username", "")
    password = request.values.get("password", "")

    # VULNERABLE ON PURPOSE: direct string interpolation into SQL.
    query = "SELECT id, username, is_admin FROM users WHERE username = '%s' AND password = '%s'" % (
        username,
        password,
    )
    try:
        cursor = DB.execute(query)
        row = cursor.fetchone()
    except sqlite3.Error as exc:
        return {"error": f"SQL error: {exc}", "query_used": query}, 400

    if row:
        return {
            "status": "login_success",
            "user_id": row[0],
            "username": row[1],
            "is_admin": bool(row[2]),
            "query_used": query,  # exposed for teaching purposes only
        }
    return {"status": "login_failed", "query_used": query}, 401


# THE FIX (do this instead -- shown here only as a comment for study purposes;
# the exploit demo script also implements this fixed version in its own
# separate function so you can compare both side by side):
#
# def login_fixed():
#     username = request.values.get("username", "")
#     password = request.values.get("password", "")
#     cursor = DB.execute(
#         "SELECT id, username, is_admin FROM users WHERE username = ? AND password = ?",
#         (username, password),
#     )
#     row = cursor.fetchone()
#     ...
#
# Parameterized queries send the SQL text and the data separately to the
# database driver, so user input can never change the query's structure.


# ----------------------------------------------------------------------------
# 2) REFLECTED XSS -- VULNERABLE ON PURPOSE
#
# The `q` parameter is echoed back into the HTML response without any
# escaping. If `q` contains a <script> tag or event handler, it will be
# parsed and executed by a real browser rendering this response.
#
# GET /search?q=...
# ----------------------------------------------------------------------------
@app.route("/search")
def search():
    q = request.args.get("q", "")

    # VULNERABLE ON PURPOSE: raw, unescaped interpolation into HTML.
    html = f"""
    <html>
      <body>
        <h2>Search Results</h2>
        <p>You searched for: {q}</p>
        <p>No results found.</p>
      </body>
    </html>
    """
    return html


# THE FIX (do this instead -- comment only):
#
# from markupsafe import escape
# @app.route("/search")
# def search_fixed():
#     q = request.args.get("q", "")
#     safe_q = escape(q)   # HTML-encodes <, >, &, ", ' so it renders as text
#     return f"<p>You searched for: {safe_q}</p>"
#
# Flask's Jinja2 templates auto-escape by default (render_template), which is
# why manually building HTML strings, like this vulnerable endpoint does, is
# a common source of XSS in real applications.


# ----------------------------------------------------------------------------
# 3) BROKEN ACCESS CONTROL / IDOR -- VULNERABLE ON PURPOSE
#
# Returns account data for any user_id with no authentication and no check
# that the requester is actually that user (or an admin). Any caller can
# simply increment user_id to enumerate every account in the system.
#
# GET /account?user_id=...
# ----------------------------------------------------------------------------
@app.route("/account")
def account():
    user_id = request.args.get("user_id", "")

    # VULNERABLE ON PURPOSE: no session/auth check that the caller owns
    # this account, and no authorization check at all.
    cursor = DB.execute(
        "SELECT id, username, email, is_admin FROM users WHERE id = ?",
        (user_id,),
    )
    row = cursor.fetchone()
    if not row:
        return {"error": "no such user"}, 404

    return {
        "id": row[0],
        "username": row[1],
        "email": row[2],
        "is_admin": bool(row[3]),
    }


# THE FIX (do this instead -- comment only):
#
# @app.route("/account")
# def account_fixed():
#     # 1. Require authentication (e.g. a session cookie / bearer token).
#     current_user_id = get_authenticated_user_id_from_session(request)
#     if current_user_id is None:
#         return {"error": "unauthenticated"}, 401
#
#     requested_id = request.args.get("user_id", "")
#     # 2. Enforce that the requester can only access their own record
#     #    (or is an admin) -- this is the actual access-control check.
#     if str(current_user_id) != str(requested_id) and not is_admin(current_user_id):
#         return {"error": "forbidden"}, 403
#     ...


if __name__ == "__main__":
    # Bind ONLY to localhost. Never change host to "0.0.0.0" for this lab.
    app.run(host="127.0.0.1", port=5000, debug=True)
