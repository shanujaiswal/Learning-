-- models/stg_orders.sql
--
-- STAGING model: the first layer of dbt transformation. Light cleaning only
-- (renaming, casting, filtering obviously-bad rows) -- no business logic and
-- no joins to other models yet. Staging models exist so every downstream
-- model reads from one consistent, cleaned-up shape instead of everyone
-- re-implementing the same casts/renames against the raw source table.
--
-- Reads from the raw source table landed by the extract/load step in
-- 01_idempotent_extract_and_load.py (staging_orders) -- in a real project
-- this `from` would instead be `{{ source('raw', 'orders') }}`, declared in
-- a sources.yml; hardcoded here to keep this reference self-contained.

select
    order_id,
    customer_id,
    product_id,
    cast(amount as decimal(10, 2))     as amount,
    lower(trim(status))                as status,
    cast(order_date as date)           as order_date,
    updated_at
from raw.staging_orders
where amount > 0            -- exclude bad/refunded rows with non-positive amounts
  and status != 'test'      -- exclude internal test orders that shouldn't reach the warehouse
