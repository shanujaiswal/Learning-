# JavaScript JSON --- Used for database

--> JSON is a format for storing and transporting data.
--> Often used when data is sent from a server to a web page.
--> JSON stands for JavaScript Object Notation
--> JSON is a lightweight data interchange format
--> JSON is language independent \*
--> JSON is "self-describing" and easy to understand
--> The JSON syntax is derived from JavaScript object notation syntax, but the JSON format is text only. Code for reading and generating JSON data can be written in any programming language.

==> JSON Example
This JSON syntax defines an employees object: an array of 3 employee records (objects):

{
"employees":[
{"firstName":"John", "lastName":"Doe"},
{"firstName":"Anna", "lastName":"Smith"},
{"firstName":"Peter", "lastName":"Jones"}
]
}

==> The JSON Format Evaluates to JavaScript Objects
--> The JSON format is syntactically identical to the code for creating JavaScript objects.

--> Because of this similarity, a JavaScript program can easily convert JSON data into native JavaScript objects.

==> JSON Syntax Rules
--> Data is in name/value pairs
--> Data is separated by commas
--> Curly braces hold objects
--> Square brackets hold arrays

==> JSON Data - A Name and a Value
--> JSON data is written as name/value pairs, just like JavaScript object properties.
--> A name/value pair consists of a field name (in double quotes), followed by a colon, followed by a value:

--> JSON names require double quotes. JavaScript names do not.

==> JSON Objects
--> JSON objects are written inside curly braces.
--> Just like in JavaScript, objects can contain multiple name/value pairs:
{"firstName":"John", "lastName":"Doe"}
==> JSON Arrays
--> JSON arrays are written inside square brackets.

--> Just like in JavaScript, an array can contain objects:
"employees":[
{"firstName":"John", "lastName":"Doe"},
{"firstName":"Anna", "lastName":"Smith"},
{"firstName":"Peter", "lastName":"Jones"}
]
In the example above, the object "employees" is an array. It contains three objects.

--> Each object is a record of a person (with a first name and a last name).

==> Converting a JSON Text to a JavaScript Object
--> A common use of JSON is to read data from a web server, and display the data in a web page.
--> For simplicity, this can be demonstrated using a string as input.
--> First, create a JavaScript string containing JSON syntax:

let text = '{ "employees" : [' +
'{ "firstName":"John" , "lastName":"Doe" },' +
'{ "firstName":"Anna" , "lastName":"Smith" },' +
'{ "firstName":"Peter" , "lastName":"Jones" } ]}';

==> Then, use the JavaScript built-in function JSON.parse() to convert the string into a JavaScript object:

const obj = JSON.parse(text);
Finally, use the new JavaScript object in your page:

Example

<p id="demo"></p>

<script>
document.getElementById("demo").innerHTML =
obj.employees[1].firstName + " " + obj.employees[1].lastName;
</script>

# JSON in React

--> JSON.stringify(data) is used when sending JS data to a server, e.g. in a fetch/axios request body: body: JSON.stringify({ name: "John" })
--> Response bodies from fetch/axios come back as JSON text and need to be parsed before use: fetch(url).then(res => res.json()) (fetch) -- axios does this parsing automatically, giving you response.data already as a JS object.
--> Component state and props are plain JavaScript objects/arrays, not JSON -- they only become JSON when explicitly serialized (JSON.stringify) for transport, e.g. to localStorage or an API call.
