# OpenXR, ARKit and ARCore Internals, Hand/Eye Tracking, Cloud Anchors and Spatial UX

- OpenXR's action-based input model, action sets and reference spaces
- ARKit and ARCore platform-specific mechanics: LiDAR scene reconstruction, the Depth API, cloud anchors, and persistent/image/object tracking
- Hand-tracking joint models and gesture recognition pipelines; eye-tracking calibration and gaze privacy
- Multi-user/shared AR sessions and colocation via cloud anchors
- 3D UI/UX design systems: spatial layout, world-space vs screen-space UI, and XR design guidelines
- Privacy and ethical considerations of always-on AR cameras

## OpenXR's Action-Based Input Model

OpenXR abstracts input away from any specific controller by binding logical **actions** ("select," "grip," "move") to **action sets**, which are then bound to physical hardware paths through **interaction profiles** supplied per-device, so the same game logic works unmodified across a Quest controller, a Vive wand, or hand tracking.

## ARKit and ARCore Platform Mechanics

ARKit's LiDAR-equipped devices perform real-time scene reconstruction into a triangle mesh, while ARCore's Depth API estimates per-pixel depth from a single RGB camera using motion parallax, both feeding into occlusion and physics against the real world. Cloud anchors let a physical position resolved on one device be re-resolved on another device later or elsewhere, and persistent/image/object tracking anchor virtual content to specific real-world references across sessions.

## Hand-Tracking and Eye-Tracking Mechanics

Hand tracking reconstructs a skeletal joint hierarchy per hand from camera images and classifies that skeleton into gestures. Eye tracking estimates gaze direction from pupil/cornea imagery after a per-user calibration step, and because gaze reveals attention and intent, it is treated as sensitive biometric data.

## Multi-User and Shared AR Sessions

Colocation lets multiple devices agree on a shared coordinate frame anchored to the physical world, so virtual content placed by one user appears in the correct real-world location for every other user in the same physical space.

## Spatial UI/UX Design Systems

3D interfaces must choose between world-locked, body-locked, and screen-locked placement, and follow ergonomic layout rules (reach envelopes, minimum legible angular size) that don't apply to flat 2D UI design.

## Privacy and Ethics of Always-On AR Cameras

Devices that continuously capture the wearer's surroundings raise bystander consent and recording-law concerns that don't exist for a phone camera used deliberately and briefly.

---
# Note

--> This is a depth-companion to the index above, following the same pattern as `05 Spatial Audio, Mixed Reality and Haptics.md` -- the index stays a fast-reference summary; everything below goes to the mechanism level (OpenXR's actual API objects, ARKit/ARCore's actual reconstruction pipelines, joint-model math, gaze calibration math, colocation protocols, layout math, and two Deep Dives) to match the depth of chapters 01-06.

# OpenXR: Action Sets, Interaction Profiles and Spaces

