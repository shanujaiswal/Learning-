// to clone and add 
let a = ["item1", "item2", "item3"]
let b = a.slice(0).concat(["item4", "item5"])  //method 2 cloning -- slicing method
let c = [].concat(a, ["item4", "item5"]);  //method 3 cloning --concat 
let d = [...a, "item4", "item5"]  //method 4 cloning --new way -- spread operator  

console.log(a);
console.log(b);
console.log(c);
console.log(d);

//for loop in array 
let e = ["apple", "grapes", "mango", "banana"];
for (let i = 0; i < e.length; i++) {
    console.log(e[i].toUpperCase())
}

//use const for creating array
const f = ["apple", "grapes", "mango", "banana"];
f.push("cherry")
console.log(f)

// while loop in array
const g = ["apple", "grapes", "mango", "banana"];
let h = 0;
while (h < g.length) {
    console.log(g[h].toUpperCase());
    h++;
}

// for of loop in array  ---- Use for in when you need to work with properties of an object.
const i = ["apple", "grapes", "mango", "banana"];
for (let j of i) {    // j is changable we can write any thing in place of j without declaration 
    console.log(j)
}

// for in loop in array  ----  Use for of when you need to loop through the values of an iterable like an array or string.
const k = ["apple", "grapes", "mango", "banana"];
for (let l in k) {    // j is changable we can write any thing in place of j without declaration 
    console.log(l)
    console.log(k[l])
}

// array Destructing 
//normal method
const m = ["value1","value2"];
let n = m[0];
let o = m[1];
console.log(n)
console.log(o)

// use time cutting methog
let[p,q] = m;  //if you are doing with let you can change value .if you are using const you can not change values 
console.log(p)
console.log(q)

const r = ["value1","value2","value3","value4","value5"];
let[s,,,u ]= r;   //to escape value no of value escape that much null value
console.log(s)
console.log(u)