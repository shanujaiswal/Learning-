# Building on Two Earlier Foundations at Once

--> The Data Analyst folder's Statistics Fundamentals file introduced hypothesis testing and confidence intervals at a business-analysis level, and this folder's own Probability and Statistics file (05) went one level deeper into the probability theory underneath them. This file goes deeper still on the INFERENCE side -- the actual mechanics of choosing, running, and correcting the tests that turn a sample into a defensible claim -- then pivots to two topics no prior file in either track has actually delivered: feature engineering (referenced from the Time Series file but never written up) and a Bayesian alternative to everything file 05's frequentist Bayes' Theorem section only introduced conceptually.

# Choosing the Right Test -- t-Tests vs z-Tests

--> Both compare a sample mean against a hypothesized value (or compare two sample means) and produce a p-value -- the difference is what you're allowed to assume about the population.
--> **z-test** -- valid when the population standard deviation is KNOWN and/or the sample is large enough (conventionally n > 30) for the Central Limit Theorem (file 05) to make the sampling distribution reliably normal regardless.
--> **t-test** -- used when the population standard deviation is UNKNOWN and has to be estimated from the sample itself, which is by far the more common real-world situation -- the t-distribution is deliberately wider/flatter than the normal distribution to honestly account for that extra estimation uncertainty, especially with small samples.

```python
from scipy.stats import ttest_ind, ttest_rel, ttest_1samp

# One-sample: is this sample's mean different from a known/hypothesized value?
ttest_1samp(sample_scores, popmean=75)

# Independent two-sample: two DIFFERENT groups of people (an A/B test's two arms, file 05 in the Data Analyst folder)
ttest_ind(group_a_conversions, group_b_conversions, equal_var=False)   # Welch's t-test -- doesn't assume equal variances

# Paired: the SAME subjects measured twice (before vs after a training program)
ttest_rel(scores_before, scores_after)
```

--> `equal_var=False` runs **Welch's t-test** rather than the classic Student's t-test -- Welch's version doesn't assume the two groups have equal variance, and is the safer default in practice since that assumption is rarely verified and rarely exactly true.
--> Choosing paired vs independent matters as much as choosing the right test family -- a paired test is far more statistically powerful when the same subjects really were measured twice, because it cancels out person-to-person variation that an independent test would count as noise.

# ANOVA and Chi-Square -- Extending Beyond Two Groups

--> **One-way ANOVA** generalizes the t-test to three or more groups' means at once, and **chi-square** tests association between categorical variables -- both are covered with worked code in the Data Analyst folder's new SQL/Excel/KPI file (07), directly alongside the non-parametric alternatives (Mann-Whitney, Kruskal-Wallis) to use when a t-test or ANOVA's normality assumption doesn't hold. That file's test-selection table is the single reference point for "which test do I actually run" across both tracks.

# Statistical Power and Effect Size

--> **Statistical power** -- the probability a test correctly detects a real effect that actually exists (formally, `1 - β`, where β is the probability of a Type II error -- failing to detect a real effect). A study can be perfectly well-designed and still miss a genuine effect purely because it was underpowered -- too small a sample to reliably distinguish a real, modest effect from noise.
--> **Effect size** (Cohen's d for two means) measures HOW BIG a difference is, independent of sample size -- critically different from a p-value, which conflates "how big" with "how much data was collected" (file 04 in the Data Analyst folder flags exactly this confusion between statistical and practical significance).

```
Cohen's d = (mean_group_1 - mean_group_2) / pooled_standard_deviation

d ≈ 0.2 -- small effect
d ≈ 0.5 -- medium effect
d ≈ 0.8 -- large effect
```

```python
import statsmodels.stats.power as smp

# How many samples per group are needed to reliably detect a medium effect (d=0.5)
# at the conventional 80% power and 5% significance level?
analysis = smp.TTestIndPower()
required_n = analysis.solve_power(effect_size=0.5, alpha=0.05, power=0.8)
print(f"Need ~{required_n:.0f} samples per group")
```

--> This is the exact calculation the Data Analyst folder's A/B testing file describes doing "in advance" before ever launching a test -- this file supplies the actual formula and code behind that advice: decide the smallest effect worth detecting, then let power analysis dictate the sample size, rather than guessing at a round number and hoping it's enough.

# Multiple Testing Correction -- Bonferroni and FDR

--> Running many hypothesis tests in the same analysis (dozens of metrics, hundreds of genes, every column against every other column) inflates the chance that AT LEAST ONE test shows "significance" purely by chance -- the same multiple-comparisons trap the A/B testing file warns about when a team digs through secondary metrics after a test, just now framed as something with an actual statistical correction rather than only a warning to watch for.

## Bonferroni Correction -- Simple and Conservative

```
Adjusted significance threshold = α / number of tests

Example: testing 20 metrics at the usual α = 0.05
Adjusted threshold = 0.05 / 20 = 0.0025
-- Only a p-value below 0.0025 (not the usual 0.05) counts as significant now
```

--> Bonferroni is easy to compute and guarantees the overall false-positive rate stays at or below α, but it's deliberately conservative -- with many tests, the adjusted threshold becomes so strict that genuinely real effects can fail to clear it (a Type II error, the flip side of the false-positive problem it's solving).

