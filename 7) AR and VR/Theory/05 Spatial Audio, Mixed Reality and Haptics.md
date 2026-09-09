# Spatial Audio, Mixed Reality and Haptics

- Spatial audio concepts and immersive sound design for XR
- Mixed reality blending: AR overlays, passthrough, and virtual objects
- Haptics and tactile feedback in VR and AR devices
- Context-aware interactions and multisensory user experiences
- Implementation patterns for audio, haptics, and mixed reality
- Comfort, accessibility, and sensory design best practices

## Spatial Audio Concepts and Immersive Sound Design for XR

Spatial audio simulates how sound arrives at the listener from different directions and distances. In VR and AR, 3D audio makes virtual objects feel anchored in the environment and helps users locate sound sources naturally.

## Mixed Reality Blending: AR Overlays, Passthrough, and Virtual Objects

Mixed reality combines virtual content with the real world. AR overlays annotate real objects, passthrough allows digital elements to be placed over live camera input, and virtual objects can be positioned relative to the user’s environment.

## Haptics and Tactile Feedback in VR and AR Devices

Haptics provide physical sensation through vibration, force, or texture simulation. In XR, haptics increase immersion by giving users feedback for interactions such as grabbing objects, hitting surfaces, or feeling impacts.

## Context-Aware Interactions and Multisensory User Experiences

Context-aware interactions adapt based on the user’s position, gaze, motion, and environment. Multisensory experiences combine visuals, audio, and haptics to create more convincing and engaging XR applications.

## Implementation Patterns for Audio, Haptics, and Mixed Reality

Building XR experiences requires careful integration of audio spatialization, haptic feedback systems, and mixed reality content. Common patterns include anchoring sound sources to scene objects, using state machines for haptic events, and blending virtual UI elements with real-world surfaces.

## Comfort, Accessibility, and Sensory Design Best Practices

Comfort and accessibility are essential in XR design. Reducing motion sickness, limiting sudden movements, and offering alternative interaction methods improve usability. Designers should also consider audio volume, captioning, and haptic intensity for users with different needs.

---
# Note

--> This is a depth-companion to `05 Spatial Audio, Mixed Reality and Haptics.md` -- that file stays as a high-level index; this one goes down to the mechanism level (HRTF math, head-tracking-driven audio updates, a worked positional-audio/haptics example, and a Deep Dive) to match the depth of chapters 01-04.

# HRTF: Why Spatial Audio Isn't Just Stereo Panning

--> Simple stereo panning only adjusts left/right VOLUME balance -- it can place a sound "more left" or "more right," but it can't make a sound feel like it's behind you, above you, or six feet away, because volume balance alone doesn't encode those cues.
--> A **Head-Related Transfer Function (HRTF)** is a per-direction filter that models how your own head, outer ears (pinnae), and torso physically reshape sound before it reaches your eardrums -- every direction a sound can come from has a slightly different filter, measured (or modeled) as an impulse response.
--> Three physical cues make this work:
--> **Interaural Time Difference (ITD)** -- a sound to your right reaches your right ear microseconds before your left ear; the brain uses this timing gap to localize azimuth (left-right angle).
--> **Interaural Level Difference (ILD)** -- your head physically shadows high frequencies from the far ear, so a sound on your right is quieter and duller in your left ear; strongest at high frequencies where wavelength is small relative to head size.
--> **Pinna spectral notches** -- the folds of your outer ear boost/attenuate specific frequency bands differently depending on ELEVATION, which is why you can tell a sound is above vs below you even though ITD/ILD alone can't distinguish front-back or up-down (both produce near-identical timing/level differences -- this is called the **cone of confusion**).
--> ==> Binaural spatial audio in XR works by convolving a mono sound source with the HRTF impulse response that matches the current direction FROM the listener TO the source, then feeding the result to stereo headphones -- this is why spatial audio in a headset requires headphones/earbuds specifically, not external speakers, to work correctly.

# How Head-Tracking Updates the Audio Field in Real Time

