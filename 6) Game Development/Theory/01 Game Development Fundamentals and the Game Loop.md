# What a Game Engine Actually Does

--> A **game engine** (Unity, Unreal, Godot) is fundamentally a big pre-built application that already solves the boring, hard, repeated problems -- rendering, physics, audio, input handling, asset loading, scene management -- so you write gameplay code on top of a working foundation instead of rebuilding a renderer from scratch.
--> Concretely, an engine gives you: a rendering pipeline, a physics simulation, an audio mixer, an input abstraction layer (so "jump button" works the same on keyboard, gamepad and touch), a scene graph, and an editor for placing and configuring objects visually.
--> This is analogous to how a web framework like Express (covered in the Node and Express notes) saves you from writing an HTTP server from scratch -- except a game engine's "framework" also has to run continuously in real time rather than respond to discrete requests.

# The Game Loop

--> Every game, regardless of engine, boils down to one loop that runs continuously until the game exits:

```
while (game is running):
    process_input()      # read keyboard/mouse/gamepad state
    update(delta_time)    # advance simulation: physics, AI, game logic
    render()               # draw the current state of the world to the screen
```

--> **Input** -- poll or receive events for what the player is currently doing.
--> **Update** -- advance the world: move objects, run physics, check collisions, update AI, resolve game rules. This is where nearly all gameplay code lives.
--> **Render** -- draw the current state of the world onto the screen. Nothing in render() should change game state; it should be a pure reflection of it.

# Fixed vs Variable Timestep

--> The naive loop above updates the world once per rendered frame, but frames don't take a constant amount of time -- a busy scene renders slower than an empty one. If you move objects by a fixed amount every frame, gameplay speed literally changes with frame rate, which is a bug (a game that runs "faster" on a better PC).
--> The fix is **delta time**: measure how long the previous frame actually took, and scale all movement by it.

```csharp
void Update() {
    float deltaTime = Time.deltaTime; // seconds since last frame, e.g. 0.016
    transform.position += velocity * deltaTime;
}
```

--> This is **variable timestep** -- update() is called once per render, using whatever delta_time actually elapsed. Simple, but physics can become inconsistent or unstable if delta_time varies a lot (e.g. after a stutter).
--> **Fixed timestep** instead runs update() at a constant rate (e.g. always 50Hz) regardless of render rate, accumulating leftover time across frames:

```
accumulator += frame_time
while accumulator >= FIXED_STEP:
    physics_update(FIXED_STEP)
    accumulator -= FIXED_STEP
render(interpolate(accumulator / FIXED_STEP))
```

--> Fixed timestep makes physics fully deterministic and reproducible -- important for both stability and for multiplayer games where every client must simulate identically (see the Multiplayer Networking chapter). Unity's `FixedUpdate()` and Unreal's substepping physics both exist for exactly this reason.

# Frame Budget: The Constraint That Doesn't Exist Elsewhere

--> At 60 frames per second, you have 1000ms / 60 = **16.6ms** to do input, update and render for one frame. At 30fps that budget doubles to 33.3ms; at 120fps (common for competitive/VR titles) it shrinks to 8.3ms.
--> This is a hard real-time-adjacent constraint that has no real analogue in typical backend work covered in the Full Stack folder -- an API endpoint that takes 200ms instead of 50ms is a performance issue; a game frame that takes 20ms instead of 16.6ms is an immediately visible stutter.
--> Profiling in games therefore focuses obsessively on "cost per frame" rather than average throughput, and this budget mindset is why chapter 3 covers object pooling and ECS -- both exist to keep per-frame cost low and predictable.

# Engines Compared -- Unity vs Unreal vs Godot vs Building Your Own

--> **Unity** -- C#, huge asset store and community, historically strongest for 2D/mobile/indie titles, general-purpose scripting via MonoBehaviours. Good default choice for learning.
--> **Unreal Engine** -- C++ (with Blueprints visual scripting), industry standard for high-end 3D/AAA titles, best-in-class rendering out of the box, steeper learning curve.
--> **Godot** -- open-source, lightweight, GDScript (Python-like) or C#, excellent for 2D and increasingly capable 3D, no royalty/revenue-share model.
--> **Building your own engine** (e.g. with a low-level library like SDL or raw OpenGL/Vulkan) -- educational and gives full control, but you re-derive everything covered by an engine already: this is rarely the right choice for shipping a game, but is the best way to actually understand what an engine is doing under the hood.
--> The trade-off across all of them is the same one seen with web frameworks vs raw HTTP servers in the Node and Express notes: more built-in machinery means faster development but less control and more "fighting the framework" when your game doesn't fit its assumptions.

# Scenes, Entities and Components (Conceptually)

--> A **scene** is a self-contained snapshot of the game world at a point in time -- a level, a menu, a loading screen -- that can be loaded and unloaded as a unit.
--> An **entity** (also called a GameObject in Unity, or an Actor in Unreal) is any "thing" in the scene -- a player, an enemy, a coin, even an invisible trigger zone.
--> A **component** is a reusable chunk of behaviour or data attached to an entity -- a `SpriteRenderer`, a `Collider`, a `Health` component. An entity's identity is really just the sum of the components attached to it.
--> This entity + component idea is foundational and gets a full dedicated treatment (as ECS, alongside state machines and object pooling) in the next chapter, because it is the primary architectural alternative to the deep class-inheritance style covered in general OOP notes.

# Deep Dive -- Why "Just Make It Faster" Isn't the Real Optimization Story

--> A common beginner mistake is assuming frame budget problems are solved by writing individual functions faster. In practice the dominant costs in real games are usually **cache misses and allocations**, not raw CPU instruction count -- scattering related data across memory (as deep object hierarchies tend to do) means the CPU spends more time waiting on memory than computing.
--> This is precisely why engines increasingly favour data-oriented designs (contiguous arrays of components, as in ECS) over object-oriented ones: it's not about elegance, it's about keeping data that's accessed together physically close together in memory, which chapter 3 covers in depth.
