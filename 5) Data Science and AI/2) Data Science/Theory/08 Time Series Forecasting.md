# What Makes Time Series Data Different

--> Every algorithm covered in the Machine Learning folder generally assumes data points are INDEPENDENT of each other -- shuffling the rows of a training dataset doesn't change what a Linear Regression or Random Forest learns. Time series data breaks this assumption entirely -- the ORDER of observations carries essential information (yesterday's sales genuinely influence today's), and shuffling the data would destroy the exact structure you're trying to model. This file covers the specific concepts and methods built around that fundamental difference, extending the time-based EDA techniques already introduced in the Data Science folder's Exploratory Data Analysis file.

# Decomposing a Time Series

--> A time series can be conceptually broken down into several component patterns, and separating them out is usually the first real step in understanding one.

--> **Trend** -- the long-term overall direction (steadily increasing revenue over several years).
--> **Seasonality** -- a pattern that repeats at FIXED, known intervals (higher retail sales every December, higher website traffic every weekday morning) -- directly connecting to the date-based feature engineering covered in the Feature Engineering Fundamentals file, since those extracted date components (month, day-of-week) are precisely what let a model capture seasonal effects.
--> **Cyclic patterns** -- similar to seasonality, but WITHOUT a fixed, known period (economic boom/bust cycles, which repeat but not on a precise, predictable calendar schedule).
--> **Residual/Noise** -- whatever's left over after removing trend, seasonality, and cyclic patterns -- the genuinely random, unpredictable component.

```python
from statsmodels.tsa.seasonal import seasonal_decompose

decomposition = seasonal_decompose(df["sales"], model="additive", period=12)   # period=12 for monthly data with yearly seasonality
decomposition.plot()   # Visualizes trend, seasonal, and residual components separately
```

