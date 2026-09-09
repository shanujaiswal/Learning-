# Coordinate Systems and Spaces

--> **World space** -- the single, fixed frame of reference for the whole scene; every object's position is ultimately expressed relative to a shared origin (0,0,0). If the Game Development folder's engine notes covered "world position" for a GameObject, this is the same idea.
--> **Local space (object space)** -- coordinates relative to an object's OWN origin/pivot, before any parent transform is applied. A hand model's fingers are defined in the hand's local space, not the world's.
--> **Screen space** -- the final 2D pixel coordinates after the 3D scene has been projected through a camera. In XR there are effectively TWO screen spaces rendered per frame (one per eye) with a slightly different camera position for each, which is what produces stereoscopic depth.
--> Moving an object through the pipeline is a chain of matrix transforms: local space -> world space (via the object's transform matrix) -> camera/view space (via the camera's inverse transform) -> screen space (via a projection matrix). Every 3D engine, from Unity to a raw WebGL/WebXR app, is doing this chain every frame.

# Vectors and Matrices, Conceptually

--> A **vector** here just represents a position or direction with x, y, z components. Adding vectors moves you along both directions at once; scaling stretches or shrinks the direction.
--> A **matrix** (typically 4x4 in 3D graphics) encodes a combination of translation, rotation, and scale as a single object you can multiply a vector by to transform it. Chaining transforms (parent -> child -> grandchild) is just multiplying their matrices together.
--> You don't need to hand-derive matrix math to build XR apps -- Unity, Unreal, and WebXR/Three.js all provide `Transform`/`Object3D` abstractions -- but understanding that "moving/rotating/scaling an object" is ALWAYS a matrix multiply under the hood explains why order matters (rotate-then-translate produces a different result than translate-then-rotate) and why performance-sensitive code sometimes manipulates matrices directly.

# Quaternions vs Euler Angles: Why Quaternions Win

--> **Euler angles** represent rotation as three separate angles (pitch, yaw, roll) applied in sequence. Intuitive to read, but suffer from **gimbal lock** -- when one axis of rotation aligns with another, you permanently lose a degree of freedom and certain rotations become impossible to represent smoothly, causing visible snapping.
--> **Quaternions** represent rotation as a single 4-component value (x, y, z, w) encoding an axis and angle of rotation in one mathematical object, with no gimbal lock and mathematically clean interpolation between two rotations (**slerp** -- spherical linear interpolation) for smooth animation.
--> ==> Practically: every XR SDK reports headset/controller orientation as a quaternion internally (Unity's `Transform.rotation` is a `Quaternion` under the hood even though the Inspector shows Euler angles for human readability). You rarely write quaternion math by hand, but you need to know it's THE reason head-tracking rotation never "flips" or snaps unexpectedly.

# SLAM: How a Headset Knows Where It Is

--> **SLAM (Simultaneous Localization and Mapping)** is the technique that lets a device build a map of an unknown environment WHILE simultaneously figuring out its own position within that map, using only its own onboard sensors (cameras + IMU), with no external tracking hardware.
--> The loop, simplified: capture a camera frame -> detect distinctive visual features (corners, edges, textured patches) -> match those features against ones seen in previous frames -> triangulate how the camera must have moved to produce that shift -> fuse this visual estimate with the IMU's (accelerometer/gyroscope) motion data for robustness -> update both the estimated device pose AND the growing map of feature points.
--> This is exactly what powers inside-out tracking (chapter 1) on Quest, Vision Pro, and ARKit/ARCore mobile AR -- SLAM is why a phone can place a virtual couch on your floor and have it stay locked in place as you walk around it.
--> ==> IMU fusion matters because cameras alone are slow (30-60fps) and can fail in low light or on featureless surfaces (blank walls), while an IMU is fast (100s of Hz) but drifts over time; combining them (a "visual-inertial odometry" system) covers each one's weakness.

# Worked Example: 2D Rotation Matrix

--> Rotating a 2D point by angle θ around the origin uses this matrix:

```
| x' |   | cos(θ)  -sin(θ) | | x |
| y' | = | sin(θ)   cos(θ) | | y |
```

--> Concretely, rotate the point (1, 0) by 90° (θ = 90°, cos(90°) = 0, sin(90°) = 1):

```
x' = (0)(1) - (1)(0) = 0
y' = (1)(1) + (0)(0) = 1

Result: (1, 0) rotates to (0, 1) -- a quarter turn counter-clockwise, as expected.
```

--> This is the 2D case for intuition only -- real headset/hand rotation is 3D and handled via quaternions as above, but the same core idea (multiplying a coordinate by a matrix built from sine/cosine of an angle) generalizes directly into 3D rotation matrices, which quaternions are ultimately a more efficient/robust encoding of.

# Deep Dive: Why Floating Origin Matters at Scale

--> Standard 32-bit floating point loses precision the further a coordinate is from (0,0,0) -- fine for a room-scale VR app, but a problem for large-scale AR experiences (city-scale navigation, large warehouse digital twins) where objects far from the origin can visibly "jitter" due to precision loss.
--> The common fix is a **floating origin** technique: periodically re-center the world's origin to near the user's current position and shift all object coordinates accordingly, keeping every rendered object's coordinates small and precise even though the "logical" world is enormous. This is a spatial-computing-specific problem that rarely comes up in traditional flat-screen game dev at the same scale.
