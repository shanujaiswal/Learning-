# Game Engine Architecture, Tools and Pipeline

- Overview of game engine architecture and core subsystems
- Rendering, physics, audio, animation, input, scripting, and asset management
- Content pipelines and how assets move from creation to runtime
- Tools for level design, animation, and asset optimization
- Engine extensibility, plugins, and custom tool development
- Build pipelines for platforms, packaging, and release automation
- Collaboration workflows, version control, and production pipelines

## Engine Architecture and Core Subsystems

Game engines provide a common runtime for multiple subsystems. Rendering shows graphics on screen, physics simulates movement and collisions, audio handles sound playback, animation drives motion, input processes user controls, and scripting connects gameplay logic.

## Content Pipeline and Asset Workflow

The content pipeline transforms art assets, audio, and data into optimized runtime formats. Artists and designers create source assets, then import tools preprocess textures, meshes, animations, and shaders for game execution.

## Tools for Level Design and Asset Optimization

Level editors, scene graph tools, and visual scripting environments let teams build worlds and gameplay without hand-editing engine source. Tools also optimize assets for performance by compressing textures, simplifying geometry, and baking animations.

## Build Pipelines and Platform Packaging

Building games for multiple platforms requires automated pipelines that compile code, package assets, and generate platform-specific binaries. Release automation includes configuration management, continuous integration, and deployment targets.

## Collaboration Workflows and Production Processes

Modern game development relies on source control, issue tracking, and task coordination across art, design, engineering, and QA. Effective collaboration workflows help teams manage large asset sets, iterate quickly, and deliver stable builds.

## Sample Workflow: Asset Creation to Runtime

1. Artist creates a texture and mesh in Blender or Maya.
2. Editor imports the asset and converts it to a runtime format.
3. Build pipeline compresses textures and generates LODs.
4. Automation scripts package the build for PC, console, or mobile.

## Tool and Engine Usage

- Unity: scene editor, asset importer, build pipeline, Package Manager.
- Unreal Engine: Blueprints, asset cookers, editor scripting, packaging.
- Godot: export templates, resource importer, scene system.
- Version control: Git LFS, Perforce, Plastic SCM for large binary assets.

## Real-World Design and Implementation Notes

- Modularize engine subsystems so rendering, audio, physics and scripting can be developed independently.
- Use editor tooling to expose game designers to level creation without code changes.
- Automate repetitive tasks with editor scripts and CI jobs to reduce manual build errors.
- Optimize assets early: texture atlases, mesh simplification, and audio compression save runtime memory and reduce load times.

## Example Tool Script

```sh
# Example Unity command-line build pipeline
/Applications/Unity/Hub/Editor/2024.1.0f1/Unity.app/Contents/MacOS/Unity \
  -projectPath /path/to/project \
  -executeMethod BuildScript.PerformBuild \
  -buildTarget StandaloneWindows64 \
  -quit
```

# Engine Subsystem Architecture

