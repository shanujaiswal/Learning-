/**
 * MemberNotFoundException.java
 *
 * Thrown when an operation references a member id that is not registered
 * in the system.
 */
public class MemberNotFoundException extends LibraryException {

    public MemberNotFoundException(String memberId) {
        super("No member found with id '" + memberId + "'.");
    }
}
