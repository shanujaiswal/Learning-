# Building on the Data Analyst Track's Statistics Foundation

--> The Data Analyst folder's Statistics Fundamentals file covered mean/median, distributions, correlation, and hypothesis testing at an applied, business-analysis level -- this file goes one level deeper into the probability theory that Machine Learning algorithms (covered in the next folder) are actually built on.

# Probability Fundamentals

--> Probability quantifies uncertainty on a scale from 0 (impossible) to 1 (certain) -- every prediction a machine learning classifier makes (covered in the ML folder) is fundamentally a probability estimate, not a certainty.
--> **Independent events** -- the outcome of one doesn't affect the other (two separate coin flips). **Dependent events** -- one outcome DOES affect the probability of the other (drawing cards from a deck without replacement).

# Conditional Probability and Bayes' Theorem

--> Conditional probability -- the probability of event A occurring GIVEN that event B has already occurred, written P(A|B).
--> Bayes' Theorem lets you flip a conditional probability around -- computing P(A|B) from P(B|A), which is often what you actually have data for.

```
P(A|B) = P(B|A) * P(A) / P(B)

Example: P(has disease | positive test) = P(positive test | has disease) * P(has disease) / P(positive test)
```

--> This is the exact mathematical foundation behind the Naive Bayes classification algorithm (covered in the Machine Learning folder), and behind spam filters, medical diagnosis models, and recommendation systems more broadly -- reasoning backward from observed evidence to the most probable underlying cause.

# Common Probability Distributions Beyond Normal

--> **Binomial distribution** -- models the number of successes in a fixed number of independent yes/no trials (e.g. number of heads in 10 coin flips, number of conversions out of 100 site visits).
--> **Poisson distribution** -- models the number of events occurring in a fixed interval of time/space, when events happen independently at a known average rate (e.g. number of customer support tickets per hour).
--> **Exponential distribution** -- models the time BETWEEN events in a Poisson process (e.g. time until the next customer arrives) -- frequently used in queueing and reliability analysis.
--> Recognizing which distribution a real-world process actually follows determines which statistical model correctly describes/predicts it.

# The Central Limit Theorem -- Why Normal Distributions Show Up Everywhere

--> States that the distribution of SAMPLE MEANS (not the raw data itself) approaches a normal distribution as sample size grows, REGARDLESS of the underlying data's original distribution shape.
--> This is precisely why so many statistical methods (confidence intervals, many hypothesis tests, covered in the Data Analyst Statistics file) can validly assume normality when working with aggregated/averaged data, even when the raw underlying data is skewed or otherwise non-normal.

# Random Variables and Expected Value

--> Expected Value -- the long-run average outcome of a random process, weighted by each outcome's probability -- the mathematical formalization of "on average, what do we expect."

```
Expected Value = Σ (outcome * probability of that outcome)

Example: A game paying $10 with 20% probability, $0 otherwise:
E[X] = (10 * 0.2) + (0 * 0.8) = $2 expected value per play
```

--> Directly underlies decision-making under uncertainty (is this marketing campaign's expected ROI positive?) and is the mathematical basis for the loss functions models are trained to minimize, covered when Machine Learning training is introduced in the next folder.

# Why This File Sits Right Before Machine Learning

--> Every core ML concept ahead -- probability-based classification, loss functions, statistical model evaluation -- rests directly on the probability foundations covered here. This is the last conceptual bridge between "understanding data" (this folder) and "building models that learn from data" (the next one).