--> The bullet list at the top of this chapter names rendering, physics, audio, animation, input, and scripting as "subsystems" without saying what actually holds them together. In practice an engine is a **scene graph** plus a set of subsystems that all read and write into it every frame, coordinated by the same update/render loop covered in [[01 Game Development Fundamentals and the Game Loop]].
--> A **scene graph** is a tree (or, in ECS-style engines, a flatter set of parallel arrays keyed by entity ID -- see [[03 Game Architecture -- ECS, State Machines and Object Pooling]]) that stores every entity's transform (position/rotation/scale) relative to its parent. A car entity's wheel is positioned relative to the car, not to world origin, so moving the car automatically carries the wheel with it -- the engine resolves each entity's final world-space transform by walking up the tree and composing parent transforms.
--> Each subsystem consumes a different slice of the same scene graph on the same tick: physics reads/writes transforms and velocities, rendering reads transforms and mesh/material data to build a draw list, animation writes bone transforms that skin a mesh, audio reads transform + listener position to compute 3D panning/attenuation. This is why "modularize subsystems so they can be developed independently" (this chapter's own bullet list above) is harder than it sounds in practice -- every subsystem is coupled through the one shared scene graph, even when their code lives in separate modules.
--> The concrete ordering within a single frame typically looks like:

```
process_input()
fixed_update():           # zero or more physics steps, per ch.1's accumulator pattern
    physics_step()         # integrate rigid bodies, resolve collisions (ch.2)
    ai_tick()              # behavior trees / pathfinding re-evaluate (ch.7)
update(delta_time):        # once per rendered frame
    animation_update()      # advance animation state machines, write bone transforms
    gameplay_update()       # scripted game logic reacting to input + physics results
    scene_graph_resolve()   # compose final world-space transforms top-down
render():
    build_draw_list()       # cull, sort, batch by material/shader
    audio_mix()              # 3D positional mix based on resolved transforms
    present_frame()
```

--> Getting this ordering wrong is a classic source of one-frame-late bugs: if `render()` runs before `scene_graph_resolve()` finishes propagating a parent's move down to its children, objects visibly lag their parent by exactly one frame. Engines guard against this by making the update-then-render split a hard contract, not a suggestion -- the same discipline chapter 1 describes for keeping `render()` a pure read of state rather than a place that mutates it.

# The Asset Pipeline in Detail

--> "The content pipeline transforms art assets ... into optimized runtime formats" (this chapter's own summary above) is doing a lot of work in one sentence. Concretely, an asset pipeline is an **import + cook** process that runs once (at import/build time), not every frame, precisely so the runtime never pays for work that can be done ahead of time:
--> **Import** -- a source asset (a `.png`, `.fbx`, `.wav`, a Blender `.blend` file) is detected by the editor's asset watcher and converted into an engine-native intermediate representation (Unity's `.meta` + serialized asset, Unreal's uasset, Godot's `.import` sidecar file). This step also runs validation -- wrong texture dimensions, missing UVs, unsupported audio sample rates all get caught here rather than at runtime.
--> **Cook/bake** -- the intermediate asset is transformed into whatever the target platform's GPU/CPU actually wants: textures get compressed into a platform-specific format (BC7/ASTC/ETC2 depending on target hardware) and have mipmaps generated, meshes get LODs (Level of Detail -- simplified versions swapped in at distance) baked and are optimized for GPU vertex cache locality, audio gets compressed (Vorbis/Opus) and possibly pre-mixed, and lighting can be baked into lightmaps ahead of time rather than computed live.
--> **Packaging** -- cooked assets are bundled into the platform's distributable format (an Android APK/AAB, an iOS `.ipa`, a Steam depot), often content-addressed and chunked so patches only need to redownload changed bundles rather than the whole game.
--> The reason this whole pipeline exists as a separate offline step rather than happening at runtime is exactly the frame budget constraint from [[01 Game Development Fundamentals and the Game Loop]]: decompressing a source `.png` and generating mipmaps from scratch every time a level loads would blow any reasonable loading-time budget, let alone a per-frame one, so the expensive work is paid for once at build time and the runtime just streams pre-cooked bytes.

# A Concrete Cross-Platform Build Example

--> The command-line build snippet earlier in this chapter is Unity-specific and OS-specific (a hardcoded macOS `.app` path). The general pattern underneath it is what actually matters, and it's the same shape across engines and operating systems: invoke the editor binary **headlessly** (no GUI), point it at a project and a build method/target, and let it run to completion and exit.
--> Unity's headless build flags, generically (the *editor binary path* is the only OS-specific part -- everything after it is identical on Windows, macOS and Linux):

```sh
# Windows:  "C:\Program Files\Unity\Hub\Editor\2024.1.0f1\Editor\Unity.exe" ...
# macOS:    /Applications/Unity/Hub/Editor/2024.1.0f1/Unity.app/Contents/MacOS/Unity ...
# Linux:    ~/Unity/Hub/Editor/2024.1.0f1/Editor/Unity ...
<unity-editor-binary> \
  -batchmode \                                   # no GUI -- required for CI runners
  -nographics \                                  # skip GPU init, for headless CI machines
  -quit \                                         # exit automatically when the method returns
  -projectPath ./MyGame \
  -executeMethod BuildScript.PerformBuild \       # a static C# method that calls BuildPipeline.BuildPlayer
  -buildTarget StandaloneLinux64 \                # or StandaloneWindows64 / StandaloneOSX / Android / iOS
  -logFile build.log                              # batchmode has no console -- always redirect logs
```

--> `-batchmode` and `-nographics` together are what make this runnable on a CI server with no display attached at all -- a GitHub Actions/Jenkins/GitLab CI runner, none of which have a monitor or GPU driver stack installed. `-quit` is what turns an interactive editor session into a one-shot process suitable for a build script: without it, Unity would open and just sit there waiting for the (nonexistent) user.
--> **Godot's** equivalent is its `--headless` export mode, same shape, different flags:

```sh
godot4 --headless --export-release "Linux/X11" ./builds/mygame.x86_64
godot4 --headless --export-release "Windows Desktop" ./builds/mygame.exe
```

--> Both examples reduce to the same three ingredients: (1) a headless flag so no display/GPU context is required, (2) an explicit target platform string, (3) an explicit output path/method, run non-interactively so a CI job can capture its exit code. A CI pipeline (GitHub Actions, Jenkins, GitLab CI) wraps this invocation per-platform, runs them in parallel matrix jobs (one job per `-buildTarget`/export preset), and fails the pipeline if any exit code is non-zero -- this is the actual mechanism behind "release automation" and "continuous integration" named in this chapter's own bullet list, not just an abstract label.

# Deep Dive -- Hot Reloading and the Editor/Runtime Boundary

--> A subtlety the sample workflow above glosses over: in a live editor session, most engines don't require a full re-cook-and-restart cycle every time an artist tweaks a texture or a designer tweaks a value. **Hot reloading** is what makes iteration fast, and it works differently depending on what's being reloaded:
--> **Asset hot-reload** -- the editor's asset watcher (a filesystem watcher process, conceptually similar to a dev-server's file watcher in the Full Stack notes) detects that a source `.png`/`.fbx` changed on disk, re-runs just that one asset's import+cook step, and pushes the updated runtime asset into the already-running scene graph, replacing the old one in place. Because rendering reads texture/mesh data through an indirection (a handle/reference, not the raw bytes inline), swapping what the handle points to updates every entity using that asset without restarting the game -- the same "don't hardcode direct dependencies where an indirection lets you swap the implementation" idea that shows up as object pooling and component composition in [[03 Game Architecture -- ECS, State Machines and Object Pooling]].
--> **Script/code hot-reload** is harder and more limited: Unity recompiles changed C# scripts and reloads the assembly while preserving serialized field values on existing objects (domain reload), but it cannot preserve arbitrary in-memory state that wasn't serialized (a local variable mid-function, a non-serialized field) -- which is exactly why a script hot-reload can silently reset state that a hot-reloaded texture swap never would. Godot's GDScript, being interpreted, can hot-swap function bodies more seamlessly since there's no native compilation step to redo.
--> **Why this matters for the pipeline, not just the editor**: the same asset-indirection design that makes hot-reload possible in the editor is what makes patching/DLC possible at runtime after ship -- a live game can download a replacement asset bundle and swap it into the same handle-based indirection, without a full game update, for exactly the same architectural reason iteration is fast in the editor. Hot-reload and live-patching are the same mechanism at two different points in the pipeline (dev-time vs post-ship).
--> The trade-off, as with most performance/flexibility choices in this folder ([[02 Game Physics and Collision Detection]]'s broad-phase/narrow-phase split, [[07 Game AI, Behavior Trees and Pathfinding]]'s pathfinding time-slicing), is that the indirection layer that enables hot-reload has a small but nonzero runtime cost (a pointer/handle dereference instead of direct inline data) -- engines accept this cost everywhere because the alternative (direct references baked at compile time) would make both editor iteration and post-ship patching impossible.

# See Also

--> [[01 Game Development Fundamentals and the Game Loop]] -- the update/render loop and frame budget that every subsystem in this chapter's frame ordering has to fit inside.
--> [[02 Game Physics and Collision Detection]] -- the physics subsystem's own broad-phase/narrow-phase cost trade-offs, one concrete instance of the "modularized subsystem" idea in this chapter.
--> [[03 Game Architecture -- ECS, State Machines and Object Pooling]] -- the entity/component data layout that scene graphs and hot-reload's asset indirection both build on.
--> [[07 Game AI, Behavior Trees and Pathfinding]] -- AI's own tick, and its performance mitigations, slotted into the same per-frame subsystem ordering described above.
