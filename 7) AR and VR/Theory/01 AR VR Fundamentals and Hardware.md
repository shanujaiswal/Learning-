---
# Head-Mounted Displays and Degrees of Freedom

--> A **Head-Mounted Display (HMD)** is a headset with one or two screens (or a single screen split optically) positioned close to the eyes, plus lenses that focus the image and sensors that track head movement.
--> **3DoF (3 Degrees of Freedom)** tracks only rotation -- pitch, yaw, roll. You can look around, but if you physically walk forward the scene doesn't move with you. Cheap mobile VR (old Google Cardboard/Daydream) was 3DoF.
--> **6DoF (6 Degrees of Freedom)** tracks rotation AND translation -- pitch, yaw, roll, plus X, Y, Z position. Walking forward, leaning, crouching all register. This is the baseline for any modern headset (Quest, Vive, Vision Pro) and is essential for presence -- without positional tracking, VR feels like watching a dome-shaped video, not "being" somewhere.

```
3DoF: rotation only         6DoF: rotation + position
     (look around)             (look around AND move through space)
        ↻ pitch                     ↻ pitch      → x
        ↻ yaw          vs           ↻ yaw        → y
        ↻ roll                      ↻ roll       → z
```

# Inside-Out vs Outside-In Tracking

--> **Outside-in tracking** uses external sensors/cameras placed around the room (base stations for the original HTC Vive, Kinect-style sensors) that observe the headset and controllers and triangulate their position. Very accurate, but requires setup and a fixed play space.
--> **Inside-out tracking** flips this -- cameras mounted ON the headset itself look outward at the room and use computer vision (SLAM, covered in chapter 2) to figure out where the headset is relative to its surroundings. No external hardware needed.
--> Inside-out tracking is why modern standalone headsets (Meta Quest line, Apple Vision Pro) can be picked up and used anywhere with zero setup -- it traded a bit of raw precision for massive convenience, and improvements in onboard computer vision have closed most of the accuracy gap.

# Key Hardware Players

--> **Meta Quest (2/3/Pro)** -- standalone, inside-out 6DoF, Android-based, the volume leader for consumer VR; also does passthrough AR.
--> **Apple Vision Pro** -- positions itself as a "spatial computer" rather than a VR headset, extremely high-resolution passthrough, eye tracking + hand tracking as the PRIMARY input (no handheld controller by default).
--> **HTC Vive** -- originally outside-in (base stations), higher-end/enterprise and sim-training focus.
--> **Microsoft HoloLens** -- optical see-through MR glasses, enterprise/industrial focus (maintenance overlays, training), true see-through rather than passthrough.
--> **Mobile AR** (ARKit on iOS, ARCore on Android) -- uses the phone's existing camera and sensors, no dedicated headset, the most widely distributed form of AR by user count (think furniture-placement apps, Snapchat/Instagram filters).

# Optical See-Through vs Video See-Through AR

--> **Optical see-through (OST)** -- you look through a transparent lens/waveguide and digital content is projected onto it, so your eyes receive real light from the world directly (HoloLens, most "AR glasses"). Real world always looks natural; digital overlays can look slightly translucent or dim in bright environments.
--> **Video see-through (VST) / passthrough** -- there is no transparent path to the real world at all; front-facing cameras capture the real environment and DISPLAY it on the internal screens alongside virtual content (Quest 3 passthrough, Apple Vision Pro). This gives full control over how the real world is composited (occlusion, relighting, blending) but always adds camera latency and can look slightly processed.
--> ==> The trade-off in one line: OST preserves the real world perfectly but constrains what you can do with virtual content; VST lets you do anything to the composite image but the real world itself now depends on camera + display quality.

# Latency: The Single Most Important Metric

--> **Motion-to-photon latency** is the time between a physical head movement and the corresponding pixel update reaching your eye. This is the metric that matters most in all of XR, more than resolution or field of view.
--> Your vestibular system (inner ear, balance) reports a head movement immediately. If the visual system reports the SAME movement even 20ms late, the two senses disagree about what's happening -- your brain interprets this mismatch as a possible poisoning event and triggers nausea as a defense response. This is **simulator sickness / VR sickness**.
--> The generally accepted threshold is roughly 20ms motion-to-photon latency as the point beyond which most users start noticing/suffering discomfort; below ~15-20ms it's largely imperceptible.
--> This is why VR requires a minimum ~90Hz refresh rate (each frame budget is ~11ms, leaving room for render + display + tracking latency) whereas flat-screen games can feel fine at 30-60fps -- there's no vestibular mismatch when you're looking at a monitor from outside the scene.

# Deep Dive: Why "Just Add a Faster GPU" Doesn't Fix Latency

--> Total motion-to-photon latency is a PIPELINE, not a single number: sensor sampling delay + sensor fusion/prediction + application render time + display scan-out + panel switching time all stack up. A faster GPU only shrinks the render-time slice.
--> The actual industry fix is **predictive tracking with reprojection**: the system predicts where your head WILL be a few milliseconds in the future (based on current velocity/acceleration), renders for that predicted pose, and then does a final cheap image warp ("timewarp"/"spacewarp") right before display to correct for any last-moment head movement -- shaving the perceived latency down far more than raw compute ever could.
--> This connects to the SLAM and coordinate-transform material in chapter 2 -- reprojection is literally reapplying a transform to an already-rendered frame rather than re-rendering the whole scene.
