# Rigid Body Basics

--> A **rigid body** is an object treated as a solid, non-deformable mass for physics purposes -- it has a position, velocity, and often mass and rotation, and the physics engine moves it according to forces applied to it.
--> The core integration each frame is simple Newtonian motion, applied using delta time from the previous chapter's game loop:

```csharp
velocity += acceleration * deltaTime;   // gravity is just a constant downward acceleration
position += velocity * deltaTime;
```

--> **Gravity** is typically just a constant acceleration (e.g. -9.8 m/s^2, though games usually tune this arbitrarily for "feel" rather than physical accuracy) added to velocity every frame.
--> This is **Euler integration** -- simple and cheap, but it accumulates error over time and can become unstable with large delta_time values or stiff forces (e.g. strong springs). More accurate games use **semi-implicit (symplectic) Euler** (update velocity first, then use the NEW velocity to update position, as shown above) or higher-order integrators like Runge-Kutta for precision-sensitive simulations.
--> Most 2D/3D games don't hand-roll this at all -- Unity's `Rigidbody`/`Rigidbody2D` and Unreal's physics bodies (both built on physics engines like PhysX or Box2D/Chipmunk) handle integration, and gameplay code just applies forces and reads resulting positions.

# Collision Detection Approaches

--> **AABB (Axis-Aligned Bounding Box)** -- the cheapest check: represent each object as a rectangle (2D) or box (3D) that never rotates, and test whether two boxes overlap.

```python
def aabb_overlap(a, b):
    return (a.x < b.x + b.width  and a.x + a.width  > b.x and
            a.y < b.y + b.height and a.y + a.height > b.y)
```

--> **Circle/Sphere collision** -- even cheaper: two circles overlap if the distance between their centers is less than the sum of their radii. Ideal for bullets, coins, particles.

```python
def circle_overlap(a, b):
    dx, dy = a.x - b.x, a.y - b.y
    distance_sq = dx*dx + dy*dy          # avoid sqrt for performance
    radius_sum = a.radius + b.radius
    return distance_sq < radius_sum * radius_sum
```

--> **SAT (Separating Axis Theorem)** -- for convex polygons that CAN rotate (unlike AABB): two convex shapes are NOT colliding if there exists at least one axis along which their projections don't overlap. Check all candidate axes (each shape's edge normals); if no separating axis is found on any of them, the shapes are colliding. More expensive than AABB but handles rotated rectangles, arbitrary convex polygons.
--> Real games and physics engines mix these: a fast circle or AABB check first, and only fall back to SAT (or full mesh collision in 3D) when the cheap check says "maybe."

# Broad-Phase vs Narrow-Phase

--> **Narrow-phase** collision detection is the exact, expensive test (SAT, mesh-vs-mesh) that tells you precisely whether and where two specific objects collide.
--> **Broad-phase** collision detection is a cheap first pass whose only job is to cut down the number of pairs that need a narrow-phase check at all -- it doesn't need to be exact, just conservative (it must never rule OUT a pair that's actually colliding).
--> Splitting into these two phases exists purely because narrow-phase checks are too expensive to run on every possible pair of objects in the scene.

# Why Naive Collision Checks Don't Scale

--> Checking every object against every other object is O(n^2) pairs -- a concept covered generally in the Python Notes folder's Python DSA Notes file. With 1,000 objects that's ~500,000 pair checks EVERY FRAME, which blows the ~16.6ms frame budget from the previous chapter almost immediately.

```python
# Naive O(n^2) -- fine for a handful of objects, catastrophic at scale
for i in range(len(objects)):
    for j in range(i + 1, len(objects)):
        if aabb_overlap(objects[i], objects[j]):
            resolve_collision(objects[i], objects[j])
```

--> **Spatial partitioning** fixes this by dividing the world into regions and only checking objects that share a region, since two objects on opposite sides of the map can never be colliding.
--> A **grid** divides the world into fixed-size cells; each object is bucketed into the cell(s) it overlaps, and collision checks only run within a cell (or against a small neighbourhood of cells).
--> A **quadtree** (2D) or **octree** (3D) recursively subdivides space into quadrants only where objects are dense, giving finer resolution in busy areas and coarser resolution in empty ones -- more setup cost than a flat grid, but better for very unevenly distributed worlds.
--> Either structure typically turns collision detection from O(n^2) toward roughly O(n log n) or better in practice, which is what makes physics viable in games with thousands of objects (particle effects, large battles, open-world crowds).

# Deep Dive -- Tunneling and Continuous Collision Detection

--> A subtle bug: if an object moves fast enough in one frame, it can completely pass THROUGH a thin wall between the "before" and "after" position checks, because discrete collision detection only ever checks the two snapshot positions, never the path between them. This is called **tunneling**, and it's a classic bug in bullets, fast projectiles, and anything moving many pixels per frame relative to the size of what it might hit.
--> **Continuous Collision Detection (CCD)** fixes this by sweeping the object's shape along its full movement path for the frame (e.g. ray-casting from old position to new position) rather than just testing the two endpoints. It's meaningfully more expensive, so engines (Unity's `Rigidbody.collisionDetectionMode`, for example) let you enable it selectively only for fast-moving objects like bullets, rather than globally.
--> This connects to a broader lesson that recurs across game physics: nearly every optimization here is a trade of GENERALITY for SPEED, applied selectively only where the cost is actually justified -- the same trade-off philosophy as indexing strategy in the DataBase notes, where you don't index every column, just the ones actually queried heavily.
