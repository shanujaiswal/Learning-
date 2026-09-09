"""
02 - FastAPI Model Serving
============================
Chapter: MLOps Fundamentals / Model Deployment

Loads the model.pkl artifact produced by 01_train_and_serialize_model.py
and exposes it behind a small HTTP API. This is the "deployment" step of
the MLOps lifecycle: turning a serialized model into a running service that
other systems can call over the network.

Install:
    pip install fastapi uvicorn scikit-learn joblib pandas

Run (after running 01_train_and_serialize_model.py to create model.pkl):
    uvicorn 02_fastapi_model_serving:app --reload

    (Note: uvicorn needs a valid Python module name; since this filename
    starts with a digit and contains dots, either rename the file to
    something like app_serving.py, or run it directly with:
        python 02_fastapi_model_serving.py
    which starts uvicorn programmatically at the bottom of this file.)

Try it:
    curl -X POST http://127.0.0.1:8000/predict \
         -H "Content-Type: application/json" \
         -d '{"feature_1": 55.0, "feature_2": 22.0}'

    curl http://127.0.0.1:8000/health
"""

import os

import joblib
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

MODEL_PATH = "model.pkl"

app = FastAPI(
    title="Tiny MLOps Model Server",
    description="Serves a scikit-learn logistic regression trained in 01_train_and_serialize_model.py",
    version="1.0.0",
)

# Load the model once at startup rather than on every request.
_model = None


def load_model():
    global _model
    if not os.path.exists(MODEL_PATH):
        raise FileNotFoundError(
            f"'{MODEL_PATH}' not found. Run 01_train_and_serialize_model.py first."
        )
    _model = joblib.load(MODEL_PATH)
    return _model


@app.on_event("startup")
def startup_event():
    load_model()
    print("Model loaded and ready to serve requests.")


class PredictionRequest(BaseModel):
    feature_1: float = Field(..., description="First numeric input feature")
    feature_2: float = Field(..., description="Second numeric input feature")


class PredictionResponse(BaseModel):
    predicted_label: int
    probability_class_1: float


@app.get("/health")
def health():
    """Simple liveness/readiness probe for the deployed service."""
    return {"status": "ok", "model_loaded": _model is not None}


@app.post("/predict", response_model=PredictionResponse)
def predict(request: PredictionRequest):
    if _model is None:
        raise HTTPException(status_code=503, detail="Model is not loaded yet.")

    features = [[request.feature_1, request.feature_2]]
    predicted_label = int(_model.predict(features)[0])
    probability_class_1 = float(_model.predict_proba(features)[0][1])

    return PredictionResponse(
        predicted_label=predicted_label,
        probability_class_1=probability_class_1,
    )


if __name__ == "__main__":
    # Programmatic launch so this script can be run directly with
    # `python 02_fastapi_model_serving.py` despite the filename not being
    # a clean importable module name for the `uvicorn <module>:app` CLI form.
    uvicorn.run(app, host="127.0.0.1", port=8000)
