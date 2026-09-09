import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * FileStorage.java
 *
 * Handles all persistence for the library: saving the full in-memory state
 * (books, members, loans) to a single pipe-delimited text file, and loading
 * it back on startup.
 *
 * Demonstrates:
 *  - File I/O via java.nio.file.Path/Files together with classic
 *    java.io.BufferedReader/BufferedWriter for line-based text processing.
 *  - try-with-resources everywhere a Reader/Writer is opened, guaranteeing
 *    the underlying file handle is closed even if an exception is thrown
 *    mid-write/read.
 *  - Checked-exception translation: low-level IOException is caught and
 *    wrapped into the domain's own LibraryException, so calling code (the
 *    console UI) only ever needs to know about library-domain exceptions.
 *
 * File format: a simple custom "sectioned CSV" text file —
 *   #BOOKS
 *   isbn|title|author|genre|totalCopies|availableCopies
 *   ...
 *   #MEMBERS
 *   id|name|email|joinDate|comma,separated,borrowed,isbns
 *   ...
 *   #LOANS
 *   isbn|memberId|checkoutDate|returnDate(or empty if still out)
 *   ...
 */
public class FileStorage {

    private static final String BOOKS_HEADER = "#BOOKS";
    private static final String MEMBERS_HEADER = "#MEMBERS";
    private static final String LOANS_HEADER = "#LOANS";

    private final Path filePath;

    public FileStorage(String fileName) {
        this.filePath = Path.of(fileName);
    }

    /** Persists the full state of the given Library to disk, overwriting any existing file. */
    public void save(Library library) throws LibraryException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                filePath, StandardCharsets.UTF_8)) {

            writer.write(BOOKS_HEADER);
            writer.newLine();
            for (Book book : library.rawBooks()) {
                writer.write(book.toCsvLine());
                writer.newLine();
            }

            writer.write(MEMBERS_HEADER);
            writer.newLine();
            for (Member member : library.rawMembers()) {
                writer.write(member.toCsvLine());
                writer.newLine();
            }

            writer.write(LOANS_HEADER);
            writer.newLine();
            for (Loan loan : library.rawLoans()) {
                writer.write(loan.toCsvLine());
                writer.newLine();
            }

        } catch (IOException e) {
            throw new LibraryException("Failed to save library data to '" + filePath + "'", e);
        }
    }

    /**
     * Loads previously saved state from disk into the given Library instance.
     * The library is populated via its package-private restore* methods so
     * validation/business rules are bypassed only for this trusted reload
     * path (data that was itself produced by save()).
     *
     * @return true if a file was found and loaded, false if no save file exists yet
     */
    public boolean load(Library library) throws LibraryException {
        if (!Files.exists(filePath)) {
            return false;
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            Section section = Section.NONE;
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                if (line.equals(BOOKS_HEADER)) {
                    section = Section.BOOKS;
                    continue;
                }
                if (line.equals(MEMBERS_HEADER)) {
                    section = Section.MEMBERS;
                    continue;
                }
                if (line.equals(LOANS_HEADER)) {
                    section = Section.LOANS;
                    continue;
                }

                switch (section) {
                    case BOOKS:
                        library.restoreBook(Book.fromCsvLine(line));
                        break;
                    case MEMBERS:
                        library.restoreMember(Member.fromCsvLine(line));
                        break;
                    case LOANS:
                        library.restoreLoan(Loan.fromCsvLine(line));
                        break;
                    default:
                        throw new LibraryException(
                                "Malformed data file: content before any section header at line " + lineNumber);
                }
            }
            return true;

        } catch (IOException e) {
            throw new LibraryException("Failed to load library data from '" + filePath + "'", e);
        } catch (RuntimeException e) {
            // Catches NumberFormatException / IllegalArgumentException / DateTimeParseException
            // from malformed lines and re-wraps them as a single domain exception type.
            throw new LibraryException("Corrupt or unreadable data file '" + filePath + "': " + e.getMessage(), e);
        }
    }

    public boolean dataFileExists() {
        return Files.exists(filePath);
    }

    public String getFilePath() {
        return filePath.toAbsolutePath().toString();
    }

    private enum Section { NONE, BOOKS, MEMBERS, LOANS }
}
