# Redux -- Core Concepts

--> Redux is a predictable state container -- ALL app state lives in a single store (a plain JS object), and the only way to change it is by dispatching an action
--> Three core principles: single source of truth (one store), state is read-only (never mutate directly), changes are made with pure reducer functions

--> Store -- holds the entire app state tree
--> Action -- a plain object describing what happened, e.g. { type: "cart/add", payload: item } -- must have a type field
--> Reducer -- a pure function (state, action) => newState -- given the same input always returns the same output, never mutates state, returns a new object instead
--> Dispatch -- store.dispatch(action) is the only way to trigger a state change -- it runs the reducer and updates the store

```javascript
function cartReducer(state = [], action) {
  switch (action.type) {
    case "cart/add":
      return [...state, action.payload]; // new array, never mutate state directly
    case "cart/remove":
      return state.filter((item) => item.id !== action.payload);
    default:
      return state;
  }
}
```

# Redux Toolkit (RTK) -- Modern Standard

--> Redux Toolkit is now the officially recommended way to write Redux -- removes most boilerplate of hand-written reducers/action types/immutable updates
--> createSlice() generates action creators and a reducer together from a single object, and lets you write "mutating" logic safely because it uses Immer internally

```javascript
import { createSlice, configureStore } from "@reduxjs/toolkit";

const cartSlice = createSlice({
  name: "cart",
  initialState: [],
  reducers: {
    add: (state, action) => { state.push(action.payload); }, // looks mutating, Immer makes it safe/immutable under the hood
    remove: (state, action) => state.filter((item) => item.id !== action.payload.id),
  },
});

export const { add, remove } = cartSlice.actions; // auto-generated action creators
const store = configureStore({ reducer: { cart: cartSlice.reducer } });
```

# Connecting Redux to React

--> react-redux provides the <Provider> component and hooks to connect components to the store

```javascript
import { Provider, useSelector, useDispatch } from "react-redux";

function App() {
  return <Provider store={store}><Cart /></Provider>;
}

function Cart() {
  const items = useSelector((state) => state.cart); // subscribes only to the slice it reads -- re-renders only if this slice changes
  const dispatch = useDispatch();
  return <button onClick={() => dispatch(add({ id: 1, name: "Book" }))}>Add</button>;
}
```

--> useSelector re-renders the component only when the SELECTED slice of state changes (by reference), not on every store update -- this is Redux's answer to Context's "everything re-renders" problem

# Middleware and Async Logic

--> Middleware sits between dispatch and the reducer -- used for logging, crash reporting, and (most commonly) async logic
--> Redux Toolkit's createAsyncThunk handles async action creators (e.g. API calls) with automatic pending/fulfilled/rejected action types

```javascript
import { createAsyncThunk } from "@reduxjs/toolkit";

const fetchUser = createAsyncThunk("user/fetch", async (id) => {
  const res = await fetch(`/api/users/${id}`);
  return res.json();
});
// dispatch(fetchUser(1)) automatically dispatches user/fetch/pending, then /fulfilled or /rejected
```

---

# Zustand -- Lightweight Alternative

--> Zustand is a small, hooks-based state management library -- no boilerplate actions/reducers, no Provider wrapper required, minimal API surface
--> A store is just a hook created with create() -- state and the functions that update it live together in one place

```javascript
import { create } from "zustand";

const useCartStore = create((set, get) => ({
  items: [],
  add: (item) => set((state) => ({ items: [...state.items, item] })),
  remove: (id) => set((state) => ({ items: state.items.filter((i) => i.id !== id) })),
  total: () => get().items.reduce((sum, i) => sum + i.price, 0),
}));
```

# Using a Zustand Store in Components

```javascript
function Cart() {
  const items = useCartStore((state) => state.items); // selector -- only re-renders when `items` changes
  const add = useCartStore((state) => state.add);
  return <button onClick={() => add({ id: 1, name: "Book", price: 10 })}>Add</button>;
}
```

--> No <Provider> needed -- the store is just a module-level hook, importable and usable directly in any component
--> Selective subscription works the same way as Redux's useSelector -- passing a selector function means the component only re-renders when that specific piece changes

# Zustand Middleware

--> persist -- automatically saves/restores store state to localStorage/sessionStorage
--> devtools -- connects the store to Redux DevTools browser extension for time-travel debugging
--> immer -- lets you write "mutating" update logic like RTK's createSlice does

```javascript
import { persist } from "zustand/middleware";

const useSettingsStore = create(
  persist(
    (set) => ({ theme: "dark", setTheme: (theme) => set({ theme }) }),
    { name: "settings-storage" } // localStorage key
  )
);
```

# Redux vs Zustand -- When to Use Which

--> Redux (with RTK) -- better for large apps needing strict conventions, time-travel debugging, a big team that benefits from enforced structure, or heavy middleware needs
--> Zustand -- better for small-to-medium apps, faster to set up, less boilerplate, no Provider tree, simpler mental model
--> Both solve the same core problem Context struggles with at scale: selective re-rendering based on which slice of state a component actually reads

# Deep Dive -- Normalizing State Shape

--> A common mistake when storing relational data (e.g. a list of blog posts, each with an author and comments) is nesting it deeply, mirroring how an API returned it -- this makes updating a single nested item awkward (requiring the same deep-spread pattern covered in the Immutability file) and can cause duplicate copies of the same entity to drift out of sync.

```javascript
// Nested (harder to update) -- if the same author appears on 10 posts, their data is duplicated 10 times
{ posts: [{ id: 1, author: { id: 5, name: "Alice" }, comments: [...] }] }

// Normalized (like a mini relational database, connecting to the Normalization file in the Database Advanced notes)
{
  posts: { byId: { 1: { id: 1, authorId: 5, commentIds: [10, 11] } }, allIds: [1] },
  authors: { byId: { 5: { id: 5, name: "Alice" } }, allIds: [5] },
  comments: { byId: { 10: {...}, 11: {...} }, allIds: [10, 11] }
}
```

--> Normalizing means each entity is stored ONCE, referenced by ID everywhere else -- updating Alice's name updates it everywhere she's referenced, with no risk of stale duplicate copies -- Redux's own documentation specifically recommends this shape for any non-trivial relational data, and libraries like `normalizr` automate the transformation from a nested API response into this shape.

# Deep Dive -- Memoized Selectors With Reselect

--> A `useSelector` that computes a NEW derived value inline (e.g. filtering/sorting a list) creates a brand-new array/object reference on EVERY render, defeating the reference-equality optimization that makes `useSelector` efficient in the first place, and causing unnecessary re-renders.

```javascript
// Problematic -- .filter() returns a NEW array reference every single render, even if nothing relevant changed
const completedTodos = useSelector(state => state.todos.filter(t => t.completed));

// Fixed with a memoized selector (createSelector from Reselect, bundled with Redux Toolkit)
import { createSelector } from "@reduxjs/toolkit";

const selectCompletedTodos = createSelector(
  state => state.todos,
  todos => todos.filter(t => t.completed)   // Only recomputes if "state.todos" itself actually changed
);
```

--> `createSelector` caches its result and only recomputes when its actual INPUT selectors' outputs change -- directly analogous to the `useMemo` concept covered in the Hooks file, just implemented at the Redux-store level rather than the component level.
