/**
 * LibraryException.java
 *
 * Root checked exception for every domain-specific error the library service
 * layer can raise. Making this a checked exception (extends Exception, not
 * RuntimeException) forces every caller in the console UI to explicitly
 * catch and handle library-domain errors instead of letting them silently
 * propagate — appropriate here because these are expected, recoverable
 * business conditions (not programmer bugs).
 */
public class LibraryException extends Exception {

    public LibraryException(String message) {
        super(message);
    }

    public LibraryException(String message, Throwable cause) {
        super(message, cause);
    }
}
