## Conditions

--> Python supports the usual logical conditions from mathematics:

# If statements

Equals: a == b
Not Equals: a != b
Less than: a < b
Less than or equal to: a <= b
Greater than: a > b
Greater than or equal to: a >= b
-->These conditions can be used in several ways, most commonly in "if statements" and loops.
--> An "if statement" is written by using the if keyword.

# Elif

==> if-elif- else (syntax)
if(condition):
Statement 1
elif(conditional):
statement 2
else:
statement N

# Else

--> The else keyword catches anything which isn't caught by the preceding conditions.

# Ternary Operators, or Conditional Expressions.

==> **Short Hand If**

-->if a > b: print("a is greater than b")

==> **Short Hand If ... Else**

--> a = 2
b = 330
print("A") if a > b else print("B")

==> multiple else statements on the same line:

--> One line if else statement, with 3 conditions:
--> a = 330
b = 330
print("A") if a > b else print("=") if a == b else print("B")

==> And
--> The and keyword is a logical operator, and is used to combine conditional statements:
a = 200
b = 33
c = 500
if a > b and c > a:
print("Both conditions are True")

==> Or
--> The or keyword is a logical operator, and is used to combine conditional statements:
a = 200
b = 33
c = 500
if a > b or a > c:
print("At least one of the conditions is True")

==> Nested If
Can have if statements inside if statements, this is called nested if statements.
--> Writing statement inside another statement
if (cond 1):
if (cond 2):
print()

==> The pass Statement
--> if statements cannot be empty, but if you for some reason have an if statement with no content, put in the pass statement to avoid getting an error.

```python
a = 33
b = 200
if b > a:
   pass
```

## Switch Statement in Python

--> Python does not have a built-in switch statement like some other programming languages (such as C, C++, or Java).
--> Similar functionality can be achieved using **match** statements (introduced in Python 3.10) or dictionary-based approaches.

# Python Match

--> The match statement is used to perform different actions based on different conditions.

==> The Python Match Statement
--> Instead of writing many if..else statements, you can use the match statement.
--> The match statement selects one of many code blocks to be executed.

```python
match expression:
    case x:
        # code block
    case y:
        # code block
    case z:
        # code block
```

==> This is how it works:
--> The match expression is evaluated once.
--> The value of the expression is compared with the values of each case.
--> If there is a match, the associated block of code is executed.
-->

```Python
day = 4
match day:
  case 1:
    print("Monday")
  case 2:
    print("Tuesday")
  case 3:
    print("Wednesday")
  case 4:
    print("Thursday")
  case 5:
    print("Friday")
  case 6:
    print("Saturday")
  case 7:
    print("Sunday")
```

==> Default Value
Use the underscore character \_ as the last case value if you want a code block to execute when there are not other matches:
-->The value \_ will always match, so it is important to place it as the last case to make it beahave as a default case.
-->

```python
day = 4
match day:
  case 6:
    print("Today is Saturday")
  case 7:
    print("Today is Sunday")
  case _:
    print("Looking forward to the Weekend")
```

==> Combine Values
Use the pipe character | as an or operator in the case evaluation to check for more than one value match in one case:
-->

```python
day = 4
match day:
  case 1 | 2 | 3 | 4 | 5:
    print("Today is a weekday")
  case 6 | 7:
    print("I love weekends!")
```

==>If Statements as Guards
You can add if statements in the case evaluation as an extra condition-check:

```Python
month = 5
day = 4
match day:
  case 1 | 2 | 3 | 4 | 5 if month == 4:
    print("A weekday in April")
  case 1 | 2 | 3 | 4 | 5 if month == 5:
    print("A weekday in May")
  case _:
    print("No match")
```

## Loops in python

--> Loops in Python are used to execute a block of code multiple times.
--> There are different types of loops, each with its specific use case.
--> Traverse means to go through or iterate over each element in a data structure, such as an array, list, or string, one by one.
--> remember to increment i, or else the loop will continue forever.
--> for loop ,while loop

# Iterator and Iteration

--> An iterator and iteration in a loop are fundamental concepts in programming, especially in Python.

1. Iterator
   --> An iterator is an object that contains a countable number of values, allows traversal (iteration) through a sequence (like lists, tuples, dictionaries, or sets) one element at a time.
   --> All these objects(Lists, tuples, dictionaries, and sets) have a iter() method which is used to get an iterator,Even strings are iterable objects, and can return an iterator
   --> The for loop actually creates an iterator object and executes the next() method for each loop.
   -->It implements two methods:
   i. **iter**() → Returns the iterator object itself.
   ii. **next**() → Returns the next element in the sequence. Raises StopIteration when there are no more elements.
2. Iteration
   Iteration is the process of repeatedly accessing elements in a sequence, usually using loops (for or while).

==> Create an Iterator
--> To create an object/class as an iterator have to implement the methods **iter**() and **next**() to our object.
--> The **iter**() method acts similar, can do operations (initializing etc.), but must always return the iterator object itself.
--> The **next**() method also allows to do operations, and must return the next item in the sequence.

==> StopIteration
--> StopIteration is a signal used by Python to say an iterator has no more items to give.
--> It automatically stops loops like for when all values are done.
--> In the **next**() method, we can add a terminating condition to raise an error if the iteration is done a specified number of times:

