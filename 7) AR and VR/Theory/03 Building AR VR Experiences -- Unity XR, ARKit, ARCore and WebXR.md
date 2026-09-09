---
# Unity XR Interaction Toolkit: The Cross-Platform Default

--> Unity's **XR Interaction Toolkit (XRIT)** is the dominant way to build XR apps that need to ship across multiple headsets (Quest, Vive, Vision Pro, Windows MR) without rewriting input code per platform.
--> It provides ready-made components for the recurring XR patterns: `XR Ray Interactor` (pointing at objects with a controller or hand), `XR Grab Interactable` (picking objects up), teleportation providers, and locomotion systems -- the same GameObject/component model covered in the Game Development folder's Unity material, just with XR-specific components layered on top.
--> Underneath XRIT sits **OpenXR**, a cross-vendor standard API that abstracts away per-headset SDK differences (Meta's SDK, Vive's SDK, etc.) the same way WebGL abstracts away per-GPU differences -- targeting OpenXR through Unity is what lets one project build for multiple headsets with minimal per-platform code.

```csharp
// Simplified Unity XRIT setup -- reacting to a controller trigger press
public class XRGrabExample : MonoBehaviour
{
    public XRBaseController controller;

    void Update()
    {
        if (controller.inputDevice.TryGetFeatureValue(
                CommonUsages.triggerButton, out bool pressed) && pressed)
        {
            Debug.Log("Trigger pressed -- grab logic runs here");
        }
    }
}
```

# Native Platform SDKs: ARKit and ARCore

--> **ARKit** (Apple, iOS) and **ARCore** (Google, Android) are native mobile AR SDKs that expose a phone's camera + motion sensors as SLAM-tracked AR sessions -- plane detection (finding the floor/tabletop), light estimation, and object/face tracking come built in.
--> These are what most consumer AR apps (furniture placement, try-on filters, AR games) are built on when they don't go through Unity -- you write directly against Swift/ARKit or Kotlin-Java/ARCore for maximum native performance and tightest OS integration.
--> Unity can also sit ON TOP of ARKit/ARCore via Unity's **AR Foundation** package, which gives you a single Unity API that maps down to whichever native SDK is available on the device -- the mobile-AR equivalent of what OpenXR does for headsets.

# WebXR: The Browser-Based, No-Install Path

--> **WebXR** is a browser API (building on WebGL, and connecting directly to this vault's Full Stack JavaScript / Web APIs notes) that lets a normal web page request an immersive AR or VR session -- no app store, no install, just a link.
--> The trade-off is real: WebXR content runs inside a browser's JS engine and sandboxing layer, so it generally can't match a native Unity/Unreal build's raw performance or access to platform-specific features, but it wins enormously on distribution -- a URL works instantly on any WebXR-capable browser/headset combination.

```javascript
// Conceptual WebXR session request in the browser
async function enterVR() {
  if (navigator.xr) {
    const supported = await navigator.xr.isSessionSupported('immersive-vr');
    if (supported) {
      const session = await navigator.xr.requestSession('immersive-vr', {
        requiredFeatures: ['local-floor']
      });
      // session now drives the render loop via session.requestAnimationFrame(...)
    }
  }
}
```

--> In practice, WebXR apps are usually written with a helper library (Three.js's `WebXRManager` or the higher-level A-Frame/Babylon.js) rather than raw WebXR calls, for exactly the same reason plain WebGL is rarely used directly in the Full Stack JavaScript notes -- the raw API is verbose and low-level.

# Native App vs WebXR: Choosing Your Stack

--> **Choose native (Unity/Unreal + platform SDK)** when you need maximum frame rate headroom, access to hand/eye tracking APIs not yet standardized in WebXR, complex physics/rendering, or you're targeting app-store distribution anyway (enterprise training apps, high-fidelity games).
--> **Choose WebXR** when frictionless distribution matters more than peak fidelity -- marketing/product-demo experiences, museum/retail try-it-now kiosks, anything where asking a user to install an app first would kill your conversion rate.
--> ==> A common hybrid: prototype and validate an interaction idea quickly in WebXR (fast iteration, no build step), then port the validated design to Unity/native once you know it works and need the extra performance -- the same "prototype cheap, then invest" logic that shows up across the Full Stack notes for MVP-first development.

# Deep Dive: Why Frame Budget Discipline Differs From Normal Game Dev

--> A traditional single-screen game targeting 60fps has a ~16.6ms frame budget. A VR app targeting 90fps has to render TWO views (one per eye, at slightly different angles) inside an ~11ms budget -- effectively less than a third of the per-eye rendering time of a flat-screen 60fps game, per frame.
--> This is why XR projects lean hard on techniques rarely needed in flat-screen dev: **single-pass stereo rendering** (rendering both eyes in one GPU pass by exploiting their near-identical view, instead of two full separate render passes), aggressive LOD (level of detail) falloff, and foveated rendering (Vision Pro, Quest Pro) which uses eye tracking to render peripheral vision at lower resolution than wherever the user is actually looking -- since the eye's fovea is the only region with high visual acuity anyway.