--> Before OpenXR, every VR runtime (Oculus SDK, SteamVR, Windows MR) exposed input through its own incompatible API, so a game targeting three headsets needed three input backends. **OpenXR** is a Khronos-standard API that fixes this the same way OpenGL/Vulkan standardized rendering across GPU vendors (Chapter 2's coordinate-math foundations are the same across both): one API, many runtime implementations underneath.
--> **Actions, not buttons.** OpenXR never lets game code ask "is the A button pressed?" -- instead the game defines abstract **actions** with a type (boolean, float, 2D vector, or pose) and a semantic meaning ("select," "grip squeeze," "aim pose"), grouped into an **action set** (e.g. "gameplay" vs "menu," so a game can activate/deactivate whole groups of actions when opening a menu without individually disabling dozens of bindings).

```c
// Simplified OpenXR flow -- real code omits extensive error checking for clarity
XrActionSetCreateInfo setInfo = { .actionSetName = "gameplay" };
xrCreateActionSet(instance, &setInfo, &gameplaySet);

XrActionCreateInfo selectActionInfo = {
    .actionName = "select_object",
    .actionType = XR_ACTION_TYPE_BOOLEAN_INPUT,
};
xrCreateAction(gameplaySet, &selectActionInfo, &selectAction);
```

--> **Interaction profiles** are the layer that maps the abstract action to an actual hardware control, supplied as a runtime-recognized string path such as `/interaction_profiles/oculus/touch_controller` bound to a specific input path like `/user/hand/right/input/trigger/value`. The game NEVER hardcodes "Touch controller trigger" -- it suggests bindings for every profile it wants to support, and the runtime picks whichever profile matches the headset actually plugged in:

```c
XrActionSuggestedBinding bindings[] = {
    { selectAction, /* path: */ oculus_touch_trigger_path },
};
XrInteractionProfileSuggestedBinding suggestedBindings = {
    .interactionProfile = oculus_touch_profile_path,
    .suggestedBindings = bindings,
    .countSuggestedBindings = 1,
};
xrSuggestInteractionProfileBindings(instance, &suggestedBindings);
```

--> ==> This indirection -- action -> binding -> interaction profile -> physical control -- is exactly the same abstraction shape as an engine's input layer described in the Game Development folder's engine-overview chapter ("jump button works the same on keyboard, gamepad, and touch"), just one layer deeper because XR hardware diversity (controllers, hand tracking, eye tracking, even future device types) is far wider than "keyboard vs gamepad."
--> **Spaces.** OpenXR tracks poses (position + orientation) relative to a chosen **reference space**, not an absolute global frame, because "absolute" has no meaning across different headsets' tracking systems:
--> ==> **LOCAL** space -- origin fixed at the headset's position when tracking started; good for seated or standing-in-place experiences where the play area's absolute geometry doesn't matter.
--> ==> **STAGE** space -- origin at a real-world floor-level point the user configured (their room's guardian/boundary setup); required for room-scale experiences where the floor plane and physical walk area matter.
--> ==> **VIEW** space -- moves and rotates WITH the headset every frame; used for head-locked UI (a HUD that should always be dead-center in view) rather than world content.
--> Every `xrLocateSpace` call resolves one space's pose relative to another space at a specific timestamp, which is the same "coordinate transform chain" concept as the local->world->view space math in Chapter 2, just exposed as an explicit API object instead of an implicit engine-internal transform stack.

# ARKit and ARCore: Scene Reconstruction and Depth

--> **LiDAR scene reconstruction (ARKit).** LiDAR-equipped Apple devices emit a grid of infrared laser pulses and measure time-of-flight to build a dense per-pixel depth map directly, independent of scene lighting or texture (unlike pure camera-based depth, which needs the scene to have visible texture to triangulate against). ARKit fuses successive depth frames into a persistent **triangle mesh** of the room (`ARMeshAnchor`), classified per-face into semantic labels (wall, floor, table, seat, window) using an on-device ML model -- this mesh is what makes physically accurate occlusion (a virtual character correctly disappearing behind a real couch) and real-world physics (a virtual ball bouncing off a real floor) possible without the developer hand-placing any real-world geometry.
--> **ARCore Depth API (no LiDAR required).** Phones without a depth sensor still get a per-pixel depth map via a purely software technique: as the phone moves (even slightly, from natural hand tremor), the same real-world point projects to slightly different pixel locations across frames -- this **motion parallax** is fed into a trained depth-estimation neural network alongside the raw camera feed, producing a depth map that updates continuously as more frames accumulate motion evidence. This is inherently noisier at depth edges and fails with zero device motion (a phone held perfectly still gives the network no parallax to work from) compared to LiDAR's direct physical measurement -- the same "sensor fusion accuracy vs constraints" trade-off that shows up in Chapter 1's hardware-tier discussion.
--> **Cloud anchors.** A normal `ARAnchor` is only meaningful within the single AR session that created it -- it vanishes when tracking resets or the app closes. A **cloud anchor** solves persistence by uploading the anchor's surrounding **visual feature map** (a compact descriptor of nearby distinctive visual features, not raw camera images, for both bandwidth and privacy reasons) to a cloud service; resolving that anchor later -- on the same device after a restart, or on an entirely different device -- means the new session's camera feed is matched against the stored feature map until enough features align, at which point the anchor's pose is recovered in the new session's local coordinate frame.

```swift
// ARKit + ARCore Cloud Anchors (Google's cross-platform cloud anchor API), conceptually
func hostAnchor(at pose: ARAnchor) {
    cloudAnchorManager.hostCloudAnchor(pose) { cloudAnchor, error in
        guard error == nil else { return }
        // cloudAnchor.cloudIdentifier is a short string -- share THIS, not raw scan data,
        // with other devices/sessions that need to resolve the same physical anchor point.
        shareIdentifierWithOtherDevices(cloudAnchor.cloudIdentifier)
    }
}

func resolveAnchor(identifier: String) {
    cloudAnchorManager.resolveCloudAnchor(identifier) { cloudAnchor, error in
        guard error == nil else { return } // resolution can fail if features don't match well enough
        placeContent(at: cloudAnchor.pose)  // now anchored in THIS session's local coordinate frame
    }
}
```

--> **Persistent, image, and object tracking** are three related but distinct anchor types: **persistent anchors** (ARKit's `ARGeoAnchor`/world maps) let content return to the same spot across sessions on one device without a cloud round-trip; **image tracking** detects and tracks a known flat reference image (a poster, a book cover) and anchors content relative to it the instant it's recognized, independent of the room's geometry; **object tracking** goes further and recognizes a known *3D* object from a pre-scanned reference model, tracking its 6-DOF pose as it's picked up and rotated, not just detected once.

# Hand-Tracking Joint Models and Gesture Recognition

--> **The skeletal joint model.** Both OpenXR's hand-tracking extension and platform SDKs (Quest hand tracking, Vision Pro) represent a tracked hand as a fixed hierarchy of **26 joints** per hand: a wrist root, then for each of the 5 fingers a chain of up to 4 joints (metacarpal, proximal, intermediate, distal) ending at a fingertip, each joint reported as a full pose (position + orientation) plus a per-joint confidence/radius value, not just a bare 3D point -- orientation matters because a "pinch" gesture depends on the thumb and index tip approaching each other with a particular relative orientation, not merely proximity.

```
        distal--tip
         /
   intermediate
       /
   proximal
      /
  metacarpal
     \
      \___ wrist (root of the whole hand hierarchy)
```

--> **The recognition pipeline**, end to end: (1) an ML model (trained on the vendor's own hand-image dataset, running on-device) infers the 2D or 3D position of each of the 26 joints per hand from the headset's tracking cameras every frame; (2) the raw per-frame joint positions are temporally filtered (a low-pass or Kalman-style filter) to remove tracking jitter, since raw per-frame ML inference is noisy enough to make raw joint positions visibly shake even when the hand is still; (3) **gesture classification** runs on the filtered joint set, either as simple geometric rules (distance between thumb tip and index tip below a threshold = pinch) or as a small trained classifier over joint-angle features for more complex gestures (a fist, a thumbs-up, a pointing pose); (4) classified gestures are debounced with a minimum hold duration before firing, exactly analogous to input debouncing on a noisy physical button, because a single noisy frame misclassifying a mid-motion hand as a brief "fist" would otherwise fire spurious events constantly.

```python
def classify_pinch(joints, threshold_m=0.02):
    thumb_tip = joints["thumb_tip"].position
    index_tip = joints["index_tip"].position
    distance = euclidean_distance(thumb_tip, index_tip)
    return distance < threshold_m

class GestureDebouncer:
    def __init__(self, hold_frames_required=3):
        self.hold_frames_required = hold_frames_required
        self.consecutive_frames = 0
        self.is_active = False

    def update(self, raw_gesture_detected):
        if raw_gesture_detected:
            self.consecutive_frames += 1
        else:
            self.consecutive_frames = 0
        # Only flip state after several consistent frames, not a single noisy one --
        # the same false-positive-suppression idea as a physical switch debounce circuit.
        if self.consecutive_frames >= self.hold_frames_required:
            self.is_active = True
        elif self.consecutive_frames == 0:
            self.is_active = False
```

--> ==> Hand tracking's fundamental weakness is **self-occlusion**: cameras mounted on the headset can't see a finger curled behind the palm or behind another finger, so the ML model is inferring (not measuring) occluded joints from context and prior motion -- confidence values exist precisely so applications can detect this and fall back to a controller or a simplified gesture set when confidence drops, rather than trusting a guessed joint position for a precision task.

# Eye-Tracking Calibration and Gaze Privacy

--> **Why calibration is required.** Eye trackers estimate gaze direction from infrared cameras imaging the pupil and the corneal reflection of known infrared LED positions (the **pupil-corneal reflection** method), but the exact offset between where the eye is physically pointed and where the *person perceives themselves to be looking* varies per-user due to the individual angle between the eye's optical axis (what the geometry measures) and its visual axis (what the retina's fovea, the actual high-acuity spot, is pointed at) -- this is why every headset with eye tracking runs a short calibration sequence (look at a sequence of dots in known screen positions) before first use, fitting a per-user correction offset, and why gaze accuracy degrades for a different person picking up the same headset without recalibrating.
--> **Foveated rendering** is eye tracking's most direct payoff for performance: since only a small foveal region of the visual field is perceived at full sharpness (peripheral vision has far lower resolving power), the renderer renders a small region around the tracked gaze point at full resolution/quality and progressively lowers resolution outward, cutting GPU shading cost substantially with minimal *perceived* quality loss specifically because the eye's own biology can't tell the difference in the periphery -- directly extending Chapter 6's general performance-optimization toolkit with a technique that's only possible once gaze is known.
--> **Gaze as biometric and behavioral data.** Gaze reveals attention (what you looked at, and for how long) and can leak information the user never intended to share -- which UI element drew a longer look, a reaction to specific content, even inferred emotional/cognitive state from pupil dilation and saccade patterns in some research. This is qualitatively different from most other XR sensor data because it is a direct readout of *attention itself*, not just body pose, which is why platform policies (Apple's Vision Pro guidelines, for instance) explicitly forbid apps from accessing raw gaze data directly -- apps only receive the *result* of a gaze-based interaction (e.g. "this button was selected," computed by the OS from gaze + a confirming pinch) rather than a continuous stream of raw gaze coordinates, keeping the sensitive raw signal inside a privacy boundary the app never crosses.

# AR Cloud Anchors and Multi-User Colocation

--> **Colocation** is the problem of getting multiple physically co-present devices to agree on one shared coordinate frame, so that virtual content one user places appears in the correct real-world spot for every other user in the room -- without this, each device only ever knows its own private, arbitrarily-originated tracking space.
--> The typical protocol: one device **hosts** an anchor at a real-world reference point (uploading its visual feature map as covered above), then every other device that wants to join the same session **resolves** that same anchor identifier, and once resolved, each device computes the transform between its own local tracking origin and the shared anchor's pose -- from that point forward, any object's pose is expressed relative to the shared anchor and each device independently transforms it back into its own local space for rendering.

```python
class ColocationSession:
    def __init__(self):
        self.shared_anchor_pose_in_local_space = None  # set once resolution succeeds

    def place_shared_object(self, pose_relative_to_anchor):
        # Every device stores object poses relative to the SHARED anchor, never
        # relative to its own private tracking origin -- that's the one invariant
        # that makes the object appear in the same real-world spot for everyone.
        self.pending_objects.append(pose_relative_to_anchor)

    def render_object(self, pose_relative_to_anchor):
        # Convert shared-space pose into THIS device's own local tracking space
        # for rendering -- this local transform differs per device even though
        # the shared-space input pose is identical for all of them.
        return self.shared_anchor_pose_in_local_space.compose(pose_relative_to_anchor)
```

--> Once colocated, ongoing state (who moved what, whose turn it is, shared object physics) is synchronized over the network using the same authoritative-server/client-prediction/reconciliation patterns as the Game Development folder's multiplayer networking chapter -- colocation solves *where things are in physical space*; keeping *what state they're in* consistent across users is the identical networking problem multiplayer games already solve, just layered on top of a shared physical anchor instead of a shared virtual world origin.
--> ==> Colocation accuracy degrades with distance from the anchor and with time since resolution (accumulated tracking drift on each device), which is why well-designed multi-user AR experiences periodically re-verify alignment (e.g. checking that a known shared physical marker still renders in the expected place) rather than trusting one resolution to hold perfectly for an entire long session.

# 3D UI/UX Design Systems

--> Flat 2D UI design assumes a fixed viewing distance and a rectangular screen; none of that holds in XR, so spatial interfaces need their own placement and layout rules.
--> **World-space vs body-locked vs screen-locked (head-locked) UI** are the three placement strategies, each with a distinct trade-off:
--> ==> **World-locked** -- a panel fixed to a point in the environment (a control panel bolted to a virtual machine). Feels physically real and doesn't follow the user, but requires the user to physically walk/turn to it, so it's wrong for anything needed urgently or constantly (a health bar).
--> ==> **Body-locked** -- follows the user's torso/waist position but not their head rotation, so it stays in a consistent relative spot (like a wrist-worn menu) without swinging wildly every time the user glances around, a middle ground between the other two.
--> ==> **Head-locked (screen-space)** -- rendered in OpenXR VIEW space, always in the same spot in the visual field regardless of head movement. Guaranteed visible, but overused head-locked UI is one of the most common causes of VR discomfort (Chapter 4) because it visually "sticks" to the user in a way nothing in real vision does, breaking the vestibular-visual consistency Chapter 4 covers -- most guidelines restrict head-locked elements to brief, minimal HUD notifications rather than persistent large panels.
--> **Spatial layout and reach envelopes.** World-space UI intended for direct hand interaction (buttons, sliders) needs to sit within the user's actual comfortable arm-reach arc, not just anywhere convenient in the scene -- panels are typically curved (following a constant-radius arc centered on the user) rather than flat, so every point on the panel is equidistant from the eyes and no edge requires uncomfortable extra focus accommodation, and are angled slightly upward/inward to match a relaxed forearm's natural resting angle rather than requiring a fully extended arm.
--> **Minimum angular size, not minimum pixel size.** Because a 2D screen's text-size guideline ("at least 12px") has no meaning at a distance that changes, XR design guidelines specify minimum sizes in **angular degrees of visual field** (e.g. "interactive targets at least 1-2 degrees wide as seen from the user") so a panel placed farther away is authored physically larger to occupy the same angular size and stay legible/hittable -- this is the spatial-UI equivalent of Chapter 2's projection math, just applied as a design constraint instead of a rendering computation.
--> **XR design guideline convergence.** Despite being written by competing platform vendors, Apple's, Meta's, and Google's spatial design guidelines converge on the same handful of rules for exactly the reasons above: keep primary interactive content within a roughly 30-60 degree comfortable field of view without requiring head turns for core interactions, avoid placing critical content directly in the user's near-field peripheral edges where focus is weakest, and always give a gaze/hand-tracked interaction some secondary confirmation channel (a hover highlight, a haptic pulse per Chapter 5) since noisy tracked input lacks the tactile certainty of a physical button press.

# Privacy and Ethics of Always-On AR Cameras

--> A phone camera is used deliberately, briefly, and its framing is visible to bystanders (a raised phone signals "I might be recording"); an AR headset's cameras are continuously active by necessity (they're what makes tracking, scene reconstruction, and hand tracking work at all) and give bystanders no equivalent visible cue that they may be captured, which is the core ethical asymmetry always-on AR introduces.
--> **Bystander privacy.** People near an AR headset wearer have no way to opt out of incidental capture the way they could decline a photo, and the wearer themselves often isn't consciously aware of what's being captured moment to moment since capture is a side effect of tracking, not a deliberate photo action -- platform responses include hardware recording-indicator LEDs, restricting raw camera-feed access from third-party apps (apps typically receive processed outputs like a depth map or a mesh, not the raw video feed itself, mirroring the gaze-privacy pattern above), and geofencing that disables certain capture-adjacent features in sensitive locations.
--> **Recording laws** already distinguish one-party-consent jurisdictions (only the recording device's owner/user needs to consent) from two-party/all-party-consent jurisdictions (everyone being recorded must consent, at least for audio) for ordinary recording devices, and AR/MR headsets that continuously process camera and microphone data raise the same legal questions with far less clarity about what counts as "recording" versus "transient real-time processing that's immediately discarded" -- this is an unsettled area specifically because existing law was written assuming a device that starts and stops recording, not one that always processes sensor data as its baseline operating mode.
--> **Data minimization as the primary mitigation.** The consistent pattern across the cloud-anchor feature maps, the gaze-result-only API, and processed-depth-instead-of-raw-video access above is deliberately narrowing what leaves the device or reaches an app to the minimum needed for the feature to work, rather than exposing raw sensor streams and trusting every app's privacy policy -- this is the same principle as data minimization in the Security/Privacy folder's broader treatment, applied specifically to sensors that are physically incapable of being turned off during normal use without breaking the device's core tracking functionality.

# Deep Dive: Why Cloud Anchors Store Features, Not Images

--> A tempting naive design for cloud anchors is "just upload a photo of the anchor point and match new photos against it later" -- but this fails on both engineering and privacy grounds simultaneously, which is exactly why every production cloud-anchor system stores a compact **feature descriptor set** instead of raw imagery. Engineering: matching a live camera frame against a stored raw image requires expensive full-image comparison sensitive to lighting/viewpoint changes, whereas a feature descriptor (a sparse set of distinctive, viewpoint-and-lighting-invariant visual keypoints, conceptually similar to the feature-matching techniques underlying visual SLAM in the Robotics folder's perception chapter) is compact, fast to match against, and tolerant of the target being re-visited under different lighting or from a different angle. Privacy: a raw uploaded photo of someone's living room persisted on a cloud server is a much larger exposure than a mathematical descriptor set that cannot be trivially reconstructed back into a recognizable image -- reducing what's stored to the minimum needed for re-localization is the same data-minimization principle as the gaze-result-only and processed-depth-only API patterns above, applied at the storage layer instead of the API-access layer. Both properties fall out of the SAME design choice, which is why "store features, not images" is close to universal across ARKit/ARCore/Azure Spatial Anchors rather than being a vendor-specific quirk.

# Cross-References

--> Chapter 1's hardware-tier and sensor discussion is the direct prerequisite for this chapter's LiDAR-vs-Depth-API comparison and for why headsets need always-on cameras in the first place.
--> Chapter 2's coordinate-transform chain (local -> world -> view space) is exactly the math underlying OpenXR's reference-space model and the colocation transform composition above -- this chapter names and applies the concrete API objects that chapter's math corresponds to.
--> Chapter 3's ARKit/ARCore/WebXR integration coverage is the entry point this chapter goes underneath -- that chapter shows how to call these SDKs; this chapter explains what's happening on the other side of the call.
--> Chapter 4's comfort and motion-sickness material is the reason head-locked UI is used sparingly above, and is the broader category the gaze-privacy and bystander-privacy sections extend into non-visual, non-motion territory.
--> Chapter 5's haptic-confirmation and audio-confirmation patterns are exactly the "secondary confirmation channel" the spatial-UI-design section recommends pairing with noisy gaze/hand input.
--> Chapter 6's performance-optimization toolkit is directly extended by foveated rendering above, and its interaction-technique coverage (raycast, pinch selection) is what the hand-tracking gesture pipeline in this chapter feeds into.
--> The Game Development folder's multiplayer networking chapter supplies the state-synchronization layer that multi-user colocated AR sessions build on top of once a shared coordinate frame is established.
