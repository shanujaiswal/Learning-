"""
03 - Computer Vision Basics (Computer Vision chapter)
========================================================
Pixel-level computer vision fundamentals using only Pillow + numpy --
no heavy CV library (no OpenCV) required, so this is guaranteed runnable.

Demonstrates:
    1. Generating (or loading) a small test image
    2. Grayscale conversion (manual weighted-average formula)
    3. Edge detection via a manually-implemented Sobel-style convolution
    4. Basic thresholding (binarization)

Install:
    pip install pillow numpy

Run:
    python 03_computer_vision_basics.py

Output:
    Saves intermediate images (original, grayscale, edges, threshold) into
    an "output_images/" folder next to this script, and prints pixel-value
    statistics for each stage.
"""

from __future__ import annotations

import os

import numpy as np
from PIL import Image, ImageDraw

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output_images")


# ---------------------------------------------------------------------------
# 1. Generate a small synthetic test image (so the script needs no input file)
# ---------------------------------------------------------------------------
def generate_test_image(size: int = 128) -> Image.Image:
    """Create a simple RGB image with geometric shapes -- gives edge detection
    something interesting to find without needing an external image file."""
    img = Image.new("RGB", (size, size), color=(30, 30, 30))
    draw = ImageDraw.Draw(img)

    # A white rectangle
    draw.rectangle([20, 20, 60, 60], fill=(255, 255, 255))
    # A gray circle
    draw.ellipse([70, 20, 110, 60], fill=(160, 160, 160))
    # A diagonal line
    draw.line([10, 110, 118, 70], fill=(220, 220, 220), width=3)
    # A filled triangle
    draw.polygon([(20, 100), (60, 100), (40, 70)], fill=(200, 80, 80))

    return img


# ---------------------------------------------------------------------------
# 2. Grayscale conversion (manual, not PIL's built-in .convert("L"))
# ---------------------------------------------------------------------------
def to_grayscale(rgb_array: np.ndarray) -> np.ndarray:
    """Convert an (H, W, 3) RGB array to (H, W) grayscale using the standard
    luminosity weighted-average formula: 0.299 R + 0.587 G + 0.114 B."""
    r = rgb_array[:, :, 0].astype(np.float64)
    g = rgb_array[:, :, 1].astype(np.float64)
    b = rgb_array[:, :, 2].astype(np.float64)
    gray = 0.299 * r + 0.587 * g + 0.114 * b
    return np.clip(gray, 0, 255).astype(np.uint8)


# ---------------------------------------------------------------------------
# 3. Manual 2D convolution + Sobel edge detection
# ---------------------------------------------------------------------------
def convolve2d(image: np.ndarray, kernel: np.ndarray) -> np.ndarray:
    """Simple, from-scratch 2D convolution with zero-padding (valid same-size output)."""
    kh, kw = kernel.shape
    pad_h, pad_w = kh // 2, kw // 2

    padded = np.pad(image.astype(np.float64), ((pad_h, pad_h), (pad_w, pad_w)), mode="edge")
    out = np.zeros_like(image, dtype=np.float64)

    h, w = image.shape
    # Flip kernel for true convolution (vs. correlation)
    kernel_flipped = np.flipud(np.fliplr(kernel))

    for i in range(h):
        for j in range(w):
            region = padded[i:i + kh, j:j + kw]
            out[i, j] = np.sum(region * kernel_flipped)

    return out


SOBEL_X = np.array([
    [-1, 0, 1],
    [-2, 0, 2],
    [-1, 0, 1],
])

SOBEL_Y = np.array([
    [-1, -2, -1],
    [0, 0, 0],
    [1, 2, 1],
])


def sobel_edge_detection(gray: np.ndarray) -> np.ndarray:
    """Apply Sobel X and Y kernels and combine via gradient magnitude."""
    gx = convolve2d(gray, SOBEL_X)
    gy = convolve2d(gray, SOBEL_Y)
    magnitude = np.sqrt(gx ** 2 + gy ** 2)
    # Normalize to 0-255 for display
    magnitude = magnitude / magnitude.max() * 255 if magnitude.max() > 0 else magnitude
    return magnitude.astype(np.uint8)


# ---------------------------------------------------------------------------
# 4. Basic thresholding (binarization)
# ---------------------------------------------------------------------------
def threshold_image(gray: np.ndarray, threshold: int = 128) -> np.ndarray:
    """Binarize: pixels >= threshold -> 255 (white), else 0 (black)."""
    return np.where(gray >= threshold, 255, 0).astype(np.uint8)


# ---------------------------------------------------------------------------
# 5. Stats helper
# ---------------------------------------------------------------------------
def print_stats(name: str, arr: np.ndarray) -> None:
    print(f"{name:<20} shape={arr.shape}  min={arr.min():>3}  max={arr.max():>3}  mean={arr.mean():6.2f}")


def main() -> None:
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    print("=== Computer Vision Basics Demo ===\n")

    # 1. Generate test image
    img = generate_test_image(size=128)
    rgb_array = np.array(img)
    img.save(os.path.join(OUTPUT_DIR, "01_original.png"))
    print_stats("Original (RGB)", rgb_array)

    # 2. Grayscale conversion
    gray = to_grayscale(rgb_array)
    Image.fromarray(gray).save(os.path.join(OUTPUT_DIR, "02_grayscale.png"))
    print_stats("Grayscale", gray)

    # 3. Edge detection (Sobel)
    edges = sobel_edge_detection(gray)
    Image.fromarray(edges).save(os.path.join(OUTPUT_DIR, "03_edges_sobel.png"))
    print_stats("Sobel edges", edges)

    # 4. Thresholding
    binary = threshold_image(gray, threshold=128)
    Image.fromarray(binary).save(os.path.join(OUTPUT_DIR, "04_threshold.png"))
    print_stats("Threshold(128)", binary)

    print(f"\nSaved 4 intermediate images to: {OUTPUT_DIR}")
    print(
        "\nPipeline summary:\n"
        "  1. Original RGB test image generated with Pillow (rectangle, circle, line, triangle)\n"
        "  2. Grayscale via luminosity formula 0.299R + 0.587G + 0.114B\n"
        "  3. Edges via manually-implemented Sobel convolution (gradient magnitude of Gx, Gy)\n"
        "  4. Binary threshold at pixel value 128 (foreground/background separation)"
    )


if __name__ == "__main__":
    main()
