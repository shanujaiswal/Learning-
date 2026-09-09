// adding two string / concatinate
let a = "shanu";
let b = "jaiswal"
console.log(a + b);
console.log(a + " " + b);

// Doing operation inside console 
let c = 100;
console.log(c * 11)  //change signs for changing operation.

//Finding lengths and words 
let d = "Shanu";
console.log(d.length); //find total length
console.log(d[d.length - 1])  //find words from backward

//Triming the words 
let e = "  Shanu   e"
e = e.trim(); // method 1 to trim 
console.log(e)
let f = e.trim();  //method 2  to trim 
console.log(f);

//Changing Sizes
let g = "Shanu"
console.log(g.toUpperCase()); // making upperCase
console.log(g.toLowerCase()); // making lowerCase

//Slicing 
let h = "Shanu"
let i = h.slice(0, 2); // to give letters between range 
console.log(i);
let j = h.slice(3); // to give letters after that range 
console.log(j);

//Types of operator 
// String. - "Shanu"
//Number.- 2,3,4,5,6,7,8
// Boolean. - True / False
// Null. - {}
// Undefined.
// BigInt.
// Symbol.
// Object.

// number to string
let k = 24;
k = k + ""; //method 1
console.log(typeof k)
k = String(k) //method 2
console.log(typeof k)

// string to number
let l = "shanu"
l = Number(l) //method 1
console.log(typeof l)
let m = +"shanu"; //method 2 
console.log(typeof m);

// template string 
let myName = "Shanu";
let age = 22

let aboutMe = "My name is " + myName + " and my age is " + age; //normal method 
console.log(aboutMe)

let aboutMe1 = `my name is ${myName} and my age is ${age}`  // template method
console.log(aboutMe1)

//undifined 
let n
console.log(typeof n)
n = "apple";
console.log(typeof n)

//null
let o = null;
console.log(o);
o = "Shanu";
console.log(typeof o)
console.log(typeof null)

//bigint
console.log(Number.MAX_SAFE_INTEGER);  //safely to store integer after that use bigint 
let p = BigInt(123)  //method 1
console.log(typeof p)
let q = 123n;  //method 2 
console.log(typeof q)
