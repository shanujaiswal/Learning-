/**
 * 02 - Interface Hierarchies and Utility Types
 * Covers Theory chapters: 02 (Interfaces, Types, Advanced Types), 06 (Utility Types)
 *
 * Run:   npx tsc --noEmit "02-interfaces-and-utility-types.ts"
 * Or:    npx ts-node "02-interfaces-and-utility-types.ts"
 */

// --- Base interface ---
interface Entity {
  id: string;
  createdAt: Date;
}

// --- Interface hierarchy via extends ---
interface Person extends Entity {
  firstName: string;
  lastName: string;
  email: string;
}

interface Employee extends Person {
  employeeCode: string;
  department: string;
  salary: number;
  manager?: Employee; // optional, recursive reference
}

const jane: Employee = {
  id: "e-001",
  createdAt: new Date("2024-01-15"),
  firstName: "Jane",
  lastName: "Doe",
  email: "jane.doe@example.com",
  employeeCode: "EMP-100",
  department: "Engineering",
  salary: 95000,
};

// ---------------------------------------------------------------------------
// Partial<T>: makes every property optional.
// Useful for PATCH-style update payloads where only some fields are sent.
// ---------------------------------------------------------------------------
type EmployeeUpdate = Partial<Employee>;

function updateEmployee(current: Employee, patch: EmployeeUpdate): Employee {
  // All fields in `patch` are optional, so a spread-merge is safe.
  return { ...current, ...patch };
}

const promoted = updateEmployee(jane, { salary: 105000, department: "Platform" });

// ---------------------------------------------------------------------------
// Pick<T, K>: builds a new type with only the listed keys.
// Useful for a slim "summary" view (e.g. list rows in a UI table).
// ---------------------------------------------------------------------------
type EmployeeSummary = Pick<Employee, "id" | "firstName" | "lastName" | "department">;

const summary: EmployeeSummary = {
  id: jane.id,
  firstName: jane.firstName,
  lastName: jane.lastName,
  department: jane.department,
  // salary, email, etc. are NOT part of this type -- including them would error.
};

// ---------------------------------------------------------------------------
// Omit<T, K>: builds a new type with every key EXCEPT the listed ones.
// Useful for "create" payloads where server-generated fields are excluded.
// ---------------------------------------------------------------------------
type NewEmployeeInput = Omit<Employee, "id" | "createdAt" | "manager">;

function createEmployee(input: NewEmployeeInput): Employee {
  return {
    ...input,
    id: `e-${Math.random().toString(36).slice(2, 8)}`,
    createdAt: new Date(),
  };
}

const hired = createEmployee({
  firstName: "Sam",
  lastName: "Lee",
  email: "sam.lee@example.com",
  employeeCode: "EMP-101",
  department: "Design",
  salary: 80000,
});

// ---------------------------------------------------------------------------
// Record<K, T>: builds an object type whose keys are K and values are T.
// Useful for lookup maps / indexes keyed by a known literal union or string.
// ---------------------------------------------------------------------------
type Department = "Engineering" | "Design" | "Platform" | "Sales";

type HeadcountByDepartment = Record<Department, number>;

const headcount: HeadcountByDepartment = {
  Engineering: 12,
  Design: 4,
  Platform: 3,
  Sales: 6,
  // Every key of Department is required, and no extra keys are allowed.
};

// ---------------------------------------------------------------------------
// Readonly<T>: makes every property immutable (compile-time only).
// Useful for values passed around that must not be mutated, e.g. a frozen
// snapshot of an employee record shown in an audit log.
// ---------------------------------------------------------------------------
type EmployeeSnapshot = Readonly<Employee>;

const snapshot: EmployeeSnapshot = { ...jane };
// snapshot.salary = 999999; // would error: cannot assign to a readonly property

function printSnapshot(s: EmployeeSnapshot): void {
  console.log(`${s.firstName} ${s.lastName} - ${s.department} ($${s.salary})`);
}

printSnapshot(snapshot);
printSnapshot(promoted as EmployeeSnapshot);

console.log(summary, hired, headcount);

export {
  Entity,
  Person,
  Employee,
  EmployeeUpdate,
  EmployeeSummary,
  NewEmployeeInput,
  HeadcountByDepartment,
  EmployeeSnapshot,
};
