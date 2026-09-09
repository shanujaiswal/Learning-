"""
Client-Side Prediction & Server Reconciliation Demo (local, no sockets)
========================================================================
Standalone, no pygame dependency, no real network. Demonstrates the core
concept from the Theory notes (05 Multiplayer Networking in Games):
client-side prediction + server reconciliation, using two in-process
Python objects standing in for "client" and "server", with an artificial
delay standing in for network latency.

Concept
-------
Naively, a networked game would work like this:
    1. Player presses a key.
    2. Client sends the input to the server.
    3. Server simulates movement, sends the new position back.
    4. Client waits for that response before moving the character on
       screen.
This makes the game feel laggy: the character only visibly moves after a
full round trip (here simulated as LATENCY seconds each way).

Client-side prediction fixes the *feel* of this: the client immediately
simulates its own movement locally using the same movement rule the
server uses, so the player sees an instant response. Every applied input
is stored (with a sequence number) in a small history buffer.

The input is ALSO sent to the server (with the same simulated delay). The
server is the authority: it simulates the input using its own copy of the
movement rule and sends back an authoritative state (position + the
sequence number of the last input it processed).

When that authoritative update arrives at the client, server
reconciliation happens:
    1. Snap the client's position to the server's authoritative position.
    2. Replay (re-simulate), on top of that authoritative position, every
       locally-stored input whose sequence number is NEWER than the one
       the server just acknowledged (i.e. inputs the server hasn't
       processed yet, which are still "in flight").
This keeps the client's on-screen position both responsive (no waiting
for round trips) AND eventually consistent with the authoritative server
state, correcting for latency/packet-loss/mispredictions without a
visible snap-back in the common case (here, since it's a deterministic
1D move-by-fixed-amount rule with no packet loss, the replay reproduces
the exact same position -- but the mechanism is identical to a real
networked game where it corrects a genuine misprediction, e.g. server
denies a move because of a wall the client didn't know about).

This file has no sockets: "network latency" is simulated with
time.sleep(LATENCY) inside a background thread that stands in for the
wire, so client and server really are two separate mutable objects
exchanging messages asynchronously, not just two function calls.

RUN
---
    python simple_client_server_prediction_demo.py
"""

import queue
import threading
import time

LATENCY = 0.15          # simulated one-way network delay, in seconds
MOVE_STEP = 1.0          # world units moved per "move right" input
TICK_INTERVAL = 0.05      # seconds between client ticks (20 Hz)


def move_rule(position: float, input_move: float) -> float:
    """The single source of truth for how an input changes position.
    Both the client (for prediction) and the server (for authority) call
    this SAME function, which is what makes prediction + replay work: the
    client can locally guess the server's outcome because it is running
    the exact same simulation rule."""
    return position + input_move


class Server:
    """The authoritative simulation. Receives (sequence_number, input_move)
    messages, applies move_rule(), and reports back the authoritative
    position plus the sequence number it just processed."""

    def __init__(self, inbox: "queue.Queue", outbox: "queue.Queue"):
        self.inbox = inbox     # client -> server messages
        self.outbox = outbox   # server -> client messages
        self.position = 0.0
        self.last_processed_seq = 0

    def handle_message(self, seq: int, input_move: float):
        # Simulate one-way network delay for the CLIENT -> SERVER hop.
        time.sleep(LATENCY)
        self.position = move_rule(self.position, input_move)
        self.last_processed_seq = seq
        print(f"[server] processed input #{seq} (+{input_move}) "
              f"-> authoritative position = {self.position:.1f}")
        # Simulate one-way network delay for the SERVER -> CLIENT hop.
        time.sleep(LATENCY)
        self.outbox.put((self.last_processed_seq, self.position))

    def run_forever(self):
        while True:
            item = self.inbox.get()
            if item is None:  # sentinel: shut down
                break
            seq, input_move = item
            self.handle_message(seq, input_move)


