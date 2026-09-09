import java.time.LocalDate;
import java.util.Objects;

/**
 * Book.java
 *
 * Model class for a single physical/catalog copy of a book.
 *
 * Demonstrates:
 *  - Encapsulation (private fields, validated setters)
 *  - Comparable<Book> so collections of books can be sorted naturally (by title)
 *  - equals/hashCode based on the unique ISBN, so a Book can be safely used as
 *    a HashMap key or stored in a HashSet without duplicates
 *  - A clean toString() for console reports
 */
public class Book implements Comparable<Book> {

    private final String isbn;
    private String title;
    private String author;
    private String genre;
    private final int totalCopies;
    private int availableCopies;

    public Book(String isbn, String title, String author, String genre, int totalCopies) {
        if (isbn == null || isbn.isBlank()) {
            throw new IllegalArgumentException("ISBN must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        if (totalCopies < 0) {
            throw new IllegalArgumentException("totalCopies cannot be negative");
        }
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.genre = genre;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    /**
     * Full constructor used when reloading state from disk, where the number
     * of copies currently checked out must be restored exactly as saved
     * rather than recomputed.
     */
    public Book(String isbn, String title, String author, String genre,
                int totalCopies, int availableCopies) {
        this(isbn, title, author, genre, totalCopies);
        if (availableCopies < 0 || availableCopies > totalCopies) {
            throw new IllegalArgumentException(
                    "availableCopies must be between 0 and totalCopies");
        }
        this.availableCopies = availableCopies;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public int getAvailableCopies() {
        return availableCopies;
    }

    public boolean isAvailable() {
        return availableCopies > 0;
    }

    /** Decrements the available count; called only after eligibility is confirmed by Library. */
    void decrementAvailable() {
        if (availableCopies <= 0) {
            throw new IllegalStateException("No available copies to decrement for " + isbn);
        }
        availableCopies--;
    }

    /** Increments the available count on return, never exceeding totalCopies. */
    void incrementAvailable() {
        if (availableCopies >= totalCopies) {
            throw new IllegalStateException("All copies already accounted for " + isbn);
        }
        availableCopies++;
    }

    /** Natural ordering: alphabetical by title (case-insensitive), used by Collections/Streams sorts. */
    @Override
    public int compareTo(Book other) {
        return this.title.compareToIgnoreCase(other.title);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book)) return false;
        Book book = (Book) o;
        return isbn.equals(book.isbn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isbn);
    }

    @Override
    public String toString() {
        return String.format("[%s] \"%s\" by %s (%s) - %d/%d available",
                isbn, title, author == null ? "Unknown" : author,
                genre == null ? "Uncategorized" : genre,
                availableCopies, totalCopies);
    }

    /** Serializes this book to one pipe-delimited line for FileStorage. Placeholder LocalDate import kept for symmetry with Loan records. */
    public String toCsvLine() {
        return String.join("|",
                isbn,
                title,
                author == null ? "" : author,
                genre == null ? "" : genre,
                String.valueOf(totalCopies),
                String.valueOf(availableCopies));
    }

    public static Book fromCsvLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 6) {
            throw new IllegalArgumentException("Malformed book line: " + line);
        }
        String isbn = parts[0];
        String title = parts[1];
        String author = parts[2].isEmpty() ? null : parts[2];
        String genre = parts[3].isEmpty() ? null : parts[3];
        int total = Integer.parseInt(parts[4]);
        int available = Integer.parseInt(parts[5]);
        return new Book(isbn, title, author, genre, total, available);
    }
}
