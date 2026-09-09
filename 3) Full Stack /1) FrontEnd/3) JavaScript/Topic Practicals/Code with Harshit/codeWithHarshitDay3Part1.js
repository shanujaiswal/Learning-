/*  Normal syntax
function functionName(parameters) {
code to be executed
} */

//normal way to write a code to print 
function a() {
    console.log("my name is shanu jaiswal");
}
a();

//Using return ;
function b() {
    return (2 + 4)
}
console.log(b());
const c = b();   //Creating a new variable
console.log(c)

// making a add function 
function d(num1, num2) {
    return num1 + num2
}
console.log(d(4, 5));    // By direct assigning value 
const e = d(4, 5)
console.log(e)         // By making a new variable 

// Function to check if the number is even or odd 
function f(num) {
    if (num % 2 === 0) {
        return true;
    } else {
        return false;
    }
}
console.log(f())     //console.log(f(prompt())) --> if we wanta number from user 

console.log(2 % 2 === 0)  //use prompt inplace of 1 to get values from users  one line function to check if odd or even  

// to get the first value of words
function g(anyString) {
    return anyString[0];
}
console.log(g("shanu"));

// to check the index of target if target is present in array 
function h(array, target) {
    for (let i = 0; i <= array.length; i++) {
        if (array[i] === target) {
            return i;
        }
    }
    return -1;
}
const i = [1, 2, 3, 4, 5, 6, 7, 8]
console.log(h(i, 98))

// function expression --> way to define a function in programming (usually JavaScript) using an expression.
const j = function () {
    console.log("happy birthday to you ......")
}

// Arrow Function
// normal 
const k = () => {
    console.log("My name is shanu jaiswal")
}
k()

// to add number
const l = (num7, num8, num9) => {
    return num7 + num8 + num9
}
console.log(l(2, 3, 4,))

//to check even or not --> normal big way 
const m = (Number) => {
    return Number % 2 === 0
}
console.log(m(11))

// one line 
const n = Number => Number % 2 === 0;
console.log(n(14))

