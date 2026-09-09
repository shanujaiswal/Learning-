# Why Games Move Away From Deep Inheritance

--> Conventional OOP (covered generally across the Full Stack folder) encourages modelling the world as a class hierarchy: `Entity -> Character -> Enemy -> FlyingEnemy`. This works until you need a flying enemy that's ALSO a boss, ALSO has a shield -- multiple orthogonal traits don't cleanly nest into a single tree, and you end up with either duplicated code or a fragile deep hierarchy (the classic "diamond problem" territory).
--> Games also have a performance reason to avoid this: objects in a deep hierarchy tend to be large, heterogeneous, and scattered across memory, which is bad for CPU cache performance when you need to update thousands of them every frame (touched on in the previous chapter's frame budget discussion).
--> **Composition over inheritance** is the general fix, and games take it to its logical extreme with the **Entity-Component-System (ECS)** pattern.

# Entity-Component-System (ECS)

--> **Entity** -- just an ID. No behaviour, no data of its own.
--> **Component** -- pure data, no behaviour. `Position { x, y }`, `Velocity { dx, dy }`, `Health { current, max }`.
--> **System** -- pure behaviour, no data of its own. A `MovementSystem` runs once per frame over EVERY entity that has both a `Position` and a `Velocity` component, and updates position accordingly.

```csharp
// Components -- plain data
struct Position { float x, y; }
struct Velocity { float dx, dy; }

// System -- operates on all entities with both components
void MovementSystem(World world, float deltaTime) {
    foreach (var entity in world.Query<Position, Velocity>()) {
        entity.Position.x += entity.Velocity.dx * deltaTime;
        entity.Position.y += entity.Velocity.dy * deltaTime;
    }
}
```

--> A flying, shielded boss is now just an entity with `Position`, `Velocity`, `Flying`, `Shield` and `Boss` components attached -- no inheritance tree required, and any combination of traits is possible without restructuring class hierarchies.
--> The performance win: components of the same type are stored contiguously in memory (an array of all `Position` components, an array of all `Velocity` components), so a system iterating over them gets excellent CPU cache locality -- this is the "data-oriented design" mentioned in chapter 1's deep dive. Unity's DOTS/ECS framework and most modern engines lean on exactly this.
--> The trade-off: ECS is a bigger mental shift than plain OOP, and for small games the added structure can be overkill -- many simple/indie games still use conventional GameObject + component-attachment (Unity's default MonoBehaviour style) without going full ECS, since the cache-locality benefits only matter once you have thousands of entities.

# Finite State Machines (FSMs)

--> A **state machine** models an entity as being in exactly one of a fixed set of **states** at a time, with defined **transitions** between them triggered by events or conditions.

```
IDLE --(player presses move)--> WALKING
WALKING --(player presses jump)--> JUMPING
JUMPING --(lands on ground)--> IDLE
WALKING --(no input)--> IDLE
any state --(health <= 0)--> DEAD
```

```csharp
enum State { Idle, Walking, Jumping, Dead }

void Update() {
    switch (currentState) {
        case State.Idle:
            if (Input.MoveHeld()) currentState = State.Walking;
            break;
        case State.Walking:
            if (Input.JumpPressed()) currentState = State.Jumping;
            else if (!Input.MoveHeld()) currentState = State.Idle;
            break;
        // ...
    }
    if (health <= 0) currentState = State.Dead;
}
```

--> FSMs are the standard way to manage character behaviour (idle/walk/run/jump/attack/dead), AI behaviour (patrol/chase/attack/flee), and even overall game state (menu/playing/paused/game-over) -- keeping every allowed transition explicit prevents the "impossible state" bugs that arise from tracking behaviour with a pile of loose boolean flags instead.
--> For more complex AI, FSMs are often generalized into **behaviour trees**, which compose small reusable conditions/actions into a tree rather than a flat set of states -- worth knowing exists, though FSMs cover the large majority of practical cases.

# Object Pooling

--> Instantiating and destroying objects (bullets, particles, enemies) every frame sounds harmless, but both operations are expensive: allocation has to find free memory, and destruction in garbage-collected languages (C# in Unity, or JavaScript/Python as covered in this vault's frontend and backend notes) marks memory for later cleanup by the **garbage collector**.
--> A GC pass can take an unpredictable amount of time and, critically, can run at any moment -- including in the middle of a frame that was supposed to take 16.6ms. A GC-caused frame spike is a visible stutter, and games spawning/destroying hundreds of bullets or particles per second are a textbook way to trigger frequent GC pauses.
--> **Object pooling** fixes this by never actually destroying objects: a fixed pool of pre-allocated objects (e.g. 200 bullets) is created once up front. "Spawning" a bullet just takes an inactive one from the pool and resets/repositions it; "destroying" it just deactivates it and returns it to the pool.

```csharp
class BulletPool {
    Queue<Bullet> available = new Queue<Bullet>();

    Bullet Get(Vector3 position) {
        Bullet b = available.Count > 0 ? available.Dequeue() : new Bullet();
        b.Reset(position);
        b.gameObject.SetActive(true);
        return b;
    }

    void Release(Bullet b) {
        b.gameObject.SetActive(false);
        available.Enqueue(b);
    }
}
```

--> This trades a small amount of upfront memory (objects that sit inactive when not needed) for eliminating allocation/GC churn during gameplay -- a very common games-specific pattern that has little equivalent urgency in typical backend code, where GC pauses of a few milliseconds are usually invisible to users.

# Deep Dive -- Combining All Three

--> These three patterns compose naturally in a real game: an ECS `System` can manage a pool of bullet entities, spawning them by re-activating pooled entity IDs and attaching fresh component data, while each bullet's own behaviour (idle in pool / flying / exploding) is tracked by a small state machine.
--> The common thread across ECS, FSMs and object pooling is the same one from chapter 1: predictability and cache-friendliness under a strict per-frame budget matter more in games than the abstraction purity that's often prioritized in general application code -- these patterns look unusual coming from a typical OOP or web background specifically because they're solving a constraint (steady 60fps) that most software never has to satisfy.
