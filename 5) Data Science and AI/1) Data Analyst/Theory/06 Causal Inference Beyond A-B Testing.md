# The Problem -- You Can't Always Run an A/B Test

--> The A/B Testing and Experimentation file covers the gold-standard method for establishing causation -- random assignment. But often, running a true randomized experiment is impossible or unethical -- you can't randomly assign some customers to receive a 20% price increase just to measure the effect, you can't randomly assign people to smoke to study health outcomes, and you often can't retroactively randomize a marketing campaign that's already been running for a year. Causal Inference is the discipline of estimating cause-and-effect relationships from OBSERVATIONAL data -- data collected without controlled random assignment -- while still trying to draw conclusions as trustworthy as a real experiment would provide.

# Why Observational Data Is So Much Harder -- Confounding Variables

--> A confounding variable is a hidden factor that influences BOTH the supposed "cause" and the supposed "effect," creating a spurious correlation that looks causal but isn't -- directly extending the correlation-vs-causation warning from the Statistics Fundamentals file into a more rigorous, structured framework for actually DIAGNOSING and CORRECTING for this problem, rather than just being generally aware it exists.

```
Observed: Customers who use the mobile app more tend to spend more money.
Naive conclusion: "The app CAUSES more spending -- let's push app adoption harder."

Possible confounder: Customers who are simply MORE ENGAGED/loyal overall use the app more
AND spend more, independent of any causal effect of the app itself. The app usage and the
spending might both just be SYMPTOMS of an underlying "engagement level" that causes both.
```

--> Without correcting for this confounder (engagement level), a company might invest heavily in forcing app adoption among LESS engaged customers, expecting the same spending increase seen in the observational data -- and be disappointed, because the real driver was never the app itself.

# Directed Acyclic Graphs (DAGs) -- Making Causal Assumptions Explicit

--> A DAG is a diagram explicitly showing your ASSUMED causal relationships between variables -- arrows point from cause to effect -- making your reasoning about confounders and causal pathways explicit and inspectable, rather than left as an implicit, unstated assumption in your head.

```
Engagement Level ---> App Usage
       |
       v
   Spending

(This DAG explicitly shows Engagement Level as a CONFOUNDER of the
App Usage -> Spending relationship -- it influences both)
```

--> Drawing out the DAG BEFORE analyzing data forces you to think carefully about what other variables might need to be controlled for -- a discipline directly analogous to defining a hypothesis and success metric BEFORE running an A/B test, covered in that file, just applied here to observational analysis instead of a designed experiment.

# Natural Experiments -- Finding "Accidental" Randomization

--> A Natural Experiment exploits a situation where something ALREADY effectively randomized who was and wasn't exposed to a "treatment," even though no one deliberately designed a controlled experiment -- letting you approximate the rigor of true randomization using naturally occurring data.

```
Example: A company's system outage randomly affected some users but not others
(due to arbitrary server routing, unrelated to any user characteristic) -- comparing
affected vs. unaffected users' subsequent behavior approximates a real randomized
experiment, since server routing wasn't influenced by anything about the users themselves.
```

# Instrumental Variables -- A Formal Technique for Natural Experiments

--> An Instrumental Variable (IV) is a variable that affects the TREATMENT but has NO DIRECT effect on the OUTCOME except THROUGH that treatment -- a specific, formal technique for extracting a valid causal estimate even when the treatment itself wasn't randomly assigned.

```
Example: Studying the effect of education on income.
Direct comparison is confounded (people who get more education may also come from
wealthier families, be more naturally talented, etc. -- all of which ALSO affect income).

A classic instrumental variable used in real economic research: distance to the
nearest college. It affects how much education someone gets (closer = more likely
to attend), but plausibly has NO OTHER direct effect on their income except through
the education it influenced.
```

