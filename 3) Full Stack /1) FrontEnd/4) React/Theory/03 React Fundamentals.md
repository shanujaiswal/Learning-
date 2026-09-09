# React Render HTML

--> React renders HTML to the web page by using a function called createRoot() and its method render()
--> In React, HTML is rendered using JSX (JavaScript XML), which allows you to write HTML-like syntax inside JavaScript. React then converts JSX into standard JavaScript using React.createElement()
--> React requires elements to be wrapped in a single parent element, often using a <div> or <> (Fragment).

# The createRoot Function

--> The createRoot() function takes one argument, an HTML element.
--> The purpose of the function is to define the HTML element where a React component should be displayed.

# The render Method

--> The render() method is then called to define the React component that should be rendered.

# React JSX

--> JSX stands for JavaScript XML.
--> JSX allows us to write HTML in React.
--> JSX makes it easier to write and add HTML in React.
--> JSX allows us to write HTML elements in JavaScript and place them in the DOM without any createElement() and/or appendChild() methods.
--> can write expressions inside curly braces { }

==> To Be Known in React
--> {} --> To write javascript in jsx
--> {{}} --> to write object

# If writing code just after return there is no need for bracket , if we are writing code in next line then there is a bracket needed.

==> Expressions in JSX
--> The expression can be a React variable, or property, or any other valid JavaScript expression. JSX will execute the expression and return the result:
--> To write HTML on multiple lines, put the HTML inside parentheses():
--> if write two paragraphs, you must put them inside a parent element, like a div element. Alternatively, you can use a "fragment"(A fragment looks like an empty HTML tag: <></>) to wrap multiple lines. This will prevent unnecessarily adding extra nodes to the DOM.
--> JSX will throw an error if the HTML is not correct, or if the HTML misses a parent element.
==> Elements Must be Closed
--> JSX follows XML rules, and therefore HTML elements must be properly closed
==> Attribute class => className
--> The class attribute is a much used attribute in HTML, but since JSX is rendered as JavaScript, and the class keyword is a reserved word in JavaScript, you are not allowed to use it in JSX. Use attribute className instead.
==> Conditions - if statements
--> React supports if statements, but not inside JSX.
--> To be able to use conditional statements in JSX, should put the if statements outside of the JSX, or use a ternary expression instead

# React Components

--> Components are like functions that return HTML elements.
-->Components are independent and reusable bits of code. They serve the same purpose as JavaScript functions, but work in isolation and return HTML.
--> When creating a React component, the component's name MUST start with an upper case letter.
--> A Function component also returns HTML, and behaves much the same way as a Class component, but Function components can be written using much less code, are easier to understand, and will be preferred in this tutorial.
==> Rendering a Component
--> rendering a component means displaying it inside the DOM. You can render a component using ReactDOM in a regular project or simply return it inside another component.

# React Props

--> Props (short for "properties") in React are used to pass data from a parent component to a child component as read-only values.
--> Props are like function arguments, and you send them into the component as attributes.
--> React Props are read-only! You will get an error if you try to change their value.

==> The children Prop
--> children is a special prop that holds whatever is nested between a component's opening and closing tags.
--> <Card><p>Some content</p></Card> --> inside Card, props.children is that <p>Some content</p>.

# React Events

--> Just like HTML DOM events, React can perform actions based on user events.
--> React has the same events as HTML: click, change, mouseover etc.
--> React events are written in camelCase syntax:( onClick instead of onclick. )
--> React event handlers are written inside curly braces:(onClick={shoot} instead of onclick="shoot()".)

==> Adding Events
--> Handling user interactions using event listeners, such as clicks, form submissions, key presses, etc. React uses synthetic events, which are wrappers around native browser events, ensuring consistency across different browsers.
==> Passing Arguments
--> To pass an argument to an event handler, use an arrow function.
==> React Event Object
--> Event handlers have access to the React event that triggered the function.

# React Conditional Rendering

--> In React, can conditionally render components.
-->There are several ways to do this
==> if Statement
--> Can use the "if" JavaScript operator to decide which component to render.
==> Logical && Operator
--> Another way to conditionally render a React component is by using the && operator.
==> Ternary Operator
--> Another way to conditionally render elements is by using a ternary operator.
--> condition ? true : false

# React Lists

--> will render lists with some type of loop.
--> The JavaScript map() array method is generally the preferred method.

# Keys

--> Keys allow React to keep track of elements. This way, if an item is updated or removed, only that item will be re-rendered instead of the entire list.
--> Keys need to be unique to each sibling. But they can be duplicated globally.
--> Avoid using the array index as a key when the list can be reordered, filtered, or have items inserted/deleted -- since the index shifts, React can match the wrong key to the wrong item, causing stale state/inputs or unnecessary re-renders of the wrong DOM nodes. Prefer a stable id from the data instead.

# Deep Dive -- What JSX Actually Compiles To

--> JSX is not understood by browsers at all -- it's syntactic sugar that a build tool (Babel/SWC, referenced in the Iterators/Module Systems file's bundler section) transforms into plain `React.createElement()` calls before the code ever reaches the browser.

```jsx
// What you write:
const element = <h1 className="title">Hello, {name}</h1>;

// What it compiles to (conceptually):
const element = React.createElement("h1", { className: "title" }, "Hello, ", name);
```

--> `React.createElement()` doesn't create a real DOM node -- it returns a plain JavaScript object (a "React element") describing what should eventually be rendered: `{ type: "h1", props: { className: "title", children: [...] } }`. This distinction matters because it's exactly why JSX expressions can be stored in variables, passed as props, and returned conditionally -- they're just plain objects, not live DOM.

# Deep Dive -- The Virtual DOM and Reconciliation

--> On every re-render, React builds a new tree of these lightweight React-element objects (the "Virtual DOM") rather than immediately touching the real, expensive-to-manipulate browser DOM. React then compares ("diffs") this new tree against the previous one -- a process called Reconciliation -- and calculates the MINIMAL set of actual DOM changes needed to bring the real DOM in sync, applying only those specific changes rather than re-rendering everything from scratch.
--> **Why keys matter for this process** -- when React diffs a list, `key` is what lets it match up elements between the old and new tree by IDENTITY rather than by position -- without stable keys (or with array-index keys on a reorderable list, as noted above), React can misidentify which specific item changed, leading to it discarding and recreating DOM nodes (and their internal state, like an input's current text) unnecessarily, rather than just moving/updating the correct existing one.

# Deep Dive -- Controlled vs Uncontrolled Form Inputs

--> A **controlled** input's value is driven entirely by React state -- the input's displayed value always equals `props.value`/state, and every keystroke goes through an `onChange` handler that updates that state, making React the single source of truth.

```jsx
function ControlledInput() {
  const [value, setValue] = useState("");
  return <input value={value} onChange={(e) => setValue(e.target.value)} />;
}
```

--> An **uncontrolled** input manages its own internal DOM state, and React only reads its current value on demand (typically via a `ref`, covered in the Hooks file) rather than tracking every keystroke in state.

```jsx
function UncontrolledInput() {
  const inputRef = useRef(null);
  const handleSubmit = () => console.log(inputRef.current.value);   // Only read when actually needed
  return <input ref={inputRef} />;
}
```

--> Controlled inputs are the more common, more "React way" default -- they enable instant validation, conditional formatting, and keeping multiple inputs in sync with each other as the user types. Uncontrolled inputs are lighter-weight and occasionally preferred for very simple forms or when integrating with non-React code that expects to manage the DOM element itself directly.
