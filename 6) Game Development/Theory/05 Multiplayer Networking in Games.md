# Client-Server vs Peer-to-Peer

--> **Client-server** -- one machine (a dedicated server, or one player's machine acting as "host") holds the authoritative game state; every other client sends inputs to it and receives world updates back. Used by nearly all competitive and persistent-world games (shooters, MMOs, battle royales).
--> **Peer-to-peer** -- every player's machine talks directly to every other player's machine, with no central authority. Simpler for small player counts (classic fighting games, small co-op sessions) but has no natural place to enforce a single "true" version of the world, and doesn't scale in connections as player count grows (each peer needs a connection to every other peer).
--> Most modern multiplayer games use client-server specifically because it gives a natural, trusted place to make final decisions -- covered further below as the **authoritative server** pattern.

# Why Games Can't Just "Wait for the Server"

--> A typical web request/response cycle (covered in the Full Stack and Computer Networks notes) tolerates a round trip of 100-300ms without the user noticing much -- a page takes a bit longer to load, that's it.
--> A game update loop runs every ~16.6ms (chapter 1). If a player presses "move forward" and the client waits for a full round trip to the server before showing any movement, that's a very noticeable, unplayable delay -- the HTTP request/response mental model (wait, then react) simply breaks down at this timescale.
--> Multiplayer games therefore also mostly avoid TCP/HTTP for real-time state updates and use **UDP** instead -- TCP's guaranteed-in-order delivery means one lost packet blocks and delays every packet after it (head-of-line blocking), which is worse for a fast-moving game than simply losing an old position update and getting a newer one moments later. This directly builds on the TCP vs UDP trade-offs from the Computer Networks folder.

# Client-Side Prediction

--> Instead of waiting for server confirmation, the client immediately moves the player locally the instant input is received, assuming the move will be accepted -- this makes local input feel instant regardless of network latency.
--> The client also keeps a short history of its recent inputs/states so it can later compare against what the server says actually happened.

```
Client:  input "move forward" -> apply locally immediately -> send input to server
Server:  receives input -> applies it authoritatively -> sends back resulting state
Client:  receives server state -> compares to its own predicted state
```

# Server Reconciliation

--> Because the server is authoritative, its version of events is the one that ultimately counts -- if the client's prediction turns out to be wrong (e.g. it predicted movement into a wall the server rejected, or another player's action interfered), the client must correct itself.
--> **Reconciliation**: when the server's confirmed state disagrees with the client's predicted state, the client snaps back to the server's version and RE-APPLIES any of its own inputs that the server hasn't processed yet, replaying forward from the corrected position. Done smoothly (often blended over a few frames rather than an instant snap) this is mostly invisible to the player, except during real corrections after packet loss or a large mispredict.
--> For OTHER players (not the local one), clients typically use **interpolation**: rather than snapping to each new position update the instant it arrives, the client smoothly animates between the last known and newly received positions, hiding the gaps between server updates.

# Lag Compensation

--> Even with prediction, there's a fundamental asymmetry: by the time a player's "I shot at that target" packet reaches the server, the target has already moved further on that player's screen than where it actually was.
--> **Lag compensation** addresses this server-side by rewinding: when a hit-registration packet arrives, the server briefly reconstructs where every relevant object WAS at the timestamp the shooting client actually saw (using each player's known latency/ping), and resolves the hit against THAT historical position rather than the current one.
--> This is why competitive shooters can feel like "what I saw is what I got" even though the server is, technically, checking hits slightly in the past.

# Authoritative Server -- Preventing Cheating

--> If clients are trusted to report their own position, health, or damage dealt, a modified client can simply lie ("I'm at the enemy's exact position", "I dealt 9999 damage") -- this is the basis of most cheating/hacking in multiplayer games.
--> The **authoritative server** pattern fixes this by making the server the single source of truth for anything that matters: clients only ever SUGGEST actions (inputs, not outcomes), and the server independently simulates and decides what actually happened, only ever sending clients the confirmed result.
--> This means client-side prediction is purely a presentation/feel trick -- the client's predicted state is never trusted for anything that affects other players or the persistent game state, only reconciled toward the server's version.
--> This connects directly to the general security principle from the Ethical Hacking and Cyber Security folders of "never trust client input" -- a game client is, from a security standpoint, exactly as untrustworthy as a web browser sending form data, just operating at a far higher update rate and with far higher stakes for real-time consistency.

# Deep Dive -- Why Even Authoritative Servers Still Get Cheated

--> Authoritative servers stop clients from directly lying about outcomes, but they can't stop a client from acting on information it legitimately has -- classic **aimbots** and **wallhacks** typically work by reading or altering what's rendered/received locally (auto-aiming based on legitimately-received enemy position data, or revealing data the game already sent but chose not to display), not by lying to the server.
--> Full defenses against this class of cheat move into anti-cheat systems (kernel-level monitoring, server-side statistical anomaly detection on aim/reaction patterns) that are a specialized security topic of their own, closely related to the general exploit and detection concepts in the Cyber Security folder rather than purely a networking one.
