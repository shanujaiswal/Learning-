// Access all elements of the array  and checking the type of 
let a = ["apple", "banana", "cherry", "Mango", "Grapes"]
console.log(a)
console.log(typeof a)

let b = [1, 2, 3, 4, 5];
console.log(b)
console.log(typeof b)

let c = [1, 2, 3, 4, "String", null, undefined];
console.log(c)
console.log(typeof c)

// to access specific Element of the given
let d = ["apple", "banana", "cherry", "Mango", "Grapes"]
console.log(d[2]);

//Changing the elements 
let e = ["apple", "banana", "cherry", "Mango", "Grapes"]
console.log(e)
e[1] = "Strawberry"
console.log(e)

// to check if it is array or not 
let f = ["apple", "banana", "cherry", "Mango", "Grapes"]
let g = {};
console.log(typeof f);
console.log(typeof g);
console.log(Array.isArray(f));
console.log(Array.isArray(g));

//push -- to add elements at last 
let h = ["apple", "banana", "cherry", "Mango", "Grapes"]
h.push("strawberry")
console.log(h)

//pop -- to delete elements from last 
let i = ["apple", "banana", "cherry", "Mango", "Grapes"]
console.log(i.pop())   //Removes and tell us value of the element which is removed from last 
console.log(i)

//unshift -- to add elements at Start 
let j = ["apple", "banana", "cherry", "Mango", "Grapes"]
j.unshift("strawberry")
console.log(j)


//Shift -- to delete elements from Start 
let k = ["apple", "banana", "cherry", "Mango", "Grapes"]
console.log(k.shift())   //Removes and tell us value of the element which is removed from Start 
console.log(k)

//Difference between primitive and reference data type 
//primitive  data type 
let l = 7;
let m = l;
console.log(l)
console.log(m)
l++
console.log(l) // Output the new value of 'l' (prints: 8)
console.log(m) // Output the value of 'm' (prints: 7). It remains unchanged because 'm' holds a copy of the value, not a reference to 'l'.

// reference data type 
let n = ["item 1", "item 2"]
let o = n;
console.log(n)
console.log(o)
n.push("item3")
console.log(n)
console.log(o)

//clone array 

let p = ["item1", "item2", "item3"]
let q = p
console.log(p === q)  // it make array clone 

let r = ["item1", "item2", "item3"]
let s = ["item1", "item2", "item3"]  //method 1 cloning  -- normal and old method 
let t = r.slice(0)  //method 2 cloning -- slicing method
let u = [].concat(r)  //method 3 cloning --concat 
let v = [...r]  //method 4 cloning --new way -- spread operator  

r.push("item4")
console.log(r === s)
console.log(r === t)
console.log(r === u)
console.log(r === v)

console.log(r)
console.log(s)
console.log(t)
console.log(u)
console.log(v)