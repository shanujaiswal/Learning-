# Why This Matters

--> `07) JVM Internals and Memory Management/Theory/04 Class Loading and Reflection.md` introduced the reflection basics: getting a `Class` object, inspecting/invoking members, `setAccessible`, and the performance/module-system caveats. This file goes further into territory that chapter didn't cover: **dynamic proxies** (how frameworks synthesize whole implementations of an interface at runtime), a deeper look at `Method`/`Field`/`Constructor`, the mechanics and risk profile of `setAccessible`, a more precise account of reflection's performance cost, and the security model around it. Where content overlaps with that file, it's referenced rather than repeated.

# Dynamic Proxies -- `java.lang.reflect.Proxy`

--> A dynamic proxy is a class GENERATED AT RUNTIME that implements one or more interfaces you specify, routing every method call through a single handler you provide -- no hand-written implementation class exists anywhere; the JVM synthesizes the bytecode on the fly. This is precisely how `getAnnotation()` (File 03) returns working annotation instances, how many mocking libraries (older Mockito internals, EasyMock) build mock objects, and how Spring builds JDK-proxy-based AOP interceptors and lazy-loading Hibernate entities.

```java
interface Greeter {
    String greet(String name);
}

InvocationHandler handler = (proxy, method, args) -> {
    System.out.println("Before calling: " + method.getName());
    Object result = "Hello, " + args[0] + "!";     // the "real" logic, here just inlined
    System.out.println("After calling: " + method.getName());
    return result;
};

Greeter greeter = (Greeter) Proxy.newProxyInstance(
        Greeter.class.getClassLoader(),
        new Class<?>[]{Greeter.class},
        handler);

System.out.println(greeter.greet("World"));   // triggers handler.invoke(...), prints logs, returns "Hello, World!"
```

--> Mechanics:

```text
1. Proxy.newProxyInstance(loader, interfaces, handler) generates a new class at runtime implementing ALL
   given interfaces (can be more than one -- useful for combining e.g. Serializable with a business interface).
2. EVERY method call on the resulting object -- regardless of which interface it came from -- is redirected to
   handler.invoke(proxyInstance, method, args), where `method` identifies WHICH method was called reflectively.
3. The handler decides what to actually do: delegate to a real object, log, validate, cache, short-circuit --
   this indirection is the entire value proposition (cross-cutting behavior without modifying real classes).
```

--> **Hard limitation: JDK dynamic proxies can only implement INTERFACES, never proxy a concrete class** -- proxying a class (no interface required) needs bytecode-generation libraries instead (CGLIB, ByteBuddy), which Spring falls back to automatically when a bean has no interface to proxy against. This is why Spring AOP historically required either "program to an interface" or an extra CGLIB dependency.

```java
// A minimal real-world-shaped example: a lazy-loading proxy
static <T> T lazy(Class<T> iface, Supplier<T> realSupplier) {
    InvocationHandler h = (proxy, method, args) -> {
        T real = realSupplier.get();          // only fetched when a method is actually called
        return method.invoke(real, args);
    };
    return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, h));
}
```

# `Method`, `Field`, `Constructor` -- A Closer Look

--> All three extend `AccessibleObject` (which supplies `setAccessible`) and implement `Member` (which supplies `getName()`, `getModifiers()`, `getDeclaringClass()`). Beyond basic invocation (covered in the JVM Internals chapter), each exposes richer introspection:

```java
Method m = MyClass.class.getDeclaredMethod("process", String.class, int.class);

m.getReturnType();              // Class<?> of the return type
m.getParameterTypes();          // Class<?>[] -- erased parameter types
m.getGenericParameterTypes();   // Type[] -- reified generic signatures, e.g. List<String> not just List
m.getExceptionTypes();          // checked exceptions in the throws clause
m.getModifiers();               // int bitmask -- decode with java.lang.reflect.Modifier.isPublic(...) etc.
m.isVarArgs();                  // true if declared with (String... args)
m.isDefault();                  // true for an interface default method
m.isSynthetic();                // true for compiler-generated methods (bridge methods, lambda-related, etc.)
m.getAnnotations();             // covered in File 03
```

