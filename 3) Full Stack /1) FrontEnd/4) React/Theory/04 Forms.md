# React Forms

--> Just like in HTML, React uses forms to allow users to interact with the web page.
==> Adding Forms in React
--> Add a form with React like any other element:
==> Handling Forms
--> Handling forms is about how handle the data when it changes value or gets submitted.
--> In HTML, form data is usually handled by the DOM.In React, form data is usually handled by the components.
--> When the data is handled by the components, all the data is stored in the component state.Can control changes by adding event handlers in the onChange attribute.
--> use the useState Hook to keep track of each inputs value and provide a "single source of truth" for the entire application.
==> Submitting Forms
--> Can control the submit action by adding an event handler in the onSubmit attribute for the <form>:
==> Multiple Input Fields
--> You can control the values of more than one input field by adding a name attribute to each element.
--> To access the fields in the event handler use the event.target.name and event.target.value syntax.
--> To update the state, use square brackets [bracket notation] around the property name.
==> Textarea
--> The textarea element in React is slightly different from ordinary HTML.
--> In HTML the value of a textarea was the text between the start tag <textarea> and the end tag </textarea> In React the value of a textarea is placed in a value attribute. We'll use the useState Hook to manage the value of the textarea:
==> Select
--> A drop down list, or a select box, in React is also a bit different from HTML.
--> In HTML, the selected value in the drop down list was defined with the selected attribute.In React, the selected value is defined with a value attribute on the select tag:

# Controlled vs Uncontrolled Components

--> Controlled component --> the input's value is driven by React state (value={state} + onChange={setState}) -- React is the "single source of truth", and every keystroke goes through a re-render.
--> Uncontrolled component --> the input manages its own value internally in the DOM, and React reads it only when needed (e.g. on submit) via a ref -- const inputRef = useRef(); inputRef.current.value.
--> defaultValue (instead of value) sets the initial value of an uncontrolled input without taking over control of it on every render.
--> Controlled is preferred for validation, conditional disabling, or formatting as-you-type. Uncontrolled is simpler/faster for large forms where re-rendering on every keystroke isn't needed.
--> Most React forms use controlled components; uncontrolled is common when integrating with non-React code or file inputs (<input type="file"> can only be uncontrolled since its value is read-only for security).
--> For non-trivial forms, real-world projects usually reach for a library instead of hand-rolling useState for every field -- React Hook Form (uncontrolled-first, less re-rendering) and Formik (controlled-first) are the common choices.
--> Validation is often handled with a schema library alongside these -- e.g. Zod or Yup -- instead of writing manual if/else validation logic.
