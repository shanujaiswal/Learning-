# What Spring Framework Is and the Problem It Solves

--> Spring is a lightweight, comprehensive framework for building Java applications, and at its core it's a **container that creates, configures, and wires your objects for you** -- instead of your code being littered with `new SomeService(new SomeRepository(new DataSource(...)))` chains, you describe what objects your application needs and how they relate, and Spring assembles the whole object graph at startup.
--> The problem it solves is **tight coupling from manual object wiring**. Without a framework, if `OrderService` needs a `PaymentGateway`, the natural thing to do is have `OrderService` construct its own `PaymentGateway` internally -- but that means `OrderService` is now hard-wired to one specific `PaymentGateway` implementation, can't be tested without a real payment gateway, and every class that needs a `PaymentGateway` has to duplicate the construction logic (which API keys, which config, which implementation class).
--> Spring's answer is to **invert who is responsible for creating and connecting objects** -- your classes just declare what they need (via constructor parameters, typically), and a container supplies those dependencies from the outside. This single idea cascades into almost everything else in the framework: testability, modularity, swappable implementations, and centralized configuration.

```java
// WITHOUT Spring -- OrderService manually constructs everything it depends on
public class OrderService {
    private final PaymentGateway paymentGateway = new StripePaymentGateway("sk_live_...");
    private final OrderRepository orderRepository = new JdbcOrderRepository(new DataSource(...));
    // Tightly coupled to StripePaymentGateway and JdbcOrderRepository specifically.
    // Can't swap implementations without editing this class.
    // Can't unit test without hitting a real database and a real payment API.
}

// WITH Spring -- OrderService just declares what it needs; something else supplies it
public class OrderService {
    private final PaymentGateway paymentGateway;
    private final OrderRepository orderRepository;

    public OrderService(PaymentGateway paymentGateway, OrderRepository orderRepository) {
        this.paymentGateway = paymentGateway;
        this.orderRepository = orderRepository;
    }
    // OrderService doesn't know or care which PaymentGateway/OrderRepository
    // implementation it gets -- Spring decides that, and it can be swapped
    // (real implementation in production, a mock/stub in tests) with zero
    // changes to OrderService itself.
}
```

--> **Spring Framework vs Spring Boot** -- Spring Framework is the core (IoC container, DI, AOP, transaction management, etc.); Spring Boot is a layer on top that auto-configures Spring Framework for you (embedded servers, sensible defaults, starter dependencies) so you don't hand-wire dozens of beans yourself. This file is about the CORE concepts that both are built on -- Spring Boot topics get their own chapter later in this folder.

# Inversion of Control (IoC)

