# Software-Defined Firewall and Access Control List Rule Engine

## Real-World Scenario

A network admin manages the ACL (Access Control List) for a software-defined
firewall protecting a **segmented office network**:

| VLAN | CIDR | Purpose |
|---|---|---|
| Guest VLAN | `10.10.0.0/24` | Untrusted visitor devices |
| Corp VLAN | `10.20.0.0/24` | Employee workstations |
| Server VLAN | `10.30.0.0/24` | Internal app / DB / SSH-managed servers |

This mirrors the security relevance called out in the Networking Fundamentals
theory file: a star-topology network's central switch/firewall is the natural
chokepoint to enforce segmentation, and VLANs are the standard technique for
creating separate broadcast domains so a compromise in one segment (e.g. a
Guest device) can't freely reach a more sensitive one (e.g. the Server VLAN).

Just like a real router ACL, Cisco `access-list`, or an AWS Security
Group/NACL, rules here are evaluated **top to bottom, in priority order**,
and the **first rule that matches wins** — every rule after that match is
never even consulted for that flow. If nothing in the list matches, an
**implicit deny-all** silently drops the traffic, exactly as it does on real
firewall/router hardware.

The project has two moving parts:

1. **Traffic simulation** — a batch of synthesized flows (legitimate
   cross-VLAN traffic, plus traffic that policy should block, e.g. Guest
   trying to reach the Server VLAN) is evaluated against a correctly-ordered
   "production" ACL.
2. **Rule-shadowing analysis** — a *separate*, deliberately misconfigured ACL
   is statically analyzed to detect rules that can never fire because an
   earlier, broader rule already matches everything the later rule would
   have matched. This is one of the most common real firewall
   misconfigurations: someone adds a broad "temporary" allow-all rule near
   the top of the list, forgets to remove it, and every more specific rule
   below it silently becomes dead code.

## Architecture

| Module | Role | Real-World Equivalent |
|---|---|---|
| `packet_flow_generator.py` | Synthesizes a batch of realistic traffic flows (src/dst IP, protocol, port) across the Guest/Corp/Server VLANs, mixing legitimate and policy-violating traffic | A NetFlow collector / firewall traffic log feed |
| `acl_rule_engine.py` | Holds the ordered ACL rule list and the first-match-wins evaluation function with implicit deny fallback | A Cisco router `access-list` / AWS Security Group / NACL rule evaluator |
| `shadow_rule_analyzer.py` | Statically analyzes a rule list to find rules fully shadowed (made unreachable) by an earlier, broader rule | A firewall rule-analysis / config-audit tool like AlgoSec or Tufin |
| `main.py` | Orchestrates: runs the traffic simulation against the production ACL, then runs the shadow analyzer against a misconfigured ACL, printing both reports | The admin's daily "did my firewall change work, and did I break anything" workflow |

Only the Python standard library is used — `ipaddress` for CIDR parsing,
membership tests (`ip in network`), and subnet-containment checks
(`network.subnet_of(...)`). No sockets, no packets actually sent, no
third-party packages.

## Run It

```bash
python main.py
```

No dependencies to install — pure standard library.

## Verified Result (actual output)

