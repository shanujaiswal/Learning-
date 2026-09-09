import java.time.LocalDate;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * LibraryManagementSystemApp.java
 *
 * Entry point: a Scanner-driven console menu that wires the console UI to
 * the Library service layer and FileStorage persistence layer. This class
 * intentionally contains no business logic itself — it only reads input,
 * calls Library/FileStorage, and prints results/errors. That separation is
 * what keeps Library independently testable and reusable.
 *
 * Demonstrates:
 *  - Exception handling: every menu action that can fail is wrapped in a
 *    try/catch targeting the specific LibraryException subclasses, with
 *    distinct, meaningful messages surfaced to the user.
 *  - Streams: report-style menu options delegate straight to Library's
 *    stream-based query methods and print the results.
 */
public class LibraryManagementSystemApp {

    private static final String DATA_FILE = "library_data.txt";

    private final Scanner scanner = new Scanner(System.in);
    private final Library library = new Library();
    private final FileStorage storage = new FileStorage(DATA_FILE);

    public static void main(String[] args) {
        new LibraryManagementSystemApp().run();
    }

    private void run() {
        System.out.println("=== Library Management System ===");
        autoLoadOnStartup();
        seedSampleDataIfEmpty();

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readMenuChoice();
            switch (choice) {
                case 1:
                    addBookFlow();
                    break;
                case 2:
                    registerMemberFlow();
                    break;
                case 3:
                    searchBooksFlow();
                    break;
                case 4:
                    checkoutFlow();
                    break;
                case 5:
                    returnBookFlow();
                    break;
                case 6:
                    listAllBooksFlow();
                    break;
                case 7:
                    listAllMembersFlow();
                    break;
                case 8:
                    listOverdueFlow();
                    break;
                case 9:
                    reportsFlow();
                    break;
                case 10:
                    saveFlow();
                    break;
                case 11:
                    loadFlow();
                    break;
                case 0:
                    running = false;
                    System.out.println("Goodbye!");
                    break;
                default:
                    System.out.println("Invalid option. Please choose a number from the menu.");
                    break;
            }
        }
        scanner.close();
    }

    private void printMenu() {
        System.out.println();
        System.out.println("------------------------------------------");
        System.out.println(" 1. Add a book");
        System.out.println(" 2. Register a member");
        System.out.println(" 3. Search books");
        System.out.println(" 4. Checkout a book");
        System.out.println(" 5. Return a book");
        System.out.println(" 6. List all books");
        System.out.println(" 7. List all members");
        System.out.println(" 8. List overdue loans");
        System.out.println(" 9. Reports (popular books / genre breakdown)");
        System.out.println("10. Save library data to file");
        System.out.println("11. Load library data from file");
        System.out.println(" 0. Exit");
        System.out.println("------------------------------------------");
        System.out.print("Choose an option: ");
    }

    private int readMenuChoice() {
        String line = scanner.nextLine().trim();
        try {
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            return -1; // falls through to "Invalid option" in the switch
        }
    }

    // ---------------------------------------------------------------
    // Menu actions
    // ---------------------------------------------------------------

    private void addBookFlow() {
        System.out.println("\n-- Add a Book --");
        String isbn = prompt("ISBN: ");
        String title = prompt("Title: ");
        String author = prompt("Author: ");
        String genre = prompt("Genre: ");
        int copies = promptPositiveInt("Number of copies: ");

        try {
            Book book = library.addBook(isbn, title, author, genre, copies);
            System.out.println("Added: " + book);
        } catch (DuplicateEntryException e) {
            System.out.println("Could not add book: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid book data: " + e.getMessage());
        }
    }

    private void registerMemberFlow() {
        System.out.println("\n-- Register a Member --");
        String id = prompt("Member ID: ");
        String name = prompt("Name: ");
        String email = prompt("Email (optional, press Enter to skip): ");
        if (email.isBlank()) {
            email = null;
        }

        try {
            Member member = library.registerMember(id, name, email);
            System.out.println("Registered: " + member);
        } catch (DuplicateEntryException e) {
            System.out.println("Could not register member: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid member data: " + e.getMessage());
        }
    }

    private void searchBooksFlow() {
        System.out.println("\n-- Search Books --");
        String keyword = prompt("Enter a keyword (matches title/author/genre): ");
        List<Book> results = library.searchBooks(keyword);
        if (results.isEmpty()) {
            System.out.println("No books matched '" + keyword + "'.");
        } else {
            System.out.println("Found " + results.size() + " book(s):");
            results.forEach(b -> System.out.println("  " + b));
        }
    }

    private void checkoutFlow() {
        System.out.println("\n-- Checkout a Book --");
        String isbn = prompt("ISBN of the book: ");
        String memberId = prompt("Member ID: ");

        try {
            Loan loan = library.checkout(isbn, memberId);
            System.out.println("Checked out successfully: " + loan);
        } catch (BookNotFoundException | MemberNotFoundException
                | BookNotAvailableException | BorrowingLimitExceededException e) {
            System.out.println("Checkout failed: " + e.getMessage());
        }
    }

    private void returnBookFlow() {
        System.out.println("\n-- Return a Book --");
        String isbn = prompt("ISBN of the book: ");
        String memberId = prompt("Member ID: ");

        try {
            Loan loan = library.returnBook(isbn, memberId);
            System.out.println("Returned successfully: " + loan);
        } catch (LibraryException e) {
            System.out.println("Return failed: " + e.getMessage());
        }
    }

    private void listAllBooksFlow() {
        System.out.println("\n-- All Books (sorted by title) --");
        List<Book> books = library.allBooksSortedByTitle();
        if (books.isEmpty()) {
            System.out.println("The catalog is empty.");
        } else {
            books.forEach(b -> System.out.println("  " + b));
        }
    }

    private void listAllMembersFlow() {
        System.out.println("\n-- All Members --");
        var members = library.allMembers().asList();
        if (members.isEmpty()) {
            System.out.println("No members registered yet.");
        } else {
            members.forEach(m -> System.out.println("  " + m));
        }
    }

    private void listOverdueFlow() {
        System.out.println("\n-- Overdue Loans --");
        List<Loan> overdue = library.overdueLoans();
        if (overdue.isEmpty()) {
            System.out.println("No overdue loans. Nice!");
        } else {
            overdue.forEach(l -> System.out.println("  " + l));
        }
    }

    private void reportsFlow() {
        System.out.println("\n-- Reports --");
        System.out.println("Total books in catalog : " + library.totalBookCount());
        System.out.println("Total registered members: " + library.totalMemberCount());
        System.out.println("Currently on loan       : " + library.currentlyOnLoan().size());

        System.out.println("\nTop 5 most-borrowed books of all time:");
        List<Map.Entry<Book, Long>> top = library.mostBorrowedBooks(5);
        if (top.isEmpty()) {
            System.out.println("  No checkout history yet.");
        } else {
            for (Map.Entry<Book, Long> entry : top) {
                System.out.printf("  %-40s borrowed %d time(s)%n",
                        entry.getKey().getTitle(), entry.getValue());
            }
        }

        System.out.println("\nBooks currently checked out, by genre:");
        Map<String, Long> byGenre = library.checkoutCountByGenre();
        if (byGenre.isEmpty()) {
            System.out.println("  Nothing currently checked out.");
        } else {
            byGenre.forEach((genre, count) -> System.out.printf("  %-20s %d%n", genre, count));
        }
    }

    private void saveFlow() {
        try {
            storage.save(library);
            System.out.println("Library data saved to " + storage.getFilePath());
        } catch (LibraryException e) {
            System.out.println("Save failed: " + e.getMessage());
        }
    }

    private void loadFlow() {
        try {
            boolean loaded = storage.load(library);
            if (loaded) {
                System.out.println("Library data loaded from " + storage.getFilePath());
            } else {
                System.out.println("No existing data file found at " + storage.getFilePath());
            }
        } catch (LibraryException e) {
            System.out.println("Load failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Startup helpers
    // ---------------------------------------------------------------

    private void autoLoadOnStartup() {
        try {
            if (storage.load(library)) {
                System.out.println("Existing library data auto-loaded from " + storage.getFilePath());
            }
        } catch (LibraryException e) {
            System.out.println("Warning: could not auto-load existing data (" + e.getMessage() + ")");
        }
    }

    /** If nothing was loaded from disk, seed a few sample records so the menu is not empty on a fresh run. */
    private void seedSampleDataIfEmpty() {
        if (library.totalBookCount() > 0) {
            return;
        }
        try {
            library.addBook("978-0132350884", "Clean Code", "Robert C. Martin", "Software Engineering", 2);
            library.addBook("978-0134685991", "Effective Java", "Joshua Bloch", "Software Engineering", 3);
            library.addBook("978-0golden001", "Dune", "Frank Herbert", "Science Fiction", 1);
            library.registerMember("M001", "Ada Lovelace", "ada@example.com");
            library.registerMember("M002", "Alan Turing", "alan@example.com");
            System.out.println("Seeded sample catalog and members for this session.");
        } catch (LibraryException e) {
            // Should never happen on a fresh, empty library, but handled defensively regardless.
            System.out.println("Warning: could not seed sample data (" + e.getMessage() + ")");
        }
    }

    // ---------------------------------------------------------------
    // Small input helpers
    // ---------------------------------------------------------------

    private String prompt(String label) {
        System.out.print(label);
        return scanner.nextLine().trim();
    }

    private int promptPositiveInt(String label) {
        while (true) {
            System.out.print(label);
            String line = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value < 0) {
                    System.out.println("Please enter a non-negative number.");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }
}
