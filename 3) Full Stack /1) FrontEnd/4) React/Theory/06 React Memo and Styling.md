# React Memo

--> React.memo() is a higher-order component that skips re-rendering a component when its props haven't changed.
--> const MyComponent = React.memo(function MyComponent(props) { ... })
--> Only useful for components that re-render often with the same props -- wrapping everything in memo has its own overhead.
--> By default React.memo does a shallow comparison of props; a custom comparison function can be passed as a second argument.
--> Doesn't help if a new object/array/function is created on every render and passed as a prop -- pair it with useMemo/useCallback on the parent so the props are actually referentially stable.

# useMemo Hook

--> Caches (memoizes) the result of an expensive calculation between renders.
--> const value = useMemo(() => computeExpensiveValue(a, b), [a, b])
--> Only recalculates when one of the dependencies in the array changes.
--> Used for performance optimization -- avoid overusing it on cheap calculations, the memoization itself has a cost.

# useCallback Hook

--> Caches a function definition between renders instead of recreating it on every render.
--> const handleClick = useCallback(() => { doSomething(a, b) }, [a, b])
--> Mainly useful when passing callbacks to memoized child components (React.memo) or as a dependency to other hooks (useEffect), so the child doesn't re-render / effect doesn't re-run unnecessarily.
--> useCallback(fn, deps) is equivalent to useMemo(() => fn, deps).

# Styling React Using CSS

--> There are many ways to style React with CSS,will take a closer look at three common ways:

Inline styling
CSS stylesheets
CSS Modules

==> Inline Styling
--> To style an element with the inline style attribute, the value must be a JavaScript object:
--> n JSX, JavaScript expressions are written inside curly braces, and since JavaScript objects also use curly braces, the styling is written inside two sets of curly braces {{}}.

==> camelCased Property Names
--> Since the inline CSS is written in a JavaScript object, properties with hyphen separators, like background-color, must be written with camel case syntax:
--> Use backgroundColor instead of background-color:

==> CSS Stylesheets
--> Write a normal .css file with regular CSS syntax, then import it into the component file: import "./App.css";
--> Apply classes with className, same as any other JSX attribute: <h1 className="title">Hello</h1>
--> Simple and familiar, but class names are global -- two components using the same class name can clash/override each other.

==> CSS Modules
--> A CSS file named with the .module.css extension (e.g. Card.module.css) whose class names are scoped locally to the component that imports it.
--> Import it as an object and reference classes as properties: import styles from "./Card.module.css"; <div className={styles.card}>...</div>
--> Build tooling (Vite/CRA/webpack) rewrites the class names to unique hashed names under the hood, so styles from one CSS Module never leak into another component.

# Styling React Using Sass(Read it sepreatly from w3school )

--> Sass is a CSS pre-processor.
--> Sass files are executed on the server and sends CSS to the browser
--> Install Sass by running this command in your terminal:
npm i sass
==> Create a Sass file
--> Create a Sass file the same way as you create CSS files, but Sass files have the file extension .scss
--> In Sass files you can use variables and other Sass functions:
