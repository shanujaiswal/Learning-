/*
 * EncapsulationAccessControlDemo.java
 *
 * Demonstrates:
 *     1. A properly encapsulated class -- private fields, validating getters/setters
 *     2. An immutable class -- final fields, no setters, defensive copy of a mutable List field
 *     3. Leaky encapsulation -- returning a mutable reference from a getter, and the fix
 *     4. Access modifiers -- private / package-private / protected / public, illustrated with
 *        comments explaining what would/would not compile from outside this file/package,
 *        plus a nested class showing protected/private access within a hierarchy
 *
 * Covers Theory chapter:
 *     10) Java/02) Object-Oriented Programming in Java/Theory/02 Encapsulation and Access Control.md
 *
 * Compile: javac 02_EncapsulationAccessControlDemo.java
 * Run:     java EncapsulationAccessControlDemo
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EncapsulationAccessControlDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {
        demoProperEncapsulation();
        demoImmutableClass();
        demoLeakyEncapsulationAndFix();
        demoAccessModifiers();
        System.out.println("\nAll Encapsulation/Access Control demos completed.");
    }

    // ---------------------------------------------------------------------------
    // 1) A properly encapsulated class -- private fields + validating getters/setters
    // ---------------------------------------------------------------------------
    static class Product {
        private String name;
        private double price;
        private boolean discontinued;

        Product(String name, double price) {
            // Constructor delegates to the setters so validation logic exists in exactly ONE
            // place and is guaranteed to run whether the field is set at construction or later.
            setName(name);
            setPrice(price);
        }

        String getName() { return name; }

        void setName(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name cannot be blank");
            }
            this.name = name;
        }

        double getPrice() { return price; }

        void setPrice(double price) {
            if (price < 0) {
                throw new IllegalArgumentException("price cannot be negative");
            }
            this.price = price;
        }

        // Boolean getter uses "is", not "get" -- JavaBeans convention.
        boolean isDiscontinued() { return discontinued; }
        void setDiscontinued(boolean discontinued) { this.discontinued = discontinued; }

        @Override
        public String toString() {
            return "Product{name='" + name + "', price=" + price + ", discontinued=" + discontinued + "}";
        }
    }

    private static void demoProperEncapsulation() {
        printSection("1) Proper Encapsulation -- Validating Getters/Setters");

        Product p = new Product("Widget", 9.99);
        System.out.println("Constructed: " + p);

        p.setPrice(12.50);
        System.out.println("After setPrice(12.50): " + p);
        assert p.getPrice() == 12.50;

        try {
            p.setPrice(-5.0);   // invariant enforced -- cannot go negative through the sanctioned path
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected exception setting negative price: " + e.getMessage());
        }

        try {
            p.setName("   ");   // blank name rejected
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected exception setting blank name: " + e.getMessage());
        }

        // Because the field is private, there is no way to bypass setPrice's validation at all --
        // the ONLY route to changing `price` from outside this class is the validating setter.
    }

    // ---------------------------------------------------------------------------
    // 2) An immutable class -- final fields, no setters, defensive copy of a List
    // ---------------------------------------------------------------------------
    static final class ImmutableTeam {   // final -- cannot be subclassed to add mutable state
        private final String teamName;
        private final List<String> members;   // a mutable type held internally

        ImmutableTeam(String teamName, List<String> members) {
            if (teamName == null || teamName.isBlank()) {
                throw new IllegalArgumentException("teamName cannot be blank");
            }
            this.teamName = teamName;
            // Defensive copy IN: if we stored the caller's `members` reference directly, the
            // caller could mutate it after construction and silently change this "immutable" object.
            this.members = new ArrayList<>(members);
        }

        String getTeamName() { return teamName; }

        List<String> getMembers() {
            // Defensive copy OUT (unmodifiable wrapper): prevents callers from mutating our
            // internal list through the reference we hand back.
            return Collections.unmodifiableList(members);
        }

        // No setters at all -- state supplied once, entirely through the constructor.

        @Override
        public String toString() {
            return "ImmutableTeam{teamName='" + teamName + "', members=" + members + "}";
        }
    }

    private static void demoImmutableClass() {
        printSection("2) Immutable Class -- final Fields + Defensive Copying");

        List<String> original = new ArrayList<>(List.of("Asha", "Ben"));
        ImmutableTeam team = new ImmutableTeam("Alpha", original);
        System.out.println("Constructed: " + team);

        // Mutating the ORIGINAL list the caller passed in must NOT affect the team's internal
        // state, because the constructor defensively copied it.
        original.add("Chidi");
        System.out.println("After mutating the original list the caller passed in: " + team);
        assert team.getMembers().size() == 2 : "team must be unaffected by mutating the caller's original list";

        // Attempting to mutate through the getter's returned list must fail -- it is unmodifiable.
        try {
            team.getMembers().add("Hacker");
        } catch (UnsupportedOperationException e) {
            System.out.println("Caught expected exception mutating getMembers() result: " + e.getClass().getSimpleName());
        }
    }

    // ---------------------------------------------------------------------------
    // 3) Leaky encapsulation -- returning a mutable reference, and the fix
    // ---------------------------------------------------------------------------
    static class LeakyRoster {
        private final List<String> names = new ArrayList<>();

        void addName(String name) { names.add(name); }

        // LEAK: returns the LIVE internal list. `private` only stops the FIELD from being
        // reassigned from outside -- it does nothing to stop the OBJECT it points to from being
        // mutated through this returned alias.
        List<String> getNamesLeaky() { return names; }
    }

    static class SafeRoster {
        private final List<String> names = new ArrayList<>();

        void addName(String name) { names.add(name); }

        // FIX: hand back a copy (or an unmodifiable view) instead of the live internal list.
        List<String> getNamesSafe() { return List.copyOf(names); }
    }

    private static void demoLeakyEncapsulationAndFix() {
        printSection("3) Leaky Encapsulation vs the Fix");

        LeakyRoster leaky = new LeakyRoster();
        leaky.addName("Original");
        List<String> leakedRef = leaky.getNamesLeaky();
        leakedRef.add("Snuck In");   // mutates LeakyRoster's internal state from completely outside it
        System.out.println("LeakyRoster internal names after external mutation via getter: " + leaky.getNamesLeaky());
        assert leaky.getNamesLeaky().contains("Snuck In") : "demonstrates the leak actually happened";
        System.out.println("  -> 'Snuck In' was added with NO call to addName() -- that is the bug.");

        SafeRoster safe = new SafeRoster();
        safe.addName("Original");
        List<String> copy = safe.getNamesSafe();
        try {
            copy.add("Snuck In");   // fails -- List.copyOf() returns an unmodifiable list
        } catch (UnsupportedOperationException e) {
            System.out.println("SafeRoster.getNamesSafe() result is unmodifiable, as expected: "
                    + e.getClass().getSimpleName());
        }
        System.out.println("SafeRoster internal state is untouched: " + safe.getNamesSafe());
        assert !safe.getNamesSafe().contains("Snuck In");
    }

    // ---------------------------------------------------------------------------
    // 4) Access modifiers -- private / package-private / protected / public
    // ---------------------------------------------------------------------------
    //
    // The four Java access levels, and who can reach a member declared with each, from OUTSIDE
    // this file (since everything below currently lives in one file/package, we can only show
    // the COMPILING cases directly -- the comments describe what would happen from elsewhere):
    //
    //   private    -- visible only inside the declaring class itself.
    //                 e.g. AccessDemo.secret below: NOTHING outside AccessDemo can reference it,
    //                 not even a nested subclass in this same file -- attempting
    //                 `new AccessDemo().secret` from AccessLevelDemoRunner would NOT compile.
    //
    //   (default)  -- package-private. Visible to any class in the SAME package, but if this file
    //                 were moved to a different package, `packageVisible` would stop compiling
    //                 for callers outside com.example.study (illustrative -- this demo has no
    //                 explicit package declaration, so everything here is in the unnamed package).
    //
    //   protected  -- visible to same-package classes AND to subclasses even in OTHER packages.
    //                 e.g. AccessDemo.protectedValue is reachable from AccessDemoSubclass below
    //                 because it extends AccessDemo, even if the subclass lived in a different
    //                 package -- protected specifically exists to support inheritance.
    //
    //   public     -- visible from absolutely anywhere, any package, any caller.
    //                 e.g. AccessDemo.publicValue -- no restriction at all.
    //
    static class AccessDemo {
        private int secret = 1;              // only AccessDemo itself can read/write this
        int packageVisible = 2;               // default/package-private
        protected int protectedValue = 3;     // package + subclasses (even in other packages)
        public int publicValue = 4;           // anyone, anywhere

        private int getSecret() { return secret; }   // private method -- only callable from within AccessDemo
    }

    // A subclass in the SAME file (would also work from a different package, since it is a
    // genuine subclass) -- demonstrates what `protected` grants that `private` does not.
    static class AccessDemoSubclass extends AccessDemo {
        void demonstrateInheritedAccess() {
            // this.secret               -- would NOT compile: secret is private to AccessDemo
            // this.getSecret()          -- would NOT compile: getSecret() is private to AccessDemo
            this.packageVisible = 20;     // OK here only because we're still in the same package
            this.protectedValue = 30;     // OK -- protected is inherited access, works from any package
            this.publicValue = 40;        // OK -- public is always accessible
            System.out.println("  Inside subclass: packageVisible=" + packageVisible
                    + ", protectedValue=" + protectedValue + ", publicValue=" + publicValue);
        }
    }

    private static void demoAccessModifiers() {
        printSection("4) Access Modifiers -- private / package-private / protected / public");

        AccessDemo ad = new AccessDemo();

        // From here (still nested inside EncapsulationAccessControlDemo, same file/package):
        // ad.secret            -- would NOT compile: private, only AccessDemo's own code can see it
        System.out.println("packageVisible (default access, same package -- compiles): " + ad.packageVisible);
        System.out.println("protectedValue (same package also grants access -- compiles): " + ad.protectedValue);
        System.out.println("publicValue (always accessible -- compiles): " + ad.publicValue);

        AccessDemoSubclass sub = new AccessDemoSubclass();
        sub.demonstrateInheritedAccess();

        System.out.println();
        System.out.println("Note: `secret` (private) and getSecret() (private) are reachable ONLY");
        System.out.println("from inside AccessDemo's own class body -- not shown compiling here on");
        System.out.println("purpose, since referencing them from this method would be a compile error.");
    }
}
