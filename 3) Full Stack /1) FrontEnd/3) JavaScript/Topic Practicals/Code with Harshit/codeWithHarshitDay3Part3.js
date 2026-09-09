// parameter destructing

// Used in objects 
// Used too much in Reacts

const a = {
    namee: "Shanu",
    gender: "male",
    age: 500
}

// normal way
function b(obj) {
    console.log(obj.namee)
    console.log(obj.gender)
}
b(a)

//destructing 
function c({namee, gender, age}) {
    console.log(namee)
    console.log(gender)
}
c(a)

// callback function
function d(){
    console.log("inside my func 2")
}
function e(callback){
console.log("hello there i as a func and i can...")
callback(d)
}
e(d)

// function returning function

function f(){
    function g(){
        console.log("how are you")
    }
    return g;
}
const h = f()
h()