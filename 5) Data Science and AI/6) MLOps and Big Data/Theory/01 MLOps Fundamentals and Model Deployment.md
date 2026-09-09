# What MLOps Solves

--> A model that performs well in a Jupyter notebook (the primary tool referenced throughout the Data Science folder) isn't automatically useful -- it needs to be deployed where real requests can reach it, monitored to catch when its performance degrades, and retrained as new data arrives -- MLOps (Machine Learning Operations) is the discipline and toolset covering this entire "model lifecycle after training."

# Serving a Model -- Wrapping It in an API

--> The most common deployment pattern -- wrap a trained model in a web API (directly connecting to the REST API concepts covered in the Full Stack Node/Express notes) so other systems can send it data and get predictions back over HTTP.

```python
from fastapi import FastAPI
import joblib

app = FastAPI()
model = joblib.load("trained_model.pkl")   # Load the model trained in the ML folder's workflow

@app.post("/predict")
def predict(features: dict):
    prediction = model.predict([list(features.values())])
    return {"prediction": prediction.tolist()}
```

--> `joblib`/`pickle` -- standard ways to save ("serialize") a trained scikit-learn model to disk after training, so it can be loaded later in a completely separate serving process without needing to retrain from scratch every time.

# Containerizing a Model Service

--> Packaging the model API above into a Docker container (covered in depth in the Full Stack DevOps notes) ensures the exact same environment (library versions, dependencies) runs identically in development and production -- the same core benefit Docker provides for any application, applied here specifically to ML serving.

```dockerfile
FROM python:3.11-slim
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY trained_model.pkl app.py .
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
```

--> From there, deploying to Kubernetes (covered in the Full Stack DevOps notes) provides the same scaling, self-healing, and rolling-update benefits for a model-serving container as for any other containerized application.

# Batch vs Real-Time Inference

--> **Batch inference** -- running predictions on a large dataset all at once, on a schedule (e.g. scoring every customer's churn risk overnight) -- simpler, and appropriate when predictions don't need to be instantaneous.
--> **Real-time (online) inference** -- serving predictions on demand, per request, with low latency (e.g. fraud detection needing an answer within milliseconds of a transaction) -- requires the API-serving approach above, with careful attention to response time.

# Model Monitoring -- Detecting Silent Failure

--> Unlike a traditional software bug (which usually causes a visible error), a degraded model often fails SILENTLY -- it keeps returning predictions, they're just gradually becoming less accurate, with no crash or error message to alert anyone.
--> **Data drift** -- the statistical properties of incoming production data gradually shift away from the training data's properties (e.g. customer behavior changes after a major world event) -- the model was trained on an increasingly outdated picture of reality.
--> **Concept drift** -- the actual relationship between features and the target changes over time, even if the input data's distribution looks stable (e.g. what predicts fraud shifts as fraudsters adapt their tactics).
--> Monitoring systems track prediction distributions and, where ground truth eventually becomes available (e.g. did the customer actually churn), compare predictions against actual outcomes over time to catch this kind of silent degradation before it causes serious business impact.

# CI/CD for Machine Learning

--> Extends the CI/CD concepts covered in the Full Stack GitHub and AWS CI/CD notes specifically to the ML lifecycle -- automatically retraining a model when new data arrives, automatically running evaluation checks (connecting to the Model Evaluation file in the ML folder) before deploying a new model version, and supporting rollback to a previous model version if a new one underperforms in production.

# A/B Testing Models in Production

--> Directly extending the A/B Testing concepts from the Data Analyst folder -- routing a portion of real production traffic to a NEW model version while the rest continues using the current one, comparing actual business-metric performance before fully rolling out the new version -- the same rigorous causal-inference discipline from that folder, applied here to comparing model versions rather than product features.