--> The critical trick that makes spatial audio feel "anchored in the world" rather than "glued to your head" is that the HRTF filter applied must be recomputed EVERY time the listener's head orientation changes, using the sound source's position relative to the NEW head orientation, not the original one.
--> Concretely, each frame: take the sound source's world-space position -> subtract the listener's head position and apply the inverse of the listener's head-rotation quaternion (the same view-space transform math from chapter 2) -> this yields the source's direction in head-relative coordinates (azimuth + elevation) -> look up or interpolate the matching HRTF impulse response -> convolve.
--> If this step is skipped or lagged, a sound source that is actually "in front of you" will appear to rotate WITH your head as you turn -- breaking the illusion that it's a fixed object in the room, and reintroducing the same sensory mismatch problem covered in chapter 1's motion-to-photon discussion, just in the audio domain instead of the visual one.
--> This means spatial audio has its own latency budget parallel to visual motion-to-photon latency -- audio-to-ear latency needs to track head rotation fast enough that the brain doesn't detect the sound "dragging" behind a head turn; engines typically update the HRTF convolution parameters every audio callback block (often 5-10ms buffers) rather than once per video frame, since audio buffers run on a separate, tighter clock than the render loop.
--> Distance is handled separately from direction -- once the direction-based HRTF filter is applied, a distance-attenuation curve (inverse-square-law-based, but usually authored as a custom curve for game-feel reasons) and air-absorption filtering (higher frequencies roll off faster over distance, mimicking real air) are layered on top.

# Worked Example: Positional Audio Attenuation in Unity

--> A minimal Unity setup combining 3D spatial blend, a custom rolloff curve, and a head-tracked listener (the `AudioListener` component, which Unity automatically attaches to the XR camera rig so its position/rotation track the headset):

```csharp
// Attach to any GameObject that emits sound -- e.g. a virtual object the user can walk around
[RequireComponent(typeof(AudioSource))]
public class SpatialSoundEmitter : MonoBehaviour
{
    private AudioSource source;

    void Start()
    {
        source = GetComponent<AudioSource>();

        // 0 = fully 2D (no spatialization), 1 = fully 3D (HRTF-spatialized).
        // XR sound effects that should feel "in the room" are almost always 1.
        source.spatialBlend = 1f;

        // Use a custom rolloff curve so volume falls off believably rather than
        // linearly -- linear rolloff makes distant sounds cut off too abruptly.
        source.rolloffMode = AudioRolloffMode.Custom;
        AnimationCurve curve = new AnimationCurve(
            new Keyframe(0f, 1f),     // full volume at the source
            new Keyframe(2f, 0.5f),   // half volume by 2 meters
            new Keyframe(10f, 0.05f), // nearly inaudible by 10 meters
            new Keyframe(20f, 0f)     // fully silent beyond max distance
        );
        source.SetCustomCurve(AudioSourceCurveType.CustomRolloff, curve);
        source.maxDistance = 20f;

        // Spatializer plugin (Oculus Spatializer, Resonance Audio, etc.) does the
        // actual HRTF convolution once spatialize is enabled -- Unity's built-in
        // panning alone is NOT true HRTF, just distance + stereo pan.
        source.spatialize = true;
    }
}
```

--> The `AudioListener` doesn't need any code here -- it inherits the XR rig's head-tracked transform automatically, so every frame Unity's audio engine reads the listener's current world position/rotation, computes each active `AudioSource`'s position relative to it, and feeds that into the spatializer plugin for HRTF convolution -- exactly the per-frame recompute described above, just handled for you by the engine rather than hand-written.

# Haptic Feedback: Intensity Curves, Not Just On/Off

--> Naive haptics trigger a single fixed-strength buzz on every event ("collision happened -> vibrate at 100%"), which feels the same whether an object was grazed or slammed into -- throwing away information the user's sense of touch is fully capable of perceiving.
--> A believable haptic response maps some physical quantity (impact velocity, grip force, penetration depth) through an intensity curve to amplitude and duration, the tactile equivalent of the audio rolloff curve above:

```csharp
// Map collision impact speed to a haptic pulse -- steeper impacts feel stronger
// and last slightly longer, gentle touches barely register, matching real feedback.
public void OnCollisionEnter(Collision collision, XRBaseController controller)
{
    float impactSpeed = collision.relativeVelocity.magnitude;

    // Clamp and normalize into a 0-1 range against a tuned max speed
    float t = Mathf.Clamp01(impactSpeed / 5f);

    float amplitude = Mathf.Lerp(0.05f, 1.0f, t);      // barely-felt -> max buzz
    float duration   = Mathf.Lerp(0.02f, 0.15f, t);    // short tap -> sustained pulse

    controller.SendHapticImpulse(amplitude, duration);
}
```

--> ==> The general pattern -- normalize a real physical quantity into 0-1, then remap through a tuned curve (linear, exponential, or an authored `AnimationCurve` for full artistic control) into amplitude/duration/frequency -- is the same shape as the audio distance-rolloff curve above; XR sensory feedback design across audio, haptics, and even visual feedback (flash intensity on impact) tends to reuse this exact "normalize -> curve -> output" pattern.

# Deep Dive: Latency Budgets for Haptic Feedback

--> Touch has its OWN perceptual latency threshold, separate from the ~20ms visual motion-to-photon threshold from chapter 1 -- human tactile perception can detect gaps between a triggering event (e.g., a virtual object touching a virtual surface) and the haptic pulse arriving at as little as **~10ms**, and mismatches beyond roughly 20-30ms between a VISUAL collision and its haptic pulse start to feel "disconnected," the same way audio-visual sync errors become noticeable past a certain threshold in video.
--> The haptic pipeline has its own stack of delays that add up exactly like the visual motion-to-photon pipeline in chapter 1's Deep Dive: collision/event detection in the physics step -> game-logic dispatch to the haptics API -> Bluetooth/wireless transmission to the controller (this is often the single biggest contributor, since consumer controllers communicate over BLE-class radio links with their own polling intervals) -> the controller's haptic actuator's own mechanical response/rise time (a linear resonant actuator (LRA) has a physical spin-up time before it reaches target amplitude, unlike an idealized instant on/off).
--> This is why haptic events should be triggered as early as physically possible in the pipeline -- e.g., firing the pulse the instant a collision is DETECTED in the physics engine, rather than waiting for the full render frame that visually depicts it, deliberately front-loading the haptic side of the pipeline to offset its longer wireless/actuator latency relative to the display.
--> A second, related MR-specific challenge worth flagging here (since haptics and MR occlusion both live in the "make the virtual object believable as a physical one" problem space): **occlusion and passthrough compositing errors**. A virtual object is only convincing as "in the room" if real objects correctly occlude it when they're physically closer to the camera than the virtual object is -- this requires the depth of every real-world pixel in the passthrough feed, usually from a lower-resolution/noisier depth sensor than the color camera, to be compared against the virtual object's known render depth every frame. Depth-sensor noise at object edges causes visible "haloing" (a sliver of wrongly-composited background around a real hand reaching toward a virtual object), and depth-sensor latency relative to the color passthrough feed can cause an occluding hand to appear to have a ghostly trailing edge during fast motion -- the same fundamental "two data streams updating at different rates/latencies" problem as the audio-visual and haptic-visual sync issues above, just between two different camera-derived signals instead of between input and output.

# Cross-References

--> Chapter 1's motion-to-photon latency and predictive-reprojection Deep Dive is the direct conceptual sibling of the haptic-latency and occlusion-latency problems above -- all three are instances of "two sensory/data channels must stay synchronized within a perceptual threshold or the brain flags a mismatch."
--> Chapter 2's coordinate-transform chain (local -> world -> view space via quaternion-based rotation) is exactly the math used above to recompute a sound source's head-relative direction every frame for HRTF lookup.
--> Chapter 4's comfort material and the "Midas touch"/sensory-mismatch discussion connects directly to why haptic and audio feedback are often used as CONFIRMATION channels for an interaction (see `06b XR Interaction Techniques, Visualization and Performance Optimization -- Deep Dive.md`) -- a haptic pulse or spatial-audio cue on selection reduces reliance on visual-only feedback, which matters when gaze/hand-tracking input is inherently noisier than a mouse click.
--> See `06 XR Interaction Techniques, Visualization and Performance Optimization.md` and its companion `06b` for how haptic/audio feedback reinforces interaction techniques like raycast and pinch selection.
