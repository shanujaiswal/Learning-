// Method
const a = {
  firstName: "rahul",
  age: 24,
  about1: function () {
    console.log("person name is shanu and person age is 24"); //Logging a hardcoded message
    console.log(
      `person name is ${this.firstName} and person age is ${this.age}`
    ); // Logging a dynamic message using 'this'
    console.log(this);
  },
};
a.about1();

function b() {
  console.log(`person name is ${this.firstName} and person age is ${this.age}`);
}
const person1 = {
  firstName: "Aditya",
  age: 24,
  about2: b,
};
const person2 = {
  firstName: "harsh",
  age: 12,
  about2: b,
};
const person3 = {
  firstName: "Pradeep",
  age: 31,
  about2: b,
};
b();
person1.about2();
person2.about2();
person3.about2();

//Call function
// calling function inside code
const c = {
  firstName: "Pradeep",
  age: 31,
  about3: function (hobby, favthing) {
    console.log(this.firstName, this.age, hobby, favthing);
  },
};

const d = {
  firstName: "Pradeep",
  age: 31,
};

c.about3.call(); // ❌ Incorrect usage: `call()` is missing a `this` argument, so `this.firstName` and `this.age` will be `undefined` (or throw an error in strict mode)
c.about3.call(d); // ✅ Correct usage: Calling `about` with `d` as `this`, logging → "Pradeep 31 undefined undefined"
c.about3.call(d, "guitar"); // ✅ Correct usage: Passing "guitar" as `hobby`, logs → "Pradeep 31 guitar undefined"

// calling function outside code
function about4(hobby, favthing) {
  console.log(this.firstName, this.age, hobby, favthing);
}
const e = {
  firstName: "Pradeep",
  age: 31,
};

const f = {
  firstName: "Pradeep",
  age: 31,
};

about4.call();
about4.call(f);
about4.call(f, "guitar");

// Apply function
about4.apply(e, ["guitar", "What"]);

// Bind function
const g = about4.bind(e, "guitar", "bach");
g();

//  arrow function
const h = {
  firstName: "Pradeep",
  age: 31,
  about5: () => {
    console.log(this.firstName, this.age); // In Arrow function this use to take value of global function
  },
};
h.about5();

const i = {
  firstName: "Pradeep",
  age: 31,
  about5() {
    console.log(this.firstName, this.age);
  },
};
i.about5();

//Proto , Prototype , Class
//1. Creating a Single User Object
const j = {
  firstName: "Shanu",
  lastName: "Jaiswal",
  email: "Shanu@gmail.com",
  age: 24,
  address: "my Place",
  about6: function () {
    return `${this.firstName} is  ${this.age} years old`;
  },
  is18: function () {
    return this.age >= 18;
  },
};
console.log(j);

//Creating Multiple Users (Inefficient Memory Usage)
// DrawBack -- Inefficient Memory Usage --> Since functions are recreated for every object, this causes unnecessary memory consumption, especially when creating thousands of users.
function k(firstName, lastName, email, age, address) {
  const l = {};
  l.firstName = firstName;
  l.lastName = lastName;
  l.email = email;
  l.age = age;
  l.address = address;
  l.about7 = function () {
    return `${this.firstName} is  ${this.age} years old`;
  };
  l.is18 = function () {
    return this.age >= 18;
  };
  return l;
}
const m = k("Shanu", "jaiswal", "Shanu@warpx.ai", 19, "my address");
console.log(m);
const is18 = m.is18();
console.log(is18);
const about7 = m.about7();
console.log(about7);

// Optimized Memory Usage (Using Shared Methods)
const n = {
  about8: function () {
    return `${this.firstName} is  ${this.age} years old`;
  },
  is18: function () {
    return this.age >= 18;
  },
};
function o(firstName, lastName, email, age, address) {
  const p = {};
  p.firstName = firstName;
  p.lastName = lastName;
  p.email = email;
  p.age = age;
  p.address = address;
  p.about = n.about8;
  p.is18 = n.is18;
  return p;
}

const user1 = o("Shanu", "jaiswal", "Shanu@warpx.ai", 19, "my address");
const user2 = o("Rahul", "jaiswal", "Shanu@warpx.ai", 21, "my address");
const user3 = o("Harsh", "jaiswal", "Shanu@warpx.ai", 23, "my address");
console.log(user1.about());
console.log(user2.about());
console.log(user3.about());

// Best Approach (Prototype-Based Object Creation)
const q = {
  about8: function () {
    return `${this.firstName} is  ${this.age} years old`;
  },
  is18: function () {
    return this.age >= 18;
  },
  sing: function () {
    return "toon naa naa naa ";
  },
};
function r(firstName, lastName, email, age, address) {
  const s = Object.create(q);
  s.firstName = firstName;
  s.lastName = lastName;
  s.email = email;
  s.age = age;
  s.address = address;
  return s;
}

const user4 = r("Shanu", "jaiswal", "Shanu@warpx.ai", 19, "my address");
const user5 = r("Rahul", "jaiswal", "Shanu@warpx.ai", 21, "my address");
const user6 = r("Harsh", "jaiswal", "Shanu@warpx.ai", 23, "my address");
console.log(user4);
console.log(user5.about8());
console.log(user6.sing());

// Understanding Prototype in JavaScript
// 1. Checking Prototype in an Object
const t = {
  key1: "Value1",
};

if (t.prototype) {
  console.log("prototype is present");
} else {
  console.log("prototype is not present");
}

// 2. Checking Prototype in a Function
function u() {
  console.log("Hello World");
}
if (u.prototype) {
  console.log("prototype is present");
} else {
  console.log("prototype is not present");
}

u.prototype.abc = "abc";
u.prototype.xyz = "xyz";
u.prototype.sing = function () {
  return "laa laa laa";
};
console.log(u.prototype);
console.log(u.prototype.sing);


//Object Creation Using Prototype in JavaScriptFF
function v(firstName, lastName, email, age, address) {
  const w = Object.create(v.prototype);
  w.firstName = firstName;
  w.lastName = lastName;
  w.email = email;
  w.age = age;
  w.address = address;
  return w;
}
t.prototype.about9 = function () {
  return `${this.firstName} is the ${this.age} years old`;
};
t.prototype.is18 = function () {
  return this.age >= 18;
};
t.prototype.sing = function () {
  return "toon naa naa naa ";
};

const user7 = new v("shashi", "jaiswal", "Shanu@warpx.ai", 19, "my address");
const user8 = new v("ashish", "jaiswal", "Shanu@warpx.ai", 21, "my address");
const user9 = new v("sachin", "jaiswal", "Shanu@warpx.ai", 23, "my address");
console.log(user7);
console.log(user8.about9());
console.log(user9.sing());