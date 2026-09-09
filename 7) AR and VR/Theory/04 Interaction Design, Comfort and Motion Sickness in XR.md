---
# Why 2D UI Instincts Fail in 3D

--> Most UI/UX intuition (covered in this vault's HTML/CSS and accessibility notes) assumes a flat rectangle, a mouse pointer with pixel-precise clicking, and a fixed viewport the user never physically moves relative to. XR breaks EVERY one of these assumptions at once.
--> There is no screen edge -- content can be behind the user, and "off-screen" isn't a meaningful concept the way it is in a webpage layout, so navigation/menu patterns built around edges (fixed headers, corner toasts) don't translate.
--> Depth adds an entire new design axis: text that's perfectly readable at arm's length becomes uncomfortable or illegible if placed too close (forces uncomfortable eye convergence) or too far (loses detail); buttons now need a physically believable size and distance, not just a CSS pixel size.
--> Input shifts from a single precise pointer to hand tracking (noisy, no tactile confirmation of a "click"), gaze (where you're looking is not always where you intend to interact -- the "Midas touch" problem, everything you glance at seemingly gets selected), and 6DoF controllers (precise but require the user to hold hardware).

# Comfort-First Design Principles

--> **Teleportation vs smooth locomotion** -- smooth locomotion (walking continuously via a joystick) feels more natural but is the single biggest cause of motion sickness because your eyes report movement while your inner ear reports standing still. Teleportation (point at a destination, blink/snap to it) avoids this mismatch entirely by removing the continuous visual motion, at the cost of feeling less immersive.
--> **Vignetting during movement** -- dynamically darkening/narrowing the peripheral field of view WHILE the user is moving reduces the peripheral vision cues that trigger vestibular conflict, then restores full FOV once movement stops. A cheap, widely used comfort trick.
--> **Maintaining a stable horizon** -- camera roll (tilting the horizon line) is far more nauseating than camera movement along a level plane; most comfort guidelines recommend keeping the horizon level even during vehicle/flight-style experiences, or providing a fixed visual reference point (a virtual cockpit/nose, dashboard) the eyes can lock onto as a stable frame -- this is why racing/flight VR games almost always keep a visible car interior or cockpit rather than a floating camera.
--> **Snap turning** -- rotating the camera in discrete increments (e.g. 30-45° snaps) rather than continuously, for the same reason teleportation beats smooth locomotion -- it eliminates the sustained visual-vestibular mismatch during the turn itself.

# Accessibility in XR

--> Motion sensitivity to the same content varies enormously person to person -- what's completely fine for one user can be nauseating within minutes for another, so comfort settings (locomotion type, vignette intensity, snap vs smooth turning) should always be USER-CONFIGURABLE, never hardcoded to one "correct" setting.
--> **Seated and one-handed alternatives** matter for users who can't stand for extended periods or only have reliable use of one hand/controller -- well-designed XR apps offer a seated calibration (recentering the play space around a seated position) and avoid interactions that REQUIRE two hands or a full-room play space when a single-controller equivalent is feasible.
--> Text legibility and color contrast guidance from the accessibility material in the Full Stack notes still applies conceptually in XR, but the specifics change -- font sizes need to be defined relative to real-world distance/angular size rather than pixels, and pure white text on pure black in a headset display can cause noticeable eye strain in a way it doesn't on a monitor.
--> Captions/subtitles in XR need a defined anchor strategy (locked to the user's view vs locked to world position) since there's no fixed screen bottom to dock them to by default.

# Deep Dive: Why "More Realism" Often Backfires

--> Increasing visual realism without a matching increase in physical fidelity WIDENS the sensory mismatch rather than closing it -- a highly photorealistic scene sets an expectation of real-world physical response (weight, resistance, momentum) that a headset and controllers can't deliver, making the gap between what you see and what you feel MORE jarring, not less. This is a spatial/vestibular cousin of the classic uncanny valley effect from character animation.
--> Concretely: a stylized, simplified VR roller coaster is often LESS nauseating than a hyper-realistic one, because the brain doesn't build up as strong an expectation of matching physical forces from stylized visuals -- the mismatch is smaller because the expectation was smaller.
--> ==> The practical rule of thumb many XR studios follow: increase realism only in tandem with increasing physical correspondence (real locomotion, haptics, force feedback) -- never increase visual fidelity alone as a way to make an experience feel "more premium," since on its own it usually makes comfort WORSE, not better.
--> This is also why most successful VR games deliberately choose stylized, cartoon-adjacent art direction rather than photorealism -- it's not merely an art choice, it's a comfort engineering decision.
