# What a Data Analyst Actually Does

--> A Data Analyst turns raw data into actionable insight for business decisions -- answering questions like "why did sales drop last quarter?" or "which marketing channel converts best?" using data that already exists, rather than building predictive models or production ML systems.
--> Distinguishing this from adjacent roles (covered in their own folders in this track): a Data Scientist builds statistical/ML models to predict or explain; a Machine Learning Engineer productionizes those models at scale; a Data Analyst's core output is usually a report, dashboard, or recommendation, not a deployed model.

# The Analytics Workflow -- Start to Finish

--> **1. Define the question** -- the single most important, most commonly rushed step. A vague question ("how's the business doing?") produces a vague, unusable answer; a sharp question ("did the new checkout flow reduce cart abandonment?") produces an actionable one.
--> **2. Gather data** -- pulling from databases (SQL, covered in depth in the Full Stack track), spreadsheets, APIs, or third-party sources.
--> **3. Clean and prepare data** -- handling missing values, duplicates, inconsistent formatting -- in practice, this step consumes more analyst time than any other, often 60-80% of a project.
--> **4. Analyze** -- applying statistical methods (covered in the Statistics Fundamentals file) to find patterns, correlations, and answers to the defined question.
--> **5. Visualize and communicate** -- translating findings into a chart/dashboard/narrative a non-technical stakeholder can act on (covered in the Data Visualization file) -- an analysis that isn't understood by its audience produces zero business value, no matter how rigorous the underlying work was.
--> **6. Recommend and follow up** -- a good analyst doesn't just report a number, but recommends an action, and ideally follows up to see whether that action actually worked.

# Key Skills Beyond Pure Technique

--> Business/domain context -- knowing which metrics actually matter to the specific business (e.g. understanding that "revenue" and "profit" tell very different stories) is what separates a useful analysis from a technically correct but irrelevant one.
--> Stakeholder communication -- translating a statistical finding ("p < 0.05") into plain business language ("this change very likely caused the improvement, not random chance") is a distinct skill from the analysis itself.
--> Skepticism of your own data -- actively questioning whether the data actually measures what you think it measures, before drawing conclusions from it (a recurring theme revisited in the A/B Testing file's discussion of common pitfalls).

# Common Analyst Deliverables

--> Ad hoc reports -- answering a specific, one-off business question.
--> Recurring dashboards -- self-service views stakeholders check regularly (weekly sales, daily active users) without needing to ask an analyst each time.
--> Deep-dive investigations -- multi-week analyses into a specific business problem, often combining several data sources.

# Data-Driven vs Data-Informed Decision Making

--> "Data-driven" (letting data dictate every decision) can be misleading in practice -- data often can't capture everything relevant (competitive dynamics, brand strategy, ethical considerations). Most mature organizations aim to be "data-informed" instead -- treating analysis as critical input to a decision, combined with judgment and context, rather than a fully automatic decision rule.
