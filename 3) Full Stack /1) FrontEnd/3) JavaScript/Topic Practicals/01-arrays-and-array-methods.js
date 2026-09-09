/**
 * 01-arrays-and-array-methods.js
 * HOW TO RUN: plain Node.js -> `node 01-arrays-and-array-methods.js`
 * (No browser/DOM APIs used. Also runs fine pasted into a browser console.)
 *
 * Covers (Theory folder):
 *  - Chapter 2: Arrays / Objects / Collections
 *  - Chapter 5: Array Methods (map, filter, reduce, find, some, every, flat, flatMap)
 *
 * We model a small e-commerce "orders" dataset and chain array methods on it
 * to answer realistic business questions.
 */

"use strict";

// ---------------------------------------------------------------------------
// Realistic dataset: an array of order objects, each with nested line items.
// ---------------------------------------------------------------------------
const orders = [
  {
    id: "ORD-1001",
    customer: "Asha Rao",
    status: "delivered",
    createdAt: "2026-01-05",
    items: [
      { sku: "SKU-1", name: "Wireless Mouse", price: 799, qty: 2, category: "electronics" },
      { sku: "SKU-2", name: "USB-C Cable", price: 299, qty: 3, category: "electronics" },
    ],
  },
  {
    id: "ORD-1002",
    customer: "Ben Wu",
    status: "cancelled",
    createdAt: "2026-01-08",
    items: [{ sku: "SKU-3", name: "Yoga Mat", price: 999, qty: 1, category: "fitness" }],
  },
  {
    id: "ORD-1003",
    customer: "Asha Rao",
    status: "delivered",
    createdAt: "2026-02-02",
    items: [
      { sku: "SKU-4", name: "Notebook", price: 99, qty: 5, category: "stationery" },
      { sku: "SKU-5", name: "Pen Set", price: 149, qty: 2, category: "stationery" },
    ],
  },
  {
    id: "ORD-1004",
    customer: "Carlos Diaz",
    status: "pending",
    createdAt: "2026-02-10",
    items: [{ sku: "SKU-1", name: "Wireless Mouse", price: 799, qty: 1, category: "electronics" }],
  },
  {
    id: "ORD-1005",
    customer: "Deja Clarke",
    status: "delivered",
    createdAt: "2026-02-15",
    items: [
      { sku: "SKU-6", name: "Dumbbell Set", price: 2499, qty: 1, category: "fitness" },
      { sku: "SKU-3", name: "Yoga Mat", price: 999, qty: 2, category: "fitness" },
    ],
  },
];

// ---------------------------------------------------------------------------
// 1) map: compute the total value of every order (price * qty summed over items)
// ---------------------------------------------------------------------------
const orderTotals = orders.map((order) => ({
  id: order.id,
  customer: order.customer,
  status: order.status,
  total: order.items.reduce((sum, item) => sum + item.price * item.qty, 0),
}));

console.log("=== Order totals (map + inner reduce) ===");
orderTotals.forEach((o) => console.log(`${o.id} (${o.customer}) -> Rs.${o.total} [${o.status}]`));

// ---------------------------------------------------------------------------
// 2) filter + reduce: total revenue from DELIVERED orders only
// ---------------------------------------------------------------------------
const deliveredRevenue = orderTotals
  .filter((o) => o.status === "delivered")
  .reduce((sum, o) => sum + o.total, 0);

console.log(`\n=== Revenue from delivered orders only ===`);
console.log(`Total delivered revenue: Rs.${deliveredRevenue}`);

// ---------------------------------------------------------------------------
// 3) find: the first pending order (e.g. to flag for fulfilment)
// ---------------------------------------------------------------------------
const firstPending = orders.find((o) => o.status === "pending");
console.log(`\n=== First pending order ===`);
console.log(firstPending ? `${firstPending.id} belongs to ${firstPending.customer}` : "No pending orders");

// ---------------------------------------------------------------------------
// 4) some / every: quick health checks
// ---------------------------------------------------------------------------
const hasCancelledOrder = orders.some((o) => o.status === "cancelled");
const allOrdersHaveItems = orders.every((o) => o.items.length > 0);

console.log(`\n=== some/every checks ===`);
console.log(`At least one order cancelled? ${hasCancelledOrder}`);
console.log(`Do all orders contain at least one item? ${allOrdersHaveItems}`);

// ---------------------------------------------------------------------------
// 5) flatMap: build a single flat list of every line item across all orders,
//    tagged with the parent order id (this is the "flatten while mapping" case).
// ---------------------------------------------------------------------------
const allLineItems = orders.flatMap((order) =>
  order.items.map((item) => ({ ...item, orderId: order.id, orderStatus: order.status }))
);

console.log(`\n=== Flattened line items via flatMap (${allLineItems.length} total) ===`);
console.log(allLineItems.slice(0, 3), "...(truncated)");

// ---------------------------------------------------------------------------
// 6) flat: demonstrate flattening a manually nested array (e.g. items grouped
//    by order, as an array-of-arrays) to contrast with flatMap above.
// ---------------------------------------------------------------------------
const itemsGroupedByOrder = orders.map((order) => order.items);
const itemsFlattenedOneLevel = itemsGroupedByOrder.flat(); // same effect as flatMap here

console.log(`\n=== flat(1) on array-of-arrays ===`);
console.log(`Grouped array length (one entry per order): ${itemsGroupedByOrder.length}`);
console.log(`Flattened length (one entry per item): ${itemsFlattenedOneLevel.length}`);

// ---------------------------------------------------------------------------
// 7) Chained pipeline: revenue per category, delivered orders only, sorted desc.
//    filter -> flatMap -> reduce -> Object.entries -> sort
// ---------------------------------------------------------------------------
const revenueByCategory = orders
  .filter((o) => o.status === "delivered")
  .flatMap((o) => o.items)
  .reduce((acc, item) => {
    acc[item.category] = (acc[item.category] ?? 0) + item.price * item.qty;
    return acc;
  }, {});

const rankedCategories = Object.entries(revenueByCategory)
  .map(([category, revenue]) => ({ category, revenue }))
  .sort((a, b) => b.revenue - a.revenue);

console.log(`\n=== Revenue by category (delivered orders, ranked) ===`);
rankedCategories.forEach((c, i) => console.log(`${i + 1}. ${c.category}: Rs.${c.revenue}`));

// ---------------------------------------------------------------------------
// 8) Repeat-customer detection: which customers ordered more than once?
// ---------------------------------------------------------------------------
const orderCountByCustomer = orders.reduce((acc, o) => {
  acc[o.customer] = (acc[o.customer] ?? 0) + 1;
  return acc;
}, {});

const repeatCustomers = Object.entries(orderCountByCustomer)
  .filter(([, count]) => count > 1)
  .map(([customer]) => customer);

console.log(`\n=== Repeat customers ===`);
console.log(repeatCustomers.length ? repeatCustomers.join(", ") : "No repeat customers");

// ---------------------------------------------------------------------------
// 9) Array.prototype.at (ES2022) - grab the most recent order without index math
// ---------------------------------------------------------------------------
const mostRecentOrder = orders.at(-1);
console.log(`\n=== Most recent order (Array.prototype.at(-1)) ===`);
console.log(`${mostRecentOrder.id} placed on ${mostRecentOrder.createdAt}`);
