# Why A/B Testing Exists

--> Simply launching a change and watching a metric change afterward doesn't prove the change CAUSED the improvement -- seasonality, marketing campaigns, or pure coincidence could explain it just as well. A/B testing (a randomized controlled experiment) is the gold-standard method for establishing that a specific change actually caused a specific effect, directly building on the correlation-vs-causation caveat from the Statistics Fundamentals file.

# The Core Mechanic -- Random Assignment

--> Users are RANDOMLY split into two (or more) groups -- Group A (the "control," seeing the current experience) and Group B (the "treatment," seeing the new version) -- and their behavior is compared on a predefined metric.
--> Random assignment is what makes the comparison valid -- because both groups are, on average, statistically identical in every OTHER way (demographics, behavior patterns, timing), any meaningful difference observed between them can be attributed to the one thing that actually differed: which experience they saw.

# Designing a Valid Experiment

--> **Define the hypothesis and metric BEFORE running the test** -- e.g. "changing the checkout button color from gray to green will increase the checkout completion rate." Deciding what counts as success AFTER seeing the data (and cherry-picking whichever metric happened to move) is a serious, common malpractice known as p-hacking.
--> **Calculate required sample size in advance** -- using the baseline conversion rate and the minimum effect size worth detecting -- stopping a test early just because it happens to look significant at that moment ("peeking") dramatically inflates the false-positive rate.
--> **Run for a full business cycle** -- at minimum a full week, to average out day-of-week effects (weekday vs weekend behavior often differs substantially).

# Analyzing the Results

--> A statistical significance test (connecting to the Hypothesis Testing content in the Statistics file) determines whether the observed difference between groups is unlikely to be pure random chance.
--> Reporting BOTH statistical significance and the actual effect size matters -- "statistically significant, +0.1% conversion" might not be worth the engineering effort to ship permanently, even though it technically "worked."

# Common Pitfalls

--> **Novelty effect** -- users may initially respond positively to ANY change simply because it's new/different, with that effect fading over time -- a short test window can mistake temporary novelty for a genuine, lasting improvement.
--> **Sample Ratio Mismatch (SRM)** -- if the actual split between groups deviates meaningfully from the intended 50/50 (or whatever ratio was configured), it signals a bug in the randomization/tracking itself, and the results shouldn't be trusted until that's investigated and fixed.
--> **Multiple comparisons problem** -- testing many metrics simultaneously dramatically increases the odds that AT LEAST ONE shows "significance" purely by chance, even if nothing real is happening -- a well-known statistical trap when a team runs a test and then digs through dozens of secondary metrics looking for something significant.
--> **Network effects / interference** -- in social or marketplace products, one group's behavior can actually affect the other group (e.g. a change to sellers' pricing affects buyers in BOTH the control and treatment group) -- violating the assumption that the two groups are truly independent, requiring more sophisticated experimental designs to handle correctly.

# Beyond Simple A/B -- Related Experimental Designs

--> **A/B/n testing** -- comparing more than two variants simultaneously.
--> **Multivariate testing** -- testing multiple changed elements at once and measuring their combined/interaction effects, rather than one change at a time.
--> **Sequential testing** -- statistical methods specifically designed to allow valid early stopping, addressing the "peeking" problem mentioned above without requiring a fixed sample size decided entirely in advance.
