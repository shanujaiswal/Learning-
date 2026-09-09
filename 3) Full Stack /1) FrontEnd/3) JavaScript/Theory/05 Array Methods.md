# Methods used in array

# ForEach

--> It used in placeof for loop . but it is not conditional as in for loop .
--> It will execute the code till the end .
--> It will not give new array .and we use console not return

# Map

--> It will return new array.

# Filter method

--> The filter() method is used to create a new array containing all the elements from an original array that pass a test specified by a provided function.
--> Work on boolean function . Returns true or False.
--> It doesn't modify the original array but returns a new one with the elements that satisfy the condition set in the callback function.

# In function method if there is return we have to create a new variable , if there is direct console then there is no need to make a new variable

# Reduce method

--> The reduce() method is used to reduce an array to a single value by applying a reducer function on each element of the array, one at a time, and accumulating the results

# Sort method

--> the sort() function is used to sort the elements of an array in place.
--> By default, it sorts the elements as strings, but we can provide a custom sorting function to sort elements according to specific criteria.

# Find method

--> The find() method in JavaScript is an array method that is used to search for the first element in an array that satisfies a provided test condition
--> Once the condition is met, it returns that element. If no element is found that satisfies the condition, it returns undefined.

# Every method

--> The every method in JavaScript is an array method used to test whether "all elements" in an array pass the given condition (specified by a callback function).
--> If every element in the array satisfies the condition, the method returns true; otherwise, it returns false.
--> Return in Boolean

# Some method

--> The some method in JavaScript is an array method that checks if "at least one element" in the array satisfies the condition specified in a callback function.
--> If any element meets the condition, it returns true; otherwise, it returns false.

# FIll method

--> The fill method in JavaScript is an array method used to fill all or part of an array with a static value.
--> can specify the start and end indices to control which part of the array is filled.

# Splice method

--> The splice method in JavaScript is an array method used to add, remove, or replace elements in an array.
--> It modifies the original array and optionally returns the removed elements as a new array.

# Slice method

--> The slice() method returns a shallow copy of a portion of an array (start inclusive, end exclusive) as a new array.
--> Does NOT modify the original array -- unlike splice(), which does.
--> Negative indices count from the end of the array, e.g. arr.slice(-2) gets the last 2 elements.

# FindIndex and FindLastIndex

--> findIndex() returns the index of the first element that satisfies the test condition, or -1 if none match.
--> findLastIndex() does the same but searches from the end of the array.
--> Similar to find(), but returns the position instead of the element itself.

# Includes method

--> includes(value) checks whether an array contains a given value, returns true/false.
--> Simpler alternative to indexOf(value) !== -1 -- also correctly detects NaN, which indexOf cannot.

# Flat and FlatMap

--> flat(depth) flattens nested arrays into a single-level array, up to the given depth (default 1); flat(Infinity) flattens completely.
--> flatMap(callback) maps each element then flattens the result by one level -- equivalent to map().flat(1), but slightly more efficient.

# Array.isArray and Array.of

--> Array.isArray(value) checks whether a value is truly an array (typeof returns "object" for arrays too, so this is the reliable check).
--> Array.of(a, b, c) creates a new array from the given arguments -- avoids the quirk of new Array(7) creating an empty array of length 7 instead of [7].

# join method

--> join(separator) combines all elements of an array into a single string, separated by the given separator (default is a comma).
--> The reverse operation of String.split().

# at method

--> at(index) returns the element at the given index, supports negative indices (e.g. arr.at(-1) gets the last element) -- cleaner alternative to arr[arr.length - 1].

# copyWithin method

--> copyWithin(target, start, end) copies part of an array to another location within the same array, overwriting existing elements, and returns the modified array.

# reduceRight method

--> reduceRight() works like reduce() but processes the array from right to left instead of left to right.

# Chaining Array Methods

--> Array methods that return a new array (map, filter, slice, flat...) can be chained together in a single readable pipeline.
--> arr.filter(n => n > 0).map(n => n * 2).reduce((sum, n) => sum + n, 0)
--> Each step in the chain works on the array/value produced by the previous step.

