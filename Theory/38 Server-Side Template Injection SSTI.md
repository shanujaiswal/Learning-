### Server-Side Template Injection (SSTI)

--> ⚠️ LEGAL / ETHICAL REMINDER: Only test the payloads below against DVWA, PortSwigger Web Security Academy's SSTI labs, TryHackMe/HackTheBox web boxes (many have dedicated Flask/Jinja2 SSTI challenges), or an authorized client app explicitly in scope. SSTI escalates directly to remote code execution on the server — running these payloads against a real, unauthorized target is a criminal act, not a "harmless test."

--> Many web frameworks render pages using server-side template engines — Jinja2 (Python/Flask), Twig (PHP/Symfony), FreeMarker/Velocity (Java), Handlebars (Node.js). These engines take a template STRING containing both literal text and special `{{ }}`/`${ }` expression syntax, and compile/render it into final HTML. SSTI happens when user input is concatenated directly INTO the template string BEFORE that compile step, so the user's input becomes part of the template's SYNTAX itself rather than being substituted in as inert data.
```python
# DANGEROUS — user input becomes part of the template source itself
return render_template_string(f"Hello {user_input}")

# SAFE — user input is passed as a separate DATA argument, substituted into a fixed, trusted template
return render_template("hello.html", name=user_input)
```
--> In the dangerous version, if `user_input` is `{{7*7}}`, the template engine compiles `Hello {{7*7}}` as a template containing a real expression and evaluates it. In the safe version, `name` is just a string value dropped into a pre-written `{{ name }}` placeholder in `hello.html` — no matter what characters `name` contains, they are never re-interpreted as template syntax.

## Detection methodology

--> The classic engine-agnostic polyglot probe, designed to break differently depending on WHICH template engine is running underneath:
```text
${{<%[%'"}}%\
```
--> Different engines choke on different fragments of this string (an error, a partial evaluation, or a specific piece disappearing from the output) — comparing exactly what survives/errors tells you which engine you're facing, without needing to guess up front.
--> Once you suspect a specific engine family, confirm with a simple math-based payload — if the output is the EVALUATED result rather than the literal payload text, the input is being executed as code, not displayed as data:
| Payload | Engine family | Expected output if vulnerable |
|---|---|---|
| `{{7*7}}` | Jinja2 (Python), Twig (PHP) | `49` |
| `${7*7}` | FreeMarker, Velocity (Java) | `49` |
| `{{7*'7'}}` | Jinja2 specifically (string repetition) | `7777777` |

--> If the page instead shows the literal text `{{7*7}}` unmodified, the input is being treated as plain data (safe) — no SSTI. If it shows `49`, the input was compiled and executed as template code.

## Jinja2 walkthrough: from confirmation to RCE

--> Jinja2 (Flask's default template engine) is the most commonly tested engine in labs, so its full exploitation chain is worth knowing step by step.

1. Confirm: submit `{{7*7}}` into whatever input reflects into the page (a name field, a search box, a URL parameter rendered server-side). Seeing `49` confirms SSTI.
2. Escalate to information disclosure: `{{config}}` dumps Flask's application config object, which frequently includes `SECRET_KEY` — the key Flask uses to cryptographically SIGN session cookies. Leaking it lets an attacker forge arbitrary, validly-signed session cookies (e.g. claiming `is_admin=True`) without ever needing to guess a password — the exact "broken authentication" impact discussed generically in note 04, except here the root cause is a leaked signing secret rather than a weak login flow.
```text
{{config}}
```
3. Escalate to full remote code execution by walking Python's own object introspection hierarchy. Jinja2's sandbox tries to restrict what's reachable from a template, but it is incomplete — Python objects expose far more of their class hierarchy than the sandbox accounts for, and that hierarchy eventually reaches back to things like `subprocess.Popen`. The well-known gadget-chain payload to FIND that reachable subclass:
```text
{{ ''.__class__.__mro__[1].__subclasses__() }}
```
--> Breaking this down: `''.__class__` gets the `str` type object; `.__mro__` (Method Resolution Order) walks up its inheritance chain — `__mro__[1]` is `object`, the base class every Python class ultimately inherits from; `.__subclasses__()` lists every class currently loaded that directly subclasses `object` — which, in a running Flask app, includes dozens of framework/library internals, among them something like `subprocess.Popen` at whatever index that particular app happens to load it at. This is why the sandbox is bypassable: restricting `{{ os }}` or `{{ import }}` directly does nothing when the object graph itself provides an indirect path to the same functionality.
4. The final RCE payload, using a different but equally standard path — through the currently-rendering template's own `self` object out to Python's builtins:
```text
{{ self.__init__.__globals__.__builtins__.__import__('os').popen('id').read() }}
```
--> `self.__init__` is a bound method; `.__globals__` exposes the global namespace of the module that method was defined in (which includes `__builtins__`); `__builtins__.__import__('os')` performs a live `import os` from inside the sandbox; `.popen('id').read()` then runs the shell command `id` and returns its output directly into the rendered page — full command execution on the server, driven entirely through template syntax.

## SSTI vs. XSS vs. deserialization RCE

--> SSTI is fundamentally different from XSS (note 04) even though both start with "user input gets reflected into a page": XSS payloads execute as JAVASCRIPT inside a VICTIM's browser (client-side) — SSTI payloads execute as the TEMPLATE ENGINE'S OWN EXPRESSION LANGUAGE on the SERVER, meaning the attacker gets code execution on the server itself, not just in someone else's browser tab.
--> SSTI is conceptually closest to insecure deserialization (note 04, and note 42's dedicated coverage): both are cases where a data format that LOOKS inert — a template string, a serialized object graph — is actually handed to an interpreter that treats parts of it as executable instructions. "Template engine as an interpreter" and "object graph as an interpreter" are the same underlying mistake: trusting a format's structure without realizing the parser behind it can be walked/abused into running arbitrary code.

## Mitigations

| Mitigation | Why it works |
|---|---|
| Use logic-less template engines where feasible (e.g. Mustache) | Logic-less engines have no expression evaluation at all in their syntax — there is no `{{7*7}}`-style code path to inject into in the first place. |
| Never pass raw user input into a template-COMPILING function | `render_template_string(user_input)` / `Template(user_input)`-style calls compile user input as source code — always pass user input as a DATA argument to a template rendered from a fixed, trusted template FILE instead. |
| Sandbox template environments and keep them patched | Sandboxes reduce but do not eliminate risk (as shown above) — treat a sandbox as defense-in-depth, not a guarantee, and keep the engine updated against newly discovered sandbox-escape gadgets. |
| Threat-model template rendering as code execution | Any feature that lets a user influence template content (custom email templates, report generators, "theme" customization) should be reviewed with the same rigor as a feature that runs user-supplied code — because functionally, that's what it is. |

--> Cross-reference note 04 (OWASP Top 10 — Injection and Broken Authentication, since a leaked `SECRET_KEY` directly enables session forgery) and note 42 (Insecure Deserialization, the closest sibling vulnerability class conceptually).

--> With template-engine and object-graph interpreters covered, note 39 moves on to the next exploitation surface in the study track.
