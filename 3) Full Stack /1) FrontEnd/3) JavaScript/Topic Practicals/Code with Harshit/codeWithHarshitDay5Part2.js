const a = new Map();
a.set("firstname", "Shanu");
a.set("age", 7);
a.set(1, "age");
a.set([1, 2, 3], "onetwothree");
a.set({ 1: "one" }, "onetwothree");
console.log(a);
console.log(a.get("firstname")); // Retrieve and log the value associated with the key 'firstname' from the Map
console.log(a.keys()); // Retrieve and log the value associated with the key 'firstname' from the Map
for (let key of a.keys()) {
  console.log(key, typeof key);
}
for (let key of a) {
  console.log(key);
}

const b = new Map([
  ["firstname", "Shanu"],
  ["age", 7],
]);
console.log(b);

// How to use an object as a key in a JavaScript Map, storing and retrieving associated data efficiently.
const c1 = {
  id: 1,
  firstname: "Shanu",
};

const c2 = {
  id: 1,
  firstname: "Shanu",
};

const d = new Map();
d.set(c1, { age: 24, gender: "male" });
d.set(c2, { age: 38, gender: "Female" });
console.log(d);
console.log(d.get(c1).gender);
console.log(d.get(c2).gender);

//Clone using object.assign
const e = {
  key1: "Value1",
  key2: "Value2",
};

const f = e; // Assign 'e' to 'f', meaning 'f' now holds a reference to 'e' (not a new copy)
const g = { ...e }; // Create a shallow copy of 'e' using the spread operator
const h = Object.assign({}, e); // Create another shallow copy of 'e' using Object.assign()
e.key3 = "Value3";
console.log(e);     // Log object 'e', which now contains 'key3'
console.log(f);     // Log object 'f', since 'f' is a reference to 'e', it also contains 'key3'
console.log(g);     // Log object 'g', since 'g' was a copy before 'key3' was added, it does not contain 'key3'
console.log(h);     // Log object 'h', which is also a separate copy and does not contain 'key3'

// optional chaining
const i = {
    firstName: "Shanu",
    address:{houseNumber: '1234'}
}

console.log(i.firstName)
console.log(i.address.houseNumber)

const j = {
    firstName: "Shanu",
    // address:{houseNumber: '1234'}
}
console.log(j?.firstName)       // Use optional chaining (?.) to safely access 'firstName' (exists, so it prints "Shanu")
// Use optional chaining (?.) to safely access 'houseNumber' inside 'address'
// Since 'address' is undefined, this avoids an error and returns 'undefined' instead
console.log(j?.address?.houseNumber)