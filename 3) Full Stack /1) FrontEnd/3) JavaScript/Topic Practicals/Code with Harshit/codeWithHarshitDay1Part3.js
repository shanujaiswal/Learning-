// Switch statement s
//normal nested if else 
let a = 0;
if (a === 0) {
    console.log("Sunday");
} else if (a === 1) {
    console.log("Monday");
} else if (a === 2) {
    console.log("Tueday");
} else if (a === 3) {
    console.log("Wednesday");
} else if (a === 4) {
    console.log("Thruday");
} else if (a === 5) {
    console.log("Friday");
} else if (a === 6) {
    console.log("Saturday");
} else {
    console.log("Invalid");
}

//Switch Statement -- evaluates a value against multiple cases and executes the code block of the matching case
// provide break if you want to stop the execution and no further responses from code otherwise it will provide all the given cases 
// we can run only one cases at a time 
let b = 2;
switch (b) {
    case 0:
        console.log("Sunday");
        break;
    case 1:
        console.log("Monday");
        break;
    case 2:
        console.log("Tueday");
        break;
    case 3:
        console.log("Wednesday");
        break;
    case 4:
        console.log("Thruday");
        break;
    case 5:
        console.log("Friday");
        break;
    case 6:
        console.log("Saturday");
        break;
    default:
        console.log("Invalid Day");
        break;
}

// while loop
let c = 0;
while (c <= 7) {
    console.log(c)
    c++
}

//method 1 
let d = 0
let e = 0
while (e <= 100) {
    d = d + e;
    e++;
}
console.log(d)

//method 2
let f = 100;
let g = (f * (f + 1)) / 2;
console.log(g)

// for loop 

for (let h = 0; h <= 9; h++) {
    console.log(h);
}

//break  -- break the output when reached to expected output 
for (let i = 0; i <= 9; i++) {
    if (i === 6) {
        break;
    }
    console.log(i);
}

//continue keywords  -- continue the  output from starting to end except the desire result 
for (let j = 0; j <= 9; j++) {
    if (j === 6) {
        continue;
    }
    console.log(j);
}

// do while 
let k = 10;
do {
    console.log(k);
    k++;
} while (k <= 9);
console.log(k)