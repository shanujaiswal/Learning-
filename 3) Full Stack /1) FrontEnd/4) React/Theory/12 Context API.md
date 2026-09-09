# Context API -- Deep Dive

--> React Context lets state be shared across the component tree without manually passing it down through every level as props ("prop drilling")
--> Already introduced briefly with useContext in "07 React Hooks.md" -- this covers the patterns needed to use Context well in a real app

# Creating and Providing Context

```javascript
import { createContext, useContext, useState } from "react";

const ThemeContext = createContext(null); // default value used only if no Provider is found above

function App() {
  const [theme, setTheme] = useState("dark");
  return (
    <ThemeContext.Provider value={{ theme, setTheme }}>
      <Toolbar />
    </ThemeContext.Provider>
  );
}

function Toolbar() {
  const { theme, setTheme } = useContext(ThemeContext);
  return <button onClick={() => setTheme(theme === "dark" ? "light" : "dark")}>{theme}</button>;
}
```

--> The default value passed to createContext() is ONLY used when a component reads the context with no matching Provider above it in the tree -- it is not a "fallback" for an empty value from a real Provider

# Splitting Contexts to Avoid Unnecessary Re-renders

--> Every component that calls useContext(SomeContext) re-renders whenever the Provider's value changes -- even if that component only cares about one field of a large object
--> Fix: split one big context into multiple smaller ones, so a component only subscribes to (and re-renders from) the slice it actually needs

```javascript
// Instead of one context with { user, theme, cart, notifications }
const UserContext = createContext(null);
const ThemeContext = createContext(null);
const CartContext = createContext(null);
// A component that only reads ThemeContext never re-renders when cart changes
```

--> Also split "state" and "dispatch/setter" into separate contexts when only some consumers need to trigger updates -- components that only read state don't re-render when just the setter function identity changes (rare, but relevant for memoized setter functions)

# Memoizing the Provider Value

--> If the value passed to Provider is a new object literal on every render of the parent, EVERY consumer re-renders on every parent render, even if the actual data didn't change
--> Wrap the value in useMemo so it keeps the same reference unless its dependencies actually change

```javascript
function App() {
  const [theme, setTheme] = useState("dark");
  const value = useMemo(() => ({ theme, setTheme }), [theme]); // stable reference unless theme changes
  return <ThemeContext.Provider value={value}><Toolbar /></ThemeContext.Provider>;
}
```

# Context + useReducer Pattern

--> Combining useReducer with Context is a common lightweight alternative to Redux for medium-sized apps -- centralizes update logic in a reducer while still avoiding prop drilling

```javascript
const CartContext = createContext(null);
const CartDispatchContext = createContext(null);

function cartReducer(state, action) {
  switch (action.type) {
    case "add": return [...state, action.item];
    case "remove": return state.filter((i) => i.id !== action.id);
    default: return state;
  }
}

function CartProvider({ children }) {
  const [cart, dispatch] = useReducer(cartReducer, []);
  return (
    <CartContext.Provider value={cart}>
      <CartDispatchContext.Provider value={dispatch}>{children}</CartDispatchContext.Provider>
    </CartContext.Provider>
  );
}

// Usage in any nested component
const cart = useContext(CartContext);
const dispatch = useContext(CartDispatchContext);
dispatch({ type: "add", item: { id: 1, name: "Book" } });
```

# Custom Hook Wrapper for Context

--> Wrapping useContext in a custom hook gives a nicer API and a helpful error if used outside its Provider, instead of a silent null/default value

```javascript
function useTheme() {
  const context = useContext(ThemeContext);
  if (context === null) {
    throw new Error("useTheme must be used within a ThemeProvider");
  }
  return context;
}
```

# When NOT to Use Context

--> Context is not a general-purpose state manager -- it's built for values that rarely change and are needed widely (theme, current user, locale)
--> For state that changes frequently and is read by many components (e.g. a large shopping cart, real-time data), Context's all-consumers-re-render behavior can hurt performance -- a dedicated state library (Redux, Zustand) with selective subscriptions is a better fit -- see "13 Redux and Zustand.md"