--> Instrumental Variables are genuinely difficult to find convincingly in most real business analytics contexts -- finding a variable that influences your "treatment" of interest but has ABSOLUTELY no other path to affecting the outcome is a strong, often-debatable assumption -- which is exactly why, when a true randomized A/B test IS possible, it remains vastly preferable to instrumental variable analysis.

# Difference-in-Differences -- Comparing Trends, Not Just Levels

--> Difference-in-Differences (DiD) compares the CHANGE over time in a group that received a treatment against the CHANGE over time in a similar group that didn't -- rather than simply comparing the treatment group's outcome to the control group's outcome at a single point in time, which could be confounded by pre-existing differences between the two groups.

```
Example: A company launches a new feature in Region A but not Region B (perhaps
for a logistical reason, not because of any planned experiment).

           Before Launch    After Launch    Change
Region A:      $100             $130         +$30
Region B:       $90              $105         +$15

Naive comparison: Region A simply performs better ($130 vs $105) -- but this ignores that
Region A was ALREADY doing better before the launch even happened.

DiD estimate: The feature's actual causal effect ≈ (+$30) - (+$15) = +$15
(Region A's improvement ABOVE AND BEYOND the general trend Region B also experienced)
```

--> The KEY assumption DiD relies on -- the "parallel trends assumption" -- is that, absent the treatment, both groups WOULD have continued on similar trend trajectories. This assumption can't be proven directly (you can never observe Region A's outcome in a world without the launch), but can be made more credible by checking that both groups' trends looked genuinely similar BEFORE the treatment was introduced.

# Propensity Score Matching -- Making Observational Groups Comparable

--> When you have observational data with a treatment and control group that DIFFER systematically on measurable characteristics (making direct comparison unfair), Propensity Score Matching estimates each individual's PROBABILITY of having received the treatment (given their observed characteristics), then compares treated and untreated individuals who had SIMILAR propensity scores -- effectively constructing an artificially more "balanced" comparison from unbalanced observational data.

```python
from sklearn.linear_model import LogisticRegression

# Step 1: Estimate each customer's probability of having received a promotional email,
# based on their observed characteristics (a Logistic Regression, covered in the Classification file)
propensity_model = LogisticRegression()
propensity_model.fit(X[["age", "past_purchases", "account_age"]], X["received_promo"])
propensity_scores = propensity_model.predict_proba(X[["age", "past_purchases", "account_age"]])[:, 1]

# Step 2: Match treated customers to untreated customers with SIMILAR propensity scores,
# then compare outcomes only within these matched, more comparable pairs
```

--> **The critical limitation to always keep in mind** -- Propensity Score Matching can only balance groups on OBSERVED characteristics included in the model -- it does nothing to correct for UNOBSERVED confounders (something genuinely important that simply wasn't measured or included), which remains a fundamental, unavoidable limitation of essentially all observational causal inference techniques, and precisely why a true randomized experiment (which balances even UNOBSERVED factors automatically, purely through random assignment) remains the strongest possible evidence whenever it's actually achievable.

# The Practical Hierarchy of Causal Evidence

--> **Strongest** -- a well-designed, properly-powered randomized A/B test (covered in its own file) -- random assignment balances both observed AND unobserved confounders automatically, by design.
--> **Strong, when assumptions hold** -- natural experiments and instrumental variables, when a genuinely convincing source of "as-if random" variation can be identified and justified.
--> **Moderate** -- difference-in-differences, when the parallel trends assumption is credible and can be at least partially checked against pre-treatment data.
--> **Weaker, but sometimes the only option available** -- propensity score matching and other observational adjustment techniques, which can only ever account for confounders that were actually measured and included.
--> A skilled analyst's job is often less about applying the fanciest technique and more about honestly assessing WHICH level of this hierarchy their actual data and situation genuinely support -- and communicating that honest confidence level to stakeholders (connecting to the stakeholder communication skill covered in the Data Analyst Workflow file) rather than overstating a causal claim that observational data alone can't fully support.
