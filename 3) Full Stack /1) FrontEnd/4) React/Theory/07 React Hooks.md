# React Hooks

--> Hooks were added to React in version 16.8.
--> Hooks allow function components to have access to state and other React features.
--> Because of this, class components are generally no longer needed.
--> Hooks generally replace class components, there are no plans to remove classes from React.
--> Must import Hooks from react.
--> State generally refers to application data or properties that need to be tracked.
--> using the **useState** Hook to keep track of the application state.

# Hook Rules

--> There are 3 rules for hooks:

1. Hooks can only be called inside React function components.
2. Hooks can only be called at the top level of a component.
3. Hooks cannot be conditional

==> Custom Hooks
If have stateful logic that needs to be reused in several components, can build our own custom Hooks.

# React useState Hook

--> Allows us to track state in a function component.
--> Generally refers to data or properties that need to be tracking in an application

==> Import useState
--> To use the useState Hook, we first need to import it into our component.At the top of our component, import the useState Hook.
import { useState } from "react";

==> Initialize useState
--> Initialize our state by calling useState in our function component.
--> useState accepts an initial state and returns two values:

1. The current state.
2. A function that updates the state.

==> Initialize state at the top of the function component.
import { useState } from "react";
function FavoriteColor() {
const [color, setColor] = useState("");
}
--> As we are destructuring the returned values from useState.
--> The first value, color, is our current state.
--> The second value, setColor, is the function that is used to update our state.

--> These names are variables that can be named anything would like.
--> Lastly, we set the initial state to an empty string: useState("")

==> Update State
--> To update our state, we use our state updater function.
--> We should never directly update state. Ex: color = "red" is not allowed.
--> const [color, setColor] = useState("red"); // Initializes state with "red", setColor updates it.

==> What Can State Hold
--> The useState Hook can be used to keep track of strings, numbers, booleans, arrays, objects, and any combination of these!
--> We could create multiple state Hooks to track individual values
--> Or, we can just use one state and include an object instead!

==> Updating Objects and Arrays in State
--> When state is updated, the entire state gets overwritten.
--> What if we only want to update the color of our car?(refer to example)
--> If we only called setCar({color: "blue"}), this would remove the brand, model, and year from our state.
--> We can use the JavaScript spread operator to help us.

# React useEffect Hooks

--> The useEffect Hook allows you to perform side effects in your components.
--> Some examples of side effects are: fetching data, directly updating the DOM, and timers.
--> useEffect accepts two arguments. The second argument is optional.
--> useEffect(<function>, <dependency>)
--> useEffect runs on every render. That means that when the count changes, a render happens, which then triggers another effect.always include the second parameter which accepts an array. We can optionally pass dependencies to useEffect in this array.

--> Clarification (restating the above cleanly): by default, with no dependency array passed, `useEffect` runs after every single render -- including renders caused by the effect's own state updates, which can lead to a render-then-effect-then-render loop. Passing a dependency array changes this: React compares each value to its previous render's value and only re-runs the effect when at least one has changed. This is why the dependency array should always be included deliberately (`[]` for "once on mount", or `[dep1, dep2]` for "on mount and whenever these values change") rather than omitted.

1. No dependency passed:

useEffect(() => {
//Runs on every render
});

2. An empty array:

useEffect(() => {
//Runs only on the first render
}, []);

3. Props or state values:

useEffect(() => {
//Runs on the first render
//And any time any dependency value changes
}, [prop, state]);

--> If there are multiple dependencies, they should be included in the useEffect dependency array.

==> Effect Cleanup
--> Some effects require cleanup to reduce memory leaks.
--> Timeouts, subscriptions, event listeners, and other effects that are no longer needed should be disposed.
--> Do this by including a return function at the end of the useEffect Hook.

# React useContext Hook

--> React Context is a way to manage state globally.
--> It can be used together with the useState Hook to share state between deeply nested components more easily than with useState alone.

==> The Problem
--> State should be held by the highest parent component in the stack that requires access to the state.
--> If, we have many nested components. The component at the top and bottom of the stack need access to the state.To do this without Context, we will need to pass the state as "props" through each nested component. This is called "prop drilling".

==> The Solution
--> The solution is to create context.
==> Create Context
--> To create context, you must Import createContext and initialize it:
import { createContext } from "react";
const UserContext = createContext();

