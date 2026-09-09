"""
target_app.py

AUTHORIZED USE ONLY / SELF-CONTAINED LAB TARGET. This Flask app is intentionally fragile and is
meant ONLY to be fuzzed by 05_fuzzer_against_local_flask_target.py on your own machine. It binds to
127.0.0.1 ONLY (never 0.0.0.0), so it is not reachable from the network — but you should still never
expose it, deploy it, or run it anywhere other than a local sandbox.

Part of Theory Ch.7 (Exploit Development / Fuzzing) integration: this is the deliberately-buggy
"victim" that the companion fuzzer script targets, so the fuzzing concept can be demonstrated
end-to-end instead of only in isolated snippets.

Endpoint:
  GET /divide?a=<number>&b=<number>  -> returns {"result": a / b}

Deliberate bugs a fuzzer should discover:
  - No validation that a/b are actually numeric        -> ValueError on int("abc")
  - No handling of b == 0                               -> ZeroDivisionError
  - No length/type limits on the input                  -> could be abused with huge payloads
  - No general try/except around the handler            -> any of the above becomes a raw HTTP 500
"""

from flask import Flask, request, jsonify

app = Flask(__name__)


@app.route("/divide")
def divide():
    # Deliberately naive: no validation, no try/except. This is the bug the fuzzer will find.
    a = int(request.args.get("a"))
    b = int(request.args.get("b"))
    result = a / b
    return jsonify({"result": result})


@app.route("/")
def index():
    return jsonify({"status": "ok", "hint": "try GET /divide?a=10&b=2"})


if __name__ == "__main__":
    # 127.0.0.1 ONLY — never change this to 0.0.0.0 for this deliberately-vulnerable demo app.
    app.run(host="127.0.0.1", port=5000, debug=False)
