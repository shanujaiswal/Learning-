// Create object 
let a = { name: "Shanu", age: 23 }
console.log(a)
console.log(typeof a)
console.log(a.name)  // to access the value in the given data 
console.log(a.age)  // to access the value in the given data 

// saving and accessing array in objects
let b = {
    name: "Shanu",
    age: 23,
    hobbies: ["chess", "cricket", "music"]
}
console.log(b);
console.log(b.hobbies)   //to access array in object

//add key value pair to objects
b.gender = "male";
console.log(b)

// difference between dot and bracket notation

let c = {
    name: "Shanu",
    age: 23,
    "person hobbies": ["chess", "cricket", "music"]
}
console.log(c.age)    //dot notation -- if single key 
console.log(c["person hobbies"]);    //bracket notation -- if two keys are there 

let d = " email";
const e = {
    name: "Shanu",
    age: 23,
    "person hobbies": ["chess", "cricket", "music"]
}
e[d] = "Shanuj4all@gmail.com" // to add key value in object 
console.log(e)

// how to iterate objects
const f = {
    name: "Shanu",
    age: 23,
    "person hobbies": ["chess", "cricket", "music"]
}
// for in loops
for (let key in f) {
    console.log(key)   //to get the keys of the objects 
}

for (let key in f) {
    console.log(f[key])     //to get the values in given objects  
}

for (let key in f) {
    console.log(`${key} : ${f[key]}`)     //Method 1 - to get the key and values of given objects 
}

for (let key in f) {
    console.log(key, f[key])     //Method 2 - to get the key and values of given objects 
}

//object.key
console.log(Object.keys(f))

//computed properties - allow you to define properties that are derived from other data, rather than being set directly.
const g = "obj1";
const h = "obj2";

const i = "value1";
const j = "value2";

//method -1 
const k = {
    [g]: i,
    [h]: j
}
console.log(k)

// spread operator
// if single value key 
const l = {
    m: "values",
    n: "values"
}
console.log(l)

// if two different value for same key 
const obj3 = {
    key1: "Values",
    key2: "Values"
}
const obj4 = {
    key1: "Values57545",
    key4: "Values",
    key5: "Values"
}
const o = { ...obj4, ...obj3 };
console.log(o)                 //always take value of last clone value

const p = { ...obj4, ...obj3, key6: "Value" };
console.log(p)                 //always take value of last clone value

// Creating an object with letters as keys using the spread operator
const q = { ..."abcdefghijklmnopqrstuvwxyz" }
console.log(q)

//object destructuring 
const r = {
    banned: "whole",
    famous: "To be Find"
}
const banned = r.banned         // key can be changed
const famous = r.famous         // key can be changed   
console.log(banned, famous)

//shortcut
const s = {
    apple: "whole",
    cherry: "To be Find"
}
const { apple, cherry } = s;
console.log(apple)

//objects inside array -- very useful in real world application 

const t = [
    { userId: 1, firstname: 'Shanu', gender: 'male' },
    { userId: 2, firstname: 'Sachin', gender: 'male' },
    { userId: 3, firstname: 'Rahul', gender: 'male' },
]
for (let user of t) {
    console.log(user)
}

const [user1, user2, user3] = t;
console.log(user1)

//objects literal 
// key -> string 
// key -> symbol 
const u ={
    firstName : "Harshit",
    age:7,
    1:"one"
}
console.log(u.firstName)
console.log(u["firstName"])
for (let key in u){
    console.log(typeof key);
}