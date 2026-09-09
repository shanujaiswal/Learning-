/**
 * BookNotAvailableException.java
 *
 * Thrown when a checkout is attempted for a book that exists in the catalog
 * but has zero available copies right now (all copies are currently on loan).
 */
public class BookNotAvailableException extends LibraryException {

    public BookNotAvailableException(String isbn) {
        super("Book with ISBN '" + isbn + "' has no available copies right now.");
    }
}
