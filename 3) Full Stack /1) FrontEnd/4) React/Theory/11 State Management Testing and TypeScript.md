# Context vs Redux/Zustand -- When to Reach for a Library

--> useContext (see React Hooks notes) is fine for state that changes rarely (theme, logged-in user, locale) -- every consumer re-renders whenever the context value changes.
--> For state that changes often and is read by many components, a dedicated state library avoids the re-render cost and gives better devtools/structure.

# Redux (brief)

--> A predictable global state container based on a single store, actions, and reducers.
--> Store --> holds the entire app state in one object. Action --> a plain object describing what happened ({ type: "INCREMENT" }). Reducer --> a pure function (state, action) => newState that computes the next state.
--> dispatch(action) is the only way to trigger a state change -- components never mutate the store directly.
--> Redux Toolkit (RTK) is the modern, recommended way to write Redux -- it removes most of the boilerplate of hand-written actions/reducers.
--> useSelector(state => state.slice) reads from the store, useDispatch() gives you the dispatch function, inside a component wrapped in <Provider store={store}>.

# Zustand (brief)

--> A lighter-weight alternative to Redux -- a store is just a hook created with create(), no Provider needed.
--> const useStore = create((set) => ({ count: 0, increment: () => set((s) => ({ count: s.count + 1 })) }))
--> Components call useStore(state => state.count) directly -- less boilerplate than Redux for small/medium apps.

# Redux Toolkit -- createSlice Example

--> Redux Toolkit's `createSlice()` generates action creators and a reducer together from a single object, eliminating the hand-written switch-statement reducers and separate action-type constants that plain Redux required.

```javascript
import { createSlice, configureStore } from "@reduxjs/toolkit";

const counterSlice = createSlice({
  name: "counter",
  initialState: { value: 0 },
  reducers: {
    increment: (state) => {
      state.value += 1;   // Looks like direct mutation, but Immer (see below) makes it safe
    },
    decrement: (state) => {
      state.value -= 1;
    },
    incrementByAmount: (state, action) => {
      state.value += action.payload;
    },
  },
});

// Auto-generated action creators
export const { increment, decrement, incrementByAmount } = counterSlice.actions;

const store = configureStore({
  reducer: { counter: counterSlice.reducer },
});

// In a component:
// const count = useSelector((state) => state.counter.value);
// const dispatch = useDispatch();
// dispatch(increment());
// dispatch(incrementByAmount(5));
```

# Immer (Why "Mutating" State Works in Redux Toolkit)

--> Immer is a library (used internally by Redux Toolkit's `createSlice`) that lets you write code that LOOKS like it's directly mutating state (`state.value += 1`), while actually producing a new, immutable state object behind the scenes.
--> It works by giving reducer functions a special "draft" proxy object -- any changes made to the draft are recorded, then Immer produces a brand-new state object with those changes applied, leaving the original state untouched.
--> This is why plain Redux reducers must NEVER mutate state directly (`state.value = 5` would silently break change-detection), but Redux Toolkit reducers safely can -- Immer is handling the immutability underneath.
```javascript
// Without Immer (plain Redux) -- must return a new object manually:
function reducer(state, action) {
  return { ...state, value: state.value + 1 };
}

// With Immer (Redux Toolkit) -- write it as if it were mutable:
function reducer(state, action) {
  state.value += 1;   // Immer converts this into an immutable update automatically
}
```

# React Query / TanStack Query (brief)

--> Handles server state (data fetched from an API -- caching, refetching, loading/error states) which is a different concern from client state (Redux/Zustand manage UI/app state you own).
--> useQuery(["todos"], fetchTodos) fetches and caches data, with built-in loading/error flags and automatic background refetching -- removes most manual useEffect + useState fetch boilerplate.

# Testing React Components (brief)

--> Jest --> the test runner/assertion library (test(), expect(), describe()) -- comes preconfigured with Create React App.
--> React Testing Library (RTL) --> renders components and lets you query/interact with them the way a user would, instead of testing internal implementation details.
--> render(<MyComponent />) mounts the component; screen.getByText(...) / getByRole(...) find elements; fireEvent.click(...) or userEvent.click(...) simulate interaction.
--> Guiding principle: "test what the user sees and does," not component internals -- makes tests survive refactors.
--> expect(screen.getByText("Hello")).toBeInTheDocument() is a typical assertion.
--> Mocking -- jest.mock("./api") replaces a module with a fake implementation so tests don't hit a real network/DB; MSW (Mock Service Worker) intercepts actual fetch/axios requests at the network level for more realistic tests.

==> Full RTL Test File Example
```javascript
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Counter from "./Counter";

test("increments the count when the button is clicked", async () => {
  const user = userEvent.setup();
  render(<Counter />);

  expect(screen.getByText("Count: 0")).toBeInTheDocument();

  await user.click(screen.getByRole("button", { name: /increment/i }));

  expect(screen.getByText("Count: 1")).toBeInTheDocument();
});
```

==> Integration Testing
--> An integration test renders multiple components together (e.g. a form + its parent page) and verifies they work correctly as a unit -- closer to how the app is actually used than isolated unit tests of a single component.
--> RTL naturally encourages this style since it queries the rendered DOM output rather than internal component instances -- the same `render()`/`screen` API works whether you render one component or an entire page tree.

==> Snapshot Testing
--> `expect(component).toMatchSnapshot()` saves a serialized copy of the rendered output on first run, then fails future test runs if the output changes unexpectedly -- useful for catching accidental UI changes.
--> Caution: snapshots are easy to blindly "update" (`jest --ci=false -u`) without actually reviewing what changed, which defeats their purpose -- best used sparingly, for small/stable pieces of UI, alongside real behavioral assertions.

==> End-to-End (E2E) Testing
--> E2E tests run against a REAL browser and a REAL running app (not a simulated DOM), clicking through actual user flows (login, checkout, navigation) exactly as a user would.
--> Cypress and Playwright are the two most common tools -- both can drive a real Chromium/Firefox/WebKit browser, take screenshots/videos on failure, and test the full stack (frontend + backend + network) together.
```javascript
// Example Playwright test
test("user can log in", async ({ page }) => {
  await page.goto("https://example.com/login");
  await page.fill('input[name="email"]', "user@example.com");
  await page.fill('input[name="password"]', "password123");
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL("https://example.com/dashboard");
});
```
--> Testing Pyramid guideline: many fast unit tests, fewer integration tests, and only a handful of slower E2E tests covering the most critical user flows -- E2E tests are valuable but slow and more brittle, so they shouldn't be the primary testing layer.

# TypeScript with React (brief)

--> Components are typed by typing their props: type Props = { name: string; age?: number }; function Greeting({ name, age }: Props) { ... }
--> useState needs an explicit type when it can't be inferred from the initial value: const [user, setUser] = useState<User | null>(null)
--> Event handler types come from React's built-in types, e.g. (e: React.ChangeEvent<HTMLInputElement>) => void for an onChange handler.
--> children prop is typed as React.ReactNode when a component can accept any renderable content.
--> Benefits: catches typos in prop names, wrong prop types, and missing required props at compile time instead of at runtime.
--> Generic reusable component: type ListProps<T> = { items: T[]; renderItem: (item: T) => React.ReactNode }; function List<T>({ items, renderItem }: ListProps<T>) { return <>{items.map(renderItem)}</> } -- reused for any item type while keeping type safety.
