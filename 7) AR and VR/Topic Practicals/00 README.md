# AR and VR Practicals

These hands-on Python scripts support the theory notes in `6) AR and VR/Theory/`.
They are designed to demonstrate XR workflows, interaction pipelines, and performance-focused implementation concepts without requiring a real headset.

## Chapter mapping

| Theory file                                                                   | Practical(s)                                                            |
| ----------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `00 AR and VR Roadmap.md`                                                     | Orientation only — no dedicated script                                  |
| `01 AR VR Fundamentals and Hardware.md`                                       | `01_ar_vr_fundamentals_demo.py`                                         |
| `02 3D Math and Spatial Computing Basics.md`                                  | `02_3d_math_spatial_computing.py`                                       |
| `03 Building AR VR Experiences -- Unity XR, ARKit, ARCore and WebXR.md`       | `03_xr_scene_graph_simulation.py`, `05_xr_interaction_pipeline_demo.py` |
| `04 Interaction Design, Comfort and Motion Sickness in XR.md`                 | `04_comfort_and_motion_sickness_demo.py`                                |
| `05 Spatial Audio, Mixed Reality and Haptics.md`                              | concept coverage; no dedicated script                                   |
| `06 XR Interaction Techniques, Visualization and Performance Optimization.md` | `05_xr_interaction_pipeline_demo.py`                                    |

## Setup

These scripts use only the Python standard library, so no external packages are required.

## Tool and engine guidance

These demos are intentionally lightweight and platform-independent. They are meant to complement real XR toolchains such as:

- Unity with OpenXR, AR Foundation, or XR Interaction Toolkit
- Unreal Engine with the XR plugin system
- WebXR applications in browsers
- HoloLens, Quest, or mobile AR prototyping tools

Use these scripts to understand the underlying pipeline steps before implementing them in an engine:

- head tracking and pose updates
- scene graph management
- gaze/hand interaction logic
- frame timing and performance measurement

## Files

1. `00 README.md` — this file.
2. `01_ar_vr_fundamentals_demo.py` — basic XR fundamentals and device pipeline simulation.
3. `02_3d_math_spatial_computing.py` — spatial math and coordinate transforms for XR.
4. `03_xr_scene_graph_simulation.py` — a simulated XR scene graph with object anchoring.
5. `04_comfort_and_motion_sickness_demo.py` — comfort metrics and motion sickness risk simulation.
6. `05_xr_interaction_pipeline_demo.py` — end-to-end XR interaction and rendering pipeline example.