```
==============================================================================
PART 1 -- Traffic Simulation vs. the Production ACL
==============================================================================
Active ACL (evaluated top to bottom, first match wins):
    [ 10] corp-to-server-https: ALLOW 10.20.0.0/24 -> 10.30.0.0/24 (tcp/port 443)
    [ 20] corp-to-server-ssh: ALLOW 10.20.0.0/24 -> 10.30.0.0/24 (tcp/port 22)
    [ 30] corp-to-server-mysql: ALLOW 10.20.0.0/24 -> 10.30.0.0/24 (tcp/port 3306)
    [ 40] guest-to-internet-web: ALLOW 10.10.0.0/24 -> 0.0.0.0/0 (tcp/port 443)
    [ 50] guest-to-internet-http: ALLOW 10.10.0.0/24 -> 0.0.0.0/0 (tcp/port 80)
    [ 60] block-guest-to-server: DENY 10.10.0.0/24 -> 10.30.0.0/24 (any proto/any port)
    [ 70] block-guest-to-corp: DENY 10.10.0.0/24 -> 10.20.0.0/24 (any proto/any port)
    [ 80] corp-intra-vlan: ALLOW 10.20.0.0/24 -> 10.20.0.0/24 (any proto/any port)
    [implicit deny-all -- fallback if nothing above matches]

Evaluating 10 simulated flows:

[ALLOW]      10.20.0.15 -> 10.30.0.10      tcp/443   | Corp workstation browsing internal HTTPS web app
         matched rule: corp-to-server-https (priority 10)
[ALLOW]      10.20.0.23 -> 10.30.0.20      tcp/22    | Corp admin SSH'ing into the jump box
         matched rule: corp-to-server-ssh (priority 20)
[ALLOW]     10.20.0.101 -> 10.30.0.30      tcp/3306  | Corp app server querying the internal MySQL DB
         matched rule: corp-to-server-mysql (priority 30)
[ALLOW]      10.10.0.11 -> 93.184.216.34   tcp/443   | Guest device browsing the public internet (HTTPS)
         matched rule: guest-to-internet-web (priority 40)
[ALLOW]      10.10.0.42 -> 142.250.72.14   tcp/80    | Guest device on plain HTTP
         matched rule: guest-to-internet-http (priority 50)
[ALLOW]      10.20.0.15 -> 10.20.0.101     tcp/445   | Corp-to-corp file share (SMB) between two workstations
         matched rule: corp-intra-vlan (priority 80)
[DENY ]      10.10.0.11 -> 10.30.0.30      tcp/3306  | Guest device attempting to reach the internal DB directly
         matched rule: block-guest-to-server (priority 60)
[DENY ]      10.10.0.42 -> 10.30.0.20      tcp/22    | Guest device attempting SSH into the internal jump box
         matched rule: block-guest-to-server (priority 60)
[DENY ]      10.10.0.77 -> 10.20.0.15      tcp/445   | Guest device probing a Corp workstation's SMB port
         matched rule: block-guest-to-corp (priority 70)
[DENY ]      10.30.0.30 -> 93.184.216.34   tcp/9999  | Server VLAN host making an unexpected outbound connection
         matched rule: implicit-deny-all (priority 10000)

Summary: 6 allowed, 4 denied, 10 total flows.

==============================================================================
PART 2 -- Rule Shadowing Analysis on a Misconfigured ACL
==============================================================================
This ACL was deliberately misconfigured with an overly broad rule
placed too early in the evaluation order:

Analyzed 6 rules in evaluation order:
    [ 10] corp-to-server-ALLOW-ALL: ALLOW 10.20.0.0/24 -> 10.30.0.0/24 (any proto/any port)
    [ 20] corp-to-server-ssh-only: ALLOW 10.20.0.0/24 -> 10.30.0.0/24 (tcp/port 22)
    [ 30] corp-to-server-mysql-only: ALLOW 10.20.0.128/28 -> 10.30.0.0/24 (tcp/port 3306)
    [ 40] block-guest-to-server: DENY 10.10.0.0/24 -> 10.30.0.0/24 (any proto/any port)
    [ 50] guest-to-internet-web: ALLOW 10.10.0.0/24 -> 0.0.0.0/0 (tcp/port 443)
    [ 60] guest-single-host-web: ALLOW 10.10.0.5/32 -> 0.0.0.0/0 (tcp/port 443)

3 shadowed (unreachable) rule(s) detected:
    - Rule [20] 'corp-to-server-ssh-only' is fully SHADOWED by earlier rule [10] 'corp-to-server-ALLOW-ALL' -- it can never fire.
    - Rule [30] 'corp-to-server-mysql-only' is fully SHADOWED by earlier rule [10] 'corp-to-server-ALLOW-ALL' -- it can never fire.
    - Rule [60] 'guest-single-host-web' is fully SHADOWED by earlier rule [50] 'guest-to-internet-web' -- it can never fire.

==============================================================================
Result
==============================================================================
3 misconfiguration(s) found -- these rules are dead code and should be reordered, narrowed, or removed:
    * 'corp-to-server-ssh-only' shadowed by 'corp-to-server-ALLOW-ALL'
    * 'corp-to-server-mysql-only' shadowed by 'corp-to-server-ALLOW-ALL'
    * 'guest-single-host-web' shadowed by 'guest-to-internet-web'

==============================================================================
Done.
==============================================================================
```

This confirms:
- All legitimate Corp-to-Server, Guest-to-Internet, and Corp-intra-VLAN
  traffic is correctly **allowed** by the specific rule intended for it.
- All Guest-to-Server and Guest-to-Corp traffic is correctly **denied**.
- Traffic matching no rule at all correctly falls through to the
  **implicit-deny-all**.
- The shadow analyzer correctly flags all 3 unreachable rules in the
  misconfigured ACL, and correctly does **not** flag any rule in the
  well-formed production ACL (no false positives) — each rule there is
  either narrower in scope than everything before it, or is itself the
  broadest applicable rule for its slice of traffic.

## Things to Try Changing

- **Reorder `OFFICE_ACL`** in `acl_rule_engine.py` — move
  `corp-intra-vlan` (priority 80) above `corp-to-server-https` (priority 10)
  and re-run; watch how Corp→Server traffic starts matching the wrong rule
  once its "any/any" scope is checked first.
- **Add a new VLAN** (e.g. an IoT VLAN `10.40.0.0/24`) with its own rules in
  `acl_rule_engine.py`, and add matching flows in
  `packet_flow_generator.py` to exercise it.
- **Introduce a fresh shadowing bug** — add a rule to `MISCONFIGURED_ACL`
  that denies all traffic (`ANY`/`ANY`) at priority 5, and observe how the
  analyzer then reports that essentially the entire rest of the ACL is dead
  code beneath it.
- **Loosen `_rule_covers` in `shadow_rule_analyzer.py`** to also flag
  *partial* overlap (not just full containment) — a stretch goal that
  mirrors what real tools call "rule overlap/redundancy" analysis rather
  than pure shadowing.
- **Swap `port` for a port *range*** (e.g. `(1024, 65535)`) in `ACLRule` and
  update `matches`/`_rule_covers` accordingly, closer to how real ACLs
  usually express ephemeral port ranges.
