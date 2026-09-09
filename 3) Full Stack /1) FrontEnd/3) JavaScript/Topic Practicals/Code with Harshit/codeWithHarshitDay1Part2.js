// if condition
let a = 23;
if (a >= 18) {
    console.log("you can give vote");
}

//if-else condition 
//ex - 1 ;
let b = 12;

if (b % 2 == 0) {
    console.log("even");
}
else {
    console.log("odd");
}
//ex - 2 Due to Prompt it is in comment ;
// let c = prompt("What is your age");
// if(c >= 18){
//     console.log("you can give vote");
// }
// else{
//     console.log("go to home ");
// }


//Falsy values
// false
// 0, -0 and 0n
// "", '' (empty strings)
// null, 
// undefined and NaN

// "", '' (empty strings)
let d;
if (d) {
    console.log(d);
} else {
    console.log("d is kinda empty");
}

// null, undefined and NaN
let e = null;
if (e) {
    console.log(e);
} else {
    console.log("e is kinda empty");
}

// ternary operator - works same as if else
//method - normal method 
let f = 12;

if (f >= 10) {
    g = "coffee"
} else {
    g = "milk"
}
console.log(g)

// condition ? (condition? true : false) : (condition? true : false));

//*method - ternary operator /conditional operator *
let h = 8;
let i = h >= 5 ? "coffee" : "milk";
console.log(i)

let j = 2;
let k = j >= 5 ? "coffee" : j <= 2 ? "milk" : "water";
console.log(k)

// and or operator 
let l = "Shanu";
let m = 24;

if (l[0] === "S" && m > 22) {
    console.log("your name start with s")
} else {
    console.log("you are what")
}

let n = "Shanu";
let o = 24;
if (n[4] === "S" || o < 22) {
    console.log("your name start with s")
} else {
    console.log("you are what")
}

//nested if else

let p = 49;
let q = prompt("guess a number");
if (q == p) {
    console.log("you are idiot")
} else {
    if (q > p) {
        console.log("you are too much high")
    } else (q < p)
    console.log("you are lowly hooman")
}

let r = 49;
let s = Number(prompt("Guess a number")); // Convert input to number

if (s === r) {
    console.log("You got it right!");
} else if (s < r - 40) {
    console.log("Way too low! Try a much higher number");
} else if (s < r - 30) {
    console.log("Too low - go up by about 30+");
} else if (s < r - 20) {
    console.log("Still low - go up by about 20+");
} else if (s < r - 10) {
    console.log("Getting closer but still low - go up by about 10+");
} else if (s > r + 40) {
    console.log("Way too high! Try a much lower number");
} else if (s > r + 30) {
    console.log("Too high - go down by about 30+");
} else if (s > r + 20) {
    console.log("Still high - go down by about 20+");
} else if (s > r + 10) {
    console.log("Getting closer but still high - go down by about 10+");
} else if (s > r) {
    console.log("Almost there but still a bit high!");
} else {
    console.log("Almost there but still a bit low!");
}

//reverse given string
// var shanu=()=>{
//     var n = "check"
//     var len = n.length
//     var io = 0
//     let res = ""
//     let rev = ""
//     while (io<len) {
//         res = n.substring(io,io+1)
//         rev = res+rev
//         io++;
//     }
//     console.log(rev)
// }
// shanu()


