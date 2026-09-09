"""
Finite State Machine Demo -- Idle / Running / Jumping
=======================================================
Standalone, focused demonstration of the FSM pattern described in the
Theory notes (Architecture -> State Machines), isolated from game.py so
the pattern is easy to see on its own.

Concept
-------
A finite state machine has:
  - A fixed set of STATES (Idle, Running, Jumping).
  - EVENTS/inputs that can trigger a transition (move, stop, jump, land).
  - TRANSITION RULES: for each (state, event) pair, at most one valid
    next state. Invalid transitions (e.g. "jump" while already jumping)
    are simply ignored.
  - Optional enter/exit hooks per state (here: just print statements).

This file has no pygame dependency and no window -- it just simulates a
character receiving a scripted sequence of inputs over a fake update
loop, printing every state change so the transition logic is visible.

RUN
---
    python state_machine_demo.py
"""

from enum import Enum, auto


class State(Enum):
    IDLE = auto()
    RUNNING = auto()
    JUMPING = auto()


class Event(Enum):
    MOVE = auto()      # movement key held down
    STOP = auto()       # movement key released
    JUMP = auto()        # jump key pressed
    LAND = auto()        # character touches the ground again


class CharacterFSM:
    """A minimal, explicit FSM. `transitions` maps (current_state, event)
    -> next_state. Anything not in the table is an invalid transition for
    that state and is ignored (e.g. JUMP while already JUMPING)."""

    # --- TRANSITION TABLE: the heart of the state machine -------------
    transitions = {
        (State.IDLE, Event.MOVE): State.RUNNING,
        (State.IDLE, Event.JUMP): State.JUMPING,

        (State.RUNNING, Event.STOP): State.IDLE,
        (State.RUNNING, Event.JUMP): State.JUMPING,

        (State.JUMPING, Event.LAND): State.IDLE,
        # Note: no (JUMPING, MOVE) or (JUMPING, JUMP) entries -- you can't
        # start running mid-air in this simple model, and you can't
        # double-jump. Both are simply ignored (no matching rule).
    }

    def __init__(self):
        self.state = State.IDLE
        self._was_moving_before_jump = False
        print(f"[FSM] initial state -> {self.state.name}")

    def handle_event(self, event: Event):
        key = (self.state, event)
        next_state = self.transitions.get(key)

        if next_state is None:
            print(f"[FSM] {self.state.name} + {event.name} -> (ignored, no valid transition)")
            return

        self._on_exit(self.state)
        old_state = self.state
        self.state = next_state
        self._on_enter(self.state)
        print(f"[FSM] {old_state.name} --{event.name}--> {self.state.name}")

    # Enter/exit hooks -- where you'd trigger animations, sounds, etc.
    def _on_enter(self, state):
        if state == State.RUNNING:
            pass  # e.g. start run animation
        elif state == State.JUMPING:
            pass  # e.g. trigger jump animation / apply upward velocity
        elif state == State.IDLE:
            pass  # e.g. start idle animation

    def _on_exit(self, state):
        pass


def simulate():
    """Simulated update loop: feeds a scripted sequence of inputs to the
    FSM, one 'frame' at a time, just like a real game loop would feed it
    keyboard/controller events."""
    fsm = CharacterFSM()

    scripted_inputs = [
        Event.MOVE,   # IDLE -> RUNNING
        Event.JUMP,   # RUNNING -> JUMPING (jumping while running)
        Event.JUMP,   # ignored: already JUMPING
        Event.LAND,   # JUMPING -> IDLE
        Event.MOVE,   # IDLE -> RUNNING
        Event.STOP,   # RUNNING -> IDLE
        Event.JUMP,   # IDLE -> JUMPING
        Event.MOVE,   # ignored: can't move while JUMPING in this model
        Event.LAND,   # JUMPING -> IDLE
        Event.STOP,   # ignored: already IDLE, nothing to stop
    ]

    for frame, event in enumerate(scripted_inputs, start=1):
        print(f"\n-- frame {frame}: input = {event.name} --")
        fsm.handle_event(event)

    print(f"\n[FSM] final state -> {fsm.state.name}")


if __name__ == "__main__":
    simulate()