```java
Field f = MyClass.class.getDeclaredField("balance");
f.getType();                    // Class<?> of the declared field type
f.isEnumConstant();
f.getModifiers();               // combine with Modifier.isFinal(f.getModifiers()) to check for `final`

Constructor<MyClass> c = MyClass.class.getDeclaredConstructor(String.class, int.class);
c.newInstance("x", 1);
c.getParameterCount();
```

--> `Modifier` is a static utility class for decoding the `int` bitmask returned by `getModifiers()`:

```java
int mods = m.getModifiers();
Modifier.isPublic(mods);
Modifier.isStatic(mods);
Modifier.isFinal(mods);
Modifier.isAbstract(mods);
System.out.println(Modifier.toString(mods));   // e.g. "public static final"
```

## `MethodHandle` -- The Faster Alternative

```java
import java.lang.invoke.*;

MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodHandle mh = lookup.findVirtual(MyClass.class, "process",
        MethodType.methodType(String.class, int.class));

String result = (String) mh.invoke(myClassInstance, 42);
```

--> `java.lang.invoke.MethodHandle` (Java 7+, and the mechanism underlying `invokedynamic` -- how lambdas are actually implemented under the hood) is a lower-level, more JIT-friendly alternative to `Method.invoke()`. Once "bound," a `MethodHandle` invocation can be inlined and optimized by the JIT much closer to a direct call, whereas `Method.invoke()`'s extra access-check and argument-boxing machinery resists those optimizations. Most application code doesn't need to reach for `MethodHandle` directly, but it's worth knowing it exists as the mechanism serious frameworks (and the JDK itself, for lambdas) use when reflection-like dynamism needs to be fast.

# `setAccessible` -- Mechanics and Risk

--> Recap from the JVM Internals chapter: `setAccessible(true)` bypasses Java's normal access checks (private/protected/package-private) for reflective access. Going deeper:

```java
Field secretField = Account.class.getDeclaredField("pin");
secretField.setAccessible(true);         // may throw InaccessibleObjectException (Java 9+) if the module
                                          // containing Account has not `opens`-ed its package
String pin = (String) secretField.get(account);
```

```text
setAccessible(true) on...
  - a private field/method within your OWN module/classpath  -- works, always has.
  - a private field/method in ANOTHER named module           -- throws InaccessibleObjectException UNLESS
                                                                  that module's module-info.java declares
                                                                  `opens com.that.package;` (or
                                                                  `opens com.that.package to your.module;`)
                                                                  or the JVM is launched with
                                                                  --add-opens that.module/com.that.package=ALL-UNNAMED
  - a FINAL field                                              -- since Java 17, reflective WRITES to final
                                                                  fields are much more aggressively blocked/
                                                                  warned against even with setAccessible(true);
                                                                  behavior has tightened release over release
                                                                  and should not be relied upon.
```

--> This tightening is a deliberate, ongoing JDK trend ("strong encapsulation," the long-term direction started by JPMS in Java 9 and increasingly enforced in Java 17+) -- code that reflectively pokes into JDK internals or unopened modules is INCREASINGLY likely to break on newer JDK versions, which is precisely why frameworks (Mockito, Jackson) have had to repeatedly adapt their reflective tricks across JDK releases, and why upgrading a reflection-heavy legacy application's JDK version is a nontrivial migration task, not just a version bump.

# Performance Cost of Reflection -- A Closer Look

--> Where reflection actually spends extra time, roughly in order of impact:

```text
1. Access checks -- every reflective call re-verifies access permissions unless setAccessible(true) was
   already called (and even then there's residual bookkeeping) -- a direct call has none of this at runtime,
   it's resolved once at link time.
2. Argument boxing/varargs array allocation -- Method.invoke(Object target, Object... args) forces primitive
   arguments to be boxed and packed into an Object[] on every call, then unboxed on return -- pure overhead
   that a direct typed call never pays.
3. Lost JIT inlining -- the JIT compiler can inline and speculatively optimize a direct call site based on
   the actual receiver types it observes; a reflective call through Method.invoke goes through a much more
   generic, harder-to-specialize code path, defeating many such optimizations.
4. Lookup cost -- getDeclaredMethod/getDeclaredField themselves scan and validate the class's metadata;
   trivial as a one-off, but expensive if repeated per-call rather than cached.
```