==> Context Provider
--> Wrap the components that need access to the context with the Context.Provider and pass it the state value.
<UserContext.Provider value={user}>
<Component1 />
</UserContext.Provider>
--> Every component nested inside the Provider will have access to the context, no matter how deep it is.

==> Use the useContext Hook
--> Any child component (at any level of nesting) can access the context value with the useContext Hook.
import { useContext } from "react";
const user = useContext(UserContext);
--> No more prop drilling -- the value is read directly from context instead of being passed down through every intermediate component.

# React useRef Hook

--> The useRef Hook allows you to persist values between renders.
--> It can be used to store a mutable value that does not cause a re-render when updated.
--> It can be used to access a DOM element directly.

==> Does Not Cause Re-renders
--> If we tried to count how many times our application renders using the useState Hook, we would be caught in an infinite loop since this Hook itself causes a re-render.
--> To avoid this, we can use the useRef Hook.
--> useRef() only returns one item. It returns an Object called current.
--> When we initialize useRef we set the initial value: useRef(0).
==> Accessing DOM Elements
--> In general, we want to let React handle all DOM manipulation.
--> But there are some instances where useRef can be used without causing issues.
--> In React, we can add a ref attribute to an element to access it directly in the DOM.
==> Tracking State Changes
--> The useRef Hook can also be used to keep track of previous state values.
--> This is because we are able to persist useRef values between renders.

# React useReducer Hook

--> The useReducer Hook is similar to the useState Hook.
--> It allows for custom state logic.
--> If you find yourself keeping track of multiple pieces of state that rely on complex logic, useReducer may be useful.
==> Syntax
--> The useReducer Hook accepts two arguments.
--> useReducer(<reducer>, <initialState>)
--> The reducer function contains your custom state logic and the initialStatecan be a simple value but generally will contain an object.
--> The useReducer Hook returns the current stateand a dispatchmethod.

# React Custom Hooks(Read through video)

--> Hooks are reusable functions.
--> When you have component logic that needs to be used by multiple components, we can extract that logic to a custom Hook.
--> Custom Hooks start with "use". Example: useFetch.
\\ ==> Build a Hook
--> Will use the JSONPlaceholder service to fetch fake data. This service is great for testing applications when there is no existing data.

==> useFetch Custom Hook Example
import { useState, useEffect } from "react";

function useFetch(url) {
const [data, setData] = useState(null);
const [loading, setLoading] = useState(true);

useEffect(() => {
fetch(url)
.then((res) => res.json())
.then((data) => {
setData(data);
setLoading(false);
});
}, [url]);

return { data, loading };
}

--> Any component can now reuse this logic: const { data, loading } = useFetch("https://jsonplaceholder.typicode.com/todos")
--> Extracting logic into a custom Hook keeps components clean and avoids duplicating the same fetch/state logic everywhere.

# React useLayoutEffect Hook

--> Similar to useEffect, but fires synchronously after all DOM mutations, before the browser paints the screen.
--> Use it when need to read layout (e.g. size/position of a DOM node) and synchronously re-render before the user sees a flicker.
--> Rarely needed -- useEffect is preferred by default since useLayoutEffect can block visual updates.

# React useId Hook

--> Generates a unique, stable id that is consistent across server and client renders.
--> Useful for accessibility attributes (linking a <label> to an <input> via htmlFor/id) when rendering lists of form fields.
--> Should not be used as a key in a list or for generating keys for CSS.

# React useTransition Hook (React 18+)

--> Lets you mark a state update as a low-priority "transition" so it doesn't block urgent updates (like typing) from rendering immediately.
--> const [isPending, startTransition] = useTransition();
--> startTransition(() => { setState(newValue) }) --> the update inside runs in the background; isPending is true while it's still rendering.
--> Useful for expensive re-renders (e.g. filtering a large list) triggered by a fast-changing input, so the input stays responsive.

# React useDeferredValue Hook (React 18+)

--> Returns a deferred (lagging) copy of a value that updates after more urgent renders have finished.
--> const deferredQuery = useDeferredValue(query); -- pass deferredQuery to the expensive part of the UI instead of query directly.
--> Similar goal to useTransition, but useful when you don't control the state setter itself (e.g. value comes from a parent/prop).

# React forwardRef + useImperativeHandle

