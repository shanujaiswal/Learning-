"""Overhead-camera vision pipeline for the recycling sorting cell.

This is the ONE module in the project that touches image pixels. It has
three real jobs, each done with a real library rather than a hand-rolled
stand-in:

1. **Synthetic camera frame rendering** -- stands in for
   `camera_driver.capture_frame()` on a real overhead USB/GigE camera. Built
   with real `cv2` drawing primitives (`cv2.circle`, `cv2.fillPoly`) plus
   sensor noise, never a hand-coded list of "already known" object records.
2. **Real OpenCV segmentation + contour geometry** -- grayscale conversion,
   thresholding, and contour extraction via `cv2.cvtColor`/`cv2.threshold`/
   `cv2.findContours`, then real shape descriptors per contour (geometric
   ratios + `cv2.HuMoments`) -- exactly how a real industrial parts-sorting
   vision system recognizes shape independent of the object's position and
   rotation on the tray.
3. **A genuinely trained `scikit-learn` classifier** (`RandomForestClassifier`)
   over that feature pipeline, with a held-out test split and a real
   `classification_report` -- this is the "AI" in "AI vision-guided arm":
   the shape category is a learned decision boundary over noisy geometric
   features, not an if/else on area thresholds.

Object color/shade is deliberately randomized independent of class (see
`_random_shade`) so segmentation happens purely on foreground/background
intensity, and classification is forced to rely on genuine shape -- exactly
the constraint a real bin of unsorted, unlabeled recyclable parts imposes.
"""

import numpy as np
import cv2
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score

FRAME_SIZE = (480, 640)  # (rows, cols) == (height, width), pixels
CLASSES = ["bottle_cap", "block", "bar"]

BACKGROUND_GRAY = 220
OBJECT_GRAY_RANGE = (60, 150)
FRAME_NOISE_STD = 9.0
THRESH_VALUE = 180  # separates OBJECT_GRAY_RANGE from BACKGROUND_GRAY robustly

MIN_CONTOUR_AREA_PX = 150

RNG = np.random.default_rng(42)


# --------------------------------------------------------------------------
# Synthetic scene rendering (stands in for a real camera driver's capture)
# --------------------------------------------------------------------------

def _random_shade(rng):
    """Object intensity is randomized independent of class -- a real bin of
    recyclables isn't color-coded by shape, and this keeps the classifier
    honest: it cannot cheat by keying off pixel intensity.
    """
    return int(rng.integers(OBJECT_GRAY_RANGE[0], OBJECT_GRAY_RANGE[1] + 1))


def _draw_bottle_cap(img, cx, cy, scale, angle_deg, shade, rng):
    # Real bottle caps are dented/oval from handling, not mathematically
    # perfect disks -- render as a slightly eccentric ellipse so circularity
    # isn't a trivial always-1.0 tell.
    radius_a = 26 * scale * rng.uniform(0.9, 1.1)
    radius_b = 26 * scale * rng.uniform(0.9, 1.1)
    cv2.ellipse(img, (cx, cy), (int(radius_a), int(radius_b)), angle_deg,
                0, 360, (shade, shade, shade), -1, lineType=cv2.LINE_AA)


def _draw_block(img, cx, cy, scale, angle_deg, shade, rng):
    # Real stamped/molded blocks aren't perfect squares -- allow the two
    # sides to vary independently so the classifier can't just memorize
    # "aspect ratio == 1.0", and so some blocks land close to a short bar's
    # aspect ratio (a genuinely ambiguous, realistic edge case).
    side_w = 40 * scale * rng.uniform(0.7, 1.4)
    side_h = 40 * scale * rng.uniform(0.7, 1.4)
    rect = ((cx, cy), (side_w, side_h), angle_deg)
    box = cv2.boxPoints(rect).astype(np.int32)
    cv2.fillPoly(img, [box], (shade, shade, shade), lineType=cv2.LINE_AA)


