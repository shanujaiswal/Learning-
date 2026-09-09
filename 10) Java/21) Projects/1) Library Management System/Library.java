import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Library.java
 *
 * Core service layer for the whole application. Holds all in-memory state
 * (catalog, members, loan history) and enforces every business rule. The
 * console UI (LibraryManagementSystemApp) never touches these collections
 * directly — it only calls Library's public methods, which is the
 * encapsulation boundary between "what the data looks like" and "how the
 * app is allowed to use it".
 *
 * Demonstrates:
 *  - Generics: a small generic helper method (sortedCopyOf) reusable for any
 *    Comparable list.
 *  - Collections: LinkedHashMap for the catalog/member registries (keeps
 *    insertion order for predictable "list all" output) and ArrayList for
 *    loan history.
 *  - Streams: filtering/sorting/reporting are implemented with the Stream
 *    API almost everywhere search or aggregation is needed.
 *  - Custom checked exceptions: every business rule violation raises one of
 *    the LibraryException subclasses rather than returning a sentinel value.
 */
public class Library {

    private final Map<String, Book> catalog = new LinkedHashMap<>();
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final List<Loan> loans = new ArrayList<>();

    // ---------------------------------------------------------------
    // Catalog management
    // ---------------------------------------------------------------

    public Book addBook(String isbn, String title, String author, String genre, int copies)
            throws DuplicateEntryException {
        if (catalog.containsKey(isbn)) {
            throw new DuplicateEntryException("Book", isbn);
        }
        Book book = new Book(isbn, title, author, genre, copies);
        catalog.put(isbn, book);
        return book;
    }

    /** Used only by FileStorage when reloading a book exactly as it was saved (including availableCopies). */
    void restoreBook(Book book) {
        catalog.put(book.getIsbn(), book);
    }

    public Book getBook(String isbn) throws BookNotFoundException {
        Book book = catalog.get(isbn);
        if (book == null) {
            throw new BookNotFoundException(isbn);
        }
        return book;
    }

    public ReadOnlyView<Book> allBooks() {
        return new ReadOnlyView<>(catalog.values());
    }

    // ---------------------------------------------------------------
    // Member management
    // ---------------------------------------------------------------

    public Member registerMember(String id, String name, String email) throws DuplicateEntryException {
        if (members.containsKey(id)) {
            throw new DuplicateEntryException("Member", id);
        }
        Member member = new Member(id, name, email, LocalDate.now());
        members.put(id, member);
        return member;
    }

    /** Used only by FileStorage when reloading a member exactly as saved (including their borrowed set). */
    void restoreMember(Member member) {
        members.put(member.getId(), member);
    }

    public Member getMember(String id) throws MemberNotFoundException {
        Member member = members.get(id);
        if (member == null) {
            throw new MemberNotFoundException(id);
        }
        return member;
    }

    public ReadOnlyView<Member> allMembers() {
        return new ReadOnlyView<>(members.values());
    }

    // ---------------------------------------------------------------
    // Checkout / return
    // ---------------------------------------------------------------

    public Loan checkout(String isbn, String memberId)
            throws BookNotFoundException, BookNotAvailableException,
                   MemberNotFoundException, BorrowingLimitExceededException {
        Book book = getBook(isbn);
        Member member = getMember(memberId);

        if (!book.isAvailable()) {
            throw new BookNotAvailableException(isbn);
        }
        if (member.hasReachedLimit()) {
            throw new BorrowingLimitExceededException(memberId, Member.MAX_BOOKS_ALLOWED);
        }

        book.decrementAvailable();
        member.addBorrowedIsbn(isbn);
        Loan loan = new Loan(isbn, memberId, LocalDate.now());
        loans.add(loan);
        return loan;
    }

    public Loan returnBook(String isbn, String memberId)
            throws BookNotFoundException, MemberNotFoundException, LibraryException {
        Book book = getBook(isbn);
        Member member = getMember(memberId);

        if (!member.hasBorrowed(isbn)) {
            throw new LibraryException(
                    "Member '" + memberId + "' does not currently have book '" + isbn + "' checked out.");
        }

        // Find the most recent still-open loan for this (isbn, member) pair.
        Loan openLoan = loans.stream()
                .filter(l -> l.getIsbn().equals(isbn) && l.getMemberId().equals(memberId) && !l.isReturned())
                .reduce((first, second) -> second) // keep the last match
                .orElseThrow(() -> new LibraryException(
                        "No open loan record found for book '" + isbn + "' and member '" + memberId + "'."));

        openLoan.markReturned(LocalDate.now());
        book.incrementAvailable();
        member.removeBorrowedIsbn(isbn);
        return openLoan;
    }