--> forwardRef lets a parent pass a ref through a component down to one of its child DOM nodes: const Input = forwardRef((props, ref) => <input ref={ref} {...props} />)
--> useImperativeHandle customizes what the parent's ref actually receives, instead of exposing the raw DOM node -- useful to expose only specific methods (e.g. focus(), reset()) from a component.

# React useSyncExternalStore Hook

--> Lets a component subscribe to an external (non-React) data store -- e.g. a third-party state library or browser API -- and stay in sync without tearing during concurrent rendering.
--> const state = useSyncExternalStore(store.subscribe, store.getSnapshot) -- mostly used inside library code (Redux, Zustand internals) rather than directly in app code.

# React 18 Strict Mode -- Double-Invoked Effects

--> In development, React 18 Strict Mode intentionally mounts, unmounts, and re-mounts components once extra (double-invoking effects) to help surface missing/incorrect cleanup functions.
--> This only happens in development, not production -- if an effect breaks under this double-invoke, it's usually missing proper cleanup in its return function.

# The Stale Closure Gotcha (useEffect / useCallback / setInterval)

--> A "stale closure" happens when a function created during one render captures OLD values of state/props from that render, and keeps using those old values even after state has since changed -- because the function was never re-created with the new values.
--> This is one of the most common React bugs, especially with `useEffect`, `setInterval`, and event listeners set up once with an empty dependency array `[]`.

```javascript
function Counter() {
  const [count, setCount] = useState(0);

  useEffect(() => {
    const id = setInterval(() => {
      console.log(count);   // Always logs 0 -- this closure "freezes" count at its value when the effect first ran
      setCount(count + 1);  // Always sets to 1, then 1, then 1... never actually increments past 1
    }, 1000);
    return () => clearInterval(id);
  }, []);   // Empty array -- effect (and its closure over `count`) only runs ONCE, on mount

  return <p>{count}</p>;
}
```

--> Fix 1 -- add the missing dependency so the effect re-runs with a fresh closure whenever `count` changes:
```javascript
useEffect(() => {
  const id = setInterval(() => setCount(count + 1), 1000);
  return () => clearInterval(id);
}, [count]);   // Re-creates the interval (and closure) every time count changes
```

--> Fix 2 (usually better) -- use the FUNCTIONAL update form of the state setter, which always receives the latest state regardless of what the closure captured:
```javascript
useEffect(() => {
  const id = setInterval(() => setCount(prev => prev + 1), 1000);
  return () => clearInterval(id);
}, []);   // Safe to keep the empty array -- setCount(prev => ...) never relies on the stale `count` variable
```

--> Same principle applies to `useCallback`/`useMemo`: if a memoized function closes over a state variable but that variable is missing from the dependency array, the memoized function keeps using its old value forever -- always trust the ESLint `react-hooks/exhaustive-deps` rule rather than omitting dependencies to "fix" unwanted re-renders.

# React 19 Features (Brief)

--> React 19 introduced several new APIs aimed at simplifying data mutations and async UI state, building on the Suspense/transitions work from React 18.

==> The use() Hook
--> A new hook that can read the value of a Promise or Context directly during render, and can be called conditionally (unlike other hooks) -- `const data = use(fetchDataPromise)`.
--> Suspends the component (shows the nearest `<Suspense>` fallback) while the promise is pending, and throws to the nearest Error Boundary if it rejects.

==> useActionState
--> Manages state for a form/async action in one hook -- given an action function and initial state, it returns the current state, a wrapped action to call, and a pending flag.
```javascript
const [state, formAction, isPending] = useActionState(updateNameAction, { name: "" });
```

==> useOptimistic
--> Lets you show an "optimistic" (assumed-successful) UI update immediately while an async action is still in flight, then automatically reconciles with the real result once it resolves (or reverts on error).
```javascript
const [optimisticName, setOptimisticName] = useOptimistic(name);
// setOptimisticName("New Name") shows the change instantly, before the server confirms it
```

==> Form Actions
--> `<form action={someAsyncFunction}>` -- forms can now take a function directly (server or client) as their `action`, letting React manage pending/error states around the submission automatically, reducing manual `onSubmit` + `useState` boilerplate for simple form-to-server flows.

--> Note: these are newer APIs (React 19, released after the React 18-era content in the rest of this file) -- most existing codebases still primarily use the useState/useEffect/useReducer patterns described above.
