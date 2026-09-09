/**
 * 04-oop-and-prototypes.js
 * HOW TO RUN: plain Node.js -> `node 04-oop-and-prototypes.js`
 * (No DOM APIs used. Also runs fine pasted into a browser console.)
 *
 * Covers (Theory folder):
 *  - Chapter 6: OOP / Prototypes
 *
 * Demonstrates:
 *  1. A class hierarchy with inheritance (Animal -> Dog / Cat).
 *  2. The prototype chain, inspected via Object.getPrototypeOf.
 *  3. A mixin pattern for sharing behaviour across unrelated classes.
 */

"use strict";

// ===========================================================================
// PART 1: Class hierarchy with inheritance.
// ===========================================================================
class Animal {
  #energy = 100; // private field - only accessible inside this class

  constructor(name, sound) {
    this.name = name;
    this.sound = sound;
  }

  speak() {
    return `${this.name} says "${this.sound}"`;
  }

  move() {
    this.#energy -= 10;
    return `${this.name} moves around (energy left: ${this.#energy})`;
  }

  describe() {
    // Calls the (possibly overridden) speak() on whatever subclass instance this is.
    return `${this.constructor.name} named ${this.name}: ${this.speak()}`;
  }
}

class Dog extends Animal {
  constructor(name) {
    super(name, "Woof");
  }

  // Override: dogs move by fetching, not just "moving around".
  move() {
    const base = super.move(); // reuse parent behaviour, then extend it
    return `${base} -> fetching the ball!`;
  }
}

class Cat extends Animal {
  constructor(name) {
    super(name, "Meow");
  }

  speak() {
    // Override speak() entirely.
    return `${this.name} says "${this.sound}" and knocks something off the table`;
  }
}

console.log("=== Class hierarchy with inheritance ===");
const animals = [new Dog("Rex"), new Cat("Whiskers"), new Animal("Generic Critter", "...")];

animals.forEach((animal) => {
  console.log(animal.describe());
  console.log(animal.move());
});

console.log(`\nIs Rex an instance of Animal? ${animals[0] instanceof Animal}`);
console.log(`Is Rex an instance of Dog? ${animals[0] instanceof Dog}`);
console.log(`Is Rex an instance of Cat? ${animals[0] instanceof Cat}`);

// ===========================================================================
// PART 2: The prototype chain, inspected directly.
// Classes are sugar over prototype-based inheritance - let's prove it.
// ===========================================================================
console.log("\n=== Prototype chain inspection ===");
const rex = animals[0];

const rexProto = Object.getPrototypeOf(rex); // Dog.prototype
const dogProtoParent = Object.getPrototypeOf(rexProto); // Animal.prototype
const animalProtoParent = Object.getPrototypeOf(dogProtoParent); // Object.prototype
const topOfChain = Object.getPrototypeOf(animalProtoParent); // null

console.log(`Object.getPrototypeOf(rex) === Dog.prototype? ${rexProto === Dog.prototype}`);
console.log(`Object.getPrototypeOf(Dog.prototype) === Animal.prototype? ${dogProtoParent === Animal.prototype}`);
console.log(
  `Object.getPrototypeOf(Animal.prototype) === Object.prototype? ${animalProtoParent === Object.prototype}`
);
console.log(`Top of the chain (Object.prototype's prototype) is null? ${topOfChain === null}`);

console.log(
  "\nFull chain for 'rex': rex -> Dog.prototype -> Animal.prototype -> Object.prototype -> null"
);

// ===========================================================================
// PART 3: Mixin pattern.
// JS classes support only single inheritance, so mixins let us share
// reusable behaviour (e.g. Serializable, EventEmitter-lite) across otherwise
// unrelated classes, by having a function return a class that extends
// whatever base class you pass it.
// ===========================================================================
const Serializable = (Base) =>
  class extends Base {
    toJSON() {
      // Build a PLAIN object from own enumerable properties first.
      // (Calling JSON.stringify(this) directly would recurse infinitely,
      // since JSON.stringify re-invokes this same toJSON() on `this`.)
      const plainCopy = { ...this };
      return JSON.stringify(plainCopy);
    }
  };

const Comparable = (Base) =>
  class extends Base {
    equals(other) {
      return this.name === other?.name;
    }
  };

// Compose multiple mixins onto a plain class by chaining function calls.
class SerializableComparableDog extends Serializable(Comparable(Dog)) {}

console.log("\n=== Mixin pattern demo ===");
const mixedDog = new SerializableComparableDog("Buddy");
const anotherDog = new SerializableComparableDog("Buddy");
const differentDog = new SerializableComparableDog("Max");

console.log(`Serialized via mixin: ${mixedDog.toJSON()}`);
console.log(`mixedDog.equals(anotherDog) [same name] -> ${mixedDog.equals(anotherDog)}`);
console.log(`mixedDog.equals(differentDog) [different name] -> ${mixedDog.equals(differentDog)}`);
console.log(`mixedDog still behaves like a Dog: ${mixedDog.speak()}`);
console.log(`mixedDog instanceof Dog? ${mixedDog instanceof Dog} (mixins preserve the base class chain)`);