class Client:
    """The local player's view. Applies inputs to a PREDICTED position
    immediately (no waiting for the server), remembers each applied
    input in a history buffer, and reconciles that predicted position
    against authoritative updates from the server as they arrive."""

    def __init__(self, inbox: "queue.Queue", outbox: "queue.Queue"):
        self.inbox = inbox      # server -> client messages
        self.outbox = outbox    # client -> server messages
        self.predicted_position = 0.0
        self._next_seq = 1
        # History of NOT-YET-ACKNOWLEDGED inputs: seq -> input_move.
        self.pending_inputs: dict[int, float] = {}

    def apply_input_locally(self, input_move: float) -> int:
        """CLIENT-SIDE PREDICTION: apply the move rule immediately, using
        the exact same move_rule() the server will eventually use, so the
        player sees instant feedback instead of waiting a round trip."""
        seq = self._next_seq
        self._next_seq += 1
        self.predicted_position = move_rule(self.predicted_position, input_move)
        self.pending_inputs[seq] = input_move
        print(f"[client] predicted input #{seq} (+{input_move}) locally "
              f"-> predicted position = {self.predicted_position:.1f} "
              f"(server not yet acked)")
        # Fire the same input off to the server, over the "network".
        self.outbox.put((seq, input_move))
        return seq

    def reconcile(self, acked_seq: int, authoritative_position: float):
        """SERVER RECONCILIATION: snap to the authoritative position, then
        replay every input newer than `acked_seq` (inputs the server
        hasn't confirmed yet) back on top of it. In this deterministic
        demo the replay reproduces the same predicted position exactly,
        but in a real game with e.g. collision this is where a genuine
        misprediction gets visibly corrected."""
        # Drop every acknowledged input from the pending buffer.
        for seq in [s for s in self.pending_inputs if s <= acked_seq]:
            del self.pending_inputs[seq]

        corrected = authoritative_position
        for seq in sorted(self.pending_inputs):
            corrected = move_rule(corrected, self.pending_inputs[seq])

        mispredicted = abs(corrected - self.predicted_position) > 1e-9
        self.predicted_position = corrected
        print(f"[client] reconciled with server ack #{acked_seq} "
              f"(authoritative={authoritative_position:.1f}) "
              f"-> replayed {len(self.pending_inputs)} in-flight input(s) "
              f"-> final position = {self.predicted_position:.1f}"
              f"{'  [correction applied]' if mispredicted else ''}")

    def drain_server_updates(self):
        """Non-blocking check for any authoritative updates that have
        arrived from the server since we last checked."""
        while True:
            try:
                acked_seq, authoritative_position = self.inbox.get_nowait()
            except queue.Empty:
                return
            self.reconcile(acked_seq, authoritative_position)


def main():
    client_to_server = queue.Queue()
    server_to_client = queue.Queue()

    server = Server(inbox=client_to_server, outbox=server_to_client)
    client = Client(inbox=server_to_client, outbox=client_to_server)

    # The server's message loop runs on its own thread so the "network"
    # delay (time.sleep) doesn't block the client -- exactly like a real
    # server process the client talks to asynchronously over sockets.
    server_thread = threading.Thread(target=server.run_forever, daemon=True)
    server_thread.start()

    scripted_moves = [1.0, 1.0, 2.0, -1.0, 3.0]

    print("=== simulating player pressing 'move right' repeatedly ===\n")
    for move in scripted_moves:
        client.apply_input_locally(move)
        client.drain_server_updates()
        time.sleep(TICK_INTERVAL)

    # Let any still-in-flight server responses arrive before we stop.
    print("\n=== waiting for remaining in-flight server acks ===")
    deadline = time.time() + LATENCY * 2 + 1.0
    while client.pending_inputs and time.time() < deadline:
        client.drain_server_updates()
        time.sleep(TICK_INTERVAL)

    print(f"\nFinal client predicted position: {client.predicted_position:.1f}")
    print(f"Final server authoritative position: {server.position:.1f}")
    assert abs(client.predicted_position - server.position) < 1e-9, (
        "client and server disagree -- reconciliation bug"
    )
    print("Client and server agree: prediction + reconciliation converged.")

    client_to_server.put(None)  # sentinel: stop the server thread
    server_thread.join(timeout=1.0)


if __name__ == "__main__":
    main()
