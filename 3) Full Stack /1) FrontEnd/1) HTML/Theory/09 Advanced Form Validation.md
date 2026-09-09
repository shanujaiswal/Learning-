# Built-In HTML5 Validation

--> Modern browsers validate forms natively before submission, without any JavaScript, based purely on input attributes -- the first line of defense before server-side validation (which is always still required, since client-side checks can be bypassed).

# Validation Attributes

--> `required` -- field must be filled before the form submits.
--> `pattern` -- validates the value against a regular expression.
--> `min` / `max` -- numeric or date range limits. `minlength` / `maxlength` -- text length limits.
--> `step` -- restricts numeric/date input to specific increments.

```html
<input type="text" required minlength="3" maxlength="20" pattern="[A-Za-z0-9_]+"
       title="3-20 characters, letters/numbers/underscore only">

<input type="number" min="1" max="100" step="1">

<input type="email" required>   <!-- browser validates email format automatically -->
```

# HTML5 Input Types

--> Using the correct `type` gives free validation AND a better UX (mobile devices show a specialized keyboard for each): `email`, `url`, `tel`, `number`, `date`, `time`, `color`, `range`, `search`.

```html
<input type="date">
<input type="range" min="0" max="10" step="1">
<input type="color">
```

# Styling Validation States with CSS

--> `:valid` / `:invalid` -- pseudo-classes matching whether a field currently passes its validation constraints.
--> `:required` / `:optional` -- match based on the `required` attribute.
--> `:user-invalid` (newer) -- like `:invalid`, but only after the user has actually interacted with the field, avoiding showing an error before they've had a chance to type anything.

```css
input:invalid {
  border-color: red;
}
input:valid {
  border-color: green;
}
```

# The Constraint Validation API (JavaScript)

--> For validation logic HTML attributes can't express (e.g. "password and confirm-password must match"), JavaScript's Constraint Validation API integrates with the browser's native validation UI instead of building a separate custom error system.

```javascript
const confirmField = document.getElementById("confirm-password");

confirmField.addEventListener("input", () => {
  if (confirmField.value !== passwordField.value) {
    confirmField.setCustomValidity("Passwords do not match");
  } else {
    confirmField.setCustomValidity("");   // Clearing means the field is valid
  }
});
```

--> `form.checkValidity()` -- programmatically checks if an entire form currently passes validation, without submitting it.
--> `element.reportValidity()` -- triggers the browser's native validation UI (the little popup bubble) for a specific field on demand.

# Why Client-Side Validation Is Never Enough

--> Client-side validation (HTML attributes or JS) is purely a UX convenience -- it can be trivially bypassed (disabling JS, using a tool like curl/Postman to hit the API directly).
--> The server MUST independently re-validate every field -- trusting client-side validation alone is a direct path to data integrity issues and security vulnerabilities (e.g. the SQL Injection risks covered in the Database notes).
