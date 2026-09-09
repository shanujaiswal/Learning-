# Game Development -- Practical

Runnable, self-contained Python code demonstrating patterns from the
`Theory` notes in this vault. Each file is standalone and prints/renders
its own results -- read the module docstring at the top of each file for
the full explanation; this README is just the index.

## Setup

Only one dependency is needed, and only for `game.py`:

```
pip install pygame
```

`state_machine_demo.py`, `ecs_demo.py`, and
`simple_client_server_prediction_demo.py` use only the Python standard
library (`enum`, `dataclasses`, `queue`, `threading`, `time`) -- no
install required for those three.

## Files

| File | Theory chapter | What it shows | Run |
|---|---|---|---|
| `game.py` | `01 Game Development Fundamentals and the Game Loop.md`, `02 Game Physics and Collision Detection.md`, `03 Game Architecture -- ECS, State Machines and Object Pooling.md` (Object Pooling) | A complete, playable pygame game ("Dodge the Falling Blocks"): a real game loop with delta-time movement, explicit AABB collision detection, and an object pool of reusable falling obstacles. | `python game.py` |
| `state_machine_demo.py` | `03 Game Architecture -- ECS, State Machines and Object Pooling.md` (State Machines) | A finite state machine (Idle / Running / Jumping) for a character, driven by a scripted sequence of events through a transition table, with invalid transitions ignored. No pygame/window needed. | `python state_machine_demo.py` |
| `ecs_demo.py` | `03 Game Architecture -- ECS, State Machines and Object Pooling.md` (Entity-Component-System) | A from-scratch ECS: entities are bare IDs, components (`Position`, `Velocity`, `Health`) are plain dataclasses, and systems are plain functions operating over whichever entities have the right components -- run for 5 ticks with printed state each tick. Includes a comment contrasting this with a deep-inheritance approach to the same problem. No pygame/window needed. | `python ecs_demo.py` |
| `simple_client_server_prediction_demo.py` | `05 Multiplayer Networking in Games.md` | Client-side prediction + server reconciliation, entirely local: two in-process objects (`Client`, `Server`) exchange messages over `queue.Queue`, with a real background thread and `time.sleep(LATENCY)` standing in for simulated network latency -- no real sockets. Shows instant local prediction on input, then reconciliation (snap to authoritative position + replay unacknowledged inputs) as delayed server acks arrive. | `python simple_client_server_prediction_demo.py` |

## Notes

- These files intentionally have **no cross-imports** between each other
  -- every pattern is isolated in its own file so it can be read and run
  independently of the others.
- `game.py` is the only file requiring a display/window; the other three
  run fully in the terminal and can be run over SSH/headless.