--> **Practical mitigation, in increasing order of effort**: (1) cache the looked-up `Method`/`Field`/`Constructor` object once (never call `getDeclaredMethod` per invocation) -- this alone eliminates most avoidable overhead in typical framework code; (2) call `setAccessible(true)` once, up front, rather than per call; (3) for genuinely hot paths, migrate to `MethodHandle`s, or generate bytecode ahead of time (as MapStruct/Dagger do -- File 04) to avoid reflection at the hot path entirely.
--> In absolute terms, modern JVMs have narrowed this gap substantially since early Java versions (`Method.invoke` was once dramatically slower) -- reflection at STARTUP/wiring time (dependency injection, one-time configuration scanning) is essentially never a real-world bottleneck; the concern is specifically reflection inside a loop that runs millions of times per second.

# Security Implications

--> Reflection, especially combined with `setAccessible(true)`, is a deliberate hole in Java's normal encapsulation guarantees -- worth treating with the same caution as any other privileged operation:

```text
- Bypassing access control -- private fields/methods, meant to be implementation details invisible outside
  their class, become readable/callable/mutable from anywhere reflection is permitted to run -- this
  defeats the entire point of `private` as an enforced (not just advisory) boundary.
- Mutating supposedly-immutable state -- reflection can, in some cases, still mutate `final` instance fields
  after construction (JVM/version-dependent, and increasingly restricted -- see above), silently breaking
  invariants code elsewhere assumes always hold (e.g. a "frozen" configuration object, a security token
  meant to never change after creation).
- Untrusted code + reflection -- historically, running untrusted code under a SecurityManager was the
  mitigation (denying ReflectPermission("suppressAccessChecks") to sandbox reflection); SecurityManager is
  REMOVED as of Java 24 (deprecated for removal starting Java 17) -- module boundaries (JPMS `opens`/`exports`)
  are now the primary remaining defense against reflective access into code that doesn't want to expose it.
- Deserialization-adjacent risk -- reflective instantiation (constructing arbitrary classes by name from
  untrusted input, e.g. a class name embedded in serialized data or a request parameter) is a classic vector
  for deserialization exploits; NEVER pass externally-controlled strings into Class.forName(...) followed by
  reflective instantiation without a strict allow-list of expected types.
```

--> Because of this, reflective library code should validate untrusted class/method names against an explicit allow-list rather than trusting external input directly, and application code exposed to a module system should treat `opens` declarations deliberately -- opening a package for reflective access (e.g. so a JSON library can populate private fields) is a real API surface decision, not a formality.

# Gotchas and Best Practices

--> **Dynamic proxies can only proxy interfaces** -- reaching for `Proxy.newProxyInstance` against a concrete class silently isn't possible; use CGLIB/ByteBuddy-style subclassing proxies instead when there's no interface.
--> **`InvocationHandler.invoke` receives the PROXY instance itself as its first argument** -- calling a method on that same proxy instance from inside the handler (instead of on the real delegate) recurses infinitely; a very easy proxy-writing mistake.
--> **`InaccessibleObjectException` (Java 9+) is a different failure mode than the classic `IllegalAccessException`** -- module-system-related access denial throws this new unchecked exception even after calling `setAccessible(true)`, distinct from the old checked exception thrown when access wasn't attempted to be bypassed at all.
--> **Always cache reflective lookups outside hot loops** -- this is the single highest-leverage reflection performance practice; the lookup, not the invocation itself, is often the more surprising cost for code that re-resolves `Method`/`Field` objects per call.
--> **Prefer `MethodHandle` or generated code over raw reflection for genuinely hot paths**; prefer plain reflection (simpler, fine performance) for anything that happens at most a handful of times per request, and especially for one-time startup wiring.
--> **Treat every `setAccessible(true)` call site as a place worth a comment explaining WHY** -- it's a deliberate encapsulation bypass; future maintainers (including future you) benefit from knowing it was intentional and what invariant, if any, the surrounding code relies on staying true despite it.
