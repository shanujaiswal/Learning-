/**
 * 04-usereducer-and-forms.jsx
 *
 * DEMONSTRATES:
 *  - A controlled, MULTI-FIELD FORM managed with useReducer instead of
 *    juggling one useState call per field.
 *  - A single dispatch-based update path (UPDATE_FIELD) that scales to
 *    any number of fields without adding more hooks.
 *  - Basic client-side validation state living in the same reducer.
 *
 * Maps to Theory chapters: 04) Forms, 07) Hooks (useReducer)
 *
 * Usage: drop into any Vite/CRA project and render <SignupForm />
 */

import { useReducer } from "react";

const initialState = {
  values: {
    name: "",
    email: "",
    password: "",
  },
  errors: {},
  submitted: false,
};

function formReducer(state, action) {
  switch (action.type) {
    case "UPDATE_FIELD":
      // One generic action handles every input — no per-field useState.
      return {
        ...state,
        values: {
          ...state.values,
          [action.field]: action.value,
        },
      };

    case "SET_ERRORS":
      return { ...state, errors: action.errors };

    case "SUBMIT_SUCCESS":
      return { ...state, submitted: true, errors: {} };

    case "RESET":
      return initialState;

    default:
      throw new Error(`Unknown action type: ${action.type}`);
  }
}

function validate(values) {
  const errors = {};
  if (!values.name.trim()) errors.name = "Name is required";
  if (!/^\S+@\S+\.\S+$/.test(values.email)) errors.email = "Invalid email";
  if (values.password.length < 6)
    errors.password = "Password must be at least 6 characters";
  return errors;
}

export default function SignupForm() {
  const [state, dispatch] = useReducer(formReducer, initialState);
  const { values, errors, submitted } = state;

  const handleChange = (e) => {
    dispatch({
      type: "UPDATE_FIELD",
      field: e.target.name,
      value: e.target.value,
    });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const validationErrors = validate(values);

    if (Object.keys(validationErrors).length > 0) {
      dispatch({ type: "SET_ERRORS", errors: validationErrors });
      return;
    }

    // In a real app: call an API here.
    dispatch({ type: "SUBMIT_SUCCESS" });
  };

  if (submitted) {
    return (
      <div style={{ fontFamily: "sans-serif", padding: 16 }}>
        <p>Thanks, {values.name}! Signup successful.</p>
        <button onClick={() => dispatch({ type: "RESET" })}>
          Start over
        </button>
      </div>
    );
  }

  return (
    <form
      onSubmit={handleSubmit}
      style={{ fontFamily: "sans-serif", padding: 16, maxWidth: 320 }}
    >
      <h2>Signup (useReducer form)</h2>

      <label>
        Name
        <input name="name" value={values.name} onChange={handleChange} />
      </label>
      {errors.name && <p style={{ color: "red" }}>{errors.name}</p>}

      <label>
        Email
        <input name="email" value={values.email} onChange={handleChange} />
      </label>
      {errors.email && <p style={{ color: "red" }}>{errors.email}</p>}

      <label>
        Password
        <input
          type="password"
          name="password"
          value={values.password}
          onChange={handleChange}
        />
      </label>
      {errors.password && <p style={{ color: "red" }}>{errors.password}</p>}

      <button type="submit">Sign up</button>
    </form>
  );
}
