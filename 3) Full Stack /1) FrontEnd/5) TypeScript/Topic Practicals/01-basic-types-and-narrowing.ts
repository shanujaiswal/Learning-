/**
 * 01 - Basic Types, Literal Types, Unions, and Type Narrowing
 * Covers Theory chapters: 01 (Fundamentals and Basic Types), 08 (Type Narrowing and Guards)
 *
 * Run:   npx tsc --noEmit "01-basic-types-and-narrowing.ts"
 * Or:    npx ts-node "01-basic-types-and-narrowing.ts"
 */

// --- Basic primitive types ---
const age: number = 27;
const username: string = "vanisha";
const isActive: boolean = true;
const tags: string[] = ["frontend", "typescript"];
const coords: [number, number] = [12.9, 77.6]; // tuple type

// --- Literal types ---
type Direction = "north" | "south" | "east" | "west";

function move(direction: Direction): string {
  return `Moving ${direction}`;
}

move("north");
// move("up"); // would error: "up" is not assignable to type Direction

// --- Union types ---
type Id = string | number;

function printId(id: Id): void {
  if (typeof id === "string") {
    console.log(`ID (string): ${id.toUpperCase()}`);
  } else {
    console.log(`ID (number): ${id.toFixed(0)}`);
  }
}

printId(42);
printId("abc-123");

// --- Discriminated union: Shape ---
interface Circle {
  kind: "circle";
  radius: number;
}

interface Square {
  kind: "square";
  sideLength: number;
}

interface Rectangle {
  kind: "rectangle";
  width: number;
  height: number;
}

type Shape = Circle | Square | Rectangle;

// Exhaustiveness helper: if this function is ever called, we forgot a case.
function assertNever(value: never): never {
  throw new Error(`Unhandled case: ${JSON.stringify(value)}`);
}

function area(shape: Shape): number {
  switch (shape.kind) {
    case "circle":
      // Narrowed to Circle here
      return Math.PI * shape.radius ** 2;
    case "square":
      // Narrowed to Square here
      return shape.sideLength ** 2;
    case "rectangle":
      // Narrowed to Rectangle here
      return shape.width * shape.height;
    default:
      // If a new shape kind is added to the union and not handled above,
      // TypeScript will flag this line because `shape` would no longer be `never`.
      return assertNever(shape);
  }
}

const shapes: Shape[] = [
  { kind: "circle", radius: 2 },
  { kind: "square", sideLength: 3 },
  { kind: "rectangle", width: 4, height: 5 },
];

shapes.forEach((s) => console.log(`${s.kind} area = ${area(s).toFixed(2)}`));

// --- Custom type guard (user-defined type predicate) ---
interface Dog {
  species: "dog";
  bark(): string;
}

interface Cat {
  species: "cat";
  meow(): string;
}

type Pet = Dog | Cat;

function isDog(pet: Pet): pet is Dog {
  return pet.species === "dog";
}

function speak(pet: Pet): string {
  if (isDog(pet)) {
    return pet.bark(); // narrowed to Dog
  }
  return pet.meow(); // narrowed to Cat
}

const rex: Dog = { species: "dog", bark: () => "Woof!" };
const whiskers: Cat = { species: "cat", meow: () => "Meow!" };

console.log(speak(rex));
console.log(speak(whiskers));

// --- Narrowing with `in` operator ---
function hasBark(pet: Pet): boolean {
  return "bark" in pet;
}

console.log(hasBark(rex), hasBark(whiskers));

export { area, speak, printId, move, Shape, Pet };
