/**
 * DuplicateEntryException.java
 *
 * Thrown when attempting to add a book (by ISBN) or a member (by id) that
 * already exists in the system, preventing silent data overwrites.
 */
public class DuplicateEntryException extends LibraryException {

    public DuplicateEntryException(String kind, String key) {
        super(kind + " with key '" + key + "' already exists.");
    }
}
