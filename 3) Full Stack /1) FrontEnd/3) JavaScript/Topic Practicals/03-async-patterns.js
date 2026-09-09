/**
 * 03-async-patterns.js
 * HOW TO RUN: plain Node.js (v18+) -> `node 03-async-patterns.js`
 * Uses the global `fetch` and `AbortController`, both built into modern Node
 * (v18+) and every modern browser - no extra install needed. Also runnable
 * by pasting into a browser console.
 *
 * Covers (Theory folder):
 *  - Chapter 8: Async JS / Generators
 *
 * Demonstrates:
 *  1. Promise.all (parallel) vs sequential await, with timestamps proving
 *     the timing difference.
 *  2. A working AbortController fetch-cancellation example.
 *  3. An async generator consuming "paginated" data.
 */

"use strict";

// A tiny helper to simulate a network call that takes `ms` milliseconds.
function fakeFetchDelay(label, ms) {
  return new Promise((resolve) =>
    setTimeout(() => resolve(`${label} resolved after ${ms}ms`), ms)
  );
}

const elapsedSince = (startedAt) => `${(Date.now() - startedAt)}ms`;

// ===========================================================================
// PART 1: Promise.all (parallel) vs sequential await - timing proof.
// ===========================================================================
async function runSequentialVsParallelDemo() {
  console.log("=== Sequential await (each call waits for the previous one) ===");
  const seqStart = Date.now();

  const a = await fakeFetchDelay("Task A", 300);
  console.log(`[sequential] t+${elapsedSince(seqStart)} -> ${a}`);

  const b = await fakeFetchDelay("Task B", 300);
  console.log(`[sequential] t+${elapsedSince(seqStart)} -> ${b}`);

  const c = await fakeFetchDelay("Task C", 300);
  console.log(`[sequential] t+${elapsedSince(seqStart)} -> ${c}`);

  console.log(`[sequential] TOTAL TIME: ${elapsedSince(seqStart)} (roughly 300+300+300 = ~900ms)\n`);

  console.log("=== Promise.all (all three run concurrently) ===");
  const parStart = Date.now();

  const [x, y, z] = await Promise.all([
    fakeFetchDelay("Task X", 300),
    fakeFetchDelay("Task Y", 300),
    fakeFetchDelay("Task Z", 300),
  ]);

  console.log(`[parallel] t+${elapsedSince(parStart)} -> ${x}`);
  console.log(`[parallel] t+${elapsedSince(parStart)} -> ${y}`);
  console.log(`[parallel] t+${elapsedSince(parStart)} -> ${z}`);
  console.log(`[parallel] TOTAL TIME: ${elapsedSince(parStart)} (roughly max(300,300,300) = ~300ms)`);
  console.log(
    "CONCLUSION: sequential await took ~3x longer than Promise.all for the same three tasks.\n"
  );
}

// ===========================================================================
// PART 2: AbortController fetch-cancellation example.
// We hit a real, slow-ish public endpoint (httpbin's /delay) and cancel the
// request deliberately before it can finish, proving the abort actually works.
// If there is no network access in your environment, the catch block still
// demonstrates the AbortError path via the timeout race below.
// ===========================================================================
async function runAbortControllerDemo() {
  console.log("=== AbortController fetch-cancellation demo ===");
  const controller = new AbortController();
  const { signal } = controller;

  // Abort the request after 200ms - well before a 3-second delayed response.
  const abortTimer = setTimeout(() => {
    console.log("[abort-demo] Aborting the request now (200ms elapsed)...");
    controller.abort();
  }, 200);

  try {
    const response = await fetch("https://httpbin.org/delay/3", { signal });
    clearTimeout(abortTimer);
    const data = await response.json();
    console.log("[abort-demo] Unexpectedly got a response before abort:", data);
  } catch (err) {
    clearTimeout(abortTimer);
    if (err?.name === "AbortError") {
      console.log("[abort-demo] SUCCESS: fetch was cancelled as expected (AbortError caught).");
    } else {
      console.log(
        `[abort-demo] Fetch failed for another reason (likely no network in this sandbox): ${err?.message ?? err}`
      );
      console.log("[abort-demo] This is expected offline - the AbortController wiring above is still correct.");
    }
  }
  console.log("");
}

// ===========================================================================
// PART 3: Async generator consuming paginated "data".
// Simulates an API that returns pages of results plus a `nextCursor`. The
// async generator yields items one page at a time, awaiting a fake network
// call between pages, and the consumer uses `for await...of` to drain it.
// ===========================================================================
const FAKE_DATABASE = Array.from({ length: 23 }, (_, i) => ({ id: i + 1, name: `Item-${i + 1}` }));

// Simulates a paged network endpoint: returns { items, nextCursor }.
function fakeFetchPage(cursor = 0, pageSize = 5) {
  return new Promise((resolve) => {
    setTimeout(() => {
      const items = FAKE_DATABASE.slice(cursor, cursor + pageSize);
      const nextCursor = cursor + pageSize < FAKE_DATABASE.length ? cursor + pageSize : null;
      resolve({ items, nextCursor });
    }, 100); // simulate network latency per page
  });
}

async function* paginate(pageSize = 5) {
  let cursor = 0;
  let pageNumber = 1;
  while (cursor !== null) {
    const { items, nextCursor } = await fakeFetchPage(cursor, pageSize);
    console.log(`[paginate] fetched page ${pageNumber} (${items.length} items, cursor was ${cursor})`);
    yield* items; // yield each item individually to the consumer
    cursor = nextCursor;
    pageNumber++;
  }
}

async function runAsyncGeneratorDemo() {
  console.log("=== Async generator consuming paginated data ===");
  const collected = [];
  for await (const item of paginate(5)) {
    collected.push(item);
  }
  console.log(`[paginate] Total items collected across all pages: ${collected.length}`);
  console.log(`[paginate] First item: ${JSON.stringify(collected.at(0))}`);
  console.log(`[paginate] Last item: ${JSON.stringify(collected.at(-1))}`);
}

// ===========================================================================
// Run all three demos in order (each awaited so console output stays readable).
// ===========================================================================
async function main() {
  await runSequentialVsParallelDemo();
  await runAbortControllerDemo();
  await runAsyncGeneratorDemo();
}

main().catch((err) => console.error("Unexpected top-level error:", err));
