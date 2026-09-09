/**
 * BookNotFoundException.java
 *
 * Thrown when an operation references an ISBN that does not exist anywhere
 * in the catalog at all (as opposed to existing but unavailable — see
 * BookNotAvailableException).
 */
public class BookNotFoundException extends LibraryException {

    public BookNotFoundException(String isbn) {
        super("No book found in the catalog with ISBN '" + isbn + "'.");
    }
}
