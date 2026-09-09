// for each 
const a = [1, 2, 3, 4, 5]

function b(number, index) {
    console.log("index is ", index)
    console.log(number * 2)
}
b(a[2], 0)
for (let i = 0; i < a.length; i++) {
    b(a[i], i)
}
a.forEach(b)


// can be written like this also 
const c = [1, 2, 3, 4, 5, 6];
console.log(c.forEach((number, index) => `index is ${index} number is ${number}`))
// map method

const d = [
    { firstName: "Abhishek", age: 23 },
    { firstName: "Rahul", age: 23 },
    { firstName: "Dharambir", age: 23 },
    { firstName: "Ivan", age: 23 },
]
console.log(d.map((user) => user.firstName))

// filter method
const e = [1, 2, 3, 4, 5, 6]
console.log(e.filter((number) => number % 2 === 0))

// reduce method 
const f = [1, 2, 3, 4, 5, 6]
console.log(f.reduce((num1, num2) => num1 + num2))

const g = [
    { productId: 1, productName: "mobile", price: 12000 },
    { productId: 2, productName: "laptop", price: 22000 },
    { productId: 3, productName: "tv", price: 18000 },
]
console.log(g.reduce((price1, price2) => price1 + price2.price, 0))  //0 will be the initial value -- generally used in objects if we change the 0 the initial value will also change 

//Sort 
//Default Sorting (As Strings):
let h = [5000, 300, 80, 1, 200];
h.sort();
console.log(h); // Output: [1, 200, 300, 5000, 80]   --> Because it will take it as string then chose only first number and convert to ASCII 

//Sorting Numbers Correctly:
let i = [5000, 300, 80, 1, 200];
i.sort((a, b) => a - b);    //Ascending Order
console.log(i);  //Output: [1, 80, 200, 300, 5000]

let j = [5000, 300, 80, 1, 200];
j.sort((a, b) => b - a);    //Descending order
console.log(j);

//Sorting Strings: -- It sorts capital letters first 
let k = ['banana', 'apple', 'Apple' ,'APPLE', 'cherry', 'date', 'appld', 'applf', 'appled', 'applet'];
k.sort();
console.log(k);

//Sorting Objects:
let l = [
    { name: 'John', age: 30 },
    { name: 'Jane', age: 25 },
    { name: 'Alex', age: 35 }
];

l.sort((a, b) => a.age - b.age);
console.log(l);

//Find method
//Finding a number in an array
const m = [10, 20, 30, 40, 50];

const result = m.find(num => num > 25);
console.log(result); // 30

//Every 
// Checking if all elements are even
const n = [2, 4, 6, 8];

const allEven = n.every(num => num % 2 === 0);
console.log(allEven); 

//Some 
// Checking if all elements are even
const o = [3, 5, 8, 9];

const Even = o.some(num => num % 2 === 0);
console.log(Even); 

//Fill 
const p = [1, 2, 3, 4];
p.fill(0);
console.log(p); 

//Filling part of an array
const q = [1, 2, 3, 4];
q.fill(9, 1, 3);
console.log(q);  //Here: 9 is the value to fill, 1 is the start index (inclusive), 3 is the end index (exclusive).

//Using negative indices
const r = [5, 6, 7, 8];
r.fill(0, -2);
console.log(r); // [5, 6, 0, 0] -2 starts filling from the second-to-last element.

// Filling an empty array
const s = new Array(5); // Creates an array with 5 empty slots
s.fill(1);
console.log(s); // [1, 1, 1, 1, 1]

// Splice   ---> Start , delete , insert 
//to delete 
const t = [5, 6, 7, 8];
const u = t.splice(1, 2); // Start at index 1, remove 2 elements
console.log("Updated array:", t);
console.log("Deleted elements:", u);

//to insert
const v = [5, 6, 7, 8];
v.splice(1,0, "Shanu")   //1 is used as place from where to delete , 0  is how much number to delete from that place , Shanu is Word to add 
console.log(v)

//to Insert & delete
const w = [5, 6, 7, 8];
const x = w.splice(1, 2, "Shanu", "Shanu2"); // Start at index 1, remove 2 elements, and insert "Shanu" and "Shanu2"

console.log("Updated array:", w); // [5, "Shanu", "Shanu2", 8]
console.log("Deleted elements:", x); // [6, 7]