/*
 * PackagesAccessModifiersDemo.java
 *
 * Demonstrates:
 *     1. (As commented illustration only) what a real multi-package project
 *        looks like on disk and in code -- package declarations, imports,
 *        wildcard imports, fully-qualified names, and the classpath
 *     2. A real, compilable static import: `import static java.lang.Math.*`
 *        used to call PI / sqrt / pow unqualified
 *     3. Access modifiers (private, default/package-private, protected, public)
 *        demonstrated on nested classes and members, all visible from within
 *        this single file/class because nested classes share their
 *        enclosing class's access
 *     4. Comments explaining exactly what would fail to compile if the same
 *        code were split across packages, to make the access-modifier table
 *        concrete rather than abstract
 *
 * IMPORTANT NOTE ON FILE STRUCTURE:
 *     Real package/import demos require MULTIPLE .java files in MULTIPLE
 *     directories (see Theory chapter). Since every other demo file in this
 *     repo is a single standalone file compiled and run on its own, this file
 *     deliberately has NO `package` statement (so it lives in the default
 *     package) and uses only single-file-representable features. Section 1
 *     below shows, purely as comments, what the multi-file version would
 *     look like.
 *
 * Covers Theory chapter:
 *     10) Java/01) Core Java Fundamentals/Theory/06 Packages Access Modifiers and Java Project Structure.md
 *
 * Compile: javac 06_packages_access_modifiers_demo.java
 * Run:     java PackagesAccessModifiersDemo
 */

// Real static import -- this line is NOT a comment, it genuinely compiles.
// It static-imports every static member of java.lang.Math, so PI, sqrt(),
// and pow() below can be used unqualified instead of writing Math.PI,
// Math.sqrt(...), Math.pow(...).
import static java.lang.Math.*;

public class PackagesAccessModifiersDemo {

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {
        demoMultiPackageLayoutIllustration();
        demoStaticImport();
        demoAccessModifiersSameFile();
        demoWhatWouldBreakAcrossPackages();
        System.out.println("\nAll packages/access-modifiers demos completed.");
    }

    // ---------------------------------------------------------------------------
    // 1) Illustration only -- what a REAL multi-package project looks like.
    //    Nothing in this method's comment block is compiled; it exists purely
    //    to show, side by side with the working code below, the directory
    //    layout and source that a multi-file version of this topic would use.
    // ---------------------------------------------------------------------------
    private static void demoMultiPackageLayoutIllustration() {
        printSection("1) Illustration -- multi-package project layout (comments only, not compiled)");

        /*
         * Directory layout (package name mirrors folder path exactly):
         *
         * project-root/
         *   src/
         *     com/warpx/billing/
         *       Invoice.java          <-- package com.warpx.billing;
         *     com/warpx/reports/
         *       InvoiceReport.java    <-- package com.warpx.reports;
         *
         * --- com/warpx/billing/Invoice.java ---
         *
         *     package com.warpx.billing;
         *
         *     public class Invoice {
         *         public String id;                 // visible everywhere
         *         protected double amount;          // visible in package + subclasses elsewhere
         *         double internalRate;              // default/package-private -- only com.warpx.billing
         *         private String auditNote;         // only inside Invoice itself
         *     }
         *
         * --- com/warpx/reports/InvoiceReport.java ---
         *
         *     package com.warpx.reports;
         *
         *     import com.warpx.billing.Invoice;      // single-type import
         *     // import com.warpx.billing.*;         // wildcard import -- discouraged, see Theory file
         *
         *     public class InvoiceReport {
         *         void summarize(Invoice inv) {
         *             System.out.println(inv.id);         // OK -- public
         *             System.out.println(inv.amount);     // OK ONLY if InvoiceReport extends Invoice
         *                                                  // (protected across packages needs subclassing)
         *             // System.out.println(inv.internalRate); // COMPILE ERROR -- default, different package
         *             // System.out.println(inv.auditNote);    // COMPILE ERROR -- private, different class
         *         }
         *     }
         *
         * Compiling and running this two-file project from project-root:
         *     javac -d out src/com/warpx/billing/Invoice.java src/com/warpx/reports/InvoiceReport.java
         *     java -cp out com.warpx.reports.InvoiceReport
         *
         * The "-d out" tells javac to place compiled .class files into out/com/warpx/billing/
         * and out/com/warpx/reports/, mirroring the package structure automatically.
         * The "-cp out" on the java command tells the JVM where to search the classpath
         * for those compiled classes.
         */

        System.out.println("(See the source comments above this method for the full multi-package example.)");
        System.out.println("Nothing here was compiled from another package -- it's illustrative text only.");
    }

    // ---------------------------------------------------------------------------
    // 2) Real, compilable static import demo
    // ---------------------------------------------------------------------------
    private static void demoStaticImport() {
        printSection("2) Static import -- 'import static java.lang.Math.*' used above this class");

        double radius = 5.0;

        // PI and sqrt/pow are used completely unqualified here because of the
        // static import at the top of the file -- without it, this would have
        // to read Math.PI, Math.sqrt(...), Math.pow(...).
        double area = PI * radius * radius;
        double diagonalOfSquare = sqrt(pow(radius, 2) * 2);

        // Expected output: PI ~= 3.14159..., area ~= 78.54, diagonal ~= 7.07
        System.out.println("PI (unqualified via static import) = " + PI);
        System.out.println("Circle area (PI * r^2)             = " + area);
        System.out.println("sqrt(pow(r,2) * 2)                 = " + diagonalOfSquare);
        System.out.println("max(3, 7) (also static-imported)   = " + max(3, 7));

        System.out.println();
        System.out.println("Caution: overusing static imports from MULTIPLE classes in one file makes");
        System.out.println("bare calls like max(a, b) ambiguous to a human reader -- is that Math.max or");
        System.out.println("Collections.max? Reserve static import for well-known, unambiguous utilities.");
    }

