/**
 * 04 - Classes: Access Modifiers, Abstract Classes, Constructor Shorthand
 * Covers Theory chapter: 03 (Classes and Functions in TypeScript)
 *
 * Note on title: this file focuses on classes and access modifiers.
 * TypeScript's experimental decorators require a tsconfig flag
 * (experimentalDecorators / or the newer stage-3 decorators need no flag
 * but a modern target); to keep this file compiling cleanly under a plain
 * `strict: true` config with no extra flags, no decorator syntax is used.
 * The class-based patterns below (access modifiers, abstract members,
 * constructor property shorthand) are the practical foundation decorators
 * are usually layered on top of.
 *
 * Run:   npx tsc --noEmit "04-classes-and-decorators.ts"
 * Or:    npx ts-node "04-classes-and-decorators.ts"
 */

// ---------------------------------------------------------------------------
// Abstract base class: cannot be instantiated directly, defines a contract
// (abstract method) that every subclass must implement.
// ---------------------------------------------------------------------------
abstract class Employee {
  // readonly: can only be assigned once, in the constructor.
  readonly id: string;

  // protected: visible to this class and subclasses, not outside.
  protected baseSalary: number;

  // private: visible only within this exact class.
  private ssn: string;

  constructor(id: string, baseSalary: number, ssn: string) {
    this.id = id;
    this.baseSalary = baseSalary;
    this.ssn = ssn;
  }

  // Abstract method: every concrete subclass must provide its own version.
  abstract calculatePay(): number;

  // Concrete method shared by all subclasses, uses the protected field.
  describe(): string {
    return `Employee ${this.id} earns $${this.calculatePay().toFixed(2)}`;
  }

  // Private members are not accessible outside the class, but methods
  // within the class can expose a safe, masked view of them.
  maskedSsn(): string {
    return `***-**-${this.ssn.slice(-4)}`;
  }
}

// ---------------------------------------------------------------------------
// Concrete subclass using constructor parameter property shorthand:
// prefixing a constructor parameter with an access modifier both declares
// the field and assigns it, without a separate `this.x = x` line.
// ---------------------------------------------------------------------------
class SalariedEmployee extends Employee {
  constructor(
    id: string,
    baseSalary: number,
    ssn: string,
    private readonly annualBonus: number, // shorthand: declares + assigns
  ) {
    super(id, baseSalary, ssn);
  }

  calculatePay(): number {
    return this.baseSalary / 12 + this.annualBonus / 12;
  }
}

class HourlyEmployee extends Employee {
  constructor(
    id: string,
    baseSalary: number,
    ssn: string,
    private readonly hoursWorked: number,
    private readonly hourlyRate: number,
  ) {
    super(id, baseSalary, ssn);
  }

  calculatePay(): number {
    return this.baseSalary / 12 + this.hoursWorked * this.hourlyRate;
  }
}

// A manager extends SalariedEmployee and adds team-management behavior,
// demonstrating a second level of inheritance.
class Manager extends SalariedEmployee {
  private reports: Employee[] = [];

  addReport(employee: Employee): void {
    this.reports.push(employee);
  }

  teamSize(): number {
    return this.reports.length;
  }

  override describe(): string {
    return `${super.describe()} and manages ${this.teamSize()} people`;
  }
}

// const e = new Employee("e0", 50000, "123-45-6789"); // would error: cannot instantiate an abstract class

const alice = new SalariedEmployee("e1", 90000, "111-22-3333", 6000);
const bob = new HourlyEmployee("e2", 0, "222-33-4444", 160, 35);
const carol = new Manager("e3", 120000, "333-44-5555", 10000);

carol.addReport(alice);
carol.addReport(bob);

console.log(alice.describe(), "|", alice.maskedSsn());
console.log(bob.describe(), "|", bob.maskedSsn());
console.log(carol.describe(), "|", carol.maskedSsn());

// alice.baseSalary; // would error: 'baseSalary' is protected
// alice.ssn; // would error: 'ssn' is private and not accessible

export { Employee, SalariedEmployee, HourlyEmployee, Manager };