    // ---------------------------------------------------------------
    // Search & reporting (Streams-heavy)
    // ---------------------------------------------------------------

    /** Case-insensitive substring search across title, author and genre. */
    public List<Book> searchBooks(String keyword) {
        String needle = keyword == null ? "" : keyword.toLowerCase();
        return catalog.values().stream()
                .filter(b -> containsIgnoreCase(b.getTitle(), needle)
                        || containsIgnoreCase(b.getAuthor(), needle)
                        || containsIgnoreCase(b.getGenre(), needle))
                .sorted()
                .collect(Collectors.toList());
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle);
    }

    public List<Book> availableBooksSorted() {
        return catalog.values().stream()
                .filter(Book::isAvailable)
                .sorted()
                .collect(Collectors.toList());
    }

    public List<Book> allBooksSortedByTitle() {
        return sortedCopyOf(catalog.values());
    }

    /** Generic helper: returns a sorted List<T> copy of any collection of Comparable<T> elements. */
    private static <T extends Comparable<T>> List<T> sortedCopyOf(java.util.Collection<T> source) {
        List<T> copy = new ArrayList<>(source);
        java.util.Collections.sort(copy);
        return copy;
    }

    /** All loans currently out (not yet returned), sorted by due date ascending — most urgent first. */
    public List<Loan> currentlyOnLoan() {
        return loans.stream()
                .filter(l -> !l.isReturned())
                .sorted(Comparator.comparing(Loan::getDueDate))
                .collect(Collectors.toList());
    }

    /** Loans that are overdue as of today. */
    public List<Loan> overdueLoans() {
        LocalDate today = LocalDate.now();
        return loans.stream()
                .filter(l -> l.isOverdue(today))
                .sorted(Comparator.comparing(Loan::getDueDate))
                .collect(Collectors.toList());
    }

    /** Full loan history (returned and outstanding), most recent checkout first. */
    public List<Loan> loanHistory() {
        return loans.stream()
                .sorted(Comparator.comparing(Loan::getCheckoutDate).reversed())
                .collect(Collectors.toList());
    }

    /** Simple aggregate report: how many books are currently checked out, grouped by genre. */
    public Map<String, Long> checkoutCountByGenre() {
        return loans.stream()
                .filter(l -> !l.isReturned())
                .map(l -> catalog.get(l.getIsbn()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(
                        b -> b.getGenre() == null ? "Uncategorized" : b.getGenre(),
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    /** Top-N most-borrowed books of all time (by total loan count), useful for a "popular books" report. */
    public List<Map.Entry<Book, Long>> mostBorrowedBooks(int topN) {
        Map<String, Long> countsByIsbn = loans.stream()
                .collect(Collectors.groupingBy(Loan::getIsbn, Collectors.counting()));

        return countsByIsbn.entrySet().stream()
                .map(e -> Map.entry(catalog.get(e.getKey()), e.getValue()))
                .filter(e -> e.getKey() != null)
                .sorted(Map.Entry.<Book, Long>comparingByValue().reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }

    public int totalBookCount() {
        return catalog.size();
    }

    public int totalMemberCount() {
        return members.size();
    }

    // ---------------------------------------------------------------
    // Package-visible accessors used only by FileStorage for persistence
    // ---------------------------------------------------------------

    java.util.Collection<Book> rawBooks() {
        return catalog.values();
    }

    java.util.Collection<Member> rawMembers() {
        return members.values();
    }

    List<Loan> rawLoans() {
        return loans;
    }

    void restoreLoan(Loan loan) {
        loans.add(loan);
    }

    /**
     * Tiny read-only wrapper so callers get an Iterable/List-like view without
     * being able to mutate Library's internal Map directly. A lightweight,
     * explicit demonstration of generics + encapsulation working together.
     */
    public static class ReadOnlyView<T> implements Iterable<T> {
        private final List<T> items;

        ReadOnlyView(java.util.Collection<T> source) {
            this.items = new ArrayList<>(source);
        }

        public List<T> asList() {
            return java.util.Collections.unmodifiableList(items);
        }

        public int size() {
            return items.size();
        }

        @Override
        public java.util.Iterator<T> iterator() {
            return asList().iterator();
        }
    }
}
