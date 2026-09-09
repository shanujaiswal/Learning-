-- models/fact_orders.sql
--
-- MART model: the fact table at the center of the star schema (chapter 05).
-- One row per order EVENT, numeric measures (amount) plus foreign keys out
-- to dimension tables -- built by joining the cleaned staging model out to
-- the dimensions it needs.
--
-- {{ ref('stg_orders') }} is dbt's way of referencing another model instead
-- of hardcoding a schema/table name -- dbt uses these ref() calls to build
-- the model dependency graph automatically and run `stg_orders` before
-- `fact_orders` on every `dbt run`, without either model needing to know
-- WHERE the other actually lives in the warehouse.

with orders as (

    select * from {{ ref('stg_orders') }}

),

customers as (

    select customer_id, customer_key from {{ ref('dim_customers') }}

),

products as (

    select product_id, product_key from {{ ref('dim_products') }}

)

select
    o.order_id,
    c.customer_key,
    p.product_key,
    o.order_date,
    o.amount,
    o.status
from orders o
join customers c on o.customer_id = c.customer_id
join products  p on o.product_id  = p.product_id