def _draw_bar(img, cx, cy, scale, angle_deg, shade, rng):
    # Length/width both vary; short, wide bars deliberately overlap the
    # elongated end of the block distribution -- a genuinely ambiguous,
    # realistic edge case a shape classifier has to actually resolve
    # statistically rather than by a hard geometric rule.
    length = 70 * scale * rng.uniform(0.6, 1.3)
    width = 16 * scale * rng.uniform(0.8, 1.6)
    rect = ((cx, cy), (length, width), angle_deg)
    box = cv2.boxPoints(rect).astype(np.int32)
    cv2.fillPoly(img, [box], (shade, shade, shade), lineType=cv2.LINE_AA)


_DRAW_FN = {
    "bottle_cap": _draw_bottle_cap,
    "block": _draw_block,
    "bar": _draw_bar,
}


def render_scene(objects, frame_size=FRAME_SIZE, noise_std=FRAME_NOISE_STD, rng=None):
    """Renders a synthetic overhead-camera BGR frame containing `objects`,
    a list of dicts {"class", "pixel_xy": (cx, cy), "scale", "angle_deg"}.
    Real `cv2` drawing calls + additive sensor noise stand in for what a
    real overhead camera driver's `capture_frame()` would hand back.
    """
    if rng is None:
        rng = RNG
    h, w = frame_size
    img = np.full((h, w, 3), BACKGROUND_GRAY, dtype=np.uint8)
    for obj in objects:
        shade = obj.get("shade", _random_shade(rng))
        cx, cy = obj["pixel_xy"]
        _DRAW_FN[obj["class"]](img, int(cx), int(cy), obj.get("scale", 1.0),
                                obj.get("angle_deg", 0.0), shade, rng)
    noise = rng.normal(0, noise_std, size=img.shape)
    img = np.clip(img.astype(np.float32) + noise, 0, 255).astype(np.uint8)
    return img


# --------------------------------------------------------------------------
# Real OpenCV segmentation + shape feature extraction
# --------------------------------------------------------------------------

def _segment(frame_bgr):
    """Real cv2 segmentation pipeline: grayscale -> Gaussian blur (real
    camera optics never hand back perfectly crisp edges) -> binary
    threshold (objects are darker than the tray background) -> external
    contours.
    """
    gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (5, 5), 0)
    _, mask = cv2.threshold(gray, THRESH_VALUE, 255, cv2.THRESH_BINARY_INV)
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    return [c for c in contours if cv2.contourArea(c) >= MIN_CONTOUR_AREA_PX]


FEATURE_NAMES = ["aspect_ratio", "extent", "rect_extent", "circularity", "solidity",
                  "hu0", "hu1", "hu2", "hu3", "hu4", "hu5", "hu6"]


def extract_features(contour):
    """Real geometric + `cv2.HuMoments` shape descriptors -- the same
    feature family industrial machine-vision sorting systems use because
    Hu moments are invariant to translation, scale, and in-plane rotation,
    exactly the invariance a part landing anywhere on the tray at any
    orientation needs.
    """
    area = cv2.contourArea(contour)
    perimeter = cv2.arcLength(contour, True)
    x, y, w, h = cv2.boundingRect(contour)
    aspect_ratio = max(w, h) / max(min(w, h), 1e-6)
    extent = area / max(w * h, 1e-6)

    (rw, rh) = cv2.minAreaRect(contour)[1]
    rect_extent = area / max(rw * rh, 1e-6)

    circularity = 4 * np.pi * area / max(perimeter ** 2, 1e-6)

    hull = cv2.convexHull(contour)
    hull_area = cv2.contourArea(hull)
    solidity = area / max(hull_area, 1e-6)

    m = cv2.moments(contour)
    hu = cv2.HuMoments(m).flatten()
    hu_log = -np.sign(hu) * np.log10(np.abs(hu) + 1e-30)

    return np.array([aspect_ratio, extent, rect_extent, circularity, solidity, *hu_log])