--> **Inversion of Control** is the general principle: the control of creating and managing objects is taken AWAY from your own code and handed to a framework/container. Traditionally, your code calls a library to do work (you're in control, you call `new X()` when you need an `X`). With IoC, that's flipped -- the container calls YOUR code, supplying it with what it needs, when it needs it ("Hollywood Principle": don't call us, we'll call you).
--> IoC is a broader concept than Dependency Injection -- DI is just the most common IMPLEMENTATION of IoC in Spring. (Other IoC mechanisms exist in other contexts, like the Template Method pattern or event-driven callback systems, but in the Spring world "IoC" and "the DI container" are used almost interchangeably.)

## The IoC Container

--> The **IoC container** is the runtime engine inside Spring that actually does the object creation and wiring -- it reads your configuration (annotations, Java config classes, or historically XML), figures out what objects ("beans") need to exist and what they depend on, instantiates them in the right order, injects their dependencies, and hands you a fully wired object graph ready to use.
--> Concretely, the IoC container is represented by the `ApplicationContext` interface (covered in depth further down) -- when people say "the Spring container," they mean a running `ApplicationContext` instance.

```text
                     +-----------------------+
  Your Config  ----> |     IoC Container      | ----> Fully wired objects
  (annotations,      | (ApplicationContext)    |       ready to use
   @Bean methods)     +-----------------------+
                       |  1. Reads config       |
                       |  2. Creates bean graph  |
                       |  3. Resolves deps       |
                       |  4. Injects deps        |
                       |  5. Manages lifecycle   |
                       +-----------------------+
```

# Dependency Injection (DI)

--> **Dependency Injection** is the specific technique: a class declares the objects it depends on (its "dependencies") as constructor parameters, setter parameters, or annotated fields, and an external party (the IoC container) supplies ("injects") those objects, rather than the class instantiating them itself.
--> **Why it matters, concretely:**
--> **Testability** -- since dependencies are supplied externally, tests can inject mocks/stubs instead of real implementations, letting you unit-test `OrderService` without a real database or payment gateway.
--> **Loose coupling** -- `OrderService` depends on the `PaymentGateway` INTERFACE, not a concrete class, so the concrete implementation can change (Stripe today, PayPal tomorrow) without touching `OrderService`.
--> **Centralized configuration** -- wiring logic lives in one place (config classes / annotations) instead of being scattered across every class that happens to need an object.
--> **Reusability** -- the same class can be wired differently in different contexts (different config per environment, e.g. an in-memory repository for tests vs a JDBC one for production) without code changes.

# The Three DI Types

--> Spring supports three mechanical ways to inject a dependency into a class: **constructor injection**, **setter injection**, and **field injection**. They all accomplish the same underlying goal but differ meaningfully in when the dependency becomes available, whether it can be made required vs optional, and how testable/immutable the result is.

## Constructor Injection

```java
@Component
public class OrderService {
    private final PaymentGateway paymentGateway;   // final -- can only be set once, at construction
    private final OrderRepository orderRepository;

    // Spring sees this constructor, and since Spring 4.3+ a single constructor
    // doesn't even need @Autowired -- Spring infers it should be used for injection.
    public OrderService(PaymentGateway paymentGateway, OrderRepository orderRepository) {
        this.paymentGateway = paymentGateway;
        this.orderRepository = orderRepository;
    }
}
```

--> Dependencies are passed as arguments to the constructor -- the object literally CANNOT be instantiated without them, so it's impossible to end up with a half-wired `OrderService` that's missing its `PaymentGateway`.

## Setter Injection

```java
@Component
public class OrderService {
    private PaymentGateway paymentGateway;   // not final -- can be reassigned later

    @Autowired
    public void setPaymentGateway(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }
    // Object is constructed first (via a no-arg constructor), THEN Spring calls
    // this setter afterward to supply the dependency -- there's a window where
    // the object exists but paymentGateway is still null.
}
```

--> Dependencies are supplied through public setter methods AFTER the object is constructed via a no-arg (or other) constructor -- useful historically for OPTIONAL dependencies, or for dependencies that need to be reconfigured/swapped after construction, since setters can be called again later.

## Field Injection

```java
@Component
public class OrderService {
    @Autowired
    private PaymentGateway paymentGateway;   // Spring uses reflection to set this directly

    @Autowired
    private OrderRepository orderRepository;
    // No constructor, no setter needed -- Spring reaches into the private field
    // via reflection and assigns it directly after construction.
}
```

--> Dependencies are injected directly into fields (often private) via reflection, with no constructor or setter involved at all -- this is the most CONCISE to write, which is exactly why it's so common in tutorials and quick demos, but it's the least recommended approach for real production code (see below).

## Why Constructor Injection Is Generally Preferred

--> This is one of the most commonly asked Spring interview questions, and the reasoning matters more than memorizing the answer.

--> **Immutability** -- constructor injection lets dependency fields be declared `final`. Once set, they can never be reassigned, which rules out an entire class of bugs where a dependency is unexpectedly null or swapped out mid-lifecycle. Setter and field injection can't use `final`, because Spring needs to assign the field/call the setter AFTER the no-arg constructor runs.
--> **Guaranteed complete initialization** -- with constructor injection, it is structurally IMPOSSIBLE to have a half-constructed object; if a required dependency is missing, the object simply cannot be created, and you find out immediately at startup (via a clear `ApplicationContext` failure) rather than later with a `NullPointerException` at runtime when the unset field is finally used.
--> **Testability without a container** -- constructor-injected classes can be instantiated directly in plain unit tests with `new OrderService(mockPaymentGateway, mockOrderRepository)` -- no Spring, no reflection tricks, no need to spin up an `ApplicationContext`. Field-injected classes require either a running container or reflection-based test hacks (`ReflectionTestUtils`) to set private fields in tests.
--> **Avoiding circular dependencies by design** -- if `A` needs `B` and `B` needs `A` via CONSTRUCTOR injection, Spring cannot resolve it at all and fails fast at startup with a clear `BeanCurrentlyInCreationException` -- forcing you to fix the actual design problem. Field/setter injection can often paper over a circular dependency (Spring creates both objects with no-arg constructors first, then injects into fields/setters afterward, breaking the cycle) -- but this just HIDES a design smell instead of surfacing it; two classes needing each other directly is almost always a sign they should be merged or refactored to depend on a shared abstraction.
--> **Required vs optional dependencies are explicit** -- constructor parameters naturally communicate "this class cannot function without this" -- there's no way to construct the object while forgetting a required dependency. Optional dependencies are still cleanly expressible via setter injection (an optional setter can simply not be called) or via `@Autowired(required = false)` on a setter/field, so the two injection styles are often used TOGETHER: constructor injection for everything mandatory, setter injection for genuinely optional collaborators.

```text
Constructor Injection  --> required deps, immutable, testable without a container, fails fast
Setter Injection       --> optional deps, mutable, reconfigurable after construction
Field Injection        --> most concise, but hardest to test/reason about -- avoid in real code
```

--> **Deep Dive -- why field injection is discouraged despite being everywhere in tutorials** -- reflection-based field injection bypasses the Java type system's normal construction guarantees, hides a class's true dependencies (you have to read the whole class body to know what it needs, instead of just its constructor signature), makes the class impossible to instantiate outside a Spring container, and makes circular dependencies silently "work" instead of failing loudly. It's popular in demos purely because it's fewer lines of boilerplate -- for real projects, most style guides (including Spring's own team) now explicitly recommend constructor injection as the default, reserving setter injection for genuinely optional dependencies and avoiding field injection almost entirely.

# Beans

--> A **bean** is simply an object that is instantiated, assembled, and managed by the Spring IoC container -- NOT every Java object in your program is a bean, only the ones you've told Spring to manage. Once something is a bean, Spring controls its full lifecycle: when it's created, what gets injected into it, when its lifecycle callbacks fire, and when it's destroyed.
--> Beans are identified within the container by a unique **bean name** (by default derived from the class name with a lowercase first letter, e.g. `OrderService` -> `orderService`), and the container uses that identity to resolve dependencies between beans.

## How Beans Are Defined

--> There are three historical/current ways to tell Spring "this object should be a managed bean," and modern Spring code overwhelmingly favors the last two:

**1. XML configuration (legacy, largely historical today)**

```xml
<!-- applicationContext.xml -->
<bean id="orderService" class="com.example.OrderService">
    <constructor-arg ref="paymentGateway"/>
    <constructor-arg ref="orderRepository"/>
</bean>
<bean id="paymentGateway" class="com.example.StripePaymentGateway"/>
```

--> Verbose, fully external to the Java code, and mostly seen in older/legacy codebases today -- included here mainly because you WILL encounter it maintaining older enterprise Spring apps, but new code almost never starts here.

**2. Java configuration with `@Configuration` and `@Bean`**

```java
@Configuration
public class AppConfig {

    @Bean
    public PaymentGateway paymentGateway() {
        return new StripePaymentGateway("sk_live_...");
    }

    @Bean
    public OrderService orderService(PaymentGateway paymentGateway, OrderRepository orderRepository) {
        // Parameters here are automatically resolved by Spring from other beans
        // in the container -- this IS constructor injection, just expressed
        // through a factory method instead of the class's own constructor.
        return new OrderService(paymentGateway, orderRepository);
    }
}
```

--> Full explicit control in plain Java -- especially useful for wiring THIRD-PARTY classes you don't own and can't annotate (you can't put `@Component` on a class from an external library, but you CAN write a `@Bean` factory method for it).

**3. Component scanning with stereotype annotations**

```java
@Component               // generic "this is a Spring-managed bean" annotation
public class OrderService { ... }

@Service                 // semantically a @Component -- marks a service-layer class
public class PaymentProcessingService { ... }

@Repository               // semantically a @Component -- marks a data-access class,
public class JdbcOrderRepository { ... }   // also enables exception translation

@Controller / @RestController   // semantically a @Component -- marks a web layer class
public class OrderController { ... }
```

--> With `@ComponentScan` (or Spring Boot's `@SpringBootApplication`, which includes it) enabled on a config class, Spring scans the specified packages, finds every class annotated with `@Component` (or a specialization of it -- `@Service`, `@Repository`, `@Controller` all ARE `@Component` under the hood, just with clearer semantic meaning), and automatically registers each one as a bean. This is the dominant style in modern Spring code because it keeps the "this is a bean" declaration right next to the class itself.

```text
XML config          --> fully external, verbose, mostly legacy today
@Configuration/@Bean --> explicit Java factory methods, best for 3rd-party/external classes
@Component + scan    --> annotate your own classes directly, least boilerplate, most common today
```

# Bean Lifecycle

--> Every managed bean goes through a well-defined sequence of phases from creation to destruction -- understanding this order matters because it tells you exactly WHEN a bean's dependencies are guaranteed to be available, and gives you hooks to run custom setup/teardown logic at the right moment.

```text
1. INSTANTIATION
   Container calls the bean's constructor (or @Bean factory method) to create the raw object.
        |
        v
2. POPULATE PROPERTIES (Dependency Injection)
   Container injects constructor args / calls setters / sets @Autowired fields.
        |
        v
3. AWARE INTERFACES (if implemented)
   BeanNameAware, BeanFactoryAware, ApplicationContextAware callbacks fire,
   giving the bean access to its own name / the container itself, if it asked for it.
        |
        v
4. BEAN POST-PROCESSORS -- before initialization
   BeanPostProcessor.postProcessBeforeInitialization() runs for every bean
   (this is the extension point that powers things like @Autowired itself).
        |
        v
5. INITIALIZATION CALLBACKS
   a) @PostConstruct-annotated method runs
   b) InitializingBean.afterPropertiesSet() runs (if the bean implements it)
   c) custom init-method (specified via @Bean(initMethod = "...")) runs
        |
        v
6. BEAN POST-PROCESSORS -- after initialization
   BeanPostProcessor.postProcessAfterInitialization() runs (this is where
   proxies get created, e.g. for @Transactional, AOP advice, etc.)
        |
        v
7. BEAN IS READY -- fully constructed, wired, initialized
   ... lives in the container, gets used by the application for as long as
   its scope dictates (see Bean Scopes below) ...
        |
        v
8. DESTRUCTION CALLBACKS (only for singleton-scoped beans, on container shutdown)
   a) @PreDestroy-annotated method runs
   b) DisposableBean.destroy() runs (if the bean implements it)
   c) custom destroy-method (specified via @Bean(destroyMethod = "...")) runs
```

```java
@Component
public class ConnectionPoolManager {

    public ConnectionPoolManager() {
        System.out.println("1. Constructor -- object instantiated");
    }

    @Autowired
    public void setDataSource(DataSource dataSource) {
        System.out.println("2. Setter injection -- dependency populated");
    }

    @PostConstruct
    public void initializePool() {
        // Guaranteed all dependencies are already injected at this point --
        // this is the right place to "start" a bean: open connections,
        // warm caches, validate config, etc.
        System.out.println("5a. @PostConstruct -- pool warmed up, ready for use");
    }

    @PreDestroy
    public void closePool() {
        // Runs just before the container discards this bean (e.g. on
        // context.close()) -- the right place to release resources:
        // close connections, flush buffers, stop background threads.
        System.out.println("8a. @PreDestroy -- pool draining and closing connections");
    }
}
```

--> **`@PostConstruct`/`@PreDestroy` vs `InitializingBean`/`DisposableBean`** -- `@PostConstruct` and `@PreDestroy` (standard `jakarta.annotation` annotations, not Spring-specific) are strongly preferred in modern code because they don't couple your class to Spring's own interfaces -- a class using them can still be understood/reused without importing Spring types. `InitializingBean.afterPropertiesSet()` and `DisposableBean.destroy()` are the older, Spring-specific interface-based approach -- still supported, occasionally seen in older code or framework-internal code, but annotation-based callbacks are the standard recommendation today.
--> **Destruction callbacks only fire reliably for singleton beans** -- this is a very common gotcha, covered in more depth in the Bean Scopes section below.

# Bean Scopes

--> A bean's **scope** determines HOW MANY instances of it exist and WHEN each instance is created -- by default every bean in Spring is a singleton, but that default is not always appropriate (e.g. a bean holding per-request state clearly shouldn't be shared across every request concurrently).

| Scope | Instances | Created When | Typical Use Case |
|---|---|---|---|
| `singleton` (default) | Exactly one per container | Eagerly, at container startup (unless lazy-initialized) | Stateless services, repositories, most everyday beans |
| `prototype` | New instance every time it's requested | On each `getBean()` call / each injection point | Stateful, non-thread-safe objects that shouldn't be shared |
| `request` | One instance per HTTP request | Per incoming web request | Web-tier data scoped to a single request (needs web-aware `ApplicationContext`) |
| `session` | One instance per HTTP session | Per user session | Per-user state across multiple requests, e.g. a shopping cart |
| `application` | One instance per `ServletContext` | Once per web application | Effectively singleton, but scoped to the `ServletContext` rather than the Spring container |
| `websocket` | One instance per WebSocket session | Per WebSocket connection | State tied to a single long-lived WebSocket session |

```java
@Component
@Scope("singleton")     // default -- can be omitted
public class ConfigService { ... }

@Component
@Scope("prototype")
public class ShoppingCartCalculator {
    // Suppose this class accumulates intermediate state during a multi-step
    // calculation. If it were a singleton, concurrent calls from different
    // threads would corrupt each other's intermediate state. Prototype
    // scope guarantees each caller gets its own fresh instance.
}
```

--> **`singleton` does NOT mean "one instance across the whole JVM"** -- it means one instance PER SPRING CONTAINER (`ApplicationContext`). If you somehow spin up two separate `ApplicationContext`s in the same JVM, each gets its own singleton instance. In the vast majority of applications there's only one container, so in practice "one per container" and "one per app" are the same thing -- but it's worth knowing precisely what the guarantee is.
--> **Web scopes require a web-aware `ApplicationContext`** (`request`, `session`, `application`, `websocket`) -- trying to use them in a plain non-web context throws an exception, since there's no HTTP request/session to scope the bean to.

--> **Deep Dive -- injecting a `prototype` bean into a `singleton` bean (the scope mismatch gotcha)** -- singletons are created ONCE at startup, and dependency injection happens ONCE at that time. If a singleton bean has a `prototype`-scoped dependency injected the normal way, it gets handed ONE prototype instance at wiring time and keeps reusing that same instance forever -- completely defeating the purpose of prototype scope (a "new instance every time" scope that's actually only fetched once). The fix is to inject a `ObjectProvider<T>` (or the older `ObjectFactory<T>`/lookup-method injection) instead of the bean directly, and call `.getObject()` each time you actually need a fresh prototype instance:

```java
@Component
public class ReportGenerator {          // singleton
    private final ObjectProvider<ShoppingCartCalculator> calculatorProvider;

    public ReportGenerator(ObjectProvider<ShoppingCartCalculator> calculatorProvider) {
        this.calculatorProvider = calculatorProvider;
    }

    public void generate() {
        ShoppingCartCalculator calculator = calculatorProvider.getObject();  // fresh prototype instance, every call
        // ... use calculator ...
    }
}
```

--> **Deep Dive -- destruction callbacks and prototype beans** -- Spring's container manages the FULL lifecycle of singleton beans, including destruction, but for `prototype` beans it hands the object over to the caller after construction and initialization and then WASHES ITS HANDS of it -- the container never tracks prototype instances after creation, so `@PreDestroy` is never called automatically on them. If a prototype bean holds resources that need explicit cleanup, your code is responsible for calling that cleanup manually; the container will not do it for you.

# ApplicationContext

--> The `ApplicationContext` interface is Spring's central IoC container interface -- it's what you interact with to retrieve beans, and it's what performs all the bean creation/wiring/lifecycle management described above under the hood.

## ApplicationContext vs BeanFactory

--> `BeanFactory` is the more primitive, root interface for the IoC container -- it provides basic DI capability (`getBean()`, bean definitions) but with LAZY initialization by default (beans are created only when first requested) and none of the extra enterprise features.
--> `ApplicationContext` EXTENDS `BeanFactory` and adds a substantial amount on top: eager initialization of singletons at startup by default (so config errors surface immediately rather than at first use), event publishing (`ApplicationEvent`/`ApplicationListener`), internationalization message support (`MessageSource`), easy access to resources (files, classpath resources, URLs) via `ResourceLoader`, and automatic registration of `BeanPostProcessor`/`BeanFactoryPostProcessor` beans found in configuration.
--> In practice, virtually all real Spring applications use `ApplicationContext`, not raw `BeanFactory` -- `BeanFactory` is mostly of academic/historical interest today, though it's still there underneath as the foundational interface.

| | `BeanFactory` | `ApplicationContext` |
|---|---|---|
| Initialization of singletons | Lazy (on first `getBean()`) | Eager, at startup, by default |
| Event publishing | No | Yes |
| Internationalization (`MessageSource`) | No | Yes |
| Automatic `BeanPostProcessor` registration | Manual | Automatic |
| Typical usage today | Rare, low-level | Standard, virtually universal |

## Common ApplicationContext Implementations

```java
// Java-config-based, annotation-driven -- the standard modern choice
ApplicationContext context =
    new AnnotationConfigApplicationContext(AppConfig.class);

// XML-based, loaded from the classpath -- legacy style
ApplicationContext context =
    new ClassPathXmlApplicationContext("applicationContext.xml");

// XML-based, loaded from an absolute/relative filesystem path
ApplicationContext context =
    new FileSystemXmlApplicationContext("/config/applicationContext.xml");

// Web-application-aware variant (used internally by Spring MVC/Boot),
// integrates with the ServletContext and enables web scopes (request/session)
WebApplicationContext webContext = ...; // typically set up for you by Spring Boot/Spring MVC
```

--> Spring Boot applications don't usually create an `ApplicationContext` manually at all -- `SpringApplication.run(...)` does it internally (typically an `AnnotationConfigServletWebServerApplicationContext` under the hood for a web app), but understanding what's happening underneath is exactly what this chapter is about.

## How ApplicationContext Resolves and Wires Beans

--> At startup, the container goes through roughly this process:
--> **1. Load bean definitions** -- scans `@Component`-annotated classes (via `@ComponentScan`) and/or reads `@Bean` methods from `@Configuration` classes and/or parses XML `<bean>` tags, building up an internal registry of "bean definitions" (metadata: class, scope, dependencies, etc.) without yet instantiating anything.
--> **2. Determine creation order** -- figures out, from the dependency graph, which beans need to be created before which other beans (a bean that's a constructor argument for another must exist first).
--> **3. Instantiate and inject singletons eagerly** -- for each singleton bean (the default scope), calls its constructor/factory method, resolves and injects its dependencies (recursively creating any not-yet-created dependency beans first), and runs it through the full lifecycle described earlier.
--> **4. Resolve dependencies by type, disambiguated by name** -- when a constructor parameter or `@Autowired` field needs a `PaymentGateway`, Spring looks for a bean of that TYPE (or a compatible subtype) in the registry. If exactly one candidate exists, it's injected. If ambiguity arises, name-based and qualifier-based disambiguation kicks in (see gotchas below).
--> **5. Context is "refreshed" and ready** -- once every singleton is created and wired, the context is fully initialized and ready to serve `getBean()` calls (which, for singletons, just return the already-created instance).

# Common Gotchas

--> **Circular dependencies** -- `A` depends on `B`, and `B` depends on `A`. With constructor injection, this is UNRESOLVABLE -- Spring cannot construct `A` (it needs a `B` first) without also constructing `B` (which needs an `A` first), so it fails fast with `BeanCurrentlyInCreationException`. With field/setter injection, Spring CAN often resolve this (by creating both objects via their no-arg constructors first, then injecting into fields/setters afterward, breaking the chicken-and-egg problem) -- but a circular dependency is virtually always a sign of a design problem (the two classes are too tightly coupled, or should be merged, or need a third mediating abstraction), so the fact that constructor injection refuses to allow it is a FEATURE, not a limitation to work around.

```java
@Component
public class A {
    public A(B b) { ... }   // needs a B to be constructed
}

@Component
public class B {
    public B(A a) { ... }   // needs an A to be constructed
}
// Neither can be constructed first -- BeanCurrentlyInCreationException at startup.
// Fix: introduce an interface/event/mediator so the dependency isn't mutual,
// or merge A and B if they're really one responsibility split in two.
```

--> **Bean not found (`NoSuchBeanDefinitionException`)** -- you asked for a bean of a type Spring has no definition for. Common causes: the class isn't annotated with `@Component`/`@Service`/etc., or it lives in a package OUTSIDE what `@ComponentScan` is scanning, or you forgot a `@Bean` method for a third-party class, or a config class defining it was never actually loaded/imported.

```text
NoSuchBeanDefinitionException: No qualifying bean of type 'com.example.PaymentGateway' available
--> Check: is the class annotated? Is it in a scanned package? Is the @Configuration
    class that defines its @Bean method actually being loaded (via @Import,
    component scan, or explicitly passed to AnnotationConfigApplicationContext)?
```

--> **Ambiguous bean / multiple candidates (`NoUniqueBeanDefinitionException`)** -- more than one bean matches the required TYPE, and Spring can't guess which one you meant.

```java
public interface PaymentGateway { ... }

@Component
public class StripePaymentGateway implements PaymentGateway { ... }

@Component
public class PayPalPaymentGateway implements PaymentGateway { ... }

@Component
public class OrderService {
    // Ambiguous! Two beans implement PaymentGateway -- Spring doesn't know which to inject.
    public OrderService(PaymentGateway paymentGateway) { ... }
}
```

--> Three standard fixes, in order of how commonly they're used:

```java
// Fix 1: @Primary -- designate one implementation as the default choice
@Component
@Primary
public class StripePaymentGateway implements PaymentGateway { ... }

// Fix 2: @Qualifier -- explicitly name which bean you want at the injection point
@Component
public class OrderService {
    public OrderService(@Qualifier("payPalPaymentGateway") PaymentGateway paymentGateway) { ... }
}

// Fix 3: match the parameter/field name to the bean name exactly
// (Spring falls back to matching by bean NAME when type alone is ambiguous)
public OrderService(PaymentGateway payPalPaymentGateway) { ... }  // matches bean named "payPalPaymentGateway"
```

--> **Scope mismatches** -- covered in the Bean Scopes Deep Dive above: injecting a `prototype` bean into a `singleton` the normal way silently captures just ONE instance forever, and web-scoped beans (`request`/`session`) throw if used outside a web-aware `ApplicationContext`. Both are subtle because they don't fail loudly at wiring time -- the app starts fine, and the bug only shows up as unexpected shared/stale state later.
--> **Destruction callbacks silently not firing** -- `@PreDestroy` only fires for container-managed lifecycle-tracked beans -- that means singletons (tracked and destroyed on `context.close()`), but NOT prototype beans (never tracked after handoff), and not beans in a context that's shut down abruptly (e.g. `System.exit()` without a proper `context.close()`/shutdown hook) -- always register a shutdown hook (`context.registerShutdownHook()`, done automatically by Spring Boot) to guarantee graceful destruction callbacks on JVM shutdown.

# Best Practices Summary

--> **Prefer constructor injection** for all required dependencies -- immutable, fails fast, testable without a container, and structurally prevents circular dependencies from compiling into a broken design.
--> **Reserve setter injection** for genuinely optional dependencies only.
--> **Avoid field injection** in real production code -- fine for throwaway demos, but hurts testability and hides a class's true dependency list.
--> **Use `@Component`/`@Service`/`@Repository`/`@Controller`** for your own classes, and `@Configuration` + `@Bean` for third-party classes you don't own or can't annotate.
--> **Keep beans stateless where possible** so the default `singleton` scope (cheap, shared, thread-safe if truly stateless) just works -- reach for `prototype` only when a bean genuinely needs fresh, isolated state per use, and inject it via `ObjectProvider<T>` into any singleton that needs one.
--> **Use `@PostConstruct`/`@PreDestroy`** over `InitializingBean`/`DisposableBean` to avoid unnecessary coupling to Spring-specific interfaces.
--> **Resolve ambiguous beans explicitly** with `@Primary` (one sensible default) or `@Qualifier` (explicit choice at the injection site) rather than relying on accidental name matching.
--> **Treat circular dependencies as a design smell to fix**, not a wiring problem to work around with field/setter injection tricks.
