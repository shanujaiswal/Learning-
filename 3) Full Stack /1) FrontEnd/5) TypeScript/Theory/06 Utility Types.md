# Why Utility Types Exist

--> Utility types are built-in generic helpers that TRANSFORM an existing type into a new one -- instead of hand-writing a near-duplicate interface every time you need a slightly different shape of an existing type.

# Partial and Required

--> `Partial<T>` -- makes every property optional. Extremely common for update/PATCH functions, where a caller might only send some fields to change.
--> `Required<T>` -- the opposite -- makes every property mandatory, even ones originally marked optional.

```typescript
interface User {
  id: number;
  name: string;
  email?: string;
}

function updateUser(id: number, changes: Partial<User>) {
  // changes might be { name: "New Name" } -- doesn't need every field
}

type StrictUser = Required<User>;   // email is now mandatory, not optional
```

# Pick and Omit

--> `Pick<T, Keys>` -- builds a new type containing ONLY the listed properties.
--> `Omit<T, Keys>` -- the opposite -- builds a new type with everything EXCEPT the listed properties.

```typescript
type UserPreview = Pick<User, "id" | "name">;      // { id: number; name: string }
type UserWithoutEmail = Omit<User, "email">;         // { id: number; name: string }
```

# Readonly

--> Makes every property immutable at the type level -- attempting to reassign a property after creation is a compile-time error.

```typescript
const config: Readonly<User> = { id: 1, name: "Alice" };
// config.name = "Bob";   // Error -- Cannot assign to 'name' because it is a read-only property
```

# Record -- Building Object Types From Key/Value Types

--> `Record<Keys, ValueType>` -- constructs an object type where every key is of type `Keys` and every value is of type `ValueType`. Very common for lookup maps/dictionaries.

```typescript
type Role = "admin" | "editor" | "viewer";

const rolePermissions: Record<Role, string[]> = {
  admin: ["read", "write", "delete"],
  editor: ["read", "write"],
  viewer: ["read"],
};
```

# Exclude and Extract (Union Type Filtering)

--> `Exclude<UnionType, ExcludedMembers>` -- removes specific members from a union type.
--> `Extract<UnionType, Members>` -- the opposite -- keeps only members matching another type.

```typescript
type Status = "active" | "inactive" | "banned" | "pending";

type ActionableStatus = Exclude<Status, "banned">;   // "active" | "inactive" | "pending"
type ApprovedStatus = Extract<Status, "active" | "inactive">;   // "active" | "inactive"
```

# ReturnType and Parameters

--> `ReturnType<F>` -- extracts a function's return type, without you having to duplicate/hardcode it.
--> `Parameters<F>` -- extracts a function's parameter types as a tuple.

```typescript
function createUser(name: string, age: number) {
  return { id: Date.now(), name, age };
}

type NewUser = ReturnType<typeof createUser>;   // { id: number; name: string; age: number }
type CreateUserArgs = Parameters<typeof createUser>;   // [string, number]
```

--> Useful for keeping derived types in sync with a function's actual implementation automatically -- if `createUser`'s return shape changes, `NewUser` updates itself, rather than becoming silently outdated like a manually duplicated interface would.
