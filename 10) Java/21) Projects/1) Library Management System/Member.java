import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Member.java
 *
 * Concrete Person subclass representing a library member.
 *
 * Demonstrates:
 *  - Inheritance (extends the abstract Person base class)
 *  - Collections/Generics: tracks the ISBNs a member currently has on loan in
 *    a LinkedHashSet<String> (preserves checkout order, forbids duplicates)
 *  - Encapsulation: the borrowed-ISBN set is exposed only as an unmodifiable
 *    view so external code cannot corrupt a member's loan state directly;
 *    all mutation goes through Library, which enforces the business rules.
 */
public class Member extends Person {

    /** Simple business rule: a member may not have more than this many books out at once. */
    public static final int MAX_BOOKS_ALLOWED = 3;

    private final LocalDate joinDate;
    private final Set<String> borrowedIsbns = new LinkedHashSet<>();

    public Member(String id, String name, String email, LocalDate joinDate) {
        super(id, name, email);
        this.joinDate = joinDate == null ? LocalDate.now() : joinDate;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    @Override
    public String getRole() {
        return "Member";
    }

    public Set<String> getBorrowedIsbns() {
        return java.util.Collections.unmodifiableSet(borrowedIsbns);
    }

    public int borrowedCount() {
        return borrowedIsbns.size();
    }

    public boolean hasReachedLimit() {
        return borrowedIsbns.size() >= MAX_BOOKS_ALLOWED;
    }

    public boolean hasBorrowed(String isbn) {
        return borrowedIsbns.contains(isbn);
    }

    /** Package-private mutators: only Library (same-package service layer) should call these. */
    void addBorrowedIsbn(String isbn) {
        borrowedIsbns.add(isbn);
    }

    void removeBorrowedIsbn(String isbn) {
        borrowedIsbns.remove(isbn);
    }

    @Override
    public String toString() {
        return String.format("Member[%s] %s <%s> joined=%s booksOut=%d/%d",
                getId(), getName(), getEmail() == null ? "-" : getEmail(),
                joinDate, borrowedIsbns.size(), MAX_BOOKS_ALLOWED);
    }

    /** Serializes this member to one pipe-delimited line, including their currently-borrowed ISBNs (comma-separated). */
    public String toCsvLine() {
        String isbnsJoined = String.join(",", borrowedIsbns);
        return String.join("|",
                getId(),
                getName(),
                getEmail() == null ? "" : getEmail(),
                joinDate.toString(),
                isbnsJoined);
    }

    public static Member fromCsvLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 5) {
            throw new IllegalArgumentException("Malformed member line: " + line);
        }
        String id = parts[0];
        String name = parts[1];
        String email = parts[2].isEmpty() ? null : parts[2];
        LocalDate joinDate = LocalDate.parse(parts[3]);
        Member member = new Member(id, name, email, joinDate);
        if (!parts[4].isEmpty()) {
            for (String isbn : parts[4].split(",")) {
                member.addBorrowedIsbn(isbn);
            }
        }
        return member;
    }
}