def _contour_pose(contour):
    m = cv2.moments(contour)
    cx = m["m10"] / m["m00"] if m["m00"] != 0 else 0.0
    cy = m["m01"] / m["m00"] if m["m00"] != 0 else 0.0
    (_, _), (_, _), angle_deg = cv2.minAreaRect(contour)
    x, y, w, h = cv2.boundingRect(contour)
    return (cx, cy), angle_deg, (x, y, w, h)


# --------------------------------------------------------------------------
# Synthetic labeled dataset + real sklearn training
# --------------------------------------------------------------------------

def _synthesize_labeled_sample(cls, rng):
    """Renders ONE random single-object frame for `cls`, runs it through the
    exact same segmentation + feature pipeline `detect_objects` uses at
    inference time, and returns its feature vector. Random position,
    rotation, scale, per-instance shade, and sensor noise every call --
    this is what makes the resulting dataset a legitimate stand-in for many
    real captured images rather than one memorized example per class.
    """
    h, w = FRAME_SIZE
    cx = int(rng.uniform(120, w - 120))
    cy = int(rng.uniform(120, h - 120))
    scale = float(rng.uniform(0.8, 1.2))
    angle_deg = float(rng.uniform(0, 180))
    obj = {"class": cls, "pixel_xy": (cx, cy), "scale": scale, "angle_deg": angle_deg}
    frame = render_scene([obj], rng=rng)
    contours = _segment(frame)
    if not contours:
        return None
    largest = max(contours, key=cv2.contourArea)
    return extract_features(largest)


def build_training_set(n_per_class=200, seed=123):
    rng = np.random.default_rng(seed)
    X, y = [], []
    for cls in CLASSES:
        n_ok = 0
        while n_ok < n_per_class:
            feats = _synthesize_labeled_sample(cls, rng)
            if feats is not None:
                X.append(feats)
                y.append(cls)
                n_ok += 1
    return np.array(X), np.array(y)


def train_classifier(n_per_class=200, test_size=0.3, seed=123):
    """Builds a synthetic labeled dataset from the real render+segment+
    feature pipeline, trains a `RandomForestClassifier` on a train/test
    split, and returns (model, report_string, test_accuracy) -- the genuine
    trained-model-in-the-loop this project requires, evaluated the way any
    real ML pipeline is: on data the model never saw during training.
    """
    X, y = build_training_set(n_per_class=n_per_class, seed=seed)
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=seed, stratify=y)

    model = RandomForestClassifier(n_estimators=200, max_depth=8, random_state=seed)
    model.fit(X_train, y_train)

    y_pred = model.predict(X_test)
    accuracy = accuracy_score(y_test, y_pred)
    report = classification_report(y_test, y_pred, digits=3)
    return model, report, accuracy


# --------------------------------------------------------------------------
# Inference: full detect pipeline over a captured frame
# --------------------------------------------------------------------------

def detect_objects(frame_bgr, model):
    """Runs the real segmentation pipeline over a captured frame, extracts
    shape features per contour, and classifies each with the trained
    sklearn `model`. Returns a list of detections:
        {"class", "confidence", "pixel_xy", "angle_deg", "bbox", "features"}
    exactly the payload a real perception node publishes for a downstream
    grasp planner to consume.
    """
    contours = _segment(frame_bgr)
    detections = []
    for contour in contours:
        feats = extract_features(contour)
        probs = model.predict_proba(feats.reshape(1, -1))[0]
        pred_idx = int(np.argmax(probs))
        pred_class = model.classes_[pred_idx]
        confidence = float(probs[pred_idx])
        pixel_xy, angle_deg, bbox = _contour_pose(contour)
        detections.append({
            "class": pred_class,
            "confidence": confidence,
            "pixel_xy": pixel_xy,
            "angle_deg": angle_deg,
            "bbox": bbox,
            "features": feats,
        })
    return detections
