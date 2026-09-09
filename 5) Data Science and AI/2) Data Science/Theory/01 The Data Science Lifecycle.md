# Data Science vs Data Analysis -- What Actually Changes

--> Building on the Data Analyst role comparison covered in that folder's first file: a Data Scientist typically works with LARGER, messier, less structured data, builds statistical/predictive MODELS rather than only descriptive reports, and often writes production-grade code (Python, sometimes contributing to the same codebases covered in the Full Stack Backend track) rather than working primarily in BI tools/spreadsheets.

# The Lifecycle, Step by Step

--> **1. Problem framing** -- translating a business question into a specific, answerable data science problem (e.g. "reduce churn" becomes "predict which customers are likely to cancel in the next 30 days").
--> **2. Data collection** -- gathering data from databases (SQL), APIs, logs, or external sources -- often combining multiple sources that weren't originally designed to be joined together.
--> **3. Data cleaning and wrangling** -- covered in depth in its own file -- handling missing values, inconsistent formats, and merging disparate sources into one usable dataset.
--> **4. Exploratory Data Analysis (EDA)** -- covered in its own file -- understanding the data's distributions, relationships, and quality issues before attempting to model it.
--> **5. Feature engineering** -- transforming raw data into the specific inputs a model can actually learn from (covered in depth in the Machine Learning folder).
--> **6. Modeling** -- training and tuning a statistical or machine learning model (the Machine Learning folder covers this fully).
--> **7. Evaluation** -- rigorously checking whether the model actually performs well, and on data it hasn't seen before, not just on the data it was trained on.
--> **8. Communication and deployment** -- presenting findings to stakeholders, and/or shipping the model into a real, running system (covered in the MLOps folder).

# This Process Is Iterative, Not Linear

--> In practice, EDA often reveals a data quality issue requiring a return to cleaning; a poorly performing model often reveals a need for better feature engineering, not a fundamentally different algorithm -- this lifecycle is drawn as a loop with feedback in most real depictions, not a strict one-way pipeline.

# CRISP-DM -- A Formalized Version of This Same Process

--> Cross-Industry Standard Process for Data Mining -- a widely referenced, more formalized version of the same lifecycle (Business Understanding → Data Understanding → Data Preparation → Modeling → Evaluation → Deployment), predating "data science" as a job title but describing essentially the same underlying process.

# Reproducibility -- A Core Data Science Discipline

--> Unlike a one-off spreadsheet analysis, data science work should be fully reproducible -- the same code, run on the same data, producing the same result -- typically achieved through version-controlled code (Git, covered in the Full Stack GitHub notes), documented data sources, and fixed random seeds where randomness is involved (covered practically in the modeling files ahead).
--> Jupyter Notebooks are the dominant tool for exploratory data science work specifically because they interleave code, output, and narrative explanation in one document -- ideal for the iterative, exploratory nature of this lifecycle, though production code eventually needs to move into proper, tested modules (connecting to the software engineering practices covered throughout the Full Stack track) rather than living permanently in a notebook.

# Why This Lifecycle Sets Up the Rest of the Folder

--> Every subsequent file in this Data Science folder maps onto a specific step above -- Python/Pandas (step 2-3's toolkit), Data Cleaning (step 3), EDA (step 4), and Statistics (the analytical grounding underlying steps 4, 6, and 7).