    // ---------------------------------------------------------------------------
    // 3) Access modifiers demonstrated on nested classes, all in this one file.
    //    Because these are nested (static member) classes of
    //    PackagesAccessModifiersDemo, everything below is "the same file /
    //    the same package (the default package)", so private, default, and
    //    protected members are ALL reachable from here -- that's exactly the
    //    point being demonstrated: same-class and same-package access is
    //    permissive, and the restrictions only bite once you cross a
    //    class/package boundary (see section 4).
    // ---------------------------------------------------------------------------

    // A default/package-private top-level-style class (nested here only because
    // this demo must stay in one file). A real top-level version of this class
    // could ALSO legally have no modifier -- top-level classes may only be
    // `public` or default, never `private`/`protected` (see Theory file).
    static class Account {
        public String accountId;      // public       -- visible from anywhere that can see Account
        protected double balance;     // protected    -- visible in-package + to subclasses elsewhere
        double interestRate;          // default      -- visible only within this same package
        private String pin;           // private      -- visible only inside Account itself

        Account(String accountId, double balance, double interestRate, String pin) {
            this.accountId = accountId;
            this.balance = balance;
            this.interestRate = interestRate;
            this.pin = pin;
        }

        // A private helper -- encapsulated implementation detail, only Account
        // itself may call this, enforced by the compiler, not just convention.
        private boolean pinMatches(String attempt) {
            return this.pin.equals(attempt);
        }

        // Public API method -- this is the sanctioned way for outside code to
        // use the private pin field: through a method Account itself controls.
        public boolean authorize(String pinAttempt) {
            return pinMatches(pinAttempt);
        }
    }

    // A subclass in the SAME file (same package) -- can see protected AND
    // default members of Account directly, because same-package access for
    // protected/default doesn't require crossing any special boundary here.
    static class SavingsAccount extends Account {
        SavingsAccount(String accountId, double balance, double interestRate, String pin) {
            super(accountId, balance, interestRate, pin);
        }

        double projectedYearEndBalance() {
            // balance (protected) and interestRate (default) are both directly
            // reachable here because SavingsAccount is in the same package as
            // Account -- no subclassing trick is even required in-package.
            return balance + (balance * interestRate);
            // this.pin would NOT compile here -- private to Account, not inherited-accessible
        }
    }

    private static void demoAccessModifiersSameFile() {
        printSection("3) Access modifiers on nested classes -- private/default/protected/public");

        Account acc = new Account("ACC-1001", 5000.00, 0.03, "4477");

        // public -- OK from anywhere that can see Account
        System.out.println("accountId (public):     " + acc.accountId);
        // protected -- OK here because this calling code is in the same package (default package)
        System.out.println("balance (protected):    " + acc.balance);
        // default/package-private -- OK here for the same reason: same package
        System.out.println("interestRate (default): " + acc.interestRate);
        // acc.pin;              // COMPILE ERROR if uncommented -- private, only Account itself can touch it
        // acc.pinMatches("x");  // COMPILE ERROR if uncommented -- private method

        // The sanctioned path to the private pin field: through Account's own public method.
        System.out.println("authorize('4477') (via public method wrapping private pin): " + acc.authorize("4477"));
        System.out.println("authorize('0000') (via public method wrapping private pin): " + acc.authorize("0000"));

        SavingsAccount savings = new SavingsAccount("ACC-2002", 10000.00, 0.05, "9911");
        // Expected output: 10500.0 -- 10000 + (10000 * 0.05)
        System.out.println("SavingsAccount projected year-end balance: " + savings.projectedYearEndBalance());
    }

    // ---------------------------------------------------------------------------
    // 4) What would break if Account/SavingsAccount were moved to another package
    // ---------------------------------------------------------------------------
    private static void demoWhatWouldBreakAcrossPackages() {
        printSection("4) What would break if this code were split across packages");

        System.out.println("If Account lived in package com.warpx.billing and this calling code lived");
        System.out.println("in a DIFFERENT, non-subclass package (e.g. com.warpx.reports):");
        System.out.println();
        System.out.println("  acc.accountId       -> still OK (public is visible everywhere)");
        System.out.println("  acc.balance         -> COMPILE ERROR (protected, and this caller isn't a subclass)");
        System.out.println("  acc.interestRate    -> COMPILE ERROR (default/package-private, different package)");
        System.out.println("  acc.pin             -> COMPILE ERROR (private, different class entirely)");
        System.out.println("  acc.authorize(...)  -> still OK (public method, callable from anywhere)");
        System.out.println();
        System.out.println("If SavingsAccount lived in a DIFFERENT package from Account but still EXTENDED it:");
        System.out.println("  this.balance        -> still OK  (protected is visible to subclasses, any package)");
        System.out.println("  this.interestRate    -> COMPILE ERROR (default is package-only, subclassing does not help)");
        System.out.println();
        System.out.println("This mirrors the visibility table in the Theory chapter:");
        System.out.println("  private   : same class only");
        System.out.println("  default   : same class + same package");
        System.out.println("  protected : same class + same package + subclasses in other packages");
        System.out.println("  public    : everywhere");
    }
}