# Newer Array Methods (ES2023) -- toSorted, toReversed, toSpliced, with

--> ES2023 introduced "copying" versions of the classic mutating array methods -- they return a NEW array instead of modifying the original, following the same non-mutating philosophy as map/filter/slice.

--> toSorted(compareFn) --> like sort(), but returns a new sorted array; the original array is left untouched.
```javascript
const nums = [3, 1, 2];
const sorted = nums.toSorted();
console.log(sorted);   // [1, 2, 3]
console.log(nums);     // [3, 1, 2] -- unchanged
```

--> toReversed() --> like reverse(), but returns a new reversed array without mutating the original.
```javascript
const arr = [1, 2, 3];
console.log(arr.toReversed());   // [3, 2, 1]
console.log(arr);                 // [1, 2, 3] -- unchanged
```

--> toSpliced(start, deleteCount, ...items) --> like splice(), but returns a new array with the changes applied, instead of mutating in place.
```javascript
const arr = [1, 2, 3, 4];
console.log(arr.toSpliced(1, 2, "a", "b"));   // [1, "a", "b", 4]
console.log(arr);                              // [1, 2, 3, 4] -- unchanged
```

--> with(index, value) --> returns a new array with the element at `index` replaced by `value` -- a non-mutating alternative to `arr[index] = value`.
```javascript
const arr = [1, 2, 3];
console.log(arr.with(1, 99));   // [1, 99, 3]
console.log(arr);                // [1, 2, 3] -- unchanged
```

--> Why these matter: they make it easy to update arrays immutably (important in React state updates, Redux reducers, etc.) without needing spread-operator workarounds like `[...arr].sort()`.

# Deep Dive -- reduce() as the "Universal" Array Method

--> `reduce()` is the most general-purpose array method -- `map()` and `filter()` can both be implemented purely in terms of `reduce()`, which illustrates that `reduce` isn't just "one array method among many" but a genuinely more fundamental building block.

```javascript
// map(), reimplemented using only reduce()
function myMap(arr, fn) {
  return arr.reduce((acc, item) => [...acc, fn(item)], []);
}

// filter(), reimplemented using only reduce()
function myFilter(arr, predicate) {
  return arr.reduce((acc, item) => predicate(item) ? [...acc, item] : acc, []);
}
```

--> In practice, prefer the dedicated `map()`/`filter()` when that's genuinely what you're doing -- they communicate INTENT more clearly to a reader than an equivalent `reduce()` would. Reach for `reduce()` specifically when the transformation doesn't cleanly fit "transform each item" (map) or "keep some items" (filter) -- e.g. grouping items by a key, building an object from an array, or computing multiple aggregate values in a single pass.

```javascript
// Grouping an array of objects by a property -- a classic reduce()-only use case
const people = [{ name: "Alice", dept: "Eng" }, { name: "Bob", dept: "Sales" }, { name: "Carol", dept: "Eng" }];

const byDept = people.reduce((acc, person) => {
  (acc[person.dept] ??= []).push(person);
  return acc;
}, {});
// { Eng: [Alice, Carol], Sales: [Bob] }
```

# Deep Dive -- Time Complexity of Common Array Methods

--> Directly connecting to the Algorithms and Big-O Complexity file's concepts, applied specifically to JS array methods: `push`/`pop` are O(1) (operate only at the end); `unshift`/`shift` are O(n) (every other element must be re-indexed); `map`/`filter`/`forEach`/`reduce`/`find`/`some`/`every` are all O(n) (each visits every element once, in the worst case); `includes`/`indexOf` are O(n) (linear scan); `sort()` is O(n log n) in modern engines. Chaining several O(n) methods together (`.filter().map().reduce()`) is still O(n) overall in practice, NOT O(n³) -- each pass is a separate O(n) traversal, and they don't multiply, though for extremely large arrays, minimizing the NUMBER of separate passes (e.g. combining logic into one `reduce()`) can still meaningfully help.
