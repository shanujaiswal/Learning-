"""
05 - Feature Engineering and Encoding
=======================================
Demonstrates: Feature Engineering + Encoding/Scaling.

We build a small realistic DataFrame (customer purchase records) and show:
  - One-hot encoding of a nominal categorical column.
  - Label encoding of an ordinal categorical column.
  - StandardScaler and MinMaxScaler on numeric columns.
  - A derived feature: day-of-week extracted from a date column.
"""

import pandas as pd
from sklearn.preprocessing import (
    OneHotEncoder,
    LabelEncoder,
    StandardScaler,
    MinMaxScaler,
)


def main():
    # 1. A small realistic DataFrame.
    df = pd.DataFrame(
        {
            "customer_id": [1, 2, 3, 4, 5, 6],
            "city": ["Mumbai", "Delhi", "Mumbai", "Bengaluru", "Delhi", "Bengaluru"],
            "membership_tier": ["Silver", "Gold", "Platinum", "Silver", "Gold", "Platinum"],
            "purchase_amount": [1200.0, 4500.0, 9800.0, 800.0, 3000.0, 12000.0],
            "purchase_date": pd.to_datetime(
                [
                    "2026-01-05",  # Monday
                    "2026-01-10",  # Saturday
                    "2026-02-02",  # Monday
                    "2026-02-14",  # Saturday
                    "2026-03-03",  # Tuesday
                    "2026-03-08",  # Sunday
                ]
            ),
        }
    )

    print("Original DataFrame:")
    print(df)

    # 2. Derived feature: day-of-week from the date column.
    df["purchase_day_of_week"] = df["purchase_date"].dt.day_name()
    df["is_weekend"] = df["purchase_date"].dt.dayofweek >= 5  # Sat=5, Sun=6

    print("\nAfter adding derived date features (day-of-week, is_weekend):")
    print(df[["purchase_date", "purchase_day_of_week", "is_weekend"]])

    # 3. One-hot encoding of a nominal categorical column ('city').
    ohe = OneHotEncoder(sparse_output=False)
    city_encoded = ohe.fit_transform(df[["city"]])
    city_encoded_df = pd.DataFrame(
        city_encoded, columns=ohe.get_feature_names_out(["city"])
    )
    print("\nOne-hot encoded 'city' column:")
    print(city_encoded_df)

    # 4. Label encoding of an ordinal categorical column ('membership_tier').
    # We map the natural order explicitly so the encoding respects the
    # ordinal relationship (Silver < Gold < Platinum), then also show the
    # generic sklearn LabelEncoder for comparison.
    tier_order = {"Silver": 0, "Gold": 1, "Platinum": 2}
    df["membership_tier_ordinal"] = df["membership_tier"].map(tier_order)

    le = LabelEncoder()
    df["membership_tier_label_encoded"] = le.fit_transform(df["membership_tier"])

    print("\nLabel-encoded 'membership_tier' (ordinal-aware map vs plain LabelEncoder):")
    print(
        df[
            [
                "membership_tier",
                "membership_tier_ordinal",
                "membership_tier_label_encoded",
            ]
        ]
    )

    # 5. Scaling numeric columns.
    numeric_cols = ["purchase_amount"]

    standard_scaled = StandardScaler().fit_transform(df[numeric_cols])
    minmax_scaled = MinMaxScaler().fit_transform(df[numeric_cols])

    df["purchase_amount_standard_scaled"] = standard_scaled
    df["purchase_amount_minmax_scaled"] = minmax_scaled

    print("\nScaled 'purchase_amount' (StandardScaler -> mean 0/std 1, MinMaxScaler -> [0,1]):")
    print(
        df[
            [
                "purchase_amount",
                "purchase_amount_standard_scaled",
                "purchase_amount_minmax_scaled",
            ]
        ]
    )

    # 6. Final engineered feature set.
    final = pd.concat(
        [
            df[
                [
                    "customer_id",
                    "purchase_day_of_week",
                    "is_weekend",
                    "membership_tier_ordinal",
                    "purchase_amount_standard_scaled",
                    "purchase_amount_minmax_scaled",
                ]
            ],
            city_encoded_df,
        ],
        axis=1,
    )
    print("\n=== Final engineered feature table ===")
    print(final)


if __name__ == "__main__":
    main()
