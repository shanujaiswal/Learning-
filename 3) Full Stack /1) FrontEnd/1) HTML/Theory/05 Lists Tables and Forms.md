# Lists

--> `<ul>` — unordered list (bullet points).
--> `<ol>` — ordered list (numbered); supports `type` (1, A, a, I, i) and `start` attributes.
--> `<li>` — a single list item.
--> Lists can be nested — a `<ul>`/`<ol>` placed inside an `<li>`.

# Tables

--> `<table>` — defines a table.
--> `<tr>` — table row.
--> `<th>` — table header cell (bold, centered by default).
--> `<td>` — table data cell.
--> `<thead>`, `<tbody>`, `<tfoot>` — group header, body, and footer rows semantically.
--> `colspan` — makes a cell span multiple columns.
--> `rowspan` — makes a cell span multiple rows.
--> `<caption>` — a title/caption for the table.
--> `scope="col"` / `scope="row"` on `<th>` — tells screen readers whether the header applies to a column or a row, improving table accessibility.

# Forms Overview

--> `<form>` — a container that collects user input and submits it (via `action` and `method` attributes).
--> `action` — the URL the form data is sent to.
--> `method` — `GET` (data in URL) or `POST` (data in request body).
--> Security: never use `GET` for sensitive data (passwords, tokens) — it ends up visible in the URL, browser history, and server logs; use `POST` instead.
--> `enctype` — controls how form data is encoded before being sent, only relevant for `method="POST"`:
    - `application/x-www-form-urlencoded` — the default; encodes data as key=value pairs.
    - `multipart/form-data` — REQUIRED whenever the form includes a file upload (`<input type="file">`); allows binary data to be sent alongside regular fields.
    - `text/plain` — sends data with minimal encoding (rarely used, mainly for debugging).

```html
<form action="/upload" method="POST" enctype="multipart/form-data">
  <input type="file" name="resume">
  <button type="submit">Upload</button>
</form>
```

# Input Elements

--> `<input>` — used to collect data; empty element.
--> `type` — type of input to be given: `text`, `email`, `password`, `number`, `date`, `checkbox`, `radio`, `file`, `submit`, etc.
--> `placeholder` — gives a hint about what kind of information to enter into the input.
--> `required` — prevents a user from submitting the form when required information is missing.
--> `name` — identifies the input's data when the form is submitted.
--> `value` — the default/current value of the input.
--> `disabled` — makes the input non-editable and excludes it from submission.

# Other Form Elements

--> `<label for="id">` — a caption attached to a specific input (clicking the label focuses the input); improves accessibility.
--> `<textarea>` — multi-line text input.
--> `<select>` + `<option>` — a dropdown list.
--> `<button>` — creates a clickable button; `type="submit"`, `type="reset"`, or `type="button"`.
--> Radio buttons (`type="radio"`) — used where we want only one answer out of multiple options (grouped by same `name`).
--> Checkboxes (`type="checkbox"`) — allow multiple selections.
--> `<fieldset>` — groups related form controls together, usually with a border.
--> `<legend>` — acts as a caption for the content inside a `<fieldset>`.

# Form Validation

--> Built-in browser validation via attributes: `required`, `minlength`, `maxlength`, `min`, `max`, `pattern`.
--> `pattern` — a regular expression the input value must match.
--> `novalidate` (on `<form>`) — disables the browser's automatic validation, useful when validation is instead handled manually via JavaScript.
--> `autocomplete="on" | "off"` — hints whether the browser should offer to auto-fill a field from previously entered data.

# Other Useful Form Elements/Attributes

--> `<datalist>` + `list="id"` on an `<input>` — provides an editable dropdown of suggestions without locking the value to the list (unlike `<select>`).
--> `<output>` — displays the result of a calculation, often linked to inputs via its `for` attribute.
--> `accept="image/*"` (on `type="file"`) — restricts which file types the file picker allows selecting.
--> `multiple` (on `type="file"` or `<select>`) — allows selecting more than one file/option.
--> `step` (on `type="number"`/`type="range"`) — sets the increment allowed between valid values.

# inputmode and Newer Input Types

--> `inputmode` — hints to mobile browsers which VIRTUAL KEYBOARD layout to show, independent of validation behavior (unlike `type`).
    - `inputmode="numeric"` — shows a number pad (for values that aren't necessarily a JS number, e.g. a PIN with no arithmetic meaning).
    - `inputmode="decimal"` — number pad with a decimal point.
    - `inputmode="tel"` — phone-style keypad.
    - `inputmode="email"` — keyboard with `@` and `.` easily accessible.
    - `inputmode="url"` — keyboard optimized for typing URLs.
    - `inputmode="search"` — keyboard with a "Search" labeled enter key.
    - `inputmode="none"` — suppresses the virtual keyboard entirely (useful for custom on-screen keyboards).

```html
<input type="text" inputmode="numeric" pattern="[0-9]*" placeholder="Enter PIN">
```

--> Newer/less common `<input>` types beyond the basics:
    - `type="color"` — a native color-picker swatch.
    - `type="range"` — a slider control (use with `min`/`max`/`step`).
    - `type="search"` — a text input styled/behaving like a search box (adds a clear "x" button in most browsers).
    - `type="month"` / `type="week"` — pick a month or week without a full date.
    - `type="datetime-local"` — combined date + time picker (without timezone).
    - `type="hidden"` — an invisible field still submitted with the form (used to pass extra data the user shouldn't edit).