--> "Additive" decomposition assumes components simply ADD together (`observed = trend + seasonal + residual`); "multiplicative" assumes they MULTIPLY (`observed = trend * seasonal * residual`) -- multiplicative is generally more appropriate when seasonal swings grow proportionally LARGER as the trend itself grows (e.g. holiday sales spikes that get bigger in absolute terms as a company's baseline revenue grows).

# Stationarity -- The Core Concept Classical Methods Depend On

--> A time series is "stationary" if its statistical properties (mean, variance) stay roughly CONSTANT over time -- most classical forecasting methods (covered below) specifically assume stationarity, and a non-stationary series (one with a clear trend, meaning its average level keeps changing) needs to be transformed BEFORE those methods can be applied correctly.

## Testing for Stationarity -- The Augmented Dickey-Fuller Test

```python
from statsmodels.tsa.stattools import adfuller

result = adfuller(df["sales"])
print(f"p-value: {result[1]}")
# A small p-value (< 0.05, connecting to the Hypothesis Testing concept from the Data Analyst Statistics file)
# suggests the series IS stationary; a large p-value suggests it's NOT
```

## Differencing -- The Standard Fix for Non-Stationarity

--> Rather than modeling the raw values directly, differencing models the CHANGE between consecutive observations -- removing a trend often makes an otherwise non-stationary series stationary.

```python
df["sales_diff"] = df["sales"].diff()   # Each value becomes "today's sales minus yesterday's sales"
# A steadily increasing trend in "sales" often becomes a roughly constant, stationary series in "sales_diff"
```

# Classical Forecasting Methods

## Moving Averages -- The Simplest Baseline

```python
df["rolling_avg"] = df["sales"].rolling(window=7).mean()   # Average of the last 7 observations
```

--> Smooths out short-term noise to reveal the underlying trend more clearly -- often used as a simple baseline forecast (predict tomorrow will look like the recent average) that more sophisticated models need to actually beat to be worth their added complexity.

## ARIMA -- AutoRegressive Integrated Moving Average

--> ARIMA combines three components, each addressing a different aspect of time series structure, into one model -- widely considered the classical statistical standard for time series forecasting before machine learning approaches became more common.
--> **AR (AutoRegressive)** -- predicts the current value as a function of its OWN previous values (today's sales depend on yesterday's and the day before's sales) -- directly analogous to Linear Regression, but with a time series' own past as the "features."
--> **I (Integrated)** -- the differencing step described above, applied to achieve stationarity before modeling.
--> **MA (Moving Average)** -- models the current value as a function of past FORECAST ERRORS, not past raw values -- capturing the idea that a systematic recent over/under-prediction pattern itself carries useful information for the next forecast.

```python
from statsmodels.tsa.arima.model import ARIMA

model = ARIMA(df["sales"], order=(2, 1, 2))   # (p, d, q) = (AR terms, differencing order, MA terms)
fitted_model = model.fit()

forecast = fitted_model.forecast(steps=30)   # Predict the next 30 time periods
```

--> Choosing the `(p, d, q)` parameters correctly traditionally required examining autocorrelation plots (ACF/PACF) by hand -- a genuinely specialized statistical skill, though modern tools like `pmdarima`'s `auto_arima` automate searching for reasonable parameter values.

## SARIMA -- Adding Seasonality to ARIMA

--> Standard ARIMA doesn't explicitly model SEASONAL patterns -- SARIMA extends it with an additional set of seasonal parameters, specifically designed for data with a known, regular seasonal cycle (monthly data with yearly seasonality, hourly data with daily seasonality).

# Modern and Practical Alternatives

## Facebook Prophet -- Designed for Practical, Business-Focused Forecasting

--> Prophet was specifically designed to be more ACCESSIBLE than ARIMA-family models for business analysts and data scientists without deep classical time series statistics training, while handling common real-world messiness (missing data, outliers, multiple overlapping seasonalities, holiday effects) gracefully by default.

```python
from prophet import Prophet

df_prophet = df.rename(columns={"date": "ds", "sales": "y"})   # Prophet requires specifically-named columns

model = Prophet(yearly_seasonality=True, weekly_seasonality=True)
model.add_country_holidays(country_name="US")   # Automatically accounts for US holiday effects on the series
model.fit(df_prophet)

future = model.make_future_dataframe(periods=90)   # Forecast 90 days into the future
forecast = model.predict(future)
```

## Machine Learning Approaches to Time Series

--> Rather than using specialized time-series-specific models, it's also possible to reframe forecasting as a standard supervised learning problem (covered in the Machine Learning folder) by engineering LAG FEATURES -- using past values as explicit input features to a normal regression model.

```python
df["sales_lag_1"] = df["sales"].shift(1)     # Yesterday's value
df["sales_lag_7"] = df["sales"].shift(7)      # Same day last week
df["rolling_mean_7"] = df["sales"].rolling(7).mean().shift(1)

# Now this is just a standard regression problem, using the Feature Engineering
# and Regression techniques covered in the Machine Learning folder directly
from sklearn.ensemble import RandomForestRegressor
model = RandomForestRegressor()
model.fit(X_train[["sales_lag_1", "sales_lag_7", "rolling_mean_7"]], y_train)
```

--> This approach can leverage powerful general-purpose algorithms (Random Forests, Gradient Boosting, covered in the Overfitting/Ensemble Methods file) and easily incorporate OTHER non-time features alongside the lag features (e.g. a promotional flag, weather data) -- something classical ARIMA-family models handle far less naturally, though it requires more manual feature engineering discipline (specifically to avoid the data leakage warning from the Feature Engineering file -- lag features must only ever use information that would genuinely have been available BEFORE the prediction point, never anything from the future).

## Deep Learning for Time Series

--> LSTMs (covered in their own Deep Learning folder file) were specifically designed for exactly this kind of sequential data, and Transformers (also covered there) have increasingly been adapted for time series forecasting too -- generally most valuable for very large, complex datasets with long-range dependencies, where the added complexity of a deep learning approach is actually justified over simpler classical or feature-engineered ML methods.

# Evaluating Time Series Forecasts -- Why Standard Cross-Validation Doesn't Work

--> The K-Fold Cross-Validation covered in the Model Evaluation file randomly shuffles data across folds -- completely inappropriate for time series, since it would let a model "see the future" (train on data from AFTER the point it's being evaluated on), a severe, specific form of the data leakage problem.
--> **Time Series Cross-Validation ("walk-forward validation")** instead always trains on PAST data and validates on data that comes strictly AFTER it chronologically, sliding this window forward through the dataset -- respecting the fundamental chronological ordering that makes time series data different from every other data type covered throughout this Machine Learning folder.

```python
from sklearn.model_selection import TimeSeriesSplit

tscv = TimeSeriesSplit(n_splits=5)
for train_index, test_index in tscv.split(df):
    # Each split trains only on data BEFORE the test period, never after
    X_train, X_test = X.iloc[train_index], X.iloc[test_index]
```