## False Discovery Rate (FDR) -- Benjamini-Hochberg

```python
from statsmodels.stats.multitest import multipletests

raw_p_values = [0.001, 0.01, 0.03, 0.04, 0.20, 0.35, 0.60]

reject, adjusted_p_values, _, _ = multipletests(raw_p_values, alpha=0.05, method="fdr_bh")
print(reject)             # Boolean array -- which tests survive correction
print(adjusted_p_values)  # p-values adjusted to control the false discovery rate instead
```

--> Rather than controlling the probability of even ONE false positive across all tests (Bonferroni's strict standard), FDR control (Benjamini-Hochberg) controls the EXPECTED PROPORTION of false positives AMONG the tests called significant -- a meaningfully less conservative standard that keeps far more real effects detectable when running dozens or hundreds of tests, which is why FDR correction is the standard default in fields like genomics and large-scale A/B testing programs where Bonferroni would be needlessly strict.

# Bayesian Statistics -- Beyond Bayes' Theorem

--> File 05 introduced Bayes' Theorem as a formula for flipping a conditional probability. Bayesian STATISTICS builds an entire alternative framework for inference on top of that formula -- one that treats an unknown parameter itself (a conversion rate, a population mean) as having a probability distribution, rather than as a single fixed number a frequentist confidence interval merely brackets.

## Priors, Likelihood, and Posteriors

--> **Prior** -- your belief about a parameter's likely value BEFORE seeing the current data (e.g. "based on similar past features, this button's conversion rate is probably somewhere around 5%, though I'm not fully certain").
--> **Likelihood** -- how probable the observed data is, for each possible value of the parameter -- exactly the `P(B|A)` piece of Bayes' Theorem from file 05.
--> **Posterior** -- the UPDATED belief about the parameter after combining the prior with the observed data -- computed via Bayes' Theorem, and it becomes the prior for the NEXT batch of data if more arrives later, letting belief update continuously as evidence accumulates rather than being recomputed from scratch each time.

```
Posterior ∝ Likelihood × Prior

Example: estimating a new landing page's true conversion rate
Prior:      Beta(2, 18)   -- weak prior belief centered around a 10% conversion rate
Data:       45 conversions out of 500 visitors
Posterior:  Beta(2 + 45, 18 + 455) = Beta(47, 473)   -- the Beta distribution's conjugate-prior
                                                          update rule for binomial data
```

--> The Beta distribution is the conventional choice here specifically because it's the "conjugate prior" for binomial/Bernoulli data (conversions, clicks, yes/no outcomes) -- meaning the posterior comes out as another Beta distribution in closed form, no numerical approximation required, which is exactly why it's the default choice for modeling a conversion rate in a Bayesian A/B test below.

## Credible Intervals vs Confidence Intervals -- A Genuinely Different Meaning

--> A 95% CONFIDENCE interval (file 04 in the Data Analyst folder) means: if you repeated the sampling process many times, 95% of the intervals constructed this way would contain the true value -- a statement about the PROCEDURE, not about this specific interval or this specific parameter.
--> A 95% CREDIBLE interval means, much more directly and intuitively: given the data actually observed, there's a 95% probability the true parameter value falls in this range -- a direct probability statement about the parameter itself, which is exactly what most people INTUITIVELY (if incorrectly) assume a confidence interval already says.
--> This distinction isn't just semantic pedantry -- it's the whole reason Bayesian inference exists as a genuine alternative framework rather than just a different way of computing the same numbers.

## MCMC -- Sampling When There's No Closed-Form Answer

--> The clean Beta-Binomial update above only works because that specific combination has a known closed-form posterior. Most real Bayesian models (many parameters, non-conjugate priors, complex hierarchical structure) have no such closed form -- the posterior can be WRITTEN down mathematically but not directly solved.
--> **Markov Chain Monte Carlo (MCMC)** solves this by SAMPLING -- generating a long sequence of parameter values, where each new sample depends only on the previous one (the "Markov" property) and the sampling process is deliberately designed so that, after enough steps, the sampled values' distribution converges to the TRUE posterior -- turning an intractable equation into a large collection of representative samples that can be summarized numerically (mean, credible interval) instead.
--> Conceptually: rather than solving for the posterior directly, MCMC explores the space of plausible parameter values, spending MORE time in regions the data makes more probable and less time in unlikely regions -- the resulting histogram of visited values IS the approximate posterior. Modern tools (PyMC, Stan) hide the sampling mechanics behind a model-specification interface, so most practitioners describe their model and let the library run MCMC automatically rather than implementing the sampler by hand.

## Bayesian A/B Testing

```python
import numpy as np
from scipy.stats import beta

# Control: 40 conversions / 500 visitors.  Treatment: 55 conversions / 500 visitors.
posterior_control   = beta(1 + 40, 1 + 460)     # Beta(1,1) = uniform, uninformative prior
posterior_treatment = beta(1 + 55, 1 + 445)

samples_control   = posterior_control.rvs(100_000)
samples_treatment = posterior_treatment.rvs(100_000)

prob_treatment_better = np.mean(samples_treatment > samples_control)
print(f"P(treatment beats control) = {prob_treatment_better:.3f}")
```

--> Rather than a p-value answering "how surprising would this data be if there were truly no difference," a Bayesian A/B test directly answers the question most stakeholders actually want answered: "what's the probability the treatment is genuinely better," plus by how much, straight from the posterior samples -- arguably a more directly interpretable output for a business audience than the p-value/significance-threshold framing throughout the Data Analyst folder's A/B testing file, though it requires committing to a prior, which is exactly the step a frequentist approach avoids.

# Feature Engineering -- Actually Covered Here

--> This folder's Time Series Forecasting file references "the Feature Engineering Fundamentals file" when explaining seasonal date features and lag features, and the Machine Learning folder leans on feature engineering as a prerequisite throughout -- but no file up to this point actually delivers it. This section is that missing piece.

## Encoding Categorical Variables

```python
import pandas as pd

df = pd.DataFrame({"city": ["NYC", "LA", "NYC", "Chicago"], "size": ["S", "M", "L", "M"]})

# One-hot encoding -- one binary column per category, no ordering implied
one_hot = pd.get_dummies(df["city"], prefix="city")
# city_Chicago  city_LA  city_NYC
#      0           0        1

# Label / ordinal encoding -- ONE column mapping categories to integers
size_order = {"S": 0, "M": 1, "L": 2}
df["size_encoded"] = df["size"].map(size_order)   # valid here because size has a real, meaningful order

# Target encoding -- replaces a category with the average of the TARGET variable for that category
target_means = df.groupby("city")["conversion_rate"].mean()
df["city_target_encoded"] = df["city"].map(target_means)
```

--> One-hot encoding is the safe default for NOMINAL categories with no inherent order (city, color) -- it avoids implying a false numeric relationship ("Chicago" > "LA") that a plain integer label would otherwise accidentally create for any model that treats that integer as a genuine magnitude, such as linear regression or a distance-based method.
--> Label/ordinal encoding is only appropriate when the categories genuinely HAVE an order (S < M < L) -- using it on an unordered category is a common, subtle beginner mistake that quietly feeds a false ordinal relationship into a model.
--> Target encoding is powerful for high-cardinality categories (hundreds of cities, where one-hot would create hundreds of sparse columns) but carries a real data leakage risk -- computing the target mean using the SAME rows you'll later train on leaks information from the label into the feature; the safe version computes target means on a held-out fold or with added smoothing/noise, not on the full training set directly.

## Scaling and Normalization

```python
from sklearn.preprocessing import StandardScaler, MinMaxScaler

# Standardization -- rescales to mean 0, standard deviation 1
X_standardized = StandardScaler().fit_transform(X)

# Min-Max normalization -- rescales to a fixed [0, 1] range
X_normalized = MinMaxScaler().fit_transform(X)
```

--> Scaling matters enormously for any algorithm that computes DISTANCES or relies on gradient-based optimization (k-NN, k-Means, gradient descent for linear/logistic regression, neural networks, all covered in the Machine Learning and Deep Learning folders) -- a feature measured in the thousands (income) would otherwise completely dominate a feature measured in single digits (years of experience) purely because of the units it happens to be recorded in, not because it's actually more predictive.
--> Tree-based models (Decision Trees, Random Forests, Gradient Boosting) are a notable exception -- they split on threshold values one feature at a time and are invariant to monotonic rescaling, so scaling is unnecessary (though harmless) for that specific model family.
--> **Critical leakage rule** -- `fit_transform` (learning the scaling parameters -- mean, std, min, max) must be called on the TRAINING set only; the test/validation set is transformed using `.transform()` with those already-learned parameters, never re-fit -- fitting the scaler on the full dataset before splitting leaks test-set information into the scaling parameters themselves, a quieter version of the same data leakage principle the Time Series file's lag-feature warning describes.

## Binning (Discretization)

```python
df["age_group"] = pd.cut(df["age"], bins=[0, 18, 35, 60, 100], labels=["minor", "young_adult", "adult", "senior"])
```

--> Converts a continuous variable into discrete categories -- useful when the RELATIONSHIP to the target is genuinely non-linear/threshold-based (e.g. insurance risk jumping at specific age brackets rather than changing smoothly), or simply to make a model's behavior easier for a business stakeholder to interpret ("senior customers convert at 12%" reads more clearly than a raw age coefficient).

## Interaction Terms

```python
df["price_x_discount_flag"] = df["price"] * df["has_discount"]
```

--> Captures the case where two features' COMBINED effect differs from what adding their individual effects would predict -- a discount might barely matter for a low-price item but substantially change behavior for a high-price one; a plain linear model with `price` and `has_discount` as separate features can't represent that combined effect on its own, while the interaction term makes it directly learnable.

## Extracting Features From Text and Dates

```python
# Date features -- turning one timestamp column into several genuinely useful numeric/categorical signals
df["day_of_week"] = df["order_date"].dt.dayofweek
df["month"]        = df["order_date"].dt.month
df["is_weekend"]    = df["order_date"].dt.dayofweek >= 5

# Text features -- simple counts, and TF-IDF for a proper numeric representation of word importance
df["review_length"] = df["review_text"].str.len()

from sklearn.feature_extraction.text import TfidfVectorizer
tfidf = TfidfVectorizer(max_features=500, stop_words="english")
text_features = tfidf.fit_transform(df["review_text"])
```

--> These extracted date components are precisely what the Time Series file points to when explaining how a model captures seasonality -- a raw timestamp is nearly useless to most models directly, but day-of-week/month/is_weekend turn it into signals a standard regression or tree-based model can actually use.
--> TF-IDF (term frequency-inverse document frequency) weights a word by how often it appears in a given document, DOWN-weighted by how common that word is across ALL documents -- so a word appearing in nearly every review ("the," "good") contributes little, while a word specific to a smaller subset of reviews ("defective," "amazing") contributes much more, giving a far more useful numeric signal than a raw word count would.

# Missing Data Imputation -- Beyond Mean/Median Fill

--> The Data Cleaning and Wrangling file (03) covers simple imputation -- filling missing values with a column's mean, median, or mode. That approach is fast but distorts the data's actual variance and, crucially, ignores every RELATIONSHIP between the missing column and every other column -- multiple imputation exists to do better.

## MICE -- Multiple Imputation by Chained Equations

```python
from sklearn.experimental import enable_iterative_imputer
from sklearn.impute import IterativeImputer

imputer = IterativeImputer(max_iter=10, random_state=42)
X_imputed = imputer.fit_transform(X)   # X has NaNs scattered across several columns
```

--> MICE imputes each column with missing values by modeling it as a regression on ALL the other columns (using their current best-guess values), cycles through every column with missing data repeatedly, and lets each column's imputed values improve as the other columns' estimates also improve -- explicitly using the correlation structure between features that a simple mean-fill throws away entirely.
--> Example: imputing missing `income` values using `age`, `education_level`, and `job_title` as predictors captures a far more realistic income estimate for each specific missing row than simply filling every missing income with the dataset's overall mean, regardless of that row's age or education.

## Multiple Imputation -- Representing the Uncertainty Imputation Itself Creates

--> A single imputed dataset (whether mean-filled or MICE-filled) has a subtle, easy-to-miss flaw -- it treats an ESTIMATED value as if it were an actually-observed one, understating how much genuine uncertainty the missingness introduced into any downstream analysis.
--> **Multiple imputation** addresses this by generating SEVERAL complete datasets (each with slightly different plausible imputed values, reflecting the genuine uncertainty in what the true missing value probably was), running the intended analysis on EACH one separately, and then pooling the resulting estimates -- the spread ACROSS those separate results becomes part of the reported uncertainty, rather than being silently hidden inside one single "best guess" dataset.
--> This matters most for analyses reported with a confidence interval or p-value (file 04 in the Data Analyst folder) -- an interval computed from a single imputed dataset is narrower than it should honestly be, understating uncertainty in a way that can make a result look more statistically solid than the data actually supports.

# Algorithmic Bias and Fairness -- A Brief but Necessary Treatment

--> Every technique in this file -- which test to run, which prior to choose, which feature to engineer, how to fill a missing value -- involves a judgment call, and each of those judgment calls can quietly encode or amplify unfairness if made carelessly. This isn't a separate ethics add-on to statistics; it's a direct consequence of the same modeling choices covered above.

--> **Sampling bias** -- if the data collected systematically over- or under-represents certain groups (file 04's "sample that isn't representative" warning, now with real stakes), any model or statistic trained on it will perform worse for the underrepresented group, however good the STATISTICAL methodology applied to that biased sample is.
--> **Label bias** -- when the target variable itself reflects past human decisions that were themselves biased (e.g. training a hiring model on "who got hired historically," when historical hiring decisions were influenced by discrimination) -- the model then learns to reproduce that same bias, dressed up as an objective statistical pattern.
--> **Proxy variables** -- a feature that seems neutral (zip code, name) can act as a close statistical proxy for a protected characteristic (race, national origin) it correlates with strongly, letting a model effectively discriminate on that characteristic even when it was never included as a feature directly -- feature engineering choices earlier in this file are exactly where this risk needs to be actively checked, not assumed away.
--> **Fairness metrics, at a conceptual level** -- there is no single universal definition of "fair"; different reasonable definitions can conflict with each other. **Demographic parity** asks whether a model's positive-outcome RATE is similar across groups; **equal opportunity** asks whether the model's TRUE POSITIVE rate is similar across groups (among people who genuinely deserve a positive outcome, are they equally likely to get it, regardless of group). A model can satisfy one of these definitions while failing another on the very same data, which is why choosing which fairness definition matters for a given use case is itself a judgment call, not a purely technical one.
--> The practical takeaway threading back through this entire file -- correcting for multiple testing, choosing a defensible prior, engineering a feature, imputing a missing value -- is that statistical rigor and fairness aren't in tension; a sloppy analysis is usually both LESS statistically sound AND more likely to produce a biased, harmful conclusion, while the same discipline (checking assumptions, being explicit about judgment calls, quantifying uncertainty honestly) that makes an analysis more rigorous is also what makes it easier to audit for bias in the first place.

# Where This Leaves the Data Science Track

--> Between file 05's probability foundations and this file's inference/Bayesian/feature-engineering depth, the statistical and data-preparation groundwork the Machine Learning folder assumes is now actually in place -- hypothesis testing and its corrections, a full Bayesian alternative framework, the encoding/scaling/imputation steps every real dataset needs before it can be fed into a model, and an explicit awareness of where bias enters that pipeline, rather than an implicit assumption that clean, representative data simply shows up on its own.
