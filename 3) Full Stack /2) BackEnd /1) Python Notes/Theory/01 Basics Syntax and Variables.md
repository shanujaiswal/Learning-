### Python

--> Python is a very popular simple programming language, and has a very straight forward syntax.
--> Its implementations involve both compilation and interpretation.
--> It is a case-sensitive language. It considers that uppercase and lowercase characters are different.
--> It can be used on a server to create web applications.
--> There are two major Python versions, Python 2 and Python 3. Python 2 and 3 are quite different
--> One difference between Python 2 and 3 is the print statement. In Python 2, the "print" statement is not a function, and therefore it is invoked without parentheses. However, in Python 3, it is a function, and must be invoked with parentheses.
--> A Python error is a code mistake that stops the programs.
--> REPL -- Read Evalate Print Loop

## Indentation

--> Indentation refers to the spaces at the beginning of a code line.
--> Where in other programming languages the indentation in code is for readability only, the indentation in Python is very important.
--> Python uses indentation to indicate a block of code . Python will give an error if skip the indentation:
--> The standard indentation requires standard Python code to use four spaces,but it has to be at least one.

## Input in Python

--> Python allows for user input. Means we are able to ask the user for input.
--> Python stops executing when it comes to the input() function, and continues when the user has given some input.
--> The method is a bit different in Python 3.6 than Python 2.7.
input() statement is used to accept value ( using keyboard) from user
--> Python 3.6 uses the input() method. Python 2.7 uses the raw_input() method.

1. input()--> default is always a str
2. int(input()) ---> value will be integer
3. float(input()) ---> value will be float

--> Python 2.7
username = raw_input("Enter username:")
print("Username is: " + username)

--> Eg :- Python 3.6
username = input("Enter username:")
print("Username is: " + username)

## Comments

--> used to explain Python code.
--> used to make the code more readable.
--> used to prevent execution when testing code.
--> Comments starts with a #, and Python will ignore them:
--> Add a multiline string (triple quotes) in code, and place comment inside it:

## Variables and Types

--> Variable -- containers for storing data values -- Memory allocation
--> Variable is a name given to a memory location in a program.
--> Every variable in Python is an object.
--> Python is completely "object oriented", and not "statically typed". Do not need to declare variables before using them, or declare their type.
--> A variable can have a short name (like x and y) or a more descriptive name (age, carname, total_volume).

# Rules for Python variables:

--> A variable name must start with a letter or the underscore character
--> A variable name cannot start with a number
--> A variable name can only contain alpha-numeric characters and underscores (A-z, 0-9, and \_ )
--> Variable names are case-sensitive (age, Age and AGE are three different variables)
--> A variable name cannot be any of the Python keywords.

# Legal variable names:

myvar = "John"
my_var = "John"
\_my_var = "John"
myVar = "John"
MYVAR = "John"
myvar2 = "John"

# Illegal variable names:

2myvar = "John"
my-var = "John"
my var = "John"

# Multi Words Variable Names

--> Variable names with more than one word can be difficult to read.
--> There are several techniques can use to make them more readable:

1. Camel Case
   --> Each word, except the first, starts with a capital letter:
   myVariableName = "John"
2. Pascal Case
   --> Each word starts with a capital letter:
   MyVariableName = "John"
3. Snake Case
   --> Each word is separated by an underscore character:
   my_variable_name = "John"

# Variables - Assign Multiple Values

==> Many Values to Multiple Variables
--> correct syntax to assign values to multiple variables in one line
x,y,z = "Orange", "Banana", "Cherry"
==> One Value to Multiple Variables
--> The same value to multiple variables in one line:
--> correct syntax to add the value to 3 variables in one statement
x = y = z = "Hello World"
==> Unpack a Collection
--> If you have a collection of values in a list, tuple etc. Python allows to extract the values into variables. This is called unpacking.
==> Unpack a list:

```python
fruits = ["apple", "banana", "cherry"]
x, y, z = fruits
print(x)
print(y)
print(z)
```

# Output Variables

==> Output Variables
--> The Python print() function is often used to output variables.
--> In the print() function, when try to combine a string and a number with the + operator, Python will give an error, The best way to output multiple variables in the print() function is to separate them with commas, which even support different data types:
--> write end = "" to avoid/prevent print a new next line at the end.
--> In the print() function, output multiple variables, separated by a comma:

```python
x = "Python"
y = "is"
z = "awesome"
print(x, y, z)
```

--> Can also use the + operator to output multiple variables, For numbers, the + character works as a mathematical operator

```Python
x = "Python "
y = "is "
z = "awesome"
print(x + y + z)
```

# Global Variable

--> Variables that are created outside of a function
are known as global variables.
--> Global variables can be used by everyone, both inside of functions and outside.
--> If you create a variable with the same name inside a function, this variable will be local, and can only be used inside the function. The global variable with the same name will remain as it was, global and with the original value.

==> The global Keyword
--> Normally, when you create a variable inside a function, that variable is local, and can only be used inside that function.
--> To create a global variable inside a function, you can use the global keyword.
--> Use the global keyword if want to change a global variable inside a function.(To change the value of a global variable inside a function, refer to the variable by using the global keyword)
```Python
def myfunc():
  global x
  x = "fantastic"
```
