/**
 * Person.java
 *
 * Abstract base class demonstrating inheritance: any human participant in the
 * library system (currently only Member, but a Librarian could extend this
 * later) shares an id, a name and an email. Keeping this abstract (rather than
 * a concrete class Member extends directly from Object) is a deliberate OOP
 * choice to show that the "person" concept is not meant to be instantiated on
 * its own — only concrete subclasses like Member make sense as objects.
 */
public abstract class Person {

    private final String id;
    private String name;
    private String email;

    protected Person(String id, String name, String email) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Every concrete Person subclass must describe its own role
     * (e.g. "Member", "Librarian") — a small abstract method used to show
     * polymorphism in the console listing code.
     */
    public abstract String getRole();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Person)) return false;
        Person other = (Person) o;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
