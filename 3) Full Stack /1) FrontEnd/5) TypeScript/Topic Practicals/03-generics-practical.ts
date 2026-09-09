/**
 * 03 - Practical Generics: a generic Repository<T> and a pluck<T, K> helper
 * Covers Theory chapter: 05 (Generics Deep Dive)
 *
 * Run:   npx tsc --noEmit "03-generics-practical.ts"
 * Or:    npx ts-node "03-generics-practical.ts"
 */

// ---------------------------------------------------------------------------
// Generic constraint: T must at least have a string `id` field so the
// repository can index and identify records without knowing anything else
// about the shape of T.
// ---------------------------------------------------------------------------
interface HasId {
  id: string;
}

class Repository<T extends HasId> {
  private store: Map<string, T> = new Map();

  add(item: T): T {
    if (this.store.has(item.id)) {
      throw new Error(`Item with id "${item.id}" already exists`);
    }
    this.store.set(item.id, item);
    return item;
  }

  getById(id: string): T | undefined {
    return this.store.get(id);
  }

  getAll(): T[] {
    return Array.from(this.store.values());
  }

  update(id: string, patch: Partial<Omit<T, "id">>): T {
    const existing = this.store.get(id);
    if (!existing) {
      throw new Error(`Item with id "${id}" not found`);
    }
    const updated: T = { ...existing, ...patch };
    this.store.set(id, updated);
    return updated;
  }

  delete(id: string): boolean {
    return this.store.delete(id);
  }

  count(): number {
    return this.store.size;
  }

  find(predicate: (item: T) => boolean): T[] {
    return this.getAll().filter(predicate);
  }
}

// --- Usage: a Task repository ---
interface Task extends HasId {
  id: string;
  title: string;
  done: boolean;
  priority: 1 | 2 | 3;
}

const tasks = new Repository<Task>();

tasks.add({ id: "t1", title: "Write TS notes", done: false, priority: 1 });
tasks.add({ id: "t2", title: "Review PR", done: false, priority: 2 });
tasks.add({ id: "t3", title: "Deploy", done: true, priority: 3 });

tasks.update("t1", { done: true });

const openHighPriority = tasks.find((t) => !t.done && t.priority <= 2);
console.log("Open high-priority tasks:", openHighPriority);
console.log("Total tasks:", tasks.count());

// ---------------------------------------------------------------------------
// Generic utility: pluck<T, K extends keyof T>
// Extracts the values for a given key from a list of objects, with the
// return type inferred precisely as T[K][] rather than any[].
// ---------------------------------------------------------------------------
function pluck<T, K extends keyof T>(items: T[], key: K): Array<T[K]> {
  return items.map((item) => item[key]);
}

const allTitles: string[] = pluck(tasks.getAll(), "title");
const allPriorities: Array<1 | 2 | 3> = pluck(tasks.getAll(), "priority");

console.log("Titles:", allTitles);
console.log("Priorities:", allPriorities);

// pluck(tasks.getAll(), "nonexistent"); // would error: "nonexistent" is not keyof Task

// ---------------------------------------------------------------------------
// A second generic constraint example: a function that merges two objects,
// constrained so both arguments must be plain object records.
// ---------------------------------------------------------------------------
function merge<A extends object, B extends object>(a: A, b: B): A & B {
  return { ...a, ...b };
}

const merged = merge({ name: "Ada" }, { age: 30 });
console.log(merged.name, merged.age);

export { Repository, HasId, Task, pluck, merge };