# Types of Loops in Python

There are two types of loops in Python:

--> **for loop** - Used for iterating over a sequence (list, tuple, string, dictionary, range, etc.).
--> **while loop** - Runs as long as a specified condition is `True`.

==> break Statement
--> Exits the loop immediately when encountered.
--> Stops further iterations even if the loop condition is still True.

```python
for num in range(1, 6):
if num == 3:
break # Stops the loop at 3
print(num)
```

==> continue Statement
--> Skips the current iteration and moves to the next one.
--> The loop does not terminate but jumps to the next cycle.

```python
for num in range(1, 6):
    if num == 3:
        continue  # Skips printing 3
    print(num)
```

# while Loop

--> The while loop runs a block of code as long as a given condition is True.
--> The loop checks the condition before each iteration.
--> If the condition becomes False, the loop terminates.
--> It requires careful use to prevent infinite loops.
--> Syntax
while condition:
statement to print(upto condition is true)
--> Example:

```python
i = 1
while i <= 5:
print(i)
i += 1
```

==> Loop Control Statements
Python provides control statements to alter the normal loop execution:

==> The else Statement
--> With the else statement we can run a block of code once when the condition no longer is true:

# for Loop

--> The for loop is used for iterating over a sequence, such as a list, tuple, string, or range.
--> It runs a block of code for each element in the sequence.
--> The loop variable takes the value of each item in the sequence during each iteration.
--> works more like an iterator method as found in other object-orientated programming languages.
--> It automatically stops when all elements in the sequence have been processed.
--> The for loop does not require an indexing variable to set beforehand.
--> syntax
for element in list:
print(element)
else:
print("message")

==> Looping Through a String
Even strings are iterable objects, they contain a sequence of characters:

```python
for x in "banana":
  print(x)
```

==> else Clause in for Loops
--> The else clause in loops executes after the loop completes all iterations normally.
--> If the loop is terminated using break, the else block does not execute.
-->

```python
for num in range(1, 6):
   if num == 3:
      break
      print(num)
   else:
      print("Loop completed")
```

# Nested Loops

--> A nested loop is a loop inside another loop.
--> The inner loop runs completely for each iteration of the outer loop.
--> It is commonly used for processing multi-dimensional data.

==> When to Use Which Loop?

- Use `for` loops when the number of iterations is known.
- Use `while` loops when the number of iterations depends on a condition.

# Range()

--> Range functions returns a sequence of numbers, starting from 0 by default, and increments by 1 (by default), and stops before a specified number.
--> The built-in range() function returns an immutable sequence of numbers, commonly used for looping a specific number of times.
--> Immutable means that it cannot be modified after it is created.

==> Creating ranges
--> The range() function can be called with 1, 2, or 3 arguments, using this syntax:
--> Syntax of range()
range( start?, stop, step? )
Where:

1. start (optional) → The starting number of the sequence (default is 0).
2. stop (required) → The sequence ends before this number.
3. step (optional) → The difference between each number (default is 1)

==> Using range() in Loops

1. Call range() With One Argument --> Basic Usage (Only stop is provided)
   --> If only one argument is given, start is assumed to be 0, and step is 1
   --> Syntax
   ```python
   for i in range(5):
   print(i)
   ```
2. Call range() With Two Arguments --> Specifying start and stop
   --> If two arguments are provided, the sequence starts from start and stops before stop.
   --> Syntax
   ```python
   for i in range(0, 6):
   print(i)
   ```
   --> Note that range(6) is not the values of 0 to 6, but the values 0 to 5.
3. Call range() With Three Arguments --> Using a Step Value (start, stop, step)
   --> We can control the increment using the step argument
   --> Syntax
   for i in range(1, 10, 2):
   print(i)
4. Using ranges
   --> Ranges are often used in for loops to iterate over a sequence of numbers.
   ```Python
   for i in range(10):
   print(i)
   ```
5. Using a Negative Step (Descending Order)
   --> If step is negative, range() generates numbers in decreasing order.
   --> Syntax
   for i in range(10, 0, -2):
   print(i)
6. Using range() with list()
   The range() function produces a generator, meaning it doesn’t create a list in memory. However, you can convert it into a list if needed:
   --> Synatx
   print(list(range(5)))
   print(list(range(1, 6)))
   print(list(range(5, 20, 3)))

==> Slicing Ranges
--> Like other sequences, ranges can be sliced to extract a subsequence.

```python
r = range(10)
print(r[2])
print(r[:3])
```

**Note** : The first print statement returns the value at index 2, and the second print statement returns a new range object, from index 0 to 3.

==> Membership Testing
Ranges support membership testing with the in operator.

```Python
r = range(0, 10, 2)
print(6 in r)
print(7 in r)
```

The return value is True when the number is present in the range, and False when it is not.

==> Length
==> Ranges support the len() function to get the number of elements in the range.

```Python
r = range(0, 10, 2)
print(len(r))
```

==> Pass Statement
--> Pass is a null statement that does nothing.It is used as a placeholder when a statement is required syntactically but no action is needed.
--> for el in range(10):
pass
