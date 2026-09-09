# The Rendering Pipeline, Conceptually

--> Turning a 3D scene into a 2D image on screen happens in stages, run for every object, every frame, within the render() step of the game loop from chapter 1:
--> **Vertices** -- a 3D model (a "mesh") is really just a list of points in 3D space (vertices), grouped into triangles -- the smallest unit GPUs render.
--> **Transforms** -- each vertex is moved through several coordinate spaces: from the model's own local space, into world space (where the model actually is in the level), into view space (relative to the camera), and finally projected into screen space (2D coordinates on the actual display). This is almost entirely matrix multiplication -- the same linear algebra covered in this vault's Machine Learning and Data Science notes, just applied to geometry instead of data.
--> **Rasterization** -- once triangles are in screen space, the GPU figures out exactly which pixels on screen each triangle covers.
--> **Fragment (pixel) shading** -- for every covered pixel, a small program computes its final colour, factoring in lighting, textures, and material properties.
--> The output of all this is a 2D grid of coloured pixels -- the frame -- which gets presented to the display, completing one iteration of the render step.

# What a Shader Actually Is

--> A **shader** is a small program that runs on the GPU, not the CPU, and typically runs once per vertex or once per pixel/fragment -- NOT once for the whole frame.
--> **Vertex shaders** run once per vertex. Their main job is figuring out where that vertex ends up on screen (the transform step above) and passing along data (like texture coordinates) that fragment shaders will need.

```glsl
// Simplified vertex shader (GLSL)
void main() {
    gl_Position = projection * view * model * vec4(vertexPosition, 1.0);
}
```

--> **Fragment shaders** run once per pixel covered by a triangle. Their job is deciding that pixel's final colour -- sampling a texture, applying lighting math, blending colours.

```glsl
// Simplified fragment shader (GLSL) -- basic textured, lit pixel
uniform sampler2D mainTexture;
uniform vec3 lightDirection;

void main() {
    vec4 texColor = texture(mainTexture, texCoord);
    float brightness = max(dot(normal, -lightDirection), 0.0);
    gl_FragColor = texColor * brightness;
}
```

--> Both shader types are written in a GPU-specific language (GLSL for OpenGL/Vulkan/WebGL, HLSL for DirectX, or Unity's Shader Graph as a visual alternative), and engines compile and dispatch them for you -- you rarely hand-write raw shader code in Unity/Unreal for standard materials, but understanding what's happening underneath is essential once you need custom visual effects (outlines, dissolve effects, water, toon shading).

# Why GPUs Parallelize This So Aggressively

--> A CPU has a handful of powerful, flexible cores good at running varied sequential logic (gameplay code, the update() step). A GPU instead has thousands of small, simple cores, because shading a pixel is almost always the SAME small program run on DIFFERENT data (each pixel's own position, texture coordinate, normal) with no dependency between pixels.
--> This "same instructions, many independent data points" shape is called **SIMD (Single Instruction, Multiple Data)** parallelism, and it's exactly why a modern GPU can shade millions of pixels 60 times a second when a CPU running the same workload sequentially would fall far short of the frame budget from chapter 1.
--> This is the same underlying hardware reason GPUs are also used for training neural networks (covered in the Deep Learning folder) -- matrix/vector math over huge independent datasets is structurally the same kind of parallel problem as shading millions of independent pixels.

# 2D Sprites vs 3D Meshes

--> A **sprite** is just a flat 2D image (usually with transparency) drawn onto a quad (two triangles forming a rectangle) -- the basis of most 2D games. Animation is typically done by swapping between a sequence of sprite images (a "sprite sheet") rather than deforming geometry.
--> A **mesh** is a full 3D model made of many vertices and triangles, used in 3D games -- far more data and far more rendering cost per object than a sprite, which is why 2D games can support vastly more on-screen objects at the same frame budget.
--> Both ultimately go through the same rasterization and fragment-shading pipeline described above -- a sprite is really just a very simple, flat mesh with a texture on it.

# Textures, Briefly

--> A **texture** is an image mapped onto a mesh's surface via **UV coordinates** -- a 2D coordinate stored per vertex saying "which part of this image corresponds to this point on the model."
--> Beyond simple colour ("diffuse") textures, materials often layer several texture maps together: normal maps (fake fine surface detail via lighting tricks without adding real geometry), specular/roughness maps (how shiny a surface is), and emission maps (self-lit areas like glowing windows).

# Deep Dive -- Why Draw Calls Are Expensive, Not Triangles

--> A common beginner assumption is that triangle count is the main rendering cost. In practice, for most games, the number of **draw calls** (the number of separate times the CPU tells the GPU "render this object now") often matters more than raw triangle count, because each draw call carries fixed overhead -- state changes, CPU-GPU communication -- regardless of how simple the object is.
--> This is why engines aggressively **batch** rendering: combining many small objects that share a material into a single draw call whenever possible (static batching, GPU instancing for repeated objects like grass or trees). A scene with 10,000 nearly-identical objects rendered as one instanced draw call can be far cheaper than the same triangle count spread across 10,000 individual draw calls.
--> This mirrors a pattern seen elsewhere in this vault: the Database notes' discussion of batching many small queries into one round trip, and the Full Stack notes on reducing the number of separate HTTP requests a page makes -- in all three cases, the fixed "per-call" overhead dominates over the size of any individual call.
