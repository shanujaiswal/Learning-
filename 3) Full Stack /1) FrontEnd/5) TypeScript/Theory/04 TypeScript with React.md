# Setting Up TypeScript in a React Project

```bash
# Vite (recommended)
npm create vite@latest my-app -- --template react-ts

# Create React App (legacy)
npx create-react-app my-app --template typescript
```

--> Component files use the .tsx extension (not .ts) -- the "x" tells the compiler the file contains JSX syntax.

# Typing Component Props

```typescript
interface ButtonProps {
  label: string;
  onClick: () => void;
  disabled?: boolean; // Optional prop
  variant?: "primary" | "secondary"; // Restricted to specific string values
}

function Button({ label, onClick, disabled = false, variant = "primary" }: ButtonProps) {
  return (
    <button onClick={onClick} disabled={disabled} className={variant}>
      {label}
    </button>
  );
}
```

--> children prop -- typed with React.ReactNode, which covers strings, numbers, JSX elements, fragments, arrays of these, etc.

```typescript
interface CardProps {
  title: string;
  children: React.ReactNode;
}

function Card({ title, children }: CardProps) {
  return (
    <div>
      <h2>{title}</h2>
      {children}
    </div>
  );
}
```

# Typing useState

```typescript
const [count, setCount] = useState<number>(0);        // Explicit type (often inferable from initial value)
const [name, setName] = useState("");                  // Inferred as string automatically
const [user, setUser] = useState<User | null>(null);    // Needed when initial value doesn't reveal the full type
const [items, setItems] = useState<string[]>([]);       // Empty array -- type must be explicit, TS can't infer from []
```

# Typing useEffect, useRef, and Event Handlers

```typescript
useEffect(() => {
  const timer = setTimeout(() => console.log("done"), 1000);
  return () => clearTimeout(timer); // Cleanup function -- return type must be void or a cleanup function
}, []);

// useRef for a DOM element
const inputRef = useRef<HTMLInputElement>(null);
// inputRef.current is HTMLInputElement | null -- must check for null before use
inputRef.current?.focus();

// useRef for a mutable value (not a DOM node)
const countRef = useRef<number>(0);

// Typing event handlers
function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
  console.log(e.target.value);
}

function handleClick(e: React.MouseEvent<HTMLButtonElement>) {
  console.log("clicked");
}

<input onChange={handleChange} />
<button onClick={handleClick}>Click</button>
```

# Typing useContext

```typescript
interface ThemeContextType {
  theme: "light" | "dark";
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined); // undefined default forces a check

function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error("useTheme must be used within a ThemeProvider"); // Runtime safety net matching the type
  }
  return context; // TypeScript now knows this is ThemeContextType, not undefined
}
```

# Typing Custom Hooks

```typescript
function useFetch<T>(url: string): { data: T | null; loading: boolean; error: string | null } {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch(url)
      .then((res) => res.json())
      .then((json: T) => setData(json))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [url]);

  return { data, loading, error };
}

// Usage -- caller specifies what shape of data to expect
const { data, loading } = useFetch<User[]>("/api/users");
```

# Typing Redux/Zustand State

```typescript
// Zustand example
interface CartState {
  items: { id: number; name: string; price: number }[];
  add: (item: { id: number; name: string; price: number }) => void;
}

const useCartStore = create<CartState>((set) => ({
  items: [],
  add: (item) => set((state) => ({ items: [...state.items, item] })),
}));
```

# Common React + TypeScript Gotchas

--> Event handler types differ per element -- React.ChangeEvent<HTMLInputElement> vs React.ChangeEvent<HTMLSelectElement> vs React.ChangeEvent<HTMLTextAreaElement> are all distinct.
--> Optional props with default values -- destructure with a default (label = "Click me") rather than marking it required; TypeScript understands the prop is optional at the call site once a default is provided.
--> Third-party libraries without built-in types -- install community types separately: npm install -D @types/<package-name> (e.g. @types/node, @types/react).
--> React.FC<Props> (function component type) is now generally discouraged in favor of a plain typed function -- React.FC implicitly adds a children prop and has awkward generic support; most modern style guides recommend `function MyComponent(props: Props)` instead.

# Deep Dive -- Generic Components

--> A component can itself be generic, letting it work correctly with different data shapes while keeping full type safety -- extremely useful for genuinely reusable components like a `<List>` or `<Table>` that render arbitrary item types.

```typescript
interface ListProps<T> {
  items: T[];
  renderItem: (item: T) => React.ReactNode;
  keyExtractor: (item: T) => string | number;
}

function List<T>({ items, renderItem, keyExtractor }: ListProps<T>) {
  return (
    <ul>
      {items.map((item) => (
        <li key={keyExtractor(item)}>{renderItem(item)}</li>
      ))}
    </ul>
  );
}

// Usage -- TypeScript infers T as "User" from the "users" array passed in, and type-checks renderItem accordingly
<List
  items={users}
  keyExtractor={(user) => user.id}
  renderItem={(user) => <span>{user.name}</span>}   // TypeScript knows "user" is a User here, with full autocomplete
/>
```

--> Without generics, this same reusable `List` component would need `any` for its items, losing all type safety for `renderItem`/`keyExtractor` -- generics let one component definition serve any data shape while still catching type errors (e.g. accessing a property that doesn't exist on the actual item type) at compile time.

# Deep Dive -- Typing Component Composition (React.ReactElement vs React.ReactNode)

--> `React.ReactNode` (used for `children` in the Card example above) is the BROADEST type -- it accepts anything renderable: strings, numbers, booleans, `null`, arrays, and JSX elements. `React.ReactElement` is narrower -- it specifically represents an actual JSX element (`<Foo />`), excluding plain strings/numbers/null.

```typescript
interface TabsProps {
  children: React.ReactElement[];   // Requires an array of actual JSX elements specifically, not arbitrary renderable content
}

interface WrapperProps {
  children: React.ReactNode;         // Accepts anything renderable -- the more common, more flexible choice
}
```

--> Use `React.ReactElement` specifically when a component needs to inspect or clone its children as actual elements (e.g. a `<Tabs>` component that reads each child's `props.label` to build a tab bar) -- `React.cloneElement()` and `React.Children.map()` (both used for this kind of children-manipulation pattern) work with actual elements, not arbitrary `ReactNode` values like plain strings.
