# Numbers

==> Python's 2 main types for Numbers is int and float (or integers and floating point numbers)
--> 10 // 3 ==> 3 --> floor division - no decimals && returns an int
--> 10 % 3 ==> 1 --> modulo operator - return the remainder. Good for deciding if number is even or odd

# Basic Functions

--> pow(5, 2) ==> 25 --> like doing 5\*\*2
--> abs(-50) ==> 50
--> round(5.46) ==> 5
--> round(5.468, 2)==> 5.47 --> round to nth digit
--> bin(512) ==> '0b1000000000' --> binary format
--> hex(512) ==> '0x200' --> hexadecimal format

# Converting Strings to Numbers

age = input("How old are you?")
age = int(age)
pi = input("What is the value of pi?")
pi = float(pi)

# Strings

==> Strings in Python are sequences of characters enclosed in quotes.
--> len("Hello") ==> 5 --> returns the length of the string
--> "hello".upper() ==> 'HELLO'
--> "HELLO".lower() ==> 'hello'
--> "hello".capitalize()==> 'Hello'
--> "hello world".title() ==> 'Hello World'
--> "hello".startswith("h") ==> True
--> "hello".endswith("o") ==> True
--> "hello".replace("h", "y") ==> 'yello'
# String Concatenation & Formatting
--> "Hello" + " " + "World" ==> 'Hello World'
--> "{} is {}".format("Python", "awesome") ==> 'Python is awesome'
--> f"{10} + {20} = {10+20}" ==> '10 + 20 = 30'

# String Slicing
text = "Python"
--> text[0] ==> 'P'
--> text[-1] ==> 'n'
--> text[0:3] ==> 'Pyt' --> slicing from index 0 to 2
--> text[:3] ==> 'Pyt' --> same as above
--> text[3:] ==> 'hon' --> from index 3 to end

# String Methods
--> "hello world".split() ==> ['hello', 'world']
--> "-".join(["hello", "world"]) ==> 'hello-world'
--> " hello ".strip() ==> 'hello' --> removes leading and trailing spaces
--> "hello".find("e") ==> 1 --> returns the index of first occurrence
--> "hello".count("l") ==> 2 --> count occurrences of 'l'

# Lists
==> Lists are mutable and store multiple items in a single variable.
--> numbers = [1, 2, 3, 4, 5]
--> numbers.append(6) ==> [1, 2, 3, 4, 5, 6]
--> numbers.insert(1, 10) ==> [1, 10, 2, 3, 4, 5, 6]
--> numbers.pop() ==> 6 (removes last item)
--> numbers.remove(10) ==> [1, 2, 3, 4, 5]
--> numbers.sort() ==> [1, 2, 3, 4, 5]
--> numbers.reverse() ==> [5, 4, 3, 2, 1]
--> len(numbers) ==> 5
--> min(numbers) ==> 1
--> max(numbers) ==> 5

# Tuples
==> Tuples are immutable lists.
--> coords = (10, 20)
--> x, y = coords --> tuple unpacking

# Dictionaries
==> Dictionaries store key-value pairs.
--> person = {"name": "John", "age": 30}
--> person["name"] ==> "John"
--> person.get("age") ==> 30
--> person["city"] = "New York"
--> del person["age"]

# Sets
==> Sets are unordered, unique collections of data.
--> s = {1, 2, 3}
--> s.add(4)
--> s.remove(2)
