# Why Statistics Underpins Every Analytical Claim

--> "Sales increased 5% this month" means very little without statistical context -- is 5% a meaningful signal, or just normal month-to-month noise? Statistics gives the tools to tell the difference between a real pattern and random variation.

# Descriptive Statistics -- Summarizing What You See

--> **Mean** -- the average -- sensitive to outliers (one huge value can drag it far from what's "typical").
--> **Median** -- the middle value when sorted -- robust to outliers, often a better "typical value" summary for skewed data like income or house prices.
--> **Mode** -- the most frequent value -- useful for categorical data where mean/median don't apply.
--> **Standard Deviation / Variance** -- how spread out the data is around the mean -- a small standard deviation means values cluster tightly; a large one means they're spread widely.

```
Data: [10, 12, 11, 90, 13]
Mean = 27.2   (heavily skewed by the outlier, 90)
Median = 12    (much more representative of the "typical" value here)
```

# Distributions -- Shapes Data Naturally Takes

--> Normal (Gaussian) Distribution -- the classic symmetric "bell curve" -- many natural and business metrics approximate this shape (heights, measurement errors, many aggregated metrics due to the Central Limit Theorem).
--> Skewed Distributions -- income and house prices are classic examples of right-skewed data (a long tail of high values) -- for skewed data, median is generally more representative than mean.
--> Recognizing a dataset's distribution shape informs which statistical methods are actually valid to apply to it -- many common statistical tests assume roughly normal data, and applying them to heavily skewed data without adjustment can produce misleading conclusions.

# Correlation -- and Its Most Important Caveat

--> Correlation measures how strongly two variables move together, ranging from -1 (perfectly inverse) to +1 (perfectly aligned), with 0 meaning no linear relationship.
--> **Correlation is not causation** -- the single most important, most frequently violated principle in applied statistics. Ice cream sales and drowning deaths correlate strongly (both rise in summer) -- ice cream doesn't cause drowning; a third factor (hot weather) drives both. Every analyst needs to actively watch for this trap before recommending an action based on a correlation alone.

# Hypothesis Testing -- Is This Difference Real?

--> A hypothesis test asks: "is the difference I'm observing likely a genuine effect, or could it plausibly have happened by random chance alone?"
--> **p-value** -- roughly, the probability of observing a result at least this extreme if there were actually NO real effect (the "null hypothesis"). A small p-value (conventionally < 0.05) suggests the observed effect is unlikely to be pure chance -- but a p-value is NOT the probability that the effect is real, a common and important misinterpretation to avoid.
--> **Statistical significance vs practical significance** -- with enough data, even a tiny, practically meaningless difference can become "statistically significant." A good analyst reports the actual SIZE of an effect (e.g. "a 0.2% conversion increase"), not just whether a p-value crossed an arbitrary threshold.

# Confidence Intervals -- Communicating Uncertainty

--> A 95% confidence interval expresses a range that would contain the true value in 95% of repeated samples, if the same experiment/measurement were repeated many times -- reporting a range ("between 3% and 7% growth") rather than a single point estimate ("5% growth") more honestly communicates the genuine uncertainty in any estimate drawn from a sample rather than the entire population.

# Sampling -- Why It Matters

--> Most analysis works with a SAMPLE of data, not the entire population -- a sample that isn't representative (a common cause: sampling only your most engaged/vocal users when surveying "all users") produces a systematically biased conclusion, no matter how sophisticated the statistical technique applied to it afterward. Getting the sample right matters more than almost any downstream statistical refinement.
