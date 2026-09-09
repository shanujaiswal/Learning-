// Sets
// to create a set 
const a = new Set([1,2,3])
console.log(a)
console.log(a[2])      //Set is not index based so it will show undefined.

// logging it to console 
const b = ["abc"]
console.log(b)

// logging it to console 
const c = new Set("abc")
console.log(c)

// To create a new Set
const d = new Set();   
d.add(1);               // To add a new item 
d.add(2);               
d.add(2);                // It will not add this number as it is duplicate item 
d.add(3);
d.add(a);                   //to add another set in given set 
d.add(['items1','items2']);      //to add array in given set
d.add(['items1','items2']);      //to add array in given set
console.log(d);

//To check in set using for loop 
if(d.has(1)){
    console.log("1 is present")
}else{
    console.log("1 is not present")
}

// Adding elements to the Set
const e = new Set()
e.add('apple');
e.add('banana');
e.add('cherry');
console.log(e)

// Checking if an element exists
console.log(e.has('apple')); // true
console.log(e.has('grape')); // false

// Removing an element
e.delete('banana');

// Checking the size of the Set
console.log(e.size); // 2

// Iterating over the Set
for (const item of e) {
  console.log(item);
}

// Converting a Set to an Array
const myArray = Array.from(e);
console.log(myArray);

// Clearing all elements from the Set
e.clear();
console.log(e.size); // 0

//Extracting unique elements from array 
const f = [1,2,3,4,4,5,5,6]
const g = new Set(f)
console.log(f)
console.log(g)
// To find the length of g
let h = 0;
for(let element of g){
  h++;
}
console.log(h)
