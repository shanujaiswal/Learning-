/**
 * BorrowingLimitExceededException.java
 *
 * Thrown when a member who already has the maximum allowed number of books
 * checked out (see Member.MAX_BOOKS_ALLOWED) attempts to check out another.
 */
public class BorrowingLimitExceededException extends LibraryException {

    public BorrowingLimitExceededException(String memberId, int limit) {
        super("Member '" + memberId + "' already has the maximum of "
                + limit + " book(s) checked out.");
    }
}
