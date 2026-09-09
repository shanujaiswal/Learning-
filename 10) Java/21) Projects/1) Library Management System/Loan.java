import java.time.LocalDate;

/**
 * Loan.java
 *
 * Model class recording one checkout transaction: which book, which member,
 * when it was checked out, when it is due, and (once returned) when it
 * actually came back. A library keeps a history of Loans separately from the
 * "currently borrowed" set on Member so that returned/overdue history is not
 * lost the moment a book comes back.
 */
public class Loan {

    public static final int LOAN_PERIOD_DAYS = 14;

    private final String isbn;
    private final String memberId;
    private final LocalDate checkoutDate;
    private final LocalDate dueDate;
    private LocalDate returnDate; // null while still on loan

    public Loan(String isbn, String memberId, LocalDate checkoutDate) {
        this.isbn = isbn;
        this.memberId = memberId;
        this.checkoutDate = checkoutDate;
        this.dueDate = checkoutDate.plusDays(LOAN_PERIOD_DAYS);
        this.returnDate = null;
    }

    /** Full constructor used when reloading a loan record from disk. */
    public Loan(String isbn, String memberId, LocalDate checkoutDate, LocalDate returnDate) {
        this.isbn = isbn;
        this.memberId = memberId;
        this.checkoutDate = checkoutDate;
        this.dueDate = checkoutDate.plusDays(LOAN_PERIOD_DAYS);
        this.returnDate = returnDate;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getMemberId() {
        return memberId;
    }

    public LocalDate getCheckoutDate() {
        return checkoutDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public boolean isReturned() {
        return returnDate != null;
    }

    void markReturned(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    /** Overdue means still out (not returned) and past the due date as of the given reference date. */
    public boolean isOverdue(LocalDate asOf) {
        return !isReturned() && asOf.isAfter(dueDate);
    }

    @Override
    public String toString() {
        String status = isReturned() ? "returned " + returnDate : "OUT (due " + dueDate + ")";
        return String.format("Loan[isbn=%s, member=%s, out=%s, %s]",
                isbn, memberId, checkoutDate, status);
    }

    public String toCsvLine() {
        return String.join("|",
                isbn,
                memberId,
                checkoutDate.toString(),
                returnDate == null ? "" : returnDate.toString());
    }

    public static Loan fromCsvLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Malformed loan line: " + line);
        }
        String isbn = parts[0];
        String memberId = parts[1];
        LocalDate checkoutDate = LocalDate.parse(parts[2]);
        LocalDate returnDate = parts[3].isEmpty() ? null : LocalDate.parse(parts[3]);
        return new Loan(isbn, memberId, checkoutDate, returnDate);
    }
}
