/**
 * 03-context-api-theme.jsx
 *
 * DEMONSTRATES:
 *  - createContext + a Provider component that holds shared state
 *  - useContext in a deeply nested consumer, AVOIDING PROP DRILLING
 *    (no need to pass `theme`/`toggleTheme` down through every level)
 *
 * Maps to Theory chapter: 12) Context API
 *
 * Usage: wrap your app (or part of it) in <ThemeProvider>, then call
 * useTheme() in any descendant component, no matter how deeply nested.
 */

import { createContext, useContext, useState } from "react";

/* ---------------------------------------------------------------------- */
/* 1) Create the context (default value only used if there's no Provider) */
/* ---------------------------------------------------------------------- */
const ThemeContext = createContext(undefined);

/* ---------------------------------------------------------------------- */
/* 2) Provider — owns the actual state and exposes it + a setter function */
/* ---------------------------------------------------------------------- */
export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState("light");

  const toggleTheme = () =>
    setTheme((prev) => (prev === "light" ? "dark" : "light"));

  const value = { theme, toggleTheme };

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}

/* ---------------------------------------------------------------------- */
/* 3) Custom hook wrapper — nicer API + guards against missing Provider    */
/* ---------------------------------------------------------------------- */
export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (ctx === undefined) {
    throw new Error("useTheme must be used within a <ThemeProvider>");
  }
  return ctx;
}

/* ---------------------------------------------------------------------- */
/* 4) Consumers — no props needed for theme, at ANY nesting depth          */
/* ---------------------------------------------------------------------- */
function ThemedButton() {
  const { theme, toggleTheme } = useTheme();

  const style = {
    background: theme === "light" ? "#fff" : "#222",
    color: theme === "light" ? "#222" : "#fff",
    border: "1px solid #888",
    padding: "8px 16px",
    borderRadius: 4,
  };

  return (
    <button style={style} onClick={toggleTheme}>
      Current theme: {theme} (click to toggle)
    </button>
  );
}

function DeeplyNestedSection() {
  // Notice: this component receives NO theme-related props at all,
  // yet it can still read (and change) the theme via useContext.
  return (
    <div>
      <p>I am several levels deep and still have access to the theme.</p>
      <ThemedButton />
    </div>
  );
}

function PageBody() {
  return (
    <section>
      <DeeplyNestedSection />
    </section>
  );
}

/* ---------------------------------------------------------------------- */
/* 5) Top-level app wiring                                                 */
/* ---------------------------------------------------------------------- */
export default function ThemeDemoApp() {
  return (
    <ThemeProvider>
      <div style={{ fontFamily: "sans-serif", padding: 16 }}>
        <h2>Context API Theme Demo</h2>
        <PageBody />
      </div>
    </ThemeProvider>
  );
}
