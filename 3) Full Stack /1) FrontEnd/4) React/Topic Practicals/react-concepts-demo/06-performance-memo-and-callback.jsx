/**
 * 06-performance-memo-and-callback.jsx
 *
 * DEMONSTRATES:
 *  - React.memo        : skips re-rendering a child when its props are
 *                         shallow-equal to the previous render's props.
 *  - useMemo            : caches the RESULT of an expensive calculation so
 *                         it isn't recomputed on every render.
 *  - useCallback         : caches a FUNCTION reference so it stays stable
 *                         across renders, which matters when that function
 *                         is passed as a prop to a React.memo'd child.
 *
 * Maps to Theory chapter: 06) Memo/Styling (performance section), 07) Hooks
 *
 * Usage: drop into any Vite/CRA project and render <PerformanceDemo />
 */

import { useState, useMemo, useCallback, memo } from "react";

/* ---------------------------------------------------------------------- */
/* Child component wrapped in React.memo                                  */
/* ---------------------------------------------------------------------- */
// WHY memo prevents a re-render:
// React.memo makes this component skip rendering whenever its props are
// shallow-equal (===) to the props from the previous render. Without memo,
// ListItem would re-render every time the PARENT re-renders, even if
// `label` and `onSelect` didn't actually change.
const ListItem = memo(function ListItem({ label, onSelect }) {
  console.log(`Rendering ListItem: ${label}`);
  return <li onClick={() => onSelect(label)}>{label}</li>;
});

/* ---------------------------------------------------------------------- */
/* Parent component                                                        */
/* ---------------------------------------------------------------------- */
export default function PerformanceDemo() {
  const [items] = useState(["Apple", "Banana", "Cherry"]);
  const [selected, setSelected] = useState(null);
  const [unrelatedCount, setUnrelatedCount] = useState(0);

  // ---------------------------------------------------------------------
  // useMemo: WHY it helps
  // ---------------------------------------------------------------------
  // expensiveSum recalculates a costly value (simulated with a busy loop).
  // Without useMemo, this would re-run on EVERY render of PerformanceDemo,
  // including renders caused by unrelated state like `unrelatedCount`.
  // useMemo only recomputes when `items` actually changes (its dependency).
  const expensiveSum = useMemo(() => {
    console.log("Computing expensive sum...");
    let total = 0;
    for (let i = 0; i < 1_000_000; i++) {
      total += i % items.length;
    }
    return total;
  }, [items]);

  // ---------------------------------------------------------------------
  // useCallback: WHY it helps
  // ---------------------------------------------------------------------
  // handleSelect is passed as a prop to the memo'd ListItem. If we defined
  // it as a plain arrow function inline, PerformanceDemo would create a
  // BRAND NEW function on every render — and since React.memo's shallow
  // prop comparison would then see a "different" onSelect prop every time,
  // ListItem would re-render anyway, defeating the memo. useCallback keeps
  // the SAME function reference across renders (as long as dependencies
  // don't change), so memo's comparison actually succeeds.
  const handleSelect = useCallback((label) => {
    setSelected(label);
  }, []); // no dependencies -> same reference forever

  return (
    <div style={{ fontFamily: "sans-serif", padding: 16 }}>
      <h2>Performance Demo (memo / useMemo / useCallback)</h2>

      <p>Expensive sum result: {expensiveSum}</p>
      <p>Selected: {selected ?? "none"}</p>

      <ul>
        {items.map((item) => (
          <ListItem key={item} label={item} onSelect={handleSelect} />
        ))}
      </ul>

      {/* Clicking this button changes unrelated state, causing
          PerformanceDemo to re-render. Thanks to memo + useCallback,
          the ListItem components below will NOT re-render, and thanks
          to useMemo, expensiveSum will NOT be recomputed. Check the
          console — no "Rendering ListItem" / "Computing expensive sum"
          logs should appear on this click. */}
      <button onClick={() => setUnrelatedCount((c) => c + 1)}>
        Trigger unrelated re-render ({unrelatedCount})
      </button>
    </div>
  );
}
