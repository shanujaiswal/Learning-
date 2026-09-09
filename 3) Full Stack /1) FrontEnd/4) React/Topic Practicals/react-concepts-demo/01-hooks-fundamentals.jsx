/**
 * 01-hooks-fundamentals.jsx
 *
 * DEMONSTRATES:
 *  - useState        : basic reactive state + functional updates
 *  - useEffect       : side effects, dependency arrays, and CLEANUP functions
 *  - useRef          : (a) direct DOM access via a ref attached to an element
 *                      (b) persisting a mutable value across renders WITHOUT
 *                          triggering a re-render (unlike useState)
 *
 * Maps to Theory chapter: 07) Hooks (useState / useEffect / useRef basics)
 *
 * Usage: drop into any Vite/CRA React 18 project and render <HooksFundamentals />
 */

import { useState, useEffect, useRef } from "react";

export default function HooksFundamentals() {
  return (
    <div style={{ fontFamily: "sans-serif", padding: 16 }}>
      <h2>Hooks Fundamentals Demo</h2>
      <CounterWithState />
      <hr />
      <TimerWithEffectCleanup />
      <hr />
      <RefDomAccessAndRenderCount />
    </div>
  );
}

/* ---------------------------------------------------------------------- */
/* 1) useState — basic state + functional update form                     */
/* ---------------------------------------------------------------------- */
function CounterWithState() {
  const [count, setCount] = useState(0);

  // Functional update is safer than setCount(count + 1) when multiple
  // updates could be batched/queued — it always operates on the latest state.
  const increment = () => setCount((prev) => prev + 1);
  const decrement = () => setCount((prev) => prev - 1);

  return (
    <section>
      <h3>useState</h3>
      <p>Count: {count}</p>
      <button onClick={increment}>+1</button>
      <button onClick={decrement}>-1</button>
    </section>
  );
}

/* ---------------------------------------------------------------------- */
/* 2) useEffect — side effect with a required CLEANUP function            */
/* ---------------------------------------------------------------------- */
function TimerWithEffectCleanup() {
  const [seconds, setSeconds] = useState(0);
  const [running, setRunning] = useState(true);

  useEffect(() => {
    if (!running) return undefined;

    const intervalId = setInterval(() => {
      setSeconds((s) => s + 1);
    }, 1000);

    // Cleanup: runs when `running` changes again (before the effect re-runs)
    // AND when the component unmounts. Without this, the interval would
    // keep firing after unmount / after `running` flips to false, causing
    // a memory leak and "setState on unmounted component" style bugs.
    return () => clearInterval(intervalId);
  }, [running]);

  return (
    <section>
      <h3>useEffect (with cleanup)</h3>
      <p>Elapsed: {seconds}s</p>
      <button onClick={() => setRunning((r) => !r)}>
        {running ? "Pause" : "Resume"}
      </button>
    </section>
  );
}

/* ---------------------------------------------------------------------- */
/* 3) useRef — DOM access + persisting a value without re-rendering       */
/* ---------------------------------------------------------------------- */
function RefDomAccessAndRenderCount() {
  // (a) DOM access: ref.current points at the actual <input> DOM node.
  const inputRef = useRef(null);

  // (b) Persisted mutable value: renderCount survives across renders but
  // changing it does NOT cause a re-render (unlike useState). Useful for
  // things like "how many times did this render", previous-value tracking,
  // storing a mutable timer id, etc.
  const renderCount = useRef(0);
  renderCount.current += 1;

  const [text, setText] = useState("");

  const focusInput = () => {
    inputRef.current?.focus();
  };

  return (
    <section>
      <h3>useRef</h3>
      <input
        ref={inputRef}
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder="Type to trigger re-renders"
      />
      <button onClick={focusInput}>Focus input</button>
      {/* This number updates on screen only because the component re-renders
          due to the `text` state change above — the ref itself never causes
          a render on its own. */}
      <p>This component has rendered {renderCount.current} time(s).</p>
    </section>
  );
}
